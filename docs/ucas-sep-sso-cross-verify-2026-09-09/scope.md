# UCAS SEP SSO 跨仓验证 (#18 回复修复) — scope

- 日期: 2026-09-09
- 学校: 中国科学院大学 (UCAS)
- 任务类型: 适配修 bug (SSO 登录链层, 非课表协议层)
- 来源: issue #18 damifan3 新评论 (2026-09-09T01:51:53Z): App 内点「请重新登录」直接渲染
  `{"code":401,"msg":"未登录或会话已过期","toUrl":"/"}`, 浏览器同链接正常跳 SEP 登录页。
- 触发: jw-cross-verify-sop「旧学校 bug」强触发场景。
- 承认: 首轮修复 (0f3d77c) 凭服务端 curl 实测直接落地, **检索矩阵未跑**; 用户问"交叉验证过
  没有啊"后补跑 (本目录 = 补跑产物)。教训: 实测实锤 ≠ 免检索, SOP 触发就跑全流程。

## 修复本体

`fix/issue-18-sep-xrw-login` @ `0f3d77c`: SepXrwStripInterceptor — shouldInterceptRequest
只拦 sep.ucas.ac.cn 的 GET, 剥 X-Requested-With 重建请求, 302/303 逐跳跟随 + Set-Cookie
同步, 出域回 302 交 WebView 原生跟随。根因链与证据见 findings.json。
