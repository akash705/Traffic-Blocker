package com.akash.apptrafficblocker.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akash.apptrafficblocker.data.PrefsManager
import com.akash.apptrafficblocker.service.BlockerService
import com.akash.apptrafficblocker.service.BlockerState
import com.akash.apptrafficblocker.ui.theme.Green500
import com.akash.apptrafficblocker.ui.theme.Orange500
import com.akash.apptrafficblocker.ui.theme.Red500
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    blockerService: BlockerService?,
    onNavigateToAppPicker: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBlocklist: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val serviceState by blockerService?.state?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(BlockerState())
        }

    val targetPackages = viewModel.prefs.targetPackages
    val targetAppNames = viewModel.prefs.targetAppNames

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Blocker") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Apps Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blocking Targets",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onNavigateToAppPicker) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit apps")
                        }
                    }

                    if (targetPackages.isNotEmpty()) {
                        Text(
                            text = "${targetPackages.size} app${if (targetPackages.size > 1) "s" else ""} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        targetPackages.forEach { pkg ->
                            val appName = targetAppNames[pkg] ?: pkg
                            val mode = viewModel.getAppMode(pkg)
                            val modeLabel = if (mode == PrefsManager.MODE_BLOCK_ALL) "Block All" else "Block Domains"
                            val modeColor = if (mode == PrefsManager.MODE_BLOCK_ALL) Red500 else MaterialTheme.colorScheme.primary

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (mode == PrefsManager.MODE_BLOCK_ALL) Icons.Default.Block else Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = modeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = pkg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = { viewModel.toggleAppMode(pkg) },
                                    label = {
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = modeColor.copy(alpha = 0.12f),
                                        labelColor = modeColor
                                    )
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onNavigateToAppPicker,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Apps to Block")
                        }
                    }
                }
            }

            // Domain Blocklists Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = onNavigateToBlocklist
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Domain Blocklists",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (serviceState.blockedDomainCount > 0) {
                            Text(
                                text = "${formatCount(serviceState.blockedDomainCount)} domains loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Configure blocklist URLs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (color, statusText) = when {
                            !serviceState.isRunning -> Pair(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                "Inactive"
                            )
                            serviceState.isPaused -> Pair(Orange500, "Paused")
                            serviceState.isBlocking -> Pair(Red500, "Blocking — foreground detected")
                            else -> Pair(Green500, "Watching — waiting for target app")
                        }

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (serviceState.lastBlockedAt != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault())
                            .format(Date(serviceState.lastBlockedAt!!))
                        Text(
                            text = "Last blocked: $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Permission Warnings
            if (!viewModel.hasUsageStatsPermission()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Usage Stats permission required",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Needed to detect which app is in the foreground.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            )
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Main Action Button
            if (targetPackages.isNotEmpty()) {
                Button(
                    onClick = {
                        if (serviceState.isRunning) {
                            stopBlockerService(context)
                        } else {
                            if (!viewModel.hasUsageStatsPermission()) {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                )
                                return@Button
                            }
                            onRequestVpnPermission()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (serviceState.isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(
                        imageVector = if (serviceState.isRunning) Icons.Default.Stop
                        else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (serviceState.isRunning) "DISABLE BLOCKER" else "ENABLE BLOCKER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun stopBlockerService(context: Context) {
    val intent = Intent(context, BlockerService::class.java).apply {
        action = BlockerService.ACTION_STOP
    }
    context.startService(intent)
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
