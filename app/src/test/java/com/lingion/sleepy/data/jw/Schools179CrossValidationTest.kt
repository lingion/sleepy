package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 179 校交叉验证回归测试 (2026-09-05, bucket 0-14 十五个 agent 报告落地)。
 *
 * 背景: WHUT "Welcome come to EMAP." 翻车后全量交叉验证, 修复 60 条/删 2 条/更名 3 条。
 * 本测试钉死最易回漂的几类事实:
 *   1) 已知死链入口永不再出现 (裸 IP / 已废弃域 / 已出售域);
 *   2) WHUT 同型翻车校的入口必须落到真实登录页;
 *   3) 协议 type 与实测系统指纹一致 (西亚斯/郑航=金智EAMS, 齐鲁=zf_new ...)。
 */
class Schools179CrossValidationTest {

    private val schoolsJson: String by lazy {
        File("src/main/assets/schools.json").readText(Charsets.UTF_8)
    }

    private fun entries(): List<Pair<String, org.json.JSONObject>> {
        val arr = JSONArray(schoolsJson)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            o.optString("name", "") to o
        }
    }

    private fun entryOf(name: String): org.json.JSONObject? =
        entries().firstOrNull { it.first == name }?.second

    // ---- 1. 已出售/已废弃域永不再入清单 ----

    @Test
    fun `caie-org sold domain must never appear`() {
        // 大连工业大学艺术与信息工程学院: caie.org 已过期待售 (域名交易页), 真域是 caie.edu.cn
        assertFalse(schoolsJson.contains("caie.org/"))
    }

    @Test
    fun `private RFC1918 urls must never appear`() {
        // 南宁师范 172.16.130.25 / 广东环保工程 10.x — 校外物理不可达, WebView 永久转圈
        val re = Regex("""https?://(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)""")
        val hits = re.findAll(schoolsJson).map { it.value }.toList()
        assertTrue("schools.json 含 RFC1918 私网地址: $hits", hits.isEmpty())
    }

    // ---- 2. WHUT 同型翻车校的入口钉死 ----

    @Test
    fun `dead-entry schools must point at verified new hosts`() {
        // (校名, 入口必须包含的 host/路径片段) — 全部经 curl 实测 200 登录页
        val mustContain = mapOf(
            "郑州大学西亚斯国际学院" to "jwxt.sias.edu.cn",
            "徐州医科大学" to "jwpt.xzhmu.edu.cn",
            "徐州幼儿师范高等专科学校" to "jwgl.xzyz.edu.cn",
            "浙江农林大学" to "jwxt.zafu.edu.cn",
            "浙江财经大学" to "jwxt.zufe.edu.cn",
            "齐鲁工业大学" to "jw.qlu.edu.cn",
            "南宁师范大学" to "jw.nnnu.edu.cn",
            "南昌大学" to "jwpt.ncu.edu.cn",
            "南京师范大学中北学院" to "zbjw.nnudy.edu.cn",
            "辽宁工业大学" to "jwglxt.lnut.edu.cn",
            "西安外事学院" to "jwxt.xaiu.edu.cn",
            "西南民族大学" to "jwglxt/xtgl/login_slogin.html",
            "中国矿业大学徐海学院" to "jwxt.cumtxh.cn",
            "成都理工大学工程技术学院" to "jwgl.cdutetc.cn",
            "长春大学" to "jwxt.ccu.edu.cn",
            "杭州电子科技大学" to "newjw.hdu.edu.cn",
            "广东海洋大学" to "jw.gdou.edu.cn",
            "湖南城市学院" to "rzpt.hncu.edu.cn",
            "嘉兴南湖学院" to "jwzx.jxnhu.edu.cn",
            "济南大学" to "jwgl.ujn.edu.cn",
            "江苏工程职业技术学院" to "jw.jcet.edu.cn",
            "青岛科技大学" to "jwglxt.qust.edu.cn",
            "湖南科技大学" to "kdjw.hnust.edu.cn",
            "湖南科技大学潇湘学院" to "xxjw.hnust.edu.cn",
            "四川大学" to "zhjw.scu.edu.cn",
            "潍坊学院" to "jw.wfu.edu.cn",
            "重庆交通大学" to "jwgln.cqjtu.edu.cn",
            "福建师范大学" to "jwglxt.fjnu.edu.cn/jwglxt/",
            "河南工程学院" to "jwgl.haue.edu.cn",
            "四川轻化工大学" to "jwgl.suse.edu.cn",
            "苏州科技大学天平学院" to "tpjw-n.usts.edu.cn",
            "山西工程技术学院" to "xsjw.sxit.edu.cn",
            "湖北医药学院" to "https://jw.hbmu.edu.cn",
            "山东第二医科大学" to "jwgl.sdsmu.edu.cn",
            "哈尔滨商业大学" to "jw.hrbcu.edu.cn",
        )
        val bad = mustContain.mapNotNull { (name, frag) ->
            val e = entryOf(name)
            when {
                e == null -> name to "(条目缺失)"
                !e.optString("url").contains(frag) -> name to e.optString("url")
                else -> null
            }
        }
        assertEquals("入口未指向验证过的新 host: $bad", 0, bad.size)
    }

    // ---- 3. 协议 type 与实测系统指纹一致 ----

    @Test
    fun `re-typed schools must carry verified protocol types`() {
        val expectType = mapOf(
            "郑州大学西亚斯国际学院" to "classic_eams",   // eams/loginExt.action 金智, 非强智
            "郑州航空工业管理学院" to "classic_eams",     // jwglxt.zua.edu.cn/eams 实测
            "西安建筑科技大学" to "classic_eams",         // 树维 EAMS + authserver CAS
            "齐鲁工业大学" to "zf_new",                  // JW-spider: jwglxt 接口链
            "浙江万里学院" to "zf_new",                  // ehall SharePoint 门户
            "湖南城市学院" to "qz",                      // 强智 lyuap 统一门户
            "华南农业大学" to "qz",                      // greyovo 两仓互证 el-table 强智
            "潍坊学院" to "qz",                          // jw.wfu.edu.cn 强智实测
            "渭南师范学院" to "zf_new",                  // 218.195.46.49 jwglxt
            "茂名职业技术学院" to "zf_new",              // zfsoft v5
            "西安外事学院" to "zf_new",                  // V-9.0 + csrftoken
            "辽宁工业大学" to "zf_new",                  // jwglxt 新域
        )
        val bad = expectType.mapNotNull { (name, type) ->
            val e = entryOf(name)
            when {
                e == null -> name to "(条目缺失,期望 $type)"
                e.optString("type") != type -> name to "${e.optString("type")} (期望 $type)"
                else -> null
            }
        }
        assertEquals("协议 type 与实测指纹不符: $bad", 0, bad.size)
    }

    // ---- 4. 更名与转设 ----

    @Test
    fun `renamed schools must use new official names`() {
        // 滨州医学院→山东第二医科大学(2023官网整体迁移); 嘉兴学院南湖学院→嘉兴南湖学院(2020转设);
        // 信阳师范学院→信阳师范大学(2023更名)
        assertTrue(entryOf("山东第二医科大学") != null)
        assertTrue(entryOf("嘉兴南湖学院") != null)
        assertTrue(entryOf("信阳师范大学") != null)
        // 旧名不得残留
        for (old in listOf("滨州医学院", "嘉兴学院南湖学院", "信阳师范学院")) {
            assertFalse("旧校名 '$old' 不应残留", entries().any { it.first == old })
        }
        // 更名兼容靠 aliases
        val xinyang = entryOf("信阳师范大学")!!
        assertTrue(xinyang.getJSONArray("aliases").let { a -> (0 until a.length()).any { a.getString(it) == "信阳师院" } })
    }

    // ---- 5. 已删除的僵尸条目不得复活 ----

    @Test
    fun `removed zombie entries must stay deleted`() {
        // 广西大学行健文理学院: 已转设南宁理工学院, 原 IP 反查=广西大学宿舍网关
        // 广东环境保护工程职业学院: 教务已内网化 (10.1.100.206), 无公网入口
        // 广西师范学院: 与南宁师范大学同源复制条目, 共用死 IP 172.16.130.25 (上游粘贴错误)
        assertEquals(
            "僵尸条目不应存在",
            emptyList<String>(),
            entries().map { it.first }.filter {
                it in setOf("广西大学行健文理学院", "广东环境保护工程职业学院", "广西师范学院")
            },
        )
    }

    // ---- 6. 总量闸 ----

    @Test
    fun `school count stays 179`() {
        // 179 - 删3 (行健文理/广东环保/广西师范学院重复条目) = 176
        // 2026-09-05 收录广东医科大学 → 177; 广州医科大学 → 178; 吉林工商学院(超星) → 179
        assertEquals(179, entries().size)
    }

    @Test
    fun `jlbtc entry pinned to chaoxing collector evidence`() {
        // 采集包 sleepy-adapt-0905-215140: 页脚 Powered by ChaoXing,
        // queryKbForGrdb 22行个人课表 (xjc=节号, zcstr 周次串) — 首个 chaoxing 协议校
        val e = entryOf("吉林工商学院")
        assertTrue("吉林工商学院 条目缺失", e != null)
        assertTrue("入口应为 jwxt.jlbtc.edu.cn", e!!.optString("url").contains("jwxt.jlbtc.edu.cn"))
        assertEquals("应为 chaoxing (Powered by ChaoXing 采集包实锤)", "chaoxing", e.optString("type"))
    }

    // ---- 7. 实采包钉死 (sleepy-collector 2026-09-05 广东医科大学用户提供) ----

    @Test
    fun `gdmu entry pinned to collector evidence`() {
        // 采集包 sleepy-adapt-0905-183138: zftal-ui-v5-1.0.2 + 菜单 /kbcx/xskbcx_cxXskbcxIndex.html
        // 登录走 authserver.gdmu.edu.cn CAS (jziotlogin 回调) — 网关页, 不作判型指纹
        val e = entryOf("广东医科大学")
        assertTrue("广东医科大学 条目缺失", e != null)
        assertTrue("广东医科大学 入口应为 jw.gdmu.edu.cn", e!!.optString("url").contains("jw.gdmu.edu.cn"))
        assertEquals("广东医科大学 应为 zf_new (zftal-ui-v5 + /kbcx/ 菜单实锤)", "zf_new", e.optString("type"))
    }

    @Test
    fun `gzhmu entry pinned to official qz evidence`() {
        // 教务处官网(2026仍挂)快速链接 → "教务管理系统" = 强智教务管理系统;
        // 入口 https://jwgl.gzhmu.edu.cn/jsxsd (jsxsd 路径 = 强智铁证)。
        // 注意: jwgl.gzhmu.edu.cn 公网 NXDOMAIN — 校内DNS/教育网限定, 校外走 webvpn.gzhmu.edu.cn (判型链已支持 hex webvpn + /jsxsd/ → qz)
        val e = entryOf("广州医科大学")
        assertTrue("广州医科大学 条目缺失", e != null)
        assertTrue("广州医科大学 入口应含 /jsxsd", e!!.optString("url").contains("/jsxsd"))
        assertEquals("广州医科大学 应为 qz (官方通知'强智教务管理系统'+jsxsd 路径)", "qz", e.optString("type"))
    }
}
