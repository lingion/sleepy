package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/chromedp"
)

func TestPlanDetailCapturesUsesNavigationForCourseLinks(t *testing.T) {
	main := "https://xkgo.ucas.ac.cn:3000/course/personSchedule"
	plans := planDetailCaptures(main, []string{
		"https://xkcts.ucas.ac.cn:8443/course/coursetime/315751",
		"https://xkcts.ucas.ac.cn:8443/course/coursetime/315751", // 去重
		"https://xkgo.ucas.ac.cn:3000/logout",                    // logout 排除
		"https://xkgo.ucas.ac.cn:3000/course/help",               // 同源但路径命中
		main, // 主页面自身排除 (导航回自己 = 死循环)
	})
	if len(plans) != 2 {
		t.Fatalf("expected cross-origin + same-origin course links (deduped, main excluded), got %#v", plans)
	}
	for _, p := range plans {
		if p.Strategy != captureByNavigation || p.Method != "GET" {
			t.Fatalf("detail capture must navigate with GET, got %#v", p)
		}
		if p.URL == main {
			t.Fatalf("main page itself must not be a navigation candidate: %s", p.URL)
		}
	}
	if plans[0].URL != "https://xkcts.ucas.ac.cn:8443/course/coursetime/315751" {
		t.Fatalf("first plan should be the cross-origin detail, got %s", plans[0].URL)
	}
}

// TestFindWeekParam 验证 G1 周参数识别: qz_app curriculum?week=N 型 query 与
// 表单体 week=/zc= 两种形态都要认出; 无周参数返回空。
func TestFindWeekParam(t *testing.T) {
	cases := []struct {
		name, url, body, wantName, wantCur string
	}{
		{"query week", "https://jw.example.edu/student/curriculum?week=3&kbjcmsid=", "", "week", "3"},
		{"query zc", "https://jw.example.edu/xskb.do?zc=5", "", "zc", "5"},
		{"body week", "https://jw.example.edu/getKb", "gnmkdm=N2151&week=2", "week", "2"},
		{"body zc", "https://jw.example.edu/xskbcx", "XNXQDM=2026-2027-1&zc=1", "zc", "1"},
		{"body weekIndex", "https://m.example.edu/api", "weekIndex=7", "weekIndex", "7"},
		{"no week param", "https://jw.example.edu/xskbcx", "XNXQDM=2026-2027-1", "", ""},
		{"empty query", "https://jw.example.edu/curriculum?", "", "", ""},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			name, cur := findWeekParam(c.url, c.body)
			if name != c.wantName || cur != c.wantCur {
				t.Fatalf("findWeekParam(%q,%q) = (%q,%q), want (%q,%q)", c.url, c.body, name, cur, c.wantName, c.wantCur)
			}
		})
	}
}

// TestReplaceFormAndURLParam 验证周参数替换 helper: 替换已存在的, 缺失时原样返回。
func TestReplaceFormAndURLParam(t *testing.T) {
	if got := replaceFormParam("a=1&week=3&b=2", "week", "9"); got != "a=1&week=9&b=2" {
		t.Fatalf("replaceFormParam got %q", got)
	}
	if got := replaceFormParam("a=1", "week", "9"); got != "a=1" {
		t.Fatalf("missing param must be untouched, got %q", got)
	}
	if got := replaceFormParam("a=1&zc", "zc", "2"); got != "a=1&zc=2" {
		t.Fatalf("valueless segment must gain value, got %q", got)
	}
	if got := replaceURLParam("https://x/y?week=3&id=5", "week", "12"); got != "https://x/y?week=12&id=5" {
		t.Fatalf("replaceURLParam got %q", got)
	}
}

// TestRespHeadersOf 验证 G3 响应头序列化: 普通头原样, Set-Cookie 只留名字。
func TestRespHeadersOf(t *testing.T) {
	got := respHeadersOf(network.Headers{
		"Content-Type": "application/json",
		"Set-Cookie":   "JSESSIONID=abc123; Path=/\nTOKEN=secret99",
		"X-Weaver-RW":  "true",
	})
	if !strings.Contains(got, "Content-Type: application/json") ||
		!strings.Contains(got, "X-Weaver-RW: true") {
		t.Fatalf("plain headers must be kept verbatim, got %q", got)
	}
	if strings.Contains(got, "abc123") || strings.Contains(got, "secret99") {
		t.Fatalf("Set-Cookie values must be stripped, got %q", got)
	}
	if !strings.Contains(got, "JSESSIONID=(值省略)") || !strings.Contains(got, "TOKEN=(值省略)") {
		t.Fatalf("Set-Cookie names must be kept, got %q", got)
	}
}

// TestJsonpPrefix 验证 G10 JSONP 探测: callback({...}); 命中, 普通 JS 不误判。
func TestJsonpPrefix(t *testing.T) {
	if got := jsonpPrefix(`callback({"a":1});`); got != "callback" {
		t.Fatalf("jsonp got %q", got)
	}
	if got := jsonpPrefix(`jQuery1234([1,2]);`); got != "jQuery1234" {
		t.Fatalf("jsonp array got %q", got)
	}
	if got := jsonpPrefix(`{"a":1}`); got != "" {
		t.Fatalf("plain JSON must not match, got %q", got)
	}
	if got := jsonpPrefix(`if(x){y()}`); got != "" {
		t.Fatalf("JS control flow must not match, got %q", got)
	}
}

func TestReplayCandidatesOnlyIncludeObservedPostRequests(t *testing.T) {
	recs := []*reqRec{
		{url: "https://jw.example.edu/api/schedule", method: "POST", mimeType: "application/json"},
		{url: "https://jw.example.edu/course/detail/1", method: "GET", mimeType: "text/html"},
		{url: "https://other.example.edu/api/detail", method: "POST", mimeType: "application/json"},
	}
	got := replayCandidates("https://jw.example.edu/schedule", recs)
	if len(got) != 1 || got[0].URL != "https://jw.example.edu/api/schedule" || got[0].Method != "POST" {
		t.Fatalf("only same-origin observed POST may replay, got %#v", got)
	}
}

func TestOutcomeSummaryDeduplicatesRepeatedCorsPreflightEvents(t *testing.T) {
	results := newOutcomeSummary()
	for _, method := range []string{"GET", "POST", "OPTIONS"} {
		results.add(collectionOutcome{
			Category: "cors_preflight_blocked",
			URL:      "https://xkcts.ucas.ac.cn:8443/course/coursetime/315751",
			Method:   method,
			Phase:    "legacy_replay",
		})
	}
	groups := results.groups()
	if len(groups) != 1 || groups[0].UniqueURLs != 1 || groups[0].Events != 3 {
		t.Fatalf("expected one grouped CORS outcome with three events, got %#v", groups)
	}
}

func TestCaptureDetailNavigationsUsesSameBrowserSessionAcrossOrigins(t *testing.T) {
	if execCtx() == nil {
		t.Skip("chromedp unavailable")
	}
	detail := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if _, err := r.Cookie("collector_session"); err != nil {
			http.Error(w, "missing session", http.StatusUnauthorized)
			return
		}
		_, _ = w.Write([]byte("<html><title>Course detail</title><body>courseWeek=10101</body></html>"))
	}))
	defer detail.Close()
	root := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.SetCookie(w, &http.Cookie{Name: "collector_session", Value: "ok", Path: "/"})
		_, _ = w.Write([]byte("<html><body>schedule</body></html>"))
	}))
	defer root.Close()

	if err := chromedp.Run(execCtx(), chromedp.Navigate(root.URL+"/course/personSchedule")); err != nil {
		t.Fatalf("open root: %v", err)
	}
	p := newPacker()
	c := NewCollector()
	success, expired, failed := c.captureDetailNavigations(execCtx(), p, root.URL+"/course/personSchedule", []string{detail.URL + "/course/coursetime/315751"})
	if success != 1 || expired != 0 || failed != 0 {
		t.Fatalf("detail navigation result success=%d expired=%d failed=%d", success, expired, failed)
	}
	if len(p.entries) != 1 || !strings.Contains(string(p.entries[0].data), "courseWeek=10101") {
		t.Fatalf("detail document was not stored: %#v", p.entries)
	}
}
