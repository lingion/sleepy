[evidence=B] 整理 2026-09-18 · 本轮未抓 developer.apple.com ActivityKit/APNs 原文；以下为通用链路记录，待官方原文复核

# Apple — Live Activities 推送更新链路

> 结论先行: 远端更新不是普通通知正文替换。App 先创建活动并拿到活动专用 push token, 服务端把 `aps` 中的 Live Activity 更新发到 APNs, payload 携带新的 `content-state`；结束活动时发送 `event=end`，还可带 `stale-date` 控制状态过期。

## 链路

```text
App 请求 Activity
  -> ActivityKit 创建 Live Activity
  -> 获取 push token
  -> 服务端保存 token 与 courseId 的对应关系
  -> APNs 推送 update
  -> Widget Extension 渲染新 ContentState
  -> end / stale-date 后清理服务端映射
```

## Payload 轮廓

```json
{
  "aps": {
    "timestamp": 1700000000,
    "event": "update",
    "content-state": {
      "title": "高等数学",
      "room": "A301",
      "endsAt": 1700002700
    },
    "stale-date": 1700002800
  }
}
```

结束事件把 `event` 改为 `end`, 并按当前 SDK 要求携带最终状态或结束策略。字段大小、认证方式、token 轮换和 APNs HTTP/2 请求头未在本轮官方资料中核验, 不写死。

## 失败处理

- token 失效或活动已结束: 服务端删除映射, 不重复重试。
- 状态过期: App/Widget 显示 stale 状态, 下次课程重新创建活动。
- 网络不可用: 本地 ActivityKit 更新作为补充, 不能假定云推送必达。
- 用户关通知或系统不支持: 回普通本地通知/日历提醒。

## Sleepy 落点

token 与课程 ID 做一对一映射, 不把 token 写进课程导出文件。课程被编辑、删除或跨表迁移时, 先结束旧活动再清理服务端映射。服务端暂时缺失时, 先做本地更新版本, 不把 APNs 当首版硬依赖。
