package buildsrc.convention

object CentralPublishTaskPolicy {
    private val allowedPluginMarkerCentralPortalPublishTasks = setOf(
        "publishCap4kPipelinePluginMarkerMavenPublicationToCentralPortalRepository",
        "publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository",
    )

    fun isCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("ToCentralPortalRepository")

    fun isPluginMarkerCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("PluginMarkerMavenPublicationToCentralPortalRepository")

    fun isAllowedPluginMarkerCentralPortalPublishTask(taskName: String): Boolean =
        taskName in allowedPluginMarkerCentralPortalPublishTasks
}
