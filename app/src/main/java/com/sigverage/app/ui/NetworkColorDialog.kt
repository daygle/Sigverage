package com.sigverage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.NetworkType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Colour picker for a single [NetworkType].
 *
 * Offers a strip of common presets for one-tap choices plus RGB sliders for
 * full control, with a live preview showing the resulting colour and its hex
 * code. [onReset] reverts the network to its built-in default; [onConfirm]
 * commits the picked colour. All channels are edited as 0-255 ints so the hex
 * readout is exact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkColorDialog(
    type: NetworkType,
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    onReset: () -> Unit,
) {
    var red by remember { mutableIntStateOf((initial.red * 255f).roundToInt()) }
    var green by remember { mutableIntStateOf((initial.green * 255f).roundToInt()) }
    var blue by remember { mutableIntStateOf((initial.blue * 255f).roundToInt()) }

    val current = Color(red = red, green = green, blue = blue)
    val hex = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.settings_network_colors_dialog_title, type.label))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Live preview swatch + hex readout.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(current, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hex,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Quick presets.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRESETS.forEach { preset ->
                        val selected = preset == current
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(preset, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    red = (preset.red * 255f).roundToInt()
                                    green = (preset.green * 255f).roundToInt()
                                    blue = (preset.blue * 255f).roundToInt()
                                },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                ChannelSlider(
                    label = stringResource(R.string.color_channel_red),
                    value = red,
                    track = Color(0xFFEF4444),
                    onValueChange = { red = it },
                )
                ChannelSlider(
                    label = stringResource(R.string.color_channel_green),
                    value = green,
                    track = Color(0xFF22C55E),
                    onValueChange = { green = it },
                )
                ChannelSlider(
                    label = stringResource(R.string.color_channel_blue),
                    value = blue,
                    track = Color(0xFF3B82F6),
                    onValueChange = { blue = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.settings_network_colors_dialog_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.settings_network_colors_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        },
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    track: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(20.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = track,
                activeTrackColor = track,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(32.dp),
        )
    }
}

/** A spread of distinct, legible presets covering the common hue wheel. */
private val PRESETS: List<Color> = listOf(
    Color(0xFFEF4444), // red
    Color(0xFFF97316), // orange
    Color(0xFFF59E0B), // amber
    Color(0xFFEAB308), // yellow
    Color(0xFF22C55E), // green
    Color(0xFF10B981), // emerald
    Color(0xFF06B6D4), // cyan
    Color(0xFF0EA5E9), // sky
    Color(0xFF3B82F6), // blue
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFF64748B), // slate
    Color(0xFF000000), // black
    Color(0xFFFFFFFF), // white
)
