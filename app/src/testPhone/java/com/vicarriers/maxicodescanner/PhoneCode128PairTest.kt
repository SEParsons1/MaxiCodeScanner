package com.vicarriers.maxicodescanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCode128PairTest {
    @Test
    fun postalMatchesSsiSort420And421() {
        assertTrue(PhoneCode128Pair.isPostalBarcode("420V8A0G2"))
        assertTrue(PhoneCode128Pair.isPostalBarcode("421124V8A0G2"))
        assertTrue(PhoneCode128Pair.isPostalBarcode("420ABC123"))
        assertTrue(PhoneCode128Pair.isPostalBarcode("421ABC123456"))
        assertFalse(PhoneCode128Pair.isPostalBarcode("420V8A0G"))
        assertFalse(PhoneCode128Pair.isPostalBarcode(" 420V8A0G2"))
        assertFalse(PhoneCode128Pair.isPostalBarcode("1ZV56D262024334271"))
    }

    @Test
    fun extractsPostalFrom420And421() {
        assertEquals("V8A0G2", PhoneCode128Pair.postalFromBarcode("420V8A0G2"))
        assertEquals("V8A0G2", PhoneCode128Pair.postalFromBarcode("421124V8A0G2"))
        assertEquals("ABC123", PhoneCode128Pair.postalFromBarcode("420ABC123"))
    }

    @Test
    fun trackingMatchesUps1Z() {
        assertEquals(
            "1ZV56D262024334271",
            PhoneCode128Pair.trackingFromBarcode("1Z V56 D26 20 2433 4271"),
        )
        assertNull(PhoneCode128Pair.trackingFromBarcode("420V8A0G2"))
        assertNull(PhoneCode128Pair.trackingFromBarcode("1ZSHORT"))
    }

    @Test
    fun pairRequiresBothBarcodesInTheSameShot() {
        val tracking = "1ZV56D262024334271"
        val postal = "420V8A0G2"
        val hit = PhoneCode128Pair.pair(listOf(tracking, postal))
        assertEquals(tracking, hit?.tracking)
        assertEquals("V8A0G2", hit?.postalCode)
        assertNull(PhoneCode128Pair.pair(listOf(tracking)))
        assertNull(PhoneCode128Pair.pair(listOf(postal)))
        assertNull(PhoneCode128Pair.pair(emptyList()))
    }
}
