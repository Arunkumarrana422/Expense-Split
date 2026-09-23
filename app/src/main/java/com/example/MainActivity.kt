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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import com.example.viewmodel.ExpenseViewModel
import kotlinx.coroutines.delay

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
            var showConnectedMessage by remember { mutableStateOf(false) }

            // Automatic detection and reload when connection state changes during runtime
            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    wasOffline = true
                    showConnectedMessage = false
                } else if (wasOffline) {
                    wasOffline = false
                    showConnectedMessage = true
                    viewModel.refreshData()
                    delay(3000)
                    showConnectedMessage = false
                }
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .let {
                            if (!isOnline) {
                                it.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { change -> change.consume() }
                                        }
                                    }
                                }
                            } else {
                                it
                            }
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isOnline) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(100f),
                                color = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                ) {
                                    Text(
                                        text = "No internet connection. Actions are disabled.",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (showConnectedMessage) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(100f),
                                color = Color(0xFF388E3C),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                ) {
                                    Text(
                                        text = "Internet connected. Loading data...",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavGraph(
                                viewModel = viewModel,
                                initialTargetScreen = initialDestination.value
                            )
                        }
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

