@file:JvmName("ScanUtils")

package com.only4.cap4k.ddd.core.share.misc

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory
import org.springframework.util.ClassUtils

/**
 * 类扫描工具
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
private val RESOLVER: ResourcePatternResolver = PathMatchingResourcePatternResolver()
private val METADATA_READER_FACTORY: MetadataReaderFactory = SimpleMetadataReaderFactory()

/**
 * 扫描指定路径下的所有类
 *
 * @param scanPath 扫描路径
 * @param concrete 扫描的类是否是具体的类，即非接口、非抽象类
 * @return 扫描到的类
 */
@Throws(Exception::class)
fun scanClass(scanPath: String, concrete: Boolean): Set<Class<*>> {
    val path = ClassUtils.convertClassNameToResourcePath(scanPath)
    val packageSearchPath = "${ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX}$path/**/*.class"

    return RESOLVER.getResources(packageSearchPath)
        .filter { it.isReadable }
        .mapNotNull { resource ->
            try {
                val metadataReader = METADATA_READER_FACTORY.getMetadataReader(resource)
                val classMetadata = metadataReader.classMetadata

                if (!concrete || classMetadata.isConcrete) {
                    Class.forName(classMetadata.className)
                } else null
            } catch (ex: Exception) {
                System.err.println("无法加载：${resource.filename}")
                null
            }
        }
        .toSet()
}

/**
 * 查找领域事件类
 *
 * @param scanPath 扫描路径
 * @return 领域事件类集合
 */
fun findDomainEventClasses(scanPath: String): Set<Class<*>> =
    scanClass(scanPath, true)
        .filter { cls -> cls.getAnnotation(DomainEvent::class.java) != null }
        .toSet()

/**
 * 查找集成事件类
 *
 * @param scanPath 扫描路径
 * @return 集成事件类集合
 */
fun findIntegrationEventClasses(scanPath: String): Set<Class<*>> =
    scanClass(scanPath, true)
        .filter { cls -> cls.getAnnotation(IntegrationEvent::class.java) != null }
        .toSet()
