package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.PinyinMatcher
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
 * 数据来自 assets/schools.json（145 所带真 URL+type）
 * 右侧字母索引栏可点击/滑动跳转到对应分组
 */
@Composable
fun SchoolSelectScreen(
    onSchoolSelected: (JwSchoolInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: JwImportViewModel = viewModel()
) {
    val schools by viewModel.schools.collectAsState()
    var query by remember { mutableStateOf("") }
    val colors = SleepyTheme.colors
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        cursorColor = colors.primary
    )

    val filtered = remember(schools, query) {
        if (query.isBlank()) schools
        else {
            val q = query.trim().lowercase()
            val matched = schools.filter { PinyinMatcher.match(it.name, it.sortKey, query, it.aliases) }
            matched.sortedByDescending { it.aliases.any { a -> a.lowercase() == q } }
        }
    }

    val isUrl = remember(query) { looksLikeUrl(query) }
    val urlProtocol = remember(query, isUrl) {
        if (isUrl) viewModel.detectProtocolFromUrl(query) else null
    }

    // 按字母分组（仅无搜索时显示分组+索引栏）
    val sections = remember(filtered) { groupByLetter(filtered) }
    val showIndexBar = query.isBlank() && sections.size > 1

    val listState = rememberLazyListState()

    // section letter → list index 映射（LazyColumn item index: section header 占偶数位, school 占奇数位）
    val letterToIndex = remember(sections) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        for (sec in sections) {
            map[sec.letter] = idx
            idx++ // header
            idx += sec.schools.size // schools
        }
        map
    }

    // 当前激活字母（用于高亮）
    val activeLetter by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            // 找当前第一个 section header
            var runningIdx = 0
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
                    IconButton(onClick = onBack) {
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_school_url), color = colors.onSurfaceVariant) },
                supportingText = {
                    Text(
                        stringResource(R.string.school_pinyin_hint),
                        color = colors.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
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
                    text = stringResource(R.string.school_count_total, schools.size),
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
                UrlDirectRow(
                    url = query.trim(),
                    protocolType = urlProtocol,
                    onClick = {
                        val school = JwSchoolInfo(
                            sortKey = "",
                            name = "自定义教务",
                            url = normalizeUrl(query.trim()),
                            type = urlProtocol,
                            status = JwSchoolInfo.STATUS_SUPPORTED
                        )
                        onSchoolSelected(school)
                    }
                )
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
                        sections.forEach { section ->
                            // Section header
                            item(key = "header_${section.letter}") {
                                SectionHeader(letter = section.letter)
                            }
                            // Schools
                            items(
                                items = section.schools,
                                key = { "${it.sortKey}_${it.name}" }
                            ) { school ->
                                SchoolRow(
                                    school = school,
                                    onClick = { onSchoolSelected(school) }
                                )
                                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
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

/** Section header — 显示首字母 */
@Composable
private fun SectionHeader(letter: String) {
    val colors = SleepyTheme.colors
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
                .clip(RoundedCornerShape(6.dp))
                .background(colors.primaryContainer.copy(alpha = 0.5f))
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
    val colors = SleepyTheme.colors
    var barCoords: LayoutCoordinates? by remember { mutableStateOf(null) }

    Box(
        modifier = modifier
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isActive) colors.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun SchoolRow(school: JwSchoolInfo, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
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
            Text(
                text = school.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
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

@Composable
private fun UrlDirectRow(url: String, protocolType: String?, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
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
            if (protocolType != null) {
                Text(
                    text = "${stringResource(R.string.url_detected)} $protoName",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            } else {
                Text(
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
    val colors = SleepyTheme.colors
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
