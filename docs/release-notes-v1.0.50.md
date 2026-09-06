# Sleepy v1.0.50

### 致谢页按学校聚合

教务适配致谢页面改版。原先按仓库逐条列出（29 条单卡滚动阅读），现在按两层结构组织：跨校普适项目（WakeUp / WakeupSchedule_BUPT / WakeupSchedule_Kotlin / 时光课程表 cqu.js / shiguang_warehouse / zfn_api / FlowCourse / iwut / shangkeschedule）保持单卡直陈；单校项目按学校聚合，1 校 1 卡，31 张卡片，默认收起，点击展开后看到该校所参考的所有学生维护 GitHub 项目；GDMU 为用户采集包确认项（共 74 个单校 token）。

覆盖范围以 179 校全量交叉验证（commit `94485ee`）过程中触达的仓库为底：合肥工业大学（4 个）、东南大学（3 个）、东北大学（6 个）、重庆大学（8 个）、电子科技大学（5 个）、广东工业大学（5 个）、长沙理工大学（5 个）、北京大学（5 个）、北京邮电大学（3 个）、广东财经大学 / 广东金融学院 / 广东外语外贸大学 / 北京林业大学 / 东北林业大学 / 东华大学 / 云南财经大学 / 安徽大学 / 四川大学 / 浙江大学 / 中国科学技术大学 / 武汉理工大学 / 北京化工大学 / 北京理工大学 / 北京信息科技大学 / 安徽建筑大学 / 重庆邮电大学移通学院 / 华南农业大学 / 齐鲁工业大学 / 渤海大学 / 东北石油大学 / 南京理工大学（各 1–2 个）。

展开态用 `mutableStateMapOf` 按卡片 id 维护，跨滚动保持。回滚逻辑只在用户主动切回关于页时清空。

致谢段落文本已在 6 个发布语言（values / values-zh-rCN / values-zh-rTW / values-en / values-ja / values-es）同步重写，每条仓库名 + 作者标记跨语种不翻译，作为 `AboutLicenseAttributionTest` 的漂移闸门依据。测试覆盖从原 29 条 token 扩展到 83 条（跨校 9 + 单校 74，含 GDMU 单 token 软致谢，因 zh-TW 与其余语种校名汉字不同不取汉字做漂移闸门），全部 6 语必查。

### 广东医科大学 GDMU：裸 `/kbcx/` 路径落地

正方教务新版 zftal-ui-v5 的个人课表真实 URL 为 `https://jw.gdmu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default`，不再走 `/jwglxt/` 前缀。原 `JwZfNewFetchJs` 仅校验 `/jwglxt/` 路径，导致用户场景报错 `请先导航到个人课表页(地址应含 /jwglxt/)`。本次同步在 JS 与 Kotlin 两侧把 `/kbcx/` 与 WebVPN `/http/<hex>/` 一并纳入合法路径前缀判断，API 路径拼接保留 `pathPrefix + '/kbcx/xskbcx_cxXsgrkb.html'`，契约由 `JwImportViewModelBridgeTest` 锁死，禁止硬编码 `/jwglxt/kbcx`。

GDMU 无外部学生维护 GitHub 仓库可参考，本次落地依赖用户提供采集包实锤 zftal-ui-v5 形态，关于页以单校软致谢形式记录（30 张学校卡中第 31 张）。

### 关于外网可达性的诚实声明

发布前对 `https://jw.gdmu.edu.cn/` 实测 `cfp-fetch` 探测：域名解析正常，但 HTTPS 握手后连接超时（exit 28），主站 `https://www.gdmu.edu.cn/` 仍可达。这是学校侧教务出口的临时网络/防火墙/维护状态，非本应用代码阻塞。建议用户在 GDMU 教务出口恢复正常后再做导入。
