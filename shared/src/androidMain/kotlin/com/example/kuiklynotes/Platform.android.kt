package com.example.kuiklynotes

import android.content.Context

/** Holds the application context for the platform storage implementation. */
object AppHolder {
    lateinit var context: Context
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun storageSave(text: String) {
    if (!AppHolder::context.isInitialized) return
    AppHolder.context
        .getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .edit()
        .putString("notes_v1", text)
        .apply()
}

actual fun storageLoad(): String {
    if (!AppHolder::context.isInitialized) return ""
    return AppHolder.context
        .getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .getString("notes_v1", "") ?: ""
}
