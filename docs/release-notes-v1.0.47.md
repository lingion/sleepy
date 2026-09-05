# Sleepy v1.0.47

### Six more universities support direct import
Before, direct import did not cover Hefei University of Technology, Southeast University, Zhejiang University, University of Science and Technology of China, Sichuan University, or Northeastern University. All six can now be imported directly.

### EAMS and semester parsing are more tolerant
Before, EAMS endpoints and semester patterns varied between schools, and USTC rows without a lesson code could not be assigned reliably. The import flow now repairs the affected endpoints, handles odd/even week expressions, infers the USTC lesson code from time when it is absent, and preserves odd/even-week information for Sichuan University.

### Network failures explain what to do next
Before, a failed request could look like a broken adapter. Requests now time out after 20 seconds, and imports for nine schools with known off-campus restrictions explain that the campus network or a VPN may be required.

### Course conflicts show the other course
Before, the conflict dialog reported an overlap without naming the existing course. It now shows the other course's name, and the rotation state survives a screen rotation.

### About has its own acknowledgements page
Before, open-source acknowledgements were mixed into the About screen. They now have a dedicated page, synchronized across all six supported locales.

### WebView and test data fixes
Before, the WebView could stop on a certificate prompt and school fixtures could drift from the adapters. The affected TLS handling and `schools.json` fixtures are now aligned, and the release was verified with 908 tests.

### Build
- versionName: `1.0.47`
- versionCode: `48`
- ABI APKs: `arm64-v8a`, `armeabi-v7a`, `x86_64`

— Lingion

---

# Sleepy v1.0.47

### 新增六所学校直连导入
之前，合肥工业大学、东南大学、浙江大学、中国科学技术大学、四川大学和东北大学还不能直连导入。现在这六所学校都已支持。

### EAMS 与单双周解析更能适应学校差异
之前，不同学校的 EAMS 端点和学期格式存在差异，中国科学技术大学缺少 lesson code 的课程行也无法稳定归类。现在已修正相关端点，支持单双周表达；缺少 lesson code 时会根据时间推断，中国科学技术大学可以正常处理，四川大学的单双周信息也会保留。

### 网络失败会给出处理方向
之前，请求失败时看起来像适配器本身出错。现在请求 20 秒无响应就会超时；对已知校外网络受限的九所学校，导入失败时会提示连接校园网或使用 VPN。

### 冲突弹窗会显示另一门课
之前，冲突弹窗只说明发生了重叠，没有指出已有课程。现在会显示另一门课程的名称，旋转屏幕后冲突课程的轮换状态也会保留。

### 开源致谢单独成页
之前，开源致谢和 About 页面内容混在一起。现在致谢拆成独立子页，并同步到六种支持的语言环境。

### WebView 与测试数据修正
之前，WebView 可能停在证书提示页，学校数据夹具也可能和适配器不一致。现在已修正相关 TLS 处理并同步 `schools.json`，本版本通过 908 项测试验证。

### 构建
- versionName：`1.0.47`
- versionCode：`48`
- ABI APK：`arm64-v8a`、`armeabi-v7a`、`x86_64`

— Lingion
