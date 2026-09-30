# 课名竖排(直书)排版方案设计

**日期**: 2026-08-06
**范围**: WeekGrid widget 课名竖排渲染 (WeekGridWidgetProvider.kt)
**状态**: 已确认, 待实现

## 背景

当前 WeekGrid widget 课名竖排用 Canvas 逐字直立 `drawText`, 存在两个排版硬伤:

1. **标点不转换**: `()` `《》` `（）` 等横排符号原样堆叠, 视觉占位大且方向错误
2. **连续拉丁逐字竖排**: `II`/`III`/`21B2090` 等被拆成单字竖排, 英文单词和罗马数字失去可读性

参考台湾/日式直书规范, 需要标准竖排处理。

## 用户已确认的设计决策

1. **单字母直立**: 孤立的 1 个拉丁字母(如 `大学物理C` 的 `C`)保持直立, 不旋转。只有 ≥2 个连续拉丁/数字才整组旋转90°。
2. **两个方案都做**:
   - **方案A'(默认, 开关关)**: 标点逐个旋转90° + 拉丁组旋转
   - **方案B(可选, 开关开)**: 标点字符替换为 Unicode Vertical Forms(U+FE19–FE44) + 拉丁组旋转
3. **开关默认关闭** → 默认走方案A'
4. **范围**: 只改竖排课名(name)。room(底部横排)不变

## Token 化算法

把课名(去空格/换行后)切成有序 token 列表, 每类 token 处理方式不同:

| Token 类型 | 判定 | 处理 | 单位高度 unit |
|---|---|---|---|
| CJK run | 连续汉字 | 直立, 逐字绘制 | 每字 = 1.0 |
| Latin run ≥2 | 连续 `[A-Za-z0-9]` 长度≥2 | **整组**顺时针旋转90° | paint.measureText(token)/charSize (比逐字短, 省空间) |
| Latin run =1 | 孤立 1 个 `[A-Za-z0-9]` | 直立 | 1.0 |
| 标点 (方案A') | `()()[]【】《》〈〉「」…—／` 等 | **逐个**顺时针旋转90° | paint.measureText(ch)/charSize |
| 标点 (方案B) | 同上 | **字符替换**为 Vertical Forms | 每字 ≈ 1.0 (竖形字符直立) |

### Token 切分规则

输入: 课名字符串, 先 `filter { it != '\n' && it != ' ' }` 去空白。

扫描时维护当前 run 的类型(CJK / Latin / 其他), 类型变化时闭合上一个 run 产出 token:

```
大学物理C（二）II
└CJK──┘└L1┘└──标点/CJK/标点──┘└L2┘
[大学物理][C][（][二][）][II]
```

- `大学物理` → CJK run, 直立逐字
- `C` → Latin run 长度1, 直立
- `（` → 标点(方案A'旋转 / 方案B替换为 `︵`)
- `二` → CJK run, 直立
- `）` → 标点(方案A'旋转 / 方案B替换为 `︶`)
- `II` → Latin run 长度2, 整组旋转90°

### 旋转方向

顺时针90°(台湾标准, 字头朝右, 自上而下读)。

## 统一字号测量(改 v20b)

高度对 charSize 线性 → 可用"单位高度"无量纲比值计算:

```
totalUnitHeight(name, paint) = Σ over tokens [ unit(token) ]

其中 unit(token):
  - CJK/单字Latin直立的每个字 = 1.0
  - Latin run≥2 (旋转) = paint.measureText(token) / paint.textSize   (旋转后占高=组宽)
  - 标点旋转(方案A') = paint.measureText(ch) / paint.textSize
  - 标点替换(方案B) = 1.0  (竖排字符直立, 与汉字同高量级)
```

`measureText/tszieze` 是比值, 与绝对字号无关 → 可用任意参考 charSize 测一次。

统一号计算(替换 v20b 的 `nameLen` 逻辑):
```
unifiedCharSize = min over all courses [
    nameAvailH(course) / totalUnitHeight(course, paint)
]
clamped to [nameMinDp=11dp, nameMaxDp=28dp]
```

Latin 组旋转后 unit < 字数 → totalUnitHeight 更小 → 统一号可能比纯逐字测量更大(字号提升)。

## Canvas 绘制

### 直立 token
逐字 `c.drawText(ch, centerX, cy, paint)`, cy 按 charSize 递增。

### 旋转 token (Latin run≥2 或 方案A'标点)
```
canvas.save()
canvas.translate(centerX, tokenCenterY)   // tokenCenterY = 该 token 占高的中点
canvas.rotate(90f)
canvas.drawText(tokenStr, 0f, paint.textSize * 0.35f, paint)  // 居中
canvas.restore()
```
- 旋转后字符串沿原 y 轴方向(竖直)排列, 顺时针90°字头朝右。

### 方案B 标点替换
不旋转, 直接用替换后的竖排字符直立绘制, 与汉字同逻辑。

### 截断逻辑(token 级)
累计 token 高度时, 若加入当前 token 会超 nameAvailH:
- 若已累计≥1字高度 → 砍掉该 token 及后续, 末尾加 `…`(竖排省略号, 方案B用 `︙`)
- 若当前是第一个 token 且本身超 nameAvailH → 宁溢不空, 仍绘制

## 标点字符映射表(方案B)

横排符号 → Unicode Vertical Forms (U+FE19–FE44):

| 横排 | → | 竖排 | 码位 |
|---|---|---|---|
| `(` `（` | FF08 | `︵` | FE35 |
| `)` `）` | FF09 | `︶` | FE36 |
| `〔` | 3014 | `︹` | FE39 |
| `〕` | 3015 | `︺` | FE3A |
| `【` | 3010 | `︻` | FE3B |
| `】` | 3011 | `︼` | FE3C |
| `《` | 300A | `︽` | FE3D |
| `》` | 300B | `︾` | FE3E |
| `〈` | 3008 | `︿` | FE3F |
| `〉` | 3009 | `﹀` | FE40 |
| `「` | 300C | `﹁` | FE41 |
| `」` | 300D | `﹂` | FE42 |
| `『` | 300E | `﹃` | FE43 |
| `』` | 300F | `﹄` | FE44 |
| `—` | 2014 | `︱` | FE31 |
| `…` | 2026 | `︙` | FE19 |
| `[` | 005B | `︻` (复用) 或旋转 | — |
| `]` | 005D | `︼` (复用) 或旋转 | — |

**字体覆盖风险**: Unicode Vertical Forms 在 Android 系统字体(Noto CJK 衍生, OPPO Sans)覆盖率需实测。若缺字形会 tofu。实现时方案B对无映射字符 fallback 到方案A'旋转。

## 可配置开关

- **存储**: `AppPrefs.isVertPunctReplace(context): Boolean` (新增), 默认 `false`
- **默认值**: false → 走方案A'(旋转)
- **设置入口**: 挂在现有设置页(与主题/显示模式同级), 项名 "竖排标点优化" 或类似

渲染时根据开关选择标点处理路径:
```kotlin
val useVertForms = AppPrefs.isVertPunctReplace(context)
// 标点 token: useVertForms=true → 替换+直立; false → 旋转
```

## 真实课表落地效果

| 课名 | Token 化 | 方案A'(默认) | 方案B(开关开) |
|---|---|---|---|
| 电路与电子II | [电路与电子][II] | 汉字直立 + II旋转 | 同 |
| 大学物理C（二） | [大学物理][C][（][二][）] | 汉字+C直立, 括号旋转 | 汉字+C直立, 括号→︵︶ |
| 马克思主义基本原理 | 纯CJK | 全直立, 无变化 | 同 |
| 大学英语（三） | [大学英语][（][三][）] | 汉字直立, 括号旋转 | 汉字直立, 括号→︵︶ |

## 涉及改动文件

1. **WeekGridWidgetProvider.kt** (主改动):
   - 新增 token 化函数 `tokenizeName(name): List<NameToken>`
   - 新增 `measureUnitHeight(tokens, paint): Float` (替换 nameLen)
   - 改 `unifiedCharSize` 计算用 unit 高度
   - 改绘制循环: 按 token 类型分别直立/旋转绘制
   - 改截断为 token 级
2. **AppPrefs.kt**: 新增 `isVertPunctReplace` get/set (默认 false)
3. **设置 UI**: 新增开关项

## 测试验证

- 真机(OPPO d3efcd6a) logcat 确认 token 化 + charSize + 各 token 处理方式
- 截图对比 方案A' vs 方案B
- 边界: 纯汉字名无变化(回归)、纯拉丁名、空名、超长名截断
- 方案B 字体覆盖实测: 画几个 Vertical Forms 字符验证 OPPO 字体支持
