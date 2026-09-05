package main

import "testing"

// WHUT issue#15 实锤: 教务把 POST body base64 编码后发送
// ("XNXQDM=2026-2027-1" → "WE5YUURNPTIwMjYtMjAyNy0x")。
// 带参重试必须识别该形态并以同形态回发, 否则服务端解码失败全空。
func TestLooksBase64Body(t *testing.T) {
	cases := []struct {
		name, in string
		want     bool
	}{
		{"whut real body", "WE5YUURNPTIwMjYtMjAyNy0x", true},
		{"whut real body with params",
			"WE5YUURNPTIwMjYtMjAyNy0xJnBhZ2VTaXplPTEmcGFnZU51bT0xJipvcmRlcj0tQ1pTSg==", true},
		{"plain form", "XNXQDM=2026-2027-1", false},
		{"empty", "", false},
		{"bad len", "WE5YUURN", false}, // 8%4==0 但解码后无 =/{ — 实为 "WE5YUUR", len 8 可整除 → 看解码
		{"json body", `{"a":1}`, false},
		{"len not mult4", "WE5YUURNPT", false},
	}
	for _, c := range cases {
		if got := looksBase64Body(c.in); got != c.want {
			t.Errorf("%s: looksBase64Body(%q) = %v, want %v", c.name, c.in, got, c.want)
		}
	}
}

func TestEncodeBodyLike(t *testing.T) {
	b64 := "WE5YUURNPTIwMjYtMjAyNy0x"
	plain := "XNXQDM=2026-2027-1"
	if got := encodeBodyLike(b64, plain); got == plain {
		t.Errorf("base64 原始体应编码后回发, got 明文 %q", got)
	}
	if got := encodeBodyLike(plain, plain); got != plain {
		t.Errorf("明文原始体应原样回发, got %q", got)
	}
}

func TestIsErrPage(t *testing.T) {
	// WHUT issue#15 采集包 3-res/ 里 3 个 .do 全是这类异常页
	sysErr := `<!DOCTYPE html><html><head><title>系统异常</title></head><body>出错了</body></html>`
	if !isErrPage(sysErr) {
		t.Error("系统异常页应被识别")
	}
	login := `<html><head><TITLE>登录</TITLE></head></html>`
	if !isErrPage(login) {
		t.Error("登录页应被识别")
	}
	ok := `{"code":"0","datas":{"rows":[{"KCM":"高数"}]}}`
	if isErrPage(ok) {
		t.Error("正常课表 JSON 不应误判为错误页")
	}
}
