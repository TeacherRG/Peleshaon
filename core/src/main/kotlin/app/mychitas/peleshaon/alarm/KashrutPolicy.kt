package app.mychitas.peleshaon.alarm

/** Fixed meat-to-dairy reminder policy; legacy variable timers must not survive migration. */
object KashrutPolicy {
    const val WAIT_HOURS = 6L
    const val POLICY_VERSION = 1
    const val WAIT_MILLIS = WAIT_HOURS * 3_600_000L

    fun deadline(startMillis: Long): Long = Math.addExact(startMillis, WAIT_MILLIS)
    fun resetLegacyTimer(version: Int, pendingDeadline: Long): Boolean =
        version < POLICY_VERSION && pendingDeadline > 0L
}
