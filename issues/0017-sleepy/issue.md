# [Sleepy] \n\n- Number: #17\n- State: open\n- Author: OTUT9\n- Created: 2026-09-06T08:46:24Z\n- Updated: 2026-09-06T13:35:43Z\n- URL: https://github.com/lingion/sleepy/issues/17\n\n## Body\n\n请描述你遇到的问题或建议：

---
**Version:** 1.0.50
**VersionCode:** 51
**Android:** 16
**Device:** Xiaomi 25113PN0EC
**Resolution:** 1220x2656
**Locale:** zh-CN
**Build:** Release
安徽大学的课表抓取失败
显示：
抓取失败：Error: POST schedule-table/datum失败 HTTP500
\n\n## Comments\n\n### lingion — 2026-09-06T09:05:47Z\n\n你好，看到 issue。

安徽大学是 EAMS5 协议，错误 `POST schedule-table/datum HTTP 500` 通常意味着：
- 教务端临时故障（最常见）；
- 该接口路径在你的登录会话下需要先 GET 一次课表视图页面建立上下文（部分 supwisdom 部署有这个前置依赖）；
- 你拿到的 session 已过期（但登录态检测应该会先拦下来）。

我马上会去适配。但安徽大学 EAMS5 在不同部署间差异不小，单靠错误信息无法定位具体是哪种。

如果你能配合提取一份适配包，速度会快很多。需要的内容：

1. **登录后到达「个人课表」页面时**，浏览器地址栏的完整 URL；
2. **课表页面 HTML 源码**（右键 → 另存为完整网页，或 F12 → Network 抓包后右键 Save all as HAR），里面要包含当前学期课程数据；
3. 浏览器 F12 → Network → 触发「抓取」后那个失败的 POST `schedule-table/datum` 请求的：
   - 完整 Request URL（含查询参数）
   - Request Headers（特别是 Cookie / Referer / User-Agent）
   - Request Body（如果有）
   - Response Body（即便 500 也可能返回 HTML 错误页，里面有错误码）
4. 如果方便，附上你登录用的账号命名规则（学号前几位是否脱敏？这影响我模拟请求）。

打包方式：直接拖进这个 issue 评论框就行。如果文件太大不方便上传，可以 [开一个带适配包的 draft issue](https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml)，或把 HAR 文件传到网盘贴个下载链接。

只要拿到这套信息，安徽大学的适配应该很快能合上。\n\n### lingion — 2026-09-06T13:26:11Z\n\n已在 [v1.0.51](https://github.com/lingion/sleepy/releases/tag/v1.0.51) 适配完成,你现在升级到 v1.0.51 重试一下。

**适配要点:**

- POST `schedule-table/datum` 是合工大形态,安大不存在这个 endpoint,本次改成 GET `.../print-data`
- 学期选项从真实 Thymeleaf 渲染的 `<select id="allSemesters">` 读(不是 JS 数组)
- 周次位图 (`weekIndexes`) 做完整 RLE 解析,支持 `"1-16单"` / `"8,10,12,14"` 等形态
- 5 个跨仓仓库 consensus: `MoeclubM/AHU-AIO` + `qiqqqqq517/shangkeschschedule` + `abydym/Ahu_Plus` + `Landon-3314/AHU-TimeTable` + `Zeraora-807/Anhui-Univ-DSH-Tool`

**如果升级后还是失败**,请按 [采集教程](https://github.com/lingion/sleepy/blob/main/docs/adapt-kit/README.md) 跑一份适配包,里面有完整 HTML + 抓包。

工具可执行文件下载(Mac/Win/Linux 三平台):[`tools/sleepy-collector/dist`](https://github.com/lingion/sleepy/tree/main/tools/sleepy-collector/dist)

跑法 (Mac/Linux):

```
chmod +x sleepy-collector-macos-arm64
./sleepy-collector-macos-arm64
```

按提示登录教务 → 进个人课表 → 工具自动抓 → 产出 `sleepy-adapt.zip` → 把 zip 拖回这个 issue。

拿到 zip 就能定位具体是 endpoint 路径变了还是 response shape 变了。
