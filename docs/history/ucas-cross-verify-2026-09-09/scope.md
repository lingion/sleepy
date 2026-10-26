# scope — UCAS #18 入口 URL 修复 (2026-09-09)

## 用户报告 (issue #18, 2026-09-09 01:51:53Z damifan3)
"教务直连时发生错误。浏览器中第一次进入 https://xkgo.ucas.ac.cn:3000/course/personSchedule 会提示登录失败
[Error 页截图: title=Error, 登录失败!, 请重新登录 链接 → http://sep.ucas.ac.cn/appStore]
点击请重新登录,可跳转 sep 账号登录,https://sep.ucas.ac.cn/?loginFrom=/appStore
但是 sleepy 中点击请重新登录直接报错:{"code":401,"msg":"未登录或会话已过期","toUrl":"/"}"

## 适配类型
**已知学校适配修 bug**(已闭环 issue #18 v1.0.52),本次 scoped 修复入口 URL,非新适配。

## 范围
只动 `app/src/main/assets/schools.json` 的 UCAS 条目 url 字段 + 1 条契约回归测试 + 本 docs 归档。
不动 parser / 不动 SEP 鉴权 / 不动 WebView XRW 注入(后者在独立分支 `fix/issue-18-sep-xrw-login` 等批,本分支不依赖其合入)。

## 不在范围(交回用户决策)
- SEP XRW 头剥离代理 (SepXrwStripInterceptor) — 在 `fix/issue-18-sep-xrw-login` (tip 907487a),未合并,本分支不动。
- 关 issue #18 / 回复用户 — 用户明示批准才动。