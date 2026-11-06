package app.overbay.allnotes.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase
import java.io.File
import java.util.Properties

/**
 * Windows Desktop / JVM implementation of [DatabaseDriverFactory].
 *
 * Uses [JdbcSqliteDriver] backed by Xerial SQLite JDBC (3.45.1.0+).
 * Natively supports both Windows x86_64 and Windows ARM64 without emulation.
 * Persists database to `%APPDATA%/AllNotes/allnotes.db` on Windows (or user.home/AllNotes on POSIX).
 */
actual class DatabaseDriverFactory(
    private val dbFile: File? = null
) {
    actual constructor() : this(null)

    actual fun createDriver(): SqlDriver {
        val file = dbFile ?: resolveStorageDatabaseFile()
        val isNewDatabase = !file.exists() || file.length() == 0L
        val properties = Properties().apply {
            put("foreign_keys", "on")
            put("journal_mode", "WAL")
            put("synchronous", "NORMAL")
            put("busy_timeout", "5000")
        }
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${file.absolutePath}",
            properties = properties
        )

        if (isNewDatabase) {
            AllNotesDatabase.Schema.create(driver)
            // PRAGMAs for high-performance concurrent local storage & integrity
            driver.execute(null, "PRAGMA user_version = ${AllNotesDatabase.Schema.version};", 0)
        } else {
            // Existing database: check and execute migrations
            val currentVersion = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version;",
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                },
                parameters = 0
            ).value

            if (currentVersion < AllNotesDatabase.Schema.version) {
                AllNotesDatabase.Schema.migrate(driver, currentVersion, AllNotesDatabase.Schema.version)
                driver.execute(null, "PRAGMA user_version = ${AllNotesDatabase.Schema.version};", 0)
            }
        }

        return driver
    }

    companion object {
        const val DATABASE_NAME = "allnotes.db"

        fun resolveStorageDirectory(): File {
            val appData = System.getenv("APPDATA")
                ?: System.getProperty("user.home")
                ?: "."
            val dir = File(appData, "AllNotes")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun resolveStorageDatabaseFile(): File {
            return File(resolveStorageDirectory(), DATABASE_NAME)
        }
    }
}
