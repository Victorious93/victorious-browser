package com.victorious.browser.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.Brand
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.ui.theme.StatusDanger
import com.victorious.browser.ui.theme.StatusIdle
import com.victorious.browser.ui.theme.StatusOk
import com.victorious.browser.ui.theme.StatusRunning
import com.victorious.browser.ui.theme.StatusWarn

/**
 * The Victorious brand mark: a glowing cyan → magenta sweep ring on charcoal.
 * Pure Compose Canvas — no external assets, cheap to draw.
 */
@Composable
fun BrandRing(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 4.dp,
    glow: Boolean = true,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            if (glow) {
                // soft outer glow
                drawCircle(
                    brush = Brand.glow(),
                    radius = this.size.minDimension / 2f,
                )
            }
            val inset = sw / 2f + 2f
            drawArc(
                brush = Brand.ringSweep(),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                style = Stroke(width = sw),
            )
        }
    }
}

/** A section header with the cyan → magenta gradient applied to the text. */
@Composable
fun NeonSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(
            brush = Brand.horizontal(),
            fontWeight = FontWeight.Bold,
        ),
    )
}

/** A gradient-title for heroes/screens. */
@Composable
fun GradientTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = Brand.horizontal(), fontWeight = FontWeight.Bold),
    )
}

/** A card with a subtle gradient border and charcoal fill. */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val base = Modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(Brand.border(borderWidth), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = modifier.then(clickable)) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

enum class NeonStatus { ON, OFF, RUNNING, RISK, WARN, IDLE }

@Composable
fun StatusChip(
    label: String,
    status: NeonStatus,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        NeonStatus.ON -> StatusOk
        NeonStatus.OFF -> StatusIdle
        NeonStatus.RUNNING -> StatusRunning
        NeonStatus.RISK -> StatusDanger
        NeonStatus.WARN -> StatusWarn
        NeonStatus.IDLE -> StatusIdle
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Primary neon button with a gradient fill. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) Brand.diagonal() else Brush.linearGradient(listOf(StatusIdle, StatusIdle)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Secondary neon button: gradient border, transparent fill. */
@Composable
fun NeonOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .clip(shape)
            .border(Brand.border(1.dp), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
    }
}

/**
 * Shared scaffold for every tool screen: a cohesive app bar with a brand ring
 * accent, title, optional subtitle, a back affordance and trailing actions,
 * over a charcoal background. Keeps tool screens from looking ad-hoc.
 */
@Composable
fun ToolScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onBack)
                                .padding(6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = NeonCyan,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    BrandRing(size = 34.dp, strokeWidth = 3.dp, glow = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        GradientTitle(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (actions != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            actions()
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Brand.horizontal()),
                )
            }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * The shared tool-screen app bar (ring accent + gradient title + back + actions
 * + a gradient underline). Drop-in replacement for the ad-hoc header Row that
 * tool screens used, so every tool reads as part of the same system.
 */
@Composable
fun NeonToolHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NeonCyan,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            BrandRing(size = 30.dp, strokeWidth = 3.dp, glow = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                GradientTitle(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (actions != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Brand.horizontal()),
        )
        Spacer(Modifier.height(8.dp))
    }
}
