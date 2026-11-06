package app.overbay.allnotes.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Multiplatform factory for creating platform-specific SQLite [SqlDriver] instances.
 *
 * Implemented via expect/actual:
 * - androidMain: AndroidSqliteDriver backed by Android SQLiteOpenHelper
 * - desktopMain: JdbcSqliteDriver backed by Xerial SQLite JDBC (x86_64 & ARM64)
 * - wasmJsMain: Strategy stub (WasmJs uses localStorage via PlatformNoteStore)
 */
expect class DatabaseDriverFactory() {
    fun createDriver(): SqlDriver
}
