package com.akash.apptrafficblocker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerStateTest {

    @Test
    fun `default state is inactive`() {
        val state = BlockerState()

        assertFalse(state.isRunning)
        assertFalse(state.isBlocking)
        assertFalse(state.isPaused)
        assertTrue(state.targetPackages.isEmpty())
        assertNull(state.lastBlockedAt)
    }

    @Test
    fun `running state with target packages`() {
        val packages = setOf("com.instagram.android", "com.facebook.katana")
        val state = BlockerState(
            isRunning = true,
            targetPackages = packages
        )

        assertTrue(state.isRunning)
        assertFalse(state.isBlocking)
        assertEquals(2, state.targetPackages.size)
    }

    @Test
    fun `blocking state`() {
        val state = BlockerState(
            isRunning = true,
            isBlocking = true,
            targetPackages = setOf("com.instagram.android"),
            lastBlockedAt = 1000L
        )

        assertTrue(state.isRunning)
        assertTrue(state.isBlocking)
        assertEquals(1000L, state.lastBlockedAt)
    }

    @Test
    fun `paused state`() {
        val state = BlockerState(
            isRunning = true,
            isPaused = true,
            targetPackages = setOf("com.instagram.android")
        )

        assertTrue(state.isRunning)
        assertTrue(state.isPaused)
        assertFalse(state.isBlocking)
    }

    @Test
    fun `copy transitions from watching to blocking`() {
        val watching = BlockerState(
            isRunning = true,
            isBlocking = false,
            targetPackages = setOf("com.instagram.android", "com.facebook.katana")
        )
        val blocking = watching.copy(
            isBlocking = true,
            lastBlockedAt = System.currentTimeMillis()
        )

        assertTrue(blocking.isRunning)
        assertTrue(blocking.isBlocking)
        assertEquals(2, blocking.targetPackages.size)
    }

    @Test
    fun `copy transitions from blocking to watching`() {
        val blocking = BlockerState(
            isRunning = true,
            isBlocking = true,
            targetPackages = setOf("com.instagram.android"),
            lastBlockedAt = 5000L
        )
        val watching = blocking.copy(isBlocking = false)

        assertTrue(watching.isRunning)
        assertFalse(watching.isBlocking)
        assertEquals(5000L, watching.lastBlockedAt)
    }

    @Test
    fun `copy transitions to paused`() {
        val running = BlockerState(
            isRunning = true,
            isBlocking = true,
            targetPackages = setOf("com.instagram.android")
        )
        val paused = running.copy(isPaused = true, isBlocking = false)

        assertTrue(paused.isRunning)
        assertTrue(paused.isPaused)
        assertFalse(paused.isBlocking)
    }
}
