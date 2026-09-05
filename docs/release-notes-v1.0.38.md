# Sleepy v1.0.38

### Linyi University direct import
Linyi University is now listed with its new Zhengfang entry. Off-campus access may still require the campus network or VPN because of the university firewall.

### More academic-system variants are recognized
The importer now covers Chengfang JSON, Peking University class grids, BNUZ weekday tables, HNUST merged-cell pages, and separate Qiangzhi variants.

### Import errors explain the next step
Instead of a generic zero-course result, failures now distinguish expired sessions, empty semesters, missing timetable containers, header mismatches, image/empty-cell pages, wrong protocols, and no parser match.

### Nested frames and direct fetch are supported
The importer can walk nested frames, wait for rendered timetables, retry loading, detect expired login pages, and fetch new-Zhengfang data through its page API.

### Parser and protocol fixes
Weekday, period, week-range, URP grid, and old/new Zhengfang parsing were corrected, and URL protocol detection no longer matches unrelated substrings.

### Build
- Release tag: `v1.0.38`
- versionCode: `39`

— Lingion

---

# Sleepy v1.0.38

### 支持临沂大学直连导入
临沂大学已加入学校列表，使用新版正方入口。由于学校防火墙限制，校外访问可能需要校园网或 VPN。

### 新增多种教务系统变体
现在支持青果 JSON、北京大学班级课表网格、北师珠按星期表格、湖南科技大学合并单元格页面，以及多个强智变体。

### 导入失败会说明原因
导入失败不再统一显示 0 门课，现在会区分会话过期、空学期、缺少课表容器、表头不匹配、图片或空单元格、协议错误和没有解析器匹配。

### 支持嵌套 frame 和页面接口抓取
导入器可以遍历嵌套 frame，等待课表渲染，重试加载，识别过期登录页，并通过新版正方页面接口获取数据。

### 解析器和协议识别修正
修正了星期、节次、周次、URP 网格和新旧正方解析；URL 协议识别也不再命中无关的同名子串。

### 构建
- Release 标签：`v1.0.38`
- versionCode：`39`

— Lingion
