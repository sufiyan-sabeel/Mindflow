package com.mindflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.ui.theme.AccentLime
import com.mindflow.app.ui.theme.BorderColor
import com.mindflow.app.ui.theme.ErrorRed
import com.mindflow.app.ui.theme.PrimaryPurple
import com.mindflow.app.ui.theme.Surface1
import com.mindflow.app.ui.theme.Surface2
import com.mindflow.app.ui.theme.Success
import com.mindflow.app.ui.theme.TextPrimary
import com.mindflow.app.ui.theme.TextSecondary

@Composable
fun MfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = Surface1,
        shape = shape,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier,
        color = Surface2,
        shape = shape,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
fun TierChip(rating: Int, modifier: Modifier = Modifier) {
    val tier = RatingEngine.tier(rating)
    val color = tierColor(tier)
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            tier.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

fun tierColor(tier: String): Color = when (tier) {
    "Master" -> Color(0xFFFF6FD8)
    "Diamond" -> Color(0xFF6FD6FF)
    "Platinum" -> Color(0xFFB7FF59)
    "Gold" -> Color(0xFFFFD166)
    "Silver" -> Color(0xFFC7C9D1)
    else -> Color(0xFFCE8A5B)
}

@Composable
fun StarRow(stars: Int, size: Int = 18, modifier: Modifier = Modifier) {
    Row(modifier) {
        repeat(3) { i ->
            Text(
                if (i < stars) "★" else "☆",
                color = if (i < stars) AccentLime else BorderColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = AccentLime) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Surface2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun MfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PrimaryPurple,
    contentColor: Color = Color(0xFF101010)
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) color else Surface2,
        shape = shape
    ) {
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) contentColor else TextSecondary
            )
        }
    }
}

@Composable
fun MfOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled) { onClick() },
        color = Surface1,
        shape = shape,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
    }
}

@Composable
fun AnswerFeedback(correct: Boolean) {
    Text(
        if (correct) "Correct" else "Wrong",
        color = if (correct) Success else ErrorRed,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun Avatar(seed: Int, size: Int = 44, modifier: Modifier = Modifier) {
    val palette = listOf(
        Color(0xFFA995FF), Color(0xFFB7FF59), Color(0xFF6FD6FF),
        Color(0xFFFF6FD8), Color(0xFFFFD166), Color(0xFF57D9A3)
    )
    val c = palette[Math.floorMod(seed, palette.size)]
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = 0.25f))
            .border(1.dp, c.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "M",
            color = c,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun KeypadValue(onDigit: (Int) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        KeyButton(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key.toInt())
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier.clip(shape).clickable { onClick() },
        color = Surface2,
        shape = shape,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Box(Modifier.padding(vertical = 16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
    }
}
