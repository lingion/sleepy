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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private fun looksLikeUrl(s: String): Boolean {
    val t = s.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return true
    // 域名+路径 或 域名:端口
    if (t.matches(Regex("""[a-zA-Z0-9][-a-zA-Z0-9]{0,62}\.[a-zA-Z]{2,}([/:].*)?"""))) return true
    // IP:端口
    if (t.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?"""))) return true
    return false
}

private fun normalizeUrl(s: String): String {
    val t = s.trim()
    return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
}

/**
 * 学校选择页 — 教务直连第一步
 *
 * 数据来自 assets/schools.json（183 所带真 URL+type — Sleepy 30 + WakeupSchedule_BUPT 184 去重）
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
            // Alias-exact match floats to top
            matched.sortedByDescending { it.aliases.any { a -> a.lowercase() == q } }
        }
    }

    val isUrl = remember(query) { looksLikeUrl(query) }
    val urlProtocol = remember(query, isUrl) {
        if (isUrl) viewModel.detectProtocolFromUrl(query) else null
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
                // URL detected but no school match — only show URL row (already shown above)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { "${it.sortKey}_${it.name}" }) { school ->
                        SchoolRow(
                            school = school,
                            onClick = { onSchoolSelected(school) }
                        )
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    }
                }
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
