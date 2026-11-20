package com.pdh.cardvault.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.R
import com.pdh.cardvault.ui.theme.CardVaultMotion
import com.pdh.cardvault.ui.theme.pressScale
import com.pdh.cardvault.ui.theme.specularRim
import com.pdh.cardvault.wallet.BackupReminder
import com.pdh.cardvault.wallet.BackupReminderPolicy
import java.util.Calendar

/** Time-of-day greeting shown above the home title. */
internal fun greetingResForHour(hour: Int): Int = when (hour) {
    in 5..10 -> R.string.home_greeting_morning
    in 11..12 -> R.string.home_greeting_noon
    in 13..17 -> R.string.home_greeting_afternoon
    in 18..22 -> R.string.home_greeting_evening
    else -> R.string.home_greeting_night
}

@Composable
fun VaultHomeScreen(
    onOpenCards: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    cardCount: Int = 0,
    addressCount: Int = 0,
    backupReminder: BackupReminder? = null,
    onOpenBackup: () -> Unit = {},
    onSnoozeBackupReminder: () -> Unit = {},
) {
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val entranceProgress = remember { Animatable(if (entrancePlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!entrancePlayed) {
            entranceProgress.animateTo(1f, CardVaultMotion.navSlide())
            entrancePlayed = true
        }
    }
    val greetingRes = remember { greetingResForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 18.dp.toPx() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            ObsidianStageBackdrop(
                primary = MaterialTheme.colorScheme.primary,
                secondary = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .widthIn(max = 540.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        val p = entranceProgress.value
                        alpha = p
                        translationY = (1f - p) * slideOffsetPx
                    },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        modifier = Modifier.specularRim(cornerRadius = 24.dp, topAlpha = 0.16f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(Modifier.size(7.dp)) {
                                drawCircle(color = Color(0xFF5CE1B8))
                            }
                            Text(
                                text = stringResource(R.string.home_offline_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val settingsInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .pressScale(settingsInteraction, pressedScale = 0.94f)
                            .specularRim(cornerRadius = 24.dp, topAlpha = 0.20f),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        interactionSource = settingsInteraction,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(greetingRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.vault_home_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.vault_home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                backupReminder?.let { reminder ->
                    BackupReminderCard(
                        reminder = reminder,
                        onOpenBackup = onOpenBackup,
                        onSnooze = onSnoozeBackupReminder,
                    )
                }

                VaultModeCard(
                    kind = VaultPortalKind.Cards,
                    accent = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.vault_home_cards_title),
                    subtitle = stringResource(R.string.vault_home_cards_subtitle),
                    count = cardCount,
                    countUnit = stringResource(R.string.vault_home_cards_unit),
                    onClick = onOpenCards,
                )

                VaultModeCard(
                    kind = VaultPortalKind.Addresses,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.vault_home_addresses_title),
                    subtitle = stringResource(R.string.vault_home_addresses_subtitle),
                    count = addressCount,
                    countUnit = stringResource(R.string.vault_home_addresses_unit),
                    onClick = onOpenAddresses,
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Static cached multi-layer atmospheric lighting (`0%` idle GPU invalidations).
 * All shaders and geometry are built once per screen size inside [drawWithCache].
 */
@Composable
private fun ObsidianStageBackdrop(
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val maxDim = size.maxDimension.coerceAtLeast(1f)
            val primaryCenter = Offset(size.width * 0.16f, size.height * 0.10f)
            val primaryRadius = maxDim * 0.62f
            val primaryBrush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.17f), Color.Transparent),
                center = primaryCenter,
                radius = primaryRadius,
            )

            val secondaryCenter = Offset(size.width * 0.92f, size.height * 0.64f)
            val secondaryRadius = maxDim * 0.54f
            val secondaryBrush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.11f), Color.Transparent),
                center = secondaryCenter,
                radius = secondaryRadius,
            )

            val beamPath = Path().apply {
                moveTo(size.width * 0.54f, 0f)
                lineTo(size.width * 0.88f, 0f)
                lineTo(size.width, size.height * 0.36f)
                lineTo(size.width * 0.30f, size.height * 0.22f)
                close()
            }
            val beamBrush = Brush.linearGradient(
                colors = listOf(primary.copy(alpha = 0.055f), Color.Transparent),
                start = Offset(size.width * 0.72f, 0f),
                end = Offset(size.width * 0.45f, size.height * 0.34f),
            )

            onDrawBehind {
                drawCircle(brush = primaryBrush, radius = primaryRadius, center = primaryCenter)
                drawCircle(brush = secondaryBrush, radius = secondaryRadius, center = secondaryCenter)
                drawPath(path = beamPath, brush = beamBrush)
            }
        },
    )
}

@Composable
private fun BackupReminderCard(
    reminder: BackupReminder,
    onOpenBackup: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .specularRim(cornerRadius = 32.dp, topAlpha = 0.22f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (reminder) {
                    BackupReminder.NeverSynced ->
                        stringResource(R.string.backup_reminder_never_title)
                    is BackupReminder.Overdue ->
                        stringResource(R.string.backup_reminder_overdue_title, reminder.daysSinceSync)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.backup_reminder_detail),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenBackup) {
                    Text(stringResource(R.string.backup_reminder_action))
                }
                TextButton(onClick = onSnooze) {
                    Text(stringResource(R.string.backup_reminder_snooze, BackupReminderPolicy.SNOOZE_DAYS))
                }
            }
        }
    }
}

private enum class VaultPortalKind {
    Cards,
    Addresses,
}

@Composable
private fun VaultModeCard(
    kind: VaultPortalKind,
    accent: Color,
    title: String,
    subtitle: String,
    count: Int,
    countUnit: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .specularRim(cornerRadius = 32.dp, topAlpha = 0.22f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .drawWithCache {
                    val glowRadius = size.maxDimension * 0.55f
                    val glowCenter = Offset(size.width * 0.12f, size.height * 0.2f)
                    val glowBrush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.13f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius,
                    )
                    onDrawBehind {
                        drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
                    }
                }
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .specularRim(cornerRadius = 20.dp, topAlpha = 0.26f),
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.16f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VaultPortalGlyph(kind = kind, color = accent)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                )
                Text(
                    text = countUnit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VaultPortalGlyph(kind: VaultPortalKind, color: Color) {
    Canvas(Modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        when (kind) {
            VaultPortalKind.Cards -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.18f),
                    size = Size(size.width * 0.84f, size.height * 0.64f),
                    cornerRadius = CornerRadius(size.minDimension * 0.16f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = Offset(size.width * 0.12f, size.height * 0.39f),
                    end = Offset(size.width * 0.88f, size.height * 0.39f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.9f),
                    topLeft = Offset(size.width * 0.20f, size.height * 0.53f),
                    size = Size(size.width * 0.22f, size.height * 0.15f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                )
            }
            VaultPortalKind.Addresses -> {
                val center = Offset(size.width * 0.5f, size.height * 0.42f)
                val pinPath = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.90f)
                    cubicTo(
                        size.width * 0.22f,
                        size.height * 0.66f,
                        size.width * 0.16f,
                        size.height * 0.48f,
                        size.width * 0.16f,
                        size.height * 0.40f,
                    )
                    cubicTo(
                        size.width * 0.16f,
                        size.height * 0.18f,
                        size.width * 0.32f,
                        size.height * 0.10f,
                        size.width * 0.5f,
                        size.height * 0.10f,
                    )
                    cubicTo(
                        size.width * 0.68f,
                        size.height * 0.10f,
                        size.width * 0.84f,
                        size.height * 0.18f,
                        size.width * 0.84f,
                        size.height * 0.40f,
                    )
                    cubicTo(
                        size.width * 0.84f,
                        size.height * 0.48f,
                        size.width * 0.78f,
                        size.height * 0.66f,
                        size.width * 0.5f,
                        size.height * 0.90f,
                    )
                    close()
                }
                drawPath(pinPath, color = color, style = Stroke(width = stroke))
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.11f,
                    center = center,
                )
            }
        }
    }
}
