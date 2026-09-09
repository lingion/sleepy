# 解析课程名称自动区分理论课/实验实践课\n\n- Number: #20\n- State: open\n- Author: Yinzhixiang152\n- Created: 2026-09-06T19:00:46Z\n- Updated: 2026-09-07T02:01:45Z\n- URL: https://github.com/lingion/sleepy/issues/20\n\n## Body\n\n### Prerequisites

- [x] I have searched existing issues and found no duplicate
- [x] I have confirmed this feature is not already in the latest release

### Problem or motivation

很多学校（例如广东医科大学）会直接把课程类型写在课程名称括号内，示例：
- 市场营销学(理论)
- 全口义齿工艺技术(实验)
如果有这个功能可以很方便的识别上什么课

### Proposed solution

1. 解析器通过正则匹配课程名字符串括号内关键词：理论、实验、实践、实训。
2.识别不到的时候，允许用户手动标记课程类型。


### Alternatives considered

目前只能手动添加课程备注，效率较低

### Additional context

<img width="309" height="571" alt="Image" src="https://github.com/user-attachments/assets/1aae0715-293d-4cea-b5db-1d7ccddcccf6" />

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-07T00:13:32Z\n\n感谢你给出具体例子：`市场营销学(理论)`、`全口义齿工艺技术(实验)`，以及“理论、实验、实践、实训”这些课程类型关键词。你提出的自动识别和识别不到时手动标记，我都记下了。

我会按这个方向处理，保留手动标记的可能，争取在接下来的两个版本内修好。\n\n### lingion — 2026-09-07T01:30:54Z\n\n回看一下自己之前那条，"争取在接下来的两个版本内修好"说得太满了。

实际状况是这样：parser 当前是按字符串整体存课程名，括号里的内容没有单独抽出来。要做"理论/实验/实践/实训"的分类，得动 CourseEntity 加 courseType 字段（或把现有 type 的语义拆开），parser 端加正则，UI 端加手动标记入口——整条链子，不是一两行能改完的。

短时间内我没法给你一个稳定的时间承诺。但能保证的是：真动手做的时候，原始课程名完整保留，括号信息不丢。即使没法自动分类，"市场营销学(理论)" 也会原样待在课程名里，手动标记的入口也一定会留。

现在急着用的话，可以先用"备注"字段自己标一下，那个能存任意文本。\n\n### Yinzhixiang152 — 2026-09-07T01:50:50Z\n\n好的，其实不是很着急。非常感谢您能做这个项目让我刷帖子刷到。
现在的课表根据上课地点也能区分理论和实验课，使我可以用手动备注方式区分。
\n\n### lingion — 2026-09-07T02:01:44Z\n\n接着上次那条，这次按你说的处理了：不判断课程名后面是什么内容，也不做清洗、裁剪或分类。

广东医科大学走的是新版正方（zf_new）协议，`JwNewZfParser` 直接把 `kcmc` 整体作为课程名保存。你给的两个例子已经加了回归测试：

- `市场营销学(理论)`：原样保留
- `全口义齿工艺技术(实验)`：原样保留

后面无论是括号、引号、书名号、省略号、星号、emoji 还是其他符号，也都按原字符串完整提取，不在 parser 里做额外适配。
