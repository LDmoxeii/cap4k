package com.only4.cap4k.plugin.pipeline.bootstrap

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.streams.toList

internal object BootstrapSlotPlanner {

    fun plan(config: BootstrapConfig): List<BootstrapPlanItem> =
        config.slots.flatMap { binding ->
            val root = Path.of(binding.sourceDir)
            if (!Files.exists(root)) return@flatMap emptyList()

            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { source ->
                        val relative = root.relativize(source).invariantSeparatorsPathString
                        val renderedRelative = renderRelativePath(relative, config)
                        BootstrapPlanItem(
                            presetId = config.preset,
                            sourcePath = source.toAbsolutePath().normalize().invariantSeparatorsPathString,
                            slotId = binding.slotId,
                            outputPath = resolveSlotOutputPath(binding, renderedRelative, config),
                            conflictPolicy = config.conflictPolicy,
                            context = bootstrapContext(config),
                        )
                    }
                    .toList()
            }
        }
}
