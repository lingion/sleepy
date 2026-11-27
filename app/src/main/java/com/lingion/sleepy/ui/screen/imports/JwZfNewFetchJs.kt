package com.lingion.sleepy.ui.screen.imports

/**
 * T5 新增: 新版正方在 WebView 内 fetch 课表 JSON。
 *
 * 流程 (参考 HUEL_adapter.js + UJS_zhengfang_v9.0.js):
 *   1) 路径指纹: /jwglxt/、/kbcx/ (广东医科等新版) 或 WebVPN /http/<hex>/, 否则 NOT_ON_TIMETABLE
 *   2) gnmkdm 从 URL 抄, 缺省 N2151
 *   3) 推导 pathPrefix: 旧/WebVPN = '/jwglxt'; 新版裸 /kbcx/ = '' (path 自带 /kbcx/)
 *   4) 三段兜底取 (xnm,xqm): URL → 页面 #xnm/#xqm → fetch index 页 + native 选学期
 *   5) POST pathPrefix + /kbcx/xskbcx_cxXsgrkb.html 拿 JSON
 *   6) 嗅探: kbList → OK; login_slogin+csrftoken+密码框 → SESSION_EXPIRED; 解析失败 → SESSION_EXPIRED
 *   7) 回调 {ok, data, format:'zf_new'} 给 native, JwImportViewModel.parseZfNewBridgeResult 拆包
 *
 * 失败兜底: 捕获任何异常 → onWiseduResult({ok:false, kind:'FORMAT_ERROR', err}),
 * 由 native 侧 tryAllParsers 已抓到的 outerHTML 接管。
 */
const val ZF_NEW_FETCH_JS = """(function(){
  // Hosts whose timetable query enforces per-request validation (e.g. slider/token)
  // and rejects replayed fetch() calls. Always reuse the page's own verified XHR.
  var PASSIVE_CAPTURE_HOSTS = ['jwgl.qdc.edu.cn'];
  var OPTIONAL_FILTER_FIELDS = ['xsdm', 'kclbdm', 'kclxdm'];
  var KB_ENDPOINT_SUFFIX = '/kbcx/xskbcx_cxXsgrkb.html';

  function fail(kind, err){
    window.__sleepyBridge.onWiseduResult(JSON.stringify({
      ok:false, kind:kind, err:err||'', format:'zf_new'
    }));
  }
  function ok(data, xnm, xqm, empty){
    window.__sleepyBridge.onWiseduResult(JSON.stringify({
      ok:true, data:data, xnm:xnm, xqm:xqm, emptySemester:!!empty, format:'zf_new'
    }));
  }
  function isPassiveCaptureHost(hostname){
    var h = String(hostname || '').toLowerCase();
    for (var i = 0; i < PASSIVE_CAPTURE_HOSTS.length; i++) {
      if (h === PASSIVE_CAPTURE_HOSTS[i]) return true;
    }
    return false;
  }
  function findKbList(obj, depth){
    if (depth > 5 || !obj || typeof obj !== 'object') return null;
    if (Array.isArray(obj)) {
      for (var i = 0; i < obj.length; i++) {
        var r = findKbList(obj[i], depth + 1);
        if (r) return r;
      }
      return null;
    }
    if (obj.kbList && Array.isArray(obj.kbList)) return obj.kbList;
    for (var k in obj) {
      var r = findKbList(obj[k], depth + 1);
      if (r) return r;
    }
    return null;
  }
  function readDomField(id){
    var el = document.querySelector('#' + id);
    if (!el || el.value == null) return null;
    return String(el.value).trim();
  }
  function captureRecordedTimetable(targetTerm){
    // Reuse the latest verified XHR response recorded by DIAGNOSTIC_NETWORK_INSTALL_JS.
    var xnm = targetTerm && targetTerm.xnm ? String(targetTerm.xnm).trim() : (readDomField('xnm') || '');
    var xqm = targetTerm && targetTerm.xqm ? String(targetTerm.xqm).trim() : (readDomField('xqm') || '');
    if (!xnm || !xqm) return null;

    var filters = [];
    for (var f = 0; f < OPTIONAL_FILTER_FIELDS.length; f++) {
      var name = OPTIONAL_FILTER_FIELDS[f];
      var val = readDomField(name);
      if (val !== null) filters.push({name: name, value: val});
    }

    var records = window.__sleepyDiagNetworkRecords || [];
    var frameTiers = ['top', 'frame'];
    for (var tier = 0; tier < frameTiers.length; tier++) {
      var wantedFrame = frameTiers[tier];
      for (var i = records.length - 1; i >= 0; i--) {
        var record = records[i];
        if (!record || record.source !== 'xhr' || record.frame !== wantedFrame || record.method !== 'POST') continue;
        var url;
        try { url = new URL(record.url, location.href); } catch(e) { continue; }
        if (url.origin !== location.origin || url.pathname.indexOf(KB_ENDPOINT_SUFFIX) < 0) continue;
        var reqParams = new URLSearchParams(record.requestBody || '');
        if (reqParams.has('kzlx') && reqParams.get('kzlx') !== 'ck') continue;
        if ((reqParams.get('xnm') || '').trim() !== xnm || (reqParams.get('xqm') || '').trim() !== xqm) continue;
        var filtersMatch = true;
        for (var j = 0; j < filters.length; j++) {
          if ((reqParams.get(filters[j].name) || '').trim() !== filters[j].value) {
            filtersMatch = false;
            break;
          }
        }
        if (!filtersMatch) continue;
        if (record.status !== 200) return null;
        try {
          var data = JSON.parse(record.responseBody);
          var kbList = findKbList(data, 0);
          if (kbList) {
            return {kbList: kbList, xnm: xnm, xqm: xqm, empty: kbList.length === 0};
          }
        } catch(e) {}
        return null;
      }
    }
    return null;
  }

  try {
    if (isPassiveCaptureHost(location.hostname)) {
      var captured = captureRecordedTimetable(null);
      if (captured) {
        ok(JSON.stringify({kbList:captured.kbList}), captured.xnm, captured.xqm, captured.empty);
      } else {
        fail('NOT_ON_TIMETABLE', '请在学生课表页点击查询，按页面提示完成验证，等课表显示后再导入');
      }
      return;
    }
    var path = location.pathname;
    // T5+/2026-09 新版正方: 旧版路径前缀 /jwglxt/, 新版 (广东医科等) 直接 /kbcx/, WebVPN 改写为 /http/<hex>/
    var onJwglxt = path.indexOf('/jwglxt/') >= 0;
    var onKbcx = path.indexOf('/kbcx/') >= 0;
    var onHttpHex = /\/http\/[0-9a-f]{4,8}\//.test(path);
    if (!onJwglxt && !onKbcx && !onHttpHex) {
      fail('NOT_ON_TIMETABLE', '请先导航到个人课表页(地址应含 /jwglxt/ 或 /kbcx/)');
      return;
    }
    // 推导接口前缀: WebVPN/老 jwglxt = '/jwglxt'; 新版裸 /kbcx/ = '' (path 自带 /kbcx/)
    var pathPrefix = onJwglxt || onHttpHex ? '/jwglxt' : '';

    var search = location.search.substring(1);
    var params = {};
    search.split('&').forEach(function(p){
      if (!p) return;
      var kv = p.split('=');
      params[kv[0]] = decodeURIComponent(kv[1] || '');
    });
    var gnmkdm = params['gnmkdm'] || 'N2151';

    function pickTerm(){
      return new Promise(function(resolve, reject){
        if (params['xnm'] && params['xqm']) { resolve({xnm:params['xnm'], xqm:params['xqm']}); return; }
        var sel = document.querySelector('#xnm');
        var sel2 = document.querySelector('#xqm');
        if (sel && sel.value && sel2 && sel2.value) {
          resolve({xnm:String(sel.value).trim(), xqm:String(sel2.value).trim()});
          return;
        }
        var idxPath = pathPrefix + '/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=' + encodeURIComponent(gnmkdm) + '&layout=default';
        fetch(idxPath, {credentials:'include', headers:{'X-Requested-With':'XMLHttpRequest'}})
          .then(function(r){ return r.text(); })
          .then(function(html){
            var optRe = /<option\b([^>]*)>([^<]*)<\/option>/gi;
            function parse(block){
              var arr=[], defIdx=-1, m;
              while ((m = optRe.exec(block)) !== null) {
                var attrs = m[1] || '';
                var valueMatch = attrs.match(/\bvalue=["']([^"']*)["']/i);
                if (!valueMatch) continue;
                arr.push({value:valueMatch[1], text:m[2]||valueMatch[1]});
                if (/\bselected(?:\s*=\s*["'][^"']*["'])?/i.test(attrs)) defIdx = arr.length - 1;
              }
              if (defIdx < 0) {
                for (var i=0;i<arr.length;i++) {
                  if (arr[i].value) { defIdx = i; break; }
                }
              }
              return {options:arr, defaultIndex:defIdx};
            }
            var xBlock = (html.match(/<select[^>]*id=["']xnm["'][^>]*>([\s\S]*?)<\/select>/i)||[])[1]||'';
            var qBlock = (html.match(/<select[^>]*id=["']xqm["'][^>]*>([\s\S]*?)<\/select>/i)||[])[1]||'';
            var xData = parse(xBlock);
            var qData = parse(qBlock);
            if (xData.defaultIndex < 0 || qData.defaultIndex < 0) {
              throw new Error('页面未提供可用学期');
            }
            resolve({
              xnm:xData.options[xData.defaultIndex].value,
              xqm:qData.options[qData.defaultIndex].value
            });
          })
          .catch(function(e){ reject(e); });
      });
    }

    function req(url, method, body){
      var opts = {
        method: method,
        credentials: 'include',
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        }
      };
      if (body) opts.body = body;
      return fetch(url, opts).then(function(r){ return r.text(); });
    }

    pickTerm().then(function(t){
      var apiPath = pathPrefix + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + encodeURIComponent(gnmkdm);
      var body = 'xnm=' + encodeURIComponent(t.xnm) +
                 '&xqm=' + encodeURIComponent(t.xqm) +
                 '&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
      return req(apiPath, 'POST', body).then(function(text){ return {t:t, text:text}; });
    }).then(function(o){
      var text = o.text, t = o.t;
      var looksLikeLogin = text.indexOf('login_slogin') >= 0
        && text.indexOf('csrftoken') >= 0
        && (text.indexOf('请输入密码') >= 0 || text.indexOf('name="mm"') >= 0 || text.indexOf('name="password"') >= 0);
      if (looksLikeLogin) { fail('SESSION_EXPIRED', '会话已过期,请重新登录'); return; }
      var data;
      try {
        data = JSON.parse(text);
      } catch(e) {
        var fallbackOnParse = captureRecordedTimetable(t);
        if (fallbackOnParse) {
          ok(JSON.stringify({kbList:fallbackOnParse.kbList}), fallbackOnParse.xnm, fallbackOnParse.xqm, fallbackOnParse.empty);
          return;
        }
        fail('SESSION_EXPIRED', '接口返回非 JSON(可能已过期): '+String(e));
        return;
      }
      var kbList = findKbList(data, 0);
      if (!kbList) {
        var fallbackOnMissing = captureRecordedTimetable(t);
        if (fallbackOnMissing) {
          ok(JSON.stringify({kbList:fallbackOnMissing.kbList}), fallbackOnMissing.xnm, fallbackOnMissing.xqm, fallbackOnMissing.empty);
          return;
        }
        ok(JSON.stringify({kbList:[]}), t.xnm, t.xqm, true);
        return;
      }
      if (kbList.length === 0) {
        ok(JSON.stringify({kbList:[]}), t.xnm, t.xqm, true);
        return;
      }
      ok(JSON.stringify({kbList:kbList}), t.xnm, t.xqm, false);
    }).catch(function(e){
      fail('FORMAT_ERROR', String(e));
    });
  } catch(err) {
    fail('FORMAT_ERROR', String(err));
  }
})();
"""
