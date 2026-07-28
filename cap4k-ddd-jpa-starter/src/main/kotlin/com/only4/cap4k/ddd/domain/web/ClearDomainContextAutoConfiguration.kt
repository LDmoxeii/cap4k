package com.only4.cap4k.ddd.domain.web

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.lang.Nullable
import org.springframework.web.servlet.HandlerInterceptor

@AutoConfiguration(after = [com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration::class])
@ConditionalOnWebApplication
class ClearDomainContextAutoConfiguration : HandlerInterceptor {
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        @Nullable ex: Exception?,
    ) {
        JpaUnitOfWork.reset()
        EventRuntimeContextManager.reset()
    }
}
