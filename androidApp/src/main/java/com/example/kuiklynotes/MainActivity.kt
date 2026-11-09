package com.example.kuiklynotes

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate

class MainActivity : Activity() {

    private var delegator: KuiklyRenderViewBaseDelegator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        setContentView(container)

        // Every method of the delegate has a default implementation.
        val delegate = object : KuiklyRenderViewBaseDelegatorDelegate {}
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
