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

完整单元测试在本轮补充作息回归前已运行通过，共 5101 项测试通过，0 failures，0 errors，3 skipped。跳过项为 WakeUP 真实服务器握手离线重放、WakeUP 活体直连和 ReleaseNotes 镜像回退环境测试。

补充作息回归后再次运行完整套件，两次都停在既有 Compose UI 测试的等待阶段，没有得到完整测试结果。第一次等待 `EditTableTargetIsolationTest.deletingATableStopsItsCachedObserverAndRestoringItsIdCreatesAFreshOne`。该用例随后单独运行通过。第二次停在 `EditorCreationRoomTest.periodReadyAndDiscardUseTheRealRowAndWaitForListCleanup` 的 Compose 根节点等待。三个定向测试组均通过，本轮完整套件重跑未完成。

lint 在最终成员点号空白调整之前运行。结果为 `BUILD SUCCESSFUL`，报告有 571 个 warning、40 个 hint 和 0 个 error。报告未发现本轮修改文件中的 lint issue。之后的成员点号调整通过 Kotlin 编译和 ECUPL 定向测试。HTML 报告位于 `app/build/reports/lint-results-debug.html`。

本轮证据仍然是脱敏页面、离线静态解析和 JVM 单元测试，没有真实账号认证导入记录。

经典 EAMS 的裸线性坐标例如 index = 42 本身不包含每天节数信息。页面同时删除坐标公式、数组写入和可识别步长时，静态解析没有唯一答案。本轮保留其历史 index 兼容路径，并限制任务扫描边界，没有凭猜测从其他数字字段推导节数。

## 用户原话与实现行为

| 用户目标 | 实现行为 |
|---|---|
| 学校只改教务名称后仍能导入 | 结构解析和候选表选择不以门户标题作为课程数据依据。 |
| 学校改变量名后仍能导入 | 共享静态层支持 Unicode 标识符和成员路径，ECUPL 作息分支也复用年表结构判定。 |
| 将协议抽象出来并逐项对比 | 以协议族矩阵记录数据证据、改名行为、边界和未能唯一推导的情形，并逐项审计已定位 PR。 |
