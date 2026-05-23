package com.acme.demo.application.queries.video_post.unique

import com.acme.demo.application.validators.video_post.unique.UniqueVideoPostSlug
import com.acme.demo.domain.aggregates.video_post.VideoPostId

@UniqueVideoPostSlug
data class AggregateUniqueApplicationCompileSmoke(
    val slug: String = "demo",
) {
    val request: UniqueVideoPostSlugQry.Request =
        UniqueVideoPostSlugQry.Request(
            slug = slug,
            excludeVideoPostId = VideoPostId.new(),
        )

    val response: UniqueVideoPostSlugQry.Response = UniqueVideoPostSlugQry.Response(exists = false)
}
