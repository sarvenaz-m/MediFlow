package dev.mediflow.model

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleEngineTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    private fun medicine(time: String = "08:00", policy: TimePolicy = TimePolicy.LOCAL_CLOCK) =
        Medication(
            "sample",
            "Sample tablet",
            "Example entry",
            MedicineForm.TABLET,
            1000,
            30_000,
            5000,
            DoseSchedule(
                listOf(LocalTime.parse(time)),
                startDate = LocalDate.of(2026, 1, 1),
                timePolicy = policy,
            ),
        )

    @Test
    fun springGapMovesForwardWithoutDroppingDose() {
        val dose =
            ScheduleEngine.occurrence(
                medicine("01:30"),
                LocalDate.of(2026, 3, 29),
                LocalTime.of(1, 30),
                lisbon,
            )!!
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), dose.scheduledAt)
        assertEquals(LocalTime.of(2, 30), dose.scheduledAt.atZone(lisbon).toLocalTime())
    }

    @Test
    fun autumnOverlapUsesFirstInstantOnly() {
        val med = medicine("01:30")
        val from = LocalDate.of(2026, 10, 25).atStartOfDay(lisbon).toInstant()
        val doses = ScheduleEngine.between(med, from, from.plus(Duration.ofHours(26)), lisbon)
        assertEquals(1, doses.size)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), doses.single().scheduledAt)
    }

    @Test
    fun localClockFollowsTravel() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val day = LocalDate.of(2026, 7, 10)
        val dose = ScheduleEngine.occurrence(medicine(), day, LocalTime.of(8, 0), tokyo)!!
        assertEquals(Instant.parse("2026-07-09T23:00:00Z"), dose.scheduledAt)
    }

    @Test
    fun homeZoneKeepsOriginalClock() {
        val dose =
            ScheduleEngine.occurrence(
                medicine(policy = TimePolicy.HOME_ZONE),
                LocalDate.of(2026, 7, 10),
                LocalTime.of(8, 0),
                ZoneId.of("Asia/Tokyo"),
            )!!
        assertEquals(Instant.parse("2026-07-10T07:00:00Z"), dose.scheduledAt)
    }

    @Test
    fun travelDoesNotChangeOccurrenceIdentity() {
        val med = medicine()
        val day = LocalDate.of(2026, 7, 10)
        val time = LocalTime.of(8, 0)
        assertEquals(
            ScheduleEngine.occurrence(med, day, time, lisbon)!!.key,
            ScheduleEngine.occurrence(med, day, time, ZoneId.of("Asia/Tokyo"))!!.key,
        )
    }

    @Test
    fun endDateIsInclusive() {
        val med =
            medicine().let {
                it.copy(schedule = it.schedule.copy(endDate = LocalDate.of(2026, 7, 10)))
            }
        assertNotNull(
            ScheduleEngine.occurrence(med, LocalDate.of(2026, 7, 10), LocalTime.of(8, 0), lisbon)
        )
        assertNull(
            ScheduleEngine.occurrence(med, LocalDate.of(2026, 7, 11), LocalTime.of(8, 0), lisbon)
        )
    }

    @Test
    fun weekdaySelectionIsHonored() {
        val med =
            medicine().let {
                it.copy(schedule = it.schedule.copy(weekdays = setOf(DayOfWeek.MONDAY)))
            }
        assertNotNull(
            ScheduleEngine.occurrence(med, LocalDate.of(2026, 10, 12), LocalTime.of(8, 0), lisbon)
        )
        assertNull(
            ScheduleEngine.occurrence(med, LocalDate.of(2026, 10, 13), LocalTime.of(8, 0), lisbon)
        )
    }

    @Test
    fun archivedMedicineProducesNoReminders() {
        assertTrue(
            ScheduleEngine.next(
                    RoutineSnapshot(listOf(medicine().copy(archived = true))),
                    Instant.parse("2026-07-10T00:00:00Z"),
                    lisbon,
                )
                .isEmpty()
        )
    }

    @Test
    fun recordedDoseIsNotScheduledAgain() {
        val now = Instant.parse("2026-07-10T00:00:00Z")
        val med = medicine()
        val first = ScheduleEngine.next(RoutineSnapshot(listOf(med)), now, lisbon).first()
        val record =
            DoseRecord(first.key, med.id, med.name, first.scheduledAt, now, 1000, DoseStatus.TAKEN)
        assertFalse(
            ScheduleEngine.next(RoutineSnapshot(listOf(med), listOf(record)), now, lisbon).any {
                it.key == first.key
            }
        )
    }

    @Test
    fun exactRangeBoundaryIsHalfOpen() {
        val med = medicine()
        val dose =
            ScheduleEngine.occurrence(med, LocalDate.of(2026, 7, 10), LocalTime.of(8, 0), lisbon)!!
        assertEquals(
            1,
            ScheduleEngine.between(med, dose.scheduledAt, dose.scheduledAt.plusSeconds(1), lisbon)
                .size,
        )
        assertTrue(
            ScheduleEngine.between(med, dose.scheduledAt.minusSeconds(1), dose.scheduledAt, lisbon)
                .isEmpty()
        )
    }

    @Test
    fun malformedKeysNeverResolve() {
        listOf(
                "",
                "wrong|2026-10-12|08:00",
                "sample|tomorrow|08:00",
                "sample|2026-10-12|99:00",
                "sample|2026-10-12|09:00",
            )
            .forEach {
                assertNull(ScheduleEngine.resolve(medicine(), it, lisbon))
            }
    }

    @Test
    fun twoTimesHaveDistinctKeys() {
        val med =
            medicine().let {
                it.copy(
                    schedule =
                        it.schedule.copy(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
                )
            }
        val doses =
            ScheduleEngine.day(RoutineSnapshot(listOf(med)), LocalDate.of(2026, 10, 12), lisbon)
        assertEquals(2, doses.map { it.key }.distinct().size)
    }

    @Test
    fun futureDosesDoNotReduceAdherence() {
        val med = medicine()
        val now = Instant.parse("2026-07-10T00:00:00Z")
        assertEquals(
            0,
            RoutineInsights.adherence(
                    ScheduleEngine.day(
                        RoutineSnapshot(listOf(med)),
                        LocalDate.of(2026, 7, 10),
                        lisbon,
                    ),
                    emptyList(),
                    now,
                )
                .total,
        )
    }

    @Test
    fun dateLineTravelStillFindsHomeZoneDose() {
        val med = medicine(policy = TimePolicy.HOME_ZONE)
        val doses =
            ScheduleEngine.day(
                RoutineSnapshot(listOf(med)),
                LocalDate.of(2026, 7, 10),
                ZoneId.of("Pacific/Kiritimati"),
            )
        assertEquals(1, doses.size)
        assertEquals(
            LocalDate.of(2026, 7, 10),
            doses.single().scheduledAt.atZone(ZoneId.of("Pacific/Kiritimati")).toLocalDate(),
        )
    }

    @Test
    fun depletionForecastHonorsWeekdaysAndFractionalUnits() {
        val med =
            medicine().let {
                it.copy(
                    stockMilli = 500,
                    quantityMilli = 500,
                    schedule = it.schedule.copy(weekdays = setOf(DayOfWeek.MONDAY)),
                )
            }
        assertEquals(
            7L,
            ScheduleEngine.stockDays(med, Instant.parse("2026-10-12T00:00:00Z"), lisbon),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTimesAreRejected() {
        DoseSchedule(listOf(LocalTime.of(8, 0), LocalTime.of(8, 0)), startDate = LocalDate.now())
    }
}
