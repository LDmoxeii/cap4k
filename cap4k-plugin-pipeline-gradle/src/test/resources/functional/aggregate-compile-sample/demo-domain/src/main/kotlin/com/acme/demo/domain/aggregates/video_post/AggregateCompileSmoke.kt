package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory
import com.acme.demo.domain.aggregates.video_post.specification.VideoPostSpecification

class AggregateCompileSmoke(
    private val factory: VideoPostFactory,
    private val specification: VideoPostSpecification,
) {
    fun wire(): Triple<VideoPostFactory, VideoPostSpecification, AggVideoPost.Id> =
        Triple(factory, specification, AggVideoPost.Id(1L))
}
