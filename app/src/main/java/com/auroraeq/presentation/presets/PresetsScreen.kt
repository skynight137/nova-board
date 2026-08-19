package com.auroraeq.app.presentation.presets

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.auroraeq.app.domain.model.Preset
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassButton
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassPanel
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generous ceiling for a picked import file — a real preset export is at most a few KB per preset,
 * so this leaves an enormous margin while still guarding against an accidental huge-file pick (the
 * any-file-type picker below doesn't restrict size) OOM-ing the app on the `readText()` this
 * replaces. Enforced by actual bytes read, not a provider-reported size, since a content provider
 * can decline to report (or misreport) size.
 */
private const val MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024

/**
 * Save/load full [Preset] snapshots — every stage's settings together, not just the 31-band EQ
 * curve. This is the "Config" save/load replacement for the old Presets screen mentioned in the v2
 * refactor spec (section 7): applying one overwrites the *live* chain state through
 * [EqViewModel.applyPreset] (same funnel as every slider), it does not switch into a separate mode.
 */
@Composable
fun PresetsScreen(viewModel: EqViewModel) {
    val presets by viewModel.presets.collectAsState()
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Preset?>(null) }
    var deleteTarget by remember { mutableStateOf<Preset?>(null) }

    // "Export all" -> Storage Access Framework "save as" (first SAF use in
    // the project; per-preset Share below still goes through the same
    // FileProvider app-logging-and-export.md set up, since that's a
    // send-to-another-app action rather than a "pick where to save" one).
    val exportAllLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(viewModel.exportAllPresetsJson().toByteArray())
            }
            Toast.makeText(context, "Exported ${presets.size} preset(s).", Toast.LENGTH_SHORT)
                .show()
        }

    // "*/*" rather than "application/json" — a file shared from another
    // app's "Share" action, or saved by a generic file manager, isn't
    // reliably tagged with that MIME type. The JSON shape itself is what's
    // validated, in EqViewModel.importPresets -> parseImportedPresetsJson.
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val json =
                runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            readTextUpTo(it, MAX_IMPORT_FILE_BYTES)
                        }
                    }
                    .getOrNull()
            if (json == null) {
                Toast.makeText(
                        context,
                        "That file couldn't be read, or is too large to be a preset export.",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                return@rememberLauncherForActivityResult
            }
            if (json.isBlank()) {
                Toast.makeText(context, "Couldn't read the selected file.", Toast.LENGTH_SHORT)
                    .show()
                return@rememberLauncherForActivityResult
            }
            val count = viewModel.importPresets(json)
            if (count > 0) {
                Toast.makeText(context, "Imported $count preset(s).", Toast.LENGTH_SHORT).show()
            }
            // count == 0 means corrupt/unrecognized JSON, already reported via
            // EqRepository.errorEvents -> AppNavigation's shared Toast pipeline.
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Presets",
                color = GlassColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Row {
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = "Import preset(s)",
                        tint = GlassColors.TextSecondary,
                    )
                }
                IconButton(
                    onClick = { exportAllLauncher.launch("aurora-eq-presets.json") },
                    enabled = presets.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Export all presets",
                        tint =
                            if (presets.isEmpty()) GlassColors.TextSecondary.copy(alpha = 0.4f)
                            else GlassColors.TextSecondary,
                    )
                }
            }
        }
        Text(
            "Save the current settings across every stage — Preamp, HPF, Sub Shelf, EQ, " +
                "Air Shelf, LPF, Compressor, Limiter, Output Gain, and Spatial — as a named " +
                "preset, and load it back later. Loading a preset replaces your current " +
                "settings; it doesn't create a separate mode. Share a single preset, or " +
                "export/import all of them as a backup file, using the icons above and on " +
                "each preset below.",
            color = GlassColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        GlassButton(label = "Save current as new preset", onClick = { showSaveDialog = true })

        if (presets.isEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No presets saved yet.",
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            presets.forEach { preset ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            preset.name,
                            color = GlassColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.applyPreset(preset.id) }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Load ${preset.name}",
                                tint = GlassColors.Cyan,
                            )
                        }
                        IconButton(
                            onClick = {
                                val json =
                                    viewModel.exportPresetJson(preset.id) ?: return@IconButton
                                sharePreset(context, preset.name, json)
                            }
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share ${preset.name}",
                                tint = GlassColors.TextSecondary,
                            )
                        }
                        IconButton(onClick = { renameTarget = preset }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Rename ${preset.name}",
                                tint = GlassColors.TextSecondary,
                            )
                        }
                        IconButton(onClick = { deleteTarget = preset }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${preset.name}",
                                tint = GlassColors.Danger,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        NamePromptDialog(
            title = "Save preset",
            confirmLabel = "Save",
            initialName = "",
            onConfirm = { name ->
                viewModel.saveCurrentAsPreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    renameTarget?.let { preset ->
        NamePromptDialog(
            title = "Rename preset",
            confirmLabel = "Rename",
            initialName = preset.name,
            onConfirm = { name ->
                viewModel.renamePreset(preset.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${preset.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(preset.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = GlassColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Writes [json] (one preset's [com.auroraeq.app.data.store.presetToJson] shape) to this app's
 * private `filesDir/exports/` directory and hands it to any app via `ACTION_SEND`, through the same
 * `com.auroraeq.app.fileprovider` FileProvider `app-logging-and-export.md` set up for the
 * log-export action (see `res/xml/file_paths.xml`'s `exports` entry) — not a second provider.
 */
private fun sharePreset(context: Context, name: String, json: String) {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    // A previous share's file has already been handed off to (and presumably
    // consumed by) whatever app the user picked by the time a new share
    // happens — clean the directory out here instead of letting it grow
    // unbounded forever, since (unlike AppLog) nothing else ever rotates or deletes these.
    dir.listFiles()?.forEach { it.delete() }
    val safeName = name.trim().ifEmpty { "preset" }.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60)
    val file = File(dir, "$safeName.json")
    file.writeText(json)

    val uri = FileProvider.getUriForFile(context, "com.auroraeq.app.fileprovider", file)
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, "Share \"$name\""))
}

/**
 * Reads [input] as UTF-8 text, aborting (returning `null`) instead of finishing the read if more
 * than [maxBytes] have come through — guards against an accidentally-huge file OOM-ing the app on
 * import, enforced by actual bytes read rather than a content provider's (possibly absent or
 * inaccurate) reported size.
 */
private fun readTextUpTo(input: java.io.InputStream, maxBytes: Int): String? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0
    while (true) {
        val read = input.read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) return null
        buffer.write(chunk, 0, read)
    }
    return buffer.toString(Charsets.UTF_8.name())
}

/**
 * Shared save/rename name-entry dialog — a blank/whitespace-only name never confirms, so a preset
 * can't silently end up with an empty label.
 */
@Composable
private fun NamePromptDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary,
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
