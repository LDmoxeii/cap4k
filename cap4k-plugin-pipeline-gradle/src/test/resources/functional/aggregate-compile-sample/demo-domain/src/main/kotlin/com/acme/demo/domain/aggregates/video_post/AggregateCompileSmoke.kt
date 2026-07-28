package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory

class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
) {
    fun wire(): VideoPostFactory = factory
}
