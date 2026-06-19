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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 学校选择页 — 教务直连第一步
 *
 * 数据来自 assets/schools.json（Apache-2.0 自 dIT8Zv/WakeupSchedule_BUPT）
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
        else schools.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择学校") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                    .padding(16.dp),
                placeholder = { Text("搜索学校…", color = colors.onSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            if (filtered.isEmpty()) {
                EmptyState(schools.isEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
            if (!school.url.isNullOrBlank()) {
                Text(
                    text = JwProtocol.displayName(school.type),
                    style = MaterialTheme.typography.bodySmall,
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
            text = if (isLoading) "加载中…" else "未找到匹配的学校",
            color = colors.onSurfaceVariant
        )
    }
}
