package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain._share.managed.ManagedFieldCatalogContribution
import com.acme.demo.domain.aggregates.uuid_native_record.UuidNativeRecord
import com.acme.demo.domain.aggregates.uuid_native_record.UuidNativeRecordId
import com.acme.demo.domain.aggregates.uuid_native_record.factory.UuidNativeRecordFactory
import com.acme.demo.domain.aggregates.uuid_string_record.UuidStringRecord
import com.acme.demo.domain.aggregates.uuid_string_record.UuidStringRecordId
import com.acme.demo.domain.aggregates.uuid_string_record.factory.UuidStringRecordFactory
import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory

object AggregateProviderPersistenceCompileSmoke {
    fun verify(
        post: VideoPost,
        uuidStringRecord: UuidStringRecord,
        uuidNativeRecord: UuidNativeRecord,
        videoPostFactory: VideoPostFactory,
        uuidStringRecordFactory: UuidStringRecordFactory,
        uuidNativeRecordFactory: UuidNativeRecordFactory,
        managedFieldCatalogContribution: ManagedFieldCatalogContribution,
    ): List<Any> {
        val videoPostPayload = VideoPostFactory.Payload(title = "identity")
        val uuidStringPayload = UuidStringRecordFactory.Payload(title = "uuid-string")
        val uuidNativePayload = UuidNativeRecordFactory.Payload(title = "uuid-native")

        val createdVideoPost = videoPostFactory.create(videoPostPayload)
        val createdUuidStringRecord = uuidStringRecordFactory.create(uuidStringPayload)
        val createdUuidNativeRecord = uuidNativeRecordFactory.create(uuidNativePayload)

        return listOf(
            post,
            uuidStringRecord,
            uuidNativeRecord,
            createdVideoPost,
            createdUuidStringRecord,
            createdUuidNativeRecord,
            UuidStringRecordId::class,
            UuidNativeRecordId::class,
            managedFieldCatalogContribution,
        )
    }
}
