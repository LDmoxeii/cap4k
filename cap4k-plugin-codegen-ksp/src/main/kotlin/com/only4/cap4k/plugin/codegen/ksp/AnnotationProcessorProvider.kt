package com.only4.cap4k.plugin.codegen.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP Processor Provider
 * KSP 通过 Java SPI 机制发现并加载此 Provider
 */
class AnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AnnotationProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
