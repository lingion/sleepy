[evidence=B] 整理 2026-09-18 · 本轮未抓 Apple HIG 原文；形态按公开 ActivityKit API 资料整理，待官方复核

# Apple — Dynamic Island 四种呈现形态

## 形态表

| 形态 | 位置/用途 | Sleepy 课程内容 |
|---|---|---|
| compact leading | 岛左侧小区域, 与 trailing 组成胶囊 | 课程简称或学科图标 |
| compact trailing | 岛右侧小区域 | 剩余分钟数/钟表 |
| minimal | 多个活动同时存在时的最小展示 | 课程图标 + 状态色的文字等价信息 |
| expanded | 用户长按或系统展开后的完整岛区域 | 课程名、教室、教师、结束时间和一个高频动作 |
| Lock Screen / Banner | 非 Dynamic Island 设备也可见 | 同一 ContentState 的完整短卡 |

## 数据与布局关系

`ActivityAttributes` 描述活动不变的身份, `ContentState` 描述会变的课程状态。四种形态都从同一个状态读取数据, 不要为每个形态维护一套互相漂移的课程模型。

```swift
DynamicIsland {
    DynamicIslandExpandedRegion(.leading) { Text(context.state.title) }
    DynamicIslandExpandedRegion(.trailing) { Text(timerInterval: Date()...context.state.endsAt) }
    DynamicIslandExpandedRegion(.bottom) { Text(context.state.room) }
} compactLeading: {
    Text(context.state.title.prefix(2))
} compactTrailing: {
    Text(timerInterval: Date()...context.state.endsAt)
} minimal: {
    Image(systemName: "book")
}
```

## 截断与降级

- compact 区域有限, 课程名必须有短名称策略。
- minimal 不能承载复杂文本; 用户需要的信息应在锁屏或展开态重复出现。
- 不支持 Dynamic Island 的设备仍应得到 Live Activity 的锁屏/横幅呈现, 不能把岛存在当作活动存在的前提。
- 具体尺寸、字数上限、同时活动数和更新策略: N/A, 等官方 HIG/ActivityKit 文档复核。

## Sleepy 落点

首版先保证 Lock Screen / Banner 内容可读, 再调 compact/minimal。每个形态都用真实长课程名、中文教室名和“第 12 节”这类字符串做快照测试。
