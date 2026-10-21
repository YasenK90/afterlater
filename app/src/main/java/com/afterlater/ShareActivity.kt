package com.afterlater

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.afterlater.feature.capture.ShareCaptureScreen
import com.afterlater.feature.capture.ShareViewModel
import com.afterlater.ui.AfterLaterRoot

class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels { ShareViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        viewModel.handleIntent(intent)

        setContent {
            AfterLaterRoot {
                ShareCaptureScreen(
                    viewModel = viewModel,
                    onDone = { finish() },
                    onOpenItem = { itemId ->
                        startActivity(MainActivity.intentForItem(this, itemId))
                        finish()
                    },
                    onOpenQuickAdd = {
                        startActivity(MainActivity.intentForQuickAdd(this))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }
}
