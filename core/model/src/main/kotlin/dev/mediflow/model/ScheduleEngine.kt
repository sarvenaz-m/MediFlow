package dev.mediflow.model

import java.time.*

/** One occurrence per wall-clock slot; a DST overlap never creates a second dose. */
object ScheduleEngine {
    fun key(id: String, date: LocalDate, time: LocalTime): String = "$id|$date|$time"

    fun occurrence(
        medication: Medication,
        date: LocalDate,
        time: LocalTime,
        deviceZone: ZoneId,
    ): DoseOccurrence? {
        if (
            medication.archived ||
                !medication.schedule.contains(date) ||
                time !in medication.schedule.times
        )
            return null
        // java.time moves a gap forward by its duration and selects the earlier offset in an
        // overlap.
        val instant =
            date
                .atTime(time)
                .atZone(medication.schedule.zone(deviceZone))
                .withEarlierOffsetAtOverlap()
                .toInstant()
        return DoseOccurrence(key(medication.id, date, time), medication, instant, date, time)
    }

    fun resolve(medication: Medication, key: String, deviceZone: ZoneId): DoseOccurrence? =
        runCatching {
                val parts = key.split('|')
                require(parts.size == 3 && parts[0] == medication.id)
                occurrence(
                    medication,
                    LocalDate.parse(parts[1]),
                    LocalTime.parse(parts[2]),
                    deviceZone,
                )
            }
            .getOrNull()

    fun between(
        medication: Medication,
        from: Instant,
        until: Instant,
        deviceZone: ZoneId,
    ): List<DoseOccurrence> {
        require(!until.isBefore(from))
        require(Duration.between(from, until).toDays() <= 400) { "Query at most 400 days at once." }
        if (medication.archived || from == until) return emptyList()
        val zone = medication.schedule.zone(deviceZone)
        val result = mutableListOf<DoseOccurrence>()
        var date = from.atZone(zone).toLocalDate().minusDays(1)
        val last = until.atZone(zone).toLocalDate().plusDays(1)
        while (!date.isAfter(last)) {
            medication.schedule.times.sorted().forEach { time ->
                occurrence(medication, date, time, deviceZone)?.let {
                    if (!it.scheduledAt.isBefore(from) && it.scheduledAt.isBefore(until))
                        result += it
                }
            }
            date = date.plusDays(1)
        }
        return result.sortedWith(compareBy({ it.scheduledAt }, { it.key }))
    }

    fun day(snapshot: RoutineSnapshot, date: LocalDate, zone: ZoneId): List<DoseOccurrence> =
        snapshot.medications
            .flatMap {
                between(
                    it,
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant(),
                    zone,
                )
            }
            .sortedWith(compareBy({ it.scheduledAt }, { it.medication.name }))

    fun next(
        snapshot: RoutineSnapshot,
        now: Instant,
        zone: ZoneId,
        days: Long = 8,
    ): List<DoseOccurrence> {
        val recorded = snapshot.records.mapTo(hashSetOf()) { it.key }
        return snapshot.medications
            .flatMap { between(it, now.plusMillis(1), now.plus(Duration.ofDays(days)), zone) }
            .filterNot { it.key in recorded }
            .sortedBy { it.scheduledAt }
    }

    fun stockDays(medication: Medication, now: Instant, zone: ZoneId): Long? {
        if (medication.archived) return null
        var left = medication.stockMilli
        for (dose in between(medication, now, now.plus(Duration.ofDays(366)), zone)) {
            if (left < medication.quantityMilli)
                return Duration.between(now, dose.scheduledAt).toDays().coerceAtLeast(0)
            left -= medication.quantityMilli
        }
        return null
    }
}

/** Status changes are reversible using the quantity captured in the existing record. */
object InventoryPolicy {
    fun stockAfter(
        stock: Long,
        previous: DoseRecord?,
        next: DoseStatus?,
        currentQuantity: Long,
    ): Long? {
        val restored = if (previous?.status == DoseStatus.TAKEN) previous.quantityMilli else 0
        val consumed = if (next == DoseStatus.TAKEN) currentQuantity else 0
        val result = stock + restored - consumed
        return result.takeIf { it in 0..Quantity.MAX }
    }
}

data class Adherence(val taken: Int, val skipped: Int, val unrecorded: Int) {
    val total: Int
        get() = taken + skipped + unrecorded

    val percent: Int
        get() = if (total == 0) 0 else (100.0 * taken / total).toInt()
}

object RoutineInsights {
    fun adherence(
        occurrences: List<DoseOccurrence>,
        records: List<DoseRecord>,
        now: Instant,
    ): Adherence {
        val byKey = records.associateBy { it.key }
        val due = occurrences.filter { !it.scheduledAt.isAfter(now) || it.key in byKey }
        return Adherence(
            due.count { byKey[it.key]?.status == DoseStatus.TAKEN },
            due.count { byKey[it.key]?.status == DoseStatus.SKIPPED },
            due.count { it.key !in byKey },
        )
    }
}
