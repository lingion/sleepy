# 求适配浙大宁波理工学院，正方教务\n\n- Number: #11\n- State: open\n- Author: L61400\n- Created: 2026-09-02T17:25:40Z\n- Updated: 2026-09-06T13:38:36Z\n- URL: https://github.com/lingion/sleepy/issues/11\n\n## Body\n\n### Prerequisites

- [x] I have searched existing issues and found no duplicate
- [x] I have confirmed this feature is not already in the latest release

### Problem or motivation

求适配浙大宁波理工学院，正方教务

### Proposed solution

求适配浙大宁波理工学院，正方教务

### Alternatives considered

_No response_

### Additional context

_No response_

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-03T02:19:36Z\n\n正在适配中,这所学校已经在计划里。

收录之前可以先试试 URL 导入:在学校搜索页的搜索框里直接粘贴你们教务系统的网址,识别出 URL 后会出现「直接用此 URL 登录」,登录进去会自动按正方教务抓取课表。

如果这样还是导入失败,麻烦补充说明一下:教务系统的完整网址、卡在哪一步、失败时的具体表现(报错提示/一直加载/课表空白都算)。我拿到这些再做 URL 导入的适配优化。
\n\n### lingion — 2026-09-03T02:44:27Z\n\n收录已提交:学校搜索页现在能搜到「浙大宁波理工学院」了(支持搜「宁波理工」或「nbt」),入口按你们教务处官网挂的地址走的,协议识别为老版正方。等下个版本发出来就能直接用。

提醒一下:这个教务地址目前只有校内网(或 VPN)能连,校外直连不通,导入时如果一直转圈先看看是不是没连校园网。\n\n### lingion — 2026-09-03T15:17:26Z\n\n已收录进 v1.0.45,刚发布:https://github.com/lingion/sleepy/releases/tag/v1.0.45

学校搜索页搜「浙大宁波理工学院」「宁波理工」或「nbt」都能找到,升级后直接登录教务拉课表即可。再提醒一次:该教务地址目前校外直连不通,导入时需要校园网或 VPN。

试用中有问题(拉取失败、课表不对、时间错乱都算)欢迎在下面补充,我继续跟进。\n\n### lingion — 2026-09-06T13:38:36Z\n\n已在 [v1.0.45](https://github.com/lingion/sleepy/releases/tag/v1.0.45) 收录并发布 — `http://jwxt.nit.net.cn/default2.aspx` (老版正方 zf) 适配已可用,升级到 v1.0.45+ 即可在校内网/VPN 下导入。

**如果还有问题**,按 [采集教程](https://github.com/lingion/sleepy/blob/main/docs/adapt-kit/README.md) 跑一份适配包回来 — 工具支持 Windows/Mac/Linux, 校网环境下跑通常 5-10 分钟, 拿到 `sleepy-adapt.zip` 我就能定位是路径变了还是字段变了。

校外直连不通是已知网络限制(端口 80/443/8080 全滤), 校园网或学校 VPN 是前提条件, 这条限制 v1.0.45 已经说明过, 本版本未变。

其他疑问或网络层之外的 bug, 都可以继续回这个 issue。
