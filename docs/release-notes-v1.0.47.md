## v1.0.47

Six more universities joined direct JW import — Zhejiang, USTC, Northeastern, Sichuan, Southeast, and HFUT among them — and the whole batch got a line-by-line audit that caught a real security hole plus a handful of parsers quietly throwing away odd/even week data. The About page's license wall moved to its own page. 34 commits.

### New

**Six more schools, 159 total**

Direct import now covers 合肥工业大学, 东南大学, 浙江大学, 中国科学技术大学, 四川大学, and 东北大学 — each with its own parser for that school's JSON dialect (EAMS5 schedule-table, URP newxk, UGR kbList, studentTableVm, SCU dateList, NEU arrangedList). The same diff also registered 北京理工, 中南, 华南理工, and 山东大学（威海）, whose existing protocol parsers already fit. Thanks to the upstream projects that documented these protocols — Chiu-xaH/HFUT-Schedule, sakimidare/SEUTimetable, Xecades/zju-ical-py, 1970633640/USTC-timetable-to-ics, Z-P-J/ScuTimetable, and CreamPig233/neu_wisedu2wakeup. Each is credited in the app.

**License & acknowledgements got their own page**

About → 开源声明 used to be one endless paragraph stuffing the GPL notice and every upstream credit together. It's now an entry row; tapping it opens a proper sub-page with the license on top and each credited project on its own card — name, license, and what Sleepy borrowed.

### Fixed

**WebView no longer accepts any TLS certificate**

The import WebView overrode SSL errors and called `proceed()` unconditionally. On a hostile network, that's a man-in-the-middle window straight into your academic-system session. Certificates are now accepted only for the school's own domain (some universities really do run private CAs); everything else is rejected.

**Fetches time out instead of hanging forever**

Tapping 导入 on Wisedu/CQU/EAMS5 schools fired a fetch inside the WebView with no timeout. If the academic server was down, the button just sat there — "fetching" forever. Twenty seconds with no response now shows an explicit timeout message.

**Odd/even week data survives parsing**

Four of the new-school parsers treated every week range as weekly, and NEU's parser choked outright on the parenthesized form the real API sends ("1-16周(双)" parsed as weeks 1 to 1). Ranges like that now parse fully, keep their 单周/双周 type, and get endpoint-corrected through one shared helper instead of three diverging copies — which also fixes an inverted-range bug where "6-6周(单)" produced a course attending week 7 of a 6.

**USTC rows without lessonCode no longer vanish**

The parser read lesson positions from a 4-digit lessonCode field the protocol doesn't guarantee. Rows without one were silently dropped even though start/end times were right there. Those rows now infer periods from times.

**SCU keeps its 单/双 markers too**

Sichuan's weekDescription strings carry 单/双 markers in the wild ("3-9周单"); the parser copied the upstream app's regex and stripped them. It doesn't anymore.

**The conflict dialog actually names the other course**

The template said 「与「%4$s」冲突」 but the code passed five arguments to a four-slot template, so what rendered in the quotes was the week range. Testing had been done against a made-up five-slot template, which is how this survived. The test now uses the real production string.

**Others**

Rotation logic (applyLayerRotation) got its first unit tests. The schools.json test fixture, 10 schools behind main, is now a byte-for-byte copy — and the hard-coded "149 schools" assertion is gone, so this stops drifting. Parsing failures on the nine newly added campuses now suggest connecting to campus network/VPN instead of claiming "no courses this semester". 20-second timeout on WebView fetches; a stale high-refresh dead branch removed; the rotation session state survives screen rotation; About page numbers corrected.

### L10n

The en/es/ja translations of the About acknowledgements still listed only WakeUp and cqu.js — batch B's five credits never landed there, and the regression test only checked three locales so it couldn't notice. All six release locales now carry the full list, and the test checks all six. The license sub-page ships fully translated.

### Tests

The audit line added ~1900 lines of tests (parser edge cases, parity endpoint correction, rotation, collision template). Full suite: 908, zero failures.

### 构建

- versionCode: 48

---

## v1.0.47

这个版本一半是收录:六所新学校的教务直连落地,外加一轮逐行审计——审计逮住一个真的安全漏洞,和几个悄悄丢单双周数据的解析器。关于页的致谢长文也拆成了独立子页。34 个提交。

### 新增

**新收录六校,共 159 所**

教务直连新增 合肥工业大学、东南大学、浙江大学、中国科学技术大学、四川大学、东北大学——每所配了对应 JSON 协议的专属解析器(EAMS5 schedule-table、URP newxk、UGR kbList、studentTableVm、SCU dateList、NEU arrangedList)。同批还登记了 北京理工、中南、华南理工、山东大学(威海) 四所,现有协议解析器直接覆盖。感谢把协议文档化的上游项目——Chiu-xaH/HFUT-Schedule、sakimidare/SEUTimetable、Xecades/zju-ical-py、1970633640/USTC-timetable-to-ics、Z-P-J/ScuTimetable、CreamPig233/neu_wisedu2wakeup,应用内关于页已逐一致谢。

**许可与致谢拆成独立子页**

关于 → 开源声明 原来是一整段塞完 GPL 声明加全部上游致谢的长文。现在关于页只留一行入口,点进去是独立子页:许可证在上,每个被致谢的项目一张卡——名字、协议、Sleepy 参考了什么。

### 修复

**WebView 不再无条件接受任何 TLS 证书**

导入用的 WebView 之前把 SSL 错误一律 `proceed()` 放行。在恶意网络里,这就是一个直通你教务会话的中间人窗口。现在只放行学校自己域名的证书(确实有高校用私有 CA),其余一律拒绝。

**抓取会超时,不再永远转圈**

Wisedu/CQU/EAMS5 学校点导入,是在 WebView 里发起 fetch,之前没有超时。教务服务器挂了的话,按钮就永远停在"正在抓取"。现在 20 秒无响应直接报超时。

**单双周数据不再被解析丢掉**

四所新校的解析器把所有周次段当每周上,NEU 的解析器更狠——真实接口发的带括号形态("1-16周(双)")直接解析成只有第 1 周。现在这类串完整解析、保留单周/双周类型,端点修正统一走一个共享函数(之前是三份各自为政的复制),顺带修掉一个端点倒挂 bug:"6-6周(单)" 会产出一门上第 7 周的课,而这门课一共只有 6 周。

**USTC 缺 lessonCode 的行不再消失**

解析器从一个 4 位 lessonCode 字段读节次,但协议并不保证有这字段。缺了的行被静默丢弃,尽管起止时间就摆在旁边。现在这类行从时间推断节次。

**SCU 的单/双标记也保住了**

四川大学的 weekDescription 真实数据带单/双标记("3-9周单"),解析器照抄上游 app 的正则把它们剥了。不剥了。

**冲突弹窗真的能显示对方课名了**

模板写着 「与「%4$s」冲突」,代码却给四占位模板传了五个参数——引号里渲染出来的是周次区间。测试一直对着自造的五占位模板跑,所以这 bug 活了下来。现在测试用真实生产字符串。

**其他**

轮换逻辑 (applyLayerRotation) 第一次有了单元测试。schools.json 测试夹具落后主资产 10 所,现在改成逐字节复制;硬编码的"149 所"断言删了,这事不会再漂。九所新校区解析失败时提示连校园网/VPN,不再谎报"本学期暂无课程"。WebView fetch 加 20 秒超时;删了一个高刷新率的死分支;轮换状态旋转屏幕后不再丢;关于页数字已校正。

### 多语言

en/es/ja 的关于页致谢还停留在只有 WakeUp 和 cqu.js——B 档五项致谢从没落进去,回归测试也只查三种语言,所以没人发现。六个发布语言现在都是完整清单,测试也查全部六个。许可子页全量翻译。

### 测试

审计这条线新增约 1900 行测试(解析器边界、单双周端点修正、轮换、冲突模板)。全套 908 个,零失败。

### 构建

- versionCode: 48
