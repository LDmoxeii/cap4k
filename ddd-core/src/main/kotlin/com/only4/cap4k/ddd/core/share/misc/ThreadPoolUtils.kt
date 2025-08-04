/**
 * 线程池工具类
 *
 * @author LD_moxeii
 * @date 2025/08/04
 */
@file:JvmName("ThreadPoolUtils")

package com.only4.cap4k.ddd.core.share.misc

import org.springframework.objenesis.instantiator.util.ClassUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import org.springframework.objenesis.instantiator.util.ClassUtils as SpringClassUtils


/**
 * 创建定时线程池
 *
 * @param threadPoolSize 线程池大小
 * @param threadFactoryClassName 线程工厂类名，为空则使用默认线程工厂
 * @param classLoader 类加载器，默认使用当前类的类加载器
 * @return 定时线程池
 */
fun createScheduledThreadPool(
    threadPoolSize: Int,
    threadFactoryClassName: String,
    classLoader: ClassLoader
): ScheduledExecutorService {
    return when {
        threadFactoryClassName.isBlank() -> {
            Executors.newScheduledThreadPool(threadPoolSize)
        }

        else -> {
            try {
                val threadFactoryClass = SpringClassUtils.getExistingClass<ThreadFactory>(
                    classLoader,
                    threadFactoryClassName
                )
                val threadFactory = SpringClassUtils.newInstance(threadFactoryClass)

                threadFactory?.let {
                    Executors.newScheduledThreadPool(threadPoolSize, threadFactory)
                } ?: Executors.newScheduledThreadPool(threadPoolSize)
            } catch (_: Exception) {
                Executors.newScheduledThreadPool(threadPoolSize)
            }
        }
    }
}

/**
 * 创建固定线程池
 *
 * @param threadPoolSize 线程池大小
 * @param threadFactoryClassName 线程工厂类名，为空则使用默认线程工厂
 * @param classLoader 类加载器，默认使用当前类的类加载器
 * @return 固定线程池
 */
fun createFixedThreadPool(
    threadPoolSize: Int,
    threadFactoryClassName: String,
    classLoader: ClassLoader
): ExecutorService {
    return when {
        threadFactoryClassName.isBlank() -> {
            Executors.newFixedThreadPool(threadPoolSize)
        }

        else -> {
            val threadFactoryClass = ClassUtils.getExistingClass<ThreadFactory>(
                classLoader, threadFactoryClassName
            )
            val threadFactory = threadFactoryClass.getDeclaredConstructor().newInstance()

            threadFactory.let {
                Executors.newFixedThreadPool(threadPoolSize, threadFactory)
            } ?: Executors.newFixedThreadPool(threadPoolSize)
        }
    }
}
