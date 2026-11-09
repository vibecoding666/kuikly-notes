package com.example.kuiklynotes

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRLogAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRRouterAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRThreadAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter
import org.json.JSONObject
import java.util.concurrent.Executors

object KRLogAdapter : IKRLogAdapter {
    override val asyncLogEnable: Boolean get() = false
    override fun i(tag: String, msg: String) { Log.i(tag, msg) }
    override fun d(tag: String, msg: String) { Log.d(tag, msg) }
    override fun e(tag: String, msg: String) { Log.e(tag, msg) }
}

class KRThreadAdapter : IKRThreadAdapter {
    override fun executeOnSubThread(task: () -> Unit) { pool.execute(task) }
    private companion object {
        val pool = Executors.newFixedThreadPool(2)
    }
}

object KRRouterAdapter : IKRRouterAdapter {
    // This app is a single Kuikly page: folder/screen switches happen in the Pager.
    override fun openPage(context: Context, pageName: String, pageData: JSONObject) = Unit
    override fun closePage(context: Context) { (context as? Activity)?.finish() }
}

object KRImageAdapter : IKRImageAdapter {
    // The notes UI uses no network images, so a no-op loader is sufficient.
    override fun fetchDrawable(imageLoadOption: HRImageLoadOption, callback: (Drawable?) -> Unit) {
        callback(null)
    }

    override fun fetchDrawable(
        imageLoadOption: HRImageLoadOption,
        extra: JSONObject,
        callback: (Drawable?) -> Unit,
    ) {
        callback(null)
    }

    override val shouldWaitViewDidLoad: Boolean get() = false
}

object KRExceptionAdapter : IKRUncaughtExceptionHandlerAdapter {
    override fun uncaughtException(throwable: Throwable) {
        Log.e("KuiklyNotes", throwable.stackTraceToString())
    }
}
