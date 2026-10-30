# 南开大学 (NKU) eamis 课表导入适配 — 立项范围

- 日期: 2026-10-27
- 学校: 南开大学 (Nankai University), catalogId `N_南开大学`, 入口 `https://eamis.nankai.edu.cn/`
- 触发来源: issue #236 (报告人 zzask77)。症状: 课表无法导入、识别不到课表容器、网页空白。报告人称「南开已使用树莓教务、不再使用金智教务」——**该断言被 38 仓检索证伪**（全部仓库零「树莓/raspberry/urmp」命中,详见 findings.json 与 protocol-matrix.md）。
- 登记现状（立项时 main）: `type=classic_eams`, 无显式 status（默认 supported）, **`enableFetch` 未开**, url 根路径。目录补 URL 战役只验到根域存活, 教务真实入口当时未实测。
- 适配类型: 二次适配——协议族 classic_eams 已在库内（NBCC 战役 PR#232 落地登录取帧通路）, 南开缺「根 URL 是 148 字节门户壳、无课表锚点」的自动动线。

## 已确证根因（无凭据证据链, 形态B 零凭据红线全程生效）

1. 供应商 = 金智 classic_eams: `courseTableForStd!courseTable.action` + `bg.form.addInput` + `new TaskActivity(...)` + `var unitCount` + `#manualArrangeCourseTable` 空壳表——三源共识（bucket-A/B/D 多仓独立协议串一致 + jxy18100/nku- 活体抓取 + 我方 schools.json 既登记）。
2. 根 URL `https://eamis.nankai.edu.cn/` 返回 148 字节空壳（bucket-D 无凭据探测）, 零 HTML 指纹 → detectProtocolFromUrl 判 null 正确, 目录 type 权威。
3. 课表入口 = 门户内链 `/eams/courseTableForStd.action`（bucket-A zawnpn/NKU-EAMIS 端点串、bucket-D jxy18100 scrape_now.py 直接 navigate 该 URL 即得带 addInput+semesterCalendar 的表单页）。
4. 登录 = IAM 飞连统一身份认证（`iam.nankai.edu.cn`, `password_account_input` 指纹, bucket-E 多仓）; 登录态失效回跳 IAM 页。
5. 当前学期号 4384 = jxy18100 scrape_now.py 实测真实值（2025-2026 学期）。

## 本轮任务

- Step 2 检索矩阵全量跑: 38 候选（cand_final.tsv 唯一真源; cand_meta.tsv 误入的本项目 fork `starlake1009/sleepy` 已剔除并记档）。
- 每个候选 schema 化 verdict, findings.json 与 candidates 1:1。
- enableFetch 开闸 + 南开自动动线（JS 内 GET 入口页→提取 scope→走既有 classic_eams 提取链）。
- 锚点扩充 manualArrangeCourseTable、IAM 登录指纹、测试 fixture 三件套、契约测试跨语言 invariant。
- Step 5.5 致谢前置（已完成后才动代码）。

## 完成门槛（自检清单锚点）

- [x] candidates.json 38 条全量纳入（唯一否决 archived∧>10y: 本轮 0 条被否; john8654john/nankai-schedule 为 NEGATIVE 同名误命中仍记档致谢）
- [x] findings.json 38 与 candidates 1:1（POSITIVE 24 / INDIRECT 10 / NEGATIVE 1 / NULL_EVIDENCE 3）
- [x] protocol-matrix.md ≥1 form（南开 vs NBCC vs 同族）
- [x] 致谢 38 = 候选数, LicenseScreen + 6 语 strings + AboutLicenseAttributionTest
- [x] 跨语言 invariant: CLASSIC_EAMS_NANKAI_HOST_RE ≡ JS literal 逐字符（NankaiClassicEamsWebViewContractTest）
- [x] fixture N form × 反例（entry / course-table / IAM login / 148B 根壳反例）
- [x] 全测试套: 3095 tests, 1 失败 = widget 计时 flaky（ReminderReschedulerTest, 单跑绿, 与 jw 零交集）; lintDebug exit 0 无新增 finding
- [ ] commit 身份 lingion@hrbeu.edu.cn, 无 Claude 尾注, ref #236 only
- [x] SOP 复用即修订 v1.15→v1.16（见 sop-revision-notes.md）

## 红线（全部生效）

- 形态B 零凭据: FETCH JS 永不触碰 password 字段; 仓库凭据中转端点（a4001234567/NKU_Project 的 82.157.171.33/cgi-bin/login_new、SunriseFox 反许可代码）一律不复用, 仅引用协议事实。
- eamis.nankai.edu.cn 本机不可达（校园网侧限制）, 全程零活体登录探测; 证据 = 公开代码逆向 + 无凭据端点形态。
- URL 裁决闸: 已闭（2026-10-27 当日）——zzask77 真机回传「可以正常导入」(issue #236 comment 6435933798), 南开 status 显式钉 supported, pinned test 加断言（详见 reporter-dump-analysis.md 裁决闸节）。
