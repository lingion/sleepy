# Sleepy v1.0.55

> Courses grouped into a section can now have their shared color changed from the course editor; the direct-import desktop mode now really switches to the desktop layout on UCAS; the schedule pager stops fighting itself after switching timetables; custom theme cards match the presets in size; following-system dark mode reacts instantly; the Honor widget family gets seven fixes; 华东政法大学 imports via its real EAMS portal.

## What's New

### Change a group's shared color

Editing any course in a group (courses that share the same name and are colored together) now offers a "跟随组色" (follow group color) row with a change-color entry. Picking a color opens the palette and a confirmation dialog (changing the group color applies to every session in the group that follows it), and sessions with their own manual color keep it. Without a custom group color the row shows "自动(黄金角)" (auto, golden-angle) and a neutral swatch.

### UCAS desktop mode actually switches (issue #18)

Tapping the desktop-site toggle in the direct-import browser used to leave SEP (sep.ucas.ac.cn) on the phone layout. The page's layout is driven by its CSS breakpoint at 980px, not by the user agent, so swapping the UA string changed nothing. Desktop mode now pins the page viewport to 1024px after the page loads, which flips SEP to its desktop grid; the browser keeps the phone layout everywhere else.

### Schedule stops jumping between weeks

Switching between two timetables could leave the week view flickering back and forth across several weeks. The two-way sync between the pager and the week state fed back into itself during programmatic scrolling. Scrolling is now marked as program-driven for its duration, and the week stays where it was asked to go.

### Custom theme cards match the presets

A custom theme card used to be taller than the five preset cards, because the edit button on it carried a minimum touch-target size. The edit entry moved to the right end of the color-swatch row as a small block, and the card now has exactly the same size and spacing as the presets.

### Following-system dark mode reacts instantly

With appearance mode set to "跟随系统" (follow system), switching the system between light and dark didn't change the app until restart, because the Compose snapshot froze at launch. The app now follows the switch immediately within the same session. This also fixes a startup crash introduced by an intermediate test build (versionCode 60) that read resources before they were attached.

### Honor widget fixes (issue #31)

- The back-to-today control on the 2×2 today widget is now a real refresh-icon button, the same size as the prev/next arrows, and tapping it returns to today. The old text label was cut off on narrow sizes and could not be tapped; on the 2×2 the header now shows the three buttons (prev / refresh / next) instead.
- The 2×2 today widget no longer draws bare ‹ › glyphs in its header; on MagicOS they looked like buttons that did nothing when tapped.
- On MagicOS, tapping the prev/next arrows opened the app instead of switching; the tap intents are rebound so the arrows switch again.
- When the header can't fit the whole nav row, the entire row is hidden instead of shrinking into an unreadable strip.
- Content taller than the widget now scrolls inside the 4×5 weekly widget instead of being cut off.
- Changing the system font size re-pushes widget layouts immediately, and the top-bar layout and large text now match the same scale.

### 华东政法大学 import fixed

The school entry pointed at jw.ecupl.edu.cn, a Sudy portal that isn't a教务系统. The entry now points at the real academic system jwxt.ecupl.edu.cn (EAMS, classic_eams type).

## Fixes

- Replaced the back-to-today text on the 2×2 today widget with a real refresh-icon button that returns to today (issue #31).
- On faces without buttons (a 2×2 widget squeezed below the nav row's minimum width, or content taller than the widget), the back-to-today text is no longer drawn — an element that can't be tapped doesn't belong on screen. The date title stays.
- Removed the bare ‹ › glyphs from the 2×2 today widget header (issue #31).
- Rebound the prev/next tap intents so arrows switch weeks on MagicOS instead of opening the app (issue #31).
- Hide the whole widget header nav row when it can't fit, instead of shrinking it (issue #31).
- Made the 4×5 weekly widget scrollable when content overflows (issue #31).
- Re-push widget layouts immediately when the system font scale changes (issue #31).
- Fixed the week view flickering across weeks after switching timetables.
- Made following-system light/dark react within the session, and fixed the startup crash from the intermediate test build.
- Fixed the UCAS desktop-mode toggle having no effect on the SEP layout (issue #18).
- Corrected the 华东政法大学 entry to its real EAMS academic system.

## Known Limitations

- The UCAS desktop layout is applied by pinning the viewport after page load; if a page re-renders late, tapping the in-app refresh button re-applies it.
- On the 2×2 today widget the date title is hidden on narrow sizes; the three nav buttons (prev / refresh / next) take the row.
- Honor/MagicOS behavior was verified on the reporter's device (Win RT, MagicOS 10) via log analysis and layout reasoning, not on a physical device in hand.

## Verification

- Tests: focused (SchedulePagerSyncRace, SepXrw strip/interceptor, ThemeFollowSystem, CustomThemeCardVisualParity, NavHeaderFit, CourseColorUtil) + full suite: 1715 tests / 0 failures / 0 errors
- APK SHA-256:
  - arm64-v8a: 57a5ba750f2b1c9c6529df1b1650827ea87c5a50e04a4241312d5434b3bab09c
  - armeabi-v7a: c1eecaba0d3eb536a0252bd47590b2b9825edff9a5d5f295324d1d3d21a549b5
  - x86_64: 16f7fe93edffba35b8aee8bd3d8dec66e59e28bbe29f24923f53658405b8a253
- Build: versionName 1.0.55 / versionCode 61

---

# Sleepy v1.0.55

> 编辑课程现在可以修改整组共享颜色;直连导入的桌面模式在 UCAS 上真正切到桌面布局;课表页切换课表后不再反复跳周;自定义主题卡与预设卡同尺寸;跟随系统深浅色即时生效;荣耀小组件系列七修;华东政法大学改走真教务 EAMS。

## 新增功能

### 修改整组共享颜色

编辑组内任意课程(同名一起配色的一组)现在有「跟随组色」一行,带改色入口。选色先弹调色盘再弹确认对话框(组色会应用到组内所有跟随组色的节次),单独设过颜色的节次保持不动。没有自定义组色时该行显示「自动(黄金角)」和中性色块。

### UCAS 桌面模式真正切换(issue #18)

直连导入浏览器里点桌面模式按钮,SEP(sep.ucas.ac.cn)以前仍是手机布局。页面布局由 980px 的 CSS 断点驱动,与 User-Agent 无关,换 UA 字符串没有效果。现在桌面模式会在页面加载完成后把视口钳到 1024px,SEP 随之切到桌面网格;其余站点保持手机布局不变。

### 课表页不再反复跳周

两张课表间切换后,周视图可能在多个周之间来回跳变。Pager 与周次状态的双向同步在程序化滚动期间互相触发形成回路。程序化滚动全程被标记,周次停在指定位置。

### 自定义主题卡与预设卡同尺寸

自定义主题卡以前比五张预设卡高,卡上的编辑按钮带了最小点击目标尺寸。编辑入口移到色板行右端的小色块上,卡片尺寸与间隙和预设卡完全一致。

### 跟随系统深浅色即时生效

外观模式选「跟随系统」时,系统切换深浅色以前要重启 app 才生效,根子是 Compose 快照在启动时冻结。现在会话内切换立即跟随。同时修复了中间测试包(versionCode 60)在资源就绪前读取导致的启动秒崩。

### 荣耀小组件修复(issue #31)

- 2×2 今日小组件的回到今天控制改成真实的刷新图标按钮,与左右箭头同尺寸,点击即回到今天。原来的文字标签在窄档被裁掉点不了;2×2 顶栏现在显示三颗按钮(prev/刷新/next)。
- 2×2 今日小组件头部不再画裸 ‹ › 字形,在 MagicOS 上它们看起来像点了没反应的按钮。
- MagicOS 上点 prev/next 箭头会打开 app 而不是翻周;点按意图已重绑,箭头恢复翻周。
- 头部放不下整条导航行时,整行隐藏,不再缩成一排看不清的字。
- 内容超高时,4×5 每周小组件内可滚动,不再截断。
- 系统字号变化后立即重推小组件布局,顶栏布局与大字同一比例。

### 华东政法大学导入修复

学校条目原来指向 jw.ecupl.edu.cn,那是 Sudy 门户,不是教务系统。条目改指向真教务 jwxt.ecupl.edu.cn(EAMS,classic_eams 类型)。

## 修复

- 2×2 今日小组件的回到今天文字换成真实刷新图标按钮,点击回到今天(issue #31)。
- 无按钮的面上(2×2 小组件缩到导航行最小宽度以下,或内容超高时)不再画「回到今天」文字——点不了的元素不该出现在屏幕上。日期标题保留。
- 删除 2×2 今日小组件头部的裸 ‹ › 字形(issue #31)。
- 重绑 prev/next 点按意图,MagicOS 上箭头翻周不再打开 app(issue #31)。
- 头部导航行放不下时整行隐藏,不再缩小(issue #31)。
- 4×5 每周小组件内容超高时内部可滚动(issue #31)。
- 系统字号变化立即重推小组件布局(issue #31)。
- 修复切换课表后周视图跨周反复跳变。
- 跟随系统深浅色会话内生效,修复中间测试包的启动崩溃。
- 修复 UCAS 桌面模式开关对 SEP 布局无效(issue #18)。
- 华东政法大学条目改指向真教务 EAMS。

## 已知限制

- UCAS 桌面布局靠页面加载后钳视口实现;个别页面若重排较晚,点应用内刷新按钮会重新应用。
- 2×2 今日小组件在窄档隐藏日期标题,三颗导航按钮(prev/刷新/next)占满这一行。
- 荣耀/MagicOS 行为通过报告人机型(Win RT,MagicOS 10)的日志分析与布局推演验证,未在手上真机完成验证。

## 验证

- 测试:定向(SchedulePagerSyncRace / SepXrw 拦截器 / ThemeFollowSystem / CustomThemeCardVisualParity / NavHeaderFit / CourseColorUtil)+ 全量 1715 例:0 失败 / 0 错误
- APK SHA-256:
  - arm64-v8a: 57a5ba750f2b1c9c6529df1b1650827ea87c5a50e04a4241312d5434b3bab09c
  - armeabi-v7a: c1eecaba0d3eb536a0252bd47590b2b9825edff9a5d5f295324d1d3d21a549b5
  - x86_64: 16f7fe93edffba35b8aee8bd3d8dec66e59e28bbe29f24923f53658405b8a253
- 构建:versionName 1.0.55 / versionCode 61
