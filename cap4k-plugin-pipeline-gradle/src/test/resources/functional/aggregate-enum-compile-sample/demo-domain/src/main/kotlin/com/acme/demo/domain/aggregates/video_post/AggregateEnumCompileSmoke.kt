package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility
import com.acme.demo.domain.shared.enums.Status

class AggregateEnumCompileSmoke(
    private val status: Status,
    private val visibility: VideoPostVisibility,
) {
    fun wire(): Pair<Status, VideoPostVisibility> = status to visibility
}
