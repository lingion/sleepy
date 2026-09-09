# JOU (江苏海洋大学) 协议矩阵 (SOP Step 4)

取证来源: 主线程 curl 实测 (2026-09-09) + 酱海带 APK strings (闭源, 用户指定参考) +
10 候选仓 agent 派单 (findings.json)。判定: 协议族 = **老版正方 (ZF classic .aspx)
+ 学校自建 CAS (lyuapServer)**。

| # | 维度 | JOU 现行形态 | 证据源 | 一致性 |
|---|------|--------------|--------|--------|
| 1 | 登录链 | `zf.jou.edu.cn/login_cas.aspx` → 302 `Object moved` → `cas.jou.edu.cn/lyuapServer/login?service=https%3A%2F%2Fzf.jou.edu.cn%2Flogin_cas.aspx` → 用户登录 (学号+密码+CheckCode.aspx 验证码) → ticket 回跳 zf 域建会话 | 主线程 curl 302 实测; APK strings (`/lyuapServer/login?service=`); brodamndamn bot (同族字段 yhm/mm/yzm) | 3 独立源一致 |
| 2 | 课表端点 | `https://zf.jou.edu.cn/xskbcx.aspx` (个人课表, 从 `/xs_main.aspx?xh=<学号>` 菜单进入) | APK strings (`/xs_main.aspx?xh=`, `/xskbcx.aspx`); 主线程实测该 path 存在 (未登录 = 200+logout 跳转脚本) | 2 独立源一致 |
| 3 | 页面容器 | ZF classic `Table1` (JwOldZfParser 锚点, 12 所在用) — 未登录态抓不到已登录 DOM, 按 12 校同族 parser + APK 全 .aspx 族判定 | JwOldZfParser 契约 + APK strings 全经典正方 .aspx 路径集 | 族级一致 (逐字 DOM 待真机核实, fixture 标 synthetic) |
| 4 | 登录态失效 | HTTP **200** + `<script>window.parent.location.href='logout.aspx'</script>` + frameset (title 现代教学管理信息系统) — 非 302 | 主线程 curl 实测 12828 字节全文存证 `jou_zf_expired_top.html` | 1 源直证 (协议惯用法, 多校通用) |
| 5 | 解析形态 | ZF classic 单元格: 课程名/属性词/周一第N,M节{第N-M周[|单周/双周]}/老师/教室, 同格多课 `<br>` 重复段 | JwOldZfParser 23 用例 + 上游 WakeupSchedule_BUPT (已致谢) | 族级一致 |
| 6 | 外围域 | 选课独立域 `zfxk.jou.edu.cn` (yhm/mm/yzm 登录, 与课表导入无关); 考试 exam / 实习 practice (CAS2Login) / 体测 tice:8080 / 门户 portal / WebVPN :58888 | APK strings; brodamndamn bot; RSSHub (公开通知域) | 3 源一致 |
| 7 | 证书/CAS | cas 与 zf 同注册域 jou.edu.cn → SslBypassRegistry 白名单天然覆盖; lyuapServer = JOU 自建 CAS (非标准 CAS /authserver 形态) | SslBypassRegistry 注册域启发式 + APK strings | 代码级一致 |

## 求同存异结论

- **同**: 与既有 12 所 zf 校共享: parser (JwOldZfParser)、采集 (DFS frames +
  Table1 锚点)、协议族 (.aspx + VIEWSTATE + CheckCode)。
- **异 ①**: 入口必须走 `login_cas.aspx` (CAS SSO 正门), 非 default2.aspx 裸表单 —
  12 所 zf 校多数是裸表单直登, JOU 是 CAS 化部署。
- **异 ②**: 登录态失效是 **200 + JS 父帧跳转 logout.aspx**, 而非登录页 HTML 本身 —
  既有登录指纹 (viewstate+password / checkcode) 在"跳转脚本页"上打分不足,
  需新增硬指纹 (D2)。
- **异 ③**: 验证码 CheckCode.aspx 在 CAS 登录页 (WebView 手动输入, 与既有
  WebView 流程一致, 零代码)。
- **风险登记**: 未登录态页内出现 5 处 `Table1` 字样 + 4 处 `__VIEWSTATE` (meta/JS
  引用, 非 id="Table1" 属性形态 — findAnchors 按属性匹配不误命中); 真机验证留待
  用户带账号实测 (校外可达性已确认, 域名解析+Tengine 正常)。
