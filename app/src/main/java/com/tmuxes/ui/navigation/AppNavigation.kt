package com.tmuxes.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.tmuxes.util.findComponentActivity
import com.tmuxes.util.safeNavigate
import com.tmuxes.util.safePopBackStack
import com.tmuxes.TmuxesApp
import com.tmuxes.i18n.t
import com.tmuxes.ssh.HostKeyEvent
import com.tmuxes.ssh.HostKeyPromptResult
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.screens.keys.KeyManagerScreen
import com.tmuxes.ui.screens.servers.AddEditServerScreen
import com.tmuxes.ui.screens.servers.PortForwardScreen
import com.tmuxes.ui.screens.servers.ServerDetailScreen
import com.tmuxes.ui.screens.servers.ServerListScreen
import com.tmuxes.ui.screens.sessions.SessionPickerScreen
import com.tmuxes.ui.screens.settings.AppAppearanceScreen
import com.tmuxes.ui.screens.settings.NotificationsScreen
import com.tmuxes.ui.screens.settings.SecurityKeysScreen
import com.tmuxes.ui.screens.settings.SettingsScreen
import com.tmuxes.ui.screens.settings.ShellSessionScreen
import com.tmuxes.ui.screens.settings.SshConnectionScreen
import com.tmuxes.ui.screens.settings.TerminalAppearanceScreen
import com.tmuxes.ui.screens.settings.TerminalInputScreen
import com.tmuxes.ui.screens.settings.YamlEditorScreen
import com.tmuxes.ui.screens.snippets.LibraryDetailScreen
import com.tmuxes.ui.screens.hosts.KnownHostsScreen
import com.tmuxes.ui.screens.settings.ColorSchemeEditorScreen
import com.tmuxes.ui.screens.snippets.SnippetsScreen
import com.tmuxes.ui.screens.terminal.TerminalScreen

// ---------------------------------------------------------------------------
// Route definitions
// ---------------------------------------------------------------------------

object Routes {
    const val HOME = "home?page={page}"
    const val SERVER_DETAIL = "server_detail/{serverId}"
    const val ADD_SERVER = "add_server"
    const val ADD_CHILD_SERVER = "add_child_server/{parentId}"
    const val IMPORT_CONFIG_HOST = "import_config_host/{parentId}/{name}/{hostname}/{username}/{port}"
    const val EDIT_SERVER = "edit_server/{serverId}"
    const val SESSION_PICKER_SERVER = "session_picker/{serverId}"
    const val TERMINAL = "terminal/{serverId}/{sessionName}"
    const val SETTINGS_APP_APPEARANCE = "settings/app_appearance"
    const val SETTINGS_TERMINAL_APPEARANCE = "settings/terminal_appearance"
    const val SETTINGS_TERMINAL_INPUT = "settings/terminal_input"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_SSH_CONNECTION = "settings/ssh_connection"
    const val SETTINGS_SHELL_SESSION = "settings/shell_session"
    const val SETTINGS_SECURITY_KEYS = "settings/security_keys"
    const val SETTINGS_DEBUG_LOG = "settings/debug_log"
    const val YAML_EDITOR = "yaml_editor/{widgetId}"
    const val PORT_FORWARD = "port_forward/{serverId}"
    const val KEY_MANAGER = "key_manager"
    const val SNIPPETS = "snippets"
    const val LIBRARY_DETAIL = "library_detail/{libraryId}"
    const val KNOWN_HOSTS = "known_hosts"
    const val COLOR_SCHEME_EDITOR = "color_scheme_editor"

    /** Home pager route; page 0=Servers (default), 1=Sessions, 2=Settings. */
    fun home(page: Int = 0) = "home?page=$page"
    fun serverDetail(serverId: Long) = "server_detail/$serverId"
    fun portForward(serverId: Long) = "port_forward/$serverId"
    /** @param widgetId -1 = global settings, -2 = servers.yaml, >=0 = specific widget */
    fun yamlEditor(widgetId: Int = -1) = "yaml_editor/$widgetId"
    fun editServer(id: Long) = "edit_server/$id"
    fun addChildServer(parentId: Long) = "add_child_server/$parentId"
    fun sessionPicker(serverId: Long) = "session_picker/$serverId"
    fun importConfigHost(parentId: Long, name: String, hostname: String, username: String?, port: Int?) =
        "import_config_host/$parentId/${Uri.encode(name)}/${Uri.encode(hostname)}/${Uri.encode(username ?: "_NONE_")}/${port ?: 22}"
    fun terminal(serverId: Long, sessionName: String) = "terminal/$serverId/${Uri.encode(sessionName)}"
    fun libraryDetail(libraryId: Long) = "library_detail/$libraryId"
}

// ---------------------------------------------------------------------------
// Routes that should hide the bottom bar
// ---------------------------------------------------------------------------

private val hideBottomBarRoutes = setOf(
    Routes.ADD_SERVER,
    Routes.ADD_CHILD_SERVER,
    Routes.IMPORT_CONFIG_HOST,
    Routes.EDIT_SERVER,
    Routes.SERVER_DETAIL,
    Routes.SESSION_PICKER_SERVER,
    Routes.PORT_FORWARD,
    Routes.TERMINAL,
    Routes.KEY_MANAGER,
    Routes.SNIPPETS,
    Routes.LIBRARY_DETAIL,
    Routes.KNOWN_HOSTS,
    Routes.COLOR_SCHEME_EDITOR,
    Routes.SETTINGS_APP_APPEARANCE,
    Routes.SETTINGS_TERMINAL_APPEARANCE,
    Routes.SETTINGS_TERMINAL_INPUT,
    Routes.SETTINGS_NOTIFICATIONS,
    Routes.SETTINGS_SSH_CONNECTION,
    Routes.SETTINGS_SHELL_SESSION,
    Routes.SETTINGS_SECURITY_KEYS,
    Routes.SETTINGS_DEBUG_LOG,
    Routes.YAML_EDITOR)

private fun shouldShowBottomBar(currentRoute: String?): Boolean {
    if (currentRoute == null) return true
    return hideBottomBarRoutes.none { pattern ->
        currentRoute == pattern || currentRoute.startsWith(pattern.substringBefore("{"))
    }
}

// ---------------------------------------------------------------------------
// AppNavigation
// ---------------------------------------------------------------------------

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = shouldShowBottomBar(currentRoute)
    val tokens = MaterialTheme.appTokens

    // Host key verification dialog
    val context = LocalContext.current
    val app = context.applicationContext as? TmuxesApp
    val hostKeyPrompt = app?.hostKeyPrompt?.collectAsState()
    val promptState = hostKeyPrompt?.value

    if (promptState != null) {
        val event = promptState.event
        val (title, message) = when (event) {
            is HostKeyEvent.Unknown -> Pair(
                "Unknown Host Key",
                t(
                    "The server {host}:{port} presented an unknown {type} key.\n\nFingerprint:\n{fingerprint}\n\nDo you want to trust this key?",
                    "host" to event.hostname,
                    "port" to event.port,
                    "type" to event.keyType,
                    "fingerprint" to event.fingerprint
                )
            )
            is HostKeyEvent.Changed -> Pair(
                "Host Key Changed",
                t(
                    "WARNING: The {type} key for {host}:{port} has changed!\n\nOld fingerprint:\n{oldFingerprint}\n\nNew fingerprint:\n{newFingerprint}\n\nThis could indicate a security issue. Accept the new key?",
                    "type" to event.keyType,
                    "host" to event.hostname,
                    "port" to event.port,
                    "oldFingerprint" to event.oldFingerprint,
                    "newFingerprint" to event.newFingerprint
                )
            )
        }
        val acceptLabel = when (event) {
            is HostKeyEvent.Unknown -> "Trust & Remember"
            is HostKeyEvent.Changed -> "Accept New Key"
        }
        AppDialog(
            title = title,
            onDismiss = { app.respondToHostKeyPrompt(HostKeyPromptResult.REJECT) },
            confirmLabel = acceptLabel,
            onConfirm = { app.respondToHostKeyPrompt(HostKeyPromptResult.ACCEPT) },
            dismissLabel = "Reject",
            neutralLabel = "Trust Once",
            onNeutral = { app.respondToHostKeyPrompt(HostKeyPromptResult.TRUST_ONCE) },
            neutralStyle = AppButtonStyle.Outlined,
            content = {
                Text(
                    text = message,
                    style = MaterialTheme.appTokens.type.monoSmall,
                    color = MaterialTheme.appTokens.colors.onSurfaceVariant
                )
            }
        )
    }

    // NavHost is the screen-tree root. It is NOT wrapped in a Scaffold —
    // each destination's screen owns the single Scaffold via AppScaffold
    // (which is the sole consumer of `WindowInsets.systemBars`). HomeScreen
    // adds its own AppBottomBar / NavigationRail / Drawer outside any
    // Scaffold; drill-down routes simply have no bottom bar, which matches
    // the previous `hideBottomBarRoutes` behavior.
    NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colors.background),
            enterTransition = {
                fadeIn(animationSpec = tween(
                    durationMillis = tokens.motion.durationMedium2,
                    easing = tokens.motion.emphasizedDecelerate
                )) + slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = tokens.motion.durationMedium2,
                        easing = tokens.motion.emphasized
                    )
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(
                    durationMillis = tokens.motion.durationShort2,
                    easing = tokens.motion.emphasizedAccelerate
                )) + slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = tokens.motion.durationMedium1,
                        easing = tokens.motion.emphasizedAccelerate
                    )
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(
                    durationMillis = tokens.motion.durationMedium2,
                    easing = tokens.motion.emphasizedDecelerate
                )) + slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(
                        durationMillis = tokens.motion.durationMedium2,
                        easing = tokens.motion.emphasized
                    )
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(
                    durationMillis = tokens.motion.durationShort2,
                    easing = tokens.motion.emphasizedAccelerate
                )) + slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(
                        durationMillis = tokens.motion.durationMedium1,
                        easing = tokens.motion.emphasizedAccelerate
                    )
                )
            }
        ) {
            // --- HOME (Servers / Sessions / Settings in a pager) ---
            composable(
                route = Routes.HOME,
                arguments = listOf(
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                val initialPage = entry.arguments?.getInt("page") ?: 0
                com.tmuxes.ui.screens.home.HomeScreen(
                    initialPage = initialPage,
                    onAddServer = { navController.safeNavigate(Routes.ADD_SERVER) },
                    onServerClick = { id -> navController.safeNavigate(Routes.serverDetail(id)) },
                    onEditServer = { id -> navController.safeNavigate(Routes.editServer(id)) },
                    onOpenTerminal = { sid, session ->
                        navController.safeNavigate(Routes.terminal(sid, session))
                    },
                    onNavigateToAppAppearance = { navController.safeNavigate(Routes.SETTINGS_APP_APPEARANCE) },
                    onNavigateToTerminalAppearance = { navController.safeNavigate(Routes.SETTINGS_TERMINAL_APPEARANCE) },
                    onNavigateToTerminalInput = { navController.safeNavigate(Routes.SETTINGS_TERMINAL_INPUT) },
                    onNavigateToNotifications = { navController.safeNavigate(Routes.SETTINGS_NOTIFICATIONS) },
                    onNavigateToSshConnection = { navController.safeNavigate(Routes.SETTINGS_SSH_CONNECTION) },
                    onNavigateToShellSession = { navController.safeNavigate(Routes.SETTINGS_SHELL_SESSION) },
                    onNavigateToSecurityKeys = { navController.safeNavigate(Routes.SETTINGS_SECURITY_KEYS) },
                    onNavigateToSnippets = { navController.safeNavigate(Routes.SNIPPETS) },
                    onNavigateToYamlEditor = { navController.safeNavigate(Routes.yamlEditor(-1)) },
                    onNavigateToDebugLog = { navController.safeNavigate(Routes.SETTINGS_DEBUG_LOG) }
                )
            }

            // --- Server detail ---
            composable(
                route = Routes.SERVER_DETAIL,
                arguments = listOf(navArgument("serverId") { type = NavType.LongType })
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: return@composable
                ServerDetailScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.safePopBackStack() },
                    onEditServer = { id -> navController.safeNavigate(Routes.editServer(id)) },
                    onAddChildServer = { parentId -> navController.safeNavigate(Routes.addChildServer(parentId)) },
                    onImportConfigHost = { parentId, name, hostname, username, port ->
                        navController.safeNavigate(Routes.importConfigHost(parentId, name, hostname, username, port))
                    },
                    onNavigateToChild = { childId -> navController.safeNavigate(Routes.serverDetail(childId)) },
                    onOpenSessions = { id -> navController.safeNavigate(Routes.sessionPicker(id)) },
                    onNavigateToYamlEditor = { navController.safeNavigate(Routes.yamlEditor(-2)) }
                )
            }

            // --- Add server ---
            composable(Routes.ADD_SERVER) {
                AddEditServerScreen(
                    serverId = null,
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Add child server ---
            composable(
                route = Routes.ADD_CHILD_SERVER,
                arguments = listOf(navArgument("parentId") { type = NavType.LongType })
            ) { entry ->
                val parentId = entry.arguments?.getLong("parentId") ?: return@composable
                AddEditServerScreen(
                    serverId = null,
                    onNavigateBack = { navController.safePopBackStack() },
                    initialParentId = parentId
                )
            }

            // --- Import config host as child server ---
            composable(
                route = Routes.IMPORT_CONFIG_HOST,
                arguments = listOf(
                    navArgument("parentId") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("hostname") { type = NavType.StringType },
                    navArgument("username") { type = NavType.StringType },
                    navArgument("port") { type = NavType.IntType }
                )
            ) { entry ->
                val parentId = entry.arguments?.getLong("parentId") ?: return@composable
                // Uri.decode throws IllegalArgumentException on invalid %XX
                // escapes — guard every decode so a mal-formed deeplink
                // doesn't crash the composition.
                val name = safeUriDecode(entry.arguments?.getString("name") ?: "")
                val hostname = safeUriDecode(entry.arguments?.getString("hostname") ?: return@composable)
                val username = safeUriDecode(entry.arguments?.getString("username") ?: "_NONE_")
                val port = entry.arguments?.getInt("port") ?: 22
                AddEditServerScreen(
                    serverId = null,
                    onNavigateBack = { navController.safePopBackStack() },
                    initialParentId = parentId,
                    initialName = name.ifBlank { null },
                    initialHostname = hostname,
                    initialUsername = if (username == "_NONE_") null else username.ifBlank { null },
                    initialPort = port
                )
            }

            // --- Edit server ---
            composable(
                route = Routes.EDIT_SERVER,
                arguments = listOf(navArgument("serverId") { type = NavType.LongType })
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: return@composable
                AddEditServerScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Port forwarding ---
            composable(
                route = Routes.PORT_FORWARD,
                arguments = listOf(navArgument("serverId") { type = NavType.LongType })
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: return@composable
                PortForwardScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Sessions (single server, drill-down) ---
            // Note: The all-servers Sessions tab is now hosted by HomeScreen's
            // HorizontalPager at Routes.HOME; only the per-server drill-down
            // remains as its own nav route.
            composable(
                route = Routes.SESSION_PICKER_SERVER,
                arguments = listOf(navArgument("serverId") { type = NavType.LongType })
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: return@composable
                SessionPickerScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.safePopBackStack() },
                    onOpenTerminal = { sid, session ->
                        navController.safeNavigate(Routes.terminal(sid, session))
                    },
                    onNavigateToServerDetail = { sid ->
                        navController.safeNavigate(Routes.serverDetail(sid))
                    }
                )
            }

            // --- Terminal ---
            composable(
                route = Routes.TERMINAL,
                arguments = listOf(
                    navArgument("serverId") { type = NavType.LongType },
                    navArgument("sessionName") { type = NavType.StringType }
                )
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: return@composable
                val sessionName = safeUriDecode(entry.arguments?.getString("sessionName") ?: return@composable)

                // Observe snippet command returned from SnippetsScreen
                val snippetCommand = entry.savedStateHandle.get<String>("snippet_command")
                // See iter 42 — avoid the `as ComponentActivity` non-null
                // cast which ClassCastExceptions on wrapped contexts
                // (ContextThemeWrapper, preview, test) and kills the
                // composition on the main thread.
                val sessionVm = androidx.lifecycle.viewmodel.compose.viewModel<com.tmuxes.ui.viewmodel.SessionViewModel>(
                    viewModelStoreOwner = LocalContext.current.findComponentActivity()
                        ?: androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!
                )
                LaunchedEffect(snippetCommand) {
                    if (snippetCommand != null) {
                        entry.savedStateHandle.remove<String>("snippet_command")
                        sessionVm.sendInput(snippetCommand.toByteArray(Charsets.UTF_8))
                    }
                }

                TerminalScreen(
                    serverId = serverId,
                    sessionName = sessionName,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            (navController.context as? android.app.Activity)?.finish()
                        }
                    }
                )
            }

            composable(Routes.SETTINGS_APP_APPEARANCE) {
                AppAppearanceScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable(Routes.SETTINGS_TERMINAL_APPEARANCE) {
                TerminalAppearanceScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    onEditCustomScheme = { navController.safeNavigate(Routes.COLOR_SCHEME_EDITOR) }
                )
            }

            composable(Routes.SETTINGS_TERMINAL_INPUT) {
                TerminalInputScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable(Routes.SETTINGS_NOTIFICATIONS) {
                NotificationsScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable(Routes.SETTINGS_SSH_CONNECTION) {
                SshConnectionScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable(Routes.SETTINGS_SHELL_SESSION) {
                ShellSessionScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            composable(Routes.SETTINGS_SECURITY_KEYS) {
                SecurityKeysScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    onNavigateToKeyManager = { navController.safeNavigate(Routes.KEY_MANAGER) },
                    onNavigateToKnownHosts = { navController.safeNavigate(Routes.KNOWN_HOSTS) }
                )
            }

            // --- Debug Log (debug-build-only feature, route always present
            // so the no-op release build still compiles cleanly) ---
            composable(Routes.SETTINGS_DEBUG_LOG) {
                com.tmuxes.ui.screens.settings.DebugLogScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- YAML Editor ---
            composable(
                route = Routes.YAML_EDITOR,
                arguments = listOf(navArgument("widgetId") { type = NavType.IntType })
            ) { entry ->
                val widgetId = entry.arguments?.getInt("widgetId") ?: -1
                YamlEditorScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    widgetId = widgetId
                )
            }

            // --- Color Scheme Editor ---
            composable(Routes.COLOR_SCHEME_EDITOR) {
                ColorSchemeEditorScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Key Manager ---
            composable(Routes.KEY_MANAGER) {
                KeyManagerScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Snippets (library list) ---
            composable(Routes.SNIPPETS) {
                SnippetsScreen(
                    onNavigateBack = { navController.safePopBackStack() },
                    onNavigateToLibraryDetail = { id -> navController.safeNavigate(Routes.libraryDetail(id)) }
                )
            }

            // --- Library detail ---
            composable(
                route = Routes.LIBRARY_DETAIL,
                arguments = listOf(navArgument("libraryId") { type = NavType.LongType })
            ) { entry ->
                val libraryId = entry.arguments?.getLong("libraryId") ?: return@composable
                LibraryDetailScreen(
                    libraryId = libraryId,
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

            // --- Known Hosts ---
            composable(Routes.KNOWN_HOSTS) {
                KnownHostsScreen(
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }

        }
}

// ---------------------------------------------------------------------------
// NOTE: TmuxesBottomBar and TmuxesNavigationRail were removed — the
// HomeScreen hosts its own bottom bar / rail as part of the
// HorizontalPager scaffold (feature request T3).

/**
 * [Uri.decode] throws [IllegalArgumentException] on invalid percent-escape
 * sequences (e.g. `%XX` where `X` is not a hex digit). Any deeplink or
 * nav-arg constructed from unvalidated input could crash the composition
 * in onDraw time. This helper returns the raw input on failure so the
 * screen can at least render.
 */
private fun safeUriDecode(input: String): String {
    if (input.isEmpty()) return input
    return try {
        Uri.decode(input)
    } catch (_: Throwable) {
        input
    }
}
