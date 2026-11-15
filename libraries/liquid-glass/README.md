# Sleepy Liquid Glass runtime

Sleepy 通过 `:liquid-glass-runtime` 消费 [runtime](runtime) 中的生产 Kotlin、AGSL 和确定性 host 回归。模块映射见根目录 `settings.gradle.kts`，应用主题、业务状态与控件路由由 `app` 管理。

研究入口、原生参考工程、实验报告、图像、数值观察与提取计划仅在独立 [sleepy-liquid-glass](https://github.com/Kerry1020/sleepy-liquid-glass) 仓库维护。迁出文件的来源与逐字节校验见[迁移记录](https://github.com/Kerry1020/sleepy-liquid-glass/tree/main/research/sleepy-research-migration-20261116)。研究源不得重新注入应用默认源码或构建。

本仓库只保留生产实现和必要应用回归。共享测试辅助代码及实际验证行为、采样和几何的 GPU 回归仍由应用测试集维护。迁移不修改材质参数、生产代码行为、主题、业务布局或模块依赖。

来源审计与归属信息见[现有审计](../../docs/attribution-audit/glass-research-sources.md)；开源参考与实验档案的许可记录随独立资产保存。
