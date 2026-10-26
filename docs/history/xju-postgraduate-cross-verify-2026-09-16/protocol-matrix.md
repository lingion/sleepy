# Protocol Matrix — xju_post (Gwork 研究生 dgData) 增量对比

日期: 2026-09-16
基线: docs/history/wakeup-protocols-cross-verify-2026-09-15/protocol-matrix.md §5 (WakeUp jadx OooOOO.java mode 1 vs Sleepy parseXju)

本轮增量 = 用户实测探针 (yjspy.xju.edu.cn 无 cookie 直连) + 新同族仓证据 (hrbust Pg / UPC / CUG / SCAU-Grad)。

## 对比维度

| 维度 | WakeUp jadx (OooOOO.java) | hrbust Pg (eduData-GoBack) | UPC/CUG (shiguang) | yjspy.xju.edu.cn 实测 | Sleepy 实现 | 判定 |
|---|---|---|---|---|---|---|
| 系统皮肤 | — | App_Themes/Gwork | 同款 ASP.NET 研究生 | **App_Themes/Gwork + Gstudent 路径** | — | 同系统 |
| 入口形态 | URL 登录后 frameset | 登录→LeftMenu→PageFrame | 课表页直入 | **frameset(TopMenuFrame/MenuFrame/PageFrame)**, PageFrame src=loging.aspx | — | 实测实锤 |
| 登录 | mode 1 表单 | __VIEWSTATE+__EVENTVALIDATION+验证码+btLogin (UpdatePanel) | — | ReLogin.aspx: ctl00$contentParent$UserName/PassWord/ValidateCode + btLogin | WebView 内人工登录, 不碰凭据 | 一致(WebView 免疫) |
| 课表页 URL | — | Course/StuCourseQuery.aspx?EID=<加密>&UID=<学号> | — | 无 session 302 ReLogin(登录墙后) | 不硬编码, DFS 抓当前 frame | EID 运行时态 |
| 表选择器 | dgData 表格 | table#contentParent_dgData tbody tr | ctl00_contentParent_dgData | (登录墙后, 同族推断 #ctl00_contentParent_dgData / #contentParent_dgData) | parseXju 双选择器兜底 | 一致 |
| 表头列序 | sundayFirst = sunIdx<monIdx | — | 列序 0:周日\|1..6 | — | 同谓词+翻转 | 一致 |
| 连堂 | rowspan i6+i30-1 | rowspan→endNode | rowspan→endSection | — | node+rowspan-1 | 一致 |
| 单元格文法 | ｛名(周次)[教师:…,地点:…]｝ | 教师:([^\s,]+)/地点:([^\s\]]+)/(\d+)-(\d+)周 | 第X周[教师:..,地点:..] | — | ｛…｝以；分条、、分周次 | 一致 |
| 抓取通路 | 原生 App 直连 | Go http + cookiejar | 油猴 DOM | — | **WebView DFS 抓 frame + ANCHORS 识别 dgData** | 本轮补通路 |

## 求同存异结论

- **求同**: 表格 id=ctl00_contentParent_dgData (或去 ctl00 前缀) 为 Gwork 族唯一课表容器; ｛名(周次)[教师,地点]｝文法族-wide; rowspan 连堂; 列序含周日判定。
- **存异**: EID 加密参数部署相关(不可硬编码) — 规避法 = 用户登录后停在课表页, DFS 抓 PageFrame 当前内容, 无需重建 URL。
- **通路判定**: xju_post 课表页在 PageFrame iframe 内(同源) — 通用 DFS (CAPTURE_FRAMES_JS_TEMPLATE depth 8) 可达; 缺口仅 ANCHORS 表无 dgData 锚 → selectBestFrame ④ 无锚点分支会误判。本轮修 = ANCHORS += "contentParent_dgData"。

## 宿主探针记录 (2026-09-16)

- GET /Gstudent/Default.aspx?UID=… → 200, frameset, title「新疆大学研究生培养管理信息系统」
- GET /Gstudent/loging.aspx?UID=… → 302 /gstudent/ReLogin.aspx (UID 需激活 cookie)
- GET /Gstudent/Course/StuCourseQuery.aspx?UID=… → 302 ReLogin (登录墙)
- 无 cookie 无法取课表页真实 HTML — 表格形态采信同族三仓 + WakeUp 逆向 + 既有 parseXju 契约测试。
