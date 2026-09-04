# 985 B 档 Wave 3 — 7 校独立 Workflow + 审计 Workflow

## Context

Wave 2 审计发现原调研严重失实(采纳 3 校全部降级,漏检厦大关键仓)。Wave 3 重做。

## 设计

### Wave 3-Research: 7 校独立调研 Workflow
每校一个独立 Workflow,内含:
- **Implementer subagent**: 严格执行更严搜索命令集,出报告
- **Reviewer subagent**: 不信报告,亲自 curl + gh search code 多 query 核验

两个 subagent 互相独立,只通过文件交互(报告路径),不共享 context。

### Wave 3-Audit: 独立审计 Workflow
7 校调研产物出齐后,启动:
- **审计 Workflow**: 每校 3 角度 (V1 端点 / V2 代码 / V3 license) verifier,共 21 subagent
- 这次提前于调研 Workflow 启动约束:`gh search code` 必须跑多 query(含教务域全字符串)

## Global Constraints (硬约束,沿用 wave 2)

- 调研 subagent 必须跑这些查询(不能漏):
  - `gh search code "<校域名>"` (e.g. "jwxt.nwpu.edu.cn")
  - `gh search code "<校教务 path>"` (e.g. "/eams/stdElectCourse")
  - `gh search repos "<校全称> 课表"`<=10 + `gh search repos "<校英文> schedule"`<=10
- **亲自 curl 所有候选端点** — 403/412/timeout 必须如实记录
- 必须 clone 上游仓验证关键代码,**不能只看 README 引用**
- 必须读 LICENSE 文件原文,**不能只看 README 声明**

## 7 校名单
1. 哈尔滨工业大学 (HIT)
2. 西北工业大学 (NWPU)
3. 南开大学 (Nankai)
4. 北京航空航天大学 (BUAA)
5. 厦门大学 (XMU)
6. 大连理工大学 (DLUT)
7. 华东师范大学 (ECNU)
