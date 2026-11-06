# WakeUP 官方教务目录 6.6.2 全量吸收 — 立项 (SOP Step 1)

日期: 2026-10-28 · 分支: feat/wakeup-catalog-absorb · 基: main 85683759 (#248 已合入)

## 用户指令链(逐字)

- "你既然都把它逆向出来这个东西了，你接下来可以把它教务的东西全部继续再逆向一遍出来，扩充我现有的学校目录"
- "继续做完"
- 既往裁决继续生效: 全量吸收禁自选筛选闸(只留安全闸)；"a类直接用 开闸 b类检查 c类直接复用但是标记未实测"；"全部都开 PR"(合并仍须明示批准)。

## 本轮战役目标

1. 逆向 WakeUP 6.6.2 APK 内加密教务学校目录资产 `school_info_android_new.txt` 并解出明文 — 已完成，见"解密定案"。
2. 将官方目录全量吸收进 Sleepy 现有学校目录生成管线(scripts/data 证据层 → generate_school_catalog.py → assets/schools.json)，扩充目录并晋升可导入入口，一律标 unverified — 进行中。

## 解密定案(逆向证据链，六段)

1. 资产形态: 纯 Base64 字母表 171179 字符，mod4=3；解码 128384 字节，恰为 16 的倍数(AES 块对齐)。
2. 密钥来源: classes6.dex `utils/o000O0Oo` 的 `OooO0OO` 方法体 — `String.getBytes(UTF-8) → Base64.decode(flag 2 NO_WRAP) → Cipher("AES/CBC/PKCS5Padding") → SecretKeySpec(arr,"AES") 与 IvParameterSpec(同一 arr) → DECRYPT → String(UTF-8)`。密钥数组 @0x37ea40 = `wakeup-yzune1215`(16 字节, key==IV)。`OooO0Oo`(encrypt) 用同数组。
3. 反转步骤与混淆诱饵: 解密在补上"密文文本整体反转"一步后当场命中(8 个变体收敛同一明文)，证实运行时管线包含 StringBuilder.reverse 语义。注意: 反编译中 `kotlin.text.o0000O00.o000OoOO`(classes7.dex 0x1ebe1c) 是诱饵 — 它调用 reverse() 但返回原入参，实为 no-op；真实反转发生在调用方。先前所有不反转的解密尝试均因缺这一步而失败。
4. 完整链(实测命中): `readText → trim → 整体反转 → Base64 decode → AES-128-CBC(key==IV=wakeup-yzune1215) → Base64(含换行) → GZIP → AdapterInfo JSON`。明文 741959 字节，GZIP magic 0x1f8b 命中，8 个变体收敛同一结果。
5. 根对象: `{data: [3570 条], release:false, versionStatus:1, versionTimeLine:1763017986(=2025-11-13 UTC)}；字段 customConf/importType/minVersion/mode/name/sortKey/type/url；importType = wakeup 2268 + ziyan 1302；带 URL 3091 条。
6. 装载路径(双分支实锤, classes6.dex 982604/988085 区): SchoolListActivity 先查 `File(filesDir,"school_info_android_new.txt")`(服务端 api.wakeup.fun 下载缓存)，不存在才 `assets.open` 兜底 → readText → 反转诱饵 → OooO0OO → OooO0O0 → Gson.fromJson(AdapterInfo)。bundled 资产与 6.1.06 版同钥可解(本轮重验 6.1.06 结论对 6.6.2 完全有效)。

## 差距分析定案(官方 6.6.2 3570 条 vs 现管线)

- 名字面: 官方 3570 条全部已在 sources/excluded 登记(0 新增名)。
- 与 6.1.06 管线逐行: 3489 条逐字节相同；57 条差异全部是会话噪声(jsessionid/(S(...))/_key/ticket/state 值换代，管线存的是 REDACTED 脱敏形态)；真实结构差异仅 3 条: 山东职业学院(旧有 URL 新清空，保留不降级)、商丘工学院(旧清空新有 URL xatu_shuwei，已吸收)、广州医科大学(service 参数换 portal 域名，噪声)。
- 未晋升带 URL 行(importType=wakeup): 327 条 — 其中 262 条目录已 operational(名字在其他源晋升过)，真实目录增益 = 63 条 pending 行可晋升 + 2 条改名行(滨州医学院→山东医药大学、潍坊医学院→山东第二医科大学，经 renames 已在目录)。
- ziyan 族未晋升带 URL 行: 102 条(80 条官方 type 已可路由)，目录增益 9 条研究生行(湖南大学-研究生/南华大学-研究生等，现 grad_pending)。ziyan 族先例: 52.36 战役已把 1302 条 ziyan 行纳入 sources 并按证据晋升(1121 条 admissions)，无排除理由 → 按全量吸收裁决同样处理。
- 21 条 URL 脱敏后仍含 REDACTED 于必查参数 → 当"入口不明"处理，登记不晋升(安全闸，非筛选闸)。

## 检索矩阵(Step 2, 全量触达登记)

常备候选源逐条过(教务 SOP v1.19 Step 2):

- 源 A GitHub 学生维护仓检索: 38 候选已全量派单+致谢(2026-09-15 战役 `docs/wakeup-protocols-cross-verify-2026-09-15/candidates.json`)，本轮无新增协议族 → 触达不重派，登记引用。
- 源 B 官方闭源 APK: 用户自有 6.6.2 APK = 本轮直接证据源，逆向完成。
- 源 C WakeUp 官方服务端 api.wakeup.fun: 装载分支实锤其提供同名缓存下载；未登录凭据状态下不请求(零凭据接触纪律)。
- 源 D 拾光 shiguang_warehouse: 264 校战役已吸收(#251)，本轮无新增交集。
- 源 E LumaSchedule (Junyxor/LumaSchedule, Apache-2.0): 本轮为交叉验证 Luma 侧 WakeUp 兼容层(convert-wakeup-school-info.mjs/compat.md)而触达；结论=其只做 profile 与登录元数据迁移、不复制 WakeUp 服务端解析器，与 Sleepy 管线边界一致；不搬运其代码，致谢登记。

## 安全与合规边界

- 密钥材料 `wakeup-yzune1215` 属逆向互操作证据，只写入本 docs 分析，禁硬编码入 app 管线(用户既往定令)。
- 资产明文为事实性学校入口元数据(校名/URL/协议标签)，无凭据、无专有代码复用；License 口径沿用 "factual metadata only"。
- 全部 URL 写入前过 sanitize_url 脱敏 + catalog_ownership 域冲突闸 + audit 安全闸(管线既有)。
- 晋升一律 unverified，enableFetch=False，用户自选导入。

## 产出物清单

| 文件 | 角色 |
|------|------|
| docs/wakeup-catalog-absorb-2026-10-28/scope.md | 本文 |
| .../candidates.json | Step 2 触达登记 |
| .../findings.md | Step 3-4 发现 |
| .../protocol-matrix.md | Step 4 type×routable×证据 matrix |
| .../current-code-state.md | Step 5 现状代码阅读 |
| .../attribution-candidates.json | Step 5.5 致谢清单 |
| .../wakeup-6.6.2-school-info-decrypted.json | 解密证据(脱敏后) |
| scripts/wakeup_662_batch_promote.py | Step 6 批量晋升管线 |
| scripts/data/wakeup_school_sources.json(+records) | 证据层: 6.6.2 源登记 |
| scripts/data/school_import_admissions.json(+entries) | 证据层: 晋升决定 |
| app/src/main/assets/schools.json 等 5 输出 | Step 7 再生成 |
