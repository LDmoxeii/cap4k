package com.only4.cap4k.plugin.pipeline.agent

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type

internal class AgentStableJson(
    private val redactor: AgentCredentialRedactor,
) {
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .setPrettyPrinting()
        .registerTypeAdapterFactory(LowercaseEnumTypeAdapterFactory)
        .create()

    fun toJson(value: Any): String = encodeTree(redactor.redactJson(gson.toJsonTree(value)))

    fun identityJson(value: Any): String = encodeTree(
        redactor.identityProjection(gson.toJsonTree(value))
    )

    fun <T> fromJson(json: String, type: Class<T>): T = gson.fromJson(json, type)

    fun <T> fromJson(json: String, type: Type): T = gson.fromJson(json, type)

    private fun encodeTree(element: JsonElement): String = gson.toJson(canonicalize(element))

    private fun canonicalize(element: JsonElement): JsonElement = when {
        element.isJsonNull -> JsonNull.INSTANCE
        element.isJsonArray -> JsonArray().also { result ->
            element.asJsonArray.forEach { value -> result.add(canonicalize(value)) }
        }
        element.isJsonObject -> JsonObject().also { result ->
            element.asJsonObject.entrySet()
                .sortedBy(Map.Entry<String, JsonElement>::key)
                .forEach { (key, value) -> result.add(key, canonicalize(value)) }
        }
        else -> element.deepCopy()
    }
}

private object LowercaseEnumTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val rawType = type.rawType
        if (!rawType.isEnum) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val constants = rawType.enumConstants as Array<Enum<*>>
        val byWireName = constants.associateBy { it.name.lowercase() }
        return object : TypeAdapter<T>() {
            override fun write(writer: JsonWriter, value: T?) {
                if (value == null) {
                    writer.nullValue()
                } else {
                    writer.value((value as Enum<*>).name.lowercase())
                }
            }

            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }
                val wireName = reader.nextString().lowercase()
                @Suppress("UNCHECKED_CAST")
                return requireNotNull(byWireName[wireName]) {
                    "unsupported ${rawType.simpleName} value: $wireName"
                } as T
            }
        }.nullSafe()
    }
}
