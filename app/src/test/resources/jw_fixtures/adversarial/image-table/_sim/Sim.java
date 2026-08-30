import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.nio.file.*;
import java.util.*;

/** Faithful port of sleepy JwQzParser / JwOldZfParser / JwUrpParser / JwNewZfParser / JwNewUrpParser / JwWiseduParser behavior on Jsoup 1.18.1. */
public class Sim {
    static final String[] OTHER_HEADER = {"时间","星期一","星期二","星期三","星期四","星期五","星期六","星期日","早晨","上午","下午","晚上"};
    static final Set<String> COURSE_PROPERTY = new HashSet<>(Arrays.asList(
        "任选","限选","实践选修","必修课","选修课","必修","选修","专基","专选","公必","公选","义修","选","必","主干","专限","公基","值班","通选","思政必","思政选","自基必","自基选","语技必"));
    static final String[] CN_WEEK = {"","周一","周二","周三","周四","周五","周六","周日"};
    static final Map<String,Integer> CN_NUM = new HashMap<>();
    static { String[] ks={"一","二","三","四","五","六","日","七","八","九","十","十一","十二","十三","十四","十五","十六","十七","十八","十九","二十"};
        int[] vs={1,2,3,4,5,6,7,7,8,9,10,11,12,13,14,15,16,17,18,19,20};
        for(int i=0;i<ks.length;i++) CN_NUM.put(ks[i],vs[i]); }

    static class Course {
        String name, room="", teacher=""; int day, startNode, endNode, startWeek, endWeek, type;
        Course(String n,String r,String t2,int d,int sn,int en,int sw,int ew,int ty){name=n;room=r==null?"":r;teacher=t2==null?"":t2;day=d;startNode=sn;endNode=en;startWeek=sw;endWeek=ew;type=ty;}
        Course(String n,int d,int sn,int en,int sw,int ew,int t){this(n,"","",d,sn,en,sw,ew,t);}
        public boolean equals(Object o){ if(!(o instanceof Course))return false; Course c=(Course)o;
            return name.equals(c.name)&&room.equals(c.room)&&teacher.equals(c.teacher)&&day==c.day&&startNode==c.startNode&&endNode==c.endNode&&startWeek==c.startWeek&&endWeek==c.endWeek&&type==c.type;}
        public String toString(){return "{\"name\":\""+name+"\",\"teacher\":\""+teacher+"\",\"room\":\""+room+"\",\"day\":"+day+",\"startNode\":"+startNode+",\"endNode\":"+endNode+",\"startWeek\":"+startWeek+",\"endWeek\":"+endWeek+",\"type\":"+type+"}";}
    }

    // ─── JwQzParser ───
    static List<Course> qz(String source, String tableName) {
        List<Course> out = new ArrayList<>();
        Document doc = Jsoup.parse(source);
        Element kb = doc.getElementById("kbtable"); if (kb == null) return out;
        Elements trs = kb.getElementsByTag("tr");
        int nodeCount = 0;
        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td"); if (tds.isEmpty()) continue;
            nodeCount++;
            int day = 0;
            for (Element td : tds) {
                day++;
                for (Element div : td.getElementsByTag("div")) {
                    Elements ce = div.getElementsByClass(tableName);
                    if (ce.text().isBlank()) continue;
                    String html = ce.html();
                    int start = 0, split = html.indexOf("-----");
                    while (split != -1) { qzConvert(out, day, nodeCount, html.substring(start, split));
                        start = html.indexOf("<br>", split) + 4; split = html.indexOf("-----", start); }
                    qzConvert(out, day, nodeCount, html.substring(start));
                }
            }
        }
        return out;
    }
    static void qzConvert(List<Course> out, int day, int nodeCount, String infoStr) {
        int node = nodeCount * 2 - 1;
        Document courseHtml = Jsoup.parse(infoStr);
        String info = substringBefore(infoStr, "<font").trim();
        String courseName = Jsoup.parse(info).text();
        String teacher = courseHtml.getElementsByAttributeValue("title","老师").text().trim();
        String room = courseHtml.getElementsByAttributeValue("title","教室").text().trim()
            + courseHtml.getElementsByAttributeValue("title","分组").text().trim();
        String weekStr = courseHtml.getElementsByAttributeValue("title","周次(节次)").text();
        if (weekStr.contains("(周)")) weekStr = weekStr.substring(0, weekStr.indexOf("(周)"));
        int sw=1, ew=1, type=0;
        for (String w : weekStr.split(",")) {
            if (w.contains("-")) {
                String[] ws = w.split("-", -1);
                sw = ws.length>0?parseIntOr(ws[0],1):1;
                if (ws.length>1) {
                    type = ws[1].contains("单")?1:(ws[1].contains("双")?2:0);
                    ew = parseIntOr(ws[1].replace("周","").replace("(","").replace(")","").trim(), sw);
                }
            } else {
                String c = w.replace("周","");
                if (c.contains("(")) c = c.substring(0, c.indexOf("("));
                int v = parseIntOr(c.trim(),1); sw=v; ew=v;
            }
            out.add(new Course(courseName,room,teacher,day,node,node+1,sw,ew,type));
        }
    }
    static int parseIntOr(String s, int d) { try { return Integer.parseInt(s.trim()); } catch(Exception e){ return d; } }
    static String substringBefore(String s, String sep) { int i = s.indexOf(sep); return i < 0 ? s : s.substring(0, i); }

    // ─── JwOldZfParser type=0 ───
    static List<Course> oldZf(String source, int type) {
        List<Object[]> beans = new ArrayList<>(); // [name,timeInfo,teacher,room,startNode,cDay]
        Document doc = Jsoup.parse(source);
        Element t1 = doc.getElementById("Table1"); List<Course> empty = new ArrayList<>();
        if (t1 == null) return empty;
        int node = -1;
        for (Element tr : t1.getElementsByTag("tr")) {
            Elements tds = tr.getElementsByTag("td");
            boolean countFlag = false; int countDay = 0;
            for (Element td : tds) {
                String cs = td.text().trim();
                if (cs.length() <= 1) { if (countFlag) countDay++; continue; }
                boolean isOther = false; for (String h : OTHER_HEADER) if (h.equals(cs)) { isOther = true; break; }
                if (isOther) continue;
                int r = headerNode(cs);
                if (r != -1) { node = r; countFlag = true; continue; }
                countDay++;
                if (type == 0) beans.addAll(importBean(td.html(), node, countDay));
                else beans.addAll(importBean1(cs, node, countDay));
            }
        }
        List<Course> out = new ArrayList<>();
        for (Object[] b : beans) {
            int[] t = parseTime((String)b[1], (Integer)b[4], source, (String)b[0]);
            int day;
            String ti = (String)b[1];
            boolean inWeek = ti.length()>=2 && Arrays.asList(CN_WEEK).contains(ti.substring(0,2));
            day = inWeek ? t[0] : (Integer)b[5];
            Course c = new Course((String)b[0], (String)b[3]==null?"":(String)b[3], (String)b[2]==null?"":(String)b[2],
                day, (Integer)b[4], (Integer)b[4]+t[1]-1, t[2], t[3], t[4]);
            out.add(c);
        }
        return out;
    }
    static int headerNode(String s) {
        if (s.matches("第.*节")) { String ns = s.substring(1, s.length()-1);
            try { return Integer.parseInt(ns); } catch (Exception e) { Integer v = CN_NUM.get(ns); return v == null ? -1 : v; } }
        return -1;
    }
    static List<Object[]> importBean(String html, int node, int cDay) {
        // JwOldZfParser: substringBeforeLast("</td>") no-op since already fragment; split by <br><br>
        String inner = html.contains("</td>") ? html.substring(0, html.lastIndexOf("</td>")) : html;
        boolean abnormal = inner.contains("<br><br><br>");
        String[] parts = abnormal ? inner.split("<br><br><br>", -1) : inner.split("<br><br>", -1);
        List<Object[]> out = new ArrayList<>();
        for (String cs : parts) {
            // substringAfter("\">").substringBeforeLast("</a>")
            String work = cs;
            if (work.contains("\">")) work = work.substring(work.indexOf("\">")+2);
            if (work.contains("</a>")) work = work.substring(0, work.lastIndexOf("</a>"));
            String[] sp = work.split("<br>", -1);
            List<String> ls = new ArrayList<>();
            for (String s : sp) { String t = s.trim(); if (t.startsWith("<a")) { int g = t.indexOf('>'); t = g>=0 ? t.substring(g+1) : t.substring(2); } ls.add(t); }
            if (ls.size() < 3) continue;
            String name, time, teacher="", room="";
            if (COURSE_PROPERTY.contains(ls.get(1))) {
                if (ls.size() == 4) { name=ls.get(0); time=ls.get(2); room=ls.get(3); teacher=""; }
                else { name=ls.get(0); time=ls.get(2); room=ls.get(4); teacher=ls.get(3); }
            } else {
                if (ls.size() == 3) { name=ls.get(0); time=ls.get(1); if (!abnormal){room=ls.get(2);teacher="";} else {teacher=ls.get(2);room="";} }
                else { name=ls.get(0); time=ls.get(1); teacher=ls.get(2); room=ls.get(3); }
            }
            out.add(new Object[]{name, time, teacher, room, node, cDay});
        }
        return out;
    }
    static List<Object[]> importBean1(String src, int node, int cDay) {
        String[] split = src.split(" +");
        List<Object[]> out = new ArrayList<>();
        int preIndex = -1; boolean hasType = false;
        for (int i = 0; i < split.length; i++) {
            if (split[i].contains("{") && split[i].contains("}")) {
                if (preIndex != -1) {
                    if (COURSE_PROPERTY.contains(split[preIndex-1])) hasType = true;
                    String name = (hasType && preIndex >= 2) ? split[preIndex-2] : split[preIndex-1];
                    Object[] t = {name, split[preIndex], "", "", node, cDay};
                    if ((i - preIndex - 2) == 1) t[2] = split[preIndex+1];
                    else { t[2] = split[preIndex+1]; t[3] = split[preIndex+2]; }
                    out.add(t); preIndex = i;
                } else preIndex = i;
            }
            if (i == split.length - 1) {
                if (preIndex == -1) continue;
                if (COURSE_PROPERTY.contains(split[preIndex-1])) hasType = true;
                String name = (hasType && preIndex >= 2) ? split[preIndex-2] : split[preIndex-1];
                Object[] t = {name, split[preIndex], "", "", node, cDay};
                if ((i - preIndex) == 1) t[2] = split[preIndex+1];
                else { t[2] = split[preIndex+1]; t[3] = split[preIndex+2]; }
                out.add(t);
            }
        }
        return out;
    }
    static java.util.regex.Pattern NODE_PAT = java.util.regex.Pattern.compile("\\(\\d{1,2}[-]*\\d*节");
    static java.util.regex.Pattern WEEK_PAT = java.util.regex.Pattern.compile("\\{第\\d{1,2}[-]*\\d*周");
    static int[] parseTime(String time, int startNode, String source, String courseName) {
        int[] r = new int[5];
        if (time.startsWith("周")) {
            String ds = time.substring(0, Math.min(2, time.length()));
            for (int i = 1; i < CN_WEEK.length; i++) if (CN_WEEK[i].equals(ds)) r[0] = i;
        }
        if (r[0] == 0) {
            int si = source.indexOf(">第"+startNode+"节</td>");
            if (si == -1) { String cn = startNode>=1&&startNode<=16 ? new String[]{"","一","二","三","四","五","六","七","八","九","十","十一","十二","十三","十四","十五","十六"}[startNode] : ""; si = source.indexOf(">第"+cn+"节</td>"); }
            int ei = 0;
            if (si != -1) ei = source.indexOf(courseName, si);
            if (si != -1 && ei != -1) r[0] = countStr(source.substring(si, ei), "Center");
        }
        int step = 0;
        if (time.contains("节/")) { int nl = time.indexOf("节/"); step = parseIntOr(time.substring(nl-1, nl), 0); }
        else if (time.contains(",")) { int loc = 0; step = 1;
            while (true) { int j = time.indexOf(",", loc); if (j == -1) break; step++; loc = j+1; } }
        else if (time.contains("第"+startNode+"节")) step = 1;
        if (step == 0) {
            java.util.regex.Matcher m = NODE_PAT.matcher(time);
            if (m.find()) { String ni = m.group(); String[] nodes = ni.substring(1, ni.length()-1).split("-");
                if (nodes.length > 1) { int s = parseIntOr(nodes[0], startNode); int e = parseIntOr(nodes[1], s); step = e - s + 1; } }
        }
        if (step == 0) step = 1;
        int sw = 1, ew = 20;
        java.util.regex.Matcher wm = WEEK_PAT.matcher(time);
        if (wm.find()) { String wi = wm.group(); String[] ws = wi.substring(2, wi.length()-1).split("-");
            if (ws.length > 0) { try { sw = Integer.parseInt(ws[0]); r[2] = sw; } catch(Exception e){} }
            if (ws.length > 1) { try { ew = Integer.parseInt(ws[1]); r[3] = ew; } catch(Exception e){} }
        } else { r[2] = sw; r[3] = ew; }
        if (time.contains("单周")) r[4] = 1; else if (time.contains("双周")) r[4] = 2;
        // faithful to sleepy JwOldZfParser.kt: computed `step` is never written into result[1]
        // (upstream ZhengFangParser.kt L214 has result[1] = step; the assignment was lost in porting).
        return r;
    }
    static int countStr(String s1, String s2) { int t = 0, i = 0; while ((i = s1.indexOf(s2, i)) != -1) { t++; i++; } return t; }

    // ─── JwUrpParser ───
    static List<Course> urp(String source) {
        List<Course> out = new ArrayList<>();
        Document doc = Jsoup.parse(source);
        Elements tables = doc.getElementsByAttributeValue("class", "displayTag");
        if (tables.isEmpty()) tables = doc.getElementsByAttributeValue("class", "table table-striped table-bordered");
        if (tables.isEmpty()) return out;
        for (Element table : tables) {
            if (table.text().contains("星期一")) continue;
            Element thead = table.getElementsByTag("thead").first(); if (thead == null) continue;
            Elements ths = thead.getElementsByTag("th");
            int headSize = ths.size();
            int nameIdx=-1, teacherIdx=-1, weekIdx=-1, dayIdx=-1, nodeIdx=-1, stepIdx=-1, buildingIdx=-1, roomIdx=-1;
            List<String> thTexts = ths.eachText();
            for (int i = 0; i < thTexts.size(); i++) {
                String s = thTexts.get(i).trim();
                switch (s) { case "课程名": nameIdx=i; break; case "教师": teacherIdx=i; break; case "周次": weekIdx=i; break;
                    case "星期": dayIdx=i; break; case "节次": nodeIdx=i; break; case "节数": stepIdx=i; break;
                    case "教学楼": buildingIdx=i; break; case "教室": roomIdx=i; break; }
            }
            if (weekIdx == -1 || nodeIdx == -1 || nameIdx == -1) continue;
            Element tbody = table.getElementsByTag("tbody").first(); if (tbody == null) continue;
            String courseName = "", teacher = "";
            for (Element tr : tbody.getElementsByTag("tr")) {
                Elements tds = tr.getElementsByTag("td");
                boolean wholeFlag = tds.size() > headSize - weekIdx;
                int acDayIdx = wholeFlag ? dayIdx : dayIdx - weekIdx;
                if (tds.get(acDayIdx).text().trim().isBlank()) continue;
                if (wholeFlag) { courseName = tds.get(nameIdx).text(); teacher = tds.get(teacherIdx).text().trim(); }
                String room;
                try { int bIdx = wholeFlag?buildingIdx:buildingIdx-weekIdx; int rIdx = wholeFlag?roomIdx:roomIdx-weekIdx;
                    room = tds.get(bIdx).text().trim() + tds.get(rIdx).text().trim(); } catch (Exception e) { room = ""; }
                Element nodeE = tds.get(wholeFlag?nodeIdx:nodeIdx-weekIdx);
                int startNode = getStartNode(nodeE.text());
                int step = (stepIdx != -1) ? getStep(tds.get(wholeFlag?stepIdx:stepIdx-weekIdx).text().trim())
                    : (parseIntOr(nodeE.text().trim().contains("-")?nodeE.text().trim().split("-",2)[1].split("节")[0].trim():"", startNode) - startNode + 1);
                int day = getDay(tds.get(acDayIdx).text());
                String weekStr = tds.get(wholeFlag?weekIdx:0).text().trim();
                for (int[] r : weekToRanges(weekStr))
                    out.add(new Course(courseName, room, teacher, day, startNode, startNode+step-1, r[0], r[1], r[2]));
            }
        }
        return out;
    }
    static int getDay(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) {
        String[] ks={"星期一","星期二","星期三","星期四","星期五","星期六","星期日","星期天"};
        for (int i=0;i<ks.length;i++) if (ks[i].equals(s.trim())) return Math.min(i+1, 7);
        return 1; } }
    static int getStartNode(String s) { String t = s.trim();
        if (t.contains("-")) return parseIntOr(t.split("-",2)[0], 1);
        // substringAfter('第').substringBefore('大').substringBefore('小')
        String x = t.contains("第") ? t.substring(t.indexOf("第")+1) : "";
        if (x.contains("大")) x = x.substring(0, x.indexOf("大"));
        if (x.contains("小")) x = x.substring(0, x.indexOf("小"));
        return parseIntOr(x, 1); }
    static int getStep(String s) { return parseIntOr(s, 1); }
    static List<int[]> weekToRanges(String weekStr) {
        List<int[]> out = new ArrayList<>();
        if (weekStr.isBlank()) { out.add(new int[]{1,20,0}); return out; }
        int type = weekStr.contains("单") ? 1 : (weekStr.contains("双") ? 2 : 0);
        for (String w : weekStr.split(",")) {
            String c = w.replace("周","").replace("(","").replace(")","");
            if (c.contains("-")) { String[] p = c.split("-",2);
                int s = parseIntOr(p[0],1); int e = p.length>1?parseIntOr(p[1],s):s;
                out.add(new int[]{s,e,type}); }
            else out.add(new int[]{parseIntOr(c,1),parseIntOr(c,1),type});
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0]; String path = args[1];
        String src = new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
        List<Course> got;
        switch (mode) {
            case "qz": got = qz(src, "kbcontent"); break;
            case "qz_crazy": got = qz(src, "kbcontent1"); break;
            case "zf": got = oldZf(src, 0); break;
            case "zf_1": got = oldZf(src, 1); break;
            case "urp": got = urp(src); break;
            default: throw new IllegalArgumentException("mode " + mode);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < got.size(); i++) { if (i>0) sb.append(","); sb.append(got.get(i)); }
        sb.append("]");
        System.out.println(sb);
    }
}
