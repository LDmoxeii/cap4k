package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotCodec
import com.only4.cap4k.plugin.pipeline.agent.CapabilityContractFactsFactory
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import java.nio.file.Files
import java.nio.file.Path

object CapabilityContractFactsExporter {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "expected one output file argument" }
        val output = Path.of(args.single()).toAbsolutePath().normalize()
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = builtInCapabilityDescriptors(),
            publicTasks = PipelinePublicTasks.contracts,
        )
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, AgentSnapshotCodec().toJson(facts))
        println(output)
    }
}
