package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post_item.VideoPostItem
import com.acme.demo.domain.aggregates.user_profile.UserProfile

class AggregateRelationCompileSmoke {
    fun touch(entity: VideoPost, child: VideoPostItem, profile: UserProfile) {
        entity.items.forEach { it.label }
        entity.author.nickname
        entity.coverProfile?.nickname
        child.videoPost.id
        profile.id
    }
}
