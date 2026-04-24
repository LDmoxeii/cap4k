package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.QueryVariant

internal val QueryVariant.requestTemplateId: String
    get() = when (this) {
        QueryVariant.DEFAULT -> "design/query.kt.peb"
        QueryVariant.LIST -> "design/query_list.kt.peb"
        QueryVariant.PAGE -> "design/query_page.kt.peb"
    }

internal val QueryVariant.handlerTemplateId: String
    get() = when (this) {
        QueryVariant.DEFAULT -> "design/query_handler.kt.peb"
        QueryVariant.LIST -> "design/query_list_handler.kt.peb"
        QueryVariant.PAGE -> "design/query_page_handler.kt.peb"
    }
