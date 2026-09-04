## v1.0.46

Conflicts got a full rework: three-plus courses in one slot now rotate instead of hiding, the stack style works at every layer count, and saving a course that collides shows exactly what it hits before anything is written. The bottom bar became a floating dock. Settings got tabbed and a lot less wordy. 31 commits.

### New

**Conflicts with three or more courses rotate instead of hiding** (#10)

A slot with three overlapping courses used to show two and fold the rest into a corner. Now the grid always shows two layers, and tapping the covered part advances the rotation: layers 123 → 231 → 312 → back. A "+N" bubble counts what's covered (+1 for three layers, +2 for four). Tap the bubble for a picker that jumps straight to any course. The rotation is session-only: switching weeks or leaving the page resets to your pinned order, and a full cycle always brings your pin back on its own.

**Saving a course now tells you exactly what it collides with**

Saving a course that overlaps existing ones used to be blocked with a vague list of days, some of them days you never touched. Nothing is blocked anymore. Instead, on collision a dialog spells out every hit: which day, which periods, which weeks (real intersection: if yours runs weeks 1–16 and the other course weeks 5–8, it says 第5-8周, with 单周/双周 qualifiers where they apply), and against which course. "Save anyway" writes it; "Go back" doesn't. Editing a course never reports a collision with itself.

**Stack style works at every layer count**

With the stack (叠层偏移) conflict style, a slot with three or more courses silently rendered as folded corners. That rule predates rotation; with rotation carrying layer identity now, it's gone. Stack stays stack at any depth. Fold and rail styles unchanged.

**Floating dock bottom bar**

Setting → 画面 → 底栏样式 offers 贴底 (classic, unchanged) and 悬浮, a macOS-style floating pill that hovers over the content with a sliding color thumb: the highlight physically springs between icons, and labels light up character by character as it passes. Scrolling content runs under it to the screen edge.

**High refresh rate**

On 90/120 Hz screens the app now pins its window to the highest available mode. Setting → 画面 → 高刷新率, on by default. Turn it off if you'd rather trade smoothness for battery.

**Settings, tabbed and decluttered**

通用设置 is now a tabbed page (主页显示 / 通用 / 画面 …) instead of one long scroll. Pure choice settings (course time display, default start page, unified capsule color) render as single rows with the choices inline. Eight redundant subtitles removed: if the title says 网格视图缩放, the row doesn't need a sentence explaining what a slider does.

**Two independent inset sliders for conflict styles**

The old "top inset" slider fed both the stack offset and the rail width. Now 叠层偏移 gets 偏移量 and 侧边竖轨 gets 右缘让宽, each visible only under its own style. Your old value migrates to both.

**Chongqing University** (my.cqu.edu.cn)

Added to direct JW import (149 schools). CQU runs its own portal REST API rather than the standard jwapp family; the adapter goes through the portal's session/table/time-pattern endpoints with a Bearer token from the WebView session. Thanks 时光 for the protocol write-up (credited in the About page).

### Fixed

**Week editor no longer jumps back after saving**

Edit a course while viewing 第 3 周, save, and the pager yanked you back to the real current week. It stays on week 3 now. The "back to this week" button still works as before.

**Status bar icons follow the in-app theme**

The system only reads the status-bar style once at launch, so switching the app from dark to light left white icons on a white bar. Icon brightness now tracks every theme change, including the in-app manual toggle.

**Third-party login fixture matrix**

One fixture file had been missing since the detection-page landed, so the parser test matrix failed on every run even though nothing was wrong. The fixture is in; the suite is 824 green.

### Settings copy pass

节假日数据源 collapsed to one line; the holiday gray-style picker moved inline into its row; the badge picker dialog now matches the table-switcher dialog's row layout (check on the top item, same background tokens). Nothing behavioral — pages are just shorter.

### Tests

The rotation line alone added 60+ unit tests (rotation kernel, layer-order override, badge counts, fold end-states, stack-at-depth, collision detail reporter). Full suite: 824, zero failures.

### 构建

- versionCode: 47

---

### 新增

**三课以上的冲突格轮换,不再藏着掖着**(#10)

一格三门课重叠,以前只露两层,剩下的折进角里。现在网格永远显示两层,点被盖住的部分轮换推进一层:123 → 231 → 312 → 循环。"+N" 气泡数被盖住的层(三层 +1,四层 +2)。点气泡直接弹列表跳到任何一门课。轮换只在当次会话有效——翻周或离开课表页就回到你的置顶层,轮满一圈你的置顶也会自己转回来。

**保存冲突课,先讲清楚撞了谁**

保存和已有课重叠的课,以前直接拦下,还列一串你没碰过的星期。现在不拦了。撞车时弹窗一条条列明白:星期几、第几节到第几节、哪几周(真实交集——你的课 1-16 周、对方 5-8 周,就写 第5-8周,单周/双周照实带)、撞的哪门课。「仍然保存」落库,「返回修改」不存。编辑一门课不会跟它自己报冲突。

**叠层偏移样式在任意层数都生效**

选叠层(叠层偏移)样式时,一格三层以上会被悄悄渲染成折角。这条规则比轮换更老;层身份现在由轮换承载,规则撤销。叠层在几层都是叠层。折角、竖轨不受影响。

**悬浮 Dock 底栏**

设置 → 画面 → 底栏样式,可选 贴底(原样)和 悬浮——macOS 式悬浮药丸,浮在内容上方,色块高亮在图标间物理弹簧滑移,文字被色块扫过时逐字变亮。滚动内容从它底下直通屏幕底。

**高刷新率**

90/120Hz 屏幕上,窗口现在钉在最高刷新率。设置 → 画面 → 高刷新率,默认开。想省电可以关。

**设置页 tab 化,啰嗦话删光**

通用设置从一条长滚动改成 tab 页(主页显示 / 通用 / 画面…)。纯选择项(课程时间显示、启动默认页、课程胶囊统一底色)变成单行平铺直选。八条冗余副标题删了——标题写着「网格视图缩放」,就不需要一句话解释滑杆是干嘛的。

**冲突样式两根滑杆各管各的**

原来的「顶卡收窄量」一根滑杆同时喂叠层偏移和竖轨宽度。现在 叠层偏移 有「偏移量」,侧边竖轨 有「右缘让宽」,各自样式下才显示。旧值迁移到两处。

**重庆大学**(my.cqu.edu.cn)

教务直连导入新增重庆大学(149 所)。重大是自建门户 REST,不走标准 jwapp 协议族;适配器走门户的 session/课表/作息三接口,Bearer token 从 WebView 会话取。感谢 时光 的协议分析(关于页已致谢)。

### 修复

**第 x 周编辑保存后不再跳回真实周**

在第 3 周视图里编辑课程,保存后 pager 把你拽回当前真实周。现在停在第 3 周。「回到本周」按钮照常。

**状态栏图标跟着应用内主题走**

系统只在启动时读一次状态栏深浅,应用内手动从深色切浅色,白底上就剩白图标看不见。现在图标深浅跟每一次主题变化,包括应用内手动切。

**第三方登录指纹矩阵**

一个 fixture 文件从检测页上线起就缺着,解析器测试矩阵逢跑必挂,虽然啥事没有。补上了。全套 824 个测试全绿。

### 设置文案瘦身

节假日数据源压成一行;灰显样式选择器收进所在行;冲突徽标选课弹窗改成和课表切换弹窗同一套行样式(顶层带勾,同套底色 token)。没有行为变化——就是页面变短了。

### 测试

光轮换这条线就新增 60 多个单元测试(轮换内核、整层序重排、徽标计数、折角端态、任意层数叠层、冲突明细报告器)。全套 824 个,零失败。

### 构建

- versionCode: 47
