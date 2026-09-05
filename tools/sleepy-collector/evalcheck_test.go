package main

import (
	"context"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

// Prove evalAsync actually awaits a promise and returns the JSON string.
func TestEvalAsyncAwaits(t *testing.T) {
	ctx, cancel := chromedp.NewExecAllocator(context.Background(),
		append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true))...)
	defer cancel()
	cctx, ccancel := chromedp.NewContext(ctx)
	defer ccancel()
	tctx, tcancel := context.WithTimeout(cctx, 30*time.Second)
	defer tcancel()
	err := chromedp.Run(tctx, chromedp.Navigate("about:blank"))
	if err != nil {
		t.Fatalf("nav: %v", err)
	}
	var got string
	err = chromedp.Run(tctx, chromedp.ActionFunc(func(ictx context.Context) error {
		return evalAsync(ictx, `(async function(){ await new Promise(r=>setTimeout(r,300)); return JSON.stringify({s:200, ct:'x', b:'hello-world'}); })()`, &got)
	}))
	if err != nil {
		t.Fatalf("evalAsync: %v", err)
	}
	if got != `{"s":200,"ct":"x","b":"hello-world"}` {
		t.Fatalf("got %q", got)
	}
}
