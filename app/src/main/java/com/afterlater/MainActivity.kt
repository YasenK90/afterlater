package com.afterlater

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.afterlater.ui.AfterLaterApp
import com.afterlater.ui.AfterLaterRoot
import kotlinx.coroutines.flow.MutableStateFlow

private const val EXTRA_OPEN_QUICK_ADD = "com.afterlater.extra.OPEN_QUICK_ADD"

class MainActivity : ComponentActivity() {

    private val deepLinkItemId = MutableStateFlow<String?>(null)
    private val quickAddRequested = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        deepLinkItemId.value = intent.itemIdFromDeepLink()
        quickAddRequested.value = intent.quickAddRequested()

        setContent {
            val itemId by deepLinkItemId.collectAsState()
            val openQuickAdd by quickAddRequested.collectAsState()
            AfterLaterRoot {
                AfterLaterApp(
                    deepLinkItemId = itemId,
                    onDeepLinkConsumed = { deepLinkItemId.value = null },
                    quickAddRequested = openQuickAdd,
                    onQuickAddConsumed = {
                        quickAddRequested.value = false
                        intent?.removeExtra(EXTRA_OPEN_QUICK_ADD)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkItemId.value = intent.itemIdFromDeepLink()
        quickAddRequested.value = intent.quickAddRequested()
    }

    companion object {
        fun intentForItem(context: Context, itemId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("afterlater://item/$itemId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        fun intentForQuickAdd(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_QUICK_ADD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}

private fun Intent?.quickAddRequested(): Boolean =
    this?.getBooleanExtra(EXTRA_OPEN_QUICK_ADD, false) == true

private fun Intent?.itemIdFromDeepLink(): String? {
    val data = this?.data ?: return null
    if (data.scheme != "afterlater" || data.host != "item") return null
    return data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
}
