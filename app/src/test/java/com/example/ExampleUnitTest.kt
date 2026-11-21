package com.example

import com.example.data.model.Bed
import com.example.data.model.BedStatus
import com.example.data.model.BookingStatus
import com.example.data.model.PgFormatters
import com.example.data.model.safeEnum
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ExampleUnitTest {
    @Test
    fun bedAvailability_respectsStatusAndReservationExpiry() {
        val now = 1_700_000_000_000L
        val availableBed = Bed(id = "b1", status = BedStatus.AVAILABLE.name)
        val activeReservedBed = Bed(
            id = "b2",
            status = BedStatus.RESERVED.name,
            reservationExpiresAt = Timestamp(Date(now + 60_000L))
        )
        val expiredReservedBed = Bed(
            id = "b3",
            status = BedStatus.RESERVED.name,
            reservationExpiresAt = Timestamp(Date(now - 60_000L))
        )
        val occupiedBed = Bed(id = "b4", status = BedStatus.OCCUPIED.name)

        assertTrue(availableBed.isEffectivelyAvailable(now))
        assertFalse(activeReservedBed.isEffectivelyAvailable(now))
        assertTrue(expiredReservedBed.isEffectivelyAvailable(now))
        assertFalse(occupiedBed.isEffectivelyAvailable(now))
    }

    @Test
    fun safeEnum_fallsBackGracefullyOnUnknownValues() {
        val parsed = safeEnum("UNKNOWN_STATUS", BookingStatus.PENDING_APPROVAL)
        assertEquals(BookingStatus.PENDING_APPROVAL, parsed)
    }

    @Test
    fun formatInr_formatsIndianRupees() {
        val formatted = PgFormatters.formatInr(14500L)
        assertTrue(formatted.contains("14,500"))
    }
}
