package app.overbay.allnotes.data

/**
 * Returns the current UTC wall-clock time in milliseconds since Unix epoch.
 */
expect fun currentEpochMillis(): Long

/**
 * Thread-safe critical section across JVM/Android multi-threaded runtimes and single-threaded WasmJs.
 */
expect fun <T> platformSynchronized(lock: Any, block: () -> T): T
