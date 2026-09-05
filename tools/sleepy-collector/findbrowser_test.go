package main

import (
	"path/filepath"
	"testing"
)

// findBrowser 全链路:本地无浏览器时必须落到 CfT 自动下载缓存。
func TestFindBrowserCachesCfT(t *testing.T) {
	p, err := findBrowser()
	if err != nil {
		t.Fatalf("findBrowser: %v", err)
	}
	if !fileExecutable(p) {
		t.Fatalf("not executable: %s", p)
	}
	t.Logf("findBrowser -> %s", filepath.Base(p))
}
