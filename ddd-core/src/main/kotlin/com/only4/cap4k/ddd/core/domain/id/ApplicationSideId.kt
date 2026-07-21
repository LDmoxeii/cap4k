package com.only4.cap4k.ddd.core.domain.id

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
/**
 * Compatibility runtime annotation for manually authored application-side IDs.
 * Generated Strong ID aggregates do not use save-time ID assignment.
 */
annotation class ApplicationSideId(
    val strategy: String
)
