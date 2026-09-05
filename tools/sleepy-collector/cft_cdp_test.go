package main

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

// chromedp 必须能驱动 CfT 内核(CDP 握手 + 页面加载 + JS 求值),
// 这是"没装 Chrome 也能用"的最后一环。
func TestChromedpDrivesCfT(t *testing.T) {
	cftPath, err := downloadCftChrome()
	if err != nil {
		t.Fatalf("downloadCftChrome: %v", err)
	}
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.ExecPath(cftPath),
		chromedp.Flag("headless", true),
	)
	allocCtx, cancel := chromedp.NewExecAllocator(context.Background(), opts...)
	defer cancel()
	ctx, cancel2 := chromedp.NewContext(allocCtx)
	defer cancel2()
	ctx3, cancel3 := context.WithTimeout(ctx, 60*time.Second)
	defer cancel3()

	var title, ua string
	if err := chromedp.Run(ctx3,
		chromedp.Navigate("https://www.example.com"),
		chromedp.WaitReady("h1"),
		chromedp.Title(&title),
		chromedp.Evaluate(`navigator.userAgent`, &ua),
	); err != nil {
		t.Fatalf("chromedp+CuT: %v", err)
	}
	if title == "" || !contains(ua, "Chrome") {
		t.Fatalf("unexpected: title=%q ua=%q", title, ua)
	}
	t.Logf("CDP via CfT OK: title=%q", title)
	_ = os.Getenv
}

func contains(s, sub string) bool {
	return len(s) >= len(sub) && (s == sub || len(sub) == 0 || indexOf(s, sub) >= 0)
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}
