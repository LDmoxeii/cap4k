package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel

internal data class DbColumnAnnotationMetadata(
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
)
