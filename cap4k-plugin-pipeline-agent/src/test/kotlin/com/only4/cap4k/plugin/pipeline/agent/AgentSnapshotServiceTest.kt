package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityObservation
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeOwnership
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeProviderFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentValidationStatus
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityProvenance
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSnapshotServiceTest {
    private val service = AgentSnapshotService()

    @Test
    fun `supported catalog is projected only from supplied descriptors`() {
        val descriptor = descriptor(
            capabilityId = "pipeline.generator.sample",
            providerId = "sample",
            tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
        )

        val snapshot = service.assemble(
            request(
                capabilityDescriptors = listOf(descriptor),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = descriptor.capabilityId,
                        providerId = "sample",
                        configured = true,
                        validation = AgentValidationStatus.VERIFIED,
                    )
                ),
            )
        )

        val supported = snapshot.capabilities.supported.single()
        assertEquals(descriptor.capabilityId, supported.capabilityId)
        assertEquals(descriptor.providerId, supported.providerId)
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), supported.tasks)
        assertEquals(AgentCapabilityStatus.READY, snapshot.capabilities.effective.single().status)
        assertEquals(AgentSnapshotStatus.OK, snapshot.capabilities.status)
    }

    @Test
    fun `identity only descriptor and failed validation aggregate section status`() {
        val identityOnly = descriptor(
            capabilityId = "pipeline.source.extension",
            providerId = "extension",
            metadataLevel = PipelineCapabilityMetadataLevel.IDENTITY_ONLY,
        )
        val partial = service.assemble(request(capabilityDescriptors = listOf(identityOnly)))
        assertEquals(AgentSnapshotStatus.PARTIAL, partial.capabilities.status)

        val invalid = service.assemble(
            request(
                capabilityDescriptors = listOf(identityOnly),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = identityOnly.capabilityId,
                        providerId = "extension",
                        configured = true,
                        validation = AgentValidationStatus.FAILED,
                        diagnosticIds = listOf("source.invalid"),
                    )
                ),
                diagnostics = listOf(
                    AgentDiagnostic(
                        id = "source.invalid",
                        level = AgentDiagnosticLevel.ERROR,
                        stage = "collect",
                        message = "source invalid",
                    )
                ),
            )
        )
        assertEquals(AgentSnapshotStatus.INVALID, invalid.capabilities.status)
        assertEquals(AgentCapabilityStatus.BLOCKED, invalid.capabilities.effective.single().status)
        assertEquals(AgentSnapshotStatus.INVALID, invalid.diagnostics.status)
    }

    @Test
    fun `unknown observations and duplicate capability identities fail fast`() {
        val descriptor = descriptor("pipeline.generator.sample", "sample")
        assertThrows(IllegalArgumentException::class.java) {
            service.assemble(
                request(
                    capabilityDescriptors = listOf(descriptor),
                    observations = listOf(
                        AgentCapabilityObservation(
                            capabilityId = "pipeline.generator.unknown",
                            providerId = "unknown",
                            configured = true,
                        )
                    ),
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.assemble(request(capabilityDescriptors = listOf(descriptor, descriptor)))
        }
    }

    @Test
    fun `runtime fact duplicates produce deterministic diagnostics and invalidate the snapshot`() {
        val capability = AgentRuntimeCapabilityFact(
            capabilityId = "runtime.test",
            displayName = "Test",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
        )
        val provider = AgentRuntimeProviderFact(
            providerId = "provider.test",
            capabilityId = capability.capabilityId,
            displayName = "Test Provider",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
        )
        val runtime = AgentRuntimeSection(
            status = AgentSnapshotStatus.OK,
            capabilities = listOf(capability, capability.copy(capabilityId = " RUNTIME.TEST ")),
            providers = listOf(provider, provider.copy(providerId = " PROVIDER.TEST ")),
        )

        val first = service.assemble(request(capabilityDescriptors = emptyList(), runtime = runtime))
        val reordered = service.assemble(
            request(
                capabilityDescriptors = emptyList(),
                runtime = runtime.copy(
                    capabilities = runtime.capabilities.reversed(),
                    providers = runtime.providers.reversed(),
                ),
            )
        )

        assertEquals(AgentSnapshotStatus.INVALID, first.runtime.status)
        assertEquals(AgentSnapshotStatus.INVALID, first.diagnostics.status)
        assertEquals(2, first.diagnostics.diagnostics.size)
        assertTrue(first.diagnostics.diagnostics.all { diagnostic -> diagnostic.stage == "runtime-facts" })
        assertEquals(
            first.diagnostics.diagnostics.map { diagnostic -> diagnostic.id },
            reordered.diagnostics.diagnostics.map { diagnostic -> diagnostic.id },
        )
    }

    @Test
    fun `mixed duplicate runtime providers are order independent through encoded snapshot`() {
        val capabilityA = AgentRuntimeCapabilityFact(
            capabilityId = "runtime.a",
            displayName = "Runtime A",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
            providerIds = listOf("provider.shared"),
        )
        val capabilityB = AgentRuntimeCapabilityFact(
            capabilityId = "runtime.b",
            displayName = "Runtime B",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
            providerIds = listOf("provider.shared"),
        )
        val providerA = AgentRuntimeProviderFact(
            providerId = "provider.shared",
            capabilityId = capabilityA.capabilityId,
            displayName = "Provider A",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
        )
        val providerB = AgentRuntimeProviderFact(
            providerId = " PROVIDER.SHARED ",
            capabilityId = capabilityB.capabilityId,
            displayName = "Provider B",
            ownership = AgentRuntimeOwnership(contractModule = "test"),
        )
        val runtime = AgentRuntimeSection(
            status = AgentSnapshotStatus.OK,
            reason = "Existing Runtime context.",
            capabilities = listOf(capabilityA, capabilityB),
            providers = listOf(providerA, providerB),
        )

        val first = service.assemble(request(capabilityDescriptors = emptyList(), runtime = runtime))
        val reordered = service.assemble(
            request(
                capabilityDescriptors = emptyList(),
                runtime = runtime.copy(
                    capabilities = runtime.capabilities.reversed(),
                    providers = runtime.providers.reversed(),
                ),
            )
        )

        val expectedReason =
            "The static Runtime fact catalog is invalid. Previous Runtime reason: Existing Runtime context."
        val firstDiagnostics = first.diagnostics.diagnostics
        val reorderedDiagnostics = reordered.diagnostics.diagnostics
        assertEquals(AgentSnapshotStatus.INVALID, first.runtime.status)
        assertEquals(AgentSnapshotStatus.INVALID, reordered.runtime.status)
        assertEquals(expectedReason, first.runtime.reason)
        assertEquals(expectedReason, reordered.runtime.reason)
        assertEquals(AgentSnapshotStatus.INVALID, first.diagnostics.status)
        assertEquals(AgentSnapshotStatus.INVALID, reordered.diagnostics.status)
        assertEquals(3, firstDiagnostics.size)
        assertEquals(
            firstDiagnostics.map { diagnostic -> diagnostic.id to diagnostic.message },
            reorderedDiagnostics.map { diagnostic -> diagnostic.id to diagnostic.message },
        )
        assertEquals(
            mapOf(
                "Multiple Runtime provider facts declared one normalized identity." to 1,
                "A Runtime capability fact references an absent or mismatched provider identity." to 2,
            ),
            firstDiagnostics.groupingBy(AgentDiagnostic::message).eachCount(),
        )

        val codec = AgentSnapshotCodec()
        val firstEncoded = codec.encode(cap4kVersion = "test", sections = first)
        val reorderedEncoded = codec.encode(cap4kVersion = "test", sections = reordered)
        listOf("runtime.json", "diagnostics.json").forEach { path ->
            assertArrayEquals(
                firstEncoded.sectionJsonByPath.getValue(path).toByteArray(UTF_8),
                reorderedEncoded.sectionJsonByPath.getValue(path).toByteArray(UTF_8),
                "$path must not depend on fact input order",
            )
        }
        assertArrayEquals(
            firstEncoded.manifestJson.toByteArray(UTF_8),
            reorderedEncoded.manifestJson.toByteArray(UTF_8),
            "manifest.json must not depend on fact input order",
        )
        assertEquals(firstEncoded.manifest.snapshotId, reorderedEncoded.manifest.snapshotId)
        assertEquals(firstEncoded.manifest.diagnosticCounts, reorderedEncoded.manifest.diagnosticCounts)
        assertEquals(3, firstEncoded.manifest.diagnosticCounts.error)
    }

    @Test
    fun `retired runtime descriptor identities fail fast`() {
        val retiredIdentities = listOf("console", "locker", "saga", "snowflake")

        retiredIdentities.forEach { retiredIdentity ->
            val capabilityFailure = assertThrows(IllegalArgumentException::class.java) {
                service.assemble(
                    request(
                        capabilityDescriptors = listOf(
                            descriptor("runtime.${retiredIdentity}", "active-provider")
                        )
                    )
                )
            }
            assertTrue(capabilityFailure.message.orEmpty().contains("retired ${retiredIdentity}"))

            val providerFailure = assertThrows(IllegalArgumentException::class.java) {
                service.assemble(
                    request(
                        capabilityDescriptors = listOf(
                            descriptor("pipeline.generator.active", retiredIdentity)
                        )
                    )
                )
            }
            assertTrue(providerFailure.message.orEmpty().contains("retired ${retiredIdentity}"))
        }
    }

    @Test
    fun `retirement matching uses exact identity segments`() {
        val descriptors = listOf(
            descriptor("runtime.console-export", "console-export"),
            descriptor("runtime.snowflake-audit", "snowflake-audit"),
            descriptor("pipeline.generator.lockers", "lockers"),
            descriptor("pipeline.generator.saga-tools", "saga-tools"),
        )

        val snapshot = service.assemble(request(capabilityDescriptors = descriptors))

        assertEquals(
            descriptors.map(PipelineCapabilityDescriptor::capabilityId).sorted(),
            snapshot.capabilities.supported.map { it.capabilityId },
        )
    }

    @Test
    fun `runtime fact retirement keeps capability segment and provider exact matching`() {
        val activeProvider = AgentRuntimeProviderFact(
            providerId = "integration-event-transport.console",
            capabilityId = "runtime.integration-event-transport",
            displayName = "Console-named Transport",
            ownership = AgentRuntimeOwnership(contractModule = "ddd-core"),
        )
        RetiredRuntimeDescriptorPolicy.requireActive(emptyList(), listOf(activeProvider))

        assertThrows(IllegalArgumentException::class.java) {
            RetiredRuntimeDescriptorPolicy.requireActive(
                capabilities = listOf(
                    AgentRuntimeCapabilityFact(
                        capabilityId = "runtime.console",
                        displayName = "Retired",
                        ownership = AgentRuntimeOwnership(contractModule = "test"),
                    )
                ),
                providers = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetiredRuntimeDescriptorPolicy.requireActive(
                capabilities = emptyList(),
                providers = listOf(activeProvider.copy(providerId = "console")),
            )
        }
    }

    @Test
    fun `observations correlate by capability identity when provider ids are shared`() {
        val source = descriptor("pipeline.source.shared", "shared").copy(
            kind = PipelineCapabilityKind.SOURCE,
        )
        val generator = descriptor("pipeline.generator.shared", "shared")

        val snapshot = service.assemble(
            request(
                capabilityDescriptors = listOf(source, generator),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = source.capabilityId,
                        providerId = source.providerId,
                        configured = true,
                        validation = AgentValidationStatus.VERIFIED,
                    ),
                    AgentCapabilityObservation(
                        capabilityId = generator.capabilityId,
                        providerId = generator.providerId,
                        configured = false,
                        applicable = false,
                    ),
                ),
            )
        )

        assertEquals(
            AgentCapabilityStatus.READY,
            snapshot.capabilities.effective.single { it.capabilityId == source.capabilityId }.status,
        )
        assertEquals(
            AgentCapabilityStatus.NOT_APPLICABLE,
            snapshot.capabilities.effective.single { it.capabilityId == generator.capabilityId }.status,
        )
    }

    private fun request(
        capabilityDescriptors: List<PipelineCapabilityDescriptor>,
        observations: List<AgentCapabilityObservation> = emptyList(),
        diagnostics: List<AgentDiagnostic> = emptyList(),
        runtime: AgentRuntimeSection = AgentRuntimeSection(status = AgentSnapshotStatus.OK),
    ) = AgentSnapshotRequest(
        project = AgentProjectSection(
            status = AgentSnapshotStatus.OK,
            project = AgentProjectSummary(name = "demo"),
        ),
        capabilityDescriptors = capabilityDescriptors,
        capabilityObservations = observations,
        inputs = AgentInputsSection(status = AgentSnapshotStatus.OK, inputs = emptyList()),
        ownership = AgentOwnershipSection(status = AgentSnapshotStatus.OK, items = emptyList()),
        runtime = runtime,
        analysis = AgentAnalysisSection(
            status = AgentSnapshotStatus.UNAVAILABLE,
            configured = false,
            reason = "IR analysis is not configured.",
        ),
        diagnostics = diagnostics,
    )

    private fun descriptor(
        capabilityId: String,
        providerId: String,
        tasks: List<String> = emptyList(),
        metadataLevel: PipelineCapabilityMetadataLevel = PipelineCapabilityMetadataLevel.COMPLETE,
    ) = PipelineCapabilityDescriptor(
        capabilityId = capabilityId,
        providerId = providerId,
        displayName = providerId,
        kind = PipelineCapabilityKind.GENERATOR,
        provenance = PipelineCapabilityProvenance.builtIn("test-module"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = tasks,
        metadataLevel = metadataLevel,
    )
}
