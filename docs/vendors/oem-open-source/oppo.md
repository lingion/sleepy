# OPPO · 开源与社区 —— 生产环境反编译证据最丰富

> 证据等级: A —— GitHub 实测(2026-09-17)。

## 生产环境反编译(比官方文档更真实)
- TikTok 经 ContentProvider + `SeedlingCardWidgetProvider` 接入 Pantanal: `https://github.com/cxxsheng/TiktokSource/tree/main/com/oplus/pantanal/seedling`
- Snapchat 同款: `https://github.com/meltingscales/com.snapchat.android`
- oplus 权限研究: `https://github.com/DroidDumps/oplus_ossi_dump`

## 官方/社区 demo
- ❌ **无官方开源 demo 库**
- Gitee 检索"原子组件"为空

## 关键反编译价值
- 验证了 Pantanal UPK 是**生产可用**的接入路径(不止商务介绍,真有 app 在跑)
- 验证了 SeedlingCardWidgetProvider 是标准 BroadcastReceiver + RemoteViews 模式变体
