package $

{ basePackage }.adapter.domain._share.configure

import org.springframework.stereotype.Component
import com.only4.cap4k.ddd.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.domain.event.EventMessage

/**
 * 事件消息拦截器
 *
 * @author cap4k-ddd-codegen
 */
@Component
class MyEventMessageInterceptor : EventMessageInterceptor {

    override fun preHandle(message: EventMessage): Boolean {
        // TODO: 实现事件消息预处理逻辑
        return true
    }

    override fun postHandle(message: EventMessage, result: Any?) {
        // TODO: 实现事件消息后处理逻辑
    }

    override fun afterCompletion(message: EventMessage, ex: Exception?) {
        // TODO: 实现事件消息完成后处理逻辑
    }
}
