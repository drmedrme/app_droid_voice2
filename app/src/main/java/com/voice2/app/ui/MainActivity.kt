package com.voice2.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.ThemeMode
import com.voice2.app.ui.chat.ChatViewModel
import com.voice2.app.ui.chat.createImageUri
import com.voice2.app.ui.navigation.Voice2NavHost
import com.voice2.app.ui.navigation.bottomNavItems
import com.voice2.app.ui.theme.Voice2Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settingsPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            Voice2Theme(themeMode = themeMode) {
                Voice2App()
            }
        }
    }
}

@Composable
fun Voice2App() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    val chatViewModel: ChatViewModel = hiltViewModel()
    val isRecording by chatViewModel.isRecording.collectAsState()

    // After photo is taken, check mic permission then start recording
    val postPhotoMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) chatViewModel.toggleRecording()
        else chatViewModel.setLastPhotoUri(null)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val hasMic = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                chatViewModel.toggleRecording()
            } else {
                postPhotoMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            chatViewModel.setLastPhotoUri(null)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            val uri = createImageUri(context)
            if (uri != null) {
                chatViewModel.setLastPhotoUri(uri)
                takePictureLauncher.launch(uri)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) chatViewModel.toggleRecording() }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(500), repeatMode = RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val fabColor by animateColorAsState(
        targetValue = if (isRecording) Color.Red else MaterialTheme.colorScheme.primaryContainer,
        label = "fab_color"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (!isRecording) {
                    FloatingActionButton(
                        onClick = {
                            val hasCamera = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasMic = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera && hasMic) {
                                val uri = createImageUri(context)
                                if (uri != null) {
                                    chatViewModel.setLastPhotoUri(uri)
                                    takePictureLauncher.launch(uri)
                                }
                            } else {
                                // Request any missing permissions, then camera flow continues
                                val needed = mutableListOf<String>()
                                if (!hasCamera) needed.add(Manifest.permission.CAMERA)
                                if (!hasMic) needed.add(Manifest.permission.RECORD_AUDIO)
                                cameraPermissionLauncher.launch(needed.toTypedArray())
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Photo & Dictate")
                    }
                }

                FloatingActionButton(
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) chatViewModel.toggleRecording()
                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    containerColor = fabColor,
                    modifier = if (isRecording) Modifier.scale(scale) else Modifier
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record"
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Voice2NavHost(
            navController = navController,
            chatViewModel = chatViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
