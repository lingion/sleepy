[evidence=B] 整理 2026-09-18 · 本轮未抓 developer.apple.com 原文；以下为 ActivityKit/Live Activities 公共 API 的通用资料，待官方原文复核

# Apple — Live Activities / ActivityKit

> 证据等级: B(通用公开知识, 非本轮 Apple 官方页落盘)。结论先行: iOS Live Activities 由 ActivityKit 管理, UI 写在 Widget Extension 的 ActivityConfiguration 中, 活动状态通过 `Activity.request`, `update` 和 `end` 驱动；Dynamic Island 只是 iPhone 14 Pro 及后续支持机型的一种呈现位置，锁屏仍是基础触点。

## 能力边界

| 项 | 当前记录 |
|---|---|
| 框架 | ActivityKit |
| 最低系统 | iOS 16.1+（需以目标 SDK/官方文档复核） |
| Dynamic Island | iPhone 14 Pro 及后续支持机型；不是所有 iPhone 都有 |
| 活动内容 | `ActivityAttributes` + `ContentState` |
| 更新 | App 内更新；服务端可经 APNs 更新 |
| 结束 | `Activity.end`，可指定结束策略 |
| 生命周期 | 活动有系统生命周期限制；iOS 16.2 起常见资料记录最长约 8 小时，需以当前系统文档复核 |

## 典型结构

```swift
struct CourseAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var title: String
        var room: String
        var endsAt: Date
    }
    var courseId: String
}

// Widget Extension 中描述四种呈现
ActivityConfiguration(for: CourseAttributes.self) { context in
    // Lock Screen / Banner
} dynamicIsland: { context in
    DynamicIsland {
        // expanded regions
    } compactLeading: {
        // compact leading
    } compactTrailing: {
        // compact trailing
    } minimal: {
        // minimal
    }
}
```

## Sleepy 落点

Apple 侧可按“单节课一个活动”建模，不把全天课表塞进一个活动。`courseId` 是稳定业务标识，`ContentState` 只放当前课程名、教室、结束时间等状态。具体 8 小时边界、更新频率、权限和 API 签名必须在补抓官方文档后再作为实现契约。
