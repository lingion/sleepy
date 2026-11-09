package com.lingion.sleepy.ui.screen.imports

/**
 * 安徽大学同源采集。学期按用户选择、服务端显式选择、当前学期和唯一候选依次确定。
 * print-data 的容器在这里校验，课程字段和来源作息只由 Kotlin 解析。
 * 离线执行契约见 tools/test-ahu-fetch.js，来源核对记录见本地跨仓审读档案。
 */
internal object JwAhuCapture {
    const val FETCH_JS = """
(function(){
  function finish(payload) {
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }
  function fail(message) { throw new Error(message); }
  function id(value) {
    if (typeof value !== 'string' && typeof value !== 'number') return null;
    if (typeof value === 'number' && !Number.isSafeInteger(value)) return null;
    var text = String(value).trim();
    return /^[1-9]\d*$/.test(text) ? text : null;
  }
  function requiredId(value) {
    var result = id(value);
    if (!result) fail('学期编号无效，请在课表页面重新选择学期后再导入');
    return result;
  }
  function objectId(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
    var keys = ['id', 'semesterId', 'value', 'code'];
    for (var i = 0; i < keys.length; i++) {
      if (Object.prototype.hasOwnProperty.call(value, keys[i])) return requiredId(value[keys[i]]);
    }
    return null;
  }
  function attr(attributes, name) {
    var found = attributes.match(new RegExp('(?:^|\\s)' + name + '\\s*=\\s*(?:"([^"]*)"|\'([^\']*)\'|([^\\s>]+))', 'i'));
    return found ? (found[1] !== undefined ? found[1] : found[2] !== undefined ? found[2] : found[3]) : null;
  }
  // Read string literals without evaluating any page script.
  function stringLiteral(text, start) {
    var quote = text[start], result = '';
    if (quote !== '"' && quote !== "'") fail('当前学期数据格式异常');
    for (var i = start + 1; i < text.length; i++) {
      var c = text[i];
      if (c === quote) return {value: result, end: i + 1};
      if (c !== '\\') { result += c; continue; }
      c = text[++i];
      var escapes = {n:'\n', r:'\r', t:'\t', b:'\b', f:'\f', v:'\v', '0':'\0'};
      if (c === 'u' || c === 'x') {
        var size = c === 'u' ? 4 : 2, hex = text.slice(i + 1, i + 1 + size);
        if (!new RegExp('^[0-9a-fA-F]{' + size + '}$').test(hex)) fail('当前学期数据格式异常');
        result += String.fromCharCode(parseInt(hex, 16)); i += size;
      } else if (c === '\n') {
        continue;
      } else if (c === '\r') {
        if (text[i + 1] === '\n') i++;
      } else if (c === undefined) {
        fail('当前学期数据格式异常');
      } else {
        result += Object.prototype.hasOwnProperty.call(escapes, c) ? escapes[c] : c;
      }
    }
    fail('当前学期数据格式异常');
  }
  function balanced(text, start) {
    var depth = 0;
    for (var i = start; i < text.length; i++) {
      var c = text[i];
      if (c === '"' || c === "'") { i = stringLiteral(text, i).end - 1; continue; }
      if (c === '{' || c === '[') depth++;
      if (c === '}' || c === ']') {
        depth--;
        if (depth === 0) return text.slice(start, i + 1);
      }
    }
    fail('学期数据未完整返回，请刷新课表页面后重试');
  }
  function currentObjectId(text) {
    try { return objectId(JSON.parse(text)); } catch (error) {
      // A JS object literal may have unquoted keys or single quoted values.
      // Split only top-level members so a nested object's id cannot win.
      var members = [], start = 1, depth = 0;
      for (var i = 1; i < text.length - 1; i++) {
        var c = text[i];
        if (c === '"' || c === "'") { i = stringLiteral(text, i).end - 1; continue; }
        if (c === '{' || c === '[') depth++;
        if (c === '}' || c === ']') depth--;
        if (c === ',' && depth === 0) { members.push(text.slice(start, i)); start = i + 1; }
      }
      members.push(text.slice(start, -1));
      for (var j = 0; j < members.length; j++) {
        var field = members[j].match(/^\s*["']?(?:id|semesterId|value|code)["']?\s*:\s*([\s\S]*?)\s*$/);
        if (!field) continue;
        var raw = field[1];
        return requiredId(raw[0] === '"' || raw[0] === "'" ? stringLiteral(raw, 0).value : raw);
      }
      return null;
    }
  }
  function liveSnapshot() {
    if (document.querySelectorAll('#allSemesters').length > 1) fail('课表页面存在多个学期控件，请重新选择学期后再导入');
    var select = document.querySelector('#allSemesters');
    if (!select) return null;
    var selected = [], options = select.selectedOptions || select.options || [];
    for (var i = 0; i < options.length; i++) {
      if (select.selectedOptions || options[i].selected) selected.push(options[i].value);
    }
    return {selected: selected, value: select.value};
  }
  function chooseSemester(html, live) {
    if (live) {
      if (live.selected.length > 1) fail('当前选择了多个学期，请只选择一个学期后再导入');
      var liveValue = live.selected.length === 1 ? live.selected[0] : live.value;
      if (liveValue !== undefined && liveValue !== null && String(liveValue).trim() !== '') return requiredId(liveValue);
    }
    var available = [], selected = [], selectRe = /<select\b([^>]*)>([\s\S]*?)<\/select>/gi, selectMatch;
    while ((selectMatch = selectRe.exec(html)) !== null) {
      if (attr(selectMatch[1], 'id') !== 'allSemesters') continue;
      var optionRe = /<option\b([^>]*)>/gi, optionMatch;
      while ((optionMatch = optionRe.exec(selectMatch[2])) !== null) {
        var value = attr(optionMatch[1], 'value');
        if (/(?:^|\s)selected(?:\s|=|$)/i.test(optionMatch[1])) selected.push(requiredId(value));
        if (id(value)) available.push(id(value));
      }
    }
    if (selected.length > 1) fail('课表页面返回多个已选学期，请重新选择学期后再导入');
    if (selected.length === 1) return selected[0];

    var current = [], currentRe = /\bcurrentSemester["']?\s*[:=]\s*/g, match;
    while ((match = currentRe.exec(html)) !== null) {
      var tail = html.slice(currentRe.lastIndex), value = null;
      if (/^JSON\.parse\s*\(/.test(tail)) {
        var opening = tail.match(/^JSON\.parse\s*\(\s*/)[0].length;
        var literal = stringLiteral(tail, opening);
        if (!/^\s*\)/.test(tail.slice(literal.end))) fail('当前学期数据格式异常');
        value = objectId(JSON.parse(literal.value));
      } else if (tail[0] === '{') {
        value = currentObjectId(balanced(tail, 0));
      }
      if (!value) fail('当前学期数据缺少有效编号');
      current.push(value);
    }
    var currentIdRe = /\bcurrentSemesterId["']?\s*[:=]\s*(?:"([^"]*)"|'([^']*)'|([^\s;,}\])<]+))/g;
    while ((match = currentIdRe.exec(html)) !== null) {
      current.push(requiredId(match[1] !== undefined ? match[1] : match[2] !== undefined ? match[2] : match[3]));
    }
    if (current.length) {
      for (var ci = 1; ci < current.length; ci++) {
        if (current[ci] !== current[0]) fail('当前学期信息不一致，请在课表页面明确选择学期后再导入');
      }
      return current[0];
    }

    var marked = [], listRe = /\b(?:allSemesters|semesters)["']?\s*[:=]\s*/g;
    while ((match = listRe.exec(html)) !== null) {
      if (html[listRe.lastIndex] !== '[') continue;
      var rows = JSON.parse(balanced(html, listRe.lastIndex));
      if (!Array.isArray(rows)) fail('学期列表格式异常');
      for (var ri = 0; ri < rows.length; ri++) {
        var row = rows[ri], rowId = objectId(row);
        if (!rowId) fail('学期列表包含无效编号');
        available.push(rowId);
        if (row.isCurrentSemester === true || row.isCurrent === true || row.current === true || row.status === 'CURRENT') marked.push(rowId);
      }
    }
    if (marked.length > 1) fail('学期列表标记了多个当前学期，请明确选择学期后再导入');
    if (marked.length === 1) return marked[0];
    var unique = available.filter(function(value, index) { return available.indexOf(value) === index; });
    if (unique.length === 1) return unique[0];
    fail('无法唯一确定学期，请在课表页面选择学期后再导入');
  }
  function loginUrl(text) {
    if (!text) return false;
    var url = new URL(text, location.href);
    return /\/(?:cas(?:\/login)?|login|sign-?in)(?:\/|$)/i.test(url.pathname);
  }
  function loginHtml(body) {
    if (!/^\s*</.test(body) || !/<(?:!doctype|html|form|input)\b/i.test(body)) return false;
    return (/\bname\s*=\s*["']lt["']/i.test(body) && /\bname\s*=\s*["']execution["']/i.test(body))
      || /casloginform|统一身份认证|请重新登录|session timeout|会话已过期/i.test(body)
      || (/<form\b/i.test(body) && /<input\b[^>]*\btype\s*=\s*["']password["']/i.test(body));
  }
  function request(url) {
    return fetch(url, {method:'GET', credentials:'include', redirect:'follow'}).then(function(response) {
      return response.text().then(function(body) {
        var destination = response.headers.get('location') || '';
        if (loginUrl(response.url) || loginUrl(destination) || loginHtml(body)) fail('登录态已失效，请在教务主页重新登录后再试');
        if (!response.ok || response.status < 200 || response.status >= 300) fail('教务请求失败，HTTP ' + response.status);
        if (response.url && new URL(response.url, location.href).origin !== location.origin) fail('教务请求离开当前站点，请返回教务主页后重试');
        return body;
      });
    });
  }
  function validatePrintData(text) {
    var root = JSON.parse(text);
    if (!root || typeof root !== 'object' || Array.isArray(root)) fail('课表接口返回格式异常');
    var tables = [], hasTables = false;
    if (Object.prototype.hasOwnProperty.call(root, 'studentTableVms')) {
      if (!Array.isArray(root.studentTableVms)) fail('课表列表格式异常');
      tables = tables.concat(root.studentTableVms); hasTables = true;
    }
    if (Object.prototype.hasOwnProperty.call(root, 'studentTableVm')) {
      tables.push(root.studentTableVm); hasTables = true;
    }
    if (!hasTables) fail('课表接口未返回课表列表');
    for (var i = 0; i < tables.length; i++) {
      var table = tables[i];
      if (!table || typeof table !== 'object' || Array.isArray(table) || !Array.isArray(table.activities)) fail('课表活动列表格式异常');
    }
  }
  try {
    if (!/(^|\.)ahu\.edu\.cn$/i.test(location.hostname)) fail('请先登录并进入安徽大学教务后再点导入');
    var PREFIX = '__EAMS5_PREFIX__';
    var initialUrl = location.href, initialSelect = document.querySelector('#allSemesters');
    var live = liveSnapshot();
    request(PREFIX + '/for-std/course-table').then(function(html) {
      var semesterId = chooseSemester(html, live);
      return request(PREFIX + '/for-std/course-table/semester/' + semesterId + '/print-data?semesterId=' + semesterId + '&hasExperiment=false');
    }).then(function(text) {
      validatePrintData(text);
      if (location.href !== initialUrl || loginUrl(location.href) || loginHtml(document.body && document.body.innerHTML || '') ||
          document.querySelector('#allSemesters') !== initialSelect || JSON.stringify(liveSnapshot()) !== JSON.stringify(live)) {
        fail('采集期间页面或学期已改变，请重新导入');
      }
      finish({ok:true, data:text, periods:[]});
    }).catch(function(error) { finish({ok:false, err:String(error)}); });
  } catch (error) { finish({ok:false, err:String(error)}); }
})();
"""
}
