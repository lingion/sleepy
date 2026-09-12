package main

// G13 修复回归: 传输层 base64 解码 + HAR 大体量截断。
// 均由 E2E 靶场实跑炸出 (2026-09-12)。

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/chromedp/cdproto/network"
)

// TestPostDataEntryTransportBase64Decoded CDP 的 PostDataEntry.bytes 是
// binary 类型 (JSON 传输层 base64)。采集入库必须解码 — 否则包里所有
// POST 体都是 base64 天书, 适配者看到的是传输编码不是业务编码。
// (E2E 实锤: 页面发明文 "a=1&b=2", 包里存 "YT0xJmI9Mg==")
func TestPostDataEntryTransportBase64Decoded(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "p1",
		Request: &network.Request{
			URL:         "http://x/api",
			Method:      "POST",
			HasPostData: true,
			PostDataEntries: []*network.PostDataEntry{
				{Bytes: "YT0xJmI9Mg=="}, // base64("a=1&b=2") — 传输编码
			},
		},
	})
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

// TestPostDataEntryPlainTextPassthrough 防过修: 非 base64 形态的 bytes
// (理论不该出现, 但防御) 原样保留, 不能瞎解。
func TestPostDataEntryPlainTextPassthrough(t *testing.T) {
	c := NewCollector()
	c.onEventTab(0, &network.EventRequestWillBeSent{
		RequestID: "p2",
		Request: &network.Request{
			URL:         "http://x/api",
			Method:      "POST",
			HasPostData: true,
			PostDataEntries: []*network.PostDataEntry{
				{Bytes: "a=1"}, // 不是合法 base64 (长度 3 非 4 倍数)
			},
		},
	})
	r := c.recs[reqKey{0, "p2"}]
	if r.postData != "a=1" {
		t.Fatalf("非 base64 体应原样: got %q", r.postData)
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
