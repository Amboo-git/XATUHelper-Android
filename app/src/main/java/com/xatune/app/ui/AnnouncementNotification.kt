package com.xatune.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun AnnouncementNotification(model: AnnouncementViewModel) {
    val pending by model.pending.collectAsStateWithLifecycle()
    val notice = pending.firstOrNull() ?: return
    var details by remember(notice.id) { mutableStateOf(false) }
    if (!details) InAppNoticeBanner(notice.title, notice.body, "查看", { details = true }, { model.dismiss(notice.id) })
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(notice.title) },
        text = { Text(notice.body, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { model.dismiss(notice.id) }) { Text("知道了") } })
}
