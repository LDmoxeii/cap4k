package buildsrc.convention

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CentralPublishTaskPolicyTest {

    @Test
    fun `detects Central portal publish tasks by task name`() {
        assertTrue(
            CentralPublishTaskPolicy.isCentralPortalPublishTask(
                "publishPluginMavenPublicationToCentralPortalRepository"
            )
        )
        assertTrue(
            CentralPublishTaskPolicy.isCentralPortalPublishTask(
                "publishMavenPublicationToCentralPortalRepository"
            )
        )
        assertFalse(
            CentralPublishTaskPolicy.isCentralPortalPublishTask(
                "publishMavenPublicationToMavenLocal"
            )
        )
    }

    @Test
    fun `detects plugin marker Central portal publish tasks by task name`() {
        assertTrue(
            CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(
                "publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository"
            )
        )
        assertFalse(
            CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(
                "publishMavenPublicationToCentralPortalRepository"
            )
        )
    }

    @Test
    fun `allows only selected plugin marker Central portal publish tasks`() {
        assertTrue(
            CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
                "publishCap4kPipelinePluginMarkerMavenPublicationToCentralPortalRepository"
            )
        )
        assertTrue(
            CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
                "publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository"
            )
        )
        assertFalse(
            CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
                "publishCap4kPluginPluginMarkerMavenPublicationToCentralPortalRepository"
            )
        )
        assertFalse(
            CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
                "publishMavenPublicationToCentralPortalRepository"
            )
        )
    }
}
