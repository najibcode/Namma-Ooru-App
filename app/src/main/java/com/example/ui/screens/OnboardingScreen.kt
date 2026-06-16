package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
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
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// OnboardingScreen.kt — 3-page swipeable onboarding for நம்ம ஊரு ஆப்
//
// Pages:
//   1. Welcome       — App introduction with village community theme
//   2. Voice Order   — How to use the mic to place orders
//   3. Local Shops   — Browse your neighbourhood stores + WhatsApp dispatch
//
// Uses HorizontalPager (Compose Foundation) for swipe gestures.
// Dot indicator tracks current page.
// "தொடங்கலாம்!" CTA fires onComplete() on the last page.
// ══════════════════════════════════════════════════════════════════════════════

data class OnboardingPage(
    val emoji: String,
    val titleTamil: String,
    val subtitleTamil: String,
    val descriptionTamil: String,
    val gradientColors: List<Color>,
    val accentColor: Color
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji            = "🏘️",
        titleTamil       = "வணக்கம்!",
        subtitleTamil    = "நம்ம ஊரு ஆப்-க்கு\nவரவேற்கிறோம்",
        descriptionTamil = "உங்கள் ஊரின் கடைகளிடம் தமிழில் குரல் வழியாக ஆர்டர் செய்யுங்கள். டைப் செய்யத் தேவையில்லை!",
        gradientColors   = listOf(Color(0xFF6B2E00), Color(0xFF944a00), Color(0xFFBF6A1A)),
        accentColor      = Color(0xFFFFD580)
    ),
    OnboardingPage(
        emoji            = "🎙️",
        titleTamil       = "குரல் வழி ஆர்டர்",
        subtitleTamil    = "மைக்-ஐ அழுத்தி\nபேசுங்கள்!",
        descriptionTamil = "\"4 பரோட்டா, 1 குருமா வேண்டும்\" என்று சொல்லுங்கள். ஆப் உங்கள் குரலைப் பதிவு செய்து கடைக்காரருக்கு அனுப்பும்.",
        gradientColors   = listOf(Color(0xFF1A3D2A), Color(0xFF2D6E44), Color(0xFF3b6934)),
        accentColor      = Color(0xFF7EFFD4)
    ),
    OnboardingPage(
        emoji            = "🛒",
        titleTamil       = "WhatsApp அறிவிப்பு",
        subtitleTamil    = "கடைக்காரருக்கு\nதானாக செய்தி போகும்!",
        descriptionTamil = "ஆர்டர் செய்தவுடன் கடைக்காரருக்கு WhatsApp மெசேஜ் மற்றும் போன் கால் தானாகவே போகும். நீங்கள் கவலைப்படவே வேண்டாம்!",
        gradientColors   = listOf(Color(0xFF1A1040), Color(0xFF2D1B7A), Color(0xFF4B2DAE)),
        accentColor      = Color(0xFFB8A9FF)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState  = rememberPagerState(pageCount = { onboardingPages.size })
    val scope       = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            OnboardingPage(
                page        = onboardingPages[pageIndex],
                isVisible   = pageIndex == pagerState.currentPage
            )
        }

        // ── Bottom controls overlay ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                onboardingPages.indices.forEach { index ->
                    val isActive = index == currentPage
                    val dotWidth by animateDpAsState(
                        targetValue   = if (isActive) 28.dp else 8.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label         = "dotWidth$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isActive) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // CTA Button
            val isLastPage = currentPage == onboardingPages.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape  = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = onboardingPages[currentPage].gradientColors.last()
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text       = if (isLastPage) "தொடங்கலாம்! 🎉" else "அடுத்தது →",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isLastPage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button (only on non-last pages)
            if (!isLastPage) {
                TextButton(
                    onClick = onComplete,
                    colors  = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.65f))
                ) {
                    Text(
                        text       = "தவிர்",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    page: OnboardingPage,
    isVisible: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pageAnim")

    // Floating animation for the emoji
    val floatOffset by infiniteTransition.animateFloat(
        initialValue  = -12f,
        targetValue   = 12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Pulse for the glow ring
    val glowScale by infiniteTransition.animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(page.gradientColors)),
        contentAlignment = Alignment.TopCenter
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-80).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 120.dp, bottom = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // ── Emoji illustration ─────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(page.accentColor.copy(alpha = glowAlpha))
                )
                // Main circle
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .offset(y = floatOffset.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = page.emoji,
                        fontSize = 72.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Text content ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = isVisible,
                enter   = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Category label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(page.accentColor.copy(alpha = 0.2f))
                            .border(1.dp, page.accentColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text       = page.titleTamil,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = page.accentColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main heading
                    Text(
                        text       = page.subtitleTamil,
                        fontSize   = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        textAlign  = TextAlign.Center,
                        lineHeight = 40.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Description
                    Text(
                        text       = page.descriptionTamil,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color      = Color.White.copy(alpha = 0.82f),
                        textAlign  = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}
