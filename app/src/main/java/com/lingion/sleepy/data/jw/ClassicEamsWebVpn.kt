package com.lingion.sleepy.data.jw

/**
 * NBCC (宁波城市职业技术学院) 形态B抓取链共享常量与纯函数 — fix-design §4 单一真源。
 *
 * 协议族: 经典正方 EAMS (JwClassicEamsParser 同族, HPU/LIXIN/NWUPL/ZYIP 先例)。
 * WebView 内同源 fetch (复刻 EAMS5_FETCH_JS 骨架, HFUT 经验移植): 页面文本提参 →
 * ≤3 组候选体 POST 相对 endpoint → `new TaskActivity(` 判据即停, 解码唯一落点 parser
 * (JS 不解码, unitCount regex 不在 JS 出现 — SOP v1.9-② 口径)。
 *
 * 跨语言 invariant: 本文件 §4 pattern 与 JwWebViewLoginScreen.kt 的
 * CLASSIC_EAMS_FETCH_JS 内 regex literal 逐字符相等, 由
 * NbccClassicEamsWebViewContractTest 锁死 (SOP 铁律3)。
 *
 * 双盐留档 (铁律 §0.3, **零代码引用** — 形态B 两盐均零落地):
 *   NBCC_URL_HEX_SALT = "wrdvpnisthebest!" — 深信服 webvpn URL hex 编解码盐 (AES-128-CFB,
 *     key=iv, segment128; 我方 dump 实解 + 调研 agent + 控制器复现三源锁定)。
 *     形态A(原生登录链)复活时接 URL 编解码; WebView 同源相对 fetch 不需要。
 *   NBCC_PORTAL_PWD_SALT = "wrdvpnisawesome!" — 新门户登录页 JS 提交前加密 password 字段盐
 *     (公开前端常量, 非凭据; 形态A 复活时接表单提交)。
 *   二者用途永不互换; 盐是按校部署常量, 禁跨校写死 (NEU 异盐先例)。
 */
object ClassicEamsWebVpn {

    /** fetch endpoint (相对当前文档目录解析, Q1 门户 hex 前缀 / Q2 vhost 直链均免盐保留前缀)。 */
    val CLASSIC_EAMS_ACTION_PATH = """courseTableForStd!courseTable.action"""

    /** URL/表单 frame 指纹 (小写比较)。 */
    val CLASSIC_EAMS_PAGE_ANCHOR = """courseTableForStd"""

    /**
     * 南开 (issue #236) 自动动线入口页路径 (相对当前文档解析, jxy18100 scrape_now.py +
     * zawnpn Dart 链双源实测 = 经典 EAMS 课表入口页, 含 addInput ids + semesterCalendar)。
     * 仅 host 命中 CLASSIC_EAMS_NANKAI_HOST_RE 时启用; NBCC 等其余校零影响。
     */
    val CLASSIC_EAMS_ENTRY_PATH = """courseTableForStd.action"""

    /** 南开自动动线 host 判据 (镜像 CLASSIC_EAMS_FETCH_JS 内同名 regex literal)。 */
    val CLASSIC_EAMS_NANKAI_HOST_RE = """eamis\.nankai\.edu\.cn"""

    /** 成功判据 — 响应文本含此串即课表数据到达 (JwClassicEamsParser 状态机同锚)。 */
    val CLASSIC_EAMS_TASK_ACTIVITY_NEEDLE = """new TaskActivity("""

    /** 学号首选: bg.form.addInput(form,"ids","NNN") — 课表表单作用域内绑定 (Dart _entryIdsRe 实测形态)。 */
    val CLASSIC_EAMS_IDS_RE = """addInput\(\s*form\s*,\s*["']ids["']\s*,\s*["'](\d+)["']"""

    /** 学号隐藏域兜底 (§4 表原文 `.*?` 行内匹配; JS/Kotlin 默认 . 均不跨行)。 */
    val CLASSIC_EAMS_IDS_INPUT_RE = """<[^>]*\bname\s*=\s*["']ids["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']"""

    /** 学期首选: semesterCalendar({…,value:"NNN"}) — Dart 实测隐藏域常为空。 */
    val CLASSIC_EAMS_SEMESTER_CAL_RE = """semesterCalendar\(\{[^}]*value\s*:\s*["'](\d+)["']"""

    /** 学期兜底。 */
    val CLASSIC_EAMS_SEMESTER_INPUT_RE = """<[^>]*\bname\s*=\s*["']semester\.id["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']"""

    /** 项目隐藏域。 */
    val CLASSIC_EAMS_PROJECT_RE = """<[^>]*\bname\s*=\s*["']project\.id["'][^>]*>.*?\bvalue\s*=\s*["'](\d+)["']"""

    /** 候选体模板: 含 project 全形态 (§4 表原文, 去 project 变体由 buildCandidates 派生)。 */
    val CLASSIC_EAMS_PARAM_TEMPLATE = """ignoreHead=1&setting.kind=std&startWeek=&project.id=%s&semester.id=%s&ids=%s"""

    /** project 缺省 (Dart defaultProjectId — NBCC 真实入口页 project select 无数值属性)。 */
    val CLASSIC_EAMS_DEFAULT_PROJECT_ID = "1"

    /** 会话失效判据 (302 跟随后最终 URL)。 */
    val CLASSIC_EAMS_LOGIN_URL_RE = """/login(\?|$)|/users/sign_in"""

    /**
     * 桌面 UA 初值 host 白名单 (parse host 精确相等, 非正则 — 与 CLASSIC_EAMS_NANKAI_HOST_RE
     * 用途不同不可互换: 后者镜像 JS 侧 fetch 动线 host 判据)。
     */
    val CLASSIC_EAMS_DESKTOP_UA_HOSTS = setOf("webvpn.nbcc.edu.cn", "eamis.nankai.edu.cn")

    private val idsRe = Regex(CLASSIC_EAMS_IDS_RE)
    private val idsInputRe = Regex(CLASSIC_EAMS_IDS_INPUT_RE)
    private val semesterCalRe = Regex(CLASSIC_EAMS_SEMESTER_CAL_RE)
    private val semesterInputRe = Regex(CLASSIC_EAMS_SEMESTER_INPUT_RE)
    private val projectRe = Regex(CLASSIC_EAMS_PROJECT_RE)
    private val loginUrlRe = Regex(CLASSIC_EAMS_LOGIN_URL_RE)

    /**
     * 学号提取: addInput 首选, name="ids" 隐藏域兜底。
     * 入参必须是课表表单作用域文本 (§3.1-② 反串表单纪律 — Dart 实测教训:
     * 整段拼接取第一个 addInput 会抓到成绩查询表单的学号)。
     */
    fun extractIds(scopeText: String): String? =
        idsRe.find(scopeText)?.groupValues?.get(1)
            ?: idsInputRe.find(scopeText)?.groupValues?.get(1)

    /** 学期提取: semesterCalendar 首选, name="semester.id" 隐藏域兜底 (真实页常为空值)。 */
    fun extractSemesterId(scopeText: String): String? =
        semesterCalRe.find(scopeText)?.groupValues?.get(1)
            ?: semesterInputRe.find(scopeText)?.groupValues?.get(1)

    /** 学期隐藏域单源提取 (buildCandidates 第三候选专用, 不走 calendar 优先)。 */
    fun extractSemesterHidden(scopeText: String): String? =
        semesterInputRe.find(scopeText)?.groupValues?.get(1)

    /** 项目提取: name="project.id" 隐藏域, 缺省 CLASSIC_EAMS_DEFAULT_PROJECT_ID。 */
    fun extractProjectId(scopeText: String): String? =
        projectRe.find(scopeText)?.groupValues?.get(1)

    /**
     * 候选 ≤3 组参数体, 与 Dart buildCourseTableParamsCandidates 同序 (设计 §3.1-③):
     *   slot1 = project + semesterCalendar 源   slot2 = 去 project + calendar 源
     *   slot3 = project + 隐藏域学期源          slot4 = 去 project + 隐藏域学期源
     * 去重后取前 3。ids 缺失 → 空列表 (不猜, §3.1-②); semester 双源皆空 → 空列表。
     */
    fun buildCandidates(scopeText: String): List<String> {
        val ids = extractIds(scopeText) ?: return emptyList()
        val semCal = extractSemesterId(scopeText)
        val semHidden = extractSemesterHidden(scopeText)
        val project = extractProjectId(scopeText) ?: CLASSIC_EAMS_DEFAULT_PROJECT_ID
        val semesters = listOfNotNull(semCal, semHidden?.takeIf { it != semCal })
        val out = LinkedHashSet<String>()
        for (sem in semesters) {
            out.add(CLASSIC_EAMS_PARAM_TEMPLATE.format(project, sem, ids))
            out.add(withoutProjectParam(sem, ids))
        }
        return out.take(3)
    }

    /** PARAM_TEMPLATE 去 project 段变体 (§4 表内括注原文形态)。 */
    fun withoutProjectParam(semesterId: String, ids: String): String =
        CLASSIC_EAMS_PARAM_TEMPLATE
            .replace("project.id=%s&", "")
            .format(semesterId, ids)

    /** 会话失效判据 (镜像 CLASSIC_EAMS_LOGIN_URL_RE, fetch 每候选 r.url 检查)。 */
    fun isLoginUrl(url: String?): Boolean = url != null && loginUrlRe.containsMatchIn(url)

    /**
     * 桌面 UA 初值 (设计 §3.5): 判据 = type==classic_eams ∧ enableFetch ∧
     * hostOf(url) ∈ {webvpn.nbcc.edu.cn, eamis.nankai.edu.cn}。
     * NBCC 门户 Q1 形态: 深信服把移动 UA 送进钉钉专用残废 SPA (issue #227 战役)。
     * 南开 eamis (issue #236): zzask77 dump 7/7 请求全带 Android WebView 指纹而
     * home.action 返回 head 空壳; WakeUp 官方 6.6.2 APK 反汇编证实同业同样把裸
     * WebView 指纹当威胁 — is_webview_desktop_mode 双态均经 setUserAgentString
     * 主动剥离 wv 标记 (桌面态整换 Windows Chrome UA), 本白名单即该形态复用。
     * NBCC-Q2 直链 (jwgl.webvpn.nbcc.cn) 本身就是 PC 教务页, 无需桌面 UA 切换;
     * 其余校 enableFetch 恒 false 或 host 不匹配 → false, 零影响。
     */
    fun initialDesktopUa(school: JwSchoolInfo): Boolean =
        school.type == JwProtocol.TYPE_CLASSIC_EAMS && school.enableFetch &&
            hostOf(school.url) in CLASSIC_EAMS_DESKTOP_UA_HOSTS

    private fun hostOf(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        if (uri.scheme?.lowercase() in setOf("http", "https")) uri.host?.lowercase() else null
    }.getOrNull()
}

/** 便捷入口 (照 Eams5PathPrefix.kt 风格)。 */
fun initialDesktopUa(school: JwSchoolInfo): Boolean = ClassicEamsWebVpn.initialDesktopUa(school)
