package com.lingion.sleepy.ui.screen.imports

/** BUAA homeapp collector. It transports the complete JSON; Kotlin owns course decoding. */
object JwBuaaCapture {
    fun scriptFor(type: String?): String? = if (type == "buaa") FETCH_JS else null

    const val FETCH_JS = """(function(){
  var finished = false;
  var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  var deadline = setTimeout(function(){ fail('课表读取超时，请重试'); }, 20000);
  function finish(payload) {
    if (finished) return;
    finished = true;
    clearTimeout(deadline);
    if (controller) controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }
  function fail(message) { finish({ok:false, err:message, format:'buaa'}); }
  function scalar(value) { return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''; }
  function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
  function term(value) {
    var text = scalar(value), code = /^(20[0-9]{2})-(20[0-9]{2})-([1-9])$/.exec(text);
    if (!code) {
      var named = /^(20[0-9]{2})-(20[0-9]{2})学年\s*第\s*([一二三四五六七八九1-9])\s*学期$/.exec(text);
      if (named) code = [text, named[1], named[2], String('一二三四五六七八九'.indexOf(named[3])+1 || Number(named[3]))];
    }
    if (!code || Number(code[2]) !== Number(code[1])+1) throw new Error('已选择的学期格式无法识别');
    return code[1]+'-'+code[2]+'-'+code[3];
  }
  function selectedTerms(W) {
    var values = [], nodes = W.document.querySelectorAll('#kbappTimeXQText,[name="termCode"],#termCode');
    for (var i=0; i<nodes.length; i++) {
      var node = nodes[i], value = scalar(node.value) || scalar(node.getAttribute('value')) || scalar(node.textContent);
      if (value) values.push(term(value));
    }
    var query = new URL(W.location.href).searchParams.getAll('termCode');
    for (var j=0; j<query.length; j++) if (scalar(query[j])) values.push(term(query[j]));
    return values.filter(function(value,index){ return values.indexOf(value) === index; });
  }
  var candidates = [], seen = [];
  function visit(W, depth) {
    if (!W || depth > 8 || seen.indexOf(W) >= 0) return;
    seen.push(W);
    try {
      var location = W.location, host = location.hostname.toLowerCase();
      if ((host === 'buaa.edu.cn' || host.endsWith('.buaa.edu.cn')) && /^https?:$/.test(location.protocol) &&
          location.origin === window.location.origin && /\/sys\/homeapp\//.test(location.pathname)) {
        var terms = selectedTerms(W);
        if (terms.length > 1) throw new Error('页面存在冲突学期，请只保留需要导入的课表');
        if (terms.length === 1) candidates.push({W:W, term:terms[0]});
      }
    } catch(error) {
      // Cross-origin frames are inaccessible. Selected-term validation errors must remain visible.
      if (error && error.message && /学期/.test(error.message)) throw error;
    }
    var frames;
    try { frames = W.frames; } catch(error) { return; }
    for (var i=0; i<frames.length; i++) visit(frames[i], depth+1);
  }
  try {
    visit(window,0);
    if (candidates.length !== 1) throw new Error(candidates.length ? '发现多个课表页面，请只保留需要导入的课表' : '请登录北航教务并打开课表，选择需要导入的学期');
    var selected = candidates[0], W = selected.W;
    var selectedHref = W.location.href;
    function verifySelection() {
      if (finished) throw new Error('采集已结束');
      candidates = []; seen = [];
      visit(window,0);
      if (candidates.length !== 1 || candidates[0].W !== W || candidates[0].term !== selected.term || W.location.href !== selectedHref)
        throw new Error('课表页面或学期已变化，请重新点击导入');
    }
    var prefix = W.location.pathname.indexOf('/jwapp/');
    var path = prefix >= 0 ? W.location.pathname.slice(0,prefix)+'/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do' : '../../../sys/homeapp/api/home/student/getMyScheduleDetail.do';
    var url = new URL(path, W.location.href);
    if (url.origin !== W.location.origin) throw new Error('课表地址与当前页面不一致');
    var options = {method:'POST', credentials:'include', mode:'same-origin', redirect:'error',
      headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','Accept':'application/json','X-Requested-With':'XMLHttpRequest'},
      body:'termCode='+encodeURIComponent(selected.term)+'&campusCode=&type=term'};
    if (controller) options.signal = controller.signal;
    W.fetch(url.href,options).then(async function(response){
      verifySelection();
      if (!response.ok) throw new Error('课表请求失败 HTTP '+response.status);
      if (response.url && new URL(response.url,url.href).origin !== url.origin) throw new Error('登录状态已变化，请重新登录');
      var text = await response.text();
      verifySelection();
      var result;
      try { result = JSON.parse(text); } catch(error) { throw new Error('课表接口返回不是 JSON，请重新登录'); }
      if (!object(result) || result.success === false || result.success === 'false' || !object(result.datas) || !Array.isArray(result.datas.arrangedList))
        throw new Error('课表响应中没有 arrangedList，请确认学期和登录状态');
      finish({ok:true, data:JSON.stringify(result), xnxq:selected.term, format:'buaa'});
    }).catch(function(error){ fail(String(error && error.message ? error.message : error)); });
  } catch(error) { fail(String(error && error.message ? error.message : error)); }
})();"""
}
