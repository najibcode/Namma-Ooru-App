package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.Shop
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    onCategorySelected: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Live sync badge
        AnimatedVisibility(visible = !uiState.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2D6E44).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF2D6E44).copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D6E44))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "நேரடி தகவல்கள் புதுப்பிக்கப்படுகின்றன",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D6E44)
                )
            }
        }

        Text(
            text = "உங்களுக்கு என்ன வேண்டும்?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "கீழே உள்ளதைத் தொடவும்",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            FirestoreLoadingShimmer()
        } else {
            BentoCategoryGrid(
                uiState = uiState,
                onCategorySelected = onCategorySelected
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

// ── Shimmer loading placeholder ──────────────────────────────────────────────

@Composable
fun FirestoreLoadingShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "shimmerAlpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Large card shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
        )
    }
}


@Composable
fun BentoCategoryGrid(
    uiState: com.example.ui.viewmodel.HomeUiState,
    onCategorySelected: (String) -> Unit
) {
    // Pick the first subscribed shop ID for each category to route into OrderScreen
    val hotelId   = uiState.hotelShops.firstOrNull { it.isSubscribed }?.id   ?: "1"
    val medicalId = uiState.medicalShops.firstOrNull { it.isSubscribed }?.id ?: "2"
    val meatId    = uiState.meatShops.firstOrNull { it.isSubscribed }?.id    ?: "4"
    val groceryId = uiState.groceryShops.firstOrNull { it.isSubscribed }?.id ?: "3"

    // Hotel image URL from Firestore (falls back to static URL if empty)
    val hotelImageUrl = uiState.hotelShops.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() }
        ?: "https://lh3.googleusercontent.com/aida-public/AB6AXuC4UsQL2OPJ0msDFRqS-Ys_1i-NdorJZ-rgN9c2Kur0eAQCWLtBcvRdzby2Yimd586VCJDBw9EXvoTZhpIZtxJB5msNIXzdW2igD52Aw3SL1sd9D66OBiHIVGrYfjbsUFI86p49A1wCuUO4w9ILIfe0HaSKxwvMNhDv9918wUe5n7K6Ur4CCZurjKEtqkeH9vj4MkUitTheNd2JPHbK9mt7SqEWXTxbSmRTBP4go5LzacMpunt67Y4yz4FNLi8t3uw7cCaD2KEqkw"

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Hotel (Large, spans 2 columns)
        LargeImageCategoryCard(
            title    = "ஹோட்டல் (சாப்பாடு)",
            imageUrl = hotelImageUrl,
            icon     = Icons.Default.Restaurant,
            onClick  = { onCategorySelected(hotelId) }
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmallCategoryCard(
                title              = "மெடிக்கல்\n(மருந்துகள்)",
                icon               = Icons.Default.MedicalServices,
                containerColor     = MaterialTheme.colorScheme.secondaryContainer,
                contentColor       = MaterialTheme.colorScheme.onSecondaryContainer,
                iconContainerColor = Color.White.copy(alpha = 0.5f),
                modifier           = Modifier.weight(1f),
                onClick            = { onCategorySelected(medicalId) }
            )

            SmallCategoryCard(
                title              = "கோழி /\nஆட்டிறைச்சி /\nமீன்",
                icon               = Icons.Default.ShoppingBasket,
                containerColor     = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor       = MaterialTheme.colorScheme.onTertiaryContainer,
                iconContainerColor = Color.White.copy(alpha = 0.5f),
                modifier           = Modifier.weight(1f),
                onClick            = { onCategorySelected(meatId) }
            )
        }

        WideCategoryCard(
            title          = "மளிகைக் கடைகள்",
            subtitle       = "அன்றாடத் தேவைகள்",
            icon           = Icons.Default.Storefront,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick        = { onCategorySelected(groceryId) }
        )

        Phase2Card()
    }
}


@Composable
fun LargeImageCategoryCard(
    title: String,
    imageUrl: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 100f
                    )
                )
        )
        
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun SmallCategoryCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconContainerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(iconContainerColor)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
fun WideCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun Phase2Card() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ElectricalServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "எலக்ட்ரிஷியன்",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Phase 2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
