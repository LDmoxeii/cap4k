package ${basePackage}.adapter.domain._share.configure

import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * 事件消息拦截器
 *
 * @author cap4k-ddd-codegen
 */
@Component
class MyEventMessageInterceptor : EventMessageInterceptor {
    override fun initPublish(message: Message<*>) {
    }

    override fun prePublish(message: Message<*>) {
    }

    override fun postPublish(message: Message<*>) {
    }

    override fun preSubscribe(message: Message<*>) {
    }

    override fun postSubscribe(message: Message<*>) {
    }

}
