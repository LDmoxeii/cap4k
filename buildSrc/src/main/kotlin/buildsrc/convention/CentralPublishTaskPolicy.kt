package buildsrc.convention

object CentralPublishTaskPolicy {

    fun isCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("ToCentralPortalRepository")

    fun isPluginMarkerCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("PluginMarkerMavenPublicationToCentralPortalRepository")
}
