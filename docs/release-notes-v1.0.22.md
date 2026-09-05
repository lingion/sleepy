# Sleepy v1.0.22

### Import directly from a university URL
Before, users had to identify the academic-system type before importing. The search field now accepts a university portal URL and tries to detect ZFSoft, JINZHI, URP, or Qiangzhi automatically. If URL detection is inconclusive, the parsers are tried and the best timetable is selected.

### ZFSoft has a dedicated parser
The new parser first reads embedded timetable data, then falls back to HTML tables, and finally to the Qiangzhi parser when necessary. This release added coverage for 92 more schools.

### Build
- Release tag: `v1.0.22`
- APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`

— Lingion

---

# Sleepy v1.0.22

### 可以直接粘贴教务 URL 导入
之前需要先判断学校使用正方、金智、URP 还是强智。现在可以把学校教务网址直接粘贴到搜索框，Sleepy 会尝试自动识别协议；识别不出时会尝试各解析器并选择结果较好的课表。

### 正方系统新增专用解析器
新解析器会优先读取页面脚本中的课表数据，失败后回退到 HTML 表格，最后再尝试强智解析器。本版本新增覆盖 92 所学校。

### 构建
- Release 标签：`v1.0.22`
- APK：`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`

— Lingion
