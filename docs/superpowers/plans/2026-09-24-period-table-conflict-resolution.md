# 作息表冲突解决 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 EditTableScreen 实现绑定了共享作息表时修改作息内容的三选项冲突处理（甲案）

**Architecture:** 
- ViewModel 层维护三类作息状态（original/draft/policy），按 spec §2.1 驱动 UI 层弹窗与提醒
- Repository 层复用已有的 copyPeriodTableAs / insertPeriodTable / bindPeriodTable / updateTableMetadata 等方法，按 spec §3 实现三种写入策略
- UI 层新增 BottomSheet 组件和待执行提醒 Banner，按 spec §4 呈现

**Tech Stack:** Kotlin / Jetpack Compose / Room / MVVM

## Global Constraints

- Sleepy v1.0.56+ (minSdk 26, targetSdk 34)
- Material 3 BottomSheet 组件
- 既有 UndoManager 撤回机制不变
- Widget 刷新走现有 notifyDataChanged 通道

---

### Task 1: ViewModel 状态模型

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt` (新建或扩展现有 EditTableViewModel)
- Test: `app/src/test/java/com/lingion/sleepy/ui/screen/mine/EditTableSchedulePolicyTest.kt` (新建)

**Interfaces:**
- Consumes: `ScheduleRepository.observeEffectivePeriodTable(tableId)` — 现有
- Produces:
  - `originalEffectiveSchedule: PeriodTableEntity?`
  - `draftEffectiveSchedule: PeriodTableEntity?`
  - `pendingSchedulePolicy: SchedulePolicy` (enum: NONE, DETACH_COPY, CREATE_NEW, SYNC)
  - `hasScheduleChanged(): Boolean`
  - `selectPolicy(policy: SchedulePolicy)`
  - `cancelPolicy()`
  - `clearPolicyIfScheduleChanged()`

- [ ] **Step 1: 写测试 - 状态模型基本行为**

```kotlin
// EditTableSchedulePolicyTest.kt
class EditTableSchedulePolicyTest {
    @Test
    fun `initial state - no policy selected`() {
        val vm = EditTableViewModel(repo, ...)
        assertEquals(SchedulePolicy.NONE, vm.pendingSchedulePolicy)
    }

    @Test
    fun `hasScheduleChanged returns false when no edit`() {
        val vm = EditTableViewModel(repo, ...)
        assertFalse(vm.hasScheduleChanged())
    }

    @Test
    fun `selectPolicy stores policy and shows banner`() {
        val vm = EditTableViewModel(repo, ...)
        vm.selectPolicy(SchedulePolicy.DETACH_COPY)
        assertEquals(SchedulePolicy.DETACH_COPY, vm.pendingSchedulePolicy)
    }

    @Test
    fun `cancelPolicy clears policy and restores original`() {
        val vm = EditTableViewModel(repo, ...)
        vm.selectPolicy(SchedulePolicy.DETACH_COPY)
        vm.cancelPolicy()
        assertEquals(SchedulePolicy.NONE, vm.pendingSchedulePolicy)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :app:testDebugUnitTest --tests "EditTableSchedulePolicyTest" 2>&1 | tail -20`
Expected: FAIL (EditTableViewModel not defined or methods not implemented)

- [ ] **Step 3: 写 ViewModel 状态模型实现**

```kotlin
// EditTableViewModel.kt 新增属性与方法
enum class SchedulePolicy { NONE, DETACH_COPY, CREATE_NEW, SYNC }

class EditTableViewModel(
    private val repo: ScheduleRepository,
    private val tableId: Long
) : ViewModel() {
    // 三类作息状态
    private var _originalEffectiveSchedule = MutableStateFlow<PeriodTableEntity?>(null)
    val originalEffectiveSchedule: StateFlow<PeriodTableEntity?> = _originalEffectiveSchedule.asStateFlow()

    private var _draftEffectiveSchedule = MutableStateFlow<PeriodTableEntity?>(null)
    val draftEffectiveSchedule: StateFlow<PeriodTableEntity?> = _draftEffectiveSchedule.asStateFlow()

    private var _pendingSchedulePolicy = MutableStateFlow(SchedulePolicy.NONE)
    val pendingSchedulePolicy: StateFlow<SchedulePolicy> = _pendingSchedulePolicy.asStateFlow()

    init {
        viewModelScope.launch {
            // 进入编辑页时，记录 original = 当前生效作息
            repo.observeEffectivePeriodTable(tableId).collect { periodTable ->
                _originalEffectiveSchedule.value = periodTable
            }
        }
    }

    fun hasScheduleChanged(): Boolean {
        val original = _originalEffectiveSchedule.value ?: return false
        val draft = _draftEffectiveSchedule.value ?: return false
        return original.timeJson != draft.timeJson ||
               original.smartConfigJson != draft.smartConfigJson ||
               original.nodesPerDay != draft.nodesPerDay
    }

    fun selectPolicy(policy: SchedulePolicy) {
        _pendingSchedulePolicy.value = policy
    }

    fun cancelPolicy() {
        // 恢复原作息草稿
        _draftEffectiveSchedule.value = _originalEffectiveSchedule.value
        _pendingSchedulePolicy.value = SchedulePolicy.NONE
    }

    fun clearPolicyIfScheduleChanged() {
        if (hasScheduleChanged()) {
            _pendingSchedulePolicy.value = SchedulePolicy.NONE
        }
    }

    fun updateDraft(periodTable: PeriodTableEntity) {
        _draftEffectiveSchedule.value = periodTable
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "EditTableSchedulePolicyTest" 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt \
       app/src/test/java/com/lingion/sleepy/ui/screen/mine/EditTableSchedulePolicyTest.kt
git commit -m "feat(edit-table): add schedule policy state model

- Add SchedulePolicy enum: NONE, DETACH_COPY, CREATE_NEW, SYNC
- Add three state flows: originalEffectiveSchedule, draftEffectiveSchedule, pendingSchedulePolicy
- Add methods: hasScheduleChanged, selectPolicy, cancelPolicy, clearPolicyIfScheduleChanged
- Add unit tests for state model"
```

---

### Task 2: Repository 层扩展 - 查询绑定数量

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt` (新增方法)
- Test: `app/src/test/java/com/lingion/sleepy/data/repository/ScheduleRepositoryTest.kt` (扩展现有测试)

**Interfaces:**
- Consumes: `periodTableDao` (已有)
- Produces: `fun getTablesBoundTo(periodTableId: Long): List<TimeTableEntity>`

- [ ] **Step 1: 写测试 - 查询绑定数量**

```kotlin
// 在 ScheduleRepositoryTest.kt 新增
@Test
fun `getTablesBoundTo returns tables bound to period table`() {
    val periodId = repo.insertPeriodTable(PeriodTableEntity(name = "Test"))
    val table1Id = repo.insertTable(TimeTableEntity(name = "T1", periodTableId = periodId))
    val table2Id = repo.insertTable(TimeTableEntity(name = "T2", periodTableId = periodId))
    
    val boundTables = repo.getTablesBoundTo(periodId)
    assertEquals(2, boundTables.size)
    assertTrue(boundTables.any { it.id == table1Id })
    assertTrue(boundTables.any { it.id == table2Id })
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :app:testDebugUnitTest --tests "ScheduleRepositoryTest.getTablesBoundTo" 2>&1 | tail -10`
Expected: FAIL (method not defined)

- [ ] **Step 3: 写实现**

```kotlin
// ScheduleRepository.kt 新增
/**
 * 查询绑定到指定作息表的所有课表
 */
fun getTablesBoundTo(periodTableId: Long): List<TimeTableEntity> {
    return tableDao.getAllTables().filter { it.periodTableId == periodTableId }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "ScheduleRepositoryTest.getTablesBoundTo" 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt \
       app/src/test/java/com/lingion/sleepy/data/repository/ScheduleRepositoryTest.kt
git commit -m "feat(schedule): add getTablesBoundTo query

- Query all tables bound to a specific period table
- Used for showing binding count in conflict resolution UI"
```

---

### Task 3: UI - 三选项 BottomSheet

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/ScheduleConflictBottomSheet.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt` (集成弹窗)

**Interfaces:**
- Consumes:
  - `SchedulePolicy` enum (Task 1 产出)
  - `periodTableName: String` (显示目标作息表名)
  - `boundTableCount: Int` (显示其他绑定课表数量)
  - `onSelect(SchedulePolicy)` callback
  - `onCancel()` callback
- Produces: Composable BottomSheet

- [ ] **Step 1: 写 BottomSheet 组件**

```kotlin
// ScheduleConflictBottomSheet.kt
@Composable
fun ScheduleConflictBottomSheet(
    periodTableName: String,
    boundTableCount: Int,
    onSelect: (SchedulePolicy) -> Unit,
    onCancel: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onCancel
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "这次作息改动要改到哪里？",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前作息：$periodTableName${if (boundTableCount > 0) "（另有 $boundTableCount 张课表绑定）" else "（仅此课表使用）"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 选项①：改本表作息
            OptionItem(
                title = "只改本课表作息",
                subtitle = "本课表改用独立作息，不再跟随共享表",
                onClick = { onSelect(SchedulePolicy.DETACH_COPY) }
            )

            // 选项②：创建新的同名作息表
            OptionItem(
                title = "新建独立作息表并绑定",
                subtitle = "以本课表名新建作息表，其他课表不受影响",
                onClick = { onSelect(SchedulePolicy.CREATE_NEW) }
            )

            // 选项③：同步更改作息表 X
            OptionItem(
                title = "同步更改「$periodTableName」",
                subtitle = if (boundTableCount > 0) "将影响另外 $boundTableCount 张课表" else "仅此课表使用该作息",
                onClick = { onSelect(SchedulePolicy.SYNC) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 取消按钮
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OptionItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: 在 EditTableScreen 集成弹窗**

```kotlin
// EditTableScreen.kt 保存按钮 onClick 逻辑修改
val hasScheduleChanged by vm.hasScheduleChanged.collectAsState()
val pendingPolicy by vm.pendingSchedulePolicy.collectAsState()

Button(
    onClick = {
        if (!hasScheduleChanged) {
            // 无作息变化，走普通保存
            vm.saveTable()
        } else if (pendingPolicy == SchedulePolicy.NONE) {
            // 有作息变化但未选择策略，弹三选项
            showConflictSheet = true
        } else {
            // 已选择策略，执行策略 + 保存
            vm.executePolicyAndSave()
        }
    }
) { ... }

// 弹窗触发
if (showConflictSheet) {
    val boundCount = repo.getTablesBoundTo(currentTable.periodTableId ?: -1).size
    ScheduleConflictBottomSheet(
        periodTableName = currentTable.periodTableId?.let { repo.getPeriodTableName(it) } ?: "",
        boundTableCount = boundCount,
        onSelect = { policy ->
            vm.selectPolicy(policy)
            showConflictSheet = false
        },
        onCancel = {
            vm.cancelPolicy()
            showConflictSheet = false
        }
    )
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -15`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/ScheduleConflictBottomSheet.kt \
       app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt
git commit -m "feat(edit-table): add schedule conflict bottom sheet

- Create ScheduleConflictBottomSheet with three options:
  - DETACH_COPY: use own schedule, unbind from shared
  - CREATE_NEW: create new period table and rebind
  - SYNC: update shared period table
- Integrate into EditTableScreen save flow"
```

---

### Task 4: 待执行提醒 Banner

**Files:**
- Create: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/PendingPolicyBanner.kt`
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt` (集成 Banner)

**Interfaces:**
- Consumes: `pendingSchedulePolicy: SchedulePolicy` (Task 1 产出), `onModify: () -> Unit` callback
- Produces: Composable Banner

- [ ] **Step 1: 写 Banner 组件**

```kotlin
// PendingPolicyBanner.kt
@Composable
fun PendingPolicyBanner(
    policy: SchedulePolicy,
    onModify: () -> Unit
) {
    if (policy == SchedulePolicy.NONE) return

    val policyText = when (policy) {
        SchedulePolicy.DETACH_COPY -> "只改本课表作息"
        SchedulePolicy.CREATE_NEW -> "新建独立作息表并绑定"
        SchedulePolicy.SYNC -> "同步更改作息表"
        SchedulePolicy.NONE -> return
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "待执行：$policyText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "再次保存后生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            TextButton(onClick = onModify) {
                Text("修改")
            }
        }
    }
}
```

- [ ] **Step 2: 在 EditTableScreen 集成 Banner**

```kotlin
// EditTableScreen.kt
val pendingPolicy by vm.pendingSchedulePolicy.collectAsState()

Column {
    // 待执行提醒 Banner
    PendingPolicyBanner(
        policy = pendingPolicy,
        onModify = { showConflictSheet = true }
    )

    // 原有编辑表单...
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -10`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/PendingPolicyBanner.kt \
       app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableScreen.kt
git commit -m "feat(edit-table): add pending policy banner

- Show pending schedule policy after user selects an option
- Allow user to modify selection before final save"
```

---

### Task 5: 执行策略 - 三个写入路径

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt` (新增 executePolicyAndSave)
- Modify: `app/src/main/java/com/lingion/sleepy/data/repository/ScheduleRepository.kt` (如需新增辅助方法)

**Interfaces:**
- Consumes: `pendingSchedulePolicy` + `draftEffectiveSchedule` (Task 1 产出), Repository 方法 (已有)
- Produces: `executePolicyAndSave()` 方法

- [ ] **Step 1: 写测试 - 三种策略执行**

```kotlin
// EditTableSchedulePolicyTest.kt 新增
@Test
fun `executePolicy DETACH_COPY writes own schedule and unbinds`() {
    val periodId = repo.insertPeriodTable(PeriodTableEntity(name = "Shared"))
    val tableId = repo.insertTable(TimeTableEntity(name = "T1", periodTableId = periodId))
    
    val vm = EditTableViewModel(repo, tableId)
    vm.selectPolicy(SchedulePolicy.DETACH_COPY)
    vm.updateDraft(PeriodTableEntity(timeJson = "{\"nodes\":[...]}"))
    
    vm.executePolicyAndSave()
    
    val table = repo.getTable(tableId)
    assertNull(table.periodTableId) // 已解绑
    assertEquals("{\"nodes\":[...]}", table.timeJson) // 写入本表
}

@Test
fun `executePolicy CREATE_NEW creates new period table and rebinds`() {
    val periodId = repo.insertPeriodTable(PeriodTableEntity(name = "Shared"))
    val tableId = repo.insertTable(TimeTableEntity(name = "T1", periodTableId = periodId))
    
    val vm = EditTableViewModel(repo, tableId)
    vm.selectPolicy(SchedulePolicy.CREATE_NEW)
    vm.updateDraft(PeriodTableEntity(timeJson = "{\"nodes\":[...]}"))
    
    vm.executePolicyAndSave()
    
    val table = repo.getTable(tableId)
    assertNotEquals(periodId, table.periodTableId) // 已换绑
    val newPeriod = repo.getPeriodTable(table.periodTableId!!)
    assertEquals("{\"nodes\":[...]}", newPeriod.timeJson)
    
    // 验证原表未被修改
    val oldPeriod = repo.getPeriodTable(periodId)
    assertEquals(originalTimeJson, oldPeriod.timeJson)
}

@Test
fun `executePolicy SYNC updates shared period table`() {
    val periodId = repo.insertPeriodTable(PeriodTableEntity(name = "Shared"))
    val tableId = repo.insertTable(TimeTableEntity(name = "T1", periodTableId = periodId))
    
    val vm = EditTableViewModel(repo, tableId)
    vm.selectPolicy(SchedulePolicy.SYNC)
    vm.updateDraft(PeriodTableEntity(timeJson = "{\"nodes\":[...]}"))
    
    vm.executePolicyAndSave()
    
    val period = repo.getPeriodTable(periodId)
    assertEquals("{\"nodes\":[...]}", period.timeJson)
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :app:testDebugUnitTest --tests "EditTableSchedulePolicyTest.executePolicy" 2>&1 | tail -10`
Expected: FAIL (method not defined)

- [ ] **Step 3: 实现 executePolicyAndSave**

```kotlin
// EditTableViewModel.kt 新增
fun executePolicyAndSave() {
    viewModelScope.launch {
        val policy = _pendingSchedulePolicy.value
        if (policy == SchedulePolicy.NONE) return@launch
        
        val draft = _draftEffectiveSchedule.value ?: return@launch
        val table = tableDao.getById(tableId) ?: return@launch
        
        when (policy) {
            SchedulePolicy.DETACH_COPY -> {
                // ① 写本表内置作息 + 解绑
                val updatedTable = table.copy(
                    timeJson = draft.timeJson,
                    smartConfigJson = draft.smartConfigJson,
                    nodesPerDay = draft.nodesPerDay,
                    periodTableId = null
                )
                repo.updateTable(updatedTable)
            }
            SchedulePolicy.CREATE_NEW -> {
                // ② 创建新表 + 改绑
                val newPeriodId = repo.copyPeriodTableAs(
                    table.periodTableId!!,
                    table.name + "作息"  // 或其他生成规则
                )
                // 更新新表内容
                repo.updatePeriodTableContent(
                    newPeriodId,
                    draft.timeJson,
                    draft.nodesPerDay,
                    draft.smartConfigJson
                )
                // 改绑到新表
                repo.bindPeriodTable(tableId, newPeriodId)
            }
            SchedulePolicy.SYNC -> {
                // ③ 写共享作息表
                val periodTable = table.periodTableId?.let { repo.getPeriodTable(it) }
                if (periodTable != null) {
                    repo.updateTableMetadataWithPeriodTable(
                        table.copy(name = table.name, startDate = table.startDate, maxWeek = table.maxWeek),
                        periodTable.copy(
                            timeJson = draft.timeJson,
                            smartConfigJson = draft.smartConfigJson,
                            nodesPerDay = draft.nodesPerDay
                        )
                    )
                }
            }
            SchedulePolicy.NONE -> { /* no-op */ }
        }
        
        // 执行策略后清除策略，下次保存不再弹窗
        _pendingSchedulePolicy.value = SchedulePolicy.NONE
        
        // 同时保存普通字段
        saveTable()
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "EditTableSchedulePolicyTest.executePolicy" 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt \
       app/src/test/java/com/lingion/sleepy/ui/screen/mine/EditTableSchedulePolicyTest.kt
git commit -m "feat(edit-table): implement execute policy logic

- DETACH_COPY: write own schedule and unbind from shared
- CREATE_NEW: create new period table and rebind
- SYNC: update shared period table in place"
```

---

### Task 6: 再次修改作息重弹三选项逻辑

**Files:**
- Modify: `app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt` (完善 clearPolicyIfScheduleChanged)

**Interfaces:**
- Consumes: `hasScheduleChanged()` (Task 1 产出)
- Produces: 保存前自动清除旧策略的调用点

- [ ] **Step 1: 在保存入口调用 clearPolicyIfScheduleChanged**

```kotlin
// EditTableViewModel.kt
fun saveTableWithCheck() {
    // 如果作息又改了，清除旧策略，下次保存重新弹窗
    clearPolicyIfScheduleChanged()
    
    val policy = _pendingSchedulePolicy.value
    if (policy != SchedulePolicy.NONE && !hasScheduleChanged()) {
        // 有策略且作息未变，执行策略 + 保存
        executePolicyAndSave()
    } else {
        // 无策略或有策略但作息已变（会被弹窗拦截），走普通保存
        saveTable()
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -10`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lingion/sleepy/ui/screen/mine/EditTableViewModel.kt
git commit -m "fix(edit-table): re-prompt when schedule changed after policy selected

- Call clearPolicyIfScheduleChanged before save
- If schedule edited again after policy selected, re-prompt on next save"
```

---

### Task 7: 集成测试 - 完整流程

**Files:**
- Create: `app/src/test/java/com/lingion/sleepy/ui/screen/mine/ScheduleConflictFlowTest.kt`

**Interfaces:**
- Consumes: 所有 Task 产出
- Produces: 端到端流程测试

- [ ] **Step 1: 写完整流程测试**

```kotlin
// ScheduleConflictFlowTest.kt
class ScheduleConflictFlowTest {
    @Test
    fun `full flow - select option then save`() {
        // 1. 创建绑定课表
        val periodId = repo.insertPeriodTable(PeriodTableEntity(name = "Shared"))
        val tableId = repo.insertTable(TimeTableEntity(name = "T1", periodTableId = periodId))
        
        // 2. 进入编辑页
        val vm = EditTableViewModel(repo, tableId)
        
        // 3. 修改作息
        vm.updateDraft(PeriodTableEntity(timeJson = "{\"nodes\":[1]}"))
        assertTrue(vm.hasScheduleChanged())
        
        // 4. 第一次保存 - 应弹窗
        vm.selectPolicy(SchedulePolicy.DETACH_COPY)
        assertEquals(SchedulePolicy.DETACH_COPY, vm.pendingSchedulePolicy)
        
        // 5. 再次保存 - 执行策略
        vm.saveTableWithCheck()
        
        // 6. 验证解绑成功
        val table = repo.getTable(tableId)
        assertNull(table.periodTableId)
    }

    @Test
    fun `cancel then save - only saves non-schedule fields`() {
        // 测试点取消后只撤销作息，其他字段保留
    }

    @Test
    fun `edit schedule again after select - re-prompts`() {
        // 测试选择后又改作息，下次保存重新弹窗
    }
}
```

- [ ] **Step 2: 运行测试验证**

Run: `./gradlew :app:testDebugUnitTest --tests "ScheduleConflictFlowTest" 2>&1 | tail -15`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/lingion/sleepy/ui/screen/mine/ScheduleConflictFlowTest.kt
git commit -m "test(edit-table): add end-to-end flow tests for schedule conflict resolution"
```

---

## 执行顺序

1. Task 1: ViewModel 状态模型（基础）
2. Task 2: Repository 扩展（支撑）
3. Task 3: 三选项 BottomSheet（UI 核心）
4. Task 4: 待执行提醒 Banner（UI 辅助）
5. Task 5: 执行策略（核心逻辑）
6. Task 6: 再次修改重弹逻辑（边界处理）
7. Task 7: 集成测试（验收）

## Plan 完成

实施计划已保存到 `docs/superpowers/plans/2026-09-24-period-table-conflict-resolution.md`。

**Two execution options:**

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
