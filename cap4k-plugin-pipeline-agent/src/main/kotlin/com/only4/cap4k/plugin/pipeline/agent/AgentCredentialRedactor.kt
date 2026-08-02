package com.only4.cap4k.plugin.pipeline.agent

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.only4.cap4k.plugin.pipeline.api.AgentOptionSummary

class AgentCredentialRedactor(
    sensitiveKeys: Set<String> = emptySet(),
) {
    private val explicitlySensitiveKeys = sensitiveKeys.mapTo(linkedSetOf(), ::normalizeKey)

    fun optionSummary(
        options: Map<String, *>,
        additionalSensitiveKeys: Set<String> = emptySet(),
    ): AgentOptionSummary {
        val configured = linkedSetOf<String>()
        val sensitive = linkedSetOf<String>()
        val normalizedAdditionalKeys = additionalSensitiveKeys.mapTo(linkedSetOf(), ::normalizeKey)
        options.toSortedMap().forEach { (key, value) ->
            collectOptionPaths(
                path = key,
                value = value,
                configured = configured,
                sensitive = sensitive,
                normalizedAdditionalKeys = normalizedAdditionalKeys,
            )
        }
        return AgentOptionSummary(
            configuredKeys = configured.sorted(),
            sensitiveKeys = sensitive.sorted(),
        )
    }

    fun isSensitiveKey(key: String): Boolean = isSensitiveKey(key, emptySet())

    fun redact(text: String): String {
        var redacted = PRIVATE_KEY_PATTERN.replace(text, CONFIGURED_MARKER)
        redacted = JDBC_URL_PATTERN.replace(redacted, CONFIGURED_MARKER)
        redacted = CONNECTION_URI_PATTERN.replace(redacted, CONFIGURED_MARKER)
        redacted = CONNECTION_STRING_PATTERN.replace(redacted, CONFIGURED_MARKER)
        redacted = URI_USER_INFO_PATTERN.replace(redacted) { match ->
            "${match.groupValues[1]}$CONFIGURED_MARKER@"
        }
        redacted = AUTHORIZATION_PATTERN.replace(redacted) { match ->
            "${match.groupValues[1]}$CONFIGURED_MARKER"
        }
        redacted = CREDENTIAL_ASSIGNMENT_PATTERN.replace(redacted) { match ->
            "${match.groupValues[1]}$CONFIGURED_MARKER"
        }
        return redacted
    }

    internal fun redactJson(element: JsonElement): JsonElement = when {
        element.isJsonNull -> JsonNull.INSTANCE
        element.isJsonArray -> JsonArray().also { result ->
            element.asJsonArray.forEach { value -> result.add(redactJson(value)) }
        }
        element.isJsonObject -> JsonObject().also { result ->
            element.asJsonObject.entrySet().forEach { (key, value) ->
                result.add(
                    key,
                    when {
                        isSensitiveKey(key) -> JsonPrimitive(CONFIGURED_MARKER)
                        value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                            JsonPrimitive(redact(value.asString))
                        else -> redactJson(value)
                    }
                )
            }
        }
        element.asJsonPrimitive.isString -> JsonPrimitive(redact(element.asString))
        else -> element.deepCopy()
    }

    internal fun identityProjection(element: JsonElement, key: String? = null): JsonElement = when {
        key != null && isSensitiveKey(key) -> JsonPrimitive(CONFIGURED_MARKER)
        element.isJsonNull -> JsonNull.INSTANCE
        element.isJsonArray -> JsonArray().also { result ->
            element.asJsonArray.forEach { value -> result.add(identityProjection(value)) }
        }
        element.isJsonObject -> JsonObject().also { result ->
            element.asJsonObject.entrySet()
                .sortedBy(Map.Entry<String, JsonElement>::key)
                .forEach { (childKey, childValue) ->
                    result.add(childKey, identityProjection(childValue, childKey))
                }
        }
        element.asJsonPrimitive.isString -> {
            val value = element.asString
            when {
                containsRawConnection(value) -> JsonPrimitive(CONFIGURED_MARKER)
                else -> JsonPrimitive(redact(value))
            }
        }
        else -> element.deepCopy()
    }

    private fun collectOptionPaths(
        path: String,
        value: Any?,
        configured: MutableSet<String>,
        sensitive: MutableSet<String>,
        normalizedAdditionalKeys: Set<String>,
    ) {
        val leafKey = path.substringAfterLast('.')
        if (
            isSensitiveKey(leafKey, normalizedAdditionalKeys) ||
            normalizeKey(path) in normalizedAdditionalKeys
        ) {
            configured += path
            sensitive += path
            return
        }
        if (value is Map<*, *> && value.isNotEmpty()) {
            value.entries
                .map { (key, childValue) -> key.toString() to childValue }
                .sortedBy(Pair<String, Any?>::first)
                .forEach { (childKey, childValue) ->
                    collectOptionPaths(
                        path = "$path.$childKey",
                        value = childValue,
                        configured = configured,
                        sensitive = sensitive,
                        normalizedAdditionalKeys = normalizedAdditionalKeys,
                    )
                }
            return
        }
        configured += path
        if (value is String && containsRawConnection(value)) {
            sensitive += path
        }
    }

    private fun isSensitiveKey(key: String, additionalSensitiveKeys: Set<String>): Boolean {
        val normalized = normalizeKey(key)
        if (normalized in explicitlySensitiveKeys || normalized in additionalSensitiveKeys) {
            return true
        }
        return SENSITIVE_KEY_TERMS.any { term ->
            normalized == term || normalized.endsWith(term)
        }
    }

    private fun containsRawConnection(value: String): Boolean =
        JDBC_URL_PATTERN.containsMatchIn(value) ||
            CONNECTION_URI_PATTERN.containsMatchIn(value) ||
            CONNECTION_STRING_PATTERN.containsMatchIn(value) ||
            URI_USER_INFO_PATTERN.containsMatchIn(value)

    private fun normalizeKey(key: String): String = key.lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val CONFIGURED_MARKER = "<configured>"

        val SENSITIVE_KEY_TERMS = setOf(
            "password",
            "passwd",
            "pwd",
            "token",
            "secret",
            "credential",
            "credentials",
            "privatekey",
            "clientsecret",
            "clientid",
            "username",
            "accesskeysecret",
            "accesskeyid",
            "accesskey",
            "secretkey",
            "apikey",
            "authorization",
            "auth",
            "authheader",
            "passphrase",
            "cookie",
            "setcookie",
            "sessiontoken",
            "signature",
            "signingkey",
            "encryptionkey",
        )
        val PRIVATE_KEY_PATTERN = Regex(
            "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----[\\s\\S]*?-----END(?: [A-Z0-9]+)? PRIVATE KEY-----",
            RegexOption.IGNORE_CASE,
        )
        val JDBC_URL_PATTERN = Regex("(?i)jdbc:[^\\s\\\"'<>]+")
        val CONNECTION_URI_PATTERN = Regex(
            "(?i)(?:mongodb(?:\\+srv)?|postgres(?:ql)?|mysql|mariadb|redis|rediss|amqp|amqps|" +
                "sqlserver|oracle|r2dbc):\\/\\/[^\\s\\\"'<>]+"
        )
        val CONNECTION_STRING_PATTERN = Regex(
            "(?i)\\b(?:server|data\\s+source|host)\\s*=\\s*[^\\r\\n]+(?:;[^\\r\\n]*)+"
        )
        val URI_USER_INFO_PATTERN = Regex("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+@")
        val AUTHORIZATION_PATTERN = Regex(
            "(?i)(authorization\\s*[:=]\\s*(?:basic|bearer)\\s+)[^\\s,;]+"
        )
        val CREDENTIAL_ASSIGNMENT_PATTERN = Regex(
            "(?i)([\\\"']?(?:password|passwd|pwd|token|secret|credential|credentials|username|user|" +
                "private[_-]?key|passphrase|client[_-]?(?:id|secret)|access[_-]?key" +
                "(?:[_-]?(?:id|secret))?|api[_-]?key|authorization|auth[_-]?header|auth|cookie|" +
                "set[_-]?cookie|session[_-]?token|x[_-]?amz[_-]?signature|" +
                "signing[_-]?key|encryption[_-]?key)[\\\"']?" +
                "\\s*(?::|=|\\bis\\b)\\s*)" +
                "(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;&}\\]]+)",
        )
    }
}
