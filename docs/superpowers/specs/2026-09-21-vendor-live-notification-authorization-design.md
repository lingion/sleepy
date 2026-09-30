# Spec: 全厂商实时通知能力与授权引导

## Objective

把课前实时通知在各厂商系统上的增强展示能力统一纳入「设置 → 通知」二级目录中的现有开关。开关名称保持在通知设置原位置，并在标题旁显示常驻的小胶囊 Tag「实验」，不新建独立实验室入口、不移动到通用设置。

用户打开开关时，应用应识别当前厂商与系统版本，检查 Android 通知权限、通知渠道和厂商增强展示能力；对于能确认未开启的能力，提供对应系统设置跳转；从设置返回后重新检查并更新状态。无论厂商能力是否可用，标准 Android 通知都必须继续作为兜底，不得把“已请求/已跳转”误报为“已获得流体云、灵动岛、原子岛或超级岛展示资格”。

覆盖当前代码登记的全部厂商族：OPPO/一加/realme、vivo/iQOO、小米/Redmi/POCO、魅族、荣耀、华为/HarmonyOS、三星，以及 AOSP/其他 Android。能力不存在公开可验证接口时，状态必须是“无法确认”，同时提供最接近的系统设置入口和通用应用通知设置入口。

### User-visible acceptance criteria

- 「流体云 / 超级岛」仍位于「设置 → 通知」原有位置。
- 标题旁显示小胶囊 Tag「实验」，Tag 不改变开关可用性，也不移动该设置项。
- 开启流程先处理 Android 通知权限，再处理厂商增强能力设置。
- OPPO、vivo、Xiaomi、HONOR、Huawei、Meizu、Samsung、OnePlus、realme、iQOO 和 generic Android 均有独立的检测/跳转策略记录；同族设备共享策略但不得按品牌名称猜测已授权。
- 从系统设置返回应用后，状态会重新检测；无法确认时显示明确的“系统无法直接确认”状态。
- 厂商增强能力失败、设置页不存在或用户拒绝授权时，课前标准通知仍可工作。
- 所有新增文案同步六个 locale 资源。

## Tech Stack

- Kotlin/JVM + Android SDK + Jetpack Compose Material 3。
- 现有 `NotificationCompat`、`FluidCloudService`、`VendorLiveCardRenderer`、`AppPrefs` 和 `ReminderScreen`。
- 不新增第三方依赖；厂商能力通过公开 Android API、显式系统设置 Intent、通知渠道状态和现有 Bundle 扩展实现。

## Commands

```bash
cd ~/sleepy
./gradlew :app:testDebugUnitTest --rerun-tasks
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

厂商策略纯逻辑测试应优先用聚焦命令：

```bash
./gradlew :app:testDebugUnitTest --tests '*Vendor*' --tests '*Notification*'
```

## Project Structure

- `app/src/main/java/com/lingion/sleepy/widget/notification/`：实时通知渲染、调度、厂商策略和授权状态。
- `app/src/main/java/com/lingion/sleepy/ui/screen/mine/ReminderScreen.kt`：通知二级目录设置 UI、实验 Tag、授权弹窗和返回复检。
- `app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt`：功能开关和授权引导状态的现有持久化边界。
- `app/src/main/res/values*/strings.xml`：六语言用户文案。
- `app/src/test/java/com/lingion/sleepy/widget/notification/`：厂商识别、状态机、Intent fallback 和文案契约测试。
- `docs/superpowers/specs/`：本功能规格及后续实现依据。

## Interface Contract

适配层提供统一的、可测试的能力状态，而不是让 UI 读取厂商属性：

```kotlin
enum class VendorCapabilityState {
    ENABLED,
    DISABLED,
    NOTIFICATION_PERMISSION_REQUIRED,
    SETTINGS_REQUIRED,
    NOT_SUPPORTED,
    UNKNOWN
}

data class VendorLiveNotificationCapability(
    val vendor: LiveCardVendor,
    val state: VendorCapabilityState,
    val summaryRes: Int,
    val settingsIntents: List<IntentSpec>,
    val fallbackToAppNotificationSettings: Boolean
)

interface VendorLiveNotificationAdapter {
    fun inspect(context: Context): VendorLiveNotificationCapability
    fun settingsIntents(context: Context): List<Intent>
}
```

实现约束：

- `ENABLED` 只能来自可验证的系统状态；不能由“设备品牌匹配”“Intent 可创建”或“曾经跳转过设置”推导。
- `UNKNOWN` 是合法终态，不得降级伪装成 `ENABLED`。
- 设置跳转按优先级尝试厂商专属入口，再尝试应用通知/渠道设置，最后尝试系统通知设置；每次失败捕获并继续 fallback。
- `inspect()` 不执行外部发布动作、不启动设置页、不修改通知内容；副作用只由 UI 的用户点击处理。
- 所有厂商都保留标准 Android 通知路径，增强适配器失败不能阻断 `CourseNotificationScheduler`。

## Data Flow

```text
ReminderScreen toggle
  -> Android POST_NOTIFICATIONS check/request
  -> VendorLiveNotificationAdapterRegistry.inspect()
  -> show status dialog
  -> user selects "去设置"
  -> vendor intent, then channel/app/system fallback
  -> onResume / lifecycle refresh
  -> inspect() again
  -> update status and keep standard notification fallback
```

开关本身的持久化语义不改变：用户确认打开后保存 `before_class_fluid_enabled`；系统增强能力状态不作为永久“已授权”布尔值保存，只在返回页面、应用恢复和通知调度前重新检查。这样可以避免用户在系统设置中撤销授权后应用仍显示已开启。

## Vendor Strategy Matrix

| 厂商族 | 增强展示 | 可验证状态 | 首选设置入口 | 无入口时 fallback |
|---|---|---|---|---|
| OPPO / OnePlus / realme | 流体云/系统实时卡片 | 通知权限、渠道及公开状态（否则 UNKNOWN） | 对应 ColorOS/OxygenOS/realme 通知或实时卡片设置 | 应用通知详情 → 系统通知设置 |
| vivo / iQOO | 原子通知/原子组件 | 通知权限、渠道及可公开读取状态 | OriginOS 原子组件/通知管理 | 应用通知详情 → 系统通知设置 |
| Xiaomi / Redmi / POCO | 超级岛/HyperOS 实时通知 | 通知权限、渠道及可公开读取状态 | HyperOS 通知/锁屏与状态栏/应用通知 | 应用通知详情 → 系统通知设置 |
| HONOR | 灵动胶囊/系统卡片 | 通知权限、渠道及可公开读取状态 | MagicOS 通知/胶囊设置 | 应用通知详情 → 系统通知设置 |
| Huawei / HarmonyOS | 系统服务卡片/通知 | 通知权限、渠道及可公开读取状态 | 华为通知管理/服务卡片设置 | 应用通知详情 → 系统通知设置 |
| Meizu | Flyme 实时通知/胶囊 | 通知权限、渠道及可公开读取状态 | Flyme 通知管理 | 应用通知详情 → 系统通知设置 |
| Samsung | Now Bar/通知增强（系统版本相关） | Android 通知与渠道；增强状态未知时 UNKNOWN | 应用通知详情 → 系统通知设置 | 系统通知设置 |
| AOSP / 其他 | 标准通知 | Android 通知权限与渠道 | 应用通知详情 | 系统通知设置 |

表中“首选设置入口”只表示尝试顺序，不承诺所有版本存在同一 Action。每个 Intent 必须通过 `resolveActivity` 或 try/catch 验证，不能硬编码一个失败入口后停止。

## UI Design

通知二级目录中的现有行改为带 Tag 的标题组合：

```text
┌────────────────────────────────────────────┐
│  流体云 / 超级岛                 [实验]  ⟷ │
│  在支持的厂商系统上显示实时课程通知         │
└────────────────────────────────────────────┘
```

- Tag 使用现有设置页面的小型高亮胶囊样式，不使用 emoji，不把“实验”拼进功能标题。
- 开关打开后显示能力状态和“去设置”按钮；状态为 `ENABLED` 时不要求用户重复授权。
- 状态为 `UNKNOWN` 时使用“系统无法直接确认”文案，避免误导。
- 用户从设置返回时自动刷新；用户取消系统设置或拒绝权限时，开关不强制关闭，但显示标准通知仍可用及增强能力未确认/未开启。

## Error Handling and Security

- Android 13+ 未授予 `POST_NOTIFICATIONS` 时使用现有运行时权限请求；用户拒绝后保留关闭状态并说明原因。
- 厂商设置 Intent 不存在、权限页崩溃或 ROM 修改 Action 时，依次 fallback，不让设置点击导致崩溃。
- 不读取 Keychain、账号凭据或隐私数据；只读取公开系统能力、通知权限和渠道状态。
- 不通过反射调用未验证的隐藏 API；厂商私有 Bundle 仅沿用已有渲染器并将其视为 best effort。
- “设置页可打开”不等于“系统已授权”；两者必须在状态模型中区分。

## Testing Strategy

- 纯 JVM 单测：厂商识别、适配器矩阵覆盖、状态优先级、Intent fallback 顺序、UNKNOWN 不误报、文案资源 key 和实验 Tag 存在性。
- Android/仪器测试（若现有测试环境支持）：`resolveActivity` 分支、返回复检、通知权限请求回调、通知渠道关闭时的状态。
- 全量回归：现有通知调度、`FluidCloudService`、`VendorLiveCardRenderer` 和标准通知路径必须保持通过。
- 每个厂商至少覆盖：权限已授予/未授予、增强能力已知关闭、状态未知、专属设置不可用四种输入。

## Boundaries

- Always:
  - 保持设置项在「通知」二级目录原位置，并显示 `[实验]` Tag。
  - 保留标准 Android 通知兜底。
  - 只把可验证状态显示为“已开启”；未知状态显示“无法确认”。
  - 所有用户文案同步六语言并为 Intent 失败写测试。
  - 实现后运行单测、构建和增量 lint 检查。
- Ask first:
  - 新增第三方依赖。
  - 需要申请非通知类敏感权限。
  - 需要接入未公开厂商 SDK、签名权限或上传厂商平台审核。
  - 需要改变现有通知开关默认值或关闭标准通知兜底。
- Never:
  - 伪造厂商授权状态。
  - 通过反射调用隐藏权限接口。
  - 因厂商增强能力失败而禁用标准通知。
  - 在用户未点击确认时自动打开系统设置。
  - 把用户数据或凭据发送给厂商服务。

## Success Criteria

1. 通知设置行原位置不变，标题旁存在“实验”Tag；六语言资源均有对应文案。
2. 全部登记厂商族都有适配器或明确的 UNKNOWN/fallback 策略，且测试覆盖四种输入状态。
3. Android 通知权限、通知渠道、厂商设置入口和返回复检均有可测试契约。
4. 任一增强能力设置失败时，标准课前通知仍可调度和显示。
5. 全量单测、Debug 构建和 lint 增量检查通过。

## Open Questions

无。用户已确认：全部厂商覆盖；功能保留在通知二级目录原位置；标题旁使用实验 Tag；开启后提供尽可能强的检测、授权引导和返回复检。
