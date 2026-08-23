package com.noyorin.balanceisland.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.data.AccountBalanceSettings
import com.noyorin.balanceisland.data.BalanceRepository
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.CredentialSummary
import com.noyorin.balanceisland.data.Provider
import com.noyorin.balanceisland.data.SnapshotStatus
import com.noyorin.balanceisland.device.DeviceProfiles
import com.noyorin.balanceisland.display.DisplayBackendSelector
import com.noyorin.balanceisland.display.OverlayDisplayPreferences
import com.noyorin.balanceisland.display.ProviderDisplayMode
import com.noyorin.balanceisland.display.StatusBarPositionPreset
import com.noyorin.balanceisland.display.StatusBarTextColor
import com.noyorin.balanceisland.display.StatusBarVisualStyle
import com.noyorin.balanceisland.quicksettings.BalanceQuickSettingsTileService
import com.noyorin.balanceisland.service.ServiceRuntimePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) DisplayBackendSelector.fallback.start(this)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = viewModel.loadCached()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BalanceIslandTheme {
                BalanceIslandScreen(
                    viewModel = viewModel,
                    startOverlay = ::startOverlay,
                    stopOverlay = { DisplayBackendSelector.fallback.stop(this) },
                    requestQuickSettingsTile = ::requestQuickSettingsTile
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BalanceRepository.ACTION_BALANCE_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(updateReceiver, filter)
        }
        viewModel.loadCached()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(updateReceiver) }
        super.onStop()
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } else {
            DisplayBackendSelector.fallback.start(this)
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, BalanceQuickSettingsTileService::class.java),
                getString(R.string.quick_settings_tile_label),
                Icon.createWithResource(this, R.drawable.ic_qs_balance),
                mainExecutor
            ) { }
        } else {
            startActivity(Intent(Settings.ACTION_QUICK_SETTINGS_SETTINGS))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceIslandScreen(
    viewModel: MainViewModel,
    startOverlay: () -> Unit,
    stopOverlay: () -> Unit,
    requestQuickSettingsTile: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = remember { DeviceProfiles.current() }
    val preferences = remember { OverlayDisplayPreferences(context) }
    val runtimePreferences = remember { ServiceRuntimePreferences(context) }

    var selectedProviderName by rememberSaveable { mutableStateOf(Provider.DEEPSEEK.name) }
    val selectedProvider = Provider.valueOf(selectedProviderName)
    var accountLabel by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(preferences.mode()) }
    var position by remember { mutableStateOf(preferences.position()) }
    var visualStyle by remember { mutableStateOf(preferences.visualStyle()) }
    var textColor by remember { mutableStateOf(preferences.textColor()) }
    var refreshMinutes by rememberSaveable {
        mutableStateOf(preferences.refreshIntervalMinutes().toString())
    }
    var autoRestart by remember { mutableStateOf(runtimePreferences.autoRestartEnabled()) }
    var horizontalOffset by remember {
        mutableFloatStateOf(preferences.horizontalOffsetDp().toFloat())
    }
    var verticalOffset by remember {
        mutableFloatStateOf(preferences.verticalOffsetDp(profile.defaultYOffsetDp).toFloat())
    }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.credentials) {
        val providers = state.credentials.map { it.provider }.toSet()
        val invalidPin = displayMode.provider?.let { it !in providers } ?: false
        if (invalidPin) {
            displayMode = ProviderDisplayMode.AUTO_CONFIGURED
            preferences.setMode(displayMode)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFF10131A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("状态栏余额", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "8 家服务商 · 后台恢复 · 分级额度告警",
                color = Color(0xFFAAB0BC),
                style = MaterialTheme.typography.bodyMedium
            )

            StatusBarPreview(state.snapshots, visualStyle, textColor)

            ExpandableSection("账户状态", initiallyExpanded = true) {
                if (state.snapshots.isEmpty()) {
                    Text("尚未添加 API 账户。", color = Color(0xFFAAB0BC))
                } else {
                    state.snapshots.forEach { SnapshotRow(it) }
                }
                Button(
                    onClick = { viewModel.refresh() },
                    enabled = !state.refreshing && state.credentials.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(if (state.refreshing) "正在查询" else "立即刷新全部")
                }
            }

            ExpandableSection("API 账户", initiallyExpanded = true) {
                Text(
                    "同一服务商可添加多个 Key；备注为空时自动显示后四位。",
                    color = Color(0xFFAAB0BC),
                    style = MaterialTheme.typography.bodySmall
                )
                Provider.entries.toList().chunked(2).forEach { rowProviders ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowProviders.forEach { provider ->
                            FilterChip(
                                selected = selectedProvider == provider,
                                onClick = { selectedProviderName = provider.name },
                                leadingIcon = { ProviderLogo(provider, 20) },
                                label = { Text(provider.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowProviders.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it.take(12) },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("例如：主账号、测试") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(selectedProvider.keyLabel)
                    },
                    placeholder = { Text(selectedProvider.keyPlaceholder) },
                    supportingText = { Text(selectedProvider.keyHelp) },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showKey = !showKey }, modifier = Modifier.weight(1f)) {
                        Text(if (showKey) "隐藏 Key" else "显示 Key")
                    }
                    Button(
                        onClick = {
                            viewModel.addCredential(selectedProvider, accountLabel, apiKey)
                            accountLabel = ""
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank() && !state.refreshing,
                        modifier = Modifier.weight(1f)
                    ) { Text("添加并测试") }
                }
                state.credentials.forEach { CredentialRow(it, viewModel::removeCredential) }
                Text(
                    "Key 使用 Android Keystore + AES-GCM 加密；界面只显示备注或后四位。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("额度警告与手动余额") {
                if (state.credentials.isEmpty()) {
                    Text("添加 API 账户后可逐个设置。", color = Color(0xFFAAB0BC))
                }
                state.credentials.forEach { summary ->
                    AccountAlertEditor(
                        summary = summary,
                        settings = state.accountSettings[summary.id]
                            ?: AccountBalanceSettings(summary.id),
                        save = viewModel::saveAccountSettings
                    )
                }
                Text(
                    "余额 ≤ 警告线×1.5 时变橙；≤ 警告线时变红并推送。下降提醒默认每 5 个货币单位触发一次。",
                    color = Color(0xFFFFB45C),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("刷新频率与通知") {
                OutlinedTextField(
                    value = refreshMinutes,
                    onValueChange = { refreshMinutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("查询间隔（分钟）") },
                    supportingText = { Text("可设置 1–1440；默认 1 分钟。文字条运行时生效。") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val minutes = refreshMinutes.toIntOrNull()?.coerceIn(1, 1440) ?: 1
                        refreshMinutes = minutes.toString()
                        preferences.setRefreshIntervalMinutes(minutes)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存刷新间隔") }
                Text(
                    "ColorOS 可能限制后台定时；保持状态栏文字条运行并允许后台活动，可获得最稳定的分钟级查询。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("显示账户") {
                ProviderDisplayMode.entries.forEach { mode ->
                    val enabled = mode.provider == null ||
                        state.credentials.any { it.provider == mode.provider }
                    FilterChip(
                        selected = displayMode == mode,
                        onClick = {
                            displayMode = mode
                            preferences.setMode(mode)
                        },
                        enabled = enabled,
                        label = { Text(mode.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "自动模式每 5 秒轮播；点按文字条立即切换账户。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("位置预设") {
                StatusBarPositionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = position == preset,
                        onClick = {
                            position = preset
                            preferences.setPosition(preset)
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("额外左右偏移：${horizontalOffset.toInt()} dp")
                Slider(
                    value = horizontalOffset,
                    onValueChange = { horizontalOffset = it },
                    onValueChangeFinished = {
                        preferences.setHorizontalOffsetDp(horizontalOffset.toInt())
                    },
                    valueRange = 0f..160f
                )
                Text("上下偏移：${verticalOffset.toInt()} dp")
                Slider(
                    value = verticalOffset,
                    onValueChange = { verticalOffset = it },
                    onValueChangeFinished = {
                        preferences.setVerticalOffsetDp(verticalOffset.toInt())
                    },
                    valueRange = 0f..72f
                )
                Text(
                    "PLC110 圆角安全预设自动增加 ${profile.roundedCornerSafeInsetDp}dp 侧边留白。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("文字样式") {
                StatusBarVisualStyle.entries.forEach { style ->
                    FilterChip(
                        selected = visualStyle == style,
                        onClick = {
                            visualStyle = style
                            preferences.setVisualStyle(style)
                        },
                        label = { Text(style.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("正常余额文字颜色")
                StatusBarTextColor.entries.forEach { preset ->
                    FilterChip(
                        selected = textColor == preset,
                        onClick = {
                            textColor = preset
                            preferences.setTextColor(preset)
                        },
                        leadingIcon = {
                            Box(Modifier.size(14.dp).background(Color(preset.argb), CircleShape))
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "警告状态会自动覆盖颜色：临近线为橙色，越线为红色。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("后台运行与控制中心", initiallyExpanded = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("被回收后自动恢复", fontWeight = FontWeight.SemiBold)
                        Text(
                            "记住运行状态；划掉任务、重启手机或应用升级后尝试恢复。",
                            color = Color(0xFF9CA3AF),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = autoRestart,
                        onCheckedChange = {
                            autoRestart = it
                            runtimePreferences.setAutoRestartEnabled(it)
                        }
                    )
                }
                Button(
                    onClick = requestQuickSettingsTile,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("添加“余额监控”控制中心按钮") }
                Text(
                    "磁贴可直接启动/停止文字条。ColorOS 还建议允许本应用自启动、后台活动，并关闭电池优化。",
                    color = Color(0xFFFFB45C),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ExpandableSection("运行", initiallyExpanded = true) {
                Text(
                    "设备：${profile.displayName} · ${DeviceProfiles.softwareLabel()}",
                    color = Color(0xFF73E0C1),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = startOverlay, modifier = Modifier.weight(1f)) { Text("启动文字条") }
                    FilledTonalButton(onClick = stopOverlay, modifier = Modifier.weight(1f)) { Text("停止") }
                }
                Text(
                    "首次启动需授予通知和“显示在其他应用上层”；长按文字条返回设置。",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191D26))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(if (expanded) "▾" else "▸", color = Color(0xFF9CA3AF))
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun AccountAlertEditor(
    summary: CredentialSummary,
    settings: AccountBalanceSettings,
    save: (AccountBalanceSettings) -> Unit
) {
    var enabled by remember(summary.id, settings.alertEnabled) { mutableStateOf(settings.alertEnabled) }
    var warningLine by remember(summary.id, settings.warningLine) { mutableStateOf(settings.warningLine.toString()) }
    var dropStep by remember(summary.id, settings.dropStep) { mutableStateOf(settings.dropStep.toString()) }
    var manualBalance by remember(summary.id, settings.manualBalance) {
        mutableStateOf(settings.manualBalance?.toString().orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11141B), RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(summary.provider, 30)
            Text(
                "${summary.provider.displayName} · ${summary.displayLabel}",
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                fontWeight = FontWeight.SemiBold
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        OutlinedTextField(
            value = warningLine,
            onValueChange = { warningLine = decimalInput(it) },
            label = { Text("警告线") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dropStep,
            onValueChange = { dropStep = decimalInput(it) },
            label = { Text("每下降多少货币单位推送") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = manualBalance,
            onValueChange = { manualBalance = decimalInput(it) },
            label = { Text("手动显示余额（可选）") },
            supportingText = { Text("留空使用 API；填写后按此数值显示和告警。") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                save(
                    AccountBalanceSettings(
                        credentialId = summary.id,
                        alertEnabled = enabled,
                        warningLine = warningLine.toDoubleOrNull() ?: 20.0,
                        dropStep = dropStep.toDoubleOrNull() ?: 5.0,
                        manualBalance = manualBalance.toDoubleOrNull()
                    )
                )
            },
            enabled = (warningLine.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (dropStep.toDoubleOrNull() ?: 0.0) > 0.0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存该账户设置") }
    }
}

@Composable
private fun StatusBarPreview(
    snapshots: List<BalanceSnapshot>,
    visualStyle: StatusBarVisualStyle,
    configuredColor: StatusBarTextColor
) {
    val snapshot = snapshots.firstOrNull()
    val sameProviderCount = snapshot?.let { selected ->
        snapshots.count { it.provider == selected.provider }
    } ?: 0
    val qualifier = if (snapshot != null &&
        (sameProviderCount > 1 || snapshot.accountLabel.isNotBlank())
    ) "［${snapshot.accountDisplayLabel}］" else ""
    val displayColor = when (snapshot?.status) {
        SnapshotStatus.WARNING -> Color(0xFFFFA63D)
        SnapshotStatus.CRITICAL -> Color(0xFFFF5260)
        SnapshotStatus.ERROR -> Color(0xFFFF6470)
        else -> Color(configuredColor.argb)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080A0F), RoundedCornerShape(20.dp))
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .background(
                    if (visualStyle == StatusBarVisualStyle.TRANSLUCENT_PILL) Color(0x99000000)
                    else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (snapshot != null) ProviderLogo(snapshot.provider, 18)
            else Box(Modifier.size(18.dp).background(Color(0xFFFFBE46), CircleShape))
            Text(
                if (snapshot == null) " 请先配置 API" else "$qualifier ${snapshot.primaryText}",
                color = displayColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CredentialRow(summary: CredentialSummary, remove: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11141B), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderLogo(summary.provider, 30)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("${summary.provider.displayName} · ${summary.displayLabel}", fontWeight = FontWeight.SemiBold)
            Text("Key 后四位：${summary.keySuffix}", color = Color(0xFF7E8592), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { remove(summary.id) }) { Text("删除", color = Color(0xFFFF7A86)) }
    }
}

@Composable
private fun SnapshotRow(snapshot: BalanceSnapshot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11141B), RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderLogo(snapshot.provider, 36)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(
                "${snapshot.provider.displayName} · ${snapshot.accountDisplayLabel}  ${snapshot.primaryText}",
                color = snapshotTextColor(snapshot),
                fontWeight = FontWeight.SemiBold
            )
            Text(snapshot.secondaryText, color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
            if (snapshot.updatedAtEpochMillis > 0) {
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(snapshot.updatedAtEpochMillis)),
                    color = Color(0xFF6F7684),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Box(Modifier.size(9.dp).background(statusColor(snapshot.status), CircleShape))
    }
}

@Composable
private fun ProviderLogo(provider: Provider, sizeDp: Int) {
    val needsDarkBackground = provider == Provider.OPENAI || provider == Provider.XAI
    val needsLightBackground = provider == Provider.MOONSHOT
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .then(
                if (needsDarkBackground) {
                    Modifier.background(Color(0xFF16191F), CircleShape).padding(3.dp)
                } else if (needsLightBackground) {
                    Modifier.background(Color.White, CircleShape).padding(3.dp)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                when (provider) {
                    Provider.DEEPSEEK -> R.drawable.ic_provider_deepseek
                    Provider.OPENAI -> R.drawable.ic_provider_openai
                    Provider.OPENROUTER -> R.drawable.ic_provider_openrouter
                    Provider.SILICONFLOW -> R.drawable.ic_provider_siliconflow
                    Provider.MOONSHOT -> R.drawable.ic_provider_kimi
                    Provider.ANTHROPIC -> R.drawable.ic_provider_anthropic
                    Provider.GEMINI -> R.drawable.ic_provider_gemini
                    Provider.XAI -> R.drawable.ic_provider_xai
                }
            ),
            contentDescription = provider.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

private fun decimalInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered.take(10)
    else filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).replace(".", "").take(2)
}

private fun snapshotTextColor(snapshot: BalanceSnapshot): Color = when (snapshot.status) {
    SnapshotStatus.WARNING -> Color(0xFFFFA63D)
    SnapshotStatus.CRITICAL -> Color(0xFFFF5260)
    SnapshotStatus.ERROR -> Color(0xFFFF6470)
    else -> Color(0xFFF1F3F7)
}

private fun statusColor(status: SnapshotStatus) = when (status) {
    SnapshotStatus.OK -> Color(0xFF51DC93)
    SnapshotStatus.WARNING -> Color(0xFFFFA63D)
    SnapshotStatus.CRITICAL -> Color(0xFFFF5260)
    SnapshotStatus.ERROR -> Color(0xFFFF6470)
    SnapshotStatus.NOT_CONFIGURED -> Color(0xFF8C919B)
}

@Composable
private fun BalanceIslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF73E0C1),
            onPrimary = Color(0xFF052019),
            secondary = Color(0xFF68B4FF),
            background = Color(0xFF10131A),
            surface = Color(0xFF191D26),
            onSurface = Color(0xFFF1F3F7)
        ),
        content = content
    )
}
