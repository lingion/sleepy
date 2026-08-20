# Changelog

## v1.0.34

### Course color split — one toggle became two

Last release shipped a "unified course color" toggle. One switch, two jobs: it stripped color from the desktop widgets *and* from the in-app schedule grid. If you wanted gray widgets but colored capsules in the app, too bad. One switch drove both.

Now they're two independent settings.

- `widget_colorless` — desktop widgets only. Behavior unchanged.
- `course_colorless` — the in-app schedule grid and today page only. New, default off.

Default off means the app's course capsules come back in color after you update, even if you had the old unified toggle on. The two sides no longer touch each other.

The split is enforced in `AppPrefs`, not just in the UI. The two keys live side by side, neither reads the other, neither seeds the other. There's no migration step — the old widget toggle keeps its stored value, the new course toggle starts from its default. Zero cross-read, so the independence can't regress silently. The course toggle's switch handler deliberately does *not* call `refreshWidgets()`, because it has nothing to do with widgets.

### CourseColorUtil — one source of truth, plus text luminance

Course color logic existed in four places. Four copies of the same HSL-picking code, drifting. This release pulls them into a single `CourseColorUtil` with three layers: constants, pure logic, and platform adapters. The four call sites now point at one implementation.

Along the way, a real bug: text color on custom course capsules used a fixed source, so a dark custom course color produced dark text you couldn't read. `CourseColorUtil` gained `luminance` and `textColorOn` pure functions. Deep custom colors now get white text; light ones keep the old behavior. This lands on every surface that renders a course capsule — the in-app card, the lesson rows, the today card, and the widget bitmap renderers.

### Glance layer deleted

The Glance framework is gone. All five widgets now render through synchronous RemoteViews + Canvas — no `SessionWorker`, no async render that OPPO freezes mid-draw. `CourseColorRules` went with it, and `loadDataSync` moved into the receivers.

This is internal. No user-visible behavior change. But it's why the widget refresh story from 1.0.31/1.0.33 finally holds everywhere: one code path, no async race.

### Settings screen reorganized

The settings entry grew to eight items. It's five now: theme, appearance, widget settings, and the rest. A new `AppearanceScreen` groups three sections — theme colors, course display, widget. The dead `ThemeColorScreen` and `MoreSettingsScreen` routes were deleted outright.

The "follow system" label is also split in two: light-follows-system and dark-follows-system, each its own switch.

### inWeek semantics — courses no longer forced into a start-to-end range

Single/double-week courses were being expanded into a continuous "start week to end week" span, whether that matched reality or not. Fixed. Courses now show on their actual weeks.

This means some schedules look like they lost cells after updating. They didn't. The old display was wrong. If a course only meets on odd weeks, it shows on odd weeks — not as a block running from week 1 to week 16.

### Cloud backup rules fixed

The cloud backup / device-transfer rules pointed at `settings.xml`. The actual preference file is `sleepy_prefs.xml`. Since v1.0.0, cloud backup has never actually covered your preferences. The three rule entries now point at the real file.

### Internationalization

Sixteen theme keys still had untranslated Simplified Chinese left in the English, Japanese, Spanish, and Traditional Chinese locales. Translated. Plus terminal-pass consistency: Traditional Chinese now uses 檔案/匯出/文字 across the board, Japanese uses 授業 for courses, Spanish uses Período for periods.

### ICS export

Single/double-week courses now export with `INTERVAL=2` — they were exporting as ordinary weekly courses before. Custom times (`ownTime`) export too.

### Stability

- lintRelease ran with eight errors. All cleared: notification permission checks inlined before each post (guards the coroutine window where the permission could be revoked), widget size reads wrapped in a `TIRAMISU` guard that fixes a real `NoSuchMethodError` crash on API 31/32, `ExportScreen` falls back through `FileProvider` on API 26-28.
- The reminder subsystem's `cancelAll` moved off `runBlocking` onto a coroutine — the screen no longer blocks while alarms cancel.
- Minute input debounced.
- Deleting a schedule table now explicitly cancels the before-class alarms of the courses it cascades away.

### Known issue (BG-14)

Fresh installs don't get the 15-minute fallback widget refresh — `WidgetUpdater.schedule` is a dead link. Slated for v1.0.35. If your widget goes stale, open the app and touch some data once.

### Build

- versionCode: 35
- versionName: 1.0.34
- APKs: `app-arm64-v8a-release.apk` (most phones), `app-armeabi-v7a-release.apk` (older arm32), `app-x86_64-release.apk` (emulator)

— Lingion

---

## v1.0.34

### 课程颜色拆分——一个开关变成两个

上个版本出了一个「统一课程底色」开关。一个开关干两件事:既去掉桌面小组件的颜色,又去掉 App 内课表的颜色。你想要小组件变灰、但 App 里胶囊保持彩色?不行,一个开关两头都绑死了。

现在拆成两个独立设置。

- `widget_colorless`——只管桌面小组件。行为不变。
- `course_colorless`——只管 App 内的课表和今日页。新增,默认关。

默认关意味着更新之后 App 的课程胶囊会恢复彩色,哪怕你之前把旧的统一开关开着。两边不再互相影响。

这个拆分是在 `AppPrefs` 层做的,不是只在 UI 上。两个 key 并排放着,谁也不读谁,谁也不给谁播种。没有迁移步骤——旧的小组件开关保留它存的值,新的课程开关从默认值开始。零互读,独立性没法静默退化。课程开关的 handler 特意**不**调 `refreshWidgets()`,因为它跟小组件没关系。

### CourseColorUtil——单一事实来源 + 文字亮度

课程配色逻辑原本散在四处。四份 HSL 选色的拷贝,各自漂移。这个版本把它们收敛进一个 `CourseColorUtil`,三层结构:常量、纯逻辑、平台适配。四个调用点现在指向同一个实现。

顺带修了一个真 bug:自定义课程胶囊的文字色用了固定来源,深色的自定义课程底配深色文字,根本看不清。`CourseColorUtil` 加了 `luminance` 和 `textColorOn` 两个纯函数。深色自定义底色现在出白字,浅色底保持原样。所有渲染课程胶囊的地方都接上了——App 内的卡片、lesson 行、今日卡片,还有小组件的 bitmap renderer。

### Glance 层整个删掉

Glance 框架没了。五个小组件现在全走同步的 RemoteViews + Canvas——没有 `SessionWorker`,没有那个 OPPO 渲染到一半就冻住的异步流程。`CourseColorRules` 一起删,`loadDataSync` 迁进了 receiver。

这是内部改动,用户看不到行为变化。但它就是 1.0.31/1.0.33 里那个小组件刷新问题终于到处都成立的原因:一条代码路径,没有异步竞态。

### 设置页重排

设置入口一度涨到八项。现在是五项:主题、外观、小组件设置,加其余。新增 `AppearanceScreen`,三组结构——主题色彩、课程显示、小组件。死掉的 `ThemeColorScreen` 和 `MoreSettingsScreen` 路由整个删掉。

「跟随系统」标签也拆成两个:浅色跟随系统、深色跟随系统,各一个开关。

### inWeek 语义——课程不再被硬塞进起止区间

单双周课程之前被扩成一个连续的「起始周—结束周」区间,不管符不符合实际。修了。课程现在按实际周次显示。

这意味着有些课表更新后看起来「少了几格」。其实没少。是旧的显示错了。一门只上单周的课,就显示在单周——不是从第 1 周到第 16 周拉一条。

### 云备份规则修正

云备份 / 设备迁移的规则指向了 `settings.xml`。实际的偏好文件是 `sleepy_prefs.xml`。自 v1.0.0 起,云备份从来没真正覆盖过你的偏好。三处规则条目现在指向真实的文件。

### 国际化

十六个主题 key 在英语、日语、西班牙语、繁体中文里还残留着没翻译的简体中文。翻完了。加上终审的一致性:繁体中文统一用 檔案/匯出/文字,日语课程统一用 授業,西班牙语节次统一用 Período。

### ICS 导出

单双周课程现在导出 `INTERVAL=2`——之前被当普通周课导。自定义时间(`ownTime`)也能导出了。

### 稳定性

- lintRelease 原本报 8 个 error,全清零:通知权限校验在每次 post 前内联(守住协程窗口期权限被撤销的兜底),小组件尺寸读取套 `TIRAMISU` 守卫修了一个 API 31/32 上的真 `NoSuchMethodError` 崩溃,`ExportScreen` 在 API 26-28 走 `FileProvider` 回退。
- 提醒子系统的 `cancelAll` 从 `runBlocking` 挪到协程——取消闹钟时界面不再卡死。
- 分钟输入加了 debounce。
- 删课表时现在显式取消级联删除课程的课前闹钟。

### 既知问题(BG-14)

新装用户拿不到 15 分钟兜底的小组件刷新——`WidgetUpdater.schedule` 是个死链。排在 v1.0.35。小组件要是过期了,进 App 碰一下任意数据。

### 构建

- versionCode: 35
- versionName: 1.0.34
- APK:`app-arm64-v8a-release.apk`(多数手机)、`app-armeabi-v7a-release.apk`(旧款 arm32)、`app-x86_64-release.apk`(模拟器)

— Lingion
