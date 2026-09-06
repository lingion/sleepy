package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/chromedp/chromedp"
)

func TestPlanDetailCapturesUsesNavigationForCrossOriginLinks(t *testing.T) {
	plans := planDetailCaptures(
		"https://xkgo.ucas.ac.cn:3000/course/personSchedule",
		[]string{
			"https://xkcts.ucas.ac.cn:8443/course/coursetime/315751",
			"https://xkcts.ucas.ac.cn:8443/course/coursetime/315751",
			"https://xkgo.ucas.ac.cn:3000/logout",
			"https://xkgo.ucas.ac.cn:3000/course/help",
		},
	)
	if len(plans) != 1 {
		t.Fatalf("expected one unique cross-origin detail plan, got %#v", plans)
	}
	if plans[0].Strategy != captureByNavigation || plans[0].Method != "GET" {
		t.Fatalf("cross-origin detail must navigate with GET, got %#v", plans[0])
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
