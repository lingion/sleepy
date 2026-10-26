# HBSM 致谢清单 — Step 5.5

全量铁律: 候选数 2 = 致谢数 2, 1:1 无删减。

## 落点

1. `LicenseScreen.kt` perSchoolEntries 新卡 `school-hbsm` (湖北商贸学院 HBSM):
   - 采集包实锤说明行
   - qiqqqqq517/shangkeschedule (Apache-2.0, zhengfang_new 条目互证)
   - LonelyMarch/OpenWakeUp (AGPL-3.0, type=zf 伞型收录同域旁证, 未复用代码)
2. 6 语 strings.xml about_license_attributions 各追加 HBSM 段 (values / values-zh-rCN / values-zh-rTW / values-en / values-es / values-ja)
3. `AboutLicenseAttributionTest` 既有断言网覆盖新卡 (6 语 token 一致性), 全量测试实绿

## 候选 → 致谢映射

| 候选 | verdict | 致谢形式 |
|------|---------|---------|
| qiqqqqq517/shangkeschedule (28★, Apache-2.0) | INDIRECT 同判定 | 新卡明细行 + 6 语 strings (foundational-shangkeschedule 底卡已有, 该校卡新增互证行) |
| LonelyMarch/OpenWakeUp (6★, AGPL-3.0) | NEGATIVE (伞型归类) | 新卡明细行 + 6 语 strings (同域旁证, 明示未复用代码) |
