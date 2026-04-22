package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
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
        assertEquals(BootstrapMode.IN_PLACE, config.mode)
        assertEquals(null, config.previewDir)
        assertEquals("only-danmuku-domain", config.modules.domainModuleName)
        assertEquals(listOf("root", "module-package:domain"), config.slots.map { it.slotId })
        assertEquals(
            listOf(
                "codegen/bootstrap-slots/root",
                "codegen/bootstrap-slots/domain-package",
            ),
            config.slots.map { it.sourceDir },
        )
    }

    @Test
    fun `build maps explicit preview subtree mode and preview dir`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            mode.set(BootstrapMode.PREVIEW_SUBTREE)
            previewDir.set("preview/demo")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val config = Cap4kBootstrapConfigFactory().build(project, extension)

        assertEquals(BootstrapMode.PREVIEW_SUBTREE, config.mode)
        assertEquals("preview/demo", config.previewDir)
    }

    @Test
    fun `build requires preview dir when mode is preview subtree`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            mode.set(BootstrapMode.PREVIEW_SUBTREE)
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.previewDir is required"))
    }

    @Test
    fun `build rejects preview dir when mode is not preview subtree`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            mode.set(BootstrapMode.IN_PLACE)
            previewDir.set("preview/demo")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.previewDir is only supported when bootstrap.mode=PREVIEW_SUBTREE"))
    }

    @Test
    fun `build rejects unsafe preview dir`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            mode.set(BootstrapMode.PREVIEW_SUBTREE)
            previewDir.set("../preview")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.previewDir must be a safe relative path"))
    }

    @Test
    fun `build rejects preview dir containing slash escape`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            mode.set(BootstrapMode.PREVIEW_SUBTREE)
            previewDir.set("preview\\demo")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.previewDir must be a safe relative path"))
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

    @Test
    fun `build fails when bootstrap is disabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.enabled must be true"))
    }

    @Test
    fun `build fails when preset is unsupported`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("unknown-preset")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("unsupported bootstrap preset"))
    }

    @Test
    fun `build fails when base package is missing`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            projectName.set("only-danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.basePackage is required"))
    }

    @Test
    fun `build fails when one bootstrap module name is missing`() {
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
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.modules.adapterModuleName is required"))
    }

    @Test
    fun `build falls back to default bootstrap template preset when blank`() {
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
                preset.set("   ")
            }
        }

        val config = Cap4kBootstrapConfigFactory().build(project, extension)

        assertEquals("ddd-default-bootstrap", config.templates.preset)
    }

    @Test
    fun `build falls back to fail conflict policy when blank`() {
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
            conflictPolicy.set(" ")
        }

        val config = Cap4kBootstrapConfigFactory().build(project, extension)

        assertEquals(ConflictPolicy.FAIL, config.conflictPolicy)
    }
}
