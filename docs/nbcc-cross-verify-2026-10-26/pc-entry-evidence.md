# nbcc_pc_entry 公开证据档案 — PC 教务入口实锤

- 日期: 2026-10-26 · 采集方式: 全部公开源(GitHub 代码搜索 / 仓库内容 / 证书透明度 / 无凭据探测),零学生凭据
- 本档定位: 用户纠偏后 Step 6 修复设计的第一证据面 —「人真实路径 = 门户 → PC 教务 → 教务账号直登」的具体 URL 与协议形态

## 1. 头号证据: NBCC 学生本人开源课表应用

`Wangxiaolu0716/class-schedule`(Flutter,README 第一人称「我是宁波城市职业技术学院的学生」)。
`lib/import/school_config.dart:106-120` 对本校的全部硬编码:

```dart
final SchoolConfig nbccSchool = SchoolConfig(
  id: 'nbcc',
  name: '宁波城市职业技术学院',
  loginUrl: 'https://webvpn.nbcc.cn/users/sign_in',
  courseTablePageUrl: 'https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action',
  courseTableActionPath: '/eams/courseTableForStd!courseTable.action',
  userAgent: androidMobileUserAgent,   // 见 §3 矛盾点
  startDate: DateTime(2026, 9, 7),
  autoImport: true,
);
```

- **PC 教务入口 URL = `https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action`**
- 数据接口 = 同源 `POST /eams/courseTableForStd!courseTable.action`,请求体由
  `lib/parsers/eams_course_table_parser.dart::buildCourseTableParamsCandidates`(L90-116)构造:
  `ignoreHead=1&setting.kind=std&startWeek=&project.id=<id>&semester.id=<sid>&ids=<学号>`
  (semester.id 首选页面 semesterCalendar 初始化值,隐藏域兜底;project.id 缺省值;最多 3 组候选依次重试)
- **成功判据 = 响应文本含 `new TaskActivity`** — 经典正方/金智 EAMS 内嵌 JS 课表格式
  (`var table0 = new CourseTable(...); var unitCount = 12; activity = new TaskActivity(...); index = D*unitCount+P`)。

## 2. 协议族定性: 不是 shuwei_json,是 classic EAMS

- 本仓 `JwProtocol.kt:276-285` 已有 `TYPE_CLASSIC_EAMS = "classic_eams"`(注释:「经典金智/树维 EAMS courseTableForStd!courseTable.action 系列, HTML+内嵌 JS;#manualArrangeCourseTable 是空壳,禁走 DOM」),解析器 `JwClassicEamsParser` 现役,先例校 = 电子科大/上财/湖南师大/南航(2026-09 批量收录)。
- NBCC 学生应用解析器与本校 `JwClassicEamsParser` 锁同一格式(双源互证: TaskActivity 指纹 + 参数体形态)。
- **schools.json 现配 `shuwei_json` = 错配**(2026-10-16 目录战役按 WakeUp `xatu_shuwei` 猜配)。WakeUp 上游同源错: `OpenWakeUp app/src/main/assets/schools.json` 实测条目 `{name: 宁波城市职业技术学院, type: xatu_shuwei, url: https://webvpn.nbcc.cn/users/sign_in}` —「树维」厂商名与「金智/正方 EAMS」课表引擎在此混标,猜配源头即此。
- 修复候选 = NBCC 条目 type 改 `classic_eams` + 入口/端点按本节配置。

## 3. 门户双轨与 UA 矛盾点(Step 6 必须实测的开放问题)

学生应用注释(`school_config.dart:11-14`)明言: **「本校 WebVPN 门户的桌面端登录流程不可用(只有手机端能登进去),因此强制使用移动端 UA」** — 登录走移动通道,落地却是 PC 版 EAMS 页。两门户实况:

| 门户 | URL 形态 | wengine 版本 | 证据 |
|---|---|---|---|
| 旧 | `webvpn.nbcc.cn/users/sign_in` | 旧版(`/wengine-vpn/` 资源,el-form/layui 登录页含 CAS/扫码/统一身份元素) | 无凭据 GET 200,53914B,CNAME→ddap.nbcc.edu.cn(112.14.39.197);学生应用入口 |
| 新 | `webvpn.nbcc.edu.cn/login` | 新版(`wengine-vpn/js/new-portal/`,`/m/portal` 移动分流) | 我方学生 dump 181 请求全在此域;移动 UA → `/m/portal` → 钉钉专用磁贴(根因档案) |

- 开放问题 Q1: 新门户桌面 UA 是否给出可点的 PC 教务磁贴(用户断言人在浏览器可 PC 直登)。netlog 仅 1 个 hex 前缀(`…21fae046…`=jwglapp.nbcc.cn,解码方法: AES-CFB key=iv=`wrdvpnisthebest!` segment128,已验证),未捕获桌面门户响应 → 需新采集包或调研 agent 公开佐证。
- 开放问题 Q2: `jwgl.webvpn.nbcc.cn` 在公共 DNS(223.5.5.5/8.8.8.8/114)无解析 — 疑为登录后门户注入的解析记录或 wengine 重写域(`webvpn 匿名-443` 形态如 `jwgl-webvpn-nbcc-cn-443.webvpn.nbcc.edu.cn` 待验)。旧门户是否仍在服务、其移动登录后是否仍直达该 PC 页。
- 两问均**不阻塞协议适配设计**(TaskActivity 解析与参数体已由双源证据锁死),阻塞的是入口 URL 的最终配置值 → Step 6 给出两分支,实测(无凭据 + 学生自愿回传)后二选一。

## 4. 与既有根因档案的关系

- 本档不推翻 `scope.md` 已定稿根因(UA 分流进移动门户 → 钉钉 SPA → 手输页「密码错误」);本档补的是「正确路径的目的地」: PC 教务 = 正方 EAMS courseTableForStd,登录主体 = 教务账号(学生应用免接触钉钉)。
- 学生应用「不自动抓时提示: 请在页面中进入: 课程管理 → 我的课表」(`webview_import_screen.dart:168`)与用户描述的人类路径「教务管理 → 我的课表」一致 — 人在门户里手动点到的就是这张 PC 页。

## 5. 触达仓库登记(Step 5.5 输入)

| 仓库 | License | 触达内容 |
|---|---|---|
| Wangxiaolu0716/class-schedule | 待查(README 自述个人自用去服务器版) | NBCC 入口/端点/参数体/成功判据 头号证据 |
| LonelyMarch/OpenWakeUp | 待查(本仓既有致谢?需核 LicenseScreen) | xatu_shuwei 猜配源头证据(错误定性用) |

## 6. 证据采集方法记录(可复用)

- wengine hex 编解码: `key=b'wrdvpnisthebest!'`,AES-128-CFB,iv=key,segment_size=128,密文前置 key 的 hex(`77726476706e69737468656265737421`…)。`fae04690…2d` → `jwglapp.nbcc.cn` 验证通过。注: HFUT 档案盐值 `wrdvpnisthebest!`,我方 dump 内 NBCC 亦此盐;另见 NEU `b0A58a69394ce73@` 异盐先例 — 盐值是按校部署常量,不得跨校写死。
- 未登录态 VPN 探测对任意内网域一律 302→/login,**无区分度**;存在性判定必须走公开索引/学生工具,禁撞库枚举。
- crt.sh 通配: `*.nbcc.cn`  SAN 仅见 zhsj/mail/webvpn 等,无教务直域 — 教务必经 VPN 通道,与双源证据吻合。
