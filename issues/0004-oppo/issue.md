# 适配OPPO流体云\n\n- Number: #4\n- State: closed\n- Author: chenddkkkk\n- Created: 2026-07-29T09:13:01Z\n- Updated: 2026-08-29T17:20:50Z\n- URL: https://github.com/lingion/sleepy/issues/4\n\n## Body\n\n可以将“提醒-每节课前提醒”接入OPPO的流体云提醒
\n\n## Comments\n\n### lingion — 2026-07-29T23:37:50Z\n\n收到 我今天做\n\n### lingion — 2026-07-31T07:04:23Z\n\n已完成 OPPO 流体云适配，相关改动已发布在 v1.0.24：

- 根据用户设置的“提前 N 分钟”触发课前胶囊，不使用固定或手工时间。
- 胶囊主位支持课程名称、上课时间、上课地点三选一。
- 展开通知统一显示完整课程预览：课程名称、上课时间、上课地点、任课老师。
- 进度条绑定实际提醒窗口，从提醒触发时开始动态更新，直到上课时间达到 100%。
- 左上角图标改为时间/时钟语义图标。
- 已在真实 OPPO/ColorOS 设备上用提前 20 分钟设置验证过真实 Alarm、胶囊和动态进度。

Release：
https://github.com/lingion/sleepy/releases/tag/v1.0.24

感谢反馈。

