package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol

/**
 * USTC's singular studentTableVm endpoint. Course fields remain opaque until JwUstcParser.
 * The production script is exercised by tools/test-ustc-fetch.js with offline responses.
 */
object JwUstcCapture {
    fun scriptFor(type: String?): String? = if (type == JwProtocol.TYPE_USTC) FETCH_JS else null

    const val FETCH_JS = """(function(){
  var finished = false, deadline = null, controller = null;
  function finish(result) {
    if (finished) return;
    finished = true;
    if (deadline !== null) clearTimeout(deadline);
    if (controller) controller.abort();
    result.format = 'ustc';
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message) { finish({ok:false,err:message}); }
  function loginUrl(url) { return /\/(?:login|ucas-sso|cas|sso)(?:\/|$)/i.test(url.pathname); }
  function loginDocument(doc) { return !!doc.querySelector('input[type="password"]'); }
  function semesters(doc, live) {
    var choices = [], ids = [], controls = doc.querySelectorAll('select');
    for (var i = 0; i < controls.length; i++) {
      var control = controls[i], options = Array.prototype.slice.call(control.options || []);
      var terms = options.filter(function(option) {
        return /^\d+$/.test(String(option.value)) && /学期/.test(option.textContent || '');
      });
      if (!terms.length) continue;
      terms.forEach(function(option) { ids.push(String(option.value)); });
      var selected = terms.filter(function(option) { return option.hasAttribute('selected'); });
      var value = String(control.value || ''), index = options.findIndex(function(option) {
        return String(option.value) === value;
      });
      if (live && index >= 0 && terms.indexOf(options[index]) >= 0 &&
          (index > 0 || selected.length === 1 || terms.length === 1)) {
        choices.push(value);
      } else if (selected.length === 1) {
        choices.push(String(selected[0].value));
      } else if (selected.length > 1) {
        throw Error('学期选择不明确，请在课表页重新选择学期');
      } else if (terms.length === 1) {
        choices.push(String(terms[0].value));
      }
    }
    choices = choices.filter(function(value, index, all) { return all.indexOf(value) === index; });
    if (choices.length > 1) throw Error('课表页存在不同的学期选择，请重新选择学期');
    return {id:choices[0] || '', ids:ids};
  }
  (async function(){
    var initial = new URL(window.location.href);
    if (initial.origin !== 'https://jw.ustc.edu.cn' || loginUrl(initial) || loginDocument(window.document)) {
      throw Error('请先在中科大教务完成登录，再打开课表页导入');
    }
    var live = semesters(window.document, true);
    controller = new AbortController();
    deadline = setTimeout(function(){ fail('教务请求超时，请重新导入'); }, 18000);
    async function request(path, redirect) {
      var url = new URL(path, initial.origin);
      var response = await window.fetch(url.href, {method:'GET',credentials:'include',mode:'same-origin',
        redirect:redirect,signal:controller.signal});
      if (finished) throw Error('采集已结束');
      if (!response.ok) throw Error('教务请求失败 HTTP ' + response.status);
      if (!response.url) throw Error('教务响应缺少最终地址');
      var finalUrl = new URL(response.url);
      if (finalUrl.origin !== initial.origin || loginUrl(finalUrl)) throw Error('登录态已失效，请重新登录');
      var text = await response.text();
      if (finished) throw Error('采集已结束');
      return {url:finalUrl,text:text,contentType:response.headers.get('content-type') || ''};
    }
    var index = await request('/for-std/course-table', 'follow');
    var match = index.url.pathname.match(/^\/for-std\/course-table\/(\d+)\/?$/);
    if (!match) throw Error('教务没有返回有效学生课表地址');
    if (!/^\s*</.test(index.text) || !/text\/html/i.test(index.contentType)) {
      throw Error('教务课表入口响应格式无效');
    }
    var doc = new DOMParser().parseFromString(index.text, 'text/html');
    if (loginDocument(doc)) throw Error('登录态已失效，请重新登录');
    var server = semesters(doc, false), semesterId = live.id || server.id;
    if (!semesterId || server.ids.indexOf(semesterId) < 0) {
      throw Error('未确定学期，请在课表页明确选择学期后导入');
    }
    var studentId = match[1];
    var data = await request('/for-std/course-table/semester/' + semesterId +
      '/print-data/' + studentId + '?weekIndex=', 'error');
    if (/^\s*</.test(data.text)) throw Error('登录态已失效或教务返回了非课表页面');
    var payload;
    try { payload = JSON.parse(data.text); } catch(e) { throw Error('教务课表 JSON 格式无效'); }
    if (!payload || payload.success === false || payload.code === 401 ||
        !payload.studentTableVm || !Array.isArray(payload.studentTableVm.activities)) {
      throw Error('教务没有返回完整学期课表');
    }
    if (window.location.href !== initial.href || semesters(window.document, true).id !== live.id) {
      throw Error('采集期间页面或学期已改变，请重新导入');
    }
    finish({ok:true,data:data.text,semesterId:semesterId,studentId:studentId});
  })().catch(function(error){ fail(String(error.message || error)); });
})();"""
}
