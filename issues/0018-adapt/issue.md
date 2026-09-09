# [Adapt]: 求适配中国科学院大学\n\n- Number: #18\n- State: open\n- Author: damifan3\n- Created: 2026-09-06T14:41:33Z\n- Updated: 2026-09-07T14:41:33Z\n- URL: https://github.com/lingion/sleepy/issues/18\n\n## Body\n\n### School / 学校

中国科学院大学

### JW system URL / 教务系统网址

https://xkgo.ucas.ac.cn:3000/course/personSchedule

### Access / 访问环境

- [x] I can access the JW system from off campus (校内网/VPN 均可)

### Collected data / 采集文件

[22:39:16] 打包完成: 请求入库 34 · 响应体获取失败 0 · 资源重取 37 · 接口重放入包 32 · 参数 {} · 错误 153 警告 17

[sleepy-adapt-0906-223916.zip](https://github.com/user-attachments/files/31881870/sleepy-adapt-0906-223916.zip)

### Anything else / 补充说明

_No response_
\n\n## Comments\n\n### lingion — 2026-09-07T00:13:28Z\n\n收到中科院大学的适配请求。你提供的教务地址是 `xkgo.ucas.ac.cn:3000/course/personSchedule`，采集包也收到了。

你提供的课程里没有详细周次，这不是你的问题，是我这边的抓取工具没有把这部分数据正确取出来。我正在用其他公开项目做交叉验证，继续适配中科院大学的教务系统。\n\n### damifan3 — 2026-09-07T14:41:03Z\n\n> 收到中科院大学的适配请求。你提供的教务地址是 `xkgo.ucas.ac.cn:3000/course/personSchedule`，采集包也收到了。
> 
> 你提供的课程里没有详细周次，这不是你的问题，是我这边的抓取工具没有把这部分数据正确取出来。我正在用其他公开项目做交叉验证，继续适配中科院大学的教务系统。

原html中，点击课表中的每个课程，进去就是详细周次。
似乎你的打包工具会将整个网页递归打包，按理说应该有每个课程的详细周次
