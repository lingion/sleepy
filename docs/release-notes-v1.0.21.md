# Sleepy v1.0.21

### Course colors are deterministic and customizable
Before, keyword-based colors could assign similar colors to similarly named courses. Automatic colors now use a deterministic golden-angle mapping, and the course editor provides an HSV picker. A manual color takes precedence.

### Long and multi-period courses render correctly
Before, long courses could collapse the time column, become unreachable while scrolling, or misalign columns. Each period now renders separately and empty placeholders are removed.

### Import shows a preview
Before, opening a JSON timetable from another app could import it immediately. Sleepy now shows a preview first, including when no timetable exists yet.

### Widgets refresh after edits and timetable switches
Both widget rendering paths now receive refresh events. Glance updates retry three times at 500 ms intervals and no longer hide exceptions silently.

### The active week and course count are correct
Inspecting another week no longer changes the actual current week. The Mine page counts distinct courses instead of database rows.

### Build
- Release tag: `v1.0.21`
- Debug APKs: `app-arm64-v8a-debug.apk`, `app-armeabi-v7a-debug.apk`, `app-x86_64-debug.apk`

— Lingion

---

# Sleepy v1.0.21

### 课程颜色支持自动分配和手动选择
之前按关键词配色时，名称相近的课程可能得到相近颜色。现在自动配色使用确定性的黄金角映射，编辑课程时还可以打开 HSV 调色盘；手动选择的颜色优先。

### 长课程和连续节次正确渲染
之前，连续多节的课程可能压缩时间栏、无法滚动查看，或造成列错位。现在每个节次单独渲染，并移除了空单元占位。

### 导入前显示预览
之前，从其他 App 打开 JSON 课表可能直接导入。现在会先显示预览，没有课表时也可以直接进入预览。

### 编辑课表后小组件会刷新
两条小组件渲染路径现在都会收到刷新事件。Glance 更新失败会以 500 毫秒间隔重试三次，不再静默吞掉异常。

### 当前周和课程数量计算正确
查看其他周不再改变实际当前周。「我的」页面现在统计去重后的课程，而不是数据库行数。

### 构建
- Release 标签：`v1.0.21`
- Debug APK：`app-arm64-v8a-debug.apk`、`app-armeabi-v7a-debug.apk`、`app-x86_64-debug.apk`

— Lingion
