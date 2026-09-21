# 广东东软学院跨仓验证

- 用户原话：把 4 所学校按照 SOP 流程全部做完，然后并且最终交叉验证。
- 学校：广东东软学院（NUIT）
- 类型：新增学校适配；Wisedu `datas`/`arrangedList` 课程对象变体
- 入口：`http://jw.nuit.edu.cn/jwapp/sys/homeapp/home/index.html?av=&contextPath=/jwapp`
- 采集包 SHA-256：`8ea31e4c27bff9bb87c9f9a89c96c019a677654f2373c636b546720be6231d73`
- 证据：真实接口 `/jwapp/sys/homeapp/api/home/student/courses.do`，课程对象位于 `datas[]`，`classDateAndPlace` 含周次、星期、节次、教师、教室。
- GitHub 直接协议证据：`3056810551/nuit-class-schedule`，MIT 未声明，`main.js` 使用 `var kbxx` 与 `jcdm2/zcs/xq/teaxms/jxcdmcs`。
- 当前代码结论：新增可复用 `JwNuitParser` 与 fetch JS；不把 NUIT 响应误路由为东北大学 `arrangedList`。
- 证据边界：fixture 脱敏，仅保留真实字段形态；不提交 Cookie、账号或令牌。
