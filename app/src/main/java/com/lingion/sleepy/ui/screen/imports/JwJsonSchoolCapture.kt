package com.lingion.sleepy.ui.screen.imports

/** Logged-in same-origin collection. SHSMU dates describe the selected date range. */
internal object JwJsonSchoolCapture {
    fun scriptFor(type: String?): String? = when (type) {
        "sustech", "shsmu", "ygu", "zju_post" -> SCRIPT.replace("__SCHOOL_TYPE__", type)
        else -> null
    }

    private const val SCRIPT = """
(async function () {
  'use strict';
  var type = '__SCHOOL_TYPE__', finished = false, W = null;
  var controller = typeof AbortController === 'function' ? new AbortController() : null;
  var timer = setTimeout(function () { fail('课表读取超时，请等待页面加载完成后重试'); }, 60000);
  function finish(result) {
    if (finished) return;
    finished = true; clearTimeout(timer);
    if (controller) controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message) { finish({ok:false, format:type, err:message}); }
  function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
  function text(value) { return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''; }
  function query(name, root) {
    var values = [], doc = root || W.document;
    Array.prototype.forEach.call(doc.querySelectorAll('[name="' + name + '"],#' + name), function (node) {
      var value = text(node.value);
      if (value && values.indexOf(value) < 0) values.push(value);
    });
    if (values.length > 1) throw Error('页面有多个不同的' + name + '值，请仅打开需要导入的课表');
    return values[0] || '';
  }
  function eitherName(first, second) {
    var a = query(first), b = query(second);
    if (a && b && a !== b) throw Error('页面学期控件值不一致');
    return a || b;
  }
  function candidateFrames(w, depth, candidates) {
    if (depth > 8) throw Error('课表页面嵌套过深，请直接打开需要导入的课表');
    var children = [];
    try {
      var host = w.location.hostname.toLowerCase(), path = w.location.pathname;
      var allowed = type === 'sustech' ? host === 'tis.sustech.edu.cn' :
        type === 'shsmu' ? (host === 'jwstu.shsmu.edu.cn' || host === 'webvpn2.shsmu.edu.cn') && /\/Home\//i.test(path) :
        type === 'ygu' ? host === 'ygu.edu.cn' : !!w.document.querySelector('#kcbForm');
      if (allowed && /^https?:$/.test(w.location.protocol)) candidates.push(w);
      for (var i=0; i<w.frames.length; i++) children.push(w.frames[i]);
    } catch (_) {}
    for (var j=0; j<children.length; j++) candidateFrames(children[j], depth+1, candidates);
    return candidates;
  }
  try {
    var candidates = candidateFrames(window, 0, []);
    if (candidates.length !== 1) throw Error(candidates.length ? '页面有多个课表，请仅打开需要导入的课表' : '请登录教务系统并打开需要导入的课表页面');
    W = candidates[0];
  } catch (selectionError) { fail(String(selectionError)); return; }
  var initialHref = W.location.href, initialDocument = W.document;
  function samePage() {
    if (finished) throw Error('采集已结束');
    var candidates = candidateFrames(window, 0, []);
    if (candidates.length !== 1 || candidates[0] !== W || W.document !== initialDocument || W.location.href !== initialHref) throw Error('采集期间课表页面已切换，请重新导入');
  }
  async function request(target, body, json) {
    samePage();
    var url = new URL(target, initialHref);
    if (url.origin !== W.location.origin || url.username || url.password) throw Error('课表接口与当前登录页面不同源');
    var init = {method:body === undefined ? 'GET' : 'POST', credentials:'include', mode:'same-origin', redirect:'error'};
    if (controller) init.signal = controller.signal;
    if (body !== undefined) {
      init.headers = {'Content-Type':json ? 'application/json;charset=UTF-8' : 'application/x-www-form-urlencoded;charset=UTF-8'};
      if (json) init.body = JSON.stringify(body);
      else { var form = new URLSearchParams(); Object.keys(body).forEach(function (key) { form.append(key, body[key]); }); init.body = form.toString(); }
    }
    var response = await W.fetch(url.href, init);
    samePage();
    if (!response.ok) throw Error('课表请求失败 HTTP ' + response.status);
    var finalUrl = new URL(response.url || url.href, url.href);
    if (finalUrl.origin !== url.origin || finalUrl.pathname !== url.pathname || finalUrl.search !== url.search) throw Error('登录状态已变化，请重新登录');
    var raw = await response.text(); samePage();
    return raw;
  }
  function parseJson(raw) {
    var result = JSON.parse(raw);
    if (!object(result) && !Array.isArray(result)) throw Error('课表接口未返回 JSON 对象或数组');
    if (object(result) && (result.success === false || result.success === 'false')) throw Error('课表接口未返回成功结果');
    return result;
  }
  async function jsonRequest(target, body, json) { return parseJson(await request(target, body, json)); }
  function date(value) {
    value = text(value);
    if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)) return '';
    var ms = Date.parse(value + 'T00:00:00Z');
    return Number.isFinite(ms) && new Date(ms).toISOString().slice(0,10) === value && value.slice(0,4) !== '0000' ? value : '';
  }
  function recentRequest(pattern) {
    var entries = W.performance && W.performance.getEntriesByType('resource') || [];
    return entries.map(function (entry) {
      try { var url = new URL(entry.name, initialHref); return url.origin === W.location.origin && pattern.test(url.pathname) ? {url:url, time:entry.startTime} : null; }
      catch (_) { return null; }
    }).filter(Boolean).sort(function (a,b) { return b.time-a.time; })[0];
  }
  async function sustech() {
    var originalXn = eitherName('xn','XN'), originalXq = eitherName('xq','XQ');
    var xn = originalXn, xq = originalXq;
    if (!xn && !xq) {
      // Existing vendored SUSTECH script, not the pinned WakeUp login implementation.
      var current = await jsonRequest('/component/querydangqianxnxq', {});
      xn = text(current.XN); xq = text(current.XQ);
    }
    if (/^[0-9]{4}$/.test(xn)) xn += '-' + (Number(xn)+1);
    var years = /^([0-9]{4})-([0-9]{4})$/.exec(xn);
    if (!years || Number(years[2]) !== Number(years[1])+1 || !/^[0-9]+$/.test(xq) || Number(xq) <= 0) throw Error('未取得明确的学年学期，请在课表页面选择学期');
    var result = await jsonRequest('/xszykb/queryxszykbzong', {xn:xn,xq:xq});
    var rows = Array.isArray(result) ? result : Array.isArray(result.content) ? result.content : result.data;
    if (!Array.isArray(rows) || !rows.every(function (row) { return object(row) && (row.KEY === 'bz' || /^xq[1-7]_jc[0-9]+$/.test(text(row.KEY)) && typeof row.SKSJ === 'string'); })) throw Error('课表响应缺少完整课程数组');
    if (eitherName('xn','XN') !== originalXn || eitherName('xq','XQ') !== originalXq) throw Error('采集期间所选学期发生变化');
    if (!originalXn && !originalXq) {
      var check = await jsonRequest('/component/querydangqianxnxq', {});
      var checkXn = text(check.XN); if (/^[0-9]{4}$/.test(checkXn)) checkXn += '-' + (Number(checkXn)+1);
      if (checkXn !== xn || text(check.XQ) !== xq) throw Error('采集期间当前学期发生变化');
    }
    if (eitherName('xn','XN') !== originalXn || eitherName('xq','XQ') !== originalXq) throw Error('采集期间所选学期发生变化');
    finish({ok:true,format:type,data:JSON.stringify(rows),semester:xn+'-'+xq});
  }
  async function shsmu() {
    var current = new URL(initialHref), observed = /\/Home\/GetCurriculumTable$/i.test(current.pathname) ? current : null;
    var fromPerformance = !observed;
    if (!observed) { var latest = recentRequest(/\/Home\/GetCurriculumTable$/i); observed = latest && latest.url; }
    var controlStart = query('Start'), controlEnd = query('End');
    fromPerformance = fromPerformance && (!controlStart || !controlEnd);
    var rangeStart = controlStart || (observed && observed.searchParams.get('Start')) || '';
    var rangeEnd = controlEnd || (observed && observed.searchParams.get('End')) || '';
    rangeStart = date(rangeStart); rangeEnd = date(rangeEnd);
    if (!rangeStart || !rangeEnd || rangeEnd < rangeStart) throw Error('请在课表页面选择待导入的起止日期，等待课表加载后再导入');
    var marker = /\/Home\//i.exec(current.pathname);
    var target = new URL(current.pathname.slice(0,marker.index) + '/Home/GetCurriculumTable', initialHref);
    target.searchParams.set('Start',rangeStart); target.searchParams.set('End',rangeEnd);
    var result = await jsonRequest(target.href);
    if (!object(result) || !Array.isArray(result.list) || !result.list.every(object)) throw Error('课表接口未返回课程列表');
    var checkStart = query('Start'), checkEnd = query('End');
    if (checkStart !== controlStart || checkEnd !== controlEnd) throw Error('采集期间日期范围发生变化');
    if (fromPerformance) {
      var latestAfter = recentRequest(/\/Home\/GetCurriculumTable$/i), observedAfter = latestAfter && latestAfter.url;
      if (!observedAfter || observedAfter.pathname !== target.pathname || date(observedAfter.searchParams.get('Start')) !== rangeStart || date(observedAfter.searchParams.get('End')) !== rangeEnd) throw Error('采集期间日期范围发生变化');
    }
    result.sleepyShsmuCapture = true;
    result.rangeStart = rangeStart; result.rangeEnd = rangeEnd;
    // A requested range may be a single displayed week. It does not prove week one.
    delete result.semesterStartDate; delete result.semesterStartBasis;
    finish({ok:true,format:type,data:JSON.stringify(result),requiresSemesterStart:true,calendarBasis:'unknown'});
  }
  function yguSelected() {
    var selected = query('xqjc');
    var url = new URL(initialHref), direct = text(url.searchParams.get('xqjc'));
    var hashQuery = url.hash.indexOf('?') >= 0 ? new URLSearchParams(url.hash.slice(url.hash.indexOf('?')+1)).get('xqjc') : '';
    var values = [selected,direct,text(hashQuery)].filter(Boolean);
    if (values.some(function (value) { return value !== values[0]; })) throw Error('页面学期值不一致，请重新选择学期');
    return values[0] || '';
  }
  async function ygu() {
    var selected = yguSelected();
    var terms = await jsonRequest('/api/jw/xlgl/listR', {}, true);
    if (!object(terms) || text(terms.code) !== '0' || !Array.isArray(terms.data)) throw Error('获取学期列表失败');
    var values = terms.data.map(function (item) { return object(item) ? text(item.xqjc) : ''; });
    if (values.some(function (value) { return !value; }) || new Set(values).size !== values.length) throw Error('学期列表不完整或重复');
    var term = selected || (values.length === 1 ? values[0] : '');
    if (!term || values.indexOf(term) < 0) throw Error('请在课表页面明确选择学期后再导入');
    var user = await jsonRequest('/api/system/user/getInfo');
    if (!object(user) || text(user.code) !== '0' || !object(user.user)) throw Error('获取用户基本信息失败');
    var number = text(user.user.studentsNumber), name = text(user.user.nickName);
    if (!number || !name) throw Error('当前登录信息缺少学生学号或姓名');
    var result = await jsonRequest('/api/jw/kpkglkcb/student', {xm:name,xh:number,xqjc:term}, true);
    if (!object(result) || text(result.code) !== '0' || !Array.isArray(result.data) || !result.data.every(function (day) {
      return object(day) && Array.isArray(day.weekList) && day.weekList.every(function (week) { return object(week) && Array.isArray(week.kcbVoList); });
    })) throw Error('课表接口未返回完整日程数组');
    if (result.xqjc !== undefined && text(result.xqjc) !== term) throw Error('课表响应学期与所选学期不同');
    if (yguSelected() !== selected) throw Error('采集期间所选学期发生变化');
    finish({ok:true,format:type,data:JSON.stringify(result),semester:term});
  }
  function weekValues(doc) {
    var select = doc.querySelector('#zc');
    if (!select) return null;
    var options = Array.prototype.slice.call(select.options || []);
    if (!options.length) throw Error('课表周次列表为空');
    var all = options.filter(function (option) { return /全/.test(text(option.textContent)); });
    if (all.length > 1) throw Error('课表包含多个全部周次选项');
    var chosen = all.length ? all : options;
    var values = chosen.map(function (option) { return text(option.value); });
    if (values.some(function (value) { return !/^-?[0-9]+$/.test(value) || !Number.isSafeInteger(Number(value)) || !all.length && Number(value) <= 0; }) || new Set(values).size !== values.length) throw Error('课表周次列表无效');
    return values;
  }
  async function zju() {
    var form = W.document.querySelector('#kcbForm');
    if (!form || W.document.querySelector('input[type="password"]')) throw Error('请打开研究生课表页面再导入');
    var semester = [query('xnxq'),query('xn'),query('xq')];
    var values = weekValues(W.document), signature = JSON.stringify(values), pages = [];
    if (!values) pages.push(W.document.documentElement.outerHTML);
    else for (var i=0; i<values.length; i++) {
      var url = new URL(initialHref); url.searchParams.set('zc',values[i]);
      var raw = await request(url.href), page = new W.DOMParser().parseFromString(raw,'text/html');
      if (!page.querySelector('#kcbForm') || page.querySelector('input[type="password"]')) throw Error('第'+values[i]+'周未返回课表页面，请重新登录');
      var responseWeeks = weekValues(page);
      if (JSON.stringify(responseWeeks) !== signature) throw Error('采集期间课表周次列表发生变化');
      pages.push(raw);
    }
    if (JSON.stringify(weekValues(W.document)) !== signature) throw Error('采集期间周次列表发生变化');
    if (JSON.stringify([query('xnxq'),query('xn'),query('xq')]) !== JSON.stringify(semester)) throw Error('采集期间所选学期发生变化');
    finish({ok:true,format:type,data:JSON.stringify(pages),requestedWeeks:values || []});
  }
  try { await (type === 'sustech' ? sustech() : type === 'shsmu' ? shsmu() : type === 'ygu' ? ygu() : zju()); }
  catch (error) { fail(text(error && error.message) || '课表读取失败'); }
})();
"""
}
