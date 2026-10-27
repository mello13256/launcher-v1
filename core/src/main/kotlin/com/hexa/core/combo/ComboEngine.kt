package com.hexa.core.combo

import com.hexa.core.model.ComboConfig
import com.hexa.core.model.ComboInput
import com.hexa.core.model.ComboSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of an interaction, delivered exactly once per gesture/sequence. */
sealed interface Resolution {
    /** Uptime (ms) of the touch event that completed the interaction. */
    val eventTime: Long

    /** Uptime (ms) at which the engine resolved it. */
    val resolvedAt: Long

    /** True when the engine had to wait for the combination window before resolving. */
    val waited: Boolean

    data class Single(
        val hive: Int,
        override val eventTime: Long,
        override val resolvedAt: Long,
        override val waited: Boolean,
    ) : Resolution

    data class Combo(
        val combo: ComboConfig,
        val sequence: List<Int>,
        override val eventTime: Long,
        override val resolvedAt: Long,
        override val waited: Boolean,
        val viaSwipe: Boolean,
    ) : Resolution

    data class Invalid(
        val sequence: List<Int>,
        val reason: Reason,
        override val eventTime: Long,
        override val resolvedAt: Long,
        override val waited: Boolean,
    ) : Resolution {
        enum class Reason {
            /** Taps formed the start of a combination but the window expired. */
            INCOMPLETE,

            /** The taps cannot belong to any combination. */
            NO_MATCH,

            /** A pending sequence was abandoned (swipe, edit mode, leaving the screen...). */
            CANCELLED,
        }
    }
}

/**
 * Recognises single taps, tap sequences and swipe paths over hives.
 *
 * Rules, in order:
 * 1. A tap on a hive that cannot start a tap combination resolves to [Resolution.Single]
 *    immediately: no added latency. Only hives that can begin a combination ever wait.
 * 2. A tap sequence that exactly matches a combination and cannot grow into a longer one resolves
 *    immediately, without waiting for the window.
 * 3. Otherwise the engine waits up to `windowMs` after the last tap (measured from the touch
 *    event time). If nothing arrives: an exact match fires, a lone tap fires as Single, anything
 *    else is INCOMPLETE and does nothing.
 * 4. A tap that cannot continue the pending sequence makes it NO_MATCH: nothing opens, so a
 *    mistyped combination never opens an unrelated app.
 * 5. Swipe paths (finger slides across hives) resolve on release with no waiting at all.
 *
 * A pending single action is always cancelled when a combination wins, so a combination never
 * opens the single-hive app as well. Must be driven from a single thread (the main thread).
 */
class ComboEngine(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onResolved: (Resolution) -> Unit,
) {
    private var matcher = ComboMatcher(emptyList())
    private var settings = ComboSettings()

    private val buffer = ArrayList<Int>(ComboMatcher.MAX_LENGTH)
    private var lastEventTime = 0L
    private var timer: Job? = null

    private val _pending = MutableStateFlow<List<Int>>(emptyList())

    /** Hives of the sequence currently waiting for more taps (for UI highlighting). */
    val pending: StateFlow<List<Int>> = _pending.asStateFlow()

    val windowMs: Long get() = settings.windowMs.coerceIn(ComboSettings.MIN_WINDOW_MS, ComboSettings.MAX_WINDOW_MS).toLong()

    private val tapsEnabled get() = settings.input != ComboInput.SWIPE
    private val swipeEnabled get() = settings.input != ComboInput.TAPS

    fun configure(combos: List<ComboConfig>, settings: ComboSettings) {
        this.settings = settings
        matcher = ComboMatcher(combos)
        if (buffer.isNotEmpty()) cancelPending()
    }

    /** True when a tap on [hive] (with nothing pending) waits for the window instead of firing at once. */
    fun mayWait(hive: Int): Boolean = tapsEnabled && matcher.canStart(hive)

    fun onTap(hive: Int, eventTime: Long) {
        if (buffer.isNotEmpty() && eventTime - lastEventTime > windowMs) {
            // The window has already expired but the timer has not run yet (busy main thread).
            resolveTimeout()
        }
        if (buffer.isEmpty() && !mayWait(hive)) {
            emit(Resolution.Single(hive, eventTime, clock(), waited = false))
            return
        }
        buffer += hive
        lastEventTime = eventTime
        val exact = matcher.exact(buffer)
        val extendable = buffer.size < ComboMatcher.MAX_LENGTH && matcher.canExtend(buffer)
        when {
            exact != null && !extendable -> {
                val seq = takeBuffer()
                emit(Resolution.Combo(exact, seq, eventTime, clock(), waited = false, viaSwipe = false))
            }
            exact != null || extendable -> scheduleTimeout()
            buffer.size == 1 -> {
                // Unreachable in practice (participating hives are extendable) but keep it safe.
                val seq = takeBuffer()
                emit(Resolution.Single(seq.first(), eventTime, clock(), waited = false))
            }
            else -> {
                val seq = takeBuffer()
                emit(Resolution.Invalid(seq, Resolution.Invalid.Reason.NO_MATCH, eventTime, clock(), waited = false))
            }
        }
    }

    /**
     * A gesture that slid across several hives. [path] is the visit order; consecutive repeats are
     * collapsed. A one-hive path is handled as a tap.
     */
    fun onSwipe(path: List<Int>, eventTime: Long) {
        val compact = ArrayList<Int>(path.size)
        for (h in path) if (compact.lastOrNull() != h) compact += h
        if (compact.isEmpty()) return
        if (compact.size == 1) {
            onTap(compact.single(), eventTime)
            return
        }
        // With swipe combinations disabled, sliding off a hive cancels the touch like a normal button.
        if (!swipeEnabled) return
        if (buffer.isNotEmpty()) cancelPending()
        val match = if (compact.size <= ComboMatcher.MAX_LENGTH) matcher.exact(compact) else null
        if (match != null) {
            emit(Resolution.Combo(match, compact, eventTime, clock(), waited = false, viaSwipe = true))
        } else {
            emit(Resolution.Invalid(compact, Resolution.Invalid.Reason.NO_MATCH, eventTime, clock(), waited = false))
        }
    }

    /** Drops a pending sequence without running anything (e.g. the launcher went to background). */
    fun cancelPending() {
        if (buffer.isEmpty()) return
        val seq = takeBuffer()
        emit(Resolution.Invalid(seq, Resolution.Invalid.Reason.CANCELLED, lastEventTime, clock(), waited = false))
    }

    private fun scheduleTimeout() {
        timer?.cancel()
        _pending.value = buffer.toList()
        val remaining = (lastEventTime + windowMs - clock()).coerceAtLeast(0L)
        timer = scope.launch {
            delay(remaining)
            timer = null
            resolveTimeout()
        }
    }

    private fun resolveTimeout() {
        if (buffer.isEmpty()) return
        val eventTime = lastEventTime
        val seq = takeBuffer()
        val exact = matcher.exact(seq)
        val now = clock()
        when {
            exact != null -> emit(Resolution.Combo(exact, seq, eventTime, now, waited = true, viaSwipe = false))
            seq.size == 1 -> emit(Resolution.Single(seq.first(), eventTime, now, waited = true))
            else -> emit(Resolution.Invalid(seq, Resolution.Invalid.Reason.INCOMPLETE, eventTime, now, waited = true))
        }
    }

    private fun takeBuffer(): List<Int> {
        timer?.cancel()
        timer = null
        val seq = buffer.toList()
        buffer.clear()
        _pending.value = emptyList()
        return seq
    }

    private fun emit(resolution: Resolution) = onResolved(resolution)
}
