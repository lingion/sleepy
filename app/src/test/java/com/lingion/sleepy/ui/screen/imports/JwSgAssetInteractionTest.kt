package com.lingion.sleepy.ui.screen.imports

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

/**
 * 直接操作生产 host 的真实原生弹窗，不用另一份状态模型替代实现。
 * 新文件在修复前不存在，因此不能把此前的编译失败记作功能红测。
 * 本测试尚未覆盖主线程拥有的 WebView 与 JS shim 接线，也不代表真实设备验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class JwSgAssetInteractionTest {
    class HostActivity : Activity()

    private class RecordingWebView(activity: Activity) : WebView(activity) {
        val scripts = mutableListOf<String>()
        override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
            scripts += script
            callback?.onReceiveValue("null")
        }
    }

    private lateinit var activityController: org.robolectric.android.controller.ActivityController<HostActivity>
    private lateinit var webView: RecordingWebView
    private lateinit var host: JwSgAssetInteraction
    private val waiting = mutableListOf<Boolean>()
    private var userCancellations = 0

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(HostActivity::class.java).setup()
        webView = RecordingWebView(activityController.get())
        activityController.get().setContentView(webView)
        host = JwSgAssetInteraction(webView, { waiting += it }, { userCancellations++ })
        host.beginSession("session")
    }

    @After
    fun tearDown() {
        host.cancelSession()
        idleMainLooper()
        webView.destroy()
        activityController.pause().stop().destroy()
        idleMainLooper()
    }

    @Test
    fun alertWaitsForUserAndMatchingAcknowledgement() {
        request(1, "alert", "content" to "导入所选学期？", "confirmText" to "开始")
        val dialog = dialog()
        assertTrue(dialog.isShowing)
        assertEquals(listOf(true), waiting)
        assertTrue(webView.scripts.isEmpty())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(true, response().getBoolean(3))
        assertTrue("提交后必须等 ack", dialog.isShowing)
        assertEquals(listOf(true, false), waiting)
        acknowledge(1, 1, true, session = "old")
        assertTrue(dialog.isShowing)
        acknowledge(1, 2, true)
        assertTrue(dialog.isShowing)
        acknowledge(1, 1, true)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun promptRejectsThenRetainsInputAndUsesANewAttempt() {
        request(1, "prompt", "tip" to "起始学年", "defaultText" to "bad",
            "validatorName" to "validateYearInput")
        val dialog = dialog()
        val input = dialog.window!!.decorView.findViewWithTag<EditText>("sg_interaction_input")
        assertEquals("bad", input.text.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("bad", response().getString(3))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        acknowledge(1, 1, false, "请输入正确学年")
        assertTrue(dialog.isShowing)
        assertEquals("bad", input.text.toString())
        assertEquals("请输入正确学年", dialog.window!!.decorView
            .findViewWithTag<TextView>("sg_interaction_error").text.toString())
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        input.setText("2026")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(2L, response().getLong(2))
        assertEquals("2026", response().getString(3))
        acknowledge(1, 1, true)
        assertTrue("旧尝试的 ack 不能关闭新输入", dialog.isShowing)
        acknowledge(1, 2, true)
        assertFalse(dialog.isShowing)
        assertEquals(listOf(true, false, true, false), waiting)
        assertTrue("host 不执行来源函数", webView.scripts.none { it.contains("validateYearInput") })
    }

    @Test
    fun selectionWithNegativeDefaultHasNoCheckedItemAndCannotConfirm() {
        request(1, "singleSelection", "items" to JSONArray(listOf("甲", "乙")), "defaultSelectedIndex" to -1)
        val dialog = dialog()
        assertEquals(-1, dialog.listView.checkedItemPosition)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(1, response().getInt(3))
        acknowledge(1, 1, true)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun validSelectionDefaultStillWaitsAndOutOfBoundsDefaultDoesNotChooseFirst() {
        request(1, "singleSelection", "itemsJsonString" to "[\"甲\",\"乙\"]", "defaultSelectedIndex" to 1)
        val first = dialog()
        assertEquals(1, first.listView.checkedItemPosition)
        assertTrue(webView.scripts.isEmpty())
        first.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        acknowledge(1, 1, true)
        request(2, "singleSelection", "items" to JSONArray(listOf("甲", "乙")), "defaultSelectedIndex" to 20)
        assertEquals(-1, dialog().listView.checkedItemPosition)
        assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }

    @Test
    fun queuedDialogsKeepArrivalOrderAndDuplicateRequestDoesNotReopen() {
        request(1, "alert")
        val first = dialog()
        request(2, "prompt")
        request(1, "alert")
        assertSame(first, dialog())
        first.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        acknowledge(2, 1, true)
        assertSame(first, dialog())
        acknowledge(1, 1, true)
        val second = dialog()
        assertNotSame(first, second)
        second.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(2L, response().getLong(1))
        acknowledge(2, 1, true)
        request(1, "alert")
        assertFalse(second.isShowing)
        assertEquals(2, webView.scripts.size)
    }

    @Test
    fun cancelButtonCancelsWholeQueueWithTypedResultsAndNotifiesOnce() {
        request(1, "alert")
        request(2, "prompt")
        request(3, "singleSelection", "items" to JSONArray(listOf("甲")))
        val dialog = dialog()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(dialog.isShowing)
        assertEquals(3, webView.scripts.size)
        assertEquals(false, response(0).getBoolean(3))
        assertTrue(response(1).isNull(3))
        assertTrue(response(2).isNull(3))
        assertEquals(listOf(true, false), waiting)
        assertEquals(1, userCancellations)
        dialog.dismiss()
        host.cancelSession()
        assertEquals(1, userCancellations)
        assertEquals(3, webView.scripts.size)
    }

    @Test
    fun backOrOutsideCancellationReturnsNullForPromptAndDropsOldAcknowledgement() {
        request(1, "prompt")
        val old = dialog()
        old.cancel()
        idleMainLooper()
        assertTrue(response().isNull(3))
        assertFalse(old.isShowing)
        host.beginSession("new")
        request(1, "alert", session = "new")
        val next = dialog()
        acknowledge(1, 1, true)
        assertTrue(next.isShowing)
        request(2, "prompt")
        next.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        acknowledge(1, 1, true, session = "new")
        assertFalse("旧会话请求不能加入新队列", next.isShowing)
    }

    @Test
    fun externalCleanupSendsCancellationWithoutUserCallback() {
        request(1, "singleSelection", "items" to JSONArray(listOf("甲")))
        val old = dialog()
        host.cancelSession()
        assertFalse(old.isShowing)
        assertTrue(response().isNull(3))
        assertEquals(0, userCancellations)
        assertEquals(listOf(true, false), waiting)
    }

    @Test
    fun resumingBudgetCanCancelSynchronouslyWithoutSendingAStaleConfirmation() {
        host = JwSgAssetInteraction(webView, { isWaiting ->
            waiting += isWaiting
            if (!isWaiting) host.cancelSession()
        })
        host.beginSession("session")
        request(1, "prompt", "defaultText" to "2026")
        val old = dialog()
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertFalse(old.isShowing)
        assertEquals(1, webView.scripts.size)
        assertTrue(response().isNull(3))
        assertEquals(2L, response().getLong(2))
        assertEquals(listOf(true, false), waiting)
    }

    @Test
    fun userTextAndSessionAreEncodedAsValuesInsteadOfProgram() {
        val session = "session\"\n);unexpected();//"
        val text = "\"\n</script>);unexpected();//\u2028"
        host.beginSession(session)
        request(1, "prompt", "defaultText" to text, session = session)
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val args = response()
        assertEquals(session, args.getString(0))
        assertEquals(text, args.getString(3))
        assertEquals(4, args.length())
    }

    @Test
    fun malformedAndOtherSessionRequestsDoNotOpenOrConsumeADialog() {
        host.handleRequest("not-json")
        request(1, "unsupported")
        request(2, "alert", session = "old")
        host.handleRequest("""{"sessionId":"session","requestId":1.5,"kind":"alert"}""")
        assertTrue(webView.scripts.isEmpty())
        assertTrue(waiting.isEmpty())
        request(3, "alert")
        assertTrue(dialog().isShowing)
        assertEquals(listOf(true), waiting)
    }

    private fun request(id: Long, kind: String, vararg fields: Pair<String, Any>, session: String = "session") {
        val obj = JSONObject().put("sessionId", session).put("requestId", id).put("kind", kind).put("title", "请求$id")
        fields.forEach { (key, value) -> obj.put(key, value) }
        host.handleRequest(obj.toString())
        // Dialog.show 的 OnShowListener 通过主线程消息投递；先完成展示再模拟用户操作。
        idleMainLooper()
    }

    private fun acknowledge(id: Long, attempt: Long, accepted: Boolean, error: String = "", session: String = "session") {
        host.handleAcknowledged(JSONObject().put("sessionId", session).put("requestId", id)
            .put("attemptId", attempt).put("accepted", accepted).put("error", error).toString())
        idleMainLooper()
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()

    private fun response(index: Int = webView.scripts.lastIndex): JSONArray {
        val script = webView.scripts[index]
        assertTrue(script.startsWith("window.SleepySgShim.respond("))
        assertTrue(script.endsWith(");"))
        return JSONArray("[" + script.removePrefix("window.SleepySgShim.respond(").removeSuffix(");") + "]")
    }
}
