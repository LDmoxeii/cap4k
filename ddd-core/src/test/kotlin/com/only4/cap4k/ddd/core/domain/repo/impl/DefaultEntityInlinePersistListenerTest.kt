package com.only4.cap4k.ddd.core.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.repo.PersistType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
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
            val cacheKey = "${TestEntityWithHandlers::class.java.name}.onCreate"
            assert(DefaultEntityInlinePersistListener.HANDLER_METHOD_CACHE.containsKey(cacheKey))
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
                .filter { it.endsWith(".onCreate") }
            assertEquals(1, createCacheEntries.size)
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
}
