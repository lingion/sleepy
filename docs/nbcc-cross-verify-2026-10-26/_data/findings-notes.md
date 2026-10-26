# NBCC(宁波城市职业技术学院)教务跨仓验证 调研笔记(SOP Step 2/3/5 证据档案)

日期:2026-10-26。约束遵守情况:全程仅读取公开仓库、公开页面与公开搜索索引;未发送任何登录请求、未使用任何凭据。匿名 GET 登录页属于公开页面抓取,不构成登录实验。

## 0. 方向纠偏后的结论框架(用户权威指示)

学生真实路径是:PC 浏览器访问深信服/网御 wengine WebVPN 门户,账号密码直登,进入门户桌面版,再点教务磁贴到 PC 教务系统;学生不使用钉钉客户端。Sleepy 之所以落到钉钉专用 SPA,是因为以移动 UA 进门户被前端脚本分流到移动版(/m/portal 一类)。钉钉路线降级为否决项,只在笔记留痕(见第 6 节)。头号证据目标改为 NBCC PC 教务入口 URL 与登录形态,字段名 nbcc_pc_entry。

## 1. nbcc_pc_entry(头号目标,已闭环)

1.1 PC 教务入口 URL(公开、经 WebVPN 反代):
- https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action(正方 EAMS 桌面版课表入口)
- 数据端点: POST /eams/courseTableForStd!courseTable.action,请求体 ignoreHead=1&setting.kind=std&startWeek=&project.id=1&semester.id=N&ids=学号
- 证据一(最高价值,本校学生仓库): Wangxiaolu0716/class-schedule,文件 lib/import/school_config.dart 中 nbccSchool 配置明文记录上述两个 URL,fixture 文件 test/fixtures/nbcc_course_table_entry.html 与 nbcc_course_table.html 内含 18 处 new TaskActivity(、courseTableForm、addInput(form,"ids",...)、semesterCalendar({...}) 等正方桌面版标志物。raw URL:
  https://raw.githubusercontent.com/Wangxiaolu0716/class-schedule/main/lib/import/school_config.dart
  https://raw.githubusercontent.com/Wangxiaolu0716/class-schedule/main/test/fixtures/nbcc_course_table.html
- 证据二(域名存在性旁证): 公开 DNS 索引站 dnsdblookup 的 nbcc.cn 子域列表包含 jwgl.webvpn.nbcc.cn(searXNG 查询 jwgl.webvpn.nbcc.cn 命中)。
- 证据三(门户域名): webvpn.nbcc.cn/users/sign_in 匿名 GET 返回 302 跳 https://webvpn.nbcc.edu.cn(登录落地到门户;PC 与移动 UA 的 HTTP 层 302 完全一致,分流发生在登录页 JS 客户端,见 1.3)。

1.2 PC 教务协议族判定:
- NBCC 教务桌面版是经典正方 EAMS(Struts2 .action 加 TaskActivity 行渲染),不是树维 MTT 表格。判据: fixture 有 new TaskActivity( 与 courseTableForStd,无 #kcb_container .wut_table。
- 因此 LonelyMarch/OpenWakeUp schools.json 将宁波城市职业技术学院标为 xatu_shuwei(第 1156 条, url 给 webvpn.nbcc.cn/users/sign_in)与本校 fixture 矛盾,应为误标或旧版页面;适配 sleepy 时按正方 EAMS 桌面协议处理。OpenWakeUp 树维解析器锚点(#kcb_container .wut_table、td[data-role=item] .mtt_arrange_item .mtt_item_sksj)在 NBCC fixture 全部缺失。

1.3 门户桌面版与移动版分流逻辑(第二目标):
- 直接证据是 NBCC 门户登录页公开 HTML(匿名 GET https://webvpn.nbcc.edu.cn/login 得 200,已存档 /tmp/nbcc-research/raw/files/nbcc_webvpn_login.html):
  - 页面 JS 含 var isMobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
  - 桌面态渲染钉钉扫码二维码(官方 DTFrameLogin, 资源 https://g.alicdn.com/dingding/h5-dingtalk-login/0.21.0/ddlogin.js, 二维码宽 290;移动态 230 并显示"钉钉授权登录"按钮)。
  - 非钉钉 UA 且 isMobile 时,按钮 href 被改写为 dingtalk://dingtalkclient/page/link?url=<encodeURIComponent('https://webvpn.nbcc.edu.cn/login?dingding_login=true')>&ddtab=true,即 URL scheme 唤起钉钉客户端完成授权后回跳门户(类别 c 的公开先例,直接在本校页面可见)。
  - 桌面默认标签只有两个: data-type="dingding"(钉钉登录)与 data-type="local"(校企合作)。但页面公告文案(ql-editor)写明"学校师生可点击校园统一身份认证登录,登录 WEBVPN 访问学校资源,使用校园统一身份认证登录无需要二次认证",帐号被锁 15 分钟自动解锁,改密码走钉钉工作台应用。公告与标签的差异说明: 师生统一身份认证入口可能在登录态或另一渲染分支出现;校企合作标签下的本地表单(auth_type 隐藏域值 local)展示了门户确实支持账号密码直登通道: 用户名 username 加密码 password POST /do-login。
- 教务磁贴 URL 形态(第二目标后半): wengine 门户登录后的校内系统链接形如 /https/<hex>/ 或 /http-<port>/<hex>/ 前缀。证据链:
  - xiaobei97/wengine-vpn-decryptor(wengine_decryptor.py): 正则 /(?:https?|http-?\d*)/([0-9a-fA-F]+)(/.*)?;密文前 32 个 hex 字符是 IV 的 hex 表示;AES-CFB(segment_size=128)解密得原始内网 URL;NEU 部署的 key 与 iv 均为字符串 'b0A58a69394ce73@'。
  - Chiu-xaH/HFUT-Schedule(WebVpnService.kt): HFUT 部署的路径前缀 hex '77726476706e69737468656265737421' 解 ASCII 恰为 'wrdvpnisthebest!'(IV 前置约定再次印证)。
  - StevenWin818/wenginetunnel(crypto.go WebvpnUrlEncrypt/Decrypt): AES-CFB 对目标 host 加解密转 hex,无填充(流模式)。
  - NBCC 部署的 key/iv 由登录页 JS 明文给出(见 2.1),据此 NBCC 磁贴密文前 32 hex 应为 77726476706e6973617765736f6d6521(key/iv 字符串 'wrdvpnisawesome!' 的 hex)。此为按公开公式推导,非实测。

## 2. wengine WebVPN 原生登录链(类别 a)要点

2.1 NBCC 部署自身(登录页公开 JS,存档 nbcc_webvpn_login.html):
- 密码加密函数内联给出: AesCfb(aesjs), encrypt(text,key,iv) = hex(ivBytes) 拼接 AES-CFB(key='wrdvpnisawesome!', iv='wrdvpnisawesome!', segment 16) 加密(右侧补 '0' 到 16 字节倍数)后的前 textLength*2 个 hex 字符。提交前对 serializeArray 的 password 字段调用 encrypt(value,'wrdvpnisawesome!','wrdvpnisawesome!')。
- 登录提交: POST /do-login(表单序列化: username, password(已加密), auth_type, _csrf);二次认证走 /do-second-login(短信 code 或 TOTP code),微信绑定 /do-wechat-binding,短信发送 /send-sms/(username=手机号, auth_type),滑块验证组件 drag.js/drag_sms.js 与 /login/image 接口;_csrf 隐藏域可见(一次性值,已脱敏不记录)。
- 成功判据: res.success 后 location.href = res.url。错误码枚举: WEEK_PASSWORD_FORBID, IP_FORBIDDEN, TOO_MANY_ATTEMPTS, INVALID_ACCOUNT, CAPTCHA_FAILED, NEED_CONFIRM, NEED_TWO_STEP, NEED_TWO_STEP_TOTP, WECHAT_BINDING, INVALID_FAILURETIME。
- 注意: 登录页脚本里也出现 #cas_login/#oidc_login 元素开关,说明该 wengine 版本支持 CAS/OIDC 对接,但 NBCC 当前渲染未启用。

2.2 各校参考实现(形态对照):
- Chiu-xaH/HFUT-Schedule(app/src/main/java/com/hfut/schedule/network/api/inf/WebVpnService.kt): 票据 GET login?cas_login=true; 桥 GET wengine-vpn/cookie?method=get&host=cas.hfut.edu.cn&scheme=http&path=/cas/login 取 key/iv; CAS 表单 POST 经 /http/<hex>/ 前缀,字段 username,password,execution,_eventId=submit,capcha(注意 HFUT 拼写 capcha); 随后 GET 教务 SSO 落地点 eams5-student/neusoft-sso/login。另有校直链思路文件: GET /eams5-student/login-salt 取 TOKEN,SHA1(TOKEN+"-"+password) 后 POST JSON 登录,绕过 webvpn 直连教务域。
- StevenWin818/wenginetunnel(cas_login.go, crypto.go): 完整原生 CAS 登录链: GET 登录页, FindInputValueByID 取 execution 与 pwdEncryptSalt; CasEncryptPassword = AES-128-CBC, 密钥取 salt 截断或补零到 16 字节, 明文 = RandomNString(64)+password, 随机 16 字节 IV, PKCS7, base64; POST 字段 username,password,rememberMe,_eventId=submit,cllt=userNameLogin,dllt=generalLogin,lt,execution,captcha; 成功判据: cookie 含 wengine_vpn_ticket 且终址不含 authserver/login。含 uTLS HelloChrome_Auto(ALPN 强制 http/1.1)指纹、BFP 设备指纹端点 /bfp/info?bfp=MD5(用户名+"_guet_device_fingerprint")、短信二次认证两阶段(/dynamicCode/getDynamicCodeByReauth.do 与 /reAuthCheck/reAuthSubmit.do)。
- forget-forever/mark: 江西理工强智教务加金智门户加深信服 webvpn 模拟登录笔记(文字档案,低星)。
- lcandy2/webvpn-converter(250 星), spencerwooo/bit-webvpn-converter, ESWZY/webvpn-dlut(WRD WebVPN URL 互转原理), DanXi-Dev/DanXi: URL 转换与通用 webvpn 客户端工具族。

2.3 正方 EAMS 移动端 Ionic SPA 逆向(类别 b):
- 结论: 未检索到针对 jwglapp/app-web 或 dingding-login 路由的公开逆向或开源客户端。代码搜索 dingding-login 的 60 条命中全部是 vue-vben-admin 第三方登录框架自带文件(术语噪声)。webvpn.nbcc.edu.cn, jwglapp.nbcc.cn, "app-web eams", "nbcc 课表", "宁波城市职业 课表" 定向代码搜索均零相关命中。
- 最接近的公开"移动教务"形态是 MilLoong/UESTC-EAMS-Helper-App: 成电 eamsapp.uestc.edu.cn 的 REST 封装(/api/ydzc-app/semester/getCurSemester, /api/ydzc-app/studentCourseTable/week?semester=&code=&week=, /api/ydzc-app/grade/student, /api/blade-user/appInfo), 会话为 ticket 换 JWT(JSESSIONID 值即 JWT, claims 里 user_name/account 为学号), 登录经 idas CAS(短信二次认证 reAuthType 映射含 12 等于钉钉动态码类型 reAuthDingTalkDynamicCodeType,见 CasLoginRepository.kt mapReAuthToAuthCodeTypeName)。该校是 BladeX 架构,与 NBCC 正方 Ionic SPA 不同构,只能借"票据换 JWT"与"MFA 兜底"设计经验。

## 3. 密码传递器先例(类别 c,结论: 存在)

- Wangxiaolu0716/class-schedule(lib/import/webview_import_screen.dart): 本校学生实际采用、且在本校成功的形态。WebView 打开 webvpn.nbcc.cn 门户,凭据始终由用户在真实教务页面输入,应用不接触账号密码;WebVPN 会把页面 jQuery/Beangle 脚本打残导致页面自身 searchTable() 静默失败,解法是在页面上下文里直接 fetch('/eams/courseTableForStd!courseTable.action',{credentials:'same-origin'}) 同源取数,轮询 window.__nbccState;因 WebVPN 把资源改写成 http 需 setMixedContentMode(alwaysAllow);多 frame 用 NBCC-FRAME 标记 DFS 收集 HTML。该形态绕开 wengine AES URL 链与钉钉换票链。
- FHfanshu/ZJXU-CampusHub(lib/features/webview/webview_page.dart 的 WebViewQuickFill, lib/core/auth/credential_store.dart): 凭据存储加 setInputValue 自动填充登录表单,选择器 ['#mm','input[name="mm"]','input[type="password"]'] 与 ['#password',...];真实凭据存储在开源版被打桩("Real credential storage implementation is not included"),公开面只有骨架。先例成立,但完整实现闭源。
- NBCC 登录页自身即 URL scheme 唤起钉钉再回跳的公开实现(dingtalk://...ddtab=true, 回跳 /login?dingding_login=true),可作为"非钉钉容器完成钉钉授权"的本地先例;但按纠偏指示,该路线已被学生实际行为否决,仅留痕。

## 4. NBCC 学生已有开源工具(类别 d,结论: 有)

- Wangxiaolu0716/class-schedule(Dart/Flutter, 1 星, 最近推送 2026-10-20, PolyForm Noncommercial 1.0.0): 作者自述"我是宁波城市职业技术学院的学生,这个 App 最早是给本校做的";内置 nbcc SchoolConfig、正方 EAMS TaskActivity 解析器(lib/import/eams_course_table_parser.dart, 关键正则: var unitCount=(\d+), new TaskActivity\((.*)\);, index=(\d+)\*unitCount\+(\d+);, actTeachers, semester.id, ids, addInput(form,"ids","N"), semesterCalendar value; 周位图为第 5 个引号参数, 1 基索引), 109 个测试用例, fixture 已脱敏。school_config.dart 注释明确: 强制 Pixel 7 移动端 UA,因为"桌面端登录流程不可用(只有手机端能登进去)";开课周由移动端"第5周=10-05~10-11"反推为 2026-09-07。
- LonelyMarch/OpenWakeUp(Kotlin, AGPL-3.0, 7 星): schools.json 收录宁波城市职业技术学院(type=xatu_shuwei, url=webvpn.nbcc.cn/users/sign_in),如上所述类型标注与 fixture 证据冲突。
- 检索矩阵中另有三条 NBCC 词面命中但与教务无关(vczero/serverless-colleage, 1837620622/gaokao-admission-crawler, SunGg12138/china-colleges,均为高校名录数据)。
- 本校仓库 lingion/sleepy 无 NBCC 条目(grep 宁波城市 零命中),本次为从零适配。

## 5. 公开论坛/搜索面取证(任务第 3 项)

- WebSearch 四组查询(教务系统 网页版/电脑端 怎么进, 正方 jwgl.webvpn.nbcc 登录, 知乎/贴吧形态)未返回有效学生描述页;本地 searXNG(http://127.0.0.1:8888)三轮查询命中: webvpn.nbcc.cn 与 webvpn.nbcc.edu.cn 官方页标题、免客户端说明"帐号和密码与学校统一信息门户一致,帐号被锁等待自动解锁,需修改密码请到钉钉工作台应用"(https://172-18-10-16-850.webvpn.nbcc.cn/ 公开快照文案)、dnsdblookup 的 jwgl.webvpn.nbcc.cn 子域记录、山东航空学院登录页 URL 展示的 wengine /http/<hex>/portal/login.html 形态旁证。知乎、贴吧无 NBCC 教务电脑端描述帖可引。结论: "PC 端可达教务"的主证据由本校学生开源仓库(class-schedule 的 jwgl.webvpn.nbcc.cn 直链与脱敏 fixture)与门户登录页公开 HTML 构成,论坛面零命中如实记录。

## 6. 钉钉路线(已降级否决,留痕)

- 门户移动版钉钉 SPA(jwglapp.nbcc.cn /app-web/ dingding-login, JSBridge 换票, 非钉钉环境"请求验证码错误")在本次全部公开检索中未获得任何公开逆向证据;学生行为纠偏后该路线不需要适配。唯一保留价值: 门户登录页已公开示明 auth_type=local 的账号密码通道与 /do-login 端点存在。

## 7. 检索矩阵统计与诚实性声明

- 三阶段检索矩阵(webvpn/wengine/wrdvpn/深信服/eams/eams5/正方/dingding-login/nbcc/宁波城市职业/shuwei/netease 等组合, repositories 与 code 双端点, gh api 限速 2.5 秒节奏)合并去重 561 仓库;相关性分类命中且未被否决者进入 candidates.json 共 268 条(含 3 条深读钉选仓库经 /repos API 补元数据后注入: class-schedule, OpenWakeUp, ZJXU-CampusHub)。唯一否决 1 条(archived 且推送早于 2016)。噪声 340 条保留在 noise-excluded.json(含逐条理由,遵守全量纳入)。
- 诚实性注记: 多个 code 查询在 per_page 上限处截断(恰 50 或 60 条),已用追加细化查询与交集查询扩大覆盖,但无法保证穷尽;钉钉词面命中绝大多数为 vue-vben-admin 噪声;OpenWakeUp 条目 URL 的域名是 nbcc.cn 而门户登录页实际在 nbcc.edu.cn(nbcc.cn 302 过去),两域名同源一部署。
- candidates.json 每条已含 pc_entry_evidence 字段(纠偏第 3 点): 深读仓库逐条填写,其余为"无"。

## 8. 对 sleepy 适配的证据支撑建议(仅调研结论,不含实现)

- 最贴合 NBCC 的登录链形态,按证据强度排序: 其一,WebView 门户登录加同源 fetch 教务 action(class-schedule 在本校实测成功,免碰 wengine AES 链与钉钉链);其二,原生 wengine 账号密码链(登录页已公开全部要素: /do-login 表单字段, 密码 AES-CFB key/iv 'wrdvpnisawesome!', 磁贴 hex 前缀推导式),参照 wenginetunnel 与 HFUT-Schedule 的成熟实现;其三,教务协议直接用正方 EAMS 桌面 TaskActivity 族(非树维)。UA 分流教训: 进门户用桌面 UA 可保留账号密码直登渲染,移动 UA 会被引向钉钉路线——与学生实际行为及 class-schedule 注释互为印证。
