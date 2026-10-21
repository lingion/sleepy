package main

// G13 修复回归: 传输层 base64 解码 + HAR 大体量截断。
// 均由 E2E 靶场实跑炸出 (2026-09-12)。

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"

	"github.com/chromedp/cdproto/network"
)

// TestPostDataEntryTransportBase64Decoded CDP 的 PostDataEntry.bytes 是
// binary 类型 (JSON 传输层 base64)。cdproto v0.158 起解码器自动解掉 —
// 采集入库拿到的必须已是原始字节, 否则包里所有 POST 体都是 base64 天书,
// 适配者看到的是传输编码不是业务编码。
// (E2E 实锤: 页面发明文 "a=1&b=2", 传输层 JSON 里是 "YT0xJmI9Mg==")
func TestPostDataEntryTransportBase64Decoded(t *testing.T) {
	var e network.EventRequestWillBeSent
	raw := `{
		"requestId": "p1",
		"request": {
			"url": "http://x/api", "method": "POST", "hasPostData": true,
			"postDataEntries": [{"bytes": "YT0xJmI9Mg=="}]
		}
	}`
	if err := json.Unmarshal([]byte(raw), &e); err != nil {
		t.Fatalf("解 CDP 事件 JSON: %v", err)
	}
	if string(e.Request.PostDataEntries[0].Bytes) != "a=1&b=2" {
		t.Fatalf("cdproto 传输层解码失效: got %q", e.Request.PostDataEntries[0].Bytes)
	}
	c := NewCollector()
	c.onEventTab(0, &e)
	r := c.recs[reqKey{0, "p1"}]
	if r == nil {
		t.Fatal("记录缺失")
	}
	if r.postData != "a=1&b=2" {
		t.Fatalf("POST 体未解传输层 base64: got %q want %q", r.postData, "a=1&b=2")
	}
	if r.postDataMissing {
		t.Fatal("解到体就不该再标 missing")
	}
}

// TestPostDataEntryBusinessBase64NotDoubleDecoded 防过修: 校方业务层自己
// base64 (WHUT 型) 时, 传输解码只剥一层 — 包里的业务 base64 必须原样保留,
// 采集代码绝不能再解一次 (两层编码互不干扰)。
func TestPostDataEntryBusinessBase64NotDoubleDecoded(t *testing.T) {
	// 业务内容 "YT0xJmI9Mg==" (本身是 base64), 传输层再包一层 base64
	business := "YT0xJmI9Mg=="
	wire := base64.StdEncoding.EncodeToString([]byte(business))
	var e network.EventRequestWillBeSent
	raw := `{
		"requestId": "p2",
		"request": {
			"url": "http://x/api", "method": "POST", "hasPostData": true,
			"postDataEntries": [{"bytes": "` + wire + `"}]
		}
	}`
	if err := json.Unmarshal([]byte(raw), &e); err != nil {
		t.Fatalf("解 CDP 事件 JSON: %v", err)
	}
	c := NewCollector()
	c.onEventTab(0, &e)
	r := c.recs[reqKey{0, "p2"}]
	if r.postData != business {
		t.Fatalf("业务级 base64 被二次解码: got %q want %q", r.postData, business)
	}
}

// TestHARStaysValidJSONWithHugeBodies HAR 里 3MB postData 截断后必须仍是
// 合法 JSON (E2E 实锤: 单文件配额把 HAR 拦腰截断 → DevTools 打不开)。
func TestHARStaysValidJSONWithHugeBodies(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "big",
		Request:   &network.Request{URL: "http://x/api/echo", Method: "POST"},
	})
	r := c.recs[reqKey{0, "big"}]
	r.postData = "big=" + strings.Repeat("x", 3<<20) // 3MB
	r.status = 200
	r.mimeType = "application/json"
	r.body = `{"recv":1}`
	r.done = true

	har, n := buildHAR(collectorRecs(c), 64<<10) // 小上限故意触发截断
	if n != 1 {
		t.Fatalf("entries = %d", n)
	}
	var probe map[string]interface{}
	if err := json.Unmarshal([]byte(har), &probe); err != nil {
		t.Fatalf("截断后的 HAR 不是合法 JSON: %v", err)
	}
	if len(har) > (64<<10)*2+4096 {
		t.Fatalf("HAR 超预算太多: %d 字符", len(har))
	}
}
