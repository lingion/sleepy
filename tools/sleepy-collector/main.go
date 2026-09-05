// sleepy-collector: Sleepy 课表适配采集工具
//
// 同学双击运行 → 输入教务系统网址 → 弹出受控浏览器窗口 → 正常登录
// (验证码/2FA 原生可用) → 看到课表后点页面右下角绿色按钮「一键采集」
// → 工具自动把浏览器打开以来的全部网络请求(请求体+响应体,CDP 层捕获,
//
//	先于页面加载,无"粘贴晚了"缺口)、页面 DOM、内联代码、浏览器存储
//	打包成 sleepy-adapt.zip
//
// 密码框内容、Cookie 值、验证码图片不会写入包内。
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/cdproto/runtime"
	"github.com/chromedp/chromedp"
)

const version = "v1.0"

// ---------------- 网络捕获 ----------------

type reqRec struct {
	requestID network.RequestID
	url       string
	method    string
	postData  string
	status    int64
	mimeType  string
	frameID   string
	done      bool // loadingFinished 收到,可取响应体
	failed    bool
	body      string // 取回的响应体
	order     int
}

type Collector struct {
	recs    map[network.RequestID]*reqRec
	order   []network.RequestID
	urlSeen map[string]bool
	urlSrc  map[string][]string
	xnxq    string
	gnmkdm  string
}

func NewCollector() *Collector {
	return &Collector{
		recs:    map[network.RequestID]*reqRec{},
		urlSeen: map[string]bool{},
		urlSrc:  map[string][]string{},
	}
}

var logoutWords = []string{"logout", "signout", "log_off", "logoff", "tuichu", "zhuxiao"}

func isLogout(u string) bool {
	l := strings.ToLower(u)
	for _, k := range logoutWords {
		if strings.Contains(l, k) {
			return true
		}
	}
	return false
}

var binaryExt = map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".gif": true, ".bmp": true,
	".webp": true, ".ico": true, ".cur": true, ".woff": true, ".woff2": true, ".ttf": true, ".eot": true,
	".otf": true, ".mp3": true, ".mp4": true, ".wav": true, ".avi": true, ".mov": true, ".pdf": true,
	".doc": true, ".docx": true, ".xls": true, ".xlsx": true, ".ppt": true, ".pptx": true, ".zip": true,
	".rar": true, ".7z": true, ".gz": true, ".exe": true, ".apk": true, ".msi": true}

func looksBinary(u string) bool {
	s := strings.ToLower(strings.Split(u, "?")[0])
	i := strings.LastIndex(s, ".")
	return i >= 0 && binaryExt[s[i:]]
}

var semRe = regexp.MustCompile(`["=:\s](\d{4}-\d{4}-\d{1,2})[",\s]`)
var gnmRe = regexp.MustCompile(`gnmkdm['":=\s]+\[?([A-Z]?\d{4,5})\]?`)

// mineValues 从接口响应里挖参数值。只看短响应:参数报错文本很短;
// 大 JSON 里的学期码本来就带在数据里,不需要从里面猜。
func (c *Collector) mineValues(text string) {
	if len(text) > 400 {
		return
	}
	if c.xnxq == "" {
		if m := semRe.FindStringSubmatch(text); len(m) > 1 {
			c.xnxq = m[1]
		}
	}
	if c.gnmkdm == "" {
		if m := gnmRe.FindStringSubmatch(text); len(m) > 1 {
			c.gnmkdm = m[1]
		}
	}
}

func (c *Collector) minedStr() string {
	var parts []string
	if c.xnxq != "" {
		parts = append(parts, `"XNXQDM":"`+c.xnxq+`"`)
	}
	if c.gnmkdm != "" {
		parts = append(parts, `"GNMKDM":"`+c.gnmkdm+`"`)
	}
	return "{" + strings.Join(parts, ",") + "}"
}

func (c *Collector) onEvent(ev interface{}) {
	switch e := ev.(type) {
	case *network.EventRequestWillBeSent:
		r := &reqRec{
			requestID: e.RequestID,
			url:       e.Request.URL,
			method:    e.Request.Method,
			frameID:   string(e.FrameID),
			order:     len(c.order),
		}
		if e.Request.HasPostData && len(e.Request.PostDataEntries) > 0 {
			var sb strings.Builder
			for _, p := range e.Request.PostDataEntries {
				sb.WriteString(p.Bytes)
			}
			r.postData = sb.String()
		}
		if _, dup := c.recs[e.RequestID]; !dup {
			c.order = append(c.order, e.RequestID)
		}
		c.recs[e.RequestID] = r
		if !c.urlSeen[r.url] {
			c.urlSeen[r.url] = true
			c.urlSrc[r.url] = append(c.urlSrc[r.url], "网络请求 "+r.method)
		}
	case *network.EventResponseReceived:
		if r := c.recs[e.RequestID]; r != nil {
			r.status = e.Response.Status
			r.mimeType = e.Response.MimeType
		}
	case *network.EventLoadingFinished:
		if r := c.recs[e.RequestID]; r != nil {
			r.done = true
		}
	case *network.EventLoadingFailed:
		if r := c.recs[e.RequestID]; r != nil {
			r.failed = true
			if r.status == 0 {
				r.status = -1
			}
		}
	}
}

// ---------------- ZIP 写入(store,零依赖) ----------------

var crcTable [256]uint32

func init() {
	for n := uint32(0); n < 256; n++ {
		c := n
		for k := 0; k < 8; k++ {
			if c&1 == 1 {
				c = 0xEDB88320 ^ (c >> 1)
			} else {
				c >>= 1
			}
		}
		crcTable[n] = c
	}
}

func crc32(b []byte) uint32 {
	c := ^uint32(0)
	for _, x := range b {
		c = crcTable[(c^uint32(x))&0xFF] ^ (c >> 8)
	}
	return ^c
}

type zipEntry struct {
	name string
	data []byte
}

func put16(dst []byte, v uint16) []byte { return append(dst, byte(v), byte(v>>8)) }
func put32(dst []byte, v uint32) []byte {
	return append(dst, byte(v), byte(v>>8), byte(v>>16), byte(v>>24))
}

func buildZip(files []zipEntry) []byte {
	var body, central []byte
	var off uint32
	now := time.Now()
	dosTime := uint16(now.Hour()<<11 | now.Minute()<<5 | now.Second()/2)
	dosDate := uint16((now.Year()-1980)<<9 | int(now.Month())<<5 | now.Day())
	for _, f := range files {
		nameB := []byte(f.name)
		crc := crc32(f.data)
		lh := []byte{}
		lh = put32(lh, 0x04034b50)
		lh = put16(lh, 20)
		lh = put16(lh, 0x0800)
		lh = put16(lh, 0)
		lh = put16(lh, dosTime)
		lh = put16(lh, dosDate)
		lh = put32(lh, crc)
		lh = put32(lh, uint32(len(f.data)))
		lh = put32(lh, uint32(len(f.data)))
		lh = put16(lh, uint16(len(nameB)))
		lh = put16(lh, 0)
		lh = append(lh, nameB...)
		body = append(body, lh...)
		body = append(body, f.data...)
		ch := []byte{}
		ch = put32(ch, 0x02014b50)
		ch = put16(ch, 20)
		ch = put16(ch, 20)
		ch = put16(ch, 0x0800)
		ch = put16(ch, 0)
		ch = put16(ch, dosTime)
		ch = put16(ch, dosDate)
		ch = put32(ch, crc)
		ch = put32(ch, uint32(len(f.data)))
		ch = put32(ch, uint32(len(f.data)))
		ch = put16(ch, uint16(len(nameB)))
		ch = put16(ch, 0)
		ch = put16(ch, 0)
		ch = put16(ch, 0)
		ch = put16(ch, 0)
		ch = put32(ch, 0)
		ch = put32(ch, off)
		ch = append(ch, nameB...)
		central = append(central, ch...)
		off += uint32(len(lh) + len(f.data))
	}
	eocd := []byte{}
	eocd = put32(eocd, 0x06054b50)
	eocd = put16(eocd, 0)
	eocd = put16(eocd, 0)
	eocd = put16(eocd, uint16(len(files)))
	eocd = put16(eocd, uint16(len(files)))
	eocd = put32(eocd, uint32(len(central)))
	eocd = put32(eocd, off)
	eocd = put16(eocd, 0)
	return append(append(body, central...), eocd...)
}

// ---------------- 入库 ----------------

const maxFileBytes = 2 * 1024 * 1024
const maxTotalBytes = 20 * 1024 * 1024

type entry struct {
	path, meta string
	data       []byte
}

type packer struct {
	entries  []entry
	used     int
	seenPath map[string]bool
	seenBody map[string]bool
}

func newPacker() *packer { return &packer{seenPath: map[string]bool{}, seenBody: map[string]bool{}} }

func (p *packer) add(path, meta, body string) bool {
	if p.seenPath[path] {
		return false
	}
	slash := strings.LastIndex(path, "/")
	key := path[:slash+1] + "|" + body
	if p.seenBody[key] {
		p.seenPath[path] = true
		return false
	}
	p.seenBody[key] = true
	p.seenPath[path] = true
	data := []byte(body)
	if len(data) > maxFileBytes {
		data = data[:maxFileBytes]
		meta += " [单文件超限已截断]"
	}
	if p.used+len(data) > maxTotalBytes {
		remain := maxTotalBytes - p.used
		if remain < 1024 {
			return false
		}
		data = data[:remain]
		meta += " [整包已达上限,截断]"
	}
	p.used += len(data)
	p.entries = append(p.entries, entry{path: path, meta: meta, data: data})
	return true
}

func (p *packer) uniq(path string) string {
	if !p.seenPath[path] {
		return path
	}
	for n := 2; ; n++ {
		var cand string
		dot := strings.LastIndex(path, ".")
		if dot > strings.LastIndex(path, "/") {
			cand = path[:dot] + "_" + fmt.Sprint(n) + path[dot:]
		} else {
			cand = fmt.Sprintf("%s_%d", path, n)
		}
		if !p.seenPath[cand] {
			return cand
		}
	}
}

func urlToPath(prefix, u, ext string) string {
	s := strings.Split(u, "?")[0]
	parts := strings.Split(strings.Trim(s, "/"), "/")
	tail := ""
	if len(parts) >= 2 {
		tail = parts[len(parts)-2] + "_" + parts[len(parts)-1]
	} else if len(parts) == 1 {
		tail = parts[0]
	}
	var b strings.Builder
	for _, r := range tail {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') ||
			r == '.' || r == '_' || r == '-' {
			b.WriteRune(r)
		} else {
			b.WriteRune('_')
		}
	}
	tail = b.String()
	if len(tail) > 64 {
		tail = tail[len(tail)-64:]
	}
	if tail == "" {
		tail = "root"
	}
	return prefix + "/" + tail + ext
}

func extOf(ct, u string) string {
	cl := strings.ToLower(ct)
	switch {
	case strings.Contains(cl, "json"):
		return ".json"
	case strings.Contains(cl, "html"):
		return ".html"
	case strings.Contains(cl, "css"):
		return ".css"
	case strings.Contains(cl, "javascript"):
		return ".js"
	}
	s := strings.Split(u, "?")[0]
	if i := strings.LastIndex(s, "."); i >= 0 && len(s)-i <= 7 {
		e := strings.ToLower(s[i:])
		if !binaryExt[e] {
			return e
		}
	}
	return ".txt"
}

func isRich(t string) bool {
	if len(t) <= 500 {
		return false
	}
	for _, k := range []string{"rows", "timetables", "list"} {
		if strings.Contains(t, k) {
			return true
		}
	}
	return strings.Contains(t, "[{")
}

func looksError(t string) bool {
	if t == "" || len(t) > 400 {
		return false
	}
	for _, k := range []string{"为空", "不能", "必填"} {
		if strings.Contains(t, k) {
			return true
		}
	}
	l := strings.ToLower(t)
	return strings.Contains(l, "param") || strings.Contains(l, "required") ||
		strings.Contains(t, `"code":"1"`)
}

// ---------------- 浏览器端 JS 片段 ----------------

const jsStorage = `JSON.stringify((function(){
  var out = {sessionStorage:{}, localStorage:{}};
  ['sessionStorage','localStorage'].forEach(function(sn){
    try {
      var st = window[sn]; var m = {};
      if (st) { for (var i=0;i<st.length;i++){ var k=st.key(i); try{ m[k]=String(st.getItem(k)); }catch(e){} } }
      out[sn] = m;
    } catch(e) {}
  });
  return out;
})())`

const jsInline = `JSON.stringify((function(){
  var out = [];
  document.querySelectorAll('script:not([src])').forEach(function(el){
    var t = el.textContent||'';
    if (t.replace(/\s/g,'').length >= 10) out.push({kind:'script', text:t});
  });
  document.querySelectorAll('style').forEach(function(el){
    var t = el.textContent||'';
    if (t.replace(/\s/g,'').length >= 10) out.push({kind:'style', text:t});
  });
  return out;
})())`

const jsPerfURLs = `JSON.stringify((function(){
  var seen = {}, out = [];
  (performance.getEntriesByType('resource')||[]).forEach(function(r){
    if (!seen[r.name]) { seen[r.name]=1; out.push(r.name); }
  });
  return out;
})())`

const jsLinks = `JSON.stringify((function(){
  var seen = {}, out = [];
  document.querySelectorAll('a[href],iframe[src],form[action]').forEach(function(el){
    var h = el.getAttribute('href')||el.getAttribute('src')||el.getAttribute('action')||'';
    if ((h.indexOf('http')===0 || h.charAt(0)==='/') && !seen[h]) { seen[h]=1; out.push(el.baseURI ? new URL(h, el.baseURI).href : h); }
  });
  return out;
})())`

// evalAsync 在页面里执行返回 Promise 的表达式并等待结果。
// 必须经 chromedp.ActionFunc 拿到 chromedp 内部的执行 ctx;直接用外层 ctx 会报 invalid context。
func evalAsync(ctx context.Context, expr string, res any) error {
	return chromedp.Run(ctx, chromedp.ActionFunc(func(ictx context.Context) error {
		return chromedp.Evaluate(expr, res, func(p *runtime.EvaluateParams) *runtime.EvaluateParams {
			return p.WithAwaitPromise(true).WithReturnByValue(true)
		}).Do(ictx)
	}))
}

func fetchJS(u string) string {
	return `(async function(){
  try {
    const r = await fetch(` + jsStr(u) + `, {credentials:'include'});
    const ct = r.headers.get('content-type') || '';
    const t = await r.text();
    return JSON.stringify({s: r.status, ct: ct, b: t.slice(0, 2*1024*1024)});
  } catch(e) { return JSON.stringify({s: 0, ct: '', b: ''}); }
})()`
}

func postJS(u, body string) string {
	return `(async function(){
  try {
    const r = await fetch(` + jsStr(u) + `, {method:'POST', credentials:'include',
      headers: {'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'XMLHttpRequest'},
      body: ` + jsStr(body) + `});
    const ct = r.headers.get('content-type') || '';
    const t = await r.text();
    return JSON.stringify({s: r.status, ct: ct, b: t.slice(0, 2*1024*1024)});
  } catch(e) { return JSON.stringify({s: 0, ct: '', b: ''}); }
})()`
}

func jsStr(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

// ---------------- 按钮注入 ----------------

const btnJS = `(function(){
  if (document.getElementById('__sleepy_btn_wrap')) return 'present';
  if (!document.body) return 'nobody';
  var d = document.createElement('div');
  d.id = '__sleepy_btn_wrap';
  d.style.cssText = 'position:fixed;right:18px;bottom:18px;z-index:2147483647;';
  var b = document.createElement('button');
  b.textContent = '✓ 课表没问题,一键采集打包';
  b.style.cssText = 'background:#2da44e;color:#fff;border:0;border-radius:10px;padding:14px 22px;'
    + 'font-size:16px;font-weight:bold;cursor:pointer;box-shadow:0 6px 24px rgba(0,0,0,.4);';
  b.onclick = function(){
    b.disabled = true; b.textContent = '正在打包,请不要关闭页面…';
    window.__sleepyDone = true;
  };
  d.appendChild(b);
  document.body.appendChild(d);
  return 'injected';
})()`

// ---------------- 主流程 ----------------

func main() {
	fmt.Println("=====================================================")
	fmt.Println("  Sleepy 课表适配采集工具 " + version)
	fmt.Println("=====================================================")
	fmt.Println()
	fmt.Println("流程:输入教务网址 → 弹出浏览器 → 你在里面登录并打开课表 →")
	fmt.Println("      点页面右下角绿色按钮 → 自动生成 sleepy-adapt.zip")
	fmt.Println()
	var target string
	if auto := os.Getenv("SLEEPY_COLLECTOR_URL"); auto != "" {
		target = auto
		fmt.Println("(自动化模式,网址来自环境变量:", auto, ")")
	} else {
		fmt.Print("请输入教务系统网址(例如 jwxt.xxx.edu.cn): ")
		fmt.Scanln(&target)
	}
	target = strings.TrimSpace(target)
	if target == "" {
		fmt.Println("未输入网址,退出。")
		return
	}
	if !strings.HasPrefix(target, "http://") && !strings.HasPrefix(target, "https://") {
		target = "https://" + target
	}

	headless := os.Getenv("SLEEPY_COLLECTOR_HEADLESS") == "1"
	// 定位浏览器:本机 Chrome/Edge/Chromium → 都没有则自动下载官方内核
	browserPath, err := findBrowser()
	if err != nil {
		fmt.Println("浏览器准备失败:", err)
		waitEnter()
		return
	}
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.ExecPath(browserPath),
		chromedp.Flag("headless", headless),
		chromedp.Flag("start-maximized", true),
		chromedp.Flag("ignore-certificate-errors", true),
		chromedp.UserDataDir(filepath.Join(os.TempDir(), "sleepy-collector-profile")),
	)
	// 自动化测试钩子:暴露 CDP 端口,供外部脚本驱动工具的浏览器(模拟人工操作)
	if os.Getenv("SLEEPY_COLLECTOR_CDP") != "" {
		opts = append(opts,
			chromedp.Flag("remote-debugging-address", "127.0.0.1"),
			chromedp.Flag("remote-debugging-port", "9333"),
		)
	}
	allocCtx, allocCancel := chromedp.NewExecAllocator(context.Background(), opts...)
	defer allocCancel()
	browserCtx, browserCancel := chromedp.NewContext(allocCtx)
	defer browserCancel()
	browserCtx, timeoutCancel := context.WithTimeout(browserCtx, 40*time.Minute)
	defer timeoutCancel()

	c := NewCollector()
	chromedp.ListenTarget(browserCtx, func(ev interface{}) { c.onEvent(ev) })

	fmt.Println()
	fmt.Println("正在启动浏览器…")
	err = chromedp.Run(browserCtx,
		network.Enable(),
		chromedp.Navigate(target),
		chromedp.WaitVisible("body", chromedp.ByQuery),
	)
	if err != nil {
		fmt.Println("浏览器启动失败:", err)
		fmt.Println("(本机需要安装 Chrome 或 Edge)")
		waitEnter()
		return
	}
	injectBtn(browserCtx)
	fmt.Println("浏览器已打开。请在窗口里登录教务系统,进入你的课表页面。")
	fmt.Println("看到课表后,点页面右下角绿色按钮「一键采集打包」;")
	fmt.Println("找不到按钮的话,回到这个黑色窗口按一次回车也行。")
	fmt.Println()

	go func() {
		if os.Getenv("SLEEPY_COLLECTOR_HEADLESS") == "1" {
			return // 自动化模式:由外部脚本设置 __sleepyDone
		}
		fmt.Scanln()
		fmt.Println("(终端回车触发采集)")
		setDone(browserCtx)
	}()

	for {
		select {
		case <-browserCtx.Done():
			fmt.Println("浏览器已关闭或超时,退出。")
			return
		default:
		}
		var done bool
		_ = chromedp.Run(browserCtx, chromedp.Evaluate(`typeof __sleepyDone !== 'undefined' && __sleepyDone === true`, &done))
		if done {
			break
		}
		time.Sleep(700 * time.Millisecond)
		injectBtn(browserCtx) // 页面跳转会清掉按钮,每次循环补挂(幂等)
	}

	fmt.Println()
	fmt.Println("收到采集指令,正在打包(页面文件多时需要一两分钟,别关窗口)…")
	zipPath, err := c.packageAll(browserCtx)
	if err != nil {
		fmt.Println("打包失败:", err)
		waitEnter()
		return
	}
	abs, _ := filepath.Abs(zipPath)
	fmt.Println()
	fmt.Println("=====================================================")
	fmt.Println("✓ 采集完成:", abs)
	fmt.Println()
	fmt.Println("接下来:把这个 zip 文件作为附件发到 Sleepy 的适配 issue")
	fmt.Println("(https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml)")
	fmt.Println("或 Sleepy 应用「关于」页公布的适配邮箱,写上你学校的名字即可。")
	fmt.Println("=====================================================")
	openFolder(abs)
	waitEnter()
}

func setDone(ctx context.Context) {
	_ = chromedp.Run(ctx, chromedp.Evaluate(`window.__sleepyDone = true; 1`, nil))
}

func injectBtn(ctx context.Context) {
	var r string
	_ = chromedp.Run(ctx, chromedp.Evaluate(btnJS, &r))
}

func waitEnter() {
	fmt.Print("(按回车关闭) ")
	fmt.Scanln()
}

func openFolder(path string) {
	dir := filepath.Dir(path)
	switch {
	case fileExists("/usr/bin/open"): // macOS
		_ = exec.Command("/usr/bin/open", dir).Start()
	case fileExists("/usr/bin/xdg-open"):
		_ = exec.Command("/usr/bin/xdg-open", dir).Start()
	case fileExists("C:\\Windows\\explorer.exe"):
		_ = exec.Command("C:\\Windows\\explorer.exe", dir).Start()
	}
}

func fileExists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

// ---------------- 打包 ----------------

func (c *Collector) packageAll(ctx context.Context) (string, error) {
	p := newPacker()

	// ---- 1. 页面状态 ----
	var title, ua, cookieFull, mainURL, mainHTML string
	_ = chromedp.Run(ctx,
		chromedp.Title(&title),
		chromedp.Evaluate(`navigator.userAgent`, &ua),
		chromedp.Evaluate(`document.cookie`, &cookieFull),
		chromedp.Evaluate(`location.href`, &mainURL),
		chromedp.Evaluate(`document.documentElement.outerHTML`, &mainHTML),
	)
	var cookieNames []string
	for _, kv := range strings.Split(cookieFull, ";") {
		kv = strings.TrimSpace(kv)
		if i := strings.Index(kv, "="); i > 0 {
			cookieNames = append(cookieNames, kv[:i])
		}
	}
	if mainHTML != "" {
		p.add(p.uniq("1-dom/top_page.html"), "页面DOM(打包时刻)", mainHTML)
	}
	var storJSON string
	_ = chromedp.Run(ctx, chromedp.Evaluate(jsStorage, &storJSON))
	var stor map[string]map[string]string
	if json.Unmarshal([]byte(storJSON), &stor) == nil {
		for _, kind := range []string{"sessionStorage", "localStorage"} {
			m := stor[kind]
			if len(m) == 0 {
				continue
			}
			var lines []string
			for k, v := range m {
				if len(v) > 4096 {
					v = v[:4096] + "...[截断]"
				}
				lines = append(lines, k+" = "+v)
			}
			sort.Strings(lines)
			p.add(p.uniq("5-storage/top_"+kind+".txt"), kind, strings.Join(lines, "\n"))
		}
	}
	var inlineJSON string
	_ = chromedp.Run(ctx, chromedp.Evaluate(jsInline, &inlineJSON))
	var inline []map[string]string
	if json.Unmarshal([]byte(inlineJSON), &inline) == nil {
		for i, x := range inline {
			kind := x["kind"]
			ext := "js"
			if kind == "style" {
				ext = "css"
			}
			p.add(p.uniq(fmt.Sprintf("2-inline/top_%s%02d.%s", kind, i+1, ext)), "内联 "+kind, x["text"])
		}
	}

	// ---- 2. CDP 捕获的网络请求:取响应体并入库 ----
	origin := ""
	_ = chromedp.Run(ctx, chromedp.Evaluate(`location.origin`, &origin))
	fmt.Printf("  网络请求共 %d 个,正在取响应体…\n", len(c.order))
	netOK := 0
	for idx, rid := range c.order {
		r := c.recs[rid]
		if r == nil || isLogout(r.url) {
			continue
		}
		// 响应体:loadingFinished 且非失败,CDP 直取(同源跨源都行,这是 CDP 的优势)
		if r.done && !r.failed && r.status >= 200 && r.status < 300 && !looksBinary(r.url) {
			var body string
			err := chromedp.Run(ctx, chromedp.ActionFunc(func(ictx context.Context) error {
				b, err := network.GetResponseBody(rid).Do(ictx)
				if err != nil {
					return err
				}
				body = string(b)
				return nil
			}))
			if err == nil && body != "" {
				r.body = body
				c.mineValues(body)
			}
		}
		if r.body == "" && r.postData == "" {
			continue // 纯静态资源没取到体的,3-res 阶段会重取
		}
		head := fmt.Sprintf("METHOD: %s\nURL: %s\nSTATUS: %d\nCONTENT-TYPE: %s\nFRAME: %s\n\n",
			r.method, r.url, r.status, r.mimeType, r.frameID)
		content := head
		if r.postData != "" {
			content += "-------- 请求体 --------\n" + r.postData + "\n"
		}
		if r.body != "" {
			content += fmt.Sprintf("-------- 响应体 (%d 字符) --------\n%s", len(r.body), r.body)
		}
		meta := fmt.Sprintf("%s %s → HTTP %d · %s", r.method, r.url, r.status, r.mimeType)
		name := fmt.Sprintf("%03d_%s_", idx+1, r.method)
		if p.add(p.uniq(urlToPath("4-net-live", name+r.url, extOf(r.mimeType, r.url))), meta, content) {
			netOK++
		}
	}

	// ---- 3. URL 总表(性能日志 + 页内链接补全) ----
	var perfJSON string
	_ = chromedp.Run(ctx, chromedp.Evaluate(jsPerfURLs, &perfJSON))
	var perfURLs []string
	_ = json.Unmarshal([]byte(perfJSON), &perfURLs)
	for _, u := range perfURLs {
		if !c.urlSeen[u] {
			c.urlSeen[u] = true
			c.urlSrc[u] = append(c.urlSrc[u], "性能日志")
		}
	}
	var linkJSON string
	_ = chromedp.Run(ctx, chromedp.Evaluate(jsLinks, &linkJSON))
	var links []string
	_ = json.Unmarshal([]byte(linkJSON), &links)
	for _, u := range links {
		if !c.urlSeen[u] {
			c.urlSeen[u] = true
			c.urlSrc[u] = append(c.urlSrc[u], "页内链接")
		}
	}
	var allURLs []string
	for u := range c.urlSrc {
		allURLs = append(allURLs, u)
	}
	sort.Strings(allURLs)

	// ---- 4. 资源重取(同站 GET,带凭证;串行) ----
	// 同站 = 同根域(含子域),教务的静态资源常放在 res.xxx.edu.cn 这类子域
	siteHost := hostOf(mainURL)
	resOK := 0
	refSkips := map[string]int{}
	refTried := 0
	for _, u := range allURLs {
		if resOK >= 200 {
			break
		}
		if isLogout(u) || looksBinary(u) {
			refSkips["binary/logout"]++
			continue
		}
		if !sameSite(hostOf(u), siteHost) {
			refSkips["cross-site"]++
			continue
		}
		refTried++
		var resp string
		evErr := evalAsync(ctx, fetchJS(u), &resp)
		if evErr == nil && len(resp) > 2 {
			var fr struct {
				Status int64  `json:"s"`
				CT     string `json:"ct"`
				Body   string `json:"b"`
			}
			if json.Unmarshal([]byte(resp), &fr) == nil && fr.Status == 200 && len(fr.Body) > 20 {
				if p.add(p.uniq(urlToPath("3-res", u, extOf(fr.CT, u))), "重取 GET "+u+fmt.Sprintf(" · HTTP %d · %s", fr.Status, fr.CT), fr.Body) {
					resOK++
				}
			}
		}
	}

	// ---- 5. 接口两阶段重放 ----
	fmt.Println("  正在重放数据接口…")
	var apis []string
	for _, rid := range c.order {
		r := c.recs[rid]
		if r == nil || isLogout(r.url) || looksBinary(r.url) {
			continue
		}
		if !sameSite(hostOf(r.url), siteHost) {
			continue
		}
		if r.mimeType != "" && !strings.Contains(strings.ToLower(r.mimeType), "json") &&
			!strings.Contains(strings.ToLower(r.mimeType), "text") &&
			!strings.Contains(strings.ToLower(r.mimeType), "javascript") &&
			!strings.Contains(strings.ToLower(r.mimeType), "html") {
			continue
		}
		apis = append(apis, r.url)
	}
	sort.Strings(apis)
	apis = uniqStrings(apis)
	if len(apis) > 100 {
		apis = apis[:100]
	}
	replayOK := 0
	var retry []string
	for i, u := range apis {
		if (i+1)%10 == 0 {
			fmt.Printf("    接口空体重放 %d/%d\n", i+1, len(apis))
		}
		var resp string
		if err := evalAsync(ctx, postJS(u, ""), &resp); err == nil && len(resp) > 2 {
			var fr struct {
				Status int64  `json:"s"`
				CT     string `json:"ct"`
				Body   string `json:"b"`
			}
			if json.Unmarshal([]byte(resp), &fr) == nil && len(fr.Body) >= 5 {
				c.mineValues(fr.Body)
				if p.add(p.uniq(urlToPath("4-net-replay", u, extOf(fr.CT, u))), "接口重放(空体) POST "+u, fr.Body) {
					replayOK++
				}
				retry = append(retry, u) // 回了数据的也带参重试(可能拿更全数据)
			}
		}
	}
	if c.xnxq != "" && len(retry) > 0 {
		body := "XNXQDM=" + urlEscape(c.xnxq)
		if c.gnmkdm != "" {
			body += "&gnmkdm=" + urlEscape(c.gnmkdm)
		}
		fmt.Println("  发现参数", c.minedStr(), "→ 带参数二次重试…")
		if len(retry) > 40 {
			retry = retry[:40]
		}
		for i, u := range retry {
			if (i+1)%10 == 0 {
				fmt.Printf("    带参重试 %d/%d\n", i+1, len(retry))
			}
			var resp string
			if err := evalAsync(ctx, postJS(u, body), &resp); err == nil && len(resp) > 2 {
				var fr struct {
					Status int64  `json:"s"`
					CT     string `json:"ct"`
					Body   string `json:"b"`
				}
				if json.Unmarshal([]byte(resp), &fr) == nil && isRich(fr.Body) {
					c.mineValues(fr.Body)
					if p.add(p.uniq(urlToPath("4-net-replay-withparam", u, extOf(fr.CT, u))), "接口重放(带参数) POST "+u+"  body: "+body, fr.Body) {
						replayOK++
					}
				}
			}
		}
	}

	// ---- 6. 日志与清单 ----
	var logLines []string
	for _, rid := range c.order {
		if r := c.recs[rid]; r != nil {
			logLines = append(logLines, fmt.Sprintf("[frame %s] %s %s → HTTP %d", r.frameID, r.method, r.url, r.status))
		}
	}
	p.add(p.uniq("6-logs/network-log.txt"), "网络日志(浏览器打开以来的全部请求)", strings.Join(logLines, "\n"))
	var urlLines []string
	for _, u := range allURLs {
		urlLines = append(urlLines, strings.Join(c.urlSrc[u], " | ")+" | "+u)
	}
	p.add(p.uniq("6-logs/all-urls.txt"), "发现的一切 URL 及来源", strings.Join(urlLines, "\n"))

	statLine := fmt.Sprintf("文件 %d 个 · 请求入库 %d · 资源重取 %d · 接口重放入包 %d · 自动发现参数 %s",
		len(p.entries), netOK, resOK, replayOK, c.minedStr())
	var idx strings.Builder
	idx.WriteString("Sleepy 课表采集包 (sleepy-collector " + version + ")\n")
	idx.WriteString("生成时间: " + time.Now().Format("2006-01-02 15:04:05") + "\n")
	idx.WriteString("页面: " + mainURL + "\n标题: " + title + "\nUser-Agent: " + ua + "\n")
	idx.WriteString("Cookie 名(只有名字,没有值): " + strings.Join(cookieNames, ", ") + "\n\n")
	idx.WriteString("== 概况 ==\n" + statLine + "\n\n== 目录说明 ==\n")
	idx.WriteString("1-dom/ 页面DOM · 2-inline/ 内联代码 · 3-res/ 重取的资源文件\n")
	idx.WriteString("4-net-live/ CDP捕获的请求(含响应体) · 4-net-replay/ 接口重放(withparam=带参数)\n")
	idx.WriteString("5-storage/ 浏览器存储 · 6-logs/ 日志\n\n== 文件清单(路径 | 说明) ==\n")
	for _, e := range p.entries {
		idx.WriteString(e.path + "  |  " + e.meta + "\n")
	}
	idx.WriteString("\n== 自查提示 ==\n接口响应与页面里可能含你的姓名/学号;提交前搜索改成 XXX 即可,不影响适配。\n")
	p.add(p.uniq("INDEX.txt"), "包清单", idx.String())

	files := make([]zipEntry, 0, len(p.entries))
	for _, e := range p.entries {
		files = append(files, zipEntry{name: e.path, data: e.data})
	}
	zipData := buildZip(files)
	home, _ := os.UserHomeDir()
	name := fmt.Sprintf("sleepy-adapt-%s.zip", time.Now().Format("0102-150405"))
	out := filepath.Join(home, "Downloads", name)
	if err := os.WriteFile(out, zipData, 0644); err != nil {
		out = name
		if err := os.WriteFile(out, zipData, 0644); err != nil {
			return "", err
		}
	}
	fmt.Println("  打包完成:", statLine)
	return out, nil
}

func hostOf(u string) string {
	s := strings.Split(u, "//")
	if len(s) < 2 {
		return ""
	}
	h := strings.Split(s[1], "/")[0]
	if i := strings.Index(h, "@"); i >= 0 {
		h = h[i+1:]
	}
	if i := strings.Index(h, ":"); i >= 0 {
		h = h[:i]
	}
	return strings.ToLower(h)
}

// sameSite 判断两 host 是否同根域(粗略 eTLD+1:取末两段;edu.cn/gov.cn 等二段后缀取末三段)
func sameSite(a, b string) bool {
	if a == "" || b == "" {
		return false
	}
	if a == b {
		return true
	}
	root := func(h string) string {
		parts := strings.Split(h, ".")
		if len(parts) >= 3 {
			pen := parts[len(parts)-2]
			if pen == "edu" || pen == "gov" || pen == "org" || pen == "com" || pen == "ac" {
				return strings.Join(parts[len(parts)-3:], ".")
			}
		}
		if len(parts) >= 2 {
			return strings.Join(parts[len(parts)-2:], ".")
		}
		return h
	}
	return root(a) == root(b)
}

func uniqStrings(in []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, s := range in {
		if !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	return out
}

func urlEscape(s string) string {
	var b strings.Builder
	for _, r := range []byte(s) {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') ||
			r == '-' || r == '_' || r == '.' || r == '~' {
			b.WriteByte(r)
		} else {
			fmt.Fprintf(&b, "%%%02X", r)
		}
	}
	return b.String()
}
