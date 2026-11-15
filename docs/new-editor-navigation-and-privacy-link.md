# 新建编辑页导航、隐私链接与日期玻璃容器

记录日期：2026-11-14。本页记录这一轮 Android 修复，完整 iOS 26.5 控件对齐目标仍未完成。

## 新建课表与作息表

此前的新建路径先插入数据库，再跳转编辑页。课表路径还会等待 `WidgetUpdater.notifyDataChanged` 完成。管理页直接观察 Room 数据，因此新记录可能先在列表中变成一张卡片，随后才发生页面跳转；这也是点击后观感迟滞、列表先变化的来源。作息表同样是插入完成后才导航。

现在点击直接压入带独立 `requestId` 的 `CreateTable` 或 `CreatePeriodTable` 路由。`NewEditorDestination` 先呈现正常的目标页顶栏、返回按钮和初始化状态，在目标页取得首帧后启动创建，没有固定毫秒数的导航等待。创建由 `ScheduleViewModel` 持有，课表未提交时跳过小组件刷新；等 ViewModel 已发布包含新 ID 的数据后才展示编辑内容，避免编辑页短暂报告找不到数据。

离场中的管理页通过 `rememberNavigationEntrySnapshot` 保留最后可见数据，导航动画期间不会先长出新卡片。Room 与共享 ViewModel 仍照常更新，编辑页可以读到刚创建的记录。

初始化期间返回会等待插入完成，再删除未保存记录，并等待列表发布删除结果后返回管理页；不直接取消可能已经提交的 Room 插入。课表同时恢复可用的原选择或默认选择；第一张实际课表仍沿用原有默认表规则。未保存作息表仅在未被绑定时删除。

保存协调器按请求 ID 保存在 ViewModel 中，阻止重复保存和保存期间重复丢弃，并跟踪 ViewModel 的保存任务。临时进入子页面或重建页面后，成功结果由当前激活的导航项只处理一次。课表编辑页原有的节假日入口继续保留，本轮没有新增其编辑能力。复制流程仍遵循既有规则，不将复制统一改成新建后进入编辑页；作息表将保存和复制完成分成独立回调，新建未保存作息表复制成功后先清理来源草稿，再返回列表，保留副本。

编辑非当前课表时，删除提示中的课程数量与作息变更策略的原始基线按目标 `tableId` 读取，不再借用首页选中课表的数据。ViewModel 为每张目标表缓存持续观察的 `StateFlow`，让子页返回时可以直接取得已加载内容，避免重新从空值启动冷 Flow，导致编辑草稿首帧被初始化页替换。

每份缓存订阅有独立、从属于 ViewModel 的 Job。子页暂时遮住编辑页时继续观察目标数据；课表列表发布实际删除后，移除该 ID 的缓存并取消对应订阅，避免反复新建、丢弃或删除课表后仍保留失效的常驻观察。ViewModel 结束时，剩余订阅随父 Job 一起结束。此生命周期修正的最终回归结果见下方验证范围。

恢复边界：已写入页面保存状态的记录 ID 可以复用；数据库尚未持久化创建请求 ID，因此进程恰在数据库提交后、记录 ID 写入页面保存状态前被终止时，仍有极窄的恢复缺口。本轮不宣称已解决这类进程终止恢复。

实现入口：

- [类型化路由](../app/src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt)、[导航操作](../app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavigator.kt)
- [新建目标页](../app/src/main/java/com/lingion/sleepy/ui/nav/NewEditorDestination.kt)、[创建与保存协调器](../app/src/main/java/com/lingion/sleepy/ui/nav/EditorCreation.kt)
- [离场页数据快照](../app/src/main/java/com/lingion/sleepy/ui/nav/NavigationEntrySnapshot.kt)、[ViewModel 创建与清理](../app/src/main/java/com/lingion/sleepy/ui/screen/schedule/ScheduleViewModel.kt)

## 关于页的三处外链

以原有开源地址的行式布局为准，Liquid Glass 的开源地址、隐私政策、开源声明共用 `AboutExternalLinkCard`：24 dp 前导图标、12 dp 图文间隔、标题与说明之间沿用 `SettingsLayout.descriptionGap`，右侧统一为现有 `GlassIconButton` 的 48 dp 触达区、36 dp 玻璃圆片与 20 dp 外链图标。卡片内边距为 20 dp，卡片间距为 12 dp。标题和说明左缘、尾按钮右缘一致，长说明允许换行，不固定高度裁字。

隐私政策之前单独增加的整行玻璃胶囊已撤回。开源声明在玻璃模式补齐同尺寸的前导图标与外链玻璃按钮；Material Design / 无玻璃宿主继续保留原有开源声明的整行点击与无前导图标布局。开源地址与隐私政策仍使用原有标题、下划线网址和尾按钮外观；开源地址的下划线文字此次也成为真实可点击链接。三处按钮均有本地化无障碍名称，文字与尾按钮使用同一目的地的独立点击处理，没有可点击父容器叠加回调。

关于页信息卡通过 `GlassMaterialScope` 导出自身的圆角材质背板，子玻璃按钮采样包含直接背板。原来的源码、隐私、许可三个 URL 保持各自地址，不相互替换。

实现见 [AboutScreen.kt](../app/src/main/java/com/lingion/sleepy/ui/screen/mine/AboutScreen.kt)。

## 日期选择器容器

Material Design 或没有玻璃宿主时，继续直接使用官方 `MaterialDatePicker`，保留官方容器配色与布局。Liquid Glass 单独组合透明日期内容，修复直接将官方容器透明后，年份网格仍覆盖在活动日历上、导致两者同时透出的实际问题。

玻璃路径复用公开的 `DatePickerTitle`、`DatePickerHeadline` 与官方日期输入 body。输入代理始终报告 `DisplayMode.Input`，避免离场输入内容收到日历模式后再启动第二个日历动画。标题区最小高度 120 dp，分隔线纳入标题区；模式转换采用当前 Material motion scheme 的公开动效规格。

仅玻璃日历与年份 body 由公开 M3 `Surface`、按钮、分页和网格等组成。年份展开、收回期间均不组合底下的月份日历，年份完全退出后才恢复日历，因此不用不透明遮罩掩盖重叠。日期状态、可选日期限制、本地化格式、官方选中日期和内容色继续来自传入的 `DatePickerState` 与官方配色。这是玻璃路径的应用层组合，不等于直接复用官方完整日期 body，也不据此宣称原生动画逐帧等效。

此前的焦点与窗口高度修复见[日期弹窗与课表选择器](date-dialog-and-table-selector.md)。

实现见 [DateTimePickers.kt](../app/src/main/java/com/lingion/sleepy/ui/component/DateTimePickers.kt)。

## 验证范围

最终运行 `validation-final9-links.log`：20 类、87 项定向测试通过，失败、错误与跳过均为 0；`assembleDebug` 和 `assembleDebugAndroidTest` 成功。覆盖创建/保存/丢弃协调、Room 记录生命周期、作息复制、目标表隔离与缓存删除、子页草稿恢复、日期焦点/透明度、关于页三处外链，以及本次合入上游的导入提示词、格式优先和教务来源迁移等相关路径。[^iteration-evidence]

最终主 APK 已安装到 API 36 的 PKX110，主机文件与设备 `/data/app` 中安装包 SHA-256 一致：`49e808c8c3c9c13caca3d848885e60af58fe516a823b535d5a82dc068fa24031`。测试 APK 已构建，本轮没有用它运行新的全量设备仪器测试。

真机证据：

- 新建作息表直接进入目标编辑页；工具栏返回清理未保存记录，父管理页只保留原作息表。
- 新建空白课表进入调休子页后，子页返回仍保留课表编辑会话；系统返回清理未保存课表，没有出现“找不到课表数据”。
- 新建课表与作息表保存后单次返回，记录存在；作息表复制成功后返回列表，只保留副本并清理未保存来源。命名测试记录均通过 UI 删除，原数据保留。
- 玻璃日期的日历、年份、输入和有焦点输入切回日历分别留图；修正后年份与日历不再叠字。它们不构成 iOS 原生逐帧等效证据。
- 关于页最终同屏截图为 `about-external-links-glass-final.png`：开源地址、隐私政策、开源声明的标题左缘均为设备 x=227 px，按钮右缘均为 x=1096 px，触达布局均为 143×143 px（对应本机约 48 dp）。图标及文本的显示和空间同时核对，未用固定卡高裁切说明。真实隐私 URL 的外部 VIEW Intent 已核对，没有读取外部网页内容。

所有上述素材、XML、失败日志、最终测试摘要、源码清单和 APK 身份保存在会话目录 `artifacts/creation-navigation-20261114`，并随本轮私有云端增量交接。`privacy-glass.png` 是已被用户指出不一致的旧胶囊，`date-glass-year.png` 是旧年份叠字失败证据，不能当作最终效果图。`unexpected-period-screen.png` 只记录一次导航状态核对，不是关于页截图。

`period-before.mp4`、`period-after.mp4`、`table-after.mp4` 连同视频 PTS 和 contact sheet 保留了同轮设备的页面/记录行为，不推导跨设备精确输入延迟。最终测试、构建与局部真机检查不替代完整导航观感、光学、多背景、加速度、圆角或逐帧一致性验收。完整目标仍进行中。

[^iteration-evidence]: `validation-final3-results` 保留子页返回首帧的真实失败，之后通过缓存持续观察修复；`validation-final4.log` 为前轮 58 项通过。`validation-final5-results` 为 59 项中 1 项新增保存测试在预览确认按钮的语义树查找竞态失败，保留数据/回调断言并改为等待真实可点击节点后，`validation-final7-results` 的 59 项全通过。`validation-final6.log` 是重启时遗漏 SDK 环境变量的失败记录。合入上游后 `validation-final8-merged-results` 的 86 项通过；用户反馈三外链不齐后调整共享组件与新增同屏回归，最终 `validation-final9-links-results` 为 87 项全通过。
