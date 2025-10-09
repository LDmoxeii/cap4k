package ${basePackage}.application.distributed.clients${package}

{ basePackage }.application.distributed.clients${ package }

import com.only4.cap4k.ddd.application.RequestParam

/**
 * ${CommentEscaped}
 *
 * @author cap4k-ddd-codegen
 * @date ${date}
 */
class $ {Client }

{

    /**
     * ${Client}客户端请求参数
     */
    data class Request(
        val param: String? = null,
    ) : RequestParam<Response>

    /**
     * ${Client}客户端响应
     */
    data class Response(
        val result: String? = null,
        val success: Boolean = false,
    )
}
