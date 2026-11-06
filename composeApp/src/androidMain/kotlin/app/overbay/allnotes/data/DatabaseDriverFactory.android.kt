package app.overbay.allnotes.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase

actual class DatabaseDriverFactory(
    private val context: Context? = AndroidStoragePaths.context
) {
    actual constructor() : this(AndroidStoragePaths.context)

    actual fun createDriver(): SqlDriver {
        val appContext = context ?: AndroidStoragePaths.context
            ?: throw IllegalStateException(
                "AndroidStoragePaths.context has not been initialized. " +
                    "Assign AndroidStoragePaths.context = applicationContext in MainActivity.onCreate."
            )

        return AndroidSqliteDriver(
            schema = AllNotesDatabase.Schema,
            context = appContext,
            name = DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(AllNotesDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    try {
                        db.setForeignKeyConstraintsEnabled(true)
                        db.enableWriteAheadLogging()
                    } catch (_: Exception) {
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    try {
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    } catch (_: Exception) {
                    }
                }
            }
        )
    }

    companion object {
        const val DATABASE_NAME = "allnotes.db"
    }
}
