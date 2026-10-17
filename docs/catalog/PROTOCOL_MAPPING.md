# WakeUp → 本协议映射裁决（Wave 2/3 阶段一）

裁决日期：2026-10-17。裁决对象：`scripts/data/school_import_admissions.json` 全部 2669 条
admissions（前 170 条人工评审行冻结不动，批次 2499 行全部逐条裁决）。
裁决脚本：`scripts/adjudicate_protocol_mapping.py`（确定性、可重放；证据来源 =
batch probe（`batch-probes.jsonl` 字段已内嵌 admission.probe）+ 页面抓取
`entry-evidence-captures.jsonl`）。

## 允许的映射依据（与 App 检测器一一对应）

URL 强特征（`JwImportViewModel.detectProtocolFromUrlImpl` 同源，webvpn 壳先剥离）：

| 特征 | 协议 |
|---|---|
| `jwglxt` / `/xtgl/` / `login_slogin` / `/kbcx/` / `xskbcx_cx` | `zf_new` |
| `default2.aspx` / `xskbcx.aspx` / `xs_main` | `zf` |
| `/jsxsd/` / `/jxd/` / `logon.do` / `verifycode.servlet` / `qzdatasoft` | `qz` |
| `eams…/login(.action)` / `loginExt.action` / `homeExt.action` / `courseTableForStd` | `classic_eams` |
| `jxglstu` / `/eams5-student` / `/for-std/` | `eams5` |
| `mh.chaoxing` / `jw.chaoxing` / `/xsd/` / `queryKbForGrdb` / `sdpkkblist` | `chaoxing` |
| `/jwapp/` | `wisedu` |
| `wengine` / `shuwei` | `shuwei_json` |
| `xkaction.do` / `actionType=6` | `urp` |
| `/xsgrkbcx` / `/new/xskb` | `cf` |
| `elective.pku` / `iaaa.pku` | `pku` |
| `kdjw.hnust` / `xxjw.hnust` | `hnust` |

页面指纹（标题/可见正文，`detectProtocolFromHtmlImpl` 同源）：
`KINGOSOFT`→`kingo_new`（青果品牌，非超星——WakeUp 把"青果"错标 `ziyan` 正是本族来源）；
`教学管理信息服务平台`/`zftal-ui`→`zf_new`；`超星综合教务`/`Powered by ChaoXing`→`chaoxing`；
`强智科技`/`教学一体化服务平台`/`qzdatasoft.com`→`qz`；`正方教务管理系统`/`__VIEWSTATE`→`zf`；
`教务网络管理系统` 且 URL 含 `jwweb`→`kingo_new`；`金智`→`wisedu`；`乘方教务/科技`→`cf`。

**排除项（不构成证据）**：CAS/`authserver` 纯认证壳、webvpn 代理壳、泛化登录页标题
（`统一身份认证`/`用户登录`/`登录页面` 等）、403/404/5xx 错误页、以及任何"看起来像"的
名称巧合。REDACTED 会话 URL 永不参与取证。

## 裁决规则（fail-closed）

1. 每条批次行独立裁决；证据引擎与上面两张表完全一致。
2. 已声明类型可路由且有正向证据 → 保留（keep）。
3. 工作树未提交 remap 差量：新类型有证据且无矛盾 → 保留；否则回退 HEAD 声明值。
4. 声明不可路由（ziyan/私有）→ 仅当存在**唯一**可路由协议锚点时升级；多协议矛盾
   或无证据 → 维持不可路由，学校留 pending。
5. 已声明可路由但证据不足的行：保留原声明（撤销已发布条目需要"证伪"证据，
   而非仅"未证实"；矛盾证据已按规则 3 处理回退）。
6. 全量逐行裁决轨迹在 `scripts/data/adjudication-log.jsonl`（idx、key、declared 源
   类型、headType、证据三元组 [协议, 来源字段, 依据]、verdict、reasons）。

## 汇总

- 批次行 2499：keep 1578；remap（含回退与升级）8；unknown（维持不可路由）913；frozen 基线行 170
- 最终文件相对 HEAD 的类型变化：12 条（其中保留工作树 remap：12；回退工作树 remap：0；其余为证据升级）
- 裁决后仍不可路由（pending）：913 条；可路由且 URL 可用：1751 条

## 逐条依据表（全部 2499 批次行；sourceRow 为 WakeUp 源表行号，括号内为来源 id）

| sourceRow | name | 原类型(HEAD) | 新类型 | 裁决 | 证据/理由 | 日期 |
|---|---|---|---|---|---|---|
| 2596（wakeup-6.1.06-asset） | 三亚学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2633（wakeup-6.1.06-asset） | 三亚中瑞酒店管理职业学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 2154（wakeup-6.1.06-asset） | 七台河职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2322（wakeup-6.1.06-asset） | 三亚城市职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2597（wakeup-6.1.06-asset） | 三亚理工职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2321（wakeup-6.1.06-asset） | 三亚航空旅游职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2333（wakeup-6.1.06-asset） | 三门峡社会管理职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2447（wakeup-6.1.06-asset） | 三峡电力职业学院 | ctgu | ctgu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2265（wakeup-6.1.06-asset） | 三明医学科技职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2382（wakeup-6.1.06-asset） | 上海东海职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1749（wakeup-52.36-user-attachment） | City University of Hong Kong | cityu | cityu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2464（wakeup-6.1.06-asset） | 三门峡职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2592（wakeup-6.1.06-asset） | 上海体育大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2504（wakeup-6.1.06-asset） | 上海中医药大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2385（wakeup-6.1.06-asset） | 上海中侨职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2369（wakeup-6.1.06-asset） | 上海农林职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2386（wakeup-6.1.06-asset） | 上海兴伟学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2387（wakeup-6.1.06-asset） | 上海公安学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2606（wakeup-6.1.06-asset） | 上海出版印刷高等专科学校 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2366（wakeup-6.1.06-asset） | 上海南湖职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2497（wakeup-6.1.06-asset） | 上海外国语大学贤达经济人文学院 | shuwei_json | shuwei_json | keep | URL anchor wengine/shuwei | 2026-10-17 |
| 814（wakeup-52.36-user-attachment） | 上海商学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 2378（wakeup-6.1.06-asset） | 上海交通职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1755（wakeup-52.36-user-attachment） | 上海交通大学医学院 | shsmu | shsmu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2367（wakeup-6.1.06-asset） | 上海工会管理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2611（wakeup-6.1.06-asset） | 上海大学（2024新版） | shu2024 | shu2024 | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2381（wakeup-6.1.06-asset） | 上海工商职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2371（wakeup-6.1.06-asset） | 上海工商外国语职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 817（wakeup-52.36-user-attachment） | 上海对外经贸大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1792（wakeup-52.36-user-attachment） | 上海工程技术大学 | eams5 | eams5 | keep | URL anchor eams5 (jxglstu/for-std) | 2026-10-17 |
| 1793（wakeup-52.36-user-attachment） | 上海工程技术大学（自动导入时间） | eams5 | eams5 | keep | URL anchor eams5 (jxglstu/for-std) | 2026-10-17 |
| 2373（wakeup-6.1.06-asset） | 上海工艺美术职业学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 1987（wakeup-52.36-user-attachment） | 上海工程技术大学高等职业技术学院 | simc | simc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2282（wakeup-6.1.06-asset） | 上海市普陀区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2284（wakeup-6.1.06-asset） | 上海市长宁区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2379（wakeup-6.1.06-asset） | 上海城建职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2286（wakeup-6.1.06-asset） | 上海市黄浦区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 819（wakeup-52.36-user-attachment） | 上海应用技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2283（wakeup-6.1.06-asset） | 上海市静安区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2285（wakeup-6.1.06-asset） | 上海市徐汇区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1986（wakeup-52.36-user-attachment） | 上海市高级技工学校 | simc | simc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2374（wakeup-6.1.06-asset） | 上海思博职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2642（wakeup-6.1.06-asset） | 上海师范大学 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1350（wakeup-52.36-user-attachment） | 上海建桥学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1694（wakeup-52.36-user-attachment） | 上海杉达学院 | shuwei_m | shuwei_m | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2280（wakeup-6.1.06-asset） | 上海开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2388（wakeup-6.1.06-asset） | 上海戏剧学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2604（wakeup-6.1.06-asset） | 上海民航职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2383（wakeup-6.1.06-asset） | 上海旅游高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2375（wakeup-6.1.06-asset） | 上海民远职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2372（wakeup-6.1.06-asset） | 上海济光职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2362（wakeup-6.1.06-asset） | 上海浦东职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2389（wakeup-6.1.06-asset） | 上海海事大学 | shuwei_json | shuwei_json | keep | URL anchor wengine/shuwei | 2026-10-17 |
| 2377（wakeup-6.1.06-asset） | 上海海事职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 821（wakeup-52.36-user-attachment） | 上海理工大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 820（wakeup-52.36-user-attachment） | 上海海关学院 | shcc | shcc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2365（wakeup-6.1.06-asset） | 上海科创职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2506（wakeup-6.1.06-asset） | 上海电子信息职业技术学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2363（wakeup-6.1.06-asset） | 上海现代化工职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2287（wakeup-6.1.06-asset） | 上海科技管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2370（wakeup-6.1.06-asset） | 上海科学技术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 823（wakeup-52.36-user-attachment） | 上海电机学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2621（wakeup-6.1.06-asset） | 上海科技大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2619（wakeup-6.1.06-asset） | 上海电力大学 | uestc_shuwei | uestc_shuwei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 826（wakeup-52.36-user-attachment） | 上海立达学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2281（wakeup-6.1.06-asset） | 上海纺织工业职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2624（wakeup-6.1.06-asset） | 上海第二工业大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1745（wakeup-52.36-user-attachment） | 上海视觉艺术学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2384（wakeup-6.1.06-asset） | 上海纽约大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2380（wakeup-6.1.06-asset） | 上海行健职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2376（wakeup-6.1.06-asset） | 上海震旦职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2627（wakeup-6.1.06-asset） | 上海音乐学院 | ecupl | ecupl | keep | declared routable type retained | 2026-10-17 |
| 2351（wakeup-6.1.06-asset） | 上饶师范学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2349（wakeup-6.1.06-asset） | 上饶卫生健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2475（wakeup-6.1.06-asset） | 上饶幼儿师范高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2368（wakeup-6.1.06-asset） | 上海邦德职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2364（wakeup-6.1.06-asset） | 上海闵行职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1333（wakeup-52.36-user-attachment） | 东北师范大学人文学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 560（wakeup-6.1.06-asset） | 东北师范大学 | qz_old | qz_old | keep | declared routable type retained | 2026-10-17 |
| 828（wakeup-52.36-user-attachment） | 上海财经大学浙江学院 | zf_new | zf_new | remap | conflict: shuwei_json via URL anchor wengine/shuwei | 2026-10-17 |
| 207（wakeup-52.36-user-attachment） | 东南大学2017 | seu_2017 | seu_2017 | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 518（wakeup-6.1.06-asset） | 东华理工大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 523（wakeup-6.1.06-asset） | 东莞城市学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 512（wakeup-6.1.06-asset） | 东北电力大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 201（wakeup-52.36-user-attachment） | 东北农业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 212（wakeup-52.36-user-attachment） | 东莞职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 562（wakeup-6.1.06-asset） | 东营科技职业学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 2350（wakeup-6.1.06-asset） | 上饶职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 210（wakeup-52.36-user-attachment） | 东莞理工学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3504（wakeup-6.1.06-asset） | 中北大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1181（wakeup-52.36-user-attachment） | 中南林业科技大学涉外学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 208（wakeup-52.36-user-attachment） | 东南大学成贤学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 564（wakeup-6.1.06-asset） | 东营职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1182（wakeup-52.36-user-attachment） | 中南民族大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3564（wakeup-6.1.06-asset） | 中南海业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3390（wakeup-6.1.06-asset） | 中国人民公安大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1184（wakeup-52.36-user-attachment） | 中原工学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3498（wakeup-6.1.06-asset） | 中南财经政法大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3530（wakeup-6.1.06-asset） | 中原科技学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3489（wakeup-6.1.06-asset） | 中国刑事警察学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1180（wakeup-52.36-user-attachment） | 中华女子学院 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1192（wakeup-52.36-user-attachment） | 中国劳动关系学院 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1739（wakeup-52.36-user-attachment） | 中国人民警察大学 | cppu | cppu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1193（wakeup-52.36-user-attachment） | 中国地质大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3482（wakeup-6.1.06-asset） | 中国政法大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1196（wakeup-52.36-user-attachment） | 中国地质大学（武汉） | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1189（wakeup-52.36-user-attachment） | 中国传媒大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3510（wakeup-6.1.06-asset） | 中国戏曲学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1195（wakeup-52.36-user-attachment） | 中国地质大学（北京） | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 3299（wakeup-6.1.06-asset） | 中国民用航空飞行学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3412（wakeup-6.1.06-asset） | 中国消防救援学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3478（wakeup-6.1.06-asset） | 中国海洋大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3474（wakeup-6.1.06-asset） | 中国石油大学（华东） | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 3512（wakeup-6.1.06-asset） | 中国社会科学院大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3569（wakeup-6.1.06-asset） | 中国科学院成都分院职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3480（wakeup-6.1.06-asset） | 中国民航大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1204（wakeup-52.36-user-attachment） | 中国石油大学（北京） | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 3468（wakeup-6.1.06-asset） | 中国美术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1205（wakeup-52.36-user-attachment） | 中国石油大学（北京）克拉玛依校区 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3511（wakeup-6.1.06-asset） | 中国青年政治学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3464（wakeup-6.1.06-asset） | 中国音乐学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3383（wakeup-6.1.06-asset） | 中央民族大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1213（wakeup-52.36-user-attachment） | 中央司法警官学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 3509（wakeup-6.1.06-asset） | 中央戏剧学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3508（wakeup-6.1.06-asset） | 中央美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3385（wakeup-6.1.06-asset） | 中央音乐学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 1217（wakeup-52.36-user-attachment） | 中山大学南方学院 | nfu | nfu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3568（wakeup-6.1.06-asset） | 中钢集团衡阳重机职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1871（wakeup-6.1.06-asset） | 临夏现代职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1216（wakeup-52.36-user-attachment） | 中山大学 | sysu | sysu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1827（wakeup-6.1.06-asset） | 临汾职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1848（wakeup-6.1.06-asset） | 临沂科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1864（wakeup-6.1.06-asset） | 临沧职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1850（wakeup-6.1.06-asset） | 临沂城市职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1862（wakeup-6.1.06-asset） | 丽江师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1843（wakeup-6.1.06-asset） | 丽江师范高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1218（wakeup-52.36-user-attachment） | 中山大学新华学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1759（wakeup-6.1.06-asset） | 丽江文化旅游学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3214（wakeup-6.1.06-asset） | 义乌工商职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2819（wakeup-6.1.06-asset） | 乌海职业技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1888（wakeup-6.1.06-asset） | 丽水职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1863（wakeup-6.1.06-asset） | 丽江职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 654（wakeup-52.36-user-attachment） | 乐山师范学院 | zf_new | zf_new | keep | page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1628（wakeup-6.1.06-asset） | 九江卫生健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1596（wakeup-6.1.06-asset） | 九州职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2906（wakeup-6.1.06-asset） | 乌兰察布职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3255（wakeup-6.1.06-asset） | 云南三鑫职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌）; page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1492（wakeup-6.1.06-asset） | 九江职业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1653（wakeup-6.1.06-asset） | 九江科技职业大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 651（wakeup-52.36-user-attachment） | 丽水学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3161（wakeup-6.1.06-asset） | 云南中医药大学 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1528（wakeup-6.1.06-asset） | 九江职业技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3246（wakeup-6.1.06-asset） | 云南交通运输职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3216（wakeup-6.1.06-asset） | 云南交通职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3211（wakeup-6.1.06-asset） | 云南医药健康职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 3223（wakeup-6.1.06-asset） | 云南农业职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1143（wakeup-52.36-user-attachment） | 云南国土资源职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3261（wakeup-6.1.06-asset） | 云南体育运动职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1144（wakeup-52.36-user-attachment） | 云南城市建设职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3208（wakeup-6.1.06-asset） | 云南大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3258（wakeup-6.1.06-asset） | 云南工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3247（wakeup-6.1.06-asset） | 云南工贸职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3263（wakeup-6.1.06-asset） | 云南司法警官职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3251（wakeup-6.1.06-asset） | 云南外事外语职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1152（wakeup-52.36-user-attachment） | 云南师范大学文理学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1149（wakeup-52.36-user-attachment） | 云南师范大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 3205（wakeup-6.1.06-asset） | 云南工商学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3257（wakeup-6.1.06-asset） | 云南新兴职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1151（wakeup-52.36-user-attachment） | 云南师范大学商学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3262（wakeup-6.1.06-asset） | 云南文化艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3237（wakeup-6.1.06-asset） | 云南开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3249（wakeup-6.1.06-asset） | 云南水利水电职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1153（wakeup-52.36-user-attachment） | 云南机电职业技术学院 | buaa | buaa | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3248（wakeup-6.1.06-asset） | 云南特殊教育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3252（wakeup-6.1.06-asset） | 云南旅游职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1155（wakeup-52.36-user-attachment） | 云南民族大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 3245（wakeup-6.1.06-asset） | 云南理工职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 3253（wakeup-6.1.06-asset） | 云南现代职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3254（wakeup-6.1.06-asset） | 云南商务职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3259（wakeup-6.1.06-asset） | 云南科技信息职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3256（wakeup-6.1.06-asset） | 云南经贸外事职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3162（wakeup-6.1.06-asset） | 云南能源职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3195（wakeup-6.1.06-asset） | 云南艺术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1156（wakeup-52.36-user-attachment） | 云南经济管理学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1157（wakeup-52.36-user-attachment） | 云南经济职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1159（wakeup-52.36-user-attachment） | 云南艺术学院文华学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3156（wakeup-6.1.06-asset） | 云南锡业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3192（wakeup-6.1.06-asset） | 云南财经大学中华职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3264（wakeup-6.1.06-asset） | 云南警官学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3250（wakeup-6.1.06-asset） | 云南财经职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3191（wakeup-6.1.06-asset） | 云南财经大学国际工商学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1590（wakeup-6.1.06-asset） | 井冈山大学 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 1223（wakeup-52.36-user-attachment） | 仲恺农业工程学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 3189（wakeup-6.1.06-asset） | 伊犁师范大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1489（wakeup-6.1.06-asset） | 伊犁职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2930（wakeup-6.1.06-asset） | 仙桃职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3285（wakeup-6.1.06-asset） | 仰恩大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 234（wakeup-52.36-user-attachment） | 佛山科学技术学院 | qz_crazy | qz_crazy | keep | declared routable type retained | 2026-10-17 |
| 244（wakeup-6.1.06-asset） | 保定幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1710（wakeup-6.1.06-asset） | 佳木斯大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 188（wakeup-6.1.06-asset） | 保定职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 117（wakeup-6.1.06-asset） | 保定理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 659（wakeup-6.1.06-asset） | 佛山大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 251（wakeup-6.1.06-asset） | 保险职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 258（wakeup-6.1.06-asset） | 保山职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 236（wakeup-52.36-user-attachment） | 佛山职业技术学院（推荐） | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3124（wakeup-6.1.06-asset） | 信阳农林学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2989（wakeup-6.1.06-asset） | 信阳航空职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2988（wakeup-6.1.06-asset） | 信阳科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3111（wakeup-6.1.06-asset） | 信阳职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2931（wakeup-6.1.06-asset） | 信阳学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1847（wakeup-6.1.06-asset） | 六安应用科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1726（wakeup-6.1.06-asset） | 克拉玛依职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 885（wakeup-6.1.06-asset） | 公安部管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1724（wakeup-6.1.06-asset） | 克孜勒苏职业技术学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1846（wakeup-6.1.06-asset） | 六安职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1861（wakeup-6.1.06-asset） | 六盘水幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1320（wakeup-52.36-user-attachment） | 六盘水职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 656（wakeup-52.36-user-attachment） | 兰州交通大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1809（wakeup-6.1.06-asset） | 六盘水师范学院 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 3148（wakeup-6.1.06-asset） | 信阳艺术职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 658（wakeup-52.36-user-attachment） | 兰州城市学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1845（wakeup-6.1.06-asset） | 兰州工业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1882（wakeup-6.1.06-asset） | 兰州博文科技学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 659（wakeup-52.36-user-attachment） | 兰州大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1868（wakeup-6.1.06-asset） | 兰州外语职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1870（wakeup-6.1.06-asset） | 兰州科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1624（wakeup-52.36-user-attachment） | 兰州工商学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1872（wakeup-6.1.06-asset） | 兰州现代职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1766（wakeup-6.1.06-asset） | 兰州文理学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1869（wakeup-6.1.06-asset） | 兰州职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1889（wakeup-6.1.06-asset） | 兰州资源环境职业技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1999（wakeup-6.1.06-asset） | 内江卫生与健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1866（wakeup-6.1.06-asset） | 兰州石化职业技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2000（wakeup-6.1.06-asset） | 内江职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2935（wakeup-6.1.06-asset） | 兴安职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1873（wakeup-6.1.06-asset） | 兰州航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1686（wakeup-6.1.06-asset） | 冀中职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2023（wakeup-6.1.06-asset） | 内蒙古体育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 703（wakeup-52.36-user-attachment） | 内江师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2022（wakeup-6.1.06-asset） | 内蒙古交通职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2026（wakeup-6.1.06-asset） | 内蒙古医科大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1953（wakeup-6.1.06-asset） | 内蒙古大学创业学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2024（wakeup-6.1.06-asset） | 内蒙古商贸职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2021（wakeup-6.1.06-asset） | 内蒙古工业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1934（wakeup-6.1.06-asset） | 内蒙古化工职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 707（wakeup-52.36-user-attachment） | 内蒙古工业大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1937（wakeup-6.1.06-asset） | 内蒙古师范大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1945（wakeup-6.1.06-asset） | 内蒙古建筑职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 709（wakeup-52.36-user-attachment） | 内蒙古民族大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 704（wakeup-52.36-user-attachment） | 内蒙古农业大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1986（wakeup-6.1.06-asset） | 内蒙古开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1979（wakeup-6.1.06-asset） | 内蒙古机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2019（wakeup-6.1.06-asset） | 内蒙古民族幼儿师范高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 711（wakeup-52.36-user-attachment） | 内蒙古科技大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2020（wakeup-6.1.06-asset） | 内蒙古美术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 710（wakeup-52.36-user-attachment） | 内蒙古电子信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 120（wakeup-6.1.06-asset） | 包头职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2101（wakeup-6.1.06-asset） | 内蒙古财经大学 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2025（wakeup-6.1.06-asset） | 内蒙古鸿德文理学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 245（wakeup-6.1.06-asset） | 包头钢铁职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 246（wakeup-6.1.06-asset） | 包头铁道职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 241（wakeup-6.1.06-asset） | 北京交通运输职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 187（wakeup-6.1.06-asset） | 包头轻工职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1959（wakeup-6.1.06-asset） | 内蒙古艺术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1730（wakeup-52.36-user-attachment） | 北京交通职业技术学院 | xytc | xytc | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 124（wakeup-6.1.06-asset） | 北京体育大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 189（wakeup-6.1.06-asset） | 北京信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 126（wakeup-6.1.06-asset） | 北京农学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 232（wakeup-6.1.06-asset） | 北京京北职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 202（wakeup-6.1.06-asset） | 北京农业职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 205（wakeup-6.1.06-asset） | 北京中医药大学东方学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 127（wakeup-6.1.06-asset） | 北京劳动保障职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 227（wakeup-6.1.06-asset） | 北京协和医学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 240（wakeup-6.1.06-asset） | 北京培黎职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 130（wakeup-6.1.06-asset） | 北京城市学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 183（wakeup-6.1.06-asset） | 北京印刷学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 242（wakeup-6.1.06-asset） | 北京卫生职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 234（wakeup-6.1.06-asset） | 北京北大方正软件职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 223（wakeup-6.1.06-asset） | 北京大学（医学部） | pku | pku | keep | URL anchor pku host | 2026-10-17 |
| 209（wakeup-6.1.06-asset） | 北京大学医学部 | pku | pku | keep | URL anchor pku host | 2026-10-17 |
| 222（wakeup-6.1.06-asset） | 北京大学（校本部） | pku | pku | keep | URL anchor pku host | 2026-10-17 |
| 224（wakeup-6.1.06-asset） | 北京大学（深圳研究院） | pku | pku | keep | URL anchor pku host | 2026-10-17 |
| 133（wakeup-6.1.06-asset） | 北京工业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 186（wakeup-6.1.06-asset） | 北京工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 65（wakeup-52.36-user-attachment） | 北京外国语大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 70（wakeup-52.36-user-attachment） | 北京师范大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 206（wakeup-6.1.06-asset） | 北京工商大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 69（wakeup-52.36-user-attachment） | 北京工商大学嘉华学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 262（wakeup-6.1.06-asset） | 北京市总工会职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 261（wakeup-6.1.06-asset） | 北京市石景山区业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1905（wakeup-52.36-user-attachment） | 北京师范大学-香港浸会大学联合国际学院 | uic | uic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 228（wakeup-6.1.06-asset） | 北京师范大学（北京校区） | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 237（wakeup-6.1.06-asset） | 北京戏曲艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 173（wakeup-6.1.06-asset） | 北京建筑大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 229（wakeup-6.1.06-asset） | 北京师范大学（珠海校区） | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 233（wakeup-6.1.06-asset） | 北京政法职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 140（wakeup-6.1.06-asset） | 北京服装学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 238（wakeup-6.1.06-asset） | 北京汇佳职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 71（wakeup-52.36-user-attachment） | 北京师范大学珠海校区 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 73（wakeup-52.36-user-attachment） | 北京物资学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1696（wakeup-52.36-user-attachment） | 北京电影学院 | bfa | bfa | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 263（wakeup-6.1.06-asset） | 北京教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 226（wakeup-6.1.06-asset） | 北京电子科技学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 264（wakeup-6.1.06-asset） | 北京开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 74（wakeup-52.36-user-attachment） | 北京理工大学珠海学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 197（wakeup-6.1.06-asset） | 北京科技大学 | qz_ustb | qz_ustb | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 221（wakeup-6.1.06-asset） | 北京科技职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 239（wakeup-6.1.06-asset） | 北京科技职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 236（wakeup-6.1.06-asset） | 北京经济技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 76（wakeup-52.36-user-attachment） | 北京石油化工学院 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 78（wakeup-52.36-user-attachment） | 北京科技大学天津学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 207（wakeup-6.1.06-asset） | 北京经济管理职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 150（wakeup-6.1.06-asset） | 北京联合大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 235（wakeup-6.1.06-asset） | 北京经贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 182（wakeup-6.1.06-asset） | 北京第二外国语学院中瑞酒店管理学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 149（wakeup-6.1.06-asset） | 北京第二外国语学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 252（wakeup-6.1.06-asset） | 北京航空航天大学北海学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 181（wakeup-6.1.06-asset） | 北京语言大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 230（wakeup-6.1.06-asset） | 北京舞蹈学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1438（wakeup-52.36-user-attachment） | 北京邮电大学世纪学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 153（wakeup-6.1.06-asset） | 北京警察学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 243（wakeup-6.1.06-asset） | 北京网络职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 156（wakeup-6.1.06-asset） | 北华大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 84（wakeup-52.36-user-attachment） | 北京邮电大学 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 154（wakeup-6.1.06-asset） | 北京财贸职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 231（wakeup-6.1.06-asset） | 北京金融科技学院（原北京工商大学嘉华学院） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 201（wakeup-6.1.06-asset） | 北京青年政治学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 225（wakeup-6.1.06-asset） | 北方工业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 157（wakeup-6.1.06-asset） | 北华航天工业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 158（wakeup-6.1.06-asset） | 北方民族大学 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 90（wakeup-52.36-user-attachment） | 北部湾大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 191（wakeup-6.1.06-asset） | 北海康养职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 203（wakeup-6.1.06-asset） | 北海职业学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 371（wakeup-52.36-user-attachment） | 华东交通大学 | ecjtu | ecjtu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 160（wakeup-6.1.06-asset） | 北海艺术设计学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 375（wakeup-52.36-user-attachment） | 华中科技大学 | hust | hust | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 373（wakeup-52.36-user-attachment） | 华东师范大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 376（wakeup-52.36-user-attachment） | 华北水利水电大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1115（wakeup-6.1.06-asset） | 华北理工大学冀唐学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 377（wakeup-52.36-user-attachment） | 华北理工大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1349（wakeup-6.1.06-asset） | 华北电力大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 174（wakeup-6.1.06-asset） | 北海职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 381（wakeup-52.36-user-attachment） | 华北电力大学科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 379（wakeup-52.36-user-attachment） | 华北电力大学保定校区 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1361（wakeup-6.1.06-asset） | 华北科技学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1444（wakeup-52.36-user-attachment） | 华北理工大学轻工学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 382（wakeup-52.36-user-attachment） | 华南农业大学珠江学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 714（wakeup-52.36-user-attachment） | 南京中医药大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 716（wakeup-52.36-user-attachment） | 南京中医药大学（泰州） | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 2017（wakeup-6.1.06-asset） | 南京交通职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 717（wakeup-52.36-user-attachment） | 南京中医药大学翰林学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 721（wakeup-52.36-user-attachment） | 南京农业大学 | qz_single_node | qz_single_node | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 720（wakeup-52.36-user-attachment） | 南京信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 727（wakeup-52.36-user-attachment） | 南京审计大学 | nau | nau | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 723（wakeup-52.36-user-attachment） | 南京医科大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2018（wakeup-6.1.06-asset） | 南京体育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2102（wakeup-6.1.06-asset） | 南京工业职业技术大学 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 728（wakeup-52.36-user-attachment） | 南京审计大学金审学院 | nau | nau | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2014（wakeup-6.1.06-asset） | 南京城市职业学院（南京开放大学） | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1964（wakeup-6.1.06-asset） | 南京师范大学泰州学院 | nnutc | nnutc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 731（wakeup-52.36-user-attachment） | 南京师范大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2050（wakeup-6.1.06-asset） | 南京晓庄学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 735（wakeup-52.36-user-attachment） | 南京森林警察学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2109（wakeup-6.1.06-asset） | 南京理工大学紫金学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 2016（wakeup-6.1.06-asset） | 南京科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2038（wakeup-6.1.06-asset） | 南京医科大学康达学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1453（wakeup-52.36-user-attachment） | 南京航空航天大学金城学院 | nuaajc | nuaajc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1687（wakeup-52.36-user-attachment） | 南京艺术学院 | nua | nua | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2015（wakeup-6.1.06-asset） | 南京视觉艺术职业学院 | zf | zf | keep | declared routable type retained | 2026-10-17 |
| 2046（wakeup-6.1.06-asset） | 南京工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1997（wakeup-6.1.06-asset） | 南充文化旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1996（wakeup-6.1.06-asset） | 南充电影工业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 738（wakeup-52.36-user-attachment） | 南京财经大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 736（wakeup-52.36-user-attachment） | 南京特殊教育师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1973（wakeup-6.1.06-asset） | 南京铁道职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1998（wakeup-6.1.06-asset） | 南充科技职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2063（wakeup-6.1.06-asset） | 南华大学船山学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 746（wakeup-52.36-user-attachment） | 南宁理工学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 748（wakeup-52.36-user-attachment） | 南开大学滨海学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2002（wakeup-6.1.06-asset） | 南宁师范大学师园学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2070（wakeup-6.1.06-asset） | 南方科技大学 | login | login | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1949（wakeup-6.1.06-asset） | 南京警察学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2007（wakeup-6.1.06-asset） | 南昌健康职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1931（wakeup-6.1.06-asset） | 南昌交通学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1975（wakeup-6.1.06-asset） | 南昌大学共青学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2001（wakeup-6.1.06-asset） | 南宁职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2008（wakeup-6.1.06-asset） | 南昌大学科学技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1976（wakeup-6.1.06-asset） | 南昌医学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2077（wakeup-6.1.06-asset） | 南昌航空大学科技学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1449（wakeup-52.36-user-attachment） | 南昌师范学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1984（wakeup-6.1.06-asset） | 南海成人学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1958（wakeup-6.1.06-asset） | 南昌职业大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2079（wakeup-6.1.06-asset） | 南通大学杏林学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 759（wakeup-52.36-user-attachment） | 南通理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 758（wakeup-52.36-user-attachment） | 南通理工学院（2023届及之后） | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1961（wakeup-6.1.06-asset） | 南昌理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2082（wakeup-6.1.06-asset） | 南通职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2006（wakeup-6.1.06-asset） | 南阳医学高等专科学校 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2003（wakeup-6.1.06-asset） | 南阳工艺美术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1965（wakeup-6.1.06-asset） | 南阳师范学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 762（wakeup-52.36-user-attachment） | 南阳理工学院 | nyist | nyist | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1978（wakeup-6.1.06-asset） | 南通科技职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2005（wakeup-6.1.06-asset） | 南阳农业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1970（wakeup-6.1.06-asset） | 南通师范高等专科学校 | nuist | nuist | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1980（wakeup-6.1.06-asset） | 南阳职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2075（wakeup-6.1.06-asset） | 南昌航空大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2004（wakeup-6.1.06-asset） | 南阳科技职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2998（wakeup-6.1.06-asset） | 厦门华天涉外职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2937（wakeup-6.1.06-asset） | 厦门南洋职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1072（wakeup-52.36-user-attachment） | 厦门医学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3152（wakeup-6.1.06-asset） | 厦门华厦学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2997（wakeup-6.1.06-asset） | 厦门兴才职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3142（wakeup-6.1.06-asset） | 厦门城市职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3000（wakeup-6.1.06-asset） | 厦门大学嘉庚学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2939（wakeup-6.1.06-asset） | 厦门大学（教务） | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 2943（wakeup-6.1.06-asset） | 厦门海洋职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3026（wakeup-6.1.06-asset） | 厦门开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2994（wakeup-6.1.06-asset） | 厦门安防科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2995（wakeup-6.1.06-asset） | 厦门东海职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2996（wakeup-6.1.06-asset） | 厦门软件职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1141（wakeup-6.1.06-asset） | 合肥信息技术职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1075（wakeup-52.36-user-attachment） | 厦门工学院 | qz_br | qz_br | keep | declared routable type retained | 2026-10-17 |
| 2999（wakeup-6.1.06-asset） | 厦门演艺职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1397（wakeup-6.1.06-asset） | 合肥共达职业技术学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1339（wakeup-6.1.06-asset） | 合肥大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1337（wakeup-6.1.06-asset） | 合肥工业大学（可切换学期） | eams5 | eams5 | keep | URL anchor eams5 (jxglstu/for-std) | 2026-10-17 |
| 1335（wakeup-6.1.06-asset） | 合肥幼儿师范高等专科学校 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1399（wakeup-6.1.06-asset） | 合肥科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1209（wakeup-6.1.06-asset） | 合肥城市学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1396（wakeup-6.1.06-asset） | 合肥通用职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 564（wakeup-52.36-user-attachment） | 吉利学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1718（wakeup-6.1.06-asset） | 吉安幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1678（wakeup-6.1.06-asset） | 吉林交通职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1334（wakeup-6.1.06-asset） | 合肥经济学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1600（wakeup-6.1.06-asset） | 吉林体育学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1394（wakeup-6.1.06-asset） | 合肥理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1324（wakeup-52.36-user-attachment） | 吉林农业大学 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1496（wakeup-6.1.06-asset） | 吉安职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 568（wakeup-52.36-user-attachment） | 吉林医药学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 990（wakeup-6.1.06-asset） | 合肥职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1677（wakeup-6.1.06-asset） | 吉林司法警官职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 571（wakeup-52.36-user-attachment） | 吉林大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1673（wakeup-6.1.06-asset） | 吉林城市职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1676（wakeup-6.1.06-asset） | 吉林工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1675（wakeup-6.1.06-asset） | 吉林工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1679（wakeup-6.1.06-asset） | 吉林师范大学博达学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1680（wakeup-6.1.06-asset） | 吉林动画学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1509（wakeup-6.1.06-asset） | 吉林建筑科技学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1616（wakeup-6.1.06-asset） | 吉林化学工业公司职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1672（wakeup-6.1.06-asset） | 吉林水利电力职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1699（wakeup-6.1.06-asset） | 吉林电子信息职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1507（wakeup-6.1.06-asset） | 吉林建筑大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1615（wakeup-6.1.06-asset） | 吉林省教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1505（wakeup-6.1.06-asset） | 吉林工程技术师范学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1607（wakeup-6.1.06-asset） | 吉林省经济管理干部学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1526（wakeup-6.1.06-asset） | 吉林化工大学 | jlict_qz_old | jlict_qz_old | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1614（wakeup-6.1.06-asset） | 吉林开放大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1510（wakeup-6.1.06-asset） | 吉林艺术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1512（wakeup-6.1.06-asset） | 吉林财经大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1599（wakeup-6.1.06-asset） | 吉林职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1674（wakeup-6.1.06-asset） | 吉林科技职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1511（wakeup-6.1.06-asset） | 吉林警察学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1515（wakeup-6.1.06-asset） | 吉首大学张家界学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1671（wakeup-6.1.06-asset） | 吉林通用航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1527（wakeup-6.1.06-asset） | 吉林铁道职业技术大学 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1613（wakeup-6.1.06-asset） | 吉林职工医科大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2805（wakeup-6.1.06-asset） | 吐鲁番职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1801（wakeup-6.1.06-asset） | 吕梁学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1829（wakeup-6.1.06-asset） | 吕梁师范高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1828（wakeup-6.1.06-asset） | 吕梁职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3533（wakeup-6.1.06-asset） | 周口职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1333（wakeup-6.1.06-asset） | 呼伦贝尔学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3548（wakeup-6.1.06-asset） | 周口文理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1331（wakeup-6.1.06-asset） | 呼和浩特民族学院 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 980（wakeup-52.36-user-attachment） | 同济大学 | tju | tju | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1332（wakeup-6.1.06-asset） | 呼伦贝尔职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1482（wakeup-6.1.06-asset） | 呼和浩特职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2769（wakeup-6.1.06-asset） | 同济大学浙江学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1177（wakeup-6.1.06-asset） | 和君职业学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 1465（wakeup-6.1.06-asset） | 和田职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2971（wakeup-6.1.06-asset） | 咸阳师范学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1464（wakeup-6.1.06-asset） | 哈密职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3106（wakeup-6.1.06-asset） | 咸宁职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1379（wakeup-6.1.06-asset） | 哈尔滨信息工程学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1392（wakeup-6.1.06-asset） | 哈尔滨北方航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1791（wakeup-52.36-user-attachment） | 哈尔滨体育学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 2945（wakeup-6.1.06-asset） | 咸阳职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1738（wakeup-52.36-user-attachment） | 哈尔滨学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 394（wakeup-52.36-user-attachment） | 哈尔滨工业大学 | hit | hit | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 396（wakeup-52.36-user-attachment） | 哈尔滨工业大学（深圳） | hitsz | hitsz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 395（wakeup-52.36-user-attachment） | 哈尔滨工业大学（威海） | hit | hit | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1388（wakeup-6.1.06-asset） | 哈尔滨应用职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 401（wakeup-52.36-user-attachment） | 哈尔滨广厦学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1391（wakeup-6.1.06-asset） | 哈尔滨幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1472（wakeup-6.1.06-asset） | 哈尔滨开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3396（wakeup-6.1.06-asset） | 周口师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1390（wakeup-6.1.06-asset） | 哈尔滨科学技术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 403（wakeup-52.36-user-attachment） | 哈尔滨石油学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 1378（wakeup-6.1.06-asset） | 哈尔滨金融学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1384（wakeup-6.1.06-asset） | 哈尔滨铁道职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2722（wakeup-6.1.06-asset） | 唐山学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 402（wakeup-52.36-user-attachment） | 哈尔滨理工大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 2776（wakeup-6.1.06-asset） | 唐山工业职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2709（wakeup-6.1.06-asset） | 唐山海运职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2248（wakeup-52.36-user-attachment） | 哈尔滨职业技术大学 | hitsz | hitsz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2247（wakeup-52.36-user-attachment） | 哈尔滨职业技术学院 | hitsz | hitsz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2696（wakeup-6.1.06-asset） | 唐山幼儿师范高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2788（wakeup-6.1.06-asset） | 唐山科技职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2335（wakeup-6.1.06-asset） | 商丘医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1122（wakeup-6.1.06-asset） | 哈尔滨远东理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2629（wakeup-6.1.06-asset） | 商丘工学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2647（wakeup-6.1.06-asset） | 商丘师范学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2646（wakeup-6.1.06-asset） | 商丘学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2766（wakeup-6.1.06-asset） | 唐山职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1723（wakeup-6.1.06-asset） | 喀什理工职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2332（wakeup-6.1.06-asset） | 商丘幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1521（wakeup-6.1.06-asset） | 嘉应学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2462（wakeup-6.1.06-asset） | 商丘职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2300（wakeup-6.1.06-asset） | 商洛职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1725（wakeup-6.1.06-asset） | 喀什职业技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2676（wakeup-6.1.06-asset） | 四川交通职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2424（wakeup-6.1.06-asset） | 四川中医药高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2632（wakeup-6.1.06-asset） | 四川三河职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2526（wakeup-6.1.06-asset） | 四川信息职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1516（wakeup-6.1.06-asset） | 嘉兴南洋职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2220（wakeup-6.1.06-asset） | 四川化工职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2225（wakeup-6.1.06-asset） | 四川卫生康复职业学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 2317（wakeup-6.1.06-asset） | 四川商务职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2310（wakeup-6.1.06-asset） | 四川体育职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 838（wakeup-52.36-user-attachment） | 四川农业大学 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2528（wakeup-6.1.06-asset） | 四川传媒学院 | zf_new | zf_new | remap | conflict: shuwei_json via URL anchor wengine/shuwei | 2026-10-17 |
| 2316（wakeup-6.1.06-asset） | 四川司法警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 843（wakeup-52.36-user-attachment） | 四川外国语大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 849（wakeup-52.36-user-attachment） | 四川工商学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2235（wakeup-6.1.06-asset） | 四川工业科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 845（wakeup-52.36-user-attachment） | 四川外国语大学重庆南方翻译学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 842（wakeup-52.36-user-attachment） | 四川城市职业学院 | vatuu | vatuu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 852（wakeup-52.36-user-attachment） | 四川师范大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2240（wakeup-6.1.06-asset） | 四川幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2311（wakeup-6.1.06-asset） | 四川希望汽车职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2238（wakeup-6.1.06-asset） | 四川工程职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2241（wakeup-6.1.06-asset） | 四川托普信息技术职业学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 1456（wakeup-52.36-user-attachment） | 四川应用技术职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2441（wakeup-6.1.06-asset） | 四川建筑职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 856（wakeup-52.36-user-attachment） | 四川护理职业学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 2270（wakeup-6.1.06-asset） | 四川开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2439（wakeup-6.1.06-asset） | 四川文化产业职业学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2432（wakeup-6.1.06-asset） | 四川文理学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 858（wakeup-52.36-user-attachment） | 四川旅游学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2247（wakeup-6.1.06-asset） | 四川机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2594（wakeup-6.1.06-asset） | 四川工程职业技术大学 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2315（wakeup-6.1.06-asset） | 四川文化传媒职业学院 | zf | zf | keep | declared routable type retained | 2026-10-17 |
| 2433（wakeup-6.1.06-asset） | 四川文化艺术学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2312（wakeup-6.1.06-asset） | 四川汽车职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2319（wakeup-6.1.06-asset） | 四川电力职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2313（wakeup-6.1.06-asset） | 四川现代职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2595（wakeup-6.1.06-asset） | 四川文轩职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2435（wakeup-6.1.06-asset） | 四川科技职业学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2438（wakeup-6.1.06-asset） | 四川电子机械职业技术学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2251（wakeup-6.1.06-asset） | 四川美术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1455（wakeup-52.36-user-attachment） | 四川省会理现代职业技术学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2252（wakeup-6.1.06-asset） | 四川职业技术学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 2318（wakeup-6.1.06-asset） | 四川航天职业技术学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 860（wakeup-52.36-user-attachment） | 四川民族学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2437（wakeup-6.1.06-asset） | 四川西南航空职业学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 2254（wakeup-6.1.06-asset） | 四川财经职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2253（wakeup-6.1.06-asset） | 四川警察学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2425（wakeup-6.1.06-asset） | 四川水利职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2256（wakeup-6.1.06-asset） | 四川邮电职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2257（wakeup-6.1.06-asset） | 四川长江职业学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx; page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 2314（wakeup-6.1.06-asset） | 四川铁道职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2288（wakeup-6.1.06-asset） | 四平农村成人高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 883（wakeup-6.1.06-asset） | 国家检察官学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2391（wakeup-6.1.06-asset） | 四平职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 886（wakeup-6.1.06-asset） | 国家开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1678（wakeup-52.36-user-attachment） | 四川音乐学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 884（wakeup-6.1.06-asset） | 国家法官学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2119（wakeup-6.1.06-asset） | 培黎职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2808（wakeup-6.1.06-asset） | 图木舒克职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2427（wakeup-6.1.06-asset） | 四川艺术职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2804（wakeup-6.1.06-asset） | 塔里木理工学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2806（wakeup-6.1.06-asset） | 塔里木职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 568（wakeup-6.1.06-asset） | 大同师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 578（wakeup-6.1.06-asset） | 大兴安岭职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 569（wakeup-6.1.06-asset） | 大同数据科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2902（wakeup-6.1.06-asset） | 外交学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 237（wakeup-52.36-user-attachment） | 复旦大学 | shuwei_m | shuwei_m | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 238（wakeup-52.36-user-attachment） | 复旦大学个人数据中心 | fdu | fdu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 527（wakeup-6.1.06-asset） | 大庆师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 528（wakeup-6.1.06-asset） | 大理大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 590（wakeup-6.1.06-asset） | 大理护理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 580（wakeup-6.1.06-asset） | 大湾区大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 589（wakeup-6.1.06-asset） | 大理农林职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 215（wakeup-52.36-user-attachment） | 大连交通大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 570（wakeup-6.1.06-asset） | 大连医科大学中山学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 571（wakeup-6.1.06-asset） | 大连东软信息学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 534（wakeup-6.1.06-asset） | 大连工业大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 577（wakeup-6.1.06-asset） | 大庆职业学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 573（wakeup-6.1.06-asset） | 大连枫叶职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 576（wakeup-6.1.06-asset） | 大连汽车职业技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 219（wakeup-52.36-user-attachment） | 大连民族大学 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 537（wakeup-6.1.06-asset） | 大连海事大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 539（wakeup-6.1.06-asset） | 大连海洋大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 223（wakeup-52.36-user-attachment） | 大连理工大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 542（wakeup-6.1.06-asset） | 大连科技学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 563（wakeup-6.1.06-asset） | 大连职业技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1528（wakeup-52.36-user-attachment） | 大连工程学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 592（wakeup-6.1.06-asset） | 大连职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 572（wakeup-6.1.06-asset） | 大连软件职业学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2798（wakeup-6.1.06-asset） | 天府新区信息职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2811（wakeup-6.1.06-asset） | 天府新区通用航空职业学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 543（wakeup-6.1.06-asset） | 大连艺术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2712（wakeup-6.1.06-asset） | 天府新区航空旅游职业学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 987（wakeup-52.36-user-attachment） | 天水师范学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2786（wakeup-6.1.06-asset） | 天津体育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2760（wakeup-6.1.06-asset） | 天津体育学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2759（wakeup-6.1.06-asset） | 天津农学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2755（wakeup-6.1.06-asset） | 天津商业大学宝德学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 575（wakeup-6.1.06-asset） | 大连装备制造职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2782（wakeup-6.1.06-asset） | 天津国土资源和房屋职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2697（wakeup-6.1.06-asset） | 天津商务职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 574（wakeup-6.1.06-asset） | 大连航运职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2725（wakeup-6.1.06-asset） | 天津外国语大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2762（wakeup-6.1.06-asset） | 天津中德应用技术大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 993（wakeup-52.36-user-attachment） | 天津商业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2754（wakeup-6.1.06-asset） | 天津城市职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2710（wakeup-6.1.06-asset） | 天津天狮学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2751（wakeup-6.1.06-asset） | 天津大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2783（wakeup-6.1.06-asset） | 天津工业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1000（wakeup-52.36-user-attachment） | 天津工业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2748（wakeup-6.1.06-asset） | 天津市职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2785（wakeup-6.1.06-asset） | 天津工艺美术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2718（wakeup-6.1.06-asset） | 天津外国语大学滨海外事学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2809（wakeup-6.1.06-asset） | 天津开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 997（wakeup-52.36-user-attachment） | 天津城建大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2715（wakeup-6.1.06-asset） | 天津海运职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2745（wakeup-6.1.06-asset） | 天津滨海职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2779（wakeup-6.1.06-asset） | 天津渤海职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2780（wakeup-6.1.06-asset） | 天津现代职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1002（wakeup-52.36-user-attachment） | 天津师范大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2723（wakeup-6.1.06-asset） | 天津电子信息职业技术学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 2777（wakeup-6.1.06-asset） | 天津师范大学津沽学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2716（wakeup-6.1.06-asset） | 天津理工大学中环信息学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2787（wakeup-6.1.06-asset） | 天津滨海汽车工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2778（wakeup-6.1.06-asset） | 天津警察学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2741（wakeup-6.1.06-asset） | 天津职业技术师范大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2743（wakeup-6.1.06-asset） | 天津科技大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2744（wakeup-6.1.06-asset） | 天津理工大学 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 2781（wakeup-6.1.06-asset） | 天津轻工职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2739（wakeup-6.1.06-asset） | 天津财经大学珠江学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2784（wakeup-6.1.06-asset） | 天津铁道职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2789（wakeup-6.1.06-asset） | 太原城市职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2699（wakeup-6.1.06-asset） | 太原学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2797（wakeup-6.1.06-asset） | 天门职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2738（wakeup-6.1.06-asset） | 太原师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2205（wakeup-52.36-user-attachment） | 天津财经大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 2774（wakeup-6.1.06-asset） | 天津美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2794（wakeup-6.1.06-asset） | 太湖创意职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2735（wakeup-6.1.06-asset） | 太原科技大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2773（wakeup-6.1.06-asset） | 天津音乐学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 2850（wakeup-6.1.06-asset） | 威海职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2849（wakeup-6.1.06-asset） | 威海海洋职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1012（wakeup-52.36-user-attachment） | 太原理工大学 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 1858（wakeup-6.1.06-asset） | 娄底职业技术学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 764（wakeup-52.36-user-attachment） | 宁夏大学 | ecupl | ecupl | keep | declared routable type retained | 2026-10-17 |
| 2086（wakeup-6.1.06-asset） | 宁夏医科大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1992（wakeup-6.1.06-asset） | 宁夏工业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1987（wakeup-6.1.06-asset） | 宁夏卫生健康职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2810（wakeup-6.1.06-asset） | 太原钢铁(集团)有限公司职工钢铁学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1983（wakeup-6.1.06-asset） | 宁夏开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1989（wakeup-6.1.06-asset） | 宁夏幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2089（wakeup-6.1.06-asset） | 宁夏师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1994（wakeup-6.1.06-asset） | 宁夏职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1988（wakeup-6.1.06-asset） | 宁夏艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2088（wakeup-6.1.06-asset） | 宁夏大学新华学院 | ecupl | ecupl | keep | declared routable type retained | 2026-10-17 |
| 767（wakeup-52.36-user-attachment） | 宁夏理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1859（wakeup-6.1.06-asset） | 娄底幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1990（wakeup-6.1.06-asset） | 宁夏葡萄酒与防沙治沙职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2009（wakeup-6.1.06-asset） | 宁德职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1991（wakeup-6.1.06-asset） | 宁夏财经职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1993（wakeup-6.1.06-asset） | 宁夏民族职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 769（wakeup-52.36-user-attachment） | 宁波大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2011（wakeup-6.1.06-asset） | 宁波东方理工大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1960（wakeup-6.1.06-asset） | 宁波卫生职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2010（wakeup-6.1.06-asset） | 宁德师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2099（wakeup-6.1.06-asset） | 宁波大学科学技术学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1985（wakeup-6.1.06-asset） | 宁波开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2013（wakeup-6.1.06-asset） | 宁波诺丁汉大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2091（wakeup-6.1.06-asset） | 宁波城市职业技术学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1483（wakeup-52.36-user-attachment） | 安庆医药高等专科学校 | login_xbellsoft | login_xbellsoft | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2093（wakeup-6.1.06-asset） | 宁波职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 77（wakeup-6.1.06-asset） | 安庆职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2098（wakeup-6.1.06-asset） | 宁波财经学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 107（wakeup-6.1.06-asset） | 安康职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 86（wakeup-6.1.06-asset） | 安徽三联学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 22（wakeup-52.36-user-attachment） | 安庆师范大学（可切换学期） | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 82（wakeup-6.1.06-asset） | 安庆师范大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 94（wakeup-6.1.06-asset） | 安徽体育运动职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 93（wakeup-6.1.06-asset） | 安徽交通职业技术学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 25（wakeup-52.36-user-attachment） | 安徽中澳科技职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1798（wakeup-52.36-user-attachment） | 安徽冶金科技职业学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 96（wakeup-6.1.06-asset） | 安徽公安职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 68（wakeup-6.1.06-asset） | 安徽卫生健康职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 31（wakeup-52.36-user-attachment） | 安徽医科大学临床医学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 29（wakeup-52.36-user-attachment） | 安徽医科大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 29（wakeup-6.1.06-asset） | 安徽医学高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1484（wakeup-52.36-user-attachment） | 安徽中医药高等专科学校 | login_xbellsoft | login_xbellsoft | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 79（wakeup-6.1.06-asset） | 安徽城市管理职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 67（wakeup-6.1.06-asset） | 安徽外国语学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1482（wakeup-52.36-user-attachment） | 安徽商贸职业技术学院 | login_xbellsoft | login_xbellsoft | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 97（wakeup-6.1.06-asset） | 安徽审计职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 38（wakeup-6.1.06-asset） | 安徽工商职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 95（wakeup-6.1.06-asset） | 安徽国防科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 90（wakeup-6.1.06-asset） | 安徽工业经济职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 37（wakeup-52.36-user-attachment） | 安徽师范大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 42（wakeup-6.1.06-asset） | 安徽广播影视职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 91（wakeup-6.1.06-asset） | 安徽工贸职业技术学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 38（wakeup-52.36-user-attachment） | 安徽师范大学（可切换学期） | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 102（wakeup-6.1.06-asset） | 安徽扬子职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 98（wakeup-6.1.06-asset） | 安徽工业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 111（wakeup-6.1.06-asset） | 安徽开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1480（wakeup-52.36-user-attachment） | 安徽新闻出版职业技术学院 | login_xbellsoft | login_xbellsoft | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 78（wakeup-6.1.06-asset） | 安徽文达信息工程学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 39（wakeup-6.1.06-asset） | 安徽工程大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 45（wakeup-6.1.06-asset） | 安徽机电职业技术学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 59（wakeup-6.1.06-asset） | 安徽林业职业技术学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 88（wakeup-6.1.06-asset） | 安徽水利水电职业技术学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 99（wakeup-6.1.06-asset） | 安徽涉外经济职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 46（wakeup-6.1.06-asset） | 安徽理工大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 92（wakeup-6.1.06-asset） | 安徽电子信息职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 69（wakeup-6.1.06-asset） | 安徽建筑大学城市建设学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 44（wakeup-6.1.06-asset） | 安徽新华学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 45（wakeup-52.36-user-attachment） | 安徽科技工程大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 100（wakeup-6.1.06-asset） | 安徽绿海商务职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 83（wakeup-6.1.06-asset） | 安徽职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 89（wakeup-6.1.06-asset） | 安徽警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 48（wakeup-6.1.06-asset） | 安徽电气工程职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 76（wakeup-6.1.06-asset） | 安徽艺术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 51（wakeup-6.1.06-asset） | 安徽财经大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 61（wakeup-6.1.06-asset） | 安阳学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 103（wakeup-6.1.06-asset） | 安徽黄梅戏艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 60（wakeup-6.1.06-asset） | 安阳师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 55（wakeup-6.1.06-asset） | 安阳幼儿师范高等专科学校 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 104（wakeup-6.1.06-asset） | 安徽粮食工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 72（wakeup-6.1.06-asset） | 安顺学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3265（wakeup-6.1.06-asset） | 宜宾医药健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 105（wakeup-6.1.06-asset） | 安阳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3266（wakeup-6.1.06-asset） | 宜宾工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 101（wakeup-6.1.06-asset） | 安徽汽车职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1295（wakeup-52.36-user-attachment） | 宜宾职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3274（wakeup-6.1.06-asset） | 宜昌科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 106（wakeup-6.1.06-asset） | 安顺职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 591（wakeup-6.1.06-asset） | 定西职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3187（wakeup-6.1.06-asset） | 宜春学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 204（wakeup-6.1.06-asset） | 宝鸡职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3282（wakeup-6.1.06-asset） | 宜春幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3283（wakeup-6.1.06-asset） | 宜春职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1164（wakeup-52.36-user-attachment） | 宜宾学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3126（wakeup-6.1.06-asset） | 宣化科技职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3001（wakeup-6.1.06-asset） | 宣城职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2352（wakeup-6.1.06-asset） | 宿州航空职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2258（wakeup-6.1.06-asset） | 宿州学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2355（wakeup-6.1.06-asset） | 宿迁幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2343（wakeup-6.1.06-asset） | 山东中医药高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2513（wakeup-6.1.06-asset） | 山东交通学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2356（wakeup-6.1.06-asset） | 宿迁泽达职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2467（wakeup-6.1.06-asset） | 山东交通职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2502（wakeup-6.1.06-asset） | 山东传媒职业学院 | zf_new | zf_new | remap | conflict: qz via page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2422（wakeup-6.1.06-asset） | 山东信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2496（wakeup-6.1.06-asset） | 山东体育学院 | sdpei | sdpei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 567（wakeup-6.1.06-asset） | 对外经济贸易大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 872（wakeup-52.36-user-attachment） | 山东中医药大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2469（wakeup-6.1.06-asset） | 山东医学高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2346（wakeup-6.1.06-asset） | 山东农业工程学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2515（wakeup-6.1.06-asset） | 山东劳动职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2260（wakeup-6.1.06-asset） | 宿迁学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2263（wakeup-6.1.06-asset） | 山东司法警官职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2501（wakeup-6.1.06-asset） | 山东商务职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2505（wakeup-6.1.06-asset） | 山东城市服务职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 94（wakeup-52.36-user-attachment） | 山东医药大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2518（wakeup-6.1.06-asset） | 山东商业职业技术学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 881（wakeup-52.36-user-attachment） | 山东大学威海校区 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2521（wakeup-6.1.06-asset） | 山东大学 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 879（wakeup-52.36-user-attachment） | 山东城市建设职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2466（wakeup-6.1.06-asset） | 山东工程职业技术大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2465（wakeup-6.1.06-asset） | 山东工业职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 882（wakeup-52.36-user-attachment） | 山东女子学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2527（wakeup-6.1.06-asset） | 山东工艺美术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2460（wakeup-6.1.06-asset） | 山东文化产业职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2336（wakeup-6.1.06-asset） | 山东文化艺术职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2533（wakeup-6.1.06-asset） | 山东政法学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2503（wakeup-6.1.06-asset） | 山东服装职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2342（wakeup-6.1.06-asset） | 山东旅游职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2341（wakeup-6.1.06-asset） | 山东杏林科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2534（wakeup-6.1.06-asset） | 山东水利职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2339（wakeup-6.1.06-asset） | 山东海事职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2337（wakeup-6.1.06-asset） | 山东特殊教育职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2463（wakeup-6.1.06-asset） | 山东现代学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 890（wakeup-52.36-user-attachment） | 山东理工大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 893（wakeup-52.36-user-attachment） | 山东畜牧兽医职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2429（wakeup-6.1.06-asset） | 山东理工职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 885（wakeup-52.36-user-attachment） | 山东师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2275（wakeup-6.1.06-asset） | 山东开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2279（wakeup-6.1.06-asset） | 山东省水利职工大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2344（wakeup-6.1.06-asset） | 山东化工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2276（wakeup-6.1.06-asset） | 山东省济宁教育学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2278（wakeup-6.1.06-asset） | 山东省经济管理干部学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2277（wakeup-6.1.06-asset） | 山东省聊城教育学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 894（wakeup-52.36-user-attachment） | 山东石油化工学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 2348（wakeup-6.1.06-asset） | 山东第二医科大学（原潍坊医学院） | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2468（wakeup-6.1.06-asset） | 山东经贸职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2345（wakeup-6.1.06-asset） | 山东胜利职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2546（wakeup-6.1.06-asset） | 山东职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 898（wakeup-52.36-user-attachment） | 山东第一医科大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 896（wakeup-52.36-user-attachment） | 山东科技大学泰山科技学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2508（wakeup-6.1.06-asset） | 山东航空学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2347（wakeup-6.1.06-asset） | 山东航空学院（原滨州学院） | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2338（wakeup-6.1.06-asset） | 山东艺术设计职业学院 | zf_new | zf_new | remap | conflict: qz via page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2548（wakeup-6.1.06-asset） | 山东艺术学院 | zf_new | zf_new | remap | conflict: qz via page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2430（wakeup-6.1.06-asset） | 山东警察学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2549（wakeup-6.1.06-asset） | 山东英才学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 907（wakeup-52.36-user-attachment） | 山东轻工职业学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2411（wakeup-6.1.06-asset） | 山西中医药大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2274（wakeup-6.1.06-asset） | 山东财政职工大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 904（wakeup-52.36-user-attachment） | 山东财经大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2292（wakeup-6.1.06-asset） | 山西兵器工业职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2402（wakeup-6.1.06-asset） | 山西体育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2409（wakeup-6.1.06-asset） | 山西医药学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2399（wakeup-6.1.06-asset） | 山西华澳商贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2340（wakeup-6.1.06-asset） | 山东药品食品职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2394（wakeup-6.1.06-asset） | 山西卫生健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1310（wakeup-52.36-user-attachment） | 山西传媒学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2543（wakeup-6.1.06-asset） | 山东科技职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 911（wakeup-52.36-user-attachment） | 山西医科大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 2224（wakeup-6.1.06-asset） | 山西工程科技职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 913（wakeup-52.36-user-attachment） | 山西大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2476（wakeup-6.1.06-asset） | 山西师范大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2266（wakeup-6.1.06-asset） | 山西应用科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2290（wakeup-6.1.06-asset） | 山西开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2560（wakeup-6.1.06-asset） | 山西工商学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2461（wakeup-6.1.06-asset） | 山西工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2294（wakeup-6.1.06-asset） | 山西机电职工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2396（wakeup-6.1.06-asset） | 山西工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2404（wakeup-6.1.06-asset） | 山西水利职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 915（wakeup-52.36-user-attachment） | 山西晋中理工学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 2489（wakeup-6.1.06-asset） | 山西林业职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2406（wakeup-6.1.06-asset） | 山西机电职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2293（wakeup-6.1.06-asset） | 山西煤炭职工联合大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1688（wakeup-52.36-user-attachment） | 山西科技学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2400（wakeup-6.1.06-asset） | 山西管理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2218（wakeup-6.1.06-asset） | 山西经贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2291（wakeup-6.1.06-asset） | 山西省职工工艺美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2410（wakeup-6.1.06-asset） | 山西电子科技学院（原山西师范大学现代文理学院） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2401（wakeup-6.1.06-asset） | 山西警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2408（wakeup-6.1.06-asset） | 山西省财政税务专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2407（wakeup-6.1.06-asset） | 山西药科职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2405（wakeup-6.1.06-asset） | 山西财贸职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2403（wakeup-6.1.06-asset） | 山西金融职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2565（wakeup-6.1.06-asset） | 山西财经大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 912（wakeup-52.36-user-attachment） | 山西大同大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2397（wakeup-6.1.06-asset） | 山西铁道职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3273（wakeup-6.1.06-asset） | 岳阳学院（原湖南理工学院南湖学院） | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3269（wakeup-6.1.06-asset） | 岳阳现代服务职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3227（wakeup-6.1.06-asset） | 岳阳职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2426（wakeup-6.1.06-asset） | 山西职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2564（wakeup-6.1.06-asset） | 山西警察学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2334（wakeup-6.1.06-asset） | 嵩山少林武术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1772（wakeup-6.1.06-asset） | 岭南师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 470（wakeup-6.1.06-asset） | 崇左幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 266（wakeup-6.1.06-asset） | 川南幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 268（wakeup-6.1.06-asset） | 常州信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 190（wakeup-6.1.06-asset） | 巴中职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 107（wakeup-52.36-user-attachment） | 常州大学怀德学院 | changzhou | changzhou | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 417（wakeup-6.1.06-asset） | 常州工业职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 493（wakeup-6.1.06-asset） | 川北幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 451（wakeup-6.1.06-asset） | 常州幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 110（wakeup-52.36-user-attachment） | 常州机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 103（wakeup-52.36-user-attachment） | 巢湖学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 452（wakeup-6.1.06-asset） | 常州铁道职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 105（wakeup-52.36-user-attachment） | 常州大学 | changzhou | changzhou | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 459（wakeup-6.1.06-asset） | 常德学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2118（wakeup-6.1.06-asset） | 平凉职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 465（wakeup-6.1.06-asset） | 常德科技职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 275（wakeup-6.1.06-asset） | 常州纺织服装职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2120（wakeup-6.1.06-asset） | 平顶山工业职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2113（wakeup-6.1.06-asset） | 平顶山文化艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2112（wakeup-6.1.06-asset） | 平顶山学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 260（wakeup-6.1.06-asset） | 巴音郭楞职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2116（wakeup-6.1.06-asset） | 平顶山科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 252（wakeup-52.36-user-attachment） | 广东交通职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2115（wakeup-6.1.06-asset） | 平顶山职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 956（wakeup-6.1.06-asset） | 广东体育职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 948（wakeup-6.1.06-asset） | 广东信息工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 272（wakeup-6.1.06-asset） | 常州工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 959（wakeup-6.1.06-asset） | 广东以色列理工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 253（wakeup-52.36-user-attachment） | 广东农工商职业技术学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 254（wakeup-52.36-user-attachment） | 广东农工商职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 950（wakeup-6.1.06-asset） | 广东南方职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 941（wakeup-6.1.06-asset） | 广东云浮中医药职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 974（wakeup-6.1.06-asset） | 广东外语外贸大学南国商学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 980（wakeup-6.1.06-asset） | 广东创新科技职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 960（wakeup-6.1.06-asset） | 广东工商职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 256（wakeup-52.36-user-attachment） | 广东南华工商职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 972（wakeup-6.1.06-asset） | 广东女子职业技术学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 266（wakeup-52.36-user-attachment） | 广东建设职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 879（wakeup-6.1.06-asset） | 广东开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1334（wakeup-52.36-user-attachment） | 广东技术师范学院天河学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 268（wakeup-52.36-user-attachment） | 广东技术师范大学天河学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 856（wakeup-6.1.06-asset） | 广东岭南职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 955（wakeup-6.1.06-asset） | 广东亚视演艺职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 878（wakeup-6.1.06-asset） | 广东新华教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 267（wakeup-52.36-user-attachment） | 广东技术师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 772（wakeup-6.1.06-asset） | 广东文艺职业学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 963（wakeup-6.1.06-asset） | 广东机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 942（wakeup-6.1.06-asset） | 广东梅州职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 834（wakeup-6.1.06-asset） | 广东松山职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 951（wakeup-6.1.06-asset） | 广东文理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 943（wakeup-6.1.06-asset） | 广东汕头幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 962（wakeup-6.1.06-asset） | 广东水利电力职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 779（wakeup-6.1.06-asset） | 广东江门中医药职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 855（wakeup-6.1.06-asset） | 广东新安职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 945（wakeup-6.1.06-asset） | 广东江门幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 664（wakeup-6.1.06-asset） | 广东理工学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 816（wakeup-6.1.06-asset） | 广东潮州卫生健康职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 666（wakeup-6.1.06-asset） | 广东生态工程职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 271（wakeup-52.36-user-attachment） | 广东环境保护工程职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 881（wakeup-6.1.06-asset） | 广东省国防工业职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 276（wakeup-52.36-user-attachment） | 广东白云学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 880（wakeup-6.1.06-asset） | 广东省公安司法管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 877（wakeup-6.1.06-asset） | 广东省职工体育运动技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 838（wakeup-6.1.06-asset） | 广东省外语艺术职业学院 | gdei | gdei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 876（wakeup-6.1.06-asset） | 广东社会科学大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 280（wakeup-52.36-user-attachment） | 广东科贸职业学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 670（wakeup-6.1.06-asset） | 广东科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 274（wakeup-52.36-user-attachment） | 广东理工职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 940（wakeup-6.1.06-asset） | 广东肇庆航空职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2023（wakeup-52.36-user-attachment） | 广东第二师范学院 | gdei | gdei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 277（wakeup-52.36-user-attachment） | 广东石油化工学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 868（wakeup-6.1.06-asset） | 广东艺术职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 947（wakeup-6.1.06-asset） | 广东茂名幼儿师范专科学校 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 281（wakeup-52.36-user-attachment） | 广东职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 675（wakeup-6.1.06-asset） | 广东警官学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 825（wakeup-6.1.06-asset） | 广东行政职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 771（wakeup-6.1.06-asset） | 广东财贸职业学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 988（wakeup-6.1.06-asset） | 广东软件学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 867（wakeup-6.1.06-asset） | 广东轻工职业技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 286（wakeup-52.36-user-attachment） | 广东轻工职业技术学校 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 799（wakeup-6.1.06-asset） | 广东酒店管理职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 680（wakeup-6.1.06-asset） | 广东邮电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 946（wakeup-6.1.06-asset） | 广东茂名农林科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 283（wakeup-52.36-user-attachment） | 广东药科大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 684（wakeup-6.1.06-asset） | 广安职业技术学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 682（wakeup-6.1.06-asset） | 广东青年职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 290（wakeup-52.36-user-attachment） | 广东食品药品职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 798（wakeup-6.1.06-asset） | 广元中核职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 949（wakeup-6.1.06-asset） | 广州东华职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 954（wakeup-6.1.06-asset） | 广州体育职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 294（wakeup-52.36-user-attachment） | 广州华商学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 853（wakeup-6.1.06-asset） | 广州华南商贸职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 849（wakeup-6.1.06-asset） | 广州南洋理工职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 690（wakeup-6.1.06-asset） | 广州华立学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 295（wakeup-52.36-user-attachment） | 广州华夏职业学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 827（wakeup-6.1.06-asset） | 广州卫生职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 693（wakeup-6.1.06-asset） | 广州城市职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 821（wakeup-6.1.06-asset） | 广州华立科技职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1379（wakeup-52.36-user-attachment） | 广州城建职业学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 813（wakeup-6.1.06-asset） | 广州华商职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 692（wakeup-6.1.06-asset） | 广州城市理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 697（wakeup-6.1.06-asset） | 广州工商学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 953（wakeup-6.1.06-asset） | 广州工程技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 944（wakeup-6.1.06-asset） | 广州幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 875（wakeup-6.1.06-asset） | 广州开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 952（wakeup-6.1.06-asset） | 广州现代信息工程职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 300（wakeup-52.36-user-attachment） | 广州大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 308（wakeup-52.36-user-attachment） | 广州理工学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 701（wakeup-6.1.06-asset） | 广州珠江职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 699（wakeup-6.1.06-asset） | 广州新华学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 957（wakeup-6.1.06-asset） | 广州民航职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1797（wakeup-52.36-user-attachment） | 广州大学华软软件学院 | seig | seig | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 809（wakeup-6.1.06-asset） | 广州科技贸易职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 847（wakeup-6.1.06-asset） | 广州科技职业技术大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 850（wakeup-6.1.06-asset） | 广州美术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 312（wakeup-52.36-user-attachment） | 广州铁路职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1796（wakeup-52.36-user-attachment） | 广州软件学院 | seig | seig | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 309（wakeup-52.36-user-attachment） | 广州职业技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 313（wakeup-52.36-user-attachment） | 广西中医药大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 840（wakeup-6.1.06-asset） | 广西体育高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 882（wakeup-6.1.06-asset） | 广州金桥管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 938（wakeup-6.1.06-asset） | 广西中医药大学赛恩斯新医药学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 833（wakeup-6.1.06-asset） | 广西农业工程职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 839（wakeup-6.1.06-asset） | 广西信息职业技术学院 | gdei | gdei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 929（wakeup-6.1.06-asset） | 广西卫生职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 316（wakeup-52.36-user-attachment） | 广西医科大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 836（wakeup-6.1.06-asset） | 广西交通职业技术学院 | changzhou | changzhou | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 869（wakeup-6.1.06-asset） | 广西制造工程职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 846（wakeup-6.1.06-asset） | 广西安全工程职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2245（wakeup-52.36-user-attachment） | 广西工业职业技术学院 | gxic | gxic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 662（wakeup-6.1.06-asset） | 广西国际商务职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 704（wakeup-6.1.06-asset） | 广州航海学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 319（wakeup-52.36-user-attachment） | 广西工商职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1699（wakeup-52.36-user-attachment） | 广西师范大学漓江学院 | gxnu | gxnu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 318（wakeup-52.36-user-attachment） | 广西大学行健文理学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1698（wakeup-52.36-user-attachment） | 广西师范大学 | gxnu | gxnu | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 717（wakeup-6.1.06-asset） | 广西幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 933（wakeup-6.1.06-asset） | 广西工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 928（wakeup-6.1.06-asset） | 广西培贤国际职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 320（wakeup-52.36-user-attachment） | 广西师范学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 873（wakeup-6.1.06-asset） | 广西开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 872（wakeup-6.1.06-asset） | 广西政法管理干部学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 822（wakeup-6.1.06-asset） | 广西教育学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 924（wakeup-6.1.06-asset） | 广西智能制造职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 718（wakeup-6.1.06-asset） | 广西机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 935（wakeup-6.1.06-asset） | 广西水利电力职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 932（wakeup-6.1.06-asset） | 广西理工职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 812（wakeup-6.1.06-asset） | 广西演艺职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 777（wakeup-6.1.06-asset） | 广西建设职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 796（wakeup-6.1.06-asset） | 广西现代职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 793（wakeup-6.1.06-asset） | 广西生态工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 818（wakeup-6.1.06-asset） | 广西电力职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 727（wakeup-6.1.06-asset） | 广西职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 720（wakeup-6.1.06-asset） | 广西民族大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 930（wakeup-6.1.06-asset） | 广西科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 934（wakeup-6.1.06-asset） | 广西英华国际职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 728（wakeup-6.1.06-asset） | 广西艺术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 931（wakeup-6.1.06-asset） | 广西经济职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 814（wakeup-6.1.06-asset） | 广西警察学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 328（wakeup-52.36-user-attachment） | 广西科技大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 334（wakeup-52.36-user-attachment） | 广西财经学院 | kingo_new | kingo_new | remap | conflict: zf_new via URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 927（wakeup-6.1.06-asset） | 广西蓝天航空职业学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 926（wakeup-6.1.06-asset） | 广西质量工程职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 795（wakeup-6.1.06-asset） | 广西自然资源职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 730（wakeup-6.1.06-asset） | 广西金融职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1773（wakeup-6.1.06-asset） | 廊坊师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3290（wakeup-6.1.06-asset） | 应天职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2146（wakeup-6.1.06-asset） | 庆阳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3231（wakeup-6.1.06-asset） | 延安大学医学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3243（wakeup-6.1.06-asset） | 延安大学西安创新学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1826（wakeup-6.1.06-asset） | 廊坊燕京职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1810（wakeup-6.1.06-asset） | 廊坊卫生职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1666（wakeup-6.1.06-asset） | 建东职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1730（wakeup-6.1.06-asset） | 开封工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1721（wakeup-6.1.06-asset） | 康复大学 | hitsz | hitsz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1729（wakeup-6.1.06-asset） | 开封智慧健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1722（wakeup-6.1.06-asset） | 开封空分设备厂职工大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3242（wakeup-6.1.06-asset） | 延安职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1732（wakeup-6.1.06-asset） | 开封大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1731（wakeup-6.1.06-asset） | 开封文化艺术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3556（wakeup-6.1.06-asset） | 张家界学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3452（wakeup-6.1.06-asset） | 张家口学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2947（wakeup-6.1.06-asset） | 徐州工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3293（wakeup-6.1.06-asset） | 延边职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3149（wakeup-6.1.06-asset） | 徐州生物工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3557（wakeup-6.1.06-asset） | 张家界航空工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1752（wakeup-6.1.06-asset） | 开封职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1691（wakeup-52.36-user-attachment） | 张家口职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 588（wakeup-6.1.06-asset） | 德宏职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 546（wakeup-6.1.06-asset） | 德州学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 579（wakeup-6.1.06-asset） | 德州工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 582（wakeup-6.1.06-asset） | 德阳城市轨道交通职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 585（wakeup-6.1.06-asset） | 德阳农业科技职业学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx; page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 583（wakeup-6.1.06-asset） | 德阳科贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1398（wakeup-6.1.06-asset） | 徽商职业学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 1080（wakeup-52.36-user-attachment） | 徐州工程学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 587（wakeup-6.1.06-asset） | 德宏师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1454（wakeup-6.1.06-asset） | 怀化师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1456（wakeup-6.1.06-asset） | 怀化工商职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 545（wakeup-6.1.06-asset） | 德宏师范高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1995（wakeup-6.1.06-asset） | 怒江职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1779（wakeup-52.36-user-attachment） | 恩施职业技术学院 | hbmzu | hbmzu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1316（wakeup-6.1.06-asset） | 惠州城市职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 489（wakeup-6.1.06-asset） | 成都东软学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2950（wakeup-6.1.06-asset） | 忻州师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 402（wakeup-6.1.06-asset） | 成都体育学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 115（wakeup-52.36-user-attachment） | 成都中医药大学 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 280（wakeup-6.1.06-asset） | 成都信息工程大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1457（wakeup-6.1.06-asset） | 惠州工程职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 282（wakeup-6.1.06-asset） | 成都医学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 121（wakeup-52.36-user-attachment） | 成都工业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 286（wakeup-6.1.06-asset） | 成都工业职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1317（wakeup-6.1.06-asset） | 怀化学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 287（wakeup-6.1.06-asset） | 成都工贸职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 505（wakeup-6.1.06-asset） | 成都工业职工大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 490（wakeup-6.1.06-asset） | 成都外国语学院（原四川外国语大学成都学院） | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 265（wakeup-6.1.06-asset） | 成都农业科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 506（wakeup-6.1.06-asset） | 成都市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 124（wakeup-52.36-user-attachment） | 成都师范学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 1445（wakeup-6.1.06-asset） | 怀化职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1454（wakeup-52.36-user-attachment） | 成都文理学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 507（wakeup-6.1.06-asset） | 成都开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 292（wakeup-6.1.06-asset） | 成都职业技术学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 125（wakeup-52.36-user-attachment） | 成都理工大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 491（wakeup-6.1.06-asset） | 成都航空职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 293（wakeup-6.1.06-asset） | 成都航空职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 494（wakeup-6.1.06-asset） | 成都轨道交通职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 126（wakeup-52.36-user-attachment） | 成都纺织高等专科学校 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 294（wakeup-6.1.06-asset） | 成都艺术职业大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3165（wakeup-6.1.06-asset） | 扬州大学广陵学院 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 295（wakeup-6.1.06-asset） | 成都银杏酒店管理学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3287（wakeup-6.1.06-asset） | 扬州中瑞酒店职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2232（wakeup-6.1.06-asset） | 成都锦城学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3164（wakeup-6.1.06-asset） | 扬州大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 3219（wakeup-6.1.06-asset） | 扬州工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3289（wakeup-6.1.06-asset） | 扬州环境资源职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3239（wakeup-6.1.06-asset） | 扬州教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 434（wakeup-6.1.06-asset） | 承德应用技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3292（wakeup-6.1.06-asset） | 扬州职业技术大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 658（wakeup-6.1.06-asset） | 抚州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 424（wakeup-6.1.06-asset） | 承德护理职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 628（wakeup-6.1.06-asset） | 抚州幼儿师范高等专科学校 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 624（wakeup-6.1.06-asset） | 抚顺师范高等专科学校 | xhtd | xhtd | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 657（wakeup-6.1.06-asset） | 抚州医药学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1619（wakeup-6.1.06-asset） | 揭阳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 776（wakeup-52.36-user-attachment） | 攀枝花学院 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 2117（wakeup-6.1.06-asset） | 攀枝花攀西职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1025（wakeup-52.36-user-attachment） | 文山学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1082（wakeup-52.36-user-attachment） | 新乡医学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2841（wakeup-6.1.06-asset） | 文山职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 297（wakeup-6.1.06-asset） | 承德医学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2992（wakeup-6.1.06-asset） | 新乡工程学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1865（wakeup-6.1.06-asset） | 拉萨师范学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1083（wakeup-52.36-user-attachment） | 新乡学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1084（wakeup-52.36-user-attachment） | 新余学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3143（wakeup-6.1.06-asset） | 新乡职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2993（wakeup-6.1.06-asset） | 新余职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2851（wakeup-6.1.06-asset） | 文华学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3025（wakeup-6.1.06-asset） | 新星职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3010（wakeup-6.1.06-asset） | 新疆农业职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1086（wakeup-52.36-user-attachment） | 新疆农业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2965（wakeup-6.1.06-asset） | 新疆和田学院（原和田师范专科学校） | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3017（wakeup-6.1.06-asset） | 新疆交通职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2964（wakeup-6.1.06-asset） | 新疆农业大学科学技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3008（wakeup-6.1.06-asset） | 新疆工程学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2984（wakeup-6.1.06-asset） | 新疆工业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3024（wakeup-6.1.06-asset） | 新疆工业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3021（wakeup-6.1.06-asset） | 新疆师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3020（wakeup-6.1.06-asset） | 新疆应用职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3011（wakeup-6.1.06-asset） | 新疆交通职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3033（wakeup-6.1.06-asset） | 新疆医科大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 3032（wakeup-6.1.06-asset） | 新疆开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3012（wakeup-6.1.06-asset） | 新疆理工职业大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3103（wakeup-6.1.06-asset） | 新疆政法学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 3016（wakeup-6.1.06-asset） | 新疆建设职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3023（wakeup-6.1.06-asset） | 新疆生产建设兵团兴新职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1685（wakeup-52.36-user-attachment） | 新疆科技学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 3038（wakeup-6.1.06-asset） | 新疆理工学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3031（wakeup-6.1.06-asset） | 新疆生产建设兵团开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3013（wakeup-6.1.06-asset） | 新疆机电职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3015（wakeup-6.1.06-asset） | 新疆能源职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2961（wakeup-6.1.06-asset） | 新疆警察学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3039（wakeup-6.1.06-asset） | 新疆财经大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3019（wakeup-6.1.06-asset） | 新疆职业大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3018（wakeup-6.1.06-asset） | 新疆石河子职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3109（wakeup-6.1.06-asset） | 新疆第二医学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2960（wakeup-6.1.06-asset） | 新疆艺术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3014（wakeup-6.1.06-asset） | 新疆轻工职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1027（wakeup-52.36-user-attachment） | 无锡太湖学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2853（wakeup-6.1.06-asset） | 无锡商业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2820（wakeup-6.1.06-asset） | 无锡工艺职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2900（wakeup-6.1.06-asset） | 无锡城市职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2847（wakeup-6.1.06-asset） | 无锡职业技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1030（wakeup-52.36-user-attachment） | 无锡职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2215（wakeup-6.1.06-asset） | 日照康养职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2214（wakeup-6.1.06-asset） | 日照科技职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2216（wakeup-6.1.06-asset） | 日照职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2821（wakeup-6.1.06-asset） | 无锡师范高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1733（wakeup-6.1.06-asset） | 昆山杜克大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1728（wakeup-6.1.06-asset） | 昆明传媒学院（原云南艺术学院文华学院） | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2217（wakeup-6.1.06-asset） | 日照航海工程职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 643（wakeup-52.36-user-attachment） | 昆明医科大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1742（wakeup-6.1.06-asset） | 昆明冶金高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1743（wakeup-6.1.06-asset） | 昆明医科大学海源学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 646（wakeup-52.36-user-attachment） | 昆明文理学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3022（wakeup-6.1.06-asset） | 新疆铁道职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1727（wakeup-6.1.06-asset） | 昆明卫生职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 299（wakeup-6.1.06-asset） | 昌吉学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1749（wakeup-6.1.06-asset） | 昆明工业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 497（wakeup-6.1.06-asset） | 昌吉职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 645（wakeup-52.36-user-attachment） | 昆明学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1720（wakeup-6.1.06-asset） | 昆玉职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1741（wakeup-6.1.06-asset） | 昆明铁道职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1457（wakeup-52.36-user-attachment） | 明达职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1711（wakeup-6.1.06-asset） | 晋中信息学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1094（wakeup-52.36-user-attachment） | 星海音乐学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 1685（wakeup-6.1.06-asset） | 晋中健康学院（原山西医科大学晋祠学院） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2306（wakeup-6.1.06-asset） | 昭通职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1698（wakeup-6.1.06-asset） | 晋中学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1523（wakeup-6.1.06-asset） | 景德镇学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1683（wakeup-6.1.06-asset） | 晋中师范高等专科学校 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1703（wakeup-6.1.06-asset） | 景德镇陶瓷职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1582（wakeup-6.1.06-asset） | 景德镇陶瓷大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1684（wakeup-6.1.06-asset） | 晋城职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 780（wakeup-52.36-user-attachment） | 曲阜师范大学 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 2133（wakeup-6.1.06-asset） | 曲阜远东职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1657（wakeup-6.1.06-asset） | 景德镇艺术职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2143（wakeup-6.1.06-asset） | 曲靖健康医学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2122（wakeup-6.1.06-asset） | 普洱学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2144（wakeup-6.1.06-asset） | 曲靖医学高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2395（wakeup-6.1.06-asset） | 朔州陶瓷职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 590（wakeup-52.36-user-attachment） | 暨南大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 441（wakeup-6.1.06-asset） | 朝阳师范学院（原朝阳师范高等专科学校） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2145（wakeup-6.1.06-asset） | 曲靖职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1754（wakeup-52.36-user-attachment） | 曲靖师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 300（wakeup-6.1.06-asset） | 曹妃甸职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 991（wakeup-6.1.06-asset） | 杭州市工人业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1700（wakeup-52.36-user-attachment） | 朝阳师范高等专科学校 | xhtd | xhtd | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1166（wakeup-6.1.06-asset） | 杭州科技职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2390（wakeup-6.1.06-asset） | 松原职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1313（wakeup-6.1.06-asset） | 杭州师范大学钱江学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1856（wakeup-6.1.06-asset） | 林州建筑职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3528（wakeup-6.1.06-asset） | 枣庄应用技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1483（wakeup-6.1.06-asset） | 杭州职业技术大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3449（wakeup-6.1.06-asset） | 枣庄学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 407（wakeup-52.36-user-attachment） | 杭州师范大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3526（wakeup-6.1.06-asset） | 枣庄科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1803（wakeup-6.1.06-asset） | 柳州城市职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3448（wakeup-6.1.06-asset） | 枣庄应用职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3527（wakeup-6.1.06-asset） | 枣庄职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3567（wakeup-6.1.06-asset） | 株洲市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 925（wakeup-6.1.06-asset） | 桂林信息工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 937（wakeup-6.1.06-asset） | 桂林信息科技学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 939（wakeup-6.1.06-asset） | 桂林学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 767（wakeup-6.1.06-asset） | 桂林医科大学 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 670（wakeup-52.36-user-attachment） | 柳州职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 874（wakeup-6.1.06-asset） | 桂林市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1800（wakeup-6.1.06-asset） | 柳州铁道职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 801（wakeup-6.1.06-asset） | 桂林师范高等专科学校 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 936（wakeup-6.1.06-asset） | 桂林师范学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 2243（wakeup-52.36-user-attachment） | 桂林电子科技大学北海校区 | gdbh | gdbh | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 340（wakeup-52.36-user-attachment） | 桂林理工大学南宁分校 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 800（wakeup-6.1.06-asset） | 桂林电子科技大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 343（wakeup-52.36-user-attachment） | 桂林航天工业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 338（wakeup-52.36-user-attachment） | 桂林理工大学 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1926（wakeup-6.1.06-asset） | 梅河口康美职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1338（wakeup-52.36-user-attachment） | 桂林医学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 2795（wakeup-6.1.06-asset） | 桐城师范高等专科学校 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 3241（wakeup-6.1.06-asset） | 榆林职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 495（wakeup-6.1.06-asset） | 楚雄医药高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2816（wakeup-6.1.06-asset） | 梧州医学高等专科学校 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 3183（wakeup-6.1.06-asset） | 榆林学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2859（wakeup-6.1.06-asset） | 武夷学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2844（wakeup-6.1.06-asset） | 武威职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2843（wakeup-6.1.06-asset） | 武威职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2830（wakeup-6.1.06-asset） | 武夷山职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2866（wakeup-6.1.06-asset） | 武汉体育学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2834（wakeup-6.1.06-asset） | 武汉体育学院体育科技学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2865（wakeup-6.1.06-asset） | 武汉传媒学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2862（wakeup-6.1.06-asset） | 武昌职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1038（wakeup-52.36-user-attachment） | 武汉华夏理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3240（wakeup-6.1.06-asset） | 榆林能源科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2840（wakeup-6.1.06-asset） | 武汉光谷职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2833（wakeup-6.1.06-asset） | 武汉城市学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2868（wakeup-6.1.06-asset） | 武汉商学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2905（wakeup-6.1.06-asset） | 武汉城市职业学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 2891（wakeup-6.1.06-asset） | 武汉信息传播职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2870（wakeup-6.1.06-asset） | 武汉商贸职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2894（wakeup-6.1.06-asset） | 武汉工程大学邮电与信息工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2838（wakeup-6.1.06-asset） | 武汉外语外事职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2873（wakeup-6.1.06-asset） | 武汉学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1044（wakeup-52.36-user-attachment） | 武汉工商学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1046（wakeup-52.36-user-attachment） | 武汉工程科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2846（wakeup-6.1.06-asset） | 武汉开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 136（wakeup-52.36-user-attachment） | 楚雄师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2839（wakeup-6.1.06-asset） | 武汉民政职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2879（wakeup-6.1.06-asset） | 武汉晴川学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2877（wakeup-6.1.06-asset） | 武汉工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2837（wakeup-6.1.06-asset） | 武汉科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1055（wakeup-52.36-user-attachment） | 武汉纺织大学外经贸学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1056（wakeup-52.36-user-attachment） | 武汉职业技术学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1052（wakeup-52.36-user-attachment） | 武汉科技大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2835（wakeup-6.1.06-asset） | 武汉航海职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2836（wakeup-6.1.06-asset） | 武汉警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2817（wakeup-6.1.06-asset） | 武汉设计工程学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2887（wakeup-6.1.06-asset） | 武汉铁路桥梁职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2901（wakeup-6.1.06-asset） | 武汉音乐学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 257（wakeup-6.1.06-asset） | 毕节工业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2922（wakeup-6.1.06-asset） | 武汉软件工程职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 256（wakeup-6.1.06-asset） | 毕节幼儿师范高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1924（wakeup-6.1.06-asset） | 民办万博科技职业学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 254（wakeup-6.1.06-asset） | 毕节职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2890（wakeup-6.1.06-asset） | 武汉铁路职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1923（wakeup-6.1.06-asset） | 民办合肥经济技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1918（wakeup-6.1.06-asset） | 民办四川天一学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 1921（wakeup-6.1.06-asset） | 民办合肥财经职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1928（wakeup-6.1.06-asset） | 民政职业大学（原北京社会管理职业学院） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 255（wakeup-6.1.06-asset） | 毕节医学高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2814（wakeup-6.1.06-asset） | 武汉电力职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1922（wakeup-6.1.06-asset） | 民办合肥滨湖职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3275（wakeup-6.1.06-asset） | 永城职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1310（wakeup-6.1.06-asset） | 汉口学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1911（wakeup-6.1.06-asset） | 民航管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3236（wakeup-6.1.06-asset） | 永州职业技术学院 | yzzy | yzzy | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1486（wakeup-6.1.06-asset） | 汉阳大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2273（wakeup-6.1.06-asset） | 汕头市业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2244（wakeup-52.36-user-attachment） | 民政职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1661（wakeup-52.36-user-attachment） | 汕头大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1208（wakeup-6.1.06-asset） | 汉中职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1309（wakeup-6.1.06-asset） | 汉江师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2213（wakeup-6.1.06-asset） | 汝州职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1668（wakeup-6.1.06-asset） | 江南影视艺术职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2636（wakeup-6.1.06-asset） | 汕头职业技术学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 1465（wakeup-52.36-user-attachment） | 江汉大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1622（wakeup-6.1.06-asset） | 江汉艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1525（wakeup-6.1.06-asset） | 江南大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1592（wakeup-6.1.06-asset） | 江苏农林职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1574（wakeup-6.1.06-asset） | 江苏农牧科技职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 593（wakeup-52.36-user-attachment） | 江苏信息职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1661（wakeup-6.1.06-asset） | 江苏司法警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1665（wakeup-6.1.06-asset） | 江海职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 595（wakeup-52.36-user-attachment） | 江苏卫生健康职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1534（wakeup-6.1.06-asset） | 江苏城乡建设职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1663（wakeup-6.1.06-asset） | 江苏商贸职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 600（wakeup-52.36-user-attachment） | 江苏大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1531（wakeup-6.1.06-asset） | 江苏医药职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1606（wakeup-6.1.06-asset） | 江苏城市职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 603（wakeup-52.36-user-attachment） | 江苏师范大学 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 1537（wakeup-6.1.06-asset） | 江苏大学京江学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1693（wakeup-6.1.06-asset） | 江苏安全技术职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1541（wakeup-6.1.06-asset） | 江苏建筑职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2324（wakeup-6.1.06-asset） | 汕尾职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1605（wakeup-6.1.06-asset） | 江苏开放大学 | nuist | nuist | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1543（wakeup-6.1.06-asset） | 江苏海事职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1542（wakeup-6.1.06-asset） | 江苏护理职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1546（wakeup-6.1.06-asset） | 江苏理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 612（wakeup-52.36-user-attachment） | 江苏科技大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1700（wakeup-6.1.06-asset） | 江苏师范大学科文学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1707（wakeup-6.1.06-asset） | 江苏旅游职业学院 | changzhou | changzhou | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1669（wakeup-6.1.06-asset） | 江苏联合职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1662（wakeup-6.1.06-asset） | 江苏航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 610（wakeup-52.36-user-attachment） | 江苏电子信息职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1550（wakeup-6.1.06-asset） | 江苏科技大学苏州理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1554（wakeup-6.1.06-asset） | 江苏财经职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1552（wakeup-6.1.06-asset） | 江苏航运职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 616（wakeup-52.36-user-attachment） | 江苏财会职业学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1633（wakeup-6.1.06-asset） | 江西传媒职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1551（wakeup-6.1.06-asset） | 江苏经贸职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1595（wakeup-6.1.06-asset） | 江西交通职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 621（wakeup-52.36-user-attachment） | 江西农业大学 | jxau | jxau | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1647（wakeup-6.1.06-asset） | 江西信息应用职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1670（wakeup-6.1.06-asset） | 江苏第二师范学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1640（wakeup-6.1.06-asset） | 江西农业工程职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1652（wakeup-6.1.06-asset） | 江西医学高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1636（wakeup-6.1.06-asset） | 江西冶金职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1555（wakeup-6.1.06-asset） | 江苏食品药品职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1695（wakeup-6.1.06-asset） | 江西外语外贸职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1591（wakeup-6.1.06-asset） | 江西工业工程职业技术学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 1651（wakeup-6.1.06-asset） | 江西司法警官职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1658（wakeup-6.1.06-asset） | 江西工程学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1560（wakeup-6.1.06-asset） | 江西工业贸易职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1696（wakeup-6.1.06-asset） | 江西制造职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1632（wakeup-6.1.06-asset） | 江西工商职业技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1642（wakeup-6.1.06-asset） | 江西工程职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1656（wakeup-6.1.06-asset） | 江西师范大学科学技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1708（wakeup-6.1.06-asset） | 江西应用科技学院 | gdei | gdei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 623（wakeup-52.36-user-attachment） | 江西师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1643（wakeup-6.1.06-asset） | 江西应用工程职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1712（wakeup-6.1.06-asset） | 江西师范大学 | jxnu | jxnu | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1694（wakeup-6.1.06-asset） | 江西建设职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1639（wakeup-6.1.06-asset） | 江西新能源科技职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1645（wakeup-6.1.06-asset） | 江西机电职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1717（wakeup-6.1.06-asset） | 江西工业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1611（wakeup-6.1.06-asset） | 江西开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1649（wakeup-6.1.06-asset） | 江西旅游商贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1638（wakeup-6.1.06-asset） | 江西枫林涉外经贸职业学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1631（wakeup-6.1.06-asset） | 江西水利职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1562（wakeup-6.1.06-asset） | 江西服装学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1659（wakeup-6.1.06-asset） | 江西水利电力大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1646（wakeup-6.1.06-asset） | 江西现代职业技术学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 1630（wakeup-6.1.06-asset） | 江西洪州职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1637（wakeup-6.1.06-asset） | 江西泰豪动漫职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1648（wakeup-6.1.06-asset） | 江西环境工程职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1644（wakeup-6.1.06-asset） | 江西科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1629（wakeup-6.1.06-asset） | 江西电子信息职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1563（wakeup-6.1.06-asset） | 江西理工大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1337（wakeup-52.36-user-attachment） | 江西经济管理干部学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 1635（wakeup-6.1.06-asset） | 江西管理职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1564（wakeup-6.1.06-asset） | 江西科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1565（wakeup-6.1.06-asset） | 江西科技师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1610（wakeup-6.1.06-asset） | 江西经济管理干部学院即江西飞行学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1490（wakeup-6.1.06-asset） | 江西警察学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 628（wakeup-52.36-user-attachment） | 江西财经大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1655（wakeup-6.1.06-asset） | 江西财经大学现代经济管理学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1650（wakeup-6.1.06-asset） | 江西陶瓷工艺美术职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1654（wakeup-6.1.06-asset） | 江西职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1567（wakeup-6.1.06-asset） | 江西财经职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1588（wakeup-6.1.06-asset） | 江门职业技术学院 | jmpt | jmpt | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1950（wakeup-52.36-user-attachment） | 江西航空职业技术学院 | javtc | javtc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1701（wakeup-6.1.06-asset） | 江西软件职业技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1687（wakeup-6.1.06-asset） | 江西飞行学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1641（wakeup-6.1.06-asset） | 江西青年职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 919（wakeup-52.36-user-attachment） | 沈阳体育学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 453（wakeup-6.1.06-asset） | 池州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 137（wakeup-52.36-user-attachment） | 池州学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2567（wakeup-6.1.06-asset） | 沈阳化工大学 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 922（wakeup-52.36-user-attachment） | 沈阳医学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 2392（wakeup-6.1.06-asset） | 沈阳北软信息职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1692（wakeup-52.36-user-attachment） | 沈阳农业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2575（wakeup-6.1.06-asset） | 沈阳工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 929（wakeup-52.36-user-attachment） | 沈阳工程学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3169（wakeup-6.1.06-asset） | 江阳城建职业学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2289（wakeup-6.1.06-asset） | 沈阳开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2499（wakeup-6.1.06-asset） | 沈阳职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2581（wakeup-6.1.06-asset） | 沈阳科技学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1568（wakeup-6.1.06-asset） | 江阴职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2582（wakeup-6.1.06-asset） | 沈阳航空航天大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2583（wakeup-6.1.06-asset） | 沙洲职业工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 925（wakeup-52.36-user-attachment） | 沈阳大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2393（wakeup-6.1.06-asset） | 沈阳音乐学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 2580（wakeup-6.1.06-asset） | 沈阳理工大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 303（wakeup-6.1.06-asset） | 沧州交通学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1365（wakeup-52.36-user-attachment） | 沈阳药科大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 430（wakeup-6.1.06-asset） | 沧州师范学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 431（wakeup-6.1.06-asset） | 沧州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1132（wakeup-6.1.06-asset） | 河北交通职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1199（wakeup-6.1.06-asset） | 河北东方学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1362（wakeup-6.1.06-asset） | 河北体育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 413（wakeup-52.36-user-attachment） | 河北农业大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 433（wakeup-6.1.06-asset） | 沧州幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1111（wakeup-6.1.06-asset） | 河北农业大学现代科技学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1306（wakeup-6.1.06-asset） | 河北劳动关系职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1305（wakeup-6.1.06-asset） | 河北化工医药职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 432（wakeup-6.1.06-asset） | 沧州医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1372（wakeup-6.1.06-asset） | 河北司法警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1364（wakeup-6.1.06-asset） | 河北中医药大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1301（wakeup-6.1.06-asset） | 河北地质大学华信学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1229（wakeup-6.1.06-asset） | 河北外国语学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1742（wakeup-52.36-user-attachment） | 河北医科大学临床学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 1373（wakeup-6.1.06-asset） | 河北女子职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1303（wakeup-6.1.06-asset） | 河北地质大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1139（wakeup-6.1.06-asset） | 河北工业职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1741（wakeup-52.36-user-attachment） | 河北医科大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 1371（wakeup-6.1.06-asset） | 河北公安警察职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 426（wakeup-52.36-user-attachment） | 河北建筑工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1299（wakeup-6.1.06-asset） | 河北工业大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1370（wakeup-6.1.06-asset） | 河北对外经贸职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌）; page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1193（wakeup-6.1.06-asset） | 河北机电职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1369（wakeup-6.1.06-asset） | 河北旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1374（wakeup-6.1.06-asset） | 河北正定师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1466（wakeup-6.1.06-asset） | 河北开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1289（wakeup-6.1.06-asset） | 河北民族师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1363（wakeup-6.1.06-asset） | 河北石油职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1366（wakeup-6.1.06-asset） | 河北石油职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 429（wakeup-52.36-user-attachment） | 河北环境工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1292（wakeup-6.1.06-asset） | 河北建材职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1117（wakeup-6.1.06-asset） | 河北科技工程职业技术大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 430（wakeup-52.36-user-attachment） | 河北科技大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1133（wakeup-6.1.06-asset） | 河北科技学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1368（wakeup-6.1.06-asset） | 河北艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 431（wakeup-52.36-user-attachment） | 河北科技大学理工学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1100（wakeup-6.1.06-asset） | 河北软件职业技术学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 1218（wakeup-6.1.06-asset） | 河北能源职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1281（wakeup-6.1.06-asset） | 河北美术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1279（wakeup-6.1.06-asset） | 河南交通职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1375（wakeup-6.1.06-asset） | 河北工艺美术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1467（wakeup-6.1.06-asset） | 河北青年管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 344（wakeup-52.36-user-attachment） | 河南中医药大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1405（wakeup-6.1.06-asset） | 河南体育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1410（wakeup-6.1.06-asset） | 河南信息统计职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1277（wakeup-6.1.06-asset） | 河南农业职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1403（wakeup-6.1.06-asset） | 河南医药大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1408（wakeup-6.1.06-asset） | 河南司法警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 436（wakeup-52.36-user-attachment） | 河南农业大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1276（wakeup-6.1.06-asset） | 河南医学高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1221（wakeup-6.1.06-asset） | 河南地矿职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 439（wakeup-52.36-user-attachment） | 河南大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1215（wakeup-6.1.06-asset） | 河南城建学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1143（wakeup-6.1.06-asset） | 河南工业和信息化职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1418（wakeup-6.1.06-asset） | 河南女子职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1419（wakeup-6.1.06-asset） | 河南对外经济贸易职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1412（wakeup-6.1.06-asset） | 河南工业贸易职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1145（wakeup-6.1.06-asset） | 河南工学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1273（wakeup-6.1.06-asset） | 河南工业职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1413（wakeup-6.1.06-asset） | 河南建筑职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 443（wakeup-52.36-user-attachment） | 河南师范大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 445（wakeup-52.36-user-attachment） | 河南广播电视大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1280（wakeup-6.1.06-asset） | 河北金融学院 | qz_crazy | qz_crazy | keep | declared routable type retained | 2026-10-17 |
| 444（wakeup-52.36-user-attachment） | 河南开放大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1420（wakeup-6.1.06-asset） | 河南新乡工商职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1219（wakeup-6.1.06-asset） | 河南机电职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1274（wakeup-6.1.06-asset） | 河南工业大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1416（wakeup-6.1.06-asset） | 河南推拿职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1181（wakeup-6.1.06-asset） | 河南开封科技传媒学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1409（wakeup-6.1.06-asset） | 河南检察职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1407（wakeup-6.1.06-asset） | 河南水利与环境职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1151（wakeup-6.1.06-asset） | 河南测绘职业学院 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1484（wakeup-6.1.06-asset） | 河南物流职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1411（wakeup-6.1.06-asset） | 河南林业职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1415（wakeup-6.1.06-asset） | 河南护理职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1267（wakeup-6.1.06-asset） | 河南理工大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1404（wakeup-6.1.06-asset） | 河南漯河市技师学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1194（wakeup-6.1.06-asset） | 河南牧业经济学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1152（wakeup-6.1.06-asset） | 河南科技学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1146（wakeup-6.1.06-asset） | 河南经贸职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1101（wakeup-6.1.06-asset） | 河南科技职业大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1414（wakeup-6.1.06-asset） | 河南艺术职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 447（wakeup-52.36-user-attachment） | 河南科技大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1263（wakeup-6.1.06-asset） | 河南财政金融学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1260（wakeup-6.1.06-asset） | 河南质量工程职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1259（wakeup-6.1.06-asset） | 河套学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 455（wakeup-52.36-user-attachment） | 河西学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2127（wakeup-6.1.06-asset） | 泉州信息工程学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1258（wakeup-6.1.06-asset） | 河池学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2153（wakeup-6.1.06-asset） | 泉州华光职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2195（wakeup-6.1.06-asset） | 泉州医学高等专科学校 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 448（wakeup-52.36-user-attachment） | 河南职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1123（wakeup-6.1.06-asset） | 河源职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2163（wakeup-6.1.06-asset） | 泉州师范学院 | umooc | umooc | keep | declared routable type retained | 2026-10-17 |
| 1264（wakeup-6.1.06-asset） | 河南警察学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2131（wakeup-6.1.06-asset） | 泉州幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2132（wakeup-6.1.06-asset） | 泉州工程职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2130（wakeup-6.1.06-asset） | 泉州海洋职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2129（wakeup-6.1.06-asset） | 泉州工艺美术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2200（wakeup-6.1.06-asset） | 泉州纺织服装职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2172（wakeup-6.1.06-asset） | 泉州经贸职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2164（wakeup-6.1.06-asset） | 泉州职业技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2731（wakeup-6.1.06-asset） | 泰山护理职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1017（wakeup-52.36-user-attachment） | 泰山学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2796（wakeup-6.1.06-asset） | 泰山职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1891（wakeup-6.1.06-asset） | 泸州医疗器械职业学院 | gdei | gdei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1625（wakeup-52.36-user-attachment） | 泰山科技大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1626（wakeup-52.36-user-attachment） | 泰山科技学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2793（wakeup-6.1.06-asset） | 泰州学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1777（wakeup-6.1.06-asset） | 泸州职业技术学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1851（wakeup-6.1.06-asset） | 洛阳师范学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1857（wakeup-6.1.06-asset） | 洛阳商业职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 631（wakeup-52.36-user-attachment） | 济南大学泉城学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 672（wakeup-52.36-user-attachment） | 洛阳理工学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 1875（wakeup-6.1.06-asset） | 洛阳轴承职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 632（wakeup-52.36-user-attachment） | 济南工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1855（wakeup-6.1.06-asset） | 洛阳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1697（wakeup-6.1.06-asset） | 济南幼儿师范高等专科学校 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1714（wakeup-6.1.06-asset） | 济南护理职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1609（wakeup-6.1.06-asset） | 济南市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 634（wakeup-52.36-user-attachment） | 济宁医学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1597（wakeup-6.1.06-asset） | 济宁职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1627（wakeup-6.1.06-asset） | 济宁汽车工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1572（wakeup-6.1.06-asset） | 济南职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2707（wakeup-6.1.06-asset） | 泰州职业技术学院 | changzhou | changzhou | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3445（wakeup-6.1.06-asset） | 浙江中医药大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1232（wakeup-52.36-user-attachment） | 浙江交通职业技术学院 | ztvtit | ztvtit | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1777（wakeup-52.36-user-attachment） | 济源职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1491（wakeup-6.1.06-asset） | 济宁学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3302（wakeup-6.1.06-asset） | 浙江农业商贸职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3304（wakeup-6.1.06-asset） | 浙江农林大学暨阳学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3446（wakeup-6.1.06-asset） | 浙江东方职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3305（wakeup-6.1.06-asset） | 浙江同济科技职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3306（wakeup-6.1.06-asset） | 浙江商业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3316（wakeup-6.1.06-asset） | 浙江工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1239（wakeup-52.36-user-attachment） | 浙江大学城市学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3519（wakeup-6.1.06-asset） | 浙江体育职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3518（wakeup-6.1.06-asset） | 浙江国际海运职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3413（wakeup-6.1.06-asset） | 浙江工商职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3319（wakeup-6.1.06-asset） | 浙江工贸职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1250（wakeup-52.36-user-attachment） | 浙江建设职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3438（wakeup-6.1.06-asset） | 浙江广厦建设职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3507（wakeup-6.1.06-asset） | 浙江机电职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3325（wakeup-6.1.06-asset） | 浙江旅游职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3514（wakeup-6.1.06-asset） | 浙江机电职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3322（wakeup-6.1.06-asset） | 浙江师范大学行知学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3320（wakeup-6.1.06-asset） | 浙江师范大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1242（wakeup-52.36-user-attachment） | 浙江安防职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3372（wakeup-6.1.06-asset） | 浙江海洋大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 3330（wakeup-6.1.06-asset） | 浙江水利水电学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 3329（wakeup-6.1.06-asset） | 浙江横店影视职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3520（wakeup-6.1.06-asset） | 浙江特殊教育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3335（wakeup-6.1.06-asset） | 浙江科技大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3338（wakeup-6.1.06-asset） | 浙江纺织服装职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3333（wakeup-6.1.06-asset） | 浙江理工大学科技与艺术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3328（wakeup-6.1.06-asset） | 浙江树人学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3516（wakeup-6.1.06-asset） | 浙江育英职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1258（wakeup-52.36-user-attachment） | 浙江理工大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3515（wakeup-6.1.06-asset） | 浙江艺术职业学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 3513（wakeup-6.1.06-asset） | 浙江药科职业大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3342（wakeup-6.1.06-asset） | 浙江警官职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3340（wakeup-6.1.06-asset） | 浙江经济职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3416（wakeup-6.1.06-asset） | 浙江警察学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3434（wakeup-6.1.06-asset） | 浙江金华科贸职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1270（wakeup-52.36-user-attachment） | 浙江越秀外国语学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1906（wakeup-52.36-user-attachment） | 浙江邮电职业技术学院 | zptc | zptc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3388（wakeup-6.1.06-asset） | 浙江舟山群岛新区旅游与健康职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3344（wakeup-6.1.06-asset） | 浙江财经大学东方学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3517（wakeup-6.1.06-asset） | 浙江长征职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3441（wakeup-6.1.06-asset） | 浙江金融职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1460（wakeup-6.1.06-asset） | 海南体育职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1254（wakeup-6.1.06-asset） | 海南工商职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1462（wakeup-6.1.06-asset） | 海南东方新丝路职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1461（wakeup-6.1.06-asset） | 海南健康管理职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3374（wakeup-6.1.06-asset） | 浙江音乐学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1458（wakeup-6.1.06-asset） | 海南医科大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1480（wakeup-6.1.06-asset） | 海南开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1223（wakeup-6.1.06-asset） | 海南职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1231（wakeup-6.1.06-asset） | 海南科技职业大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1250（wakeup-6.1.06-asset） | 海南软件职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1463（wakeup-6.1.06-asset） | 海南艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3346（wakeup-6.1.06-asset） | 淄博师范高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1272（wakeup-52.36-user-attachment） | 淄博职业学院 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 1876（wakeup-6.1.06-asset） | 涟源钢铁总厂职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1119（wakeup-6.1.06-asset） | 海口经济学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1459（wakeup-6.1.06-asset） | 海南政法职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3525（wakeup-6.1.06-asset） | 淄博职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1249（wakeup-6.1.06-asset） | 淮北师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1784（wakeup-52.36-user-attachment） | 淮北理工学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1395（wakeup-6.1.06-asset） | 淮北职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1140（wakeup-6.1.06-asset） | 淮南职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2326（wakeup-6.1.06-asset） | 深圳信息职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 463（wakeup-52.36-user-attachment） | 淮南联合大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 467（wakeup-52.36-user-attachment） | 淮阴工学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2472（wakeup-6.1.06-asset） | 深圳北理莫斯科大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1241（wakeup-6.1.06-asset） | 淮阴师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2323（wakeup-6.1.06-asset） | 深圳城市职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2584（wakeup-6.1.06-asset） | 深圳信息职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2593（wakeup-6.1.06-asset） | 深圳职业技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2586（wakeup-6.1.06-asset） | 深圳技术大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2271（wakeup-6.1.06-asset） | 深圳开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 165（wakeup-6.1.06-asset） | 渤海船舶职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 164（wakeup-6.1.06-asset） | 渤海理工职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2845（wakeup-6.1.06-asset） | 温州市工人业余大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1683（wakeup-52.36-user-attachment） | 清远职业技术学院 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 2842（wakeup-6.1.06-asset） | 渭南职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2917（wakeup-6.1.06-asset） | 温州大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2823（wakeup-6.1.06-asset） | 温州职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 784（wakeup-52.36-user-attachment） | 清华大学（反馈群：807321351） | thu | thu | keep | declared routable type retained | 2026-10-17 |
| 1919（wakeup-6.1.06-asset） | 湄洲湾职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1423（wakeup-6.1.06-asset） | 湖北三峡航空学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1432（wakeup-6.1.06-asset） | 湖北体育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1102（wakeup-6.1.06-asset） | 湖北中医药高等专科学校 | yl | yl | keep | declared routable type retained | 2026-10-17 |
| 1481（wakeup-6.1.06-asset） | 湖北交通职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2822（wakeup-6.1.06-asset） | 温州肯恩大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1434（wakeup-6.1.06-asset） | 湖北健康职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 472（wakeup-52.36-user-attachment） | 湖北医药学院药护学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 1429（wakeup-6.1.06-asset） | 湖北国土资源职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 478（wakeup-52.36-user-attachment） | 湖北工业大学工程技术学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 995（wakeup-6.1.06-asset） | 湖北大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1234（wakeup-6.1.06-asset） | 湖北幼儿师范高等专科学校 | buaa | buaa | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1431（wakeup-6.1.06-asset） | 湖北工程职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 473（wakeup-52.36-user-attachment） | 湖北城市建设职业技术学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1475（wakeup-6.1.06-asset） | 湖北开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 470（wakeup-52.36-user-attachment） | 湖北中医药大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1425（wakeup-6.1.06-asset） | 湖北开放职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 996（wakeup-6.1.06-asset） | 湖北大学知行学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1225（wakeup-6.1.06-asset） | 湖北恩施学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1120（wakeup-6.1.06-asset） | 湖北文理学院理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1476（wakeup-6.1.06-asset） | 湖北武汉公安管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1294（wakeup-52.36-user-attachment） | 湖北汽车工业学院 | huat | huat | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1778（wakeup-52.36-user-attachment） | 湖北民族大学 | hbmzu | hbmzu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1422（wakeup-6.1.06-asset） | 湖北汽车工业学院科技学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1007（wakeup-6.1.06-asset） | 湖北生物科技职业学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1430（wakeup-6.1.06-asset） | 湖北生态工程职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1474（wakeup-6.1.06-asset） | 湖北省纺织职工大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1005（wakeup-6.1.06-asset） | 湖北文理学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1157（wakeup-6.1.06-asset） | 湖北水利水电职业技术学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 1424（wakeup-6.1.06-asset） | 湖北职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1008（wakeup-6.1.06-asset） | 湖北科技学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1013（wakeup-6.1.06-asset） | 湖北警官学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1009（wakeup-6.1.06-asset） | 湖北科技职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1426（wakeup-6.1.06-asset） | 湖北艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1433（wakeup-6.1.06-asset） | 湖北铁道运输职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 488（wakeup-52.36-user-attachment） | 湖北经济学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1427（wakeup-6.1.06-asset） | 湖北财税职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1014（wakeup-6.1.06-asset） | 湖南中医药大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1195（wakeup-6.1.06-asset） | 湖南三一工业职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1421（wakeup-6.1.06-asset） | 湖北美术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1183（wakeup-6.1.06-asset） | 湖南中医药大学湘杏学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1447（wakeup-6.1.06-asset） | 湖南中医药高等专科学校 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1015（wakeup-6.1.06-asset） | 湖南交通职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 495（wakeup-52.36-user-attachment） | 湖南信息职业技术学院 | hniu | hniu | keep | declared routable type retained | 2026-10-17 |
| 1440（wakeup-6.1.06-asset） | 湖南体育职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 494（wakeup-52.36-user-attachment） | 湖南信息学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1130（wakeup-6.1.06-asset） | 湖南劳动人事职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1436（wakeup-6.1.06-asset） | 湖南交通工程学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1022（wakeup-6.1.06-asset） | 湖南化工职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1438（wakeup-6.1.06-asset） | 湖南冶金职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1027（wakeup-6.1.06-asset） | 湖南吉利汽车职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1021（wakeup-6.1.06-asset） | 湖南农业大学东方科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1016（wakeup-6.1.06-asset） | 湖南人文科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 504（wakeup-52.36-user-attachment） | 湖南商学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1442（wakeup-6.1.06-asset） | 湖南司法警官职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1131（wakeup-6.1.06-asset） | 湖南外国语职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1446（wakeup-6.1.06-asset） | 湖南城建职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1439（wakeup-6.1.06-asset） | 湖南大众传媒职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1031（wakeup-6.1.06-asset） | 湖南外贸职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1451（wakeup-6.1.06-asset） | 湖南国防工业职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 510（wakeup-52.36-user-attachment） | 湖南工业职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 511（wakeup-52.36-user-attachment） | 湖南工商大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1035（wakeup-6.1.06-asset） | 湖南工业大学科技学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1033（wakeup-6.1.06-asset） | 湖南女子学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1453（wakeup-6.1.06-asset） | 湖南工商职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1187（wakeup-6.1.06-asset） | 湖南安全技术职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1780（wakeup-52.36-user-attachment） | 湖南工程学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1795（wakeup-52.36-user-attachment） | 湖南应用技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1189（wakeup-6.1.06-asset） | 湖南工程学院应用技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1196（wakeup-6.1.06-asset） | 湖南商务职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1203（wakeup-6.1.06-asset） | 湖南幼儿师范高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1753（wakeup-52.36-user-attachment） | 湖南工艺美术职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1216（wakeup-6.1.06-asset） | 湖南民族职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1046（wakeup-6.1.06-asset） | 湖南机电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1443（wakeup-6.1.06-asset） | 湖南环境生物职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1180（wakeup-6.1.06-asset） | 湖南汽车工程职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1437（wakeup-6.1.06-asset） | 湖南汽车工程职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1043（wakeup-6.1.06-asset） | 湖南文理学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1478（wakeup-6.1.06-asset） | 湖南开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1448（wakeup-6.1.06-asset） | 湖南电气职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1053（wakeup-6.1.06-asset） | 湖南科技学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1450（wakeup-6.1.06-asset） | 湖南电子科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 520（wakeup-52.36-user-attachment） | 湖南生物机电职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1049（wakeup-6.1.06-asset） | 湖南理工大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1054（wakeup-6.1.06-asset） | 湖南科技职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1055（wakeup-6.1.06-asset） | 湖南第一师范学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1477（wakeup-6.1.06-asset） | 湖南航空工业职工工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1441（wakeup-6.1.06-asset） | 湖南网络工程职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1144（wakeup-6.1.06-asset） | 湖南石油化工职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 525（wakeup-52.36-user-attachment） | 湖南财政经济学院 | qz_crazy | qz_crazy | keep | declared routable type retained | 2026-10-17 |
| 1444（wakeup-6.1.06-asset） | 湖南艺术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1156（wakeup-6.1.06-asset） | 湖南财经工业职业技术学院 | xytc | xytc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1148（wakeup-6.1.06-asset） | 湖南铁道职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1449（wakeup-6.1.06-asset） | 湖南都市职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1393（wakeup-6.1.06-asset） | 湖州健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1479（wakeup-6.1.06-asset） | 湖南金融技术职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1061（wakeup-6.1.06-asset） | 湖南高速铁路职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1452（wakeup-6.1.06-asset） | 湖南高尔夫旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3042（wakeup-6.1.06-asset） | 湘中幼儿师范高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 531（wakeup-52.36-user-attachment） | 湖州师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3133（wakeup-6.1.06-asset） | 湘南学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2983（wakeup-6.1.06-asset） | 湘南幼儿师范高等专科学校 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1096（wakeup-52.36-user-attachment） | 湘潭医卫职业技术学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1062（wakeup-6.1.06-asset） | 湖州学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2982（wakeup-6.1.06-asset） | 湘潭科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3027（wakeup-6.1.06-asset） | 湘潭教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1224（wakeup-6.1.06-asset） | 湖州职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3129（wakeup-6.1.06-asset） | 湘潭大学兴湘学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3045（wakeup-6.1.06-asset） | 湘潭理工学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2926（wakeup-6.1.06-asset） | 湘西民族职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 454（wakeup-6.1.06-asset） | 滁州城市职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 304（wakeup-6.1.06-asset） | 滁州学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 231（wakeup-52.36-user-attachment） | 滇西应用技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1927（wakeup-6.1.06-asset） | 满洲里俄语职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1894（wakeup-6.1.06-asset） | 漯河医学高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1853（wakeup-6.1.06-asset） | 漯河职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 586（wakeup-6.1.06-asset） | 滇池学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1854（wakeup-6.1.06-asset） | 漯河食品职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1425（wakeup-52.36-user-attachment） | 湛江幼儿师范专科学校 | urp | urp | keep | declared routable type retained | 2026-10-17 |
| 1466（wakeup-52.36-user-attachment） | 滁州职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1852（wakeup-6.1.06-asset） | 漯河食品工程职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 549（wakeup-6.1.06-asset） | 滇西科技师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3414（wakeup-6.1.06-asset） | 漳州卫生职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2985（wakeup-6.1.06-asset） | 潇湘职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3524（wakeup-6.1.06-asset） | 漳州理工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2885（wakeup-6.1.06-asset） | 潍坊工程职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3523（wakeup-6.1.06-asset） | 漳州科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3521（wakeup-6.1.06-asset） | 漳州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2884（wakeup-6.1.06-asset） | 潍坊科技学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2832（wakeup-6.1.06-asset） | 潍坊食品科技职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2831（wakeup-6.1.06-asset） | 潍坊工商职业学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 410（wakeup-6.1.06-asset） | 潮州卫生健康职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2889（wakeup-6.1.06-asset） | 潍坊环境工程职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2818（wakeup-6.1.06-asset） | 潍坊理工学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 3522（wakeup-6.1.06-asset） | 漳州城市职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2125（wakeup-6.1.06-asset） | 濮阳医学高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3288（wakeup-6.1.06-asset） | 炎黄职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2114（wakeup-6.1.06-asset） | 濮阳石油化工职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1169（wakeup-52.36-user-attachment） | 烟台大学文经学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3157（wakeup-6.1.06-asset） | 烟台大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1168（wakeup-52.36-user-attachment） | 烟台大学（从选课结果导入！） | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 3277（wakeup-6.1.06-asset） | 烟台城市科技职业学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 3280（wakeup-6.1.06-asset） | 烟台工程职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3279（wakeup-6.1.06-asset） | 烟台汽车工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 469（wakeup-6.1.06-asset） | 潮汕职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1626（wakeup-6.1.06-asset） | 焦作工贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3217（wakeup-6.1.06-asset） | 烟台南山学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3281（wakeup-6.1.06-asset） | 烟台职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1813（wakeup-52.36-user-attachment） | 烟台科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1575（wakeup-6.1.06-asset） | 焦作师范高等专科学校 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 3179（wakeup-6.1.06-asset） | 燕山大学里仁学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3278（wakeup-6.1.06-asset） | 烟台幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1625（wakeup-6.1.06-asset） | 焦作新材料职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1925（wakeup-6.1.06-asset） | 牡丹江大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1362（wakeup-52.36-user-attachment） | 牡丹江医学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 1929（wakeup-6.1.06-asset） | 牡丹江医科大学 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1452（wakeup-52.36-user-attachment） | 燕京理工学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3267（wakeup-6.1.06-asset） | 玉林职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3177（wakeup-6.1.06-asset） | 玉溪农业职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3178（wakeup-6.1.06-asset） | 玉林师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3505（wakeup-6.1.06-asset） | 珠海城市职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3176（wakeup-6.1.06-asset） | 玉溪师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3244（wakeup-6.1.06-asset） | 玉溪职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3268（wakeup-6.1.06-asset） | 玉柴职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 741（wakeup-6.1.06-asset） | 甘肃农业大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 739（wakeup-6.1.06-asset） | 甘肃中医药大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3559（wakeup-6.1.06-asset） | 珠海艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 742（wakeup-6.1.06-asset） | 甘肃卫生职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 894（wakeup-6.1.06-asset） | 甘肃工业职业技术大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 743（wakeup-6.1.06-asset） | 甘肃工业职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 892（wakeup-6.1.06-asset） | 甘肃农业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 785（wakeup-6.1.06-asset） | 甘肃医学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 848（wakeup-6.1.06-asset） | 甘孜职业学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 870（wakeup-6.1.06-asset） | 甘肃开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 889（wakeup-6.1.06-asset） | 甘肃机电职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 768（wakeup-6.1.06-asset） | 甘肃建筑职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 698（wakeup-52.36-user-attachment） | 牡丹江师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 888（wakeup-6.1.06-asset） | 甘肃有色冶金职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 895（wakeup-6.1.06-asset） | 甘肃林业职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1372（wakeup-52.36-user-attachment） | 甘肃政法大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 897（wakeup-6.1.06-asset） | 甘肃民族师范学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 890（wakeup-6.1.06-asset） | 甘肃钢铁职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 887（wakeup-6.1.06-asset） | 甘肃财贸职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 891（wakeup-6.1.06-asset） | 甘肃畜牧工程职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 170（wakeup-6.1.06-asset） | 白城医学高等专科学校 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 896（wakeup-6.1.06-asset） | 甘肃警察学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 259（wakeup-6.1.06-asset） | 白银矿冶职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 893（wakeup-6.1.06-asset） | 甘肃警察职业学院 | zf | zf | keep | URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 253（wakeup-6.1.06-asset） | 百色职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1481（wakeup-52.36-user-attachment） | 皖南医学院 | login_xbellsoft | login_xbellsoft | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 100（wakeup-52.36-user-attachment） | 百色学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1064（wakeup-52.36-user-attachment） | 皖江工学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2826（wakeup-6.1.06-asset） | 皖西卫生职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 247（wakeup-6.1.06-asset） | 白城职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3271（wakeup-6.1.06-asset） | 益阳医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3238（wakeup-6.1.06-asset） | 益阳教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 171（wakeup-6.1.06-asset） | 白城师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3270（wakeup-6.1.06-asset） | 益阳师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2909（wakeup-6.1.06-asset） | 皖西学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2827（wakeup-6.1.06-asset） | 皖北卫生职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3224（wakeup-6.1.06-asset） | 盐城工业职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3272（wakeup-6.1.06-asset） | 益阳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3291（wakeup-6.1.06-asset） | 盐城幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2124（wakeup-6.1.06-asset） | 盘锦职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1898（wakeup-6.1.06-asset） | 眉山职业技术学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 2295（wakeup-6.1.06-asset） | 石嘴山工贸职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3175（wakeup-6.1.06-asset） | 盐城工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3174（wakeup-6.1.06-asset） | 盐城师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2418（wakeup-6.1.06-asset） | 石家庄信息工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2417（wakeup-6.1.06-asset） | 石家庄工商职业学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 2414（wakeup-6.1.06-asset） | 石家庄医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2420（wakeup-6.1.06-asset） | 石家庄工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2413（wakeup-6.1.06-asset） | 石家庄科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2415（wakeup-6.1.06-asset） | 石家庄科技信息职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2416（wakeup-6.1.06-asset） | 石家庄理工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2412（wakeup-6.1.06-asset） | 石家庄幼儿师范高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3286（wakeup-6.1.06-asset） | 盐城农业科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2590（wakeup-6.1.06-asset） | 石家庄铁路职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2419（wakeup-6.1.06-asset） | 石家庄财经职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2588（wakeup-6.1.06-asset） | 石家庄金融职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2692（wakeup-6.1.06-asset） | 石家庄铁道大学四方学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2589（wakeup-6.1.06-asset） | 石家庄邮电职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2500（wakeup-6.1.06-asset） | 石家庄职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 941（wakeup-52.36-user-attachment） | 石家庄铁道大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1912（wakeup-6.1.06-asset） | 磨料磨具工业职工大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2296（wakeup-6.1.06-asset） | 神木职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2267（wakeup-6.1.06-asset） | 石河子大学师范学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 644（wakeup-6.1.06-asset） | 福州大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 646（wakeup-6.1.06-asset） | 福州大学至诚学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 653（wakeup-6.1.06-asset） | 福州科技职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 604（wakeup-6.1.06-asset） | 福州理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 656（wakeup-6.1.06-asset） | 福州墨尔本理工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 630（wakeup-6.1.06-asset） | 福州英华职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 652（wakeup-6.1.06-asset） | 福州黎明职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 645（wakeup-6.1.06-asset） | 福州工商学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 632（wakeup-6.1.06-asset） | 福州职业技术学院 | fstvc | fstvc | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2209（wakeup-52.36-user-attachment） | 福州软件职业技术学院 | fstvc | fstvc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 605（wakeup-6.1.06-asset） | 福建中医药大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 633（wakeup-6.1.06-asset） | 福建信息职业技术学院 | fstvc | fstvc | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 243（wakeup-52.36-user-attachment） | 福建医科大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 634（wakeup-6.1.06-asset） | 福建体育职业技术学院 | fstvc | fstvc | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 612（wakeup-6.1.06-asset） | 福建师范大学协和学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 651（wakeup-6.1.06-asset） | 福建农业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 609（wakeup-6.1.06-asset） | 福建商学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 636（wakeup-6.1.06-asset） | 福建教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 655（wakeup-6.1.06-asset） | 福建幼儿师范高等专科学校 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 635（wakeup-6.1.06-asset） | 福建开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 648（wakeup-6.1.06-asset） | 福建华南女子职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 614（wakeup-6.1.06-asset） | 福建水利电力职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 248（wakeup-52.36-user-attachment） | 福建江夏学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 649（wakeup-6.1.06-asset） | 福建林业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 647（wakeup-6.1.06-asset） | 福建福耀科技大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 610（wakeup-6.1.06-asset） | 福建理工大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 650（wakeup-6.1.06-asset） | 福建电力职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 654（wakeup-6.1.06-asset） | 福建艺术职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 629（wakeup-6.1.06-asset） | 福建警察学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 622（wakeup-6.1.06-asset） | 福建船政交通职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2155（wakeup-6.1.06-asset） | 秦皇岛工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 943（wakeup-52.36-user-attachment） | 绍兴文理学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2211（wakeup-6.1.06-asset） | 秦皇岛职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1217（wakeup-6.1.06-asset） | 红河卫生职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1740（wakeup-52.36-user-attachment） | 绥化学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 2689（wakeup-6.1.06-asset） | 绍兴理工学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2687（wakeup-6.1.06-asset） | 绍兴职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1065（wakeup-6.1.06-asset） | 红河学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1901（wakeup-6.1.06-asset） | 绵阳城市学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1860（wakeup-6.1.06-asset） | 罗定职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1916（wakeup-6.1.06-asset） | 绵阳师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1897（wakeup-6.1.06-asset） | 绵阳飞行职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1902（wakeup-6.1.06-asset） | 绵阳职业技术学院 | scau | scau | keep | declared routable type retained | 2026-10-17 |
| 1813（wakeup-6.1.06-asset） | 聊城大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1849（wakeup-6.1.06-asset） | 聊城科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3562（wakeup-6.1.06-asset） | 自贡职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3558（wakeup-6.1.06-asset） | 肇庆医学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 625（wakeup-6.1.06-asset） | 福建卫生职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2825（wakeup-6.1.06-asset） | 芜湖职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2828（wakeup-6.1.06-asset） | 芜湖医药健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1887（wakeup-6.1.06-asset） | 聊城职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2824（wakeup-6.1.06-asset） | 芜湖学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2829（wakeup-6.1.06-asset） | 芜湖航空职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2686（wakeup-6.1.06-asset） | 苏州信息职业技术学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 2360（wakeup-6.1.06-asset） | 苏州城市学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1335（wakeup-52.36-user-attachment） | 肇庆学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 2684（wakeup-6.1.06-asset） | 苏州农业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 949（wakeup-52.36-user-attachment） | 苏州卫生职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2361（wakeup-6.1.06-asset） | 苏州工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2358（wakeup-6.1.06-asset） | 苏州幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2677（wakeup-6.1.06-asset） | 苏州工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2354（wakeup-6.1.06-asset） | 苏州旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2680（wakeup-6.1.06-asset） | 苏州大学应用技术学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 950（wakeup-52.36-user-attachment） | 苏州大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2357（wakeup-6.1.06-asset） | 苏州百年职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2670（wakeup-6.1.06-asset） | 苏州经贸职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2507（wakeup-6.1.06-asset） | 苏州托普信息职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2359（wakeup-6.1.06-asset） | 苏州职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2243（wakeup-6.1.06-asset） | 苏州高博职业学院 | wisedu | wisedu | keep | page 金智 | 2026-10-17 |
| 1576（wakeup-6.1.06-asset） | 荆州学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1905（wakeup-6.1.06-asset） | 茅台学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2685（wakeup-6.1.06-asset） | 苏州健雄职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1623（wakeup-6.1.06-asset） | 荆州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1815（wakeup-6.1.06-asset） | 莱芜职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1620（wakeup-6.1.06-asset） | 荆门通用航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1401（wakeup-6.1.06-asset） | 菏泽家政职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1624（wakeup-6.1.06-asset） | 荆州理工职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1066（wakeup-6.1.06-asset） | 菏泽医学专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2123（wakeup-6.1.06-asset） | 莆田学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1621（wakeup-6.1.06-asset） | 荆门职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1402（wakeup-6.1.06-asset） | 菏泽职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2111（wakeup-6.1.06-asset） | 萍乡卫生职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3294（wakeup-6.1.06-asset） | 营口职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1176（wakeup-52.36-user-attachment） | 营口理工学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 248（wakeup-6.1.06-asset） | 蚌埠医科大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2110（wakeup-6.1.06-asset） | 萍乡学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 250（wakeup-6.1.06-asset） | 蚌埠经济技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1068（wakeup-6.1.06-asset） | 衡水学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 192（wakeup-6.1.06-asset） | 蚌埠学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1367（wakeup-6.1.06-asset） | 衡水职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1455（wakeup-6.1.06-asset） | 衡阳科技职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1188（wakeup-6.1.06-asset） | 衡阳幼儿师范高等专科学校 | zf_new | zf_new | remap | conflict: qz via page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2168（wakeup-6.1.06-asset） | 衢州学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 249（wakeup-6.1.06-asset） | 蚌埠工商学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 537（wakeup-52.36-user-attachment） | 衡阳师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1435（wakeup-6.1.06-asset） | 衡阳师范学院南岳学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3003（wakeup-6.1.06-asset） | 西交利物浦大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2986（wakeup-6.1.06-asset） | 襄阳科技职业学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 2210（wakeup-6.1.06-asset） | 衢州职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2987（wakeup-6.1.06-asset） | 襄阳汽车职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1104（wakeup-52.36-user-attachment） | 西北工业大学（备用） | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 3037（wakeup-6.1.06-asset） | 西北工业大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1103（wakeup-52.36-user-attachment） | 西北工业大学（浏览器登录） | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1111（wakeup-52.36-user-attachment） | 西南医科大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1105（wakeup-52.36-user-attachment） | 西北师范大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3055（wakeup-6.1.06-asset） | 西北民族大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1100（wakeup-52.36-user-attachment） | 西北农林科技大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1115（wakeup-52.36-user-attachment） | 西南林业大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3062（wakeup-6.1.06-asset） | 西南大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1746（wakeup-52.36-user-attachment） | 西南科技大学 | swust | swust | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1098（wakeup-52.36-user-attachment） | 襄阳职业技术大学 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1117（wakeup-52.36-user-attachment） | 西南石油大学南充校区 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1116（wakeup-52.36-user-attachment） | 西南石油大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3069（wakeup-6.1.06-asset） | 西南财经大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2978（wakeup-6.1.06-asset） | 西南财经大学天府学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3127（wakeup-6.1.06-asset） | 西安交通工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1119（wakeup-52.36-user-attachment） | 西安交通大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1650（wakeup-52.36-user-attachment） | 西安医学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2928（wakeup-6.1.06-asset） | 西安信息职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2955（wakeup-6.1.06-asset） | 西安城市建设职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2958（wakeup-6.1.06-asset） | 西安医学高等专科学校 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3075（wakeup-6.1.06-asset） | 西安工业大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3074（wakeup-6.1.06-asset） | 西安外国语大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2959（wakeup-6.1.06-asset） | 西宁城市职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2976（wakeup-6.1.06-asset） | 西双版纳职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3076（wakeup-6.1.06-asset） | 西安工程大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1458（wakeup-52.36-user-attachment） | 西安工商学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2970（wakeup-6.1.06-asset） | 西安体育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3030（wakeup-6.1.06-asset） | 西安开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3078（wakeup-6.1.06-asset） | 西安建筑科技大学（可切换学期） | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 2967（wakeup-6.1.06-asset） | 西安市卫生学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3107（wakeup-6.1.06-asset） | 西安建筑科技大学华清学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3080（wakeup-6.1.06-asset） | 西安文理学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3147（wakeup-6.1.06-asset） | 西安明德理工学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2966（wakeup-6.1.06-asset） | 西安欧亚学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1125（wakeup-52.36-user-attachment） | 西安思源学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2962（wakeup-6.1.06-asset） | 西安汽车职业大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2956（wakeup-6.1.06-asset） | 西安海棠职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3029（wakeup-6.1.06-asset） | 西安电力机械制造公司机电学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2954（wakeup-6.1.06-asset） | 西安电力高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2929（wakeup-6.1.06-asset） | 西安科技大学高新学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3085（wakeup-6.1.06-asset） | 西安石油大学 | xsyu_shuwei | xsyu_shuwei | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2968（wakeup-6.1.06-asset） | 西安美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2963（wakeup-6.1.06-asset） | 西安电子科技大学长安学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3108（wakeup-6.1.06-asset） | 西安职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3087（wakeup-6.1.06-asset） | 西安航空学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1128（wakeup-52.36-user-attachment） | 西安电子科技大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3132（wakeup-6.1.06-asset） | 西安航空职业技术学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1133（wakeup-52.36-user-attachment） | 西安邮电大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3150（wakeup-6.1.06-asset） | 西安财经大学行知学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3035（wakeup-6.1.06-asset） | 西安工商大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1132（wakeup-52.36-user-attachment） | 西安财经大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2957（wakeup-6.1.06-asset） | 西安铁路职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1693（wakeup-52.36-user-attachment） | 西安翻译学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3028（wakeup-6.1.06-asset） | 西安铁路工程职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2977（wakeup-6.1.06-asset） | 西昌医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3002（wakeup-6.1.06-asset） | 西湖大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3093（wakeup-6.1.06-asset） | 西藏大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3036（wakeup-6.1.06-asset） | 西藏农牧大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2969（wakeup-6.1.06-asset） | 西安音乐学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3112（wakeup-6.1.06-asset） | 西昌民族幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2974（wakeup-6.1.06-asset） | 西藏藏医药大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2972（wakeup-6.1.06-asset） | 西藏职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3090（wakeup-6.1.06-asset） | 西安高新科技职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3105（wakeup-6.1.06-asset） | 许昌职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3130（wakeup-6.1.06-asset） | 西藏民族大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3276（wakeup-6.1.06-asset） | 豫北医学院（原新乡医学院三全学院） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2991（wakeup-6.1.06-asset） | 许昌陶瓷职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3230（wakeup-6.1.06-asset） | 豫章师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 921（wakeup-6.1.06-asset） | 贵州交通职业大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 746（wakeup-6.1.06-asset） | 贵州中医药大学时珍学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2973（wakeup-6.1.06-asset） | 西藏警官高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3094（wakeup-6.1.06-asset） | 许昌学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 353（wakeup-52.36-user-attachment） | 贵州医科大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 2990（wakeup-6.1.06-asset） | 许昌电气职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 829（wakeup-6.1.06-asset） | 贵州健康职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 917（wakeup-6.1.06-asset） | 贵州城市职业学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 899（wakeup-6.1.06-asset） | 贵州传媒职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 748（wakeup-6.1.06-asset） | 贵州商学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 922（wakeup-6.1.06-asset） | 贵州医科大学神奇民族医药学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 916（wakeup-6.1.06-asset） | 贵州工业职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 911（wakeup-6.1.06-asset） | 贵州工程职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 750（wakeup-6.1.06-asset） | 贵州工程应用技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 912（wakeup-6.1.06-asset） | 贵州农业职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 908（wakeup-6.1.06-asset） | 贵州应用技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 749（wakeup-6.1.06-asset） | 贵州大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 910（wakeup-6.1.06-asset） | 贵州工贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 871（wakeup-6.1.06-asset） | 贵州开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 906（wakeup-6.1.06-asset） | 贵州护理职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 913（wakeup-6.1.06-asset） | 贵州建设职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 852（wakeup-6.1.06-asset） | 贵州工商职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 789（wakeup-6.1.06-asset） | 贵州民族大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 903（wakeup-6.1.06-asset） | 贵州民用航空职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 752（wakeup-6.1.06-asset） | 贵州师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 905（wakeup-6.1.06-asset） | 贵州机电职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 359（wakeup-52.36-user-attachment） | 贵州理工学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 898（wakeup-6.1.06-asset） | 贵州生态能源职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 909（wakeup-6.1.06-asset） | 贵州电子商务职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 918（wakeup-6.1.06-asset） | 贵州电子信息职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 902（wakeup-6.1.06-asset） | 贵州文化旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 817（wakeup-6.1.06-asset） | 贵州水利水电职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 907（wakeup-6.1.06-asset） | 贵州电子科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 914（wakeup-6.1.06-asset） | 贵州盛华职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 854（wakeup-6.1.06-asset） | 贵州经贸职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 915（wakeup-6.1.06-asset） | 贵州职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 756（wakeup-6.1.06-asset） | 贵州财经大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 901（wakeup-6.1.06-asset） | 贵州航空职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 920（wakeup-6.1.06-asset） | 贵州轻工职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 757（wakeup-6.1.06-asset） | 贵州轻工职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 904（wakeup-6.1.06-asset） | 贵州财经职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 900（wakeup-6.1.06-asset） | 贵州铜仁数据职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 919（wakeup-6.1.06-asset） | 贵州航天职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1584（wakeup-52.36-user-attachment） | 贵阳信息科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 923（wakeup-6.1.06-asset） | 贵州黔南经济学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 828（wakeup-6.1.06-asset） | 贵阳学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3560（wakeup-6.1.06-asset） | 资阳口腔职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 810（wakeup-6.1.06-asset） | 贵阳康养职业大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3561（wakeup-6.1.06-asset） | 资阳环境科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 774（wakeup-6.1.06-asset） | 赣东学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 781（wakeup-6.1.06-asset） | 赣南卫生健康职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 832（wakeup-6.1.06-asset） | 贵州食品工程职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 985（wakeup-6.1.06-asset） | 赣东职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 806（wakeup-6.1.06-asset） | 贵州黔南科技学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 857（wakeup-6.1.06-asset） | 赣南科技学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 987（wakeup-6.1.06-asset） | 赣西科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 986（wakeup-6.1.06-asset） | 赣州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 760（wakeup-6.1.06-asset） | 贵阳职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 438（wakeup-6.1.06-asset） | 赤峰学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 439（wakeup-6.1.06-asset） | 赤峰工业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1816（wakeup-6.1.06-asset） | 辽宁中医药大学杏林学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1797（wakeup-6.1.06-asset） | 辽东学院 | buaa | buaa | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1808（wakeup-6.1.06-asset） | 辽宁何氏医学院 | shuwei_m | shuwei_m | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 440（wakeup-6.1.06-asset） | 赤峰应用技术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 762（wakeup-6.1.06-asset） | 赣南师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1818（wakeup-6.1.06-asset） | 辽宁农业职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 764（wakeup-6.1.06-asset） | 赣州师范高等专科学校 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1819（wakeup-6.1.06-asset） | 辽宁医药职业学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 1839（wakeup-6.1.06-asset） | 辽宁冶金职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1690（wakeup-52.36-user-attachment） | 辽宁大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1822（wakeup-6.1.06-asset） | 辽宁工程技术大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1754（wakeup-6.1.06-asset） | 辽宁师范大学海华学院 | wisedu | wisedu | keep | URL anchor /jwapp/ | 2026-10-17 |
| 1795（wakeup-6.1.06-asset） | 辽宁师范大学 | shuwei_json | shuwei_json | keep | URL anchor wengine/shuwei | 2026-10-17 |
| 1838（wakeup-6.1.06-asset） | 辽宁建筑职业学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1755（wakeup-6.1.06-asset） | 辽宁师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 684（wakeup-52.36-user-attachment） | 辽宁机电职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1840（wakeup-6.1.06-asset） | 辽宁工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1834（wakeup-6.1.06-asset） | 辽宁广告职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1874（wakeup-6.1.06-asset） | 辽宁开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1890（wakeup-6.1.06-asset） | 辽宁理工学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1831（wakeup-6.1.06-asset） | 辽宁理工职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1805（wakeup-6.1.06-asset） | 辽宁省交通高等专科学校 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1780（wakeup-6.1.06-asset） | 辽宁石油化工大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1782（wakeup-6.1.06-asset） | 辽宁经济职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1833（wakeup-6.1.06-asset） | 辽宁生态工程职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 689（wakeup-52.36-user-attachment） | 辽宁职业学院 | aic | aic | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1781（wakeup-6.1.06-asset） | 辽宁科技学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 1835（wakeup-6.1.06-asset） | 辽宁装备制造职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1832（wakeup-6.1.06-asset） | 辽宁财贸学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2216（wakeup-52.36-user-attachment） | 辽宁轨道交通职业学院 1 | lngd | lngd | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1886（wakeup-6.1.06-asset） | 辽宁轨道交通职业学院 | buaa | buaa | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1892（wakeup-6.1.06-asset） | 辽宁石化职业技术学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1837（wakeup-6.1.06-asset） | 辽宁铁道职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1842（wakeup-6.1.06-asset） | 辽宁轻工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1836（wakeup-6.1.06-asset） | 辽河石油职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1812（wakeup-6.1.06-asset） | 辽宁金融职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1844（wakeup-6.1.06-asset） | 辽源职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1784（wakeup-6.1.06-asset） | 辽宁警察学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1841（wakeup-6.1.06-asset） | 辽宁城市建设职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3295（wakeup-6.1.06-asset） | 运城师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1785（wakeup-6.1.06-asset） | 辽阳职业技术学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3222（wakeup-6.1.06-asset） | 运城学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3297（wakeup-6.1.06-asset） | 运城幼儿师范高等专科学校 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 1787（wakeup-6.1.06-asset） | 连云港职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 581（wakeup-6.1.06-asset） | 达州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3158（wakeup-6.1.06-asset） | 运城职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2730（wakeup-6.1.06-asset） | 通辽职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1893（wakeup-6.1.06-asset） | 连云港师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2307（wakeup-6.1.06-asset） | 遂宁职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 584（wakeup-6.1.06-asset） | 达州中医药职业学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 2792（wakeup-6.1.06-asset） | 通化医药健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2309（wakeup-6.1.06-asset） | 遂宁能源职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2721（wakeup-6.1.06-asset） | 通化师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3006（wakeup-6.1.06-asset） | 邢台医学院（原邢台医学高等专科学校） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2308（wakeup-6.1.06-asset） | 遂宁工程职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3386（wakeup-6.1.06-asset） | 遵义师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3563（wakeup-6.1.06-asset） | 遵义职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1376（wakeup-6.1.06-asset） | 邯郸幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1377（wakeup-6.1.06-asset） | 邯郸科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3424（wakeup-6.1.06-asset） | 遵义医药高等专科学校 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 1365（wakeup-6.1.06-asset） | 邯郸职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3005（wakeup-6.1.06-asset） | 邢台新能源职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1071（wakeup-6.1.06-asset） | 邯郸学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2474（wakeup-6.1.06-asset） | 邵阳职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3544（wakeup-6.1.06-asset） | 郑州亚欧交通职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3128（wakeup-6.1.06-asset） | 邢台学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2330（wakeup-6.1.06-asset） | 邵阳学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1275（wakeup-52.36-user-attachment） | 郑州信息科技职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3547（wakeup-6.1.06-asset） | 郑州体育职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3539（wakeup-6.1.06-asset） | 郑州信息工程职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3532（wakeup-6.1.06-asset） | 郑州健康学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3550（wakeup-6.1.06-asset） | 郑州医药健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3543（wakeup-6.1.06-asset） | 郑州卫生健康职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1277（wakeup-52.36-user-attachment） | 郑州商学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3540（wakeup-6.1.06-asset） | 郑州商贸旅游职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1276（wakeup-52.36-user-attachment） | 郑州升达经贸管理学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3549（wakeup-6.1.06-asset） | 郑州城建职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2329（wakeup-6.1.06-asset） | 邵阳工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1280（wakeup-52.36-user-attachment） | 郑州工业应用技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3437（wakeup-6.1.06-asset） | 郑州工业安全职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1282（wakeup-52.36-user-attachment） | 郑州工商学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3565（wakeup-6.1.06-asset） | 郑州市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3360（wakeup-6.1.06-asset） | 郑州师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3361（wakeup-6.1.06-asset） | 郑州幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1279（wakeup-52.36-user-attachment） | 郑州大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 3397（wakeup-6.1.06-asset） | 郑州旅游职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3534（wakeup-6.1.06-asset） | 郑州澍青医学高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1281（wakeup-52.36-user-attachment） | 郑州工程技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3554（wakeup-6.1.06-asset） | 郑州汽车工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3552（wakeup-6.1.06-asset） | 郑州智能科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3536（wakeup-6.1.06-asset） | 郑州电力职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2328（wakeup-6.1.06-asset） | 邵阳通航职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 3538（wakeup-6.1.06-asset） | 郑州理工职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3362（wakeup-6.1.06-asset） | 郑州电力高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3535（wakeup-6.1.06-asset） | 郑州电子信息职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3431（wakeup-6.1.06-asset） | 郑州经贸学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 3364（wakeup-6.1.06-asset） | 郑州职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3545（wakeup-6.1.06-asset） | 郑州电子商务职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3531（wakeup-6.1.06-asset） | 郑州美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3369（wakeup-6.1.06-asset） | 郑州财经学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 3551（wakeup-6.1.06-asset） | 郑州软件职业技术学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3529（wakeup-6.1.06-asset） | 郑州轻工业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3546（wakeup-6.1.06-asset） | 郑州轨道工程职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3363（wakeup-6.1.06-asset） | 郑州科技学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3553（wakeup-6.1.06-asset） | 郑州食品工程职业学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3541（wakeup-6.1.06-asset） | 郑州黄河护理职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3367（wakeup-6.1.06-asset） | 郑州西亚斯学院 | sias_shuwei | sias_shuwei | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3440（wakeup-6.1.06-asset） | 郑州警察学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 466（wakeup-6.1.06-asset） | 郴州思科职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 597（wakeup-6.1.06-asset） | 鄂尔多斯职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 461（wakeup-6.1.06-asset） | 郴州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 596（wakeup-6.1.06-asset） | 鄂尔多斯生态环境职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 594（wakeup-6.1.06-asset） | 鄂尔多斯应用技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 595（wakeup-6.1.06-asset） | 鄂州职业大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1618（wakeup-6.1.06-asset） | 酒泉职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1617（wakeup-6.1.06-asset） | 酒泉职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 305（wakeup-6.1.06-asset） | 重庆三峡医药高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 306（wakeup-6.1.06-asset） | 重庆三峡科技大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 3425（wakeup-6.1.06-asset） | 郑州铁路职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 143（wakeup-52.36-user-attachment） | 重庆人文科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 420（wakeup-6.1.06-asset） | 重庆中医药学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 141（wakeup-52.36-user-attachment） | 重庆三峡职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 487（wakeup-6.1.06-asset） | 重庆五一职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 474（wakeup-6.1.06-asset） | 重庆信息技术职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 426（wakeup-6.1.06-asset） | 重庆传媒职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft; page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 142（wakeup-52.36-user-attachment） | 重庆交通职业学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 418（wakeup-6.1.06-asset） | 重庆健康职业学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 313（wakeup-6.1.06-asset） | 重庆化工职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 318（wakeup-6.1.06-asset） | 重庆城市管理职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 316（wakeup-6.1.06-asset） | 重庆商务职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 503（wakeup-6.1.06-asset） | 重庆冶金成人学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 472（wakeup-6.1.06-asset） | 重庆外语外事学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 411（wakeup-6.1.06-asset） | 重庆城市职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 152（wakeup-52.36-user-attachment） | 重庆大学城市科技学院 | qz_with_node | qz_with_node | keep | declared routable type retained | 2026-10-17 |
| 1985（wakeup-52.36-user-attachment） | 重庆对外经贸学院（备用） | cqu | cqu | keep | declared routable type retained | 2026-10-17 |
| 1686（wakeup-52.36-user-attachment） | 重庆对外经贸学院 | ccibe | ccibe | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 419（wakeup-6.1.06-asset） | 重庆安全技术职业学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 486（wakeup-6.1.06-asset） | 重庆工信职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 153（wakeup-52.36-user-attachment） | 重庆工业职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 154（wakeup-52.36-user-attachment） | 重庆工商大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 475（wakeup-6.1.06-asset） | 重庆工商职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 323（wakeup-6.1.06-asset） | 重庆工商大学派斯学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 427（wakeup-6.1.06-asset） | 重庆工业职业技术大学 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 317（wakeup-6.1.06-asset） | 重庆城市科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 160（wakeup-52.36-user-attachment） | 重庆师范大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 158（wakeup-52.36-user-attachment） | 重庆工程学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 421（wakeup-6.1.06-asset） | 重庆工贸职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 414（wakeup-6.1.06-asset） | 重庆幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 159（wakeup-52.36-user-attachment） | 重庆工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 504（wakeup-6.1.06-asset） | 重庆开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 412（wakeup-6.1.06-asset） | 重庆建筑科技职业学院 | cqu | cqu | keep | declared routable type retained | 2026-10-17 |
| 388（wakeup-6.1.06-asset） | 重庆护理职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 329（wakeup-6.1.06-asset） | 重庆建筑工程职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 476（wakeup-6.1.06-asset） | 重庆应用技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 162（wakeup-52.36-user-attachment） | 重庆文理学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 387（wakeup-6.1.06-asset） | 重庆旅游职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 404（wakeup-6.1.06-asset） | 重庆水利电力职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 398（wakeup-6.1.06-asset） | 重庆智能工程职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 331（wakeup-6.1.06-asset） | 重庆机电职业技术大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 473（wakeup-6.1.06-asset） | 重庆海联职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 480（wakeup-6.1.06-asset） | 重庆电信职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 482（wakeup-6.1.06-asset） | 重庆文化艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 164（wakeup-52.36-user-attachment） | 重庆理工大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 488（wakeup-6.1.06-asset） | 重庆现代制造职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 485（wakeup-6.1.06-asset） | 重庆理工职业学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 334（wakeup-6.1.06-asset） | 重庆电子工程职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 428（wakeup-6.1.06-asset） | 重庆电子科技职业大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 477（wakeup-6.1.06-asset） | 重庆电讯职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 502（wakeup-6.1.06-asset） | 重庆电力职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 425（wakeup-6.1.06-asset） | 重庆科技大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 336（wakeup-6.1.06-asset） | 重庆科创职业学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 481（wakeup-6.1.06-asset） | 重庆经贸职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 333（wakeup-6.1.06-asset） | 重庆电力高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 471（wakeup-6.1.06-asset） | 重庆警察学院 | ziyan | kingo_new | keep | page 教务网络管理系统 + jwweb | 2026-10-17 |
| 478（wakeup-6.1.06-asset） | 重庆能源职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 483（wakeup-6.1.06-asset） | 重庆科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 386（wakeup-6.1.06-asset） | 重庆移通学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 340（wakeup-6.1.06-asset） | 重庆财经学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 341（wakeup-6.1.06-asset） | 重庆财经职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 479（wakeup-6.1.06-asset） | 重庆轻工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 484（wakeup-6.1.06-asset） | 重庆资源与环境保护职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 381（wakeup-6.1.06-asset） | 重庆邮电大学 | cqupt | cqupt | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1612（wakeup-6.1.06-asset） | 金华教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 170（wakeup-52.36-user-attachment） | 重庆第二师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1667（wakeup-6.1.06-asset） | 金肯职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 393（wakeup-6.1.06-asset） | 重庆青年职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1660（wakeup-6.1.06-asset） | 金华职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1578（wakeup-6.1.06-asset） | 金华职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 640（wakeup-52.36-user-attachment） | 金陵科技学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 3442（wakeup-6.1.06-asset） | 钟山职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2790（wakeup-6.1.06-asset） | 铁岭师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2799（wakeup-6.1.06-asset） | 铜仁学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2807（wakeup-6.1.06-asset） | 铁门关职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2802（wakeup-6.1.06-asset） | 铜仁幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1664（wakeup-6.1.06-asset） | 金山职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2800（wakeup-6.1.06-asset） | 铜仁职业技术大学 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2801（wakeup-6.1.06-asset） | 铜仁职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2139（wakeup-6.1.06-asset） | 钦州幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2728（wakeup-6.1.06-asset） | 铜陵学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2803（wakeup-6.1.06-asset） | 铜川职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2791（wakeup-6.1.06-asset） | 铁岭卫生职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1682（wakeup-6.1.06-asset） | 锦州医科大学医疗学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1177（wakeup-52.36-user-attachment） | 银川科技学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2706（wakeup-6.1.06-asset） | 铜陵职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1681（wakeup-6.1.06-asset） | 锦州师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3225（wakeup-6.1.06-asset） | 银川能源学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 399（wakeup-6.1.06-asset） | 长垣烹饪职业技术学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 496（wakeup-6.1.06-asset） | 长安大学兴华学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 343（wakeup-6.1.06-asset） | 长安大学 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 3004（wakeup-6.1.06-asset） | 锡林郭勒职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1330（wakeup-52.36-user-attachment） | 长春人文学院 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 3370（wakeup-6.1.06-asset） | 镇江市高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 446（wakeup-6.1.06-asset） | 长春东方职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 344（wakeup-6.1.06-asset） | 长春中医药大学 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 447（wakeup-6.1.06-asset） | 长春信息技术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 179（wakeup-52.36-user-attachment） | 长春工业大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 350（wakeup-6.1.06-asset） | 长春工程学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 390（wakeup-6.1.06-asset） | 长春光华学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 352（wakeup-6.1.06-asset） | 长春建筑学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 443（wakeup-6.1.06-asset） | 长春工业大学人文信息学院 | cf | cf | keep | page 乘方教务/科技 | 2026-10-17 |
| 499（wakeup-6.1.06-asset） | 长春教育学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 181（wakeup-52.36-user-attachment） | 长春师范大学 | cnu | cnu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 450（wakeup-6.1.06-asset） | 长春数字科技职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 449（wakeup-6.1.06-asset） | 长春早期教育职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 346（wakeup-6.1.06-asset） | 长春健康职业学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 429（wakeup-6.1.06-asset） | 长春汽车职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 442（wakeup-6.1.06-asset） | 长春理工大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 444（wakeup-6.1.06-asset） | 长春职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 357（wakeup-6.1.06-asset） | 长春职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 356（wakeup-6.1.06-asset） | 长春电子科技学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 359（wakeup-6.1.06-asset） | 长春金融高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 498（wakeup-6.1.06-asset） | 长春职工医科大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 445（wakeup-6.1.06-asset） | 长春师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 456（wakeup-6.1.06-asset） | 长江工程职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 373（wakeup-6.1.06-asset） | 长江大学文理学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 374（wakeup-6.1.06-asset） | 长江大学 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 358（wakeup-6.1.06-asset） | 长春财经学院 | lz | lz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 464（wakeup-6.1.06-asset） | 长沙卫生职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 361（wakeup-6.1.06-asset） | 长江师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 455（wakeup-6.1.06-asset） | 长江职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 401（wakeup-6.1.06-asset） | 长沙南方职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 363（wakeup-6.1.06-asset） | 长沙商贸旅游职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 195（wakeup-52.36-user-attachment） | 长沙师范学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 467（wakeup-6.1.06-asset） | 长沙医药健康职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 457（wakeup-6.1.06-asset） | 长沙工业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 501（wakeup-6.1.06-asset） | 长沙工业职工大学 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 366（wakeup-6.1.06-asset） | 长沙民政职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 462（wakeup-6.1.06-asset） | 长沙环境保护职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 463（wakeup-6.1.06-asset） | 长沙电力职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 194（wakeup-52.36-user-attachment） | 长沙学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 500（wakeup-6.1.06-asset） | 长沙教育学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 460（wakeup-6.1.06-asset） | 长沙科技学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 405（wakeup-6.1.06-asset） | 长沙职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 468（wakeup-6.1.06-asset） | 长沙科技职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 400（wakeup-6.1.06-asset） | 长治学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 371（wakeup-6.1.06-asset） | 长沙航空职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 437（wakeup-6.1.06-asset） | 长治幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 436（wakeup-6.1.06-asset） | 长治职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1920（wakeup-6.1.06-asset） | 闽南理工学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 369（wakeup-6.1.06-asset） | 长沙理工大学城南学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 448（wakeup-6.1.06-asset） | 长白山职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 661（wakeup-6.1.06-asset） | 阜康职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1914（wakeup-6.1.06-asset） | 闽北职业技术学院 | zf | zf | keep | page 正方教务管理系统/__VIEWSTATE | 2026-10-17 |
| 435（wakeup-6.1.06-asset） | 长治医学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 637（wakeup-6.1.06-asset） | 阜新矿务局职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1896（wakeup-6.1.06-asset） | 闽南科技学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 617（wakeup-6.1.06-asset） | 阜新高等专科学校 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 640（wakeup-6.1.06-asset） | 阜阳师范大学信息工程学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 641（wakeup-6.1.06-asset） | 阜阳理工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 626（wakeup-6.1.06-asset） | 阜阳师范大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 642（wakeup-6.1.06-asset） | 阜阳科技职业学院 | chaoxing | chaoxing | keep | declared routable type retained | 2026-10-17 |
| 1375（wakeup-52.36-user-attachment） | 防灾科技学院 | cidp | cidp | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2159（wakeup-52.36-user-attachment） | 阳光学院 | ygu | ygu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 660（wakeup-6.1.06-asset） | 防城港职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1895（wakeup-6.1.06-asset） | 闽江学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3298（wakeup-6.1.06-asset） | 阳泉职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3171（wakeup-6.1.06-asset） | 阳江职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 3296（wakeup-6.1.06-asset） | 阳泉师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 112（wakeup-6.1.06-asset） | 阿克苏教育学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 108（wakeup-6.1.06-asset） | 阿克苏职业技术学院 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 109（wakeup-6.1.06-asset） | 阿勒泰职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 52（wakeup-52.36-user-attachment） | 阿坝师范学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 66（wakeup-6.1.06-asset） | 阿坝职业学院 | cf | cf | keep | declared routable type retained | 2026-10-17 |
| 84（wakeup-6.1.06-asset） | 阿拉善职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2488（wakeup-6.1.06-asset） | 陕西中医药大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 110（wakeup-6.1.06-asset） | 阿克苏工业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1789（wakeup-6.1.06-asset） | 陇东学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2269（wakeup-6.1.06-asset） | 陕西兵器工业职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1867（wakeup-6.1.06-asset） | 陇南师范学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2664（wakeup-6.1.06-asset） | 陕西国际商贸学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2639（wakeup-6.1.06-asset） | 陕西学前师范学院 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2302（wakeup-6.1.06-asset） | 陕西农林职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2219（wakeup-6.1.06-asset） | 陕西工业职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2301（wakeup-6.1.06-asset） | 陕西工业职业技术大学 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2667（wakeup-6.1.06-asset） | 陕西交通职业技术学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 963（wakeup-52.36-user-attachment） | 陕西国防工业职业技术学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2298（wakeup-6.1.06-asset） | 陕西工商职业学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2305（wakeup-6.1.06-asset） | 陕西服装工程学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2268（wakeup-6.1.06-asset） | 陕西开放大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2660（wakeup-6.1.06-asset） | 陕西科技大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2661（wakeup-6.1.06-asset） | 陕西理工大学 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2662（wakeup-6.1.06-asset） | 陕西师范大学 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 2657（wakeup-6.1.06-asset） | 陕西能源职业技术学院 | zf_new | zf_new | remap | conflict: zf via URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 2658（wakeup-6.1.06-asset） | 陕西职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2637（wakeup-6.1.06-asset） | 陕西机电职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2428（wakeup-6.1.06-asset） | 陕西警官职业学院 | zf | zf | keep | declared routable type retained | 2026-10-17 |
| 2655（wakeup-6.1.06-asset） | 陕西铁路工程职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2303（wakeup-6.1.06-asset） | 陕西警察学院 | zf | zf | keep | declared routable type retained | 2026-10-17 |
| 2297（wakeup-6.1.06-asset） | 陕西艺术职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2304（wakeup-6.1.06-asset） | 陕西科技大学镐京学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2331（wakeup-6.1.06-asset） | 随州职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 641（wakeup-52.36-user-attachment） | 集宁师范学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2169（wakeup-6.1.06-asset） | 青岛农业大学 | qz_bjfu | qz_bjfu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3190（wakeup-6.1.06-asset） | 雅安职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1802（wakeup-6.1.06-asset） | 陇南师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1340（wakeup-52.36-user-attachment） | 集美大学 | cumtb | cumtb | keep | declared routable type retained | 2026-10-17 |
| 789（wakeup-52.36-user-attachment） | 青岛大学 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2158（wakeup-6.1.06-asset） | 青岛开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2205（wakeup-6.1.06-asset） | 青岛工程职业学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 2299（wakeup-6.1.06-asset） | 陕西青年职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2137（wakeup-6.1.06-asset） | 青岛幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2135（wakeup-6.1.06-asset） | 青岛求实职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2197（wakeup-6.1.06-asset） | 青岛恒星科技学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2194（wakeup-6.1.06-asset） | 青岛电影学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 793（wakeup-52.36-user-attachment） | 青岛城市学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 2138（wakeup-6.1.06-asset） | 青岛航空科技职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2134（wakeup-6.1.06-asset） | 青岛职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2179（wakeup-6.1.06-asset） | 青岛酒店管理职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2181（wakeup-6.1.06-asset） | 青海大学 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2151（wakeup-6.1.06-asset） | 青海农牧科技职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2147（wakeup-6.1.06-asset） | 青海大学昆仑学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2182（wakeup-6.1.06-asset） | 青海师范大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2152（wakeup-6.1.06-asset） | 青海建筑职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2136（wakeup-6.1.06-asset） | 青岛远洋船员职业学院 | wisedu | wisedu | keep | declared routable type retained | 2026-10-17 |
| 2148（wakeup-6.1.06-asset） | 青海理工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2159（wakeup-6.1.06-asset） | 青海开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2207（wakeup-6.1.06-asset） | 青岛港湾职业技术学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2183（wakeup-6.1.06-asset） | 青海民族大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2156（wakeup-6.1.06-asset） | 青海高等职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2149（wakeup-6.1.06-asset） | 青海职业技术大学 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 2157（wakeup-6.1.06-asset） | 青海柴达木职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 80（wakeup-6.1.06-asset） | 鞍山师范学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2272（wakeup-6.1.06-asset） | 韶关市职工大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 85（wakeup-6.1.06-asset） | 鞍山职业技术学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2150（wakeup-6.1.06-asset） | 青海警官职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1072（wakeup-6.1.06-asset） | 韩山师范学院 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 2325（wakeup-6.1.06-asset） | 顺德职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2653（wakeup-6.1.06-asset） | 首都体育学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2327（wakeup-6.1.06-asset） | 顺德职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 975（wakeup-52.36-user-attachment） | 首都医科大学 | cf_new | cf_new | keep | declared routable type retained | 2026-10-17 |
| 2421（wakeup-6.1.06-asset） | 首钢工学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2651（wakeup-6.1.06-asset） | 首都师范大学 | shuwei_json | shuwei_json | keep | declared routable type retained | 2026-10-17 |
| 977（wakeup-52.36-user-attachment） | 首都师范大学科德学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 2979（wakeup-6.1.06-asset） | 香港城市大学（东莞） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2980（wakeup-6.1.06-asset） | 香港中文大学（深圳） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1747（wakeup-52.36-user-attachment） | 香港城市大学 | cityu | cityu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1748（wakeup-52.36-user-attachment） | 香港城市大學 | cityu | cityu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1570（wakeup-52.36-user-attachment） | 马鞍山职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2981（wakeup-6.1.06-asset） | 香港科技大学（广州） | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2975（wakeup-6.1.06-asset） | 香格里拉职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3542（wakeup-6.1.06-asset） | 驻马店幼儿师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3566（wakeup-6.1.06-asset） | 驻马店教育学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 3555（wakeup-6.1.06-asset） | 驻马店农业工程职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1830（wakeup-6.1.06-asset） | 鲁迅美术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 3537（wakeup-6.1.06-asset） | 驻马店职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1445（wakeup-52.36-user-attachment） | 鲁东大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 1907（wakeup-6.1.06-asset） | 马鞍山学院 | classic_eams | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1417（wakeup-6.1.06-asset） | 鹤壁能源化工职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1178（wakeup-6.1.06-asset） | 鹤壁职业技术学院 | shuwei_m | shuwei_m | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1910（wakeup-6.1.06-asset） | 马鞍山师范高等专科学校 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 3284（wakeup-6.1.06-asset） | 鹰潭职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1383（wakeup-6.1.06-asset） | 鹤岗师范高等专科学校 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1077（wakeup-6.1.06-asset） | 黄山职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1073（wakeup-6.1.06-asset） | 黄冈师范学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1074（wakeup-6.1.06-asset） | 黄冈职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1309（wakeup-52.36-user-attachment） | 黄河交通学院 | classic_eams | classic_eams | keep | declared routable type retained | 2026-10-17 |
| 1400（wakeup-6.1.06-asset） | 黄山健康职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx; page 教学管理信息服务平台/zftal-ui | 2026-10-17 |
| 1078（wakeup-6.1.06-asset） | 黄河科技学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1192（wakeup-6.1.06-asset） | 黄淮学院 | kingo_new | kingo_new | keep | declared routable type retained | 2026-10-17 |
| 1428（wakeup-6.1.06-asset） | 黄冈科技职业学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1080（wakeup-6.1.06-asset） | 黑龙江中医药大学 | qz | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 1406（wakeup-6.1.06-asset） | 黄河水利职业技术大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1124（wakeup-6.1.06-asset） | 黑龙江交通职业技术学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1382（wakeup-6.1.06-asset） | 黑龙江农业职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1658（wakeup-52.36-user-attachment） | 黑龙江农业工程职业技术大学 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1198（wakeup-6.1.06-asset） | 黑龙江八一农垦大学 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1083（wakeup-6.1.06-asset） | 黑龙江外国语学院 | qz | qz | keep | page 强智科技/教学一体化/qzdatasoft.com | 2026-10-17 |
| 1468（wakeup-6.1.06-asset） | 黑龙江农垦管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1081（wakeup-6.1.06-asset） | 黑龙江农业经济职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1082（wakeup-6.1.06-asset） | 黑龙江农垦职业学院 | zf_new | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 551（wakeup-52.36-user-attachment） | 黑龙江大学 | hitsz | hitsz | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1222（wakeup-6.1.06-asset） | 黑龙江工程学院昆仑旅游学院 | kingo_new | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 552（wakeup-52.36-user-attachment） | 黑龙江工业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1471（wakeup-6.1.06-asset） | 黑龙江开放大学 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1098（wakeup-6.1.06-asset） | 黑河学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1389（wakeup-6.1.06-asset） | 黑龙江幼儿师范高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1387（wakeup-6.1.06-asset） | 黑龙江民族职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1381（wakeup-6.1.06-asset） | 黑龙江林业职业技术学院 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 1385（wakeup-6.1.06-asset） | 黑龙江生态工程职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 553（wakeup-52.36-user-attachment） | 黑龙江工商学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1089（wakeup-6.1.06-asset） | 黑龙江生物科技职业学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1470（wakeup-6.1.06-asset） | 黑龙江省教育学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1091（wakeup-6.1.06-asset） | 黑龙江职业学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 555（wakeup-52.36-user-attachment） | 黑龙江建筑职业技术学院 | hit | hit | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1386（wakeup-6.1.06-asset） | 黑龙江能源职业学院 | chaoxing | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1092（wakeup-6.1.06-asset） | 黑龙江财经学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 1380（wakeup-6.1.06-asset） | 黑龙江艺术职业学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2185（wakeup-6.1.06-asset） | 黔南民族幼儿师范高等专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2204（wakeup-6.1.06-asset） | 黔南民族师范学院 | qz | qz | keep | declared routable type retained | 2026-10-17 |
| 1469（wakeup-6.1.06-asset） | 黑龙江省经济管理干部学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2141（wakeup-6.1.06-asset） | 黔南民族职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2189（wakeup-6.1.06-asset） | 齐鲁理工学院 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 2140（wakeup-6.1.06-asset） | 黔南民族医学高等专科学校 | ziyan | ziyan | unknown | auth/proxy shell only; no system anchor | 2026-10-17 |
| 805（wakeup-52.36-user-attachment） | 齐齐哈尔大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |
| 803（wakeup-52.36-user-attachment） | 齐鲁师范学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2142（wakeup-6.1.06-asset） | 黔西南民族职业技术学院 | ziyan | ziyan | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 2198（wakeup-6.1.06-asset） | 齐齐哈尔工程学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2193（wakeup-6.1.06-asset） | 齐齐哈尔医学院 | qmu | qmu | unknown | no decisive evidence; stays unroutable | 2026-10-17 |
| 1791（wakeup-6.1.06-asset） | 龙岩学院 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 2196（wakeup-6.1.06-asset） | 齐齐哈尔高等师范专科学校 | zf_new | zf_new | keep | declared routable type retained | 2026-10-17 |
| 557（wakeup-52.36-user-attachment） | 黑龙江科技大学 | urp_new | urp_new | keep | declared routable type retained | 2026-10-17 |

## 保持不可路由的家族

- `ziyan` umbrella 声明 1068 条：绝大多数无超星/强智/正方/KINGOSOFT 锚点，维持不可路由；其中有页面/URL 强特征的行已按上表逐条改写。
- 学校私有协议（aic/lz/cityu/buaa/hitsz 等）：无通用协议正向证据，维持不可路由；后续应补自定义解析器，而不是伪装成通用协议。
- CAS/authserver 纯壳与 webvpn 壳 URL：永不作为操作值依据，也不构成协议证据。

注意：协议可路由≠条目入目录。协议裁决之后仍有所有权闸门与域名碰撞闸门（`catalog_ownership.apply_ownership` / `_candidate_poisons`）决定 URL 是否进入 `schools.json`：owner 未证明与被 HEAD 唯一行占住主机/域名（会破坏按 URL 反查学校的 fail-closed 定位）的行被拦截。逐条依据表见 [证据生成报告](EVIDENCE_GENERATION_REPORT.md) 的“域名碰撞闸门”一节。

完整 2499 行逐行证据（含未变更行为何可信）在 `scripts/data/adjudication-log.jsonl`。
