package dev.hydrogen.tieba.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Distorts only the sampled backdrop; labels and hit targets remain unchanged. */
@RequiresApi(33)
internal class LiquidRefraction {
    private val shader = RuntimeShader("""
        uniform shader backdrop;
        uniform float2 resolution;
        uniform float density;
        uniform float focus;
        half4 main(float2 p) {
            float radius = resolution.y * 0.5;
            float2 c = p - resolution * 0.5;
            float2 n = float2(c.x - clamp(c.x, -resolution.x * 0.5 + radius, resolution.x * 0.5 - radius), c.y);
            float edge = clamp((radius - length(n)) / max(radius, 1.0), 0.0, 1.0);
            float2 normal = n / max(length(n), 0.001);
            float rim = 1.0 - smoothstep(0.0, 0.55, edge);
            float2 lens = float2((p.x - focus * resolution.x) / (resolution.x * 0.12), c.y / max(radius, 1.0));
            float convex = 1.0 - smoothstep(0.1, 1.0, length(lens));
            float2 offset = normal * rim * 5.0 * density + lens * convex * 2.0 * density;
            float2 uv = clamp(p - offset, float2(0.5), resolution - 0.5);
            half4 color = backdrop.eval(uv);
            color.r = backdrop.eval(clamp(uv + normal * rim * density * 0.55, float2(0.5), resolution - 0.5)).r;
            color.b = backdrop.eval(clamp(uv - normal * rim * density * 0.55, float2(0.5), resolution - 0.5)).b;
            return color;
        }
    """.trimIndent())

    fun effect(width: Float, height: Float, density: Float, focus: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("focus", focus.coerceIn(0f, 1f))
        val blur = RenderEffect.createBlurEffect(10f * density, 10f * density, android.graphics.Shader.TileMode.CLAMP)
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "backdrop"), blur).asComposeRenderEffect()
    }
}
