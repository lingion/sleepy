# SWJTU 适配前现状

生成日期：2026-09-15。此文件记录动代码前的基线。

## 协议登记与计数

- `JwProtocol.kt` 当前有 31 个 `TYPE_*` 常量；`ALL_TYPES`、`displayName()`、`category()` 的 when/集合均需同步，新增 YETHAN 预计为第 32 种。
- `schools.json` 当前 337 所学校、29 个已声明协议类型；已有“西南交通大学希望学院”是 `urp`，不是本次本部 `yhxt.swjtu.edu.cn`，不能复用或改写。
- 当前 fixture 目录没有 `swjtu` 或 `yethan` 条目。

## WebView 入口

`JwWebViewLoginScreen.kt` 的 fetch 分派按协议 type 逐个 if 分支调用 `evaluateFetchWithTimeout`，已有 `CQU_FETCH_JS` 作为“登录后 fetch JSON，再回调 bridge”的参考形态。YETHAN 应增加独立分支和 `YETHAN_FETCH_JS`，不要把 YETHAN URL 塞入旧 `urp`/`zf` 分支。

## 目标协议（仅作为设计输入）

- 登录：CAS 回跳后取得 `ytoken`；请求头 `ytoken: <JWT>`。
- 课表：GET `/yethan/common/course-schedule/student-course-schedule`，无业务参数。
- 配置：GET `/yethan/public/sys/config/web`，用于学期/开学日期/节次配置。
- 响应 envelope：`code == "00000"` 成功；`401`、`A0230`、`A0422` 视为失效。
- `classTime1..40` 与 `classPlace1..40` 成对读取；周次文本须支持单周、连续周和顿号枚举/带洞集合。

## 测试基线

- 既有协议测试集中于 `app/src/test/java/com/lingion/sleepy/data/jw/`，包括 `JwProtocolAllTypesTest`、`JwProtocolFixtureMatrixTest`、各 parser 测试以及 WebView contract tests。
- 致谢契约位于 `AboutLicenseAttributionTest.kt`；当前覆盖 6 个发布 locale：`values`、`values-zh-rCN`、`values-zh-rTW`、`values-en`、`values-ja`、`values-es`。
- 当前工作树已有用户此前未提交的 release/issue 变更；本轮调研只新增本目录材料，未修改那些既有文件。
