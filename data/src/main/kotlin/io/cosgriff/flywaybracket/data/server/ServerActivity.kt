package io.cosgriff.flywaybracket.data.server

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the app is most likely waiting on the server to wake (#93). The hosted server sleeps after some
 * minutes with no requests, and the first request after that waits several seconds while it starts
 * (`docs/server/hosting.md`). [waking] is true while any call to it has waited longer than [slowAfterMillis],
 * or is on its retry; the screens that wait on the server then say so.
 *
 * One for the app, shared by every [ServerClient] call. It holds no request and nothing about the player.
 */
class ServerActivity(private val slowAfterMillis: Long = SLOW_AFTER_MILLIS) {
    private val lock = Any()
    private var slowCalls = 0
    private val mutableWaking = MutableStateFlow(false)

    /** True while a call has waited longer than [slowAfterMillis] or is being tried again. */
    val waking: StateFlow<Boolean> = mutableWaking.asStateFlow()

    /** Runs one call, [block], counted as waking once it has run [slowAfterMillis] or says it is [Call.slow]. */
    internal suspend fun <T> follow(block: suspend (Call) -> T): T = coroutineScope {
        val call = Call()
        call.timer = launch {
            delay(slowAfterMillis)
            call.slow()
        }
        try {
            block(call)
        } finally {
            call.done()
        }
    }

    /** One call being followed. */
    internal inner class Call {
        internal var timer: Job? = null
        private var counted = false

        /** The call is being tried again, or has waited long: the server is most likely waking. */
        fun slow() = synchronized(lock) {
            if (!counted) {
                counted = true
                slowCalls++
                mutableWaking.value = true
            }
        }

        /** The call has its answer, or has failed, or the server has started sending a file: it waits no more. */
        fun done() {
            timer?.cancel()
            synchronized(lock) {
                if (counted) {
                    counted = false
                    slowCalls--
                    mutableWaking.value = slowCalls > 0
                }
            }
        }
    }

    companion object {
        /** About how long an awake server takes at most; a call waiting longer is most likely waiting on a start. */
        const val SLOW_AFTER_MILLIS = 2_000L
    }
}
