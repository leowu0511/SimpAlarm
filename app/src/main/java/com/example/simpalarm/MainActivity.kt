package com.example.simpalarm

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.simpalarm.ui.theme.SimpAlarmTheme

private val BlushBackground = Color(0xFFFBE4E2)
private val DarkBackground = Color(0xFF1F171B)
private val CardPink = Color(0xFFFFF7F7)
private val DarkCard = Color(0xFF2A2025)
private val WineText = Color(0xFF4A1111)
private val DarkText = Color(0xFFFFF7F7)
private val MutedWine = Color(0xFF7B5151)
private val DarkMuted = Color(0xFFD4C5CA)
private val PrimaryPink = Color(0xFFF43F68)
private val PrimaryBlue = Color(0xFF55689E)
private val SoftBorder = Color(0xFFC6BFD0)
private val SuccessGreen = Color(0xFF246B45)
private val WarningOrange = Color(0xFFB56A16)
private val DangerRed = Color(0xFFD92845)

private enum class PermissionPrompt {
    NotificationListener,
    BatteryOptimization,
    FullScreenIntent
}

private enum class OnboardingStep {
    SelectApps,
    AddTarget,
    NotificationListener,
    AlarmNotification,
    FullScreenIntent,
    BatteryOptimization,
    TestAlarm,
    Done
}

private enum class AppScreen {
    Home,
    Targets,
    Apps,
    History,
    Settings,
    TargetDetail
}

private fun AppScreen.animationIndex(): Int {
    return when (this) {
        AppScreen.Home -> 0
        AppScreen.Targets -> 1
        AppScreen.TargetDetail -> 2
        AppScreen.Apps -> 3
        AppScreen.History -> 4
        AppScreen.Settings -> 5
    }
}

private enum class TargetFilter {
    All,
    Enabled,
    Starred
}

private const val AUTO_RECONNECT_INTERVAL_MS = 30_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpAlarmTheme(dynamicColor = false) {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val onboardPrefs = remember {
        context.getSharedPreferences("simp_onboarding", Context.MODE_PRIVATE)
    }
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var targetFilter by remember { mutableStateOf(TargetFilter.All) }
    var historyFilter by remember { mutableStateOf<String?>(null) }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    var editingTarget by remember { mutableStateOf<SimpTarget?>(null) }
    var deletingTarget by remember { mutableStateOf<SimpTarget?>(null) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(onboardPrefs.getBoolean("done", false)) }
    var showOnboarding by remember { mutableStateOf(!onboardingDone) }
    var onboardingBatterySkipped by remember { mutableStateOf(onboardPrefs.getBoolean("batterySkipped", false)) }
    var onboardingTestDone by remember { mutableStateOf(onboardPrefs.getBoolean("testDone", false)) }
    var darkMode by remember { mutableStateOf(false) }

    var targets by remember { mutableStateOf(SimpTargetManager.getTargetItems(context)) }
    var triggerMode by remember { mutableStateOf(SimpTargetManager.getTriggerMode(context)) }
    var alarmPresentationMode by remember {
        mutableStateOf(SimpTargetManager.getAlarmPresentationMode(context))
    }
    var monitoredApps by remember { mutableStateOf(SimpTargetManager.getMonitoredApps(context)) }
    var history by remember { mutableStateOf(SimpEventLog.triggerHistory(context)) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var alarmNotificationAllowed by remember { mutableStateOf(isAlarmNotificationAllowed(context)) }
    var fullScreenIntentAllowed by remember { mutableStateOf(isFullScreenIntentAllowed(context)) }
    var overlayAllowed by remember { mutableStateOf(SimpAlarmOverlay.canShow(context)) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimizationActive(context)) }
    var powerSaveMode by remember { mutableStateOf(isPowerSaveModeActive(context)) }
    var lastEvent by remember { mutableStateOf(SimpEventLog.lastEvent(context)) }
    var pendingPrompt by remember { mutableStateOf<PermissionPrompt?>(null) }
    var requestedNotificationPermission by remember { mutableStateOf(false) }
    var skippedListenerPrompt by remember { mutableStateOf(false) }
    var skippedFullScreenPrompt by remember { mutableStateOf(false) }
    var skippedBatteryPrompt by remember { mutableStateOf(false) }
    var lastBackPressedAt by remember { mutableStateOf(0L) }

    val colors = UiColors(darkMode)
    val scrollState = rememberScrollState()
    val scrollViewportTop = remember { mutableFloatStateOf(0f) }
    val scrollViewportBottom = remember { mutableFloatStateOf(0f) }

    fun refreshAppState() {
        val currentListenerEnabled = isNotificationListenerEnabled(context)
        val currentTargets = SimpTargetManager.getTargetItems(context)
        val currentMonitoredApps = SimpTargetManager.getMonitoredApps(context)
        targets = currentTargets
        triggerMode = SimpTargetManager.getTriggerMode(context)
        alarmPresentationMode = SimpTargetManager.getAlarmPresentationMode(context)
        monitoredApps = currentMonitoredApps
        history = SimpEventLog.triggerHistory(context)
        listenerEnabled = currentListenerEnabled
        alarmNotificationAllowed = isAlarmNotificationAllowed(context)
        fullScreenIntentAllowed = isFullScreenIntentAllowed(context)
        overlayAllowed = SimpAlarmOverlay.canShow(context)
        batteryOptimized = isBatteryOptimizationActive(context)
        powerSaveMode = isPowerSaveModeActive(context)
        lastEvent = SimpEventLog.lastEvent(context)
        syncListenerHeartbeat(context, currentListenerEnabled)
    }

    fun currentOnboardingStep(): OnboardingStep {
        return when {
            monitoredApps.isEmpty() -> OnboardingStep.SelectApps
            targets.isEmpty() -> OnboardingStep.AddTarget
            !listenerEnabled -> OnboardingStep.NotificationListener
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !alarmNotificationAllowed -> OnboardingStep.AlarmNotification
            !fullScreenIntentAllowed -> OnboardingStep.FullScreenIntent
            batteryOptimized && !onboardingBatterySkipped -> OnboardingStep.BatteryOptimization
            !onboardingTestDone -> OnboardingStep.TestAlarm
            else -> OnboardingStep.Done
        }
    }

    fun finishOnboarding() {
        onboardingDone = true
        showOnboarding = false
        onboardPrefs.edit().putBoolean("done", true).apply()
    }

    fun openOnboarding() {
        onboardingDone = false
        showOnboarding = true
        onboardPrefs.edit().putBoolean("done", false).apply()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        alarmNotificationAllowed = granted || isAlarmNotificationAllowed(context)
    }

    BackHandler {
        when {
            showTargetDialog -> showTargetDialog = false
            deletingTarget != null -> deletingTarget = null
            showClearHistoryDialog -> showClearHistoryDialog = false
            showOnboarding -> showOnboarding = false
            pendingPrompt != null -> pendingPrompt = null
            screen == AppScreen.TargetDetail -> screen = AppScreen.Targets
            screen != AppScreen.Home -> {
                screen = AppScreen.Home
                lastBackPressedAt = 0L
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt <= 2_000L) {
                    activity?.finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(context, "再按一次返回即可退出應用。", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        requestNotificationListenerReconnectSilently(context)
        refreshAppState()
    }

    LaunchedEffect(listenerEnabled) {
        if (listenerEnabled) {
            while (true) {
                requestNotificationListenerReconnectSilently(context)
                delay(60_000L)
            }
        }
    }

    LaunchedEffect(
        onboardingDone,
        showTargetDialog,
        targets,
        monitoredApps,
        listenerEnabled,
        alarmNotificationAllowed,
        fullScreenIntentAllowed,
        batteryOptimized,
        onboardingBatterySkipped,
        onboardingTestDone
    ) {
        if (!onboardingDone && !showTargetDialog) {
            showOnboarding = true
        }
    }

    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                requestNotificationListenerReconnectSilently(context)
                refreshAppState()
                if (!onboardingDone && !showTargetDialog) {
                    showOnboarding = true
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(alarmNotificationAllowed, fullScreenIntentAllowed, listenerEnabled, batteryOptimized, pendingPrompt, onboardingDone) {
        if (!onboardingDone) return@LaunchedEffect
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !alarmNotificationAllowed &&
            !requestedNotificationPermission
        ) {
            requestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return@LaunchedEffect
        }

        if (!listenerEnabled && !skippedListenerPrompt) {
            pendingPrompt = PermissionPrompt.NotificationListener
            return@LaunchedEffect
        }

        if (!fullScreenIntentAllowed && !skippedFullScreenPrompt) {
            pendingPrompt = PermissionPrompt.FullScreenIntent
            return@LaunchedEffect
        }

        if (batteryOptimized && !skippedBatteryPrompt) {
            pendingPrompt = PermissionPrompt.BatteryOptimization
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        scrollViewportTop.floatValue = pos.y
                        scrollViewportBottom.floatValue = pos.y + coords.size.height
                    }
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.animationIndex() >= initialState.animationIndex()
                        val slideDistance = 52
                        val enterOffset = if (forward) slideDistance else -slideDistance
                        val exitOffset = if (forward) -slideDistance else slideDistance
                        (fadeIn(animationSpec = tween(160)) +
                            slideInHorizontally(animationSpec = tween(220)) { enterOffset })
                            .togetherWith(
                                fadeOut(animationSpec = tween(120)) +
                                    slideOutHorizontally(animationSpec = tween(180)) { exitOffset }
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "screen_transition"
                ) { targetScreen ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    when (targetScreen) {
                        AppScreen.Home -> HomeScreen(
                        colors = colors,
                        targets = targets,
                        monitoredApps = monitoredApps,
                        triggerMode = triggerMode,
                        alarmPresentationMode = alarmPresentationMode,
                        listenerEnabled = listenerEnabled,
                        alarmNotificationAllowed = alarmNotificationAllowed,
                        fullScreenIntentAllowed = fullScreenIntentAllowed,
                        overlayAllowed = overlayAllowed,
                        batteryOptimized = batteryOptimized,
                        powerSaveMode = powerSaveMode,
                        lastEvent = lastEvent,
                        onNavigate = { screen = it },
                        onRefresh = { refreshAppState() },
                        onReconnect = {
                            requestNotificationListenerReconnect(context)
                            refreshAppState()
                        },
                        onTest = {
                            startTestAlarm(context)
                            refreshAppState()
                        },
                        onOpenPermissions = { screen = AppScreen.Settings },
                        onOpenOnboarding = { openOnboarding() }
                    )

                    AppScreen.Targets -> TargetsScreen(
                        colors = colors,
                        targets = targets,
                        targetFilter = targetFilter,
                        triggerMode = triggerMode,
                        scrollState = scrollState,
                        viewportBounds = {
                            scrollViewportTop.floatValue to scrollViewportBottom.floatValue
                        },
                        onFilterChange = { targetFilter = it },
                        onAdd = {
                            editingTarget = null
                            showTargetDialog = true
                        },
                        onEdit = {
                            editingTarget = it
                            showTargetDialog = true
                        },
                        onOpenDetail = {
                            selectedTargetId = it.id
                            screen = AppScreen.TargetDetail
                        },
                        onToggleEnabled = { target, enabled ->
                            SimpTargetManager.setTargetEnabled(context, target.id, enabled)
                            refreshAppState()
                        },
                        onToggleStar = { target ->
                            SimpTargetManager.setTargetContinuousOverride(context, target.id, !target.continuousOverride)
                            refreshAppState()
                        },
                        onTogglePin = { target ->
                            SimpTargetManager.setTargetPinned(context, target.id, !target.pinned)
                            refreshAppState()
                        },
                        onReorder = { orderedTargetIds ->
                            SimpTargetManager.reorderTargets(context, orderedTargetIds)
                            refreshAppState()
                        },
                        onDelete = { deletingTarget = it }
                    )

                    AppScreen.Apps -> AppsScreen(
                        colors = colors,
                        monitoredApps = monitoredApps,
                        onAppEnabledChange = { packageName, enabled ->
                            SimpTargetManager.setMonitoredAppEnabled(context, packageName, enabled)
                            refreshAppState()
                        }
                    )

                    AppScreen.History -> HistoryScreen(
                        colors = colors,
                        history = history,
                        targets = targets,
                        historyFilter = historyFilter,
                        onFilterChange = { historyFilter = it },
                        onOpenTarget = { targetId ->
                            selectedTargetId = targetId
                            screen = AppScreen.TargetDetail
                        },
                        onClearHistory = { showClearHistoryDialog = true },
                        onTest = {
                            startTestAlarm(context)
                            refreshAppState()
                        }
                    )

                    AppScreen.Settings -> SettingsScreen(
                        colors = colors,
                        triggerMode = triggerMode,
                        alarmPresentationMode = alarmPresentationMode,
                        darkMode = darkMode,
                        listenerEnabled = listenerEnabled,
                        alarmNotificationAllowed = alarmNotificationAllowed,
                        fullScreenIntentAllowed = fullScreenIntentAllowed,
                        overlayAllowed = overlayAllowed,
                        batteryOptimized = batteryOptimized,
                        powerSaveMode = powerSaveMode,
                        onTriggerModeChange = { mode ->
                            SimpTargetManager.setTriggerMode(context, mode)
                            refreshAppState()
                        },
                        onAlarmPresentationModeChange = { mode ->
                            SimpTargetManager.setAlarmPresentationMode(context, mode)
                            refreshAppState()
                        },
                        onDarkModeChange = { darkMode = it },
                        onOpenHistory = { screen = AppScreen.History },
                        onOpenOnboarding = { openOnboarding() },
                        onTest = {
                            startTestAlarm(context)
                            refreshAppState()
                        },
                        onOpenListenerSettings = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onRequestNotificationPermission = {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !alarmNotificationAllowed &&
                                !requestedNotificationPermission
                            ) {
                                requestedNotificationPermission = true
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        },
                        onOpenFullScreenIntentSettings = { openFullScreenIntentSettings(context) },
                        onOpenOverlaySettings = { openOverlaySettings(context) },
                        onOpenBatterySettings = { requestIgnoreBatteryOptimization(context) }
                    )

                    AppScreen.TargetDetail -> TargetDetailScreen(
                        colors = colors,
                        target = targets.firstOrNull { it.id == selectedTargetId },
                        history = history.filter { it.targetId == selectedTargetId },
                        triggerMode = triggerMode,
                        onBack = { screen = AppScreen.Targets },
                        onEdit = { target ->
                            editingTarget = target
                            showTargetDialog = true
                        },
                        onToggleEnabled = { target, enabled ->
                            SimpTargetManager.setTargetEnabled(context, target.id, enabled)
                            refreshAppState()
                        },
                        onToggleStar = { target ->
                            SimpTargetManager.setTargetContinuousOverride(context, target.id, !target.continuousOverride)
                            refreshAppState()
                        },
                        onDelete = { deletingTarget = it },
                        onTest = {
                            startTestAlarm(context)
                            refreshAppState()
                        }
                    )
                }
            }
            }
            }
            BottomNav(colors = colors, screen = screen) { screen = it }
        }

        PermissionGuideDialog(
            colors = colors,
            prompt = pendingPrompt,
            onDismiss = {
                when (pendingPrompt) {
                    PermissionPrompt.NotificationListener -> skippedListenerPrompt = true
                    PermissionPrompt.BatteryOptimization -> skippedBatteryPrompt = true
                    PermissionPrompt.FullScreenIntent -> skippedFullScreenPrompt = true
                    null -> Unit
                }
                pendingPrompt = null
            },
            onOpenListenerSettings = {
                skippedListenerPrompt = true
                pendingPrompt = null
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onOpenFullScreenIntentSettings = {
                skippedFullScreenPrompt = true
                pendingPrompt = null
                openFullScreenIntentSettings(context)
            },
            onRequestBatteryOptimization = {
                skippedBatteryPrompt = true
                pendingPrompt = null
                requestIgnoreBatteryOptimization(context)
            }
        )

        if (showTargetDialog) {
            TargetEditDialog(
                colors = colors,
                target = editingTarget,
                onDismiss = { showTargetDialog = false },
                onSave = { displayName, notificationNames, photoUri ->
                    val target = editingTarget
                    if (target == null) {
                        SimpTargetManager.addTarget(context, displayName, notificationNames, photoUri)
                    } else {
                        SimpTargetManager.updateTarget(context, target.id, displayName, notificationNames, photoUri)
                    }
                    showTargetDialog = false
                    refreshAppState()
                }
            )
        }

        deletingTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deletingTarget = null },
                title = { Text("刪除監聽對象？") },
                text = {
                    Text("確定要刪除「${target.displayName}」嗎？這會移除顯示名稱、通知 ID、頭像與監聽狀態。")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            SimpTargetManager.removeTarget(context, target.id)
                            deletingTarget = null
                            if (screen == AppScreen.TargetDetail) screen = AppScreen.Targets
                            refreshAppState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("確認刪除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingTarget = null }) {
                        Text("取消", color = colors.primary)
                    }
                },
                containerColor = colors.card,
                titleContentColor = colors.text,
                textContentColor = colors.muted
            )
        }

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("清除觸發紀錄？") },
                text = { Text("這會移除目前所有觸發紀錄，對象設定不會受到影響。") },
                confirmButton = {
                    Button(
                        onClick = {
                            SimpEventLog.clearTriggerHistory(context)
                            showClearHistoryDialog = false
                            refreshAppState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("確認清除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("取消", color = colors.primary)
                    }
                },
                containerColor = colors.card,
                titleContentColor = colors.text,
                textContentColor = colors.muted
            )
        }

        if (showOnboarding) {
            OnboardingDialog(
                colors = colors,
                currentStep = currentOnboardingStep(),
                monitoredAppsReady = monitoredApps.isNotEmpty(),
                targetReady = targets.isNotEmpty(),
                listenerReady = listenerEnabled,
                alarmNotificationReady = alarmNotificationAllowed,
                fullScreenReady = fullScreenIntentAllowed,
                batteryReady = !batteryOptimized || onboardingBatterySkipped,
                testReady = onboardingTestDone,
                onSkip = {
                    finishOnboarding()
                },
                onDone = {
                    finishOnboarding()
                },
                onAction = { step ->
                    when (step) {
                        OnboardingStep.SelectApps -> {
                            screen = AppScreen.Apps
                            showOnboarding = false
                        }
                        OnboardingStep.AddTarget -> {
                            editingTarget = null
                            showTargetDialog = true
                            showOnboarding = false
                        }
                        OnboardingStep.NotificationListener -> {
                            showOnboarding = false
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                        OnboardingStep.AlarmNotification -> {
                            showOnboarding = false
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !alarmNotificationAllowed
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        }
                        OnboardingStep.FullScreenIntent -> {
                            showOnboarding = false
                            openFullScreenIntentSettings(context)
                        }
                        OnboardingStep.BatteryOptimization -> {
                            showOnboarding = false
                            requestIgnoreBatteryOptimization(context)
                        }
                        OnboardingStep.TestAlarm -> {
                            startTestAlarm(context)
                            onboardingTestDone = true
                            onboardPrefs.edit().putBoolean("testDone", true).apply()
                        }
                        OnboardingStep.Done -> {
                            finishOnboarding()
                        }
                    }
                },
                onSkipBattery = {
                    onboardingBatterySkipped = true
                    onboardPrefs.edit().putBoolean("batterySkipped", true).apply()
                }
            )
        }
    }
}

private data class UiColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val border: Color
)

private fun UiColors(dark: Boolean): UiColors {
    return if (dark) {
        UiColors(DarkBackground, DarkCard, DarkText, DarkMuted, PrimaryPink, Color(0xFF70444F))
    } else {
        UiColors(BlushBackground, CardPink, WineText, MutedWine, PrimaryPink, SoftBorder)
    }
}

@Composable
private fun Header(colors: UiColors, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = colors.text)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
    }
}

@Composable
private fun TopBar(
    colors: UiColors,
    title: String,
    leading: String? = null,
    trailing: String? = null,
    onLeading: () -> Unit = {},
    onTrailing: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (leading == null) Spacer(modifier = Modifier.size(48.dp)) else TextButton(onClick = onLeading) { Text(leading, color = colors.text) }
        Text(title, color = colors.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (trailing == null) Spacer(modifier = Modifier.size(48.dp)) else TextButton(onClick = onTrailing) { Text(trailing, color = colors.primary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AppCard(colors: UiColors, elevation: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun SectionTitle(colors: UiColors, text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.text)
}

@Composable
private fun StatButton(colors: UiColors, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.background.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = colors.muted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = colors.text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickButton(colors: UiColors, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = colors.text, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> Segmented(colors: UiColors, items: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (value, label) ->
            ModeButton(Modifier.weight(1f), value == selected, label, colors) { onSelected(value) }
        }
    }
}

@Composable
private fun ModeButton(modifier: Modifier, selected: Boolean, text: String, colors: UiColors, onClick: () -> Unit) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
            Text(text)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick, border = BorderStroke(1.dp, colors.border)) {
            Text(text, color = colors.primary)
        }
    }
}

@Composable
private fun EmptyState(colors: UiColors, title: String, detail: String, action: String, onAction: () -> Unit) {
    AppCard(colors) {
        Text(title, color = colors.text, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.muted)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
            Text(action)
        }
    }
}

@Composable
private fun TargetAvatar(colors: UiColors, target: SimpTarget, sizeDp: Int) {
    val context = LocalContext.current
    val bitmap = remember(target.photoUri) {
        target.photoUri?.let { uriString ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            GeometricAvatar(target.avatarSeed, colors)
        }
    }
}

@Composable
private fun GeometricAvatar(seed: Int, colors: UiColors) {
    val random = remember(seed) { Random(seed) }
    val palette = listOf(
        listOf(Color(0xFFFFE3E8), PrimaryPink, Color(0xFFFF8AA0)),
        listOf(Color(0xFFE9EFFF), PrimaryBlue, Color(0xFF8CA0DF)),
        listOf(Color(0xFFE8FFF3), Color(0xFF1F9D66), Color(0xFF7DD9AB)),
        listOf(Color(0xFFFFF1D8), Color(0xFFD9822B), Color(0xFFF7BD6D)),
        listOf(Color(0xFFF2E8FF), Color(0xFF8B5CF6), Color(0xFFC4A8FF)),
        listOf(Color(0xFFE6FAFF), Color(0xFF0891B2), Color(0xFF67E8F9)),
        listOf(Color(0xFFFFF7D6), Color(0xFFEAB308), Color(0xFFFDE047)),
        listOf(Color(0xFFFFE7F5), Color(0xFFDB2777), Color(0xFFF9A8D4))
    )[seed.mod(8)]
    val template = seed.mod(6)
    Canvas(modifier = Modifier.fillMaxSize()) {
        fun f(min: Float, max: Float) = min + random.nextFloat() * (max - min)
        val w = size.width
        val h = size.height
        val d = size.minDimension
        drawCircle(palette[0], radius = d / 2f, center = Offset(w / 2f, h / 2f))
        when (template) {
            0 -> {
                drawCircle(palette[1], radius = d * f(0.18f, 0.28f), center = Offset(w * f(0.28f, 0.42f), h * f(0.25f, 0.45f)))
                drawCircle(palette[2], radius = d * f(0.12f, 0.20f), center = Offset(w * f(0.55f, 0.74f), h * f(0.42f, 0.64f)))
                drawRoundRect(colors.card.copy(alpha = 0.70f), topLeft = Offset(w * f(0.20f, 0.34f), h * f(0.64f, 0.76f)), size = Size(w * f(0.34f, 0.58f), h * f(0.10f, 0.18f)))
            }
            1 -> {
                drawOval(palette[1], topLeft = Offset(w * f(0.20f, 0.36f), h * f(0.20f, 0.34f)), size = Size(w * f(0.34f, 0.50f), h * f(0.28f, 0.44f)))
                drawCircle(palette[2], radius = d * f(0.11f, 0.18f), center = Offset(w * f(0.58f, 0.74f), h * f(0.28f, 0.48f)))
                drawRoundRect(colors.card.copy(alpha = 0.72f), topLeft = Offset(w * f(0.22f, 0.40f), h * f(0.62f, 0.76f)), size = Size(w * f(0.26f, 0.48f), h * f(0.12f, 0.22f)))
            }
            2 -> {
                drawRoundRect(palette[1], topLeft = Offset(w * f(0.22f, 0.36f), h * f(0.24f, 0.40f)), size = Size(w * f(0.30f, 0.48f), h * f(0.30f, 0.48f)))
                drawCircle(palette[2], radius = d * f(0.10f, 0.17f), center = Offset(w * f(0.55f, 0.76f), h * f(0.56f, 0.72f)))
                drawOval(colors.card.copy(alpha = 0.72f), topLeft = Offset(w * f(0.18f, 0.32f), h * f(0.64f, 0.78f)), size = Size(w * f(0.42f, 0.62f), h * f(0.10f, 0.18f)))
            }
            3 -> {
                drawCircle(palette[1], radius = d * f(0.13f, 0.22f), center = Offset(w * f(0.30f, 0.42f), h * f(0.55f, 0.72f)))
                drawOval(palette[2], topLeft = Offset(w * f(0.44f, 0.58f), h * f(0.22f, 0.38f)), size = Size(w * f(0.22f, 0.36f), h * f(0.40f, 0.58f)))
                drawCircle(colors.card.copy(alpha = 0.75f), radius = d * f(0.08f, 0.14f), center = Offset(w * f(0.46f, 0.58f), h * f(0.42f, 0.58f)))
            }
            4 -> {
                drawRoundRect(palette[1], topLeft = Offset(w * f(0.18f, 0.30f), h * f(0.46f, 0.62f)), size = Size(w * f(0.44f, 0.62f), h * f(0.18f, 0.30f)))
                drawCircle(palette[2], radius = d * f(0.16f, 0.24f), center = Offset(w * f(0.42f, 0.60f), h * f(0.30f, 0.46f)))
                drawCircle(colors.card.copy(alpha = 0.72f), radius = d * f(0.08f, 0.13f), center = Offset(w * f(0.22f, 0.36f), h * f(0.30f, 0.48f)))
            }
            else -> {
                drawOval(palette[1], topLeft = Offset(w * f(0.18f, 0.32f), h * f(0.32f, 0.52f)), size = Size(w * f(0.48f, 0.66f), h * f(0.24f, 0.38f)))
                drawRoundRect(palette[2], topLeft = Offset(w * f(0.42f, 0.58f), h * f(0.20f, 0.34f)), size = Size(w * f(0.22f, 0.34f), h * f(0.22f, 0.34f)))
                drawCircle(colors.card.copy(alpha = 0.75f), radius = d * f(0.09f, 0.15f), center = Offset(w * f(0.30f, 0.46f), h * f(0.62f, 0.76f)))
            }
        }
    }
}

@Composable
private fun BottomNav(colors: UiColors, screen: AppScreen, onNavigate: (AppScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                AppScreen.Home to "首頁",
                AppScreen.Targets to "對象",
                AppScreen.Apps to "應用",
                AppScreen.History to "紀錄",
                AppScreen.Settings to "設定"
            ).forEach { (targetScreen, label) ->
                val selected = screen == targetScreen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onNavigate(targetScreen) }
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 3.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) colors.primary else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = label,
                        color = if (selected) colors.primary else colors.muted,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun computeDropIndex(
    orderedTargets: List<SimpTarget>,
    rowHeights: Map<String, Int>,
    fallbackPx: Int,
    draggedId: String?,
    startIndex: Int,
    offsetY: Float
): Int {
    if (draggedId == null) return startIndex
    val refH = rowHeights[draggedId] ?: fallbackPx
    val heights = orderedTargets.map { rowHeights[it.id] ?: refH }
    if (heights.isEmpty()) return startIndex
    val draggedH = heights.getOrNull(startIndex) ?: refH
    val startTop = heights.subList(0, startIndex).sum()
    val draggedCenter = startTop + draggedH / 2f + offsetY
    var acc = 0
    var idx = 0
    for (i in heights.indices) {
        val h = heights[i]
        if (draggedCenter < acc + h) {
            idx = i
            break
        }
        acc += h
        idx = i
    }
    val draggedTarget = orderedTargets.firstOrNull { it.id == draggedId }
    val pinnedCount = orderedTargets.count { it.pinned }
    val groupStart: Int
    val groupEnd: Int
    if (draggedTarget?.pinned == true) {
        groupStart = 0
        groupEnd = (pinnedCount - 1).coerceAtLeast(0)
    } else {
        groupStart = pinnedCount
        groupEnd = orderedTargets.lastIndex
    }
    return idx.coerceIn(groupStart, groupEnd)
}

private fun targetStatusLabel(target: SimpTarget, triggerMode: TriggerMode): String {
    if (!target.enabled) return "已關閉"
    if (target.continuousOverride) return "持續監聽"
    return if (triggerMode == TriggerMode.Once) "單次模式" else "持續監聽"
}

@Composable
private fun PermissionSummaryCard(
    colors: UiColors,
    listenerEnabled: Boolean,
    alarmNotificationAllowed: Boolean,
    fullScreenIntentAllowed: Boolean,
    overlayAllowed: Boolean,
    batteryOptimized: Boolean,
    powerSaveMode: Boolean,
    onClick: () -> Unit
) {
    val missingRequired = !listenerEnabled || !alarmNotificationAllowed
    val missingRecommended = !fullScreenIntentAllowed || !overlayAllowed || batteryOptimized || powerSaveMode
    val statusColor = when {
        missingRequired -> DangerRed
        missingRecommended -> WarningOrange
        else -> SuccessGreen
    }
    AppCard(colors) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("權限狀態提醒", color = statusColor, fontWeight = FontWeight.Bold)
                Text(
                    text = when {
                        missingRequired -> "必要權限尚未完成，鬧鐘可能無法觸發。"
                        missingRecommended -> "必要權限已完成，建議確認背景與全螢幕設定。"
                        else -> "必要權限都準備好了。"
                    },
                    color = colors.muted
                )
            }
            TextButton(onClick = onClick) { Text("檢查", color = colors.primary) }
        }
    }
}

@Composable
private fun PermissionCard(
    colors: UiColors,
    listenerEnabled: Boolean,
    alarmNotificationAllowed: Boolean,
    fullScreenIntentAllowed: Boolean,
    overlayAllowed: Boolean,
    batteryOptimized: Boolean,
    powerSaveMode: Boolean,
    onOpenListenerSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val items = listOf(
        PermissionUiItem("通知監聽", if (listenerEnabled) "已啟用" else "未啟用", listenerEnabled, true, onOpenListenerSettings),
        PermissionUiItem("鬧鐘通知", if (alarmNotificationAllowed) "已允許" else "未允許", alarmNotificationAllowed, true, onRequestNotificationPermission),
        PermissionUiItem("全螢幕鬧鐘", if (fullScreenIntentAllowed) "已允許" else "未允許", fullScreenIntentAllowed, false, onOpenFullScreenIntentSettings),
        PermissionUiItem("浮動鬧鐘", if (overlayAllowed) "已允許" else "未允許", overlayAllowed, false, onOpenOverlaySettings),
        PermissionUiItem("電池最佳化", if (batteryOptimized) "未允許" else "已允許", !batteryOptimized, false, onOpenBatterySettings),
        PermissionUiItem("省電模式", if (powerSaveMode) "已開啟" else "未開啟", !powerSaveMode, false, onOpenBatterySettings)
    )
    val visibleItems = if (showAll) items else items.filterNot { it.isOk }
    AppCard(colors) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(colors, "權限檢查")
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "收合" else "顯示全部", color = colors.primary)
            }
        }
        if (visibleItems.isEmpty()) {
            Text("必要權限都準備好了。", color = SuccessGreen, fontWeight = FontWeight.Bold)
        } else {
            visibleItems.forEach { item -> PermissionRow(colors, item) }
        }
    }
}

private data class PermissionUiItem(
    val label: String,
    val status: String,
    val isOk: Boolean,
    val required: Boolean,
    val action: () -> Unit
)

@Composable
private fun PermissionRow(colors: UiColors, item: PermissionUiItem) {
    val statusColor = when {
        item.isOk -> SuccessGreen
        item.required -> DangerRed
        else -> WarningOrange
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, color = colors.text)
            Text(item.status, color = statusColor, fontWeight = FontWeight.SemiBold)
        }
        if (!item.isOk) TextButton(onClick = item.action) { Text("設定", color = colors.primary) }
    }
}

@Composable
private fun TargetEditDialog(
    colors: UiColors,
    target: SimpTarget?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var displayName by remember(target?.id) { mutableStateOf(target?.displayName.orEmpty()) }
    var notificationNames by remember(target?.id) { mutableStateOf(target?.notificationNames.orEmpty()) }
    var photoUri by remember(target?.id) { mutableStateOf(target?.photoUri) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        photoUri = uri?.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == null) "新增監聽對象" else "編輯監聽對象") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("列表顯示名稱") },
                    placeholder = { Text("例如：我的重點對象") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notificationNames,
                    onValueChange = { notificationNames = it },
                    label = { Text("通知比對名稱") },
                    placeholder = { Text("例如：寶寶2號, 網戀被騙10元") },
                    singleLine = true
                )
                Text("請填通知上實際會出現的暱稱或用戶 ID。多個名稱請用逗號、頓號或斜線分隔。", color = colors.muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launcher.launch(arrayOf("image/*")) }) { Text("選擇圖片") }
                    OutlinedButton(onClick = { photoUri = null }) { Text("使用幾何圖形") }
                }
                Text(if (photoUri == null) "目前使用幾何頭像。" else "已選擇頭像圖片。", color = colors.muted)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, notificationNames, photoUri) },
                enabled = displayName.isNotBlank() && notificationNames.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(if (target == null) "新增" else "儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.primary) }
        },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.muted
    )
}

@Composable
private fun OnboardingDialog(
    colors: UiColors,
    currentStep: OnboardingStep,
    monitoredAppsReady: Boolean,
    targetReady: Boolean,
    listenerReady: Boolean,
    alarmNotificationReady: Boolean,
    fullScreenReady: Boolean,
    batteryReady: Boolean,
    testReady: Boolean,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    onAction: (OnboardingStep) -> Unit,
    onSkipBattery: () -> Unit
) {
    val tasks = listOf(
        Triple(OnboardingStep.SelectApps, "選擇監聽 App", monitoredAppsReady),
        Triple(OnboardingStep.AddTarget, "新增監聽對象", targetReady),
        Triple(OnboardingStep.NotificationListener, "開啟通知監聽", listenerReady),
        Triple(OnboardingStep.AlarmNotification, "允許鬧鐘通知", alarmNotificationReady),
        Triple(OnboardingStep.FullScreenIntent, "允許跳出鬧鐘畫面", fullScreenReady),
        Triple(OnboardingStep.BatteryOptimization, "背景執行建議", batteryReady),
        Triple(OnboardingStep.TestAlarm, "測試鬧鐘", testReady)
    )
    val completedCount = tasks.count { it.third }
    val currentTitle = onboardingStepTitle(currentStep)
    val currentText = onboardingStepDescription(currentStep)
    val currentAction = onboardingStepActionText(currentStep)

    AlertDialog(
        onDismissRequest = { },
        title = { Text("首次設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$completedCount / ${tasks.size} 已完成",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("這個流程可以中斷。前往系統設定後，回到 App 會自動檢查並接著下一步。", color = colors.muted)
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                tasks.forEach { (step, label, done) ->
                    OnboardingTaskRow(colors, label, done, step == currentStep)
                }
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                Text(currentTitle, color = colors.text, fontWeight = FontWeight.Bold)
                Text(currentText, color = colors.muted)
                if (currentStep == OnboardingStep.BatteryOptimization) {
                    TextButton(onClick = onSkipBattery) {
                        Text("略過背景執行建議", color = colors.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentStep == OnboardingStep.Done) onDone() else onAction(currentStep)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(currentAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("略過設定", color = colors.primary) }
        },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.muted
    )
}

@Composable
private fun OnboardingTaskRow(colors: UiColors, label: String, done: Boolean, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (active) colors.primary else colors.text,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
        )
        Text(
            if (done) "已完成" else if (active) "目前步驟" else "待處理",
            color = if (done) SuccessGreen else if (active) colors.primary else colors.muted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (done || active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun onboardingStepTitle(step: OnboardingStep): String {
    return when (step) {
        OnboardingStep.SelectApps -> "先選擇要監聽的 App"
        OnboardingStep.AddTarget -> "新增第一個監聽對象"
        OnboardingStep.NotificationListener -> "開啟通知監聽權限"
        OnboardingStep.AlarmNotification -> "允許鬧鐘通知"
        OnboardingStep.FullScreenIntent -> "允許跳出鬧鐘畫面"
        OnboardingStep.BatteryOptimization -> "建議允許背景執行"
        OnboardingStep.TestAlarm -> "測試一次鬧鐘"
        OnboardingStep.Done -> "設定完成"
    }
}

private fun onboardingStepDescription(step: OnboardingStep): String {
    return when (step) {
        OnboardingStep.SelectApps -> "勾選 Instagram、LINE、Discord 等通知來源。"
        OnboardingStep.AddTarget -> "設定顯示名稱與通知上實際會出現的 ID。"
        OnboardingStep.NotificationListener -> "系統需要允許 Simp Alarm 讀取通知內容，才能判斷誰傳訊息。"
        OnboardingStep.AlarmNotification -> "允許通知後，鬧鐘才能在通知欄顯示並提供關閉按鈕。"
        OnboardingStep.FullScreenIntent -> "允許後，符合條件時可以跳出解除鬧鐘畫面。"
        OnboardingStep.BatteryOptimization -> "這不是硬性必要，但能降低背景監聽被系統中止的機率。"
        OnboardingStep.TestAlarm -> "確認音效、震動、通知欄關閉與跳出畫面都正常。"
        OnboardingStep.Done -> "必要設定都完成了，可以開始使用。"
    }
}

private fun onboardingStepActionText(step: OnboardingStep): String {
    return when (step) {
        OnboardingStep.SelectApps -> "選擇 App"
        OnboardingStep.AddTarget -> "新增對象"
        OnboardingStep.NotificationListener -> "前往通知監聽設定"
        OnboardingStep.AlarmNotification -> "允許鬧鐘通知"
        OnboardingStep.FullScreenIntent -> "前往全螢幕設定"
        OnboardingStep.BatteryOptimization -> "開啟電源設定"
        OnboardingStep.TestAlarm -> "測試鬧鐘"
        OnboardingStep.Done -> "完成"
    }
}

@Composable
private fun PermissionGuideDialog(
    colors: UiColors,
    prompt: PermissionPrompt?,
    onDismiss: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    when (prompt) {
        PermissionPrompt.NotificationListener -> GuideDialog(colors, "需要開啟通知監聽", "請在下一個設定頁允許 Simp Alarm 讀取通知。", "前往設定", onOpenListenerSettings, onDismiss)
        PermissionPrompt.BatteryOptimization -> GuideDialog(colors, "建議允許背景執行", "建議將 Simp Alarm 加入電池最佳化例外。", "允許背景執行", onRequestBatteryOptimization, onDismiss)
        PermissionPrompt.FullScreenIntent -> GuideDialog(colors, "需要允許全螢幕鬧鐘", "跳出解除畫面需要系統允許全螢幕通知。", "前往設定", onOpenFullScreenIntentSettings, onDismiss)
        null -> Unit
    }
}

@Composable
private fun GuideDialog(colors: UiColors, title: String, text: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍後再說", color = colors.primary) } },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.muted
    )
}

private fun startTestAlarm(context: Context) {
    val intent = Intent(context, SimpAlarmService::class.java).apply {
        putExtra(SimpAlarmService.EXTRA_SENDER_NAME, "測試對象")
        putExtra(SimpAlarmService.EXTRA_MESSAGE_TEXT, "這是一則測試鬧鐘。")
        putExtra(SimpAlarmService.EXTRA_SOURCE_PACKAGE, context.packageName)
        putExtra(SimpAlarmService.EXTRA_SOURCE_APP_LABEL, "Simp Alarm")
        putExtra(SimpAlarmService.EXTRA_RETURN_TO_APP_ON_DISMISS, true)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}

private fun requestNotificationListenerReconnect(context: Context) {
    if (!isNotificationListenerEnabled(context)) {
        Toast.makeText(context, "通知監聽尚未啟用，請先開啟權限。", Toast.LENGTH_SHORT).show()
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        NotificationListenerService.requestRebind(
            ComponentName(context, SimpNotificationListener::class.java)
        )
        SimpEventLog.record(context, "已要求系統重新連線通知監聽。")
        Toast.makeText(context, "已要求重新連線通知監聽。", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "請到通知監聽設定關閉後再重新開啟。", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

private fun requestNotificationListenerReconnectSilently(context: Context) {
    if (!isNotificationListenerEnabled(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return
    }

    val prefs = context.applicationContext.getSharedPreferences("simp_listener_reconnect", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val lastRequest = prefs.getLong("last_request_at", 0L)
    if (now - lastRequest < AUTO_RECONNECT_INTERVAL_MS) {
        return
    }

    runCatching {
        NotificationListenerService.requestRebind(
            ComponentName(context, SimpNotificationListener::class.java)
        )
        prefs.edit().putLong("last_request_at", now).apply()
        SimpEventLog.record(context, "已自動要求系統重新連線通知監聽。")
    }.onFailure { error ->
        SimpEventLog.record(context, "自動重新連線通知監聽失敗：${error.javaClass.simpleName}")
    }
}

private fun syncListenerHeartbeat(context: Context, listenerEnabled: Boolean) {
    if (listenerEnabled) {
        runCatching { SimpListenerHeartbeatService.start(context) }
            .onFailure { error ->
                SimpEventLog.record(context, "通知監聽保活啟動失敗：${error.javaClass.simpleName}")
            }
    } else {
        SimpEventLog.record(context, "通知監聽權限未啟用，保活服務保持停止。")
        SimpListenerHeartbeatService.stop(context)
    }
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    val batteryOptimizationActive = isBatteryOptimizationActive(context)
    val powerSaveModeActive = isPowerSaveModeActive(context)

    if (!batteryOptimizationActive && !powerSaveModeActive) {
        Toast.makeText(context, "已允許背景執行，不需要再設定。", Toast.LENGTH_SHORT).show()
        return
    }

    val intents = buildList {
        if (batteryOptimizationActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
        add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        add(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }

    Toast.makeText(context, "正在開啟電池設定...", Toast.LENGTH_SHORT).show()
    val opened = intents.any { safeStartActivity(context, it) }
    if (!opened) {
        Toast.makeText(context, "無法開啟電池設定，請手動到系統設定中調整。", Toast.LENGTH_LONG).show()
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    if (!safeStartActivity(context, intent)) {
        openAppDetailsSettings(context)
    }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (isFullScreenIntentAllowed(context)) {
        Toast.makeText(context, "全螢幕鬧鐘已允許。", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = if (Build.VERSION.SDK_INT >= 34) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    if (!safeStartActivity(context, intent)) {
        openAppNotificationSettings(context)
    }
}

private fun openOverlaySettings(context: Context) {
    if (SimpAlarmOverlay.canShow(context)) {
        Toast.makeText(context, "浮動鬧鐘已允許。", Toast.LENGTH_SHORT).show()
        return
    }

    Toast.makeText(context, "請允許 Simp Alarm 顯示在其他 App 上層。", Toast.LENGTH_LONG).show()
    if (!safeStartActivity(context, SimpAlarmOverlay.settingsIntent(context))) {
        openAppDetailsSettings(context)
    }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    if (!safeStartActivity(context, intent)) {
        Toast.makeText(context, "無法開啟系統設定，請手動到設定中開啟權限。", Toast.LENGTH_LONG).show()
    }
}

private fun safeStartActivity(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val expected = ComponentName(context, SimpNotificationListener::class.java).flattenToString()
    return flat.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun isAlarmNotificationAllowed(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun isFullScreenIntentAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return notificationManager.canUseFullScreenIntent()
}

private fun isBatteryOptimizationActive(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isPowerSaveModeActive(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isPowerSaveMode
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    SimpAlarmTheme(dynamicColor = false) {
        MainScreen()
    }
}



@Composable
private fun HomeScreen(
    colors: UiColors,
    targets: List<SimpTarget>,
    monitoredApps: Set<String>,
    triggerMode: TriggerMode,
    alarmPresentationMode: AlarmPresentationMode,
    listenerEnabled: Boolean,
    alarmNotificationAllowed: Boolean,
    fullScreenIntentAllowed: Boolean,
    overlayAllowed: Boolean,
    batteryOptimized: Boolean,
    powerSaveMode: Boolean,
    lastEvent: String,
    onNavigate: (AppScreen) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onTest: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    Header(colors, title = "Simp Alarm", subtitle = "指定 App 與對象，符合通知時立刻響鬧鐘。")
    AppCard(colors) {
        Text(
            text = if (listenerEnabled && targets.any { it.enabled } && monitoredApps.isNotEmpty()) "已就緒" else "需要設定",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primary
        )
        Text(
            text = if (listenerEnabled) "正在等待已選擇 App 的通知。" else "請先開啟通知監聽權限。",
            color = colors.muted
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatButton(colors, "監聽 App", monitoredApps.size.toString(), Modifier.weight(1f)) { onNavigate(AppScreen.Apps) }
            StatButton(colors, "監聽對象", "${targets.size} 個", Modifier.weight(1f)) { onNavigate(AppScreen.Targets) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatButton(colors, "觸發模式", if (triggerMode == TriggerMode.Once) "單次" else "持續", Modifier.weight(1f)) { onNavigate(AppScreen.Settings) }
            StatButton(colors, "鬧鐘方式", if (alarmPresentationMode == AlarmPresentationMode.FullScreen) "跳出畫面" else "只響鈴", Modifier.weight(1f)) { onNavigate(AppScreen.Settings) }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onTest,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text("測試鬧鐘")
        }
    }
    SectionTitle(colors, "快速操作")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickButton(colors, "監聽對象", "管理名單", Modifier.weight(1f)) { onNavigate(AppScreen.Targets) }
        QuickButton(colors, "監聽 App", "選擇來源", Modifier.weight(1f)) { onNavigate(AppScreen.Apps) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickButton(colors, "觸發紀錄", "最近事件", Modifier.weight(1f)) { onNavigate(AppScreen.History) }
        QuickButton(colors, "首次設定", "步驟引導", Modifier.weight(1f), onOpenOnboarding)
    }
    PermissionSummaryCard(
        colors = colors,
        listenerEnabled = listenerEnabled,
        alarmNotificationAllowed = alarmNotificationAllowed,
        fullScreenIntentAllowed = fullScreenIntentAllowed,
        overlayAllowed = overlayAllowed,
        batteryOptimized = batteryOptimized,
        powerSaveMode = powerSaveMode,
        onClick = onOpenPermissions
    )
    AppCard(colors) {
        SectionTitle(colors, "最後事件")
        Text(lastEvent, color = colors.muted)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onRefresh) { Text("刷新") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onReconnect) { Text("重新連線") }
        }
    }
}

@Composable
private fun TargetsScreen(
    colors: UiColors,
    targets: List<SimpTarget>,
    targetFilter: TargetFilter,
    triggerMode: TriggerMode,
    scrollState: ScrollState,
    viewportBounds: () -> Pair<Float, Float>,
    onFilterChange: (TargetFilter) -> Unit,
    onAdd: () -> Unit,
    onEdit: (SimpTarget) -> Unit,
    onOpenDetail: (SimpTarget) -> Unit,
    onToggleEnabled: (SimpTarget, Boolean) -> Unit,
    onToggleStar: (SimpTarget) -> Unit,
    onTogglePin: (SimpTarget) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (SimpTarget) -> Unit
) {
    TopBar(colors, "監聽對象", trailing = "＋", onTrailing = onAdd)
    AppCard(colors) {
        Text("已儲存 ${targets.size} 個對象", color = colors.primary, fontWeight = FontWeight.Bold)
        Text(
            "${targets.count { it.enabled }} 個已啟用・${targets.count { it.pinned }} 個已置頂・${targets.count { it.continuousOverride }} 個星號鎖定",
            color = colors.muted
        )
        Text(
            "長按對象卡片可拖曳排序，圖釘可將對象置頂。",
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Segmented(
        colors = colors,
        items = listOf(TargetFilter.All to "全部", TargetFilter.Enabled to "啟用中", TargetFilter.Starred to "已標星"),
        selected = targetFilter,
        onSelected = onFilterChange
    )
    val visibleTargets = targets.filter {
        targetFilter == TargetFilter.All ||
            (targetFilter == TargetFilter.Enabled && it.enabled) ||
            (targetFilter == TargetFilter.Starred && it.continuousOverride)
    }
    val density = LocalDensity.current
    val fallbackRowPx = with(density) { 92.dp.roundToPx() }
    var orderedTargetIds by remember { mutableStateOf(visibleTargets.map { it.id }) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val rowScreenTops = remember { mutableStateMapOf<String, Float>() }
    var draggingTargetId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartScreenTop by remember { mutableFloatStateOf(0f) }
    var dragStartScrollValue by remember { mutableStateOf(0) }
    val latestDragOffsetY by rememberUpdatedState(dragOffsetY)
    val latestDragStartScreenTop by rememberUpdatedState(dragStartScreenTop)
    val latestDragStartScrollValue by rememberUpdatedState(dragStartScrollValue)

    LaunchedEffect(visibleTargets.map { it.id }) {
        if (draggingTargetId == null) {
            orderedTargetIds = visibleTargets.map { it.id }
        }
    }

    val orderedVisibleTargets = orderedTargetIds
        .mapNotNull { id -> visibleTargets.firstOrNull { it.id == id } }
        .ifEmpty { visibleTargets }
    val dragCurrentIndex = computeDropIndex(
        orderedVisibleTargets, rowHeights, fallbackRowPx, draggingTargetId, dragStartIndex, dragOffsetY
    )
    val draggedH = (rowHeights[draggingTargetId] ?: fallbackRowPx).toFloat()

    LaunchedEffect(draggingTargetId) {
        val draggedId = draggingTargetId ?: return@LaunchedEffect
        Log.d("DragScroll", "開始拖曳 $draggedId scroll=${scrollState.value} max=${scrollState.maxValue}")
        val edgeZone = with(density) { 150.dp.toPx() }
        val maxSpeed = with(density) { 36.dp.toPx() }
        while (draggingTargetId == draggedId) {
            val (viewTop, viewBottom) = viewportBounds()
            if (viewBottom > viewTop) {
                val currentScrollDelta = scrollState.value - latestDragStartScrollValue
                val currentDragOffset = latestDragOffsetY
                val draggedTop = latestDragStartScreenTop + currentDragOffset - currentScrollDelta
                val draggedBottom = draggedTop + draggedH
                Log.d(
                    "DragScroll",
                    "viewTop=$viewTop viewBottom=$viewBottom draggedTop=$draggedTop draggedBottom=$draggedBottom scroll=${scrollState.value}/${scrollState.maxValue} offset=$currentDragOffset"
                )
                val delta = when {
                    draggedTop < viewTop + edgeZone ->
                        -maxSpeed * (((viewTop + edgeZone) - draggedTop) / edgeZone).coerceIn(0f, 1f)
                    draggedBottom > viewBottom - edgeZone ->
                        maxSpeed * ((draggedBottom - (viewBottom - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (delta != 0f) {
                    val consumed = scrollState.scrollBy(delta)
                    Log.d("DragScroll", "delta=$delta consumed=$consumed afterScroll=${scrollState.value}/${scrollState.maxValue}")
                    if (consumed != 0f) dragOffsetY += consumed
                }
            } else {
                Log.d("DragScroll", "viewportBounds 異常: top=$viewTop bottom=$viewBottom")
            }
            delay(16)
        }
        Log.d("DragScroll", "結束拖曳 $draggedId")
    }

    if (visibleTargets.isEmpty()) {
        EmptyState(colors, "還沒有監聽對象", "新增第一個對象，設定顯示名稱與通知 ID。", "新增監聽對象", onAdd)
    } else {
        orderedVisibleTargets.forEachIndexed { index, target ->
            val isDragging = draggingTargetId == target.id
            val displacementTarget = when {
                isDragging -> 0f
                draggingTargetId == null -> 0f
                dragCurrentIndex == dragStartIndex -> 0f
                index in minOf(dragStartIndex, dragCurrentIndex)..maxOf(dragStartIndex, dragCurrentIndex) -> {
                    if (dragCurrentIndex > dragStartIndex) -draggedH else draggedH
                }
                else -> 0f
            }
            val animatedDisplacement by animateFloatAsState(
                targetValue = displacementTarget,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
                label = "rowDisplacement"
            )
            val rowOffset = if (isDragging) dragOffsetY else animatedDisplacement
            TargetRow(
                colors = colors,
                target = target,
                triggerMode = triggerMode,
                index = index,
                isDragging = isDragging,
                dragOffsetY = rowOffset,
                dragEnabled = targetFilter == TargetFilter.All,
                onRowHeight = { rowHeights[target.id] = it },
                onDraggedPosition = { top -> rowScreenTops[target.id] = top },
                onEdit = { onEdit(target) },
                onOpenDetail = { onOpenDetail(target) },
                onToggleEnabled = { onToggleEnabled(target, it) },
                onToggleStar = { onToggleStar(target) },
                onTogglePin = { onTogglePin(target) },
                onDragStart = {
                    if (targetFilter == TargetFilter.All) {
                        draggingTargetId = target.id
                        dragStartIndex = index
                        dragOffsetY = 0f
                        dragStartScreenTop = rowScreenTops[target.id] ?: 0f
                        dragStartScrollValue = scrollState.value
                    }
                },
                onDrag = { deltaY -> dragOffsetY += deltaY },
                onDragEnd = {
                    val currentOrdered = orderedTargetIds
                        .mapNotNull { id -> visibleTargets.firstOrNull { it.id == id } }
                        .ifEmpty { visibleTargets }
                    val targetIndex = computeDropIndex(
                        currentOrdered, rowHeights, fallbackRowPx, draggingTargetId, dragStartIndex, dragOffsetY
                    )
                    if (targetIndex != dragStartIndex) {
                        val newOrder = orderedTargetIds.toMutableList()
                        val movedId = newOrder.removeAt(dragStartIndex)
                        newOrder.add(targetIndex.coerceIn(0, newOrder.size), movedId)
                        orderedTargetIds = newOrder
                        onReorder(newOrder)
                    }
                    draggingTargetId = null
                    dragOffsetY = 0f
                },
                onDelete = { onDelete(target) }
            )
        }
    }
    AppCard(colors) {
        SectionTitle(colors, "名稱比對說明")
        Text("請輸入通知上實際會顯示的名稱；沒有出現在通知裡的 ID 或暱稱可能無法判斷。", color = colors.muted)
    }
}

@Composable
private fun TargetRow(
    colors: UiColors,
    target: SimpTarget,
    triggerMode: TriggerMode,
    index: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    dragEnabled: Boolean,
    onRowHeight: (Int) -> Unit,
    onDraggedPosition: (Float) -> Unit,
    onEdit: () -> Unit,
    onOpenDetail: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit
) {
    val pinnedTint = if (target.pinned) PrimaryBlue else colors.muted
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "dragScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onRowHeight(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { coords ->
                onDraggedPosition(coords.positionInWindow().y)
            }
            .pointerInput(dragEnabled, target.id, index) {
                if (!dragEnabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { _, dragAmount -> onDrag(dragAmount.y) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    ) {
        AppCard(colors, elevation = if (isDragging) 8.dp else 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (dragEnabled) {
                    DragHandle(colors, Modifier.size(12.dp))
                }
                TargetAvatar(colors, target, 40)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenDetail)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            target.displayName,
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (target.pinned) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("置頂", color = PrimaryBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        target.notificationNames,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    Text(
                        text = targetStatusLabel(target, triggerMode),
                        color = if (target.enabled) colors.primary else colors.muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier.size(30.dp).clickable(onClick = onTogglePin),
                    contentAlignment = Alignment.Center
                ) {
                    PinIcon(active = target.pinned, tint = pinnedTint, modifier = Modifier.size(17.dp))
                }
                Box(
                    modifier = Modifier.size(30.dp).clickable(onClick = onToggleStar),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (target.continuousOverride) "★" else "☆",
                        color = if (target.continuousOverride) WarningOrange else colors.primary,
                        fontSize = 15.sp
                    )
                }
                Box(
                    modifier = Modifier.size(26.dp).clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = colors.primary, fontSize = 13.sp)
                }
                Switch(
                    checked = target.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f }
                )
                Box(
                    modifier = Modifier.size(30.dp).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun PinIcon(active: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val headRadius = size.minDimension * 0.26f
        val headCenter = Offset(size.width * 0.5f, size.height * 0.30f)
        val strokeW = size.minDimension * 0.10f
        val needleStart = Offset(size.width * 0.5f, size.height * 0.56f)
        val needleEnd = Offset(size.width * 0.5f, size.height * 0.95f)
        if (active) {
            drawCircle(tint, headRadius, headCenter)
        } else {
            drawCircle(tint, headRadius, headCenter, style = Stroke(width = strokeW))
        }
        drawLine(tint, needleStart, needleEnd, strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
private fun DragHandle(colors: UiColors, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dotRadius = size.minDimension * 0.11f
        val cols = listOf(size.width * 0.32f, size.width * 0.68f)
        val rows = listOf(size.height * 0.25f, size.height * 0.5f, size.height * 0.75f)
        cols.forEach { x ->
            rows.forEach { y ->
                drawCircle(colors.muted, dotRadius, Offset(x, y))
            }
        }
    }
}

@Composable
private fun AppsScreen(
    colors: UiColors,
    monitoredApps: Set<String>,
    onAppEnabledChange: (String, Boolean) -> Unit
) {
    TopBar(colors, "監聽 App")
    AppCard(colors) {
        Text("已選擇 ${monitoredApps.size} 個 App", color = colors.primary, fontWeight = FontWeight.Bold)
        Text("只有勾選的 App 通知會被檢查。", color = colors.muted)
    }
    SimpTargetManager.supportedApps.forEach { app ->
        AppCard(colors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(app.label, color = colors.text, fontWeight = FontWeight.Bold)
                    Text(app.packageName, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = app.packageName in monitoredApps,
                    onCheckedChange = { onAppEnabledChange(app.packageName, it) }
                )
            }
        }
    }
}

@Composable
private fun HistoryTargetFilter(
    colors: UiColors,
    targets: List<SimpTarget>,
    selectedTargetId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTarget = targets.firstOrNull { it.id == selectedTargetId }
    val label = selectedTarget?.displayName ?: "全部對象"

    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.White
            ),
            onClick = { expanded = true }
        ) {
            Text(
                text = "對象篩選：$label",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "全部對象",
                        color = if (selectedTargetId == null) colors.primary else colors.text,
                        fontWeight = if (selectedTargetId == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            targets.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TargetAvatar(colors, target, 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.displayName,
                                    color = if (selectedTargetId == target.id) colors.primary else colors.text,
                                    fontWeight = if (selectedTargetId == target.id) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = target.notificationNames,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelected(target.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    colors: UiColors,
    history: List<TriggerHistoryItem>,
    targets: List<SimpTarget>,
    historyFilter: String?,
    onFilterChange: (String?) -> Unit,
    onOpenTarget: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTest: () -> Unit
) {
    TopBar(colors, "觸發紀錄", trailing = "清除", onTrailing = onClearHistory)
    AppCard(colors) {
        Text("最近 ${history.size} 次觸發", color = colors.primary, fontWeight = FontWeight.Bold)
        Text("記錄對象、來源 App 與時間。", color = colors.muted)
    }
    HistoryTargetFilter(
        colors = colors,
        targets = targets,
        selectedTargetId = historyFilter,
        onSelected = onFilterChange
    )
    val visibleHistory = history.filter { historyFilter == null || it.targetId == historyFilter }
    if (visibleHistory.isEmpty()) {
        EmptyState(colors, "尚無觸發紀錄", "觸發鬧鐘後，這裡會顯示對象、來源 App 與時間。", "測試鬧鐘", onTest)
    } else {
        visibleHistory.forEach { item ->
            AppCard(colors) {
                Column(modifier = Modifier.clickable { onOpenTarget(item.targetId) }) {
                    Text("${item.targetName}・${item.appLabel}", color = colors.text, fontWeight = FontWeight.Bold)
                    Text(item.time, color = colors.primary, style = MaterialTheme.typography.bodySmall)
                    Text(item.message, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    colors: UiColors,
    triggerMode: TriggerMode,
    alarmPresentationMode: AlarmPresentationMode,
    darkMode: Boolean,
    listenerEnabled: Boolean,
    alarmNotificationAllowed: Boolean,
    fullScreenIntentAllowed: Boolean,
    overlayAllowed: Boolean,
    batteryOptimized: Boolean,
    powerSaveMode: Boolean,
    onTriggerModeChange: (TriggerMode) -> Unit,
    onAlarmPresentationModeChange: (AlarmPresentationMode) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onTest: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    TopBar(colors, "設定")
    AppCard(colors) {
        SectionTitle(colors, "觸發模式")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton(Modifier.weight(1f), triggerMode == TriggerMode.Once, "單次模式", colors) {
                onTriggerModeChange(TriggerMode.Once)
            }
            ModeButton(Modifier.weight(1f), triggerMode == TriggerMode.Continuous, "持續監聽", colors) {
                onTriggerModeChange(TriggerMode.Continuous)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        SectionTitle(colors, "鬧鐘顯示方式")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton(Modifier.weight(1f), alarmPresentationMode == AlarmPresentationMode.FullScreen, "跳出畫面", colors) {
                onAlarmPresentationModeChange(AlarmPresentationMode.FullScreen)
            }
            ModeButton(Modifier.weight(1f), alarmPresentationMode == AlarmPresentationMode.SoundOnly, "只響鈴", colors) {
                onAlarmPresentationModeChange(AlarmPresentationMode.SoundOnly)
            }
        }
    }
    AppCard(colors) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                SectionTitle(colors, "深色模式")
                Text(if (darkMode) "目前使用深色模式" else "目前使用淺色模式", color = colors.muted)
            }
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
    }
    PermissionCard(
        colors = colors,
        listenerEnabled = listenerEnabled,
        alarmNotificationAllowed = alarmNotificationAllowed,
        fullScreenIntentAllowed = fullScreenIntentAllowed,
        overlayAllowed = overlayAllowed,
        batteryOptimized = batteryOptimized,
        powerSaveMode = powerSaveMode,
        onOpenListenerSettings = onOpenListenerSettings,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
        onOpenOverlaySettings = onOpenOverlaySettings,
        onOpenBatterySettings = onOpenBatterySettings
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickButton(colors, "觸發紀錄", "查看最近事件", Modifier.weight(1f), onOpenHistory)
        QuickButton(colors, "首次設定", "重新查看流程", Modifier.weight(1f), onOpenOnboarding)
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onTest,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
    ) {
        Text("測試鬧鐘")
    }
}

@Composable
private fun TargetDetailScreen(
    colors: UiColors,
    target: SimpTarget?,
    history: List<TriggerHistoryItem>,
    triggerMode: TriggerMode,
    onBack: () -> Unit,
    onEdit: (SimpTarget) -> Unit,
    onToggleEnabled: (SimpTarget, Boolean) -> Unit,
    onToggleStar: (SimpTarget) -> Unit,
    onDelete: (SimpTarget) -> Unit,
    onTest: () -> Unit
) {
    TopBar(colors, "對象詳情", leading = "←", onLeading = onBack)
    if (target == null) {
        EmptyState(colors, "找不到對象", "這個對象可能已被刪除。", "回到列表", onBack)
        return
    }
    AppCard(colors) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TargetAvatar(colors, target, 76)
            Column(modifier = Modifier.weight(1f)) {
                Text(target.displayName, color = colors.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(target.notificationNames, color = colors.muted)
                Text(targetStatusLabel(target, triggerMode), color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    AppCard(colors) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                SectionTitle(colors, "持續監聽鎖定")
                Text("不受全域單次模式影響。", color = colors.muted)
            }
            Switch(checked = target.continuousOverride, onCheckedChange = { onToggleStar(target) })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.border)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                SectionTitle(colors, "啟用監聽")
                Text("關閉後保留資料但不觸發。", color = colors.muted)
            }
            Switch(checked = target.enabled, onCheckedChange = { onToggleEnabled(target, it) })
        }
    }
    SectionTitle(colors, "最近觸發")
    if (history.isEmpty()) {
        AppCard(colors) {
            Text("這個對象還沒有觸發紀錄。", color = colors.muted)
        }
    } else {
        history.forEach { item ->
            AppCard(colors) {
                Text("${item.appLabel}・${item.time}", color = colors.primary, fontWeight = FontWeight.Bold)
                Text(item.message, color = colors.muted)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onEdit(target) }) { Text("編輯") }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onDelete(target) }) { Text("刪除", color = DangerRed) }
    }
    Button(modifier = Modifier.fillMaxWidth(), onClick = onTest, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
        Text("測試鬧鐘")
    }
}
