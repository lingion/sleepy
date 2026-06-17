# 全网视觉识别 MCP 服务器清单

**搜索时间**: 2026-06-17
**搜索方法**: GitHub Topics, Glama.ai Registry, awesome-mcp-servers, 中文搜索
**总计**: **50+ 个视觉/图像相关 MCP 服务器**

---

## 1. 通用视觉识别（基于 LLM）

| MCP | 仓库 | 后端 | 特点 |
|-----|------|------|------|
| **mcp-image-recognition** | [mario-andreschak/mcp-image-recognition](https://github.com/mario-andreschak/mcp-image-recognition) | Claude Vision / GPT-4V / OpenRouter | 36⭐，可选 Tesseract OCR |
| **image-recognition-mcp** | [bhxch/image-recognition-mcp](https://github.com/bhxch/image-recognition-mcp) | OpenAI / LM Studio / Ollama | URL 或本地路径 |
| **ai-vision-mcp** | [tan-yong-sheng/ai-vision-mcp](https://github.com/tan-yong-sheng/ai-vision-mcp) | **Gemini / Vertex AI** | 47⭐，**支持图片+视频**，4种分析模式 |
| **mcp-vision** | [groundlight/mcp-vision](https://github.com/groundlight/mcp-vision) | 多模态模型 | 工业视觉 |
| **mcp-vision-server** | [LZMW/mcp-vision-server](https://github.com/LZMW/mcp-vision-server) | OpenAI 兼容 | 轻量 |
| **vision-mcp-server** | [calandnong/vision-mcp-server](https://github.com/calandnong/vision-mcp-server) | 多模态 | |
| **Vision MCP (explainx)** | [explainx.ai/mcp-servers/vision](https://explainx.ai/mcp-servers/vision) | 商业 | |
| **vision-mcp** | [cpramod/vision-mcp](https://github.com/cpramod/vision-mcp) | Claude | |
| **vision-context-mcp** | [tanobuffone/vision-context-mcp](https://github.com/tanobuffone/vision-context-mcp) | Claude | 自动注入视觉上下文 |
| **claudecode-vision-mcp** | [look4yo/claudecode-vision-mcp](https://github.com/look4yo/claudecode-vision-mcp) | Claude Code 专用 | |
| **local-vision-mcp** | [LyuboslavLyubenov/local-vision-mcp](https://github.com/LyuboslavLyubenov/local-vision-mcp) | 本地模型 | 完全离线 |
| **mcp-server-glm-vision** | [danilofalcao/mcp-server-glm-vision](https://glama.ai/mcp/servers/danilofalcao/mcp-server-glm-vision) | **智谱 GLM-4V** | 中文场景好 |
| **mcp-hydrocoder-vision** | [hydroCoderClaud/mcp-hydrocoder-vision](https://glama.ai/mcp/servers/hydroCoderClaud/mcp-hydrocoder-vision) | HydroCoder | |

---

## 2. 视频分析 MCP

| MCP | 仓库 | 后端 | 特点 |
|-----|------|------|------|
| **mcp-video-analyzer** | [guimatheus92/mcp-video-analyzer](https://github.com/guimatheus92/mcp-video-analyzer) | Claude Vision | |
| **gemini-video-mcp-server** | [moe5445/gemini-video-mcp-server](https://github.com/moe5445/gemini-video-mcp-server) | **Gemini 视频** | 长视频 |
| **gemini-video-mcp-server (adamanz)** | [adamanz/gemini-video-mcp-server](https://github.com/adamanz/gemini-video-mcp-server) | Gemini | |

> 💡 `ai-vision-mcp` 已支持视频（YouTube 链接 / 本地 mp4），4种分析模式含 audit_design。

---

## 3. 截图 / 屏幕视觉 MCP

| MCP | 仓库 | 特点 |
|-----|------|------|
| **mcp-screenshot** | [signal-slot/mcp-screenshot](https://github.com/signal-slot/mcp-screenshot) | 跨平台 |
| **mcp-screenshot-server** | [sethbang/mcp-screenshot-server](https://github.com/sethbang/mcp-screenshot-server) | |
| **Digital-Defiance/mcp-screenshot** | [Digital-Defiance/mcp-screenshot](https://github.com/Digital-Defiance/mcp-screenshot) | |
| **kazuph/mcp-screenshot** | [kazuph/mcp-screenshot](https://github.com/kazuph/mcp-screenshot) | |
| **screen-vision** | [avicuna/screen-vision](https://github.com/avicuna/screen-vision) | 屏幕+视觉 |
| **screen-vision-mcp** | [TIMBOTGPT/screen-vision-mcp](https://github.com/TIMBOTGPT/screen-vision-mcp) | |
| **mcp-see** | [sanity-labs/mcp-see](https://github.com/sanity-labs/mcp-see) | 屏幕理解 |
| **ui-vision-mcp** | [xbghc/ui-vision-mcp](https://github.com/xbghc/ui-vision-mcp) | UI 视觉测试 |
| **OpticMCP** | [Timorleiderman/OpticMCP](https://github.com/Timorleiderman/OpticMCP) | 光学视觉 |

---

## 4. 物体检测 / YOLO

| MCP | 仓库 | 特点 |
|-----|------|------|
| **opencv-mcp-server** | [GongRzhe/opencv-mcp-server](https://github.com/GongRzhe/opencv-mcp-server) | OpenCV |
| **YOLO-MCP-Server** | [GongRzhe/YOLO-MCP-Server](https://github.com/GongRzhe/YOLO-MCP-Server) | **YOLO 检测** |
| **mcp-yolo** | [rjn32s/mcp-yolo](https://github.com/rjn32s/mcp-yolo) | YOLO |
| **ultralytics_mcp_server** | [MetehanYasar11/ultralytics_mcp_server](https://github.com/MetehanYasar11/ultralytics_mcp_server) | Ultralytics YOLO |

---

## 5. OCR 文字识别

| MCP | 仓库 | 后端 | 特点 |
|-----|------|------|------|
| **PaddleOCR MCP** | [PaddleOCR 官方](https://www.paddleocr.ai/latest/en/version3.x/integrations/mcp_server.html) | **PaddleOCR** | **中文 OCR 之王** |
| **paddleocr-mcp-server** | [pypi.org/project/paddleocr-mcp-server](https://pypi.org/project/paddleocr-mcp-server/) | PaddleOCR | 0.1.0 |
| **mcp-ocr** | [rjn32s/mcp-ocr](https://github.com/rjn32s/mcp-ocr) | 多 OCR | |
| **tesseract-mcp-server** | [maximdx/tesseract-mcp-server](https://github.com/maximdx/tesseract-mcp-server) | **Tesseract** | 本地 |
| **mcp-ocr-server** | [dangvinh/mcp-ocr-server](https://github.com/dangvinh/mcp-ocr-server) | | |
| **handwriting-ocr-mcp-server** | [Handwriting-OCR/handwriting-ocr-mcp-server](https://github.com/Handwriting-OCR/handwriting-ocr-mcp-server) | Handwriting OCR API | **手写体** |
| **Aspose.BarCode-Cloud-MCP** | [aspose-barcode-cloud/Aspose.BarCode-Cloud-MCP](https://github.com/aspose-barcode-cloud/Aspose.BarCode-Cloud-MCP) | Aspose | **条形码/二维码** |

---

## 6. 人脸识别

| MCP | 仓库 | 特点 |
|-----|------|------|
| **azure-ai-vision-face-api-mcp-server** | [Azure-Samples/azure-ai-vision-face-api-mcp-server](https://github.com/Azure-Samples/azure-ai-vision-face-api-mcp-server) | **Azure Face API** 官方 |
| **FaceSearch MCP** | [facesearch.net/mcp-server](https://facesearch.net/mcp-server) | 商业人脸搜索 |

---

## 7. 设计 / UI 视觉对比

| MCP | 仓库 | 特点 |
|-----|------|------|
| **mcp-design-comparison** | [w01fgang/mcp-design-comparison](https://github.com/w01fgang/mcp-design-comparison) | 视觉回归 |
| **mcp-image-compare-server** | [leky90/mcp-image-compare-server](https://github.com/leky90/mcp-image-compare-server) | 图像对比 |
| **vrt-mcp** | [NoirJ0e/vrt-mcp](https://github.com/NoirJ0e/vrt-mcp) | Visual Regression Test |
| **snapdiff-mcp** | [corralimited/snapdiff-mcp](https://github.com/corralimited/snapdiff-mcp) | 视觉差异 |
| **quickchart-mcp-server** | [TakanariShimbo/quickchart-mcp-server](https://github.com/TakanariShimbo/quickchart-mcp-server) | 图表生成 |

> 💡 `ai-vision-mcp` 的 `audit_design` 工具做 WCAG 对比度+设计审计。

---

## 8. PDF 视觉分析

| MCP | 仓库 | 特点 |
|-----|------|------|
| **mcp-pdf-vision** | [MIMICLab/mcp-pdf-vision](https://github.com/MIMICLab/mcp-pdf-vision) | PDF + Vision |
| **pdf-mcp** | [jztan/pdf-mcp](https://github.com/jztan/pdf-mcp) | |
| **pdf-mcp (maxhodak)** | [maxhodak/pdf-mcp](https://github.com/maxhodak/pdf-mcp) | |
| **pdf-reader-mcp** | [damateosg/pdf-reader-mcp](https://github.com/damateosg/pdf-reader-mcp) | |
| **pdf-parser-mcp** | [adbutler007/pdf-parser-mcp](https://github.com/adbutler007/pdf-parser-mcp) | |

---

## 9. AI 图像生成（视觉创作）

| MCP | 仓库 | 特点 |
|-----|------|------|
| **dalle-mcp** | [garoth/dalle-mcp](https://github.com/garoth/dalle-mcp) | **DALL-E** |
| **openai-image-mcp** | [lpenguin/openai-mcp](https://github.com/lpenguin/openai-mcp) | OpenAI 图像 |
| **imagegen-mcp-d3** | [chrisurf/imagegen-mcp-d3](https://github.com/chrisurf/imagegen-mcp-d3) | 图像生成 |
| **render-mcp** | [LarsenClose/render-mcp](https://github.com/LarsenClose/render-mcp) | 渲染 |
| **987xyz-image** (本机) | [987xyz-image skill](file://) | pro/gpt-image-2 |

---

## 10. 工具 / 实用视觉处理

| MCP | 仓库 | 特点 |
|-----|------|------|
| **image-tiler-mcp-server** | [keiver/image-tiler-mcp-server](https://github.com/keiver/image-tiler-mcp-server) | 切图 |
| **mcp-server (jrgtwo)** | [jrgtwo/mcp-server](https://github.com/jrgtwo/mcp-server) | 通用视觉 |
| **@floto/mcp-server** | [@floto/mcp-server](https://registry.npmjs.org/@floto/mcp-server) | npm 官方 |

---

## 11. 电脑视觉操作（Computer Use）

| MCP | 仓库 | 特点 |
|-----|------|------|
| **claude_code_computer_use_mcp** | [SebastianBaltes/claude_code_computer_use_mcp](https://github.com/SebastianBaltes/claude_code_computer_use_mcp) | **Anthropic Computer Use** |
| **computer_use (Janksuu)** | [Janksuu/computer_use](https://github.com/Janksuu/computer_use) | |
| **computer-use-mcp** | [syedazharmbnr1/computer-use-mcp](https://github.com/syedazharmbnr1/computer-use-mcp) | |

---

## 12. 社媒视觉（图片/视频内容）

| MCP | 仓库 | 特点 |
|-----|------|------|
| **instagram-mcp** | [William-Gao/instagram-mcp](https://github.com/William-Gao/instagram-mcp) | |
| **instagram-mcp (AleemHaider)** | [AleemHaider/instagram-mcp](https://github.com/AleemHaider/instagram-mcp) | |
| **instagram-mcp (mcpware)** | [mcpware/instagram-mcp](https://github.com/mcpware/instagram-mcp) | |

---

## 🏆 推荐组合（按使用场景）

### 中文课程表 / 截图识别
1. **PaddleOCR MCP**（中文 OCR 之王）
2. **mcp-server-glm-vision**（智谱 GLM-4V，中文友好）
3. **ai-vision-mcp**（Gemini 多模态 + 中文理解强）

### 安卓 App 截图自动分析
1. **mcp-screenshot**（截图）
2. **ai-vision-mcp / mcp-image-recognition**（视觉理解）
3. **mcp-design-comparison**（前后对比）

### 离线 / 本地
1. **local-vision-mcp**（本地模型）
2. **tesseract-mcp-server**（本地 OCR）
3. **YOLO-MCP-Server**（本地检测）

### 商业级 / 大规模
1. **ai-vision-mcp + Vertex AI**（Google）
2. **azure-ai-vision-face-api-mcp-server**（Azure）
3. **mcp-image-recognition + GPT-4o**（OpenAI）

---

## 📊 数据来源

- **GitHub Topics**: `mcp-server`, `image-recognition`, `computer-vision`
- **awesome-mcp-servers** ([punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers))
- **Glama.ai Registry** ([glama.ai/mcp/servers](https://glama.ai/mcp/servers))
- **Smithery** ([smithery.ai](https://smithery.ai))
- **官方 MCP Registry** ([registry.modelcontextprotocol.io](https://registry.modelcontextprotocol.io))

> ⚠️ MCP 生态增长极快，部分项目可能已归档；按需核查最新 commit 和 stars。
