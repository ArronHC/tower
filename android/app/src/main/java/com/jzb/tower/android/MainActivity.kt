package com.jzb.tower.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jzb.tower.android.ui.TowerAppRoot
import com.jzb.tower.core.model.AppTab

class MainActivity : ComponentActivity() {

    private val viewModel: TowerViewModel by viewModels {
        val repo = (application as TowerApplication).container.repository
        TowerViewModel.Factory(repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.initialize()
        handleIncomingShareIntent(intent)

        setContent {
            TowerAppRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!sharedText.isNullOrEmpty()) {
            viewModel.setIncomingSharedText(sharedText)
            viewModel.selectTab(AppTab.SUBSCRIPTIONS)
        }
    }
}
