package com.lingion.sleepy.ui.screen.imports

/**
 * 新青果 NTSS (新版青果/乘方, 江西中医药大学 jiaowu.jxutcm.edu.cn) WebView 内 fetch 课表 JSON。
 *
 * 页面形态: /new/student/xsgrkb/week.page, FullCalendar agendaWeek 渲染 (2026-09 采集包实锤)。
 * 页面 DOM 无任何课表数据 — 课程行由页面脚本 $.post('/new/student/xsgrkb/getCalendarWeekDatas')
 * 异步注入, 禁走 outerHTML 抓取。
 *
 * 帧结构 (2026-09-15 用户 DOM 树实锤): 课表页深嵌同源 iframe —
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
 *   5) 单请求全量: POST getCalendarWeekDatas zc='' (不带周次) → 服务端返回整学期,
 *      行 zc 为各行所属周 (2026-09-16 用户实测); 防御性检查响应含多个不同 zc 才采纳,
 *      否则回退逐周 POST zc=1..22 (行 zc == 请求周) — 兼容按周过滤的部署
 *      (传输层透传, 与 wisedu/CQU/BJTU 同级; 解码仍归 Kotlin parser)
 *   6) 合并 {"weeks":[{week,rows},…], periods:[…]} 组合源 + sleepyCfNtss 标记回传,
 *      Kotlin 路由 JwImportViewModel.parseHtml(..., "cf_new") → JwCfNewParser
 *
 * 失败兜底: 路径不对 → NOT_ON_TIMETABLE; 全部周请求失败 (登录页 HTML 解析不出 JSON)
 * → SESSION_EXPIRED; 其余异常 → FORMAT_ERROR。
 * 跨语言 invariant: JS 只做 fetch + 传输层透传, 禁止解码协议字段 (解码唯一落点 = JwCfNewParser)。
 */
const val CF_NEW_FETCH_JS = """(function(){
  function fail(kind, err){
    window.__sleepyBridge.onWiseduResult(JSON.stringify({
      ok:false, kind:kind, err:err||'', format:'cf_new'
    }));
  }
  function ok(data, startDate, periods){
    window.__sleepyBridge.onWiseduResult(JSON.stringify({
      ok:true, data:data, startDate:startDate||'', periods:periods||[], format:'cf_new'
    }));
  }
  function postForm(url, params){
    var body = Object.keys(params).map(function(k){
      return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return W.fetch(url, {
      method:'POST',
      credentials:'include',
      headers:{
        'X-Requested-With':'XMLHttpRequest',
        'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'
      },
      body: body
    }).then(function(r){ return r.text(); });
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
  try {
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
      W.fetch('/new/student/xsgrkb/week.page', {credentials:'include'})
        .then(function(r){ return r.text(); })
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
          // 3a. 单请求全量: zc 空时服务端返回整学期, 行 zc 为各行所属周 (2026-09-16 用户实测)
          var params = {xnxqdm: xnxqdm, zc: ''};
          if (base) {
            params.d1 = base + ' 00:00:00';
            params.d2 = addDays(base, 7) + ' 00:00:00';
          }
          return postForm('/new/student/xsgrkb/getCalendarWeekDatas', params)
            .then(function(text){
              var res = JSON.parse(text);
              var rows = (res && res.code >= 0 && res.data) ? res.data : [];
              var distinct = {};
              for (var ri = 0; ri < rows.length; ri++) {
                var z = String((rows[ri] && rows[ri].zc) || '');
                if (z) distinct[z] = 1;
              }
              // 全学期形态: 行 zc 覆盖多个周 — 单 bucket (week:0) 交 parser 按行 zc 归属;
              // 否则 (空/单周形态) 回退逐周兜底
              if (Object.keys(distinct).length > 1) return [{week: 0, rows: rows}];
              return null;
            })
            .catch(function(){ return null; })
            .then(function(full){
              if (full) return full;
              // 3b. 兜底: 逐周并行 POST 1..22 周 (页面 #zc 上限 22; 行 zc == 请求周,
              //     跨周重复行由 parser 按 key 合并)
              var weekNums = [];
              for (var w = 1; w <= 22; w++) { weekNums.push(w); }
              var jobs = weekNums.map(function(w){
                var p = {xnxqdm: xnxqdm, zc: String(w)};
                if (base) {
                  p.d1 = addDays(base, 7 * (w - 1)) + ' 00:00:00';
                  p.d2 = addDays(base, 7 * w) + ' 00:00:00';
                }
                return postForm('/new/student/xsgrkb/getCalendarWeekDatas', p)
                  .then(function(text){
                    var res = JSON.parse(text);
                    if (res && res.code >= 0 && res.data) return {week: w, rows: res.data};
                    return {week: w, rows: []};
                  })
                  .catch(function(){ return {week: w, rows: [], failed: true}; });
              });
              return Promise.all(jobs).then(function(results){
                var good = [];
                for (var gi = 0; gi < results.length; gi++) {
                  if (!results[gi].failed) good.push(results[gi]);
                }
                return good;
              });
            })
            .then(function(good){
              if (good.length === 0) {
                fail('SESSION_EXPIRED', '课表接口无有效响应, 请确认已登录教务并进入个人课表页后再点导入');
                return;
              }
              ok(JSON.stringify({
                sleepyCfNtss: true,
                xnxqdm: xnxqdm,
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
