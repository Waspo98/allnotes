package app.overbay.allnotes.data

import app.cash.sqldelight.db.SqlDriver

/**
 * WasmJs driver factory stub.
 *
 * In SQLDelight 2.1.0, SQLite drivers are not published for Kotlin/WasmJs.
 * [PlatformNoteStore] on WasmJs persists directly to browser localStorage.
 */
actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException(
            "SQLDelight SQLite driver is not available for WasmJs. " +
            "PlatformNoteStore on WasmJs persists via browser localStorage."
        )
    }
}
