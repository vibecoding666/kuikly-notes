package com.example.kuiklynotes

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import java.util.concurrent.Executors

/**
 * Native EditText exposed to Kuikly. The FULL TEXT never crosses the bridge:
 * Kuikly only receives { length, editedAt } events, and the body is read/written
 * in chunks on a background executor with an 800 ms debounce.
 *
 * Deliberately extends the platform EditText (not AppCompatEditText), which is
 * measurably faster for very large text.
 */
class HRNotesEditorView(context: Context) : EditText(context), IKuiklyRenderViewExport {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private var noteId = ""
    private var metaCallback: KuiklyRenderCallback? = null
    private var readyCallback: KuiklyRenderCallback? = null
    private var pendingSave: Runnable? = null
    private var dirty = false
    private var suppressWatcher = false
    private var dark = false

    init {
        // No box, no AppCompat wrapper, fixed padding, plain text.
        setBackgroundColor(Color.TRANSPARENT)
        val pad = (14 * density).toInt()
        setPadding(pad, (10 * density).toInt(), pad, pad)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.35f)
        hint = "正文"
        isFocusableInTouchMode = true
        // Text must start at the top-left of the editor, not float in the middle.
        // EditText inside a wrap-content container is vertically centred by default.
        gravity = Gravity.TOP or Gravity.START
        setHorizontallyScrolling(false)
        // Never grow/shrink away from the space Kuikly gave us.
        minimumHeight = 0
        minimumWidth = 0
        includeFontPadding = false
        // Give the user a visible scroll indicator, and no rubber-band jitter.
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = true
        applyColors()

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return
                dirty = true
                fireMeta(s?.length ?: 0)
                scheduleSave()
            }
        })
    }

    private fun applyColors() {
        setTextColor(if (dark) 0xFFEEF1F5.toInt() else 0xFF232830.toInt())
        setHintTextColor(if (dark) 0xFF7C848E.toInt() else 0xFF9299A2.toInt())
    }

    private fun fireMeta(length: Int) {
        metaCallback?.invoke(mapOf("length" to length, "editedAt" to System.currentTimeMillis()))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fill the space Kuikly allocated instead of wrapping to the content,
        // which would leave the body floating in the middle of the screen.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            setMeasuredDimension(w, h)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * Kuikly's container installs its own gesture detector which happily steals
     * MOVE events. Without this, scrolling the body only ever moves a few pixels
     * per gesture ("one line at a time"), because the parent intercepts the rest.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> {
                // Kuikly's container re-arms its own gesture detector during a drag,
                // so the request has to be renewed on every MOVE. Without this the
                // body only scrolls a tiny amount per gesture.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            moveEvents++
            if (moveEvents % 5 == 0) Log.i(TAG, "MOVE n=$moveEvents")
        }
        if (event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL
        ) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(event)
    }

    /** Number of MOVE events that actually reached this view (smoke probe). */
    var moveEvents = 0
        private set

    /** Scroll probe used by the smoke test: TextView text scrolling bypasses
     *  onScrollChanged, but this is called on every scroll step. */
    override fun computeVerticalScrollOffset(): Int {
        val y = super.computeVerticalScrollOffset()
        Log.i(TAG, "SCROLL y=$y")
        return y
    }

    override fun setProp(propKey: String, propValue: Any): Boolean {
        return when (propKey) {
            "noteId" -> { load(propValue.toString()); true }
            "placeholder" -> { hint = propValue.toString(); true }
            "textSize" -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, (propValue as Number).toFloat()); true }
            "dark" -> { dark = propValue as? Boolean ?: false; applyColors(); true }
            "onMeta" -> { metaCallback = propValue as? KuiklyRenderCallback; true }
            "onReady" -> { readyCallback = propValue as? KuiklyRenderCallback; true }
            else -> super.setProp(propKey, propValue)
        }
    }

    /** Chunked load: each part is appended on the UI thread via post(). */
    private fun load(id: String) {
        if (id == noteId) return
        noteId = id
        io.execute {
            val t0 = System.currentTimeMillis()
            val count = BodyFiles.chunkCount(id)
            for (i in 0 until count) {
                val chunk = BodyFiles.readChunk(id, i)
                main.post {
                    suppressWatcher = true
                    if (i == 0) setText(chunk) else append(chunk)
                    suppressWatcher = false
                }
            }
            main.post {
                if (count == 0) {
                    suppressWatcher = true
                    setText("")
                    suppressWatcher = false
                }
                val ms = System.currentTimeMillis() - t0
                Log.i(TAG, "LOAD noteId=$id chunks=$count chars=${text.length} elapsedMs=$ms")
                fireMeta(text.length)
                readyCallback?.invoke(emptyMap<String, Any>())
            }
        }
    }

    private fun scheduleSave() {
        pendingSave?.let { main.removeCallbacks(it) }
        val r = Runnable { flush() }
        pendingSave = r
        main.postDelayed(r, 800)
    }

    private fun flush() {
        if (!dirty || noteId.isEmpty()) return
        dirty = false
        val snapshot = text.toString()
        val id = noteId
        io.execute { BodyFiles.write(id, snapshot) }
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "flush" -> { flush(); null }
            else -> super.call(method, params, callback)
        }
    }

    companion object {
        private const val TAG = "HRNotesEditor"
    }
}
