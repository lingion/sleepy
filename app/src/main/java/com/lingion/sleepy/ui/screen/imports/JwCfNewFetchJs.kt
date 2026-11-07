package com.lingion.sleepy.ui.screen.imports

/**
 * 新青果 NTSS (新版青果/乘方, 江西中医药大学 jiaowu.jxutcm.edu.cn) WebView 内 fetch 课表 JSON。
 *
 * 现有适配约定: /new/student/xsgrkb/week.page, FullCalendar agendaWeek 渲染。
 * 原注释引用的采集包未在仓库找到；当前验证为离线合成响应，真实部署仍待复核。
 * 页面 DOM 无任何课表数据 — 课程行由页面脚本 $.post('/new/student/xsgrkb/getCalendarWeekDatas')
 * 异步注入, 禁走 outerHTML 抓取。
 *
 * 现有适配覆盖的帧结构: 课表页深嵌同源 iframe —
 *   top(/new/welcome.page?ui=new) → iframe4(main.page) → frame(/new/student/xsgrkb/week.page?xnxqdm=202601)。
 * evaluateJavascript 只注入主 frame, 顶层 location.pathname 不含课表路径、
 * 顶层 document 无 #xnxqdm/businessHours — 本 JS 必须先下钻同源 frame 找到
 * 课表 window (findTargetWin), DOM/参数/fetch 全部以该 window 为准。
 *
 * 流程 (复用 __sleepyBridge.onWiseduResult 同一回调通道):
 *   1) 路径指纹: /new/student/xsgrkb, 否则 NOT_ON_TIMETABLE
 *   2) 学期: 课表 frame URL ?xnxqdm= → frame DOM #xnxqdm → GET week.page 抠 <select>
 *      取 selected 项 (服务端默认 = 当前学期, 无需用户交互)
 *   3) 开学日: POST /new/xlxx/getDatesOfWeek {xnxqdm, zc:'1'} → xqmc=='1' 行的 rq
 *      (第一周周一) → startDate 回传确认页预填 + 逐周 d1/d2 推算
 *   4) 节次时间: 页面 businessHours = $.parseJSON('[…]') → periods [{node,start,end}]
 *      (除回传确认页外, 同时内嵌进 envelope 供 parser 在 ps/pe 空串时按时间反推节点)
 *   5) 读取当前课表 frame 的 #zc options，按明确周次逐周请求 getCalendarWeekDatas。
 *      返回多个周次不能证明覆盖整学期；不使用固定 22 周或空 zc 作为完整性依据。
 *      缺少或含非法周次时返回错误，等待页面准备好明确的周次列表。
 *   6) 合并 {"weeks":[{week,rows},…], periods:[…]} 组合源 + sleepyCfNtss 标记回传,
 *      Kotlin 路由 JwImportViewModel.parseHtml(..., "cf_new") → JwCfNewParser
 *
 * 优先透传当前页面或同源框架中的 lay-tips 课表；仅 NTSS 页面走接口采集。
 * 任一逐周请求失败时整体失败，不能把剩余周作为完整课表返回。
 * 跨语言 invariant: JS 只做 fetch + 传输层透传, 禁止解码协议字段 (解码唯一落点 = JwCfNewParser)。
 */
const val CF_NEW_FETCH_JS = """(function(){
  var finished = false;
  var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  var deadline = setTimeout(function(){ fail('TIMEOUT', '课表读取超时，请重试'); }, 20000);
  function finish(payload){
    if (finished) return;
    finished = true;
    clearTimeout(deadline);
    if (controller) controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }
  function fail(kind, err){
    finish({
      ok:false, kind:kind, err:err||'', format:'cf_new'
    });
  }
  function ok(data, startDate, periods){
    finish({
      ok:true, data:data, startDate:startDate||'', periods:periods||[], format:'cf_new'
    });
  }
  function fetchText(url, options){
    if (finished) return Promise.reject(new Error('采集已结束'));
    if (controller) options.signal = controller.signal;
    return W.fetch(url, options).then(function(r){
      if (!r.ok) throw new Error('课表请求失败 HTTP ' + r.status);
      return r.text();
    });
  }
  function courseRows(text){
    var res = JSON.parse(text);
    if (!res || typeof res.code !== 'number' || res.code < 0 || !Array.isArray(res.data)) {
      throw new Error('课表接口未返回有效课程数组');
    }
    return res.data;
  }
  function postForm(url, params){
    var body = Object.keys(params).map(function(k){
      return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return fetchText(url, {
      method:'POST',
      credentials:'include',
      headers:{
        'X-Requested-With':'XMLHttpRequest',
        'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'
      },
      body: body
    });
  }
  function addDays(iso, n){
    var p = iso.split('-');
    var d = new Date(parseInt(p[0],10), parseInt(p[1],10)-1, parseInt(p[2],10));
    d.setDate(d.getDate() + n);
    var mm = ('0' + (d.getMonth()+1)).slice(-2);
    var dd = ('0' + d.getDate()).slice(-2);
    return d.getFullYear() + '-' + mm + '-' + dd;
  }
  function findTargetWin(win, depth){
    if (!win || depth > 8) return null;
    var p = '';
    try { p = win.location.pathname || ''; } catch(e) { return null; }
    if (p.indexOf('/new/student/xsgrkb') >= 0) return win;
    for (var i = 0; i < win.frames.length; i++) {
      var found = findTargetWin(win.frames[i], depth + 1);
      if (found) return found;
    }
    return null;
  }
  function findHtmlTable(win, depth){
    if (!win || depth > 8) return null;
    try {
      var tips = win.document.querySelectorAll('[lay-tips]');
      for (var ti = 0; ti < tips.length; ti++) {
        if ((tips[ti].getAttribute('lay-tips') || '').indexOf('上课任务[') >= 0) {
          return win.document.documentElement.outerHTML;
        }
      }
      for (var fi = 0; fi < win.frames.length; fi++) {
        var html = findHtmlTable(win.frames[fi], depth + 1);
        if (html) return html;
      }
    } catch(e) {}
    return null;
  }
  try {
    var html = findHtmlTable(window, 0);
    if (html) { ok(html, '', []); return; }
    // 课表页嵌在 main.page 的同源 iframe 里 — 下钻找课表 window, 顶层不在课表页属正常
    var W = findTargetWin(window, 0);
    if (!W) {
      fail('NOT_ON_TIMETABLE', '请先进入个人课表页(未找到 /new/student/xsgrkb 页面框架)');
      return;
    }

    // 0. 节次时间: 页面 businessHours = $.parseJSON('[…]') — 传输层透传
    var periods = [];
    try {
      var bh = W.document.documentElement.innerHTML.match(/businessHours\s*=\s*\$\.parseJSON\('([\s\S]*?)'\)/);
      if (!bh) bh = W.document.documentElement.innerHTML.match(/businessHours\s*=\s*(\[\{[\s\S]*?\}\])\s*[;,]/);
      if (bh) {
        var bhArr = JSON.parse(bh[1]);
        for (var bi = 0; bi < bhArr.length; bi++) {
          periods.push({
            node: parseInt(bhArr[bi].jcdm, 10),
            start: String(bhArr[bi].qssj || '').substring(0, 5),
            end: String(bhArr[bi].jssj || '').substring(0, 5)
          });
        }
        periods.sort(function(a,b){ return a.node - b.node; });
      }
    } catch(e) { periods = []; }

    // 1. 学期: frame URL ?xnxqdm= → frame DOM #xnxqdm → GET week.page 抠 select (selected 优先)
    new Promise(function(resolve, reject){
      // 课表 frame URL 自带 xnxqdm (week.page?xnxqdm=202601) — 最直接
      var qm = W.location.search.match(/[?&]xnxqdm=([^&]+)/);
      if (qm) { resolve(decodeURIComponent(qm[1])); return; }
      var sel = W.document.querySelector('#xnxqdm');
      if (sel && sel.value) { resolve(String(sel.value).trim()); return; }
      fetchText('/new/student/xsgrkb/week.page', {credentials:'include'})
        .then(function(html){
          var block = (html.match(/<select[^>]*id=["']xnxqdm["'][^>]*>([\s\S]*?)<\/select>/i) || [])[1] || '';
          var optRe = /<option[^>]*value=["']([^"']*)["'][^>]*>/gi;
          var m, selected = '', first = '';
          while ((m = optRe.exec(block)) !== null) {
            if (!first) first = m[1];
            if (/\bselected\b/i.test(m[0])) { selected = m[1]; break; }
          }
          var term = selected || first;
          if (!term) reject(new Error('未取到学期列表(xnxqdm), 请在个人课表页停留后再点导入'));
          else resolve(term);
        })
        .catch(function(e){ reject(e); });
    }).then(function(xnxqdm){
      // 2. 第一周周一 (开学日): getDatesOfWeek zc=1 里 xqmc=='1' 的 rq
      return postForm('/new/xlxx/getDatesOfWeek', {xnxqdm: xnxqdm, zc: '1'})
        .then(function(text){
          var base = '';
          try {
            var arr = JSON.parse(text);
            for (var di = 0; di < arr.length; di++) {
              if (String(arr[di].xqmc) === '1') { base = String(arr[di].rq || ''); break; }
            }
          } catch(e) {}
          return base;
        })
        .catch(function(){ return ''; })
        .then(function(base){
          // 3. Use the timetable's explicit week options; absence is not a 22-week semester.
          var weekSelect = W.document.querySelector('#zc');
          if (!weekSelect || !weekSelect.options) {
            fail('WEEK_RANGE_UNAVAILABLE', '未取得本学期周次列表，请等待个人课表页加载完成后重试');
            return;
          }
          var weekNums = [];
          for (var wi = 0; wi < weekSelect.options.length; wi++) {
            var rawWeek = String(weekSelect.options[wi].value).trim();
            if (rawWeek === '' || rawWeek === '0') continue;
            if (!/^[0-9]+$/.test(rawWeek) || Number(rawWeek) < 1 || Number(rawWeek) > 366) {
              fail('WEEK_RANGE_UNAVAILABLE', '课表周次列表包含无法识别的值，请重新打开个人课表页');
              return;
            }
            var week = Number(rawWeek);
            if (weekNums.indexOf(week) < 0) weekNums.push(week);
          }
          if (weekNums.length === 0) {
            fail('WEEK_RANGE_UNAVAILABLE', '个人课表页尚未提供本学期周次列表');
            return;
          }
          weekNums.sort(function(a,b){ return a-b; });
          var jobs = weekNums.map(function(w){
            var p = {xnxqdm: xnxqdm, zc: String(w)};
            if (base) {
              p.d1 = addDays(base, 7 * (w - 1)) + ' 00:00:00';
              p.d2 = addDays(base, 7 * w) + ' 00:00:00';
            }
            return postForm('/new/student/xsgrkb/getCalendarWeekDatas', p)
              .then(function(text){ return {week: w, rows: courseRows(text)}; });
          });
          return Promise.all(jobs)
            .then(function(good){
              if (good.length === 0) {
                fail('SESSION_EXPIRED', '课表接口无有效响应, 请确认已登录教务并进入个人课表页后再点导入');
                return;
              }
              ok(JSON.stringify({
                sleepyCfNtss: true,
                xnxqdm: xnxqdm,
                requestedWeeks: weekNums,
                weeks: good,
                periods: periods
              }), base, periods);
            });
        });
    }).catch(function(e){
      fail('FORMAT_ERROR', String(e));
    });
  } catch(err) {
    fail('FORMAT_ERROR', String(err));
  }
})();
"""
