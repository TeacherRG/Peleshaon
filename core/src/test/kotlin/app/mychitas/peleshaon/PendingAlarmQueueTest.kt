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
    @Test fun queueCanBeClearedAfterServiceDestruction() {
        val queue = PendingAlarmQueue()
        queue.enqueue("TORAH", "MORNING")
        queue.clear()
        assertNull(queue.poll())
    }
}
