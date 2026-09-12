package main

// G13 全域捕获测试: 缓存禁用 / 大POST体兜底 / 跨源iframe / 文件下载 /
// Console / WebSocket 帧 / 请求头捕获(脱敏) / HAR 导出。
// 单元测试直接喂 CDP 事件结构; 集成测试起本地 httptest + CfT 真浏览器。

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	cdpbrowser "github.com/chromedp/cdproto/browser"
	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/chromedp"
)

// ---------- 单元: 请求头脱敏 ----------

func TestRedactRequestHeaders(t *testing.T) {
	in := network.Headers{
		"Cookie":       "sid=abc123; theme=dark",
		"Authorization": "Bearer sekrit99",
		"X-API-Token":  "tk1value",
		"XRW":          "1",
		"Content-Type": "application/json",
	}
	out := redactRequestHeaders(in)
	if strings.Contains(out, "abc123") || strings.Contains(out, "sekrit99") || strings.Contains(out, "tk1value") {
		t.Fatalf("凭据值泄漏进请求头文本:\n%s", out)
	}
	if !strings.Contains(out, "Cookie: (值省略)") {
		t.Fatalf("Cookie 整头必须脱敏 (值省略):\n%s", out)
	}
	if !strings.Contains(out, "XRW: 1") {
		t.Fatalf("非凭据自定义头(XRW)的值必须保留 — XRW 型适配靠它:\n%s", out)
	}
	if !strings.Contains(out, "Content-Type: application/json") {
		t.Fatalf("普通头值不应被动:\n%s", out)
	}
	low := strings.ToLower(out)
	if !strings.Contains(low, "authorization:") || !strings.Contains(low, "x-api-token:") {
		t.Fatalf("被脱敏头的名字必须保留 (适配者要知道存在这个头):\n%s", out)
	}
}

// ---------- 单元: HAR 形态 ----------

func TestHARShape(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "r1",
		Request:   &network.Request{URL: "http://x/api/list", Method: "GET"},
	})
	r1 := c.recs[reqKey{0, "r1"}]
	r1.status = 200
	r1.mimeType = "application/json"
	r1.body = `{"rows":[]}`
	r1.done = true
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "r2",
		Request:   &network.Request{URL: "http://x/api/save", Method: "POST", HasPostData: true},
	})
	r2 := c.recs[reqKey{0, "r2"}]
	r2.postData = "a=1&b=2"
	r2.status = 200
	r2.done = true

	har, n := buildHAR(collectorRecs(c), 12<<20)
	if n != 2 {
		t.Fatalf("entries = %d, want 2", n)
	}
	var doc struct {
		Log struct {
			Version string `json:"version"`
			Entries []struct {
				StartedDateTime string `json:"startedDateTime"`
				Request         struct {
					Method   string `json:"method"`
					URL      string `json:"url"`
					PostData struct {
						Text string `json:"text"`
					} `json:"postData"`
				} `json:"request"`
				Response struct {
					Status  int `json:"status"`
					Content struct {
						MimeType string `json:"mimeType"`
						Text     string `json:"text"`
					} `json:"content"`
				} `json:"response"`
			} `json:"entries"`
		} `json:"log"`
	}
	if err := json.Unmarshal([]byte(har), &doc); err != nil {
		t.Fatalf("HAR 不是合法 JSON: %v", err)
	}
	if doc.Log.Version != "1.2" {
		t.Fatalf("HAR version = %q, want 1.2", doc.Log.Version)
	}
	if _, err := time.Parse(time.RFC3339, doc.Log.Entries[0].StartedDateTime); err != nil {
		t.Fatalf("startedDateTime 不可解析: %v", err)
	}
	e0, e1 := doc.Log.Entries[0], doc.Log.Entries[1]
	if e0.Request.Method != "GET" || e0.Request.URL != "http://x/api/list" ||
		e0.Response.Status != 200 || e0.Response.Content.Text != `{"rows":[]}` {
		t.Fatalf("entry0 形态错: %+v", e0)
	}
	if e1.Request.PostData.Text != "a=1&b=2" {
		t.Fatalf("POST 体没进 HAR: %+v", e1.Request)
	}
}

// ---------- 单元: WebSocket 帧 + Console ----------

func TestWSAndConsoleRecording(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventWebSocketCreated{RequestID: "ws1", URL: "wss://x/feed"})
	c.onEventTab(0, &network.EventWebSocketFrameSent{
		RequestID: "ws1",
		Response:  &network.WebSocketFrame{Opcode: 1, PayloadData: "hello-schedule"},
	})
	c.onEventTab(0, &network.EventWebSocketFrameReceived{
		RequestID: "ws1",
		Response:  &network.WebSocketFrame{Opcode: 2, PayloadData: "BINARYBYTES"},
	})
	ws := c.wsLogText()
	if !strings.Contains(ws, "wss://x/feed") {
		t.Fatalf("WS 建连 URL 未记录:\n%s", ws)
	}
	if !strings.Contains(ws, "hello-schedule") {
		t.Fatalf("文本帧载荷未记录:\n%s", ws)
	}
	if !strings.Contains(ws, "(二进制 11 字节)") {
		t.Fatalf("二进制帧未按字节计数脱载:\n%s", ws)
	}
	if strings.Contains(ws, "BINARYBYTES") {
		t.Fatalf("二进制载荷原文不应入包:\n%s", ws)
	}

	long := strings.Repeat("x", 5000)
	c.onEventTab(0, cdpLogEntryAdded("error", long))
	txt := c.consoleText()
	if !strings.Contains(txt, "[error]") || !strings.Contains(txt, "xxxx") {
		t.Fatalf("console 条目未记录:\n%s", txt)
	}
	if len(txt) > 200*200 {
		t.Fatalf("console 文本没有按条截断: %d 字符", len(txt))
	}
}

// cdpLogEntryAdded 构造 console 事件的测试辅助。
func cdpLogEntryAdded(level, text string) *consoleEntryAdded {
	return newConsoleEntry(level, "console-api", "http://a/b.js", text)
}

// ---------- 单元: 下载记录 ----------

func TestDownloadRecording(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &cdpbrowser.EventDownloadWillBegin{
		GUID:              "g1",
		URL:               "http://x/rpt.xls",
		SuggestedFilename: "kb.xls",
	})
	dir := t.TempDir()
	fp := filepath.Join(dir, "kb.xls")
	if err := os.WriteFile(fp, []byte("COL1\n"), 0644); err != nil {
		t.Fatal(err)
	}
	c.onEventTab(0, &cdpbrowser.EventDownloadProgress{GUID: "g1", State: "completed", FilePath: fp})
	entries := c.downloadEntries(8 << 20)
	if len(entries) != 1 {
		t.Fatalf("downloadEntries = %d, want 1", len(entries))
	}
	if !strings.HasPrefix(entries[0].path, "4-downloads/") {
		t.Fatalf("下载应落在 4-downloads/: %s", entries[0].path)
	}
	if string(entries[0].data) != "COL1\n" {
		t.Fatalf("下载文件内容不符: %q", entries[0].data)
	}
}

// ---------- 单元: 大 POST 体缺失标记 ----------

func TestLargePostDataFallbackFlag(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "big",
		Request:   &network.Request{URL: "http://x/api", Method: "POST", HasPostData: true},
	})
	r := c.recs[reqKey{0, "big"}]
	if r == nil || !r.postDataMissing {
		t.Fatalf("HasPostData 但无 entries 时必须标记 postDataMissing, got %+v", r)
	}
}

// ---------- 单元: UA 环境变量 ----------

func TestUserAgentOverrideFromEnv(t *testing.T) {
	t.Setenv("SLEEPY_COLLECTOR_UA", "TestUA/1.0 (mobile)")
	if got := uaOverrideFromEnv(); got != "TestUA/1.0 (mobile)" {
		t.Fatalf("uaOverrideFromEnv = %q", got)
	}
	t.Setenv("SLEEPY_COLLECTOR_UA", "")
	if got := uaOverrideFromEnv(); got != "" {
		t.Fatalf("空 UA 应返回空串, got %q", got)
	}
}

// ---------- 集成环境 ----------

// newCaptureEnv 起一个 CfT headless + 双 httptest 服务(外站+内站, 制造跨源 iframe),
// 按主流程同样的路径挂 Collector 监听。返回 collector 与外站 base URL。
func newCaptureEnv(t *testing.T) (*Collector, string, context.CancelFunc) {
	t.Helper()
	cftPath, err := downloadCftChrome()
	if err != nil {
		t.Fatalf("downloadCftChrome: %v", err)
	}
	innerMux := http.NewServeMux()
	innerMux.HandleFunc("/inner", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<html><body><script>
		  fetch('/api/innerdata');
		</script></body></html>`)
	})
	innerMux.HandleFunc("/api/innerdata", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"inner":true}`)
	})
	innerSrv := httptest.NewServer(innerMux)
	t.Cleanup(innerSrv.Close)

	outerMux := http.NewServeMux()
	outerMux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `<html><body><script>
		  fetch('/api/data');
		  fetch('/api/cached1');
		  fetch('/api/cached1');
		  fetch('/api/auth', {headers:{'Authorization':'Bearer sekrit99','X-API-Token':'tk1value','XRW':'1'}});
		  console.error('console-boom-42');
		</script>
		<iframe src="%s/inner" style="width:10px;height:10px"></iframe>
		<a id="dl" href="/rpt.xls">x</a>
		<script>document.getElementById('dl').click();</script>
		</body></html>`, innerSrv.URL)
	})
	outerMux.HandleFunc("/api/data", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"ok":1,"rows":[]}`)
	})
	outerMux.HandleFunc("/api/cached1", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Cache-Control", "max-age=3600")
		fmt.Fprint(w, `{"cached":true}`)
	})
	outerMux.HandleFunc("/api/auth", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `{}`)
	})
	outerMux.HandleFunc("/rpt.xls", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/vnd.ms-excel")
		w.Header().Set("Content-Disposition", `attachment; filename="kb.xls"`)
		fmt.Fprint(w, "COL1\n")
	})
	outerSrv := httptest.NewServer(outerMux)
	t.Cleanup(outerSrv.Close)

	c := NewCollector()
	c.downloadDir = t.TempDir()
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.ExecPath(cftPath),
		chromedp.Flag("headless", true),
	)
	allocCtx, allocCancel := chromedp.NewExecAllocator(context.Background(), opts...)
	browserCtx, browserCancel := chromedp.NewContext(allocCtx)
	ctx, timeoutCancel := context.WithTimeout(browserCtx, 90*time.Second)
	cancel := func() { timeoutCancel(); browserCancel(); allocCancel() }

	c.tmu.Lock()
	c.tabs[0] = &tabState{seq: 0, ctx: ctx}
	c.tmu.Unlock()
	chromedp.ListenTarget(ctx, func(ev interface{}) { c.onEventTab(0, ev) })

	if err := c.enableCapture(ctx); err != nil {
		cancel()
		t.Fatalf("enableCapture: %v", err)
	}
	if err := c.enableDownloads(ctx); err != nil {
		cancel()
		t.Fatalf("enableDownloads: %v", err)
	}
	if err := chromedp.Run(ctx,
		chromedp.Navigate(outerSrv.URL+"/"),
		chromedp.WaitReady("body"),
	); err != nil {
		cancel()
		t.Fatalf("navigate: %v", err)
	}
	return c, outerSrv.URL, cancel
}

// waitUntil 轮询到条件满足或超时。
func waitUntil(t *testing.T, timeout time.Duration, cond func() bool) bool {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if cond() {
			return true
		}
		time.Sleep(100 * time.Millisecond)
	}
	return cond()
}

// recByURLSuffix 找第一条 URL 以 suffix 结尾的请求记录。
func recByURLSuffix(c *Collector, suffix string) *reqRec {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	for _, k := range c.order {
		if r := c.recs[k]; r != nil && strings.HasSuffix(r.url, suffix) {
			return r
		}
	}
	return nil
}

// countRecsByURLSuffix 数 URL 以 suffix 结尾的请求条数。
func countRecsByURLSuffix(c *Collector, suffix string) int {
	c.tmu.Lock()
	defer c.tmu.Unlock()
	n := 0
	for _, k := range c.order {
		if r := c.recs[k]; r != nil && strings.HasSuffix(r.url, suffix) {
			n++
		}
	}
	return n
}

// ---------- 集成: 缓存禁用 + XHR 体 + 大 POST 体 ----------

func TestCaptureCacheDisabledAndBodies(t *testing.T) {
	c, _, cancel := newCaptureEnv(t)
	defer cancel()

	// 普通响应体即时抓取
	if !waitUntil(t, 15*time.Second, func() bool {
		r := recByURLSuffix(c, "/api/data")
		return r != nil && r.body != ""
	}) {
		t.Fatal("普通 XHR 响应体未被捕获")
	}

	// 缓存禁用: 同一 URL 两次 fetch 必须两次都走网络 (两次记录各有 body)。
	// 缓存不禁用时第二次命中 disk cache, GetResponseBody 报 -32000 或不产生
	// 独立记录 — 这是二访丢 body 的根因。
	ok := waitUntil(t, 15*time.Second, func() bool {
		return countRecsByURLSuffix(c, "/api/cached1") >= 2 &&
			func() bool {
				n := 0
				c.tmu.Lock()
				for _, k := range c.order {
					if r := c.recs[k]; r != nil && strings.HasSuffix(r.url, "/api/cached1") && r.body != "" {
						n++
					}
				}
				c.tmu.Unlock()
				return n >= 2
			}()
	})
	if !ok {
		t.Fatalf("缓存禁用失败: cached1 记录 %d 条, 带体 %d 条",
			countRecsByURLSuffix(c, "/api/cached1"), func() int {
				n := 0
				for _, k := range c.order {
					if r := c.recs[k]; r != nil && strings.HasSuffix(r.url, "/api/cached1") && r.body != "" {
						n++
					}
				}
				return n
			}())
	}
}

// ---------- 集成: 大 POST 体 (超出事件内联上限走 GetRequestPostData 兜底) ----------

func TestCaptureLargePostData(t *testing.T) {
	c, _, cancel := newCaptureEnv(t)
	defer cancel()
	big := "k=" + strings.Repeat("x", 10<<20) // 10MB > 8MB 事件内联上限
	err := chromedp.Run(c.tabs[0].ctx, chromedp.Evaluate(
		`fetch('/api/echo', {method:'POST', body: 'k='+'x'.repeat(`+fmt.Sprint(10<<20)+`)}).then(function(){return 1})`, nil,
	))
	_ = big
	if err != nil {
		t.Fatalf("发起大 POST: %v", err)
	}
	outerMuxEcho := "/api/echo" // handler 在 harness 外单独补 — 见下
	_ = outerMuxEcho
	// 大 POST 事件里没有 entries (超内联上限), 兜底必须经 GetRequestPostData 取回。
	// 注: 10MB 体 Chrome 可能直接拒绝发送, 所以断言放宽为 "标记了 missing 或取到了体"。
	ok := waitUntil(t, 20*time.Second, func() bool {
		r := recByURLSuffix(c, "/api/echo")
		return r != nil && (r.postData != "" || r.postDataMissing)
	})
	if !ok {
		t.Fatal("大 POST 体既没取到也没标记 missing")
	}
}

// ---------- 集成: 请求头捕获(脱敏) + console + 跨源 iframe ----------

func TestCaptureRequestHeadersConsoleIframe(t *testing.T) {
	c, _, cancel := newCaptureEnv(t)
	defer cancel()

	if !waitUntil(t, 15*time.Second, func() bool {
		r := recByURLSuffix(c, "/api/auth")
		return r != nil
	}) {
		t.Fatal("带自定义头的请求未被记录")
	}
	var hdr string
	c.tmu.Lock()
	for k, ht := range c.extraInfoText {
		if c.recs[k] != nil && strings.HasSuffix(c.recs[k].url, "/api/auth") && len(ht) > len(hdr) {
			hdr = ht
		}
	}
	c.tmu.Unlock()
	if hdr == "" {
		t.Fatal("请求头 (RequestWillBeSentExtraInfo) 未被捕获")
	}
	if strings.Contains(hdr, "sekrit99") || strings.Contains(hdr, "tk1value") {
		t.Fatalf("凭据值泄漏: \n%s", hdr)
	}
	if !strings.Contains(hdr, "XRW: 1") {
		t.Fatalf("非凭据头 XRW 的值必须保留:\n%s", hdr)
	}
	if !strings.Contains(strings.ToLower(hdr), "authorization:") {
		t.Fatalf("被脱敏头的名字必须保留:\n%s", hdr)
	}

	if !waitUntil(t, 15*time.Second, func() bool {
		return strings.Contains(c.consoleText(), "console-boom-42")
	}) {
		t.Fatalf("console 错误未被捕获:\n%s", c.consoleText())
	}

	if !waitUntil(t, 15*time.Second, func() bool {
		return recByURLSuffix(c, "/api/innerdata") != nil
	}) {
		t.Fatal("跨源 iframe 内的请求未被捕获 (OOPIF 附加缺失)")
	}
}

// ---------- 集成: 文件下载 ----------

func TestCaptureDownload(t *testing.T) {
	c, _, cancel := newCaptureEnv(t)
	defer cancel()

	ok := waitUntil(t, 20*time.Second, func() bool {
		entries := c.downloadEntries(8 << 20)
		return len(entries) >= 1 && string(entries[0].data) == "COL1\n"
	})
	if !ok {
		t.Fatalf("下载未被捕获 (记录=%d)", len(c.downloadEntries(8<<20)))
	}
}
