# Android v1.0.63 公开文档事实来源

产品源码固定为 [v1.0.63](https://github.com/lingion/sleepy/tree/v1.0.63)，提交 `b16aa84d11c250acdf644cd8b7812ab27d03dc6a`。发布时间为 2026-11-01T17:06:44Z，文档核对日期为 2026-11-02。此表记录 FAQ、llms.txt 与官网共同事实的来源，不将后续 main 改动归入已发布 APK。

## 功能对账

| 公开事实 | 发布源码中的证据 | 文档处理 |
| --- | --- | --- |
| Android最低API26，target/compile37，独立debug包名 | app/build.gradle.kts | 保留，区分图形API限制 |
| 五种界面语言 | app/build.gradle.kts localeFilters及values资源 | 补充，不与网站中英混同 |
| 多表、冲突、作息、日期和长课间 | 数据实体、ConflictLayoutEngine、TimeSlotEditor、课表及周视图 | 保留原有说明并限定范围 |
| 撤回默认5步，可配置，进程内历史 | data/undo/UndoManager.kt；data/undo/LabUndoDepthContractTest.kt | 修正最近一次的旧说法，说明生命周期 |
| 单次调整、快速加课、500ms边缘翻周 | SingleOccurrenceCourseForm.kt、QuickAddCourse.kt、CourseOccurrenceFloatDragTest.kt | 补充，不包含后续拖拽修复 |
| 幽灵滑杆原值参考与表头时间对齐 | 滑杆几何/原值测试、TimeSlotEditor及节次表头设置 | 补充，不误称第二个生效区间 |
| 三种课程配色及主题动态色 | util/CourseColorUtil.kt、主题偏好与主题设置 | 修正所有模式黄金角的说法 |
| API33着色器和旧设备限制 | liquidglass/LiquidGlassEffect.kt、LiquidGlassNavigationBar.kt | 补充兼容边界，不宣称后续修复已发布 |
| Android13主题图标 | monochrome资源与图标契约测试 | 与Android12应用动态色分别说明 |
| WakeUP在线口令与第三方联网 | data/jw/WakeUpShareClient.kt、WakeUpShareProtocol.kt、ImportSheet.kt | 补充，区分离线格式兼容 |
| 原生分享文本、WakeUp JSON、ICS | ExportScreen.kt、SleepyNativeExporter、导入导出测试 | 修正WakeUp分享文本导出的旧说法 |
| 日历导出、备份恢复、可选权限 | Calendar导出实现、备份服务、Manifest与权限调用点 | 保留，不承诺双向同步或完整权限清单 |
| 五家族13种widget配置 | AndroidManifest.xml及res/xml的widget配置，Canvas/RemoteViews渲染器 | 保留，区分OriginOS桌面与原子岛提醒 |
| 提醒合并多表、厂商实况限制 | notification调度、每日议程与docs/vendors/oppo-coloros-fluid-cloud.md | 保留正式版行为，不混入后续仅当前课表变更 |
| 本机控制台可读写 | console/DebugConsoleServer.kt、handlers/DataOpsHandlers.kt、docs/agent-debug-console/AGENT-SKILL.md | 修正只读误称，保留认证、回环和修改风险 |
| 不需Sleepy账号，学校登录可能需要凭据 | 学校WebView导入与离线建表入口 | 修正无需学号的无条件表达 |
| 本地存储仍有主动联网入口 | 教务、WakeUP口令、节假日、版本更新与导出实现 | 修正不经任何服务器的绝对说法 |
| iOS与网页为独立发布范围 | 各仓库公开入口 | 删除未核实的完全同等能力承诺 |
| 公开贡献指引 | 公开CONTRIBUTING和issue模板 | 删除私有维护约定，只保留公开可执行材料 |

表中简短文件名均指 tag 内对应源码或测试，不代表实时运行认证。学校服务、厂商展示和旧设备效果仍需对应环境验证。

## 目录口径

对发布源码 `app/src/main/assets/schools.json` 按 `JwSchoolInfo` 状态归一：

| 口径 | 数量 |
| --- | ---: |
| 全部目录记录 | 4140 |
| supported | 350 |
| grad_supported | 2 |
| 已配置基准合计 | 352 |
| unverified | 2480 |
| grad_unverified | 48 |
| 未验证合计 | 2528 |
| pending | 1007 |
| grad_pending | 253 |
| 待接入合计 | 1260 |

348条省略状态字段，按兼容规则包含在supported内，不能另加。目录身份以catalogId区分。48个非空type键是快照内登记的类型数，不等于48种均经过在线验证的协议。264个拾光来源院校与43个最终sg_asset通路记录是不同口径。

## 官网消费面

官网中文FAQ使用与本仓FAQ.md相同的审核内容，英文逐节逐问翻译，生成同一份faq.json供网页、机器JSON和FAQPage使用。学校目录、首页状态数字、llms和社交卡片使用同一发布快照。下载版本、文件名、字节数和SHA-256来自对应GitHub Release资产，而不是本机构建文件或版本号猜测。

官网保留既有视觉结构；源数据中没有可靠省份证据的记录明确归为未知，不丢条目，不把收录条数宣传成全部可直连。后续版本更新应同步产品tag、源码SHA、快照、翻译和生成内容，不能只修改页脚版本号。
