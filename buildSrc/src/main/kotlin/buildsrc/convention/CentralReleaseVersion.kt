package buildsrc.convention

internal object CentralReleaseVersion {
    const val groupId = "io.github.ldmoxeii"
    const val baselineVersion = "0.5.0-dev"
    const val releaseVersionProperty = "release.version"
    const val releaseVersionEnvironment = "RELEASE_VERSION"

    private val releaseVersionPattern = Regex("""\d+\.\d+\.\d+""")

    fun resolve(releaseVersionInput: String?): String {
        val normalized = releaseVersionInput?.trim().orEmpty()
        return if (normalized.isEmpty()) {
            baselineVersion
        } else {
            validateReleaseVersion(normalized)
        }
    }

    fun isReleaseBuild(releaseVersionInput: String?): Boolean =
        releaseVersionInput?.isNotBlank() == true

    private fun validateReleaseVersion(releaseVersion: String): String {
        val normalized = releaseVersion.trim()
        require(!normalized.endsWith("-SNAPSHOT")) {
            "Snapshot versions are not allowed for Maven Central release: $releaseVersion"
        }
        require(releaseVersionPattern.matches(normalized)) {
            "Release version must come from a v<major>.<minor>.<patch> tag. Got: $releaseVersion"
        }
        return normalized
    }
}
