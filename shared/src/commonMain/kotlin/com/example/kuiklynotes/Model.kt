package com.example.kuiklynotes

/** Platform clock. Implemented per target. */
expect fun nowMillis(): Long

/** Persistent key/value store used by the note repository. */
expect fun storageSave(text: String)
expect fun storageLoad(): String

data class CheckItem(var text: String = "", var done: Boolean = false)

data class Note(
    val id: String,
    var title: String = "",
    var content: String = "",
    var tags: MutableList<String> = mutableListOf(),
    var color: Int = 0,
    var pinned: Boolean = false,
    var starred: Boolean = false,
    var archived: Boolean = false,
    var deleted: Boolean = false,
    var checklist: MutableList<CheckItem> = mutableListOf(),
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var reminderAt: Long = 0L,
) {
    /** First non-empty line of the content, used as a card preview. */
    fun preview(limit: Int = 60): String {
        val flat = content.replace('\n', ' ').trim()
        return if (flat.length <= limit) flat else flat.substring(0, limit) + "…"
    }

    fun displayTitle(): String = if (title.isBlank()) "无标题便签" else title.trim()

    fun doneCount(): Int = checklist.count { it.done }
}

/** ARGB colors (0xAARRGGBB) shared between the model and the UI layer. */
object NoteColors {
    val palette = longArrayOf(
        0xFFFFF8E1L, 0xFFE3F2FDL, 0xFFE8F5E9L, 0xFFFCE4ECL,
        0xFFF3E5F5L, 0xFFFFFFFFL, 0xFFECEFF1L, 0xFFFFF3E0L,
    )
    val names = arrayOf("柠檬", "天蓝", "薄荷", "樱粉", "薰衣草", "纯白", "雾灰", "暖杏")

    fun normalized(index: Int): Int {
        val n = palette.size
        return ((index % n) + n) % n
    }
}

enum class SortMode(val label: String) {
    UPDATED("最近修改"),
    CREATED("创建时间"),
    TITLE("标题 A-Z"),
    PINNED("置顶优先"),
    COLOR("按颜色"),
}

enum class Folder(val label: String) {
    ALL("全部便签"),
    STARRED("收藏"),
    PINNED("置顶"),
    ARCHIVE("归档"),
    TRASH("回收站"),
}

data class NoteStats(
    val total: Int,
    val active: Int,
    val pinned: Int,
    val starred: Int,
    val archived: Int,
    val trashed: Int,
    val tagCount: Int,
    val checkDone: Int,
    val checkTotal: Int,
)
