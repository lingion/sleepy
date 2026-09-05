# Sleepy v1.0.15

### HEU import uses the Wisedu jwapp flow
Before, HEU imports could return 403 errors or collapse multi-period courses. The importer now performs CAS login, session initialization, semester lookup, and timetable fetch through Wisedu jwapp; the verified sample contained 34 rows and 46 courses with multi-period splits preserved.

### Period times are confirmed instead of hardcoded
Before, every school inherited fixed period times. The first import now asks you to confirm the times supplied by the academic system.

### Build
- Release tag: `v1.0.15`

— Lingion

---

# Sleepy v1.0.15

### 哈工程改用 Wisedu jwapp 导入
之前，哈工程导入可能遇到 403，连续多节课也会被压成一块。现在改走 Wisedu jwapp，完成 CAS 登录、会话初始化、学期查询和课表获取；已核验样本包含 34 行、46 门课，连续节次保持不变。

### 节次时间不再写死
之前，所有学校都使用固定节次时间。现在首次导入时会让你确认教务系统提供的节次时间。

### 构建
- Release 标签：`v1.0.15`

— Lingion
