import os
import urllib.request
import json
import base64

with open('/Users/lingion_k/Desktop/sleepy/.verify/token') as f:
    GH_TOKEN = f.read().strip()

RELEASE_ID = 340314875
APK_PATH = "/Users/lingion_k/Desktop/sleepy/app/build/outputs/apk/release/sleepy-v1.0.0-arm64-release.apk"
APK_NAME = "sleepy-v1.0.0-arm64-release.apk"

new_body = """## v1.0.0 正式版

### 本次更新（2026-06-17）

**新增功能：导入课表支持 CSV / HTML 格式**

除了原来的 WakeUp 分享文本 / WakeUp JSON / ICS 日历 / 纯文本 四种格式，现在还支持：

- **CSV 文件** — 自动识别中英文表头
  - 支持「单列节次」`1-2` 和「两列节次」`开始节数 + 结束节数`（教务处常见导出格式）
  - **支持多区间周数**：`2-5,7-9,11-14` / `6-14,16-18` / 离散周 `11,13,15,17` / 单周 `5`
  - 多区间会自动展开成多条课程记录
  - 支持带引号的字段（含逗号）：`"线性代数, 进阶"`、`Physics "Lab"`
- **HTML 表格** — 任意 `<table>` 形式，自动识别表头后逐行解析
  - 从学校教务处导出的 HTML 课表直接可用

**修复**
- 修：节次解析 bug（旧 parser 把 start-end 错当成 start-step，导致 `3-4` 显示为 3-6 节）
- 修：`parseRange("5")` 不支持单数字（单周/单节点）
- 修：debug-only 测试入口（`TestImportActivity`）移除
- 修：splash_background.xml 引用的图标在部分设备上无法解析导致闪退
- 修：DailyNotifyReceiver 漏注册到 AndroidManifest.xml，导致闹钟触发但通知从不弹出

### 下载

- **arm64-v8a**（推荐，国产 Android 设备）：[sleepy-v1.0.0-arm64-release.apk](https://github.com/lingion/sleepy/releases/download/v1.0.0/sleepy-v1.0.0-arm64-release.apk)
- 体积：约 12.5 MB
- 最低 Android 版本：7.0 (API 24)
- 签名：debug keystore（仅个人使用，不会上架应用商店）

### 使用说明

1. 安装前先**卸载旧版本**（不卸载会装不上，签名不同）
2. 打开「导入课表」标签，从文件管理器选择课表文件（.csv / .html / .ics / .json / .txt）
3. 也支持复制文本后用「粘贴导入」
4. 首次启动会让你设置「学期开始日期」，选完就能看到课程

---

有问题可以提 [Issue](https://github.com/lingion/sleepy/issues)，源码在 [lingion/sleepy](https://github.com/lingion/sleepy)。
"""

# 1. Delete old APK
print("=== Step 1: Get release + delete old APK ===")
req = urllib.request.Request(
    f"https://api.github.com/repos/lingion/sleepy/releases/{RELEASE_ID}",
    headers={'Authorization': f'token {GH_TOKEN}'},
)
with urllib.request.urlopen(req) as resp:
    rel = json.loads(resp.read())
for asset in rel.get('assets', []):
    if 'apk' in asset['name'].lower():
        del_req = urllib.request.Request(
            f"https://api.github.com/repos/lingion/sleepy/releases/assets/{asset['id']}",
            headers={'Authorization': f'token {GH_TOKEN}'},
            method='DELETE',
        )
        try:
            with urllib.request.urlopen(del_req) as resp:
                print(f"  deleted {asset['name']} (HTTP {resp.status})")
        except urllib.error.HTTPError as e:
            print(f"  HTTP {e.code}")

# 2. Upload new APK
print()
print("=== Step 2: Upload new APK ===")
with open(APK_PATH, 'rb') as f:
    apk_data = f.read()
print(f"  size: {len(apk_data)} bytes ({len(apk_data)/1024/1024:.1f} MB)")
url = f"https://uploads.github.com/repos/lingion/sleepy/releases/{RELEASE_ID}/assets?name={APK_NAME}"
req = urllib.request.Request(
    url, data=apk_data,
    headers={
        'Authorization': f'token {GH_TOKEN}',
        'Content-Type': 'application/vnd.android.package-archive',
    },
    method='POST',
)
try:
    with urllib.request.urlopen(req, timeout=120) as resp:
        result = json.loads(resp.read())
        print(f"  UPLOADED: {result['name']}")
        print(f"  URL: {result['browser_download_url']}")
        print(f"  size: {result['size']} bytes")
except urllib.error.HTTPError as e:
    print(f"  HTTP {e.code}: {e.read().decode()[:500]}")

# 3. Update body
print()
print("=== Step 3: Update release body ===")
url = f"https://api.github.com/repos/lingion/sleepy/releases/{RELEASE_ID}"
data = json.dumps({"body": new_body}).encode()
req = urllib.request.Request(
    url, data=data,
    headers={
        'Authorization': f'token {GH_TOKEN}',
        'Content-Type': 'application/json',
    },
    method='PATCH',
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read())
        print(f"  body length: {len(result.get('body',''))} chars")
        print(f"  assets: {[(a['name'], a['size']) for a in result.get('assets',[])]}")
except urllib.error.HTTPError as e:
    print(f"  HTTP {e.code}: {e.read().decode()[:500]}")
