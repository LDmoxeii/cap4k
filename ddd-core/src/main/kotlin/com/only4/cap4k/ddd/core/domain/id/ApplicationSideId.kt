package com.only4.cap4k.ddd.core.domain.id

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationSideId(
    val strategy: String
)
