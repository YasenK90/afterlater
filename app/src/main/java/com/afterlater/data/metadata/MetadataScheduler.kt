package com.afterlater.data.metadata

interface MetadataScheduler {
    fun enqueue(itemId: String, replaceExisting: Boolean = false)
}
