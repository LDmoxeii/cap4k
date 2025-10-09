package ${basePackage}.adapter.application.distributed.clients${package}

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.only4.cap4k.ddd.application.distributed.client.DistributedClient
import ${basePackage}.application.distributed.clients${package}.${Client} as ClientContract

/**
 * ${CommentEscaped}
 *
 * @author cap4k-ddd-codegen
 * @date ${date}
 */
@Service
class ${Client}Handler : DistributedClient<ClientContract.Request, ClientContract.Response> {

    companion object {
        private val log = LoggerFactory.getLogger(${Client} Handler ::class.java)
    }

    override fun exec(request: ClientContract.Request): ClientContract.Response {
        log.info("执行${Client}远程调用: {}", request)

        return ClientContract.Response(
            result = "调用成功",
            success = true
        )
    }
}
