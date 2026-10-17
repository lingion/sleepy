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

- 批次行 2499：keep 1522；remap（含回退与升级）171；unknown（维持不可路由）806；frozen 基线行 170
- 最终文件相对 HEAD 的类型变化：100 条（其中保留工作树 remap：81；回退工作树 remap：0；其余为证据升级）
- 裁决后仍不可路由（pending）：938 条；可路由且 URL 可用：1726 条

## 逐条依据表（所有最终类型 ≠ HEAD 声明类型的批次行）

| sourceRow | name | 原类型(HEAD) | 新类型 | 裁决 | 证据/理由 | 日期 |
|---|---|---|---|---|---|---|
| 2154（wakeup-6.1.06-asset） | 七台河职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2379（wakeup-6.1.06-asset） | 上海城建职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2389（wakeup-6.1.06-asset） | 上海海事大学 | ziyan | shuwei_json | remap | upgraded by URL anchor wengine/shuwei | 2026-10-17 |
| 2380（wakeup-6.1.06-asset） | 上海行健职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1827（wakeup-6.1.06-asset） | 临汾职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3255（wakeup-6.1.06-asset） | 云南三鑫职业技术学院 | ziyan | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3251（wakeup-6.1.06-asset） | 云南外事外语职业学院 | ziyan | kingo_new | remap | upgraded by page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3257（wakeup-6.1.06-asset） | 云南新兴职业学院 | ziyan | kingo_new | remap | upgraded by page KINGOSOFT（青果品牌） | 2026-10-17 |
| 3252（wakeup-6.1.06-asset） | 云南旅游职业学院 | ziyan | wisedu | remap | upgraded by URL anchor /jwapp/ | 2026-10-17 |
| 3250（wakeup-6.1.06-asset） | 云南财经职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1489（wakeup-6.1.06-asset） | 伊犁职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1724（wakeup-6.1.06-asset） | 克孜勒苏职业技术学院 | ziyan | cf | remap | upgraded by page 乘方教务/科技 | 2026-10-17 |
| 2019（wakeup-6.1.06-asset） | 内蒙古民族幼儿师范高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 241（wakeup-6.1.06-asset） | 北京交通运输职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 240（wakeup-6.1.06-asset） | 北京培黎职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 239（wakeup-6.1.06-asset） | 北京科技职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2007（wakeup-6.1.06-asset） | 南昌健康职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2004（wakeup-6.1.06-asset） | 南阳科技职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2995（wakeup-6.1.06-asset） | 厦门东海职业技术学院 | ziyan | kingo_new | remap | upgraded by page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2119（wakeup-6.1.06-asset） | 培黎职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2806（wakeup-6.1.06-asset） | 塔里木职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 577（wakeup-6.1.06-asset） | 大庆职业学院 | ziyan | zf | remap | upgraded by URL anchor default2/xskbcx.aspx | 2026-10-17 |
| 2780（wakeup-6.1.06-asset） | 天津现代职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2781（wakeup-6.1.06-asset） | 天津轻工职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1990（wakeup-6.1.06-asset） | 宁夏葡萄酒与防沙治沙职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 29（wakeup-52.36-user-attachment） | 安徽医科大学 | xsyu_shuwei | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 106（wakeup-6.1.06-asset） | 安顺职业技术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2352（wakeup-6.1.06-asset） | 宿州航空职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2343（wakeup-6.1.06-asset） | 山东中医药高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2336（wakeup-6.1.06-asset） | 山东文化艺术职业学院 | ziyan | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2342（wakeup-6.1.06-asset） | 山东旅游职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2339（wakeup-6.1.06-asset） | 山东海事职业学院 | ziyan | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2337（wakeup-6.1.06-asset） | 山东特殊教育职业学院 | ziyan | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2404（wakeup-6.1.06-asset） | 山西水利职业技术学院 | ziyan | kingo_new | remap | upgraded by page KINGOSOFT（青果品牌） | 2026-10-17 |
| 2115（wakeup-6.1.06-asset） | 平顶山职业技术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 929（wakeup-6.1.06-asset） | 广西卫生职业技术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3292（wakeup-6.1.06-asset） | 扬州职业技术大学 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2965（wakeup-6.1.06-asset） | 新疆和田学院（原和田师范专科学校） | ziyan | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 3015（wakeup-6.1.06-asset） | 新疆能源职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2390（wakeup-6.1.06-asset） | 松原职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1856（wakeup-6.1.06-asset） | 林州建筑职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 767（wakeup-6.1.06-asset） | 桂林医科大学 | ziyan | cf | remap | upgraded by page 乘方教务/科技 | 2026-10-17 |
| 1926（wakeup-6.1.06-asset） | 梅河口康美职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2795（wakeup-6.1.06-asset） | 桐城师范高等专科学校 | ziyan | wisedu | remap | upgraded by page 金智 | 2026-10-17 |
| 495（wakeup-6.1.06-asset） | 楚雄医药高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2834（wakeup-6.1.06-asset） | 武汉体育学院体育科技学院 | ziyan | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2839（wakeup-6.1.06-asset） | 武汉民政职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 256（wakeup-6.1.06-asset） | 毕节幼儿师范高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 254（wakeup-6.1.06-asset） | 毕节职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1668（wakeup-6.1.06-asset） | 江南影视艺术职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1633（wakeup-6.1.06-asset） | 江西传媒职业学院 | ziyan | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1652（wakeup-6.1.06-asset） | 江西医学高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1642（wakeup-6.1.06-asset） | 江西工程职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1639（wakeup-6.1.06-asset） | 江西新能源科技职业学院 | ziyan | zf_new | remap | upgraded by URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1631（wakeup-6.1.06-asset） | 江西水利职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1370（wakeup-6.1.06-asset） | 河北对外经贸职业学院 | ziyan | kingo_new | keep | page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1484（wakeup-6.1.06-asset） | 河南物流职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1857（wakeup-6.1.06-asset） | 洛阳商业职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3320（wakeup-6.1.06-asset） | 浙江师范大学 | ziyan | zf_new | remap | upgraded by URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 1460（wakeup-6.1.06-asset） | 海南体育职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1462（wakeup-6.1.06-asset） | 海南东方新丝路职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1421（wakeup-6.1.06-asset） | 湖北美术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2832（wakeup-6.1.06-asset） | 潍坊食品科技职业学院 | ziyan | qz | remap | upgraded by URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2831（wakeup-6.1.06-asset） | 潍坊工商职业学院 | ziyan | cf | remap | upgraded by page 乘方教务/科技 | 2026-10-17 |
| 3280（wakeup-6.1.06-asset） | 烟台工程职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1625（wakeup-6.1.06-asset） | 焦作新材料职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 894（wakeup-6.1.06-asset） | 甘肃工业职业技术大学 | ziyan | qz | remap | upgraded by URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2124（wakeup-6.1.06-asset） | 盘锦职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1912（wakeup-6.1.06-asset） | 磨料磨具工业职工大学 | ziyan | zf_new | remap | upgraded by URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 652（wakeup-6.1.06-asset） | 福州黎明职业技术学院 | ziyan | kingo_new | remap | upgraded by page KINGOSOFT（青果品牌） | 2026-10-17 |
| 1897（wakeup-6.1.06-asset） | 绵阳飞行职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2829（wakeup-6.1.06-asset） | 芜湖航空职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2357（wakeup-6.1.06-asset） | 苏州百年职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1624（wakeup-6.1.06-asset） | 荆州理工职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 1111（wakeup-52.36-user-attachment） | 西南医科大学 | qz_single_node | qz | keep | URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft | 2026-10-17 |
| 2958（wakeup-6.1.06-asset） | 西安医学高等专科学校 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2991（wakeup-6.1.06-asset） | 许昌陶瓷职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 911（wakeup-6.1.06-asset） | 贵州工程职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 905（wakeup-6.1.06-asset） | 贵州机电职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 915（wakeup-6.1.06-asset） | 贵州职业技术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 985（wakeup-6.1.06-asset） | 赣东职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1795（wakeup-6.1.06-asset） | 辽宁师范大学 | shuwei_m | shuwei_json | keep | URL anchor wengine/shuwei | 2026-10-17 |
| 1844（wakeup-6.1.06-asset） | 辽源职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2307（wakeup-6.1.06-asset） | 遂宁职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 584（wakeup-6.1.06-asset） | 达州中医药职业学院 | ziyan | cf | remap | upgraded by page 乘方教务/科技 | 2026-10-17 |
| 3551（wakeup-6.1.06-asset） | 郑州软件职业技术学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 3553（wakeup-6.1.06-asset） | 郑州食品工程职业学院 | ziyan | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 485（wakeup-6.1.06-asset） | 重庆理工职业学院 | ziyan | cf | remap | upgraded by page 乘方教务/科技 | 2026-10-17 |
| 425（wakeup-6.1.06-asset） | 重庆科技大学 | uestc_shuwei | classic_eams | keep | URL anchor eams login(.action)/loginExt/homeExt | 2026-10-17 |
| 2800（wakeup-6.1.06-asset） | 铜仁职业技术大学 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2801（wakeup-6.1.06-asset） | 铜仁职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 446（wakeup-6.1.06-asset） | 长春东方职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 450（wakeup-6.1.06-asset） | 长春数字科技职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 449（wakeup-6.1.06-asset） | 长春早期教育职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 3298（wakeup-6.1.06-asset） | 阳泉职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 2301（wakeup-6.1.06-asset） | 陕西工业职业技术大学 | ziyan | zf_new | keep | URL anchor jwglxt/xtgl/login_slogin/kbcx | 2026-10-17 |
| 2138（wakeup-6.1.06-asset） | 青岛航空科技职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 85（wakeup-6.1.06-asset） | 鞍山职业技术学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1385（wakeup-6.1.06-asset） | 黑龙江生态工程职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |
| 1386（wakeup-6.1.06-asset） | 黑龙江能源职业学院 | ziyan | chaoxing | keep | URL anchor chaoxing host/xsd/queryKbForGrdb | 2026-10-17 |

## 保持不可路由的家族

- `ziyan` umbrella 声明 1068 条：绝大多数无超星/强智/正方/KINGOSOFT 锚点，维持不可路由；其中有页面/URL 强特征的行已按上表逐条改写。
- 学校私有协议（aic/lz/cityu/buaa/hitsz 等）：无通用协议正向证据，维持不可路由；后续应补自定义解析器，而不是伪装成通用协议。
- CAS/authserver 纯壳与 webvpn 壳 URL：永不作为操作值依据，也不构成协议证据。

完整 2499 行逐行证据（含未变更行为何可信）在 `scripts/data/adjudication-log.jsonl`。
