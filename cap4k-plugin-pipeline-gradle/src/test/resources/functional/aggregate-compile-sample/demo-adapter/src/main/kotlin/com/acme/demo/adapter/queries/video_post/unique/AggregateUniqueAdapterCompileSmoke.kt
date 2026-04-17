package com.acme.demo.adapter.queries.video_post.unique

import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry

@Suppress("unused")
internal object AggregateUniqueAdapterCompileSmoke {
    fun wire(
        handler: UniqueVideoPostSlugQryHandler = UniqueVideoPostSlugQryHandler(),
    ): UniqueVideoPostSlugQry.Response {
        val request = UniqueVideoPostSlugQry.Request(
            slug = "demo",
            excludeVideoPostId = 1L,
        )
        return handler.exec(request)
    }
}
