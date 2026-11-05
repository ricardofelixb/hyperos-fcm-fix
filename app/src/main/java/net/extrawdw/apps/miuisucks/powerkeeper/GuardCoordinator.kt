package net.extrawdw.apps.miuisucks.powerkeeper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns bursts of events into single passes. A burst is waited out once with a debounce (settings
 * writes and package broadcasts arrive in clusters) and then run; nothing is scheduled to repeat.
 * Work that could not complete, such as when Shizuku is not ready, stays pending until the next
 * event submits more.
 */
class GuardCoordinator(
    scope: CoroutineScope,
    private val debounceMillis: Long,
    private val maxDelayMillis: Long,
    private val run: suspend (GuardWork) -> Boolean,
) {
    private val lock = Any()
    private var pending = GuardWork.NONE
    private var submissions = 0L
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in wake) {
                settle()
                val work = take()
                if (work.isEmpty) continue
                if (!runCatching { run(work) }.getOrDefault(false)) restore(work)
            }
        }
    }

    fun submit(work: GuardWork) {
        synchronized(lock) {
            pending += work
            submissions++
        }
        wake.trySend(Unit)
    }

    private suspend fun settle() {
        var waited = 0L
        while (waited < maxDelayMillis) {
            val seen = synchronized(lock) { submissions }
            delay(debounceMillis)
            waited += debounceMillis
            if (synchronized(lock) { submissions } == seen) return
        }
    }

    private fun take(): GuardWork = synchronized(lock) { pending.also { pending = GuardWork.NONE } }

    private fun restore(work: GuardWork) {
        synchronized(lock) { pending = work + pending }
    }
}
