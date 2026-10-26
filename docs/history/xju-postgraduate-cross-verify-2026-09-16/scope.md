# Scope — 新疆大学（研究生）yjs Gwork 教务收录

日期: 2026-09-16
分支: main (HEAD 7ddb6254)
适配类型: 学校收录（协议族 xju_post 已有, 本轮 = schools.json 条目 + WebView iframe 采集 JS + fixture）

## 用户原话

「https://yjspy.xju.edu.cn/Gstudent/Default.aspx?UID=107552604972 这个教务我们适配了吗？在 Wake Up 的逆向里面有吗？」
「要的，按照 SOP 走完」

## 学校

- 全称: 新疆大学（研究生 / yjspy = 研究生培育）
- 域名: yjspy.xju.edu.cn
- 系统: 研究生培养管理信息系统（Gwork 皮肤, App_Themes/Gwork）
- 学段: 研究生

## 协议判定

- `TYPE_XJU_POST`（WakeUp 兼容层 8 族之一, JwXjuParser → WakeUpCompat.parseXju）
- 解析目标: ASP.NET `#ctl00_contentParent_dgData` 表格（sundayFirst 表头序判定 / rowspan 连堂折叠 / ｛名(周次)[教师：…,地点：…]｝文法）
- WakeUp 逆向锚点: OooOOO.java mode 1（docs/history/wakeup-protocols-cross-verify-2026-09-15/protocol-matrix.md §5 已横向对比）
- 本轮 0 新协议: parser 与 Registry 已在 main, 无新 type

## 实测探针（2026-09-16, 直连无 cookie）

| 探测 | 结果 |
|---|---|
| GET /Gstudent/Default.aspx?UID=… | 200, frameset(TopMenu/LeftMenu/PageFrame), App_Themes/Gwork, 标题「新疆大学研究生培养管理信息系统」 |
| GET /Gstudent/loging.aspx?UID=… | 302 → ReLogin.aspx（UID 需激活 cookie, 预期） |
| GET ReLogin.aspx | 200, __VIEWSTATE/__EVENTVALIDATION/ctl00$contentParent$UserName/PassWord/ValidateCode + btLogin |
| GET /Gstudent/Course/StuCourseQuery.aspx?UID=… | 302 → ReLogin（登录墙后） |

同族实锤（huhu415/eduData-GoBack hrbust 研究生, 同款 Gwork 系统）:
- 课表页 URL: `Course/StuCourseQuery.aspx?EID=<部署加密菜单ID>&UID=<学号>` — **EID 不可硬编码**, 需从 LeftMenu 树运行时发现
- 表格: `table#contentParent_dgData tbody tr`
- 单元格文法: `教师:([^\s,]+)` / `地点:([^\s\]]+)` / `(\d+)-(\d+)周`

## 完成门槛（gate）

1. schools.json 收录新疆大学（type=xju_post, yjspy.xju.edu.cn），双副本同步
2. WebView 采集通路: XJU_FETCH_JS 钻 PageFrame iframe 同源取 dgData 表格（含 LeftMenu EID 运行时发现兜底）
3. fixture 覆盖 xju_post 表格形态 + 反例
4. 四处联动闸（校数 339→340 / 双副本 1:1 / knownTypes / sortKey 拼音序）
5. 致谢边界: 本轮触达 huhu415/eduData-GoBack（协议形态同族实锤）→ Step 5.5 致谢

## 非目标

- 不动 WakeUp 8 族其他协议
- 不做 XJU 本科教务（jw.xju.edu.cn 是另一套系统）
- 不硬编码任何 EID/UID
