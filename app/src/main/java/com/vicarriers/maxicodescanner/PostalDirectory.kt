package com.vicarriers.maxicodescanner

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Offline Canadian postal-code directory: GeoNames CA_full overlaid with
 * Statistics Canada NAR mailing municipalities (July 2025).
 */
class PostalDirectory(context: Context) {
    private val database: SQLiteDatabase

    init {
        val dest = File(context.filesDir, DB_NAME)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installed = prefs.getInt(PREF_VERSION, 0)
        if (installed != DB_VERSION || !dest.exists() || dest.length() == 0L) {
            if (dest.exists()) dest.delete()
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            prefs.edit().putInt(PREF_VERSION, DB_VERSION).apply()
        }
        database = SQLiteDatabase.openDatabase(
            dest.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    fun cityForPostal(postalCode: String?): String? {
        if (postalCode.isNullOrEmpty()) return null
        val key = postalCode.replace(" ", "").uppercase()
        database.rawQuery(
            "SELECT place_name FROM postal_codes WHERE postal_code = ? LIMIT 1",
            arrayOf(key),
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                PlaceNames.display(cursor.getString(0))
            } else {
                null
            }
        }
    }

    fun close() {
        if (database.isOpen) database.close()
    }

    companion object {
        private const val DB_NAME = "postal_codes.db"
        private const val DB_VERSION = 2
        private const val PREFS = "postal_directory"
        private const val PREF_VERSION = "db_version"
    }
}
