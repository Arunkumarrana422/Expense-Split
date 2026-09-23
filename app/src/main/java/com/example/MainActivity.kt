package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.NoInternetScreen
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import com.example.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ExpenseViewModel by viewModels()
    private val initialDestination = mutableStateOf<String?>(null)
    private lateinit var networkMonitor: NetworkMonitor

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        networkMonitor = NetworkMonitor(applicationContext)

        // Handle navigation from notification tap
        val navExtra = intent?.getStringExtra("navigate_to")
        if (navExtra != null) {
            initialDestination.value = navExtra
        }

        // Create notification channel
        NotificationHelper.initNotificationChannel(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> systemDark
            }

            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(
                initialValue = networkMonitor.isCurrentlyConnected()
            )
            var wasOffline by remember { mutableStateOf(!networkMonitor.isCurrentlyConnected()) }
            // Automatic detection and reload when connection state changes during runtime
            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    viewModel.refreshData()
                }
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                AnimatedContent(
                    targetState = isOnline,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "OnlineStateTransition"
                ) { online ->
                    if (online) {
                        AppNavGraph(
                            viewModel = viewModel,
                            initialTargetScreen = initialDestination.value
                        )
                    } else {
                        NoInternetScreen(
                            onRetry = {
                                if (networkMonitor.isCurrentlyConnected()) {
                                    viewModel.refreshData()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navExtra = intent.getStringExtra("navigate_to")
        if (navExtra != null) {
            initialDestination.value = navExtra
        }
    }

}

