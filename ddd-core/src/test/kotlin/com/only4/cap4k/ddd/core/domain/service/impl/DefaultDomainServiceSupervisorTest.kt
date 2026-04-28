package com.only4.cap4k.ddd.core.domain.service.impl

import com.only4.cap4k.ddd.core.domain.service.annotation.DomainService
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext

@DisplayName("DefaultDomainServiceSupervisor 测试")
class DefaultDomainServiceSupervisorTest {

    private lateinit var supervisor: DefaultDomainServiceSupervisor
    private lateinit var mockApplicationContext: ApplicationContext

    @BeforeEach
    fun setUp() {
        mockApplicationContext = mockk()
        supervisor = DefaultDomainServiceSupervisor(mockApplicationContext)
    }

    @Nested
    @DisplayName("getService 方法测试")
    inner class GetServiceTests {

        @Test
        @DisplayName("应该返回带有@DomainService注解的服务")
        fun `should return service with DomainService annotation`() {
            // given
            val serviceInstance = TestDomainServiceWithAnnotation()
            every { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) } returns serviceInstance

            // when
            val result = supervisor.getService(TestDomainServiceWithAnnotation::class.java)

            // then
            assertEquals(serviceInstance, result)
            verify { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) }
        }

        @Test
        @DisplayName("应该对没有@DomainService注解的服务抛出异常")
        fun `should throw for service without DomainService annotation`() {
            // given
            val serviceInstance = TestServiceWithoutAnnotation()
            every { mockApplicationContext.getBean(TestServiceWithoutAnnotation::class.java) } returns serviceInstance

            // when
            val ex = assertThrows<IllegalStateException> {
                supervisor.getService(TestServiceWithoutAnnotation::class.java)
            }

            // then
            assertEquals("Bean is not a domain service: ${TestServiceWithoutAnnotation::class.java.name}", ex.message)
            verify { mockApplicationContext.getBean(TestServiceWithoutAnnotation::class.java) }
        }

        @Test
        @DisplayName("当Spring容器中找不到Bean时应该抛出异常")
        fun `should throw when bean not found in Spring context`() {
            // given
            every { mockApplicationContext.getBean(NonExistentService::class.java) } throws NoSuchBeanDefinitionException(
                "Bean not found"
            )

            // when
            val ex = assertThrows<IllegalStateException> {
                supervisor.getService(NonExistentService::class.java)
            }

            // then
            assertEquals("Domain service not found: ${NonExistentService::class.java.name}", ex.message)
            verify { mockApplicationContext.getBean(NonExistentService::class.java) }
        }

        @Test
        @DisplayName("当ApplicationContext抛出其他异常时应该抛出异常")
        fun `should throw when ApplicationContext throws other exceptions`() {
            // given
            every { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) } throws RuntimeException(
                "Unexpected error"
            )

            // when
            val ex = assertThrows<IllegalStateException> {
                supervisor.getService(TestDomainServiceWithAnnotation::class.java)
            }

            // then
            assertEquals("Domain service not found: ${TestDomainServiceWithAnnotation::class.java.name}", ex.message)
            assertEquals("Unexpected error", ex.cause?.message)
            verify { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) }
        }

        @Test
        @DisplayName("应该正确处理带有自定义属性的@DomainService注解")
        fun `should handle DomainService annotation with custom properties`() {
            // given
            val serviceInstance = TestDomainServiceWithCustomAnnotation()
            every { mockApplicationContext.getBean(TestDomainServiceWithCustomAnnotation::class.java) } returns serviceInstance

            // when
            val result = supervisor.getService(TestDomainServiceWithCustomAnnotation::class.java)

            // then
            assertEquals(serviceInstance, result)
            verify { mockApplicationContext.getBean(TestDomainServiceWithCustomAnnotation::class.java) }
        }

        @Test
        @DisplayName("应该正确处理继承的服务类")
        fun `should handle inherited service classes`() {
            // given
            val serviceInstance = TestInheritedDomainService()
            every { mockApplicationContext.getBean(TestInheritedDomainService::class.java) } returns serviceInstance

            // when
            val result = supervisor.getService(TestInheritedDomainService::class.java)

            // then
            assertEquals(serviceInstance, result)
            verify { mockApplicationContext.getBean(TestInheritedDomainService::class.java) }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("应该处理接口类型的服务请求")
        fun `should handle interface service requests`() {
            // given
            val serviceInstance: TestDomainServiceInterface = TestDomainServiceWithAnnotation()
            every { mockApplicationContext.getBean(TestDomainServiceInterface::class.java) } returns serviceInstance

            // when
            val result = supervisor.getService(TestDomainServiceInterface::class.java)

            // then
            assertEquals(serviceInstance, result)
            verify { mockApplicationContext.getBean(TestDomainServiceInterface::class.java) }
        }
    }

    @Nested
    @DisplayName("注解检查测试")
    inner class AnnotationCheckTests {

        @Test
        @DisplayName("应该拒绝只有接口带注解但实现类没有注解的服务")
        fun `should reject interface annotation without implementation annotation`() {
            // given - 接口有注解，但实现类没有
            val serviceInstance = TestServiceImplementationWithoutAnnotation()
            every { mockApplicationContext.getBean(TestServiceImplementationWithoutAnnotation::class.java) } returns serviceInstance

            // when
            val ex = assertThrows<IllegalStateException> {
                supervisor.getService(TestServiceImplementationWithoutAnnotation::class.java)
            }

            // then
            assertEquals(
                "Bean is not a domain service: ${TestServiceImplementationWithoutAnnotation::class.java.name}",
                ex.message
            )
            verify { mockApplicationContext.getBean(TestServiceImplementationWithoutAnnotation::class.java) }
        }

        @Test
        @DisplayName("应该正确识别代理类的注解")
        fun `should correctly identify annotations on proxy classes`() {
            // given - 模拟Spring代理类的情况
            val serviceInstance = TestDomainServiceWithAnnotation()
            val proxyInstance = spyk(serviceInstance) // 创建代理

            every { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) } returns proxyInstance

            // when
            val result = supervisor.getService(TestDomainServiceWithAnnotation::class.java)

            // then
            assertEquals(proxyInstance, result)
            verify { mockApplicationContext.getBean(TestDomainServiceWithAnnotation::class.java) }
        }
    }

    // 测试用的类和接口

    @DomainService
    class TestDomainServiceWithAnnotation : TestDomainServiceInterface

    class TestServiceWithoutAnnotation

    class NonExistentService

    @DomainService(name = "customService", description = "A custom domain service")
    class TestDomainServiceWithCustomAnnotation

    @DomainService
    open class TestBaseDomainService

    class TestInheritedDomainService : TestBaseDomainService()

    interface TestDomainServiceInterface

    @DomainService
    interface TestAnnotatedInterface

    class TestServiceImplementationWithoutAnnotation : TestAnnotatedInterface
}
