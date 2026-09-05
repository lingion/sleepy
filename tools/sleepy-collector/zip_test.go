package main

import (
	"archive/zip"
	"bytes"
	"testing"
)

func TestBuildZipValid(t *testing.T) {
	files := []zipEntry{
		{"INDEX.txt", []byte("Sleepy 采集包\n中文内容测试\n")},
		{"1-dom/top_page.html", []byte("<html><body>课表</body></html>")},
		{"4-net-replay/xskcb.do.json", []byte(`{"code":"0","datas":{"timetables":[{"KCM":"大学物理","ZC":3}]}}`)},
		{"6-logs/network-log.txt", bytes.Repeat([]byte("x"), 300000)},
	}
	data := buildZip(files)
	zr, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatalf("zip.Reader: %v", err)
	}
	if len(zr.File) != len(files) {
		t.Fatalf("entries: got %d want %d", len(zr.File), len(files))
	}
	want := map[string]string{}
	for _, f := range files {
		want[f.name] = string(f.data)
	}
	for _, f := range zr.File {
		r, err := f.Open()
		if err != nil {
			t.Fatalf("open %s: %v", f.Name, err)
		}
		var buf bytes.Buffer
		if _, err := buf.ReadFrom(r); err != nil {
			t.Fatalf("read %s: %v", f.Name, err)
		}
		r.Close()
		if got := buf.String(); got != want[f.Name] {
			t.Fatalf("content mismatch %s: %d vs %d bytes", f.Name, len(got), len(want[f.Name]))
		}
	}
}

func TestCRCKnownVector(t *testing.T) {
	if got := crc32([]byte("123456789")); got != 0xCBF43926 {
		t.Fatalf("crc32(123456789) = %x, want cbf43926", got)
	}
}

func TestPackerDedupBudget(t *testing.T) {
	p := newPacker()
	if !p.add("a/x.txt", "", "hello") {
		t.Fatal("first add should succeed")
	}
	if p.add("a/y.txt", "", "hello") {
		t.Fatal("same-dir duplicate body should merge")
	}
	if !p.add("b/x.txt", "", "hello") {
		t.Fatal("different dir same body should keep")
	}
	// budget: fill with big bodies until overflow drops
	big := make([]byte, 3*1024*1024)
	n := 0
	for i := 0; i < 20; i++ {
		if p.add(p.uniq("big/f"+string(rune('a'+i))+".bin"), "", string(big)) {
			n++
		}
	}
	if p.used > maxTotalBytes {
		t.Fatalf("budget exceeded: %d", p.used)
	}
}
