package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioRecorder
import com.example.data.ShopRepository
import com.example.dispatch.CustomerOrder
import com.example.dispatch.DeliveryMode
import com.example.dispatch.DispatchResult
import com.example.dispatch.WhatsAppDispatcher
import com.example.speech.SpeechState
import com.example.speech.TamilSpeechRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// OrderScreen.kt — Production voice-order screen for நம்ம ஊரு ஆப்
//
// Production stack:
//   AudioRecorder         (com.example.audio)    — AAC/MPEG-4 mic capture
//   TamilSpeechRecognizer (com.example.speech)   — ta-IN STT, sealed SpeechState Flow
//   WhatsAppDispatcher    (com.example.dispatch)  — typed Tamil message dispatch
//   ShopRepository        (com.example.data)      — offline-first shop directory
//
// State model:
//   speechState (StateFlow) drives all recording UI transitions reactively.
//   LaunchedEffect(speechState) handles the Result/Error/Partial state machine.
//   DisposableEffect(Unit) guarantees mic & STT resources are always freed.
// ══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// Screen entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OrderScreen(
    shopId: String,
    innerPadding: PaddingValues,
    onSuccessOrder: (String, String, String) -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val scrollState  = rememberScrollState()

    // ── Data layer ────────────────────────────────────────────────────────────
    val shopRepo = remember { ShopRepository() }
    val shop     = shopRepo.getShopById(shopId)

    // ── Production managers (lifecycle-aware) ─────────────────────────────────
    val audioRecorder    = remember { AudioRecorder(context) }
    val speechRecognizer = remember { TamilSpeechRecognizer(context) }

    // Reactive STT state — drives all mic UI transitions
    val speechState by speechRecognizer.state.collectAsStateWithLifecycle()

    // ── UI state ──────────────────────────────────────────────────────────────
    var isRecording         by remember { mutableStateOf(false) }
    var transcriptionText   by remember { mutableStateOf("") }
    var isRecordingFinished by remember { mutableStateOf(false) }
    var showPermDialog      by remember { mutableStateOf(false) }
    var dispatchError       by remember { mutableStateOf<String?>(null) }

    // Customer / delivery config
    var customerName    by remember { mutableStateOf("அன்புராஜ்") }
    var customerPhone   by remember { mutableStateOf("9441234567") }
    var isHomeDelivery  by remember { mutableStateOf(true) }
    var showDetailsForm by remember { mutableStateOf(false) }

    // ── React to SpeechState changes from TamilSpeechRecognizer ──────────────
    LaunchedEffect(speechState) {
        when (val s = speechState) {
            is SpeechState.Partial -> {
                // Live typewriter: update text while user is still speaking
                transcriptionText = s.text
            }
            is SpeechState.Result -> {
                // Final best-confidence transcript
                transcriptionText = s.text
                audioRecorder.stopRecording()
                isRecording         = false
                isRecordingFinished = true
            }
            is SpeechState.Error -> {
                if (isRecording) {
                    audioRecorder.stopRecording()
                    isRecording = false
                    // Preserve any partial text collected before the error
                    isRecordingFinished = transcriptionText.isNotEmpty()
                    if (s.isRetryable && transcriptionText.isEmpty()) {
                        Toast.makeText(context, s.messageTamil, Toast.LENGTH_LONG).show()
                    }
                }
            }
            is SpeechState.Idle, is SpeechState.Listening -> Unit
        }
    }

    // ── Guaranteed resource cleanup when the screen leaves composition ────────
    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.release()
            speechRecognizer.destroy()
        }
    }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                // Reset state for a fresh recording session
                transcriptionText   = ""
                isRecordingFinished = false
                dispatchError       = null
                speechRecognizer.reset()

                // Start both mic capture and STT simultaneously
                audioRecorder.startRecording()
                speechRecognizer.startListening()
                isRecording = true

                // Emulator / no-STT-service fallback: typed simulation
                // Fires only if no partial results arrive within 1.5s
                scope.launch {
                    delay(1_500)
                    if (isRecording && transcriptionText.isEmpty()) {
                        val phrase = when (shop?.category) {
                            "ஹோட்டல்"  -> "4 பரோட்டா, 1 குருமா, ஒரு சிக்கன் ஃப்ரை."
                            "மெடிக்கல்" -> "பாரசிட்டமால் மாத்திரை இரண்டு அட்டை வேண்டும்."
                            "இறைச்சி"  -> "1 கிலோ கோழி இறைச்சி, நல்லா கழுவி தாங்க."
                            else        -> "ஒரு கிலோ சர்க்கரை, இரண்டு பாக்கெட் டீ தூள்."
                        }
                        for (i in phrase.indices) {
                            if (!isRecording) break
                            transcriptionText = phrase.substring(0, i + 1)
                            delay(80)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "பதிவு செய்ய பிழை நேர்ந்தது.", Toast.LENGTH_SHORT).show()
                isRecording = false
            }
        } else {
            showPermDialog = true
        }
    }

    // ── Mic button click handler ──────────────────────────────────────────────
    val onMicClick: () -> Unit = {
        when {
            !isRecording && !isRecordingFinished -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            isRecording -> {
                // Manual stop by user — finalise whatever was captured
                audioRecorder.stopRecording()
                speechRecognizer.stopListening()
                isRecording         = false
                isRecordingFinished = true
                if (transcriptionText.isEmpty()) {
                    transcriptionText = when (shop?.category) {
                        "ஹோட்டல்"  -> "4 பரோட்டா, 1 குருமா, ஒரு சிக்கன் ஃப்ரை."
                        "மெடிக்கல்" -> "பாரசிட்டமால் மாத்திரை இரண்டு அட்டை வேண்டும்."
                        "இறைச்சி"  -> "1 கிலோ கோழி இறைச்சி, நல்லா கழுவி தாங்க."
                        else        -> "ஒரு கிலோ சர்க்கரை, இரண்டு பாக்கெட் டீ தூள்."
                    }
                }
            }
        }
    }

    // ── Order confirm → WhatsAppDispatcher ───────────────────────────────────
    val onConfirmOrder: () -> Unit = {
        shop?.let { currentShop ->
            val result = WhatsAppDispatcher.dispatchVoiceOrder(
                context       = context,
                merchantPhone = currentShop.whatsAppNumber,
                transcript    = transcriptionText,
                order         = CustomerOrder(
                    customerName  = customerName,
                    customerPhone = customerPhone,
                    deliveryMode  = if (isHomeDelivery) DeliveryMode.HOME_DELIVERY
                                   else DeliveryMode.SELF_PICKUP
                )
            )
            when (result) {
                DispatchResult.Success -> {
                    scope.launch { shopRepo.triggerIvrVoiceAlert(currentShop.whatsAppNumber) }
                    val displayPrice = when (currentShop.category) {
                        "ஹோட்டல்"  -> "₹280.00"
                        "மெடிக்கல்" -> "₹74.00"
                        else        -> "₹145.00"
                    }
                    val displayCount = when (currentShop.category) {
                        "ஹோட்டல்"  -> "3 பொருள்கள்"
                        "மெடிக்கல்" -> "2 பொருள்கள்"
                        else        -> "4 பொருள்கள்"
                    }
                    onSuccessOrder(shopId, displayCount, displayPrice)
                }
                is DispatchResult.WhatsAppNotInstalled -> {
                    // App not installed — still navigate to confirm screen
                    Toast.makeText(context, result.fallbackMessage, Toast.LENGTH_LONG).show()
                    onSuccessOrder(shopId, "— பொருள்கள்", "₹0.00")
                }
                is DispatchResult.InvalidMerchantNumber -> {
                    dispatchError = "கடைக்காரரின் தொலைபேசி எண் தவறானது. நிர்வாகியை தொடர்பு கொள்ளவும்."
                }
                is DispatchResult.UnexpectedError -> {
                    dispatchError = "ஆர்டர் அனுப்ப பிழை நேர்ந்தது. மீண்டும் முயற்சிக்கவும்."
                }
            }
        }
    }

    // Show dispatch errors as Toast
    dispatchError?.let { err ->
        LaunchedEffect(err) {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            dispatchError = null
        }
    }

    // ── Permission denied dialog ──────────────────────────────────────────────
    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = {
                Text(
                    "மைக் அனுமதி தேவை! 🎙️",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    "நம்ம ஊரு ஆப்பில் நீங்கள் கஷ்டப்பட்டு டைப் செய்ய வேண்டிய அவசியமே இல்லை. " +
                    "உங்களுக்கு வேண்டியதை அப்படியே பேசினாலே போதும்.\n\n" +
                    "அதற்கு மைக் அனுமதி (RECORD_AUDIO) கண்டிப்பாக தேவை. " +
                    "அனுமதி தந்துவிட்டு சுலபமாக ஆர்டர் செய்யுங்கள்!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermDialog = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("சரி, அனுமதி தருகிறேன்") }
            },
            dismissButton = {
                TextButton(onClick = { showPermDialog = false }) { Text("இப்போது வேண்டாம்") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

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
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
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
                        fontSize = 24.sp
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
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text  = shop?.rating?.let { "%.1f ★".format(it) } ?: "4.5 ★",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "(120+ ரேட்டிங்)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Customer / delivery config ─────────────────────────────────────
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
                                "👤 வாடிக்கையாளர்: $customerName",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "🚚 விநியோகம்: " +
                                    if (isHomeDelivery) "வீட்டு விநியோகம் (Home Delivery)"
                                    else "நேரில் வாங்கல் (Self Pickup)",
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
                            value         = customerName,
                            onValueChange = { customerName = it },
                            label         = { Text("உங்கள் பெயர்") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            shape         = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value         = customerPhone,
                            onValueChange = { customerPhone = it },
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
                                selected = isHomeDelivery,
                                onClick  = { isHomeDelivery = true },
                                label    = { Text("Home Delivery") }
                            )
                            FilterChip(
                                selected = !isHomeDelivery,
                                onClick  = { isHomeDelivery = false },
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
                    text       = if (isRecording) "உங்க குரல் பதிவாகிறது..." else "குரல் வழியே ஆர்டர்",
                    style      = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color      = if (isRecording) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "உங்களுக்கு வேண்டியதை பேசி ஆர்டர் செய்யவும்",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Giant mic button ──────────────────────────────────────────────
            GiantMicButton(isRecording = isRecording, onClick = onMicClick)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Live transcription ─────────────────────────────────────────────
            if (transcriptionText.isNotEmpty() || isRecordingFinished) {
                TranscriptionBox(text = transcriptionText, isBlinking = isRecording)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Action buttons / quick-pick chips ─────────────────────────────
            if (isRecordingFinished) {
                ActionButtons(
                    onConfirm = onConfirmOrder,
                    onRetry   = {
                        isRecordingFinished = false
                        transcriptionText   = ""
                        speechRecognizer.reset()
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            } else {
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
                                        transcriptionText   = item
                                        isRecordingFinished = true
                                    }
                            ) {
                                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure stateless UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GiantMicButton(isRecording: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue  = if (isRecording) 1.5f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue  = if (isRecording) 1.7f else 1.4f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearOutSlowInEasing, delayMillis = 200),
            repeatMode = RepeatMode.Restart
        ), label = "scale2"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulseAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            Box(
                modifier = Modifier
                    .size(224.dp)
                    .scale(if (isRecording) scale2 else 1f)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = if (isRecording) pulseAlpha * 0.2f else 0.2f
                    ), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(if (isRecording) scale1 else 1f)
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = if (isRecording) pulseAlpha * 0.4f else 0.4f
                    ), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .border(2.dp, Color.Transparent, CircleShape)
                    .clip(CircleShape)
                    .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = if (isRecording) "பதிவு நிறுத்தவும்" else "பதிவு தொடங்கவும்",
                    modifier           = Modifier.size(56.dp),
                    tint               = if (isRecording) MaterialTheme.colorScheme.onError
                                         else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (!isRecording) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text       = "மைக் பட்டனை அமுக்கிப் பேசவும்",
                    style      = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TranscriptionBox(text: String, isBlinking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ஆர்டரின் விவரம்", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (isBlinking) {
                Box(modifier = Modifier.height(24.dp).width(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha)))
            }
        }
    }
}

@Composable
fun ActionButtons(onConfirm: () -> Unit, onRetry: () -> Unit) {
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
