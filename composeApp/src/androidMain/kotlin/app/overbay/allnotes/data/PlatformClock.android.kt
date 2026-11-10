package app.overbay.allnotes.data

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

actual fun <T> platformSynchronized(lock: Any, block: () -> T): T = kotlin.synchronized(lock, block)
