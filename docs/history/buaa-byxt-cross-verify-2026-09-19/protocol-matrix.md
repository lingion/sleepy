# byxt.buaa.edu.cn 协议矩阵 (2026-09-19)

## 端点对照

| 学校 | 端点 | campusCode | type | 字段集 |
|---|---|---|---|---|
| 东北大学 NEU | /jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do | 由 getMyScheduledCampus.do 获得 | term | titleDetail (周数串 教室) |
| 北京航空航天大学 byxt (本科新本研) | /jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do | "" (直传) | term | titleDetail + cellDetail + weeksAndTeachers |
| 强智 iEAS (jwxt.buaa.edu.cn:7001/ieas2.1) | /ieas2.1/kbcx/queryGrkb | — | — | HTML 表格 |

## 字段映射 (JwNeuParser 兼容双形态)

| arrangedList row 字段 | NEU 形态 | byxt 形态 | parser 处理 |
|---|---|---|---|
| courseName/courseCode | ✓ | ✓ | 直接读 |
| dayOfWeek/beginSection/endSection | ✓ | ✓ | 直接读 |
| weeksAndTeachers | 偶有 | 必有 | 教师 fallback |
| cellDetail[].text | 无/极少 | 必有 | 教师+周次 首选 |
| titleDetail[] | `周数 教室` 形态 | `上课地点：沙河校区/...` 形态 | 周数+教室 fallback |
| placeName | 无 | 必有 | 兜底占位 |

## 协议族判断

```
byxt.buaa.edu.cn  → TYPE_NEU (复用 NEU_FETCH_JS + JwNeuParser)
jwxt.neu.edu.cn   → TYPE_NEU (原形态)
jwxt.buaa.edu.cn  → TYPE_QZ_IEAS (强智 iEAS 保留)
/jwapp/           → TYPE_WISEDU (WISEDU_FETCH_JS, 不含 byxt/NEU)
```

URL 检测顺序必须: byxt host anchor → NEU host anchor → /jwapp/ catch-all,
否则 byxt URLs 会被 /jwapp/ 吸进 TYPE_WISEDU, WiseduFetch 走 wdkb/xskcb.do 拿不到 arrangedList。

## byxt fetch JS 端 (NEU_FETCH_JS 内部分支)

```
if (isBuaaByxt) {
  body = 'termCode=' + termCode + '&campusCode=&type=term';
  fetch('/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do', POST);
} else {
  // NEU 三步: getMyScheduledCampus → campusCode → getMyScheduleDetail
}
```

## 已知边界

- gsmis.buaa.edu.cn (研究生 GSMIS, /gsapp/sys/wdkbapp/) 不在 scope
- jwxt-7001.e2.buaa.edu.cn (校园网代拨 → 强智 iEAS) 保留 TYPE_QZ_IEAS 不变
- 旧 jwxt.buaa.edu.cn:7001 (直连强智) 同上保留
