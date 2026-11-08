# 武汉理工采集输出回放

原始课程数据来自仓库已有 `jw/whut-live/whut_cxxszhxqkb_live.json`。这两份文件由生产 WebView 采集脚本在离线 HTTP 桩中执行后生成，分别覆盖动态节次表和节次表请求失败后的既有映射。没有执行真实登录或本次学校网络请求。

`node tools/test-whut-fetch.js --check-fixtures` 校验源课程数组保持原始教务编号，并且旧 HTML 课表直接返回。Kotlin 回归逐项展开原始响应的每一周、每一节，与实际采集输出经过导入入口和草稿后的结果比较，防止脚本与解析器重复转换节次。
