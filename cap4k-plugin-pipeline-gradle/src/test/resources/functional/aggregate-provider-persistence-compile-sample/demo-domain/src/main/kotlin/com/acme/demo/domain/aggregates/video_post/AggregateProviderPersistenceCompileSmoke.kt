package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain._share.identity.GeneratedOwnIdCatalogContribution
import com.acme.demo.domain.aggregates.snowflake_long_record.SnowflakeLongRecord
import com.acme.demo.domain.aggregates.snowflake_long_record.SnowflakeLongRecordGeneratedOwnIdAccessor
import com.acme.demo.domain.aggregates.snowflake_long_record.SnowflakeLongRecordId
import com.acme.demo.domain.aggregates.snowflake_long_record.factory.SnowflakeLongRecordFactory
import com.acme.demo.domain.aggregates.snowflake_string_record.SnowflakeStringRecord
import com.acme.demo.domain.aggregates.snowflake_string_record.SnowflakeStringRecordGeneratedOwnIdAccessor
import com.acme.demo.domain.aggregates.snowflake_string_record.SnowflakeStringRecordId
import com.acme.demo.domain.aggregates.snowflake_string_record.factory.SnowflakeStringRecordFactory
import com.acme.demo.domain.aggregates.uuid_native_record.UuidNativeRecord
import com.acme.demo.domain.aggregates.uuid_native_record.UuidNativeRecordGeneratedOwnIdAccessor
import com.acme.demo.domain.aggregates.uuid_native_record.UuidNativeRecordId
import com.acme.demo.domain.aggregates.uuid_native_record.factory.UuidNativeRecordFactory
import com.acme.demo.domain.aggregates.uuid_string_record.UuidStringRecord
import com.acme.demo.domain.aggregates.uuid_string_record.UuidStringRecordGeneratedOwnIdAccessor
import com.acme.demo.domain.aggregates.uuid_string_record.UuidStringRecordId
import com.acme.demo.domain.aggregates.uuid_string_record.factory.UuidStringRecordFactory
import com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory

object AggregateProviderPersistenceCompileSmoke {
    fun verify(
        post: VideoPost,
        snowflakeLongRecord: SnowflakeLongRecord,
        snowflakeStringRecord: SnowflakeStringRecord,
        uuidStringRecord: UuidStringRecord,
        uuidNativeRecord: UuidNativeRecord,
        videoPostFactory: VideoPostFactory,
        snowflakeLongRecordFactory: SnowflakeLongRecordFactory,
        snowflakeStringRecordFactory: SnowflakeStringRecordFactory,
        uuidStringRecordFactory: UuidStringRecordFactory,
        uuidNativeRecordFactory: UuidNativeRecordFactory,
        generatedOwnIdCatalogContribution: GeneratedOwnIdCatalogContribution,
    ): List<Any> {
        val videoPostPayload = VideoPostFactory.Payload(title = "identity")
        val snowflakeLongPayload = SnowflakeLongRecordFactory.Payload(title = "snowflake long")
        val snowflakeStringPayload = SnowflakeStringRecordFactory.Payload(title = "snowflake-string")
        val uuidStringPayload = UuidStringRecordFactory.Payload(title = "uuid-string")
        val uuidNativePayload = UuidNativeRecordFactory.Payload(title = "uuid-native")

        val createdVideoPost = videoPostFactory.create(videoPostPayload)
        val createdSnowflakeLongRecord = snowflakeLongRecordFactory.create(snowflakeLongPayload)
        val createdSnowflakeStringRecord = snowflakeStringRecordFactory.create(snowflakeStringPayload)
        val createdUuidStringRecord = uuidStringRecordFactory.create(uuidStringPayload)
        val createdUuidNativeRecord = uuidNativeRecordFactory.create(uuidNativePayload)

        return listOf(
            post,
            snowflakeLongRecord,
            snowflakeStringRecord,
            uuidStringRecord,
            uuidNativeRecord,
            createdVideoPost,
            createdSnowflakeLongRecord,
            createdSnowflakeStringRecord,
            createdUuidStringRecord,
            createdUuidNativeRecord,
            SnowflakeLongRecordId::class,
            SnowflakeStringRecordId::class,
            UuidStringRecordId::class,
            UuidNativeRecordId::class,
            SnowflakeLongRecordGeneratedOwnIdAccessor,
            SnowflakeStringRecordGeneratedOwnIdAccessor,
            UuidStringRecordGeneratedOwnIdAccessor,
            UuidNativeRecordGeneratedOwnIdAccessor,
            generatedOwnIdCatalogContribution,
        )
    }
}
