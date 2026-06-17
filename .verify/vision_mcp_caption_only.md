# 极简图片转文字描述 MCP（按用户需求：只做"图片 → 文字描述"）

**用户场景**: 丢一张图，AI 返回自然语言描述。不要 OCR、检测、对比、生成、UI 测试等复杂功能。

---

## 🏆 最佳选择（按推荐顺序）

### 1. ⭐ ghbalf/llm-vision-mcp
**仓库**: https://github.com/ghbalf/llm-vision-mcp
- 单一功能：`describe_image` tool
- 支持 OpenAI / Anthropic / Ollama / OpenRouter
- 接受 file path / URL / base64
- **最小依赖、最简单**

### 2. ⭐ xkiranj/ollama-vision-mcp
**仓库**: https://github.com/xkiranj/ollama-vision-mcp
- 专门对接 Ollama 本地视觉模型（llava / llama3.2-vision / qwen2-vl）
- 单一 tool：`describe`
- **完全离线，不需 API key**
- 适合：本地有 Ollama 的用户

### 3. ⭐ karlcc/image_mcp
**仓库**: https://github.com/karlcc/image_mcp
- 单一 tool：`image_to_text`
- OpenAI 兼容接口
- 支持本地文件 / URL / base64

### 4. samhains/cv-mcp
**仓库**: https://github.com/samhains/cv-mcp
- 单一 tool：`describe`
- OpenAI Vision 兼容
- 适合：纯 OpenAI 路线

### 5. bhxch/image-recognition-mcp
**仓库**: https://github.com/bhxch/image-recognition-mcp
- 单一 tool：`describe-image`
- 兼容 OpenAI / LM Studio / Ollama / OpenRouter
- 安全：path 白名单 + domain 白名单

### 6. xronocode/vision-sidecar-mcp
**仓库**: https://github.com/xronocode/vision-sidecar-mcp
- 单一 tool：`describe_image`
- Sidecar 模式（独立进程）

### 7. arealicehole/vision-mcp
**仓库**: https://github.com/arealicehole/vision-mcp
- 单一 tool：`describe`

---

## 🥈 次选（功能稍多但仍简单）

### 8. mcp-image-recognition (mario-andreschak)
**仓库**: https://github.com/mario-andreschak/mcp-image-recognition (36⭐)
- 主 tool：`describe_image` / `describe_image_from_file`
- 选配 OCR（不启用即纯描述）
- 最多 stars 之一，稳定

### 9. sanity-labs/mcp-see
**仓库**: https://github.com/sanity-labs/mcp-see
- 屏幕截图 + 描述
- 适合：要"看屏幕"而不是"看图片"时

### 10. local-vision-mcp
**仓库**: https://github.com/LyuboslavLyubenov/local-vision-mcp
- 本地模型，完全离线
- 单一功能：图片描述

---

## 🛠️ 配置示例（最简方式）

```json
{
  "mcpServers": {
    "vision": {
      "command": "npx",
      "args": ["-y", "llm-vision-mcp"],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

调用：
```
describe_image(image_source="/path/to/photo.jpg", prompt="描述这张图片")
```

---

## 🎯 用户选择建议

| 你的情况 | 推荐 |
|---|---|
| 有 OpenAI / Claude / Gemini API key | **ghbalf/llm-vision-mcp** 或 **bhxch/image-recognition-mcp** |
| 想完全离线 | **xkiranj/ollama-vision-mcp** (需 Ollama 视觉模型) |
| 已有 LM Studio / OpenRouter | **bhxch/image-recognition-mcp** |
| 屏幕截图描述 | **sanity-labs/mcp-see** |
| 最稳最热 | **mario-andreschak/mcp-image-recognition** (36⭐) |
| 中文场景 | **danilofalcao/mcp-server-glm-vision** (智谱 GLM-4V) |

---

## ❌ 已排除的复杂 MCP

以下 MCP 含 OCR、检测、对比、设计审计、视频、PDF、生成等多功能，**对"图片转文字描述"需求过于复杂**：

- ai-vision-mcp（含 5 个 tool：分析/对比/检测/审计/视频）
- PaddleOCR MCP（专为中文 OCR，非描述）
- YOLO-MCP-Server（检测，非描述）
- mcp-design-comparison（视觉对比）
- mcp-pdf-vision（PDF）
- 截图/Computer Use 系列
- DALL-E 等生成类
- azure-ai-vision-face-api-mcp-server（人脸）
