package com.example.kuiklynotes

expect fun nowMillis(): Long

/** Small metadata blob (SharedPreferences). Never holds note bodies. */
expect fun storageSave(text: String)
expect fun storageLoad(): String

/** Large note bodies live in chunked files on the native side. */
expect fun bodyWrite(id: String, text: String)
expect fun bodyDelete(id: String)
expect fun bodyPreview(id: String, limit: Int): String
expect fun bodyCopy(srcId: String, dstId: String)

data class CheckItem(var text: String = "", var done: Boolean = false)

/**
 * Metadata only. The full body is NEVER held in common Kotlin code, so a
 * 500,000-character note never crosses the Kotlin <-> native bridge.
 */
data class Note(
    val id: String,
    var title: String = "",
    var summary: String = "",
    var length: Int = 0,
    var tags: MutableList<String> = mutableListOf(),
    var color: Int = 0,
    var pinned: Boolean = false,
    var starred: Boolean = false,
    var archived: Boolean = false,
    var deleted: Boolean = false,
    var checklist: MutableList<CheckItem> = mutableListOf(),
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var kind: Int = KIND_NOTE,
    var done: Boolean = false,
) {
    fun displayTitle(): String = if (title.isBlank()) "无标题" else title.trim()
    fun doneCount(): Int = checklist.count { it.done }

    companion object {
        const val KIND_NOTE = 0
        const val KIND_TODO = 1
    }
}

object NoteColors {
    val palette = longArrayOf(
        0xFFE8C86AL, 0xFF8FB8D8L, 0xFFA8CFA0L, 0xFFE8A9A9L,
        0xFFC4B0DCL, 0xFFD9D2C5L, 0xFFB8C4CCL, 0xFFE8C49AL,
    )
    val names = arrayOf("柠檬", "天蓝", "薄荷", "樱粉", "薰衣草", "素纸", "雾灰", "暖杏")

    fun normalized(index: Int): Int {
        val n = palette.size
        return ((index % n) + n) % n
    }
}

enum class SortMode(val label: String) {
    UPDATED("最近修改"), CREATED("创建时间"), TITLE("标题 A-Z"), PINNED("置顶优先"), COLOR("按颜色"),
}

enum class Folder(val label: String) {
    ALL("全部"), STARRED("收藏"), PINNED("置顶"), ARCHIVE("归档"), TRASH("回收站"),
}

data class NoteStats(
    val notes: Int,
    val todoOpen: Int,
    val todoDone: Int,
    val pinned: Int,
    val starred: Int,
    val archived: Int,
    val trashed: Int,
    val tagCount: Int,
)
