package app.overbay.allnotes.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase

/**
 * Android implementation of [DatabaseDriverFactory].
 *
 * Backed by [AndroidSqliteDriver]. Retrieves the application [Context] from
 * [AndroidStoragePaths.context] initialized during [MainActivity.onCreate], or
 * from an optional explicit constructor parameter.
 */
actual class DatabaseDriverFactory(
    private val context: Context? = AndroidStoragePaths.context
) {
    actual constructor() : this(AndroidStoragePaths.context)

    actual fun createDriver(): SqlDriver {
        val appContext = context ?: AndroidStoragePaths.context
            ?: throw IllegalStateException(
                "AndroidStoragePaths.context has not been initialized. " +
                "Please assign AndroidStoragePaths.context = applicationContext in MainActivity.onCreate " +
                "before instantiating DatabaseDriverFactory."
            )

        return AndroidSqliteDriver(
            schema = AllNotesDatabase.Schema,
            context = appContext,
            name = DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(AllNotesDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.enableWriteAheadLogging()
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
    }

    companion object {
        const val DATABASE_NAME = "allnotes.db"
    }
}
