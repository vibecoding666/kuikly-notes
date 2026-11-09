package com.example.kuiklynotes

import android.app.Application
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager

class NotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppHolder.context = applicationContext

        with(KuiklyRenderAdapterManager) {
            krImageAdapter = KRImageAdapter
            krLogAdapter = KRLogAdapter
            krUncaughtExceptionHandlerAdapter = KRExceptionAdapter
            krRouterAdapter = KRRouterAdapter
            krThreadAdapter = KRThreadAdapter()
        }
    }
}
