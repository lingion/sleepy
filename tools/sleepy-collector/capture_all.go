// capture_all.go: G13 全域捕获层 — 缓存禁用 / 大 POST 体兜底 / 跨源 iframe /
// 文件下载 / Console / WebSocket 帧 / 请求头捕获(脱敏) / Cookie 全貌 / HAR 导出。
//
// 设计约束:
//   - 不碰 main.go 的 G1-G12 既有逻辑, 纯增量
//   - 隐私铁律: Cookie 值、密码框、凭据头的值, 永不入包
//   - 事件回调内禁同步 Run(死锁律), 一律走 worker 或打包阶段
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	cdpbrowser "github.com/chromedp/cdproto/browser"
	"github.com/chromedp/cdproto/emulation"
	"github.com/chromedp/cdproto/log"
	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/cdproto/runtime"
	"github.com/chromedp/cdproto/target"
	"github.com/chromedp/chromedp"
)

// ---------------- 配置读取 ----------------

// uaOverrideFromEnv 移动端 UA 伪装开关 (SLEEPY_COLLECTOR_UA)。
// 强智 qz_app 等移动教务 H5 端点校验 UA, 桌面 Chrome 的 UA 会被服务端拒。
func uaOverrideFromEnv() string {
	return strings.TrimSpace(os.Getenv("SLEEPY_COLLECTOR_UA"))
}

// ---------------- 请求头捕获与脱敏 ----------------

// credHeaderNames 判定"凭据头": 值必须脱敏, 名字保留 (适配者要知道有这个头,
// 但值是 token/密码, 铁律不入包)。
var credHeaderNames = []string{"cookie", "authorization", "proxy-authorization", "x-api-token", "x-token", "token", "api-key", "x-api-key", "x-auth", "x-csrf-token", "csrf-token", "x-xsrf-token"}

func isCredHeader(name string) bool {
	l := strings.ToLower(name)
	for _, c := range credHeaderNames {
		if l == c {
			return true
		}
	}
	// csrf/xsrf/token 类自定义头一律按凭据处理 (保守脱敏)
	return strings.Contains(l, "csrf") || strings.Contains(l, "xsrf")
}

// redactRequestHeaders 把请求头压成逐行文本: 凭据头值脱敏, 其余原样。
func redactRequestHeaders(headers network.Headers) string {
	return headersText(redactHeadersMap(headers))
}

func redactHeadersMap(headers network.Headers) map[string]string {
	if len(headers) == 0 {
		return map[string]string{}
	}
	names := make([]string, 0, len(headers))
	for k := range headers {
		names = append(names, k)
	}
	sort.Strings(names)
	out := map[string]string{}
	for _, k := range names {
		if isCredHeader(k) {
			out[k] = "(值省略)"
			continue
		}
		out[k] = fmt.Sprintf("%v", headers[k])
	}
	return out
}

// headersText map → 逐行 "Name: value" (按名字排序)。
func headersText(m map[string]string) string {
	names := make([]string, 0, len(m))
	for k := range m {
		names = append(names, k)
	}
	sort.Strings(names)
	var lines []string
	for _, k := range names {
		lines = append(lines, k+": "+m[k])
	}
	return strings.Join(lines, "\n")
}

// ---------------- Console 与 WebSocket 记录 ----------------

// consoleEntry console/log/runtime 三源 console 记录 (截断在写入时统一做)。
type consoleEntry struct {
	time   string
	level  string
	source string
	url    string
	text   string
}

func newConsoleEntry(level, source, url, text string) *consoleEntryAdded {
	return &consoleEntryAdded{e: &consoleEntry{
		time:   time.Now().Format("15:04:05"),
		level:  level,
		source: source,
		url:    url,
		text:   text,
	}}
}

// consoleEntryAdded 测试辅助类型 — 喂给 onEventTab 的模拟 console 事件。
type consoleEntryAdded struct{ e *consoleEntry }

// wsRec 一个 WebSocket 连接的帧记录。
type wsRec struct {
	url   string
	time  string
	lines []string // 逐帧 "↑/↓ [text|bin N] payload/truncated"
}

const (
	consoleMaxPerEntry  = 2000 // 单条 console 文本截断
	consoleMaxEntries   = 200  // 条数上限
	consoleMaxTotalText = 200 * 200
	wsMaxPayloadText    = 2000    // 文本帧载荷截断
	wsMaxFrames         = 400     // 每连接帧数上限
	wsBinaryMaxShow     = 512     // 二进制帧最多展示字节数(hex) — 0 关闭
	wsFrameLineMax      = 4 << 10 // 单帧行字符上限
)

// enableCapture 每 tab 一次: network(缓存禁用+大体量) + log + runtime console
// + UA override。在非事件回调 goroutine 调用 (与 discoverTabs 同层)。
func (c *Collector) enableCapture(ctx context.Context) error {
	return chromedp.Run(ctx,
		network.Enable().WithMaxPostDataSize(8<<20), // 8MB 内联上限, 更大的走 GetRequestPostData 兜底
		network.SetCacheDisabled(true),              // 二访 disk cache 命中会让 GetResponseBody 报 -32000, body 直接丢
		log.Enable(),
		runtime.Enable(),
	)
}

// applyUserAgentOverride 可选: 移动端 UA 伪装 (环境变量开启才生效)。
func applyUserAgentOverride(ctx context.Context) error {
	ua := uaOverrideFromEnv()
	if ua == "" {
		return nil
	}
	return chromedp.Run(ctx, emulation.SetUserAgentOverride(ua).WithAcceptLanguage("zh-CN,zh;q=0.9"))
}

// consoleArgText 把 runtime.RemoteObject 参数压成短文本 (console.log("x", 1) 的多参)。
func consoleArgText(a *runtime.RemoteObject) string {
	if a == nil {
		return ""
	}
	if a.Description != "" {
		return a.Description
	}
	v := string(a.Value)
	switch a.Type {
	case "string":
		// jsontext.Value 是原始 JSON 文本 ("…") — 剥掉外层引号
		var s string
		if err := json.Unmarshal(a.Value, &s); err == nil {
			return s
		}
		return v
	case "undefined":
		return "undefined"
	default:
		return v
	}
}

// handleConsoleEvent 归一处理 log.EntryAdded / runtime consoleAPICalled。
// 从 onEventTab 分发进来 (tab 已知), 不做任何 Run。
func (c *Collector) handleConsoleEvent(level, source, url, text string) {
	if len(text) > consoleMaxPerEntry {
		text = text[:consoleMaxPerEntry] + "...[截断]"
	}
	c.tmu.Lock()
	c.consoleEntries = append(c.consoleEntries, consoleEntry{
		time:   time.Now().Format("15:04:05"),
		level:  level,
		source: source,
		url:    url,
		text:   text,
	})
	c.tmu.Unlock()
}

// consoleText 全量 console 文本 (入包 6-logs/console.txt)。
func (c *Collector) consoleText() string {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	var sb strings.Builder
	total := 0
	for _, e := range c.consoleEntries {
		if total >= consoleMaxTotalText {
			sb.WriteString("...[后续条目省略]\n")
			break
		}
		line := fmt.Sprintf("[%s] [%s] [%s] %s", e.time, e.level, e.source, e.text)
		if e.url != "" {
			line += fmt.Sprintf("  @ %s", e.url)
		}
		sb.WriteString(line + "\n")
		total += len(line)
	}
	if len(c.consoleEntries) == 0 {
		sb.WriteString("(无)\n")
	}
	return sb.String()
}

// handleWSEvent WebSocket 建连/帧事件。帧载荷: 文本截断保留, 二进制只记字节数。
func (c *Collector) handleWSEvent(tabSeq int, ev interface{}) {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	switch e := ev.(type) {
	case *network.EventWebSocketCreated:
		c.wsConns[e.RequestID] = &wsRec{
			url:  e.URL,
			time: time.Now().Format("15:04:05"),
		}
	case *network.EventWebSocketFrameSent:
		c.wsAppendLocked(e.RequestID, "↑", e.Response)
	case *network.EventWebSocketFrameReceived:
		c.wsAppendLocked(e.RequestID, "↓", e.Response)
	case *network.EventWebSocketFrameError:
		if w := c.wsConns[e.RequestID]; w != nil {
			w.lines = append(w.lines, fmt.Sprintf("! 帧错误: %s", e.ErrorMessage))
		}
	}
}

// wsAppendLocked 追加一帧 (调用方持锁)。
func (c *Collector) wsAppendLocked(rid network.RequestID, dir string, f *network.WebSocketFrame) {
	w := c.wsConns[rid]
	if w == nil {
		return
	}
	if len(w.lines) >= wsMaxFrames {
		if len(w.lines) == wsMaxFrames {
			w.lines = append(w.lines, "...[后续帧省略]")
		}
		return
	}
	var line string
	if f == nil {
		line = dir + " ?"
	} else if f.Opcode == 2 || f.Opcode == 9 || f.Opcode == 10 { // binary / ping / pong
		line = fmt.Sprintf("%s (二进制 %d 字节)", dir, len(f.PayloadData))
	} else {
		p := f.PayloadData
		if len(p) > wsMaxPayloadText {
			p = p[:wsMaxPayloadText] + "...[截断]"
		}
		line = dir + " " + p
	}
	if len(line) > wsFrameLineMax {
		line = line[:wsFrameLineMax] + "...[截断]"
	}
	w.lines = append(w.lines, line)
}

// wsLogText 全部 WS 连接的逐帧记录 (入包 6-logs/websockets.txt)。
func (c *Collector) wsLogText() string {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	var sb strings.Builder
	n := 0
	for rid, w := range c.wsConns {
		n++
		fmt.Fprintf(&sb, "== WS#%d [%s] %s ==\n", n, w.time, w.url)
		_ = rid
		for _, l := range w.lines {
			sb.WriteString(l + "\n")
		}
		sb.WriteString("\n")
	}
	if n == 0 {
		sb.WriteString("(无)\n")
	}
	return sb.String()
}

// ---------------- 请求头 (ExtraInfo) 与 Cookie 全貌 ----------------

// handleExtraInfo RequestWillBeSentExtraInfo — 真正发上线的请求头 (含 Cookie
// 展开与浏览器补的头)。凭据值脱敏后按 (tab, requestID) 配对挂到 extraInfoText。
func (c *Collector) handleExtraInfo(tabSeq int, rid network.RequestID, e *network.EventRequestWillBeSentExtraInfo) {
	m := redactHeadersMap(e.Headers)
	// AssociatedCookies 只留名字+blockedReason (Cookie 值铁律不入包)
	if len(e.AssociatedCookies) > 0 {
		var names []string
		for _, ac := range e.AssociatedCookies {
			if ac == nil {
				continue
			}
			entry := ac.Cookie.Name
			if len(ac.BlockedReasons) > 0 {
				entry += "(blocked)"
			}
			names = append(names, entry)
		}
		if len(names) > 0 {
			m["__associated_cookies__"] = strings.Join(names, "; ")
		}
	}
	c.tmu.Lock()
	if c.extraInfoText == nil {
		c.extraInfoText = map[reqKey]string{}
	}
	c.extraInfoText[reqKey{tab: tabSeq, rid: rid}] = headersText(m)
	c.tmu.Unlock()
}

// cookieFullText 打包阶段取全量 cookie (仅名+域+过期+安全位, 值铁律不入包)。
func cookieFullText(ctx context.Context) string {
	var cookies []*network.Cookie
	err := chromedp.Run(ctx, chromedp.ActionFunc(func(ictx context.Context) error {
		var err error
		cookies, err = network.GetCookies().Do(ictx)
		return err
	}))
	if err != nil {
		return "(取 cookie 失败: " + err.Error() + ")"
	}
	if len(cookies) == 0 {
		return "(无 cookie)\n"
	}
	var sb strings.Builder
	fmt.Fprintf(&sb, "共 %d 条 (值永不入包)\n", len(cookies))
	for _, ck := range cookies {
		expiry := "会话级"
		if ck.Expires > 0 {
			expiry = time.Unix(int64(ck.Expires), 0).Format("2006-01-02 15:04")
		}
		flags := ""
		if ck.HTTPOnly {
			flags += " httpOnly"
		}
		if ck.Secure {
			flags += " secure"
		}
		fmt.Fprintf(&sb, "%s  domain=%s path=%s 到期=%s 大小=%dB%s\n",
			ck.Name, ck.Domain, ck.Path, expiry, ck.Size, flags)
	}
	return sb.String()
}

// ---------------- 文件下载捕获 ----------------

// enableDownloads 声明"允许下载且文件落到指定目录"+下载事件开关。
// 不调它, 教务"导出课表 xls"只会把下载静默丢弃 — 采集包里缺导出格式的课表。
func (c *Collector) enableDownloads(ctx context.Context) error {
	c.downloadDir = filepath.Join(os.TempDir(), fmt.Sprintf("sleepy-dl-%d", time.Now().UnixNano()))
	if err := os.MkdirAll(c.downloadDir, 0755); err != nil {
		return err
	}
	return chromedp.Run(ctx,
		cdpbrowser.SetDownloadBehavior(cdpbrowser.SetDownloadBehaviorBehaviorAllowAndName).
			WithDownloadPath(c.downloadDir).
			WithEventsEnabled(true),
	)
}

// handleDownloadWillBegin 记录下载元信息。
func (c *Collector) handleDownloadWillBegin(e *cdpbrowser.EventDownloadWillBegin) {
	c.tmu.Lock()
	c.downloadRecs = append(c.downloadRecs, downloadRec{
		guid:     e.GUID,
		url:      e.URL,
		filename: e.SuggestedFilename,
		time:     time.Now().Format("15:04:05"),
	})
	c.tmu.Unlock()
	c.log.Log("info", "检测到文件下载: %s (%s)", e.SuggestedFilename, shortURL(e.URL))
}

// downloadRec 一次下载的元信息。
type downloadRec struct {
	guid     string
	url      string
	filename string
	time     string
	filePath string // 完成后回填
}

// handleDownloadProgress 回填完成后的落盘路径。
func (c *Collector) handleDownloadProgress(e *cdpbrowser.EventDownloadProgress) {
	if e.State != cdpbrowser.DownloadProgressStateCompleted {
		return
	}
	c.tmu.Lock()
	for i := range c.downloadRecs {
		if c.downloadRecs[i].guid == e.GUID {
			c.downloadRecs[i].filePath = e.FilePath
		}
	}
	c.tmu.Unlock()
}

// downloadEntries 把完成的下载文件读出来变成 zip 条目 (打包阶段调用)。
func (c *Collector) downloadEntries(maxBytes int64) []entry {
	c.tmu.Lock()
	recs := make([]downloadRec, len(c.downloadRecs))
	copy(recs, c.downloadRecs)
	c.tmu.Unlock()
	var out []entry
	for _, r := range recs {
		if r.filePath == "" {
			continue
		}
		data, err := os.ReadFile(r.filePath)
		if err != nil {
			continue
		}
		if int64(len(data)) > maxBytes {
			data = data[:maxBytes]
		}
		name := r.filename
		if name == "" {
			name = filepath.Base(r.filePath)
		}
		name = sanitizeFileName(name)
		out = append(out, entry{
			path: "4-downloads/" + name,
			meta: fmt.Sprintf("浏览器下载文件 · %s · %s · %d 字节", r.time, r.url, len(data)),
			data: data,
		})
	}
	return out
}

// sanitizeFileName zip 路径安全化。
func sanitizeFileName(s string) string {
	var b strings.Builder
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') ||
			r == '.' || r == '_' || r == '-' {
			b.WriteRune(r)
		} else {
			b.WriteRune('_')
		}
	}
	out := b.String()
	if out == "" {
		out = "download.bin"
	}
	return out
}

// ---------------- 跨源 iframe (OOPIF) 附加 ----------------

// handleAutoAttach target.SetAutoAttach 后, OOPIF(跨源 iframe) 以独立 session
// 出现。在这里把 iframe 也挂上网络监听 — 强智/正方新版把课表塞在 iframe 里,
// 只挂顶层 page 等于瞎。
func (c *Collector) handleAutoAttach(browserCtx context.Context) func(ev interface{}) {
	return func(ev interface{}) {
		e, ok := ev.(*target.EventAttachedToTarget)
		if !ok {
			return
		}
		if e.TargetInfo.Type != "iframe" {
			return
		}
		c.tmu.Lock()
		if c.iframeAttached[e.TargetInfo.TargetID] {
			c.tmu.Unlock()
			return
		}
		c.iframeAttached[e.TargetInfo.TargetID] = true
		c.tabSeq++
		seq := c.tabSeq
		c.tmu.Unlock()
		iframeCtx, _ := chromedp.NewContext(browserCtx, chromedp.WithTargetID(e.TargetInfo.TargetID))
		iframeCtx, cancel := context.WithCancel(iframeCtx)
		c.tmu.Lock()
		c.tabs[seq] = &tabState{seq: seq, ctx: iframeCtx}
		c.tmu.Unlock()
		fmt.Printf("  (跨源 iframe 已附加监听: %s)\n", shortURL(e.TargetInfo.URL))
		_ = chromedp.Run(iframeCtx, network.Enable().WithMaxPostDataSize(8<<20), log.Enable(), runtime.Enable())
		chromedp.ListenTarget(iframeCtx, func(ev interface{}) { c.onEventTab(seq, ev) })
		go func() {
			<-browserCtx.Done()
			cancel()
		}()
	}
}

// setupAutoAttach 主 tab 侧开 autoAttach (flatten 模式)。
func setupAutoAttach(ctx context.Context) error {
	return chromedp.Run(ctx,
		target.SetAutoAttach(true, false).WithFlatten(true),
	)
}

// ---------------- HAR 导出 ----------------

// collectorRecs 按收录顺序取全部请求记录 (HAR/清单共用)。
func collectorRecs(c *Collector) []*reqRec {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	out := make([]*reqRec, 0, len(c.order))
	for _, k := range c.order {
		if r := c.recs[k]; r != nil {
			out = append(out, r)
		}
	}
	return out
}

// harEntryTime reqRec 没有 wallTime 存量 — HAR 要求绝对时间, 这里统一取打包时刻
// (采集期相对顺序已由 order 数组保证, 时间对 HAR 回放无关紧要)。
func harEntryTime() string {
	return time.Now().UTC().Format("2006-01-02T15:04:05.000Z")
}

// buildHAR 把捕获的请求序列转成 HAR 1.2 (DevTools/Charles 直接打开回放)。
// 返回 JSON 文本与 entry 数。
func buildHAR(recs []*reqRec, maxBodyBytes int) (string, int) {
	type harParam struct {
		Name  string `json:"name"`
		Value string `json:"value"`
	}
	type harPostData struct {
		MimeType string      `json:"mimeType,omitempty"`
		Params   []harParam  `json:"params,omitempty"`
		Text     string      `json:"text,omitempty"`
	}
	type harHeader struct {
		Name  string `json:"name"`
		Value string `json:"value"`
	}
	type harContent struct {
		Size        int    `json:"size"`
		MimeType    string `json:"mimeType"`
		Text        string `json:"text,omitempty"`
		Encoding    string `json:"encoding,omitempty"`
	}
	type harRequest struct {
		Method      string      `json:"method"`
		URL         string      `json:"url"`
		HTTPVersion string      `json:"httpVersion"`
		Headers     []harHeader `json:"headers"`
		QueryString []harParam  `json:"queryString"`
		PostData    *harPostData `json:"postData,omitempty"`
		HeadersSize int         `json:"headersSize"`
		BodySize    int         `json:"bodySize"`
	}
	type harResponse struct {
		Status      int         `json:"status"`
		StatusText  string      `json:"statusText"`
		HTTPVersion string      `json:"httpVersion"`
		Headers     []harHeader `json:"headers"`
		Content     harContent  `json:"content"`
		RedirectURL string      `json:"redirectURL"`
		HeadersSize int         `json:"headersSize"`
		BodySize    int         `json:"bodySize"`
	}
	type harEntry struct {
		Pageref         string     `json:"pageref"`
		StartedDateTime string     `json:"startedDateTime"`
		Time            float64    `json:"time"`
		Request         harRequest `json:"request"`
		Response        harResponse `json:"response"`
		Cache           struct{}   `json:"cache"`
		Timings         struct {
			Send    float64 `json:"send"`
			Wait    float64 `json:"wait"`
			Receive float64 `json:"receive"`
		} `json:"timings"`
	}
	type harLog struct {
		Version string `json:"version"`
		Creator struct {
			Name    string `json:"name"`
			Version string `json:"version"`
		} `json:"creator"`
		Entries []harEntry `json:"entries"`
	}
	type harDoc struct {
		Log harLog `json:"log"`
	}

	doc := harDoc{}
	doc.Log.Version = "1.2"
	doc.Log.Creator.Name = "sleepy-collector"
	doc.Log.Creator.Version = version

	for _, r := range recs {
		if r == nil || isLogout(r.url) {
			continue
		}
		if looksBinary(r.url) {
			continue
		}
		e := harEntry{
			Pageref:         "page_1",
			StartedDateTime: harEntryTime(),
			Time:            0,
		}
		req := harRequest{
			Method:      r.method,
			URL:         r.url,
			HTTPVersion: "HTTP/1.1",
			Headers:     []harHeader{},
			QueryString: []harParam{},
			HeadersSize: -1,
			BodySize:    len(r.postData),
		}
		if u, err := url.Parse(r.url); err == nil {
			for k, vs := range u.Query() {
				for _, v := range vs {
					req.QueryString = append(req.QueryString, harParam{k, v})
				}
			}
		}
		if r.postData != "" {
			req.PostData = &harPostData{
				MimeType: guessPostMimeType(r.postData),
				Text:     r.postData,
			}
		}
		e.Request = req

		bodyText := r.body
		bodyEnc := ""
		if len(bodyText) > maxBodyBytes {
			bodyText = bodyText[:maxBodyBytes]
			bodyEnc = "truncate-marker" // 非法值标记截断 (HAR 规范只认 base64; 截断不编码)
		}
		resp := harResponse{
			Status:      int(r.status),
			StatusText:  "",
			HTTPVersion: "HTTP/1.1",
			Headers:     []harHeader{},
			Content: harContent{
				Size:     len(r.body),
				MimeType: r.mimeType,
				Text:     bodyText,
				Encoding: bodyEnc,
			},
			HeadersSize: -1,
			BodySize:    len(r.body),
		}
		if r.respHeaders != "" {
			for _, line := range strings.Split(r.respHeaders, "\n") {
				if i := strings.Index(line, ": "); i > 0 {
					resp.Headers = append(resp.Headers, harHeader{line[:i], line[i+2:]})
				}
			}
		}
		e.Response = resp
		e.Timings.Send = 0
		e.Timings.Wait = 0
		e.Timings.Receive = 0
		doc.Log.Entries = append(doc.Log.Entries, e)
	}
	b, _ := json.MarshalIndent(doc, "", "  ")
	return string(b), len(doc.Log.Entries)
}

// guessPostMimeType 粗判 POST 体形态 (HAR postData.mimeType)。
func guessPostMimeType(body string) string {
	t := strings.TrimSpace(body)
	if strings.HasPrefix(t, "{") || strings.HasPrefix(t, "[") {
		return "application/json"
	}
	if looksBase64Body(t) {
		return "application/x-www-form-urlencoded" // WHUT 型 base64 表单
	}
	if strings.Contains(t, "=") && strings.Contains(t, "&") {
		return "application/x-www-form-urlencoded"
	}
	return "text/plain"
}

// ---------------- 大 POST 体兜底 ----------------

// fetchMissingPostData 打包阶段对 postDataMissing 的请求调 GetRequestPostData
// (必须在非事件回调 goroutine; 发回原 tab session)。
func (c *Collector) fetchMissingPostData(tabSeq int, k reqKey, r *reqRec) {
	c.tmu.Lock()
	ts := c.tabs[tabSeq]
	c.tmu.Unlock()
	if ts == nil {
		return
	}
	var postData []byte
	err := chromedp.Run(ts.ctx, chromedp.ActionFunc(func(ictx context.Context) error {
		b, err := network.GetRequestPostData(k.rid).Do(ictx)
		if err != nil {
			return err
		}
		postData = b
		return nil
	}))
	if err == nil && len(postData) > 0 {
		c.tmu.Lock()
		r.postData = string(postData)
		r.postDataMissing = false
		c.tmu.Unlock()
	}
}

// ---------------- Cookie 采集(名/域/过期) ----------------

// collectAllCookies 打包阶段把全量 cookie (跨 tab 同 profile 共享) 写成文本。
func (c *Collector) collectAllCookies(ctx context.Context) string {
	return cookieFullText(ctx)
}
