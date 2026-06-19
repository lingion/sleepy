# Sleepy 缺失 Parser 详细实现规格报告

> 分析对象：[dIT8Zv/WakeupSchedule_BUPT](https://github.com/dIT8Zv/WakeupSchedule_BUPT)（默认分支 `master`）`app/.../schedule_import/parser/` 下 10 个 sleepy 尚未真正实现的 parser。
> 目标项目：`/Users/lingion_k/Desktop/sleepy`，parser 目录 `app/src/main/java/com/lingion/sleepy/data/jw/`。
> 生成时间：2026-06-19。源码已下载到 `/tmp/wakeup_parsers/`。

---

## 0. 关键发现（先读这段）

1. **sleepy 目前的"已有"是假实现**：`JwImportViewModel.parseHtml()`（第 80–92 行）对 `zf / zf_new / zf_1 / qz_br / qz_with_node / qz_old` **全部 fallback 到 `JwQzParser(html)`**，注释明确写"先 fallback，后续补"。正方（ZF）和强智带节次变体用 QZ 基础解析器基本都会解析失败或数据错乱。**这才是本次要补的真实缺口。**
2. **sleepy 的基类是 `JwParser`**（输出 `List<JwCourse>`），不是 WakeUp 的 `Parser`（输出 `List<Course>` 再转 bean）。`JwCourse` 字段与 WakeUp `Course` 一一对应，已存在。新 parser 全部 `extends JwParser` 即可。
3. **Jsoup 已是 sleepy 依赖**（`JwQzParser` 已 `import org.jsoup.Jsoup`），无需新增依赖。
4. **`Common` 工具类 sleepy 没有**——WakeUp 各 parser 大量依赖 `Common.nodePattern / weekPattern / chineseWeekList / otherHeader / courseProperty / parseHeaderNodeString / getWeekFromChinese / countStr / getNodeStr / weekIntList2WeekBeanList`。sleepy 需要新建一个等价工具对象（建议 `JwCommon`）或在各 parser 内联。**这是本次实现的隐性前置工作。**
5. sleepy `schools.json` 当前仅 30 所学校，**bnuz / qz_old / hniu 在 sleepy 中 0 所学校使用**（但 WakeUp 主仓库有），实现优先级最低。

---

## 1. 总览表

| # | type 常量 | 教务系统 | 中文描述 | 输入格式 | 复杂度 | sleepy 使用校数 | 实现优先级 |
|---|-----------|----------|----------|----------|--------|----------------|-----------|
| 1 | `zf` (+`zf_1`) | 正方教务（旧版） | 传统 Table1 网格课表 | HTML `<table id="Table1">` | **复杂** | zf=3, zf_1=1（共4）| 🔴 高 |
| 2 | `zf_new` | 正方教务（新版） | div + festival 节次 + title 属性 | HTML `<table id="table1">` | 中等 | 5 | 🔴 高 |
| 3 | `pku` | 北京大学 | PKU 自有系统 elective | HTML `<table class="datagrid">` | 中等 | 1 | 🟡 中 |
| 4 | `cf` | 青果教务（程坊） | 内嵌 JS 变量 `var kbxx` | JSON（JS 变量提取）| 简单 | 1 | 🟡 中 |
| 5 | `bnuz` | 北师大珠海分校 | BNUZ 自有 | HTML `<table id="table1">` | 中等 | 0 | 🟢 低 |
| 6 | `hnust` | 湖南科技大学 / 东北石油 | kbtable + div + `<br>` 拆分 | HTML `<table id="kbtable">` | 中等 | 1 (东北石油) | 🟡 中 |
| 7 | `qz_br` | 强智（`<br>` 课程名变体） | 仅改课程名提取 | 继承 QzParser | **极简** | 4 | 🟢 低（但量大）|
| 8 | `qz_with_node` | 强智（带节次） | override convert 提取节点 | 继承 QzParser | 中等 | 5 | 🔴 高 |
| 9 | `qz_old` | 强智旧版 | `[周][节]` 文本解析 | HTML `<table id="kbtable">` | 中等 | 0 | 🟢 低 |
| 10 | `hniu` | 湖南信息职院 | bordercolordark 表格 | HTML 表格 | 中等 | 0 | 🟢 低 |

> **优先级依据**：sleepy 使用校数 + 当前 fallback 失效程度。`zf*`、`qz_with_node`、`qz_br` 合计覆盖 15 所学校（占 sleepy 50%），且全部走错误 fallback，必须最先补。

---

## 2. 前置工作：新建 `JwCommon` 工具对象（必须先做）

WakeUp 的 `Common.kt`（227 行）被几乎所有 parser 引用。sleepy 需移植以下成员到 `data/jw/JwCommon.kt`：

```kotlin
object JwCommon {
    // —— 正则（4 个）——
    val nodePattern  = Regex("""\(\d{1,2}[-]*\d*节""")     // (1-2节 / (3节
    val nodePattern1 = Regex("""\d{1,2}[~]*\d*节""")        // 1~2节（PKU 用）
    val weekPattern  = Regex("""\{第\d{1,2}[-]*\d*周""")    // {第2-16周
    val weekPattern2 = Regex("""\d{1,2}周""")               // 16周（HNUST/OldQz 探测用）

    // —— 常量表 ——
    val chineseWeekList = arrayOf("","周一","周二","周三","周四","周五","周六","周日")
    val otherHeader = arrayOf("时间","星期一".."星期日","早晨","上午","下午","晚上")
    val courseProperty = arrayOf("任选","限选","必修","选修","专基",.../* 约 50 个 */)

    // —— 函数 ——
    fun parseHeaderNodeString(str): Int          // "第3节" -> 3
    fun getWeekFromChinese(chineseWeek): Int      // "周三" -> 3
    fun countStr(str, sub): Int                   // 子串出现次数（ZF 用）
    fun getNodeStr(node): String / getNodeInt(s)  // 1<->"一"
    fun weekIntList2WeekBeanList(List<Int>): List<WeekBean>  // [1,3,5] -> 奇数周合并（CF 用）
}
```

> `weekIntList2WeekBeanList` 仅 ChengFangParser 用，可单独实现。`courseProperty`（约 50 个课程属性词）仅 ZhengFangParser 用。

---

## 3. 逐个 Parser 实现规格

---

### 3.1 ZhengFangParser（type=`zf`，subtype `zf_1`=type1）🔴 高优先

**教务系统**：正方教务管理系统（旧版/经典版）。
**sleepy 学校**：`zf`=3（北京信息科技大学、华南农业大学、杭州电子科技大学）+ `zf_1`=1（福建农林大学）= **4 所**。

**HTML 结构**：
```
<table id="Table1">
  <tr>
    <td>时间</td><td>星期一</td>...   <!-- 表头：otherHeader 词 -->
    <td>第一节</td>                     <!-- 行首：第N节（中文/数字）-->
    <td>...课程HTML...</td>             <!-- 数据格 -->
  </tr>
</table>
```
- 数据格内为 `<a>` 链接包裹，多门课用 `<br><br>`（异常情况 `<br><br><br>`）分隔。
- 每段课程按 `<br>` 拆成数组：`[课程名, (课程属性?), 时间, 老师, 教室]`，字段数 3~5 不定。
- 时间字符串形如 `周三第1,2节{第2-16周|单周}`。

**核心逻辑（伪代码）**：
```
doc = Jsoup.parse(source)
trs = doc.#Table1.所有tr
node = -1; countFlag=false
for tr in trs:
    countDay = 0
    for td in tr.所有td:
        txt = td.text.trim
        if len(txt)<=1: if countFlag: countDay++; continue
        if txt in otherHeader: continue              # 跳过表头
        nodeResult = parseHeaderNodeString(txt)       # "第3节"->3
        if nodeResult != -1: node=nodeResult; countFlag=true; continue
        countDay++
        if type==0:  # zf：HTML 模式
            parseImportBean(countDay, td.html, node)  # 按a标签+br拆
        else:        # zf_1：纯文本模式
            parseImportBean1(countDay, td.text, node) # 按空格+花括号拆
```

**`parseImportBean`（type0，HTML 模式）要点**：
- 用 `</td>` 截断，按 `<br><br>` 或 `<br><br><br>`（异常）拆课程段。
- 每段：`substringAfter("\">").substringBeforeLast("</a>")` 去链接标签，再按 `<br>` 拆数组。
- **数组字段位置依赖 `split[1]` 是否在 `courseProperty` 表中**：
  - 是属性 → name=split[0], time=split[2], teacher=split[3], room=split[4]（3 元素时无 teacher）
  - 非属性 → name=split[0], time=split[1], teacher=split[2], room=split[3]（3 元素特殊处理）

**`parseImportBean1`（type1，zf_1，空格模式）要点**：
- 按 `" "` 拆 token，遍历找含 `{...}` 的时间 token，回溯取课程名（考虑属性占位），前向取老师/教室。

**`parseTime`（时间解析，最复杂）**：
```
result = [day, step, startWeek, endWeek, type]
# day：time[0]=='周' 则 getWeekFromChinese(前2字)；否则回溯从 source 数 "Center" 出现次数定位列
# step：优先 "节/" 前数字；否则数逗号数+1；否则匹配 nodePattern 取 起止节算差
# 周数：weekPattern 匹配 {第2-16周 -> startWeek/endWeek
# 单双周：含"单周"->1, "双周"->2
```
> ⚠️ **day 回溯逻辑**（`source.indexOf(">第N节")` → 数 "Center"）非常 hacky，是对 colspan 合并格的补偿。建议移植时保留原逻辑。

**辅助依赖**：`JwCommon` 全套（parseHeaderNodeString / otherHeader / courseProperty / getWeekFromChinese / countStr / getNodeStr / nodePattern / weekPattern / chineseWeekList）。需自定义 `ImportBean` 中间结构（6 字段）。

**复杂度**：🔴 **复杂**（249 行，2 套分支 + 时间 hack + 列回溯）。是 10 个里最难的一个。

---

### 3.2 NewZFParser（type=`zf_new`）🔴 高优先

**教务系统**：正方教务新版（zf 新版，modern UI）。
**sleepy 学校**：**5 所**（安徽信息工程学院、北京化工大学、福建工程学院、华中农业大学、华中师范大学）。

**HTML 结构**：
```
<table id="table1">  <!-- 注意小写 table1（ZF 是大写 Table1）-->
  <tr>
    <td class="festival">1</td>  <!-- 节次号，class="festival" -->
    <td id="1-1-1">              <!-- id 首字符=星期几 -->
      <div class="kbcontent">
        <span class="title">课程名</span>
        <p title="教师">张三</p>
        <p title="上课地点">A101</p>
        <p title="节/周">(1-2节)1-16周,3-14周(单)</p>  <!-- 或 title="周/节" -->
      </div>
    </td>
  </tr>
</table>
```

**核心逻辑**：
```
trs = doc.#table1.所有tr
for tr in trs:
    nodeStr = tr.getElementsByClass("festival").text
    if nodeStr.isEmpty(): continue
    node = nodeStr.toInt
    for td in tr.所有td:
        for div in td.所有div:
            courseName = div.getElementsByClass("title").text; if empty continue
            day = td.attr("id")[0].toInt            # id 首字符即星期
            for p in div.所有p:
                switch p.attr("title"):
                    "教师" -> teacher
                    "上课地点" -> room
                    "节/周"|"周/节" -> timeStr; 解析 (起-止节) 和 周次列表
            # timeStr 解析：nodePattern 提取 (1-2节) 重置 node/step
            # 周次：去掉"(X节)"后按逗号拆，每段 "1-16周"/"3-14周(单)"
            for weekItem in weekList:
                含'-' -> 拆 startWeek/endWeek；含'单'->1/'双'->2
                否则 -> 单周
                add Course
```

**辅助依赖**：`JwCommon.nodePattern`。**比 ZF 简单很多**（结构化 div+p，无需列回溯）。
**复杂度**：🟡 **中等**（117 行）。建议作为 ZF 系列第一个实现。

---

### 3.3 PekingParser（type=`pku`）🟡 中

**教务系统**：北京大学 elective 选课系统。
**sleepy 学校**：**1 所**（北京大学）。

**HTML 结构**：
```
<table class="datagrid">
  <tbody>
    <tr>
      <td>课程名</td><td>..</td><td>..</td><td>..</td><td>教师</td>
      <td>..</td><td>..</td><td>时间HTML(多行br)</td>...
      <!-- 固定列：tds[0]=课程名 tds[4]=教师 tds[7]=时间 tds[8]=状态(含"未"则跳过) -->
    </tr>
  </tbody>
</table>
```
- **每行一门课**（非网格课表），时间格 `tds[7].html()` 按 `<br>` 拆多个时间段。
- 时间段格式：`1~16周 周三1~2节 教室` 或 `1~16周 单 周三1~2节 (教室)`。

**核心逻辑**：
```
tbody = doc.select("table.datagrid").first > tbody
for tr in tbody.所有tr:
    tds = tr.所有td
    if tds.size < 11: continue
    if tds[8].text.contains('未'): continue       # 未安排
    courseName = tds[0].text; teacher = tds[4].text
    for line in tds[7].html.split("<br>"):
        parts = Jsoup.parse(line).text.split(' ')
        if parts.size < 2: continue
        # parts[0]="1~16周" -> split('~') 取 startWeek/endWeek
        # parts[1]: 含"单"->1/"双"->2; 匹配 chineseWeekList 取 day; nodePattern1 匹配 1~2节 取 node
        # parts[2]=room（无则从 parts[1] 括号取）
        add Course
```

**辅助依赖**：`JwCommon.chineseWeekList / nodePattern1`。
**复杂度**：🟡 **中等**（75 行）。固定列索引，逻辑清晰。

---

### 3.4 ChengFangParser（type=`cf`）🟡 中

**教务系统**：青果教务 / 程坊科技（广工等）。
**sleepy 学校**：**1 所**（广东工业大学）。

**输入结构**：页面内嵌 **JavaScript 变量**，非 HTML 表格！
```javascript
var kbxx = [{"kcmc":"高等数学","jcdm2":"1,2","zcs":"1,2,3,4,5","xq":"3","jxcdmcs":"A101","teaxms":"张三"}, ...];
```
| JSON 字段 | 含义 |
|-----------|------|
| `kcmc` | 课程名 |
| `jcdm2` | 节次码（逗号分隔，首=起节，末=止节）|
| `zcs` | 周次码（逗号分隔的离散周数，如 "1,2,3,4,5"）|
| `xq` | 星期（1-7）|
| `jxcdmcs` | 教室 |
| `teaxms` | 教师 |

**核心逻辑**：
```
json = source.substringAfter("var kbxx = ").substringBefore(';')   # 提取 JS 变量
list = Gson.fromJson(json, Array<ChengFangInfo>)
for it in list:
    weekList = it.zcs.split(',').map(toInt).sorted
    day = it.xq.toInt
    startNode = it.jcdm2.split(',')[0].toInt
    endNode = it.jcdm2.split(',').last.toInt
    step = endNode - startNode + 1
    # 用 weekIntList2WeekBeanList 把离散周数合并成连续区间（含单双周识别）
    for wb in weekIntList2WeekBeanList(weekList):
        add Course(name=kcmc, room=jxcdmcs, teacher=teaxms, day, startNode, endNode=startNode+step-1,
                   startWeek=wb.start, endWeek=wb.end, type=wb.type)
```

**辅助依赖**：
- `JwCommon.weekIntList2WeekBeanList`（离散周数→连续区间+单双周，**必须移植**，约 45 行）。
- `WeekBean(start,end,type)` 数据类。
- JSON 解析：sleepy 用 `kotlinx.serialization`（已有），无需 Gson。定义 `@Serializable data class ChengFangInfo(...)`。
**复杂度**：🟢 **简单**（40 行主体），难点全在 `weekIntList2WeekBeanList`。

---

### 3.5 BNUZParser（type=`bnuz`）🟢 低

**教务系统**：北京师范大学珠海分校。
**sleepy 学校**：**0 所**（sleepy 暂无此校）。

**HTML 结构**：`<table id="table1">`，与 NewZF 类似但格式不同：
- 行首 td 为纯数字（节次），靠 `Pattern.matches("\\d+", txt)` 识别。
- 单元格内：`<span>...</span>` 后接课程信息，按 `<br>` 拆。
- 格式：`课程名` / `老师{周次}` / `教室(节)` —— 老师与周次在同一段（`老师{1,3-5}`），节次从教室段括号取。

**核心逻辑**：
```
trs = doc.#table1.所有tr; node=0; countDay=1; countFlag=false
for tr in trs:
    for td in tr.所有td:
        v = td.text.trim
        if v in otherHeader: continue
        if v.isEmpty: if countFlag: countDay++; continue
        if v 是纯数字: node=v.toInt; countFlag=true; continue
        infos = td.html.substringAfter("</span>").substringBeforeLast("<br>").split("<br>")
        courseName = infos[0]
        for i in 1..infos.size step 2:           # 成对：[老师{周}, 教室(节)]
            teacher = infos[i].substringBefore('{')
            room = infos[i+1]
            step = room.substringAfterLast('(').substringBeforeLast('节').toInt
            weekList = infos[i].substringAfter('{').substringBefore('}').split(',')
            for w in weekList:  # "1-16" 或 "3"；含单/双判 type
                add Course(day=countDay, node, node+step-1, ...)
        countDay++
```

**辅助依赖**：`JwCommon.otherHeader`。两个 `Pattern`（weekRange/单周）。
**复杂度**：🟡 中等（98 行）。sleepy 0 校使用，可最后做。

---

### 3.6 HNUSTParser（type=`hnust`）🟡 中

**教务系统**：湖南科技大学 / 东北石油大学 / 湖南科大潇湘学院。
**sleepy 学校**：**1 所**（东北石油大学）。

**HTML 结构**：`<table id="kbtable">`（与 QZ 同表），但课程在 `<div>` 内，靠 `style="display: none;"` 区分显隐（有 `oldQzType` 参数控制正反逻辑）。
- div.html 按 `<br>` 拆：`[课程名, ..., 老师, 时间(含周), 教室, ...]`。
- **时间靠探测定位**：遍历 split，用 `weekPattern2`（`\d+周`）匹配到的索引为 `preIndex`，则 teacher=split[preIndex-1]、room=split[preIndex+1]。
- 节次来自 `div.attr("id")`：`id.split('-')[0].toInt()*2-1`（即第 N 个时间槽的起始节，固定 step=2）。
- 周次：时间串按 `,` 拆，每段 `substringBefore('周')` 取 `start-end`。

**核心逻辑**：
```
trs = doc.#kbtable.所有tr
for tr in trs:
    day = -1
    for td in tr.所有td:
        day++
        for div in td.所有div:
            if oldQzType==0: skip if style!="display: none;" (取隐藏项)
            else:            skip if style=="display: none;"  (取显示项)
            split = div.html.split("<br>")
            preIndex = -1
            for i in 0..split.size:
                if weekPattern2 含于 split[i]:
                    if preIndex!=-1: toCourse()  # 上一门课收尾
                    preIndex = i
                if 末元素: toCourse()
    # toCourse(): courseName=split[0], room=split[preIndex+1], teacher=split[preIndex-1]
    #   时间 split[preIndex] 按","拆周次; startNode=div.id.split('-')[0]*2-1; endNode=startNode+1
```

**辅助依赖**：`JwCommon.weekPattern2`。构造参数 `oldQzType: Int`。
**复杂度**：🟡 中等（79 行）。需注意 `oldQzType` 控制显隐方向。

---

### 3.7 QzBrParser（type=`qz_br`）🟢 低（极简）

**教务系统**：强智教务（`<br>` 课程名变体，如北京林业大学）。
**sleepy 学校**：**4 所**（北京林业、长春大学、长沙理工、广东金融）。

**实现**：**仅 8 行**，直接继承 `JwQzParser`，只 override `parseCourseName`：
```kotlin
class JwQzBrParser(source: String) : JwQzParser(source) {
    override fun parseCourseName(infoStr: String): String =
        infoStr.substringBefore("<br>").trim()
}
```
原版 `JwQzParser.parseCourseName` 取 `substringBefore("<font")`；此变体课程名后跟 `<br>` 而非 `<font>`，故只改这一处。

**复杂度**：🟢 **极简**。sleepy 当前 fallback 到 JwQzParser（课程名提取错），只需新建这一个 8 行类并改 dispatch。
**注意**：sleepy `JwQzParser.parseCourseName` 已是 `open`，可直接继承。

---

### 3.8 QzWithNodeParser（type=`qz_with_node`）🔴 高

**教务系统**：强智教务（带节次信息变体，如北邮、北理工、广外、海南大学）。
**sleepy 学校**：**5 所**（北京邮电、北京理工、长沙医学院、广外、海南大学）。

**与 QzParser 的差异**：override `convert()`，**节点不固定为 `nodeCount*2-1`，而是从页面 `title="周次(节次)"` 提取真实节次**。课程名提取也更复杂（多 font/span）。

**核心逻辑（convert override）**：
```
courseName = Jsoup.parse(infoStr.substringBefore("<font").substringBefore("<span>").trim()).text
teacher = html.getElementsByAttributeValue("title","老师").text
room = html.["title","教室"].text + html.["title","分组"].text
tempStr = html.["title","周次(节次)"].text    # 形如 "1-16周(周) 1-2节" 或 "1-16周"
# 三分支解析周次/节次（tempStr 含空格 / 为空 / 含括号）：
if tempStr 含空格:
    weekStr = tempStr.split(' ')[0]; nodeList = split(' ')[1].去掉"[]".split('-')
elif tempStr 为空:
    weekStr = html.["title","周次"].text
    nodeList = html.["title","节次"].text.substringAfter(')').去掉"[]".split('-')
else:
    weekStr = tempStr.substringBefore(')')
    nodeList = tempStr.substringAfter(')').去掉"[]".split('-')
# 周次 weekStr 按','拆，含'-'取区间，尾含"单"/"双"判 type
# node: nodeList.first.substringBefore('节') 起 / nodeList.last 止
for w in weekList: add Course(startNode, endNode, ...)
```

**关键差异点**：`startNode/endNode 来自 nodeList`（而非 Qz 的固定双节），因此可正确处理跨节课。

**辅助依赖**：无额外（复用 JwQzParser 的 generateCourseList/tableName）。
**复杂度**：🟡 中等（71 行 convert）。但解析分支多、容错复杂。sleepy 当前 fallback 节点全错（都按 `nodeCount*2-1`），**必须修**。

---

### 3.9 OldQzParser（type=`qz_old`）🟢 低

**教务系统**：强智教务旧版。
**sleepy 学校**：**0 所**。

**HTML 结构**：`<table id="kbtable">`，div 内 `<br>` 拆分，时间格式 `[周][节]`：
- 单元格内容：`课程名<br>老师<br>1-16周[1-2节]<br>教室`。
- 时间段靠探测含 `[`+`]`+`周`+`节` 的行定位（`preIndex`），teacher=split[preIndex-1]、room=split[preIndex+1]。
- 时间解析：`split("周[")` → `[startWeek-endWeek, startNode-endNode节]`。

**核心逻辑**：与 HNUST 几乎同构（kbtable + br + preIndex 探测），区别：
- 探测条件是 `[`+`]`+`周`+`节`（HNUST 用 `weekPattern2`）。
- 节次直接从时间串 `[1-2节]` 取（HNUST 从 div.id 算）。
- 不涉及 display:none 显隐。

**复杂度**：🟡 中等（73 行）。sleepy 0 校，优先级最低。

---

### 3.10 HNIUParser（type=`hniu`）🟢 低

**教务系统**：湖南信息职业技术学院。
**sleepy 学校**：**0 所**。

**HTML 结构**：无 id 的表格，靠属性定位：
```
tbody = doc.getElementsByAttributeValue("bordercolordark","#FFFFFF")[0] > tbody
```
- 行内 td 靠 `align=="center"` 跳过、`valign=="top"` 计 day。
- 单元格 `td.html` 按 `<br>` 拆。格式：`课程名 / 老师 [周次][节次] / 教室`。
- 时间串 `[1,3-5周][1-2节]`：按 `"周]["` 拆出周次段和节次段。
- 单格多课：若 split.size>4，按含 `[周]节` 的行二次切分。

**核心逻辑（convertHNIU）**：
```
courseName = source[0].split(' ')[0]
teacher = source[1].split(' ')[0]
room = source[2]（空则取 source[1] 末 token）
timeStr = source[1].substringAfter('[').substringBeforeLast('节')
weekList = timeStr.split("周][")[0].split(", ","，")  # 周次
nodeStr = timeStr.split("周][")[1]                    # 节次 "1-2"
startNode, step = parseRange(nodeStr)
for w in weekList: 含'-'取区间 else 单周; add Course(type=0)
```

**辅助依赖**：无（纯字符串处理）。
**复杂度**：🟡 中等（101 行）。sleepy 0 校，最低优先级。

---

## 4. 实现顺序建议（按 ROI）

| 阶段 | 任务 | 工作量 | 覆盖 sleepy 学校 |
|------|------|--------|-----------------|
| **0** | 新建 `JwCommon.kt`（移植正则/常表/函数）| 0.5 天 | 前置 |
| **1** | `JwQzBrParser`（8 行）+ 改 dispatch | 0.1 天 | 4 |
| **2** | `JwQzWithNodeParser`（override convert）| 0.5 天 | 5 |
| **3** | `JwNewZFParser`（div+festival）| 0.5 天 | 5 |
| **4** | `JwZhengFangParser`（含 zf/zf_1 双模式）| 1.5 天 | 4 |
| **5** | `JwPekingParser`（固定列）| 0.3 天 | 1 |
| **6** | `JwChengFangParser` + `weekIntList2WeekBeanList` | 0.5 天 | 1 |
| **7** | `JwHnustParser` | 0.3 天 | 1 |
| **8** | `JwBnuzParser` / `JwOldQzParser` / `JwHniuParser`（0 校，可选）| 各 0.3 天 | 0 |
| **合计** | 阶段 0–7 | ~4.2 天 | **21/30 所** |

**dispatch 修改点**：`JwImportViewModel.kt` 第 83–90 行，把 fallback 的 `JwQzParser(html)` 逐个换成对应新类。

---

## 5. 数据结构对照（sleepy 已有，无需新建）

| WakeUp | sleepy | 说明 |
|--------|--------|------|
| `Course` | `JwCourse` | ✅ 字段完全一致（name/room/teacher/day/startNode/endNode/startWeek/endWeek/type）|
| `Parser`（abstract）| `JwParser`（abstract）| ✅ `generateCourseList()` 签名一致，输出类型换 JwCourse |
| `ImportBean`（ZF 内部）| 需在 ZF parser 内部定义 | 6 字段中间结构 |
| `WeekBean`（CF 用）| 需在 JwCommon 或 CF parser 内定义 | start/end/type |
| `ChengFangInfo` | 用 `@Serializable data class` | 6 字段，kotlinx.serialization |

---

## 6. 风险与注意事项

1. **ZhengFangParser 的 day 回溯 hack**：`source.indexOf(">第N节")` + `countStr("Center")` 依赖原始 HTML 的 colspan 结构，移植后必须用真实正方课表 HTML 验证，否则星期会错。
2. **ChengFang 的 `var kbxx =` 提取**：用 `substringAfter/substringBefore`，若学校 JS 变量名/格式微调会断。建议加正则兜底。
3. **QzWithNode 的三分支**（tempStr 含空格/为空/含括号）覆盖不同强智版本，缺一会导致部分学校解析空。需分别测试。
4. **Jsoup `.first()` 空指针**：WakeUp 原版多处 `.first()` 未判空（PKU/CF/HNIU）。sleepy 移植时应加 `?: return emptyList()` 防御。
5. **sleepy 用 `toIntOrNull()` 更安全**：WakeUp 大量直接 `.toInt()`，移植物建议统一改 `toIntOrNull() ?: 默认值`（sleepy JwQzParser 已这么做）。

---

*报告完。源码原件存于 `/tmp/wakeup_parsers/`（10 个 parser + Common.kt + QzParser.kt + Course.kt + ChengFangInfo.kt）。*
