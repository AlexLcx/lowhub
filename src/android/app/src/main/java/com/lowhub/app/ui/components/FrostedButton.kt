package com.lowhub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * [T-tavern-frosted-button] Frosted-glass button for the tavern
 * surfaces (library primary actions, the setup sheet's confirm row).
 *
 * Heavier and more matte than the approximate translucency
 * [MinisButton] picked up in the frosted-buttons pass: the container
 * is the theme surface at a high alpha (0.75 light / 0.55 dark) with a
 * stronger translucent-white edge, so it reads as a slab of frosted
 * glass rather than a tinted pill. Like MinisButton this is flat
 * translucency only — no backdrop blur (Compose's Modifier.blur blurs
 * the element itself, not what is behind it).
 *
 * Shape and minimum height match [MinisButton] (ButtonDefaults.shape,
 * [MinisButtonHeight]) so the two button families can sit side by side.
 */
@Composable
fun FrostedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinisButtonHeight),
        enabled = enabled,
        shape = ButtonDefaults.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.surface.copy(alpha = if (isDark) 0.55f else 0.75f),
            contentColor = scheme.onSurface,
            disabledContainerColor = scheme.surface.copy(alpha = if (isDark) 0.33f else 0.45f),
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp,
        ),
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (isDark) 0.30f else 0.65f),
        ),
        contentPadding = contentPadding,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        content = content,
    )
}

/** Text-only convenience overload. */
@Composable
fun FrostedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FrostedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}
