# Sleepy v1.0.48

### Twenty more universities support direct import
Before, 159 universities were available for direct import. This release adds Wuhan University of Technology, Shanghai Jiao Tong University, Wuhan University, Shanghai University, South China Normal University, Renmin University of China, Hunan University, Beijing University of Chinese Medicine, East China University of Political Science and Law, Shanghai International Studies University, Donghua University, Xiamen University, Yanbian University, Shihezi University, Anhui University, China University of Mining and Technology (Beijing), University of Electronic Science and Technology of China, Shanghai University of Finance and Economics, Hunan Normal University, and Nanjing University of Aeronautics and Astronautics, bringing the total to 179.

### EAMS5 schools no longer need a fixed URL prefix
Before, EAMS5 imports depended on one URL prefix and could fail when a school deployed the service elsewhere. The importer now adapts to the configured EAMS5 endpoint. The classic EAMS parser remains available for older deployments.

### WHUT's Kingosoft variant is supported
Before, Wuhan University of Technology used an EAMS variant that did not match the existing parser. It now has a dedicated parser for its Kingosoft endpoints.

### Downloads can fall back to a mirror
Before, an APK download could fail when the primary host was unreachable. The downloader now falls back to a mirror. Twelve schools with known off-campus network restrictions also explain when a campus network or VPN is needed.

### Tests
The release was verified with 941 tests.

### Build
- versionName: `1.0.48`
- versionCode: `49`
- ABI APKs: `arm64-v8a`, `armeabi-v7a`, `x86_64`

— Lingion

---

# Sleepy v1.0.48

### 新增二十所学校直连导入
之前，Sleepy 支持 159 所学校直连导入。本版本新增武汉理工大学、上海交通大学、武汉大学、上海大学、华南师范大学、中国人民大学、湖南大学、北京中医药大学、华东政法大学、上海外国语大学、东华大学、厦门大学、延边大学、石河子大学、安徽大学、中国矿业大学（北京）、电子科技大学、上海财经大学、湖南师范大学和南京航空航天大学，支持总数达到 179 所。

### EAMS5 不再依赖固定 URL 前缀
之前，EAMS5 导入依赖固定的 URL 前缀，学校部署位置变化后可能失败。现在导入器会根据配置的 EAMS5 端点自适应；较早的部署仍由 classic EAMS parser 处理。

### 支持武汉理工大学金智变体
之前，武汉理工大学使用的 EAMS 变体与现有解析器不一致。现在已为它的金智端点加入专用解析器。

### APK 下载增加镜像回退
之前，主下载地址不可达时 APK 下载会失败。现在下载器会自动回退到镜像地址；对已知校外网络受限的十二所学校，也会提示连接校园网或使用 VPN。

### 测试
本版本通过 941 项测试验证。

### 构建
- versionName：`1.0.48`
- versionCode：`49`
- ABI APK：`arm64-v8a`、`armeabi-v7a`、`x86_64`

— Lingion
