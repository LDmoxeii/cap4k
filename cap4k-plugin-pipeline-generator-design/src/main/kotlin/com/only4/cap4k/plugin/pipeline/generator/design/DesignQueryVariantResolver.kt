package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel

internal enum class DesignQueryVariant(
    val requestTemplateId: String,
    val handlerTemplateId: String,
) {
    DEFAULT(
        requestTemplateId = "design/query.kt.peb",
        handlerTemplateId = "design/query_handler.kt.peb",
    ),
    LIST(
        requestTemplateId = "design/query_list.kt.peb",
        handlerTemplateId = "design/query_list_handler.kt.peb",
    ),
    PAGE(
        requestTemplateId = "design/query_page.kt.peb",
        handlerTemplateId = "design/query_page_handler.kt.peb",
    ),
}

internal object DesignQueryVariantResolver {
    fun resolve(request: RequestModel): DesignQueryVariant? =
        if (request.kind != RequestKind.QUERY) {
            null
        } else when {
            request.typeName.endsWith("PageQry") -> DesignQueryVariant.PAGE
            request.typeName.endsWith("ListQry") -> DesignQueryVariant.LIST
            else -> DesignQueryVariant.DEFAULT
        }
}
