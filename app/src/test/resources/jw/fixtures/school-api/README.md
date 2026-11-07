# 离线采集包

这里的五份 JSON 分别由 `tools/test-school-api-fetch.js`、`tools/test-jlu-fetch.js` 和 `tools/test-paged-school-fetch.js` 使用 `--write-fixtures` 执行生产采集脚本生成。DOM 和 HTTP 响应为依据本地 WakeUp 6.6.2 字段契约构造的合成数据，未使用真实账号或学校网络。文件创建使用排他写入，不覆盖已有采集包。

日常校验为三个脚本分别传入 `--check-fixtures`。脚本检查请求顺序、参数、缺周、分页、错误响应、超时和回调次数，再比较实际脚本输出与这里的文件。Kotlin 测试读取同一文件，独立核对每个课程发生日、离散节次、周次、教师、教室、学分以及草稿往返。
