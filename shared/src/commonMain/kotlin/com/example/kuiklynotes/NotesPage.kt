package com.example.kuiklynotes

import com.example.kuiklynotes.Note.Companion.KIND_NOTE
import com.example.kuiklynotes.Note.Companion.KIND_TODO
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private val BG = Color(0xFFF6F4EEL)
private val PAPER = Color(0xFFFFFFFFL)
private val INK = Color(0xFF232830L)
private val INK2 = Color(0xFF4A515AL)
private val MUTED = Color(0xFF9299A2L)
private val LINE = Color(0xFFE9E5DCL)
private val ACCENT = Color(0xFF2F5D8CL)
private val ACCENT_SOFT = Color(0xFFE7EEF6L)
private val DANGER = Color(0xFFD9534FL)

/** One row of the 2-column notes grid. */
internal class GridRow(val left: NoteCard?, val right: NoteCard?)

internal class NoteCard(
    val id: String, val title: String, val summary: String, val meta: String,
    val color: Long, val pinned: Boolean, val starred: Boolean, val trashed: Boolean,
    val archived: Boolean, val done: Boolean, val kind: Int,
)

@Page("NotesPage")
internal class NotesPage : Pager() {

    private val repo = NoteRepository()

    private var tab by observable(0)              // 0 notes / 1 todo
    private var screen by observable("list")      // list / editor / menu / tags / stats
    private var dark by observable(false)
    private var query by observable("")
    private var folderIdx by observable(0)
    private var sortIdx by observable(0)
    private var tagFilter by observable(NoteRepository.ALL_TAG)
    private var banner by observable("")

    private var gridRows: ObservableList<GridRow> by observableList()
    private var todoOpen: ObservableList<NoteCard> by observableList()
    private var todoDone: ObservableList<NoteCard> by observableList()
    private var tagRows: ObservableList<String> by observableList()
    private var menuRows: ObservableList<String> by observableList()
    private var colorRows: ObservableList<String> by observableList()

    private var headText by observable("我的记录")
    private var subText by observable("")
    private var countText by observable("0 字")
    private var createdText by observable("")
    private var editedText by observable("")
    private var editorTitle by observable("")
    private var editorId by observable("")

    private var editingId: String = ""
    private var editTitle: String = ""
    private var editorRef: ViewRef<NativeEditorView>? = null

    private val tabLabels: ObservableList<String> by observableList()
    private val folderLabels: ObservableList<String> by observableList()
    private val sortLabels: ObservableList<String> by observableList()
    private val menuLabels: ObservableList<String> by observableList()
    private val colorLabels: ObservableList<String> by observableList()

    init {
        repo.load()
        listOf("笔记", "待办").forEach { tabLabels.add(it) }
        listOf("全部", "收藏", "置顶", "归档", "回收站").forEach { folderLabels.add(it) }
        listOf("最近", "创建", "标题", "置顶", "颜色").forEach { sortLabels.add(it) }
        listOf("标签", "颜色", "置顶", "收藏", "归档", "复制", "深色模式", "统计 / 导出", "删除").forEach { menuLabels.add(it) }
        listOf("柠檬", "天蓝", "薄荷", "樱粉", "薰衣草", "素纸", "雾灰", "暖杏").forEach { colorLabels.add(it) }
        refresh()
    }

    // ---------- data ----------

    private fun currentFolder(): Folder = Folder.values()[folderIdx]
    private fun currentSort(): SortMode = SortMode.values()[sortIdx]

    private fun refresh() {
        syncSlots()
        val list = repo.visible(query, tab, currentFolder(), tagFilter, currentSort())
        val cards = list.map { it.toCard() }

        gridRows.clear()
        if (tab == 0) {
            if (cards.isEmpty()) {
                gridRows.add(GridRow(null, NoteCard("__empty__", "还没有便签", "点右下角 ＋ 新建", "", 0xFFE9E5DCL, false, false, false, false, false, KIND_NOTE)))
            } else {
                var i = 0
                while (i < cards.size) {
                    gridRows.add(GridRow(cards[i], cards.getOrNull(i + 1)))
                    i += 2
                }
            }
        }

        todoOpen.clear(); todoDone.clear()
        if (tab == 1) {
            cards.filter { !it.trashed }.forEach { if (it.done) todoDone.add(it) else todoOpen.add(it) }
        }

        val s = repo.stats()
        headText = if (tab == 0) "我的记录" else "待办"
        subText = if (tab == 0) "共 ${s.notes} 条 · ${s.pinned} 条置顶" else "${s.todoOpen} 项待办 · ${s.todoDone} 项已完成"
    }

    private fun Note.toCard() = NoteCard(
        id = id,
        title = displayTitle(),
        summary = summary.ifBlank { "（空白）" },
        meta = (if (tags.isEmpty()) "" else tags.joinToString("  #", prefix = "#") + "   ") + timeText(updatedAt) +
            if (length > 0) " · $length 字" else "",
        color = NoteColors.palette[NoteColors.normalized(color)],
        pinned = pinned, starred = starred, trashed = deleted, archived = archived,
        done = done, kind = kind,
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

    private fun stamp(t: Long): String {
        if (t <= 0L) return "—"
        val d = nowMillis() - t
        return when {
            d < 60_000L -> "刚刚"
            d < 3_600_000L -> "${d / 60_000L} 分钟前"
            d < 86_400_000L -> "${d / 3_600_000L} 小时前"
            else -> "${d / 86_400_000L} 天前"
        }
    }

    // ---------- actions ----------

    private fun say(t: String) { banner = t }

    private fun openEditor(id: String) {
        val n = repo.find(id) ?: return
        editingId = id
        editorId = id
        editTitle = n.title
        editorTitle = n.title
        createdText = stamp(n.createdAt)
        editedText = stamp(n.updatedAt)
        countText = "${n.length} 字"
        screen = "editor"
        syncSlots()
    }

    private fun closeEditor() {
        editorRef?.view?.flush()
        screen = "list"
        syncSlots()
        refresh()
    }

    private fun createNote() {
        val n = repo.create(if (tab == 0) KIND_NOTE else KIND_TODO)
        openEditor(n.id)
    }

    private fun onMeta(length: Int, editedAt: Long) {
        countText = "$length 字"
        editedText = "刚刚"
        repo.updateBodyMeta(editingId, length, editedAt)
    }

    private fun switchTab(i: Int) {
        tab = i
        folderIdx = 0
        tagFilter = NoteRepository.ALL_TAG
        refresh()
    }

    private fun toggleMenu() { menuOpen = !menuOpen; syncSlots() }
    private fun toggleEditorMenu() { editorMenuOpen = !editorMenuOpen; syncSlots() }

    // ---------- UI ----------

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(if (ctx.dark) Color(0xFF15181DL) else BG)
                flexDirectionColumn()
                autoDarkEnable(false)
            }

            // ===== LIST SCREEN =====
            vfor({ ctx.screenList }) { _ ->
                // App bar
                View {
                    attr {
                        flexDirectionRow()
                        height(74f)
                        padding(left = 18f, right = 14f, top = 14f, bottom = 8f)
                        allCenter()
                    }
                    View {
                        attr { flex(1f); flexDirectionColumn() }
                        Text { attr { text(ctx.headText); fontSize(22f); fontWeightBold(); color(if (ctx.dark) Color(0xFFEEF1F5L) else INK) } }
                        Text { attr { text(ctx.subText); fontSize(10f); color(MUTED); marginTop(3f) } }
                    }
                    View {
                        attr { size(34f, 34f); borderRadius(11f); backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER); allCenter() }
                        event { click { ctx.searchVisible = !ctx.searchVisible } }
                        Text { attr { text("⌕"); fontSize(19f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                    }
                    View {
                        attr { size(34f, 34f); marginLeft(7f); borderRadius(11f); backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER); allCenter() }
                        event { click { ctx.toggleMenu() } }
                        Text { attr { text("⋮"); fontSize(19f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                    }
                }

                // Search
                View {
                    attr {
                        flexDirectionRow(); height(40f)
                        margin(left = 16f, right = 16f, bottom = 6f)
                        padding(left = 12f, right = 8f)
                        borderRadius(12f); backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                        allCenter()
                    }
                    Input {
                        attr {
                            flex(1f); height(36f)
                            placeholder("搜索标题 / 正文 / 标签")
                            fontSize(13f); color(if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                        }
                        event { textDidChange { ctx.query = it.text; ctx.refresh() } }
                    }
                    View {
                        attr { size(26f, 26f); borderRadius(13f); backgroundColor(ACCENT_SOFT); allCenter() }
                        event { click { ctx.query = ""; ctx.refresh() } }
                        Text { attr { text("×"); fontSize(14f); color(ACCENT) } }
                    }
                }

                // Content
                List {
                    attr { flex(1f) }

                    // Notes: 2-column grid
                    vfor({ ctx.gridRows }) { row ->
                        if (ctx.tab == 0) {
                            View {
                                attr {
                                    flexDirectionRow()
                                    margin(left = 14f, right = 14f, top = 5f, bottom = 5f)
                                }
                                if (row.left != null) {
                                    View {
                                        attr { flex(1f); marginRight(5f) }
                                        val c = row.left!!
if (c.id == "__empty__") {
            View {
                attr {
                    height(120f); borderRadius(16f)
                    backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                    allCenter()
                }
                Text { attr { text(c.title); fontSize(13f); color(if (ctx.dark) Color(0xFFEEF1F5L) else INK) } }
                Text { attr { text(c.summary); fontSize(10f); color(MUTED); marginTop(6f) } }
            }
        } else {
            View {
                attr {
                    height(126f); borderRadius(16f)
                    backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                    padding(left = 12f, right = 10f, top = 12f, bottom = 10f)
                }
                event { click { if (!c.trashed) ctx.openEditor(c.id) } }
                View {
                    attr { flexDirectionRow() }
                    View { attr { size(4f, 16f); borderRadius(2f); backgroundColor(Color(c.color)); marginRight(7f) } }
                    Text {
                        attr {
                            text(c.title); fontSize(13f); fontWeightBold()
                            color(if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                            flex(1f)
                        }
                    }
                    if (c.pinned) { Text { attr { text("⌃"); fontSize(11f); color(ACCENT) } } }
                    if (c.starred) { Text { attr { text("☆"); fontSize(11f); color(ACCENT); marginLeft(3f) } } }
                }
                Text {
                    attr {
                        text(c.summary); fontSize(10f)
                        color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2)
                        marginTop(7f); flex(1f)
                    }
                }
                View {
                    attr { flexDirectionRow(); marginTop(7f); allCenter() }
                    Text { attr { text(c.meta); fontSize(9f); color(MUTED); flex(1f) } }
                    if (c.trashed) {
                        View {
                            attr { size(26f, 24f); borderRadius(8f); backgroundColor(ACCENT_SOFT); allCenter() }
                            event { click { ctx.repo.restore(c.id); ctx.refresh(); ctx.say("已恢复") } }
                            Text { attr { text("↺"); fontSize(12f); color(ACCENT) } }
                        }
                    } else {
                        View {
                            attr { size(26f, 24f); borderRadius(8f); backgroundColor(ACCENT_SOFT); allCenter() }
                            event {
                                click {
                                    if (c.trashed) ctx.repo.purge(c.id) else { ctx.repo.softDelete(c.id); ctx.say("已移入回收站") }
                                    ctx.refresh()
                                }
                            }
                            Text { attr { text("×"); fontSize(12f); color(DANGER) } }
                        }
                    }
                }
            }
        }
                                    }
                                } else {
                                    View { attr { flex(1f); marginRight(5f) } }
                                }
                                if (row.right != null) {
                                    View {
                                        attr { flex(1f); marginLeft(5f) }
                                        val c = row.right!!
if (c.id == "__empty__") {
            View {
                attr {
                    height(120f); borderRadius(16f)
                    backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                    allCenter()
                }
                Text { attr { text(c.title); fontSize(13f); color(if (ctx.dark) Color(0xFFEEF1F5L) else INK) } }
                Text { attr { text(c.summary); fontSize(10f); color(MUTED); marginTop(6f) } }
            }
        } else {
            View {
                attr {
                    height(126f); borderRadius(16f)
                    backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                    padding(left = 12f, right = 10f, top = 12f, bottom = 10f)
                }
                event { click { if (!c.trashed) ctx.openEditor(c.id) } }
                View {
                    attr { flexDirectionRow() }
                    View { attr { size(4f, 16f); borderRadius(2f); backgroundColor(Color(c.color)); marginRight(7f) } }
                    Text {
                        attr {
                            text(c.title); fontSize(13f); fontWeightBold()
                            color(if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                            flex(1f)
                        }
                    }
                    if (c.pinned) { Text { attr { text("⌃"); fontSize(11f); color(ACCENT) } } }
                    if (c.starred) { Text { attr { text("☆"); fontSize(11f); color(ACCENT); marginLeft(3f) } } }
                }
                Text {
                    attr {
                        text(c.summary); fontSize(10f)
                        color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2)
                        marginTop(7f); flex(1f)
                    }
                }
                View {
                    attr { flexDirectionRow(); marginTop(7f); allCenter() }
                    Text { attr { text(c.meta); fontSize(9f); color(MUTED); flex(1f) } }
                    if (c.trashed) {
                        View {
                            attr { size(26f, 24f); borderRadius(8f); backgroundColor(ACCENT_SOFT); allCenter() }
                            event { click { ctx.repo.restore(c.id); ctx.refresh(); ctx.say("已恢复") } }
                            Text { attr { text("↺"); fontSize(12f); color(ACCENT) } }
                        }
                    } else {
                        View {
                            attr { size(26f, 24f); borderRadius(8f); backgroundColor(ACCENT_SOFT); allCenter() }
                            event {
                                click {
                                    if (c.trashed) ctx.repo.purge(c.id) else { ctx.repo.softDelete(c.id); ctx.say("已移入回收站") }
                                    ctx.refresh()
                                }
                            }
                            Text { attr { text("×"); fontSize(12f); color(DANGER) } }
                        }
                    }
                }
            }
        }
                                    }
                                } else {
                                    View { attr { flex(1f); marginLeft(5f) } }
                                }
                            }
                        }
                    }

                    // Todo: open group
                    vfor({ ctx.todoGroupOpen }) { _ ->
                        if (ctx.tab == 1) {
                            Text {
                                attr {
                                    text("待办 · ${ctx.todoOpen.size}")
                                    fontSize(11f); color(MUTED)
                                    margin(left = 18f, right = 18f, top = 10f, bottom = 6f)
                                }
                            }
                            vfor({ ctx.todoOpen }) { c ->
                                View {
            attr {
                flexDirectionRow(); height(50f)
                margin(left = 14f, right = 14f, bottom = 7f)
                padding(left = 12f, right = 12f)
                borderRadius(13f)
                backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                allCenter()
            }
            event { click { ctx.repo.toggleDone(c.id); ctx.refresh() } }
            View {
                attr {
                    size(19f, 19f); borderRadius(7f)
                    backgroundColor(if (c.done) ACCENT else ACCENT_SOFT)
                    allCenter()
                }
                if (c.done) { Text { attr { text("✓"); fontSize(11f); color(PAPER) } } }
            }
            Text {
                attr {
                    text(c.title); fontSize(13f); flex(1f); marginLeft(11f)
                    color(if (c.done) MUTED else if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                }
            }
            Text { attr { text(c.meta); fontSize(9f); color(MUTED) } }
        }
                            }
                            Text {
                                attr {
                                    text("已完成 (${ctx.todoDone.size})")
                                    fontSize(11f); color(MUTED)
                                    margin(left = 18f, right = 18f, top = 14f, bottom = 6f)
                                }
                            }
                            vfor({ ctx.todoDone }) { c ->
                                View {
            attr {
                flexDirectionRow(); height(50f)
                margin(left = 14f, right = 14f, bottom = 7f)
                padding(left = 12f, right = 12f)
                borderRadius(13f)
                backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                allCenter()
            }
            event { click { ctx.repo.toggleDone(c.id); ctx.refresh() } }
            View {
                attr {
                    size(19f, 19f); borderRadius(7f)
                    backgroundColor(if (c.done) ACCENT else ACCENT_SOFT)
                    allCenter()
                }
                if (c.done) { Text { attr { text("✓"); fontSize(11f); color(PAPER) } } }
            }
            Text {
                attr {
                    text(c.title); fontSize(13f); flex(1f); marginLeft(11f)
                    color(if (c.done) MUTED else if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                }
            }
            Text { attr { text(c.meta); fontSize(9f); color(MUTED) } }
        }
                            }
                        }
                    }
                }

                // Banner
                Text {
                    attr {
                        text(if (ctx.banner.isEmpty()) "" else "› " + ctx.banner)
                        fontSize(11f); color(ACCENT)
                        margin(left = 18f, right = 18f, top = 2f, bottom = 2f)
                    }
                }

                // FAB
                View {
                    attr {
                        absolutePosition(bottom = 82f, right = 18f)
                        size(52f, 52f); borderRadius(17f); backgroundColor(ACCENT); allCenter()
                    }
                    event { click { ctx.createNote() } }
                    Text { attr { text("＋"); fontSize(24f); color(PAPER) } }
                }

                // Bottom segmented tab
                View {
                    attr {
                        height(64f)
                        backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                        flexDirectionRow()
                        padding(left = 14f, right = 14f, top = 8f, bottom = 10f)
                        allCenter()
                    }
                    View {
                        attr {
                            flex(1f); height(44f); flexDirectionRow()
                            borderRadius(14f)
                            backgroundColor(if (ctx.dark) Color(0xFF15181DL) else BG)
                            padding(left = 4f, right = 4f, top = 4f, bottom = 4f)
                            allCenter()
                        }
                        vfor({ ctx.tabLabels }) { label ->
                            View {
                                attr {
                                    flex(1f); height(36f); borderRadius(10f)
                                    backgroundColor(if (ctx.tabLabels.indexOf(label) == ctx.tab) (if (ctx.dark) Color(0xFF1D2127L) else PAPER) else Color(0x00000000L))
                                    allCenter()
                                }
                                event { click { ctx.switchTab(ctx.tabLabels.indexOf(label)) } }
                                Text {
                                    attr {
                                        text(label); fontSize(13f)
                                        color(if (ctx.tabLabels.indexOf(label) == ctx.tab) ACCENT else MUTED)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== EDITOR SCREEN (full screen, no boxes) =====
            vfor({ ctx.screenEditor }) { _ ->
                View {
                    attr {
                        absolutePosition(0f, 0f, 0f, 0f)
                        backgroundColor(if (ctx.dark) Color(0xFF15181DL) else BG)
                        flexDirectionColumn()
                    }

                    // Top: back + title + more
                    View {
                        attr {
                            flexDirectionRow(); height(54f)
                            padding(left = 10f, right = 12f, top = 8f, bottom = 4f)
                            allCenter()
                        }
                        View {
                            attr { size(34f, 38f); borderRadius(9f); allCenter() }
                            event { click { ctx.closeEditor() } }
                            Text { attr { text("‹"); fontSize(24f); color(if (ctx.dark) Color(0xFFEEF1F5L) else INK) } }
                        }
                        Input {
                            attr {
                                flex(1f); height(38f)
                                text(ctx.editorTitle)
                                placeholder("标题")
                                fontSize(17f); fontWeightBold()
                                color(if (ctx.dark) Color(0xFFEEF1F5L) else INK)
                            }
                            event { textDidChange { ctx.editTitle = it.text } }
                        }
                        View {
                            attr { size(32f, 36f); allCenter() }
                            event { click { ctx.toggleEditorMenu() } }
                            Text { attr { text("⋮"); fontSize(19f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                        }
                    }

                    // Meta line: created / edited / word count
                    View {
                        attr {
                            flexDirectionRow(); height(24f)
                            padding(left = 18f, right = 16f)
                            allCenter()
                        }
                        Text { attr { text("创建 ${ctx.createdText}"); fontSize(10f); color(MUTED) } }
                        Text { attr { text("   最后编辑 ${ctx.editedText}"); fontSize(10f); color(MUTED); flex(1f) } }
                        Text { attr { text(ctx.countText); fontSize(10f); color(ACCENT) } }
                    }

                    // Native editor: full text never enters Kotlin
                    NativeEditor {
                        ref { ctx.editorRef = it }
                        attr {
                            flex(1f)
                            noteId(ctx.editorId)
                            placeholder("正文")
                            textSize(15f)
                            dark(ctx.dark)
                        }
                        event {
                            onMeta { len, at -> ctx.onMeta(len, at) }
                            onReady { ctx.say("编辑器就绪") }
                        }
                    }

                    // Editor three-dot menu
                    vfor({ ctx.editorMenuList }) { _ ->
                        View {
                            attr {
                                absolutePosition(top = 50f, right = 12f)
                                width(170f)
                                backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                                borderRadius(14f)
                                padding(6f)
                            }
                            vfor({ ctx.menuLabels }) { label ->
                                View {
                                    attr { height(34f); padding(left = 10f, right = 10f); allCenter() }
                                    event { click { ctx.onEditorMenu(label) } }
                                    Text { attr { text(label); fontSize(12f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                                }
                            }
                        }
                    }
                }
            }

            // ===== LIST three-dot menu =====
            vfor({ ctx.listMenuList }) { _ ->
                View {
                    attr {
                        absolutePosition(top = 64f, right = 14f)
                        width(178f)
                        backgroundColor(if (ctx.dark) Color(0xFF1D2127L) else PAPER)
                        borderRadius(14f)
                        padding(6f)
                    }
                    vfor({ ctx.folderLabels }) { label ->
                        View {
                            attr { height(32f); padding(left = 10f, right = 10f); allCenter() }
                            event {
                                click {
                                    ctx.folderIdx = ctx.folderLabels.indexOf(label)
                                    ctx.menuOpen = false
                                    ctx.syncSlots()
                                    ctx.refresh()
                                    ctx.say("筛选：${label}")
                                }
                            }
                            Text { attr { text("视图 · $label"); fontSize(11f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                        }
                    }
                    vfor({ ctx.sortLabels }) { label ->
                        View {
                            attr { height(32f); padding(left = 10f, right = 10f); allCenter() }
                            event {
                                click {
                                    ctx.sortIdx = ctx.sortLabels.indexOf(label)
                                    ctx.menuOpen = false
                                    ctx.syncSlots()
                                    ctx.refresh()
                                    ctx.say("排序：${label}")
                                }
                            }
                            Text { attr { text("排序 · $label"); fontSize(11f); color(if (ctx.dark) Color(0xFFC3CAD3L) else INK2) } }
                        }
                    }
                    View {
                        attr { height(32f); padding(left = 10f, right = 10f); allCenter() }
                        event { click { ctx.dark = !ctx.dark; ctx.menuOpen = false; ctx.syncSlots(); ctx.say("已切换主题") } }
                        Text { attr { text("深色模式"); fontSize(11f); color(ACCENT) } }
                    }
                }
            }
        }
    }

    // ---------- reusable card / row builders (called as DSL statements) ----------





    private fun onEditorMenu(label: String) {
        editorMenuOpen = false
        syncSlots()
        when (label) {
            "置顶" -> { repo.togglePin(editingId); refresh(); say("已切换置顶") }
            "收藏" -> { repo.toggleStar(editingId); refresh(); say("已切换收藏") }
            "归档" -> { repo.toggleArchive(editingId); refresh(); say("已切换归档") }
            "复制" -> { repo.duplicate(editingId); refresh(); say("已创建副本") }
            "深色模式" -> { dark = !dark; say("已切换主题") }
            "删除" -> { repo.softDelete(editingId); closeEditor(); say("已移入回收站") }
            "标签" -> { buildTags(); screen = "list"; say("标签见三点菜单筛选") }
            else -> say("$label（示意）")
        }
    }

    private fun buildTags() {
        tagRows.clear()
        tagRows.add(NoteRepository.ALL_TAG)
        repo.allTags().forEach { tagRows.add(it.first) }
    }

    // ---------- conditional-render slots (0 or 1 item each) ----------

    private var searchVisible by observable(false)
    private var menuOpen by observable(false)
    private var editorMenuOpen by observable(false)
    private var screenList: ObservableList<String> by observableList()
    private var screenEditor: ObservableList<String> by observableList()
    private var listMenuList: ObservableList<String> by observableList()
    private var editorMenuList: ObservableList<String> by observableList()
    private var todoGroupOpen: ObservableList<String> by observableList()

    // Slots are rebuilt whenever a screen/menu changes.
    private fun syncSlots() {
        screenList.clear(); screenEditor.clear()
        if (screen == "list") screenList.add("list") else screenEditor.add("editor")
        listMenuList.clear(); if (menuOpen && screen == "list") listMenuList.add("menu")
        editorMenuList.clear(); if (editorMenuOpen && screen == "editor") editorMenuList.add("menu")
        if (todoGroupOpen.isEmpty()) todoGroupOpen.add("todo")
    }
}
