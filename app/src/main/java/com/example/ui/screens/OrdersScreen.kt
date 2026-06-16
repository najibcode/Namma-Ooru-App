package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OrderHistoryRepository
import com.example.data.OrderRecord

// ══════════════════════════════════════════════════════════════════════════════
// OrdersScreen.kt — Live voice-order history tab
//
// Reactive:
//   Observes OrderHistoryRepository.ordersFlow via collectAsStateWithLifecycle.
//   Every time OrderViewModel appends a new order on the Main dispatcher,
//   this screen recomposes automatically within one frame — no pull-to-refresh.
//
// Structure:
//   ┌─ Header (icon + title) ─────────────────────────────────────────────┐
//   │  LazyColumn of OrderHistoryCard items (newest first)                │
//   │  Empty state: illustrated card if no orders placed yet              │
//   └─────────────────────────────────────────────────────────────────────┘
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun OrdersScreen(innerPadding: PaddingValues) {

    // ── Live observation: recomposes whenever a new order is appended ─────────
    val orders by OrderHistoryRepository.ordersFlow.collectAsStateWithLifecycle(
        initialValue = OrderHistoryRepository.currentOrders()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = "Orders",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text  = "உங்களின் ஆர்டர்கள்",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "${orders.size} வாய்ஸ் ஆர்டர்கள்",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // ── Body: lazy list or empty state ────────────────────────────────────
        if (orders.isEmpty()) {
            EmptyOrdersState(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement   = Arrangement.spacedBy(16.dp)
            ) {
                items(orders, key = { it.id }) { record ->
                    OrderHistoryCard(record = record)
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order history card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Displays a single [OrderRecord] with shop name, voice transcript, delivery mode,
 * price, and timestamp.
 *
 * Status badge colour is driven by [OrderRecord.isDispatched]:
 * - `true`  → green secondary (ஆர்டர் அனுப்பப்பட்டது)
 * - `false` → amber primary  (WhatsApp திறக்கப்படவில்லை)
 */
@Composable
fun OrderHistoryCard(record: OrderRecord) {
    val statusLabel = if (record.isDispatched) "ஆர்டர் அனுப்பப்பட்டது"
                      else                     "WhatsApp திறக்கவில்லை"
    val statusColor = if (record.isDispatched) MaterialTheme.colorScheme.secondary
                      else                     MaterialTheme.colorScheme.tertiary
    val statusBg    = if (record.isDispatched) MaterialTheme.colorScheme.secondaryContainer
                      else                     MaterialTheme.colorScheme.tertiaryContainer

    val categoryEmoji = when (record.category) {
        "ஹோட்டல்"  -> "🍲"
        "மெடிக்கல்" -> "💊"
        "இறைச்சி"  -> "🍗"
        else        -> "🛒"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            ),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Row 1: shop name + status badge ──────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = record.shopName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(statusBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = statusLabel,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Row 2: voice transcript ───────────────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Mic,
                        contentDescription = "Voice Order",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text  = "பேசிய விபரம்:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text       = "\"${record.transcript}\"",
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Row 3: delivery mode chip ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = "🚚 ${record.deliveryMode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(12.dp))

            // ── Row 4: timestamp + price ──────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Schedule,
                        contentDescription = "Date",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text  = record.timestamp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text       = record.displayPrice,
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shown when [OrderHistoryRepository.ordersFlow] emits an empty list.
 *
 * In practice this should never appear since the repository is seeded with
 * sample data, but it's the correct UX for a clean install with no orders.
 */
@Composable
fun EmptyOrdersState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.MoodBad,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = "இதுவரை ஆர்டர் இல்லை",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "வீட்டு திரையில் கடை தேர்ந்தெடுத்து குரல் ஆர்டர் செய்யுங்கள்",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
