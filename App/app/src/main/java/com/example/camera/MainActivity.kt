package com.example.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.camera.login.LoginScreen
import com.example.camera.ui.theme.CameraTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private var haveCameraPermissionsState = MutableStateFlow(false)
    private var isLoggedInState = MutableStateFlow(false)
    private var currentUsernameState = MutableStateFlow("")
    private var authTokenState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        val haveCameraPermissions by haveCameraPermissionsState.collectAsState()
                        val isLoggedIn by isLoggedInState.collectAsState()
                        val currentUsername by currentUsernameState.collectAsState()
                        val authToken by authTokenState.collectAsState()

                        when {
                            // Step 1: Show login screen if not logged in
                            !isLoggedIn -> {
                                LoginScreen(
                                    onLoginSuccess = { username, token ->
                                        currentUsernameState.value = username
                                        authTokenState.value = token
                                        isLoggedInState.value = true
                                        // After login, check permissions
                                        verifyPermissions()
                                    },
                                    onContinueAsGuest = {
                                        currentUsernameState.value = "Guest"
                                        authTokenState.value = ""
                                        isLoggedInState.value = true
                                        // After choosing guest, check permissions
                                        verifyPermissions()
                                    }
                                )
                            }
                            // Step 2: Request camera permissions if needed
                            !haveCameraPermissions -> {
                                Text("Requesting camera permissions...")
                            }
                            // Step 3: Show camera screen with token
                            else -> {
                                var showGallery by remember { mutableStateOf(false) }

                                val isLoggedIn = authToken.isNotEmpty()

                                if (showGallery && isLoggedIn) {
                                    PhotoGalleryScreen(
                                        token = authToken,
                                        username = currentUsername,
                                        onBackToCamera = { showGallery = false }
                                    )
                                } else {
                                    CameraScreen(
                                        onRequestPermission = { verifyPermissions() },
                                        username = currentUsername,
                                        token = authToken,
                                        isLoggedIn = isLoggedIn,
                                        onOpenGallery = {
                                            if (isLoggedIn) {
                                                showGallery = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun verifyPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            haveCameraPermissionsState.value = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            haveCameraPermissionsState.value = granted
        }
}