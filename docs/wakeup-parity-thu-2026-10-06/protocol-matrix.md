# THU 协议证据矩阵（2026-10-13 复核）

| 来源/固定提交 | 结论 | 支持 | 不支持 |
|---|---|---|---|
| Starrah/THUCourseHelper 64a543518f9007e2b5a615582bd049acf5d2ada9 | POSITIVE | 整体课表入口；setInitValue 的 C1/C2、网格解析 | 当前登录后响应、14 节表或大节映射 |
| YZune/WakeUpSchedule 5d439287a22a64b36d14c08c81bd9fc7bb9a53db | NEGATIVE | 2018 仓 README 仅述苏州大学导入 | 无被引 THUParser.java 或 LICENSE，不能称 Apache-2.0 主参考 |
| lingion/wakeup-mod 879f0d6781da19d1da9300d5eda6b0c2629b6a63 | 反编译衍生证据 | THUParser.java 的 START=[0,1,3,6,8,10,12]、END=[0,2,5,7,9,11,14] 和 14 节时间 | 现行学校作息、原发布者授权 |
| piggyham/thu_timetable 762f0bdc75fd9219dee75f3416c172d634dd1816 | INDIRECT | jxmh_out.do JSONP 日期记录、14 节概念 | 无 zhkbcx/strHTML 指纹；第 10、11、14 节时间不同 |

补充直接来源：smartThise/NextTHUxk 在固定提交 086c8c29cad824d6da7363e7e16008e6951329f7 的 src/data.js 中抓同一 ztkbSearch 端点，并从脚本文字解析 strHTML1 与 aN_M；它容忍选课候选、多学期和缺失字段，Sleepy 当前并未覆盖这些变体。heyeshuang/YAATO 在固定提交 2931561f4455d08b9e6e7c3597a16966f5465805 通过渲染后的 .mainHref/.blue_red_none 读取整体课表，是 2012 年另一条 DOM 路径，不证明当前页面脚本稳定。独立来源相互支持历史整体课表结构，但均不能替代 2026 年登录后实测。

完整固定源码判断见 evidence/qualified-candidates.json 与各 verdict-thu-*.json；人工 fixture 不代表学校实测。
