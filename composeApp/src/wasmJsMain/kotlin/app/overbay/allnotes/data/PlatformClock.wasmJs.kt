@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.overbay.allnotes.data

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual fun currentEpochMillis(): Long = jsDateNow().toLong()

actual fun <T> platformSynchronized(lock: Any, block: () -> T): T = block()
