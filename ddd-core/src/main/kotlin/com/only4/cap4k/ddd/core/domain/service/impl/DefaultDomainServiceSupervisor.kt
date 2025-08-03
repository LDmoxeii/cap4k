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

    override fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE? {
        return try {
            val domainService = applicationContext.getBean(domainServiceClass)!!

            // 使用takeIf确保只有带@DomainService注解的服务才被返回（包括继承的注解）
            domainService.takeIf {
                hasAnnotationRecursively(it.javaClass, DomainService::class.java)
            }
        } catch (e: Exception) {
            // 如果Spring容器中找不到Bean，返回null
            null
        }
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
