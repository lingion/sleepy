package main

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/chromedp/chromedp"
)

func TestStorageCapture(t *testing.T) {
	ctx, cancel := chromedp.NewExecAllocator(context.Background(),
		append(chromedp.DefaultExecAllocatorOptions[:], chromedp.Flag("headless", true))...)
	defer cancel()
	cctx, ccancel := chromedp.NewContext(ctx)
	defer ccancel()
	tctx, tcancel := context.WithTimeout(cctx, 30*time.Second)
	defer tcancel()
	err := chromedp.Run(tctx, chromedp.ActionFunc(func(ictx context.Context) error {
		if err := chromedp.Navigate("http://httpbin.org/html").Do(ictx); err != nil {
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
