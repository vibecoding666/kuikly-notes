package com.example.kuiklynotes

import android.content.Context

/** Holds the application context for the platform storage implementation. */
object AppHolder {
    var context: Context? = null
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun storageSave(text: String) {
    val c = AppHolder.context ?: return
    c.getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .edit()
        .putString("notes_v1", text)
        .apply()
}

actual fun storageLoad(): String {
    val c = AppHolder.context ?: return ""
    return c.getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .getString("notes_v1", "") ?: ""
}
