# 学校目录来源与准入

目录收录与教务导入是两个独立口径。旧 350 个导入配置保留其既有实测证据；若发现确证错配，所有配置均须纠正或停用；新增条目只有当前公开页面具备教务入口证据、且解析器候选合理时才列为“待验证，可尝试”。未进行账户登录或真实课表导入，新增条目均不标为已支持。计数以 manifest.json 为准。

## 来源范围

- 教育部《全国高等学校名单》：2026-06-18 发布，截至 2026-06-17，大陆 3,196 所（普通 2,952、成人 244）。原名单不含港澳台；不据此宣称军事院校全量。
- 香港教育局：22 所本地学位颁授高等院校；澳门教育及青年发展局：10 所高等院校；台湾地区官方 114 学年度地址资料：149 所。港澳页面未标日期，因此 source_date/data_as_of 为 null。具体地域口径见 scripts/data/official_regions_metadata.json，不能将此集合描述为包含所有特殊院校的“中国所有高校”。
- WakeUp 6.1.06 原版 APK 内置资源：3,570 个原始记录，内部时间戳为 2025-11-13 07:13:06 UTC；不是 2026 最新在线库。22 个是通用协议/分享入口，不是高校。asset SHA-256：3b4441d4598ea2c07766a0cf5baf93786ba90a671afd20390d8adb984fb83349。
- 用户先前提供的 WakeUp 52.36 差集附件：2,253 个记录。附件 metadata 称当时全库 2,440、与 Sleepy 匹配 187。此次将用户指定的 2,253 条完整保留追溯，其中 22 个通用入口单列排除。

两 WakeUp 源合计 5,779 条学校来源行、44 条通用入口来源行。重复、旧名、研究生与不同校区入口不能计成同等数量的独立高校。只做精确校名匹配及 school_catalog_renames.json 明确列出的更名；不做模糊匹配，不去掉研究生或校区后缀。旧名保留在 sourceName，原 URL/type 只作来源证据，不自动进入可导入配置。

## 可复现文件

- scripts/data/school_catalog_baseline.json：扩展前 350 个配置快照。
- scripts/data/official_schools.json：3,377 条官方事实名录，逐条带来源 URL、日期、行号或官方代码。
- scripts/data/wakeup_school_sources.json：学校名、原入口、协议标识、导入类型和来源行号；excludedTemplates 单列通用入口。
- scripts/data/school_catalog_renames.json：明确更名白名单及证据，不复用仅用于城市定位的研究生/校区映射。
- scripts/data/school_catalog_pinyin.json：固定检索排序拼音，不用于判断学校实体。
- scripts/data/school_import_admissions.json：经审核的公开页面证据和可尝试准入决策。
- manifest.json：计数、状态分布、固定输入 SHA-256、覆盖检查摘要。
- source-links.json：每个目录 ID 对应的全部官方/WakeUp 来源行，保留旧 sourceName。
- import-admissions.json：最终可尝试名单及 HTTP/页面证据。

运行 python3 scripts/generate_school_catalog.py 重建 main/test 两份资产及审计结果；运行 python3 scripts/generate_school_catalog.py --check 检查输出是否与固定输入完全一致。生成器只用 Python 标准库，不访问网络、不依赖 APK。城市索引独立由 scripts/enrich_school_cities.py 生成；人读目录由 scripts/generate_schools_list.py 生成。

## 准入限制

公开页面探测日期为 2026-10-14，不采集凭据，不登录。HTTP 200 仅说明页面可读，不能说明导入成功。403、412、超时、私网/VPN等保持待适配，不据此声称学校关闭。

WakeUp importType=ziyan、非空专有 customConf、未知协议、空 URL 不直接启用。通用站点品牌、sudy/学校首页、官网应用列表中的教务超链接不算教务页面证据。zf 必须区分现代正方和旧版正方，不能只按同名映射。当前乘方品牌没有证明 cf 旧版解析器，故相关候选暂缓。金智认证外壳无 wdkb 证据暂缓。EAMS 的 Beangle 资源只能证明入口属于候选家族，仍未验证实际课表结构。所有新增可尝试项须用户确认，且 enableFetch=false，不把未核实抓取路径自动启用。

## 来源与许可

教育部入口：http://www.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/t20260618_1441074.html。港澳台官方入口逐行保存在 official_schools.json；来源时间未知时保持 null。

WakeUp 数据来自本机原版 APK 和先前用户附件，未定位到该专有 APK/程序的再分发许可证。仓库仅记录学校名称、事实入口、协议标识和出处，不复制 APK、反编译程序、专有抓取脚本或配置代码，不把专有实现标为 Apache-2.0。公开可获取不等于专有程序已获再分发许可；项目许可证不覆盖第三方原始材料。


## URL 脱敏边界

来源 URL 递归检查百分号编码层及嵌套 service/redirect URL。账户、口令、token、ticket、认证 code、state 和会话字段（含 ASP.NET 路径会话及 jsessionid）只保留 REDACTED；每个受影响字段的 urlRedactions 记录原始 URL 的 SHA-256、参数名及 displayCopyOnly 标记。client_id、response_type、学校/应用代码等公开路由参数保留。脱敏来源仅为展示证据，禁止作为带会话的操作链接；可导入 URL 不携带 REDACTED 占位或旧会话。

原始来源保留在研究目录外部，不提交仓库。scripts/catalog_url_safety.py 在生成器读取固定输入时执行 fail-closed 检查；遇到未脱敏敏感 URL 会中止，错误仅列字段路径与参数名，不回显值。合成边界用例由 python3 scripts/test_catalog_url_safety.py 运行。

## 学校与入口归属校验

`school_url_evidence.json` 对扩展前的 519 个非空入口逐条记录独立的学校归属核验。生成器严格检查证据 ID、学校名和被审 URL 一致，防止修改 URL 后沿用旧证据；缺证据、重复记录、未知判定值均拒绝生成。HTTP 可达性、协议指纹、公开归属、登录后导入是独立结论。

新候选若归属未知，撤回到 pending，清空操作 URL/type；有确证错配时旧配置同样停用。替换入口需学校官方发布地址和对应页面身份、相同协议候选共同支持，替换后仍为 unverified。潍坊护理职业学院的错校入口已替换为经官方公告、HTML刷新及连续HTTP跳转证明的实名入口；替换后仍为unverified且enableFetch=false。保存快照已去除Cookie、脚本和输入值，保留原始及脱敏快照哈希；错误来源 URL 留在审计证据中以便追溯。原有实测配置不会仅因本轮无法公开访问而自动失效；未解决的公开归属证据缺口在 manifest 的 ownershipAudit 中明确列出，不声称全部已验证。

运行 `python3 scripts/test_catalog_ownership.py` 检查未知/错配入口阻断、证据时效绑定和研究生状态保留。位置证据详见 `location-evidence.md`，城市匹配精度不等于校区坐标精度。

入口等价仅接受相同 scheme、host、port、path、query、fragment（默认端口与根斜杠规范化），或归档的连续定向跳转。不凭同一主机、父路径、通用 SSO 页面推断租户归属。

## 用户体验：学校选择只展示可尝试条目

早期版本应用内学校选择页加载 `schools.json` 全部 4,140 条目录条目，其中仅约八百条带教务入口 URL，其余显示为不可点击的灰色项，用户反馈“四千多所学校只有几百所能用”。`JwImportViewModel.loadSchools()` 现过滤 `url.isNotBlank()`，只展示可实际尝试导入的条目（当前 1,478 条，见 manifest.statusCounts 与 [学校目录](../schools-list.md)）；待适配条目仍通过目录文档、issue 模板和自定义 URL 识别通道可被发现和申请。过滤发生在展示层，目录数据本身保留全部条目与来源记录，收录不等于支持的原则不变。
