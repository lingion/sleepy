// cdp_events.go: chromedp v0.21 适配层 — 旧版 ListenTarget 单回调分发所有
// 事件;新版改成 typed event 迭代器 (iter.Seq2[E, error])。本文件把订阅
// 适配回旧形态:pumpEvents 在独立 goroutine 消费迭代器,按指针类型分发回
// 原有 onEventTab/handleAutoAttach 等处理器(它们的 switch 无需改动)。
//
// 语义要点 (对齐旧版 v0.16):
//   - Events/BrowserEvents 返回迭代器时即完成订阅注册,同步调用不会丢事件;
//   - 迭代器阻塞等事件,必须放独立 goroutine;ctx 结束时订阅自动关闭;
//   - 每个订阅是独立无界队列,多路订阅互不吞事件。
package main

import (
	"context"

	"github.com/chromedp/cdproto/cdp"
	"github.com/chromedp/cdproto/log"
	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/cdproto/runtime"
	"github.com/chromedp/chromedp"
)

// ptrVal 生成命令参数里的可选指针字段。
func ptrVal[T any](v T) *T { return &v }

// act 把一个 CDP 命令包成 Void 动作,供 Do/Steps 组合 (target 级会话)。
func act[P, R any](cmd cdp.Command[P, R], params P) chromedp.Action[chromedp.Void] {
	return chromedp.Func(func(ctx context.Context, t *chromedp.Target) error {
		_, err := cdp.Call(ctx, t, cmd, params)
		return err
	})
}

// evalString 页内求值取 string,失败返回空串 (行为等价旧版指针出参 + 忽略错误)。
func evalString(ctx context.Context, expr string) string {
	v, err := chromedp.Run(ctx, chromedp.Evaluate[string](expr))
	if err != nil {
		return ""
	}
	return v
}

// pumpEvents 在 ctx 对应 tab 会话订阅 typed 事件,逐条以指针分发。
func pumpEvents[E any](ctx context.Context, ev cdp.Event[E], dispatch func(ev any)) {
	go func() {
		for e, err := range chromedp.Events(ctx, ev) {
			if err != nil {
				return // ctx 结束或解码失败,订阅自动收尾
			}
			e := e
			dispatch(&e)
		}
	}()
}

// pumpBrowserEvents 浏览器级会话 (Browser/Target 域) 事件订阅。
func pumpBrowserEvents[E any](ctx context.Context, ev cdp.Event[E], dispatch func(ev any)) {
	go func() {
		for e, err := range chromedp.BrowserEvents(ctx, ev) {
			if err != nil {
				return
			}
			e := e
			dispatch(&e)
		}
	}()
}

// startTabPumps 一次性挂上全域捕获所需的全部 tab 级事件订阅
// (等价旧版 ListenTarget(c.onEventTab) + 下载/iframe 走浏览器级订阅)。
func (c *Collector) startTabPumps(ctx context.Context, seq int) {
	dispatch := func(ev any) { c.onEventTab(seq, ev) }
	pumpEvents(ctx, network.RequestWillBeSentExtraInfo, dispatch)
	pumpEvents(ctx, network.WebSocketCreated, dispatch)
	pumpEvents(ctx, network.WebSocketFrameSent, dispatch)
	pumpEvents(ctx, network.WebSocketFrameReceived, dispatch)
	pumpEvents(ctx, network.WebSocketFrameError, dispatch)
	pumpEvents(ctx, network.RequestWillBeSent, dispatch)
	pumpEvents(ctx, network.ResponseReceived, dispatch)
	pumpEvents(ctx, network.LoadingFinished, dispatch)
	pumpEvents(ctx, network.LoadingFailed, dispatch)
	pumpEvents(ctx, log.EntryAdded, dispatch)
	pumpEvents(ctx, runtime.ConsoleAPICalled, dispatch)
}
