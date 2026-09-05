package main

import (
	"testing"
)

// sanity: postJS output must be valid JS that resolves to a JSON string — verify shape only
func TestPostJSShape(t *testing.T) {
	s := postJS("https://x.example/a.do", "A=1")
	if len(s) < 50 || s[:3] != "(as" {
		t.Fatalf("unexpected postJS head: %q", s[:20])
	}
}
