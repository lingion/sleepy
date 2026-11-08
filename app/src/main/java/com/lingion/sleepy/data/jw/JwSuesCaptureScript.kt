package com.lingion.sleepy.data.jw

/** Reads the most recently observed same-origin print-data request, without guessing a semester. */
internal object JwSuesCaptureScript {
    val script = """
(async function () {
  function finish(value) { window.__sleepyBridge.onWiseduResult(JSON.stringify(value)); }
  function hasActivities(table) { return table && typeof table === 'object' && Array.isArray(table.activities); }
  try {
    var entries = performance.getEntriesByType('resource').filter(function (entry) {
      try {
        var url = new URL(entry.name, location.href);
        return url.origin === location.origin && /\/print-data(?:\/|${'$'})/.test(url.pathname);
      } catch (_) { return false; }
    }).sort(function (a, b) { return a.startTime - b.startTime; });
    if (!entries.length) {
      finish({ok:false, err:'请打开本学期课表打印页面，等待课表加载后再点导入'});
      return;
    }
    var target = new URL(entries[entries.length - 1].name, location.href);
    var response = await fetch(target.href, {method:'GET', credentials:'include', redirect:'follow'});
    if (!response.ok) throw new Error('课表数据请求失败 HTTP ' + response.status);
    var finalUrl = new URL(response.url || target.href, location.href);
    if (finalUrl.origin !== location.origin || /\/(?:login|cas)(?:\/|${'$'})/i.test(finalUrl.pathname)) {
      throw new Error('请重新登录教务并打开课表打印页面');
    }
    var raw = await response.text();
    var root = JSON.parse(raw);
    if (!root || typeof root !== 'object' || Array.isArray(root)) throw new Error('返回数据不是课表对象');
    var tables = Array.isArray(root.studentTableVms) ? root.studentTableVms.slice() : [];
    if (root.studentTableVm && typeof root.studentTableVm === 'object') tables.push(root.studentTableVm);
    if (!tables.length || !tables.every(hasActivities)) throw new Error('返回数据缺少课程活动列表');
    finish({ok:true, data:raw, periods:[]});
  } catch (error) {
    finish({ok:false, err:String(error && error.message || error)});
  }
})();
    """.trimIndent()
}
