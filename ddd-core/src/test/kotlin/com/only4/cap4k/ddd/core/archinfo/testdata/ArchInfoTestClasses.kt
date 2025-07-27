package com.only4.cap4k.ddd.core.archinfo.testdata

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.application.saga.SagaParam
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.domain.service.annotation.DomainService
import org.springframework.context.annotation.Description
import org.springframework.context.event.EventListener

// Test Aggregate Classes
@Aggregate(aggregate = "user", type = Aggregate.TYPE_REPOSITORY, name = "UserRepository", description = "用户仓储")
class TestUserRepository

@Aggregate(aggregate = "user", type = Aggregate.TYPE_FACTORY, name = "UserFactory", description = "用户工厂")
class TestUserFactory

@Aggregate(
    aggregate = "user",
    type = Aggregate.TYPE_FACTORY_PAYLOAD,
    name = "UserCreatePayload",
    description = "用户创建载荷"
)
class TestUserCreatePayload

@Aggregate(aggregate = "user", type = Aggregate.TYPE_ENTITY, name = "User", description = "用户实体", root = true)
@Description("用户根实体")
class TestUserEntity

@Aggregate(
    aggregate = "user",
    type = Aggregate.TYPE_ENTITY,
    name = "UserProfile",
    description = "用户档案",
    root = false
)
class TestUserProfileEntity

@Aggregate(aggregate = "user", type = Aggregate.TYPE_VALUE_OBJECT, name = "UserEmail", description = "用户邮箱")
class TestUserEmailVO

@Aggregate(aggregate = "user", type = Aggregate.TYPE_ENUM, name = "UserStatus", description = "用户状态")
enum class TestUserStatus { ACTIVE, INACTIVE, SUSPENDED }

@Aggregate(
    aggregate = "user",
    type = Aggregate.TYPE_SPECIFICATION,
    name = "UserValidSpec",
    description = "用户验证规约"
)
class TestUserValidSpecification

@Aggregate(aggregate = "user", type = Aggregate.TYPE_DOMAIN_EVENT, name = "UserCreated", description = "用户已创建事件")
@DomainEvent
class TestUserCreatedEvent

// Domain Service
@DomainService(name = "UserService", description = "用户领域服务")
@Description("用户相关的业务逻辑")
class TestUserDomainService

// Events
@DomainEvent
@Aggregate(aggregate = "user", name = "UserUpdated", description = "用户已更新事件")
class TestUserUpdatedEvent

@IntegrationEvent
@Description("用户注册完成集成事件")
class TestUserRegisteredIntegrationEvent

// Event Subscribers
class TestUserEventSubscriber : EventSubscriber<TestUserCreatedEvent> {
    override fun onEvent(event: TestUserCreatedEvent) {
        // Handle event
    }
}

class TestSpringEventListener {
    @EventListener
    fun handleUserCreated(event: TestUserCreatedEvent) {
        // Handle user created event
    }

    @EventListener(TestUserRegisteredIntegrationEvent::class)
    @Description("处理用户注册集成事件")
    fun handleUserRegistered(event: TestUserRegisteredIntegrationEvent) {
        // Handle integration event
    }
}

// Request Handlers
class TestUserQuery : Query<TestUserQueryRequest, TestUserQueryResponse> {
    override fun exec(request: TestUserQueryRequest): TestUserQueryResponse {
        return TestUserQueryResponse()
    }
}

class TestUserCommand : Command<TestUserCommandRequest, TestUserCommandResponse> {
    override fun exec(request: TestUserCommandRequest): TestUserCommandResponse {
        return TestUserCommandResponse()
    }
}

class TestUserSaga : SagaHandler<TestUserSagaRequest, TestUserSagaResponse> {
    override fun exec(request: TestUserSagaRequest): TestUserSagaResponse {
        return TestUserSagaResponse()
    }
}

class TestUserRequestHandler : RequestHandler<TestUserRequest, TestUserResponse> {
    override fun exec(request: TestUserRequest): TestUserResponse {
        return TestUserResponse()
    }
}

// Request/Response DTOs
data class TestUserQueryRequest(val userId: String) : RequestParam<TestUserQueryResponse>
data class TestUserQueryResponse(val user: String = "test")

data class TestUserCommandRequest(val name: String) : RequestParam<TestUserCommandResponse>
data class TestUserCommandResponse(val id: String = "123")

data class TestUserSagaRequest(val processId: String) : SagaParam<TestUserSagaResponse>
data class TestUserSagaResponse(val status: String = "completed")

data class TestUserRequest(val action: String) : RequestParam<TestUserResponse>
data class TestUserResponse(val result: String = "success")

// Test classes for different aggregates
@Aggregate(aggregate = "order", type = Aggregate.TYPE_REPOSITORY, name = "OrderRepository", description = "订单仓储")
class TestOrderRepository

@Aggregate(aggregate = "order", type = Aggregate.TYPE_ENTITY, name = "Order", description = "订单实体", root = true)
class TestOrderEntity

@Aggregate(
    aggregate = "order",
    type = Aggregate.TYPE_DOMAIN_EVENT,
    name = "OrderCreated",
    description = "订单已创建事件"
)
@DomainEvent
class TestOrderCreatedEvent

// Classes without annotations for negative testing
class TestPlainClass
interface TestPlainInterface
abstract class TestPlainAbstractClass
