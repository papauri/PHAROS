package org.pharos.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pharos.app.ui.PharosCockpitScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PharosViewModel
    private val volumeDownTimestamps = ArrayDeque<Long>(4)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated after Prominent Disclosure
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PharosViewModel(applicationContext) as T
                }
            }
        )[PharosViewModel::class.java]

        setContent {
            PharosCockpitScreen(
                viewModel = viewModel,
                onRequestRuntimePermission = { category -> requestPermissionsForCategory(category) },
                onOpenDndPolicySettings = { openDndPolicySettingsSafely() }
            )
        }
    }

    /**
     * Triple-press Volume-Down within 1,500ms triggers instant Stealth Screen Blackout.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = System.currentTimeMillis()
            volumeDownTimestamps.addLast(now)
            while (volumeDownTimestamps.size > 3) {
                volumeDownTimestamps.removeFirst()
            }
            if (volumeDownTimestamps.size == 3 && (now - volumeDownTimestamps.first()) <= 1500L) {
                volumeDownTimestamps.clear()
                val currentlyBlackedOut = viewModel.uiState.value.isStealthBlackoutActive
                viewModel.setStealthBlackout(!currentlyBlackedOut)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissionsForCategory(category: String) {
        val perms = when (category) {
            "CAMERA" -> arrayOf(Manifest.permission.CAMERA)
            "AUDIO" -> arrayOf(Manifest.permission.RECORD_AUDIO)
            else -> buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        }
        permissionLauncher.launch(perms)
    }

    private fun openDndPolicySettingsSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        } catch (_: Throwable) {
        }
    }
}
