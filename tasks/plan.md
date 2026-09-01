# Implementation Plan: 教务直连命名 + 类型列 type=3 + 组合列拆分

## Overview

本轮三个修复，用户拍板：**a3 + b3 + c1**。

- **a3**: `JwImportActivity` 的"确认课表节次"对话框加命名输入框 + 改标题为"导入前确认"
- **b3**: 类型列全改 = 加 type=3 + Prompt 放宽 + 渲染逻辑 `startWeek==endWeek` 单独优化
- **c1**: Prompt 重写按 "-" 拆分组合列 + 解析器 fallback

## Architecture Decisions

1. **教务直连命名 = 单独 TextField**：沿用 `JwImportActivity` 现有 `AlertDialog`，加一个 TextField，不重构成 ImportSheet
2. **type=3 语义化**：`inWeek()` 在 type=3 时按 `startWeek..endWeek` 区间判定，不使用"奇偶"
3. **解析器 fallback**：当课程名末段像教室（`\d+#\d+` 或 `逸夫楼xxx` 或 `^xx楼$`）时抽到教室列
4. **数据库 schema 不动**：type 字段已是 Int，0/1/2/3 全部合法，无需 Room migration

## Task List

### Phase 1: 教务直连命名（a3）

- [ ] Task 1: JwImportActivity 加命名 TextField + 改标题
  - 标题 `jw_config_title` 改文案为"导入前确认"
  - Dialog 加 TextField `课表名称`，初值 `getString(R.string.jw_import_title, school.name)`
  - 落库用用户输入的 `tableName`
  - strings.xml 6 locale 同步文案

### Phase 2: 类型列 type=3（b3）

- [ ] Task 2: CourseEntity.inWeek() 支持 type=3
  - type=3 → `week in startWeek..endWeek`
  - 其他类型保持原行为

- [ ] Task 3: ScheduleParser parseSimpleText / parseCsv / parseHtml 类型缺失默认 3
  - parseType 空串/缺失返回 3
  - 数字 0/1/2/3 正常映射

- [ ] Task 4: AddCourseScreen 周类型选项加 "按周次"
  - 增加 `import_week_type_custom` strings (6 locale)
  - 选项文本按用户当前 locale

### Phase 3: Prompt 重写（c1 + b3 Prompt 部分）

- [ ] Task 5: ai_prompt_text 重写
  - 类型列规则：未填=3"按周次显示"
  - 课程名/教室组合列规则：明确按 "-" 拆分，最后一段像教室抽到教室列
  - 6 locale 同步

- [ ] Task 6: 解析器 fallback：组合列拆分
  - parseSimpleText 在解析课程名时，检测末段教室正则

### Checkpoint: 全部完成

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleRelease -q` 通过
- [ ] 解析器 type=3 / 组合列拆分单测补齐
- [ ] `apksigner verify --print-certs` 通过
- [ ] `aapt dump badging` 验证 `versionName=1.0.42` `versionCode=43`
- [ ] 给用户发 APK

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| type=3 字段渲染在 widget 端不一致 | High | 复用现有 `inWeek()`，所有渲染都走它 |
| Prompt 改了后 AI 输出更稀疏 | Med | 测试已有 prompt 输出的 5 个示例，仍能正确解析 |
| 解析器 fallback 拆错合法课程名（含"-"） | Low | 仅在末段严格匹配教室正则时拆，其他场景保留整串 |
| 教务直连 activity 改动影响 WebView session | Low | 只改 Dialog 部分，不改 WebView 生命周期 |

## Open Questions

- 用户未明确：教务直连命名框默认值是学校名（沿用旧）还是空（强制输入）？
  假设：沿用旧 `jw_import_title`，用户可改 — 风险最低