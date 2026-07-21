package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.generator.common.types.CanonicalTypeSymbolRegistryFactory
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry

internal fun ProjectConfig.designTypeSymbolRegistry(model: CanonicalModel): TypeSymbolRegistry =
    CanonicalTypeSymbolRegistryFactory.from(
        config = this,
        model = model,
        artifactLayout = ArtifactLayoutResolver(basePackage, artifactLayout),
    )
