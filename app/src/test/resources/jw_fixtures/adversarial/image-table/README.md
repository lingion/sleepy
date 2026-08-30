# adversarial/image-table — 图片课表与空白格对抗样本组

## 目标
图片课表(整格 img 无文本)与空白格: 各 parser 应返回 0 课而非崩;
错误分类应可区分为: 图片课表不支持 / 可信空课表 / 页面无课表容器 / 会话过期 / 脏课假成功。

## 覆盖矩阵
| 样本 | 协议 | 形态 | 期望 |
|---|---|---|---|
| zf_image_cell | zf | Table1 全 img | 0 课, 图片课表不支持 |
| zf_1_image_cell | zf_1 | Table1 无 a 全 img | 0 课, 图片课表不支持 |
| zf_mixed_image_and_text | zf | img 与文本格混排 | 2 课但 endNode=startNode-1 (parseTime 丢 result[1]=step) |
| zf_blank_cells | zf | Table1 结构完整全 NBSP | 0 课, 可信空课表 |
| zf_new_image_kbgrid | zf_new | #kblist_table title img | 0 课, 图片课表不支持 |
| zf_new_image_list_view | zf_new | el-table__body + td_wrap img | 0 课, 图片课表不支持 (真实 jwglxt DOM, td_wrap 非 kbcontent) |
| qz_image_cell | qz | kbtable div.kbcontent img | 0 课, 图片课表不支持 |
| qz_image_cell_with_text | qz | div.kbcontent img+文字混合 | 1 门半成品脏课 (QzParser week=1-1 vs NewZfParser week=1-16 分歧) |
| qz_blank_kbtable | qz | kbtable 全 NBSP | 0 课, 可信空课表 |
| qz_with_node_image_cell | qz_with_node | title 含节次的 img 格 | 1 门课名错+节次错 (基础解析器一锅烩, 无专用 parser) |
| urp_new_image_only | urp_new | 整页 img + dateList:[] | 0 课, 图片课表不支持 |
| urp_old_image_cell | urp | displayTag 列含 img | 2 门未命名脏课 (td.text() 不含 img alt, 假成功) |
| urp_old_blank | urp | displayTag tbody 空 | 0 课, 可信空课表 |
| wisedu_empty_rows | wisedu | JSON rows:[] | 0 课, 可信空课表 (图片语义不适用) |
| wisedu_img_only_row | wisedu | JSON 字段塞 HTML img | 1 门脏课 name/teacher 为 img 字面量 |
| cross_protocol_image_table | zf+qz | 同页 Table1+kbtable 全 img | 0 课, 协议判定不确定 |
| blank_no_table | unknown | 无任何课表容器 | 0 课, 页面无课表容器 |
| login_page_only | zf_new (URL) | jwglxt 登录页 | 0 课, 会话过期或未登录 |

## 未覆盖(解析器在 sleepy 不存在)
cf / pku / bnuz / hnust / qz_br / qz_old 六个协议的 image-table 样本无法构造:
对应解析器类(JwChengFangParser/JwPekingParser/JwBnuzParser/JwHnustParser/JwQzBrParser/
JwOldQzParser)在仓库中不存在(plan T2/T3 待移植), 当前 TYPE 映射(JwImportViewModel.kt:95-97)
全部回退到 JwQzParser, 行为等价于 qz 基础样本。

## 验证
python3 _verify.py  — 用 Jsoup 1.18.1 真实跑 Sim/SimNewZf, 对比 case.json 的 courses/courseCount
