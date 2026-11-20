package dev.hydrogen.tieba.ui

/** One press owns a speed snapshot. Release, cancellation and lifecycle cleanup
 * all use finish(), so late cleanup cannot overwrite a later speed selection. */
internal class VideoHoldSpeed {
    var active = false
        private set
    private var originalSpeed = 1f

    fun begin(playing: Boolean, currentSpeed: Float): Float? {
        if (!playing || active) return null
        originalSpeed = currentSpeed
        active = true
        return 2f
    }

    fun finish(): Float? {
        if (!active) return null
        active = false
        return originalSpeed
    }
}

internal object VideoControlsPolicy {
    const val AutoHideDelay = 2500L

    fun isHoldEdge(x: Float, width: Float): Boolean =
        width > 0f && x in 0f..width && (x <= width * .35f || x >= width * .65f)

    fun canAutoHide(playing: Boolean, busy: Boolean, seeking: Boolean, menuOpen: Boolean,
        touchExploration: Boolean, holding: Boolean, failed: Boolean, controlFocused: Boolean = false): Boolean =
        playing && !busy && !seeking && !menuOpen && !touchExploration && !holding && !failed && !controlFocused
}
