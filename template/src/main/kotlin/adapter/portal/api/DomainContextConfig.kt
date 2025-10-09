package ${basePackage}.adapter.portal.api._share.configure

import com.only4.cap4k.ddd.domain.web.ClearDomainContextInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * DomainContextConfig
 *
 * @author cap4k-ddd-codegen
 */
@Configuration
class DomainContextConfig(
    private val clearDomainContextInterceptor: ClearDomainContextInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(clearDomainContextInterceptor)
            .addPathPatterns("/**")
    }
}
