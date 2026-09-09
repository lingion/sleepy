# [Feature]:more\n\n- Number: #24\n- State: open\n- Author: startime-ltk\n- Created: 2026-09-07T01:09:22Z\n- Updated: 2026-09-07T03:34:38Z\n- URL: https://github.com/lingion/sleepy/issues/24\n\n## Body\n\n### Prerequisites

- [x] I have searched existing issues and found no duplicate
- [x] I have confirmed this feature is not already in the latest release

### Problem or motivation

[Feature]: 希望添加一个功能：使同一个app可以添加不同的课表小组件到桌面上。即：实现在桌面上可以看到两个人的课表，不需要在app中切换了，在需要对比课程的时候非常有用。还有小组件中的每日课表，希望可以通过滑动的方式切换日期（类似vivo课表）。辛苦作者大大꒰ *•ɷ•* ꒱

### Proposed solution

(•‾̑⌣‾̑•)✧˖

### Alternatives considered

_No response_

### Additional context

_No response_

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-07T01:50:20Z\n\nFeature 1(多课表 widget)3 个版本内完成,改动面比较小,好做。

Feature 2(日期滑动)范围还没定,先把几个点跟你确认下,确认完我再给版本计划。

**Feature 1(多课表 widget)**

你说的"不需要在 app 中切换",我理解是这个流程:长按桌面 → 添加 Sleepy widget → 在 widget 配置页里**从 app 内已添加的所有课表里选一个绑到这个 widget 实例** → 一个 app 多个 widget 实例,各绑不同课表,对比时一目了然。

再确认两点:
1. 配置入口是「添加 widget 时弹选择页」,还是「先在 app 内选好课表 → 添加 widget 默认绑定」?
2. 绑定的课表之后能在 widget 上换吗?(比如长按 widget → 编辑 → 重新选课表)

**Feature 2(日期滑动)**

你说的"小组件中的每日课表"我理解就是**每日课程 widget**(显示当天课的那个 widget 变体)。想确认:日期左右滑动切日期这个能力,是只加在**每日课程 widget**上,还是**所有课表 widget 变体**(每日 / 周列表 / 周网格)都要支持?这个范围决定接下来的工作量,确认完我再给 Feature 2 的版本计划。\n\n### startime-ltk — 2026-09-07T03:08:23Z\n\n我个人希望可以在添加小组件的时候弹出选择页。并且可以在小组件上更换课表。另外，关于日期滑动，只在每日小组件中支持就好\n\n### lingion — 2026-09-07T03:34:38Z\n\n感谢补充。

**Feature 1** 这块我又想了想,最终还是决定单独做一个 app 内入口来做二次编辑,理由是这样:

考虑到大部分用户(包括我自己)添加小组件的时候,默认就按当前课表显示更顺畅 —— 选课表这一步可以推迟到「哪天确实想对比两个人的课表了」再做,不需要在添加那一刻就打断流程。

所以计划是: 添加小组件时保持当前行为(默认当前课表),在 app 内「我的 → 通用设置 → 小组件」里新增一项「管理桌面小组件」,点进去可以给每个已放置的 widget 实例单独换绑的课表。这个入口后续也会留作放更多小组件相关设置的位置(主题变体 override / 周次范围 / 是否显示日期等)。

**Feature 2** 日期滑动只加在每日小组件 —— 收到,这个改动范围小,等 Feature 1 落地之后我再给你一个版本计划。
