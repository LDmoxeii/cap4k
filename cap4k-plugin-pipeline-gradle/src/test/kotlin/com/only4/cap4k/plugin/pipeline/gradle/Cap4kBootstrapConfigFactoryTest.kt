package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Cap4kBootstrapConfigFactoryTest {

    @Test
    fun `build returns bootstrap config for ddd multi module preset`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
            templates {
                preset.set("ddd-default-bootstrap")
            }
            slots {
                root.from("codegen/bootstrap-slots/root")
                modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
            }
        }

        val config = Cap4kBootstrapConfigFactory().build(project, extension)

        assertEquals("ddd-multi-module", config.preset)
        assertEquals("only-danmuku", config.projectName)
        assertEquals("edu.only4.danmuku", config.basePackage)
        assertEquals(ConflictPolicy.FAIL, config.conflictPolicy)
        assertEquals("only-danmuku-domain", config.modules.domainModuleName)
        assertEquals(listOf("root", "module-package:domain"), config.slots.map { it.slotId })
    }

    @Test
    fun `build fails when bootstrap enabled without project name`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap.enabled.set(true)
        extension.bootstrap.preset.set("ddd-multi-module")
        extension.bootstrap.basePackage.set("edu.only4.danmuku")

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.projectName is required"))
    }

    @Test
    fun `build fails when slot role is outside fixed module roles`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
            slots {
                moduleRoot("start").from("codegen/bootstrap-slots/start-root")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("unsupported bootstrap slot role"))
    }
}
