package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

/** A bounded scroll range, separate from the bottom navigation's visibility thresholds. */
internal class BrowsingHeaderState(private val enabled: Boolean = true) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var height = 0f

    fun updateHeight(value: Float) {
        height = value.coerceAtLeast(0f)
        offset = offset.coerceIn(-height, 0f)
    }

    /** Consume only the distance used by the header; pass the rest to scrolling/refresh. */
    fun scrollBy(delta: Float): Float {
        if (!enabled) return 0f
        val previous = offset
        offset = (previous + delta).coerceIn(-height, 0f)
        return offset - previous
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, scrollBy(available.y))
    }
}

@Composable
internal fun rememberBrowsingHeaderState(enabled: Boolean): BrowsingHeaderState =
    remember(enabled) { BrowsingHeaderState(enabled) }

/** Slide the intact content upwards and release its occupied space without a timed transition. */
@Composable
internal fun BrowsingHeader(state: BrowsingHeaderState, content: @Composable ColumnScope.() -> Unit) {
    Layout(content = { Column(Modifier.fillMaxWidth(), content = content) },
        modifier = Modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        val header = measurables.single().measure(constraints.copy(minHeight = 0))
        state.updateHeight(header.height.toFloat())
        val y = state.offset.roundToInt()
        layout(header.width, (header.height + y).coerceAtLeast(0)) {
            header.placeRelative(0, y)
        }
    }
}
