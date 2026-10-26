[evidence=B] 抓取 2026-09-13 · 源URL: https://developer.huawei.com/consumer/cn/doc/ ; https://developer.huawei.com/consumer/cn/deveco-studio/archive/ ; https://developer.huawei.com/consumer/cn/sdk/form-kit

# 华为开发者门户总览（HarmonyOS Developer）

## 站点结构

华为开发者官网为 developer.huawei.com（consumer 路径为华为开发者联盟/终端生态开发）。主要入口（均由 searxNG 快照 + 页面标题核实）：

- https://developer.huawei.com/consumer/cn/ — 华为开发者联盟首页。
- https://developer.huawei.com/consumer/cn/doc/ — 文档中心（“HarmonyOS NEXT 开发文档”），分类包括版本说明、指南、API 参考、最佳实践、FAQ；按 HarmonyOS 5.0 及以上 / HarmonyOS 4.X 及以下分代。
- https://developer.harmonyos.com/ — 旧域名，301 到 developer.huawei.com。
- https://developer.huawei.com/consumer/cn/deveco-studio/ — DevEco Studio IDE 页。
- https://developer.huawei.com/consumer/cn/deveco-studio/archive/ — DevEco Studio 历史版本下载（含 Package/Size/SHA-256 checksum 三列）。
- https://developer.huawei.com/consumer/cn/sdk/form-kit — Form Kit（卡片开发服务）SDK 介绍页。
- https://device.harmonyos.com/ — 设备开发（开源/南向）站点。

## 抓取可行性（关键）

- developer.huawei.com/consumer/cn/doc/ 的文档页是 JS 渲染 SPA：直接抓取返回仅含脚本引用的空壳（正文文本长度为个位数），cfp-fetch 与直连均如此。文档正文不可直接抓取。
- 该域名的搜索快照（searxNG）携带真实正文片段，可用来定位具体文档 URL 并核对关键词。
- consumer.huawei.com 的消费者支持页（zh-cnNNNNNN 路径）是服务端渲染，可直接抓取全文，是行为事实的高可信来源。
- 同主题正文事实可用 OpenHarmony 官方 docs 仓库（gitee.com/openharmony/docs，`zh-cn/application-dev/form/`）核对；Form Kit 文档与 OpenHarmony 文档同源。

## DevEco Studio 与 SDK 下载渠道

DevEco Studio 历史版本下载页（搜索快照与页面残留数据核实）列出 Windows(64-bit)/Mac(X86)/Mac(ARM) 三平台包，每包给出 Package 文件名、Size 与 SHA-256 checksum。快照示例（3.1.1 Release 行）：

- Windows(64-bit): devecostudio-windows-3.1.0.501.zip, 843.6M, sha256 fbe79d92017d642ee91b2471b36c3e22ff3c186a0df36f3ae683129cfd445d9c
- Mac(X86): devecostudio-mac-3.1.0.501.zip, 942.9M, sha256 1a380b8b4a172b0f00af476b3bdcd83ee2dab24937c00b72d20d9121db99f5b7
- Mac(ARM): devecostudio-mac-arm-3.1.0.501.zip, 934.8M, sha256 f3e77ba60e596c9e49cd5fc3ab67f3f944efd235f2fa7b298b501abcfc668f04（快照截断，整值以官方页为准）

DevEco Studio 集成 HarmonyOS SDK、Node.js、hvigor、ohpm 和模拟器，开箱即用（官方英文文档快照）。

DevEco CLI（AI Agent 适配，官方页快照）：`npm install -g @deveco/deveco-cli@stable`，提供 HarmonyOS 应用开发原子化能力（工具集、官方知识库、Skills），覆盖工程创建、语法检查、编译构建、运行验证场景。npm 渠道符合镜像规则（npmmirror 可装）。

## 版本清单入口

- https://developer.huawei.com/consumer/cn/doc/harmonyos-releases/overview-allversion — 所有 HarmonyOS 开发套件版本清单（快照核实存在）。
- HarmonyOS NEXT 开发文档以“HarmonyOS 5.0 及以上”为主线，旧版（4.X 及以下）文档单独分代。

## 对 Sleepy 工作流的落点

- 抓取华为文档正文：developer.huawei.com/consumer/cn/doc 走 SPA 墙，先 searxNG 定位 URL，再用 OpenHarmony gitee 仓库 raw markdown 核对正文（Form Kit 主题已验证此路径可行）。
- 行为事实（桌面操作、深色模式、主题体系）：直接抓 consumer.huawei.com 支持页。
- 下载 DevEco/SDK：官方 archive 页 sha256 优先，npmmirror 装 `@deveco/deveco-cli`。
