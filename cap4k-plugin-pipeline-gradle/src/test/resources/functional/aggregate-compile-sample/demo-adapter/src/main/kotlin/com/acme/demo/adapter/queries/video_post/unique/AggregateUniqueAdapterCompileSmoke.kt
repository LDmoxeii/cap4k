package com.acme.demo.adapter.queries.video_post.unique

import com.acme.demo.adapter.domain.repositories.VideoPostRepository
import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry
import com.acme.demo.domain.aggregates.video_post.VideoPostId
import java.lang.reflect.Proxy

@Suppress("unused")
internal object AggregateUniqueAdapterCompileSmoke {
    fun wire(
        handler: UniqueVideoPostSlugQryHandler = UniqueVideoPostSlugQryHandler(repository()),
    ): UniqueVideoPostSlugQry.Response {
        val request = UniqueVideoPostSlugQry.Request(
            slug = "demo",
            excludeVideoPostId = VideoPostId.new(),
        )
        return handler.exec(request)
    }

    private fun repository(): VideoPostRepository =
        Proxy.newProxyInstance(
            VideoPostRepository::class.java.classLoader,
            arrayOf(VideoPostRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "exists" -> false
                "hashCode" -> 0
                "toString" -> "AggregateUniqueAdapterCompileSmokeRepository"
                "equals" -> false
                else -> throw UnsupportedOperationException("Unexpected repository method: ${method.name}")
            }
        } as VideoPostRepository
}
