package $

{ basePackage }.application.distributed.sagas${ package }

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.only4.cap4k.ddd.application.RequestParam
import com.only4.cap4k.ddd.application.saga.Saga
import com.only4.cap4k.ddd.core.share.X

/**
 * ${CommentEscaped}
 *
 * @author cap4k-ddd-codegen
 * @date ${date}
 */
class $ {Saga }

{

    /**
     * ${Saga}Saga处理器
     */
    @Service
    class Handler : Saga<Request, Response> {

        companion object {
            private val log = LoggerFactory.getLogger(Handler::class.java)
        }

        override fun exec(cmd: Request): Response {
            // TODO: 实现Saga业务逻辑
            X.uow().save()

            return Response(success = true)
        }

        override fun compensate(cmd: Request): Response {
            // TODO: 实现补偿逻辑
            log.info("补偿 ${Saga}: {}", cmd)
            return Response(success = true)
        }
    }

    /**
     * ${Saga}请求参数
     */
    data class Request(
        val param: String? = null,
    ) : RequestParam<Response>

    /**
     * ${Saga}响应
     */
    data class Response(
        val success: Boolean = false,
    )
}
