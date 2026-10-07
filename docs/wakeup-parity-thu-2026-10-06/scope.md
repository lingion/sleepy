# THU 协议 parity 立项 — v1.11 SOP Step 1

**Owner**: lingion <lingion@hrbeu.edu.cn>
**Branch**: feat/wakeup-parity-2026-10
**Started**: 2026-10-06
**Target**: 清华大学 (THU) zhjwxk.cic.tsinghua.edu.cn 教务协议 — Sleepy 端落地 JwThuparser

## 1. 立项动机

WakeUp 课程表有 THUParser.java (608 行, 已闭环), Sleepy 缺 THU 单校实现。SOP 视觉/UI 三维度已搞定 (`witc 8family` 2026-09-15 cross-verify 8 配)。本轮专项 = THU 教务协议。

## 2. 范围 (In-Scope)

- 新增 `JwThuparser` 类 (extends `JwParser`), 协议 = WakeUp THUParser 算法直译 + Step 3 跨仓印证强化的 invariant
- 新增 `JwProtocol.TYPE_THU = "thu"` + `WAKEUP_COMPAT_TYPES` 排序 + `category()` 装配
- 装配 `JwParserRegistry.FACTORIES` 路由
- Fixture: 至少 2 个真实 THU HTML (1 来自 WakeUp 自带测试样本, 1 来自 GitHub POSITIVE 仓 Starrah/THUCourseHelper)
- parser 单元测试 (week grammar + 节次↔坐标 + 类型识别 + 跨仓不变式)
- 致谢 4 处同步 (LicenseScreen.kt + 6 语 strings.xml + AboutLicenseAttributionTest.kt + attribution-candidates.json)
- Step 5.5 attribution 漏斗: 8 仓 (POSITIVE=1, INDIRECT=4, NULL_EVIDENCE=2, NEGATIVE=1) 全列

## 3. 范围 (Out-of-Scope)

- **schedule-data.netlify.app/{semester}.json 外部 JSON 路径**: 单源独证 (1/8 = 仅 WakeUp), 跨仓印证缺失 → **Sleepy 落地保留须自建**, 本轮仅硬编码 14 节作息表 (4/8 仓 cross-verify)
- 校历 fetch (semester start/end dates) — piggyham 独证, 不复用, Sleepy 用本地学期配置
- THU 评教 endpoint (xkBks.xgpg_xspjyxkt.do) — huangkaka NULL_EVIDENCE, 仅 1 个 p_xnxq 字段命中, 不纳入
- 实验课/评教/课表全黄标记: huangkaka 是 NULL_EVIDENCE (指纹命中但 endpoint 语义错), 不进 THU 指纹字典

## 4. 跨仓印证 (SOP v1.11 Step 2 + Step 3)

- 1005 候选 → 8 POSITIVE + 40 INDIRECT + 14 NULL_EVIDENCE + 943 NEGATIVE (build_verdicts.py)
- Step 3 subagent pass: 8 POSITIVE 仓全部派单 verdict 化 (findings.json + protocol-matrix.md)
- 核心不变式: HTML 协议六件套 + 周次文字字典 + 清华 14 节作息表 = 跨仓共识, schedule-data.netlify.app = 单源独证
- 致谢 8 仓 (含 NULL_EVIDENCE/NEGATIVE, SOP §5.5 触达即致谢)

## 5. 验收标准 (Sprint Contract)

1. `./gradlew test` 全绿, 新增 parser 单元测试 ≥ 6 个 (含跨仓 regex 不变式测试)
2. parser 至少处理 5 类样本: 节次↔坐标映射 + 全周/前八周/后八周/单周/双周
3. 致谢 4 处同步 + attribution-candidates.json 含 8 仓
4. git commit 走 commit-sop v1.2 (作者 lingion@hrbeu.edu.cn, 无 Co-Authored-By trailer, 无 Fixes/Closes 关键词)
5. memory 写 v1 摘要 + 归档 docs/

## 6. 风险与缓解

- **GPL 传染**: Starrah MIT ✅, piggyham 无 LICENSE ⚠ (致谢按无 LICENSE 标), xiangwentao666 AGPL-3.0 🚫 不复用代码仅参考思路, huangkaka MIT ✅
- **THU 协议路径稳定期**: 2020-2026 同协议 (7 年), 不需担心 endpoint 切换
- **跨仓 regex 不变式**: SOP 铁律 3, 必须 char-by-char 校验 (Java regex = JS regex literal), 已在 JwBjtuParserTest §F 落地, 本次复用同模式

## 7. 关联决策
- `[SCHEDULED]` THU ECUPL 同流程 (P2 字节 mode), 等 THU commit 后立即 spawn
- `[SCHEDULED]` THU NWPU_POST (P2) + 4 个 smali body dump (kingo_zx/kingo_super/scau/ecupl)_纯 smali 逆向
- `[SCHEDULED]` P1 urp_new/jz/zf/shuwei 跨仓验证补全
