# 江西中医药大学 `cf_new` 协议核验矩阵

日期：2026-09-19

## 证据边界

PR #50 的代码注释声称存在 2026-09 采集包和真实响应锚点，但当前 checkout、PR 文件和测试资源中没有该采集包或原始响应。本表把代码实现与脱敏合成 fixture 作为实现审查材料，不把它们升级为现场协议证据。

## 形态矩阵

| 形态 | 请求 | envelope / bucket | 行字段 | 证据状态 |
|---|---|---|---|---|
| 单请求全量 | `POST /new/student/xsgrkb/getCalendarWeekDatas`，`zc=''` | `sleepyCfNtss=true`；`weeks:[{week:0,rows:[...]}]`；另带 `periods` | `kcbh,kcmc,teaxms,jxcdmc,xq,ps,pe,qssj,jssj,zc,jxbmc,bapjxcd` | PR 代码声称来自真实响应；原始包缺失，待现场复核 |
| 逐周回退 | 同端点，`zc=1..22`，带请求周 `d1/d2` | `weeks:[{week:n,rows:[...]}]`；行 `zc` 通常为请求周 | 同上；缺失 `zc` 时 parser 回退 bucket `week` | 代码路径可审；真实部署行为待现场复核 |
| 节次时间 | `POST /new/xlxx/getDatesOfWeek` 取得第一周周一；页面 `businessHours` 提供节次 | envelope `startDate`；`periods:[{node,start,end}]` | 行 `qssj/jssj` 与 `periods` 时间匹配以反推 `ps/pe` | 代码路径可审；原始页面/响应缺失 |

## Parser 契约

- 课程名为空、星期无法解析、节次无法由字段或时间映射确定、周次无法从 `zc` 或 bucket 得到的行被丢弃。
- `ps/pe` 允许零填充和空串；空串依次尝试 `periods` 和同响应中带节次的行建立的时间锚点。
- `zc` 支持单值、逗号列表、区间及混合区间；多周结果交给既有 `weekIntList2WeekBeanList` 无损折叠。
- 聚合键为 `kcbh|kcmc|teaxms|jxcdmc|xq|ps|pe|jxbmc`，同课跨周行合并周次集合。
- `bapjxcd=1` 且场地为空时展示 `不用场地`。

## 结论

本轮可确认的是：抓取脚本与 Kotlin parser 的职责边界、两种抓取形态的兼容路径，以及合成 fixture 对这些契约的覆盖。不能确认的是：真实端点当前仍返回同样字段、`zc=''` 当前确实返回整学期、以及代码注释所称现场采集时间。接入前仍需补充脱敏真实采集包并重跑全量验证。
