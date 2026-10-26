# BJTU 跨仓验证 — 立项 (scope)

> 日期: 2026-09-09 · 分支: `adapt/bjtu-jw` (base=main a6e2cac) · issue: #19

## 学校信息

- **学校全称**: 北京交通大学 (Beijing Jiaotong University)
- **英文缩写**: BJTU
- **slug**: `bjtu`
- **适配类型**: 初次适配 (schools.json 无北京交通大学条目, 181 条中无任何 bjtu/北交大记录, git 历史无涉及该校的 commit)
- **涉及现行 parser**: 无 (新增协议支持; 候选协议族待 Step 3/4 跨仓验证后定)

## 用户原话 (issue #19)

> [Adapt]: 求适配北京交通大学

- Author: `2627581175-cloud` · Created 2026-09-06T15:52:02Z
- **正文为空**, 0 评论, 0 附件, 无采集包
- **状态: closed** — 作者本人于 2026-09-06T16:02:09Z 自行关闭 (state_reason: null)
- 本轮不评论/不重开/不改 issue 状态 (任务禁令 + [[issue-reply-needs-explicit-approval]])

## 数据缺口 (本轮如实记录)

| 缺口 | 影响 | 缓解 |
|------|------|------|
| 无采集包 | 无真实页面 HTML/请求序列可对照 | fixture 用公开仓库真实数据形状; 不编造 |
| 无教务 URL | 连域名都只能靠公开仓库交叉证实 | 检索矩阵 D 查 (代码内字符串) 锚定真实域名 |
| 无真实账号 | 无法实测登录后接口 | 交付=代码层支持 + 公开数据 fixture 单测验证; AHU v1.0.51 式已知限制措辞 |
| issue 已被作者关闭 | 无法在 issue 下追问 | 不动 issue 状态; 适配照做, 报告里说明 |

## gate (完成门槛)

1. schools.json 新增北京交通大学条目 (sortKey=B, 插入位置=北京化工大学与北京理工大学之间, 禁全量重排)
2. 新协议 parser + 接线 + fixture (公开仓库真实数据形状) 单测全绿
3. 致谢 1:1 (candidates.json 数 = attribution 条数), LicenseScreen + 6 语 strings.xml + AboutLicenseAttributionTest 同步
4. `./gradlew :app:testDebugUnitTest` 全绿 · `lintDebug` 不新增 warning · `*AboutLicenseAttributionTest*` 绿
5. 无采集包→不声称"已用真实账号验证"; release notes 已知限制措辞 (AHU v1.0.51 式) 已产出
