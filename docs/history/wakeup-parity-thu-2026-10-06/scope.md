# THU 教务导入范围与证据（2026-10-13 复核）

清华大学条目指向 https://zhjwxk.cic.tsinghua.edu.cn/ 。未登录访问该域名转向学校 OAuth；尚未取得登录后的实际 HTML。用户应登录、打开「整体课表」、选择学期，再点「导入此页」。

实现：JwThuparser 解析 setInitValue、strHTML/strHTML1 和 aN_M 网格脚本。无法无损表达的 C2 时间、周集合及无效周范围会跳过；预览提示用户核对并手动补录。学校目录、URL 判型、帧锚点及六语提示已加。测试 HTML 是人工合成，并非学校回包。

来源：Starrah/THUCourseHelper @ 64a543518f9007e2b5a615582bd049acf5d2ada9 的 THUCourseDataSouce.kt 支持整体课表入口与 C1/C2 解析（MIT）。lingion/wakeup-mod @ 879f0d6781da19d1da9300d5eda6b0c2629b6a63 的反编译 APK THUParser.java 支持固定版本大节映射和 14 节表，但不能证明当前学校作息或原作者授权，不复用源码。该文件不在 YZune/WakeUpSchedule 的被引仓库。piggyham/thu_timetable @ 762f0bdc75fd9219dee75f3416c172d634dd1816 使用另一路 jxmh_out.do JSONP，不支持现有 HTML 指纹。

旧文档声称 1005 候选和多仓共识，但未附原始逐仓记录，现不采信。2026-10-13 宽泛四式报告总命中 1175（各式可能重叠），仅抓取前 100/100/50/100、去重 343，混入大量教学实验仓，不能作为合格候选数。精确校域名 README 查询返回 8 仓，已逐个读 README/入口并列 verdict；另对旧触达名单和同协议来源核查，当前 evidence/qualified-candidates.json 列 14 个有据候选/待定身份。正向证据包括 Starrah、NextTHUxk、历史 YAATO；其他多为不同任务、不同协议或身份未核，不能计入同一解析实现共识。所有固定提交 verdict 与原始搜索矩阵见本目录 evidence/ 。需登录后脱敏课表样本方能验收线上兼容。
