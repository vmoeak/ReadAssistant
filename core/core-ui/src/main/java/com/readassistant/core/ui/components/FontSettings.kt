package com.readassistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun FontSettings(
    fontSize: Int,
    lineHeight: Float,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Font Size", style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            FilledTonalIconButton(onClick = { onFontSizeChange(fontSize - 1) }) {
                Text("-", style = MaterialTheme.typography.titleLarge)
            }
            Text("$fontSize", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onFontSizeChange(fontSize + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Line Height", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.0f..2.5f,
            steps = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Text("%.1f".format(lineHeight), style = MaterialTheme.typography.bodySmall)
    }
}
