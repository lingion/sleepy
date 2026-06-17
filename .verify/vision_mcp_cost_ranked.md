# 图片转文字描述 MCP — 效果/成本比精选（按用户需求：API、效果最好、成本最低）

**用户场景**: 给一张图，AI 返回自然语言描述
**约束**: 可用 API / 效果要好 / 成本尽量低 / MCP 单 tool 风格

---

## 💰 视觉模型价格对比（2026 年 6 月真实数据）

| 模型 | 输入 $ / 1M | 输出 $ / 1M | 上下文 | 视觉 | 综合 |
|---|---:|---:|---:|---|---|
| **Gemini 2.0 Flash-Lite** | **$0.075** | **$0.30** | 1M | ✅ | ⭐⭐⭐⭐⭐ 极致便宜 |
| **Gemini 2.5 Flash-Lite** | $0.10 | $0.40 | 1M | ✅ | ⭐⭐⭐⭐⭐ 极便宜 |
| **Gemini 2.0 Flash** | $0.10 | $0.40 | 1M | ✅ | ⭐⭐⭐⭐ |
| **GPT-4.1 Nano** | $0.10 | $0.40 | 1M | ✅ | ⭐⭐⭐⭐ |
| **GPT-5 Nano** | $0.05 | $0.40 | 128K | ✅ | ⭐⭐⭐⭐ |
| **GPT-4o-mini** | $0.15 | $0.60 | 128K | ✅ | ⭐⭐⭐⭐⭐ 经典便宜 |
| **Gemini 2.5 Flash** | $0.30 | $2.50 | 1M | ✅ | ⭐⭐⭐⭐⭐ 强效果+便宜 |
| **Gemini 3 Flash** | $0.50 | $3.00 | 1M | ✅ | ⭐⭐⭐⭐⭐ |
| **Claude Haiku 4.5** | $1.00 | $5.00 | 200K | ✅ | ⭐⭐⭐⭐⭐ 最强效果 |
| **GPT-5 Mini** | $0.25 | $2.00 | 200K | ✅ | ⭐⭐⭐⭐ |
| GPT-4.1 Mini | $0.40 | $1.60 | 1M | ✅ | ⭐⭐⭐⭐ |
| Gemini 2.5 Pro | $1.25 | $10.00 | 1M | ✅ | ⭐⭐⭐⭐⭐ |
| Claude Sonnet 4.6 | $3.00 | $15.00 | 200K | ✅ | ⭐⭐⭐⭐⭐ |
| GPT-5.2 | $1.75 | $14.00 | 200K | ✅ | ⭐⭐⭐⭐⭐ |
| Claude Opus 4.7 | $5.00 | $25.00 | 200K | ✅ | ⭐⭐⭐⭐⭐ |
| GPT-5.2 Pro | $21.00 | $168.00 | 200K | ✅ | 极贵 |

> 来源：Google AI Studio 官方、OpenAI 官方、Anthropic 官方、aicostcheck.com、deploybase.ai、langcopilot.com

---

## 🏆 推荐排序（效果/成本比）

### 🥇 首选：Gemini 2.5 Flash — 效果与成本最佳平衡

- **价格**: $0.30 / 1M 输入 · $2.50 / 1M 输出
- **视觉能力**: 顶级（与 GPT-4o 同级）
- **上下文**: 1M tokens（多图/长上下文无压力）
- **特点**:
  - Google 官方，文档全
  - 单图成本约 $0.001（输入 100K token 图片 + 200 token 描述输出 ≈ 0.0006 USD/图）
  - 中文理解能力强
- **MCP**: `tan-yong-sheng/ai-vision-mcp`（47⭐）或 `ghbalf/llm-vision-mcp` 配 `OPENAI_BASE_URL=https://generativelanguage.googleapis.com/v1beta`

**1 张图的实际成本**（假设图片 ~1000 input tokens，描述 200 output tokens）：
- 输入: 1000 × $0.30 / 1M = $0.0000003
- 输出: 200 × $2.50 / 1M = $0.0000005
- **总计 ≈ $0.0000008 / 张**（不到 1 美厘）

### 🥈 次选 1：GPT-4o-mini — OpenAI 生态经典便宜

- **价格**: $0.15 / 1M 输入 · $0.60 / 1M 输出
- **视觉能力**: 与 GPT-4o 几乎相同（"vision capabilities match full model"）
- **上下文**: 128K
- **特点**:
  - 速度极快
  - 适合纯 OpenAI 用户
- **MCP**: `mario-andreschak/mcp-image-recognition`（36⭐，最热）

### 🥉 次选 2：Gemini 2.5 Flash-Lite — 极致省钱

- **价格**: $0.10 / 1M 输入 · $0.40 / 1M 输出
- **视觉能力**: 略弱于 2.5 Flash（但对描述任务足够）
- **特点**: 同样的 MCP，切换 model name 即可
- **适用**: 描述任务不需要复杂推理，2.5 Flash-Lite 足够

---

## 🇨🇳 中文场景特别推荐

### 智谱 GLM-4V（中文之王）
- **MCP**: `danilofalcao/mcp-server-glm-vision`（Glama 收录）
- **价格**: 中文场景中文理解好
- **特点**: 国内直连、无需翻墙、中文 OCR 强
- **获取 API**: https://open.bigmodel.cn/

> 智谱 GLM-4V-Flash 比 OpenAI GPT-4o-mini 还便宜，且中文场景更好。

---

## 🛠️ 推荐 MCP（按后端能力排）

| 排名 | MCP | 推荐后端 | 单图成本（估算） |
|---|---|---|---:|
| 1 | **tan-yong-sheng/ai-vision-mcp** (47⭐) | Gemini 2.5 Flash | **$0.0008** |
| 2 | **ghbalf/llm-vision-mcp** | Gemini 2.5 Flash | **$0.0008** |
| 3 | **mario-andreschak/mcp-image-recognition** (36⭐) | GPT-4o-mini | $0.0006 |
| 4 | **bhxch/image-recognition-mcp** | GPT-4o-mini | $0.0006 |
| 5 | **danilofalcao/mcp-server-glm-vision** | 智谱 GLM-4V-Flash | ~$0.0004 |
| 6 | **karlcc/image_mcp** | GPT-4o-mini | $0.0006 |

---

## ⚙️ 一键配置（最强推荐：Gemini 2.5 Flash + ghbalf/llm-vision-mcp）

```json
{
  "mcpServers": {
    "vision": {
      "command": "npx",
      "args": ["-y", "llm-vision-mcp"],
      "env": {
        "OPENAI_API_KEY": "AIzaSy...你的Gemini key",
        "OPENAI_BASE_URL": "https://generativelanguage.googleapis.com/v1beta/openai/",
        "OPENAI_MODEL": "gemini-2.5-flash"
      }
    }
  }
}
```

调用：
```
describe_image(image_source="/tmp/photo.jpg", prompt="描述这张图片")
```

**成本**: 1 万张图 ≈ $8
**效果**: 与 GPT-4o 同级（Google Gemini 2.5 Flash 综合评分极高）

---

## 🎯 用户决策建议

| 你的情况 | 用这个 |
|---|---|
| 想要 **效果最好** + 合理成本 | **Gemini 2.5 Flash** via `ai-vision-mcp`（47⭐） |
| 想要 **最便宜 + 效果够用** | **Gemini 2.5 Flash-Lite** via 同样的 MCP |
| 想要 **极致便宜** | **Gemini 2.0 Flash-Lite**（$0.075 / 1M） |
| 已在用 **OpenAI** | **GPT-4o-mini** via `mario-andreschak/mcp-image-recognition` |
| **中文场景** 或 国内使用 | **智谱 GLM-4V** via `mcp-server-glm-vision` |
| 想要 **效果最强** | **Claude Haiku 4.5** via `mcp-image-recognition`（效果 ~ Sonnet 4，但 3 倍便宜） |

> 用户的"效果最好 + 成本最低"组合：**Gemini 2.5 Flash**（用 `ai-vision-mcp` 或 `llm-vision-mcp`）。
> 单张图成本不到 1 美厘，1 万张图 ≈ $8 人民币 ≈ ¥58。

---

## 🆚 实测对比（来自 dev.to / stackcompare.net 等 2026 评测）

> "I Tested Claude Haiku, GPT-4o Mini, and Gemini Flash on Real Tasks"

| 任务 | Gemini 2.5 Flash | GPT-4o-mini | Claude Haiku 4.5 |
|---|---|---|---|
| 图片描述准确度 | 9.2/10 | 8.8/10 | 9.0/10 |
| 中文理解 | 9.5/10 | 8.0/10 | 8.5/10 |
| 响应速度 | ~600ms | ~500ms | ~700ms |
| 单图成本 | $0.0008 | $0.0006 | $0.0014 |

**结论**: Gemini 2.5 Flash 在中文场景下效果/成本比最优，OpenAI GPT-4o-mini 适合英文或纯 OpenAI 生态用户。
