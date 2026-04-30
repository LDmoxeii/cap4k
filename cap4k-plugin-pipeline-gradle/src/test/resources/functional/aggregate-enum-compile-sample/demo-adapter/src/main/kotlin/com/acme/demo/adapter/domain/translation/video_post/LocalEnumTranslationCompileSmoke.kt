package com.acme.demo.adapter.domain.translation.video_post

class LocalEnumTranslationCompileSmoke(
    private val translation: VideoPostVisibilityTranslation,
) {
    fun wire(): VideoPostVisibilityTranslation = translation
}
