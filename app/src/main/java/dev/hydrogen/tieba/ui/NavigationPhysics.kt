package dev.hydrogen.tieba.ui

import kotlin.math.abs

internal object NavigationPhysics {
    fun resist(position: Float): Float = when {
        position < 0f -> position * .18f
        position > 4f -> 4f + (position - 4f) * .18f
        else -> position
    }

    // The center plus is an action. A scrub gesture must never open publishing.
    fun destination(position: Float, velocity: Float): HomeTab {
        val projected = (position + (velocity * .06f).coerceIn(-.35f, .35f)).coerceIn(0f, 4f)
        return HomeTab.entries.filter { it != HomeTab.Compose }.minBy { abs(it.ordinal - projected) }
    }
}
