# 引用与致谢补全验收记录

本次来源核对日期为 2026 年 11 月 3 日，来源审计基线为 `3751fddb`。提交前已将改动应用到 main 的 `9985c997`，保留同期新增的小组件资源。

## 覆盖范围

本次从现有源码、Gradle 与 Go 依赖、源码导入记录、历史 attribution-candidates 清单、厂商与平台研究文档，以及本地液态玻璃调研记录核对出处。补充清单包含 192 项来源，分别记录作者、链接、许可状态、使用性质及仓库内依据；另保留 304 条厂商与平台资料引用，以及十个尚不能唯一定位仓库的历史项目简称。既有跨校、逐校与六套语言教务致谢继续保留。

GitHub API 与本地引用依据核对了其中 183 项仓库记录；另外三条历史仓库地址本次未确认，六项资料与数据源沿用归档证据。这里的仓库可访问性核对不等于全部许可已完成版本级审计，也不等于 304 条平台页面均已重新访问。

## 应用内顺序不变量

原有许可证、贡献者、教务导语、跨校和逐校卡片的相对顺序保持一致。新增来源声明常驻展示，分类明细使用现有的折叠卡片。

```text
开源声明
  许可证说明
  贡献者
  依赖、素材与研究参考
    常驻源码来源声明
    源码移植与素材来源
    运行、构建与测试依赖
    日历与地理数据
    设计规范与官方资料
    液态玻璃调研
    厂商通知与小组件调研
    后台提醒可靠性调研
    教务协议调研补充
  既有教务致谢导语
  既有跨校项目
  既有逐校项目
```

## 来源审计阶段验证（基线 `3751fddb`）

- `python3 scripts/test_attributions.py`：8 项检查通过，覆盖来源依据、六套资源、页面分组顺序、常驻署名、许可 SHA-256、README、文档本地链接及生成文件漂移。补充历史教务组之前的红阶段确实检出了缺失条目和缺少分组，补齐后转绿。
- `python3 -m unittest discover -s scripts -p test_public_docs.py`：12 项检查通过。
- `./gradlew --offline :app:testDebugUnitTest --tests com.lingion.sleepy.AboutLicenseAttributionTest --max-workers=2`：最终资源版本构建成功，7 项 Android 致谢测试通过，没有失败或错误。
- `./gradlew --offline :app:testDebugUnitTest --tests com.lingion.sleepy.AboutLicenseAttributionTest :app:lintDebug --max-workers=2`：执行成功。Lint 报告有 588 条警告，没有错误；LicenseScreen.kt 与新增 attributions.xml 没有报告告警。此后修正了链接、说明文字及 Haze 的归类，并重新完成资源处理、编译与致谢测试。
- `actionlint .github/workflows/ci.yml`：使用 1.7.11 通过，零输出。临时二进制先经官方发布 SHA-256 清单校验。CI 已接入来源完整性检查。
- `git diff --check`：通过。
- 最终合并后的 Android assets 中，ATTRIBUTIONS.json 与源文件逐字节一致，六份许可与声明文件的 SHA-256 全部与清单一致。

应用到 `9985c997` 后已重新通过八项来源检查、十二项公开文档检查、生成文件检查及 actionlint。Android 全量单测与 lint 的提交级结果以对应 PR 检查为准。验证范围不包含设备安装与真机视觉验收。

## 保留的出处限制

SleepDown 定制模块的导入修订没有记录。现已保留其当前非商业、源码可见许可 1.1 和第三方声明，并将它与 Kyant 原始 Apache-2.0 内容区分；这次补全不代表已经确认定制代码的分发许可兼容性。DataV 数据授权仍沿用现有数据出处文件的未确认状态。

Signal-Chat-Backup、miosegit/MiPushSDK 与 ECSA-Thu 的历史地址本次没有确认。JetIsland、VLC 和 Mifos Mobile 的错误历史链接经项目搜索与仓库元数据复核后修正，旧拼写保存在 historical_name 字段。
