package com.acme.demo.domain.aggregates.video_post

class AggregateRelationCompileSmoke {
    fun touch(
        entity: VideoPost,
        child: VideoPostItem,
        file: VideoPostFile,
        variant: VideoPostFileVariant,
    ) {
        entity.items.add(child)
        entity.file = file
        file.variants.add(variant)
        entity.file?.variants?.firstOrNull()?.variantKey
    }
}
