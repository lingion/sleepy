# Sleepy Agent Debug Console — 端点速查（AGENT-SKILL）

> 一致性由 `ConsoleDocContractTest`（JVM, 扫源码注册表）与
> `ConsoleAcceptanceAndroidTest`（设备, CommandRouter 真表导出）双向互锁：
> 每个注册 path+method 必须出现在本文, 反向亦然。改端点必须同批改本文。

**传输**：`adb forward tcp:3737 tcp:3737` → `curl http://127.0.0.1:3737/...`
**鉴权**：除 `/v1/hello`、`/v1/enable` 外全端点必须带头 `X-Sleepy-Token: <token>`
**口令**：`sleepy.is-my.app`（release 解锁用；debug 包无需 enable）
**信封**：成功 `{"ok":true,"data":{...}}`；失败 `{"ok":false,"error":"...","code":N}`
**包名**：debug 变体一律 `com.lingion.sleepy.debug`（装机验证记忆教训）

## 解锁流程（仅 release 包）

1. 关于页连点版本号 7 次（60s 窗口内）→ 设备绑端口（只开 hello/enable）
2. `POST /v1/enable` `{"passphrase":"sleepy.is-my.app"}` → `{"result":"OK","token":"...","expiresAt":...}`
3. 解锁态 = 进程存活期；红条常驻可一键 DEACTIVATE；能力降级：catalog 写 403 / trigger 404（类物理不存在）/ jw-mock 403
4. debug 包：端口随进程无条件在, `/v1/enable` 短路 `ALREADY_ACTIVE`

## 端点表

| 方法 | 路径 | 用途 | 请求体（JSON）| 关键响应字段 |
|---|---|---|---|---|
| POST | /v1/hello | 探活+能力清单（免 token）| 空 | `paths[], debugBuild, unlocked, active, token`（仅 debug 构建回显活 token; release 恒 null——解锁态 token 只从 /v1/enable 成功响应获取）|
| POST | /v1/enable | release 口令解锁（免 token）| `{passphrase}` | `result, token, expiresAt` |
| GET | /v1/clock | 读虚拟时钟 | — | `virtualNowEpochMs, offsetMs, realNowEpochMs` |
| POST | /v1/clock | 设虚拟时钟 | `{mode:"offset"\|"absolute"\|"reset", deltaMs?\|dayOffset?\|epochMs?}` | 同上 |
| GET | /v1/data/tables | 全课表+课程读出 | — | `tables[]` |
| POST | /v1/data/table | 表操作 | `{op:"create"\|"delete"\|"setDefault"\|"rename", ...}` | `id` / 摘要 |
| POST | /v1/data/course | 课程注入（结构化或文本通路）| `{tableId, courses:[...]}` 或 `{text}` | `inserted` |
| POST | /v1/data/timesetting | 作息 timeJson 设 | `{tableId, timeJson}` | 摘要 |
| GET | /v1/data/prefs | 读 prefs（console_ 前缀键导出排除）| — | `prefs{}` |
| POST | /v1/data/prefs | 写 prefs（仅白名单键）| `{key, value}` | 摘要 |
| POST | /v1/data/catalog | 教务目录覆盖层（debug 限定, release 403）| `{name, status?, type?, url?, clear?}` | 摘要 |
| GET | /v1/ui/tree | 语义树+View 树 dump | — | `activity, nodes[], fingerprint` |
| POST | /v1/ui/find | selector 定位 | `{testTag?\|text?\|textContains?\|className?\|role?\|clickable?[, index]}` | `nodes[]` 含 bounds/center |
| POST | /v1/ui/action | 语义手势 | `{selector?...}或{x,y}`, `action:"tap"\|"longPress"\|"doubleTap"\|"swipe"\|"drag"\|"scroll"\|"input"\|"back"...`, payload 对齐 | `diff{appeared,disappeared,focus,textChanged,activityChanged}` |
| GET | /v1/ui/screenshot | 截屏 | — | `image` = base64 PNG（长边≤1568）|
| POST | /v1/trigger | 事件触发（debug 专属, release 404）| `{type:"before_class_alarm"\|"fluid_cloud"\|"live_card"\|"notification"\|"sync"\|"import_helper"\|"restore_and_cleanup"\|"delete_non_default_tables"\|"clear_fluid_notification"\|"test_before_class"\|"schedule_before_class_now"\|"add_soonest_course", ...}` | `type, result` |
| POST | /v1/jw/mock | 注入回放规则（release 403）| `{url_pattern, respond_with:"file"\|"b64"\|"html", body, content_type?, status?, headers?}` | `url_pattern, bytes, rules_total` |
| GET | /v1/jw/mock | 规则列表 | — | `rules[]` |
| DELETE | /v1/jw/mock | 删规则/清全部 | `{url_pattern}` 或 `{all:true}` | `removed`/`cleared` |
| POST | /v1/session/reset | 会话归零 | 空 | `tables_deleted[], tables_kept, clock_reset, mock_rules_cleared, catalog_overrides_cleared` |
| GET | /v1/log | 日志双环 | query `?limit=N` | `app[], audit[]` |

## 语义寻址约定（dbgTag）

- 命名 = `屏前缀_控件语义`；多实例后缀 `#<index>` / `#<稳定id>` / `#<枚举名>`。
- 共享组件前缀派生：`<prefix>_confirm` / `_dismiss` / `_third`（DialogActionButtons）、
  `_pick` / `_pick_confirm` / `_pick_dismiss`（日期/时间字段弹窗）、
  `_mode#i`（SegmentedSwitcher 段）、`_row#i` / `_add` / `_bind_none` / `_bind#<表id>`（TimeSlotEditor）、
  `_auto_*`（SmartPeriodEditor 经 `_mode#1` 后的 Auto Tab）。
- 全库锚点速查（选择器首选）：`about_version`（连点解锁）、`settings_theme`（主题段）、
  `import_existing/import_jw/import_paste/import_manual/import_file`、`import_format#WAKEUP_SHARE|WAKEUP_JSON|ICS|CSV|HTML|PLAIN`、
  `import_parse`、`import_preview_backup/_append/_dismiss/_apply`、`import_confirm_dialog_confirm`、
  `exit_draft_confirm/_dismiss/_third`、`select_course#<i>`、`import_select_all/_confirm`、
  `jw_date_field/jw_name_field/jw_config_confirm/jw_preview_*`、`edit_table_slot_*`、`period_table_edit_slot_*`、
  `debug_banner/debug_banner_deactivate`。
- 手势区（拖拽行）注册为 `clickable=false`：find 可见, tap 不命中 = 设计语义。

## reset 判据（用户数据守恒）

只删 `console:` 前缀表（agent 经 data-ops 建表按此约定命名）+ 草稿全清 + 时钟归零 +
mock 清空 + catalog 覆盖层清空；其余课表（含触发器产物"流体云触发测试"）一律保留；
prefs 不动。

## 验收清单（SPEC §8, 装 debug 包 `com.lingion.sleepy.debug`）

自动化（ConsoleAcceptanceAndroidTest, 5 条）：
1. hello 回能力清单且含全部 21 条路由 — 自动
2. trigger add_soonest_course(delayMin=2) → 虚拟+2 分钟课入库 — 自动（DB 态；真机通知到达见下方人工）
3. clock 设未来周三 → ui/tree 的指纹与今天课程跟随 → reset 复原 — 自动（JVM 时钟面+树注册面）
4. ui/action 语义 tap `settings_theme` 系 selector → UI 状态变化 — 自动（UiOpsConsoleAndroidTest 链）
5. jw-mock 注入 fixture HTML → 拦截点回放构造正确 — 自动（JwMockConsoleAndroidTest）
6. 文档一致性 — 自动（ConsoleDocContractTest）
7. 全量测试 0 失败 + lint 0 警告 — 命令面

人工（需真机与截图, 存 `docs/agent-debug-console/evidence/`）：
- M1 = SPEC#2：注入课 2 分钟后真实收到课前通知并截图。
- M2 = SPEC#6：release 包未解锁 `nc -z` 拒连 → 连点+enable 解锁 → tap 可用 → 杀进程重启又拒连。
