# NWPU POST Parser 跨仓验证与协议适配

> 日期: 2026-10-07  
> 学校: 西北工业大学 (Northwestern Polytechnical University, NWPU)  
> 协议: NWPUPostParser (研究生 POST/HTML 表格课表协议)  
> 来源: WakeUp Schedule 逆向分析 + GitHub 开源教务项目跨仓交叉验证  

## 一、立项背景

根据 WakeUp 课程表反编译分析，`NWPUPostParser` 处理西北工业大学研究生教务系统课表：
- 采用流水清单式 HTML 表格（`sample-table-1` 锚点）
- 支持长安校区和友谊校区两种排课模式，友谊校区支持冬夏令制作息
- 节次格式采用汉字时段加序号（`上/中/下/晚`），需要基准偏移量换算为 1~13 节
- 周次支持 `第(\d+)-(\d+)周` 格式，支持单双周判定
- 课表名称自动从下拉框 `select#xq` 提取学期信息拼接生成

本轮任务依据 `jw-cross-verify-sop.md` 执行跨仓求证、独立开分支实现、落地单元测试及致谢收录。
