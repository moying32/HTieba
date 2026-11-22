package dev.hydrogen.tieba

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import dev.hydrogen.tieba.ui.HydrogenApp
import dev.hydrogen.tieba.ui.TiebaViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<TiebaViewModel>()
    private var systemMotionScale by mutableFloatStateOf(1f)
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { readMotionScale() }
    }
    private fun readMotionScale() {
        systemMotionScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceAtLeast(0f)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readMotionScale()
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
        intent.dataString?.let(model::navigateInput)
        // Propagate zero duration to Material sheets, ripples and controls too.
        // This is local to this window and preserves the user's system setting.
        val motionScale = object : MotionDurationScale {
            override val scaleFactor: Float get() = if (model.minimalMode) 0f else systemMotionScale
        }
        val recomposer = window.decorView.createLifecycleAwareWindowRecomposer(motionScale, lifecycle)
        setContent(parent = recomposer) { HydrogenApp(model) }
        model.updates.onLaunch()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(model::navigateInput)
    }
    override fun onResume() {
        super.onResume()
        model.refreshAccount()
    }
    override fun onDestroy() {
        contentResolver.unregisterContentObserver(motionObserver)
        super.onDestroy()
    }
}
