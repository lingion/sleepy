package main

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

func requireBrowserTests(t *testing.T) {
	t.Helper()
	if os.Getenv("SLEEPY_CFT_E2E") != "1" {
		t.Skip("set SLEEPY_CFT_E2E=1 to run browser integration tests (may download ~180MB)")
	}
}

func newBrowserTestContext(t *testing.T) context.Context {
	t.Helper()
	requireBrowserTests(t)
	allocCtx, allocCancel := chromedp.NewExecAllocator(context.Background(),
		append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true), chromedp.Flag("no-sandbox", true), chromedp.Flag("disable-dev-shm-usage", true))...)
	browserCtx, browserCancel := chromedp.NewContext(allocCtx)
	ctx, timeoutCancel := context.WithTimeout(browserCtx, 60*time.Second)
	t.Cleanup(func() { timeoutCancel(); browserCancel(); allocCancel() })
	return ctx
}

// 验证"没装 Chrome 也能用":直接调自动下载,绕过本机已有浏览器
func TestDownloadCftChrome(t *testing.T) {
	requireBrowserTests(t)
	p, err := downloadCftChrome()
	if err != nil {
		t.Fatalf("downloadCftChrome: %v", err)
	}
	fi, err := os.Stat(p)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	// mac CfT 主程序是薄启动器(26KB,实体在 Frameworks),只验证能启动
	// 能启动 = 真能用
	out, err := exec.Command(p, "--version").CombinedOutput()
	if err != nil {
		t.Fatalf("launch: %v\n%s", err, out)
	}
	t.Logf("CfT OK: %s (%d MB) -> %s", filepath.Base(p), fi.Size()>>20, string(out))
}

func TestCftPlatform(t *testing.T) {
	plat, exe, err := cftPlatform()
	if err != nil {
		t.Fatalf("cftPlatform: %v", err)
	}
	if plat == "" || exe == "" {
		t.Fatal("empty platform/exec")
	}
	t.Logf("platform=%s exe=%s", plat, exe)
}
