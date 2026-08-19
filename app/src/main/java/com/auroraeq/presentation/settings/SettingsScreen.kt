package com.auroraeq.app.presentation.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.auroraeq.app.BuildConfig
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassButton
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassPanel
import com.auroraeq.app.service.GlobalEqService
import com.auroraeq.app.util.AppLog
import com.auroraeq.app.util.buildLogShareIntent
import com.auroraeq.app.util.buildReleasePageIntent

@Composable
fun SettingsScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Settings",
            color = GlassColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "System-wide processing",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Aurora EQ is always on — it attaches its processing chain to the shared " +
                        "output mix (audio session 0) as soon as the app starts, no toggle " +
                        "needed. Android does not let non-root apps intercept all system audio " +
                        "the way iOS or desktop system equalizers can — this is a platform " +
                        "security restriction, not a limitation of this app. Many devices route " +
                        "most apps' sound through session 0, but behavior varies by " +
                        "manufacturer and Android version; it won't affect system UI sounds, " +
                        "calls, or every hardware-accelerated audio path.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    "To fully stop Aurora EQ, use system Settings → Apps → Aurora EQ → " +
                        "Force stop. There is no in-app or notification stop control — on some " +
                        "phones, manufacturer battery-saving policies silently disable a " +
                        "notification's stop button once the app is swiped away from Recents, " +
                        "so Force stop is the one way that reliably works on every device.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    when {
                        !state.engineReady -> "○ Waiting to attach…"
                        !state.dspAvailable -> "● Spatial only — full EQ needs Android 9+"
                        else -> "● Processing chain attached"
                    },
                    color = if (state.engineReady) GlassColors.Cyan else GlassColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // Covers the gap where tapping "Stop" on the notification stops the
                // service but not the app process. The service retries its attach in
                // onStartCommand(), including when it is already running.
                if (!state.engineReady) {
                    GlassButton(
                        label = "Resume processing",
                        onClick = {
                            context.startForegroundService(
                                Intent(context, GlobalEqService::class.java)
                            )
                        },
                    )
                }
            }
        }

        if (!state.dspAvailable) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Limited processing on this device",
                        color = GlassColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "This device is running an Android version older than 9.0, which doesn't " +
                            "support the platform effect Aurora EQ needs for Preamp, HPF, LPF, Sub " +
                            "Shelf, the 31-band EQ, Air Shelf, Compressor, Limiter, and Output Gain. " +
                            "Those stages are inactive here — only Spatial (Virtualizer) and the " +
                            "volume control still work. Your other settings are still saved and " +
                            "will take effect automatically on a device running Android 9 or newer.",
                        color = GlassColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Processing settings",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Restore every audio-processing stage to its default setting. Your system media " +
                        "volume and saved presets will not be changed.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
                GlassButton(
                    label = "Reset all processing",
                    onClick = { showResetDialog = true },
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Updates and support",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Check the Aurora EQ release page for the latest APK. Updates are installed " +
                        "manually so Android keeps the install under your control.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
                GlassButton(
                    label = "Check for updates",
                    onClick = {
                        try {
                            context.startActivity(
                                buildReleasePageIntent(BuildConfig.RELEASE_PAGE_URL)
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                    context,
                                    "Could not open the Aurora EQ release page.",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    },
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "About HPF / LPF",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Android has no dedicated steep-slope high-pass/low-pass filter effect " +
                        "available to a non-root app. The HPF and LPF stages on the EQ screen " +
                        "are approximated as a gain roll-off blended into the nearby 31-band EQ " +
                        "bands, not a true filter — a real Butterworth-style filter would " +
                        "require custom native audio processing that can't attach to the " +
                        "shared system output.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Pro Mode (root)",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Not available in this build. A rooted device could run a HAL/driver-level " +
                        "module for true system-wide processing, similar to Wavelet or " +
                        "ViPER4Android — a possible future addition, gated behind explicit " +
                        "opt-in and root detection.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "App log",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Aurora EQ keeps a local log file (not sent anywhere automatically) " +
                        "covering normal activity, warnings, and crashes, for troubleshooting " +
                        "a real problem after the fact. Export it to send to the developer.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
                GlassButton(
                    label = "Export log",
                    onClick = {
                        val logFile = AppLog.currentLogFile()
                        if (logFile == null) {
                            Toast.makeText(context, "Nothing logged yet.", Toast.LENGTH_SHORT)
                                .show()
                            return@GlassButton
                        }
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "com.auroraeq.app.fileprovider",
                                logFile,
                            )
                        context.startActivity(
                            Intent.createChooser(
                                buildLogShareIntent(uri),
                                "Export Aurora EQ log",
                            )
                        )
                    },
                )
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "About",
                    color = GlassColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Aurora EQ — Liquid Glass equalizer for Android.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all processing?") },
            text = {
                Text(
                    "All live audio stages will return to their default settings. " +
                        "Your system volume and saved presets will remain unchanged."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllProcessing()
                        showResetDialog = false
                        Toast.makeText(context, "Processing reset to defaults.", Toast.LENGTH_SHORT)
                            .show()
                    }
                ) {
                    Text("Reset", color = GlassColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}
