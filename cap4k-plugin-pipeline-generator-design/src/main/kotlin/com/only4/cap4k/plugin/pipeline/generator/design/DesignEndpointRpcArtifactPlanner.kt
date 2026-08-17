package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*
import java.security.MessageDigest

class DesignEndpointRpcArtifactPlanner : GeneratorProvider {
    override val id: String = "endpoint-rpc"

    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Endpoint RPC Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-design",
        activation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
        tacticalCarriers = listOf("Endpoint"),
        executionLanes = listOf(PipelineExecutionLane.GENERATED_SOURCE),
        tasks = listOf(PipelinePublicTasks.GENERATE_SOURCES),
        inputRequirements = listOf(PipelineInputRequirement("design-json-input", listOf("pipeline.source.design-json"))),
        outputKinds = listOf(ArtifactOutputKind.GENERATED_SOURCE, ArtifactOutputKind.GENERATED_RESOURCE),
        boundaries = runtimeDesignBoundaries(providerOwned = true),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val generator = config.generators[id] ?: return emptyList()
        val serviceId = generator.options["serviceId"]?.toString()?.trim().orEmpty()
        require(serviceId.isNotEmpty()) { "generators.endpointRpc.serviceId must not be blank." }
        val selectedNames = generator.options["operationNames"].asStringList().map(String::trim)
        require(selectedNames.isNotEmpty() && selectedNames.none(String::isEmpty)) {
            "generators.endpointRpc.operationNames must contain at least one non-blank operation name."
        }
        require(selectedNames.size == selectedNames.toSet().size) {
            "generators.endpointRpc.operationNames must not contain duplicates."
        }
        val endpointsByName = model.endpoints.associateBy { it.operationName }
        val unknown = selectedNames.filterNot(endpointsByName::containsKey).sorted()
        require(unknown.isEmpty()) {
            "generators.endpointRpc.operationNames contains unknown Endpoint operations: ${unknown.joinToString(", ")}"
        }
        val selected = selectedNames.map(endpointsByName::getValue)
        val adapterRoot = requireRelativeModuleRoot(config, "adapter")
        val clientRoot = requireRelativeModuleRoot(config, "endpoint-client")
        requireRelativeModuleRoot(config, "contract")
        val layout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val packageName = config.basePackage.trim('.').let { if (it.isEmpty()) "endpoint.rpc.generated" else "$it.endpoint.rpc.generated" }
        val operations = selected.map { endpoint -> endpoint.toRpcOperationContext() }
        val common = mapOf(
            "packageName" to packageName,
            "serviceId" to serviceId,
            "serviceIdKotlinStringLiteral" to serviceId.toKotlinStringLiteral(),
            "operations" to operations,
            "imports" to emptyList<String>(),
        )
        val items = mutableListOf<ArtifactPlanItem>()
        items += ArtifactPlanItem(
            generatorId = id,
            moduleRole = "adapter",
            templateId = "design/endpoint-rpc-provider-bindings.kt.peb",
            outputPath = layout.generatedKotlinSourcePath(adapterRoot, packageName, "EndpointRpcProviderBindings"),
            context = common,
            conflictPolicy = ConflictPolicy.OVERWRITE,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
        )
        selected.forEach { endpoint ->
            val operation = endpoint.toRpcOperationContext()
            items += ArtifactPlanItem(
                generatorId = id,
                moduleRole = "endpoint-client",
                templateId = "design/endpoint-rpc-remote-handler.kt.peb",
                outputPath = layout.generatedKotlinSourcePath(clientRoot, packageName, operation.getValue("handlerTypeName") as String),
                context = common + operation,
                conflictPolicy = ConflictPolicy.OVERWRITE,
                outputKind = ArtifactOutputKind.GENERATED_SOURCE,
            )
        }
        items += ArtifactPlanItem(
            generatorId = id,
            moduleRole = "endpoint-client",
            templateId = "design/endpoint-rpc-client-auto-configuration.kt.peb",
            outputPath = layout.generatedKotlinSourcePath(clientRoot, packageName, "EndpointRpcClientAutoConfiguration"),
            context = common,
            conflictPolicy = ConflictPolicy.OVERWRITE,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
        )
        items += ArtifactPlanItem(
            generatorId = id,
            moduleRole = "endpoint-client",
            templateId = "design/endpoint-rpc-auto-configuration.imports.peb",
            outputPath = "$clientRoot/build/generated/cap4k/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            context = common + ("autoConfigurationFqn" to "$packageName.EndpointRpcClientAutoConfiguration"),
            conflictPolicy = ConflictPolicy.OVERWRITE,
            outputKind = ArtifactOutputKind.GENERATED_RESOURCE,
        )
        return items
    }
}

private fun EndpointModel.toRpcOperationContext(): Map<String, Any?> {
    val ownerFqn = listOf(packageName, typeName).filter(String::isNotBlank).joinToString(".")
    val identitySuffix = MessageDigest.getInstance("SHA-256")
        .digest((operationName + "|" + ownerFqn).toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        .take(8)
    return mapOf(
        "operationName" to operationName,
        "ownerFqn" to ownerFqn,
        "requestFqn" to "$ownerFqn.Request",
        "responseFqn" to "$ownerFqn.Response",
        "handlerTypeName" to typeName + "RemoteEndpointHandler_" + identitySuffix,
        "beanMethodName" to typeName.replaceFirstChar { it.lowercase() } + "RemoteEndpointHandler_" + identitySuffix,
    )
}

private fun Any?.asStringList(): List<String> = when (this) {
    is Iterable<*> -> mapNotNull { it?.toString() }
    is Array<*> -> mapNotNull { it?.toString() }
    null -> emptyList()
    else -> listOf(toString())
}

