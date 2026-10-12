package dev.mediflow.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
    private val record =
        DoseRecord("k", "m", "Sample", Instant.EPOCH, Instant.EPOCH, 500, DoseStatus.TAKEN)

    @Test
    fun fractionalQuantityIsExact() {
        assertEquals(125L, Quantity.parse("0.125"))
        assertEquals("0.125", Quantity.format(125))
    }

    @Test
    fun quantitiesRoundTrip() {
        listOf(0L, 1L, 500L, 123456L, Quantity.MAX).forEach {
            assertEquals(it, Quantity.parse(Quantity.format(it)))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessPrecisionRejected() {
        Quantity.parse("0.0001")
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeQuantityRejected() {
        Quantity.parse("-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteRejected() {
        Quantity.parse("NaN")
    }

    @Test
    fun takingConsumesStock() {
        assertEquals(1500L, InventoryPolicy.stockAfter(2000, null, DoseStatus.TAKEN, 500))
    }

    @Test
    fun skippingKeepsStock() {
        assertEquals(2000L, InventoryPolicy.stockAfter(2000, null, DoseStatus.SKIPPED, 500))
    }

    @Test
    fun undoRestoresHistoricalQuantity() {
        assertEquals(2500L, InventoryPolicy.stockAfter(2000, record, null, 1000))
    }

    @Test
    fun changingTakenToSkippedRestoresStock() {
        assertEquals(2500L, InventoryPolicy.stockAfter(2000, record, DoseStatus.SKIPPED, 1000))
    }

    @Test
    fun insufficientStockIsRejected() {
        assertNull(InventoryPolicy.stockAfter(200, null, DoseStatus.TAKEN, 500))
    }

    @Test
    fun overflowIsRejected() {
        assertNull(InventoryPolicy.stockAfter(Quantity.MAX, record, null, 500))
    }

    @Test
    fun repeatedFractionalConsumptionNeverDrifts() {
        var stock = 10_000L
        repeat(80) { stock = InventoryPolicy.stockAfter(stock, null, DoseStatus.TAKEN, 125)!! }
        assertEquals(0L, stock)
    }
}
