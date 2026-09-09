# [Feature]: 适配不同时长的课长，课程节数优化\n\n- Number: #23\n- State: open\n- Author: dark-knifes\n- Created: 2026-09-06T21:24:06Z\n- Updated: 2026-09-07T00:13:29Z\n- URL: https://github.com/lingion/sleepy/issues/23\n\n## Body\n\n### Prerequisites

- [x] I have searched existing issues and found no duplicate
- [x] I have confirmed this feature is not already in the latest release

### Problem or motivation

我的学校通常是40分钟一节课，但是偏偏中午吃饭时也算一节30分钟的课，这节课也在课程安排时间的节数中（）。例如这样的情况也存在，同时我认为可以添加第0节课第n+1节课这类脱离原有按节数运行的课程。方便临时加入如早晚自习等临时课程的需求

### Proposed solution

增加大小课时以及添加可自定义或插入的课节数，即现在不影响原课程运行情况下插入课程。（另一种实现方式：将按节数运行的课程表转化成按填入的时间表推算出的按时间运行的，再进行添加或插入）

### Alternatives considered

_No response_

### Additional context

_No response_

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-07T00:13:29Z\n\n感谢你把场景写得这么具体：40 分钟一节课，中午有 30 分钟的课，还需要第 0 节和第 n+1 节来放早晚自习这类临时课程。

我会按这个方向处理，争取在接下来的两个版本内把不同时长和自定义节次的问题修出来。
