# HFUT × WakeUp 教务实现交叉核查 (2026-09-16)

任务: 用户原话「合肥工业大学再去看看 wakeup 的教务」。
方法: WakeUp v6.0.23 APK (dexdump 反汇编, jadx 源对照) + 开源 wakeup-android-project 树。
背景: sleepy 已落地 issue#46 修 (2d4e4916): EAMS5 JS 链第 3.5 段 POST timetable-layout 拿服务端 courseUnitList 精确查表。

## 1. WakeUp 对 HFUT 的适配形态

| 维度 | WakeUp 实现 |
|------|-------------|
| 入口 | 校库条目「合肥工业大学」type=`login` (专属原生登录解析器, 致谢 @Renton, LoginWebFragment:1227 硬编码学校名分支) |
| 解析器 | `schedule_import/login_school/hfu/HFUCourse.kt` (默认 baseUrl=`http://jxglstu.hfut.edu.cn/eams5-student`, needDataId=true) |
| 同款复用 | 西安建筑科技大学复用同一 HFUCourse (baseUrl=`https://swjw.xauat.edu.cn/student`, needDataId=false) |
| 登录链 | GET `/login-salt` (404 循环重试) → POST `/login` body=`{username, password: md5(salt+"-"+password), captcha:""}` → GET `/for-std/course-table/` |
| dataId | needDataId=true: 重定向 URL 含 `/info/` 取尾段, 否则 css `.student-panel-body.info-page > button` 取 value |
| 学期 | 页面 `#allSemesters` option[selected].value |
| get-data | `?bizTypeId=2&semesterId=X&dataId=Y`, lessonIds 空 → bizTypeId=23 重试 → 仍空 → allSemesters[0] + bizTypeId=2 再试 |
| datum | POST `/ws/schedule-table/datum` body=`{lessonIds, studentId, weekIndex:""}` — **与 sleepy EAMS5 四链完全同协议** |
| JSON 解析 | kotlinx.serialization bean `HFUInfo{result:{lessonList[{id,courseName}], scheduleList[{lessonId,personName,startTime,endTime,weekday,weekIndex,room{nameZh},teacherId}]}}` |

## 2. 节次 (node) 计算 — WakeUp 无 courseUnitList, 全硬编码算术

WakeUp HFU parser (`parser/oo000o`, OooO0oo=true) 不读 datum 里的 courseUnitList (v6.0.23 dex 无 timetable-layout 请求),
对每条 schedule:

```
startNode = if (st < 1230) (st-800)/100 + 1
            else if (st < 1800) (st-1400)/100 + 5
            else (st-1900)/100 + 9
span      = dur in [50,200) ? 1 : dur<=200 ? 2 : dur in [210,340] ? 3 : 4   // dur=et-st
endNode   = startNode + span - 1
```

排序: scheduleList 先按 compareBy(weekIndex, teacherId, startTime, lessonId, weekday) 排, 再逐条相邻合并周次 (weekIndex 二值 01 位图, '1'=有课, 逐字符扫描, 位置即周次-1)。
缺 lessonId 映射的课名 → 「未知」占位 (与 sleepy Cumtb 修法同形)。

## 3. 与 sleepy 现实现对照 (用 HFUT 宣城真实 fixture 122 行跑双模拟)

sleepy 真相源 = 服务端 courseUnitList 查表 (startTime==unit.startTime 精确映射, 122/122 全中)。
宣城校区 12 节布局: 1:800-845, 2:855-940, 3:1000-1045, 4:1055-1140, 5:1400-1445, 6:1455-1540, 7:1550-1635, 8:1645-1730, 9:1740-1825, 10:1920-2005, 11:2015-2100, 12:2110-2155。

| 时段 (真实 122 行分布) | 服务端 layout 真值 | WakeUp 硬编码 | sleepy (查表) | 判定 |
|---|---|---|---|---|
| (800,940) ×22 | 1-2 | 1-2 | 1-2 | 三方一致 |
| (1000,1140) ×47 | 3-4 | 3-4 | 3-4 | 三方一致 |
| (1400,1540) ×35 | 5-6 | 5-6 | 5-6 | 三方一致 |
| (1550,1730) ×10 | **7-8** | **6-7 ✗** | 7-8 | WakeUp 错一节 (把 15:50 当午后第 2 节起) |
| (1920,2100) ×8 | **10-11** | **9-10 ✗** | 10-11 | WakeUp 错一节 (晚间按 19:00 整点基准) |

**结论: 18/122 (14.8%) 行 WakeUp 算术错位, 恰是 sleepy issue#46 修复前的同两类错误形态** (sop v1.11 ①「报错地点≠根因地点」的隐含假设段表)。
WakeUp 的 (800/1400/1900 整点五段算术) 是合肥校区默认布局的快照假设; 宣城校区 15:50/19:20 开课即越界。
sleepy 的服务端布局查表严格优于 WakeUp 硬编码, 现实现无需改动。

## 4. 顺手收益核对

- WakeUp HFUInfo bean 无 periods/timeTable 消费 — 不像 sleepy 把 courseUnitList 转 periods[] 预填确认页作息表。sleepy 此点同样领先。
- WakeUp 的盐密码 `md5(salt+"-"+password)`、bizTypeId=23 兜底、`button[value]` dataId 提取为通用 EAMS5 部署 (如西建大 swjw) 提供了旁证; sleepy 现有 EAMS5 JS 链靠 WebView 内登录天然免盐密码路径, 无需采纳。

## 5. 非目标 / 观察项 (不动手)

1. **西建大协议漂移信号**: WakeUp 对 xauat 走 `swjw.xauat.edu.cn/student` (EAMS5 族, HFUCourse 复用), sleepy schools.json 仍 `authserver.xauat.edu.cn` + `classic_eams`。若 XAUAT 已迁 swjw 新平台, sleepy 条目会失效 — 留待 XAUAT 报告人/下轮复核, 本轮不动。
2. WakeUp 校库另含「合肥工业大学 - 研究生」south_soft(南软) 条目, URL 为空模板不探测; 与本轮本科生 jxglstu 链无关。

## 6. 判定纪律引用

数维判定禁靠仓名: 本次判定依据 = dex 反汇编实际指令流 (HFUCourse 构造参数、HFUInfo bean 键集、oo000o.OooO0o 算术), 非 jxglstu 字样命中。
(另证: dexdump 阶段 retrofit2/obfusc 包名下发现同形 EAMS5 端点串, 靠代码定位非包名。)
