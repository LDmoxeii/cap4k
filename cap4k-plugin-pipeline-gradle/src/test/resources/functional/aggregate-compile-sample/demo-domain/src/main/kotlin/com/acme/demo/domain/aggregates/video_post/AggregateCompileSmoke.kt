package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory
import com.acme.demo.domain.aggregates.video_post.specification.VideoPostSpecification

class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
    private val specification: VideoPostSpecification,
) {
    fun wire(): Pair<VideoPostFactory, VideoPostSpecification> = factory to specification
}
