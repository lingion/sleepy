package main

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

// retryable: ctx 取消/超时不重试;其余错误可重试
func TestRetryable(t *testing.T) {
	if retryable(nil) {
		t.Fatal("nil error 不该重试")
	}
	if retryable(context.Canceled) || retryable(context.DeadlineExceeded) {
		t.Fatal("ctx 取消/超时不该重试")
	}
	if !retryable(errors.New("timeout awaiting headers")) {
		t.Fatal("网络错误应可重试")
	}
	if retryable(errors.New("wrapped: context canceled")) {
		t.Fatal("包裹的 ctx 取消也不该重试")
	}
}

// evalAsyncRetry: 表达式非法时返回错误但退避总时长有界 (attempts=3 → 0.5+1.0s)
func TestEvalAsyncRetryBoundsTime(t *testing.T) {
	if execCtx() == nil {
		t.Skip("chromedp 不可用")
	}
	ctx, cancel := context.WithTimeout(execCtx(), 5*time.Second)
	defer cancel()
	start := time.Now()
	_, err := evalAsyncRetry(ctx, `throw new Error("x")`, 3)
	if err == nil {
		t.Fatal("非法表达式应返回错误")
	}
	if d := time.Since(start); d > 4*time.Second {
		t.Fatalf("退避总时长超出上界: %v", d)
	}
}

// evalAsyncRetry: attempts=1 等价 evalAsync,不额外耗时
func TestEvalAsyncRetrySingleAttempt(t *testing.T) {
	if execCtx() == nil {
		t.Skip("chromedp 不可用")
	}
	start := time.Now()
	out, err := evalAsyncRetry(execCtx(), `"1"`, 1)
	if d := time.Since(start); d > 500*time.Millisecond {
		t.Fatalf("单次尝试不应有退避: %v", d)
	}
	if err != nil {
		t.Fatalf("evalAsyncRetry: %v", err)
	}
	if out != "1" {
		t.Fatalf("期望 \"1\", got %q", out)
	}
}

// evalAsyncRetry 的重试不吞 ctx 取消
func TestEvalAsyncRetryRespectsCancel(t *testing.T) {
	if execCtx() == nil {
		t.Skip("chromedp 不可用")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := evalAsyncRetry(ctx, `1`, 3)
	if err == nil || !strings.Contains(err.Error(), "context") {
		t.Fatalf("取消后应立即返回 ctx 错误, got %v", err)
	}
}

// execCtx 惰性创建 headless 浏览器 ctx (与 TestEvalAsyncAwaits 同款;无浏览器环境跳过)
var execCtxCache context.Context

func execCtx() context.Context {
	if execCtxCache == nil {
		actx, acancel := chromedp.NewExecAllocator(context.Background(),
			append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true), chromedp.Flag("no-sandbox", true), chromedp.Flag("disable-dev-shm-usage", true))...)
		_ = acancel
		cctx, ccancel := chromedp.NewContext(actx)
		_ = ccancel
		tctx, tcancel := context.WithTimeout(cctx, 60*time.Second)
		_ = tcancel
		if err := chromedp.Do(tctx, chromedp.Navigate("about:blank")); err != nil {
			return nil
		}
		execCtxCache = tctx
	}
	return execCtxCache
}
