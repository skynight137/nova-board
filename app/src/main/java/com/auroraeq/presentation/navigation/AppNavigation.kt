package com.auroraeq.app.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auroraeq.app.domain.model.SignalChainState
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.presets.PresetsScreen
import com.auroraeq.app.presentation.settings.SettingsScreen
import com.auroraeq.app.presentation.stages.AirShelfScreen
import com.auroraeq.app.presentation.stages.CompressorScreen
import com.auroraeq.app.presentation.stages.EqScreen
import com.auroraeq.app.presentation.stages.HpfScreen
import com.auroraeq.app.presentation.stages.LimiterScreen
import com.auroraeq.app.presentation.stages.LpfScreen
import com.auroraeq.app.presentation.stages.OutputGainScreen
import com.auroraeq.app.presentation.stages.PreampScreen
import com.auroraeq.app.presentation.stages.SpatialScreen
import com.auroraeq.app.presentation.stages.SubShelfScreen
import com.auroraeq.app.presentation.stages.VolumeScreen
import com.auroraeq.app.presentation.theme.GlassColors
import kotlinx.coroutines.launch

/**
 * The two top-level menus. The menu sheet is the only way to switch between them — swipe never
 * crosses this boundary (refactor spec section 6).
 */
private enum class Group(val label: String) {
    AUDIO_MANAGEMENT("Audio Management"),
    SETTINGS("Settings"),
}

/**
 * One navigable child screen: its menu label, its content, and — for stages with a bypass concept —
 * how to read its enabled flag off the current chain. Pairing label/enabled-lookup/content in a
 * single list (rather than three parallel lists plus a page-index `when`) makes it impossible for
 * them to drift out of sync, and page lookups are a plain indexed access instead of an unchecked
 * `when` with an `else` fallback.
 */
private class Stage(
    val label: String,
    val enabled: ((SignalChainState) -> Boolean)? = null,
    val content: @Composable (EqViewModel) -> Unit,
)

/**
 * Audio Management's children, in signal-chain order (refactor spec sections 5 and 10). Volume is
 * the earliest step — it controls the real system volume ahead of Preamp, not a DynamicsProcessing
 * stage, and has no enabled/bypass concept. Settings has a single "General" child for now.
 */
private val audioStages =
    listOf(
        Stage("Volume") { VolumeScreen(it) },
        Stage("Preamp", enabled = { it.preamp.enabled }) { PreampScreen(it) },
        Stage("HPF", enabled = { it.hpf.enabled }) { HpfScreen(it) },
        Stage("Sub Shelf", enabled = { it.subShelf.enabled }) { SubShelfScreen(it) },
        Stage("EQ", enabled = { it.eq.enabled }) { EqScreen(it) },
        Stage("Air Shelf", enabled = { it.airShelf.enabled }) { AirShelfScreen(it) },
        Stage("LPF", enabled = { it.lpf.enabled }) { LpfScreen(it) },
        Stage("Compressor", enabled = { it.compressor.enabled }) { CompressorScreen(it) },
        Stage("Limiter", enabled = { it.limiter.enabled }) { LimiterScreen(it) },
        Stage("Output Gain", enabled = { it.outputGain.enabled }) { OutputGainScreen(it) },
        Stage("Spatial", enabled = { it.spatial.enabled }) { SpatialScreen(it) },
    )
private val settingsStages =
    listOf(
        Stage("General") { SettingsScreen(it) },
        Stage("Presets") { PresetsScreen(it) },
    )
private val audioStageLabels = audioStages.map { it.label }
private val settingsStageLabels = settingsStages.map { it.label }

/**
 * Full-screen content plus a bottom-left menu button that opens a bottom sheet with the collapsible
 * navigation menu grouping sections into "Audio Management" (the 10 DSP chain stages, in order) and
 * "Settings" (General). The button sits in the bottom-left corner — within easy thumb reach —
 * instead of the old top bar. Swipe navigation is scoped to whichever group is currently selected —
 * each group owns its own [HorizontalPager]/[androidx.compose.foundation.pager.PagerState], so
 * swiping through the chain never leaks into Settings or back (refactor spec section 6). There is
 * no Player entry — local playback UI was removed in the v2 refactor. Presets (Settings > Presets)
 * returned as a full-chain save/load feature (refactor spec section 7's deferred "Config" feature),
 * distinct from the old per-EQ-curve presets screen it replaces.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val eqViewModel: EqViewModel =
        viewModel(
            factory = EqViewModel.factory(context.applicationContext as android.app.Application)
        )
    val eqUiState by eqViewModel.uiState.collectAsState()

    // Surfaces engine-attach/persistence failures as a Toast instead of only
    // `Log.w` — collected once at the top level so it fires regardless of
    // which stage screen is currently visible (refactor: better error UX).
    LaunchedEffect(eqViewModel) {
        eqViewModel.errorEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    var selectedGroup by remember { mutableStateOf(Group.AUDIO_MANAGEMENT) }
    val audioPagerState = rememberPagerState(initialPage = 0) { audioStageLabels.size }
    val settingsPagerState = rememberPagerState(initialPage = 0) { settingsStageLabels.size }
    val scope = rememberCoroutineScope()

    // Which Audio Management stages are currently enabled, keyed by their
    // menu label — Volume has no enabled concept (its `enabled` lookup is
    // null) and is left out of the map entirely, so it never shows a check
    // (refactor request: menu should make it easy to see what's on at a
    // glance). Derived straight from `audioStages`, so it can never drift
    // out of sync with the pager content below.
    val stageEnabled =
        remember(eqUiState) {
            val chain = eqUiState.chain
            audioStages
                .mapNotNull { stage -> stage.enabled?.let { stage.label to it(chain) } }
                .toMap()
        }

    val currentLabel =
        when (selectedGroup) {
            Group.AUDIO_MANAGEMENT -> audioStages[audioPagerState.currentPage].label
            Group.SETTINGS -> settingsStages[settingsPagerState.currentPage].label
        }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (selectedGroup) {
            Group.AUDIO_MANAGEMENT ->
                HorizontalPager(state = audioPagerState, modifier = Modifier.fillMaxSize()) { page
                    ->
                    audioStages[page].content(eqViewModel)
                }
            Group.SETTINGS ->
                HorizontalPager(state = settingsPagerState, modifier = Modifier.fillMaxSize()) {
                    page ->
                    settingsStages[page].content(eqViewModel)
                }
        }

        BottomLeftMenu(currentLabel = currentLabel, stageEnabled = stageEnabled) { group, page ->
            selectedGroup = group
            scope.launch {
                when (group) {
                    Group.AUDIO_MANAGEMENT -> audioPagerState.scrollToPage(page)
                    Group.SETTINGS -> settingsPagerState.scrollToPage(page)
                }
            }
        }
    }
}

/**
 * Bottom-left floating menu button that reveals a modal bottom sheet containing the collapsible
 * navigation menu (refactor spec section 6 — moved from the old top-bar dropdown for easier
 * one-handed thumb reach).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.BottomLeftMenu(
    currentLabel: String,
    stageEnabled: Map<String, Boolean>,
    onNavigate: (Group, Int) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    FloatingActionButton(
        onClick = { sheetOpen = true },
        containerColor = GlassColors.BackgroundDeep,
        contentColor = GlassColors.TextPrimary,
        shape = CircleShape,
        modifier =
            Modifier.align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 24.dp)
                .size(56.dp),
    ) {
        Icon(Icons.Filled.Menu, contentDescription = "Navigation menu")
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = GlassColors.BackgroundDeep,
        ) {
            CollapsibleMenuContent(
                currentLabel = currentLabel,
                stageEnabled = stageEnabled,
                onNavigate = { group, page ->
                    sheetOpen = false
                    onNavigate(group, page)
                },
            )
        }
    }
}

/**
 * Breadcrumb-style collapsible menu: each group is a collapsible header ("[Audio Management] >" /
 * "[Settings] >") that expands to reveal its children. Only one group is expanded by default
 * (whichever is active) to keep the sheet compact and clean. Each Audio Management child shows a
 * small check mark when its stage is currently enabled — and nothing at all when it's off — so the
 * whole chain's on/off state is visible at a glance without opening every screen.
 */
@Composable
private fun CollapsibleMenuContent(
    currentLabel: String,
    stageEnabled: Map<String, Boolean>,
    onNavigate: (Group, Int) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CollapsibleGroup(
            group = Group.AUDIO_MANAGEMENT,
            labels = audioStageLabels,
            currentLabel = currentLabel,
            stageEnabled = stageEnabled,
            initiallyExpanded = audioStageLabels.contains(currentLabel),
            onNavigate = onNavigate,
        )
        Spacer(modifier = Modifier.height(4.dp))
        CollapsibleGroup(
            group = Group.SETTINGS,
            labels = settingsStageLabels,
            currentLabel = currentLabel,
            stageEnabled = emptyMap(),
            initiallyExpanded = settingsStageLabels.contains(currentLabel),
            onNavigate = onNavigate,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CollapsibleGroup(
    group: Group,
    labels: List<String>,
    currentLabel: String,
    stageEnabled: Map<String, Boolean>,
    initiallyExpanded: Boolean,
    onNavigate: (Group, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .animateContentSize()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                "[${group.label}]",
                color = GlassColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = GlassColors.TextSecondary,
            )
        }

        if (expanded) {
            labels.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onNavigate(group, index) }
                            .padding(start = 28.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        label,
                        color =
                            if (label == currentLabel && labels.contains(currentLabel))
                                GlassColors.Cyan
                            else GlassColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight =
                            if (label == currentLabel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (stageEnabled[label] == true) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Enabled",
                            tint = GlassColors.Cyan,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
