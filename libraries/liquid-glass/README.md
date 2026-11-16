# Sleepy Liquid Glass runtime

Sleepy 通过 `:liquid-glass-runtime` 消费 [runtime](runtime) 中的生产 Kotlin、AGSL 和确定性 host 回归。模块映射见根目录 `settings.gradle.kts`，应用主题、业务状态与控件路由由 `app` 管理。

模块只包含生产实现和必要应用回归。共享测试辅助代码及实际验证行为、采样和几何的 GPU 回归由应用测试集维护。

来源与许可信息见[来源审计](../../docs/attribution-audit/glass-research-sources.md)。
