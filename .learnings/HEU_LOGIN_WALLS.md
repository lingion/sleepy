# HEU 登录 + v1.0.15 节次时间根因 客观记录

> 客观记录 HEU 登录实测过程 + v1.0.15 抓不到节次时间的真实根因。

## HEU 登录 API 完整梳理

| 端点 | 方法 | 状态 | 备注 |
|---|---|---|---|
| `/cas/login` | GET | ✅ 200 | 拿 lt/execution/pid/publicKey(空)/source(cas)；Set-Cookie JSESSIONID + INGRESSCOOKIE + **X_CAPTCHA=true 强制** |
| `/cas/login` | POST | ✅ 200 | form submit 端点（login-type="cas"） |
| `/sso/apis/v2/open/captcha?imageWidth=100&captchaSize=4` | GET | ✅ 200 | JSON {img: base64 jpeg, token} |
| `/sso/logon?x_jump_mark=LOCAL_LOGIN` | POST | ❌ **404** | login.js 写错端点（OPTIONS 204 是 CORS 预检） |
| `/sso/login` | POST | ❌ 200 但返 SSO HTML | 不是 login 端点 |
| `/sso/apis/v3/open/captcha` | GET | ❌ 102B 小 JSON | 错误响应 |
| `/sso/cas/serviceValidate?service=&ticket=` | GET | ❌ 500 | 端点不对 |

## HEU 验证码规则（user 纠正）

- **4 字符**
- **纯字母 a-zA-Z**（不含数字）
- **不分大小写**

## HEU 登录关键发现

1. **HEU 接受明文密码**（publicKey 始终为空，passwordRSA 跳过加密，pwdAfter: "UNENCRYPTED"，5/5 登录成功）
2. **publicKey 死路**（HTML 空 + Vue data 无 + login.js 无写代码 + XHR 拦截无）
3. **死磕 4 小时 publicKey 死胡同**（user 之前重新确认的 `7Fuckyou` 密码是 user **自己**记错——`u` 实际是 `e`，正确密码 `7Fuckheu`）
4. **ACCOUNT_LOCKED** 是 `7Fuckyou` 错密码触发，**不是**加密问题

## 登录完整流程

```python
# Step 1: login (allow_redirects=False)
GET /cas/login → 拿 lt/execution + Set-Cookie
GET /sso/apis/v2/open/captcha?imageWidth=100&captchaSize=4 → 拿 img + token
# ddddocr 喂原图（不预处理）+ 过滤: 长度=4 + 不含数字 0-9 + 不含中文
POST /cas/login (form-urlencoded) body: {username, password(明文), captcha, token, lt, execution, _eventId=submit, source=cas}
→ Set-Cookie CASTGC (TGT-...)

# Step 2: 访问 jwgl 根 (allow_redirects=True) — 自动 SSO
GET /cas/login?service=cas → 触发 SSO
→ Set-Cookie CAS_TICKET + LOGIN_TOKEN + GS_SESSIONID + _WEU + jwgl JSESSIONID

# Step 3: 访问 wdkb 入口
GET /jwapp/sys/wdkb/*default/index.do → 初始化 wdkb app
POST /jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do → 拿当前学期
POST /jwapp/sys/wdkb/modules/xskcb/xskcb.do (XNXQDM=2025-2026-2) → 34 门课
```

## v1.0.15 抓不到节次时间 根因实测

### HEU 教务系统暴露的数据

| API | 内容 | 字段 | 学生权限 |
|---|---|---|---|
| `/jwapp/sys/wdkb/modules/wdkb/jcsj.do` | 节次时间定义 | 13 节 5 大节 | ❌ 403 |
| `/jwapp/sys/wdkb/modules/wdkb/jcsjmb.do` | 节次时间模板 | 同上 | ❌ 403 |
| `/jwapp/sys/wdkb/modules/xskcb/xskcb.do` | 学生课表 | KCM, KSJC(开始节次), SKZC(周次位图), SKJS(教师), JASMC(教室) | ✅ 200 |
| xskcb 响应字段 | **KSSJ=null** (开始时间**没有**) | — | — |

### v1.0.15 抓取逻辑

- 注入 WISEDU_FETCH_JS 到 WebView
- `fetch xskcb.do` → 拿课表
- `document.body.innerText` regex 抓 `HH:MM-HH:MM`
- innerText 抓**空** → periods=[] → Activity fallback 12 节

### v1.0.15 真因（实测）

1. **xskcb 响应 KSSJ=null** — HEU 不在 API 暴露节次时间
2. **jcsj endpoint 学生 403** — HEU 节次时间 API 学生没权限
3. **wdkb 入口 HTML body 完全空** — widget `weekUnitTableInfo.js` JS 动态渲染
4. **widget `loadUnitsUrl` 参数化** — 节次时间在 widget 渲染后 DOM，URL 端点未知
5. **widget 实例化位置未找到** — boot.js / boot_before.js / jsloader.js / jwcommon.js / jwpublic.js / ggFunctions.js 都不含 weekUnitTableInfo 实例化
6. **v1.0.15 innerText 抓取时序问题** — widget 渲染需要 ajax 异步拉数据

### 修复方向（v1.0.15 范围外）

- **不**能 auto-extract HEU 节次时间（HEU 端点 + API 都不暴露给学生）
- 方案 A: **hardcode** HEU 13 节时间表到 `schools.json`（与 v1.0.13 一样）
- 方案 B: xskcb 返 KSJC（开始节次）+ 默认 13 节时间表
- 方案 C: user 手动填节次时间（v1.0.13 TimeSlotEditor 已有）
- 方案 D: 用 mcp browser + vision 实际渲染后 widget 找节次时间（需 user 真机验证 widget 渲染流程）

## 当前状态

- ✅ 凭据已存 `~/.hermes/.credentials.json` (学号 2025089104, 密码 7Fuckheu)
- ✅ HEU 登录 5/5 成功 + wdkb app 完整访问 + xskcb 课表 200 (34 门课)
- ❌ HEU 节次时间对学生**不**暴露（jcsj 403 + xskcb KSSJ=null + widget 渲染时序未知）
- ❌ v1.0.15 抓不到节次时间 = HEU 后端不暴露学生端节次时间数据
