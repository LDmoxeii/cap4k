package com.only4.cap4k.plugin.pipeline.agent

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AgentHashing {
    private const val SHA_256_HEX_LENGTH = 64

    fun sha256(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))

    fun sha256(value: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun sectionSha256(json: String): String = sha256(json)

    fun snapshotSha256(sectionSha256ByPath: Map<String, String>): String {
        require(sectionSha256ByPath.isNotEmpty()) { "agent snapshot must contain at least one section hash" }
        val bytes = ByteArrayOutputStream()
        sectionSha256ByPath.toSortedMap().forEach { (path, hash) ->
            require(path.isNotBlank()) { "agent snapshot section path must not be blank" }
            require(hash.length == SHA_256_HEX_LENGTH && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
                "agent snapshot section hash must be a lowercase SHA-256 value: $path"
            }
            writeLengthPrefixed(bytes, path.toByteArray(StandardCharsets.UTF_8))
            writeLengthPrefixed(bytes, hash.toByteArray(StandardCharsets.US_ASCII))
        }
        return sha256(bytes.toByteArray())
    }

    internal fun stableIdentity(parts: List<Pair<String, ByteArray>>): String {
        val bytes = ByteArrayOutputStream()
        parts.sortedBy(Pair<String, ByteArray>::first).forEach { (name, value) ->
            writeLengthPrefixed(bytes, name.toByteArray(StandardCharsets.UTF_8))
            writeLengthPrefixed(bytes, value)
        }
        return sha256(bytes.toByteArray())
    }

    private fun writeLengthPrefixed(target: ByteArrayOutputStream, value: ByteArray) {
        target.write((value.size ushr 24) and 0xff)
        target.write((value.size ushr 16) and 0xff)
        target.write((value.size ushr 8) and 0xff)
        target.write(value.size and 0xff)
        target.write(value)
    }
}
