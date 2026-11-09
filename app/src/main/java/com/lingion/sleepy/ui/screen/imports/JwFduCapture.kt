package com.lingion.sleepy.ui.screen.imports

/**
 * Fudan's undergraduate print-data and postgraduate loadKbxx endpoints, after manual login.
 * DanXi establishes these GET routes and the Sunday start-date convention. Course JSON remains
 * byte-for-byte opaque for JwFduParser; no course coordinates or unproven timetable fields change.
 * The production script is executed offline by tools/test-fdu-fetch.js.
 */
internal object JwFduCapture {
    const val FETCH_JS = """(function(){
  var finished = false, deadline = null, controller = null;
  function finish(result) {
    if (finished) return;
    finished = true;
    if (deadline !== null) clearTimeout(deadline);
    if (controller) controller.abort();
    result.format = 'fdu';
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message) { throw Error(message); }
  function own(object, key) { return Object.prototype.hasOwnProperty.call(object, key); }
  function record(value) { return !!value && typeof value === 'object' && !Array.isArray(value); }
  function id(value) {
    if (typeof value !== 'string' && typeof value !== 'number') return '';
    if (typeof value === 'number' && !Number.isSafeInteger(value)) return '';
    var text = String(value).trim();
    return /^[1-9]\d*$/.test(text) ? text : '';
  }
  function requiredId(value) {
    var result = id(value);
    if (!result) fail('学期编号无效，请在课表页重新选择学期');
    return result;
  }
  function loginUrl(url) {
    return /\/(?:login|cas|sso|sign-?in|auth)(?:\/|$)/i.test(url.pathname);
  }
  function loginDocument(doc) { return !!doc.querySelector('input[type="password"]'); }
  function selectionState(doc) {
    return JSON.stringify(Array.prototype.map.call(doc.querySelectorAll('select'), function(control) {
      return {id:control.id || '', name:control.name || '', value:String(control.value || ''),
        selected:Array.prototype.filter.call(control.options || [], function(option) {
          return option.selected;
        }).map(function(option) { return String(option.value); })};
    }));
  }
  function semesterControl(doc, live) {
    var choices = [], available = [];
    Array.prototype.forEach.call(doc.querySelectorAll('#allSemesters'), function(control) {
      var options = Array.prototype.slice.call(control.options || []);
      var selected = options.filter(function(option) { return option.hasAttribute('selected'); });
      if (selected.length > 1 || (control.selectedOptions && control.selectedOptions.length > 1)) {
        fail('课表页选择了多个学期，请只选择一个学期');
      }
      var valid = options.filter(function(option) { return !!id(option.value); });
      valid.forEach(function(option) { available.push(id(option.value)); });
      var value = String(control.value || ''), index = options.findIndex(function(option) {
        return String(option.value) === value;
      });
      if (live && value) {
        requiredId(value);
        if (index < 0) fail('所选学期不在课表页候选中，请重新选择');
        // An implicit first option among several is not evidence of a user choice.
        if (index > 0 || selected.length === 1 || valid.length === 1) choices.push(value);
      } else if (selected.length === 1) {
        choices.push(requiredId(selected[0].value));
      } else if (valid.length === 1) {
        choices.push(id(valid[0].value));
      }
    });
    choices = choices.filter(function(value, index) { return choices.indexOf(value) === index; });
    if (choices.length > 1) fail('课表页存在不同的学期选择，请重新选择学期');
    return {id:choices[0] || '', ids:available};
  }
  // Decode only a quoted literal; never execute a script from the school page.
  function quoted(text, start) {
    var quote = text[start], result = '';
    if (quote !== '"' && quote !== "'") fail('学期列表格式异常');
    for (var i = start + 1; i < text.length; i++) {
      var c = text[i];
      if (c === quote) return {value:result, end:i + 1};
      if (c === '\n' || c === '\r') fail('学期列表格式异常');
      if (c !== '\\') { result += c; continue; }
      c = text[++i];
      var escapes = {n:'\n', r:'\r', t:'\t', b:'\b', f:'\f', v:'\v', '0':'\0'};
      if (c === 'u' || c === 'x') {
        var size = c === 'u' ? 4 : 2, hex = text.slice(i + 1, i + 1 + size);
        if (!new RegExp('^[0-9a-fA-F]{' + size + '}$').test(hex)) fail('学期列表格式异常');
        result += String.fromCharCode(parseInt(hex, 16)); i += size;
      } else if (c === '\n') {
        continue;
      } else if (c === '\r') {
        if (text[i + 1] === '\n') i++;
      } else if (c === undefined) {
        fail('学期列表格式异常');
      } else {
        result += own(escapes, c) ? escapes[c] : c;
      }
    }
    fail('学期列表未完整返回');
  }
  function semesterRows(doc) {
    var rows = {}, ids = [];
    Array.prototype.forEach.call(doc.querySelectorAll('script'), function(script) {
      var text = script.textContent || '', pattern = /\bvar\s+semesters\s*=\s*JSON\.parse\s*\(\s*/g, match;
      while ((match = pattern.exec(text)) !== null) {
        var literal = quoted(text, pattern.lastIndex);
        if (!/^\s*\)\s*;/.test(text.slice(literal.end))) fail('学期列表格式异常');
        var list;
        try { list = JSON.parse(literal.value); } catch (error) { fail('学期列表 JSON 格式异常'); }
        if (!Array.isArray(list)) fail('学期列表格式异常');
        list.forEach(function(row) {
          if (!record(row)) fail('学期列表格式异常');
          var key = requiredId(row.id);
          if (own(rows, key) && rows[key].startDate !== row.startDate) fail('同一学期的开学日期不一致');
          rows[key] = row; ids.push(key);
        });
        pattern.lastIndex = literal.end;
      }
    });
    return {rows:rows, ids:ids};
  }
  function openingDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return '';
    var parts = value.split('-').map(Number), date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
    if (date.getUTCFullYear() !== parts[0] || date.getUTCMonth() !== parts[1] - 1 ||
        date.getUTCDate() !== parts[2] || date.getUTCDay() !== 0) return '';
    // JWGL's Sunday starts its week. Sleepy's week one begins the following Monday.
    date.setUTCDate(date.getUTCDate() + 1);
    var result = date.toISOString().slice(0, 10);
    return /^\d{4}-\d{2}-\d{2}$/.test(result) ? result : '';
  }
  function validate(text, undergraduate) {
    if (/^\s*</.test(text)) fail('登录态已失效或教务返回了非课表页面，请重新登录');
    var root;
    try { root = JSON.parse(text); } catch (error) { fail('教务课表 JSON 格式无效'); }
    if (!record(root) || root.success === false || root.code === 401 || root.code === '401' ||
        root.code === 403 || root.code === '403') fail('教务没有返回完整课表');
    if (undergraduate) {
      if (own(root, 'results') || !Array.isArray(root.studentTableVms) ||
          !record(root.studentTableVms[0]) || !Array.isArray(root.studentTableVms[0].activities)) {
        fail('本科课表信封无效，请确认已打开本科课表页');
      }
    } else {
      if (own(root, 'studentTableVms') || own(root, 'studentTableVm') || !Array.isArray(root.results) ||
          (root.results.length && !root.results.some(function(row) {
            return record(row) && ['KCMC','ZCBH','XQ','KSJCDM'].every(function(key) { return own(row, key); });
          }))) fail('研究生课表信封无效，请确认已打开研究生课表页');
    }
  }
  (async function(){
    var initial = new URL(window.location.href);
    var undergraduate = initial.origin === 'https://fdjwgl.fudan.edu.cn';
    var postgraduate = initial.origin === 'http://yjsxk.fudan.edu.cn' ||
      initial.origin === 'http://yjsxktest.fudan.sh.cn';
    if ((!undergraduate && !postgraduate) || initial.username || initial.password ||
        loginUrl(initial) || loginDocument(window.document)) {
      fail('请先在复旦本科或研究生教务完成登录，再打开课表页导入');
    }
    var initialSelection = selectionState(window.document);
    var live = undergraduate ? semesterControl(window.document, true) : null;
    controller = new AbortController();
    deadline = setTimeout(function(){ finish({ok:false,err:'教务请求超时，请重新导入'}); }, 18000);
    async function request(path) {
      var url = new URL(path, initial.origin);
      var response = await window.fetch(url.href, {method:'GET', credentials:'include', mode:'same-origin',
        redirect:'error', signal:controller.signal});
      if (finished) fail('采集已结束');
      if (!response.ok || response.status < 200 || response.status >= 300) fail('教务请求失败 HTTP ' + response.status);
      if (!response.url) fail('教务响应缺少最终地址');
      var finalUrl = new URL(response.url);
      if (finalUrl.origin !== initial.origin || loginUrl(finalUrl) || response.redirected ||
          response.headers.get('location') || finalUrl.href !== url.href) {
        fail('教务响应离开课表接口或登录态已失效，请重新登录');
      }
      var text = await response.text();
      if (finished) fail('采集已结束');
      return {text:text, contentType:response.headers.get('content-type') || ''};
    }
    var semesterId = '', sourceStartDate = '', startDate = '', data;
    if (undergraduate) {
      var index = await request('/student/for-std/course-table');
      if (!/^\s*</.test(index.text) || !/text\/html/i.test(index.contentType)) fail('本科课表入口响应格式无效');
      var doc = new DOMParser().parseFromString(index.text, 'text/html');
      if (loginDocument(doc)) fail('登录态已失效，请重新登录');
      var server = semesterControl(doc, false), metadata = semesterRows(doc);
      var available = server.ids.concat(metadata.ids).filter(function(value, index, all) { return all.indexOf(value) === index; });
      semesterId = live.id || server.id || (available.length === 1 ? available[0] : '');
      if (!semesterId || available.indexOf(semesterId) < 0 ||
          (metadata.ids.length && metadata.ids.indexOf(semesterId) < 0)) {
        fail('无法唯一确定学期，请在本科课表页明确选择学期后导入');
      }
      var row = metadata.rows[semesterId];
      sourceStartDate = row && typeof row.startDate === 'string' ? row.startDate : '';
      startDate = openingDate(sourceStartDate);
      data = await request('/student/for-std/course-table/semester/' + semesterId + '/print-data');
    } else {
      // The source establishes no semester query or opening date for this PG interface.
      data = await request('/yjsxkapp/sys/xsxkappfudan/xsxkCourse/loadKbxx.do?_=' + Date.now());
    }
    validate(data.text, undergraduate);
    if (window.location.href !== initial.href || selectionState(window.document) !== initialSelection ||
        loginDocument(window.document)) fail('采集期间页面或学期已改变，请重新导入');
    finish({ok:true, data:data.text, variant:undergraduate ? 'undergraduate' : 'postgraduate',
      semesterId:semesterId, sourceStartDate:sourceStartDate, startDate:startDate, periods:[]});
  })().catch(function(error){ finish({ok:false,err:String(error.message || error)}); });
})();"""
}
