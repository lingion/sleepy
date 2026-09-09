# 河北资源环境职业技术学院 cross-verification scope

- Date: 2026-09-09
- School: 河北资源环境职业技术学院 (jwpt.hebzyhj.edu.cn:1233, schoolCode 50139)
- Change type: NEW school adoption (new protocol type `qz_app`)
- Reporter: 学生邮件单独回传给维护者 (非 GitHub issue; 提供采集包 sleepy-adapt-0907-120917.zip)
- Trigger: 用户指示"按照分支规范去适配这个大学, 但禁止先落地"
- Evidence: 学生回传 v1.2 采集包 (61 文件: DOM/inline/res/net-live/net-replay/storage/logs)
- Protocol family: 强智移动教务 SPA (qzdatasoft mobile, /dist/#/login + JSON API)
- API prefix: /njwhd (serverconfig.json ApiUrl, 各校部署可不同 → 运行时发现)
- Docs dir: docs/hebzyhj-cross-verify-2026-09-09/
- Capture 归档: docs/hebzyhj-cross-verify-2026-09-09/capture/ (gitignore 全排除, 仅 README)
- Branch: adapt/hebzyhj-qzapp (落地需用户明示批准, 本目录与代码暂不 commit)

## Step 1 摘要

1. 采集包身份实锤: dist_serverconfig.json = {title: 河北资源环境职业技术学院,
   schoolCode: 50139, ApiUrl: https://jwpt.hebzyhj.edu.cn:1233/njwhd}
2. 协议族实锤: 强智移动教务 (jx0404id/jx0408id/xx0101id/kbjcmsid 强智字段族 +
   qzkj key + /dist/#/ SPA 壳), 非经典强智 /jsxsd HTML 表格
3. 摄取设计: WebView 登录 SPA → 注入 JS GET serverconfig.json 发现 ApiUrl →
   带 sessionStorage.Token POST /student/curriculum → 桥回 Kotlin 解码
4. 解码唯一落点: JwQzAppParser (Kotlin); JS 只做 fetch + 传输层 401 路由
   (跨语言 invariant, 契约测试锁死)
