package tj.behruz.devicesignals.sdk.internal

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.Executors

/**
 * Remembers when a call was last observed in this process, so a collect can
 * report calls that ended shortly before it ran.
 */
internal object CallTracker {

    const val WINDOW_MS = 3 * 60 * 1000L

    @Volatile
    private var lastCallSeenAt: Long = 0L

    @Volatile
    private var listening = false

    /** On API 31+ listens for audio-mode changes so calls between collects are seen. */
    fun start(context: Context) {
        if (listening || Build.VERSION.SDK_INT < 31) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            am.addOnModeChangedListener(Executors.newSingleThreadExecutor()) { mode ->
                if (isCallMode(mode)) markCall()
            }
            listening = true
        } catch (_: Exception) { }
    }

    fun markCall() {
        lastCallSeenAt = SystemClock.elapsedRealtime()
    }

    fun seenWithinWindow(): Boolean {
        val last = lastCallSeenAt
        return last != 0L && SystemClock.elapsedRealtime() - last <= WINDOW_MS
    }

    fun isCallMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            (Build.VERSION.SDK_INT >= 30 && mode == AudioManager.MODE_CALL_SCREENING)
}
