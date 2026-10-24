package com.lowhub.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lowhub.app.R
import com.lowhub.app.agent.WritingSettings
import com.lowhub.app.agent.WritingStore
import com.lowhub.app.ui.components.FrostedButton

/**
 * [T-writing-controls] Section header for the composer sheets,
 * extracted for this sheet and styled to match TavernSetupSheet /
 * ChatPersonaPickerSheet exactly (20dp horizontal padding on the
 * sheet column, 15sp Medium section labels, 12–16dp gaps), so the
 * three sheets read as one family.
 */
@Composable
private fun WritingSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * [T-writing-controls] One selectable option in the writing sheet.
 * Unselected chips wear the FrostedButton treatment (theme surface at
 * high alpha + translucent white edge); the selected chip switches to
 * a primary tint with a primary edge so the current pick reads at a
 * glance.
 */
@Composable
private fun WritingChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            scheme.primary.copy(alpha = 0.16f)
        } else {
            scheme.surface.copy(alpha = if (isDark) 0.55f else 0.75f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                scheme.primary.copy(alpha = 0.65f)
            } else {
                Color.White.copy(alpha = if (isDark) 0.30f else 0.65f)
            },
        ),
        contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/**
 * [T-writing-controls] A sampling-knob row (temperature / Top P): a
 * label + value + enable switch header, and a stepped slider while
 * enabled. OFF means the setting is null — "send nothing, provider /
 * per-model config decides" — which the value label shows as
 * "Default". Dragging only moves local state; the ViewModel is
 * updated when the drag finishes, so a drag doesn't spam persists.
 */
@Composable
private fun SamplingRow(
    label: String,
    value: Double?,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    defaultWhenEnabled: Double,
    onChange: (Double?) -> Unit,
) {
    var sliderValue by remember(value != null) {
        mutableStateOf((value ?: defaultWhenEnabled).toFloat())
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (value == null) {
                stringResource(R.string.writing_sampling_default)
            } else {
                String.format("%.2f", sliderValue)
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(
            checked = value != null,
            onCheckedChange = { on ->
                onChange(if (on) snapSampling(sliderValue) else null)
            },
        )
    }
    if (value != null) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(snapSampling(sliderValue)) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Slider steps are 0.05; store the pushed value snapped to cents. */
private fun snapSampling(v: Float): Double = Math.round(v * 100.0) / 100.0

/**
 * [T-writing-controls] The writing-controls sheet (composer "writing"
 * capsule → this). Every control writes through to
 * [ChatViewModel.updateWritingSettings] immediately — there is no
 * save button; dismissing the sheet is the confirmation. The Done
 * button is a FrostedButton like the tavern sheet's confirm row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingSettingsSheet(
    settings: WritingSettings,
    onUpdate: (WritingSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.writing_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // Novel mode toggle.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.writing_novel_mode),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.writing_novel_mode_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.novelMode,
                    onCheckedChange = { on ->
                        onUpdate(settings.copy(novelMode = on))
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            // Length: output-form tiers normally, K-char picks in
            // novel mode (the custom target then wins over the tier).
            WritingSectionHeader(stringResource(R.string.writing_section_length))
            Spacer(Modifier.height(8.dp))
            if (settings.novelMode) {
                val ks = listOf(1, 2, 3, 4, 5, 6, 8, 10)
                ks.chunked(4).forEachIndexed { index, rowKs ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowKs.forEach { k ->
                            WritingChip(
                                selected = settings.customKChars == k,
                                onClick = {
                                    onUpdate(settings.copy(customKChars = k))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.writing_k_chars, k),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        repeat(4 - rowKs.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                val tiers = listOf(
                    Triple(
                        WritingStore.TIER_CONCISE,
                        R.string.writing_tier_concise,
                        R.string.writing_tier_concise_range,
                    ),
                    Triple(
                        WritingStore.TIER_STANDARD,
                        R.string.writing_tier_standard,
                        R.string.writing_tier_standard_range,
                    ),
                    Triple(
                        WritingStore.TIER_DETAILED,
                        R.string.writing_tier_detailed,
                        R.string.writing_tier_detailed_range,
                    ),
                    Triple(
                        WritingStore.TIER_ULTRA,
                        R.string.writing_tier_ultra,
                        R.string.writing_tier_ultra_range,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiers.forEach { (tier, labelRes, rangeRes) ->
                        WritingChip(
                            selected = settings.lengthTier == tier,
                            onClick = {
                                onUpdate(settings.copy(lengthTier = tier))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(labelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(rangeRes),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Prose style.
            WritingSectionHeader(stringResource(R.string.writing_section_style))
            Spacer(Modifier.height(8.dp))
            val styles = listOf(
                WritingStore.STYLE_PROFESSIONAL to R.string.writing_style_professional,
                WritingStore.STYLE_VIVID to R.string.writing_style_vivid,
                WritingStore.STYLE_CREATIVE to R.string.writing_style_creative,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                styles.forEach { (style, labelRes) ->
                    WritingChip(
                        selected = settings.style == style,
                        onClick = { onUpdate(settings.copy(style = style)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Sampling knobs.
            SamplingRow(
                label = stringResource(R.string.writing_temperature),
                value = settings.temperature,
                range = 0f..2f,
                steps = 39,
                defaultWhenEnabled = 1.0,
                onChange = { onUpdate(settings.copy(temperature = it)) },
            )
            SamplingRow(
                label = stringResource(R.string.writing_top_p),
                value = settings.topP,
                range = 0f..1f,
                steps = 19,
                defaultWhenEnabled = 0.95,
                onChange = { onUpdate(settings.copy(topP = it)) },
            )
            Spacer(Modifier.height(20.dp))

            FrostedButton(
                text = stringResource(R.string.writing_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
