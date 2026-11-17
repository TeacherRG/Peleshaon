package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.PendingAlarmQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingAlarmQueueTest {
    @Test fun simultaneousAlarmsDoNotOverwriteOrMislabelEachOther() {
        val queue = PendingAlarmQueue()
        assertTrue(queue.enqueue("TORAH", "MORNING"))
        assertFalse(queue.enqueue("MORNING", "MORNING"))
        assertFalse(queue.enqueue("TORAH", "MORNING"))
        assertTrue(queue.enqueue("C1", "MORNING"))
        assertEquals("TORAH", queue.poll())
        assertEquals("C1", queue.poll())
        assertNull(queue.poll())
        assertTrue(queue.isEmpty())
    }
    @Test fun everyConfiguredAlarmFitsWithoutDroppingTheTail() {
        val queue = PendingAlarmQueue()
        assertTrue(queue.enqueue("TORAH", "MORNING"))
        assertTrue(queue.enqueue("KASHRUT", "MORNING"))
        for (n in 1..10) assertTrue(queue.enqueue("C$n", "MORNING"))
        assertFalse(queue.enqueue("TORAH", "MORNING"))
        assertEquals(listOf("TORAH", "KASHRUT") + (1..10).map { "C$it" },
            (1..12).map { queue.poll() })
        assertNull(queue.poll())
    }
    @Test fun queueCanBeClearedAfterServiceDestruction() {
        val queue = PendingAlarmQueue()
        queue.enqueue("TORAH", "MORNING")
        queue.clear()
        assertNull(queue.poll())
    }
}
