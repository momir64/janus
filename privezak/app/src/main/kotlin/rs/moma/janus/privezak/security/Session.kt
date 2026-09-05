package rs.moma.janus.privezak.security

import android.os.SystemClock

enum class SessionTimeout(private val label: String, val millis: Long) {
    Immediately("Immediately", 0),
    OneMinute("1 minute", 60_000L),
    FiveMinutes("5 minutes", 5 * 60_000L),
    FifteenMinutes("15 minutes", 15 * 60_000L),
    ThirtyMinutes("30 minutes", 30 * 60_000L),
    OneHour("1 hour", 60 * 60_000L),
    FourHours("4 hours", 4 * 60 * 60_000L),
    Never("Never", Long.MAX_VALUE);

    override fun toString() = label
}

object Session {
    private var key: ByteArray? = null
    private var expiresAt = 0L

    @Volatile
    var isVisible = false

    @Synchronized
    fun start(dataKey: ByteArray, timeout: SessionTimeout) {
        clear()
        if (timeout == SessionTimeout.Immediately) return
        key = dataKey.copyOf()
        expiresAt = if (timeout == SessionTimeout.Never) Long.MAX_VALUE
        else SystemClock.elapsedRealtime() + timeout.millis
    }

    @Synchronized
    fun key(): ByteArray? {
        if (key != null && SystemClock.elapsedRealtime() >= expiresAt) clear()
        return key
    }

    @Synchronized
    fun clear() {
        key?.fill(0)
        key = null
        expiresAt = 0
    }
}
