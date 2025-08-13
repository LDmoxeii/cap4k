package com.only4.cap4k.ddd.core.archinfo

import com.only4.cap4k.ddd.core.archinfo.model.ArchInfo
import com.only4.cap4k.ddd.core.archinfo.model.elements.DomainServiceElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.ElementRef
import com.only4.cap4k.ddd.core.archinfo.model.elements.ListCatalog
import com.only4.cap4k.ddd.core.archinfo.model.elements.MapCatalog
import com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate.EntityElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate.FactoryElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate.RepositoryElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub.IntegrationEventElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub.SubscriberElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.CommandElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.QueryElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.RequestElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.SagaElement
import com.only4.cap4k.ddd.core.share.Constants.ARCH_INFO_VERSION
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@DisplayName("ArchInfoManager 测试")
class ArchInfoManagerTest {

    private lateinit var archInfoManager: ArchInfoManager

    @BeforeEach
    fun setUp() {
        archInfoManager = ArchInfoManager(
            name = "TestApp",
            version = "1.0.0",
            basePackage = "com.only4.cap4k.ddd.core.archinfo.testdata"
        )
    }

    @Nested
    @DisplayName("基本功能测试")
    inner class BasicFunctionalityTests {

        @Test
        @DisplayName("应该能够创建ArchInfoManager实例")
        fun shouldCreateArchInfoManagerInstance() {
            val manager = ArchInfoManager("TestApp", "1.0.0", "test.package")
            assertNotNull(manager)
        }

        @Test
        @DisplayName("应该能够获取基本的ArchInfo")
        fun shouldGetBasicArchInfo() {
            val archInfo = archInfoManager.getArchInfo()

            assertNotNull(archInfo)
            assertEquals("TestApp", archInfo.name)
            assertEquals("1.0.0", archInfo.version)
            assertEquals(ARCH_INFO_VERSION, archInfo.archInfoVersion)
            assertNotNull(archInfo.architecture)
        }

        @Test
        @DisplayName("应该能够配置ArchInfo后处理器")
        fun shouldConfigureArchInfoPostProcessor() {
            val configFunction: (ArchInfo) -> ArchInfo = { archInfo ->
                archInfo.copy(name = "ModifiedApp")
            }

            archInfoManager.configure(configFunction)
            val archInfo = archInfoManager.getArchInfo()

            assertEquals("ModifiedApp", archInfo.name)
        }

        @Test
        @DisplayName("多次调用getArchInfo应该返回一致的结果")
        fun shouldReturnConsistentResultsOnMultipleCalls() {
            val archInfo1 = archInfoManager.getArchInfo()
            val archInfo2 = archInfoManager.getArchInfo()

            assertEquals(archInfo1.name, archInfo2.name)
            assertEquals(archInfo1.version, archInfo2.version)
            assertEquals(archInfo1.archInfoVersion, archInfo2.archInfoVersion)
        }
    }

    @Nested
    @DisplayName("架构信息加载测试")
    inner class ArchitectureLoadingTests {

        @Test
        @DisplayName("应该正确加载应用层架构信息")
        fun shouldLoadApplicationArchitecture() {
            val archInfo = archInfoManager.getArchInfo()
            val application = archInfo.architecture.application

            assertNotNull(application)
            assertNotNull(application.requests)
            assertNotNull(application.events)
        }

        @Test
        @DisplayName("应该正确加载领域层架构信息")
        fun shouldLoadDomainArchitecture() {
            val archInfo = archInfoManager.getArchInfo()
            val domain = archInfo.architecture.domain

            assertNotNull(domain)
            assertNotNull(domain.aggregates)
            assertNotNull(domain.services)
        }
    }

    @Nested
    @DisplayName("请求处理架构测试")
    inner class RequestArchitectureTests {

        @Test
        @DisplayName("应该正确识别和分类Command处理器")
        fun shouldIdentifyCommandHandlers() {
            val archInfo = archInfoManager.getArchInfo()
            val requests = archInfo.architecture.application.requests as MapCatalog
            val commands = requests["commands"] as? ListCatalog

            assertNotNull(commands)
            assertTrue(commands.isNotEmpty())
            val commandElement = commands.find {
                it is CommandElement && it.classRef.contains("TestUserCommand")
            } as? CommandElement

            assertNotNull(commandElement)
            assertEquals("TestUserCommand", commandElement.name)
        }

        @Test
        @DisplayName("应该正确识别和分类Query处理器")
        fun shouldIdentifyQueryHandlers() {
            val archInfo = archInfoManager.getArchInfo()
            val requests = archInfo.architecture.application.requests as MapCatalog
            val queries = requests["queries"] as? ListCatalog

            assertNotNull(queries)
            assertTrue(queries.isNotEmpty())
            val queryElement = queries.find {
                it is QueryElement && it.classRef.contains("TestUserQuery")
            } as? QueryElement

            assertNotNull(queryElement)
            assertEquals("TestUserQuery", queryElement.name)
        }

        @Test
        @DisplayName("应该正确识别和分类Saga处理器")
        fun shouldIdentifySagaHandlers() {
            val archInfo = archInfoManager.getArchInfo()
            val requests = archInfo.architecture.application.requests as MapCatalog
            val sagas = requests["sagas"] as? ListCatalog

            assertNotNull(sagas)
            assertTrue(sagas.isNotEmpty())
            val sagaElement = sagas.find {
                it is SagaElement && it.classRef.contains("TestUserSaga")
            } as? SagaElement

            assertNotNull(sagaElement)
            assertEquals("TestUserSaga", sagaElement.name)
        }

        @Test
        @DisplayName("应该正确识别和分类Request处理器")
        fun shouldIdentifyRequestHandlers() {
            val archInfo = archInfoManager.getArchInfo()
            val requests = archInfo.architecture.application.requests as MapCatalog
            val requestHandlers = requests["requests"] as? ListCatalog

            assertNotNull(requestHandlers)
            assertTrue(requestHandlers.isNotEmpty())
            val requestElement = requestHandlers.find {
                it is RequestElement && it.classRef.contains("TestUserRequestHandler")
            } as? RequestElement

            assertNotNull(requestElement)
            assertEquals("TestUserRequestHandler", requestElement.name)
        }
    }

    @Nested
    @DisplayName("事件架构测试")
    inner class EventArchitectureTests {

        @Test
        @DisplayName("应该正确识别领域事件")
        fun shouldIdentifyDomainEvents() {
            val archInfo = archInfoManager.getArchInfo()
            val events = archInfo.architecture.application.events as MapCatalog
            val domainEvents = events["domain"] as? ListCatalog

            assertNotNull(domainEvents)
            assertTrue(domainEvents.isNotEmpty())
            val domainEvent = domainEvents.find {
                it is ElementRef && it.name.contains("TestUserCreatedEvent")
            }

            assertNotNull(domainEvent)
        }

        @Test
        @DisplayName("应该正确识别集成事件")
        fun shouldIdentifyIntegrationEvents() {
            val archInfo = archInfoManager.getArchInfo()
            val events = archInfo.architecture.application.events as MapCatalog
            val integrationEvents = events["integration"] as? ListCatalog

            assertNotNull(integrationEvents)
            assertTrue(integrationEvents.isNotEmpty())
            val integrationEvent = integrationEvents.find {
                it is IntegrationEventElement && it.classRef.contains("TestUserRegisteredIntegrationEvent")
            } as? IntegrationEventElement

            assertNotNull(integrationEvent)
            assertEquals("TestUserRegisteredIntegrationEvent", integrationEvent.name)
        }

        @Test
        @DisplayName("应该正确识别事件订阅者")
        fun shouldIdentifyEventSubscribers() {
            val archInfo = archInfoManager.getArchInfo()
            val events = archInfo.architecture.application.events as MapCatalog
            val subscribers = events["subscribers"] as? ListCatalog

            assertNotNull(subscribers)
            assertTrue(subscribers.isNotEmpty())

            // Check EventSubscriber implementation
            val eventSubscriber = subscribers.find {
                it is SubscriberElement && it.classRef.contains("TestUserEventSubscriber")
            } as? SubscriberElement
            assertNotNull(eventSubscriber)

            // Check @EventListener method
            val eventListener = subscribers.find {
                it is SubscriberElement && it.name.contains("TestSpringEventListener#")
            } as? SubscriberElement
            assertNotNull(eventListener)
        }
    }

    @Nested
    @DisplayName("聚合架构测试")
    inner class AggregateArchitectureTests {

        @Test
        @DisplayName("应该正确识别聚合根实体")
        fun shouldIdentifyAggregateRootEntities() {
            val archInfo = archInfoManager.getArchInfo()
            val aggregates = archInfo.architecture.domain.aggregates as MapCatalog
            val userAggregate = aggregates["user"] as? MapCatalog

            assertNotNull(userAggregate)
            val rootEntity = userAggregate["root"] as? EntityElement

            assertNotNull(rootEntity)
            assertEquals("User", rootEntity.name)
            assertTrue(rootEntity.root)
            assertTrue(rootEntity.classRef.contains("TestUserEntity"))
        }

        @Test
        @DisplayName("应该正确识别聚合仓储")
        fun shouldIdentifyAggregateRepositories() {
            val archInfo = archInfoManager.getArchInfo()
            val aggregates = archInfo.architecture.domain.aggregates as MapCatalog
            val userAggregate = aggregates["user"] as? MapCatalog

            assertNotNull(userAggregate)
            val repository = userAggregate["repository"] as? RepositoryElement

            assertNotNull(repository)
            assertEquals("UserRepository", repository.name)
            assertTrue(repository.classRef.contains("TestUserRepository"))
        }

        @Test
        @DisplayName("应该正确识别聚合工厂")
        fun shouldIdentifyAggregateFactories() {
            val archInfo = archInfoManager.getArchInfo()
            val aggregates = archInfo.architecture.domain.aggregates as MapCatalog
            val userAggregate = aggregates["user"] as? MapCatalog

            assertNotNull(userAggregate)
            val factory = userAggregate["factory"] as? FactoryElement

            assertNotNull(factory)
            assertEquals("UserFactory", factory.name)
            assertTrue(factory.classRef.contains("TestUserFactory"))
            assertTrue(factory.payloadClassRef.any { it.contains("TestUserCreatePayload") })
        }
    }

    @Nested
    @DisplayName("领域服务测试")
    inner class DomainServiceTests {

        @Test
        @DisplayName("应该正确识别领域服务")
        fun shouldIdentifyDomainServices() {
            val archInfo = archInfoManager.getArchInfo()
            val services = archInfo.architecture.domain.services as ListCatalog

            assertTrue(services.isNotEmpty())
            val userService = services.find {
                it is DomainServiceElement && it.name == "UserService"
            } as? DomainServiceElement

            assertNotNull(userService)
            assertEquals("用户相关的业务逻辑", userService.description) // From @Description annotation
            assertTrue(userService.classRef.contains("TestUserDomainService"))
        }
    }

    @Nested
    @DisplayName("边界条件和异常处理测试")
    inner class EdgeCasesAndExceptionTests {

        @Test
        @DisplayName("应该正确处理空包路径")
        fun shouldHandleEmptyPackagePath() {
            val emptyPackageManager = ArchInfoManager("TestApp", "1.0.0", "nonexistent.package")
            val archInfo = emptyPackageManager.getArchInfo()

            assertNotNull(archInfo)
            assertEquals("TestApp", archInfo.name)
        }

        @Test
        @DisplayName("重复调用resolve方法应该是幂等的")
        fun shouldBeIdempotentOnMultipleResolveCalls() {
            val archInfo1 = archInfoManager.getArchInfo()
            val archInfo2 = archInfoManager.getArchInfo()
            val archInfo3 = archInfoManager.getArchInfo()

            // Should have same structure (not necessarily same object references)
            assertEquals(archInfo1.name, archInfo2.name)
            assertEquals(archInfo2.name, archInfo3.name)
            assertEquals(archInfo1.version, archInfo2.version)
            assertEquals(archInfo2.version, archInfo3.version)
        }
    }
}
