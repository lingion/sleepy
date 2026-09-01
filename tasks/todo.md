# Todo List

## Phase 1: 教务直连命名（a3）

- [ ] Task 1: JwImportActivity 加命名 TextField + 改标题
  - [ ] 改 `jw_config_title` 6 locale strings
  - [ ] 加 `jw_table_name_label` 6 locale strings
  - [ ] Dialog 加 TextField + 状态保存
  - [ ] 落库用用户输入
  - [ ] 编译通过

## Phase 2: 类型列 type=3（b3）

- [ ] Task 2: CourseEntity.inWeek() 支持 type=3
  - [ ] 修改 inWeek 函数
  - [ ] 添加单测

- [ ] Task 3: ScheduleParser parseSimpleText / parseCsv / parseHtml 类型默认 3
  - [ ] parseType 空/缺失返回 3
  - [ ] 单测覆盖 type=0/1/2/3/缺失

- [ ] Task 4: AddCourseScreen 周类型选项加 "按周次"
  - [ ] 加 `import_week_type_custom` 6 locale strings
  - [ ] UI 选项同步

## Phase 3: Prompt 重写 + 解析器 fallback

- [ ] Task 5: ai_prompt_text 重写
  - [ ] 中文版 strings
  - [ ] 5 locale 同步

- [ ] Task 6: 解析器 fallback：组合列拆分
  - [ ] parseSimpleText 检测末段教室
  - [ ] 单测覆盖

## Checkpoint

- [ ] testDebugUnitTest 通过
- [ ] assembleRelease 通过
- [ ] APK 签名验证
- [ ] 发 APK 给用户