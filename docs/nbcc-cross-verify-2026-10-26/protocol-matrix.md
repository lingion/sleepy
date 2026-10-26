# Step 4 协议 matrix — WebVPN/wengine 教务登录链横向对比

- 日期: 2026-10-26 · 依据: SOP Step 4 七维度;证据源 = `_data/findings-notes.md` §1-§3(公开逆向) + 深读仓原文存档 `/tmp/nbcc-research/raw/files/`
- 本表先按调研笔记定稿四形态骨架;Step 3 裁决(268 verdict)落地后只补行不翻形态。

## 形态 A — 原生 wengine 登录链(全自主协议,不经 WebView)

代表: `StevenWin818/wenginetunnel`(Go, GUET)、`Chiu-xaH/HFUT-Schedule`(Kotlin, HFUT)

| 维度 | wenginetunnel (GUET) | HFUT-Schedule (HFUT) | NBCC 若走 A 的对应值(证据态) |
|---|---|---|---|
| 入口 URL 形态 | GET `webvpn…/login?cas_login=true` 取票 | 同左,HFUT cas | 新门户 `/do-login`(auth_type=local 表单直登,登录页 JS 实锤)或 `?cas_login=true` 待验 |
| studentId 提取位置 | 教务会话页 DOM | `eams5-student` 页面 | PC EAMS `courseTableForStd.action` 页面 semesterCalendar/隐藏域 |
| studentId 形态 | 学号 | 学号 | 学号(class-schedule `ids=` 参数,双源) |
| API path | CAS `/http/<hex>/…execution` | `wengine-vpn/cookie?method=get` 桥 + `/http/<hex>/cas/login` | 门户 POST `/do-login`(username, password=AES-CFB(`wrdvpnisawesome!`), auth_type, _csrf) |
| 请求方法 | GET+POST 表单 | GET+POST 表单 | POST 表单;票据自动落 cookie |
| 会话失效形态 | 302→/login, cookie 无 `wengine_vpn_ticket` | 同左 | 待实测(无凭据探测一律 302,无区分度) |
| 反爬 | uTLS HelloChrome + BFP 设备指纹 + 短信 MFA 两阶段 | 无 | 登录页有滑块(drag.js)与 `_csrf`;错误码含 TOO_MANY_ATTEMPTS/IP_FORBIDDEN |

## 形态 B — WebView 门户登录 + 同源 fetch 教务 action(用户真实凭据不接触)

代表: `Wangxiaolu0716/class-schedule`(NBCC 本校学生实测成功)

| 维度 | 值 |
|---|---|
| 入口 URL 形态 | WebView 打开门户(`webvpn.nbcc.cn/users/sign_in` 旧门户)登录,人输凭据;移动 UA(旧门户桌面登录流程不可用) |
| studentId 提取位置 | PC EAMS 页面 HTML: `semesterCalendar({...})` 初始化值 + form 隐藏域兜底 |
| studentId 形态 | 学号(页面已登录态内嵌) |
| API path | 页面上下文 `fetch('/eams/courseTableForStd!courseTable.action', {credentials:'same-origin'})` |
| 请求方法 | POST,体 `ignoreHead=1&setting.kind=std&startWeek=&project.id=&semester.id=&ids=`(≤3 组候选重试) |
| 会话失效形态 | 响应不含 `new TaskActivity` → 回登录页由用户处理 |
| 反爬 | 无;WebVPN 打残页面 jQuery → 必须原生 fetch;mixedContent alwaysAllow |

## 形态 C — WebView 凭据自动填充(密码传递器)

代表: `FHfanshu/ZJXU-CampusHub`(开源版凭据存储打桩,闭源完整)

| 维度 | 值 |
|---|---|
| 入口 | WebView 登录页 + setInputValue 注入(`#mm`/`input[name=mm]`/`input[type=password]`) |
| 前提 | 目标页为纯 HTML 表单;正方 Ionic SPA 不适用 |
| 先例判定 | 密码传递器形态成立但 NBCC 必要性存疑:B 已实测免接触凭据,C 反而引入凭据面 |

## 形态 D — 教务域直登(绕 WebVPN)

代表: HFUT-Schedule 校直链文件(`GET /eams5-student/login-salt` + SHA1(TOKEN+"-"+pwd))、`MilLoong/UESTC-EAMS-Helper-App`(ticket→JWT)

| 维度 | 值 |
|---|---|
| 适用前提 | 教务域公网可解析 |
| NBCC 判定 | 不可行:`jwgl.webvpn.nbcc.cn` 公共 DNS 零解析(crt.sh 亦无教务直域),教务必经 VPN 通道(双源证据) |

## 结论(对 Step 6 的输入)

1. NBCC 首选 = 形态 B(本校学生实测,零凭据接触,绕开 hex 链与钉钉链);形态 A 为次选(门户 `/do-login` 要素已全部公开,但含滑块/_csrf/锁定策略,风险高)。
2. 教务协议 = 经典正方 EAMS(非 shuwei),sleepy `TYPE_CLASSIC_EAMS` 现役承接。
3. 形态 C(密码传递器)调研结论:先例存在、对本校非必需 — 如实回应用户「考虑新增」命题。
4. 形态 D 对本校排除。
5. 待 Step 3 verdict 补:同族仓数分布、各形态可借鉴参数细节、shuwei 错配的他仓旁证。
