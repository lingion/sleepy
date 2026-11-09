package com.lingion.sleepy.ui.screen.imports

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.lingion.sleepy.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * 拾光异步交互的原生弹窗。所有入口由现有 JS bridge 切到主线程后调用。
 *
 * 本类只传输用户答案。来源 validator 由 SleepySgShim.respond 在原 WebView 执行；
 * accepted ack 才消费当前请求，原生点击确认不代表课程已经保存。
 * 宿主在导航、刷新、完成和释放 WebView 时必须调用 [cancelSession]。
 */
internal class JwSgAssetInteraction(
    private val webView: WebView,
    private val onWaitingChanged: (Boolean) -> Unit,
    private val onSessionCancelled: () -> Unit = {},
) {
    private var sessionId: String? = null
    private val queue = ArrayDeque<Request>()
    private val seenRequests = mutableSetOf<Long>()
    private var active: Active? = null
    private var waiting = false

    fun beginSession(sessionId: String) {
        requireMainThread()
        require(sessionId.isNotBlank()) { "A session ID is required" }
        cancelSession()
        this.sessionId = sessionId
    }

    fun handleRequest(json: String) {
        requireMainThread()
        val request = Request.parse(json) ?: return
        if (request.sessionId != sessionId || !seenRequests.add(request.id)) return
        queue.addLast(request)
        showNext()
    }

    fun handleAcknowledged(json: String) {
        requireMainThread()
        val current = active ?: return
        val ack = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (ack.opt("sessionId") != sessionId ||
            positiveId(ack.opt("requestId")) != current.request.id ||
            positiveId(ack.opt("attemptId")) != current.attemptId ||
            !current.submitting
        ) return
        when (ack.opt("accepted")) {
            true -> {
                releaseDialog()
                showNext()
            }
            false -> {
                current.submitting = false
                val message = (ack.opt("error") as? String)?.takeIf { it.isNotBlank() }
                    ?: webView.context.getString(R.string.jw_fetch_format_error)
                current.error.text = message
                current.error.visibility = View.VISIBLE
                updateControls(current)
                updateWaiting()
            }
        }
    }

    /** 外部清理不会回调 onSessionCancelled，避免宿主结束会话时递归。 */
    fun cancelSession() {
        requireMainThread()
        val oldSession = sessionId
        sessionId = null
        val pending = buildList {
            active?.let { add(it.request to (it.attemptId + 1)) }
            queue.forEach { add(it to 1L) }
        }
        queue.clear()
        seenRequests.clear()
        releaseDialog()
        updateWaiting()
        if (oldSession != null) pending.forEach { (request, attempt) ->
            // 页面可能已销毁，取消只是尽力回传，不能阻止 native 引用被清理。
            runCatching { reply(request, attempt, if (request.kind == Kind.ALERT) false else null) }
        }
    }

    private fun showNext() {
        if (active != null) return
        val request = queue.pollFirst()
        if (sessionId == null || request == null) {
            updateWaiting()
            return
        }
        val activity = webView.context.findActivity()
        if (activity != null && (activity.isFinishing || activity.isDestroyed)) {
            queue.addFirst(request)
            cancelFromDialog()
            return
        }
        val context = webView.context
        val error = TextView(context).apply {
            visibility = View.GONE
            tag = "sg_interaction_error"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        var input: EditText? = null
        val builder = AlertDialog.Builder(context)
            .setTitle(request.title)
            .setPositiveButton(request.confirmText.ifBlank { context.getString(android.R.string.ok) }, null)
            .setNegativeButton(android.R.string.cancel, null)
        val selected = request.defaultSelectedIndex?.takeIf { it in request.items.indices }
        when (request.kind) {
            Kind.ALERT -> builder.setMessage(request.content).setView(error)
            Kind.PROMPT -> {
                input = EditText(context).apply {
                    tag = "sg_interaction_input"
                    setSingleLine(true)
                    setText(request.defaultText)
                    setSelection(text.length)
                }
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val inset = (24 * resources.displayMetrics.density).toInt()
                    setPadding(inset, 0, inset, 0)
                    addView(input, LinearLayout.LayoutParams(-1, -2))
                    addView(error, LinearLayout.LayoutParams(-1, -2))
                }
                builder.setMessage(request.tip).setView(content)
            }
            Kind.SELECTION -> {
                // 使用原生单选列表；没有合法默认值时 checkedItem 必须为负一。
                builder.setSingleChoiceItems(request.items.toTypedArray(), selected ?: -1) { _, which ->
                    val current = active ?: return@setSingleChoiceItems
                    if (current.request !== request || current.submitting) return@setSingleChoiceItems
                    current.selectedIndex = which
                    current.error.visibility = View.GONE
                    updateControls(current)
                }
                val title = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val inset = (24 * resources.displayMetrics.density).toInt()
                    setPadding(inset, inset, inset, 0)
                    addView(TextView(context).apply {
                        text = request.title
                        setTextAppearance(android.R.style.TextAppearance_Material_DialogWindowTitle)
                    })
                    addView(error)
                }
                builder.setCustomTitle(title)
            }
        }
        val dialog = builder.create()
        val current = Active(request, dialog, input, error, selected)
        active = current
        dialog.setOnCancelListener { if (active === current) cancelFromDialog() }
        dialog.setOnDismissListener { if (active === current) cancelFromDialog() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(current) }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancelFromDialog() }
            updateControls(current)
        }
        try {
            dialog.show()
            updateWaiting()
        } catch (_: RuntimeException) {
            // Activity 已离场或没有有效 window token 时，按取消清理全部引用。
            cancelFromDialog()
        }
    }

    private fun submit(current: Active) {
        if (active !== current || current.submitting || sessionId != current.request.sessionId) return
        val answer: Any = when (current.request.kind) {
            Kind.ALERT -> true
            Kind.PROMPT -> current.input?.text?.toString() ?: return
            Kind.SELECTION -> current.selectedIndex?.takeIf { it in current.request.items.indices } ?: return
        }
        current.attemptId++
        current.submitting = true
        current.error.visibility = View.GONE
        updateControls(current)
        // 等待 JS 校验时继续计时，不能让丢失 ack 无限暂停执行预算。
        updateWaiting()
        // 宿主恢复预算时可能同步结束已超时的会话，不能再发送刚才的确认值。
        if (active !== current || sessionId != current.request.sessionId) return
        try {
            reply(current.request, current.attemptId, answer)
        } catch (_: RuntimeException) {
            cancelFromDialog()
        }
    }

    private fun reply(request: Request, attemptId: Long, value: Any?) {
        val encoded = when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            is Boolean, is Int -> value.toString()
            else -> error("Unsupported interaction answer")
        }
        webView.evaluateJavascript(
            "window.SleepySgShim.respond(${JSONObject.quote(request.sessionId)},${request.id},$attemptId,$encoded);",
            null,
        )
    }

    private fun cancelFromDialog() {
        if (sessionId == null) return
        cancelSession()
        onSessionCancelled()
    }

    private fun updateControls(current: Active) {
        current.dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !current.submitting &&
            (current.request.kind != Kind.SELECTION || current.selectedIndex != null)
        current.input?.isEnabled = !current.submitting
        current.dialog.listView?.isEnabled = !current.submitting
    }

    private fun updateWaiting() {
        val next = active?.let { !it.submitting } == true
        if (next != waiting) {
            waiting = next
            onWaitingChanged(next)
        }
    }

    private fun releaseDialog() {
        val current = active ?: return
        active = null
        current.dialog.setOnShowListener(null)
        current.dialog.setOnCancelListener(null)
        current.dialog.setOnDismissListener(null)
        current.dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener(null)
        current.dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener(null)
        current.dialog.listView?.onItemClickListener = null
        current.dialog.dismiss()
    }

    private class Active(
        val request: Request,
        val dialog: AlertDialog,
        val input: EditText?,
        val error: TextView,
        var selectedIndex: Int?,
        var attemptId: Long = 0,
        var submitting: Boolean = false,
    )

    private enum class Kind { ALERT, PROMPT, SELECTION }

    private data class Request(
        val sessionId: String,
        val id: Long,
        val kind: Kind,
        val title: String,
        val content: String = "",
        val confirmText: String = "",
        val tip: String = "",
        val defaultText: String = "",
        val items: List<String> = emptyList(),
        val defaultSelectedIndex: Int? = null,
    ) {
        companion object {
            fun parse(json: String): Request? = runCatching {
                val obj = JSONObject(json)
                val session = (obj.opt("sessionId") as? String)?.takeIf { it.isNotBlank() } ?: return null
                val id = positiveId(obj.opt("requestId")) ?: return null
                val rawKind = obj.opt("kind") as? String ?: return null
                val kind = when (rawKind) {
                    "alert" -> Kind.ALERT
                    "prompt" -> Kind.PROMPT
                    "singleSelection", "selection" -> Kind.SELECTION
                    else -> return null
                }
                val items = if (kind == Kind.SELECTION) {
                    val array = obj.optJSONArray("items") ?: (obj.opt("itemsJsonString") as? String)?.let(::JSONArray)
                        ?: return null
                    if (array.length() == 0) return null
                    List(array.length()) { array.opt(it) as? String ?: return null }
                } else emptyList()
                val selected = (obj.opt("defaultSelectedIndex") as? Number)?.toDouble()?.let {
                    if (it.isFinite() && it == it.toInt().toDouble()) it.toInt() else null
                }
                Request(session, id, kind, obj.opt("title") as? String ?: "",
                    obj.opt("content") as? String ?: "", obj.opt("confirmText") as? String ?: "",
                    obj.opt("tip") as? String ?: "", obj.opt("defaultText") as? String ?: "", items, selected)
            }.getOrNull()
        }
    }

    private companion object {
        fun positiveId(value: Any?): Long? = (value as? Number)?.toDouble()?.takeIf {
            it.isFinite() && it >= 1 && it <= 9_007_199_254_740_991.0 && it == it.toLong().toDouble()
        }?.toLong()

        fun requireMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
            "Sg interaction must run on the main thread"
        }

        fun Context.findActivity(): Activity? {
            var current = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                val base = current.baseContext
                if (base === current) return null
                current = base
            }
            return current as? Activity
        }
    }
}
