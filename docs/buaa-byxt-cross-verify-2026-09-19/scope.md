# BUAA byxt.buaa.edu.cn 适配 — SOP scope

日期: 2026-09-19
适配类型: 协议升级 — 老 jwxt.buaa.edu.cn (强智 iEAS) 保留 + 新增 byxt.buaa.edu.cn (金智 Wisedu jwapp homeapp)
用户原话: "反馈一下 BUAA 有 bug, 给出的 jwxt.buaa.edu.cn 疑似是旧版本的系统, 新版本是 byxt.buaa.edu.cn"

## 现状 (current-code-state)
- jwxt.buaa.edu.cn:7001/ieas2.1 强智 iEAS 网络版 (TYPE_QZ_IEAS)
  - parser: JwQzIeasParser (HTML 表格)
  - fetch JS: JwQzIeas 走 outerHTML 路径 (无独立 FETCH_JS, type=FetchKind.QZ_IEAS 时仅影响 pathSegment)
  - schools.json: type=qz_ieas, url=jwxt.buaa.edu.cn:7001/ieas2.1
  - 5 仓 cross-verified (2026-09-06)

## 目标
新增 byxt.buaa.edu.cn 入口 — 用户进入新教务时不能走 iEAS 解析器 (协议族完全不同).
9+ 仓交叉印证 byxt = 金智 Wisedu jwapp homeapp:
  - fontlos/buaa-api (Rust, MIT)
  - CoolwindHF/buaa2wakeup (Python, MIT)
  - awesome-buaa-cs/buaa-curriculum (无 license)
  - BUAASubnet/UBAA (Kotlin, MIT, 245★)
  - Krignd/KAgenda (Kotlin, 无 license)
  - cantBeFoundGroup/OpenBUAA (Python, 无 license)
  - Yiki21/iclass_buaa_tui (Rust, GPL-3.0, 13★)
  - el-ev/BUAA-ics-gen (Python, MIT)
  - Lidozs55/BUAAer-Smart-Schedule-on-electron (Vue, 无 license)
  - WhXcjm/buaa-byxt-aischedule (JS, GPL-3.0)
  - MeanZhang/buaa-ai-schedule (archived, MIT)
  - Alyssumira/BUAA-Schedule (Kotlin, MIT)
  - lyy1119/BuaaScheduleRender (Go, MIT)
  - zjafb/BUAA-Hangzhou-Schedule (MIT, UBAA fork)

## 决策依据
- byxt 协议 = 金智 jwapp /homeapp/ family (与 HEU 现有 TYPE_WISEDU 协议层同源但 endpoint 形态不同)
- 不能直接复用 TYPE_WISEDU (现有 WISEDU_FETCH_JS 走 wdkb/xskcb.do, 拿不到 arrangedList)
- 走 TYPE_NEU 已实现的"金智新版 homeapp"通路 (NEU fetch JS + JwNeuParser) — 协议层同族
  - 端点形态: NEU 多 getMyScheduledCampus.do, byxt 9/21 仓均 campusCode=""
  - 字段形态: NEU titleDetail[]; byxt 9/21 仓均同时含 weeksAndTeachers/cellDetail (字段更全)
- 落地: TYPE_NEU 兼容 byxt (协议同源, 字段形态多源实锤一致), 仅 fetch JS 端 campusCode='' 兼容

## 风险与边界
- 新增 url → byxt.buaa.edu.cn, 旧 jwxt.buaa.edu.cn 保留 (issue 报"疑似旧版"≠ 强制废弃)
- TYPE_NEU parser 已被 NEU 采采集包实锤; byxt 复用作 NEU 系分支属于"同协议族跨学校复用", 与 SOP §1 复用增量形态一致
- 真实采集包缺失: fixture 仍为合成 (按 NEU 已合采集包形态), 不冒充现场采集
- commit 不写 Co-Authored-By Claude (全局)
- commit body 不写 Fixes:/Closes:/Resolves: (push 自动关帖防御)