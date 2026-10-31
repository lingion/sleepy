package com.lingion.sleepy.ui.screen.imports

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.jw.SchoolDomainMatch
import com.lingion.sleepy.data.jw.SchoolCityIndex
import com.lingion.sleepy.data.jw.SchoolSearchIndex
import com.lingion.sleepy.data.jw.SchoolLocationState
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.launch

private fun looksLikeUrl(s: String): Boolean {
    val t = s.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return true
    if (t.matches(Regex("""[a-zA-Z0-9][-a-zA-Z0-9]{0,62}\.[a-zA-Z]{2,}([/:].*)?"""))) return true
    if (t.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?"""))) return true
    return false
}

private fun normalizeUrl(s: String): String {
    val t = s.trim()
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

/** 学校首字母分组 */
private data class SchoolSection(
    val letter: String,
    val schools: List<JwSchoolInfo>
)

/**
 * 生成学校的完整拼音排序键。
 * 使用 schools.json 中的 sortKeyFull 字段（完整拼音，如 "haerbingongchengdaxue"），
 * 保证同首字母内严格按拼音字典序排列（ha < hai < hang < he ... < hua < huanan）。
 */
private fun schoolSortKey(s: JwSchoolInfo): String {
    val firstLetter = if (s.sortKey.isNotEmpty() && s.sortKey[0].isLetter()) {
        s.sortKey[0].uppercase()
    } else {
        "★"
    }
    // sortKeyFull 由 pypinyin 预生成，如 "haerbingongchengdaxue"
    // 缺失时 fallback 到 name（自定义 URL 场景）
    return "$firstLetter|${s.sortKeyFull.ifBlank { s.name }}"
}

/** 把扁平学校列表按完整拼音排序后，按首字母分组 */
private fun groupByLetter(schools: List<JwSchoolInfo>): List<SchoolSection> {
    if (schools.isEmpty()) return emptyList()
    // 1. 按完整拼音排序
    val sorted = schools.sortedWith(compareBy { schoolSortKey(it) })
    // 2. 按首字母分组
    val groups = linkedMapOf<String, MutableList<JwSchoolInfo>>()
    for (s in sorted) {
        val letter = if (s.sortKey.isNotEmpty() && s.sortKey[0].isLetter()) {
            s.sortKey[0].uppercase()
        } else {
            "★"
        }
        groups.getOrPut(letter) { mutableListOf() }.add(s)
    }
    return groups.map { (k, v) -> SchoolSection(k, v) }
}

/**
 * 学校选择页 — 教务直连第一步
 *
 * 数据来自 assets/schools.json；城市推荐元数据来自 assets/school_cities.json。
 * 右侧字母索引栏可点击/滑动跳转到对应分组
 */
@Composable
fun SchoolSelectScreen(
    onSchoolSelected: (JwSchoolInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val schools by viewModel.schools.collectAsState()
    val locationState by viewModel.schoolLocation.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeLocationAfterSettings by rememberSaveable { mutableStateOf(false) }
    val requestedPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    val hasLocationPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // The locator derives accuracy from actual system grants, including approximate-only access.
        viewModel.locateSchoolCity()
    }
    val requestLocation: () -> Unit = {
        if (hasLocationPermission()) viewModel.locateSchoolCity()
        else locationPermission.launch(requestedPermissions)
    }
    val openPermissionSettings: () -> Unit = {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) { /* Some ROMs do not provide this settings page. */ }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelSchoolLocation()
            // Only continue a lookup when its button opened system location settings.
            // Entering this screen or returning from permission settings does not locate.
            if (event == Lifecycle.Event.ON_RESUME && resumeLocationAfterSettings) {
                resumeLocationAfterSettings = false
                viewModel.locateSchoolCity()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelSchoolLocation()
        }
    }
    val retryLocation: () -> Unit = {
        if (locationState == SchoolLocationState.LocationDisabled) {
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                resumeLocationAfterSettings = true
            } catch (_: ActivityNotFoundException) { /* Keep manual lookup available. */ }
        } else {
            requestLocation()
        }
    }
    // 搜索词 rememberSaveable: 选校进 WebView 再返回, 列表滚动位置由 JwImportActivity
    // stage 分支的 SaveableStateProvider 恢复, 搜索词也要跟着回来(remember 会随覆盖销毁)
    var query by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 「自定义教务链接」入口 → 聚焦搜索框 + 弹键盘 (用户点入口直接开始输入 URL)
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val fieldColors = SleepyTheme.fieldColors()

    val searchIndex = remember(schools) { SchoolSearchIndex(schools) }
    val filtered = remember(searchIndex, query) { searchIndex.search(query) }

    val isUrl = remember(query) { looksLikeUrl(query) }
    val urlProtocol = remember(query, isUrl) {
        if (isUrl) viewModel.detectProtocolFromUrl(query) else null
    }

    // 按字母分组（仅无搜索时显示分组+索引栏）
    val sections = remember(filtered) { groupByLetter(filtered) }
    val showIndexBar = query.isBlank() && sections.size > 1
    val showRecommendations = query.isBlank()
    val city = (locationState as? SchoolLocationState.Ready)?.city
    val recommendedSchools = remember(schools, city) {
        city?.let { SchoolCityIndex.schoolsInCity(schools, it) }.orEmpty()
    }
    // The recommendation card and custom URL entry each occupy one LazyColumn item.
    val sectionStartIndex = if (showRecommendations) 2 else 1

    val listState = rememberLazyListState()

    // section letter → list index 映射（LazyColumn item index: section header 占偶数位, school 占奇数位）
    // Account for the recommendation card so letter taps still land on their headers.
    val letterToIndex = remember(sections, sectionStartIndex) {
        val map = mutableMapOf<String, Int>()
        var idx = sectionStartIndex
        for (sec in sections) {
            map[sec.letter] = idx
            idx++ // header
            idx += sec.schools.size // schools
        }
        map
    }

    // 当前激活字母（用于高亮）— 起始索引随推荐卡是否显示而变化。
    val activeLetter by remember(sections, sectionStartIndex) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            // 找当前第一个 section header
            var runningIdx = sectionStartIndex
            for (sec in sections) {
                val headerIdx = runningIdx
                val lastSchoolIdx = runningIdx + sec.schools.size
                if (firstVisible in headerIdx..lastSchoolIdx) return@derivedStateOf sec.letter
                runningIdx = lastSchoolIdx + 1
            }
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_school)) },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.dbgTag("school_back", role = "button"),
                        onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .dbgTag("school_search", role = "textbox")
                    .focusRequester(searchFocusRequester)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_school_url), color = colors.onSurfaceVariant) },
                supportingText = {
                    Text(
                        stringResource(R.string.school_pinyin_hint),
                        color = colors.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = SleepyTheme.fieldShape,
                colors = fieldColors
            )

            // 计数行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.jw_catalog_counts, schools.size, schools.count { it.isSupported && it.canImport }, schools.count { it.isUnverified }),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                if (query.isNotBlank()) {
                    Text(
                        text = "匹配 ${filtered.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary
                    )
                }
            }

            if (isUrl) {
                // Keep exact catalogue identities so typed URLs cannot bypass admission prompts.
                val urlSelections = remember(query, schools, urlProtocol) {
                    SchoolDomainMatch.selectionsForUrl(
                        normalizeUrl(query.trim()), schools, urlProtocol, "自定义教务"
                    )
                }
                if (urlSelections.size == 1) {
                    val school = urlSelections.single()
                    UrlDirectRow(
                        url = query.trim(),
                        protocolType = urlProtocol,
                        matchedSchool = school.takeIf { it in schools },
                        onClick = { onSchoolSelected(school) }
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(urlSelections, key = { it.stableKey }) { school ->
                            SchoolRow(school = school, onClick = { onSchoolSelected(school) })
                        }
                    }
                }
            }

            if (filtered.isEmpty() && !isUrl) {
                EmptyState(schools.isEmpty())
            } else if (isUrl && filtered.isEmpty()) {
                // URL only, no school list
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top
                ) {
                    // 学校列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (showRecommendations) {
                            item(key = "possible_schools") {
                                PossibleSchoolsCard(
                                    state = locationState,
                                    schools = recommendedSchools,
                                    isLoadingSchools = schools.isEmpty(),
                                    onLocate = retryLocation,
                                    onPermissionSettings = openPermissionSettings,
                                    onSchoolSelected = onSchoolSelected
                                )
                            }
                        }
                        // 自定义教务链接入口 — 位于推荐卡后、A 分组前，不随搜索过滤消失:
                        // 学校不在目录里的用户从这里走, 点了直接聚焦搜索框弹键盘输 URL
                        item(key = "custom_url_entry") {
                            CustomUrlEntryRow(
                                onClick = {
                                    searchFocusRequester.requestFocus()
                                    keyboard?.show()
                                }
                            )
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        }
                        sections.forEach { section ->
                            // Section header
                            item(key = "header_${section.letter}") {
                                SectionHeader(letter = section.letter)
                            }
                            // Schools
                            items(
                                items = section.schools,
                                key = { it.stableKey }
                            ) { school ->
                                SchoolRow(
                                    school = school,
                                    onClick = { onSchoolSelected(school) }
                                )
                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                            }
                        }
                    }

                    // 字母索引栏
                    if (showIndexBar) {
                        AlphabetIndexBar(
                            letters = sections.map { it.letter },
                            activeLetter = activeLetter,
                            onLetterTap = { letter ->
                                val targetIdx = letterToIndex[letter]
                                if (targetIdx != null) {
                                    scope.launch {
                                        listState.animateScrollToItem(targetIdx)
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(32.dp)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** Same-city suggestions stay inside one outlined box and use the original school click path. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PossibleSchoolsCard(
    state: SchoolLocationState,
    schools: List<JwSchoolInfo>,
    isLoadingSchools: Boolean,
    onLocate: () -> Unit,
    onPermissionSettings: () -> Unit,
    onSchoolSelected: (JwSchoolInfo) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val city = (state as? SchoolLocationState.Ready)?.city
    var expanded by rememberSaveable(city) { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.possible_schools),
                        style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    Text(
                        text = when (state) {
                            is SchoolLocationState.Ready -> pluralStringResource(R.plurals.school_location_city, schools.size, state.city, schools.size)
                            SchoolLocationState.Locating -> stringResource(R.string.school_location_loading)
                            SchoolLocationState.PermissionDenied -> stringResource(R.string.school_location_denied)
                            SchoolLocationState.AccuracyInsufficient -> stringResource(R.string.school_location_accuracy_insufficient)
                            SchoolLocationState.LocationDisabled -> stringResource(R.string.school_location_disabled)
                            SchoolLocationState.PositionUnavailable -> stringResource(R.string.school_location_no_fix)
                            SchoolLocationState.Unavailable -> stringResource(R.string.school_location_unavailable)
                            SchoolLocationState.Idle -> stringResource(R.string.school_location_hint)
                        },
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }
                if (state == SchoolLocationState.Locating) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(
                        modifier = Modifier.dbgTag("school_locate", role = "button"),
                        onClick = onLocate) {
                        Text(stringResource(when (state) {
                            SchoolLocationState.Idle -> R.string.school_location_locate
                            SchoolLocationState.PermissionDenied -> R.string.school_location_enable
                            SchoolLocationState.LocationDisabled -> R.string.school_location_settings
                            else -> R.string.school_location_retry
                        }))
                    }
                }
            }
            if (state is SchoolLocationState.Ready) {
                if (schools.isEmpty()) {
                    Text(stringResource(if (isLoadingSchools) R.string.loading else R.string.school_location_no_matches),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.school_location_city_hint),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                    if (expanded) {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(schools, key = { it.stableKey }) { school ->
                                SchoolRow(school = school, onClick = { onSchoolSelected(school) })
                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                            }
                        }
                    } else schools.take(5).forEach { school ->
                        SchoolRow(school = school, onClick = { onSchoolSelected(school) })
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    }
                }
            }
            if (state == SchoolLocationState.PermissionDenied || state == SchoolLocationState.AccuracyInsufficient || state is SchoolLocationState.Ready) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state is SchoolLocationState.Ready && schools.size > 5) {
                        TextButton(
                            modifier = Modifier.dbgTag("school_recommended_expand", role = "button"),
                            onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.school_location_collapse else R.string.school_location_show_all))
                        }
                    }
                    TextButton(
                        modifier = Modifier.dbgTag("school_permission_settings", role = "button"),
                        onClick = onPermissionSettings) {
                        Text(stringResource(R.string.school_location_permission_settings))
                    }
                }
            }
        }
    }
}

/**
 * 「自定义教务链接」入口 — 位于推荐卡后、A 分组之前。
 * 与 UrlDirectRow 同构 (Link 图标 + primary 色) 但语义是引导: 点击不导入,
 * 而是聚焦搜索框弹键盘, 让用户把教务 URL 输进去 — 输入合法 URL 后
 * 搜索框下方出现 UrlDirectRow 完成实际导入。
 */
@Composable
private fun CustomUrlEntryRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .dbgTag("school_custom_url_entry", text = stringResource(R.string.custom_url_entry), role = "button")
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SleepyTheme.shapes.small)
                .background(colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.custom_url_entry),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.primary
            )
            Text(
                text = stringResource(R.string.custom_url_entry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Section header — 显示首字母 */
@Composable
private fun SectionHeader(letter: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
            modifier = Modifier
                .clip(SleepyTheme.shapes.extraSmall)
                .background(colors.primaryContainer.copy(alpha = SleepyTheme.Alpha.hairline))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

/** 右侧字母索引栏 — 支持点击+滑动 */
@Composable
private fun AlphabetIndexBar(
    letters: List<String>,
    activeLetter: String?,
    onLetterTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var barCoords: LayoutCoordinates? by remember { mutableStateOf(null) }

    Box(
        modifier = modifier
            .dbgTag("school_index_bar", role = "slider", clickable = false)
            .onGloballyPositioned { barCoords = it }
            .pointerInput(letters) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                            val change = event.changes.firstOrNull() ?: continue
                            if (!change.pressed) continue
                            val coords = barCoords ?: continue
                            val y = change.position.y
                            val barHeight = coords.size.height.toFloat()
                            if (barHeight <= 0f) continue
                            val ratio = (y / barHeight).coerceIn(0f, 0.999f)
                            val idx = (ratio * letters.size).toInt()
                            if (idx in letters.indices) {
                                onLetterTap(letters[idx])
                            }
                        }
                    }
                }
            }
            .padding(end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // 用 Layout 均匀撑满高度，每个字母占 1/N，触摸 Y→index 精准对应
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (letter in letters) {
                val isActive = letter == activeLetter
                Text(
                    text = letter,
                    style = if (isActive) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.labelSmall,
                    color = if (isActive) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(SleepyTheme.shapes.extraSmall)
                        .background(
                            if (isActive) colors.primaryContainer.copy(alpha = SleepyTheme.Alpha.inactive) else Color.Transparent
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun SchoolRow(school: JwSchoolInfo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Every entry can show its availability; the Activity gates actual navigation.
    val isClickable = school.canImport
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .dbgTag("school_item", text = school.name, role = "button")
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SleepyTheme.shapes.small)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // T13: status badge (supported 不渲染, 避免冗余)
                // 徽章与校名之间的间距必须和徽章本身同增同减 — 无条件 Spacer 会让无徽章
                // (supported, 占绝大多数) 的校名比第二行「协议 · 网址」凭空右移, 两行首端错位
                // unverified 同样无徽章(2026-10-18 列表静默) — 判定必须与 SchoolStatusBadge 分支一致
                if (school.status != JwSchoolInfo.STATUS_SUPPORTED && !school.isUnverified) {
                    SchoolStatusBadge(school = school)
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Text(
                    text = school.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (isClickable) colors.onSurface else colors.onSurfaceVariant
                )
            }
            if (!school.url.isBlank()) {
                Text(
                    text = JwProtocol.displayName(school.type) + " · " + school.url.replace("https://", "").replace("http://", "").trimEnd('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 学校状态 badge — T13 新增。
 * supported 不渲染 badge（默认全功能）; pending=「待适配」tertiary chip;
 * grad_supported=「研究生」secondary chip; legacy=「旧版」surfaceVariant chip。
 * 文案消费 strings.xml 既有 jw_pending_* 键（此前 0 引用的 dead 字符串）。
 *
 * 2026-10-18 用户: unverified 不渲染徽章 — 目录扩充后待验证占列表绝大多数,
 * 满屏「待验证」chip 反而淹没其余状态信号。提示职责移到两处弹窗:
 * 点进前 catalogNotice 确认（保留）+ 导入成功后报告弹窗（新增, JwImportActivity）。
 */
@Composable
private fun SchoolStatusBadge(school: JwSchoolInfo) {
    if (school.status == JwSchoolInfo.STATUS_SUPPORTED) return
    val colors = MaterialTheme.colorScheme
    val (label, bg, fg) = when (school.status) {
        JwSchoolInfo.STATUS_PENDING -> Triple(
            stringResource(R.string.jw_pending_pending),
            colors.tertiaryContainer,
            colors.onTertiaryContainer
        )
        JwSchoolInfo.STATUS_GRAD_PENDING -> Triple(
            stringResource(R.string.jw_pending_pending) + " · " + stringResource(R.string.jw_pending_grad),
            colors.tertiaryContainer,
            colors.onTertiaryContainer
        )
        JwSchoolInfo.STATUS_GRAD_SUPPORTED -> Triple(
            stringResource(R.string.jw_pending_grad),
            colors.secondaryContainer,
            colors.onSecondaryContainer
        )
        JwSchoolInfo.STATUS_LEGACY -> Triple(
            stringResource(R.string.jw_pending_legacy),
            colors.surfaceVariant,
            colors.onSurfaceVariant
        )
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(SleepyTheme.shapes.small)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
private fun UrlDirectRow(url: String, protocolType: String?, matchedSchool: JwSchoolInfo?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .dbgTag("school_url_direct", text = url, role = "button")
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SleepyTheme.shapes.small)
                .background(colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.url_direct_login),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.primary
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            val protoName = JwProtocol.displayName(if (protocolType.isNullOrBlank()) "" else protocolType)
            when {
                // issue #25: 域名映射命中目录条目时, 显示学校名, 比协议名更可确认
                matchedSchool != null -> Text(
                    text = stringResource(R.string.url_match_school, matchedSchool.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
                protocolType != null -> Text(
                    text = "${stringResource(R.string.url_detected)} $protoName",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
                else -> Text(
                    text = stringResource(R.string.url_auto_detect),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isLoading: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isLoading) stringResource(R.string.loading) else stringResource(R.string.no_school_found),
            color = colors.onSurfaceVariant
        )
    }
}
