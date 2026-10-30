package com.afterlater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.afterlater.AfterLaterApplication
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterLaterTheme
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Text
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import com.afterlater.feature.archive.ArchiveScreen
import com.afterlater.feature.archive.ArchiveViewModel
import com.afterlater.feature.capture.QuickAddScreen
import com.afterlater.feature.capture.QuickAddViewModel
import com.afterlater.feature.settings.SettingsScreen
import com.afterlater.feature.settings.SettingsViewModel
import com.afterlater.feature.itemdetail.ItemDetailScreen
import com.afterlater.feature.itemdetail.ItemDetailViewModel
import com.afterlater.feature.today.TodayScreen
import com.afterlater.feature.today.TodayViewModel
import com.afterlater.feature.welcome.WelcomeScreen
import kotlinx.coroutines.launch

private const val ROUTE_TODAY = "today"
private const val ROUTE_ARCHIVE = "archive"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_QUICK_ADD = "quick-add"
private const val ROUTE_ITEM = "item/{itemId}"

private val topLevelRoutes = setOf(ROUTE_TODAY, ROUTE_ARCHIVE, ROUTE_SETTINGS)

@Composable
fun AfterLaterRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AfterLaterApplication
    // Null until DataStore's first value arrives, so the welcome screen never
    // flashes over an already-onboarded install.
    val settings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val scope = rememberCoroutineScope()

    AfterLaterTheme(theme = settings?.theme ?: AppTheme.INK) {
        val current = settings
        when {
            current == null -> Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalAfterLaterColors.current.background),
            )

            current.onboardingDone -> content()

            else -> WelcomeScreen(
                clipboardCaptureEnabled = current.clipboardCaptureEnabled,
                onSetClipboardCapture = { enabled ->
                    scope.launch {
                        runCatching { app.container.settingsRepository.setClipboardCaptureEnabled(enabled) }
                    }
                },
                onDone = {
                    scope.launch {
                        runCatching { app.container.settingsRepository.completeOnboarding() }
                    }
                },
            )
        }
    }
}

@Composable
fun AfterLaterApp(
    deepLinkItemId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    quickAddRequested: Boolean = false,
    onQuickAddConsumed: () -> Unit = {},
    openTodayRequested: Boolean = false,
    onOpenTodayConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes
    val colors = LocalAfterLaterColors.current

    LaunchedEffect(deepLinkItemId) {
        if (!deepLinkItemId.isNullOrBlank()) {
            navController.navigate("item/$deepLinkItemId") { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(quickAddRequested) {
        if (quickAddRequested) {
            navController.navigate(ROUTE_QUICK_ADD) { launchSingleTop = true }
            onQuickAddConsumed()
        }
    }

    LaunchedEffect(openTodayRequested) {
        if (openTodayRequested) {
            navController.navigateTopLevel(ROUTE_TODAY)
            onOpenTodayConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                AfterBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                    onAdd = { navController.navigate(ROUTE_QUICK_ADD) { launchSingleTop = true } },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_TODAY,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(ROUTE_TODAY) {
                TodayScreen(
                    viewModel = viewModel(factory = TodayViewModel.Factory),
                    onOpenQuickAdd = { navController.navigate(ROUTE_QUICK_ADD) { launchSingleTop = true } },
                    onOpenArchive = { navController.navigateTopLevel(ROUTE_ARCHIVE) },
                    onOpenItem = { id -> navController.navigate("item/$id") },
                )
            }
            composable(ROUTE_ARCHIVE) {
                ArchiveScreen(
                    viewModel = viewModel(factory = ArchiveViewModel.Factory),
                    onOpenItem = { id -> navController.navigate("item/$id") },
                    onSaveNew = { navController.navigate(ROUTE_QUICK_ADD) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel = viewModel(factory = SettingsViewModel.Factory))
            }
            composable(ROUTE_QUICK_ADD) {
                QuickAddScreen(
                    viewModel = viewModel(factory = QuickAddViewModel.Factory),
                    onBack = { navController.popBackStack() },
                    onViewItem = { id ->
                        navController.navigate("item/$id") {
                            popUpTo(ROUTE_QUICK_ADD) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = ROUTE_ITEM,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId").orEmpty()
                ItemDetailScreen(
                    viewModel = viewModel(
                        key = itemId,
                        factory = ItemDetailViewModel.factory(itemId),
                    ),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AfterBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(
                label = "TODAY",
                selected = currentRoute == ROUTE_TODAY,
                onClick = { onNavigate(ROUTE_TODAY) },
                icon = {
                    Icon(
                        imageVector = AfterIcons.Clock,
                        contentDescription = null,
                        tint = if (currentRoute == ROUTE_TODAY) colors.accent else colors.textDim,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            BottomItem(
                label = "ARCHIVE",
                selected = currentRoute == ROUTE_ARCHIVE,
                onClick = { onNavigate(ROUTE_ARCHIVE) },
                icon = {
                    Icon(
                        imageVector = AfterIcons.ArchiveBox,
                        contentDescription = null,
                        tint = if (currentRoute == ROUTE_ARCHIVE) colors.accent else colors.textDim,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            AddAction(onClick = onAdd, modifier = Modifier.weight(1f))
            BottomItem(
                label = "SETTINGS",
                selected = currentRoute == ROUTE_SETTINGS,
                onClick = { onNavigate(ROUTE_SETTINGS) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (currentRoute == ROUTE_SETTINGS) colors.accent else colors.textDim,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(20.dp)
                .height(2.dp)
                .background(if (selected) colors.accent else Color.Transparent),
        )
        Spacer(Modifier.height(8.dp))
        icon()
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            AfterType.MonoSmall.copy(letterSpacing = 1.1.sp),
            if (selected) colors.accent else colors.textDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.accent, RoundedCornerShape(1.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.accentInk,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("ADD", AfterType.MonoSmall, colors.accent, maxLines = 1)
    }
}
