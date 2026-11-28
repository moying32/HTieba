package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.ui.theme.currentDesign
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import dev.hydrogen.tieba.data.VisualStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Editorial surfaces stay inexpensive; the floating controls retain real glass sampling. */
@Composable
internal fun LayeredInputCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val editorial = LocalVisualStyle.current == VisualStyle.Claude
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(if (editorial) 19.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (editorial) 13.dp else 10.dp), content = content)
    }
}

/** Independent lazy items form one surface without composing the whole hot list. */
@Composable
internal fun LayeredListPart(
    first: Boolean = false,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    val radius = currentDesign.card.dp
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(
            topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
            bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
