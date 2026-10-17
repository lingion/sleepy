# Wave 2/3 证据生成报告（PR #179）

数据基准：2026-10-17。本报告汇总四个阶段的证据管线结果；所有数字都可以追溯到仓库内的证据文件与脚本，可复现、可审计。

复现顺序：

1. `python3 scripts/adjudicate_protocol_mapping.py --write` — 逐行协议裁定（回放 `scripts/data/batch-probes.jsonl` + `scripts/data/entry-evidence-captures.jsonl`）
2. `python3 scripts/capture_entry_evidence.py --resume` — 补抓公开入口页
3. `python3 scripts/integrate_batch_evidence.py` — 把捕获整合进 `scripts/data/school_url_evidence.json`
4. `python3 scripts/generate_school_catalog.py && python3 scripts/generate_schools_list.py` — 重建目录与人读清单

## 阶段 1 — 逐行协议裁定

输入是 WakeUp 来源的 2,499 条批量 admission（`scripts/data/school_import_admissions.json` 第 171 行起，前 170 行为冻结基线）。每一条只有两种合法证据类别：

- **URL 强特征**：路径/参数锚定已知协议（如 `/jwglxt/xtgl/login_slogin.html` → 金智、`/eams/homeExt.action` → 强智 classic_eams、`zxkd` → kingo_new）；
- **页面指纹**：脱机捕获的入口页 HTML 中的产品名、表单 action、资源路径指纹。

CAS / authserver / 通用门户壳一律**不构成**协议证据；不做批量猜测；无充分证据即 fail-closed 留在 `ziyan` 等待人工适配。每条裁定连同 `type|source|label` 证据写入 `scripts/data/adjudication-log.jsonl`，全 2,499 行的裁定结果表见 **[PROTOCOL_MAPPING.md](PROTOCOL_MAPPING.md)**。

当前状态（本轮 13 行由新捕获指纹改判后）：

| 项目 | 数量 |
|---|---|
| 批量 admission 总数 | 2,499 |
| 已映射到具体协议 | 2,499 |
| 裁定为可路由（已注册解析器） | 1,586（其中 5 条无可用 finalUrl） |
| 维持无解析器协议（fail-closed，永不可运营） | 913 |

改判示例（均有日志证据）：兰州/吕梁职业技术学院 ziyan→kingo_new；河南广播电视大学/河南开放大学/郑州信息科技/长江大学文理/马鞍山 ziyan 或 shuwei_m→classic_eams（eams URL 锚点）；江西飞行/牡丹江医科/长春工业人文→cf；苏州工学院/贵州民用航空→zf_new；锦州医科大→qz。

## 阶段 2 — 入口页证据捕获与整合

`capture_entry_evidence.py` 只抓取 probe http==200 的公开入口页（无登录、无第三方），追加写入 `scripts/data/entry-evidence-captures.jsonl`（3,031 行，其中 1,585 条可用捕获；HEAD 中 1,808 行保持字节级不变，只追加不修改）。`integrate_batch_evidence.py` 将其折叠进 `scripts/data/school_url_evidence.json`：

| 项目 | 数量 |
|---|---|
| 证据条目总数 | 2,100（批量 1,581 / 基线 350 / 导入 169） |
| 批量 admission 中有证据行（active，去重后） | 1,581 |
| — verified（来源页证明身份） | 400 |
| — unknown（身份不足以证明，fail-closed；含 487 未抓到入口页、70 抓取时不可达） | 1,181 |
| 全库所有权裁定 | verified 748 / unknown 1,351 / contradicted 1 |
| evidence == active 不变式 | 0 missing / 0 stale / 0 empty-url / 0 url-mismatch |

排除在证据体系之外、未强行给证据的行：913 行无解析器协议（unroutable-type）+ 5 行可路由类型但无可用 finalUrl（no-url，含会话绑定 REDACTED 值）；其余 1,581 行全部获得证据行——其中 487 行 probe 非 200 未能抓取入口页（no-capture）、70 行抓取时不可达（unreachable）、1,024 行按抓取页证据裁定（verified 400 / unknown 624）。

### 修复的 NEL 数据完整性缺陷

捕获页正文含 `\x85`(NEL)/`\u2028`/`\u2029` 时，`str.splitlines()` 会把 JSONL 单行误当作多行边界，`clean_journal` 随之把合法行当“损坏”静默删除。已在全部读写点修复：统一 `split('\n')`，写入时转义这三个字符。修复后两条此前被跳过的 probe-200 端点（文华学院、长沙电力）补抓成功，并按证据正确 fail-closed 为 unknown（泛化产品标题不具校名身份）。证据日志完整性以 HEAD 前 1,808 行逐字节比对验证。

## 阶段 3 — 目录重建与验证

`generate_school_catalog.py`（`--check` 幂等通过）+ `generate_schools_list.py` 重建，断言冻结基线不被改写、无 pending-with-url、无新增 supported：

| 指标 | 上一轮（2026-10-16） | 本轮（2026-10-17） |
|---|---|---|
| 目录条目 | 4,140 | 4,140 |
| 已实现配置（supported + grad_supported） | 350 | 350（未变） |
| 待验证·可尝试（unverified） | 418 | **1,128** |
| 待适配（pending + grad_pending） | 3,372 | **2,662** |
| 带教务入口 URL 的条目 | 873 | **1,478** |

关于 2,100 条证据行与 1,478 条带 URL 条目的差额（所有权闸门 + 域名碰撞闸门，`catalog_ownership.apply_ownership` / `_candidate_poisons`，fail-closed 设计）：

| 所有权动作 | 数量 | 结果 |
|---|---|---|
| ownership-confirmed（verified 且来源页证明身份） | 748 | 保留/新启用 URL |
| retained-wakeup-declared-owner（WakeUp 声明校长，unknown） | 592 | 保留 URL（可尝试） |
| retained-existing-config-ownership-unresolved | 137 | 历史配置保留 URL |
| corrected-official-entry | 1 | 官方链路纠错后启用 |
| withheld-new-unproven-owner | 590 | 新条目身份未证明 → 置 pending、清空 URL |
| withheld-domain-collision | 32 | 与 HEAD 唯一行同主机/域名，会破坏 URL 反查 → 拦截（依据表见下） |

唯一 contradicted 行（潍坊护理职业学院错校入口）经官方公告 + HTML 刷新 + 连续 HTTP 跳转链证明后替换为实名入口（corrected-official-entry），仍为 unverified、enableFetch=false。

即：590 条“URL 可达、协议可路由，但公开证据不足以证明该入口属于这所学校”的行被刻意拦下——收录不等于支持，宁可少放不可放错。`docs/catalog/manifest.json` 的 `ownershipAudit` 保存每条决定，`docs/catalog/import-admissions.json` 收录全部 1,128 条可尝试行（各带 `limitation` 声明：仅有公开入口与协议族证据，未做登录态导测）。

### 域名碰撞闸门（withheld-domain-collision，32 条）

保留 WakeUp 声明校长的晋升（+591 条）带来一个新风险：两条目录行若落在同一主机或同一可注册域名上，应用侧 fail-closed 定位（`SchoolDomainMatch.matchSchool`：同条目 → 同主机 → 可注册域名三层，唯一且已支持的行才返回）会从“恰好命中一行”退化为“命中多行/空”，用户按 URL 反查学校时拿不到唯一结果。因此 `catalog_ownership._candidate_poisons` 对每条晋升候选做忠实复刻的回归探针：候选的 URL（非 webvpn 主机）与其可注册域名的合成探针（webvpn 主机作为查询输入不可见，但作为域名层条目仍然污染）在 HEAD 冻结目录（`scripts/data/school_catalog_domain_reference.json`）中解析到唯一已支持行、而在生成视图中解析失败或错位，即拦截该行（置 pending、清空 URL）。凡被 HEAD 已有行占住主机/域名的候选，全部同主机候选一并拦截。32 条逐条依据：

| 被拦条目 | 候选 URL | 类型 | 碰撞的 HEAD 唯一行（HEAD URL） |
|---|---|---|---|
| 大连工程学院 | http://jw.dlutci.edu.cn/jwapp/… | wisedu | 大连理工大学城市学院（http://jw.dlutci.edu.cn/jwapp/sys/emaphome/portal/index.do，同主机同条目） |
| 南通大学杏林学院 | https://authserver.ntu.edu.cn/authserver/login | zf_new | 南通大学（http://tdjw.ntu.edu.cn/，域名层 ntu.edu.cn） |
| 同济大学浙江学院 | http://eas.wzbc.edu.cn/jwglxt/kbcx/… | zf_new | 温州商学院（http://eas.wzbc.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html，同条目） |
| 安徽科技工程大学 | http://jwxt.ahstu.edu.cn/eams/localLogin.action | classic_eams | 安徽科技学院（http://jwxt.ahstu.edu.cn/eams/localLogin.action，同条目） |
| 集美大学 | http://i.jmu.edu.cn | cumtb | 集美大学诚毅学院（http://chengyi.jmu.edu.cn，域名层 jmu.edu.cn） |
| 山东大学 | http://bkzhjx.wh.sdu.edu.cn/sso.jsp | qz_with_node | 山东大学（威海）（https://service.sdu.edu.cn/，域名层 sdu.edu.cn） |
| 襄阳职业技术大学 | http://jwxt.hbxytc.cn/ | wisedu | 襄阳职业技术学院（http://jwxt.hbxytc.cn/，同主机） |
| 湖北工程职业学院 | https://jw.hbgyzy.edu.cn/ | qz | 湖北工业职业技术学院（http://jw.hbgyzy.edu.cn/jsxsd/，同主机） |
| 襄阳科技职业学院 | https://authserver.hbxytc.cn/authserver/login | wisedu | 襄阳职业技术学院（http://jwxt.hbxytc.cn/，域名层 hbxytc.cn） |
| 湘潭大学兴湘学院 | https://jwxt.xtu.edu.cn/jsxsd/ | qz | 湘潭大学（http://jwxt.xtu.edu.cn/jsxsd/，同条目） |
| 湖南工业大学科技学院 | http://jwxt.hut.edu.cn/ | qz | 湖南工业大学（http://jwxt.hut.edu.cn/jsxsd/，同主机） |
| 湘潭理工学院 | https://49.234.155.183/hngsdxbjxy_jsxsd/ | qz | 广西民族大学相思湖学院（http://49.234.155.183/gxmzdxxshxy_jsxsd/，同主机 IP） |
| 湖南农业大学东方科技学院 | https://webvpn.hunau.edu.cn/https/… | qz | 湖南农业大学（https://jwc.hunau.edu.cn/jwglxt/…，webvpn 主机经域名层 hunau.edu.cn 探针命中） |
| 广州职业技术大学 | http://jiaowu.gzpyp.edu.cn/jsxsd/ | qz | 广州番禺职业技术学院（http://jiaowu.gzpyp.edu.cn/jsxsd/，同条目） |
| 重庆三峡科技大学 | http://jwglxt.sanxiau.edu.cn/xtgl/login_slogin.html | zf_new | 重庆三峡学院（http://jwglxt.sanxiau.edu.cn/，同主机） |
| 吉利学院 | http://49.234.155.183/jlxy/ | qz | 广西民族大学相思湖学院（http://49.234.155.183/gxmzdxxshxy_jsxsd/，同主机 IP） |
| 西安电子科技大学 | http://ehall.xidian.edu.cn/new/index.html | qz | 西安电子科技大学 - 研究生（http://yjspt.xidian.edu.cn/，域名层 xidian.edu.cn） |
| 山东省聊城教育学院 | https://jw.lcudcc.edu.cn/jwglxt/… | zf_new | 聊城大学东昌学院（https://jw.lcudcc.edu.cn/jwglxt/xtgl/login_slogin.html，同条目） |
| 湖北省纺织职工大学 | https://jwglxt.wtu.edu.cn/jwglxt/… | zf_new | 武汉纺织大学（http://jwglxt.wtu.edu.cn/sso/jziotlogin，同主机） |
| 安徽医学高等专科学校 | http://authserver.ahyz.cn/authserver/login | qz | 安徽第二医学院（http://ehall.ahyz.cn/new/index.html，域名层 ahyz.cn） |
| 北京大学医学部 | http://elective.pku.edu.cn | pku | 北京大学（http://elective.pku.edu.cn，同条目） |
| 北京大学（校本部） | http://elective.pku.edu.cn | pku | 北京大学（同上，同条目） |
| 北京大学（医学部） | http://elective.pku.edu.cn | pku | 北京大学（同上，同条目） |
| 北京大学（深圳研究院） | http://elective.pku.edu.cn | pku | 北京大学（同上，同条目） |
| 北京航空航天大学北海学院 | https://sso.buaa.edu.cn/login | urp_new | 北京航空航天大学（https://byxt.buaa.edu.cn/jwapp/…，域名层 buaa.edu.cn） |
| 合肥工业大学（可切换学期） | http://jxglstu.hfut.edu.cn/eams5-student/login | eams5 | 合肥工业大学（https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table，同主机） |
| 清华大学（反馈群：807321351） | https://zhjwxk.cic.tsinghua.edu.cn/xklogin.do | thu | 清华大学（https://zhjwxk.cic.tsinghua.edu.cn/，同主机） |
| 山东省经济管理干部学院 | https://jwxt.sdmu.edu.cn/jwglxt/… | zf_new | 山东管理学院（http://jwxt.sdmu.edu.cn/jwglxt/xtgl/login_slogin.html，同主机） |
| 山东第二医科大学（原潍坊医学院） | https://webvpn.sdsmu.edu.cn/login | qz | 山东管理学院（http://jwxt.sdmu.edu.cn/jwglxt/…，webvpn 主机经域名层 sdmu.edu.cn 探针命中） |
| 山东大学威海校区 | http://service.sdu.edu.cn/ | qz | 山东大学（威海）（https://service.sdu.edu.cn/，同主机） |
| 厦门大学（教务） | https://jw.xmu.edu.cn/new/index.html | wisedu | 厦门大学 - 研究生（https://jw.xmu.edu.cn/new/index.html，同条目） |
| 西安建筑科技大学（可切换学期） | https://swjw.xauat.edu.cn/student/login | cumtb | 西安建筑科技大学（https://authserver.xauat.edu.cn/authserver/login，域名层 xauat.edu.cn） |

共同点：这 32 条要么是历史别名/更名重复（安徽科技学院↔安徽科技工程大学、潍坊医学院↔山东第二医科大学、襄阳职业技术学院↔襄阳职业技术大学/科技职业学院——同一教务系统换牌），要么与 HEAD 唯一行共享主机/域名（北大四个别名、集美↔诚毅、湖南农大↔东方科技学院、安医↔安徽第二医学院）。解锁条件：确认两行确属不同学校后，通过目录去重合并或由官方证据链改绑独立入口，而不是绕过碰撞闸门。

测试（串行执行，避免 KSP 并发冲突）：

- Python：ownership 41 项（含域名碰撞闸门 6 个定点用例）、URL 脱敏 7 项、pytest 61 项 — 全绿
- Gradle：`:app:testDebugUnitTest`（含 SchoolsJsonConsistencyTest、Schools179CrossValidationTest、JwImportViewModel 系列）、`:app:lintDebug`、`:app:assembleDebug` — 全部 BUILD SUCCESSFUL

## 阶段 4 — 说明与用户可见口径

- **[学校目录](../schools-list.md)** 计数与资产一致：4,140 条目；已实现配置 350、待验证·可尝试 1,128、待适配 2,662。
- 应用内学校选择页只展示带 URL、可实际尝试的 1,478 个条目（`JwImportViewModel` 过滤无 URL 行），不再有 4,140 全量列表造成的“灰项不可用”困扰；该 UX 修复记录已并入[目录来源说明](README.md)。
- 与旧指令中 2,185 预期 active 数的差异：该数字产生于逐行裁定之前；裁定把 913 行判为无解析器协议、并剔除了 CAS 壳等伪证据后，1,751（去重 1,581）才是证据正确的 active 规模。


## 遗留（不在本轮自动化范围）

- 913 行无解析器协议：需要先在应用内注册对应解析器或人工适配（`ziyan` 等待办见 PROTOCOL_MAPPING.md）。
- 590 行 withheld（owner 未证明）：需要官方校页→教务入口的证据链（meta-chain / 官方公布地址）才能解锁；另有 32 行 withheld-domain-collision 需要目录去重或独立入口证据。
- 289+ 城市映射、单校登录态实机验证：按 `docs/adapt-kit` 与 issue 模板推进。
