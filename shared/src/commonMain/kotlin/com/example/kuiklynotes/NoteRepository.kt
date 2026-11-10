package com.example.kuiklynotes

/** Metadata repository. Full bodies stay in native chunk files. */
class NoteRepository {

    private val notes = mutableListOf<Note>()
    private var seq = 0

    fun load() {
        notes.clear()
        val raw = storageLoad()
        if (raw.isNotBlank()) {
            runCatching { notes.addAll(JsonCodec.decode(raw)) }
            return
        }
        // Only seed on a genuinely fresh install. An empty "[]" means the user
        // deleted every note and must NOT be repopulated with samples.
        seed()
    }

    fun save() { storageSave(JsonCodec.encode(notes)) }

    private fun nextId(): String {
        seq += 1
        return nowMillis().toString(36) + "-" + seq.toString(36)
    }

    private fun seedNote(
        title: String, body: String, tags: List<String>, color: Int,
        kind: Int, pinned: Boolean = false, starred: Boolean = false,
        ago: Long,
    ) {
        val t = nowMillis() - ago
        val id = nextId()
        bodyWrite(id, body)
        notes.add(
            Note(
                id = id, title = title, summary = body.replace('\n', ' ').take(80),
                length = body.length, tags = tags.toMutableList(), color = color,
                pinned = pinned, starred = starred, createdAt = t, updatedAt = t,
                kind = kind,
            )
        )
    }

    private fun seed() {
        seedNote(
            "欢迎使用轻记",
            "这是一份用腾讯 Kuikly 写的便签。\n支持置顶、收藏、标签、颜色、归档、回收站、搜索、排序与清单。\n正文由原生编辑器承载，可写到 50 万字以上而不卡顿。",
            listOf("指南", "Kuikly"), 0, Note.KIND_NOTE, pinned = true, ago = 7_200_000L,
        )
        seedNote(
            "读书笔记",
            "跨端框架的取舍：自绘换一致性，原生渲染换体积与手感。",
            listOf("技术"), 1, Note.KIND_NOTE, starred = true, ago = 10_800_000L,
        )
        seedNote(
            "周末计划",
            "去公园散步，顺便把阳台的花换盆，晚上看一部电影。",
            listOf("生活"), 3, Note.KIND_NOTE, ago = 86_400_000L,
        )
        seedNote(
            "项目想法",
            "做一个只有一屏的小工具，把每天记的东西自动按标签归类。",
            listOf("灵感"), 4, Note.KIND_NOTE, ago = 172_800_000L,
        )
        seedNote("整理本周待办", "", listOf("生活"), 2, Note.KIND_TODO, ago = 3_600_000L)
        seedNote("回复设计稿邮件", "", listOf("工作"), 1, Note.KIND_TODO, ago = 7_200_000L)
        seedNote("买咖啡豆", "", listOf("生活"), 2, Note.KIND_TODO, ago = 86_400_000L)
        seedNote("更新简历", "", listOf("工作"), 0, Note.KIND_TODO, ago = 172_800_000L)
            .also { /* marked done below */ }
        notes.last().done = true
        seedNote("备份照片", "", listOf("生活"), 5, Note.KIND_TODO, ago = 259_200_000L)
        notes.last().done = true
        save()
    }

    // ---------- CRUD (metadata only) ----------

    fun all(): List<Note> = notes.toList()
    fun find(id: String): Note? = notes.firstOrNull { it.id == id }

    fun create(kind: Int): Note {
        val t = nowMillis()
        val n = Note(id = nextId(), createdAt = t, updatedAt = t, kind = kind)
        bodyWrite(n.id, "")
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

    /** Called by the native editor with ONLY the length, never the text. */
    fun updateBodyMeta(id: String, length: Int, editedAt: Long) {
        find(id)?.let {
            it.length = length
            it.updatedAt = editedAt
            it.summary = bodyPreview(id, 80).replace('\n', ' ')
            save()
        }
    }

    /** Creates a note whose body is ~[chars] characters, for stress testing. */
    fun createStressNote(chars: Int): Note {
        val t = nowMillis()
        val n = Note(
            id = nextId(), title = "压测样张 $chars 字", createdAt = t, updatedAt = t,
            kind = Note.KIND_NOTE, length = chars,
        )
        bodyWriteSample(n.id, chars)
        notes.add(0, n)
        save()
        return n
    }

    fun duplicate(id: String): Note? {
        val src = find(id) ?: return null
        val copy = src.copy(
            id = nextId(), title = src.title + " (副本)",
            tags = src.tags.toMutableList(),
            checklist = src.checklist.map { it.copy() }.toMutableList(),
            createdAt = nowMillis(), updatedAt = nowMillis(),
            pinned = false, deleted = false, archived = false, done = false,
        )
        bodyCopy(id, copy.id)
        notes.add(0, copy)
        save()
        return copy
    }

    fun softDelete(id: String) { find(id)?.let { it.deleted = true; it.updatedAt = nowMillis() }; save() }
    fun restore(id: String) { find(id)?.let { it.deleted = false; it.updatedAt = nowMillis() }; save() }
    fun purge(id: String) { notes.removeAll { it.id == id }; bodyDelete(id); save() }
    fun emptyTrash() { notes.filter { it.deleted }.forEach { bodyDelete(it.id) }; notes.removeAll { it.deleted }; save() }
    fun togglePin(id: String) { find(id)?.let { it.pinned = !it.pinned; it.updatedAt = nowMillis() }; save() }
    fun toggleStar(id: String) { find(id)?.let { it.starred = !it.starred; it.updatedAt = nowMillis() }; save() }
    fun toggleArchive(id: String) { find(id)?.let { it.archived = !it.archived; it.updatedAt = nowMillis() }; save() }
    fun toggleDone(id: String) { find(id)?.let { it.done = !it.done; it.updatedAt = nowMillis() }; save() }
    fun setColor(id: String, color: Int) { find(id)?.let { it.color = NoteColors.normalized(color); it.updatedAt = nowMillis() }; save() }
    fun setTags(id: String, tags: List<String>) { find(id)?.let { it.tags = tags.toMutableList(); it.updatedAt = nowMillis() }; save() }

    // ---------- Query ----------

    fun visible(query: String, tab: Int, folder: Folder, tag: String, sort: SortMode): List<Note> {
        val q = query.trim().lowercase()
        val wantKind = if (tab == 0) Note.KIND_NOTE else Note.KIND_TODO
        var list = notes.filter { n ->
            n.kind == wantKind && when (folder) {
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
                n.title.lowercase().contains(q) || n.summary.lowercase().contains(q) ||
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
        notes.filter { !it.deleted }.forEach { n -> n.tags.forEach { t -> if (t.isNotBlank()) counts[t] = (counts[t] ?: 0) + 1 } }
        return counts.toList().sortedByDescending { it.second }
    }

    fun stats(): NoteStats = NoteStats(
        notes = notes.count { it.kind == Note.KIND_NOTE && !it.deleted && !it.archived },
        todoOpen = notes.count { it.kind == Note.KIND_TODO && !it.deleted && !it.done },
        todoDone = notes.count { it.kind == Note.KIND_TODO && !it.deleted && it.done },
        pinned = notes.count { it.pinned && !it.deleted },
        starred = notes.count { it.starred && !it.deleted },
        archived = notes.count { it.archived && !it.deleted },
        trashed = notes.count { it.deleted },
        tagCount = allTags().size,
    )

    fun exportJson(): String = JsonCodec.encode(notes)
    fun importJson(text: String): Boolean {
        val decoded = runCatching { JsonCodec.decode(text) }.getOrNull() ?: return false
        if (decoded.isEmpty()) return false
        notes.clear(); notes.addAll(decoded); save(); return true
    }

    companion object { const val ALL_TAG = "__all__" }
}
