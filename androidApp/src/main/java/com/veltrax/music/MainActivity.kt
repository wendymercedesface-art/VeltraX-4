package com.veltrax.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veltrax.music.ui.VeltraXScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: MainViewModel = viewModel()
            var hasAudio by remember { mutableStateOf(hasAudioPermission()) }
            var hasNotif by remember { mutableStateOf(hasNotificationPermission()) }

            val audioLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasAudio = granted
                if (granted) model.refreshSongs()
            }

            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasNotif = granted
            }

            LaunchedEffect(hasAudio) {
                if (hasAudio) model.refreshSongs()
            }

            // Ask for notifications once (needed for download + media progress on Android 13+).
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && !hasNotif) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            VeltraXScreen(
                model = model,
                hasPermission = hasAudio,
                onPermission = {
                    val perm = if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    audioLauncher.launch(perm)
                }
            )
        }
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
