# Current code state (2026-10-13)

- JwProtocol TYPE_SEU invokes WebView fetch at the SEU portal, reusing WISEDU_FETCH_JS with a portal host allowance and selected-term lookup in one same-origin iframe level.
- Fetch checks HTTP status, JSON parse and datas.xskcb.rows array. An empty array remains a valid empty timetable; missing rows and login HTML fail with an actionable error.
- JwSeuParser safely unwraps data and prioritizes a syntactically valid SKZC bitmap over ZCMC. A syntactically valid all-zero bitmap means no weeks, without text fallback; malformed bitmap uses ZCMC.
- tools/test-seu-fetch.js exercises twelve VM cases and CI runs it. JwSeuParserTest and JwWiseduWebViewContractTest cover parser and source contracts.
- The jwgl.seu.edu.cn/xsglapp alternative is not verified and has no implemented gateway switch. No real authenticated SEU end-to-end proof exists in this audit.
