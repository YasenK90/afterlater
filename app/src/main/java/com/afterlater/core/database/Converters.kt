package com.afterlater.core.database

import androidx.room.TypeConverter
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.ReviewAction
import com.afterlater.core.model.SurfaceHint

class Converters {

    @TypeConverter
    fun itemStatusToString(value: ItemStatus?): String? = value?.name

    @TypeConverter
    fun stringToItemStatus(value: String?): ItemStatus? = value?.let(ItemStatus::valueOf)

    @TypeConverter
    fun metadataStatusToString(value: MetadataStatus?): String? = value?.name

    @TypeConverter
    fun stringToMetadataStatus(value: String?): MetadataStatus? = value?.let(MetadataStatus::valueOf)

    @TypeConverter
    fun reviewActionToString(value: ReviewAction?): String? = value?.name

    @TypeConverter
    fun stringToReviewAction(value: String?): ReviewAction? = value?.let(ReviewAction::valueOf)

    @TypeConverter
    fun surfaceHintToString(value: SurfaceHint?): String? = value?.name

    @TypeConverter
    fun stringToSurfaceHint(value: String?): SurfaceHint? = value?.let(SurfaceHint::valueOf)
}
