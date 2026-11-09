package com.lingion.sleepy.ui.screen.imports

import org.json.JSONObject

/** Execution budget ends on a protocol result, never on evaluateJavascript's return value. */
internal class JwFetchBudget(private val now: () -> Long) {
    var captureId: String? = null
        private set
    private var remaining = 0L
    private var runningSince: Long? = null

    fun begin(id: String, timeoutMs: Long) {
        require(timeoutMs > 0)
        captureId = id
        remaining = timeoutMs
        runningSince = now()
    }

    fun remainingMs(id: String): Long? {
        if (captureId != id || runningSince == null) return null
        return (remaining - (now() - runningSince!!).coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun setWaiting(id: String, waiting: Boolean) {
        if (captureId != id) return
        if (waiting && runningSince != null) {
            remaining = remainingMs(id) ?: remaining
            runningSince = null
        } else if (!waiting && runningSince == null) {
            runningSince = now()
        }
    }

    fun complete(id: String): Boolean {
        if (captureId != id) return false
        cancel()
        return true
    }

    fun cancel() {
        captureId = null
        runningSince = null
        remaining = 0
    }
}

/** Capture the native bridge lexically so a late result cannot adopt another capture's ID. */
internal fun scopeJwFetchScript(script: String, captureId: String): String = """
(function(__sleepyNativeBridge){
  var __sleepyCaptureBridge = {onWiseduResult:function(json){
    var payload;
    try { payload = JSON.parse(json); }
    catch(e) { payload = {ok:false,err:'教务响应格式无效'}; }
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      payload = {ok:false,err:'教务响应格式无效'};
    }
    payload.captureId = ${JSONObject.quote(captureId)};
    __sleepyNativeBridge.onWiseduResult(JSON.stringify(payload));
  }};
  ${script.replace("window.__sleepyBridge", "__sleepyCaptureBridge")}
})(window.__sleepyBridge);
""".trimIndent()
