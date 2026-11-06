package app.overbay.allnotes.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase

actual class DatabaseDriverFactory(
    private val appContext: Context? = null
) {
    actual constructor() : this(null)

    actual fun createDriver(): SqlDriver {
        val ctx = appContext
        if (ctx != null) {
            return createAndroidDriver(ctx.applicationContext)
        }
        val factory = AndroidStoragePaths.driverFactory
            ?: throw IllegalStateException(
                "AndroidStoragePaths.driverFactory has not been initialized. " +
                    "Configure AndroidStoragePaths.driverFactory in MainActivity.onCreate."
            )
        return factory()
    }

    companion object {
        const val DATABASE_NAME = "allnotes.db"

        fun createAndroidDriver(appContext: Context): SqlDriver {
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
    }
}
