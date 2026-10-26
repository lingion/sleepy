# Scope — WakeUp 八族导入协议跨仓交叉验证

日期: 2026-09-15
分支: feat/wakeup-protocol-coverage (HEAD 0955119c)
适配类型: 适配补齐(WakeUp 6.1.80 兼容层) + 交叉验证(本轮)

## 用户原话
「所有新加的协议都按照新需要适配的 SOP,即新协议适配 SOP,进行交叉验证;同时找开源项目核查,最终验证通过后才能合并」

## 范围: 8 个协议族 (JwWakeUpCompatParsers.kt)
| 协议族 | JwParser type | WakeUp mode | 本轮实现 | 参照 |
|--------|--------------|-------------|---------|------|
| kingo_new | TYPE_KINGO | 11 (TaskActivity grid) | parseKingoTaskActivity | o0000OO0.java |
| jz | TYPE_JZ | 11/13/24 (JSON+HTML) | parseJzJson/parseJzHtml/parseKingoInfo | o00oO0o.java |
| south_soft | TYPE_SOUTH_SOFT | (已有) | 既有 | — |
| login_chaoxing | TYPE_CHAOXING | kckbData JSON | 既有 | — |
| shuwei_json | TYPE_SHUWEI | 16 positional grid | 既有+核实 | o000O0Oo.java |
| suda_post | TYPE_SUDA | 3 DataGrid1 | parseSuda | o0OO00O.java |
| cumtb | TYPE_CUMTB | 表格 join | 既有 | — |
| xju_post | TYPE_XJU | 1 dgData 表格 | parseXju | OooOOO.java |

## 检索目标
- 8 族各自找 GitHub 学生维护仓库(WakeUp 同款协议导入实现)
- 候选全量纳入, 每仓一份 schema 化 verdict
- 唯一否决: archived + >10 年

## 质疑点(需跨仓验证的具体断言)
1. kingo_new: index = day*unitCount+node 坐标公式, node 13→10 / <9→+1 / else+2 映射
2. jz dsz 反转映射 (dsz==1→单, dsz==2→每周, else→双)
3. jz mode 13 KingoInfo 数组结构 (week1..week7 行)
4. shuwei 16: day=arrayIndex, node=unitCount+1
5. suda DataGrid1 行列语义 + rowspan
6. xju ctl00_contentParent_dgData + 星期日序判定
7. cumtb / south_soft / chaoxing: 既有实现是否与开源一致
