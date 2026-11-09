package com.example.kuiklynotes

import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

class NativeEditorAttr : Attr() {
    fun noteId(v: String): NativeEditorAttr { "noteId" with v; return this }
    fun placeholder(v: String): NativeEditorAttr { "placeholder" with v; return this }
    fun textSize(v: Float): NativeEditorAttr { "textSize" with v; return this }
    fun dark(v: Boolean): NativeEditorAttr { "dark" with v; return this }
}

class NativeEditorEvent : Event() {
    /** Only the length + timestamp travel back to Kotlin, never the body. */
    fun onMeta(handler: (Int, Long) -> Unit) {
        register(EVENT_META) {
            val o = it as? JSONObject ?: JSONObject()
            handler(o.optInt("length", 0), o.optLong("editedAt", 0L))
        }
    }

    fun onReady(handler: () -> Unit) {
        register(EVENT_READY) { handler() }
    }

    companion object {
        const val EVENT_META = "onMeta"
        const val EVENT_READY = "onReady"
    }
}

class NativeEditorView : DeclarativeBaseView<NativeEditorAttr, NativeEditorEvent>() {
    override fun createAttr(): NativeEditorAttr = NativeEditorAttr()
    override fun createEvent(): NativeEditorEvent = NativeEditorEvent()
    override fun viewName(): String = "HRNotesEditor"

    fun flush() {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("flush", null)
        }
    }
}

fun ViewContainer<*, *>.NativeEditor(init: NativeEditorView.() -> Unit) {
    addChild(NativeEditorView(), init)
}
