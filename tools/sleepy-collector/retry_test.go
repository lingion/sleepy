package main

import (
	"context"
	"errors"
	"testing"
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
