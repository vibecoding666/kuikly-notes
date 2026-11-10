package com.example.kuiklynotes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Full-screen native editor.
 *
 * This deliberately lives OUTSIDE the Kuikly view tree: Kuikly's renderer installs
 * its own gesture detector on the root view and consumes MOVE events, which made a
 * nested native EditText unable to scroll (it only ever moved a line or two). Running
 * the editor in its own Activity gives us stock Android text scrolling, IME handling
 * and long-document performance.
 */
class EditorActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var noteId: String = ""
    private var dirty = false
    private var savePending: Runnable? = null
    private var originalTitle: String = ""
    private var moveCount = 0

    private lateinit var etTitle: EditText
    private lateinit var etBody: EditText
    private lateinit var tvCreated: TextView
    private lateinit var tvEdited: TextView
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        noteId = intent.getStringExtra(EXTRA_ID) ?: ""
        val repo = NoteRepository().also { it.load() }
        val note = repo.find(noteId)
        if (note == null) {
            finish()
            return
        }
        originalTitle = note.title

        etTitle = findViewById(R.id.etTitle)
        etBody = findViewById(R.id.etBody)
        tvCreated = findViewById(R.id.tvCreated)
        tvEdited = findViewById(R.id.tvEdited)
        tvCount = findViewById(R.id.tvCount)

        styleBody()
        etTitle.setText(note.title)
        tvCreated.text = "创建 ${timeText(note.createdAt)}"
        tvEdited.text = "最后编辑 ${timeText(note.updatedAt)}"

        findViewById<TextView>(R.id.btnBack).setOnClickListener { closeAndSave(repo) }
        findViewById<TextView>(R.id.btnMore).setOnClickListener { showMenu(repo, note) }

        // Load the body off the UI thread, in chunks, then set it once.
        io.execute {
            val text = BodyFiles.readAll(noteId)
            main.post {
                etBody.setText(text)
                etBody.setSelection(0)
                updateCount()
            }
        }

        etBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                dirty = true
                updateCount()
                scheduleSave()
            }
        })
    }

    private fun styleBody() {
        etBody.gravity = Gravity.TOP or Gravity.START
        etBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        etBody.isVerticalScrollBarEnabled = true

        // Probes for the CI smoke test: prove that gestures and scrolling reach the
        // native editor (previously Kuikly's container swallowed every MOVE event).
        etBody.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                moveCount++
                Log.i(TAG, "MOVE n=$moveCount")
            }
            false
        }
        etBody.setOnScrollChangeListener { _, _, y, _, _ ->
            Log.i(TAG, "SCROLL y=$y")
        }
    }

    private fun updateCount() {
        tvCount.text = "${etBody.text.length} 字"
        tvEdited.text = "最后编辑 刚刚"
    }

    private fun scheduleSave() {
        savePending?.let { main.removeCallbacks(it) }
        val r = Runnable { saveBody() }
        savePending = r
        main.postDelayed(r, 800)
    }

    private fun saveBody() {
        if (!dirty || noteId.isEmpty()) return
        dirty = false
        val snapshot = etBody.text.toString()
        val id = noteId
        io.execute { BodyFiles.write(id, snapshot) }
    }

    private fun closeAndSave(repo: NoteRepository) {
        saveBody()
        val title = etTitle.text.toString()
        val note = repo.find(noteId)
        if (note != null && title != note.title) {
            note.title = title
            repo.upsert(note)
        }
        // Persist the fresh length + preview so the list card is up to date.
        repo.updateBodyMeta(noteId, etBody.text.length, System.currentTimeMillis())
        finish()
    }

    private fun showMenu(repo: NoteRepository, note: Note) {
        val popup = PopupMenu(this, findViewById(R.id.btnMore))
        popup.menu.add(0, 1, 0, "置顶")
        popup.menu.add(0, 2, 1, "收藏")
        popup.menu.add(0, 3, 2, "归档")
        popup.menu.add(0, 4, 3, "复制")
        popup.menu.add(0, 5, 4, "压测样张（50 万字）")
        popup.menu.add(0, 6, 5, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { repo.togglePin(noteId); finish() }
                2 -> { repo.toggleStar(noteId); finish() }
                3 -> { repo.toggleArchive(noteId); finish() }
                4 -> { repo.duplicate(noteId); finish() }
                5 -> {
                    saveBody()
                    val stress = repo.createStressNote(500_000)
                    startActivity(Intent(this, EditorActivity::class.java).putExtra(EXTRA_ID, stress.id))
                    finish()
                }
                6 -> {
                    repo.softDelete(noteId)
                    finish()
                }
            }
            true
        }
        popup.show()
    }

    override fun onBackPressed() {
        val repo = NoteRepository().also { it.load() }
        closeAndSave(repo)
    }

    override fun onPause() {
        super.onPause()
        saveBody()
    }

    private fun timeText(t: Long): String {
        if (t <= 0L) return "—"
        val d = System.currentTimeMillis() - t
        return when {
            d < 60_000L -> "刚刚"
            d < 3_600_000L -> "${d / 60_000L} 分钟前"
            d < 86_400_000L -> "${d / 3_600_000L} 小时前"
            else -> "${d / 86_400_000L} 天前"
        }
    }

    companion object {
        private const val EXTRA_ID = "noteId"
        private const val TAG = "EditorBody"

        fun start(context: Context, noteId: String) {
            context.startActivity(
                Intent(context, EditorActivity::class.java)
                    .putExtra(EXTRA_ID, noteId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
