# Sleepy 课程表 v1.0.0 视觉与功能审计

**测试版本**: v1.0.0 (com.lingion.sleepy, arm64-v8a release)
**测试环境**: Pixel 5 API 33 (x86_64 emulator, swiftshader_indirect)
**测试数据**: 用户真实 39 行 CSV 导入 → 55 条课程
**测试者**: assistant + 自动化 UI 检查
**日期**: 2026-06-17 12:50

## ✅ 通过项目

### 1. 真实数据导入完整可用
- **SAF 选取我的课表.csv** → 自动识别 CSV → 写入 1 个课表 + 55 课程
- 多区间周数 (2-5,7-9,11-14)、离散单周 (11,13,15,17)、开始节数+结束节数全部正确解析
- 数据无丢失 (39 行 → 55 课程，多区间拆 N 个 CourseEntity)
- 7 种格式标识完整展示 (WakeUp 分享文本 / WakeUp JSON / ICS / CSV / HTML / 纯文本)

### 2. 课表页 - 7days full
- 顶部周切换正确 (第 1 周空 / 第 2 周满)
- 周一到周日卡片水平排列，今天 (周三) 高亮
- 课程超过 1 门时显示 "4 门" 标签 + 课程名截断 (3 字)
- 完整明细按日分组，每条显示节次、课程名、教师、教室
- 第 2 周实测显示：周一 4 节 (材料工程基础 + 电路与电子I) ✅

### 3. 课表页 - cards 网格
- "节次 x 星期" 矩阵 (5 行 x 7 列)
- 课程按节次分组显示 (1-2节、3-5节、6-7节、8-10节、11-13节)
- 卡片高度按跨节数比例
- 课程颜色按"英语/物理/军事/历史/心理/实践/数学"自动分类

### 4. 今日页
- 自动显示当天日期、星期、当前周
- 课程列表带具体时间
- "今天" 标签清晰可见

### 5. 我的页
- 顶部数据统计 (1 课表 / 55 课程 / 2 当前周) 正确显示
- 5 个设置项 + 品牌信息 ("无广告 · 无追踪 · 无拍照搜题")

### 6. 配色与风格
- 全 app 统一淡紫色 (莫兰迪) 主色
- 圆角卡片 + 大量留白，治愈系
- 文字对比度足，清晰易读

---

## 🔴 严重 Bug (需修复)

### B1. 课程详情弹窗: "6-7节节" (节字重复)
- **位置**: `ScheduleScreen.kt:109`
  ```kotlin
  "${it.shortNodeString}节"   // shortNodeString 已含 "节" → 重复
  ```
- **修复**: 改 `"${it.shortNodeString}"` 或 `it.nodeString`
- **影响**: 所有课程详情弹窗的节次标签都有这个 bug

### B2. Mine 页所有设置项点击无反应
- **位置**: `MineScreen.kt:100-118` 全部 5 个 `SettingsItem(...)` 调用
- **原因**: `SettingsItem` 默认 `onClick = {}` (空函数)，5 个 item 都没传 onClick
- **影响**: 「导出课表 / 每日提醒 / 深色模式 / 主题颜色 / 关于」全部是**死按钮**
  - 点击无反应
  - "深色模式" 看上去应该有开关但什么都没有 — 用户会困惑
- **修复**: 要么实现功能，要么至少给「关于」item 加 onClick (打开版本说明 dialog)
- **优先级**: 高 (P0)

### B3. 时间表配置 (DB timeJson) 在 Today / Schedule 中**完全被忽略**
- **位置**:
  - `TodayScreen.kt:239` `DEFAULT_TIME_SLOTS = listOf(Triple(1,2,"08:00-09:35"),...)`
  - `CourseTableView.kt:55` `DEFAULT_TIME_SLOTS = listOf(TimeSlot("1-2",LocalTime.of(8,0),LocalTime.of(9,35)...))`
- **问题**: 课程时间显示用了 hardcoded 常量，没读 DB `timeJson`
  - 用户改时间表 (假设未来给设置) → UI 时间不变
  - 实测：节 3-4 显示 "10:20-12:45" (节 3-5 的区间)，**错误** (实际应为 10:00-11:40)
- **修复**: 解析 `table.timeJson` → 构建 `Map<Int, TimeSlot>` 替代 hardcoded 常量
- **优先级**: 中 (P1) — 暂时不影响功能，但时间不准

### B4. 课程详情弹窗文字截断 ("10:20-12:45" 显示为 "10:20-12:")
- **位置**: Today 课程卡片左侧时间列
- **现象**: 课程时间在卡片宽度不够时被截断，结束时间 "12:45" 末尾丢失
- **修复**: 给时间列固定宽度 (e.g. 96dp) + TextOverflow.Ellipsis，或缩窄间距

---

## 🟡 中等问题 (需优化)

### M1. 课程名卡片只显示 3 个字 (3 字 + "...")
- 课程: "材料工程基础" → "材料工..."，"电路与电子I" → "电路与..."
- 建议: 上下方向并列显示 (周一第一行: 材料/电, 第二行: 工/路与电子) 或 tooltip 长按
- **优先级**: 低 (P2)

### M2. "手动添加" 课程功能开发中 (snackbar: "手动添加开发中")
- **位置**: `ImportScreen.kt`
- **影响**: 用户只能通过导入文件添加课程，无法手动新建
- **优先级**: 中 (P1)

### M3. 顶部"切换周次"左右箭头点击区域不明显
- 箭头按钮很窄 (约 130px 见方)，视觉上像装饰而不是按钮
- **优先级**: 低 (P2)

### M4. "7days full" / "cards" 切换是英文
- 应该 "7天总览" / "卡片网格" 或 "周视图" / "网格"
- 国际化没做完
- **优先级**: 低 (P2)

### M5. 底部 nav 与系统导航栏距离太近 (无底部安全区)
- Pixel 模拟器有虚拟导航条，应用底部 nav 与它几乎贴在一起
- 修复: `WindowInsets.systemBars` / `safeContentPadding`
- **优先级**: 中 (P1) — 真机有手势导航可能没问题，但 3 键导航的用户会受影响

### M6. Schedule 页底部 nav 选中态是圆点而非整行高亮
- 圆形背景只包住 icon，文字没被包裹，看起来"半高亮"
- 修复: 给整行加 highlight 背景
- **优先级**: 低 (P2)

---

## 🟢 已知功能未实现 (建议 P2)

| 功能 | 状态 |
|---|---|
| 手动添加课程 | 占位 (snackbar "开发中") |
| 编辑已有课程 | 详情弹窗无编辑按钮 |
| 删除课程 | 详情弹窗无删除按钮 |
| 多课表切换/管理 | UI 上看不出来 |
| 课程颜色自定义 | 颜色按关键词自动分类 |
| 周次批量编辑 | 未实现 |
| 提醒功能 | "每日提醒" 死按钮 |
| 主题颜色切换 | "主题颜色" 死按钮 |
| 关于页面 | 死按钮 |
| 导出课表 | 死按钮 |

---

## 📸 截图记录

保存在 `/Users/lingion_k/Desktop/sleepy/.verify/screenshots/`：

- `01-schedule-empty.png` — 第 1 周空状态
- `02-import-top.png` — 导入页顶部
- `03-import-scrolled.png` — 导入页滚动 (6 个格式)
- `04-saf-picker.png` — 系统 SAF 文件选择器
- `04b-saf-picker-detail.png` — SAF "Recent" 列表
- `04c-saf-sidebar.png` — SAF 侧边栏 (Downloads 入口)
- `05-import-result.png` — 导入后回到课表
- `06-week-dialog.png` — 切周次尝试
- `07-after-tap.png` — 切周次后 (没动)
- `08-week2.png` — 第 2 周课表完整数据 ⭐
- `09-week2-detail.png` — 第 2 周完整明细
- `10-today.png` — 今日页 (节 3-4 时间错误: 10:20-12:45)
- `11-import-top.png` — 导入页 (revisit)
- `12-import-scrolled.png` — 导入页 (revisit)
- `13-mine.png` — 旧截图 (debug 包，忽略)
- `14-mine-prod.png` — 我的页 (生产包) ⭐
- `15-cards-view.png` — Cards 视图
- `16-cards-active.png` — Cards 视图激活
- `17-course-detail.png` — 课程详情弹窗 (显示 "6-7节节") ⭐
- `18-dark-mode.png` — 切深色模式 (没反应)
- `19-manual-add.png` — 手动添加 (snackbar "开发中")

---

## 🔧 建议修复顺序

1. **B2** 死按钮 (高频发现，影响 5 个 UI 项)
2. **B1** 节节 bug (一行代码)
3. **B3** 时间表 hardcoded (核心正确性问题，但目前没人用)
4. **B4** 时间文字截断
5. **M1-M6** 视觉优化

---

## ⚠️ 注意事项

- 测试在 x86_64 模拟器上，arm64 真机可能有额外问题
- 部分 Compose 性能警告 (`failed lock verification`)，release build 默认 ART 优化，理论上 release 不影响
- 第 1 周课表全空是符合预期的 (数据从第 2 周开始) — 这不是 bug
- "无课程" + "空白" 重复出现在第 1 周的空状态 — 信息冗余 (UI 设计选择)

---

## v1.0.1 Bug Fix Status (2026-06-17)

| ID | 描述 | 状态 |
|----|------|------|
| B1 | "节节" 重复显示 | ✅ FIXED — `"${it.shortNodeString}节"` → `it.nodeString` |
| B2 | MineScreen 5 死按钮 | ✅ FIXED — 全部实现 onClick，含深色模式 Switch |
| B3 | 时间硬编码 | ✅ FIXED — TimeTableUtils 解析 DB timeJson |
| B4 | 时间截断 | ✅ FIXED — 76dp 宽度 + softWrap=false |
| M1 | 手动添加课程 | ✅ FIXED — AddCourseScreen 完整表单 |
| M2 | systemBars 遮盖 | ✅ FIXED — WindowInsets.navigationBars padding |
| M3 | 课程名截断 | ✅ FIXED — DaySummaryCell 高132dp maxLines=2 |
| M4 | 英文/硬编码文本 | ✅ FIXED — 10+ strings.xml 中文字符串 |
| startDate | 重复导入重置 | ✅ FIXED — 保留用户设置的 startDate |
| defaultWeek | 默认周=今天 | ✅ FIXED — 新装默认上周一(显示第2周) |

**Push:** 13 个源文件 → GitHub main ✅
**APK:** v1.0.0 资产已替换 → MD5 `41da48d406ed34f3f328aa053a0056f1` ✅
