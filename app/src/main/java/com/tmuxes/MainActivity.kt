package com.tmuxes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.tmuxes.data.settings.Settings
import com.tmuxes.launcher.LauncherIconController
import com.tmuxes.ui.design.AppTheme
import com.tmuxes.ui.navigation.AppNavigation
import com.tmuxes.ui.navigation.Routes

class MainActivity : ComponentActivity() {

    internal data class LaunchRequest(
        val serverId: Long,
        val sessionName: String?,
        val requestId: Long
    )

    private var nextLaunchRequestId = 0L
    private val pendingLaunchRequest = mutableStateOf<LaunchRequest?>(null)
    private val pendingYamlEditorWidgetId = mutableStateOf<Int?>(null)
    private val shouldHandleDefaultLaunch = mutableStateOf(true)
    private var lastLaunchRequest: LaunchRequest? = null

    /** Hoisted so onNewIntent() can navigate. Set in setContent. */
    internal var navController: NavHostController? = null

    /**
     * Runtime permission launcher for POST_NOTIFICATIONS (Android 13+).
     * The foreground service notification is silently invisible without this
     * permission, which can cause Android 14+ to kill the foreground service
     * more aggressively. We don't fail if denied — the service still runs.
     *
     * Lint thinks this needs Fragment 1.3.0+ but we're using ComponentActivity
     * directly, which has supported registerForActivityResult since 1.2.0 and
     * does not require any Fragment dependency. Suppress the false positive.
     */
    @Suppress("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
            "MainActivity.onCreate restored=${savedInstanceState != null} action=${intent?.action}"
        }
        try { enableEdgeToEdge() } catch (_: Throwable) {} // allow-bypass-D5: defensive insets call before super.onCreate
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+ if not already granted.
        // Declared in the manifest but it's a runtime permission since API 33.
        // Without it, our foreground service notification is invisible AND
        // some Android builds kill the FGS more aggressively because it
        // looks like a notification-less background service.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (_: Throwable) {}

        // The manifest declares TmuxesApp as the application class, so the
        // cast should normally succeed. Fall back to a nullable cast and
        // finish cleanly rather than let a synthetic NPE abort onCreate — a
        // crash here would be observed as "app won't even open".
        val app = applicationContext as? TmuxesApp
        if (app == null) {
            finish()
            return
        }
        try { handleLaunchIntent(intent) } catch (t: Throwable) {
            com.tmuxes.util.AppLogger.e(com.tmuxes.util.AppLogger.Category.LIFECYCLE, t) {
                "MainActivity.handleLaunchIntent threw — launch path silently dropped action=${intent?.action}"
            }
        }

        // setContent itself doesn't run the composable — the lambda is
        // evaluated on the first Choreographer frame. An exception thrown
        // during that first composition (e.g., a preferences lazy init
        // throw, a missing vector drawable, a theme resource that can't be
        // resolved on a vendor Android build) normally propagates to the main thread
        // and kills the activity. Wrap setContent itself so a surprise
        // synchronous throw at call time can't leak; the composition-time
        // error path is covered by individual catchSafe/.catch operators
        // on the flows feeding the composition.
        try {
        setContent {
            val themeMode by app.preferences.flow(Settings.theme)
                .collectAsState(initial = Settings.theme.default)
            val palette by app.preferences.flow(Settings.appColorPalette)
                .collectAsState(initial = Settings.appColorPalette.default)
            val accent by app.preferences.flow(Settings.appAccentColor)
                .collectAsState(initial = Settings.appAccentColor.default)
            val density by app.preferences.flow(Settings.appDensity)
                .collectAsState(initial = Settings.appDensity.default)
            val typeScale by app.preferences.flow(Settings.appTypeScale)
                .collectAsState(initial = Settings.appTypeScale.default)
            val cornerStyle by app.preferences.flow(Settings.appCornerStyle)
                .collectAsState(initial = Settings.appCornerStyle.default)
            val statusBarStyle by app.preferences.flow(Settings.appStatusBarStyle)
                .collectAsState(initial = Settings.appStatusBarStyle.default)
            val navigationStyle by app.preferences.flow(Settings.appNavigationStyle)
                .collectAsState(initial = Settings.appNavigationStyle.default)
            val appLanguage by app.preferences.flow(Settings.appLanguage)
                .collectAsState(initial = Settings.appLanguage.default)
            val startScreen by app.preferences.flow(Settings.startScreen)
                .collectAsState(initial = Settings.startScreen.default)
            val lastServerId by app.preferences.flow(Settings.lastSessionServerId)
                .collectAsState(initial = Settings.lastSessionServerId.default)
            val lastSessionName by app.preferences.flow(Settings.lastSessionName)
                .collectAsState(initial = Settings.lastSessionName.default)
            val bubbleOpacityPercent by app.preferences.flow(Settings.bubbleOpacity)
                .collectAsState(initial = Settings.bubbleOpacity.default)
            val fabOpacityPercent by app.preferences.flow(Settings.fabOpacity)
                .collectAsState(initial = Settings.fabOpacity.default)

            LaunchedEffect(appLanguage) {
                com.tmuxes.i18n.I18nRuntime.setLanguage(appLanguage)
            }

            LaunchedEffect(themeMode, palette, accent) {
                LauncherIconController.request(
                    context = this@MainActivity,
                    themeMode = themeMode,
                    paletteKey = palette,
                    accentArgb = accent
                )
            }

            AppTheme(
                themeMode = themeMode,
                palette = palette,
                accentArgb = accent,
                density = density,
                typeScale = typeScale,
                cornerStyle = cornerStyle,
                statusBarStyle = statusBarStyle,
                bubbleOpacityPercent = bubbleOpacityPercent,
                fabOpacityPercent = fabOpacityPercent
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.tmuxes.i18n.LocalI18n provides com.tmuxes.i18n.I18n(
                        com.tmuxes.i18n.AppLanguage.resolve(appLanguage)
                    ),
                    com.tmuxes.ui.design.LocalNavigationStyle provides navigationStyle
                ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    navController = nav
                    val launchRequest = pendingLaunchRequest.value
                    val canHandleDefaultLaunch = shouldHandleDefaultLaunch.value

                    AppNavigation(navController = nav)

                    // Show a one-time dialog if a crash from a previous
                    // session was detected. The dialog is dismissed by
                    // calling acknowledgeCrashLog() so it doesn't show
                    // again until a new crash is appended to the file.
                    com.tmuxes.ui.components.app.AppCrashRecoveryDialog()

                    LaunchedEffect(launchRequest?.requestId) {
                        val request = launchRequest ?: return@LaunchedEffect
                        shouldHandleDefaultLaunch.value = false
                        try {
                            when {
                                !request.sessionName.isNullOrBlank() -> {
                                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                        "launch.branch=widget→terminal serverId=${request.serverId} session='${request.sessionName}'"
                                    }
                                    nav.navigate(
                                        Routes.terminal(request.serverId, request.sessionName)
                                    ) {
                                        // Clear back stack so Back from terminal returns to desktop
                                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                else -> {
                                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                        "launch.branch=widget→serverDetail serverId=${request.serverId}"
                                    }
                                    nav.navigate(Routes.serverDetail(request.serverId)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            com.tmuxes.util.AppLogger.w(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                "launch.nav ✗ cause='${e.message}'"
                            }
                        }
                        pendingLaunchRequest.value = null
                    }

                    // Navigate to YAML editor when launched from WidgetConfigActivity
                    val yamlEditorWidgetId = pendingYamlEditorWidgetId.value
                    LaunchedEffect(yamlEditorWidgetId) {
                        val wId = yamlEditorWidgetId ?: return@LaunchedEffect
                        shouldHandleDefaultLaunch.value = false
                        com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                            "launch.branch=deep-link→yamlEditor widgetId=$wId"
                        }
                        try {
                            nav.navigate(Routes.yamlEditor(wId)) {
                                launchSingleTop = true
                            }
                        } catch (e: Throwable) {
                            com.tmuxes.util.AppLogger.w(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                "launch.nav→yamlEditor ✗ widgetId=$wId cause='${e.message}'"
                            }
                        }
                        pendingYamlEditorWidgetId.value = null
                    }

                    // Handle normal startup when there is no widget/deep-link target.
                    LaunchedEffect(
                        startScreen,
                        lastServerId,
                        lastSessionName,
                        launchRequest?.requestId,
                        canHandleDefaultLaunch
                    ) {
                        if (launchRequest != null || !canHandleDefaultLaunch) return@LaunchedEffect
                        try {
                            when {
                                startScreen == "last_session" &&
                                    lastServerId > 0 &&
                                    lastSessionName.isNotBlank() -> {
                                    shouldHandleDefaultLaunch.value = false
                                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                        "launch.branch=default→last_session serverId=$lastServerId session='$lastSessionName'"
                                    }
                                    nav.navigate(Routes.terminal(lastServerId, lastSessionName)) {
                                        launchSingleTop = true
                                    }
                                }
                                startScreen == "sessions" -> {
                                    shouldHandleDefaultLaunch.value = false
                                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                        "launch.branch=default→sessions tab"
                                    }
                                    // Feature T3: the three bottom tabs
                                    // are now pages of a single HOME
                                    // route. Navigate to HOME with
                                    // page=1 (Sessions) instead of the
                                    // old standalone SESSION_PICKER_ALL
                                    // route.
                                    nav.navigate(Routes.home(1)) {
                                        popUpTo(Routes.home(0)) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                startScreen == "servers" -> {
                                    shouldHandleDefaultLaunch.value = false
                                    com.tmuxes.util.AppLogger.d(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                        "launch.branch=default→servers (already on default tab)"
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            com.tmuxes.util.AppLogger.w(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                                "launch.default-branch ✗ cause='${e.message}'"
                            }
                        }
                    }
                }
                }
            }
        }
        } catch (e: Throwable) {
            // Last-resort: log and finish cleanly rather than crash in
            // the Application-managed onCreate path.
            try {
                com.tmuxes.util.AppLogger.e(com.tmuxes.util.AppLogger.Category.LIFECYCLE, e) { "MainActivity.setContent failed" }
            } catch (_: Throwable) {}
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        LauncherIconController.applyPending(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
            "MainActivity.onNewIntent action=${intent.action} extras=${intent.extras?.keySet()?.joinToString(",") ?: ""}"
        }
        super.onNewIntent(intent)
        setIntent(intent)

        try {
            val navigateTo = intent.getStringExtra("navigate_to")
            val serverId = intent.getLongExtra("server_id", -1L).takeIf { it > 0L }
            when {
                navigateTo == "yaml_editor" -> {
                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                        "onNewIntent.branch=yaml_editor"
                    }
                    handleLaunchIntent(intent)
                }
                serverId != null -> {
                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                        "onNewIntent.branch=widget serverId=$serverId"
                    }
                    handleLaunchIntent(intent)
                }
                else -> {
                    com.tmuxes.util.AppLogger.i(com.tmuxes.util.AppLogger.Category.LIFECYCLE) {
                        "onNewIntent.branch=launcher-icon-reset"
                    }
                    pendingLaunchRequest.value = null
                    navController?.let { nav ->
                        try {
                            nav.navigate(Routes.home(0)) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {
            // Never crash on a new intent — Android will deliver garbage
            // extras or mutated intents in rare cases.
        }
    }

    internal fun handleLaunchIntent(intent: Intent?) {
        // Handle YAML editor navigation from WidgetConfigActivity
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo == "yaml_editor") {
            val widgetId = intent.getIntExtra("widgetId", -1)
            pendingYamlEditorWidgetId.value = widgetId
            pendingLaunchRequest.value = null
            return
        }

        val serverId = intent?.getLongExtra("server_id", -1L)
            ?.takeIf { it > 0L }

        pendingLaunchRequest.value = if (serverId != null) {
            LaunchRequest(
                serverId = serverId,
                sessionName = intent.getStringExtra("session_name"),
                requestId = ++nextLaunchRequestId
            ).also { request ->
                lastLaunchRequest = request
            }
        } else {
            null
        }
    }

    internal fun lastLaunchRequestForTest(): LaunchRequest? = lastLaunchRequest
}
