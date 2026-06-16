package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════════════════
// SplashScreen.kt — Brand launch screen for நம்ம ஊரு ஆப்
//
// Animation sequence (total ~2500ms):
//   0ms    → Logo icon scale-in + fade-in
//   600ms  → App name text fades up
//   1000ms → Tagline fades in
//   1800ms → Progress dots animate
//   2500ms → onComplete() fires → navigate to Onboarding or Home
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onComplete: () -> Unit) {

    // ── Animation states ───────────────────────────────────────────────────
    var logoVisible    by remember { mutableStateOf(false) }
    var titleVisible   by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var dotsVisible    by remember { mutableStateOf(false) }

    // ── Logo scale + alpha ─────────────────────────────────────────────────
    val logoScale by animateFloatAsState(
        targetValue   = if (logoVisible) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label         = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue   = if (logoVisible) 1f else 0f,
        animationSpec = tween(500),
        label         = "logoAlpha"
    )

    // ── Title slide-up + alpha ─────────────────────────────────────────────
    val titleAlpha by animateFloatAsState(
        targetValue   = if (titleVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue   = if (titleVisible) 0f else 30f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "titleOffset"
    )

    // ── Tagline alpha ──────────────────────────────────────────────────────
    val taglineAlpha by animateFloatAsState(
        targetValue   = if (taglineVisible) 1f else 0f,
        animationSpec = tween(500),
        label         = "taglineAlpha"
    )

    // ── Loading dots pulsing ───────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue  = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(0)),
        label         = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue  = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(170)),
        label         = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue  = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(340)),
        label         = "dot3"
    )

    // ── Pulse ring for logo ────────────────────────────────────────────────
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label         = "pulseAlpha"
    )

    // ── Sequenced launch ──────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(100)
        logoVisible = true
        delay(600)
        titleVisible = true
        delay(400)
        taglineVisible = true
        delay(300)
        dotsVisible = true
        delay(1100)
        onComplete()
    }

    // ── Layout ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6B2E00),  // Deep amber-brown
                        Color(0xFF944a00),  // Primary brand amber
                        Color(0xFFBF6A1A)   // Warm golden
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background circles
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-80).dp, y = (-120).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 80.dp, y = 160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // ── Logo with pulse ring ───────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Outer pulse ring
                if (logoVisible) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }

                // Logo container
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner ring
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text     = "🛒",
                            fontSize = 52.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── App name ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "நம்ம ஊரு ஆப்",
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        textAlign  = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text       = "Namma Ooru App",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tagline ───────────────────────────────────────────────────
            Box(modifier = Modifier.alpha(taglineAlpha)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text       = "🗣️  குரல் வழியே ஆர்டர் செய்யுங்கள்",
                        fontSize   = 13.sp,
                        color      = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        textAlign  = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            // ── Loading dots ──────────────────────────────────────────────
            if (dotsVisible) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(alpha)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Version label at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(taglineAlpha)
        ) {
            Text(
                text      = "பொட்டல்புதூர், திருநெல்வேலி மாவட்டம்",
                style     = MaterialTheme.typography.labelSmall,
                color     = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
