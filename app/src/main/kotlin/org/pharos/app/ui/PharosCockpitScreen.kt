package org.pharos.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.pharos.app.data.SurvivalCodexRepository
import org.pharos.app.data.SurvivalGuideEntry
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharos.app.PharosUiState
import org.pharos.app.PharosViewModel
import org.pharos.app.TacticalTab
import org.pharos.core.optical.OpticalModemEngine
import org.pharos.core.protocol.TacticalCard

@Composable
fun PharosCockpitScreen(
    viewModel: PharosViewModel,
    onRequestRuntimePermission: (String) -> Unit,
    onOpenDndPolicySettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val palette = getNightOpsPalette(state.isRed660nmNightVision)
    var showPinDialog by remember { mutableStateOf(false) }

    // Stealth Blackout Screen (Triple-Press Volume Down)
    if (state.isStealthBlackoutActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { viewModel.setStealthBlackout(false) },
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(modifier = Modifier.size(1.dp).background(Color(0xFF330000)))
        }
        return
    }

    PharosNightOpsTheme(isRed660nmMode = state.isRed660nmNightVision) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = palette.bgPureBlack,
                topBar = {
                    SimpleTopBar(
                        state = state,
                        palette = palette,
                        onToggleRedMode = { viewModel.toggleNightVision660nm() },
                        onOpenLock = { showPinDialog = true },
                        onStartGuide = { viewModel.startWalkthrough() }
                    )
                },
                bottomBar = {
                    SimpleBottomBar(
                        selectedTab = state.selectedTab,
                        palette = palette,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.bgPureBlack)
                        .padding(innerPadding)
                ) {
                    when (state.selectedTab) {
                        TacticalTab.BEAM -> SimpleBeamTab(
                            state = state,
                            palette = palette,
                            onSelectTxMode = { viewModel.setOpticalTxMode(it) },
                            onSendLight = { viewModel.triggerOpticalBeamCycle() },
                            onRequestCamera = { viewModel.showProminentDisclosure("CAMERA") }
                        )
                        TacticalTab.PULSE -> SimplePulseTab(
                            state = state,
                            palette = palette,
                            onSelectTrusted = { viewModel.selectFeedFilter(it) },
                            onSendCard = { tier, note -> viewModel.broadcastNewTacticalCard(tier, note) },
                            onPlayVoice = { id -> viewModel.playCodec2VoiceInRam(id) }
                        )
                        TacticalTab.BRIDGE -> SimpleBridgeTab(
                            state = state,
                            palette = palette,
                            onToggleHotspot = { viewModel.toggleHotspotServer() },
                            onSendRadioAudio = { viewModel.transmitWalkieTalkieMfskAndDtmfTest() },
                            onPairPhrase = { phrase -> viewModel.runSpake2MemoryPairing(phrase) },
                            onCycleDnd = { viewModel.cycleDndStealthMode() },
                            onOpenDndSettings = onOpenDndPolicySettings
                        )
                        TacticalTab.SURVIVAL -> SimpleSurvivalManualTab(
                            palette = palette
                        )
                    }
                }
            }

            // First-Launch Thorough Interactive Walkthrough with Pointers
            if (state.isWalkthroughActive) {
                FirstLaunchWalkthroughOverlay(
                    stepIndex = state.walkthroughStepIndex,
                    palette = palette,
                    onNext = { viewModel.nextWalkthroughStep(WALKTHROUGH_STEPS.size) },
                    onBack = { viewModel.previousWalkthroughStep() },
                    onSkip = { viewModel.completeWalkthrough() }
                )
            }
        }

        if (showPinDialog) {
            SimplePinDialog(
                palette = palette,
                onDismiss = { showPinDialog = false },
                onSubmitPin = { pin ->
                    viewModel.submitVaultPin(pin)
                    showPinDialog = false
                }
            )
        }

        state.prominentDisclosureType?.let { permType ->
            SimplePermissionDialog(
                permissionCategory = permType,
                palette = palette,
                onDismiss = { viewModel.showProminentDisclosure(null) },
                onAccept = {
                    viewModel.showProminentDisclosure(null)
                    onRequestRuntimePermission(permType)
                }
            )
        }
    }
}

private enum class PointerZone {
    TOP_BAR_RIGHT,
    TOP_BAR_LOCK,
    CENTER_VIEWFINDER,
    BOTTOM_LIGHT_CONTROLS,
    TOP_MAP_AND_FILTERS,
    BOTTOM_MESSAGE_COMPOSER,
    TOP_HOTSPOT_QR,
    BOTTOM_RADIO_AND_PAIR
}

private data class WalkthroughStepSpec(
    val title: String,
    val pointerLabel: String,
    val bodyPoints: List<String>,
    val pointerZone: PointerZone,
    val cardAtBottom: Boolean
)

private val WALKTHROUGH_STEPS = listOf(
    WalkthroughStepSpec(
        title = "1 of 8 • Welcome & Red Night Screen",
        pointerLabel = "POINTS TO: Red ON/OFF & Sync Timer",
        bodyPoints = listOf(
            "PHAROS works with zero internet and zero cell towers.",
            "Tap 'Red ON' in the top-right to keep your screen in deep red so it won't ruin your night vision in the dark.",
            "The sync timer uses passive GPS satellite time to wake nearby phones for 15 seconds every 15 minutes (:00, :15, :30, :45), saving battery for up to 4 days."
        ),
        pointerZone = PointerZone.TOP_BAR_RIGHT,
        cardAtBottom = true
    ),
    WalkthroughStepSpec(
        title = "2 of 8 • PIN Lock, Duress Wipe & Blackout",
        pointerLabel = "POINTS TO: Lock Button",
        bodyPoints = listOf(
            "Tap 'Lock' to open the security vault.",
            "Normal PIN (default 2580): Unlocks your real messages.",
            "Emergency Duress PIN (default 9911): If forced to unlock your phone under threat, entering 9911 erases all keys in under 0.1 seconds and turns the app into a harmless First Aid Guide.",
            "Quick Blackout: Press your physical Volume-Down button 3 times fast at any time to black out the screen."
        ),
        pointerZone = PointerZone.TOP_BAR_LOCK,
        cardAtBottom = true
    ),
    WalkthroughStepSpec(
        title = "3 of 8 • Receiving Light Signals (Light Tab)",
        pointerLabel = "POINTS TO: Center Optical Target",
        bodyPoints = listOf(
            "To receive a message by light (from 30m up to 5 km away at night), point your camera so the other person's flashlight or screen sits inside this center target ring.",
            "PHAROS automatically stabilizes hand shake and repairs damaged data bits as the progress bar reaches 100%."
        ),
        pointerZone = PointerZone.CENTER_VIEWFINDER,
        cardAtBottom = true
    ),
    WalkthroughStepSpec(
        title = "4 of 8 • Sending Light Signals (Light Tab)",
        pointerLabel = "POINTS TO: Mode Buttons & Send Light Signal",
        bodyPoints = listOf(
            "Flashlight: Pulses your rear camera LED flash to beam messages 1–5 km across valleys at night.",
            "Screen: Flashes a fast 4x4 grid on your screen for closer range (30–300m).",
            "Morse: Flashes standard SOS Morse code readable by human eyes or any camera.",
            "Tap 'Send Light Signal' to transmit your latest message."
        ),
        pointerZone = PointerZone.BOTTOM_LIGHT_CONTROLS,
        cardAtBottom = false
    ),
    WalkthroughStepSpec(
        title = "5 of 8 • Offline Map & Trusted Filter (Messages Tab)",
        pointerLabel = "POINTS TO: Offline Map & Trusted / Nearby SOS",
        bodyPoints = listOf(
            "The top map plots nearby messages relative to your position with zero internet.",
            "Trusted (Green): Verified messages from contacts you paired with or friends they vouched for.",
            "Nearby SOS (Amber): Unverified SOS broadcasts from strangers nearby—kept separate so you can verify before acting."
        ),
        pointerZone = PointerZone.TOP_MAP_AND_FILTERS,
        cardAtBottom = true
    ),
    WalkthroughStepSpec(
        title = "6 of 8 • Sending SOS, GPS & Voice Notes (Messages Tab)",
        pointerLabel = "POINTS TO: Status Picker & Send Bar",
        bodyPoints = listOf(
            "Choose 'SOS', 'Medical', or 'Safe', type a short note, and tap 'Send'.",
            "Your GPS coordinates and a compressed voice note (~1 KB for 10 seconds) are sealed into a 256-byte encrypted packet.",
            "Voice notes are decrypted only in temporary RAM while playing and never saved to your phone's storage."
        ),
        pointerZone = PointerZone.BOTTOM_MESSAGE_COMPOSER,
        cardAtBottom = false
    ),
    WalkthroughStepSpec(
        title = "7 of 8 • Share PHAROS Offline (Radio & Share Tab)",
        pointerLabel = "POINTS TO: QR Code & Start Wi-Fi Hotspot",
        bodyPoints = listOf(
            "Need to reach someone nearby who doesn't have PHAROS and there is no internet?",
            "Tap 'Start Wi-Fi Hotspot' and let them scan this QR code:",
            "• Play Store version: Opens a zero-install offline web communicator right in their phone's browser.",
            "• Standalone version: Also lets them download the full PHAROS APK directly from your phone."
        ),
        pointerZone = PointerZone.TOP_HOTSPOT_QR,
        cardAtBottom = true
    ),
    WalkthroughStepSpec(
        title = "8 of 8 • Walkie-Talkies & Contact Pairing (Radio & Share Tab)",
        pointerLabel = "POINTS TO: Walkie-Talkie Audio & Pair Contact",
        bodyPoints = listOf(
            "Send Over Radio Audio: Hold your phone near any cheap walkie-talkie (FRS/PMR/CB) and press PTT. PHAROS sends encrypted messages as audio chirps over 2–10+ km (and decodes keypad tones from old Nokia phones).",
            "Pair Contact: Type a shared memory phrase you and your contact both know, tap 'Pair', and check that the 4 verification words match.",
            "Tap '?' in the top bar anytime to view this walkthrough again."
        ),
        pointerZone = PointerZone.BOTTOM_RADIO_AND_PAIR,
        cardAtBottom = false
    )
)

@Composable
private fun FirstLaunchWalkthroughOverlay(
    stepIndex: Int,
    palette: NightOpsPalette,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val step = WALKTHROUGH_STEPS[stepIndex.coerceIn(0, WALKTHROUGH_STEPS.lastIndex)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true, onClick = {})
    ) {
        // Spotlight Highlight Box & Directional Pointer Arrow Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pointerColor = palette.secondaryAmber

            data class SpotlightGeometry(
                val boxTopLeft: Offset,
                val boxSize: Size,
                val arrowStart: Offset,
                val arrowTip: Offset
            )

            val geom = when (step.pointerZone) {
                PointerZone.TOP_BAR_RIGHT -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.56f, 8f),
                    boxSize = Size(w * 0.42f, 64.dp.toPx()),
                    arrowStart = Offset(w * 0.75f, h * 0.34f),
                    arrowTip = Offset(w * 0.75f, 76.dp.toPx())
                )
                PointerZone.TOP_BAR_LOCK -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.76f, 8f),
                    boxSize = Size(w * 0.22f, 64.dp.toPx()),
                    arrowStart = Offset(w * 0.86f, h * 0.34f),
                    arrowTip = Offset(w * 0.86f, 76.dp.toPx())
                )
                PointerZone.CENTER_VIEWFINDER -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.08f, h * 0.14f),
                    boxSize = Size(w * 0.84f, h * 0.34f),
                    arrowStart = Offset(w * 0.5f, h * 0.56f),
                    arrowTip = Offset(w * 0.5f, h * 0.49f)
                )
                PointerZone.BOTTOM_LIGHT_CONTROLS -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.04f, h * 0.64f),
                    boxSize = Size(w * 0.92f, h * 0.25f),
                    arrowStart = Offset(w * 0.5f, h * 0.54f),
                    arrowTip = Offset(w * 0.5f, h * 0.63f)
                )
                PointerZone.TOP_MAP_AND_FILTERS -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.04f, h * 0.11f),
                    boxSize = Size(w * 0.92f, h * 0.28f),
                    arrowStart = Offset(w * 0.5f, h * 0.48f),
                    arrowTip = Offset(w * 0.5f, h * 0.40f)
                )
                PointerZone.BOTTOM_MESSAGE_COMPOSER -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.04f, h * 0.72f),
                    boxSize = Size(w * 0.92f, h * 0.18f),
                    arrowStart = Offset(w * 0.5f, h * 0.60f),
                    arrowTip = Offset(w * 0.5f, h * 0.71f)
                )
                PointerZone.TOP_HOTSPOT_QR -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.04f, h * 0.11f),
                    boxSize = Size(w * 0.92f, h * 0.35f),
                    arrowStart = Offset(w * 0.5f, h * 0.54f),
                    arrowTip = Offset(w * 0.5f, h * 0.47f)
                )
                PointerZone.BOTTOM_RADIO_AND_PAIR -> SpotlightGeometry(
                    boxTopLeft = Offset(w * 0.04f, h * 0.48f),
                    boxSize = Size(w * 0.92f, h * 0.40f),
                    arrowStart = Offset(w * 0.5f, h * 0.40f),
                    arrowTip = Offset(w * 0.5f, h * 0.47f)
                )
            }

            // 1. Draw Spotlight Target Box
            drawRoundRect(
                color = pointerColor,
                topLeft = geom.boxTopLeft,
                size = geom.boxSize,
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. Draw Pointer Stem Line
            drawLine(
                color = pointerColor,
                start = geom.arrowStart,
                end = geom.arrowTip,
                strokeWidth = 3.5.dp.toPx()
            )

            // 3. Draw Directional Arrowhead at arrowTip
            val pointingUp = geom.arrowTip.y < geom.arrowStart.y
            val headSize = 14.dp.toPx()
            val arrowPath = Path().apply {
                moveTo(geom.arrowTip.x, geom.arrowTip.y)
                if (pointingUp) {
                    lineTo(geom.arrowTip.x - headSize, geom.arrowTip.y + headSize)
                    lineTo(geom.arrowTip.x + headSize, geom.arrowTip.y + headSize)
                } else {
                    lineTo(geom.arrowTip.x - headSize, geom.arrowTip.y - headSize)
                    lineTo(geom.arrowTip.x + headSize, geom.arrowTip.y - headSize)
                }
                close()
            }
            drawPath(path = arrowPath, color = pointerColor)

            // 4. Draw Pulsing Anchor Dot at arrowStart
            drawCircle(
                color = pointerColor,
                radius = 5.dp.toPx(),
                center = geom.arrowStart
            )
        }

        // Walkthrough Instruction Card (Positioned opposite the highlighted UI zone so it never blocks it)
        Column(
            modifier = Modifier
                .align(if (step.cardAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(
                    top = if (step.cardAtBottom) 16.dp else 78.dp,
                    bottom = if (step.cardAtBottom) 82.dp else 16.dp,
                    start = 16.dp,
                    end = 16.dp
                )
                .fillMaxWidth()
                .background(Color(0xFF0E0E0E))
                .border(2.dp, palette.secondaryAmber)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Skip",
                    color = palette.mutedText,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { onSkip() }
                        .padding(4.dp)
                )
            }

            Text(
                text = "▶ ${step.pointerLabel}",
                color = palette.secondaryAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / WALKTHROUGH_STEPS.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = palette.secondaryAmber,
                trackColor = Color(0xFF262626)
            )

            step.bodyPoints.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Back", color = palette.textPrimary, fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAmber)
                ) {
                    Text(
                        text = if (stepIndex == WALKTHROUGH_STEPS.lastIndex) "Finish Guide" else "Next →",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleTopBar(
    state: PharosUiState,
    palette: NightOpsPalette,
    onToggleRedMode: () -> Unit,
    onOpenLock: () -> Unit,
    onStartGuide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (state.isDecoyMode) "First Aid Guide" else "PHAROS",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Offline • Next sync ${state.nextFireflyBurstSec / 60}m",
                color = palette.mutedText,
                fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onStartGuide,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "?",
                    color = palette.secondaryAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            OutlinedButton(
                onClick = onToggleRedMode,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (state.isRed660nmNightVision) "Red ON" else "Red OFF",
                    color = palette.textPrimary,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onOpenLock,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Lock",
                    color = palette.textPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SimpleBottomBar(
    selectedTab: TacticalTab,
    palette: NightOpsPalette,
    onSelectTab: (TacticalTab) -> Unit
) {
    val labels = mapOf(
        TacticalTab.BEAM to "Light",
        TacticalTab.PULSE to "Messages",
        TacticalTab.BRIDGE to "Radio",
        TacticalTab.SURVIVAL to "Manual"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TacticalTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Button(
                onClick = { onSelectTab(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) palette.primarySignal else palette.surfaceDark,
                    contentColor = if (selected) Color.Black else palette.textPrimary
                )
            ) {
                Text(
                    text = labels[tab] ?: tab.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SimpleBeamTab(
    state: PharosUiState,
    palette: NightOpsPalette,
    onSelectTxMode: (OpticalModemEngine.OpticalTxMode) -> Unit,
    onSendLight: () -> Unit,
    onRequestCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Clean Optical Viewfinder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(palette.surfaceDark)
                .border(1.dp, palette.borderStroke)
                .clickable { onRequestCamera() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val boxSize = minOf(size.width, size.height) * 0.56f
                val startX = cx - boxSize / 2f
                val startY = cy - boxSize / 2f
                val cellS = boxSize / 4f

                for (gy in 0 until 4) {
                    for (gx in 0 until 4) {
                        val level = state.activeMatrixCells16.getOrElse(gy * 4 + gx) { 0 }
                        val cellColor = when (level and 3) {
                            0 -> Color(0xFF141414)
                            1 -> palette.primarySignal.copy(alpha = 0.55f)
                            2 -> palette.secondaryAmber.copy(alpha = 0.8f)
                            else -> palette.textPrimary
                        }
                        drawRect(
                            color = cellColor,
                            topLeft = Offset(startX + gx * cellS + 3f, startY + gy * cellS + 3f),
                            size = Size(cellS - 6f, cellS - 6f)
                        )
                    }
                }

                // Simple target ring
                drawCircle(
                    color = palette.primarySignal,
                    radius = boxSize * 0.68f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simple signal bar + plain status
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = state.beamStatusLog, color = palette.textPrimary, fontSize = 14.sp)
                Text(text = "${state.fecLockProgressPercent}%", color = palette.mutedText, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { state.fecLockProgressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = palette.verifiedGreen,
                trackColor = palette.surfaceDark
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3 Clean Mode Buttons: Flashlight, Screen, Morse
        val mainModes = listOf(
            OpticalModemEngine.OpticalTxMode.TORCH_4PPM_LONG_RANGE to "Flashlight",
            OpticalModemEngine.OpticalTxMode.OLED_4X4_RGBW_MATRIX to "Screen",
            OpticalModemEngine.OpticalTxMode.MANUAL_MORSE_BEACON to "Morse"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mainModes.forEach { (mode, label) ->
                val selected = state.opticalTxMode == mode
                OutlinedButton(
                    onClick = { onSelectTxMode(mode) },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) palette.primarySignal else palette.mutedText,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Single huge primary button
        Button(
            onClick = onSendLight,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
        ) {
            Text(
                text = if (state.isOpticalTxActive) "Sending Light..." else "Send Light Signal",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun SimplePulseTab(
    state: PharosUiState,
    palette: NightOpsPalette,
    onSelectTrusted: (Boolean) -> Unit,
    onSendCard: (TacticalCard.StatusTier, String) -> Unit,
    onPlayVoice: (Long) -> Unit
) {
    var draftNote by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf(TacticalCard.StatusTier.LIFE_SAFETY_SOS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Minimal Map
        OfflineVectorTacticalMap(
            verifiedCards = state.verifiedCards,
            quarantineCards = state.quarantineCards,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        // Two Simple Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSelectTrusted(true) },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.showVerifiedFeed) palette.verifiedGreen else palette.surfaceDark,
                    contentColor = if (state.showVerifiedFeed) Color.Black else palette.textPrimary
                )
            ) {
                Text("Trusted (${state.verifiedCards.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = { onSelectTrusted(false) },
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!state.showVerifiedFeed) palette.quarantineAmber else palette.surfaceDark,
                    contentColor = if (!state.showVerifiedFeed) Color.Black else palette.textPrimary
                )
            ) {
                Text("Nearby SOS (${state.quarantineCards.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Clean Message Feed
        val activeCards = if (state.showVerifiedFeed) state.verifiedCards else state.quarantineCards
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeCards, key = { it.cardId }) { card ->
                val badgeColor = if (state.showVerifiedFeed) palette.verifiedGreen else palette.quarantineAmber
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceDark)
                        .border(1.dp, palette.borderStroke)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = card.statusTier.label,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${"%.3f".format(card.latitudeDeg)}, ${"%.3f".format(card.longitudeDeg)}",
                            color = palette.mutedText,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = card.note,
                        color = palette.textPrimary,
                        fontSize = 15.sp
                    )
                    if (card.attachmentType != TacticalCard.AttachmentType.NONE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onPlayVoice(card.cardId) },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = if (state.activeRamVoiceClipId != null) "Playing..." else "Play Voice",
                                color = palette.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Clean Message Composer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TacticalCard.StatusTier.LIFE_SAFETY_SOS to "SOS",
                TacticalCard.StatusTier.MEDICAL_TRIAGE to "Medical",
                TacticalCard.StatusTier.SAFE_CHECKIN to "Safe"
            ).forEach { (tier, label) ->
                val sel = tier == selectedTier
                OutlinedButton(
                    onClick = { selectedTier = tier },
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text(
                        text = label,
                        color = if (sel) palette.primarySignal else palette.mutedText,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draftNote,
                onValueChange = { draftNote = it },
                placeholder = { Text("Message...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    onSendCard(selectedTier, draftNote)
                    draftNote = ""
                },
                modifier = Modifier.height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
            ) {
                Text("Send", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SimpleBridgeTab(
    state: PharosUiState,
    palette: NightOpsPalette,
    onToggleHotspot: () -> Unit,
    onSendRadioAudio: () -> Unit,
    onPairPhrase: (String) -> Unit,
    onCycleDnd: () -> Unit,
    onOpenDndSettings: () -> Unit
) {
    var phraseInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Offline Hotspot Share
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceDark)
                .border(1.dp, palette.borderStroke)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Share PHAROS Offline",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            TacticalQrCodeCanvas(
                payload = state.hotspotUrl,
                palette = palette,
                sizeDp = 136.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onToggleHotspot,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
            ) {
                Text(
                    text = if (state.isHotspotRunning) "Stop Wi-Fi Hotspot" else "Start Wi-Fi Hotspot",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 2. Walkie-Talkie Audio Bridge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceDark)
                .border(1.dp, palette.borderStroke)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Walkie-Talkie Audio",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = state.lastAudioBridgeLog,
                color = palette.mutedText,
                fontSize = 13.sp
            )
            Button(
                onClick = onSendRadioAudio,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
            ) {
                Text(
                    text = "Send Over Radio Audio",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 3. Pair Contact
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceDark)
                .border(1.dp, palette.borderStroke)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pair Contact",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Verify words: ${state.fourWordNodeSas}",
                color = palette.verifiedGreen,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    placeholder = { Text("Shared memory phrase...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { onPairPhrase(phraseInput) },
                    modifier = Modifier.height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondaryAmber)
                ) {
                    Text("Pair", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 4. Silent / Alert Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCycleDnd,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text(
                    text = "Alert: ${state.dndMode.name.substringBefore("_")}",
                    color = palette.textPrimary,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onOpenDndSettings,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text(
                    text = "SOS Wake Access",
                    color = palette.textPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SimpleSurvivalManualTab(
    palette: NightOpsPalette
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var expandedId by remember { mutableStateOf<String?>("grid_down_60m") }

    val filteredEntries = remember(searchQuery, selectedCategory) {
        SurvivalCodexRepository.entries.filter { entry ->
            val matchesCat = selectedCategory == "All" || entry.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.exactFormulasOrRatios.lowercase().contains(q) ||
                entry.criticalSteps.any { it.lowercase().contains(q) }
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search survival manual (water, bleach, tourniquet, nuclear, fire...)", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SurvivalCodexRepository.categories.forEach { cat ->
                val sel = cat == selectedCategory
                OutlinedButton(
                    onClick = { selectedCategory = cat },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (sel) palette.primarySignal else palette.mutedText,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredEntries, key = { it.id }) { entry ->
                val isExpanded = expandedId == entry.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceDark)
                        .border(1.dp, if (isExpanded) palette.primarySignal else palette.borderStroke)
                        .clickable { expandedId = if (isExpanded) null else entry.id }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.category.uppercase(),
                            color = palette.secondaryAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (isExpanded) "Hide ▲" else "Read ▼",
                            color = palette.mutedText,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = entry.title,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = entry.summary,
                        color = palette.mutedText,
                        fontSize = 13.sp
                    )
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        entry.criticalSteps.forEach { step ->
                            Text(
                                text = step,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .border(1.dp, palette.secondaryAmber)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = entry.exactFormulasOrRatios,
                                color = palette.verifiedGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimplePinDialog(
    palette: NightOpsPalette,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceDark,
        title = {
            Text("Enter PIN", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it },
                placeholder = { Text("PIN", fontSize = 14.sp) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPin(pinInput) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
            ) {
                Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textPrimary)
            }
        }
    )
}

@Composable
private fun SimplePermissionDialog(
    permissionCategory: String,
    palette: NightOpsPalette,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val body = when (permissionCategory) {
        "CAMERA" -> "Camera and flashlight are used only to send and read light signals between phones. PHAROS has no internet access."
        "AUDIO" -> "Microphone is used only for walkie-talkie audio signals and voice notes. Nothing leaves your phone."
        else -> "GPS and local Wi-Fi/Bluetooth are used only to sync time and exchange offline messages nearby."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceDark,
        title = {
            Text("$permissionCategory Access", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(body, color = palette.textPrimary, fontSize = 14.sp)
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = palette.primarySignal)
            ) {
                Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = palette.mutedText)
            }
        }
    )
}
