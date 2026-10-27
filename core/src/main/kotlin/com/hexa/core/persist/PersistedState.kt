package com.hexa.core.persist

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-memory state backed by a file.
 *
 * - Reads happen once, off the main thread ([load]).
 * - Updates are applied to memory immediately (the UI never waits for disk).
 * - Writes are debounced: a burst of changes (dragging a slider) produces one write.
 * - Nothing is written when the encoded content equals what is already on disk.
 */
class PersistedState<T>(
    private val file: AtomicTextFile,
    private val default: T,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val debounceMs: Long = 400,
) {
    private val _state = MutableStateFlow(default)
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val writeLock = Mutex()
    private var lastWritten: String? = null
    private var pendingWrite: Job? = null

    /** Number of physical writes performed (exposed for tests and diagnostics). */
    @Volatile
    var writeCount: Int = 0
        private set

    /** Loads from disk; a corrupt file is quarantined and defaults are used. Returns true if a file was read. */
    suspend fun load(): Boolean {
        val result = withContext(io) {
            val text = file.readOrNull() ?: return@withContext null
            try {
                decode(text) to text
            } catch (e: Exception) {
                file.quarantine()
                null
            }
        }
        if (result != null) {
            lastWritten = result.second
            _state.value = result.first
        }
        _loaded.value = true
        return result != null
    }

    fun update(transform: (T) -> T) {
        val before = _state.value
        _state.update(transform)
        if (_state.value != before) scheduleWrite()
    }

    fun set(value: T) = update { value }

    private fun scheduleWrite() {
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(debounceMs)
            writeNow()
        }
    }

    /** Writes immediately if there are unsaved changes (call when the app goes to background). */
    suspend fun flush() {
        pendingWrite?.cancel()
        pendingWrite = null
        writeNow()
    }

    private suspend fun writeNow() {
        val snapshot = _state.value
        writeLock.withLock {
            withContext(io) {
                val text = encode(snapshot)
                if (text == lastWritten) return@withContext
                file.write(text)
                lastWritten = text
                writeCount++
            }
        }
    }
}
