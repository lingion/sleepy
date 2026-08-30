import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.nio.file.*;
import java.util.*;

/** Faithful port of sleepy JwNewZfParser HTML path. */
public class SimNewZf {
    static class Course {
        String name, room="", teacher=""; int day, startNode, endNode, startWeek, endWeek, type;
        Course(String n,String r,String t,int d,int sn,int en,int sw,int ew,int ty){name=n;room=r==null?"":r;teacher=t==null?"":t;day=d;startNode=sn;endNode=en;startWeek=sw;endWeek=ew;type=ty;}
        public String toString(){return "{\"name\":\""+name+"\",\"teacher\":\""+teacher+"\",\"room\":\""+room+"\",\"day\":"+day+",\"startNode\":"+startNode+",\"endNode\":"+endNode+",\"startWeek\":"+startWeek+",\"endWeek\":"+endWeek+",\"type\":"+type+"}";}
    }
    public static void main(String[] args) throws Exception {
        String src = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        List<Course> out = new ArrayList<>();
        Document doc = Jsoup.parse(src);
        Element container = doc.getElementById("kbtable");
        if (container == null) container = doc.getElementById("kbgrid");
        if (container == null) container = doc.selectFirst("table.el-table__body");
        if (container == null) container = doc.selectFirst(".kbcapi-table");
        if (container == null) container = doc.selectFirst("[id*=kb]");
        if (container == null) { System.out.println("[FALLBACK_QZ]"); return; }
        Elements trs = container.getElementsByTag("tr");
        int nodeCount = 0;
        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td");
            if (tds.isEmpty()) continue;
            String firstCellText = tds.first().text().trim();
            boolean isSectionHeader = firstCellText.contains("节");
            if (isSectionHeader) { for (Element td : tds) if (!td.getElementsByClass("kbcontent").isEmpty()) { isSectionHeader = false; break; } }
            if (isSectionHeader) continue;
            nodeCount++;
            int day = 0;
            for (Element td : tds) {
                day++;
                Elements cells = td.getElementsByClass("kbcontent");
                if (cells.isEmpty()) continue;
                for (Element cell : cells) {
                    String html = cell.html();
                    if (html.isBlank()) continue;
                    for (String part : html.split("-----")) out.addAll(parseCell(part.trim(), day, nodeCount));
                }
            }
        }
        if (out.isEmpty()) { System.out.println("[FALLBACK_QZ]"); return; }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < out.size(); i++) { if (i>0) sb.append(","); sb.append(out.get(i)); }
        sb.append("]");
        System.out.println(sb);
    }
    static List<Course> parseCell(String html, int day, int nodeCount) {
        List<Course> out = new ArrayList<>();
        Document cellDoc = Jsoup.parse(html);
        String info = html.contains("<font") ? html.substring(0, html.indexOf("<font")).trim() : html.trim();
        String name = Jsoup.parse(info).text();
        if (name.isBlank()) return out;
        String teacher = cellDoc.getElementsByAttributeValue("title","老师").text().trim();
        String room = cellDoc.getElementsByAttributeValue("title","教室").text().trim();
        String weekStr0 = cellDoc.getElementsByAttributeValue("title","周次(节次)").text();
        String weekStr = weekStr0.contains("(周)") ? weekStr0.substring(0, weekStr0.indexOf("(周)")) : weekStr0;
        List<int[]> ranges = parseWeekStr(weekStr);
        int node = nodeCount * 2 - 1;
        for (int[] r : ranges) out.add(new Course(name, room, teacher, day, node, node+1, r[0], r[1], r[2]));
        return out;
    }
    static List<int[]> parseWeekStr(String s) {
        List<int[]> result = new ArrayList<>();
        if (s.trim().isEmpty()) { result.add(new int[]{1,16,0}); return result; }
        if (s.length() >= 10 && s.replaceAll("[01]","").isEmpty()) {
            List<Integer> weeks = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) if (s.charAt(i)=='1') weeks.add(i+1);
            return bitsToRanges(weeks);
        }
        for (String part : s.split("[,,;;]")) {
            String cleaned = part.replace("周","").replace("(","").replace(")","").trim();
            int type = part.contains("单") ? 1 : (part.contains("双") ? 2 : 0);
            if (cleaned.contains("-")) {
                String[] parts = cleaned.split("-",2);
                String p0 = parts[0].replaceAll("[^0-9]","");
                String p1 = parts.length>1?parts[1].replaceAll("[^0-9]",""):"";
                int st = p0.isEmpty()?1:Integer.parseInt(p0);
                int en = p1.isEmpty()?st:Integer.parseInt(p1);
                result.add(new int[]{st,en,type});
            } else {
                String digits = cleaned.replaceAll("[^0-9]","");
                if (digits.isEmpty()) continue;
                int v = Integer.parseInt(digits);
                result.add(new int[]{v,v,type});
            }
        }
        if (result.isEmpty()) result.add(new int[]{1,16,0});
        return result;
    }
    static List<int[]> bitsToRanges(List<Integer> weeks) {
        List<int[]> result = new ArrayList<>();
        if (weeks.isEmpty()) return result;
        int i = 0;
        while (i < weeks.size()) {
            int start = weeks.get(i); int end = start;
            if (i+1 < weeks.size() && weeks.get(i+1) - start == 2) {
                end = weeks.get(i+1); int k = i+1;
                while (k+1 < weeks.size() && weeks.get(k+1) - weeks.get(k) == 2) { k++; end = weeks.get(k); }
                result.add(new int[]{start, end, start%2==1?1:2}); i = k+1;
            } else if (i+1 < weeks.size() && weeks.get(i+1) - start == 1) {
                end = weeks.get(i+1); int k = i+1;
                while (k+1 < weeks.size() && weeks.get(k+1) - weeks.get(k) == 1) { k++; end = weeks.get(k); }
                result.add(new int[]{start, end, 0}); i = k+1;
            } else { result.add(new int[]{start, end, 0}); i++; }
        }
        return result;
    }
}
