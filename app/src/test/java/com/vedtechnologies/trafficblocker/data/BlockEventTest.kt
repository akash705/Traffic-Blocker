package com.vedtechnologies.trafficblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEventTest {

    @Test
    fun `BlockEvent creates with correct fields`() {
        val event = BlockEvent(
            packageName = "com.instagram.android",
            eventType = "BLOCKED",
            timestamp = 1000L
        )

        assertEquals(0, event.id)
        assertEquals("com.instagram.android", event.packageName)
        assertEquals("BLOCKED", event.eventType)
        assertEquals(1000L, event.timestamp)
    }

    @Test
    fun `BlockEvent default id is 0`() {
        val event = BlockEvent(
            packageName = "com.example.app",
            eventType = "UNBLOCKED"
        )
        assertEquals(0, event.id)
    }

    @Test
    fun `BlockEvent timestamp defaults to current time`() {
        val before = System.currentTimeMillis()
        val event = BlockEvent(
            packageName = "com.example.app",
            eventType = "BLOCKED"
        )
        val after = System.currentTimeMillis()

        assertTrue(event.timestamp in before..after)
    }

    @Test
    fun `BlockEvent copy works correctly`() {
        val event = BlockEvent(
            id = 1,
            packageName = "com.example.app",
            eventType = "BLOCKED",
            timestamp = 5000L
        )
        val copied = event.copy(eventType = "UNBLOCKED")

        assertEquals(1, copied.id)
        assertEquals("com.example.app", copied.packageName)
        assertEquals("UNBLOCKED", copied.eventType)
        assertEquals(5000L, copied.timestamp)
    }
}
