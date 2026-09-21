# cf_new / 新青果 NTSS fixture

本目录是 `JwCfNewParser` 的脱敏合成 fixture，不是江西中医药大学真实登录态采集包。PR #50 未附原始响应；现场响应证据缺口记录在 `docs/jxutcm-cross-verify-2026-09-19/scope.md`。

## 来源与边界

- 协议入口：江西中医药大学 `jiaowu.jxutcm.edu.cn` 的 `/new/student/xsgrkb`。
- 字段形状：依据 PR #50 中 `getCalendarWeekDatas`、`businessHours` 和 parser KDoc 的字段契约手写脱敏样本。
- 不包含真实学号、cookie、VIEWSTATE、账号或可回溯个人信息。

## 覆盖场景

`ntss_mixed.synthetic.json` 同时覆盖：

- `ps`/`pe` 直接存在，且 `01` 零填充；
- `ps`/`pe` 为空，通过 `periods` 的起止时间反推；
- `zc` 单值、区间、混合区间；
- `week=4` bucket 且行 `zc` 为空时，回退请求周号；
- 同一课程跨周聚合；
- `bapjxcd=1` 且教室为空时映射为“​​不用场地”；
- 空课程名、非法星期、无时间映射负例。

后续取得真实采集包后，应新增现场 fixture 并保留本合成样本作为边界回归，不以本文件替代真实证据。
