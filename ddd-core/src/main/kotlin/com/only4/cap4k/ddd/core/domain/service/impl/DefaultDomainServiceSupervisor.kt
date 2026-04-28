package com.only4.cap4k.ddd.core.domain.service.impl

import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.domain.service.annotation.DomainService
import org.springframework.context.ApplicationContext

/**
 * 默认领域服务管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class DefaultDomainServiceSupervisor(
    private val applicationContext: ApplicationContext
) : DomainServiceSupervisor {

    override fun <DOMAIN_SERVICE : Any> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE {
        val domainService = try {
            applicationContext.getBean(domainServiceClass)
        } catch (e: Exception) {
            throw IllegalStateException("Domain service not found: ${domainServiceClass.name}", e)
        }

        if (!hasAnnotationRecursively(domainService.javaClass, DomainService::class.java)) {
            throw IllegalStateException("Bean is not a domain service: ${domainService.javaClass.name}")
        }

        return domainService
    }

    /**
     * 递归检查类及其父类是否存在指定注解
     */
    private fun hasAnnotationRecursively(clazz: Class<*>, annotationClass: Class<out Annotation>): Boolean {
        var currentClass: Class<*> = clazz
        while (currentClass != Any::class.java) {
            if (currentClass.isAnnotationPresent(annotationClass)) {
                return true
            }
            currentClass = currentClass.superclass
        }
        return false
    }
}
