package com.example.kuiklynotes

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate

class MainActivity : Activity() {

    private var delegator: KuiklyRenderViewBaseDelegator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        setContentView(container)

        val delegate = object : KuiklyRenderViewBaseDelegatorDelegate {
            override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
                super.registerExternalRenderView(kuiklyRenderExport)
                with(kuiklyRenderExport) {
                    renderViewExport("HRNotesEditor", { context -> HRNotesEditorView(context) })
                }
            }
        }
        val d = KuiklyRenderViewBaseDelegator(delegate)
        delegator = d
        d.onAttach(container, "", "NotesPage", emptyMap())

        // CI hook: "adb shell am start -n com.example.kuiklynotes/.MainActivity --ei stress 500000"
        // creates a 500k-character note and opens the editor, so the smoke test does
        // not have to guess pixel coordinates.
        val stress = intent?.getIntExtra("stress", 0) ?: 0
        if (stress > 0) {
            container.postDelayed({
                delegator?.sendEvent("stress", mapOf("chars" to stress))
            }, 2500)
        }
    }

    override fun onResume() {
        super.onResume()
        delegator?.onResume()
    }

    /**
     * When the full-screen editor is open, the system BACK key closes the editor
     * instead of exiting the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isEditorOpen()) {
            delegator?.sendEvent("closeEditor", mapOf())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        delegator?.onPause()
    }

    override fun onDestroy() {
        delegator?.onDetach()
        super.onDestroy()
    }
}
