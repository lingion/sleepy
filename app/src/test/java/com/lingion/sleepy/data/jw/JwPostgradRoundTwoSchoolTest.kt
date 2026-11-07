package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic layouts with independently enumerated occurrences, not authenticated school pages. */
class JwPostgradRoundTwoSchoolTest {
    private fun parse(type: String, html: String): List<JwCourse> {
        val direct = JwPostgradSchoolParser(html, type).generateCourseList()
        assertEquals(direct, JwParserRegistry.selectBest(html, type).first)
        assertEquals(direct, JwImportViewModel.parseHtmlStatic(html, type).first)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), direct, emptyList())
        assertEquals(direct, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        if (type !in setOf("csv", "zju_post")) assertDeclaredHtmlFrame(type, html, direct)
        return direct
    }
    private fun occurrences(courses: List<JwCourse>): List<String> = courses.flatMap { course ->
        (course.startWeek..course.endWeek).filter { course.type == 0 || it % 2 == course.type % 2 }.flatMap { week ->
            (course.startNode..course.endNode).map { node -> "${course.name}|${course.teacher}|${course.room}|${course.day}|$node|$week" }
        }
    }.sorted()

    @Test fun `xjtu distinct weeks and nodes keep name suffix and fields`() {
        val html = """<table id="tbl"><tr><td id="abc3">课程：数学分析2<br>班级：A班<br>教师：王<br>教室：东楼<br>节次：1,3-4<br>周次：第1-3单周，第7周</td></tr></table>"""
        val actual = parse("xjtu_post", html)
        assertEquals(listOf(
            JwCourse("数学分析2A班", "东楼", "王", 3, 1, 1, 1, 3, 1),
            JwCourse("数学分析2A班", "东楼", "王", 3, 3, 4, 1, 3, 1),
            JwCourse("数学分析2A班", "东楼", "王", 3, 1, 1, 7, 7),
            JwCourse("数学分析2A班", "东楼", "王", 3, 3, 4, 7, 7),
        ), actual)
        assertEquals(listOf(1,3,7).flatMap { w -> listOf(1,3,4).map { "数学分析2A班|王|东楼|3|$it|$w" } }.sorted(), occurrences(actual))
    }

    @Test fun `uestc eight slash fields align separate meetings`() {
        val html = """<table id="tbl"><tr><th>表头</th></tr><tr><td>节次</td><td>代码/光学/属性/院系/李/2-6双周,9周/(5~6)/A101， 代码/专题/属性/院系//5周/(9)/</td></tr></table>"""
        val actual = parse("uestc_post", html)
        assertEquals(listOf(JwCourse("光学", "A101", "李", 1,5,6,2,6,2),
            JwCourse("光学", "A101", "李", 1,5,6,9,9), JwCourse("专题", "", "", 1,9,9,5,5)), actual)
        assertEquals((listOf(2,4,6,9).flatMap { w -> listOf(5,6).map { "光学|李|A101|1|$it|$w" } } +
            "专题|||1|9|5").sorted(), occurrences(actual))
    }

    @Test fun `bfa empty weekday cell retains day and note workload`() {
        val html = """<div id="table"><table class="pages-sec-student-scheme-pages-student-timetable-query-timetable-index-tableContent"><tr><td _nk=""></td><td _nk="2"><div><p>序号</p><p>影像2</p><p>限选</p><p>周</p><p>摄影棚[1-8]</p></div><div>1-2节</div></td></tr></table></div>"""
        val actual = parse("bfa_post", html)
        assertEquals(listOf(JwCourse("影像2", "摄影棚", "周", 2,1,2,1,8, credit=1f, note="限选")), actual)
        assertEquals(listOf(1,2,3,4,5,6,7,8).flatMap { w -> listOf(1,2).map { "影像2|周|摄影棚|2|$it|$w" } }.sorted(), occurrences(actual))
    }

    @Test fun `gdbyxy last nested table discrete weeks and underscore room`() {
        val html = """<div id="pageRpt"><table><tr><td>[X1]伪课程<br>伪教师<br>[1-20周]一[1-2节]<br>伪教室<br></td></tr></table><table><tr><td>[A123]法理<br>陈<br>[1,3-5周]日[7-8节]单<br>主楼_201_多余<br></td></tr></table></div>"""
        val actual = parse("gdbyxy", html)
        assertEquals(listOf(JwCourse("法理", "主楼-201", "陈", 7,7,8,1,1,1),
            JwCourse("法理", "主楼-201", "陈", 7,7,8,3,5,1)), actual)
        assertEquals(listOf(1,3,5).flatMap { w -> listOf(7,8).map { "法理|陈|主楼-201|7|$it|$w" } }.sorted(), occurrences(actual))
    }

    @Test fun `malformed entries preserve later valid courses without defaults`() {
        assertTrue(parse("xjtu_post", "<table id='tbl'><tr><td id='abc0'>课程：坏课<br>A<br>王<br>房<br>1-2<br>1-20</td></tr></table>").isEmpty())
        val html = """<table id="tbl"><tr><th>头</th></tr><tr><td>节</td><td>x/坏课/x/x/王/待定/(1-2)/A， x/好课/x/x/李/5周/(3)/B</td></tr></table>"""
        assertEquals(listOf(JwCourse("好课","B","李",1,3,3,5,5)), parse("uestc_post",html))
    }

    @Test fun `cupl sunday header rowspan and optional campus retain each arrangement`() {
        val html = """<table id="tabCT"><tr><th>节次</th><th>星期日</th><th>星期一</th></tr><tr><td>第1节</td><td rowspan="2"><b>法史</b><br>周次：第1-3周<br>校区：海淀<br>教室：研101<br>教师：陈</td><td><b>专题</b><br>第5周<br>教室：昌平<br>教师：赵</td></tr><tr><td>第2节</td><td><b>周一后课</b><br>第7周<br>教室：A<br>教师：李</td></tr></table>"""
        val actual = parse("cupl_post", html)
        assertEquals(listOf(JwCourse("法史","研101","陈",7,1,2,1,3),
            JwCourse("专题","昌平","赵",1,1,1,5,5), JwCourse("周一后课","A","李",1,2,2,7,7)),actual)
        assertEquals((listOf(1,2,3).flatMap { w -> listOf(1,2).map { "法史|陈|研101|7|$it|$w" } } +
            listOf("专题|赵|昌平|1|1|5", "周一后课|李|A|1|2|7")).sorted(),occurrences(actual))
    }

    @Test fun `ysu alternate anchor separate groups and rowspans keep weekday`() {
        val html = """<table id="MainWork_DataGrid1"><tr align="center"><th>节次</th><th>星期一</th><th>星期二</th></tr><tr><td align="center">1</td><td rowspan="2">机械<br>[第1-5单周,第8周](教师：吴)(教室:B)<br><br>方法<br>[第6周](教师：王)(教室:C)</td><td>设计<br>[第2-4双周](教师：李)(教室:D)</td></tr><tr><td align="center">2</td><td>后续<br>[第9周](教师：陈)(教室:E)</td></tr></table>"""
        val actual = parse("ysu_post",html)
        assertEquals(listOf(JwCourse("机械","B","吴",1,1,2,1,5,1),JwCourse("机械","B","吴",1,1,2,8,8),
            JwCourse("方法","C","王",1,1,2,6,6),JwCourse("设计","D","李",2,1,1,2,4,2),
            JwCourse("后续","E","陈",2,2,2,9,9)),actual)
        assertEquals((listOf(1,3,5,8).flatMap { w -> listOf(1,2).map { "机械|吴|B|1|$it|$w" } } +
            listOf("方法|王|C|1|1|6","方法|王|C|1|2|6","设计|李|D|2|1|2","设计|李|D|2|1|4","后续|陈|E|2|2|9")).sorted(),occurrences(actual))
    }

    @Test fun `whu keeps numeric course suffix and room teacher variants`() {
        val html = """<div class="table_con"><table><tr><td rowspan="2">上午</td><td>1</td><td>数学分析2 1-3单周1-2节 A，B 张<br>123 5周3节 李</td></tr><tr><td>2</td><td>数学分析1 4周5节</td><td>实验 7周7-8节 王</td></tr></table></div>"""
        val actual = parse("whu_post",html)
        assertEquals(listOf(JwCourse("数学分析2","A,B","张",1,1,2,1,3,1),JwCourse("123","","李",1,3,3,5,5),
            JwCourse("数学分析1","","",1,5,5,4,4),JwCourse("实验","","王",2,7,8,7,7)),actual)
        assertEquals(listOf("数学分析2|张|A,B|1|1|1","数学分析2|张|A,B|1|2|1","数学分析2|张|A,B|1|1|3","数学分析2|张|A,B|1|2|3",
            "123|李||1|3|5","数学分析1|||1|5|4","实验|王||2|7|7","实验|王||2|8|7").sorted(),occurrences(actual))
    }

    @Test fun `hrbeu teacher week enumeration does not insert week two`() {
        val html = """<div id="StuCul_TimetableQry_TimeTable"><table class="WtbodyZlistS"><tr><td>节次</td><td field="Wednesday">课程:数学分析2<br>教师:一班多师 2班<br>节次:1,3-4<br>周次:1,3周(甲);4-6双周(乙)<br>教室:研楼<br>备注:限选</td></tr></table></div>"""
        val actual = parse("hrbeu_post",html)
        assertEquals(listOf(JwCourse("数学分析2(2班)","研楼","甲",3,1,1,1,1,note="备注:限选"),
            JwCourse("数学分析2(2班)","研楼","甲",3,3,4,1,1,note="备注:限选"),
            JwCourse("数学分析2(2班)","研楼","甲",3,1,1,3,3,note="备注:限选"),
            JwCourse("数学分析2(2班)","研楼","甲",3,3,4,3,3,note="备注:限选"),
            JwCourse("数学分析2(2班)","研楼","乙",3,1,1,4,6,2,note="备注:限选"),
            JwCourse("数学分析2(2班)","研楼","乙",3,3,4,4,6,2,note="备注:限选")),actual)
        assertEquals((listOf(1,3).flatMap { w -> listOf(1,3,4).map { "数学分析2(2班)|甲|研楼|3|$it|$w" } } +
            listOf(4,6).flatMap { w -> listOf(1,3,4).map { "数学分析2(2班)|乙|研楼|3|$it|$w" } }).sorted(),occurrences(actual))
    }

    @Test fun `grid invalid spans and malformed timing do not invent positions or weeks`() {
        assertTrue(parse("cupl_post","<table id='tabCT'><tr><th>头</th></tr><tr><td rowspan='0'><b>法史</b><br>第5周<br>教室：B<br>教师：王</td></tr></table>").isEmpty())
        assertTrue(parse("hrbeu_post","<div id='StuCul_TimetableQry_TimeTable'><table class='WtbodyZlistS'><tr><td>节</td><td field='monday'>课程:坏<br>教师:王<br>节次:1-2<br>周次:待定<br>教室:B</td></tr></table></div>").isEmpty())
    }

    @Test fun `old shtu singleton exclusions and adjacent node differences remain exact`() {
        val html = """<table id="div-table"><tr><td>节次</td><td>周一</td></tr><tr><td>1</td><td>统计2班<br>王<br>A<br>第1-5周(除第2,4周)</td></tr><tr><td>2</td><td>统计2班<br>王<br>A<br>第1-5周(除第3周)</td></tr><tr><td>3</td><td>代数1班<br>李<br>B<br>第5周</td></tr></table>"""
        val actual = parse("shtu_post",html)
        assertEquals(listOf(1,3,5).map { JwCourse("统计","A","王",1,1,1,it,it) } +
            listOf(1,2,4,5).map { JwCourse("统计","A","王",1,2,2,it,it) } + JwCourse("代数","B","李",1,3,3,5,5),actual)
        assertEquals(listOf("统计|王|A|1|1|1","统计|王|A|1|1|3","统计|王|A|1|1|5","统计|王|A|1|2|1",
            "统计|王|A|1|2|2","统计|王|A|1|2|4","统计|王|A|1|2|5","代数|李|B|1|3|5").sorted(),occurrences(actual))
    }

    @Test fun `ustc semicolon appointments retain note sparse periods parity and explicit clocks`() {
        val fields = listOf("序号","限选","代码","院系","类别","量子2","1~3(单周);4~6(双周)","周","A101: 1(1,3);B201: 2(8:40~10:20)")
        val html = "<table id='tablexskbcx-index-table'><tr>"+fields.joinToString(""){"<td><span>$it</span></td>"}+"</tr></table>"
        val actual = parse("ustc_post",html)
        assertEquals(listOf(JwCourse("量子2","A101","周",1,1,1,1,3,1,note="限选"),
            JwCourse("量子2","A101","周",1,3,3,1,3,1,note="限选"),
            JwCourse("量子2","B201","周",2,2,3,4,6,2,"08:40","10:20",note="限选")),actual)
        assertEquals(listOf("量子2|周|A101|1|1|1","量子2|周|A101|1|3|1","量子2|周|A101|1|1|3","量子2|周|A101|1|3|3",
            "量子2|周|B201|2|2|4","量子2|周|B201|2|3|4","量子2|周|B201|2|2|6","量子2|周|B201|2|3|6").sorted(),occurrences(actual))
    }

    @Test fun `xauat script assignments ignore comments and read either parity marker`() {
        val html = """<script>
            var td = document.getElementById('td_3_47');
            td.innerHTML += "课程:结构2<br>编号:A1<br>教师：陈（主讲）<br>地点-主楼(第2-6周 双周)";
            // var td = document.getElementById('td_1_41'); td.innerHTML += "课程:伪课<br>x<br>教师：伪<br>地点-伪(第1-20周 单周)";
            var td = document.getElementById('td_7_49');
            td.innerHTML += "课程:专题<br>编号:B1<br>主讲教师：李<br>地点-副楼(第5周 每周)";
        </script>"""
        val actual = parse("xauat_post",html)
        assertEquals(listOf(JwCourse("结构2","主楼","陈",3,7,7,2,6,2),JwCourse("专题","副楼","李",7,9,9,5,5)),actual)
        assertEquals(listOf("结构2|陈|主楼|3|7|2","结构2|陈|主楼|3|7|4","结构2|陈|主楼|3|7|6","专题|李|副楼|7|9|5").sorted(),occurrences(actual))
    }

    @Test fun `zju JSON HTML rowspan explicit Chinese weeks and no page-index fallback`() {
        fun anchor(name: String,weeks:String,teacher:String,room:String) = "<a><strong>$name</strong><br><br>类别<br>时间||($weeks 周)<br>$teacher<br>$room</a>"
        val page = "<div><form id='kcbForm'></form><table><tr><th>头</th></tr><tr><td rowspan='2'>${anchor("研讨","前二","王","A")}</td><td>${anchor("专题","3,5","李","B")}</td></tr><tr><td>${anchor("坏课","待定","赵","C")}</td></tr></table></div>"
        val actual = parse("zju_post",org.json.JSONArray().put(page).toString())
        assertEquals(listOf(JwCourse("研讨","A","王",1,1,2,1,2),JwCourse("专题","B","李",2,1,1,3,3),
            JwCourse("专题","B","李",2,1,1,5,5)),actual)
        assertEquals(listOf("研讨|王|A|1|1|1","研讨|王|A|1|2|1","研讨|王|A|1|1|2","研讨|王|A|1|2|2",
            "专题|李|B|2|1|3","专题|李|B|2|1|5").sorted(),occurrences(actual))
    }

    @Test fun `csv quoted commas quotes newlines delimiters and invalid calendar text`() {
        val header = "课程名称,星期,开始节数,结束节数,教师,教室,周数"
        val html = header+"\r\n\"R,\"\"D\"\"\",星期三,1,2,\"王\n李\",A,\"1-5单周,8周\"\r\n坏日期,1,1,2,王,B,11月7日\r\n"
        val actual = parse("csv",html)
        assertEquals(listOf(JwCourse("R,\"D\"","A","王\n李",3,1,2,1,5,1),JwCourse("R,\"D\"","A","王\n李",3,1,2,8,8)),actual)
        assertEquals(listOf(1,3,5,8).flatMap { w -> listOf(1,2).map { "R,\"D\"|王\n李|A|3|$it|$w" } }.sorted(),occurrences(actual))
        assertEquals(listOf(JwCourse("分号","B","李",7,59,60,5,5)),parse("csv",header.replace(',',';')+"\n分号;7;59;60;李;B;5\n"))
        assertTrue(parse("csv",header+"\n\"未闭合,1,1,2,王,B,5").isEmpty())
        assertTrue(parse("csv",header+"\n坏星期,8,1,2,王,B,5\n倒置,1,4,2,王,B,5").isEmpty())
    }

    @Test fun `legacy shuwei quoted argument shape bitmap and carried activity coordinates`() {
        val html = """<script>var activity = null;
            var actTeachers = [{"name":"王"},{"name":"李"}];
            activity = new TaskActivity("id","计算2(旧名)","roomId","A101","010100","extra","甲组");
            index = 0 * unitCount + 0;
            index = 2 * unitCount + 2;
            var courseName = "专题";
            courseName += "2";
            var actTeachers = [{"name":"陈"}];
            activity = new TaskActivity("id","被声明名覆盖","roomId","B201","000010","extra","乙组");
            index = 6 * unitCount + 4;
            table0.marshalTable();</script>"""
        val actual = parse("shuwei_old",html)
        assertEquals(listOf(JwCourse("计算2(甲组)","A101","王, 李",1,1,1,1,1),JwCourse("计算2(甲组)","A101","王, 李",1,1,1,3,3),
            JwCourse("计算2(甲组)","A101","王, 李",3,3,3,1,1),JwCourse("计算2(甲组)","A101","王, 李",3,3,3,3,3),
            JwCourse("专题2","B201","陈",7,5,5,4,4)),actual)
        assertEquals(listOf("计算2(甲组)|王, 李|A101|1|1|1","计算2(甲组)|王, 李|A101|1|1|3","计算2(甲组)|王, 李|A101|3|3|1",
            "计算2(甲组)|王, 李|A101|3|3|3","专题2|陈|B201|7|5|4").sorted(),occurrences(actual))
        assertTrue(parse("shuwei_old",html.replace("010100","110100")).none{it.name.startsWith("计算")})
    }
}
