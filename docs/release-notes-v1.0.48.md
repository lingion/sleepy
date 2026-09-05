## v1.0.48

Twenty universities joined direct JW import. WHUT came first, then a 211 sweep: Shanghai Jiao Tong, Wuhan University, UESTC, Nanjing University of Aeronautics, Hunan University, Xiamen University, and thirteen more. The count is now 179. One real fix too: APK downloads now fall back to the mirror, because until now only the release-info check did. 5 commits.

### New

**Twenty more schools, 179 total**

Direct import now covers Wuhan University of Technology plus nineteen 211 schools: Shanghai Jiao Tong University, Wuhan University, Shanghai University, South China Normal University, Renmin University of China, Hunan University, Beijing University of Chinese Medicine, East China University of Political Science and Law, Shanghai International Studies University, Donghua University, Xiamen University, Yanbian University, Shihezi University, Anhui University, China University of Mining and Technology (Beijing), University of Electronic Science and Technology of China, Shanghai University of Finance and Economics, Hunan Normal University, and Nanjing University of Aeronautics and Astronautics. Each protocol was verified against the school's actual login page.

Four groups needed new code:

- UESTC, SUFE, Hunan Normal, and NUAA run the old Jinzhi EAMS. There the timetable is not in the HTML at all; it sits in JavaScript blocks inside the page (`new TaskActivity(...)` and friends), and the table you see is filled in by script after load. The new parser reads those blocks directly. It also handles schools with 13 periods instead of 12, teacher lists given as expressions, and course names containing commas.
- Anhui University and CUMT (Beijing) run the same supwisdom EAMS5 platform as HFUT under a different URL prefix. The fetch code now detects the prefix instead of hardcoding one.
- Wuhan University of Technology uses a Jinzhi variant (kcbcxby endpoint, DM-prefixed periods) with its own parser. Four upstream projects documented this protocol; all four were cross-checked.
- The rest plug into the existing Zhengfang/Jinzhi/Qiangzhi parsers.

Thanks to shiguang_warehouse (MIT) for the WHUT kcbcxby adapter and the hunnu/uestc/hpu EAMS adapters, and to WakeupSchedule_Kotlin (Apache-2.0) for its classic EAMS import implementation. Both are credited in the app.

### Fixed

**APK downloads fall back to the mirror**

The in-app updater used to fail with `failed to connect to github.com/...: port 443, timeout 15000ms` on bad connections, seconds after a successful update check. The mirror fallback only covered fetching release info. Once the info arrived, the download link still pointed straight at github.com, and nothing retried it. Download URLs now go to the mirror first; if the mirror fails, the downloader retries github.com directly. A download you cancel stays cancelled and does not trigger the fallback.

**A dozen new campuses now suggest campus network instead of reporting "no courses"**

Renmin, Hunan University, Donghua, Anhui, CUMT (Beijing), Yanbian, Shihezi, UESTC, SUFE, Hunan Normal, NUAA, and SCNU are mostly unreachable from off campus. Import there used to say "no courses this semester", which reads like you picked the wrong term. It now tells you to connect to campus network or VPN.

### Tests

The classic EAMS parser has 14 tests of its own (comma-in-name argument splitting, week-bitmap indexing cross-checked against four independent sources, teacher-ID fallback, cancelled-class blocks, empty pages). Full suite: 941, zero failures. versionCode: 49.

---

## v1.0.48

这个版本收录了二十所。武汉理工先落地,接着是 211 批量普查:上海交大、武汉大学、电子科大、南航、湖南大学、厦大等十九所,总数到 179。另有一个真修复:应用内下载 APK 会回退镜像了——之前只有检查更新走镜像。5 个提交。

### 新增

**新收录二十校,共 179 所**

教务直连新增 武汉理工大学,以及十九所 211:上海交通大学、武汉大学、上海大学、华南师范大学、中国人民大学、湖南大学、北京中医药大学、华东政法大学、上海外国语大学、东华大学、厦门大学、延边大学、石河子大学、安徽大学、中国矿业大学(北京)、电子科技大学、上海财经大学、湖南师范大学、南京航空航天大学。每所的协议类型都对着该校实际登录页核实过。

四组要写新代码:

- 电子科大、上财、湖南师大、南航用的是老版金智 EAMS。这种系统课表根本不在 HTML 里,而是埋在页面内嵌的 JavaScript 块中(`new TaskActivity(...)` 一类),你看到的表格是脚本加载后填的。新解析器直接读这些块。13 节课的学校、教师名以表达式给出的、课名带逗号的,都能处理。
- 安徽大学、矿大(北京)和合工大同属 supwisdom EAMS5 平台,URL 前缀不同。抓取代码现在自己识别前缀,不再写死。
- 武汉理工大学是金智变体(kcbcxby 接口、DM 开头的节次),配了专属解析器。四个上游项目记录过这套协议,逐一交叉核对过。
- 其余接入现有正方/金智/强智解析器。

感谢 shiguang_warehouse (MIT) 的 WHUT kcbcxby 适配器和 hunnu/uestc/hpu EAMS 适配器,以及 WakeupSchedule_Kotlin (Apache-2.0) 的经典 EAMS 导入实现。应用内关于页已致谢。

### 修复

**APK 下载会回退镜像了**

网络不好时,应用内更新会报 `failed to connect to github.com/...: port 443, timeout 15000ms`,而几秒前检查更新还是成功的。镜像回退只覆盖了"拉版本信息"这一步,信息拿到后下载地址照样直指 github.com,没有任何重试。现在下载地址先走镜像,镜像失败再回退 github.com 直连。你自己取消的下载就是取消了,不会触发回退。

**十二所新校区解析失败时提示连校园网,不再报"没有课程"**

人大、湖南大学、东华、安大、矿大(北京)、延边、石河子、电子科大、上财、湖南师大、南航、华南师大 校外大多访问不通。之前导入失败会说"本学期暂无课程",看着像你选错了学期。现在会提示连接校园网或 VPN。

### 测试

仅经典 EAMS 解析器就有 14 个测试(课名带逗号的参数切分、周次位图下标经四个独立来源交叉核对、教师 ID 回退、停课跳过、空页面)。全套 941 个,零失败。versionCode: 49。
