package com.talayeman.gold

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.talayeman.gold.service.NotificationHelper
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.navigation.GoldNavGraph
import com.talayeman.gold.ui.navigation.bottomNavItems
import com.talayeman.gold.ui.theme.GoldTheme
import com.talayeman.gold.util.BiometricHelper
import com.talayeman.gold.util.PinManager
import com.talayeman.gold.worker.DailyPriceNotificationWorker
import com.talayeman.gold.worker.PriceUpdateWorker

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val biometricEnabled by viewModel.biometricEnabled.collectAsState()
            var unlocked by remember { mutableStateOf(!biometricEnabled) }

            GoldTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (!unlocked && biometricEnabled) {
                        BiometricLockScreen(
                            onUnlocked = { unlocked = true },
                            activity = this@MainActivity
                        )
                    } else {
                        MainScaffold(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlocked: () -> Unit,
    activity: FragmentActivity
) {
    val pinManager = remember { PinManager(activity) }
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var usePin by remember { mutableStateOf(!BiometricHelper.canAuthenticate(activity) || !pinManager.hasPin()) }

    LaunchedEffect(Unit) {
        if (BiometricHelper.canAuthenticate(activity) && pinManager.hasPin()) {
            BiometricHelper.authenticate(
                activity = activity,
                title = "ورود به طلای من",
                subtitle = "با اثر انگشت تأیید کنید یا از PIN استفاده کنید",
                onSuccess = onUnlocked,
                onError = { usePin = true },
                onFailed = { usePin = true }
            )
        } else if (!pinManager.hasPin()) {
            // No PIN and no biometric configured properly — unlock
            onUnlocked()
        } else {
            usePin = true
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (usePin && pinManager.hasPin()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("کد PIN چهار رقمی", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pinInput = it
                            error = null
                        }
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    )
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    if (pinManager.verifyPin(pinInput)) onUnlocked()
                    else {
                        error = "PIN نادرست است"
                        pinInput = ""
                    }
                }) {
                    Text("ورود")
                }
                if (BiometricHelper.canAuthenticate(activity)) {
                    TextButton(onClick = {
                        BiometricHelper.authenticate(
                            activity = activity,
                            onSuccess = onUnlocked,
                            onError = {},
                            onFailed = {}
                        )
                    }) {
                        Text("استفاده از اثر انگشت")
                    }
                }
            }
        }
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
                NavigationBar {
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
                            label = null
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            GoldNavGraph(navController)
        }
    }
}
