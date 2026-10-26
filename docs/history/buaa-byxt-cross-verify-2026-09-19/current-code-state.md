# 适配前代码状态 (2026-09-19)

## schools.json (BUAA 段)
```json
{
  "id": "buaa",
  "aliases": ["北航", "BUAA", "北京航空航天大学"],
  "url": "https://jwxt.buaa.edu.cn:7001/ieas2.1/kbcx/queryGrkb",
  "type": "qz_ieas"
}
```

## JwImportViewModel.detectProtocolFromUrlImpl 分支
- byxt.buaa.edu.cn → 不存在 (新加)
- jwxt.buaa.edu.cn → TYPE_QZ_IEAS
- /jwapp/ → TYPE_WISEDU (WISEDU_FETCH_JS, 拿不到 arrangedList)

## JwNeuParser (NEU 专用)
- generateCourseList: weeksAndTeachers 解析; titleDetail `周数 教室` 形态
- extractWeeksAndRooms: titleDetail 周数正则 → fallback weeksAndTeachers
- 实验性单 pass cellDetail parser (易丢数据)

## NEU_FETCH_JS (JwWebViewLoginScreen.kt)
- 仅 hostname === 'jwxt.neu.edu.cn'
- 三步: getMyScheduledCampus → campusCode → getMyScheduleDetail

## 测试覆盖
- JwProtocolDetectionUnitTest: byxt 5 URL forms missing
- JwNeuParserTest: cellDetail 双周/单周/连续周 missing
- JwNewSchoolsTest: BUAA type=qz_ieas (旧)

## 致谢
- AboutLicenseAttributionTest.kt BUAA 段: 5 仓 (iEAS 旧版)
- values{,-en,-es,-ja,-zh-rCN,-zh-rTW}/strings.xml about_license_body: 同 5 仓
