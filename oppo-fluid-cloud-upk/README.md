# Sleepy OPPO Fluid Cloud UPK

这是 Pantanal DevStudio 的有宿主流体云工程骨架，**尚未接入 APK 构建或运行链路**。Sleepy 当前的 ColorOS 流体云适配走 Android 16 Live Updates，不使用此 UPK；完整状态见 [OPPO / ColorOS 接入状态](../docs/vendors/oppo-coloros-fluid-cloud.md)。

复核日期：2026-10-29（仓库代码复核，本次未执行真机或 DevStudio 验证）。

## 已有源文件

- `src/config.json`：API 2.0、`immediate`、`notification/statusbar` 入口
- `src/assets/card-config.json`：调试宿主包名 `com.lingion.sleepy.debug`、预留 Provider 名称、sm/md/lg 三种流体云尺寸；其中 `SleepyFluidCloudProvider` 在 APK 中尚不存在
- `src/pages/index.oml`：课程名、上课时间、地点、教师、状态字段

## 尚未完成的宿主接入

- `app/libs/seedling-support-lite-3.0.7.aar` 已存放在仓库，但 `app/build.gradle.kts` 未声明依赖
- 尚未实现 `SeedlingCardWidgetProvider` 生命周期桥接，也未在 `AndroidManifest.xml` 注册对应 Provider
- 课程调度和 `FluidCloudService` 尚未调用 Seedling SDK 创建、更新或结束卡片
- 接入前还需按实际调试/发布宿主核对 `card-config.json` 中的包名、Provider、最低宿主版本和点击入口

## 必须由 OPPO 分配后替换

- `identifier.id`
- `intent.action`
- `intent.domain`
- 事件名称与 eventCode
- 可能的白名单/grade/requestShowPanel 能力

`src/config.json` 中的 service ID、intent action/domain 仍为 `REPLACE_WITH_OPPO_*`；事件名称与 eventCode 尚未接入宿主代码。该工程不能提交发布，也不能作为真实流体云 PASS 证据。这里的缺口不影响已实现的 Android Live Updates 通道。

## 构建

需要 Pantanal DevStudio 验证 OML 模板语法并生成 UPK。仓库未提供 UPK 构建任务或生成的 `.upk`，Android Gradle 构建不会自动打包、发布此目录。
