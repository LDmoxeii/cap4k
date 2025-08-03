package com.only4.cap4k.ddd.core.archinfo

import com.only4.cap4k.ddd.core.application.RequestHandler
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.saga.SagaHandler
import com.only4.cap4k.ddd.core.archinfo.model.ArchInfo
import com.only4.cap4k.ddd.core.archinfo.model.Architecture
import com.only4.cap4k.ddd.core.archinfo.model.elements.*
import com.only4.cap4k.ddd.core.archinfo.model.elements.aggreagate.*
import com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub.DomainEventElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub.IntegrationEventElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.pubsub.SubscriberElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.CommandElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.QueryElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.RequestElement
import com.only4.cap4k.ddd.core.archinfo.model.elements.reqres.SagaElement
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.domain.service.annotation.DomainService
import com.only4.cap4k.ddd.core.share.Constants.ARCH_INFO_VERSION
import com.only4.cap4k.ddd.core.share.misc.findMethod
import com.only4.cap4k.ddd.core.share.misc.scanClass
import org.springframework.context.annotation.Description
import org.springframework.context.event.EventListener
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * 解析后的类分类结果
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class ResolvedClasses(
    val repositoryClasses: List<Class<*>> = emptyList(),
    val factoryClasses: List<Class<*>> = emptyList(),
    val factoryPayloadClasses: List<Class<*>> = emptyList(),
    val entityClasses: List<Class<*>> = emptyList(),
    val valueObjectClasses: List<Class<*>> = emptyList(),
    val enumObjectClasses: List<Class<*>> = emptyList(),
    val specificationClasses: List<Class<*>> = emptyList(),
    val domainServiceClasses: List<Class<*>> = emptyList(),
    val domainEventClasses: List<Class<*>> = emptyList(),
    val integrationEventClasses: List<Class<*>> = emptyList(),
    val subscriberClasses: List<Class<*>> = emptyList(),
    val queryClasses: List<Class<*>> = emptyList(),
    val commandClasses: List<Class<*>> = emptyList(),
    val requestClasses: List<Class<*>> = emptyList(),
    val sagaClasses: List<Class<*>> = emptyList()
) {
    companion object {
        fun empty() = ResolvedClasses()
    }
}

/**
 * 架构信息管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
class ArchInfoManager(
    private val name: String,
    private val version: String,
    private val basePackage: String
) {
    private var config: ((ArchInfo) -> ArchInfo)? = null

    /**
     * 懒加载的类分类结果，线程安全
     */
    private val resolvedClasses by lazy {
        resolveAllClasses()
    }

    /**
     * 解析所有类并按类型分类
     * 此方法只会被懒加载调用一次，并且是线程安全的
     */
    private fun resolveAllClasses(): ResolvedClasses {
        return try {
            val classes = scanClass(basePackage, true)

            // 使用可变列表进行分类
            val repositoryClasses = mutableListOf<Class<*>>()
            val factoryClasses = mutableListOf<Class<*>>()
            val factoryPayloadClasses = mutableListOf<Class<*>>()
            val entityClasses = mutableListOf<Class<*>>()
            val valueObjectClasses = mutableListOf<Class<*>>()
            val enumObjectClasses = mutableListOf<Class<*>>()
            val specificationClasses = mutableListOf<Class<*>>()
            val domainServiceClasses = mutableListOf<Class<*>>()
            val domainEventClasses = mutableListOf<Class<*>>()
            val integrationEventClasses = mutableListOf<Class<*>>()
            val subscriberClasses = mutableListOf<Class<*>>()
            val queryClasses = mutableListOf<Class<*>>()
            val commandClasses = mutableListOf<Class<*>>()
            val requestClasses = mutableListOf<Class<*>>()
            val sagaClasses = mutableListOf<Class<*>>()

            classes.forEach { cls ->
                // Check for event listener methods
                if (cls.declaredMethods.any { method ->
                        getDomainOrIntegrationEventListener(method) != null
                    }) {
                    subscriberClasses.add(cls)
                }

                // Check for Aggregate annotation
                if (cls.isAnnotationPresent(Aggregate::class.java)) {
                    val aggregate = cls.getAnnotation(Aggregate::class.java)
                    when (aggregate.type) {
                        Aggregate.TYPE_REPOSITORY -> repositoryClasses.add(cls)
                        Aggregate.TYPE_FACTORY -> factoryClasses.add(cls)
                        Aggregate.TYPE_FACTORY_PAYLOAD -> factoryPayloadClasses.add(cls)
                        Aggregate.TYPE_ENTITY -> entityClasses.add(cls)
                        Aggregate.TYPE_VALUE_OBJECT -> valueObjectClasses.add(cls)
                        Aggregate.TYPE_ENUM -> enumObjectClasses.add(cls)
                        Aggregate.TYPE_SPECIFICATION -> specificationClasses.add(cls)
                        Aggregate.TYPE_DOMAIN_EVENT -> domainEventClasses.add(cls)
                    }
                    return@forEach
                }

                // Check for other annotations
                when {
                    cls.isAnnotationPresent(DomainService::class.java) ->
                        domainServiceClasses.add(cls)

                    cls.isAnnotationPresent(DomainEvent::class.java) ->
                        domainEventClasses.add(cls)

                    cls.isAnnotationPresent(IntegrationEvent::class.java) ->
                        integrationEventClasses.add(cls)

                    EventSubscriber::class.java.isAssignableFrom(cls) ->
                        subscriberClasses.add(cls)

                    Query::class.java.isAssignableFrom(cls) ->
                        queryClasses.add(cls)

                    Command::class.java.isAssignableFrom(cls) ->
                        commandClasses.add(cls)

                    SagaHandler::class.java.isAssignableFrom(cls) ->
                        sagaClasses.add(cls)

                    RequestHandler::class.java.isAssignableFrom(cls) ->
                        requestClasses.add(cls)
                }
            }

            ResolvedClasses(
                repositoryClasses = repositoryClasses.toList(),
                factoryClasses = factoryClasses.toList(),
                factoryPayloadClasses = factoryPayloadClasses.toList(),
                entityClasses = entityClasses.toList(),
                valueObjectClasses = valueObjectClasses.toList(),
                enumObjectClasses = enumObjectClasses.toList(),
                specificationClasses = specificationClasses.toList(),
                domainServiceClasses = domainServiceClasses.toList(),
                domainEventClasses = domainEventClasses.toList(),
                integrationEventClasses = integrationEventClasses.toList(),
                subscriberClasses = subscriberClasses.toList(),
                queryClasses = queryClasses.toList(),
                commandClasses = commandClasses.toList(),
                requestClasses = requestClasses.toList(),
                sagaClasses = sagaClasses.toList()
            )
        } catch (e: Exception) {
            // 在异常情况下返回空的分类结果，避免应用崩溃
            ResolvedClasses.empty()
        }
    }

    fun configure(config: (ArchInfo) -> ArchInfo) {
        this.config = config
    }

    fun getArchInfo(): ArchInfo {
        val archInfo = loadArchInfo()
        return config?.invoke(archInfo) ?: archInfo
    }

    private fun loadArchInfo(): ArchInfo {
        return ArchInfo(
            name = name,
            version = version,
            archInfoVersion = ARCH_INFO_VERSION,
            architecture = loadArchitecture()
        )
    }

    private fun loadArchitecture(): Architecture {
        return Architecture(
            application = loadApplication(),
            domain = loadDomain()
        )
    }

    private fun loadApplication(): Architecture.Application {
        return Architecture.Application(
            requests = loadRequests(),
            events = loadEvents()
        )
    }

    private fun loadRequests(): MapCatalog {
        val commandCatalog = ListCatalog(
            name = "commands",
            description = "命令",
            elements = resolvedClasses.commandClasses.map { cls ->
                CommandElement(
                    classRef = cls.name,
                    requestClassRef = resolveRequestClass(cls).typeName,
                    responseClassRef = resolveResponseClass(cls).typeName,
                    name = cls.simpleName,
                    description = getDescription(cls, "")
                )
            }
        )

        val queryCatalog = ListCatalog(
            name = "queries",
            description = "查询",
            elements = resolvedClasses.queryClasses.map { cls ->
                QueryElement(
                    classRef = cls.name,
                    requestClassRef = resolveRequestClass(cls).typeName,
                    responseClassRef = resolveResponseClass(cls).typeName,
                    name = cls.simpleName,
                    description = getDescription(cls, "")
                )
            }
        )

        val sagaCatalog = ListCatalog(
            name = "sagas",
            description = "SAGA",
            elements = resolvedClasses.sagaClasses.map { cls ->
                SagaElement(
                    classRef = cls.name,
                    requestClassRef = resolveRequestClass(cls).typeName,
                    responseClassRef = resolveResponseClass(cls).typeName,
                    name = cls.simpleName,
                    description = getDescription(cls, "")
                )
            }
        )

        val requestCatalog = ListCatalog(
            name = "requests",
            description = "请求处理",
            elements = resolvedClasses.requestClasses.map { cls ->
                RequestElement(
                    classRef = cls.name,
                    requestClassRef = resolveRequestClass(cls).typeName,
                    responseClassRef = resolveResponseClass(cls).typeName,
                    name = cls.simpleName,
                    description = getDescription(cls, "")
                )
            }
        )

        return MapCatalog(
            name = "requests",
            description = "请求响应",
            elements = mapOf(
                "commands" to commandCatalog,
                "queries" to queryCatalog,
                "sagas" to sagaCatalog,
                "requests" to requestCatalog
            )
        )
    }

    private fun loadEventSubscriberMap(): Map<Class<*>, List<SubscriberElement>> {
        val eventSubscriberMap = mutableMapOf<Class<*>, MutableList<SubscriberElement>>()

        resolvedClasses.subscriberClasses.forEach { cls ->
            when {
                EventSubscriber::class.java.isAssignableFrom(cls) -> {
                    val eventCls = findMethod(cls, "onEvent") { method ->
                        method.parameterCount == 1
                    }!!.parameterTypes[0]

                    eventSubscriberMap.computeIfAbsent(eventCls) { mutableListOf() }
                        .add(
                            SubscriberElement(
                                classRef = cls.name,
                                name = cls.simpleName,
                                description = getDescription(cls, ""),
                                eventRef = resolveEventRef(eventCls)
                            )
                        )
                }

                else -> {
                    cls.declaredMethods
                        .filter { method -> getDomainOrIntegrationEventListener(method) != null }
                        .forEach { method ->
                            val eventListener = method.getAnnotation(EventListener::class.java)
                            val eventCls = when {
                                eventListener.classes.isNotEmpty() -> eventListener.classes[0].java
                                eventListener.value.isNotEmpty() -> eventListener.value[0].java
                                else -> return@forEach
                            }

                            eventSubscriberMap.computeIfAbsent(eventCls) { mutableListOf() }
                                .add(
                                    SubscriberElement(
                                        classRef = cls.name,
                                        name = "${cls.simpleName}#${method.name}",
                                        description = getDescription(method, ""),
                                        eventRef = resolveEventRef(eventCls)
                                    )
                                )
                        }
                }
            }
        }

        return eventSubscriberMap
    }

    private fun loadEvents(): MapCatalog {
        val eventSubscriberMap = loadEventSubscriberMap()

        val subscriberCatalog = ListCatalog(
            name = "subscribers",
            description = "订阅者",
            elements = eventSubscriberMap.values.flatten()
        )

        val domainEventCatalog = ListCatalog(
            name = "domain",
            description = "领域事件",
            elements = resolvedClasses.domainEventClasses.map { cls ->
                ElementRef(
                    ref = resolveEventRef(cls),
                    name = cls.simpleName,
                    description = getDescription(cls, "")
                )
            }
        )

        val integrationEventCatalog = ListCatalog(
            name = "integration",
            description = "集成事件",
            elements = resolvedClasses.integrationEventClasses.map { cls ->
                IntegrationEventElement(
                    classRef = cls.name,
                    name = cls.simpleName,
                    description = getDescription(cls, ""),
                    subscribersRef = eventSubscriberMap[cls]?.map { sub ->
                        "/architecture/application/events/subscribers/${sub.name}"
                    } ?: emptyList()
                )
            }
        )

        return MapCatalog(
            name = "events",
            description = "事件",
            elements = mapOf(
                "domain" to domainEventCatalog,
                "integration" to integrationEventCatalog,
                "subscribers" to subscriberCatalog
            )
        )
    }

    private fun loadDomain(): Architecture.Domain {
        return Architecture.Domain(
            aggregates = loadAggregates(),
            services = loadDomainServices()
        )
    }

    private fun loadAggregates(): MapCatalog {
        val eventSubscriberMap = loadEventSubscriberMap()

        val aggregateElements = resolvedClasses.repositoryClasses.map { cls ->
            val aggregate = getAggregate(cls)!!

            val rootCatalog = resolvedClasses.entityClasses
                .filter { entityCls ->
                    val entityAggregate = getAggregate(entityCls)
                    entityAggregate?.aggregate == aggregate.aggregate && entityAggregate.root
                }
                .map { entityCls ->
                    val entityAggregate = getAggregate(entityCls)!!
                    EntityElement(
                        classRef = entityCls.name,
                        name = entityAggregate.name,
                        description = getDescription(entityCls, entityAggregate.description),
                        root = true
                    )
                }
                .firstOrNull() ?: NoneElement

            val repositoryCatalog = resolvedClasses.repositoryClasses
                .filter { repositoryCls ->
                    getAggregate(repositoryCls)?.aggregate == aggregate.aggregate
                }
                .map { repositoryCls ->
                    val repositoryAggregate = getAggregate(repositoryCls)!!
                    RepositoryElement(
                        classRef = repositoryCls.name,
                        name = repositoryAggregate.name,
                        description = getDescription(repositoryCls, repositoryAggregate.description)
                    )
                }
                .firstOrNull() ?: NoneElement

            val factoryCatalog = resolvedClasses.factoryClasses
                .filter { factoryCls ->
                    getAggregate(factoryCls)?.aggregate == aggregate.aggregate
                }
                .map { factoryCls ->
                    val factoryAggregate = getAggregate(factoryCls)!!
                    FactoryElement(
                        classRef = factoryCls.name,
                        payloadClassRef = resolvedClasses.factoryPayloadClasses
                            .filter { payloadCls ->
                                getAggregate(payloadCls)?.aggregate == aggregate.aggregate
                            }
                            .map { it.name },
                        name = factoryAggregate.name,
                        description = getDescription(factoryCls, factoryAggregate.description)
                    )
                }
                .firstOrNull() ?: NoneElement

            val entityCatalog = ListCatalog(
                name = "entities",
                description = "实体",
                elements = resolvedClasses.entityClasses
                    .filter { entityCls ->
                        val entityAggregate = getAggregate(entityCls)
                        entityAggregate?.aggregate == aggregate.aggregate && !entityAggregate.root
                    }
                    .map { entityCls ->
                        val entityAggregate = getAggregate(entityCls)!!
                        EntityElement(
                            classRef = entityCls.name,
                            name = entityAggregate.name,
                            description = getDescription(entityCls, entityAggregate.description),
                            root = false
                        )
                    }
            )

            val valueObjectCatalog = ListCatalog(
                name = "valueObjects",
                description = "值对象",
                elements = resolvedClasses.valueObjectClasses
                    .filter { valueObjectCls ->
                        getAggregate(valueObjectCls)?.aggregate == aggregate.aggregate
                    }
                    .map { valueObjectCls ->
                        val valueObjectAggregate = getAggregate(valueObjectCls)!!
                        ValueObjectElement(
                            classRef = valueObjectCls.name,
                            name = valueObjectAggregate.name,
                            description = getDescription(valueObjectCls, valueObjectAggregate.description)
                        )
                    }
            )

            val enumCatalog = ListCatalog(
                name = "enums",
                description = "枚举",
                elements = resolvedClasses.enumObjectClasses
                    .filter { enumCls ->
                        getAggregate(enumCls)?.aggregate == aggregate.aggregate
                    }
                    .map { enumCls ->
                        val enumAggregate = getAggregate(enumCls)!!
                        EnumElement(
                            classRef = enumCls.name,
                            name = enumAggregate.name,
                            description = getDescription(enumCls, enumAggregate.description)
                        )
                    }
            )

            val specificationCatalog = ListCatalog(
                name = "specifications",
                description = "规约",
                elements = resolvedClasses.specificationClasses
                    .filter { specCls ->
                        getAggregate(specCls)?.aggregate == aggregate.aggregate
                    }
                    .map { specCls ->
                        val specAggregate = getAggregate(specCls)!!
                        SpecificationElement(
                            classRef = specCls.name,
                            name = specAggregate.name,
                            description = getDescription(specCls, specAggregate.description)
                        )
                    }
            )

            val eventCatalog = ListCatalog(
                name = "events",
                description = "领域事件",
                elements = resolvedClasses.domainEventClasses
                    .filter { domainEventCls ->
                        getAggregate(domainEventCls)?.aggregate == aggregate.aggregate
                    }
                    .map { domainEventCls ->
                        val eventAggregate = getAggregate(domainEventCls)!!
                        DomainEventElement(
                            classRef = domainEventCls.name,
                            name = eventAggregate.name,
                            description = getDescription(domainEventCls, eventAggregate.description),
                            subscribersRef = eventSubscriberMap[domainEventCls]?.map { sub ->
                                "/architecture/application/events/subscribers/${sub.name}"
                            } ?: emptyList()
                        )
                    }
            )

            MapCatalog(
                name = aggregate.aggregate,
                description = aggregate.description,
                elements = mapOf(
                    "root" to rootCatalog,
                    "repository" to repositoryCatalog,
                    "factory" to factoryCatalog,
                    "entities" to entityCatalog,
                    "valueObjects" to valueObjectCatalog,
                    "enums" to enumCatalog,
                    "specifications" to specificationCatalog,
                    "events" to eventCatalog
                )
            )
        }

        return MapCatalog(
            name = "aggregates",
            description = "聚合",
            elements = aggregateElements.associateBy { it.name }
        )
    }

    private fun loadDomainServices(): ListCatalog {
        return ListCatalog(
            name = "services",
            description = "领域服务",
            elements = resolvedClasses.domainServiceClasses.map { cls ->
                val domainService = cls.getAnnotation(DomainService::class.java)
                DomainServiceElement(
                    classRef = cls.name,
                    name = domainService.name,
                    description = getDescription(cls, domainService.description)
                )
            }
        )
    }

    private fun resolveEventRef(eventCls: Class<*>): String {
        return when {
            eventCls.isAnnotationPresent(DomainEvent::class.java) &&
                    eventCls.isAnnotationPresent(Aggregate::class.java) -> {
                val aggregate = eventCls.getAnnotation(Aggregate::class.java)
                "/architecture/domain/aggregates/${aggregate.aggregate}/events/${aggregate.name}"
            }

            eventCls.isAnnotationPresent(IntegrationEvent::class.java) -> {
                "/architecture/application/events/integration/${eventCls.simpleName}"
            }

            else -> ""
        }
    }

    private fun resolveRequestClass(requestHandlerCls: Class<*>): Type {
        val method = findMethod(requestHandlerCls, "exec") { m ->
            m.parameterCount == 1
        }!!
        return method.genericParameterTypes[0]
    }

    private fun resolveResponseClass(requestHandlerCls: Class<*>): Type {
        val method = findMethod(requestHandlerCls, "exec") { m ->
            m.parameterCount == 1
        }!!
        return method.genericReturnType
    }

    private fun getAggregate(accessibleObject: AnnotatedElement): Aggregate? {
        return if (accessibleObject.isAnnotationPresent(Aggregate::class.java)) {
            accessibleObject.getAnnotation(Aggregate::class.java)
        } else {
            null
        }
    }

    private fun getDescription(accessibleObject: AnnotatedElement, defaultVal: String): String {
        return if (accessibleObject.isAnnotationPresent(Description::class.java)) {
            accessibleObject.getAnnotation(Description::class.java).value
        } else {
            defaultVal
        }
    }

    private fun getDomainOrIntegrationEventListener(method: Method): EventListener? {
        if (!method.isAnnotationPresent(EventListener::class.java)) {
            return null
        }

        val eventListener = method.getAnnotation(EventListener::class.java)

        val eventClass = when {
            eventListener.classes.size == 1 -> eventListener.classes[0].java
            eventListener.value.size == 1 -> eventListener.value[0].java
            else -> return null
        }

        return if (eventClass.isAnnotationPresent(DomainEvent::class.java) ||
            eventClass.isAnnotationPresent(IntegrationEvent::class.java)
        ) {
            eventListener
        } else {
            null
        }
    }
}
