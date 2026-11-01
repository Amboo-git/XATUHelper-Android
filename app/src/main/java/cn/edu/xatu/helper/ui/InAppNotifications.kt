package cn.edu.xatu.helper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import cn.edu.xatu.helper.model.AcademicNotice
import kotlinx.coroutines.delay

/** All colors share the same surface palette, including the separately composed action buttons. */
@Composable internal fun InAppNoticeBanner(title: String, message: String? = null, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Popup(alignment = Alignment.TopCenter) {
        Snackbar(modifier = Modifier.safeDrawingPadding().padding(16.dp).widthIn(max = 560.dp).testTag("inAppNotice"),
            containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface,
            action = { TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)) { Text(action) } },
            dismissAction = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭通知", tint = colors.onSurfaceVariant) } }) {
            Column(Modifier.clickable(onClick = onAction)) {
                Text(title, fontWeight = FontWeight.Medium)
                message?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable internal fun AcademicNotification(notice: AcademicNotice, onOpen: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(notice.id) { delay(6000); onDismiss() }
    InAppNoticeBanner(notice.title, notice.message, "查看", onOpen, onDismiss)
}
