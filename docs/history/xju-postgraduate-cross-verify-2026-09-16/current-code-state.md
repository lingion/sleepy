# Current Code State — xju_post (2026-09-16 收录前基线)

## 已有 (main 7ddb6254)

- parser: `JwXjuParser` (`app/src/main/java/com/lingion/sleepy/data/jw/JwWakeUpCompatParsers.kt:443`)
  → `WakeUpCompat.parseXju` (:207): `#ctl00_contentParent_dgData` 主选择器 + `#contentParent_dgData` 兜底;
  th 首行 星期日/星期一 顺序 → sundayFirst 翻转 day; td 索引+1=day (node td 占 index 0, align=center);
  rowspan → endNode = node+rowspan-1; ｛名(周次)[教师：X,地点：Y]｝以；分条、、分周次 (parseWeekTokens 单双)。
- type: `JwProtocol.TYPE_XJU_POST = "xju_post"` (:61), WAKEUP_COMPAT_TYPES 成员, displayName「西交/新疆大学教务」
- Registry: `JwParserRegistry.kt:27` 优先级 15, `:69` 工厂路由
- 测试: `JwWakeUpCompatParserTest.kt:144` xju dgData brace blocks (单双周/星期序/教师地点)
- schools.json: **无新疆大学条目** (339 校, xju.edu.cn 域名 0 条) — 本轮缺口 ①

## 缺口 (本轮要补)

1. schools.json 无条目 → 用户列表选不到新疆大学
2. 抓取通路: xju_post 走默认 DFS 路径, 但 `JwWebViewFrameCapture.ANCHORS` (:120) 无 dgData 锚 —
   课表在 PageFrame 同源 iframe 内且全 frame 无 ANCHORS 命中 → selectBestFrame ④
   (looksLikeLoginPage: loging.aspx 页含 btLogin 弱指纹) → SESSION_EXPIRED 误报。
   修法: ANCHORS += "contentParent_dgData" (id 属性子串匹配, ctl00_ 前缀部署差异兼容 —
   anchorNeedle 匹配 id="contentParent_dgData" 同时命中 id="ctl00_contentParent_dgData" 子串? 否 —
   needle 是 `id="contentParent_dgData"` 完整属性串, ctl00 前缀形态下 needle 为 `id="ctl00_contentParent_dgData"`;
   故锚名取共同后缀 "contentParent_dgData" 并同时登记两种 id 全形)。
3. detectProtocolFromUrlImpl 无 xju_post 分支 → 用户手输 yjspy.xju.edu.cn URL 时判 null。
   修法: host 锚 `yjspy.xju.edu.cn` → TYPE_XJU_POST (①c YETHAN 同款 host 唯一锚模式)。
4. 无 xju_post fixture 文件 (既有测试用内联 HTML)

## 不需要动

- parseXju 文法本身 (85 形态由 WakeUp 逆向 + 同族仓交叉验证过, 契约测试已锁)
- WAKEUP_COMPAT_TYPES / Registry / knownTypes (无新 type, xju_post 已全注册)
- WebView fetch JS (DFS 通用通路可达同源 iframe, 不需要专属 XJU_FETCH_JS)
