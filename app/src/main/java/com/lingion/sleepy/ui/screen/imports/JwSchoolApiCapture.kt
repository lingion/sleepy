package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol

/** Collect complete API responses in the user's logged-in frame; course decoding stays in Kotlin. */
object JwSchoolApiCapture {
    fun scriptFor(type: String?): String? = when (type) {
        JwProtocol.TYPE_SUES -> com.lingion.sleepy.data.jw.JwSuesCaptureScript.script
        JwProtocol.TYPE_CCIBE, JwProtocol.TYPE_CPPU, JwProtocol.TYPE_JLU_POST -> SCHOOL_API_FETCH_JS.replace("__SCHOOL_TYPE__", type)
        else -> null
    }
}

private const val SCHOOL_API_FETCH_JS = """(function(){
  var type = '__SCHOOL_TYPE__';
  var finished = false;
  var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  var deadline = setTimeout(function(){ fail('课表读取超时，请重试'); }, 20000);
  function finish(result) {
    if (finished) return;
    if (result.ok) {
      try { samePage(); } catch(e) { result = {ok:false, err:e.message, format:type}; }
    }
    finished = true;
    clearTimeout(deadline);
    if (controller) controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message) { finish({ok:false, err:message, format:type}); }
  function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
  function scalar(value) {
    return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : '';
  }
  function positiveInt(value) {
    var text = scalar(value);
    return /^[0-9]+$/.test(text) && Number(text) >= 1 && Number(text) <= 366 ? Number(text) : 0;
  }
  var selector = type === 'ccibe' ? '#rc_select_0_list [aria-selected="true"]' :
    type === 'jlu_post' ? '#myXnxqSelect' : '.je-grid-btnbar-queryStrategy';
  function candidateFrames(w, depth, candidates) {
    if (depth > 8) throw new Error('课表页面嵌套过深，请直接打开需要导入的课表');
    var children = [];
    try {
      var host = w.location.hostname.toLowerCase();
      var allowed = type === 'ccibe' ? host === 'my.vpn.ccibe.edu.cn' || host === 'my.ccibe.edu.cn' :
        type === 'jlu_post' ? (host === 'jlu.edu.cn' || host.endsWith('.jlu.edu.cn')) && w.location.pathname.indexOf('/gsapp/sys/') >= 0 :
        host === 'jw.cppu.edu.cn';
      if (allowed && /^https?:$/.test(w.location.protocol) && w.document.querySelector(selector)) candidates.push(w);
      for (var i=0; i<w.frames.length; i++) children.push(w.frames[i]);
    } catch(e) {}
    for (var j=0; j<children.length; j++) candidateFrames(children[j], depth+1, candidates);
  }
  function controlState(doc) {
    var nodes = doc.querySelectorAll('input,select,textarea');
    if (nodes.length > 10000) throw new Error('页面控件过多，请直接打开课表');
    return JSON.stringify(Array.prototype.map.call(nodes, function(n){
      return [n.tagName,n.name || '',n.id || '',n.type || '',scalar(n.value),!!n.checked,
        n.options ? Array.prototype.map.call(n.options,function(o){return [scalar(o.value),!!o.selected];}) : null];
    }));
  }
  function selectionState(doc) {
    var nodes = doc.querySelectorAll(selector);
    return JSON.stringify(Array.prototype.map.call(nodes, function(n){
      if (type === 'ccibe') return scalar(n.textContent);
      if (type === 'jlu_post') return scalar(n.value) || scalar(n.options && n.options[0] && n.options[0].value);
      return n.parentElement ? Array.prototype.filter.call(n.parentElement.childNodes,function(c){return c.nodeType === 3;})
        .map(function(c){return c.textContent || '';}).join(' ') : '';
    }));
  }
  var W, initialDocument, initialHref, initialControls, initialSelection;
  try {
    var candidates = []; candidateFrames(window,0,candidates);
    if (candidates.length !== 1) throw new Error('请仅打开一个已选择学期的教务课表页面');
    W = candidates[0]; initialDocument = W.document; initialHref = W.location.href;
    initialControls = controlState(initialDocument); initialSelection = selectionState(initialDocument);
  } catch(e) { fail(e.message); return; }
  function samePage() {
    if (finished) throw new Error('采集已结束');
    var candidates = []; candidateFrames(window,0,candidates);
    if (candidates.length !== 1 || candidates[0] !== W || W.document !== initialDocument || W.location.href !== initialHref)
      throw new Error('采集期间课表页面发生变化，请重试');
    if (controlState(initialDocument) !== initialControls || selectionState(initialDocument) !== initialSelection)
      throw new Error('采集期间所选学期或页面控件发生变化，请重试');
  }
  async function request(path, fields) {
    samePage();
    var url = new URL(path, initialHref);
    if (url.origin !== W.location.origin || url.username || url.password) throw new Error('课表地址与当前页面不一致');
    var options = {method:fields ? 'POST' : 'GET', credentials:'include', mode:'same-origin', redirect:'error'};
    if (controller) options.signal = controller.signal;
    if (fields) {
      var form = new FormData();
      Object.keys(fields).forEach(function(key){ form.append(key, fields[key]); });
      options.body = form;
    }
    var response = await W.fetch(url.href, options);
    samePage();
    if (!response.ok) throw new Error('课表请求失败 HTTP ' + response.status);
    var finalUrl = new URL(response.url || url.href, url.href);
    if (finalUrl.origin !== url.origin || finalUrl.pathname !== url.pathname || finalUrl.search !== url.search)
      throw new Error('登录状态已变化，请重新登录');
    var text = await response.text(); samePage();
    var result = JSON.parse(text);
    if (!object(result) || result.success === false || result.success === 'false') throw new Error('课表接口未返回成功结果');
    return result;
  }
  async function ccibe() {
    var selected = scalar(W.document.querySelector(selector).textContent);
    if (!selected) throw new Error('未找到已选择的学期');
    var path = '/api/manager/synchronize/synchronizestudent/curriculumNew';
    var first = await request(path + '?calendarId=' + encodeURIComponent(selected) + '&week=');
    var data = first.data;
    if (!object(data) || !object(data.calendar)) throw new Error('未取得学期日历');
    var calendar = data.calendar, id = scalar(calendar.id), count = positiveInt(calendar.allWeek);
    if (!id || !count || !scalar(calendar.startTime)) throw new Error('学期日历不完整');
    var weeks = [];
    for (var week=1; week<=count; week++) {
      var response = await request(path + '?calendarId=' + encodeURIComponent(id) + '&week=' + week);
      var page = response.data;
      if (!object(page) || !Array.isArray(page.wdkb)) throw new Error('第' + week + '周课表不完整');
      if (page.calendar && scalar(page.calendar.id) !== id) throw new Error('采集期间学期发生变化');
      weeks.push({week:week, data:page});
    }
    var check = await request(path + '?calendarId=' + encodeURIComponent(selected) + '&week=');
    var checked = check.data && check.data.calendar;
    if (!object(checked) || scalar(checked.id) !== id || positiveInt(checked.allWeek) !== count ||
        scalar(checked.startTime) !== scalar(calendar.startTime)) throw new Error('采集期间学期日历发生变化，请重试');
    finish({ok:true, format:type, data:JSON.stringify({calendar:calendar, time:data.time || [], weeks:weeks})});
  }
  async function cppu() {
    var control = W.document.querySelector(selector);
    // The upstream selector reads ownText(), excluding nested button labels.
    var label = control.parentElement ? Array.prototype.filter.call(control.parentElement.childNodes, function(n){
      return n.nodeType === 3;
    }).map(function(n){ return n.textContent || ''; }).join(' ') : '';
    var termPath = '/je/system/' + (label.indexOf('下一') >= 0 ? 'getNowNextXnxq' : 'getNowXnxq');
    var term = await request(termPath);
    var code = object(term.data) ? scalar(term.data.values && term.data.values.DM) : scalar(term.data);
    if (!code) throw new Error('未取得所选学期');
    var calendar = await request('/edumis_je/pkgl/pksz/xlsj/getClassFormDefault.action', {xnxqCode:code});
    var startDate = scalar(calendar.jxzqsrq);
    if (!startDate) throw new Error('未取得学期起始日期');
    var result = await request('/je/load', {
      funcCode:'V_JWBZK_PK_XSKBZHCX', funcId:'n3VPye8LJPUucKtVB1H', columnLazy:'true', mark:'false', postil:'',
      funcEdit:'false', coverJquery:'0', tableCode:'V_JWBZK_PK_XSKBZHCX', _isFunc_:'true',
      j_query:JSON.stringify({custom:[{type:'and',value:[{type:'=',code:'XNXQ_CODE',value:code,cn:'and'}]}],_custom_types:['strategy']}),
      j_order:'[]', dbQueryObj:'{}', page:'1', start:'0', limit:'100000'
    });
    if (!Array.isArray(result.rows)) throw new Error('课表接口未返回课程数组');
    for (var key of ['totalCount','total']) {
      if (result[key] !== undefined && result[key] !== null) {
        var total = scalar(result[key]);
        if (!/^[0-9]+$/.test(total) || Number(total) !== result.rows.length) throw new Error('课表响应不完整，请重试');
      }
    }
    var checkTerm = await request(termPath);
    var checkedCode = object(checkTerm.data) ? scalar(checkTerm.data.values && checkTerm.data.values.DM) : scalar(checkTerm.data);
    if (checkedCode !== code) throw new Error('采集期间所选学期发生变化，请重试');
    var checkCalendar = await request('/edumis_je/pkgl/pksz/xlsj/getClassFormDefault.action', {xnxqCode:code});
    if (scalar(checkCalendar.jxzqsrq) !== startDate) throw new Error('采集期间学期日历发生变化，请重试');
    result.startDate = startDate;
    finish({ok:true, format:type, data:JSON.stringify(result)});
  }
  async function jlu() {
    var select = W.document.querySelector(selector);
    var term = scalar(select.value) || scalar(select.options && select.options[0] && select.options[0].value);
    if (!term) throw new Error('未找到所选学期，请等待课表加载完成');
    var prefix = W.location.pathname.slice(0, W.location.pathname.indexOf('/gsapp/sys/'));
    var result = await request(prefix + '/gsapp/sys/wdkbapp/modules/xskcb/xsjxrwcx.do?XNXQDM=' + encodeURIComponent(term));
    var page = result.datas && result.datas.xsjxrwcx;
    if (!object(page) || !Array.isArray(page.rows)) throw new Error('课表接口未返回课程数组');
    if (page.totalSize !== undefined && page.totalSize !== null) {
      // WISEDU uses -1 when the query does not compute a total count.
      var total = scalar(page.totalSize);
      if (total !== '-1' && (!/^[0-9]+$/.test(total) || Number(total) !== page.rows.length)) {
        throw new Error('课表响应不完整，请重试');
      }
    }
    finish({ok:true, format:type, data:JSON.stringify(result)});
  }
  (type === 'ccibe' ? ccibe() : type === 'jlu_post' ? jlu() : cppu()).catch(function(e){
    fail(e && e.message ? e.message : '课表读取失败');
  });
})();"""
