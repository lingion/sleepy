# vivo 抓取 gaps (2026-09-13)

## 1. vivo 开发者门户 JS 渲染页 (dev.vivo.com.cn 网页版)

- 主题名: dev.vivo.com.cn 网页正文 (documentCenter 网页版文档渲染)
- 尝试过的 URL: https://dev.vivo.com.cn/documentCenter/doc/845 、
  /documentCenter/doc/12 、 /promote/atomicComponent 、 /home
- 失败原因: 全部为 Vue SPA,HTML 里只有 `<div id="app">` 与 bundle 引用,
  cfp-fetch/web2text 均只拿到 "vivo开放平台" 8 字正文。
- 已用绕行渠道 (成功): 从 bundle `app.f3332d79.js` 反提取 `webapi/*` 端点清单,
  发现 `webapi/doc/info?id=<docId>` 返回完整 JSON (title/content/updateTime/
  breadCrumbs),全部文档内容已按此渠道 A 级落盘 — 门户网页本身无需再抓。

## 2. vivo 官网服务 FAQ 页 (vivo.com.cn/service/questions/all)

- 主题名: 系统级深色模式开关路径、锁屏小组件添加路径、第三方桌面设置
- 尝试过的 URL:
  https://www.vivo.com.cn/service/questions/all?categoryId=170&questionId=1745 、
  ...questionId=1784 、 ...questionId=2027 、
  https://www.vivo.com.cn/originos (OriginOS 产品页更新日志)
- 失败原因: 同为 JS SPA;HTML 中无 webapi 端点引用 (仅
  /search/ajax/assResult 等搜索接口);问答正文走未暴露的动态接口。
- 现状: 用 searxNG 抓到的官方 FAQ 摘要 (含原文数值/路径) 作为 evidence=B
  引用进 darkmode-behavior.md / launcher-behavior.md。
- 建议后续渠道: 手机/桌面 Chrome 渲染后保存 HTML; 或 vivo 官网 App 内
  抓包问答接口; 或 IT之家/网易等转载 (已部分引用)。

## 3. OriginOS 5/6 官网更新日志全文

- 主题名: OriginOS 5 → 6 逐版本桌面/原子组件变化日志
- 尝试过的 URL: https://www.vivo.com.cn/originos (正文 0 命中"桌面/原子组件")
- 失败原因: JS SPA;更新日志在动态渲染区。
- 现状: doc 927 (OriginOS 6 开发者预览版新特性) 为 A 级原文; 官网
  changelog 仅存 searxNG 摘要 (抽屉置顶/文件夹功能/空白页中空态图标等)。
- 建议后续渠道: bbs.vivo.com.cn 本周系统升级资讯系列帖 (Vol.174 等已
  发现入口); vivo 开发者大会 (2026-09-16) 后的官方发布稿。

## 4. developers.vivo.com 国际门户

- 主题名: 国际版开发者能力清单 (System/App Services 开放能力)
- 尝试过的 URL: https://developers.vivo.com/ 、 /doc/ 、
  /doc/d/94079ff7fd20038c3123b3389a1d9dfe
- 失败原因: "developers-portal-web" SPA;文档中心页面提示
  "We're sorry but developers-portal-web doesn't work properly without
  JavaScript enabled"。
- 影响: 低 — 国际门户内容 (AI/IoT/System 开放能力) 与 Android AppWidget
  适配关联弱; 大陆文档树已全量。
- 建议后续渠道: 同 bundle 反提取法 (先抓 index.html 找 bundle)。

## 5. 妙玩组件设计指导 v1.0 全文 (腾讯文档)

- 主题名: 各组件卡类型制作指导说明 (灵动卡/中卡/大卡逐类型)
- 尝试过的 URL: doc 808 内嵌腾讯文档跳转链接 (外链)
- 失败原因: 腾讯文档需登录/JS;doc 808 正文只给出大纲。
- 现状: doc 801 (效果引擎+数据组件开发指导) 已 A 级落盘,覆盖主要工程约束。
- 建议后续渠道: 无登录环境的腾讯文档只读导出; 或 i 主题开发者 QQ 群索取。

## 6. 跨厂商 StackOverflow 实锤帖补充

- 主题名: requestPinAppWidget 在 vivo 上的静默失败二次证据
- URL: https://stackoverflow.com/questions/72492999/widget-pinning-not-working-with-android-huawei-and-vivo-devices
- 状态: 已知入口未展开 (CSDN 147627142 渠道矩阵已覆盖同一结论, B 级落进
  atomic-widget-dev-notes.md); 后续如需欧美用户实证可补此帖评论时间线。
