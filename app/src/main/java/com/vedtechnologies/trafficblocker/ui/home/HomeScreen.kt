package com.vedtechnologies.trafficblocker.ui.home

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedtechnologies.trafficblocker.data.PrefsManager
import com.vedtechnologies.trafficblocker.service.BlockerService
import com.vedtechnologies.trafficblocker.service.BlockerState
import com.vedtechnologies.trafficblocker.ui.theme.Green500
import com.vedtechnologies.trafficblocker.ui.theme.Orange500
import com.vedtechnologies.trafficblocker.ui.theme.Red500
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
    onNavigateToDnsLog: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val serviceState by blockerService?.state?.collectAsState()
        ?: remember { mutableStateOf(BlockerState()) }

    val targetPackages = viewModel.prefs.targetPackages
    val targetAppNames = viewModel.prefs.targetAppNames

    // Re-check usage stats permission when returning from Settings
    var hasUsagePermission by remember { mutableStateOf(viewModel.hasUsageStatsPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = viewModel.hasUsageStatsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

                        var expandedPkg by remember { mutableStateOf<String?>(null) }

                        targetPackages.forEach { pkg ->
                            val appName = targetAppNames[pkg] ?: pkg
                            val mode = viewModel.getAppMode(pkg)
                            val modeLabel = if (mode == PrefsManager.MODE_BLOCK_ALL) "Block All" else "Block Domains"
                            val modeColor = if (mode == PrefsManager.MODE_BLOCK_ALL) Red500 else MaterialTheme.colorScheme.primary
                            val bgBlocking = viewModel.isBackgroundBlocking(pkg)
                            val isExpanded = expandedPkg == pkg

                            // Summary text for collapsed state
                            val summaryParts = mutableListOf(modeLabel)
                            if (bgBlocking) summaryParts.add("BG Blocked")
                            val summaryText = summaryParts.joinToString(" \u00B7 ")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                // Header row — always visible, tap to expand/collapse
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            expandedPkg = if (isExpanded) null else pkg
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
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
                                            text = summaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        if (isExpanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Expanded controls
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 28.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                        AssistChip(
                                            onClick = { viewModel.toggleBackgroundBlocking(pkg) },
                                            label = {
                                                Text(
                                                    text = if (bgBlocking) "BG Blocked" else "BG Allowed",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            leadingIcon = {
                                                if (bgBlocking) {
                                                    Icon(
                                                        Icons.Default.WifiOff,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (bgBlocking) Orange500.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = if (bgBlocking) Orange500
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
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

            // DNS Query Log Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = onNavigateToDnsLog
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DNS Query Log",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "View blocked & allowed queries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Profiles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                onClick = onNavigateToProfiles
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profiles",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Save & switch blocking configurations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            if (!hasUsagePermission) {
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
                            if (!hasUsagePermission) {
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
