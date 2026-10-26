# WakeUp 私有协议逆向工程总结

## 完成的工作

### 1. 协议逆向（72/72 完成）

通过逆向 WakeUp 6.1.06 smali 代码，识别并映射了所有私有协议：

| 私有协议 | 条目数 | 映射到 | 逆向方法 |
|---------|--------|--------|---------|
| xatu_shuwei | 28 | shuwei_json | 分析 unitCount/courseUnits 字段 |
| shuwei_new | 18 | shuwei_json | 分析 studentTableVms/activities 字段 |
| umooc | 14 | umooc (新协议) | HTML 解析 timetable/infolist_hr_common |
| yl | 12 | yl (新协议) | HTML 解析 CourseTimeTable-styles-timeTable |

**总计**: 72 条私有协议条目全部完成协议映射

### 2. 解析器实现

#### JwUmoocParser
- 文件: `app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt`
- 特征: `/academic/common/security/login.jsp`, `timetable` 元素, `infolist_hr_common` 类
- 解析逻辑: 查找 timetable 容器，遍历行提取课程数据

#### JwYlParser
- 文件: `app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt`
- 特征: `/lyuapServer/login`, `app-components-CourseTimeTable-styles-timeTable` 类
- 解析逻辑: 查找 timeTable 容器，解析 tbody/tr 结构

#### 协议注册
- `JwProtocol.kt`: 添加 TYPE_UMOOC 和 TYPE_YL 常量
- `JwParserRegistry.kt`: 注册新解析器，设置优先级

### 3. 代码提交

```
commit f7277b98
feat(jw): 新增 UMOOC 和奕联教务协议解析器

- 逆向 WakeUp 6.1.06 smali 代码，识别 umooc 和 yl 私有协议
- 实现 JwUmoocParser: 解析 timetable/infolist_hr_common HTML 结构
- 实现 JwYlParser: 解析 CourseTimeTable-styles-timeTable HTML 结构
- 在 JwProtocol.kt 中添加 TYPE_UMOOC 和 TYPE_YL 常量
- 在 JwParserRegistry.kt 中注册新解析器
- 更新 admissions: 14 条 umooc + 12 条 yl 映射到新协议类型
```

### 4. 编译验证

```
BUILD SUCCESSFUL in 50s
8 actionable tasks: 2 executed, 6 up-to-date
```

## 阻塞问题

### Evidence 文件不同步

**问题**: `scripts/data/school_url_evidence.json` 与当前 admissions 不同步

- Evidence 条目: 1,905 条（350 个唯一学校）
- Operational 条目: 2,669 条（有 URL 的 admissions）
- 缺失: 2,669 条（所有 operational 条目都缺 evidence）

**影响**: `scripts/generate_school_catalog.py` 无法运行，因为 `catalog_ownership.py` 要求 evidence 覆盖所有 operational 条目

**根本原因**: Evidence 文件是之前批次的产物，与当前 admissions 完全不匹配

### 解决方案选项

1. **运行 evidence 生成脚本**（推荐）
   ```bash
   python3 scripts/capture_entry_evidence.py --workers 10
   ```
   - 需要网络访问
   - 预计时间: 数小时（取决于 URL 响应速度）
   - 会生成所有缺失的 evidence

2. **手动审查并更新 evidence**
   - 逐个检查 2,669 个 URL
   - 生成 ownership evidence
   - 耗时更长

3. **临时绕过 evidence 检查**（不推荐）
   - 修改 `catalog_ownership.py` 放宽检查
   - 会降低数据质量保障

## 剩余工作

### 1. 生成 Evidence（阻塞 catalog 生成）

运行 `capture_entry_evidence.py` 为所有 2,669 个 operational 条目生成 evidence。

### 2. 重新生成 Catalog

Evidence 生成后，运行：
```bash
python3 scripts/generate_school_catalog.py
```

### 3. 手动调研 65 条无法自动映射的条目

这些条目的 URL 无法通过启发式规则映射到已知协议，需要手动调研：
- 访问每个 URL
- 确定教务系统类型
- 手动映射到正确的协议

## 技术细节

### 逆向工程方法

1. **定位 smali 代码**
   - 目录: `~/tools/reverse/wakeup-fresh/`
   - WakeUp 6.1.06 反编译输出

2. **协议路由分析**
   - 文件: `smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/base/OooOOO0.smali`
   - 发现 WakeUp 的路由逻辑：
     ```
     if importType == "wakeup" → use schoolInfo.type
     if importType == "ziyan" → use customConf.androidZiyanType
     else → use importType itself
     ```

3. **解析器实现分析**
   - xatu_shuwei: `smali_classes4/o00OOoo.2/OooOO0O.smali`
   - shuwei_new: `smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/o00O0O00.smali`
   - umooc: `smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/o0O.smali`
   - yl: `smali_classes4/com/suda/yzune/wakeupschedule/schedule_parser/parser/o0OOo000.smali`

### 协议特征

#### UMOOC
- URL 模式: `/academic/common/security/login.jsp`
- HTML 特征: `timetable` 元素, `infolist_hr_common` 类
- 代表学校: 中华女子学院, 兰州大学, 中国地质大学（北京）

#### 奕联 (YL)
- URL 模式: `/lyuapServer/login`, `authserver` 登录
- HTML 特征: `app-components-CourseTimeTable-styles-timeTable` 类
- 代表学校: 井冈山大学, 四川职业技术学院, 天津电子信息职业技术学院

## 结论

所有 WakeUp 私有协议已完成逆向和映射，解析器已实现并通过编译。主要阻塞问题是 evidence 文件不同步，需要运行 evidence 生成脚本后才能重新生成 catalog。

**下一步**: 运行 `python3 scripts/capture_entry_evidence.py` 生成缺失的 evidence。
