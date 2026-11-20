package dev.hydrogen.tieba.ui

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal fun tabDirection(from: String, to: String): Int {
    val tabs = listOf("Feed", "Browse", "Messages", "Profile")
    return if (tabs.indexOf(to.substringAfter(':')) >= tabs.indexOf(from.substringAfter(':'))) 1 else -1
}

/** Only the page content moves; the glass navigation and its lens keep their own physics. */
@Composable
internal fun AnimatedContentScope.cameraMaterial(enabled: Boolean, canvas: Color): Modifier {
    if (!enabled) return Modifier
    val clarity by transition.animateFloat(
        transitionSpec = { tween(HydrogenMotion.TabDuration, easing = HydrogenMotion.EaseOut) }, label = "页面清晰度"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = (1f - clarity) * 5.dp.toPx()
            renderEffect = if (radius > .1f) android.graphics.RenderEffect.createBlurEffect(radius, radius,
                android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
        }
    }.drawWithCache {
        val vignette = Brush.radialGradient(listOf(Color.Transparent, canvas),
            center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * .65f)
        onDrawWithContent {
            drawContent()
            val amount = (1f - clarity) * .38f
            if (amount > .001f) drawRect(vignette, alpha = amount)
        }
    }
}
