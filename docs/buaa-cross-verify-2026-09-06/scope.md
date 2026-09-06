# Scope — 北航 (BUAA / 北京航空航天大学) iEAS 适配

> 适配日: 2026-09-06
> SOP: jw-cross-verify-sop.md v1.1
> 模式: **不开 subagent, 单线程** (用户明确指示)

## 用户原话

> "按照 SOP 适配新学校流程,不开 sub agents 单线程完成所有的工作,适配北航"

## 学校信息

- 中文名: 北京航空航天大学
- 英文: Beihang University (BUAA / 北京航空航天学院)
- 简称: 北航 / BUAA / Beihang
- 域名: jwgl.buaa.edu.cn (教务处官网)
- 已知分类: **强智 iEAS 网络版** — `jwxt.buaa.edu.cn:7001/ieas2.1/`
- type 字段: `qz_ieas`（独立 HTML parser，不复用 EAMS5）

## 现状

- `app/src/main/assets/schools.json`: **未收录** (grep 无 BUAA/beihang/北航)
- `app/src/test/resources/jw/schools.json`: **未收录** (同上)
- 提交历史 (`git log --oneline | grep -iE "北航|buaa|beihang"`): 0 命中

## 适配类型

**初次适配** (非协议升级 / 非已知 bug 修复)。

## SOP 流程进度 (预计)

1. ✅ Step 1: scope.md (本文档)
2. ✅ Step 2: GitHub 多源检索 (全量纳入)
3. ✅ Step 3: 跨仓协议提取 (findings.json)
4. ✅ Step 4: 多仓对比 + protocol-matrix.md
5. ✅ Step 5: 现有代码与 parser 阅读 (current-code-state.md)
6. ✅ Step 5.5: 致谢清单 (现有 UI 已锁 5 个直接来源)
7. ✅ Step 6: 修复设计
8. ✅ Step 7: fixture + 测试
9. ⏳ Step 8: 全测试套验证
11. ⏳ Step 9: commit + memory
10. ⏳ Step 10: SOP 自检
