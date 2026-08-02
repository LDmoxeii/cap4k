package com.only4.cap4k.plugin.pipeline.agent

import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class AgentIdentity(
    private val redactor: AgentCredentialRedactor = AgentCredentialRedactor(),
) {
    private val json = AgentStableJson(redactor)

    /**
     * Hashes a redacted canonical projection of adapter configuration. Sensitive
     * fields and raw JDBC URLs contribute only a configured marker, so changing a
     * password or credential cannot change or be inferred from this identity.
     */
    fun configurationIdentity(configuration: Any): String =
        AgentHashing.sha256(json.identityJson(configuration))

    /**
     * Builds one order-independent identity for local inputs without doing file I/O.
     * Text inputs are credential-redacted before hashing. Explicitly sensitive paths
     * contribute only a configured marker.
     */
    fun localInputIdentity(
        inputs: Map<String, ByteArray>,
        sensitivePaths: Set<String> = emptySet(),
    ): String {
        val normalizedSensitivePaths = sensitivePaths.mapTo(linkedSetOf(), ::normalizePath)
        val normalizedInputs = inputs.entries.map { (path, content) ->
            normalizePath(path) to content
        }
        val duplicatePath = normalizedInputs
            .groupingBy(Pair<String, ByteArray>::first)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicatePath == null) { "duplicate normalized local input path: $duplicatePath" }
        return AgentHashing.stableIdentity(
            normalizedInputs.map { (path, content) ->
                val identityContent = when {
                    path in normalizedSensitivePaths || pathLooksSensitive(path) -> CONFIGURED_MARKER_BYTES
                    else -> redactTextContent(content)
                }
                path to identityContent
            }
        )
    }

    fun localTextInputIdentity(
        inputs: Map<String, String>,
        sensitivePaths: Set<String> = emptySet(),
    ): String = localInputIdentity(
        inputs = inputs.mapValues { (_, value) -> value.toByteArray(StandardCharsets.UTF_8) },
        sensitivePaths = sensitivePaths,
    )

    private fun redactTextContent(content: ByteArray): ByteArray {
        val text = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString()
        }.getOrNull() ?: return CONFIGURED_MARKER_BYTES
        val jsonElement = runCatching { JsonParser.parseString(text) }.getOrNull()
        val redactedText = if (jsonElement != null) {
            json.identityJson(jsonElement)
        } else {
            redactor.redact(text)
        }
        return redactedText.toByteArray(StandardCharsets.UTF_8)
    }

    private fun pathLooksSensitive(path: String): Boolean = path.split('/').any { segment ->
        redactor.isSensitiveKey(segment.substringBeforeLast('.'))
    }

    private fun normalizePath(path: String): String {
        val normalized = path.trim().replace('\\', '/').removePrefix("./")
        require(normalized.isNotBlank()) { "local input path must not be blank" }
        require(!normalized.startsWith("/") && !WINDOWS_ABSOLUTE_PATH.matches(normalized)) {
            "local input path must be project-relative: $path"
        }
        require(normalized.split('/').none { it == ".." }) {
            "local input path must not escape the project: $path"
        }
        return normalized
    }

    private companion object {
        val CONFIGURED_MARKER_BYTES = "<configured>".toByteArray(StandardCharsets.UTF_8)
        val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")
    }
}
