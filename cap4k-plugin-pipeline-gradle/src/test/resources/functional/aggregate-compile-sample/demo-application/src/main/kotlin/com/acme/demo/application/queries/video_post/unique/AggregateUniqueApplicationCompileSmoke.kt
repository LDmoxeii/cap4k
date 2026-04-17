package com.acme.demo.application.queries.video_post.unique

import com.acme.demo.application.validators.video_post.unique.UniqueVideoPostSlug

@UniqueVideoPostSlug
data class AggregateUniqueApplicationCompileSmoke(
    val slug: String = "demo",
) {
    val request: UniqueVideoPostSlugQry.Request =
        UniqueVideoPostSlugQry.Request(
            slug = slug,
            excludeVideoPostId = 1L,
        )

    val response: UniqueVideoPostSlugQry.Response = UniqueVideoPostSlugQry.Response(exists = false)
}
