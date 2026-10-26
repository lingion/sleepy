# 负一屏设备验证矩阵

> 这张表记录真实设备或对应系统镜像的证据状态。`verified` 只能由 `tools/verify_widget_device.sh` 在对应设备上通过后填写；标准 AppWidget 的静态契约通过不等于厂商真机认证。

## 状态定义

| 状态 | 含义 |
|---|---|
| `verified` | 对应厂商设备/系统镜像已安装当前 APK，13 个 provider、标准更新入口和 Xiaomi 兼容入口（若存在）已由 ADB smoke 通过。|
| `static-verified` | 公共 Android AppWidget XML、Manifest、渲染和 JVM 契约通过，但没有对应厂商设备证据。|
| `platform-bound` | 私有负一屏卡片需要厂商平台、商店或独立生态，不能由 APK 单方面完成。|
| `pending-device` | 有标准 APK 路径，但当前没有对应设备/镜像证据。|

## 当前证据

| 厂商/生态 | APK 路径 | 状态 | 证据 |
|---|---|---|---|
| Google Pixel / AOSP | 标准 AppWidget | `verified` | Android Emulator 三档: API 33 (`TE1A.220922.034`) / API 34 (`UE1A.230829.036.A1`) / API 35 (`AE3A.240806.019`); 三台全部 `verify_widget_device.sh` PASS 且 13 个唯一 provider 注册; API 35 上 `setWidgetPreview()` 实调 13 次返回 0 + logcat `REGISTERED(count=13)`; API 34 上 registrar 按契约返回 `UNSUPPORTED_API` 走 XML 回退 |
| OPPO / ColorOS | 标准 AppWidget；Pantanal 另需授权 | `static-verified` | 标准契约 + 已有 OPPO 实机渲染证据；本轮未连接 OPPO 设备 |
| Samsung / One UI | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 Samsung 设备证据 |
| Xiaomi / HyperOS | AppWidget + 已声明 AppVault 扩展 | `pending-device` | Manifest/JVM 契约；缺 Xiaomi 设备证据 |
| vivo / OriginOS | AppWidget + 原子组件声明 | `pending-device` | Manifest/JVM 契约；缺 vivo 设备证据 |
| HONOR / MagicOS | 标准 AppWidget；MagicOS 卡片另需授权 | `pending-device` | 公共 Android 契约；缺 HONOR 设备证据 |
| Huawei EMUI | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 Huawei 设备证据 |
| Huawei HarmonyOS NEXT | 原生服务卡 HAP/ArkTS | `platform-bound` | Android APK 不替代该生态 |
| Sony Xperia | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 Sony 设备证据 |
| Motorola / Lenovo | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 Motorola/Lenovo 设备证据 |
| ASUS | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 ASUS 设备证据 |
| Nothing | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 Nothing 设备证据 |
| HMD / Nokia | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺 HMD/Nokia 设备证据 |
| TECNO / Infinix / itel | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺设备证据 |
| ZTE / TCL / Sharp / Fujitsu / Panasonic / Kyocera | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺设备证据 |
| CAT / Unihertz / DOOGEE / Ulefone / Blackview | 标准 AppWidget | `pending-device` | 公共 Android 契约；缺设备证据 |
| Meizu / Flyme | 标准 AppWidget；Aicy 私有协议未公开 | `pending-device` | 公共 Android 契约；缺 Meizu 设备证据 |
| Snapdragon Spaces | 独立 XR 平台 | `platform-bound` | Android APK 不替代该生态 |

## 本机测试资产盘点（2026-09-19）

- 已安装 AVD：`sleepy_test`（API 33）、`sleepy_test_14`（API 34）、`sleepy_test_15`（API 35），均为 Google Pixel 5 profile、`default/x86_64` 官方 AOSP 镜像（sdkmanager 官方源下载，非第三方）。
- 本机没有 Samsung、Xiaomi、vivo、HONOR、Huawei、Sony、Motorola 等 OEM system image，也没有对应真机连接。
- 因此这些条目必须保持 `pending-device`；新增设备后，不能直接改表，必须先运行 smoke 并保存输出。

## 可重复命令

```sh
ANDROID_SERIAL=<serial> tools/verify_widget_device.sh
```

脚本只读取包路径、设备属性和已注册组件/广播，不卸载、不清数据、不写数据库。它验证的是 APK 侧接线，不宣称厂商私有负一屏已经获得商务准入。
