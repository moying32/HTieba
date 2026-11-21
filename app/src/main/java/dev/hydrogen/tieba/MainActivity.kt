package dev.hydrogen.tieba

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.hydrogen.tieba.ui.HydrogenApp
import dev.hydrogen.tieba.ui.TiebaViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<TiebaViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.dataString?.let(model::navigateInput)
        setContent { HydrogenApp(model) }
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
}
