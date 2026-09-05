# Sleepy v1.0.27

### Re-imported courses keep their colors
Before, colors could change because database row numbers were used as the seed. Automatic colors now use the stable course group, so the same course keeps its color across days, periods, teachers, rooms, and re-imports.

### Updates no longer downgrade the app
Before, an update check could download a release older than the installed build. Sleepy now downloads only when the available version is newer, and the status text distinguishes checking from downloading.

### Weeks before the semester no longer display negative numbers
The schedule now reports that it is outside the semester range before a term starts, while in-range and after-term states remain distinct.

### Build
- Release tag: `v1.0.27`
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`

— Lingion

---

# Sleepy v1.0.27

### 重新导入后课程颜色保持不变
之前用数据库行号作为颜色种子，重新导入后颜色可能改变。现在自动配色使用稳定的课程组，同一门课跨星期、节次、教师、教室和重复导入都会保持颜色。

### 更新不会降级
之前检查更新时可能下载比已安装版本更旧的 Release。现在只有可用版本更新时才下载，状态文案也区分检查和下载。

### 开学前不再显示负周数
学期开始前，课表现在显示当前不在学期范围内；学期内和学期结束后的状态也分别处理。

### 构建
- Release 标签：`v1.0.27`
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86_64`

— Lingion
