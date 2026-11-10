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
        val tabIdx = intent?.getIntExtra("tab", -1) ?: -1
        val openMenu = intent?.getIntExtra("menu", 0) ?: 0
        val deleteTest = intent?.getStringExtra("delete_test")
        val selectTest = intent?.getStringExtra("select_test")
        if (stress > 0 || tabIdx >= 0 || openMenu > 0 || deleteTest != null || selectTest != null) {
            container.postDelayed({
                if (stress > 0) delegator?.sendEvent("stress", mapOf("chars" to stress))
                if (tabIdx >= 0) delegator?.sendEvent("switchTab", mapOf("tab" to tabIdx))
                if (openMenu > 0) delegator?.sendEvent("openMenu", mapOf())
                if (deleteTest != null) delegator?.sendEvent("deleteTest", mapOf())
                if (selectTest != null) delegator?.sendEvent("selectTest", mapOf())
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
