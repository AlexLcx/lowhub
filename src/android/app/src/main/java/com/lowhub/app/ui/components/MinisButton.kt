package com.lowhub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

// Material3 ButtonDefaults.MinHeight = 40dp; tuned to 48dp for touch
// ergonomics on phones. IconButton family is unaffected (icon-sized).
val MinisButtonHeight = 48.dp

// Compact button height for actions embedded inside section cards
// (e.g. "Sign out" inside a credentials card, "Set Bearer Token" inside
// a token section). Visually subordinate to MinisButtonHeight (48dp)
// which remains the size for primary screen actions ("Add Custom Model",
// TopAppBar Save, AlertDialog confirm).
val MinisSmallButtonHeight = 32.dp

private val SmallButtonContentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

// [T-frosted-buttons] Approximate frosted glass (iPhone-style) for the main
// buttons: a translucent container plus a 1dp translucent white edge —
// bright in light mode, faint in dark mode. This is flat translucency only:
// the project has no backdrop-blur dependency, and Compose's Modifier.blur
// blurs the element itself rather than what sits behind it, so true frosted
// glass is not achievable here. Only DEFAULT colors / border change —
// callers passing explicit values keep their exact look.
@Composable
private fun frostedButtonColors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = scheme.primary.copy(alpha = 0.62f),
        contentColor = scheme.onPrimary,
        disabledContainerColor = scheme.primary.copy(alpha = 0.38f),
        disabledContentColor = scheme.onPrimary.copy(alpha = 0.7f),
    )
}

@Composable
private fun frostedOutlinedButtonColors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ButtonDefaults.outlinedButtonColors(
        containerColor = scheme.surface.copy(alpha = 0.45f),
        contentColor = scheme.primary,
        disabledContainerColor = scheme.surface.copy(alpha = 0.3f),
        disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
    )
}

@Composable
private fun frostedBorderStroke(): BorderStroke {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.22f else 0.55f))
}

@Composable
fun MinisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = frostedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(
        defaultElevation = 1.dp,
        pressedElevation = 2.dp,
    ),
    border: BorderStroke? = frostedBorderStroke(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinisButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun MinisOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = frostedOutlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = frostedBorderStroke(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinisButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun MinisTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinisButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

// defaultMinSize is also pinned at MinisSmallButtonHeight so Material3's
// internal 40dp floor (ButtonDefaults.MinHeight) doesn't override the
// heightIn modifier and keep the button at 40dp.
@Composable
fun MinisSmallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = SmallButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinisSmallButtonHeight)
            .defaultMinSize(minHeight = MinisSmallButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun MinisSmallOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
    contentPadding: PaddingValues = SmallButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinisSmallButtonHeight)
            .defaultMinSize(minHeight = MinisSmallButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

@Composable
fun MinisSmallTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = SmallButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinisSmallButtonHeight)
            .defaultMinSize(minHeight = MinisSmallButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}
