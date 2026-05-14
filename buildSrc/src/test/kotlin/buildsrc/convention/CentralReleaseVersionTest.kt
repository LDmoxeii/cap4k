package buildsrc.convention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CentralReleaseVersionTest {

    @Test
    fun `resolve uses baseline version when release version is missing`() {
        assertEquals("0.5.0-dev", CentralReleaseVersion.resolve(null))
        assertEquals("0.5.0-dev", CentralReleaseVersion.resolve("   "))
    }

    @Test
    fun `resolve accepts plain release versions`() {
        assertEquals("0.5.0", CentralReleaseVersion.resolve("0.5.0"))
        assertEquals("1.2.3", CentralReleaseVersion.resolve(" 1.2.3 "))
    }

    @Test
    fun `resolve rejects snapshot release versions`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CentralReleaseVersion.resolve("0.5.0-SNAPSHOT")
        }
        assertEquals(
            "Snapshot versions are not allowed for Maven Central release: 0.5.0-SNAPSHOT",
            error.message
        )
    }

    @Test
    fun `resolve rejects malformed release versions`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CentralReleaseVersion.resolve("v0.5.0")
        }
        assertEquals(
            "Release version must come from a v<major>.<minor>.<patch> tag. Got: v0.5.0",
            error.message
        )
    }
}
