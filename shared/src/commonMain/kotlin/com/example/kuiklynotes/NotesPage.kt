package com.example.kuiklynotes

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

private val BG = Color(0xFFF2F4F7L)
private val CARD = Color(0xFFFFFFFFL)
private val INK = Color(0xFF1F2329L)
private val MUTED = Color(0xFF8A9099L)
private val ACCENT = Color(0xFF3C6CBDL)
private val DANGER = Color(0xFFE5484DL)
private val CHIP = Color(0xFFE9EDF3L)

/** A flat, immutable snapshot of a note for the reactive list. */
internal class NoteRow(
    val id: String,
    val title: String,
    val preview: String,
    val meta: String,
    val color: Long,
    val starred: Boolean,
    val trashed: Boolean,
    val archived: Boolean,
)

@Page("NotesPage")
internal class NotesPage : Pager() {

    private val repo = NoteRepository()

    private var folderIdx by observable(0)
    private var sortIdx by observable(0)
    private var tagFilter by observable(NoteRepository.ALL_TAG)
    private var rows: ObservableList<NoteRow> by observableList()
    private var overlay: ObservableList<String> by observableList()
    private var tagRows: ObservableList<String> by observableList()
    private var editChecks: ObservableList<String> by observableList()

    private var statsText by observable("")
    private var banner by observable("")
    private var exportText by observable("")

    private var query by observable("")
    private var editTitle by observable("")
    private var editTags by observable("")
    private var editContent by observable("")
    private var checkInput by observable("")
    private var importInput by observable("")
    private var editColor by observable(0)
    private var editPinned by observable(false)
    private var editStarred by observable(false)

    // Non-observable so that binding them to Input.text() does not create a
    // reactive dependency (which would move the cursor on every keystroke).
    private var editId: String = ""
    private var initialTitle: String = ""
    private var initialContent: String = ""
    private var initialTags: String = ""

    private val folderLabels: ObservableList<String> by observableList()
    private val sortLabels: ObservableList<String> by observableList()
    private val toolLabels: ObservableList<String> by observableList()
    private val colorLabels: ObservableList<String> by observableList()
    private val toggleLabels: ObservableList<String> by observableList()

    init {
        repo.load()
        listOf("全部", "收藏", "置顶", "归档", "回收站").forEach { folderLabels.add(it) }
        listOf("最近", "创建", "标题", "置顶", "颜色").forEach { sortLabels.add(it) }
        listOf("＋ 新建", "标签", "统计", "清空回收站").forEach { toolLabels.add(it) }
        listOf("柠檬", "天蓝", "薄荷", "樱粉", "薰衣草", "纯白", "雾灰", "暖杏").forEach { colorLabels.add(it) }
        listOf("置顶", "收藏", "归档", "复制").forEach { toggleLabels.add(it) }
        refresh()
    }

    // ---------- data ----------

    private fun currentFolder(): Folder = Folder.values()[folderIdx]
    private fun currentSort(): SortMode = SortMode.values()[sortIdx]

    private fun refresh() {
        val list = repo.visible(query, currentFolder(), tagFilter, currentSort())
        rows.clear()
        if (list.isEmpty()) {
            rows.add(NoteRow("__empty__", "这里还没有便签", "点右下角「＋ 新建」开始记录", "", 0xFFE9EDF3L, false, false, false))
        } else {
            list.forEach { rows.add(it.toRow()) }
        }
        val s = repo.stats()
        statsText = "${list.size} 条显示 · 共 ${s.total} 条 · 置顶 ${s.pinned} · 收藏 ${s.starred} · 归档 ${s.archived} · 回收站 ${s.trashed}"
        exportText = repo.exportJson()
    }

    private fun Note.toRow(): NoteRow = NoteRow(
        id = id,
        title = (if (pinned) "📌 " else "") + (if (archived) "📦 " else "") + displayTitle(),
        preview = if (checklist.isEmpty()) preview() else "☑ ${doneCount()}/${checklist.size}  " + preview(),
        meta = (if (tags.isEmpty()) "" else tags.joinToString("  #", prefix = "#") + "   ") + timeText(updatedAt),
        color = NoteColors.palette[NoteColors.normalized(color)],
        starred = starred,
        trashed = deleted,
        archived = archived,
    )

    private fun timeText(t: Long): String {
        if (t <= 0L) return ""
        val d = nowMillis() - t
        return when {
            d < 60_000L -> "刚刚"
            d < 3_600_000L -> "${d / 60_000L} 分钟前"
            d < 86_400_000L -> "${d / 3_600_000L} 小时前"
            else -> "${d / 86_400_000L} 天前"
        }
    }

    private fun say(text: String) { banner = text }

    // ---------- actions ----------

    private fun newNote() {
        val n = repo.create()
        openEdit(n.id)
    }

    private fun openEdit(id: String) {
        val n = repo.find(id) ?: return
        editId = id
        initialTitle = n.title
        initialContent = n.content
        initialTags = n.tags.joinToString(", ")
        editTitle = ""
        editContent = ""
        editTags = ""
        editColor = n.color
        editPinned = n.pinned
        editStarred = n.starred
        checkInput = ""
        syncChecks()
        setOverlay("edit")
    }

    private fun syncChecks() {
        val n = repo.find(editId) ?: return
        editChecks.clear()
        n.checklist.forEach { c -> editChecks.add((if (c.done) "☑ " else "☐ ") + c.text) }
        if (editChecks.isEmpty()) editChecks.add("（暂无待办，可在下方添加）")
    }

    private fun saveEdit() {
        val n = repo.find(editId) ?: return
        n.title = editTitle.ifBlank { initialTitle }
        n.content = editContent.ifBlank { initialContent }
        val tagSource = editTags.ifBlank { initialTags }
        n.tags = tagSource.split(',', '，', ' ', '\n')
            .map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        n.color = editColor
        n.pinned = editPinned
        n.starred = editStarred
        repo.upsert(n)
        closeOverlay()
        refresh()
        say("已保存")
    }

    private fun addCheck() {
        if (checkInput.isBlank()) return
        repo.addCheck(editId, checkInput)
        checkInput = ""
        syncChecks()
    }

    private fun setOverlay(name: String) {
        overlay.clear()
        overlay.add(name)
        if (name == "tags") {
            tagRows.clear()
            tagRows.add(NoteRepository.ALL_TAG)
            repo.allTags().forEach { tagRows.add(it.first) }
        }
        if (name == "stats") {
            exportText = repo.exportJson()
        }
    }

    private fun closeOverlay() { overlay.clear() }

    private fun setFolder(idx: Int) {
        folderIdx = idx
        tagFilter = NoteRepository.ALL_TAG
        closeOverlay()
        refresh()
    }

    // ---------- UI ----------

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(BG)
                flexDirectionColumn()
                autoDarkEnable(false)
            }

            // Header
            View {
                attr {
                    flexDirectionRow()
                    height(52f)
                    padding(left = 16f, right = 16f)
                    backgroundColor(CARD)
                    allCenter()
                }
                Text {
                    attr {
                        text("轻记 · Kuikly Notes")
                        fontSize(19f)
                        fontWeightBold()
                        color(INK)
                        flex(1f)
                    }
                }
                Text {
                    attr { text("本地保存"); fontSize(11f); color(MUTED) }
                }
            }

            // Search
            View {
                attr {
                    flexDirectionRow()
                    height(42f)
                    margin(left = 12f, right = 12f, top = 10f)
                    padding(left = 12f, right = 8f)
                    borderRadius(10f)
                    backgroundColor(CARD)
                    allCenter()
                }
                Input {
                    attr {
                        flex(1f)
                        height(36f)
                        placeholder("搜索标题 / 正文 / 标签")
                        fontSize(14f)
                        color(INK)
                    }
                    event {
                        textDidChange { ctx.query = it.text; ctx.refresh() }
                    }
                }
                View {
                    attr { size(30f, 30f); allCenter(); backgroundColor(CHIP); borderRadius(15f) }
                    event { click { ctx.query = ""; ctx.refresh() } }
                    Text { attr { text("×"); fontSize(16f); color(INK) } }
                }
            }

            // Stats line + banner
            Text {
                attr {
                    text(ctx.statsText)
                    fontSize(11f)
                    color(MUTED)
                    margin(left = 14f, right = 14f, top = 8f)
                }
            }
            Text {
                attr {
                    text(if (ctx.banner.isEmpty()) "" else "› " + ctx.banner)
                    fontSize(12f)
                    color(ACCENT)
                    margin(left = 14f, right = 14f, top = 2f)
                }
            }

            // Folder tabs
            View {
                attr {
                    flexDirectionRow()
                    margin(left = 10f, right = 10f, top = 8f)
                    height(34f)
                }
                vfor({ ctx.folderLabels }) { label ->
                    View {
                        attr {
                            flex(1f)
                            height(30f)
                            margin(left = 3f, right = 3f)
                            borderRadius(8f)
                            backgroundColor(if (ctx.folderLabels.indexOf(label) == ctx.folderIdx) ACCENT else CARD)
                            allCenter()
                        }
                        event { click { ctx.setFolder(ctx.folderLabels.indexOf(label)) } }
                        Text {
                            attr {
                                text(label)
                                fontSize(11f)
                                color(if (ctx.folderLabels.indexOf(label) == ctx.folderIdx) CARD else INK)
                            }
                        }
                    }
                }
            }

            // Sort row
            View {
                attr {
                    flexDirectionRow()
                    margin(left = 10f, right = 10f, top = 4f)
                    height(30f)
                }
                vfor({ ctx.sortLabels }) { label ->
                    View {
                        attr {
                            flex(1f)
                            height(26f)
                            margin(left = 3f, right = 3f)
                            borderRadius(6f)
                            backgroundColor(if (ctx.sortLabels.indexOf(label) == ctx.sortIdx) CHIP else CARD)
                            allCenter()
                        }
                        event { click { ctx.sortIdx = ctx.sortLabels.indexOf(label); ctx.refresh() } }
                        Text { attr { text(label); fontSize(10f); color(INK) } }
                    }
                }
            }

            // Note list
            List {
                attr { flex(1f) }
                vfor({ ctx.rows }) { row ->
                    if (row.id == "__empty__") {
                        View {
                            attr {
                                margin(left = 16f, right = 16f, top = 60f)
                                padding(24f)
                                borderRadius(14f)
                                backgroundColor(CARD)
                                allCenter()
                            }
                            Text { attr { text(row.title); fontSize(16f); color(INK) } }
                            Text { attr { text(row.preview); fontSize(12f); color(MUTED); marginTop(8f) } }
                        }
                    } else {
                        View {
                            attr {
                                flexDirectionRow()
                                margin(left = 12f, right = 12f, top = 6f, bottom = 6f)
                                padding(12f)
                                borderRadius(12f)
                                backgroundColor(Color(row.color))
                                allCenter()
                            }
                            event { click { if (!row.trashed) ctx.openEdit(row.id) } }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text {
                                    attr {
                                        text(row.title); fontSize(15f); fontWeightBold(); color(INK)
                                    }
                                }
                                Text {
                                    attr {
                                        text(row.preview); fontSize(12f); color(INK)
                                        marginTop(4f)
                                    }
                                }
                                Text {
                                    attr {
                                        text(row.meta); fontSize(10f); color(MUTED); marginTop(6f)
                                    }
                                }
                            }
                            View {
                                attr {
                                    size(34f, 34f); marginLeft(8f); borderRadius(17f)
                                    backgroundColor(CARD); allCenter()
                                }
                                event {
                                    click {
                                        if (row.trashed) {
                                            ctx.repo.restore(row.id); ctx.refresh(); ctx.say("已恢复")
                                        } else {
                                            ctx.repo.toggleStar(row.id); ctx.refresh()
                                        }
                                    }
                                }
                                Text {
                                    attr {
                                        text(if (row.trashed) "↺" else if (row.starred) "★" else "☆")
                                        fontSize(17f)
                                        color(if (row.trashed) ACCENT else Color(0xFFFFB300L))
                                    }
                                }
                            }
                            View {
                                attr {
                                    size(34f, 34f); marginLeft(6f); borderRadius(17f)
                                    backgroundColor(CARD); allCenter()
                                }
                                event {
                                    click {
                                        if (row.trashed) {
                                            ctx.repo.purge(row.id); ctx.refresh(); ctx.say("已彻底删除")
                                        } else {
                                            ctx.repo.softDelete(row.id); ctx.refresh(); ctx.say("已移入回收站")
                                        }
                                    }
                                }
                                Text { attr { text("✕"); fontSize(14f); color(DANGER) } }
                            }
                        }
                    }
                }
            }

            // Bottom toolbar
            View {
                attr {
                    flexDirectionRow()
                    height(52f)
                    backgroundColor(CARD)
                    marginTop(4f)
                }
                vfor({ ctx.toolLabels }) { label ->
                    View {
                        attr { flex(1f); height(52f); allCenter(); backgroundColor(CARD) }
                        event {
                            click {
                                when (label) {
                                    "＋ 新建" -> ctx.newNote()
                                    "标签" -> ctx.setOverlay("tags")
                                    "统计" -> ctx.setOverlay("stats")
                                    "清空回收站" -> {
                                        ctx.repo.emptyTrash(); ctx.refresh(); ctx.say("回收站已清空")
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        Text {
                            attr {
                                text(label)
                                fontSize(if (label == "＋ 新建") 14f else 11f)
                                color(if (label == "＋ 新建") ACCENT else INK)
                            }
                        }
                    }
                }
            }

            // Full-screen overlays (0 or 1 item)
            vfor({ ctx.overlay }) { name ->
                when (name) {
                    "edit" -> {
                        View {
                            attr {
                                absolutePosition(0f, 0f, 0f, 0f)
                                backgroundColor(BG)
                                flexDirectionColumn()
                                padding(left = 14f, right = 14f, top = 12f, bottom = 12f)
                            }
                            View {
                                attr { flexDirectionRow(); height(40f); allCenter() }
                                View {
                                    attr { flex(1f) }
                                    Text { attr { text("编辑便签"); fontSize(18f); fontWeightBold(); color(INK) } }
                                }
                                View {
                                    attr { size(34f, 34f); borderRadius(17f); backgroundColor(CARD); allCenter() }
                                    event { click { ctx.closeOverlay() } }
                                    Text { attr { text("×"); fontSize(16f); color(INK) } }
                                }
                            }

                            Text {
                                attr {
                                    text("原标题：" + ctx.initialTitle.ifBlank { "（空）" })
                                    fontSize(11f); color(MUTED); marginTop(4f)
                                }
                            }
                            Text {
                                attr {
                                    text("原正文：" + ctx.initialContent.replace('\n', ' ').take(40))
                                    fontSize(11f); color(MUTED); marginTop(2f)
                                }
                            }

                            Input {
                                attr {
                                    height(40f); marginTop(10f)
                                    borderRadius(8f); backgroundColor(CARD)
                                    text(ctx.initialTitle)
                                    placeholder("标题（留空保留原标题）")
                                    fontSize(15f); color(INK)
                                }
                                event { textDidChange { ctx.editTitle = it.text } }
                            }
                            TextArea {
                                attr {
                                    height(120f); marginTop(8f)
                                    borderRadius(8f); backgroundColor(CARD)
                                    placeholder("正文（留空保留原正文）")
                                    fontSize(14f); color(INK)
                                }
                                event { textDidChange { ctx.editContent = it.text } }
                            }
                            Input {
                                attr {
                                    height(38f); marginTop(8f)
                                    borderRadius(8f); backgroundColor(CARD)
                                    text(ctx.initialTags)
                                    placeholder("标签，用逗号分隔（留空保留）")
                                    fontSize(13f); color(INK)
                                }
                                event { textDidChange { ctx.editTags = it.text } }
                            }

                            // Color palette
                            View {
                                attr { flexDirectionRow(); height(40f); marginTop(8f) }
                                vfor({ ctx.colorLabels }) { label ->
                                    View {
                                        attr {
                                            flex(1f); height(32f); margin(left = 2f, right = 2f)
                                            borderRadius(8f)
                                            backgroundColor(Color(NoteColors.palette[ctx.colorLabels.indexOf(label)]))
                                            allCenter()
                                        }
                                        event { click { ctx.editColor = ctx.colorLabels.indexOf(label) } }
                                        Text { attr { text(label); fontSize(9f); color(INK) } }
                                    }
                                }
                            }

                            // Toggles
                            View {
                                attr { flexDirectionRow(); height(40f); marginTop(6f) }
                                vfor({ ctx.toggleLabels }) { label ->
                                    View {
                                        attr {
                                            flex(1f); height(34f); margin(left = 3f, right = 3f)
                                            borderRadius(8f); backgroundColor(CHIP); allCenter()
                                        }
                                        event {
                                            click {
                                                when (label) {
                                                    "置顶" -> ctx.editPinned = !ctx.editPinned
                                                    "收藏" -> ctx.editStarred = !ctx.editStarred
                                                    "归档" -> {
                                                        ctx.repo.toggleArchive(ctx.editId); ctx.refresh(); ctx.say("归档状态已切换")
                                                    }
                                                    "复制" -> {
                                                        ctx.repo.duplicate(ctx.editId); ctx.refresh(); ctx.say("已创建副本")
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                        }
                                        Text { attr { text(label); fontSize(12f); color(INK) } }
                                    }
                                }
                            }

                            Text {
                                attr {
                                    text("置顶 " + (if (ctx.editPinned) "开" else "关") + "   ·   收藏 " + (if (ctx.editStarred) "开" else "关"))
                                    fontSize(11f); color(MUTED); marginTop(4f)
                                }
                            }

                            // Checklist
                            Text { attr { text("待办清单"); fontSize(13f); fontWeightBold(); color(INK); marginTop(8f) } }
                            List {
                                attr { height(110f); marginTop(4f); backgroundColor(CARD); borderRadius(8f) }
                                vfor({ ctx.editChecks }) { item ->
                                    View {
                                        attr {
                                            flexDirectionRow(); height(30f)
                                            padding(left = 10f, right = 10f); allCenter()
                                        }
                                        event {
                                            click {
                                                val idx = ctx.editChecks.indexOf(item)
                                                if (idx >= 0 && ctx.repo.find(ctx.editId)?.checklist?.size ?: 0 > idx) {
                                                    ctx.repo.toggleCheck(ctx.editId, idx); ctx.syncChecks()
                                                }
                                            }
                                        }
                                        Text { attr { text(item); fontSize(12f); color(INK); flex(1f) } }
                                    }
                                }
                            }
                            View {
                                attr { flexDirectionRow(); height(40f); marginTop(4f) }
                                Input {
                                    attr {
                                        flex(1f); height(36f)
                                        borderRadius(8f); backgroundColor(CARD)
                                        placeholder("添加待办项"); fontSize(12f); color(INK)
                                    }
                                    event { textDidChange { ctx.checkInput = it.text } }
                                }
                                View {
                                    attr { size(50f, 36f); marginLeft(6f); borderRadius(8f); backgroundColor(ACCENT); allCenter() }
                                    event { click { ctx.addCheck() } }
                                    Text { attr { text("添加"); fontSize(12f); color(CARD) } }
                                }
                            }

                            // Save / delete
                            View {
                                attr { flexDirectionRow(); height(46f); marginTop(10f) }
                                View {
                                    attr { flex(1f); height(44f); marginRight(6f); borderRadius(10f); backgroundColor(ACCENT); allCenter() }
                                    event { click { ctx.saveEdit() } }
                                    Text { attr { text("保存便签"); fontSize(15f); fontWeightBold(); color(CARD) } }
                                }
                                View {
                                    attr { size(90f, 44f); borderRadius(10f); backgroundColor(CARD); allCenter() }
                                    event {
                                        click {
                                            ctx.repo.softDelete(ctx.editId)
                                            ctx.closeOverlay(); ctx.refresh(); ctx.say("已移入回收站")
                                        }
                                    }
                                    Text { attr { text("删除"); fontSize(13f); color(DANGER) } }
                                }
                            }
                        }
                    }

                    "tags" -> {
                        View {
                            attr {
                                absolutePosition(0f, 0f, 0f, 0f)
                                backgroundColor(BG); flexDirectionColumn(); padding(14f)
                            }
                            View {
                                attr { flexDirectionRow(); height(40f); allCenter() }
                                View { attr { flex(1f) }; Text { attr { text("按标签筛选"); fontSize(18f); fontWeightBold(); color(INK); flex(1f) } } }
                                View {
                                    attr { size(34f, 34f); borderRadius(17f); backgroundColor(CARD); allCenter() }
                                    event { click { ctx.closeOverlay() } }
                                    Text { attr { text("×"); fontSize(16f); color(INK) } }
                                }
                            }
                            List {
                                attr { flex(1f); marginTop(8f) }
                                vfor({ ctx.tagRows }) { tag ->
                                    View {
                                        attr {
                                            height(44f); marginTop(6f); padding(left = 14f, right = 14f)
                                            borderRadius(10f); backgroundColor(CARD); allCenter()
                                        }
                                        event {
                                            click {
                                                ctx.tagFilter = tag
                                                ctx.closeOverlay(); ctx.refresh()
                                                ctx.say(if (tag == NoteRepository.ALL_TAG) "显示全部标签" else "筛选：$tag")
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(if (tag == NoteRepository.ALL_TAG) "全部标签" else "# $tag")
                                                fontSize(14f); color(INK)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "stats" -> {
                        View {
                            attr {
                                absolutePosition(0f, 0f, 0f, 0f)
                                backgroundColor(BG); flexDirectionColumn(); padding(14f)
                            }
                            View {
                                attr { flexDirectionRow(); height(40f); allCenter() }
                                View { attr { flex(1f) }; Text { attr { text("统计 · 导出 · 导入"); fontSize(17f); fontWeightBold(); color(INK); flex(1f) } } }
                                View {
                                    attr { size(34f, 34f); borderRadius(17f); backgroundColor(CARD); allCenter() }
                                    event { click { ctx.closeOverlay() } }
                                    Text { attr { text("×"); fontSize(16f); color(INK) } }
                                }
                            }
                            Text {
                                attr {
                                    text(ctx.statsText); fontSize(13f); color(INK); marginTop(10f)
                                }
                            }
                            Text {
                                attr {
                                    text("导出 JSON（可长按复制）"); fontSize(12f); fontWeightBold(); color(INK); marginTop(12f)
                                }
                            }
                            List {
                                attr { height(140f); marginTop(6f); backgroundColor(CARD); borderRadius(8f) }
                                Text {
                                    attr {
                                        text(ctx.exportText.take(900)); fontSize(10f); color(MUTED)
                                        margin(10f)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text("导入 JSON（粘贴后点导入，会覆盖现有数据）"); fontSize(12f); color(INK); marginTop(12f)
                                }
                            }
                            Input {
                                attr {
                                    height(40f); marginTop(6f)
                                    borderRadius(8f); backgroundColor(CARD)
                                    placeholder("在此粘贴 JSON"); fontSize(11f); color(INK)
                                }
                                event { textDidChange { ctx.importInput = it.text } }
                            }
                            View {
                                attr { flexDirectionRow(); height(44f); marginTop(10f) }
                                View {
                                    attr { flex(1f); height(42f); marginRight(6f); borderRadius(10f); backgroundColor(ACCENT); allCenter() }
                                    event {
                                        click {
                                            val ok = ctx.repo.importJson(ctx.importInput)
                                            ctx.refresh()
                                            ctx.say(if (ok) "导入成功" else "导入失败：JSON 格式不正确")
                                        }
                                    }
                                    Text { attr { text("导入"); fontSize(14f); color(CARD) } }
                                }
                                View {
                                    attr { size(100f, 42f); borderRadius(10f); backgroundColor(CARD); allCenter() }
                                    event { click { ctx.exportText = ctx.repo.exportJson(); ctx.say("已刷新导出内容") } }
                                    Text { attr { text("刷新导出"); fontSize(12f); color(INK) } }
                                }
                            }
                        }
                    }

                    else -> { View { attr { absolutePosition(0f, 0f, 0f, 0f) } } }
                }
            }
        }
    }


}
