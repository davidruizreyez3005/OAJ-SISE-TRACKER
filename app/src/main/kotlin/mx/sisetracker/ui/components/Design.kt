package mx.sisetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.ui.theme.LocalBrandColors

/** The app's motion: quick and eased, the same on every screen. */
object SiseMotion {
    const val DURATION_MILLIS = 300

    fun <T> spec(): FiniteAnimationSpec<T> = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)
}

/** The thin gold rule under the guinda chrome. */
@Composable
fun GoldRule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 3.dp, color = LocalBrandColors.current.goldRule)
}

/**
 * A guinda band that continues the top app bar (whose own rule is then
 * hidden) down to a deeper guinda, ending in the gold rule. Text on it uses
 * [mx.sisetracker.ui.theme.BrandColors.onTopBar] and its variants.
 */
@Composable
fun BrandHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val brand = LocalBrandColors.current
    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(brand.topBar, brand.topBarDeep)))
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
        GoldRule()
    }
}

/** An icon in a tinted circle: the lead of cards, sections and list rows. */
@Composable
fun IconBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

/** The colors of the app's cards: the brightest surface, with a hairline border. */
@Composable
fun siseCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
fun siseCardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

/**
 * The app's card, optionally clickable, with an optional [accent] stripe on
 * its leading edge (gold for unseen acuerdos).
 */
@Composable
fun SiseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { if (accent != null) drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = siseCardColors(),
            border = siseCardBorder(),
            content = inner,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = siseCardColors(),
            border = siseCardBorder(),
            content = inner,
        )
    }
}

/**
 * One part of a form or screen: an icon badge and a title (with an optional
 * step number above it, in gold), then the content.
 */
@Composable
fun SectionCard(
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    step: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = siseCardColors(),
        border = siseCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    if (step != null) {
                        Text(
                            step,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

/**
 * A small pill with an optional icon: dates, cuaderno, ids. With large text
 * it wraps instead of cutting what it says; its corners keep it a pill on
 * one line and a rounded box on more.
 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, contentColor = content, modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lines for a name a card shortens: more when the user has enlarged the text. */
@Composable
fun cardNameLines(): Int = if (LocalDensity.current.fontScale > 1.3f) 4 else 2

/** An [InfoChip] for a guinda header band. */
@Composable
fun HeaderChip(text: String, modifier: Modifier = Modifier, icon: Painter? = null) {
    val brand = LocalBrandColors.current
    InfoChip(
        text = text,
        modifier = modifier,
        icon = icon,
        container = Color.White.copy(alpha = 0.14f),
        content = brand.onTopBar,
    )
}

/** The icon for an órgano's kind: a gavel for juzgados, a court building for tribunales. */
@DrawableRes
fun OrganoKind.iconRes(): Int = when (this) {
    OrganoKind.JUZGADOS -> R.drawable.ic_gavel
    OrganoKind.TRIBUNALES -> R.drawable.ic_account_balance
    OrganoKind.OTROS -> R.drawable.ic_description
}

@Composable
fun OrganoKind.icon(): Painter = painterResource(iconRes())

/**
 * The empty home's drawing: two case files, the front one with a guinda
 * header, and a gold seal. Drawn with the theme's colors, so it follows
 * the dark theme.
 */
@Composable
fun CasesIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val brand = LocalBrandColors.current
    Canvas(modifier = modifier.size(184.dp)) {
        val unit = size.minDimension / 100f
        drawCircle(colors.primaryContainer.copy(alpha = 0.55f), radius = 48f * unit)
        // The back file, tilted.
        rotate(-10f, pivot = Offset(46f * unit, 52f * unit)) {
            drawRoundRect(
                colors.surfaceContainerHighest,
                topLeft = Offset(24f * unit, 22f * unit),
                size = Size(40f * unit, 54f * unit),
                cornerRadius = CornerRadius(4f * unit),
            )
        }
        // The front file: white page, guinda header, text lines.
        val left = 36f * unit
        val top = 20f * unit
        val width = 42f * unit
        val height = 58f * unit
        drawRoundRect(
            colors.surfaceContainerLowest,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(4f * unit),
        )
        drawRoundRect(
            colors.outlineVariant,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(4f * unit),
            style = Stroke(width = 0.8f * unit),
        )
        drawRoundRect(
            brand.topBar,
            topLeft = Offset(left, top),
            size = Size(width, 10f * unit),
            cornerRadius = CornerRadius(4f * unit),
        )
        drawRect(brand.goldRule, topLeft = Offset(left, top + 10f * unit), size = Size(width, 1.4f * unit))
        listOf(0.82f, 0.9f, 0.7f, 0.86f, 0.55f).forEachIndexed { index, fraction ->
            val y = top + (18f + index * 7f) * unit
            drawLine(
                colors.outlineVariant,
                start = Offset(left + 5f * unit, y),
                end = Offset(left + 5f * unit + (width - 10f * unit) * fraction, y),
                strokeWidth = 2.2f * unit,
                cap = StrokeCap.Round,
            )
        }
        // The gold seal with a check mark.
        val seal = Offset(74f * unit, 70f * unit)
        drawCircle(colors.tertiary, radius = 13f * unit, center = seal)
        drawCircle(colors.surfaceContainerLowest, radius = 13f * unit, center = seal, style = Stroke(width = 1.6f * unit))
        val check = Path().apply {
            moveTo(seal.x - 5.5f * unit, seal.y + 0.5f * unit)
            lineTo(seal.x - 1.5f * unit, seal.y + 4.5f * unit)
            lineTo(seal.x + 6f * unit, seal.y - 4f * unit)
        }
        drawPath(check, colors.onTertiary, style = Stroke(width = 2.6f * unit, cap = StrokeCap.Round))
    }
}

/** A numbered step for "Cómo funciona": a gold number in a circle, then the text. */
@Composable
fun StepRow(number: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
