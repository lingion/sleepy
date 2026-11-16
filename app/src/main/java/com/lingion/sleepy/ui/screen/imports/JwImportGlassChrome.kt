package com.lingion.sleepy.ui.screen.imports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.liquidglass.GlassButton
import com.lingion.sleepy.ui.component.liquidglass.GlassMaterialRole
import com.lingion.sleepy.ui.component.liquidglass.glassControl

/** Browser chrome only: each control owns its material; there is no enclosing glass panel. */
@Composable
internal fun JwImportGlassToolbar(
    schoolName: String,
    currentUrl: String,
    urlDraft: TextFieldState,
    editingUrl: Boolean,
    enabled: Boolean,
    showAdapter: Boolean,
    desktopUa: Boolean,
    onBack: () -> Unit,
    onEditOrSubmit: () -> Unit,
    onSubmit: () -> Unit,
    onChooseAdapter: () -> Unit,
    onToggleUa: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val editLabel = stringResource(if (editingUrl) R.string.jw_url_submit else R.string.jw_edit_url)
    Column(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwImportGlassIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                label = stringResource(R.string.back),
                tag = "jw_back",
                onClick = onBack,
            )
            JwImportGlassAddressField(
                currentUrl = currentUrl,
                state = urlDraft,
                editing = editingUrl,
                enabled = enabled,
                onStartEditing = onEditOrSubmit,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            JwImportGlassIconButton(
                icon = if (editingUrl) Icons.Outlined.Check else Icons.Outlined.Edit,
                label = editLabel,
                tag = "jw_url_edit",
                enabled = enabled,
                onClick = onEditOrSubmit,
            )
            if (showAdapter) {
                JwImportGlassIconButton(
                    icon = Icons.Outlined.School,
                    label = stringResource(R.string.jw_select_academic_adapter),
                    tag = "jw_select_adapter",
                    enabled = enabled,
                    onClick = onChooseAdapter,
                )
            }
            JwImportGlassIconButton(
                icon = if (desktopUa) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                label = stringResource(if (desktopUa) R.string.jw_toggle_mobile_ua else R.string.jw_toggle_desktop_ua),
                tag = "jw_toggle_ua",
                enabled = enabled,
                onClick = onToggleUa,
            )
            JwImportGlassIconButton(
                icon = Icons.Outlined.Refresh,
                label = stringResource(R.string.jw_refresh),
                tag = "jw_refresh",
                enabled = enabled,
                onClick = onRefresh,
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(
                text = schoolName,
                modifier = Modifier.glassControl(
                    shape = CircleShape,
                    role = GlassMaterialRole.Menu,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall.copy(
                    shadow = floatingTextShadow(colors.onSurface),
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JwImportGlassAddressField(
    currentUrl: String,
    state: TextFieldState,
    editing: Boolean,
    enabled: Boolean,
    onStartEditing: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.jw_url_hint)
    val editLabel = stringResource(R.string.jw_edit_url)
    val interaction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Box(
        modifier = modifier.heightIn(min = 48.dp).then(
            if (editing) Modifier else Modifier
                .dbgTag("jw_url_display", text = currentUrl, role = "button")
                .semantics { contentDescription = editLabel }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onStartEditing,
                ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .glassControl(shape = CircleShape, interactionSource = interaction)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (editing) {
                if (state.text.isEmpty()) {
                    Text(hint, color = colors.onSurfaceVariant, fontSize = 16.sp, maxLines = 1)
                }
                BasicTextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        .dbgTag("jw_url_field", role = "textbox")
                        .semantics { contentDescription = hint },
                    enabled = enabled,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(colors.primary),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    scrollState = rememberScrollState(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    onKeyboardAction = KeyboardActionHandler { onSubmit() },
                )
            } else {
                Text(
                    text = currentUrl.ifBlank { hint },
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Import-page sizing choice: 44dp body inside a separate 48dp accessible target. */
@Composable
private fun JwImportGlassIconButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(48.dp).dbgTag(tag, text = label, role = "button")
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(44.dp).glassControl(
                shape = CircleShape,
                enabled = enabled,
                interactionSource = interaction,
                pressedContentAlpha = .5f,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f),
            )
        }
    }
}

/** The instructional copy and primary action remain independent of the page's bottom backing. */
@Composable
internal fun JwImportGlassCaptureBar(
    enabled: Boolean,
    navigationHint: String?,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).glassControl(
                shape = RoundedCornerShape(20.dp),
                role = GlassMaterialRole.Menu,
            ).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.jw_after_login),
                style = MaterialTheme.typography.bodySmall.copy(
                    shadow = floatingTextShadow(colors.onSurfaceVariant),
                ),
                color = colors.onSurfaceVariant,
            )
            Text(
                text = navigationHint ?: stringResource(R.string.jw_nav_hint),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    shadow = floatingTextShadow(colors.onSurface),
                ),
                color = colors.onSurface,
            )
        }
        GlassButton(
            onClick = onCapture,
            enabled = enabled,
            modifier = Modifier.dbgTag("jw_capture", text = stringResource(R.string.jw_import_page), role = "button"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.jw_import_page), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** A small opposite-tone halo keeps floating labels legible across changing page content. */
private fun floatingTextShadow(textColor: Color): Shadow = Shadow(
    color = if (textColor.luminance() > .5f) Color.Black.copy(alpha = .45f)
        else Color.White.copy(alpha = .65f),
    offset = Offset(0f, .5f),
    blurRadius = 3f,
)
