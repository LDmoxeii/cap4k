package com.acme.demo.adapter.domain.translation.shared

class SharedEnumTranslationCompileSmoke(
    private val translation: StatusTranslation,
) {
    fun wire(): StatusTranslation = translation
}
