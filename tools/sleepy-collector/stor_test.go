package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

// TestStorageCapture 用本地 httptest server 替代外网 httpbin.org —
// 原实现依赖 httpbin.org/html, 网络抖动/被拦时 ERR_BLOCKED_BY_CLIENT
// 假红 (2026-09-11 实锤); 测试目标是 jsStorage 序列化, 本地页即可。
func TestStorageCapture(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = w.Write([]byte("<html><body>ok</body></html>"))
	}))
	defer srv.Close()

	ctx, cancel := chromedp.NewExecAllocator(context.Background(),
		append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true))...)
	defer cancel()
	cctx, ccancel := chromedp.NewContext(ctx)
	defer ccancel()
	tctx, tcancel := context.WithTimeout(cctx, 30*time.Second)
	defer tcancel()
	err := chromedp.Run(tctx, chromedp.ActionFunc(func(ictx context.Context) error {
		if err := chromedp.Navigate(srv.URL).Do(ictx); err != nil {
			return err
		}
		if err := chromedp.Evaluate(`localStorage.setItem('probe','中文值'); 1`, nil).Do(ictx); err != nil {
			return err
		}
		var storJSON string
		if err := chromedp.Evaluate(jsStorage, &storJSON).Do(ictx); err != nil {
			return err
		}
		var stor map[string]map[string]string
		if err := json.Unmarshal([]byte(storJSON), &stor); err != nil {
			return err
		}
		t.Logf("storage: %v", stor)
		if stor["localStorage"]["probe"] != "中文值" {
			t.Fatalf("probe missing: %q", storJSON)
		}
		return nil
	}))
	if err != nil {
		t.Fatalf("run: %v", err)
	}
}

// TestSelectsCapture 验证 G2 学期/周次下拉枚举: option 的 value/text 全量序列化。
func TestSelectsCapture(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = w.Write([]byte(`<html><body>
			<select name="xnxqdm" id="term">
				<option value="2025-2026-2">2025-2026学年第二学期</option>
				<option value="2026-2027-1" selected>2026-2027学年第一学期</option>
			</select>
		</body></html>`))
	}))
	defer srv.Close()

	ctx, cancel := chromedp.NewExecAllocator(context.Background(),
		append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true))...)
	defer cancel()
	cctx, ccancel := chromedp.NewContext(ctx)
	defer ccancel()
	tctx, tcancel := context.WithTimeout(cctx, 30*time.Second)
	defer tcancel()
	var selJSON string
	if err := chromedp.Run(tctx,
		chromedp.Navigate(srv.URL),
		chromedp.Evaluate(jsSelects, &selJSON),
	); err != nil {
		t.Fatalf("run: %v", err)
	}
	var sels []map[string]interface{}
	if err := json.Unmarshal([]byte(selJSON), &sels); err != nil {
		t.Fatalf("unmarshal: %v (%q)", err, selJSON)
	}
	if len(sels) != 1 {
		t.Fatalf("expected 1 select, got %d (%q)", len(sels), selJSON)
	}
	raw, _ := json.Marshal(sels[0])
	joined := string(raw)
	if !strings.Contains(joined, "2026-2027-1") || !strings.Contains(joined, "xnxqdm") {
		t.Fatalf("select options/name missing: %s", joined)
	}
}
