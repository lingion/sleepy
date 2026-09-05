// ============================================================
// Sleepy 课表适配采集脚本 v3.0 —— 全量捕获 + ZIP 打包
// ============================================================
// 用法: 登录教务系统 → 打开"我的课表"页面 → F12 打开控制台
//       → 粘贴本文件全部内容回车 → 点右下角面板绿色按钮
//       → 自动下载 sleepy-adapt.zip
//
// 采集内容(不区分教务系统类型,看到什么收什么,相关无关一律全存):
//   1-dom/        每个页面、每个 iframe 的完整 DOM(课表渲染结果也在这里)
//   2-inline/     页面里所有内联 <script> / <style> 源码
//   3-res/        页面加载过的所有 HTML/CSS/JS/JSON 文件,逐个重新抓取完整内容
//   4-net-live/   粘贴脚本之后页面发出的每一个请求:URL+请求体+响应体
//   4-net-replay/ 粘贴之前已发出的数据接口:自动重放拿响应(含带参数二次重试)
//   5-storage/    localStorage / sessionStorage 键值
//   6-logs/       浏览器完整网络日志、发现的一切 URL、抓取失败清单
//   INDEX.txt     全包清单:每个文件是什么、从哪来的
// 不采集: 密码框内容、Cookie 值(只记名字)、验证码图片。
// 注意: 接口响应里可能含你的学号姓名,提交前按 README 自查一遍。
// ============================================================
(function () {
  'use strict';
  if (window.__SLEEPY_KIT__) { console.log('[sleepy] 本页已经挂过采集器了,不要重复粘贴'); return; }
  window.__SLEEPY_KIT__ = {
    version: 'v3.0', net: [], urls: {}, apiUrls: {},
    entries: [], seen: {}, pathSeen: {}, bodySeen: {}, usedBytes: 0, mined: {}
  };
  var K = window.__SLEEPY_KIT__;

  var MAX_FILE_BYTES  = 2 * 1024 * 1024;   // 单文件上限 2MB
  var MAX_TOTAL_BYTES = 20 * 1024 * 1024;  // 整包上限 20MB (GitHub 附件限 25MB)
  var MAX_NET = 400;                       // 实时录制上限
  var MAX_REFETCH = 200;                   // 资源重取上限
  var MAX_REPLAY2 = 40;                    // 带参数重试上限

  var enc = new TextEncoder();
  function bytesOf(s) { return enc.encode(s); }

  var BINARY_EXT = /\.(png|jpe?g|gif|bmp|webp|ico|cur|woff2?|ttf|eot|otf|mp3|mp4|wav|avi|mov|pdf|docx?|xlsx?|pptx?|zip|rar|7z|gz|exe|apk|msi)([?#]|$)/i;
  var LOGOUT_RE = /(logout|signout|log_off|logoff|tuichu|zhuxiao|sso\/logout|cancel)/i;

  // ---------- 基础工具 ----------
  function safe(fn) { try { return fn(); } catch (e) { return null; } }
  function nowStr() { try { return new Date().toISOString(); } catch (e) { return ''; } }
  function sameOrigin(u) { try { return new URL(u, location.href).origin === location.origin; } catch (e) { return false; } }
  function absUrl(u, base) { try { return new URL(u, base || location.href).href; } catch (e) { return null; } }

  // ---------- ZIP 写入器(零依赖,store 不压缩,UTF-8 文件名) ----------
  var CRC_TABLE = (function () {
    var t = new Array(256), c, n, k;
    for (n = 0; n < 256; n++) { c = n; for (k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1); t[n] = c >>> 0; }
    return t;
  })();
  function crc32(u8) {
    var c = 0xFFFFFFFF, i;
    for (i = 0; i < u8.length; i++) c = CRC_TABLE[(c ^ u8[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  function dosDT(d) {
    return {
      time: ((d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1)) & 0xFFFF,
      date: (((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate()) & 0xFFFF
    };
  }
  function u16(v) { return [v & 255, (v >>> 8) & 255]; }
  function u32(v) { return [v & 255, (v >>> 8) & 255, (v >>> 16) & 255, (v >>> 24) & 255]; }
  function buildZip(files) {   // files: [{name, data:Uint8Array}] → Uint8Array
    var chunks = [], centrals = [], offset = 0, dt = dosDT(new Date());
    files.forEach(function (f) {
      var name = bytesOf(f.name), crc = crc32(f.data);
      var lh = [].concat(u32(0x04034b50), u16(20), u16(0x0800), u16(0), u16(dt.time), u16(dt.date),
        u32(crc), u32(f.data.length), u32(f.data.length), u16(name.length), u16(0));
      chunks.push(new Uint8Array(lh), name, f.data);
      centrals.push({ name: name, crc: crc, size: f.data.length, off: offset });
      offset += lh.length + name.length + f.data.length;
    });
    var cdStart = offset;
    centrals.forEach(function (c) {
      var ch = [].concat(u32(0x02014b50), u16(20), u16(20), u16(0x0800), u16(0), u16(dt.time), u16(dt.date),
        u32(c.crc), u32(c.size), u32(c.size), u16(c.name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(c.off));
      chunks.push(new Uint8Array(ch), c.name);
      offset += ch.length + c.name.length;
    });
    chunks.push(new Uint8Array([].concat(u32(0x06054b50), u16(0), u16(0),
      u16(centrals.length), u16(centrals.length), u32(offset - cdStart), u32(cdStart), u16(0))));
    var total = 0; chunks.forEach(function (c) { total += c.length; });
    var out = new Uint8Array(total), pos = 0;
    chunks.forEach(function (c) { out.set(c, pos); pos += c.length; });
    return out;
  }

  // ---------- 条目入库(路径唯一 + 内容去重 + 字节预算) ----------
  function addEntry(path, meta, body) {
    if (K.seen[path]) return null;
    var b = body == null ? '' : String(body);
    var dedupKey = path.slice(0, path.lastIndexOf('/')) + '|' + b;
    if (K.bodySeen[dedupKey]) { K.seen[path] = { merged: K.bodySeen[dedupKey] }; return null; }
    K.seen[path] = {};
    K.bodySeen[dedupKey] = path;
    var note = '';
    var data = bytesOf(b);
    if (data.length > MAX_FILE_BYTES) {
      var cut = MAX_FILE_BYTES;
      while (cut > 0 && (data[cut] & 0xC0) === 0x80) cut--;   // 不撕坏多字节字符
      data = data.slice(0, cut);
      note = ' [单文件超限已截断]';
    }
    var remain = MAX_TOTAL_BYTES - K.usedBytes;
    if (data.length > remain) {
      if (remain < 1024) { K.seen[path] = { dropped: '整包已满' }; return null; }
      var cut2 = remain;
      while (cut2 > 0 && (data[cut2] & 0xC0) === 0x80) cut2--;
      data = data.slice(0, cut2);
      note = ' [整包已达上限,截断]';
    }
    K.usedBytes += data.length;
    K.entries.push({ path: path, meta: meta + note, data: data, text: b });
    return path;
  }
  function uniqPath(p) {
    if (!K.pathSeen[p]) { K.pathSeen[p] = 1; return p; }
    var n = ++K.pathSeen[p], dot = p.lastIndexOf('.'), slash = p.lastIndexOf('/');
    return dot > slash ? p.slice(0, dot) + '_' + n + p.slice(dot) : p + '_' + n;
  }
  function urlToPath(prefix, u, ext) {
    var s = String(u), h = 0, i;
    for (i = 0; i < s.length; i++) h = ((h << 5) - h + s.charCodeAt(i)) | 0;
    var tail = s.split('?')[0].split('/').filter(Boolean).slice(-2).join('_');
    tail = tail.replace(/[^A-Za-z0-9._-]/g, '_').slice(-64) || 'root';
    return prefix + '/' + tail + '_' + (h >>> 0).toString(36).slice(0, 7) + (ext || '');
  }
  function extFromCt(ct, u) {
    ct = (ct || '').toLowerCase();
    if (ct.indexOf('json') !== -1) return '.json';
    if (ct.indexOf('html') !== -1) return '.html';
    if (ct.indexOf('css') !== -1) return '.css';
    if (ct.indexOf('javascript') !== -1) return '.js';
    var m = String(u).split('?')[0].match(/\.([a-z0-9]{1,6})$/i);
    return m ? ('.' + m[1].toLowerCase()) : '.txt';
  }
  function labelToPath(label) {
    var s = String(label).replace(/https?:\/\//, '').replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 50);
    return s || 'page';
  }

  // ---------- 1. 网络记录器:粘贴之后的一切请求 ----------
  function recordNet(e) {
    if (K.net.length >= MAX_NET) return;
    e.time = nowStr();
    K.net.push(e);
    var a = absUrl(e.url);
    if (a && sameOrigin(a)) K.apiUrls[a] = true;
    refreshPanel();
  }
  function instrument(win) {
    return safe(function () {
      var of = win.fetch;
      if (of && !win.__sleepyFetch) {
        win.__sleepyFetch = true;
        win.fetch = function (input, init) {
          var url = '', method = 'GET', body = '';
          safe(function () {
            url = typeof input === 'string' ? input : (input && input.url) || String(input);
            method = (init && init.method) || (input && input.method) || 'GET';
            body = (init && init.body) || '';
            if (typeof body !== 'string') body = '(非文本请求体:' + (body && body.constructor && body.constructor.name) + ')';
          });
          return of.apply(this, arguments).then(function (res) {
            safe(function () {
              var ct = '';
              try { ct = res.headers.get('content-type') || ''; } catch (e) {}
              var rec = function (t) { recordNet({ via: 'fetch', method: method, url: url, reqBody: body, status: res.status, contentType: ct, body: t }); };
              res.clone().text().then(rec).catch(function () { rec('(响应体读取失败)'); });
            });
            return res;
          });
        };
      }
      var xp = win.XMLHttpRequest && win.XMLHttpRequest.prototype;
      if (xp && !xp.__sleepyXhr) {
        xp.__sleepyXhr = true;
        var oo = xp.open, os = xp.send;
        xp.open = function (m, u) {
          var self = this;
          safe(function () { self.__sm = m; self.__su = u; });
          return oo.apply(this, arguments);
        };
        xp.send = function (b) {
          var self = this;
          safe(function () {
            self.addEventListener('load', function () {
              safe(function () {
                var ct = '', t = '';
                try { ct = self.getResponseHeader('content-type') || ''; } catch (e) {}
                try { t = self.responseText; } catch (e) { t = '(响应体不可读,可能是二进制)'; }
                recordNet({ via: 'xhr', method: self.__sm, url: self.__su, reqBody: b == null ? '' : String(b), status: self.status, contentType: ct, body: t });
              });
            });
          });
          return os.apply(this, arguments);
        };
      }
      if (win.history && win.history.pushState && !win.__sleepyRoute) {
        win.__sleepyRoute = true;
        var op = win.history.pushState;
        win.history.pushState = function () {
          var self = this, args = arguments;
          safe(function () { recordNet({ via: 'route', method: 'SPA', url: String(args[2] || ''), reqBody: '', status: 0, contentType: '', body: '' }); });
          return op.apply(this, args);
        };
      }
      return true;
    });
  }

  // ---------- 2. URL 登记 ----------
  function addUrl(u, source, base) {
    var a = absUrl(u, base);
    if (!a) return;
    if (!K.urls[a]) K.urls[a] = [];
    if (K.urls[a].indexOf(source) === -1) K.urls[a].push(source);
  }
  function harvestDoc(doc, label) {
    var base = safe(function () { return doc.baseURI; }) || location.href;
    safe(function () {
      doc.querySelectorAll('script[src]').forEach(function (el) { addUrl(el.getAttribute('src'), label + ' script标签', base); });
      doc.querySelectorAll('link[href]').forEach(function (el) {
        var rel = (el.getAttribute('rel') || '').toLowerCase();
        if (rel.indexOf('icon') !== -1) return;
        if (rel.indexOf('stylesheet') !== -1 || rel.indexOf('preload') !== -1 || rel.indexOf('import') !== -1)
          addUrl(el.getAttribute('href'), label + ' link标签', base);
      });
      doc.querySelectorAll('iframe[src]').forEach(function (el) { addUrl(el.getAttribute('src'), label + ' iframe', base); });
      doc.querySelectorAll('form[action]').forEach(function (el) { addUrl(el.getAttribute('action'), label + ' form提交地址', base); });
      // 页内链接:菜单/入口的 URL 规律对手工适配有直接价值
      doc.querySelectorAll('a[href]').forEach(function (el) {
        var h = el.getAttribute('href') || '';
        if (/^https?:/i.test(h) || /^\//.test(h)) addUrl(h, label + ' 页内链接', base);
      });
      safe(function () {
        (doc.defaultView.performance.getEntriesByType('resource') || []).forEach(function (r) {
          addUrl(r.name, label + ' 已加载 ' + (r.initiatorType || '?'), base);
          if (r.initiatorType === 'xmlhttprequest' || r.initiatorType === 'fetch') {
            var a = absUrl(r.name, base);
            if (a && sameOrigin(a)) K.apiUrls[a] = true;
          }
        });
      });
    });
  }

  // ---------- 3. 静态捕获:DOM / 内联代码 / 浏览器存储 ----------
  function captureDoc(win, label) {
    var doc = safe(function () { return win.document; });
    if (!doc || !doc.documentElement) return;
    safe(function () { addEntry(uniqPath('1-dom/' + labelToPath(label) + '_page.html'), label + ' 页面DOM(打包时刻)', doc.documentElement.outerHTML); });
    safe(function () {
      var n = 0;
      doc.querySelectorAll('script:not([src])').forEach(function (el) {
        var t = el.textContent || '';
        if (t.replace(/\s/g, '').length < 10) return;
        n++;
        addEntry(uniqPath('2-inline/' + labelToPath(label) + '_script' + n + '.js'), label + ' 内联脚本', t);
      });
      n = 0;
      doc.querySelectorAll('style').forEach(function (el) {
        var t = el.textContent || '';
        if (t.replace(/\s/g, '').length < 10) return;
        n++;
        addEntry(uniqPath('2-inline/' + labelToPath(label) + '_style' + n + '.css'), label + ' 内联样式', t);
      });
    });
    safe(function () {
      ['sessionStorage', 'localStorage'].forEach(function (sn) {
        var st = win[sn];
        if (!st || !st.length) return;
        var lines = [];
        for (var i = 0; i < st.length; i++) {
          var k = st.key(i), v = '';
          try { v = String(st.getItem(k)); } catch (e) {}
          if (v.length > 4096) v = v.slice(0, 4096) + '...[截断]';
          lines.push(k + ' = ' + v);
        }
        if (lines.length) addEntry(uniqPath('5-storage/' + labelToPath(label) + '_' + sn + '.txt'), label + ' ' + sn, lines.join('\n'));
      });
    });
    // 已抓到的样式文本里再挖 url()/@import 引用
    safe(function () {
      K.entries.forEach(function (f) {
        if (!/\.css$/.test(f.path)) return;
        var re = /url\(\s*['"]?([^'")]+)['"]?\s*\)|@import\s+['"]([^'"]+)['"]/g, m;
        while ((m = re.exec(f.text))) addUrl(m[1] || m[2], 'CSS引用', doc.baseURI);
      });
    });
    harvestDoc(doc, label);
  }

  // ---------- 4. 帧遍历(顶页 + 同源 iframe,嵌套两层) ----------
  function eachFrame(cb) {
    cb(window, 'top');
    function walk(doc, depth) {
      if (depth > 2) return;
      safe(function () {
        doc.querySelectorAll('iframe').forEach(function (f, i) {
          var w = safe(function () { return f.contentWindow; });
          var d = safe(function () { return f.contentDocument; });
          if (w && d && d.documentElement) {
            cb(w, 'iframe' + depth + '-' + i);
            walk(d, depth + 1);
          } else {
            addUrl(f.src, '跨域iframe(浏览器安全限制,只记地址)', location.href);
          }
        });
      });
    }
    walk(document, 1);
  }

  // ---------- 5. 资源重取(串行,不轰炸教务服务器) ----------
  function fetchOne(u) {
    return new Promise(function (resolve) {
      var done = false;
      var x = new XMLHttpRequest();
      var to = setTimeout(function () {
        if (!done) { done = true; try { x.abort(); } catch (e) {} resolve({ u: u, note: '超时' }); }
      }, 15000);
      x.onload = x.onerror = x.onabort = function () {
        if (done) return; done = true; clearTimeout(to);
        var ct = '';
        try { ct = x.getResponseHeader('content-type') || ''; } catch (e) {}
        resolve({ u: u, status: x.status, ct: ct, text: x.responseText || '' });
      };
      try { x.open('GET', u, true); x.send(null); }
      catch (e) { done = true; clearTimeout(to); resolve({ u: u, note: String(e && e.message || e) }); }
    });
  }
  function refetchAll(progressCb) {
    var eligible = Object.keys(K.urls).filter(function (u) {
      return sameOrigin(u) && !BINARY_EXT.test(u) && !LOGOUT_RE.test(u);
    });
    var all = eligible.slice(0, MAX_REFETCH);
    var over = eligible.length - all.length;
    var skipped = [], done = 0, ok = 0;
    var chain = Promise.resolve();
    all.forEach(function (u) {
      chain = chain.then(function () {
        return fetchOne(u).then(function (r) {
          done++;
          if (progressCb) progressCb(done, all.length);
          var ct = (r.ct || '').toLowerCase();
          var binary = ct.indexOf('octet-stream') !== -1 || ct.indexOf('image/') === 0 ||
                       ct.indexOf('font') !== -1 || ct.indexOf('audio') === 0 || ct.indexOf('video') === 0;
          if (r.note) skipped.push(r.u + '  [' + r.note + ']');
          else if (r.status === 0) skipped.push(r.u + '  [网络失败]');
          else if (binary) skipped.push(r.u + '  [二进制 ' + r.ct + ',不入包]');
          else if (!r.text || r.text.length < 20) skipped.push(r.u + '  [HTTP ' + r.status + ' 空/过短]');
          else if (addEntry(uniqPath(urlToPath('3-res', r.u, extFromCt(r.ct, r.u))),
                    '重取 GET ' + r.u + ' · HTTP ' + r.status + ' · ' + r.ct, r.text)) ok++;
        });
      });
    });
    return chain.then(function () {
      for (var i = 0; i < over; i++) skipped.push('(超出 ' + MAX_REFETCH + ' 个上限,未重取)');
      return { ok: ok, skipped: skipped };
    });
  }

  // ---------- 6. 数据接口两阶段重放(全部运行时发现,零协议硬编码) ----------
  function looksLikeError(t) {
    if (!t || t.length > 400) return false;
    return t.indexOf('为空') !== -1 || t.indexOf('不能') !== -1 || t.indexOf('必填') !== -1 ||
           /param/i.test(t) || /required/i.test(t) || /"code"\s*:\s*"1"/.test(t) || /^\s*$/.test(t);
  }
  function isRichData(t) {
    return t && t.length > 500 && (t.indexOf('rows') !== -1 || t.indexOf('timetables') !== -1 ||
           t.indexOf('list') !== -1 || /\[\s*\{/.test(t));
  }
  function postProbe(u, body) {
    return new Promise(function (resolve) {
      var finished = false;
      var x = new XMLHttpRequest();
      var to = setTimeout(function () {
        if (!finished) { finished = true; try { x.abort(); } catch (e) {} resolve({ u: u, note: '超时' }); }
      }, 15000);
      x.onload = x.onerror = function () {
        if (finished) return; finished = true; clearTimeout(to);
        var ct = '';
        try { ct = x.getResponseHeader('content-type') || ''; } catch (e) {}
        resolve({ u: u, status: x.status, ct: ct, text: x.responseText || '' });
      };
      try {
        x.open('POST', u, true);
        x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        x.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        x.send(body || '');
      } catch (e) { finished = true; clearTimeout(to); resolve({ u: u, note: String(e && e.message || e) }); }
    });
  }
  function mineValues(text) {
    safe(function () {
      var m = text.match(/\d{4}-\d{4}-\d{1,2}/g);                    // 学期码形态: 2026-2027-1
      if (m && !K.mined.XNXQDM) K.mined.XNXQDM = m[0];
      var g = text.match(/gnmkdm['":=\s]+\[?([A-Z]?\d{4,5})\]?/i);   // 功能码形态: N2151
      if (g && !K.mined.GNMKDM) K.mined.GNMKDM = g[1];
    });
  }
  function storeReplay(r, tag) {
    if (r.note || r.status === 0 || !r.text || r.text.length < 5) return false;
    return !!addEntry(
      uniqPath(urlToPath('4-net-replay' + (tag ? '-withparam' : ''), r.u, extFromCt(r.ct, r.u))),
      '接口重放' + (tag ? '(带参数 ' + tag + ')' : '(空体)') + ' POST ' + r.u + ' · HTTP ' + r.status + ' · ' + (r.ct || '?')
      + (looksLikeError(r.text) ? '(参数报错:报错文本说明该接口要什么参数)' : ''),
      r.text);
  }
  function replayApis(progressCb) {
    var apis = Object.keys(K.apiUrls).filter(function (u) { return !LOGOUT_RE.test(u); }).slice(0, 100);
    var done = 0, ok = 0, retry = [], skipped = [];
    var chain = Promise.resolve();
    apis.forEach(function (u) {                       // 阶段 1:空体重放
      chain = chain.then(function () {
        return postProbe(u, '').then(function (r) {
          done++; if (progressCb) progressCb(done);
          if (r.text) mineValues(r.text);
          if (storeReplay(r, '')) { ok++; retry.push(u); }          // 回了数据也重试一次,带参数可能拿更全的
          else if (r.text && looksLikeError(r.text)) retry.push(u);
          else skipped.push(u + '  [空体重放无效 HTTP ' + r.status + ']');
        });
      });
    });
    return chain.then(function () {                   // 阶段 2:用挖到的参数值重试
      var tag = Object.keys(K.mined).map(function (k) { return k + '=' + K.mined[k]; }).join('&');
      if (!K.mined.XNXQDM || !retry.length) return;
      var body = 'XNXQDM=' + encodeURIComponent(K.mined.XNXQDM) +
                 (K.mined.GNMKDM ? '&gnmkdm=' + encodeURIComponent(K.mined.GNMKDM) : '');
      var chain2 = Promise.resolve();
      retry.slice(0, MAX_REPLAY2).forEach(function (u) {
        chain2 = chain2.then(function () {
          return postProbe(u, body).then(function (r2) {
            done++; if (progressCb) progressCb(done);
            if (r2.text) mineValues(r2.text);
            if (isRichData(r2.text)) { if (storeReplay(r2, tag)) ok++; }
          });
        });
      });
      return chain2;
    }).then(function () {
      return { ok: ok, skipped: skipped, mined: K.mined };
    });
  }

  // ---------- 7. 日志与清单 ----------
  function buildLogs() {
    var L = [], apiEndpoints = [];
    L.push('======== 浏览器网络日志(含粘贴脚本之前的请求) ========');
    eachFrame(function (win, label) {
      safe(function () {
        var es = win.performance.getEntriesByType('resource') || [];
        L.push('');
        L.push('---- ' + label + ' (' + es.length + ' 条) ----');
        es.forEach(function (r) {
          L.push('  [' + (r.initiatorType || '?') + '] ' + Math.round(r.duration || 0) + 'ms ' + (r.transferSize || 0) + 'B ' + r.name);
          if (r.initiatorType === 'xmlhttprequest' || r.initiatorType === 'fetch') apiEndpoints.push(r.name);
        });
      });
    });
    L.push('');
    L.push('======== 数据接口清单(fetch/xhr 发起,课表数据大概率出自这里) ========');
    Array.from(new Set(apiEndpoints)).slice(0, 100).forEach(function (u) { L.push('  ' + u); });
    return L.join('\n');
  }
  function buildIndex(statLine) {
    var L = [];
    L.push('Sleepy 课表采集包 v3.0');
    L.push('生成时间: ' + nowStr());
    L.push('页面: ' + location.href);
    L.push('标题: ' + document.title);
    L.push('User-Agent: ' + navigator.userAgent);
    L.push('Cookie 名(只有名字,没有值): ' + (safe(function () {
      return document.cookie.split(';').map(function (c) { return c.trim().split('=')[0]; }).filter(Boolean).join(', ');
    }) || '(无)'));
    L.push('');
    L.push('== 概况 ==');
    L.push(statLine);
    L.push('');
    L.push('== 目录说明 ==');
    L.push('1-dom/        页面与 iframe 的完整 DOM(课表如已渲染,数据也在里面)');
    L.push('2-inline/     页面内联 <script>/<style> 源码');
    L.push('3-res/        页面加载过的 HTML/CSS/JS/JSON 文件原样重取');
    L.push('4-net-live/   粘贴脚本后页面发出的请求(含请求体+响应体)');
    L.push('4-net-replay/ 粘贴前已发出接口的重放响应(withparam=带参数二次重试)');
    L.push('5-storage/    localStorage/sessionStorage');
    L.push('6-logs/       浏览器网络日志 / 发现的 URL / 失败清单');
    L.push('');
    L.push('== 文件清单(路径 | 说明) ==');
    K.entries.forEach(function (f) { L.push(f.path + '  |  ' + f.meta); });
    Object.keys(K.seen).forEach(function (p) {
      var s = K.seen[p];
      if (s.merged) L.push(p + '  |  (与 ' + s.merged + ' 内容相同,已合并)');
      else if (s.dropped) L.push(p + '  |  (未入包: ' + s.dropped + ')');
    });
    L.push('');
    L.push('== 自查提示 ==');
    L.push('接口响应与页面里可能含你的姓名/学号;浏览器存储可能含登录令牌(离开页面即失效)。');
    L.push('提交前 Ctrl+F 搜自己的姓名和学号,改成 XXX 即可,不影响适配。');
    return L.join('\n');
  }

  // ---------- 8. 采集面板 ----------
  function stats() { return '已录制请求 ' + K.net.length + ' · 待打包'; }
  function refreshPanel() {
    var el = document.getElementById('__sleepy_stats');
    if (el) el.textContent = stats();
  }
  function mountPanel() {
    safe(function () {
      if (document.getElementById('__sleepy_panel')) return;
      var d = document.createElement('div');
      d.id = '__sleepy_panel';
      d.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;background:#1f2328;color:#f0f6fc;'
        + 'padding:14px 16px;border-radius:10px;font:13px/1.6 -apple-system,"Microsoft YaHei",sans-serif;'
        + 'box-shadow:0 6px 24px rgba(0,0,0,.35);max-width:330px;';
      d.innerHTML =
        '<div style="font-weight:bold;margin-bottom:4px;">Sleepy 课表采集器</div>'
        + '<div id="__sleepy_stats" style="color:#8ddb8c;margin-bottom:8px;">' + stats() + '</div>'
        + '<div style="color:#d0d7de;">只要能看到你的<strong>课表</strong>,直接点下面按钮。</div>'
        + '<div style="color:#d0d7de;margin:4px 0 10px;">页面、代码、接口数据会全部自动打包成一个 zip。</div>'
        + '<button id="__sleepy_btn" style="background:#2da44e;color:#fff;border:0;border-radius:6px;'
        + 'padding:8px 14px;font-size:14px;font-weight:bold;cursor:pointer;margin-right:10px;">生成采集包</button>'
        + '<a id="__sleepy_close" style="color:#8b949e;cursor:pointer;text-decoration:underline;">取消</a>';
      document.body.appendChild(d);
      document.getElementById('__sleepy_btn').addEventListener('click', run);
      document.getElementById('__sleepy_close').addEventListener('click', function () {
        var p = document.getElementById('__sleepy_panel');
        if (p) p.parentNode.removeChild(p);
      });
    });
  }

  // ---------- 9. 主流程 ----------
  function run() {
    var btn = document.getElementById('__sleepy_btn');
    if (btn) { btn.disabled = true; btn.textContent = '正在打包,请稍候…'; }
    var statsEl = document.getElementById('__sleepy_stats');
    if (statsEl) statsEl.style.display = 'none';           // 避免混入 DOM 快照
    setTimeout(function () {
      eachFrame(function (win, label) { captureDoc(win, label); });
      refetchAll(function (done, total) {
        if (btn) btn.textContent = '抓取页面文件 ' + done + '/' + total + '…';
      })
        .then(function (rr) {
          return replayApis(function (done) {
            if (btn) btn.textContent = '补抓接口数据 ' + done + '…';
          }).then(function (rp) {
            rr.replayOk = rp.ok; rr.mined = rp.mined;
            rr.skipped = rr.skipped.concat(rp.skipped);
            return rr;
          });
        })
        .then(function (rr) {
          // 实时录制的请求逐个入库
          K.net.forEach(function (e, i) {
            var head = 'VIA: ' + e.via + '\nMETHOD: ' + e.method + '\nURL: ' + e.url
              + '\nSTATUS: ' + e.status + '\nCONTENT-TYPE: ' + (e.contentType || '?')
              + '\nTIME: ' + (e.time || '') + '\n';
            var body = '';
            if (e.reqBody) body += '-------- 请求体 --------\n' + e.reqBody + '\n';
            if (e.body) body += '-------- 响应体 (' + e.body.length + ' 字符) --------\n' + e.body;
            addEntry(uniqPath(urlToPath('4-net-live', String(i + 1).padStart(3, '0') + '_' + e.method + '_' + e.url, '.txt')),
              '实时录制 ' + e.method + ' ' + e.url + ' → HTTP ' + e.status, head + body);
          });
          addEntry(uniqPath('6-logs/network-log.txt'), '浏览器网络日志', buildLogs());
          addEntry(uniqPath('6-logs/all-urls.txt'), '页面发现的一切 URL 及来源',
            Object.keys(K.urls).map(function (u) { return K.urls[u].join(' | ') + ' | ' + u; }).join('\n'));
          addEntry(uniqPath('6-logs/failed.txt'), '未能抓到内容的地址',
            rr.skipped.length ? rr.skipped.join('\n') : '(无,全部成功)');
          var statLine = '文件 ' + K.entries.length + ' 个 · 重取资源 ' + rr.ok + ' · 接口重放入包 ' + rr.replayOk
            + ' · 实时录制 ' + K.net.length + ' · 自动发现参数 ' + JSON.stringify(rr.mined || {});
          addEntry(uniqPath('INDEX.txt'), '包清单', buildIndex(statLine));
          var zip = buildZip(K.entries.map(function (f) { return { name: f.path, data: f.data }; }));
          var blob = new Blob([zip], { type: 'application/zip' });
          var a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = 'sleepy-adapt.zip';
          (document.body || document.documentElement).appendChild(a);
          a.click();
          if (btn) { btn.disabled = false; btn.textContent = '完成!可重新生成'; }
          console.log('%c✓ sleepy-adapt.zip 已生成(' + Math.round(zip.length / 1024) + ' KB)并开始下载。'
            + '\n若没自动下载,重新点一下绿色按钮。把它提交到 Sleepy 适配 issue / 指定邮箱。',
            'color:green;font-size:14px');
        })
        .catch(function (e) {
          alert('[sleepy] 打包出错: ' + (e && e.message || e));
          if (btn) { btn.disabled = false; btn.textContent = '生成采集包'; }
        });
    }, 60);
  }

  // ---------- 10. 启动 ----------
  instrument(window);
  eachFrame(function (win) { instrument(win); });
  setInterval(function () { eachFrame(function (win) { instrument(win); }); }, 1000);  // 后出现的 iframe 也装上记录器
  mountPanel();
  console.log('%c[Sleepy] 采集器已挂载 ✓ 看到你的课表后,点右下角面板的 [生成采集包]',
    'color:#0969da;font-size:14px;font-weight:bold');
})();
