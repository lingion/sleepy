[evidence=A] 抓取 2026-09-13 · 源URL: https://dev.vivo.com.cn/webapi/doc/info?id=834 (设计理念及简介) + id=840 (原子组件体系) + id=841 (原子组件产品规范) + id=842 (交互规范) + id=835 (视觉规范) + id=843 (动效规范) + id=845 (UI适配技术规范) + id=927 (OriginOS 6 开发者预览版) + id=103 (全面屏适配) + id=573/582 (分屏/小窗适配) + id=934 (三方接入vivo物理滑动方案)

# OriginOS 桌面 launcher 行为 (vivo/iQOO)

## 华容网格 — 桌面底层架构 (doc 834)

- OriginOS 以"本原做设计,设计为本原"为理念,桌面架构首创"华容网格":
  对桌面结构重新分割计算,求解出"白银分割率"网格,保证桌面图标和组件在
  **任意排列组合下**都拥有对齐的行距与视觉一致性。
- 传统安卓桌面图标与组件混排会出现的行间距不统一、无法对齐问题,在
  华容网格下由桌面侧统一处理 — 第三方 App 无需自行对齐。
- doc 840: 原子组件体系"基于 Android 小部件并结合 OriginOS 桌面华容网格,
  对设计与技术进行了规范,并持续迭代"。

## 原子组件三规格与网格适配 (doc 845)

原子组件仅支持三种规格 (其他尺寸不支持):

| 规格 | 建议minWidth | minWidth范围 | 建议minHeight | minHeight范围 |
|---|---|---|---|---|
| 2x2 | 120dp | >80 且 ≤160dp | 120dp | >80 且 ≤160dp |
| 4x2 | 280dp | >240 且 ≤320dp | 120dp | >80 且 ≤160dp |
| 4x4 | 280dp | >240 且 ≤320dp | 280dp | >240 且 ≤320dp |

- `targetCellWidth` / `targetCellHeight` 定义占用格子数。
- **ResizeMode 失效**: 原生 `android:resizeMode` (拖拽改大小) 原子组件不支持,
  必须声明 `android:resizeMode="none"` 或删除该声明 — 用户不能手动拖拽改变
  组件大小,尺寸锁定在三种规格。
- 根布局建议 `match_parent`,禁写固定值; 背景不要用一张整图 (切布局会拉伸)。

## 组件内存与刷新约束 (doc 841/829, 原文)

- **组件在桌面的内存占用不得超过 10M**。
- **组件的被动间隔刷新时间 ≥ 12h** (`updatePeriodMillis` 示例值 43200000 = 12h)。
- 刷新场景三类: 应用主动刷新 / 用户主动触发 / 按设置间隔刷新 (≥12h)。

## 交互硬约束 (doc 842/829)

- 组件内**仅支持点击**,全局禁止滑动、长按、拖拽手势。
- 禁止组件内 Tab/Switch 切换内容 — 需要切换就拆成独立原子组件。
- 最小点击热区 36dp (5mm 物理尺寸)。
- 设置入口禁止出现在一级界面,统一放长按气泡菜单 ("组件设置")。
- 每个组件最多 4 个气泡快捷菜单 (`shortcutinfo0..3` meta-data,
  value 格式 `action/packagename/classname/extravalue` 以"/"分隔)。

## 视觉规范要点 (doc 835)

- 设计基准 = "4x7 布局无标题大尺寸",其他布局由**桌面统一缩放处理**。
- 组件开发用直角,桌面框架自动裁切圆角 (圆角统一 20dp / 平滑圆角 60%)。
- 安全边距统一 14dp (背景图/蒙层不受限)。
- 深色资源 + 浅色资源同名配对; 预览图需深/浅两套、3 倍图 PNG。

## 动效规范 (doc 843/845)

- 支持**缩放、位移、旋转、透明度**四种补间动画; 时长建议 200-800ms。
- **不支持 SVG、Lottie、PAG 动效格式**。
- 原生实现只能用 View 的 `layoutAnimation` 属性 (XML 声明,View add/remove 时执行)。
- 组件状态主动/被动切换需有动画过渡,纯文本类允许硬切。

## RemoteViews 工程约束 (doc 845 §9, 原文)

- **禁止复用 RemoteViews**: 每次更新前重新构建,复用导致内部操作记录表
  只增不减 → 内存泄露和 OOM。
- **避免 RemoteViews 传递 Bitmap**: 单个或累计超过 **100K** 即视为大数据,
  跨进程 (原子组件进程 → AppWidgetService → 宿主显示进程) 极易 binder
  传输失败。`setImageViewBitmap` 不建议使用,建议 `setImageViewUri()`。
- 耗时操作 (文件读取/数据库访问/字体加载) 必须放子线程。
- 组件内禁止上下左右滑动。

## 端内引导添加能力 (doc 845 §7/§8)

- **跳转组件库详情页**: 需申请权限
  `<uses-permission android:name="com.bbk.launcher2.permission.JUMP_ORIGIN"/>`,
  显式 intent 跳 `vivo://com.bbk.launcher2/origin?pkg=..&classname=..&comType=0&locType=1`。
  需 OriginOS ≥14.0 (`ro.vivo.os.version`),**pad 不支持**。
- **一键添加到桌面**: 走原生 `requestPinAppWidget()`,成功回调用
  `PendingIntent.getBroadcast(..., FLAG_UPDATE_CURRENT | FLAG_MUTABLE)`
  (两个 Flag 必选)。**该能力需经过原子组件平台审核并测试通过才支持** —
  未上架/未审核时调用无任何效果 (见 atomic-widget-dev-notes.md pin 矩阵)。

## 物理滑动方案 (doc 934, 三方接入)

- vivo 定制物理滑动反馈: 推荐**直接用系统控件** — `AbsListView`/`ScrollView`/
  `HorizontalScrollView` 及子类、`RecyclerView`、`NestedScrollView`,合入物理
  滑动修改的 vivo 机型上自动生效。
- 自定义控件需用框架 `android.widget.OverScroller` 做滑动计算才能获得物理反馈。
- **非 Android 原生实现 (Flutter 等) 目前没有方案接入**。
- 对 Sleepy 关联: 挂件内的 ListView 滚动行为与本方案同源 (系统控件优先)。

## 分屏/小窗 (doc 573/582)

- 全局小窗基于安卓原生多窗口,需声明 `android:resizeableActivity="true"`;
  `configChanges` 处理 `screenSize|smallestScreenSize|screenLayout|orientation`。
- 小窗模式下应用顶部有功能 bar,沉浸式需为 captionBarInsets 预留空间,
  **不要用固定高度** (监听 WindowInsetsListener)。
- Activity 需设置独立 `android:taskAffinity` — 继承 rootActivity 的多窗口属性
  会导致无法启动到小窗 (与 Sleepy Today nav configure 竞态修复同构问题)。
- 分屏下屏/小窗无状态栏,不应留状态栏高度,需从顶部开始布局。

## OriginOS 5 → 6 桌面变化 (doc 927 + 官方 FAQ 摘要)

- OriginOS 5: 新增原子岛、小V搜索、小V建议、景深壁纸、**锁屏小组件**
  (步数/温度/闹钟/日程等,设置 > 桌面、锁屏与壁纸 > 锁屏编辑)、
  妙玩组件 (i主题内,样式多于原子组件)。
- OriginOS 6 开发者预览版 (2025-10, 仅内测机型): 全新时钟样式 (动态时钟调节)、
  原子岛音乐中卡律动动效、原子岛环境光、音乐锁屏、symbols 全局可变图标、
  质感图标 (光影分层)、**官方图标包与三方主题图标包独立更换**。
- 变形器: OriginOS 1.0 时代图标变形需切"平行世界"; OriginOS Ocean (2.0) 起
  两世界合并,变形器直接调图标尺寸/风格 (精美/简约),平行世界入口已移除
  (极客公园 2021-12 体验稿, evidence=C)。图标可调 1x1/1x2/2x2 等尺寸 —
  **cell 实际 dp 随用户变形器设置变化**,widget 需依赖 options 读取真实 cell
  (Sleepy 已覆盖, 见 atomic-widget-dev-notes.md)。

## 桌面图标角标与长按快捷方式 (doc 459/460/787, 原文)

- 桌面角标 (数字角标) 支持平台: **FuntouchOS 全系 + OriginOS 1.0/2.0/3.0/4.0** 全 ✓。
- 双通道: 系统私有 API (仅进程存活时可更新) 与 Vpush (不受进程存活状态影响);
  用桌面角标必须适配系统私有 API,Vpush 按业务需求选接。
- Funtouch 接口: 广播 `launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM`
  (packageName/className/notificationNum),权限
  `com.vivo.notification.permission.BADGE_ICON`,Android 8.0+ 需
  `FLAG_RECEIVER_INCLUDE_BACKGROUND`。
- OriginOS 新接口 (内销 Funtouch ≥12.0 / 外销 vos ≥2.0):
  ContentProvider `content://com.vivo.abe.provider.launcher.notification.num`,
  权限 `com.vivo.abe.permission.launcher.notification.num`,
  call 方法 `change_badge`/`add_badge`/`reduce_badge`;
  **必须用 `acquireUnstableContentProviderClient` 建非稳连接**,直接
  getContentResolver().call 有 Server 端崩溃带崩 Client 端的风险。
  新接口返回码 1000-1040/2004/2005/999998 (2004=未读消息角标总开关未开,
  2005=应用角标开关未开,1013=调用者和目标包名不一致)。
- 桌面角标显示规范 (doc 782): 数字需与 App 内未处理消息条数匹配。
- 长按快捷方式 (doc 460): vivo 桌面 v9.3.0 起开放三方自定义 shortcuts
  (Android 7.0 特性,静态/动态注册,action MAIN + category LAUNCHER 的
  activity 才能配置); 最多显示 4 个自定义功能; 系统默认功能
  (卸载/应用信息/编辑桌面) 不支持自定义。
  shortcutLongLabel/shortcutShortLabel/shortcutDisabledMessage 的 string
  必须写在 strings.xml,否则无编译。

## 全面屏/异形屏 (doc 103/195)

- vivo 主流分辨率: 1080x2400/2460/2520 等高长宽比; 审核规范第六节列出
  全部机型分辨率表 (X Fold5 展开 2200*2480 / iQOO 15 3168*1440 /
  vivo Pad6 Pro 3840*2512 等, Android 9.0-16)。
- 布局禁固定屏幕比例判断; 模拟验证法: `adb shell wm size 1080x2400` 后
  `wm size reset` 恢复。
