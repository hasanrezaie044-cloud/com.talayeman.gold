package com.talayeman.gold

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.talayeman.gold.service.NotificationHelper
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.auth.LockScreen
import com.talayeman.gold.ui.navigation.GoldNavGraph
import com.talayeman.gold.ui.navigation.bottomNavItems
import com.talayeman.gold.ui.theme.GoldTheme
import com.talayeman.gold.util.PinManager
import com.talayeman.gold.worker.DailyPriceNotificationWorker
import com.talayeman.gold.worker.PriceUpdateWorker

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /**
     * True while the app must show the login screen. Decided synchronously from the secure store
     * (NOT from an async settings flow), so protected content is never composed before login.
     */
    private var locked by mutableStateOf(false)
    private var stoppedAtElapsed = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locked = PinManager.get(this).hasPin()

        NotificationHelper.createChannels(this)
        PriceUpdateWorker.enqueue(this)
        DailyPriceNotificationWorker.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            GoldTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (locked) {
                        LockScreen(
                            activity = this@MainActivity,
                            onUnlocked = {
                                stoppedAtElapsed = 0L
                                locked = false
                            }
                        )
                    } else {
                        MainScaffold(viewModel)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAtElapsed = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock when the app was in the background for more than 30 seconds.
        if (stoppedAtElapsed != 0L &&
            SystemClock.elapsedRealtime() - stoppedAtElapsed > RELOCK_AFTER_MS &&
            PinManager.get(this).hasPin()
        ) {
            locked = true
        }
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            GoldNavGraph(navController)
        }
    }
}
