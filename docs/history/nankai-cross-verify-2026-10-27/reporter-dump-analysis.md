# 报告人活体诊断包核验 — zzask77 附件（issue #236, 2026-10-27）

## 包本体

- 来源: issue #236 正文 Current behavior / Steps reproduce 两栏, 两个 user-attachments 链接指向同一 zip（SHA-256 一致, `1b30e925…e922c4`）, 6519 字节。
- 性质: **不是**第三方协议采集包, 是 Sleepy 自带诊断导出（`sleepy-jw-dump-20261027-094704.zip`, appVersion=1.0.62/10062, debug=false）。含 16 文件: summary、单帧 outerHTML、dom-inventory、netlog、console、cookies、storage、links、net-live manifest、collection-summary、device 等。
- 采集环境: vivo V2536A, Android 16 (sdk=36), Google WebView 155。
- 脱敏状态: **未脱敏** — frame 含真实姓名+学号, cookies-full.txt 含活体 JSESSIONID(.std3)/CLBSERVERID 值（本档案一律不复写原值）。

## 现场判读（与修复前行为逐条对桩）

| 包内证据 | 读数 | 对桩结论 |
|---|---|---|
| summary.txt | `status=WRONG_PAGE, matchedAnchors=(空), selectedFramePath=(top), diagnosticHint=当前页面未检测到课表容器` | 与根 URL 直接导入 → 无锚点 → WRONG_PAGE 旧行为完全一致 |
| netlog.txt (7 请求) | `GET /` → favicon → `GET /eams/home.action`(×2 重载) → 此后**零后续业务请求** | 登录后落在 home.action 就停住, 从未触到 courseTableForStd 子路径 — 旧版本无入口自动动线的直接活体证据 |
| frames/1-_top_.html (6612B) | home.action 框架页: banner 带真实姓名+学号、`#menu_panel`/`#main` 两个空 `ajax_container`、内联脚本全依赖 `jQuery(...)`/`bg.Go(...)` | 用户看到「网页空白」的直接原因: `Uncaught ReferenceError: jQuery is not defined / bg is not defined`（console.txt 4 连发, +6838ms）→ 菜单容器永不填充。**非树莓教务迁移**, 是 classic_eams home.action 在 WebView 里脚本依赖断链 |
| cookies-full.txt | eamis 域 JSESSIONID(.std3) + CLBSERVERID 成对存在 | **IAM 登录态有效**, 失败不怪登录, 怪没有动线把他送进课表入口 |
| collection-summary.json / links.json / storage.json | networkRecords=0, links=[], sessionStorage={} | 采集面干净: 页面确实没发出任何可复用请求, 不存在「动作页响应体」可供反推 — 反证其包不承载协议信息 |
| 帧形态判据 | home.action 无 `ids` 注入、无学期下拉、不含 IAM 指纹、无课表锚点 | tryEntryPage 的 scope-not-found 分支正确命中; 不会被误判成 scope 页, 也不会被 LOGIN_FINGERPRINTS 误报 SESSION_EXPIRED |

## 针对性覆盖结论

- **他的症状 = 我们的修复面**: 根 URL 导入 → WRONG_PAGE「请先在门户进入」→ 修复后同场景由 5eaef53e `tryEntryPage()` 接管（host 命中南开 → 同源 GET `courseTableForStd.action` 入口页 → 离屏解析取 scope → 走原提取链）。jQuery/bg 断链不影响 fetch 通路（原生 XRW, 非页面脚本）。
- **他的包不覆盖的部分**: 无 courseTableForStd 入口页/动作页样本。协议串与 fixture 形态仍由 38 仓跨仓共识 + 同族 NBCC 互证支撑; 其包的作用是**独立印证旧失败形态与登录态有效性**, 属反证链补强, 不是修复输入源。

## 残余风险（本包新读出, 修正此前矩阵判断）

- `frames/1-_top_.html` 的 `<head>` 为空, netlog 全程**零子资源请求**（除 favicon 外无一个 js/css）→ ReferenceError 的直接原因不是 eamis 本身不发脚本, 而是**该次响应根本没带外部资源链**。这与 NBCC「深信服把移动 UA 送进钉钉残废 SPA」同型嫌疑: 南开侧也可能对 UA（sec-ch-ua-mobile ?1 + Android WebView）做条件输出。
- 影响面评估: 修复主链靠**同源 XRW GET 入口页**取 scope, 不依赖页面脚本; 但若 courseTableForStd.action 同样按 UA 条件返回残废页, tryEntryPage 会拿到无 `ids` 的壳 → 回退「请先在门户进入」。协议串本身（POST body、学期号）不依赖页面, 仍可能直接走通。
- 本机不可达, 不活体探测, 暂不改码。优先级预案: 活体回传若仍失败 → 第一排查项 = UA 分流, 手法复用 NBCC 桌面 UA 分支（protocol-matrix.md 的「UA 分流证据未见」结论由本节取代, 定性从「无」改「存疑·单向」）。

## 裁决闸影响

- 该包为 **1.0.62（修复前）** 回传, 只证旧行为对症; 新链路活体成功由随后的测试包回传补齐（下一行）。
- **闸已闭（2026-10-27 当日, 取代上一行）**：测试包（1.0.62-debug, 含入口自动动线）经仓库临时分支 tmp/issue236-nankai-apk 分发, zzask77 真机回传「可以正常导入」(issue #236 comment 6435933798, 07:08Z) — 根 URL 导入链路活体走通, tryEntryPage 在有效 IAM 会话下实测成功。他同时报「页面空白」, 与残余风险节 UA 存疑吻合: 页面视觉残废不影响原生 XRW 取数, 空白排查继续。落地: 基线行南开补显式 `"status": "supported"`（此前缺省隐式 supported, supported 计数 351 不变）, Schools179 pinned 测试加 status 断言并注明裁决依据。
- 隐私跟进（待发版回复时一并, 未获批准前不外发）: 提醒其原附件含未脱敏姓名/学号/活体会话 Cookie, 建议删除或替换为脱敏版; JSESSIONID 公开存在会话冒用窗口。
