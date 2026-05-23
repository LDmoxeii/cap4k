package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal fun ProjectConfig.designTypeRegistryFqns(model: CanonicalModel): Map<String, String> =
    typeRegistryFqns() + model.strongIds.associate { strongId ->
        strongId.typeName to "${strongId.packageName}.${strongId.typeName}"
    }
