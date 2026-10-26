# root-cause — 为什么用户一进去就是"登录失败"

## 用户视角的三个观察
1. 在 Sleepy 里点 UCAS 导入,进去就是"登录失败"页面。
2. 浏览器里直接打开 https://xkgo.ucas.ac.cn:3000/course/personSchedule 会弹"登录失败"页面。
3. 在 Sleepy WebView 里点"请重新登录"链接,直接报错 401 JSON。

## 根因链

### 因 1 — schools.json UCAS url 指向受保护的课表页
```json
{
  "name": "中国科学院大学",
  "url": "https://xkgo.ucas.ac.cn:3000/course/personSchedule",  ← 受 SEP 会话保护
  "type": "ucas",
  ...
}
```
该 url 是**课表网格页**(personSchedule),本身需要 SEP session。

### 因 2 — JwWebViewLoginScreen.kt 用 school.url 作首屏
文件 `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt:308`:
```kotlin
JwWebView(url = school.url.ifBlank { "https://www.baidu.com" }, ...)
```
即直接 `loadUrl(school.url)`,**没有任何前置登录跳转**。UCAS 条目 url = xkgo personSchedule,WebView 直接 GET。

### 因 3 — xkgo 未登录不 302 重定向到 SEP,而是返回静态 Error 页
实测(probes.md 实测 1):xkgo personSchedule 未登录时**返 200 HTML Error 页**(title=Error, 含 `登录失败!` + `请重新登录` 链接)。

"登录失败"文案出处: xkgo 自家 HTML `<label id="loginError" class="error">` 渲染的红色提示条,**不是 Sleepy 自己的 strings.xml**。
Sleepy 的错误 UI(strings.xml + JwImportActivity 错误路径)**只有在解析失败时**才显示,与本场景无关。

### 因 4 — 浏览器行为看似不同实则相同
浏览器直开 xkgo personSchedule:浏览器**也**收到 Error 页(同 200 + 同 body),用户看到的也是"登录失败"。浏览器"弹 CAS 登录"的现象,实际是用户**手动点击 Error 页里的"请重新登录"链接**才触发的 —— 浏览器跟着链接走 302 → SEP → CAS → 回 xkgo。
Sleepy 用户点同一链接,被 SEP `/appStore` 的 XRW filter 拦截返 401 JSON(因 5)。

### 因 5 — SEP /appStore 受 XRW 头污染(独立 bug,不在本分支)
WebView 强制注入 `X-Requested-With: <包名>`,SEP filter 据此返 401 JSON 而非 302 登录页。
**这与本入口修复正交**:本分支只解决"入口一进去就 Error"的问题;SEP 登录后的 /appStore XRW 拦截由独立分支 `fix/issue-18-sep-xrw-login` (tip 907487a) 修,等用户批准合并。

## 修复判据
- 不指向任何 xkgo 路径(全返 Error)。
- 不指向 SEP 受保护路径(被 XRW 拦截,401 JSON)。
- 指向 XRW-safe 的 SEP 路径作为登录入口。

唯一满足三条的 URL: `https://sep.ucas.ac.cn/`(SEP 根,登录页,200,XRW-safe)。