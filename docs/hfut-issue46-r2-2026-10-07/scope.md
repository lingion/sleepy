# HFUT Issue #46 R2 — cysxun 课表解析失败

> 来源: <https://github.com/lingion/sleepy/issues/46#issuecomment-6031772583>
> 状态: 诊断完成, 待 QQ 群跟进结果确认复现路径与修法方向
> 适配类型: 已知学校适配修 bug (HFUT 已收录)
> 立项: 2026-10-07 · SOP: jw-cross-verify-sop v1.11

## 1. 用户原话

> "[https://github.com/lingion/sleepy/issues/46#issuecomment-6031772583](https://github.com/lingion/sleepy/issues/46#issuecomment-6031772583) 完成这个试配"

`comment databaseId=6031772583` by `cysxun` at `2026-10-07T05:42:28Z`:

> "[sleepy-jw-dump-20261007-133854.zip](https://github.com/user-attachments/files/33139688/sleepy-jw-dump-20261007-133854.zip) 课表解析失败, 并附上由 app 导出的教务日志"

lingion 自身评论 #6031972480 (16 分钟后) 建议加 QQ 群私下跟进。本文档立项时 lingion 已与 cysxun 在 QQ 群对接, 具体复现步骤/语义尚未同步到本目录。

## 2. commit 历史 (HFUT 相关, 简表)

| SHA | 标题 | 含义 |
|---|---|---|
| `963d929f` | fix(jw): 合工大 EAMS5 节次改服务端布局查表 — 修宣城 12 节 15% 错位 (ref #46) | v1.0.56 单次抓取链节次修复, 主轴本次诊断的"对照组" |
| `034525cf` | fix(jw): 合工大 EAMS5 采集补全四段链 — 修 datum HTTP 500 (ref #25) | v1.0.56 补全采集链 4 段, 防止 datum 缺链 500 |

## 3. 现有 parser / 适配入口

- Parser: `app/src/main/java/com/lingion/sleepy/data/jw/JwEams5Parser.kt`
- 入口: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt` (EAMS5 JS 链, 含 v1.0.56 引入的 3.5 段 POST timetable-layout)
- Path: HFUT EAMS5, base = `https://jxglstu.hfut.edu.cn/eams5-student/`
- 4 段链语义: ①GET get-data → ②POST timetable-layout (3.5 段) → ③POST datum → ④(渲染 + week-indices-digest ×N 用于服务端布局查表)

## 4. cysxun dump 摘要

- 文件: `sleepy-jw-dump-20261007-133854.zip` (237112 bytes, unzip -t OK, 中央目录完整)
- 来源: `gh.qdp.qzz.io/user-attachments/files/33139688/...` (SOP v1.11 §4 镜像格式)
- 解压: `dump/` (本目录下)
- appVersion: **1.0.59 (10059)** (v1.0.56/57/58/59 链路最新发布版)
- session: 20261007-133854
- cookies: `SESSION=9b50ff31-d746-496e-88c8-0d06dd6e6b7b; SRVID=s115` (有效登录态)
- console: 0 messages (WebView JS 未报错)
- storage: sessionStorage={} localStorage={}
- dom-inventory: (no document), total=0
- 2-inline/links: empty
- summary 状态: status=UNKNOWN, courseCount=0, selectedFramePath=`<none>`, matchedAnchors=(空), retryCount=0
- diagnosticHint: "抓取失败: Error: POST schedule-table/datum 失败 HTTP 500 (lessonIds:0)"

## 5. 抓取链时序 (manifest.json 70 entries, 关键段)

| idx | method | URL | status | request body 摘要 |
|---|---|---|---|---|
| 0 | GET | /get-data?bizTypeId=23&**semesterId=354**&dataId=178619 | 200 | — |
| 1 | POST | /timetable-layout | 200 | `{timeTableLayoutId:122}` |
| 2 | POST | /datum | **200** | `lessonIds:[13 个]` ✅ 完整课表 |
| 3-15 | GET | /static/.../templates/* | 200 | — |
| 16-31 | POST | /week-indices-digest ×16 | 200 | weekIndicesGroupId: x,y |
| 32 | POST | /datum | **200** | `lessonIds:[13 个]` ✅ |
| 33-49 | POST | /week-indices-digest ×17 | 200 | — |
| 50-65 | POST | /week-indices-digest ×16 | 200 | — |
| 66 | GET | /info/178619 | 200 | — |
| 67 | GET | /info/178619 | 200 | — |
| 68 | GET | /get-data?bizTypeId=23&dataId=178619 **(缺 semesterId)** | **500** | — |
| 69 | POST | /datum | **500** | `lessonIds:[]` ❌ |

**entry 68 响应体 (500) 摘要**:

> `java.lang.NullPointerException` at `org.springframework.boot.web.support.ErrorPageFilter.handleException(ErrorPageFilter.java:170)`

**entry 68 与 entry 0 关键差异**: `semesterId=354` 在 entry 0 出现, 在 entry 68 **丢失**。

## 6. 根因诊断 (working hypothesis, 待 QQ 群验证)

**二次启动抓取链时 lose `semesterId=354` 参数**:

1. entry 66-67 的 GET /info/178619 触发 WebView 重启 (用户切换学期/手动刷新/学期下拉选择 — 具体触发点待 QQ 群跟进)
2. WebView 重启后 JS 链再跑一遍采集, 但本次拼接 `/get-data` URL 时 **未注入 `semesterId=354`**
3. 服务端 GET /get-data 返回 500 (Spring ErrorPageFilter NPE, 缺学期上下文)
4. JS 拿到 500 后 lessonIds=空数组, 喂给 POST /datum → 500
5. sleep 状态机把整体标记 status=UNKNOWN courseCount=0, 课程表解析失败

### 与 v1.0.56 修复的关系

- v1.0.56 (963d929f) 修的是"硬编码节次表 → 服务端布局查表", 单次抓取链完美 (122/122 行对齐)
- v1.0.56 引入 3.5 段 timetable-layout + 多次 week-indices-digest + 多次 datum 重查机制
- 本次新 bug 是 **二次启动 lose semesterId**, 不是节次问题, 不是登录失效 (cookie SESSION 有效)

### 反向验证 (counter-check)

- entry 0, 1, 2 首次抓取 100% 成功 → v1.0.56 修复链本身工作正常
- entry 32, 49 三次 datum 调用都带 13 个 lessonIds → JS 在"非二次启动"路径下对 lessonIds 处理正确
- entry 68/69 是 **唯一的 lose-semesterId 场景**, 触发条件 = WebView 二次启动
- 反向: 若 lose semesterId 是常态 bug, entry 0 早就该 500 — 但 entry 0 正常 → 二次启动路径特有

## 7. 待 QQ 群确认 (lingion 已与 cysxun 私下对接)

1. **复现路径**: 是用户切换学期 / WebView reload / 多次导入触发 entry 66-69 的二次启动? 建议 cysxun 提供"成功一次后再次进入"的 dump 对照。
2. **修法方向**:
   - **A. JS 链持久化 semesterId**: 在 WebView sessionStorage 里缓存学期参数, 重启时优先取缓存 (低侵入, 但 WebView storage 在 Android 上跨进程易丢)
   - **B. JS 链读学期来源加固**: 在 JwWebViewLoginScreen.kt 里确保 semesterId 来源唯一 (URL 参数 + 隐藏字段 + select 选项), 缺一即提示用户选择学期, 不盲目发请求 (中侵入, 但符合"缺学期 → 让用户选"语义)
   - **C. 捕获 entry 68 类 500 留 lessonIds**: JS 拿到 500 后保留上一次的 lessonIds (与 entry 0-2 的 13 个 ID) 作为兜底重试 (高侵入, 引入缓存一致性风险)
3. **回归范围**: v1.0.56 的 122/122 行对齐测试 (EAMS5/HFUT 102 测试) 必须全绿, 新增 "二次启动 + 缺 semesterId 兜底" 测试

## 8. 致谢 (Step 5.5)

本轮触达:
- dump 来源 = cysxun 个人反馈 (issue #46 comment #6031772583), **非仓库**, 致谢走"问题反馈者"列, 不入 LicenseScreen 致谢清单 (SOP v1.5 "闭源参考 0 致谢边界" 对偶)
- 仓库 0 个新触达 (本轮不需要跨仓检索, v1.0.56 已有 11 仓共识)
- 沿用 v1.0.56 致谢清单 (`feat(about): issue #46 跨仓验证致谢 11 仓 + 六件套 docs (ref #46)` 3b64f085)

## 9. 待办

- [x] Step 1 scope.md (本文档)
- [ ] Step 5 当前代码状态 (JwWebViewLoginScreen.kt JS 链 semesterId 来源分析)
- [ ] Step 6 修复设计 (等用户拍板 A/B/C)
- [ ] Step 7 fixture + 回归测试 (entry 68 形态: 缺 semesterId → 500 → 兜底)
- [ ] Step 8 全测试套验证 (EAMS5/HFUT 102 测试基线 + 新增 1-2 条)
- [ ] Step 9 commit + docs 归档
- [ ] (并行) lingion 的 feat/wakeup-parity-2026-10 dirty 处置 (与本任务无关, 待用户拍板)

## 10. 不可恢复的事实层 (供 commit body 与 release notes 同源)

- zip md5: `822352afb9a20210f4167a673c20a804` (size=237112 bytes, unzip -t OK, 中央目录完整)
- dump md5: 一致 (find . -type f | md5 -r diff ✓)
- 抓取链 entry 数: 70
- 成功 datum 次数: 3 (entry 2, 32, 49, 均 lessonIds=13)
- 失败 datum 次数: 1 (entry 69, lessonIds=0)
- 500 端点: GET /get-data (entry 68), POST /datum (entry 69)
- 服务端 NPE 位置: org.springframework.boot.web.support.ErrorPageFilter.handleException:170
