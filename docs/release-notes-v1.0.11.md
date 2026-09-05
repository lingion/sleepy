# Sleepy v1.0.11

### Widgets no longer drop courses
Before, the widget layout had a child limit and could omit courses from a larger timetable. The rendering stack now draws onto a fixed canvas, so the widget can show the full course list.

### Widget text fits its canvas
Text sizing and the minimum canvas dimensions now adapt to the amount of content, reducing overflow at the widget edges.

### Widget colors match the timetable
The widget now uses the same course-color mapping and fallback behavior as the main timetable.

### Vertical layout keeps text upright
In Taiwan-style right-to-left layouts, course names appear in the right column and classrooms in the left column, with characters kept upright.

### Build
- Release tag: `v1.0.11`

— Lingion

---

# Sleepy v1.0.11

### 小组件不再漏掉课程
之前，小组件布局有子项数量限制，课表较大时会漏掉课程。现在改为在固定画布上渲染，可以显示完整课程列表。

### 小组件文字会适应画布
字号和最小画布尺寸会根据内容调整，减少文字溢出小组件边缘的情况。

### 小组件颜色与课表统一
现在小组件使用与主课表相同的课程配色和回退规则。

### 竖排布局中的文字保持正向
在台湾式从右向左的布局中，课程名位于右列、教室位于左列，文字保持正向，不再横躺。

### 构建
- Release 标签：`v1.0.11`

— Lingion
