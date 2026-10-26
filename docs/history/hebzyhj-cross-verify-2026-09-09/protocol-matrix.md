# hebzyhj 协议 matrix — 强智移动教务 vs 近亲协议族

## 7 维对比

| 维度 | qz_app (本案) | qz (经典强智) | wisedu | cqu |
|------|--------------|--------------|--------|-----|
| 入口形态 | SPA `/dist/#/login` | HTML 表格页 `/jsxsd/kbcx/xskbcx` | mobile JSON `/jwapp` | 自建门户 REST |
| 鉴权 | `token: <JWT>` 请求头 (sessionStorage.Token) | JSESSIONID cookie | cookie (CAS) | cookie |
| 课表端点 | POST `{ApiUrl}/student/curriculum?week=&kbjcmsid=` | POST /jsxsd/kbcx/getKbcx... | GET dqxnxq + xskcb.do | 门户 REST |
| 响应形态 | JSON: `{code,Msg,data:[{date,courses[]}]}` | HTML 表格 | JSON: `{datas:{xskcb:[…]}}` | JSON |
| 周次字段 | classWeek "1-4,6-19" 区间串 (+classWeekDetails 位图回退) | SKZC 逗号位图 | SKZC 位图 | – |
| 时间字段 | classTime "10304" = 星期+起止节 | kbcontent cell 文本 | – | – |
| 前缀发现 | serverconfig.json ApiUrl (运行时发现, 禁硬编码) | 固定 | 固定 | 固定 |
| 抓取方式 | WebView 注入 JS (fetch + token 头) | WebView 通用 HTML 捕获 | WebView 注入 JS | WebView 注入 JS |

## 求同

- 全族 JSON/HTML 单响应一次性返回全学期; 无翻页
- 周次带洞 → 拆连续段; 等差 2 → 单/双周 (与 JwWiseduParser.weekRuns 同语义)
- 抓取统一 WebView 注入 JS + __sleepyBridge.onWiseduResult {ok,data} 桥回

## 存异

- 鉴权: 唯一用自定义 `token` 头 (非纯 cookie), sessionStorage 而非 cookie
- 前缀: 唯一需运行时发现 API 前缀的协议族 (serverconfig.json 免鉴权静态资源)
- classTime 编码是独有形态: WDD{SS}{EE} 3/5 位定长数字, 与所有既有 parser 不同
- SPA 壳: 页面 HTML 零课程数据 → 必须走 fetch JS, 不能落通用 HTML 捕获
