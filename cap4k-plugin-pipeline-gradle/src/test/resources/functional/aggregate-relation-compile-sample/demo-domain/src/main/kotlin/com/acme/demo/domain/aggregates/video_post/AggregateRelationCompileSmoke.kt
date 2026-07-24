package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.user_profile.UserProfile

class AggregateRelationCompileSmoke {
    fun touch(entity: VideoPost, child: VideoPostItem, file: VideoPostFile, profile: UserProfile) {
        entity.items.add(child)
        entity.items.remove(child)
        entity.items.forEach { it.label }
        entity.items.firstOrNull()?.label
        entity.file = file
        entity.file?.storageKey
        entity.authorId
        entity.coverProfileId
        child.videoPost.id
        profile.id
    }
}
