[evidence=-] 抓取 2026-09-13 · 记录华为开发者文档中抓取失败的主题

# 华为文档抓取缺口（gaps）

## 1. developer.huawei.com 文档中心正文（全部 harmonyos-guides / harmonyos-references 页）

- 主题：Form Kit 全部官方指南页、API 参考页、桌面万象小组件测试审核规范、主题引擎规范、AGC 发布应用（APK/RPK）文档。
- 尝试过的 URL（全部返回 HTTP 200 但正文只有脚本引用的 SPA 空壳，正文文本长度为 4 字符“文档中心”）：
  - https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/formkit-overview
  - https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-service-widget-overview
  - https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/arkts-ui-widget-process （大写旧代路径同样空壳）
  - https://developer.huawei.com/consumer/cn/doc/widget-test-0000001458222628 （桌面万象小组件-测试审核规范）
  - https://developer.huawei.com/consumer/cn/doc/content/widget-0000001245999755 （桌面万象小组件-随主题包上架）
  - https://developer.huawei.com/consumer/cn/doc/widget-separate-0000001337881265 （桌面万象小组件-单独上架）
  - https://developer.huawei.com/consumer/cn/doc/app/agc-help-releaseapkrpk-0000001106463276 （AGC 发布应用 APK）
  - https://developer.huawei.com/consumer/cn/doc/harmonyos-references/js-apis-postcardaction.md （尝试 .md 后缀 → 404 SPA 壳）
  - https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/assets/const/official/env.20260904153816.js （返回 HTML 壳而非 JS）
  - https://dcs.developer.huawei.com/doc/api/getDoc?lang=cn&path=... （HTTP 530，Cloudflare 1016）
- 失败原因：文档中心是 Angular 式 JS SPA，正文由运行时 JS 调 API 注入；未公开裸 markdown 端点；cfp-fetch（无头抓取）与直连 curl 均只拿壳。
- 建议后续获取渠道：
  - 浏览器级抓取（control-chrome skill / 本地 Chrome 打开页面后 CDP 取渲染后 DOM）。
  - searxNG 逐页快照：对具体 URL 搜索能拿到部分正文片段（本任务已用于核对标题与关键句），适合逐条事实核对，不适合整页存档。
  - OpenHarmony 官方 docs 仓库（gitee.com/openharmony/docs raw markdown）可覆盖 Form Kit 大部分开发指南正文（本任务已采用）；华为 Next 专属内容（如测试审核规范、主题引擎规范）不在该仓库。

## 2. 华为官方对“NEXT 不兼容 APK”的正式文字声明

- 主题：华为官网/开发者站点对“HarmonyOS NEXT 不再支持 APK/仅支持 HAP”的直接条款原文。
- 尝试过的 URL：
  - https://consumer.huawei.com/cn/harmonyos-next/ （可抓，但为营销页：全新架构/纯净安全/丝滑流畅，无 APK 字样）
  - https://developer.huawei.com/consumer/cn/doc/ 及 AGC 发布文档（SPA 墙，见上）
- 失败原因：正式条款位置可能在 AGC 文档（上架/发布 RPK/APK 的适用范围）或 HarmonyOS 兼容性白皮书中，均被 SPA 墙挡住；consumer 营销页不包含该表述。
- 建议后续获取渠道：浏览器渲染后抓 AGC “发布应用”文档与“HarmonyOS 应用市场上架指南”；或华为年度白皮书 PDF（consumer.huawei.com/content/dam/... 路径可直抓，如 EMUI 8.0 安全技术白皮书 PDF 已确认可下载，可找对应 NEXT 白皮书）。

## 3. 华为桌面（launcher）对 Android AppWidget 的网格规格表

- 主题：华为桌面 4xN 网格的单元格 dp、AppWidget minWidth/minHeight 与行/列映射、跨 EMUI/HarmonyOS 2-4 版本的网格差异。
- 尝试：searxNG 多组中文查询（华为桌面 网格 4x2 4x4、EMUI 小工具 尺寸、minWidth 等），consumer.huawei.com 支持页只给出添加/移动/调整大小的操作步骤，未给出网格数值。
- 失败原因：华为未公开发布 launcher 网格规格表；此类数值只在厂商内部规范或设备实测中出现。
- 建议后续获取渠道：真实设备矩阵实测（adb dumpsys window / uiautomator 量测 widget 单元格）；华为 AGC“桌面卡片设计规范”浏览器渲染后抓取（该文档可能含尺寸建议）。

## 4. 桌面万象小组件（主题引擎）规范全文

- 主题：HarmonyOS 4.X 及以下主题引擎规范（widget-0000001245999755 随主题包上架、widget-separate-0000001337881265 单独上架、globalPersist 联动、百变卡片设计指导及规范）。
- 已知信息（仅来自 searxNG 快照片段，未存全文）：theme-widget 文件夹含 preview 目录（多张 bg_X.png + manifest.xml），bg_X.png 背景尺寸 984*1656px；description.xml 为组件描述文件；设计“不能有圆角、边框，设计内容需要铺满规范尺寸”；8 类功能小组件尺寸要求在专区展示；globalPersist 属性为锁屏/桌面/小组件变量同步桥梁。
- 失败原因：同缺口 1（SPA 墙），快照只够确认文档存在与零星规格。
- 建议后续获取渠道：浏览器级抓取上述 4 个 URL；主题中心（developer.huawei.com/consumer/cn/doc/...主题中心）逐页渲染后存档。

## 5. Form Kit C API（capi-oh-form-*）与 OpenHarmony 未覆盖的华为 Next 增量

- 主题：华为 Next 专有 API（如互动卡片 liveform、场景动效、待机屏保 standby 字段）的华为侧文档全文。
- 现状：OpenHarmony gitee docs 已含 liveform/sceneanimation/standby 等 md（master 分支 `zh-cn/application-dev/form/`），本次已下载主要文件；华为文档中心对 Next 增量的措辞（API version 20+ 互动卡片等）已在 OpenHarmony 同源文件中得到印证，无额外缺口。
- 若需华为侧措辞原文：同缺口 1 渠道。
