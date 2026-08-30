package com.lingion.sleepy.ui.screen.imports

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * T7 — WebView 抓取结果的 frame 级分析与决策。纯 JVM, 不 import android.*。
 *
 * 分层: WebView 内 CAPTURE_FRAMES_JS 只负责"把每个可达 frame 的 outerHTML + 路径 + 跨域标记"
 * 按 JSON 回传(Kotlin 信任它的输出, 不在 JS 做决策); 本文件在 JVM 上重建 frame 树、
 * 扫锚点、做登录页指纹判定、ready-check 重试决策。全部逻辑可被 JUnit4 直接构造 JSON 驱动。
 */
enum class FrameCaptureStatus {
    /** 命中含锚点 frame 且(调用方喂 parser 后)课程数 > 0 */
    OK,
    /** 命中含锚点 frame 但 0 门课 — 容器在, 真无课/学期选错 */
    EMPTY_SEMESTER,
    /** 所有可达 frame 均无锚点且无登录指纹 — 页面不是课表页/协议不匹配 */
    WRONG_PAGE,
    /** 存在跨域 frame 且其余 frame 均无锚点 — 课表在内层但同源策略阻挡 */
    CROSS_DOMAIN_IFRAME_BLOCKED,
    /** 延迟渲染容器存在但内容未填充, 重试 3 次仍空 */
    CONTAINER_EMPTY_AFTER_DELAY,
    /** 任意可达 frame 命中登录页指纹 — 会话过期, 用户需重新登录 */
    SESSION_EXPIRED,
    /** iframe src 仍是 about:blank/空 且 body 空壳, 重试耗尽 — 课表框架还没开始导航 */
    IFRAME_NAV_PENDING,
    /** JS 回传解析失败等兜底 */
    UNKNOWN
}

data class FrameCaptureResult(
    /** 选中 frame 的路径, 如 ["(top)","main","content"]; 顶层时首元素即 "(top)"。null = 没有可达 frame */
    val selectedFramePath: List<String>?,
    /** 选中 frame 的 outerHTML(交 JwParser.generateCourseList 消费); 无选中时为 "" */
    val html: String,
    /** 该 frame 命中的锚点名, 与 ANCHORS 子集对应 */
    val matchedAnchors: List<String>,
    /** 调用方喂 parser 后回填; 抓取层产出时恒 0 */
    val courseCount: Int = 0,
    val status: FrameCaptureStatus,
    /** 跨域 frame 描述 "frameName@domain", 仅 CROSS_DOMAIN_IFRAME_BLOCKED / 诊断时非空 */
    val blockedFrames: List<String> = emptyList(),
    /** 重试次数(0=首次), 供 captureWithRetry 传递与日志 */
    val retryCount: Int = 0,
    /** DFS 实际到达的最大深度(诊断用) */
    val maxDepthReached: Int = 0,
    /** 被跳过的无锚点 frame 名列表(诊断用) */
    val skippedFrames: List<String> = emptyList(),
    /** 用户可读诊断片段(不含学号/Cookie/HTML 原文 — 隐私红线) */
    val diagnosticHint: String = ""
)

data class FrameSnapshot(
    val frameName: String?,      // 顶层固定 "(top)"
    val frameSrc: String?,
    val depth: Int,              // 顶层 0
    val parentPath: List<String>,// 顶层 emptyList
    val outerHTML: String?,      // null = 跨域或读取失败
    val blockedDomain: String?   // 非空 = 该 frame 被同源策略挡掉, 记域名
) {
    companion object {
        /** 测试便利入口: 整页 JSON → FrameSnapshotList(与 FrameSnapshotList.fromJson 同义) */
        fun fromJson(json: String): FrameSnapshotList = FrameSnapshotList.fromJson(json)
    }
}

data class FrameSnapshotList(
    val url: String,
    val declaredMaxDepth: Int,
    val frames: List<FrameSnapshot>
) {
    companion object {
        /**
         * 解析 CAPTURE_FRAMES_JS 回传 JSON。
         * 结构: {"ok":true,"url":"...","depth":N,"frames":[{name,src,depth,path:[],html,blocked},...]}
         * 容错: ok=false / frames 缺失 / 某 frame 的 html 为 null / blocked 非空 都不抛异常。
         * org.json 的 optString 对 JSON null 返回 "" — 因此 html:null 与 blocked:"" 天然落成 null/""。
         */
        fun fromJson(json: String): FrameSnapshotList {
            if (json.isBlank()) return FrameSnapshotList("", 0, emptyList())
            return try {
                val obj = JSONObject(json)
                val url = obj.optString("url", "")
                val depth = obj.optInt("depth", 0)
                val frames = mutableListOf<FrameSnapshot>()
                val arr = obj.optJSONArray("frames") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    val htmlStr = f.optString("html", "")
                    val blockedStr = f.optString("blocked", "")
                    frames += FrameSnapshot(
                        frameName = f.optString("name", "").ifBlank { null },
                        frameSrc = f.optString("src", "").ifBlank { null },
                        depth = f.optInt("depth", 0),
                        parentPath = (0 until f.optJSONArray("path")?.length().let { len -> len ?: 0 })
                            .map { j -> f.optJSONArray("path")!!.getString(j) },
                        outerHTML = htmlStr.ifBlank { null },
                        blockedDomain = blockedStr.ifBlank { null }
                    )
                }
                FrameSnapshotList(url, depth, frames)
            } catch (e: Exception) {
                FrameSnapshotList("", 0, emptyList())
            }
        }
    }
}

object FrameTraversalTree {
    /**
     * 锚点集 — 与 parser 容器选择器对齐
     * (Table1→JwOldZfParser, blacktab→T1 兜底链, kbtable→JwQzParser,
     *  kbgrid_table_0/kblist_table→T4, kbList→JwNewZfParser JSON 路径,
     *  kbxx→CF/T8 之前 JwNewZfParser 会误命中, T7 仅收集不解析)。
     * 匹配语义: 对 frame 的 outerHTML 小写化后做 id="/class=" 属性子串匹配; 不做裸子串匹配。
     */
    val ANCHORS: List<String> = listOf(
        "Table1", "blacktab", "kbtable", "kbgrid_table_0", "kblist_table", "kbList", "kbxx", "timetable"
    )

    private fun anchorNeedle(anchor: String): List<String> =
        listOf("id=\"$anchor\"", "id='$anchor'", "class=\"$anchor\"", "class='$anchor'")

    /** 扫锚点: 返回 ANCHORS 的命中子集, 顺序保持 ANCHORS 原序(测试断言依赖稳定序) */
    fun findAnchors(html: String): List<String> {
        if (html.isEmpty()) return emptyList()
        val lower = html.lowercase()
        return ANCHORS.filter { anchor ->
            anchorNeedle(anchor).any { needle -> lower.contains(needle.lowercase()) }
        }
    }

    /** 跨域 frame 列表: 返回 "frameName@domain" 字符串(frameName 缺省用 src 尾段) */
    fun findBlockedFrames(snapshots: FrameSnapshotList): List<String> {
        return snapshots.frames.mapNotNull { f ->
            val domain = f.blockedDomain ?: return@mapNotNull null
            val name = f.frameName ?: f.frameSrc?.trim()?.takeLastWhile { it != '/' } ?: "unknown"
            "$name@${shortDomain(domain)}"
        }
    }

    private fun shortDomain(src: String): String = try {
        URI(src).host ?: src.take(80)
    } catch (e: Exception) {
        src.take(80)
    }

    /**
     * 登录页指纹表 — 关键词小写包含即命中, ≥2 条命中才判 SESSION_EXPIRED(单关键词误伤率高)。
     * 每条 = 关键词 ; 来源协议
     */
    val LOGIN_FINGERPRINTS: List<Pair<String, String>> = listOf(
        "__viewstate" to "zf/ASP.NET",          // 必与下一条(密码框)同时出现才计
        "type=\"password\"" to "zf/ASP.NET",
        "checkcode.aspx" to "zf",
        "logon.do" to "qz",
        "verifycode.servlet" to "qz",
        "logintoxk" to "qz",
        "login_slogin.html" to "zf_new",
        "/authserver/login" to "cf",
        "/iaaa/oauth.jsp" to "pku",
        "appid=syllabus" to "pku",
        "action=\"default.aspx\"" to "bnuz",
        "j_spring_security_check" to "urp_new",
        "cas/login" to "sso-cas",
        "请输入密码" to "通用",
        "会话已超时" to "通用",
        "请重新登录" to "通用",
        "您的账号在其它地方登录" to "qz-顶号"
    )

    /**
     * 登录页判定: 对一份 frame HTML 独立打分。
     * 命中 1 条记 1 分; "__viewstate" 与 "type=\"password\"" 是同族对, 同时命中合并记 2 分。
     * 返回 true 当 score >= 2。在所有可达 frame 上分别调用, 任一 true 即整页 SESSION_EXPIRED。
     */
    fun looksLikeLoginPage(html: String): Boolean {
        if (html.isEmpty()) return false
        val lower = html.lowercase()
        var score = 0
        for ((kw, _) in LOGIN_FINGERPRINTS) {
            if (lower.contains(kw)) score++
        }
        return score >= 2
    }

    internal data class Candidate(
        val snapshot: FrameSnapshot,
        val anchors: List<String>,
        val order: Int
    )

    /**
     * skippedFrames = 可达但无锚点、且不是任一候选 frame 的祖先(或本身)的 frame 名。
     * 祖先链 frame 是通往锚点容器的路径, 不算"被跳过"。
     */
    internal fun computeSkipped(
        snapshots: FrameSnapshotList,
        maxDepth: Int,
        candidates: List<Candidate>
    ): List<String> {
        val candidateFrames = candidates.map { it.snapshot }.toSet()
        val ancestorSet = HashSet<FrameSnapshot>()
        for (c in candidates) {
            // parentPath 是祖先 frame 名链(如 ["(top)","main"]), 名字对应即视为祖先
            val names = c.snapshot.parentPath.toSet()
            snapshots.frames.filterTo(ancestorSet) { it.frameName != null && it.frameName in names }
            ancestorSet.add(c.snapshot)
        }
        return snapshots.frames
            .filter { it.outerHTML != null && it.depth <= maxDepth }
            .filter { findAnchors(it.outerHTML!!).isEmpty() }
            .filter { it !in ancestorSet }
            .mapNotNull { it.frameName }
    }

    /** ③ 候选排序: 锚点数降序 → 深度降序 → DOM 顺序升序 */
    internal fun rankedCandidates(snapshots: FrameSnapshotList, maxDepth: Int = 8): List<Candidate> {
        return snapshots.frames
            .withIndex()
            .filter { (_, f) -> f.outerHTML != null && f.depth <= maxDepth }
            .map { (i, f) -> Candidate(f, findAnchors(f.outerHTML!!), i) }
            .filter { it.anchors.isNotEmpty() }
            .sortedWith(
                compareByDescending<Candidate> { it.anchors.size }
                    .thenByDescending { it.snapshot.depth }
                    .thenBy { it.order }
            )
    }

    /**
     * 选择规则(优先级自上而下, 来自 11 份 case.json 的 frameStrategy 字段):
     *  ① 可达 = outerHTML != null 且 depth <= maxDepth(默认 8)。
     *  ② 对每个可达 frame 独立 findAnchors; 命中者进候选。
     *  ③ 候选排序: 锚点数降序 → 深度降序 → DOM 顺序升序; 排序第一名为初选。
     *  ④ 全部可达 frame 均无锚点: 登录指纹 > 跨域 > WRONG_PAGE。
     *  ⑤ 全部 frame 不可达(顶层都跨域): blocked 非空 → CROSS_DOMAIN_IFRAME_BLOCKED, 否则 WRONG_PAGE。
     */
    fun selectBestFrame(snapshots: FrameSnapshotList, maxDepth: Int = 8): FrameCaptureResult {
        val blocked = findBlockedFrames(snapshots)
        val reachable = snapshots.frames.filter { it.outerHTML != null && it.depth <= maxDepth }
        val maxDepthReached = snapshots.frames.maxOfOrNull { it.depth } ?: 0
        // ②③ 候选
        val candidates = rankedCandidates(snapshots, maxDepth)
        val best = candidates.firstOrNull()
        val skipped = computeSkipped(snapshots, maxDepth, candidates)
        if (best != null) {
            return FrameCaptureResult(
                selectedFramePath = best.snapshot.parentPath + (best.snapshot.frameName ?: ""),
                html = best.snapshot.outerHTML ?: "",
                matchedAnchors = best.anchors,
                status = FrameCaptureStatus.OK,
                blockedFrames = blocked,
                maxDepthReached = maxDepthReached,
                skippedFrames = skipped
            )
        }

        // ④ 全部可达 frame 均无锚点
        //  - 任一可达 frame looksLikeLoginPage → SESSION_EXPIRED
        //    多个 frame 同时命中时取最深的(顶层 frameset 可能因注释/说明文字含弱指纹词,
        //    真正的登录表单在更深的内层 frame — case #8)
        val loginFrame = reachable.filter { looksLikeLoginPage(it.outerHTML!!) }
            .maxByOrNull { it.depth }
        if (loginFrame != null) {
            return FrameCaptureResult(
                selectedFramePath = loginFrame.parentPath + (loginFrame.frameName ?: ""),
                html = loginFrame.outerHTML ?: "",
                matchedAnchors = emptyList(),
                status = FrameCaptureStatus.SESSION_EXPIRED,
                blockedFrames = blocked,
                maxDepthReached = maxDepthReached,
                skippedFrames = skipped,
                diagnosticHint = "检测到登录页/会话过期特征(frame=${loginFrame.frameName ?: "(top)"})，请重新登录后再点「导入此页」"
            )
        }
        //  - 存在 blocked frame → CROSS_DOMAIN_IFRAME_BLOCKED
        if (blocked.isNotEmpty()) {
            return FrameCaptureResult(
                selectedFramePath = null,
                html = "",
                matchedAnchors = emptyList(),
                status = FrameCaptureStatus.CROSS_DOMAIN_IFRAME_BLOCKED,
                blockedFrames = blocked,
                maxDepthReached = maxDepthReached,
                skippedFrames = skipped,
                diagnosticHint = blocked.joinToString("、")
            )
        }
        //  - 否则 WRONG_PAGE (空壳 iframe 由调用方 captureWithRetry 按重试情况升格 IFRAME_NAV_PENDING)
        return FrameCaptureResult(
            selectedFramePath = reachable.firstOrNull()?.let { it.parentPath + (it.frameName ?: "") },
            html = reachable.firstOrNull()?.outerHTML ?: "",
            matchedAnchors = emptyList(),
            status = FrameCaptureStatus.WRONG_PAGE,
            blockedFrames = blocked,
            maxDepthReached = maxDepthReached,
            skippedFrames = skipped,
            diagnosticHint = if (reachable.isEmpty()) "页面无可读 frame" else "当前页面未检测到课表容器"
        )
    }

    /**
     * 解析驱动的候选回退: 把"锚点命中"升级为"锚点命中 且 parser 产出 > 0 课"。
     *
     * @param parse 单个 frame HTML 的解析函数 — 抓取层不依赖解析层, 通过高阶函数注入。
     * @return 课程数最大的候选(并列取排序靠前者); 全部 0 课 → status=EMPTY_SEMESTER(锚点命中过)。
     */
    fun rankAll(
        snapshots: FrameSnapshotList,
        maxDepth: Int = 8,
        parse: (String) -> List<*>
    ): FrameCaptureResult {
        val blocked = findBlockedFrames(snapshots)
        val maxDepthReached = snapshots.frames.maxOfOrNull { it.depth } ?: 0
        val candidates = rankedCandidates(snapshots, maxDepth)
        val skipped = computeSkipped(snapshots, maxDepth, candidates)

        var bestCount = 0
        var bestCandidate: Candidate? = null
        for (c in candidates) {
            val count = try {
                parse(c.snapshot.outerHTML!!).size
            } catch (t: Throwable) {
                0
            }
            if (count > bestCount) {
                bestCount = count
                bestCandidate = c
            }
        }

        val first = candidates.firstOrNull()
        if (bestCandidate != null && bestCount > 0) {
            return FrameCaptureResult(
                selectedFramePath = bestCandidate.snapshot.parentPath + (bestCandidate.snapshot.frameName ?: ""),
                html = bestCandidate.snapshot.outerHTML ?: "",
                matchedAnchors = bestCandidate.anchors,
                courseCount = bestCount,
                status = FrameCaptureStatus.OK,
                blockedFrames = blocked,
                maxDepthReached = maxDepthReached,
                skippedFrames = skipped
            )
        }

        if (first != null) {
            // 有锚点候选但全部 0 课 → EMPTY_SEMESTER
            return FrameCaptureResult(
                selectedFramePath = first.snapshot.parentPath + (first.snapshot.frameName ?: ""),
                html = first.snapshot.outerHTML ?: "",
                matchedAnchors = first.anchors,
                courseCount = 0,
                status = FrameCaptureStatus.EMPTY_SEMESTER,
                blockedFrames = blocked,
                maxDepthReached = maxDepthReached,
                skippedFrames = skipped,
                diagnosticHint = "已尝试 ${candidates.size} 个含课表容器的 frame, 均未解析出课程"
            )
        }

        // 候选为空 → 与 selectBestFrame 一致(登录指纹 > 跨域 > WRONG_PAGE)
        return selectBestFrame(snapshots, maxDepth)
    }
}

object RenderReadinessChecker {
    /** 延迟渲染容器 — 新正方 grid/list 视图 + 老正方 Table1Wrap */
    val DELAYED_CONTAINER_ANCHORS: List<String> =
        listOf("Table1Wrap", "kbgrid_table_0", "kblist_table", "kbcontent", "timetable_con")

    enum class Readiness { READY, DELAY, GIVE_UP }

    /**
     * @param html 本次抓取选中的 frame HTML(不是整页拼接)
     * @param retryCount 已完成的重试次数(0=首次)
     */
    fun check(html: String, retryCount: Int): Readiness {
        if (retryCount >= 3) return Readiness.GIVE_UP
        if (html.isBlank()) return Readiness.DELAY
        val lower = html.lowercase()
        val hasDelayed = DELAYED_CONTAINER_ANCHORS.any { lower.contains(it.lowercase()) }
        if (hasDelayed) {
            val loadingText = lower.contains("加载中") || lower.contains("loading")
                || lower.contains("数据加载") || lower.contains("暂无数据")
            val gridEmpty = lower.contains("kbgrid_table_0") && !lower.contains("kbcontent")
            val listEmpty = lower.contains("kblist_table") && !lower.contains(".title ")
            if (loadingText || gridEmpty || listEmpty) return Readiness.DELAY
        }
        return Readiness.READY
    }

    /**
     * case.json #7 专款: 空 iframe 壳判定 — html 非空但结构上只有空 head/body。
     * 判定 true: 去空白后长度 < 60 且 (含 "<html" 或含 "<body") 且不含 "<table" 不含 "<div" 不含 "<form"。
     */
    fun checkBlankShell(html: String): Boolean {
        val stripped = html.replace(Regex("""\s"""), "")
        if (stripped.length >= 60) return false
        val lower = html.lowercase()
        if (!(lower.contains("<html") || lower.contains("<body"))) return false
        if (lower.contains("<table") || lower.contains("<div") || lower.contains("<form")) return false
        return true
    }

    fun delayedStatus(retryCount: Int): FrameCaptureStatus =
        if (retryCount >= 3) FrameCaptureStatus.CONTAINER_EMPTY_AFTER_DELAY else FrameCaptureStatus.OK
}
