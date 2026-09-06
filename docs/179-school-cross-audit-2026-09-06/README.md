# 179-School Cross-Audit 2026-09-06

## 背景
179 所院校 教务系统适配 (schools.json) 全量交叉验证。
18 个 bucket 并行 subagent 验证,落地证据。

## 制品
- `bucket_<X>.json` (18 个, A..Z 含 X)
- `result_<X>.json` (18 个, A..Z + BUPT)
  - status: PASS / DRIFT / OFFICIAL_DENY / UNVERIFIED
  - old_url / old_type
  - final_recommendation (url / type / reason)
  - evidence_chain

## 总账
- DRIFT: 17 所 (其中 16 所有新 URL 可填)
- DRIFT 无新 URL: 1 所 (南宁职业技术学院, 学校升级 + jw 域名全挂)
- OFFICIAL_DENY: 2 所
- PASS: 5 所 (全部在 X 桶)
- UNVERIFIED: 1 所 (南宁职业, 同 DRIFT 无 URL 校)

## 待补
- 无 — X 桶 result_X.json 已补齐 (7 所), H/N 桶 final_recommendation 已 patch。

## 桶→subagent 映射 (父 session b0b8bfab)
A=agent-aa739b43037f6d5a2, B=agent-a5c1d115d1218c682,
C=agent-a9f51a3505cf8b875, D=agent-aa89d29493a5a1e7e,
F=agent-a26a811e5f91afaa0, G=agent-a254291521738f5e1,
H=agent-aae8b8be7901ea9e0, J=agent-ad7abf96b2860faa3,
L=agent-a86a1855d245596e3, N=agent-a039b4bf4bfb917ea,
Q=agent-aa5b099b84f6e453b, S=agent-ab901241a6be5f90c,
T=agent-ace81bd831791eff5, W=agent-a5e0aa057203c5835,
Y=agent-aeece58576674ffab, Z=agent-a7269b0f294688143
X = 跑空, 主线程 (minimax-m3, cfp-fetch) 已补

## 时间戳
- /tmp/buckets/start_d.txt / end_d.txt (父 session 跑完 marker)

## 下一步
1. 修 17 所 DRIFT 到 schools.json
2. 处理 2 所 OFFICIAL_DENY (B 桶 + W 桶)
3. 决定南宁职业技术大学是否保留旧条 (新 URL 暂缺)

## 工具
- cfp-fetch ~/bin/cfp-fetch (CFP 代理抓取)
- 校验法: HTTP 200 / 403 = 可达; 522 / 530 = origin 挂; 404 = 子路径鉴权门