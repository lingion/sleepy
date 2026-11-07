package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwThuCapturePayload
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pinned source fetches this public calendar natively, outside the school's WebView. */
internal object JwThuSemesterCapture {
    // Snapshot current form properties without changing the page's form or event handlers.
    internal const val LIVE_HTML_JS = """
(function() {
  var doc = win.document;
  var root = doc.documentElement.cloneNode(true);
  var live = doc.querySelectorAll('[name=p_xnxq]');
  var saved = root.querySelectorAll('[name=p_xnxq]');
  for (var i = 0; i < live.length; i++) {
    if (!saved[i]) throw new Error('semester snapshot mismatch');
    if (String(live[i].tagName).toLowerCase() === 'select') {
      for (var j = 0; j < live[i].options.length; j++) {
        if (live[i].options[j].selected) saved[i].options[j].setAttribute('selected', 'selected');
        else saved[i].options[j].removeAttribute('selected');
      }
    } else saved[i].setAttribute('value', String(live[i].value || ''));
  }
  return root.outerHTML;
})()
"""

    suspend fun capture(html:String):String = withContext(Dispatchers.IO) {
        captureWith(html) { target ->
            val connection=URL(target).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000
            connection.readTimeout=15_000
            connection.setRequestProperty("Accept","application/json")
            try {
                check(connection.responseCode==200) { "清华学期日历请求失败 HTTP ${connection.responseCode}" }
                connection.inputStream.use { stream ->
                    // A bounded public metadata response; never carry school cookies to this host.
                    val output=ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true) {
                        val count=stream.read(buffer)
                        if(count<0) break
                        check(output.size()+count<=2*1024*1024) { "清华学期日历响应过大" }
                        output.write(buffer,0,count)
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                }
            } finally { connection.disconnect() }
        }
    }

    internal suspend fun captureWith(html:String,load:suspend (String)->String):String {
        val target=JwThuCapturePayload.calendarUrl(html) ?: throw IllegalArgumentException("请打开清华所选学期课表后再导入")
        return JwThuCapturePayload.wrap(html,load(target))
    }
}
