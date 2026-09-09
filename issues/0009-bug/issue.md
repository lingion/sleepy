# [Bug]: 可以设置超过最大节次的课程\n\n- Number: #9\n- State: closed\n- Author: jim139129\n- Created: 2026-09-01T08:21:38Z\n- Updated: 2026-09-01T15:35:03Z\n- URL: https://github.com/lingion/sleepy/issues/9\n\n## Body\n\n### Prerequisites

- [x] I am using the latest version of Sleepy
- [x] I have searched existing issues and found no duplicate
- [x] I will attach logs, screenshots, or a sample schedule file if helpful

### Current behavior

以一天12节课为例，新建课程可以设置从12节开始连上8节，显示为12-19节

### Expected behavior

连上几节的范围 不应该是1-8，而是课表总节数-开始节数+1

### Steps to reproduce

新建课表
手动添加课程
从最后一节课开始加连上多节的课

### Schedule source

_No response_

### Additional context

_No response_

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-01T11:25:12Z\n\n已在 v1.0.43 (versionCode 44) 修复：手动添加课程的「连上几节」范围限制为当前课表总节数。节次超出最大节次时编辑器会夹紧值并显示指向「课表管理」的内联提示。

参考 commit：7ae357c（fix: limit manual course periods to timetable range）。

下载：https://github.com/lingion/sleepy/releases/tag/v1.0.43
