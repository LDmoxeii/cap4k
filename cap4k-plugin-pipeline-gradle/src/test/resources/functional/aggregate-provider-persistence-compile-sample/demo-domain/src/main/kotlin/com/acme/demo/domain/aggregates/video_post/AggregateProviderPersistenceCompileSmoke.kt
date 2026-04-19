package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.audit_log.AuditLog

object AggregateProviderPersistenceCompileSmoke {
    fun verify(post: VideoPost, log: AuditLog): List<Any> = listOf(post, log)
}
