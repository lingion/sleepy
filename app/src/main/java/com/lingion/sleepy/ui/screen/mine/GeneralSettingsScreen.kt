package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs

/**
 * 通用设置页(决策 D1 L1 ⑤): 暂仅「语言」1 项, 预留扩展位
 * (后续默认开学日期格式、学期起始月等迁入, 见详报 04 分组④)。
 * 语言卡沿用原 MoreSettingsScreen 折叠列表的卡片样式(此处单卡默认展开)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }

    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_general)) },
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    languages.forEach { (code, label) ->
                        val selected = language == code
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                language = code
                                AppPrefs.setLanguage(context, code)
                                (context as? android.app.Activity)?.recreate()
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
                            if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        if (code != languages.last().first) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
