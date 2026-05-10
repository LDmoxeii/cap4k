package com.only4.cap4k.ddd.core.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.repo.PersistType
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorDeleteAndMemberRemove
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorHooks
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorHooksProxy
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithBehaviorRemoveOnly
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithInitializingBehaviorFile
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithMemberAndBehaviorHooks
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithThrowingBehaviorHook
import com.only4.cap4k.ddd.core.domain.repo.impl.lifecycle.TestEntityWithoutBehaviorHooks
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.reflect.InvocationTargetException

@DisplayName("DefaultEntityInlinePersistListener 测试")
class DefaultEntityInlinePersistListenerTest {

    private lateinit var listener: DefaultEntityInlinePersistListener

    @BeforeEach
    fun setUp() {
        listener = DefaultEntityInlinePersistListener()
        // 清理缓存
        DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.clear()
    }

    @Nested
    @DisplayName("onCreate 方法测试")
    inner class OnCreateTests {

        @Test
        @DisplayName("应该调用实体的onCreate方法")
        fun `should call entity onCreate method`() {
            // given
            val entity = TestEntityWithHandlers()

            // when
            listener.onCreate(entity)

            // then
            assertEquals(1, entity.onCreateCallCount)
        }

        @Test
        @DisplayName("当实体没有onCreate方法时应该正常处理")
        fun `should handle entity without onCreate method gracefully`() {
            // given
            val entity = TestEntityWithoutHandlers()

            // when & then - 不应该抛出异常
            listener.onCreate(entity)
        }

        @Test
        @DisplayName("应该缓存方法查找结果")
        fun `should cache method lookup results`() {
            // given
            val entity1 = TestEntityWithHandlers()
            val entity2 = TestEntityWithHandlers()

            // when
            listener.onCreate(entity1)
            listener.onCreate(entity2)

            // then
            assertTrue(
                DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.keys.any {
                    it.kind == "member" &&
                        it.targetClassName == TestEntityWithHandlers::class.java.name &&
                        it.matchesTargetClass(TestEntityWithHandlers::class.java) &&
                        it.methodName == "onCreate"
                }
            )
            assertEquals(2, entity1.onCreateCallCount + entity2.onCreateCallCount)
        }

        @Test
        @DisplayName("方法调用异常应该向上传播")
        fun `should propagate method invocation exceptions`() {
            // given
            val entity = TestEntityWithExceptionHandlers()

            // when & then
            assertThrows<InvocationTargetException> {
                listener.onCreate(entity)
            }
        }

        @Test
        @DisplayName("应该调用行为扩展的onCreate方法")
        fun `should call behavior extension onCreate method`() {
            val entity = TestEntityWithBehaviorHooks()

            listener.onCreate(entity)

            assertEquals(1, entity.onCreateCallCount)
        }

        @Test
        @DisplayName("应该为运行时代理类调用聚合根约定的行为扩展onCreate方法")
        fun `should resolve aggregate behavior extension for runtime proxy class`() {
            val entity = TestEntityWithBehaviorHooksProxy()

            listener.onCreate(entity)

            assertEquals(1, entity.onCreateCallCount)
        }

        @Test
        @DisplayName("查找不存在的行为扩展方法不应该初始化行为文件")
        fun `should not initialize behavior file when behavior hook is absent`() {
            val entity = TestEntityWithInitializingBehaviorFile()

            listener.onCreate(entity)
        }

        @Test
        @DisplayName("实体onCreate方法应该优先于行为扩展onCreate方法")
        fun `should prefer entity onCreate method over behavior extension onCreate method`() {
            val entity = TestEntityWithMemberAndBehaviorHooks()

            listener.onCreate(entity)

            assertEquals(1, entity.memberCreateCallCount)
            assertEquals(0, entity.behaviorCreateCallCount)
        }

        @Test
        @DisplayName("行为扩展方法调用异常应该向上传播")
        fun `should propagate behavior extension invocation exceptions`() {
            val entity = TestEntityWithThrowingBehaviorHook()

            assertThrows<InvocationTargetException> {
                listener.onCreate(entity)
            }
        }
    }

    @Nested
    @DisplayName("onUpdate 方法测试")
    inner class OnUpdateTests {

        @Test
        @DisplayName("应该调用实体的onUpdate方法")
        fun `should call entity onUpdate method`() {
            // given
            val entity = TestEntityWithHandlers()

            // when
            listener.onUpdate(entity)

            // then
            assertEquals(1, entity.onUpdateCallCount)
        }

        @Test
        @DisplayName("当实体没有onUpdate方法时应该正常处理")
        fun `should handle entity without onUpdate method gracefully`() {
            // given
            val entity = TestEntityWithoutHandlers()

            // when & then - 不应该抛出异常
            listener.onUpdate(entity)
        }

        @Test
        @DisplayName("应该调用行为扩展的onUpdate方法")
        fun `should call behavior extension onUpdate method`() {
            val entity = TestEntityWithBehaviorHooks()

            listener.onUpdate(entity)

            assertEquals(1, entity.onUpdateCallCount)
        }
    }

    @Nested
    @DisplayName("onDelete 方法测试")
    inner class OnDeleteTests {

        @Test
        @DisplayName("应该优先调用实体的onDelete方法")
        fun `should call entity onDelete method when available`() {
            // given
            val entity = TestEntityWithHandlers()

            // when
            listener.onDelete(entity)

            // then
            assertEquals(1, entity.onDeleteCallCount)
            assertEquals(0, entity.onRemoveCallCount)
        }

        @Test
        @DisplayName("当没有onDelete方法时应该调用onRemove方法")
        fun `should call entity onRemove method when onDelete not available`() {
            // given
            val entity = TestEntityWithOnlyRemove()

            // when
            listener.onDelete(entity)

            // then
            assertEquals(1, entity.onRemoveCallCount)
        }

        @Test
        @DisplayName("当onDelete和onRemove都不存在时应该正常处理")
        fun `should handle entity without onDelete or onRemove methods gracefully`() {
            // given
            val entity = TestEntityWithoutHandlers()

            // when & then - 不应该抛出异常
            listener.onDelete(entity)
        }

        @Test
        @DisplayName("应该调用行为扩展的onDelete方法")
        fun `should call behavior extension onDelete method`() {
            val entity = TestEntityWithBehaviorHooks()

            listener.onDelete(entity)

            assertEquals(1, entity.onDeleteCallCount)
        }

        @Test
        @DisplayName("行为扩展onDelete应该优先于实体onRemove方法")
        fun `should prefer behavior onDelete before entity onRemove fallback`() {
            val entity = TestEntityWithBehaviorDeleteAndMemberRemove()

            listener.onDelete(entity)

            assertEquals(1, entity.behaviorDeleteCallCount)
            assertEquals(0, entity.memberRemoveCallCount)
        }

        @Test
        @DisplayName("当onDelete不存在时应该调用行为扩展onRemove方法")
        fun `should call behavior onRemove when onDelete is absent`() {
            val entity = TestEntityWithBehaviorRemoveOnly()

            listener.onDelete(entity)

            assertEquals(1, entity.behaviorRemoveCallCount)
        }
    }

    @Nested
    @DisplayName("AbstractPersistListener 集成测试")
    inner class AbstractPersistListenerIntegrationTests {

        @Test
        @DisplayName("onChange方法应该根据类型调用对应的方法")
        fun `onChange should call appropriate method based on type`() {
            // given
            val entity = TestEntityWithHandlers()

            // when
            listener.onChange(entity, PersistType.CREATE)
            listener.onChange(entity, PersistType.UPDATE)
            listener.onChange(entity, PersistType.DELETE)

            // then
            assertEquals(1, entity.onCreateCallCount)
            assertEquals(1, entity.onUpdateCallCount)
            assertEquals(1, entity.onDeleteCallCount)
        }
    }

    @Nested
    @DisplayName("缓存机制测试")
    inner class CacheTests {

        @Test
        @DisplayName("同一类的不同实例应该使用缓存")
        fun `different instances of same class should use cache`() {
            // given
            val entity1 = TestEntityWithHandlers()
            val entity2 = TestEntityWithHandlers()

            // when
            listener.onCreate(entity1)
            listener.onCreate(entity2)

            // then
            assertEquals(1, entity1.onCreateCallCount)
            assertEquals(1, entity2.onCreateCallCount)
            // 验证缓存被使用（只有一个缓存条目）
            val createCacheEntries = DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.keys
                .filter {
                    it.kind == "member" &&
                        it.targetClassName == TestEntityWithHandlers::class.java.name &&
                        it.matchesTargetClass(TestEntityWithHandlers::class.java) &&
                        it.methodName == "onCreate"
                }
            assertEquals(1, createCacheEntries.size)
        }

        @Test
        @DisplayName("行为扩展方法查找应该使用独立缓存键")
        fun `behavior extension lookup should use distinct cache key`() {
            val entity = TestEntityWithBehaviorHooks()
            val entityClass = TestEntityWithBehaviorHooks::class.java
            val behaviorClassName = "${entityClass.`package`.name}.${entityClass.simpleName}BehaviorKt"

            listener.onCreate(entity)

            assertTrue(
                DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.keys.any {
                    it.kind == "behavior" &&
                        it.targetClassName == entityClass.name &&
                        it.matchesTargetClass(entityClass) &&
                        it.methodName == "onCreate" &&
                        it.behaviorClassName == behaviorClassName
                }
            )
        }

        @Test
        @DisplayName("同名实体在不同ClassLoader下不应该复用旧行为扩展方法")
        fun `same entity fqn loaded by different classloaders should not reuse old behavior method`() {
            val parentEntity = TestEntityWithBehaviorHooks()
            listener.onCreate(parentEntity)
            assertEquals(1, parentEntity.onCreateCallCount)

            val entityClassName = TestEntityWithBehaviorHooks::class.java.name
            val behaviorClassName = "${TestEntityWithBehaviorHooks::class.java.`package`.name}.${TestEntityWithBehaviorHooks::class.java.simpleName}BehaviorKt"
            val loader = ChildFirstClassLoader(
                parentLoader = javaClass.classLoader,
                childFirstClassNames = setOf(entityClassName, behaviorClassName),
            )
            val entityClass = loader.loadClass(entityClassName)
            val entity = entityClass.getDeclaredConstructor().newInstance()

            listener.onCreate(entity)

            val onCreateCallCount = entityClass.getDeclaredField("onCreateCallCount")
            onCreateCallCount.isAccessible = true
            assertEquals(1, onCreateCallCount.get(entity))
        }

        @Test
        @DisplayName("缺失的生命周期处理方法也应该缓存查找结果")
        fun `missing lifecycle handler lookup should be cached`() {
            val entity = TestEntityWithoutBehaviorHooks()
            val entityClass = TestEntityWithoutBehaviorHooks::class.java
            val behaviorClassName = "${entityClass.`package`.name}.${entityClass.simpleName}BehaviorKt"

            listener.onCreate(entity)

            assertTrue(
                DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.keys.any {
                    it.kind == "member" &&
                        it.targetClassName == entityClass.name &&
                        it.matchesTargetClass(entityClass) &&
                        it.methodName == "onCreate"
                }
            )
            assertTrue(
                DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.keys.any {
                    it.kind == "behavior" &&
                        it.targetClassName == entityClass.name &&
                        it.matchesTargetClass(entityClass) &&
                        it.methodName == "onCreate" &&
                        it.behaviorClassName == behaviorClassName
                }
            )
        }
    }

    // 测试用的实体类
    class TestEntityWithHandlers {
        var onCreateCallCount = 0
        var onUpdateCallCount = 0
        var onDeleteCallCount = 0
        var onRemoveCallCount = 0

        fun onCreate() {
            onCreateCallCount++
        }

        fun onUpdate() {
            onUpdateCallCount++
        }

        fun onDelete() {
            onDeleteCallCount++
        }

        fun onRemove() {
            onRemoveCallCount++
        }
    }

    class TestEntityWithoutHandlers {
        // 没有任何处理方法
    }

    class TestEntityWithOnlyRemove {
        var onRemoveCallCount = 0

        fun onRemove() {
            onRemoveCallCount++
        }
    }

    class TestEntityWithExceptionHandlers {
        fun onCreate() {
            throw RuntimeException("onCreate failed")
        }

        fun onUpdate() {
            throw RuntimeException("onUpdate failed")
        }

        fun onDelete() {
            throw RuntimeException("onDelete failed")
        }
    }

    private class ChildFirstClassLoader(
        private val parentLoader: ClassLoader,
        private val childFirstClassNames: Set<String>,
    ) : ClassLoader(parentLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name !in childFirstClassNames) {
                return super.loadClass(name, resolve)
            }

            val loadedClass = findLoadedClass(name) ?: findClass(name)
            if (resolve) {
                resolveClass(loadedClass)
            }
            return loadedClass
        }

        override fun findClass(name: String): Class<*> {
            val resourceName = name.replace('.', '/') + ".class"
            val bytes = parentLoader.getResourceAsStream(resourceName)?.use { it.readBytes() }
                ?: throw ClassNotFoundException(name)
            return defineClass(name, bytes, 0, bytes.size)
        }
    }
}
