package com.example.kuiklynotes

import android.content.Context
import java.io.File

object AppHolder {
    var context: Context? = null
}

/**
 * Chunked on-disk body store. A 500k-character note is split into 32K-char
 * parts so no single read/write allocates a megabyte-sized string on the UI
 * thread, and previews only touch the first chunk.
 */
object BodyFiles {
    private const val CHUNK = 32_768

    private fun dir(): File = File(AppHolder.context!!.filesDir, "bodies").also { it.mkdirs() }
    private fun meta(id: String) = File(dir(), "$id.meta")
    private fun part(id: String, i: Int) = File(dir(), "${id}_$i.txt")

    fun chunkCount(id: String): Int =
        runCatching { meta(id).readText(Charsets.UTF_8).trim().toInt() }.getOrDefault(0)

    fun readChunk(id: String, i: Int): String =
        runCatching { part(id, i).readText(Charsets.UTF_8) }.getOrDefault("")

    fun readAll(id: String): String {
        val sb = StringBuilder()
        repeat(chunkCount(id)) { sb.append(readChunk(id, it)) }
        return sb.toString()
    }

    fun readPrefix(id: String, limit: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (sb.length < limit && i < chunkCount(id)) {
            sb.append(readChunk(id, i)); i++
        }
        return sb.toString().take(limit)
    }

    fun write(id: String, text: String) {
        val n = if (text.isEmpty()) 0 else (text.length + CHUNK - 1) / CHUNK
        for (i in 0 until n) {
            val end = minOf((i + 1) * CHUNK, text.length)
            part(id, i).writeText(text.substring(i * CHUNK, end), Charsets.UTF_8)
        }
        var stale = n
        while (part(id, stale).exists()) { part(id, stale).delete(); stale++ }
        meta(id).writeText(n.toString(), Charsets.UTF_8)
    }

    fun copy(src: String, dst: String) {
        clear(dst)
        val n = chunkCount(src)
        for (i in 0 until n) part(dst, i).writeText(readChunk(src, i), Charsets.UTF_8)
        meta(dst).writeText(n.toString(), Charsets.UTF_8)
    }

    fun clear(id: String) {
        var i = 0
        while (part(id, i).exists()) { part(id, i).delete(); i++ }
        meta(id).delete()
    }
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun storageSave(text: String) {
    val c = AppHolder.context ?: return
    c.getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .edit().putString("notes_v1", text).apply()
}

actual fun storageLoad(): String {
    val c = AppHolder.context ?: return ""
    return c.getSharedPreferences("kuikly_notes", Context.MODE_PRIVATE)
        .getString("notes_v1", "") ?: ""
}

actual fun bodyWrite(id: String, text: String) { BodyFiles.write(id, text) }
actual fun bodyDelete(id: String) { BodyFiles.clear(id) }
actual fun bodyPreview(id: String, limit: Int): String = BodyFiles.readPrefix(id, limit)
actual fun bodyCopy(srcId: String, dstId: String) { BodyFiles.copy(srcId, dstId) }
