package com.only4.cap4k.ddd.domain.web

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.event.impl.DefaultIntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventSupervisor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.lang.Nullable
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 领域事件上下文清理拦截器
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConditionalOnWebApplication
class ClearDomainContextInterceptor : HandlerInterceptor {

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        @Nullable ex: Exception?
    ) {
        JpaUnitOfWork.reset()
        DefaultDomainEventSupervisor.reset()
        DefaultIntegrationEventSupervisor.reset()
    }
}
