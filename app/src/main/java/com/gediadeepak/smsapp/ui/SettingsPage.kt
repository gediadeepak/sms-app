@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gediadeepak.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** A full-screen destination with system back and edge-to-edge insets. */
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
      Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Canvas, topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter, content = content)
      }
    }
}
