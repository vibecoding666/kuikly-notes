package com.example.kuiklynotes

import android.app.Activity
import android.os.Bundle
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
    }

    override fun onResume() {
        super.onResume()
        delegator?.onResume()
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
