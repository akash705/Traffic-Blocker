package com.akash.apptrafficblocker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockEventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BlockEventDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.blockEventDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetLastEvent() = runTest {
        val event = BlockEvent(
            packageName = "com.instagram.android",
            eventType = "BLOCKED",
            timestamp = 1000L
        )
        dao.insert(event)

        val last = dao.getLastEvent()
        assertEquals("com.instagram.android", last?.packageName)
        assertEquals("BLOCKED", last?.eventType)
        assertEquals(1000L, last?.timestamp)
    }

    @Test
    fun getLastEventReturnsNullWhenEmpty() = runTest {
        val last = dao.getLastEvent()
        assertNull(last)
    }

    @Test
    fun getLastEventReturnsMostRecent() = runTest {
        dao.insert(BlockEvent(packageName = "com.app1", eventType = "BLOCKED", timestamp = 1000L))
        dao.insert(BlockEvent(packageName = "com.app2", eventType = "BLOCKED", timestamp = 2000L))
        dao.insert(BlockEvent(packageName = "com.app3", eventType = "UNBLOCKED", timestamp = 3000L))

        val last = dao.getLastEvent()
        assertEquals("com.app3", last?.packageName)
        assertEquals("UNBLOCKED", last?.eventType)
    }

    @Test
    fun getRecentEventsReturnsDescendingOrder() = runTest {
        dao.insert(BlockEvent(packageName = "com.app", eventType = "BLOCKED", timestamp = 1000L))
        dao.insert(BlockEvent(packageName = "com.app", eventType = "UNBLOCKED", timestamp = 2000L))
        dao.insert(BlockEvent(packageName = "com.app", eventType = "BLOCKED", timestamp = 3000L))

        val events = dao.getRecentEvents().first()
        assertEquals(3, events.size)
        assertEquals(3000L, events[0].timestamp)
        assertEquals(2000L, events[1].timestamp)
        assertEquals(1000L, events[2].timestamp)
    }

    @Test
    fun getRecentEventsLimitsTo50() = runTest {
        repeat(60) { i ->
            dao.insert(
                BlockEvent(
                    packageName = "com.app",
                    eventType = "BLOCKED",
                    timestamp = i.toLong()
                )
            )
        }

        val events = dao.getRecentEvents().first()
        assertEquals(50, events.size)
    }

    @Test
    fun clearAllRemovesAllEvents() = runTest {
        dao.insert(BlockEvent(packageName = "com.app1", eventType = "BLOCKED"))
        dao.insert(BlockEvent(packageName = "com.app2", eventType = "BLOCKED"))
        dao.insert(BlockEvent(packageName = "com.app3", eventType = "BLOCKED"))

        dao.clearAll()

        val events = dao.getRecentEvents().first()
        assertTrue(events.isEmpty())
        assertNull(dao.getLastEvent())
    }

    @Test
    fun insertMultipleEventsForSamePackage() = runTest {
        dao.insert(BlockEvent(packageName = "com.instagram.android", eventType = "BLOCKED", timestamp = 1000L))
        dao.insert(BlockEvent(packageName = "com.instagram.android", eventType = "UNBLOCKED", timestamp = 2000L))
        dao.insert(BlockEvent(packageName = "com.instagram.android", eventType = "BLOCKED", timestamp = 3000L))

        val events = dao.getRecentEvents().first()
        assertEquals(3, events.size)
        // Each has a unique auto-generated ID
        val ids = events.map { it.id }.toSet()
        assertEquals(3, ids.size)
    }
}
