# BJTU 协议 matrix (Step 4, 7 维求同存异)

> 依据: findings.md 24 verdict · POSITIVE 12 · 证据仓跨 2019-2026 七年仍指向同一协议族 → 高置信

## D1 登录 / SSO

| 层 | 端点 | 细节 | 证据仓 |
|----|------|------|--------|
| CAS | `cas.bjtu.edu.cn/auth/login/?next=...` | Django form#login; 隐藏域 next/csrfmiddlewaretoken/captcha_0; img.captcha; POST `loginname(学号)+password+captcha_0(id)+captcha_1(算式答案)`; 算式图 `N+N=` 提交**结果**非算式; 失败留 /auth/login, 错误在 .tishi | id1 id3 id4 id5 |
| MIS 桥 | `mis.bjtu.edu.cn/module/module/10/` | AA 桥页; regex 提取 `/client/login/...` 直登 AA; `module/322`=dean 桥, `module/104`=VE 桥; `mis /home/` 可作登录态探针 | id1 id3 |
| AA 会话 | `aa.bjtu.edu.cn` | CAS 后会话 cookie 即可用; 失效=重定向 `/client/login/` 或 body `用户登录`+`教学` | id1 id3 |
| 老教务 | `dean.bjtu.edu.cn` | 遗留; 同 MIS 桥进入; term 4 段码 | id12 id13 id16 |
| VE | `123.121.147.7:88` | /s.shtml md5 密码登录 + sessionId | id4 id11 |

## D2 课表端点

| 端点 | 参数 | 说明 | 证据仓 |
|------|------|------|--------|
| `aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/` | 无 | 本学期课表, 服务端按会话定 term/week | id1 id3 id14 id15 |
| `aa.bjtu.edu.cn/course_selection/courseselecttask/schedule/` | 无 | 选课任务课表(全学期全课) | id3 |
| `dean.bjtu.edu.cn/course_selection/courseselecttask/remains/` | - | 老教务遗存 | id13 id16 |
| `123.121.147.7:88/ve/back/course.shtml?method=getTimeList` | - | 周课表 JSON {weekCode} | id4 |
| `123.121.147.7:8081/course/get_stu_course_sched.action?id=00000&dateStr=YYYYMMDD` | dateStr | 移动端按日, 返回 classBeginTime/classEndTime | id11 |

**共识**: 主端点 = AA stuschedule(本学期) / schedule(全学期), WebView 会话内直接 GET, 无 term/week 请求参数。

## D3 课表 HTML 形状 (解析目标)

```
<table class="table table-bordered">
 <tr><th>节次</th><th>星期一</th>...<th>星期日</th></tr>
 <tr>
  <td>第1节 <span class="text-muted">[08:00-09:50]</span></td>
  <td>
    <div>
      M310005B [01]                       <!-- regex ([A-Z]\d+[A-Z]?)\s*\[([^]]+)] -->
      <span>课程名</span>
      <div style="max-width:120px">第01-16周 <i>教师</i></div>
      <span class="text-muted">主校区, 思源楼, 301</span>
    </div>
  </td>
 </tr>
</table>
```
变体: div.ellipsis[title="第01-16周 星期二 第1节 主校区, 楼, 室"] (id2); span.green 选中态 (id3); 课名 span 有无 text-muted 类差异 (id1)。

## D4 周次文法 (3 形态 + 奇偶)

| 形态 | 例 | 处理 |
|------|-----|------|
| 连续 | `第01-16周` | 区间展开 |
| 离散 | `第1,3,5-7周` | 逗号分段后逐段展开 |
| 单值 | `第X周` | 单值 |
| 奇偶后缀 | `（单周）/（双周）`/`(单)/(双)` | **必须**奇/偶过滤 — fish2lab(bjtu-cli) 剥字后丢后缀=已知 bug, 禁复制; 以 id1 canonical 为准 |

分隔符实测: `- ~ — – － 至 到` (id1 regex `(\d{1,2})(?:\s*[-~—–－至到]\s*(\d{1,2}))?`)。

## D5 节次时间

两条来源路径:
1. **页面内嵌**(推荐优先): 行首格 `第N节 <span class="text-muted">[HH:MM-HH:MM]</span>` — id1 id6 id2 均从页面解析, 每学期自洽;
2. **硬编码兜底**(无时间行时): 7 节共识表 `1=08:00-09:50, 2=10:10-12:00, 3=12:10-14:00, 4=14:10-16:00, 5=16:20-18:10, 6=19:00-20:50, 7=21:00-21:50`; 错峰: 思源西楼/逸夫教学楼 第2节=10:30-12:20。Moliseeee(id9, NEGATIVE) 的 PERIODS 表逐项一致 = 第二独立来源。

## D6 学期编码

| 系统 | 形态 | 例 |
|------|------|-----|
| AA/dean 表单名 | `zxjxjhh` | `<select name=zxjxjhh>` |
| AA term | 3 段 | `2024-2025-1` |
| dean term | 4 段 | `2019-2020-1-2` |
| VE xqCode | YYYYYYYY+TT | `2025202602` |
| bksy | .NET Date JSON | SemesterTranPage hidJson |

## D7 登录态 / 错误检测

- AA 会话失效: 重定向到 `/client/login/` 或 body 含 `用户登录`+`教学` (id1); `会话已过期/登录已失效/重新登录` (id3)。
- CAS: final URL 仍在 `/auth/login` = 登录失败 (id1)。
- 2FA 词条: `二次认证/多因素认证` (id3)。

## 协议族判定

**BJTU = 自研 Django 系** (CAS+MIS+AA+dean+VE 五件套), **不匹配** Sleepy 任何现有协议族(wisedu/cqu/neu/eams5/zf_new/zf/urp/qz/ucas):
- 有独立 CAS 域 + 算式验证码 (非 cqu 门户 REST 非 neu homeapp POST 非 ucas sep)
- 表格=节次行 x 星期列 + div 块 (非正方 jqGrid/kbList, 非 eams5 table-bordered 模板)
- term=zxjxjhh 但值形态/端点路径自成体系
→ 新增 `TYPE_BJTU` + `BjtuParser` + WebView fetch JS (fetch stuschedule + schedule 双端点)。
