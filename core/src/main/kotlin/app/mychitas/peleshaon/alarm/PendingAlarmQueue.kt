package app.mychitas.peleshaon.alarm

/**
 * In-memory FIFO for alarms that fire simultaneously in one running service.
 * Never confuse a duplicate broadcast of the same alarm with a second event.
 */
class PendingAlarmQueue {
    private val values = ArrayDeque<String>()
    fun enqueue(type: String, currentlyRinging: String?): Boolean {
        if (type == currentlyRinging || values.contains(type)) return false
        values.addLast(type)
        return true
    }
    fun poll(): String? = if (values.isEmpty()) null else values.removeFirst()
    fun isEmpty(): Boolean = values.isEmpty()
    fun clear() = values.clear()
}
