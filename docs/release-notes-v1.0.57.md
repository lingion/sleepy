# Sleepy v1.0.57

> Fix the Xinjiang University postgraduate schedule import that still reported "no timetable detected", and ship the previous-evening reminder that PR #48 contributed.

## What's New

- **Previous-evening tomorrow preview** — the daily reminder card now hosts both today's summary and tomorrow's preview behind one master switch. Tomorrow preview defaults to off, fires at 22:00 by default, and ships in all six locales (zh-CN / zh-TW / en / ja / es). On a no-course night it reads "明日无课,好好休息!"; otherwise it lists the first class and total class count, mirroring the same-day reminder's wording. Strings, notification channel description, AppPrefs keys (`tomorrow_reminder` boolean default false, `tomorrow_reminder_time` "HH:mm" default "22:00"), and notification scheduler are all wired.
- **Daily reminder card restructured** — the old standalone "提醒开关" card is gone. The Daily card header carries the master switch; expanding it reveals today's summary row (time + on/off + preview) and tomorrow preview row (time + on/off + preview) as siblings. No AppPrefs key migration; existing user settings keep working.
- **Contributors card** — the About page now lists code contributors who merged pull requests. Two new entries this release: YYiChen (PR #43 timetable adaptive height, PR #48 previous-evening summary) and LzBsA (PR #30 Yanshan University postgraduate boya_pp adaptation, contributor-preserving merge after the original PR was closed).

## Fixes

- **Xinjiang University postgraduate (`yjspy.xju.edu.cn`)** — the "学期课表信息查询" page used a different table shape than the rest of the Gwork family: time-band header (`时间|节次|星期一..星期日`), Chinese numeral periods (`一`..`十二`), 上午/下午/晚上 columns with `rowspan="4"`, and course names written *outside* the braces (`名｛周次[教师:…,地点:…]｝`). The parser handled the older `｛名(周次)[教师:…,地点:…]｝` shape but produced zero courses on this page, so users saw "当前页面未检测到课表容器". The parser now reads both shapes, tracks rowspan/colspan occupancy per column so the `时间` band does not shove the day columns left, supports Chinese numeral period labels, and links consecutive identical rows so 连堂 (双节连排) extends the endNode. Real-page fixture (`xju-postgraduate-real-gwork.html`) plus an end-to-end PageFrame iframe test lock the regression at the parser and at the frame-capture seam.

## Known Limitations

- Lint baseline stays at 38 pre-existing errors / 434 warnings / 1 hint; this release adds none.

## Verification

- Tests: XJU class 12/12 green, full suite 1974/1974 green.
- APK SHA-256:
  - arm64-v8a: 74ba1a2ab719a85b23f995d975510d3e8bbd54c0184b81a1df0f78723791f1fd
  - armeabi-v7a: e56611a8b57813ab31365d6cf6df7929191712ae97da748bbb61c97046936413
  - x86_64: 7999c6f59ec3047a1053ef5e94e9a853c2644271e530c55c9bca5589e2c08fef
- Build: versionName 1.0.57 / versionCode 63

---

# Sleepy v1.0.57

> 修新疆大学研究生学期课表"未检测到课表容器",并上线 PR #48 提出的前一天晚上明日预告。

## 新增

- **前一天晚上明日预告** —— 「每日提醒」卡改为单卡母子结构,头部总开关,展开后并列两项:今日摘要(时间+开关+预览)、明日预告(时间+开关+预览)。明日预告默认关闭,默认时间 22:00,六种语言同步落地(简中/繁中/英文/日文/西班牙文)。无课晚推「明日无课,好好休息!」;有课时列出首节课与总课数,文案风格与当天提醒一致。涉及字符串、通知通道描述、AppPrefs 新增 `tomorrow_reminder`(bool 默认 false)与 `tomorrow_reminder_time`("HH:mm" 默认 "22:00"),通知调度器已全部接通。
- **每日提醒卡重排版** —— 原独立「提醒开关」卡已删除。每日卡头部开关控制当天+前一晚;展开后两个子项并列。AppPrefs 键值不迁移,既有用户的当天提醒习惯不受影响。
- **贡献者卡片** —— 关于页新增「贡献者」卡片,收录提过且已合并到 main 的外部 PR 贡献者。本轮新入册两位:YYiChen(PR #43 课表自适应高度、PR #48 前一晚明日预告)、LzBsA(PR #30 燕山大学研究生 boya_pp 协议适配,PR 关闭后走贡献者保留式 merge)。

## 修复

- **新疆大学研究生(yjspy.xju.edu.cn)** —— 「学期课表信息查询」页与其他 Gwork 族页面表头形态不一致:时间为带状表头(`时间|节次|星期一..星期日`),节次为中文数字(一..十二),上午/下午/晚上各列 `rowspan="4"`,课程名写在花括号**外面**(`名｛周次[教师:…,地点:…]｝`)。旧解析器只认 `｛名(周次)[教师:…,地点:…]｝` 的旧形态,在这页解析出 0 门课,故用户看见「当前页面未检测到课表容器」。现 parseXju 同时识别两种形态,按列追踪 rowspan/colspan 占用避免时间列把日列向左挤,支持中文数字节次标,纵向相邻同文本合并使连堂(endNode)自动延伸。真实页面脱敏 fixture(`xju-postgraduate-real-gwork.html`)与 PageFrame iframe 端到端测试,锁住解析器层与帧捕获层接缝的回归。

## 已知限制

- Lint 基线 38 errors / 434 warnings / 1 hint 维持不变,本轮零新增。

## 验证

- 测试:XJU 单测 12/12 绿,全套件 1974/1974 绿。
- APK SHA-256:
  - arm64-v8a: 74ba1a2ab719a85b23f995d975510d3e8bbd54c0184b81a1df0f78723791f1fd
  - armeabi-v7a: e56611a8b57813ab31365d6cf6df7929191712ae97da748bbb61c97046936413
  - x86_64: 7999c6f59ec3047a1053ef5e94e9a853c2644271e530c55c9bca5589e2c08fef
- 构建:versionName 1.0.57 / versionCode 63