package com.afterlater.core.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

object LinkOpener {

    fun open(context: Context, url: String): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Log.w("AFTERLATER", "No activity can open a shared link")
            false
        } catch (error: SecurityException) {
            Log.w("AFTERLATER", "Link could not be handed to another app")
            false
        }
    }
}
