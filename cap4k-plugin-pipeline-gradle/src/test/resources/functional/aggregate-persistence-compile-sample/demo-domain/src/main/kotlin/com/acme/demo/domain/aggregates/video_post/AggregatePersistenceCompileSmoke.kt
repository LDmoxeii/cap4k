package com.acme.demo.domain.aggregates.video_post

class AggregatePersistenceCompileSmoke {
    fun touch(entity: VideoPost) {
        entity.id
        entity.version
        entity.created_by
        entity.updated_by
        entity.title
    }
}
