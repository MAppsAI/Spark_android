package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ScrimBlue
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.SparkBlueBright
import com.example.ui.theme.SparkBlueDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * Spark design primitives — the shared vocabulary every screen draws from:
 * layered cards with hairline borders, a signature azure gradient, and
 * premium empty states. Keeping these centralized is what makes the app
 * feel designed rather than assembled.
 */

// Signature brand gradient (used sparingly: hero accents, FAB, active pills)
val SparkGradient =
  Brush.linearGradient(
    listOf(SparkBlueBright, SparkBlue, SparkBlueDeep),
  )

val SparkGradientVertical =
  Brush.verticalGradient(
    listOf(SparkBlueBright, SparkBlue),
  )

// Subtle wash behind hero headers — reads as depth, not decoration
fun heroWash(topTint: Color = ScrimBlue): Brush =
  Brush.verticalGradient(
    0f to topTint.copy(alpha = 0.55f),
    0.6f to topTint.copy(alpha = 0.12f),
    1f to Color.Transparent,
  )

@Composable
fun SparkCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  contentPadding: androidx.compose.foundation.layout.PaddingValues =
    androidx.compose.foundation.layout.PaddingValues(16.dp),
  containerColor: Color = DarkSurface,
  borderColor: Color = DarkBorder,
  gradientBorder: Boolean = false,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(18.dp)
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val alpha by animateFloatAsState(if (pressed) 0.88f else 1f, label = "press")

  val base = modifier
    .fillMaxWidth()
    .clip(shape)
    .background(containerColor.copy(alpha = alpha))
    .then(
      if (gradientBorder) {
        Modifier.border(
          width = 1.dp,
          brush = Brush.linearGradient(
            listOf(SparkBlue.copy(alpha = 0.65f), SparkBlue.copy(alpha = 0.12f)),
          ),
          shape = shape,
        )
      } else {
        Modifier.border(width = 1.dp, color = borderColor, shape = shape)
      },
    )
    .then(
      if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
      else Modifier,
    )

  Box(modifier = base) {
    Column(modifier = Modifier.padding(contentPadding)) { content() }
  }
}

// Small square icon tile with tinted wash — the standard leading motif
@Composable
fun IconTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  modifier: Modifier = Modifier,
  size: androidx.compose.ui.unit.Dp = 38.dp,
  iconSize: androidx.compose.ui.unit.Dp = 20.dp,
  corner: Int = 12,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(corner.dp))
      .background(
        Brush.linearGradient(
          listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f)),
        ),
      )
      .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(corner.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
  }
}

// Oversized frosted icon for empty states
@Composable
fun SparkEmptyState(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Hub,
  accent: Color = SparkBlue,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(88.dp)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f)),
          ),
        )
        .border(1.dp, accent.copy(alpha = 0.22f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(38.dp),
      )
    }
    Spacer(modifier = Modifier.height(18.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = TextMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    )
    if (action != null) {
      Spacer(modifier = Modifier.height(22.dp))
      action()
    }
  }
}

// Consistent section label used above groups of content
@Composable
fun SectionLabel(
  text: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text.uppercase(),
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
      ),
      color = TextMuted,
    )
    trailing?.invoke()
  }
}

// Thin full-width divider that never shouts
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(DarkBorderSubtle),
  )
}

// Pill container used for metadata rows (mono text values)
@Composable
fun MetaPillRow(
  items: List<Pair<String, String>>,
  modifier: Modifier = Modifier,
  valueColor: Color = TextSecondary,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items.forEach { (label, value) ->
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(DarkSurfaceVariant)
          .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
          color = valueColor,
        )
      }
    }
  }
}
