package com.vedtechnologies.trafficblocker.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedtechnologies.trafficblocker.data.PrefsManager
import com.vedtechnologies.trafficblocker.ui.theme.Green500
import com.vedtechnologies.trafficblocker.ui.theme.Red500
import com.vedtechnologies.trafficblocker.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(viewModel.prefs.autoStartOnBoot) }
    var pollInterval by remember { mutableLongStateOf(viewModel.prefs.pollIntervalMs) }
    var upstreamDns by remember { mutableStateOf(viewModel.prefs.upstreamDns) }

    // Re-check permissions every time this screen is (re)composed / resumed
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasUsageStats by remember { mutableStateOf(viewModel.hasUsageStatsPermission()) }
    var hasBatteryExempt by remember { mutableStateOf(viewModel.isBatteryOptimizationExempt()) }
    var hasVpn by remember { mutableStateOf(viewModel.hasVpnPermission()) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsageStats = viewModel.hasUsageStatsPermission()
                hasBatteryExempt = viewModel.isBatteryOptimizationExempt()
                hasVpn = viewModel.hasVpnPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val themes = listOf(
                        PrefsManager.THEME_SYSTEM to "System default",
                        PrefsManager.THEME_LIGHT to "Light",
                        PrefsManager.THEME_DARK to "Dark"
                    )
                    themes.forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = ThemeState.themeMode == mode,
                                onClick = {
                                    ThemeState.themeMode = mode
                                    viewModel.prefs.themeMode = mode
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Auto-start toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-start on boot",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Resume blocking after device restarts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            viewModel.prefs.autoStartOnBoot = it
                        }
                    )
                }
            }

            // Poll interval
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Poll interval",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "How often to check for foreground app changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val intervals = listOf(500L to "500ms (faster)", 1000L to "1s (default)", 2000L to "2s (battery saver)")
                    intervals.forEach { (ms, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = pollInterval == ms,
                                onClick = {
                                    pollInterval = ms
                                    viewModel.prefs.pollIntervalMs = ms
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Upstream DNS
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Upstream DNS",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Server used to resolve unblocked domains. Takes effect on next service start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val dnsOptions = listOf(
                        PrefsManager.DNS_GOOGLE to "Google (8.8.8.8)",
                        PrefsManager.DNS_CLOUDFLARE to "Cloudflare (1.1.1.1)",
                        PrefsManager.DNS_QUAD9 to "Quad9 (9.9.9.9)"
                    )
                    dnsOptions.forEach { (ip, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = upstreamDns == ip,
                                onClick = {
                                    upstreamDns = ip
                                    viewModel.prefs.upstreamDns = ip
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Permissions Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(name = "VPN", granted = hasVpn)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionRow(name = "Usage Stats", granted = hasUsageStats)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionRow(
                        name = "Battery Optimization exempt",
                        granted = hasBatteryExempt
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!hasUsageStats || !hasBatteryExempt) {
                        Button(
                            onClick = {
                                // Open each missing permission screen
                                if (!hasUsageStats) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    )
                                }
                                if (!hasBatteryExempt) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Request Missing Permissions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (granted) Green500 else Red500,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
