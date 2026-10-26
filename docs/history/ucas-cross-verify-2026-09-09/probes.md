# probes — UCAS 入口 URL 网络实测 (2026-09-09)

## 工具
`curl -sS --max-redirs 0 -o /dev/null -w 'code=%{http_code} loc=%{redirect_url} len=%{size_download} ct=%{content_type}'`
- 无 XRW: 桌面浏览器 / SEP-exempt / POST 路径等价
- WITH XRW: `-H 'X-Requested-With: com.lingion.sleepy'` 模拟 Android WebView 顶层导航(chromium 硬编码注入,公开 API 无法移除)

## 实测 1: xkgo.ucas.ac.cn:3000/course/personSchedule (未登录)

```
=== xkgo personSchedule (no XRW) ===
http_code=200  redirect_url=  ct=text/html; charset=utf-8  len=6380
=== xkgo personSchedule (WITH XRW) ===
http_code=200  redirect_url=  ct=text/html; charset=utf-8  len=6380
```

body 头: `<!DOCTYPE html><html><head><title>Error</title>...`
关键文本: `<label id="loginError" class="error">` 与
`<div><a href="http://sep.ucas.ac.cn/appStore">请重新登录</a></div>`

**结论**: xkgo 未登录时**不 302 重定向到 SEP**,而是返回 200 HTML 静态 Error 页。
XRW 头对该 xkgo 路径**无影响**(服务器不检测)。"登录失败"文案**来自 xkgo 自己的 HTML**(title="Error"),**不是 Sleepy 的错误 UI**。

## 实测 2: 其他 xkgo 路径也是同款 Error 页

```
xkgo /         → code=200  loc=  len=6380  ct=text/html  (同款 Error 页)
xkgo /login    → code=404  loc=  len=17
```

**结论**: xkgo 任意路径在未登录时都返回同一 Error 页,**没有任何 xkgo URL 可作安全入口**。

## 实测 3: SEP / (登录门户根)

```
=== sep.ucas.ac.cn/ (no XRW) ===
http_code=200  redirect_url=  num_redirects=0
=== sep.ucas.ac.cn/ (WITH XRW) ===
http_code=200  redirect_url=  ct=text/html;charset=UTF-8
```

body 头: `<title>SEP 教育业务接入平台</title>`

**结论**: SEP 根路径是登录页(非受保护资源),带/不带 XRW 同为 200。

## 实测 4: SEP 受保护路径(/appStore 等)的 XRW 分流

```
/appStore       noXRW=302|sep.ucas.ac.cn/?loginFrom=/appStore  XRW=401||len=60
/welcome        noXRW=302|sep.ucas.ac.cn/?loginFrom=/welcome   XRW=401||len=60
/portal         noXRW=302|sep.ucas.ac.cn/?loginFrom=/portal    XRW=401||len=60
/index          noXRW=302|sep.ucas.ac.cn/?loginFrom=/index     XRW=401||len=60
/home           noXRW=302|sep.ucas.ac.cn/?loginFrom=/home      XRW=401||len=60
/slogin         noXRW=403                                       XRW=403 (凭据错误)
/login          noXRW=303|sep.ucas.ac.cn/                       XRW=303|sep.ucas.ac.cn/
```

**结论**:
- SEP 任何**非根**路径都受 XRW filter 保护(带 XRW → 401 JSON,与 app 报告中 `{"code":401,...}` 一致)。
- SEP **根路径**(`/` 与 `/?loginFrom=*`)始终 200,**不受 XRW 影响**。
- `/slogin` 是登录端点,XRW 不影响(POST 豁免)。
- `/login` 303 → `/`,XRW 不影响。

## 实测 5: SEP 根路径 body

`<title>SEP 教育业务接入平台</title>`,len=16027 text/html
→ 这是 SEP 登录页面,用户可在此直接输入账号密码登录。

## 综合结论
唯一可用作首屏入口且不带 XRW 风险的 URL = `https://sep.ucas.ac.cn/`。
xkgo 任意 URL 都不行(都返 Error 页)。SEP 受保护路径(`/appStore` 等)虽然带 cookie 后正常,但被 WebView XRW 注入污染,需要独立分支的 XRW Strip 修复才能在 WebView 中落地。