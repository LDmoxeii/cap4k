package com.only4.cap4k.ddd.application.event.persistence

import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import jakarta.persistence.*
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where

@Aggregate(
    aggregate = "event_http_subscriber",
    name = "EventHttpSubscriber",
    root = true,
    type = Aggregate.TYPE_ENTITY,
    description = "集成事件订阅"
)
@Entity
@Table(name = "`__event_http_subscriber`")
@DynamicInsert
@DynamicUpdate
@SQLDelete(sql = "update `__event_http_subscriber` set `db_deleted` = 1 where `id` = ? and `version` = ? ")
@Where(clause = "`db_deleted` = 0")
data class EventHttpSubscriber(
    /**
     * bigint
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /**
     * 事件
     * varchar(255)
     */
    @Column(name = "event")
    val event: String,

    /**
     * 订阅者
     * varchar(255)
     */
    @Column(name = "subscriber")
    val subscriber: String,

    /**
     * 回调地址
     * varchar(1023)
     */
    @Column(name = "callback_url")
    var callbackUrl: String,

    /**
     * 数据版本（支持乐观锁）
     * int
     */
    @Version
    @Column(name = "version")
    val version: Int = 0,
) {
    companion object {
        const val F_ID = "id"
        const val F_EVENT = "event"
        const val F_SUBSCRIBER = "subscriber"
        const val F_CALLBACK_URL = "callbackUrl"
    }
}
