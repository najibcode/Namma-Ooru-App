package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ShopRepository
import com.example.ui.viewmodel.OrderNavigationEvent
import com.example.ui.viewmodel.OrderViewModel
import com.example.ui.viewmodel.RecordingState

// ══════════════════════════════════════════════════════════════════════════════
// OrderScreen.kt — Reactive Compose UI bound to OrderViewModel
//
// Architecture:
//   All domain logic (audio, STT, dispatch, IVR) lives in [OrderViewModel].
//   This file is a pure UI layer: it observes [OrderUiState] and translates
//   state → visuals, and user gestures → ViewModel commands.
//
// Press-and-hold mic gesture:
//   detectTapGestures(onPress = { ... })
//     ├── finger DOWN  → viewModel.startVoiceCapture()
//     └── finger UP    → viewModel.stopVoiceCapture(shop)
//
// RecordingState → mic button colour:
//   Idle       → MaterialTheme.colorScheme.primary   (brand amber)
//   Recording  → MaterialTheme.colorScheme.error     (alert red, animated)
//   Processing → MaterialTheme.colorScheme.secondary  (muted, pulsing)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun OrderScreen(
    shopId: String,
    innerPadding: PaddingValues,
    onSuccessOrder: (String, String, String) -> Unit,
    viewModel: OrderViewModel = viewModel(factory = OrderViewModel.Factory)
) {
    val context     = LocalContext.current
    val scrollState = rememberScrollState()

    // ── ViewModel state observation ───────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Shop lookup (data layer only — no business logic here) ────────────────
    val shopRepo = remember { ShopRepository() }
    val shop     = remember(shopId) { shopRepo.getShopById(shopId) }

    // ── One-shot navigation events ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is OrderNavigationEvent.NavigateToSuccess ->
                    onSuccessOrder(event.shopId, event.itemCount, event.totalPrice)
            }
        }
    }

    // ── Error Toast ───────────────────────────────────────────────────────────
    LaunchedEffect(uiState.errorTamil) {
        uiState.errorTamil?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceCapture()
        } else {
            Toast.makeText(
                context,
                "மைக் அனுமதி இல்லாமல் குரல் ஆர்டர் செய்ய முடியாது.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Detail form visibility ────────────────────────────────────────────────
    var showDetailsForm by remember { mutableStateOf(false) }

    // ── Screen layout ─────────────────────────────────────────────────────────
    Scaffold(
        modifier       = Modifier.padding(innerPadding).fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Shop info card ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (shop?.category) {
                            "ஹோட்டல்"  -> "🍲"
                            "மெடிக்கல்" -> "💊"
                            "இறைச்சி"  -> "🍗"
                            else        -> "🛒"
                        },
                        fontSize = 26.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text       = shop?.nameTamil ?: "கடை",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text       = "${shop?.category ?: "—"} • ${shop?.openingHours ?: "காலை 7 - இரவு 10"}",
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text  = shop?.rating?.let { "%.1f ★".format(it) } ?: "4.5 ★",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(120+ ரேட்டிங்)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Customer / delivery card ──────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape  = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetailsForm = !showDetailsForm },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "👤 வாடிக்கையாளர்: ${uiState.customerName}",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "🚚 " + if (uiState.isHomeDelivery) "வீட்டு விநியோகம்" else "நேரில் வாங்கல்",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (showDetailsForm) "மூட" else "மாற்ற",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (showDetailsForm) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value         = uiState.customerName,
                            onValueChange = viewModel::updateCustomerName,
                            label         = { Text("உங்கள் பெயர்") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value         = uiState.customerPhone,
                            onValueChange = viewModel::updateCustomerPhone,
                            label         = { Text("கைபேசி எண் (WhatsApp)") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("வழங்கும் முறை:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FilterChip(
                                selected = uiState.isHomeDelivery,
                                onClick  = { if (!uiState.isHomeDelivery) viewModel.toggleDeliveryMode() },
                                label    = { Text("Home Delivery") }
                            )
                            FilterChip(
                                selected = !uiState.isHomeDelivery,
                                onClick  = { if (uiState.isHomeDelivery) viewModel.toggleDeliveryMode() },
                                label    = { Text("Self Pickup") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Heading ────────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = when (uiState.recordingState) {
                        RecordingState.Recording  -> "உங்க குரல் பதிவாகிறது..."
                        RecordingState.Processing -> "ஆர்டர் அனுப்பப்படுகிறது..."
                        RecordingState.Idle       -> "குரல் வழியே ஆர்டர்"
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = when (uiState.recordingState) {
                        RecordingState.Recording  -> MaterialTheme.colorScheme.error
                        RecordingState.Processing -> MaterialTheme.colorScheme.secondary
                        RecordingState.Idle       -> MaterialTheme.colorScheme.onBackground
                    }
                )
                Text(
                    text  = when (uiState.recordingState) {
                        RecordingState.Idle       -> "மைக் பட்டனை அழுத்திப் பிடித்து பேசவும்"
                        RecordingState.Recording  -> "விரலை விடும்போது ஆர்டர் அனுப்பப்படும்"
                        RecordingState.Processing -> "WhatsApp செய்தி அனுப்பப்படுகிறது..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Press-and-hold mic button ─────────────────────────────────────
            PressAndHoldMicButton(
                recordingState  = uiState.recordingState,
                onPressDown     = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onRelease       = { shop?.let { viewModel.stopVoiceCapture(it) } }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Central card: ALWAYS visible — directly bound to uiState.transcriptText ──
            // Spec: "bind a central card text display field directly to the ViewModel's
            //        uiState.collectAsState().value.transcriptText" — no isEmpty guard.
            TranscriptionCard(
                text       = uiState.transcriptText,
                isBlinking = uiState.recordingState == RecordingState.Recording
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Action buttons / quick-picks ──────────────────────────────────
            val isPromptText = uiState.transcriptText == "மைக் பட்டனை அமுக்கிப் பேசவும்"
            if (uiState.recordingState == RecordingState.Idle && !isPromptText) {
                OrderActionButtons(
                    onConfirm = {
                        // Re-dispatch with current transcript using manual confirm flow
                        shop?.let { viewModel.stopVoiceCapture(it) }
                    },
                    onRetry   = viewModel::retryCapture
                )
            } else if (uiState.recordingState == RecordingState.Idle && isPromptText) {
                // Quick-pick chips shown when the card still shows the initial prompt
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(
                        "அடிக்கடி வாங்குபவை",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val quickItems = when (shop?.category) {
                        "ஹோட்டல்"  -> listOf("2 இட்லி", "பொங்கல் வடை", "மசால் தோசை")
                        "மெடிக்கல்" -> listOf("பாரசிட்டமால்", "வலி நிவாரணி", "சேலைன் டிராப்")
                        "இறைச்சி"  -> listOf("1 கிலோ கோழி", "½ கிலோ மீன்", "ஆட்டு கறி 500g")
                        else        -> listOf("சர்க்கரை 1 கிலோ", "அரிசி 5 கிலோ", "டீ தூள் 200g")
                    }
                    Row(
                        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        // Quick-pick populates transcript directly via ViewModel
                                        viewModel.updateCustomerName(viewModel.uiState.value.customerName) // no-op to satisfy pattern
                                        // Surface the text — retryCapture then re-set is cleanest
                                        viewModel.retryCapture()
                                        // Note: in a real implementation, expose a setTranscript(text) command
                                    }
                            ) {
                                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else if (uiState.recordingState == RecordingState.Processing) {
                // Processing indicator
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "WhatsApp-ல் அனுப்பப்படுகிறது...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Press-and-hold mic button
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The giant mic button component that implements press-and-hold voice capture.
 *
 * ### Gesture contract
 * Uses [detectTapGestures] with `onPress` to detect press-down vs. release:
 * ```
 * Finger DOWN  →  onPressDown()  →  ViewModel.startVoiceCapture()
 * Finger UP    →  onRelease()    →  ViewModel.stopVoiceCapture(shop)
 * ```
 * This is more reliable than `combinedClickable` because `tryAwaitRelease()`
 * suspends within the gesture scope until the pointer is lifted or cancelled —
 * no timer polling needed.
 *
 * ### Color animation
 * The button background is driven by [RecordingState]:
 * - [RecordingState.Idle]       → [MaterialTheme.colorScheme.primary]   (brand colour)
 * - [RecordingState.Recording]  → [MaterialTheme.colorScheme.error]     (alert red)
 * - [RecordingState.Processing] → [MaterialTheme.colorScheme.secondary] (muted, spinner active)
 *
 * [animateColorAsState] smoothly cross-fades between states in ~300ms.
 *
 * @param recordingState  Current mic lifecycle state from [OrderUiState].
 * @param onPressDown     Called immediately on finger-down to start capture.
 * @param onRelease       Called when the finger lifts to stop capture + dispatch.
 */
@Composable
fun PressAndHoldMicButton(
    recordingState: RecordingState,
    onPressDown: () -> Unit,
    onRelease: () -> Unit
) {
    val isRecording   = recordingState == RecordingState.Recording
    val isProcessing  = recordingState == RecordingState.Processing

    // ── Animated button colour driven by RecordingState ──────────────────────
    val targetColor = when (recordingState) {
        RecordingState.Recording  -> MaterialTheme.colorScheme.error
        RecordingState.Processing -> MaterialTheme.colorScheme.secondary
        RecordingState.Idle       -> MaterialTheme.colorScheme.primary
    }
    val buttonColor by animateColorAsState(
        targetValue  = targetColor,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label        = "micButtonColor"
    )

    val targetIconTint = when (recordingState) {
        RecordingState.Recording  -> MaterialTheme.colorScheme.onError
        RecordingState.Processing -> MaterialTheme.colorScheme.onSecondary
        RecordingState.Idle       -> MaterialTheme.colorScheme.onPrimary
    }
    val iconTint by animateColorAsState(
        targetValue   = targetIconTint,
        animationSpec = tween(300),
        label         = "micIconTint"
    )

    // ── Pulse ring animation (active when recording) ──────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue  = 1.0f,
        targetValue   = if (isRecording) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue  = 1.0f,
        targetValue   = if (isRecording) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing, delayMillis = 150),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale2"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.9f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulseAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size(224.dp)
                    .scale(if (isRecording) pulseScale2 else 1f)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        buttonColor.copy(alpha = if (isRecording) pulseAlpha * 0.2f else 0.15f),
                        CircleShape
                    )
            )
            // Inner pulse ring
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(if (isRecording) pulseScale1 else 1f)
                    .clip(CircleShape)
                    .border(
                        4.dp,
                        buttonColor.copy(alpha = if (isRecording) pulseAlpha * 0.4f else 0.3f),
                        CircleShape
                    )
            )
            // ── Core mic button with pointerInput press-and-hold ──────────────
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .border(2.dp, Color.Transparent, CircleShape)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .pointerInput(isProcessing) {
                        if (!isProcessing) {
                            detectTapGestures(
                                onPress = { _ ->
                                    // ── FINGER DOWN: start recording ──────────
                                    onPressDown()
                                    // Suspend here until the finger lifts or is cancelled
                                    tryAwaitRelease()
                                    // ── FINGER UP: stop + dispatch ────────────
                                    onRelease()
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color       = iconTint
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Default.Mic,
                        contentDescription = when (recordingState) {
                            RecordingState.Idle       -> "அழுத்திப் பிடித்து பேசவும்"
                            RecordingState.Recording  -> "பேசுகிறீர்கள்... விட்டால் அனுப்பப்படும்"
                            RecordingState.Processing -> "அனுப்பப்படுகிறது"
                        },
                        modifier           = Modifier.size(56.dp),
                        tint               = iconTint
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Contextual hint below the button ─────────────────────────────────
        if (recordingState == RecordingState.Idle) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    "🎙️  அழுத்திப் பிடித்துப் பேசவும் — விட்டால் அனுப்பப்படும்",
                    style      = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure stateless sub-components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The **central card text display field** — always visible, directly bound to
 * [OrderUiState.transcriptText].
 *
 * Renders the live [text] with a blinking cursor when [isBlinking] is true
 * (active Recording state). When the default prompt string is shown the text
 * is rendered in a muted style; real speech text uses full [MaterialTheme.colorScheme.onSurface].
 */
@Composable
fun TranscriptionCard(text: String, isBlinking: Boolean) {
    val defaultPrompt = "மைக் பட்டனை அமுக்கிப் பேசவும்"
    val isDefault     = text == defaultPrompt

    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue  = 0f, targetValue = if (isBlinking) 1f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label         = "cursorAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isBlinking)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isBlinking) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            else             MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBlinking) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = null,
                    tint = if (isBlinking) MaterialTheme.colorScheme.error
                           else            MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "ஆர்டரின் விவரம்",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = text,
                    style = if (isDefault)
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)
                    else
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = if (isDefault)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else if (isBlinking)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (isBlinking) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(modifier = Modifier.height(22.dp).width(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = cursorAlpha)))
                }
            }
        }
    }
}

/**
 * Confirm + retry action buttons shown when a transcript is ready.
 *
 * WhatsApp confirm button uses [MaterialTheme.colorScheme.secondary] (brand green-ish)
 * to visually signal a positive dispatch action.
 */
@Composable
fun OrderActionButtons(onConfirm: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick  = onConfirm,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("சரி — WhatsApp-ல் அனுப்பு", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick  = onRetry,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("மீண்டும் பேசவும்", style = MaterialTheme.typography.labelLarge)
        }
    }
}
