# Sleepy v1.0.30

### Fluid Cloud can recover after a missed reminder
Before, the before-class Fluid Cloud depended on one alarm at a single moment. If the process was killed or the alarm was delayed, it might never appear. It now checks several recovery points, including app foregrounding, timetable changes, and a periodic fallback, and starts when the current time is inside the reminder window.

### The progress bar is continuous
The Fluid Cloud progress indicator no longer jumps at a fixed 70% boundary.

### Widgets follow the active timetable
Before, a widget could select whichever timetable had the most courses. Widgets now follow the app's selected default timetable.

### Editing courses reschedules reminders
Adding, editing, deleting, or importing course data now recalculates the related reminder alarms.

### Import, search, and scheduler stability
This release fixes timetable ID collisions during import, missing week headers, discontinuous alternate-week ranges, Turkish-locale search, malformed times, and deletion without a selected timetable. Before-class alarms use stable course IDs and are no longer limited by the old fixed alarm count.

### Build
- Release tag: `v1.0.30`

— Lingion

---

# Sleepy v1.0.30

### 流体云可以从漏掉的提醒中恢复
之前，课前流体云只依赖某一刻触发的闹钟，进程被杀或闹钟延迟后可能完全不出现。现在会在回到 App、课表变化和周期兜底等多个时机检查；只要当前时间仍在提醒窗口内，就会补起流体云。

### 进度条连续显示
流体云进度条不再在固定的 70% 位置突然跳变。

### 小组件跟随当前课表
之前，小组件可能选择课程最多的课表。现在会跟随 App 当前选中的默认课表。

### 修改课程会重新安排提醒
新增、编辑、删除或导入课程后，相关提醒闹钟现在会按新课表重新计算。

### 导入、搜索和提醒稳定性修正
本版本修正了导入时的课表 ID 冲突、缺失周次表头、不连续单双周、土耳其语环境搜索、非法时间值和未选课表时删除课程的问题。课前提醒改用稳定课程 ID，不再受旧的固定闹钟数量限制。

### 构建
- Release 标签：`v1.0.30`

— Lingion
