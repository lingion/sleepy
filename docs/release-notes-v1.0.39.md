# Sleepy v1.0.39

### Each course slot keeps its own weeks
Before, editing a course could merge slots with different week ranges and overwrite their schedule. Each slot now keeps its own start/end weeks and odd/even setting. Conflict checks compare actual overlapping weeks, and an Apply to all slots action is available.

### Weekday dates use the Monday of week one
Before, a non-Monday semester start shifted every weekday header. The date is now normalized to that week's Monday when saving and reading timetables.

### Invalid week ranges are rejected
A slot whose start week is after its end week now fails validation instead of being saved.

### Build
- Release tag: `v1.0.39`
- versionCode: `40`

— Lingion

---

# Sleepy v1.0.39

### 每个课程时段保留自己的周次
之前，编辑课程时不同周次的时段可能被合并，保存后覆盖原有安排。现在每个时段独立保存起止周和单双周设置，冲突检查按实际重叠周次判断，并提供“应用到所有时段”操作。

### 周次日期统一按第一周周一计算
之前，学期开始日期不是周一时，星期表头会整体错位。现在保存和读取课表时都会把日期归一到所在周的周一。

### 非法周次范围会被拦截
起始周晚于结束周的时段现在会验证失败，不再写入。

### 构建
- Release 标签：`v1.0.39`
- versionCode：`40`

— Lingion
