package com.acme.demo.domain.translation.shared

class SharedEnumTranslationCompileSmoke(
    private val translation: StatusTranslation,
) {
    fun wire(): StatusTranslation = translation
}
