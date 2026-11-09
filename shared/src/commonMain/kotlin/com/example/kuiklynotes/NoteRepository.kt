package com.example.kuiklynotes

/**
 * All note business logic lives here as plain Kotlin so it is independent of
 * any UI framework and can be unit tested on any target.
 */
class NoteRepository {

    private val notes = mutableListOf<Note>()
    private var seq = 0

    fun load() {
        notes.clear()
        val raw = storageLoad()
        if (raw.isNotBlank()) {
            runCatching { notes.addAll(JsonCodec.decode(raw)) }
        }
        if (notes.isEmpty()) seed()
    }

    fun save() {
        storageSave(JsonCodec.encode(notes))
    }

    private fun nextId(): String {
        seq += 1
        return nowMillis().toString(36) + "-" + seq.toString(36)
    }

    private fun seed() {
        val t = nowMillis()
        notes.add(
            Note(
                id = nextId(),
                title = "欢迎使用轻记",
                content = "这是一份用腾讯 Kuikly 写的便签。\n支持置顶、收藏、标签、颜色、归档、回收站、搜索、排序与清单。\n所有数据都保存在本机。",
                tags = mutableListOf("指南", "Kuikly"),
                color = 0,
                pinned = true,
                createdAt = t - 7200000L,
                updatedAt = t - 7200000L,
            )
        )
        notes.add(
            Note(
                id = nextId(),
                title = "购物清单",
                content = "周末采购",
                tags = mutableListOf("生活"),
                color = 2,
                checklist = mutableListOf(
                    CheckItem("牛奶", true),
                    CheckItem("鸡蛋", false),
                    CheckItem("咖啡豆", false),
                ),
                createdAt = t - 3600000L,
                updatedAt = t - 3600000L,
            )
        )
        notes.add(
            Note(
                id = nextId(),
                title = "读书笔记",
                content = "跨端框架的取舍：自绘换一致性，原生渲染换体积与手感。",
                tags = mutableListOf("学习", "技术"),
                color = 1,
                starred = true,
                createdAt = t - 1800000L,
                updatedAt = t - 1800000L,
            )
        )
        save()
    }

    // ---------- CRUD ----------

    fun all(): List<Note> = notes.toList()

    fun find(id: String): Note? = notes.firstOrNull { it.id == id }

    fun create(): Note {
        val t = nowMillis()
        val n = Note(id = nextId(), createdAt = t, updatedAt = t)
        notes.add(0, n)
        save()
        return n
    }

    fun upsert(note: Note) {
        note.updatedAt = nowMillis()
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) notes[idx] = note else notes.add(0, note)
        save()
    }

    fun duplicate(id: String): Note? {
        val src = find(id) ?: return null
        val copy = src.copy(
            id = nextId(),
            title = src.title + " (副本)",
            tags = src.tags.toMutableList(),
            checklist = src.checklist.map { it.copy() }.toMutableList(),
            createdAt = nowMillis(),
            updatedAt = nowMillis(),
            pinned = false,
            deleted = false,
            archived = false,
        )
        notes.add(0, copy)
        save()
        return copy
    }

    fun softDelete(id: String) {
        find(id)?.let { it.deleted = true; it.updatedAt = nowMillis() }
        save()
    }

    fun restore(id: String) {
        find(id)?.let { it.deleted = false; it.updatedAt = nowMillis() }
        save()
    }

    fun purge(id: String) {
        notes.removeAll { it.id == id }
        save()
    }

    fun emptyTrash() {
        notes.removeAll { it.deleted }
        save()
    }

    fun togglePin(id: String) {
        find(id)?.let { it.pinned = !it.pinned; it.updatedAt = nowMillis() }
        save()
    }

    fun toggleStar(id: String) {
        find(id)?.let { it.starred = !it.starred; it.updatedAt = nowMillis() }
        save()
    }

    fun toggleArchive(id: String) {
        find(id)?.let { it.archived = !it.archived; it.updatedAt = nowMillis() }
        save()
    }

    fun setColor(id: String, color: Int) {
        find(id)?.let { it.color = NoteColors.normalized(color); it.updatedAt = nowMillis() }
        save()
    }

    fun toggleCheck(noteId: String, index: Int) {
        val n = find(noteId) ?: return
        if (index in n.checklist.indices) {
            n.checklist[index].done = !n.checklist[index].done
            n.updatedAt = nowMillis()
            save()
        }
    }

    fun addCheck(noteId: String, text: String) {
        val n = find(noteId) ?: return
        if (text.isBlank()) return
        n.checklist.add(CheckItem(text.trim()))
        n.updatedAt = nowMillis()
        save()
    }

    fun removeCheck(noteId: String, index: Int) {
        val n = find(noteId) ?: return
        if (index in n.checklist.indices) {
            n.checklist.removeAt(index)
            n.updatedAt = nowMillis()
            save()
        }
    }

    // ---------- Query ----------

    fun visible(query: String, folder: Folder, tag: String, sort: SortMode): List<Note> {
        val q = query.trim().lowercase()
        var list = notes.filter { n ->
            when (folder) {
                Folder.TRASH -> n.deleted
                Folder.ARCHIVE -> !n.deleted && n.archived
                Folder.STARRED -> !n.deleted && !n.archived && n.starred
                Folder.PINNED -> !n.deleted && !n.archived && n.pinned
                Folder.ALL -> !n.deleted && !n.archived
            }
        }
        if (tag.isNotBlank() && tag != ALL_TAG) {
            list = list.filter { n -> n.tags.any { it.equals(tag, ignoreCase = true) } }
        }
        if (q.isNotBlank()) {
            list = list.filter { n ->
                n.title.lowercase().contains(q) ||
                    n.content.lowercase().contains(q) ||
                    n.tags.any { it.lowercase().contains(q) }
            }
        }
        return when (sort) {
            SortMode.UPDATED -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            SortMode.CREATED -> list.sortedByDescending { it.createdAt }
            SortMode.TITLE -> list.sortedBy { it.displayTitle().lowercase() }
            SortMode.PINNED -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.starred }.thenByDescending { it.updatedAt })
            SortMode.COLOR -> list.sortedWith(compareBy<Note> { it.color }.thenByDescending { it.updatedAt })
        }
    }

    fun allTags(): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        notes.filter { !it.deleted }.forEach { n ->
            n.tags.forEach { t -> if (t.isNotBlank()) counts[t] = (counts[t] ?: 0) + 1 }
        }
        return counts.toList().sortedByDescending { it.second }
    }

    fun stats(): NoteStats {
        val alive = notes.filter { !it.deleted }
        val checks = alive.flatMap { it.checklist }
        return NoteStats(
            total = notes.size,
            active = alive.count { !it.archived },
            pinned = alive.count { it.pinned },
            starred = alive.count { it.starred },
            archived = alive.count { it.archived },
            trashed = notes.count { it.deleted },
            tagCount = allTags().size,
            checkDone = checks.count { it.done },
            checkTotal = checks.size,
        )
    }

    /** Export the whole database as readable JSON text. */
    fun exportJson(): String = JsonCodec.encode(notes)

    /** Replace the database with notes decoded from JSON text. */
    fun importJson(text: String): Boolean {
        val decoded = runCatching { JsonCodec.decode(text) }.getOrNull() ?: return false
        if (decoded.isEmpty()) return false
        notes.clear()
        notes.addAll(decoded)
        save()
        return true
    }

    companion object {
        const val ALL_TAG = "__all__"
    }
}
