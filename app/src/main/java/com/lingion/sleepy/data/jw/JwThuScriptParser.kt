package com.lingion.sleepy.data.jw

import org.json.JSONObject
import org.jsoup.Jsoup

/** THU script records. Optional semester JSON must have been captured separately by the caller. */
class JwThuScriptParser(source:String, private val semesterDataJson:String?=null):JwParser(source) {
    companion object {
        fun validSemesterData(raw:String):Boolean = JwThuScriptParser("",raw).semester()!=null
    }
    var skippedCourseCount: Int = 0
        private set
    private data class Move(val week:Int,val day:Int,val targetWeek:Int=0,val targetDay:Int=0)
    private data class Semester(val weeks:Int=16,val moves:List<Move> = emptyList())
    private data class Metadata(val name:String="",val id:String="",val teacher:String="",val note:String="")
    private data class Pending(var id:String="",var name:String="",val params:MutableList<String> = mutableListOf(),
        var room:String="",var weeks:String="",var time:String="",var special:Boolean=false)
    private val starts=intArrayOf(0,1,3,6,8,10,12)
    private val ends=intArrayOf(0,2,5,7,9,11,14)
    private val courseKinds=setOf("任选","专必","通必","公共基础必修课","学科基础必修课","公共基础选修课","学科基础选修课",
        "限选","实践","实践选修","必修课","选修课","必修","选修","专基","专选","公必","公选","义修","选","必","主干",
        "专限","公基","值班","通选","思政必","思政选","自基必","自基选","语技必","语技选","体育必","体育选","专业基础课",
        "双创必","双创选","新生必","新生选","学科必修","学科选修","通识必修","通识选修","公共基础","第二课堂","学科实践",
        "专业实践","专业必修","辅修","专业选修","外语","方向","专业必修课","全选","专业","学必","学选")

    override fun confidence():Int = if("function setInitValue" in source && "getElementById" in source) 100 else 0

    override fun generateCourseList():List<JwCourse> {
        skippedCourseCount = 0
        val semester=semester() ?: return emptyList()
        val scripts=Jsoup.parse(source).select("script")
        val script=withoutComments(if(scripts.isEmpty()) source else scripts.joinToString("\n") { it.data() })
        val metadata=metadata(script)
        val start=Regex("function\\s+setInitValue\\s*\\(\\s*\\)").find(script)?.range?.first ?: return emptyList()
        val block=script.substring(start,Regex("Event\\.observe").find(script,start)?.range?.first ?: script.length)
        val token=Regex("""(strHTML1|strHTML)\s*(\+=|=)\s*("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(?:document\.)?getElementById\(\s*['"]a([1-6])_([1-7])['"]\s*\)\.innerHTML\s*\+=\s*strHTML""")
        val result=mutableListOf<JwCourse>()
        val found=mutableMapOf<String,Pending>()
        var pending=Pending()
        var active=false
        var inherited=false
        for(match in token.findAll(block)) {
            if(match.groupValues[4].isNotEmpty()) {
                if(active) {
                    val emitted=emit(pending,match.groupValues[4].toInt(),match.groupValues[5].toInt(),metadata,semester)
                    if(emitted==null) skippedCourseCount++ else {
                        result += emitted
                        if(pending.id.isNotBlank()) found[pending.id]=pending.copy(params=pending.params.toMutableList())
                    }
                }
                pending=Pending()
                active=false; inherited=false
                continue
            }
            val target=match.groupValues[1]
            val value=decode(match.groupValues[3]) ?: continue
            if(target=="strHTML1") {
                if(pending.name.isNotBlank()) {
                    if(inherited) { pending.params.clear(); inherited=false }
                    pending.params += text(value).trimStart('；',';').trim()
                }
                continue
            }
            if("blue_red_none" in value) {
                if(active) skippedCourseCount++
                pending=Pending(id=identifier(value),special=true)
                active=true; inherited=false
                val fonts=Jsoup.parseBodyFragment(value).select("font[color=blue]")
                pending.name=fonts.getOrNull(0)?.text().orEmpty()
                val detail=fonts.getOrNull(1)?.text().orEmpty().replace('（','(').replace('）',')')
                if('(' !in detail || !detail.endsWith(')')) continue
                val prefix=detail.substringBefore('(').trim()
                val pieces=detail.substringAfter('(').dropLast(1).split('；',';').map(String::trim)
                val rooms=mutableListOf<String>()
                for(piece in pieces) when {
                    "周" in piece -> pending.weeks=piece
                    piece.startsWith("时间：") || piece.startsWith("时间:") -> pending.time=piece.drop(3)
                    piece.isNotBlank() -> rooms+=piece
                }
                pending.room=rooms.joinToString("；")+if(prefix.isNotBlank()) "($prefix)" else ""
            } else if("<a " in value) {
                if(active) skippedCourseCount++
                val id=identifier(value)
                pending=found[id]?.copy(params=found.getValue(id).params.toMutableList()) ?: Pending(id=id)
                active=true; inherited=found.containsKey(id)
            } else if("<b>" in value) {
                pending.name=Jsoup.parseBodyFragment(value).selectFirst("b")?.text().orEmpty()
            } else if(match.groupValues[2]=="=" && value.isBlank()) {
                if(active) skippedCourseCount++
                pending=Pending(); active=false
            }
        }
        if(active) skippedCourseCount++
        return result.distinct()
    }

    private fun emit(p:Pending,big:Int,day:Int,metadata:List<Metadata>,semester:Semester):List<JwCourse>? {
        if(p.name.isBlank()) return null
        val byId=metadata.filter { p.id.isNotBlank() && it.id==p.id }.distinct()
        val byName=metadata.filter { it.name==p.name }.distinct()
        val info=byId.singleOrNull() ?: byName.singleOrNull() ?: Metadata()
        var teacher=info.teacher
        var room=p.room
        var weekText=p.weeks
        if(!p.special) {
            val weekIndex=p.params.indexOfLast { knownWeeks(it,semester.weeks)!=null }
            if(weekIndex<0) return null
            weekText=p.params[weekIndex]
            room=p.params.drop(weekIndex+1).filter(String::isNotBlank).joinToString("；")
            val before=p.params.take(weekIndex).filter { it !in courseKinds && it.isNotBlank() }
            if(before.isNotEmpty()) teacher=before.joinToString("；")
        }
        val weeks=knownWeeks(weekText,semester.weeks) ?: return null
        val clock=if(p.time.isNotBlank()) {
            val time=p.time.replace('：',':').replace('～','-').replace('~','-').trim()
            val parts=time.split('-')
            if(parts.size!=2) return null
            val first=clock(parts[0]) ?: return null
            val last=clock(parts[1]) ?: return null
            if(!JwCourse.isValidTimeRange(first,last)) return null
            first to last
        } else "" to ""
        if(semester.moves.isEmpty()) {
            val slots=weekSlots(weekText,semester.weeks) ?: return null
            return slots.map { (first,last,kind) -> JwCourse(p.name,room,teacher,day,starts[big],ends[big],first,last,kind,
                startTime=clock.first,endTime=clock.second,note=info.note) }
        }
        val original=weeks.map { it to day }.toSet()
        val destinations=semester.moves.filter { it.targetWeek>0 }.map { it.targetWeek to it.targetDay }.toSet()
        val moves=semester.moves.associateBy { it.week to it.day }
        val occurrences=original.filter { it !in destinations && it !in moves }.toMutableSet()
        for((coordinate,move) in moves) if(coordinate in original && move.targetWeek>0) occurrences+=move.targetWeek to move.targetDay
        return occurrences.groupBy({it.second},{it.first}).toSortedMap().flatMap { (weekday,values) ->
            contiguous(values).map { (first,last) ->
                // The source mistakenly derives credit from the last digit of the course identifier.
                JwCourse(p.name,room,teacher,weekday,starts[big],ends[big],first,last,
                    startTime=clock.first,endTime=clock.second,note=info.note)
            }
        }
    }

    private fun weekSlots(raw:String,count:Int):List<Triple<Int,Int,Int>>? {
        val slots=when(raw.trim()) {
            "全周" -> listOf(Triple(1,count,0))
            "前八周" -> listOf(Triple(1,minOf(8,count),0))
            "后八周" -> if(count>=9) listOf(Triple(9,count,0)) else emptyList()
            "单周" -> listOf(Triple(1,count,1))
            "双周" -> if(count>=2) listOf(Triple(2,count,2)) else emptyList()
            else -> schoolWeekRanges(raw.trim(),trailingParityAppliesToAll=true)
        }
        return slots.mapNotNull { (first,last,kind) ->
            val adjusted=if(kind!=0 && first%2!=kind%2) first+1 else first
            if(adjusted<=last) Triple(adjusted,last,kind) else null
        }.takeIf { it.isNotEmpty() }
    }

    private fun knownWeeks(raw:String,count:Int):List<Int>? {
        val text=raw.trim()
        return when(text) {
            "全周" -> (1..count).toList()
            "前八周" -> (1..minOf(8,count)).toList()
            "后八周" -> (9..count).toList()
            "单周" -> (1..count step 2).toList()
            "双周" -> (2..count step 2).toList()
            else -> {
                val ranges=schoolWeekRanges(text,trailingParityAppliesToAll=true)
                if(ranges.isEmpty()) null else ranges.flatMap { (first,last,kind) ->
                    (first..last).filter { kind==0 || it%2==kind%2 }
                }.distinct().sorted()
            }
        }
    }

    private fun contiguous(values:List<Int>):List<Pair<Int,Int>> {
        val result=mutableListOf<Pair<Int,Int>>()
        for(value in values.distinct().sorted()) {
            val last=result.lastOrNull()
            if(last!=null && last.second+1==value) result[result.lastIndex]=last.first to value else result+=value to value
        }
        return result
    }

    private fun semester():Semester? {
        val json=semesterDataJson ?: return Semester()
        return runCatching {
            val objectValue=JSONObject(json)
            fun integer(value:Any):Int {
                require(value is Number && value.toDouble()==value.toInt().toDouble())
                return value.toInt()
            }
            val count=if(objectValue.isNull("weekCount")) 16 else integer(objectValue.get("weekCount"))
            require(count in 1..366)
            val rows=objectValue.optJSONArray("reschedule")
            require(objectValue.isNull("reschedule") || rows!=null)
            val moves=if(rows==null) emptyList() else (0 until rows.length()).map { index ->
                val row=rows.getJSONArray(index)
                require(row.length()==2 || row.length()==4)
                val week=integer(row.get(0)); val day=integer(row.get(1))
                require(week in 1..366 && day in 1..7)
                if(row.length()==2) Move(week,day) else {
                    val targetWeek=integer(row.get(2)); val targetDay=integer(row.get(3))
                    require((targetWeek==0 && targetDay==0) || (targetWeek in 1..366 && targetDay in 1..7))
                    Move(week,day,targetWeek,targetDay)
                }
            }
            require(moves.groupBy { it.week to it.day }.values.none { it.distinct().size>1 })
            Semester(count,moves.distinct())
        }.getOrNull()
    }

    private fun clock(raw:String):String? {
        val parts=raw.trim().split(':')
        if(parts.size!=2 || parts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) return null
        val hour=parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute=parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour.toString().padStart(2,'0')+":"+minute.toString().padStart(2,'0')
    }

    private fun identifier(value:String):String =
        Regex("(?:;|[?&]p_id=)([0-9A-Z]{8})(?=['\"&#?<>]|$)").find(value)?.groupValues?.get(1).orEmpty()

    private fun metadata(script:String):List<Metadata> {
        val columns=array(script,"gridColumns")?.let(::split) ?: return emptyList()
        val name=columns.indexOfFirst { "课程名" in it }
        val teacher=columns.indexOfFirst { "任课教师" in it }
        val note=columns.indexOfFirst { "选课文字说明" in it }
        if(name<0) return emptyList()
        val rows=array(script,"gridData")?.let(::split) ?: return emptyList()
        val result=mutableListOf<Metadata>()
        for(row in rows) {
            if(!row.trim().startsWith('[') || !row.trim().endsWith(']')) continue
            val fields=split(row.trim().drop(1).dropLast(1)).map { decode(it.trim()) ?: it.trim() }
            val key=fields.getOrNull(name)?.takeIf(String::isNotBlank) ?: continue
            result+=Metadata(text(key),identifier(key),fields.getOrNull(teacher).orEmpty(),fields.getOrNull(note).orEmpty())
        }
        return result
    }

    private fun array(script:String,name:String):String? {
        val match=Regex("\\bvar\\s+$name\\s*=\\s*\\[").find(script) ?: return null
        val start=match.range.last+1
        var depth=1; var quote:Char?=null; var escape=false
        for(index in start until script.length) {
            val c=script[index]
            if(quote!=null) {
                if(escape) escape=false else if(c=='\\') escape=true else if(c==quote) quote=null
            } else if(c=='\'' || c=='"') quote=c else if(c=='[') depth++ else if(c==']' && --depth==0) return script.substring(start,index)
        }
        return null
    }

    private fun split(value:String):List<String> {
        val result=mutableListOf<String>(); var start=0; var depth=0; var quote:Char?=null; var escape=false
        for((index,c) in value.withIndex()) {
            if(quote!=null) {
                if(escape) escape=false else if(c=='\\') escape=true else if(c==quote) quote=null
            } else when(c) {
                '\'','"' -> quote=c
                '[','{' -> depth++
                ']','}' -> depth--
                ',' -> if(depth==0) { result+=value.substring(start,index); start=index+1 }
            }
        }
        result+=value.substring(start)
        return result
    }

    private fun text(value:String)=Jsoup.parseBodyFragment(value).text().trim()
    private fun decode(value:String):String? {
        if(value.length<2 || value.first() !in listOf('"','\'') || value.last()!=value.first()) return null
        val result=StringBuilder(); var index=1
        while(index<value.lastIndex) {
            val c=value[index++]
            if(c!='\\') result.append(c) else {
                if(index>=value.lastIndex) return null
                when(val next=value[index++]) {
                    'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                    '\\','"','\'','/' -> result.append(next)
                    'u' -> {
                        if(index+4>value.lastIndex) return null
                        val code=value.substring(index,index+4).toIntOrNull(16) ?: return null
                        result.append(code.toChar()); index+=4
                    }
                    else -> return null
                }
            }
        }
        return result.toString()
    }

    private fun withoutComments(input:String):String {
        val out=StringBuilder(); var index=0; var quote:Char?=null; var escape=false
        while(index<input.length) {
            val c=input[index]
            if(quote!=null) {
                out.append(c)
                if(escape) escape=false else if(c=='\\') escape=true else if(c==quote) quote=null
                index++
            } else if(c=='\'' || c=='"') { quote=c; out.append(c); index++ }
            else if(input.startsWith("//",index)) {
                while(index<input.length && input[index]!='\n') { out.append(' '); index++ }
            } else if(input.startsWith("/*",index)) {
                out.append("  "); index+=2
                while(index<input.length && !input.startsWith("*/",index)) { out.append(if(input[index]=='\n') '\n' else ' '); index++ }
                if(index<input.length) { out.append("  "); index+=2 }
            } else { out.append(c); index++ }
        }
        return out.toString()
    }
}
