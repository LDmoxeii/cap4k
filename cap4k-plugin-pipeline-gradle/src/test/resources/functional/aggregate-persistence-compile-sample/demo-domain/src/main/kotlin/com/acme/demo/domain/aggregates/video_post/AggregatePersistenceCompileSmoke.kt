package com.acme.demo.domain.aggregates.video_post

class AggregatePersistenceCompileSmoke {
    fun touch(entity: VideoPost) {
        entity.id
        entity.version
        entity.title
    }
}
