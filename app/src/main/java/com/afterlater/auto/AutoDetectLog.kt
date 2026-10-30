package com.afterlater.auto

import android.util.Log
import com.afterlater.BuildConfig

object AutoDetectLog {

    const val TAG = "AFTERLATER-DETECT"

    fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message.take(MAX_MESSAGE_LENGTH))
    }

    fun warning(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, error)
    }

    private const val MAX_MESSAGE_LENGTH = 4_000
}
