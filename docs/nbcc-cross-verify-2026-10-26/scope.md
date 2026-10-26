# NBCC(宁波城市职业技术学院)WebVPN 教务适配 — 立项范围

- 日期: 2026-10-26
- 学校: 宁波城市职业技术学院 (NBCC), catalogId `moe:4133012645`
- 登记现状(github/main schools.json, 共 4140 校): `url=https://webvpn.nbcc.edu.cn/login`, `type=shuwei_json`, **`status=unverified`, `enableFetch=false`** — 目录补 URL 战役(2026-10-16 probe)只验到「webvpn.nbcc.cn 302 → webvpn.nbcc.edu.cn/login(WEBVPN 标题)」,教务真实入口当时不可见,故协议按 wakeup 资产(`xatu_shuwei`→`shuwei_json`)猜配,从未实测。
- 触发来源: 学生 issue(邮箱/采集包, session `20261026-115437`, app v1.0.62)。用户报:「账号密码都对,但一直显示密码错误」。学生凭据 = VPN 门户凭据(门户 API 全带票成功,cookie 有活 `wrdvpn1-` 票据)→ **排除真错**。
- 适配类型: 初次适配(该协议族深信服 WebVPN 在库内无登录通路)。

## 已确证根因(采集包 netlog 实锤, /tmp/jwdump-20261026/netlog.txt)

1. VPN 段传递正常: 门户登录→钉钉回跳 `?code=`→`/m/portal` 全程带票(`wengine-vpn/cookie?method=get` 桥接正常)。
2. 断点: 门户点「教务管理」→ MAIN 导航 `/https/<hex>/app-web/dingding-login?corpId=ding6b93...`(EAMS 移动端 SPA,Ionic+Angular7,真实域 `jwglapp.nbcc.cn`)。
3. 该 SPA 设计上**钉钉容器 authCode 换票**免密;console 实锤 `DINGTALK-JSAPI ERROR 4040: notInDingTalk` + `Cordova is not available` → 换票请求**根本没发出**(netlog +67s→+116s 零 nbcc POST)。
4. 换票失败 → App 落兜底手输页 → 学生输 VPN 凭据但 EAMS 内部账号体系不同源 → 「密码错误」。
5. 结论: **环境/传递问题**(容器依赖),与学生凭据无关;定性接受用户判断——「我们的传递」在此处=原生容器桥(notInDingTalk)。

## 本轮任务(用户原话锚点)

- 「调研所有 webvpn 的解决方案」 → Step 2 检索矩阵全量跑(见 candidates.json)
- 「结合调研内容适配 NBCC」 → Step 6 修复设计
- 「考虑新增一个密码传递器」 → 候选方案之一(WebView JS 向原生递凭据,原生走 VPN CAS 登录链),以调研为准
- 「参考 HFUT 当时的经验」 → 考古 github/main: `034525cf`(#25 四段链)、`963d929f`(#46 节次查表)、`fc38a18a`(WakeUp 反汇编)、对抗 fixture 组 `jw_fixtures/adversarial/webvpn-urls/`(pku/bnuz/cf)、`HfutPortalEams5WebViewContractTest` 等
- 问学生(主线负责): 不用钉钉,该校学生平时怎么进教务(PC 端? App?)——`nbcc_pc_entry` 证据由调研 agent 并行收集

## 完成门槛(gate)

- [ ] Step 2 检索矩阵全跑,candidates.json 全量纳入(唯一否决: archived ∧ pushed>10y)
- [ ] 每个候选一份 schema 化 verdict(findings.json 与 candidates 1:1)
- [ ] protocol-matrix.md ≥1 form(WebVPN 登录链横向对比)
- [ ] Step 5.5 致谢前置: 触达仓库全部进 LicenseScreen + 6 语 strings + AboutLicenseAttributionTest(动手改代码之前)
- [ ] 修复方案有证据支撑: NBCC 学生实际路径(PC 直连 / 门户+换票注入 / 密码传递器)三选一或如实 pending
- [ ] fixture N form × 反例 + 回归测试;跨语言 invariant(Kotlin pattern ≡ JS literal 逐字符)
- [ ] `./gradlew :app:testDebugUnitTest` 全绿 + lint 0 新增 warning
- [ ] commit 身份 lingion@hrbeu.edu.cn, 无 Co-Authored-By, 无 Fixes/Closes/Resolves #N(ref #N)
- [ ] docs 六件套齐;SOP 复用即修订(v1.14→v1.15)
- [ ] **硬约束: 不拿学生真实凭据发登录请求做实验**。协议验证只用: 公开 JS 逆向 + 无凭据端点探测(预期 302/登录页) + 学生自愿回传。

## 红线

- 深信服 WebVPN = 学校官方给学生的访问通道(学生本来就用),我们做的是该通道内课表数据的自动化读取,与 HFUT/NEU/BUA A 等学生开源工具同性质;禁任何撞库/提权/访问非教务资源。
- KEY/IV 属前端可提取常量(wengine-vpn-decryptor 公开逆向),用于 URL 加解密是协议适配必需,不作绕过认证用。
