# SEP SSO 登录链协议 matrix (对比维度: 登录实现层 × XRW 处理 × 重定向兼容 × 会话载体)

| 实现层 | XRW 处理 | 重定向 | 会话载体 | 登录页可达性 (XRW 在场时) |
|---|---|---|---|---|
| 桌面浏览器 (用户实测) | 不发 | 302/303 原生跟 | JSESSIONID | ✅ 可达 (302→303→200) |
| TraintimePda-UCAS (Dart 原生 HTTP) | 天然无 | 302+303 双兼容 (曾因只判 302 翻车) | JSESSIONID (内存 jar) | 不适用 (不走页面) |
| sep-api (Python 原生 session) | 天然无 | 原生跟 | JSESSIONID | 不适用 (原生 POST /slogin) |
| Sleepy WebView 修复前 | **每请求强制 <包名>** | 300..399 原生跟 | JSESSIONID | ✗ SEP 保护页 = 401 JSON |
| **Sleepy WebView 修复后 (0f3d77c)** | **GET 剥头代理** | 逐跳 300..399 + Set-Cookie 同步 + 出域合成 302 | JSESSIONID (CookieManager 同步) | ✅ 可达 (代理剥头) |

结论: 五个实现层里只有 WebView 形态天然携带 XRW; SEP filter 按头分流是 UCAS 特有配置,
修复 = 剥头代理使 WebView 形态达到"桌面浏览器等价"。
