package com.acme.demo.domain.smoke

import com.acme.demo.domain.aggregates.video_post.VideoPost
import com.acme.demo.domain.shared.enums.Status

object EnumManifestCompileSmoke {
    val statusType = Status::class
    val entityType = VideoPost::class

    fun isPublishedTerminal(): Boolean =
        Status.PUBLISHED.group == "published" && Status.PUBLISHED.terminal && Status.PUBLISHED.isTerminal()

    fun converterRoundTrip(): Boolean {
        val converter = Status.Converter()
        val stored = converter.convertToDatabaseColumn(Status.PUBLISHED)
        return stored == 1 && converter.convertToEntityAttribute(stored) === Status.PUBLISHED
    }
}

fun main() {
    check(EnumManifestCompileSmoke.converterRoundTrip())
}
