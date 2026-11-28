package dev.hydrogen.tieba.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) = CircularProgressIndicator(modifier, color, strokeWidth)

@Composable
internal fun AppLinearProgressIndicator(modifier: Modifier = Modifier) = LinearProgressIndicator(modifier)

@Composable
internal fun AppLinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator(progress = progress, modifier = modifier)
