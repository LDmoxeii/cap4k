package com.only4.cap4k.plugin.codeanalysis.flow

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Cap4kFlowExportPluginTest {

    @Test
    fun `resolve module projects uses explicit pipeline module paths`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        val adapter = ProjectBuilder.builder()
            .withName("sample-adapter")
            .withParent(root)
            .build()
        val application = ProjectBuilder.builder()
            .withName("sample-application")
            .withParent(root)
            .build()
        val domain = ProjectBuilder.builder()
            .withName("sample-domain")
            .withParent(root)
            .build()

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = "sample-adapter",
            applicationModulePath = "sample-application",
            domainModulePath = "sample-domain",
        )

        assertEquals(
            listOf(adapter.path, application.path, domain.path),
            resolveModuleProjects(root, shape).map { it.path }
        )
    }

    @Test
    fun `resolve module projects falls back to root when no pipeline shape is present`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        assertEquals(listOf(root.path), resolveModuleProjects(root, null).map { it.path })
    }

    @Test
    fun `resolve label prefixes includes pipeline base package and project group`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        root.group = "com.acme"

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = null,
            applicationModulePath = null,
            domainModulePath = null,
        )

        assertEquals(
            listOf("com.acme.demo.", "com.acme."),
            resolveLabelPrefixes(root, shape)
        )
    }
}
