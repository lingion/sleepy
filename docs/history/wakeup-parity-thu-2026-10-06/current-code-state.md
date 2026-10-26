# THU 当前代码状态（2026-10-13）

- JwThuparser.kt 解析用户当前 WebView 页面脚本；schools.json 条目类型为 thu。
- JwWebViewFrameCapture.kt 依脚本与网格组合指纹定位课表帧；JwWebViewLoginScreen.kt 提示登录后打开整体课表、选学期并导入当前页。
- JwImportActivity.kt 在预览提示无法无损表达而跳过的课程数量。未知周集合及小时区间不伪造全周课。
- jw_fixtures/thu 中 HTML 均为人工合成，尚无真实学校登录后回包。固定版本反编译 APK 的节次映射与时间表不能代表学校当前作息，详见 protocol-matrix.md。
