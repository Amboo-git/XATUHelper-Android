@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.xatune.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember { context.assets.open("privacy-policy.txt").bufferedReader().use { it.readText() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("隐私政策") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭隐私政策") } }) }) { padding ->
            Text(text, Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
