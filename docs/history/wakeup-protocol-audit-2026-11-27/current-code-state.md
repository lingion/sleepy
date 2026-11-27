# 本轮代码状态和验证

## 修改范围

本轮继续修改以下生产文件。

- app/src/main/java/com/lingion/sleepy/data/jw/JwClassicEamsParser.kt
- app/src/main/java/com/lingion/sleepy/data/jw/JwEcuplScriptParser.kt
- app/src/main/java/com/lingion/sleepy/data/jw/JwEcuplParser.kt
- app/src/main/java/com/lingion/sleepy/data/jw/JwSourcePeriods.kt
- app/src/main/java/com/lingion/sleepy/data/jw/JwPostgradSchoolParser.kt

共享扫描和其他协议解析改动还包括以下文件。

- JwScriptSource.kt
- JwScriptMask.kt
- JwScriptJson.kt
- JwThuScriptParser.kt
- JwUndergradHtmlParser.kt

新增回归文件为以下四个。

- app/src/test/java/com/lingion/sleepy/data/jw/JwStaticScriptIdentifierRenameTest.kt
- app/src/test/java/com/lingion/sleepy/data/jw/JwEcuplRenamedScriptIdentifiersTest.kt
- app/src/test/java/com/lingion/sleepy/data/jw/JwUndergradHtmlContainerRenameTest.kt
- app/src/test/java/com/lingion/sleepy/data/jw/JwEcuplMixedSourcePeriodsTest.kt

## 本轮生产行为

经典 EAMS 现在把任务块边界截断到下一个任务或实际接收活动的 marshalTable。这样页面收尾后的表单、菜单和其他数字赋值不会进入课程坐标。

ECUPL 脚本活动绑定只接受任务前的真实赋值或声明。找不到绑定时跳过该活动，不再回退到固定的 activity 名称。

旧树维的置信度识别要求存在真实的 TaskActivity 代码，并且存在活动数组写入或任意接收者的 marshalTable。它仍保留旧空课表和最小采集片段的成功判定。

ECUPL 年表解析和来源作息选择复用 TaskActivity 与活动表接收者对应的 marshalTable 参数，允许该调用位于另一个脚本块，也不再要求固定的 `table0` 或 ASCII 标识符。shuwei_old 仅在相邻字符串累加器唯一时用其兼容旧标题，否则保留 TaskActivity 中明确声明的课程名。

## 测试

三个独立测试组均通过。

脚本解析组执行以下命令，213 项通过。

    ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.lingion.sleepy.data.jw.JwStaticScriptIdentifierRenameTest' --tests 'com.lingion.sleepy.data.jw.JwLegacyScriptBindingTest' --tests 'com.lingion.sleepy.data.jw.JwClassicEams*Test' --tests 'com.lingion.sleepy.data.jw.JwScript*ResilienceTest' --tests 'com.lingion.sleepy.data.jw.JwStaticScriptVariationsTest' --tests 'com.lingion.sleepy.data.jw.JwPostgrad*Test' --tests 'com.lingion.sleepy.data.jw.JwThu*Test' --offline --console=plain --max-workers=2

ECUPL 组执行以下命令，62 项通过。

    ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.lingion.sleepy.data.jw.JwEcuplRenamedScriptIdentifiersTest' --tests 'com.lingion.sleepy.data.jw.JwEcuplMixedSourcePeriodsTest' --tests 'com.lingion.sleepy.data.jw.JwSourcePeriodsTest' --tests 'com.lingion.sleepy.data.jw.JwEcupl*Test' --offline --console=plain --max-workers=2

本科 HTML 组执行以下命令，102 项通过。

    ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.lingion.sleepy.data.jw.JwUndergradHtmlContainerRenameTest' --tests 'com.lingion.sleepy.data.jw.JwUndergrad*Test' --offline --console=plain --max-workers=2

新增回归覆盖以下情形。

- ECUPL 使用中文声明名并通过 addActivityByTime 实际引用。
- 经典 EAMS 的 marshalTable 之后存在无关数字赋值。
- shuwei_old 使用中文活动名、教师名、坐标名和表格名。
- shuwei_old 改名后的中文标题累加器替代占位标题。
- shuwei_old 存在多个字符串累加器时保留 TaskActivity 明确声明的课程名。
- ECUPL 混合年表和脚本页改名表格、活动、节数及坐标绑定后仍选择年表作息。
- ECUPL marshalTable 接收者改名并在成员点号两侧增加空白后仍选择年表数据和作息。
- ECUPL 不把其他表对象的 marshalTable 误认作年表收尾，并保留同一活动表跨脚本绑定。
- 既有脚本改名、声明方式、成员路径空白、注释、字符串和正则伪数据。

## 证据边界和剩余限制

完整单元测试在补充作息回归前通过 5101 项，0 failures，0 errors，3 skipped。补充回归后曾有两次完整运行停在既有 Compose UI 等待用例。第一次停在 `EditTableTargetIsolationTest.deletingATableStopsItsCachedObserverAndRestoringItsIdCreatesAFreshOne`，该用例随后单独运行通过。第二次停在 `EditorCreationRoomTest.periodReadyAndDiscardUseTheRealRowAndWaitForListCleanup` 的 Compose 根节点等待。

随后在整合提交 `4acdc374982f98253ae3879d38bf36671f42f537` 上，以 `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon --offline --console=plain --max-workers=2 :app:testDebugUnitTest --rerun-tasks` 强制重跑全量单测。结果为 `BUILD SUCCESSFUL in 21m 19s`，59 项 Gradle 任务全部执行。682 份 XML 共计 5105 tests，0 failures，0 errors，3 skipped。三个跳过项分别是缺少 `SLEEPY_WAKEUP_XINPUT` 的离线握手重放、缺少 `SLEEPY_WAKEUP_LIVE` 的 WakeUP 活体直连，以及缺少镜像 HTML fixture 的 `ReleaseNotesContractTest.websiteHtmlAlsoSurvivesTheMirrorFallback`。

PR #485 重放到主线 `30a8b1bd` 后，在代码树 `e5797875` 上重新运行本科定向测试，102 tests、0 failures、0 errors、0 skipped，59 项 Gradle 任务全部执行。PR 的必需 CI 检查 `commit-lint`、`android-unit-test`、`android-assemble` 和 `collector-test` 均通过，lint、学校目录、脚本抓取、Release Notes consumer 与签名构建检查也通过。该代码树与前述 5,105 项全量测试使用的代码树及主线合并后代码树一致。PR #485 于 2026-11-27 15:17 UTC 合并为 `4017cf3241f2ce534d34d368eceba66e90674338`。

lint 在最终成员点号空白调整之前运行。结果为 `BUILD SUCCESSFUL`，报告有 571 个 warning、40 个 hint 和 0 个 error。报告未发现本轮修改文件中的 lint issue。之后的成员点号调整通过 Kotlin 编译和 ECUPL 定向测试。HTML 报告位于 `app/build/reports/lint-results-debug.html`。

本轮证据仍然是脱敏页面、离线静态解析和 JVM 单元测试，没有真实账号认证导入记录。

经典 EAMS 的裸线性坐标例如 index = 42 本身不包含每天节数信息。页面同时删除坐标公式、数组写入和可识别步长时，静态解析没有唯一答案。本轮保留其历史 index 兼容路径，并限制任务扫描边界，没有凭猜测从其他数字字段推导节数。

## 用户原话与实现行为

| 用户目标 | 实现行为 |
|---|---|
| 学校只改教务名称后仍能导入 | 结构解析和候选表选择不以门户标题作为课程数据依据。 |
| 学校改变量名后仍能导入 | 共享静态层支持 Unicode 标识符和成员路径，ECUPL 作息分支也复用年表结构判定。 |
| 将协议抽象出来并逐项对比 | 以协议族矩阵记录数据证据、改名行为、边界和未能唯一推导的情形，并逐项审计已定位 PR。 |
