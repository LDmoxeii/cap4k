package com.only4.cap4k.codegen.context.design.builders

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.design.MutableDesignContext
import com.only4.cap4k.codegen.context.design.models.DesignElement
import java.io.File
import java.nio.charset.Charset

class DesignContextBuilder : ContextBuilder<MutableDesignContext> {

    override val order: Int = 10

    override fun build(context: MutableDesignContext) {
        val designFiles = context.baseMap["designFiles"] as? Set<File> ?: emptySet()


        if (designFiles.isEmpty()) {
            return
        }

        designFiles.forEach { file ->
            loadDesignFile(file, context)
        }
    }

    private fun loadDesignFile(file: File, context: MutableDesignContext) {
        val encoding = context.getString("designEncoding", "UTF-8")
        val content = file.readText(Charset.forName(encoding))
        val jsonArray = JsonParser.parseString(content).asJsonArray

        parseJsonDesignArray(jsonArray, context)
    }

    private fun parseJsonDesignArray(jsonArray: JsonArray, context: MutableDesignContext) {
        // 定义支持的设计类型别名映射（基于条目内的 tag 字段）
        val typeAliasMap = mapOf(
            // command
            "cmd" to "cmd",
            "command" to "cmd",
            "commands" to "cmd",

            // query
            "qry" to "qry",
            "query" to "qry",
            "queries" to "qry",

            // saga
            "saga" to "saga",
            "sagas" to "saga",

            // client
            "cli" to "cli",
            "client" to "cli",
            "clients" to "cli",

            // integration event
            "ie" to "ie",
            "integration_event" to "ie",
            "integration_events" to "ie",

            // domain event
            "de" to "de",
            "domain_event" to "de",
            "domain_events" to "de",

            // domain service
            "svc" to "svc",
            "service" to "svc",
            "services" to "svc",
            "domain_service" to "svc",
            "domain_services" to "svc"
        )

        jsonArray.forEach { item ->
            val obj = (item as? JsonObject) ?: item.asJsonObject
            val rawTag = obj.get("tag")?.asString?.lowercase() ?: return@forEach
            val normalizedType = typeAliasMap[rawTag] ?: rawTag

            val element = parseDesignElement(normalizedType, obj)
            context.designElementMap
                .computeIfAbsent(normalizedType) { mutableListOf() }
                .add(element)
        }
    }

    private fun parseDesignElement(type: String, jsonObj: JsonObject): DesignElement {
        val `package` = jsonObj.get("package")?.asString ?: ""
        val name = jsonObj.get("name")?.asString ?: ""
        val desc = jsonObj.get("desc")?.asString ?: ""

        val aggregates = if (jsonObj.has("aggregates") && jsonObj.get("aggregates").isJsonArray) {
            jsonObj.getAsJsonArray("aggregates").map { it.asString }
        } else null

        val metadata = mutableMapOf<String, Any?>()
        jsonObj.entrySet().forEach { (key, value) ->
            when (key) {
                "package", "name", "aggregate", "aggregates", "desc", "tag" -> {}
                "metadata" -> {
                    if (value.isJsonObject) {
                        value.asJsonObject.entrySet().forEach { (metaKey, metaVal) ->
                            metadata[metaKey] = jsonPrimitiveToKotlin(metaVal)
                        }
                    }
                }
                else -> {
                    metadata[key] = jsonPrimitiveToKotlin(value)
                }
            }
        }

        return DesignElement(
            type = type,
            `package` = `package`,
            name = name,
            aggregates = aggregates,
            desc = desc,
            metadata = metadata
        )
    }

    private fun jsonPrimitiveToKotlin(value: com.google.gson.JsonElement): Any? = when {
        value.isJsonNull -> null
        value.isJsonPrimitive -> {
            val p = value.asJsonPrimitive
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asNumber
                p.isString -> p.asString
                else -> p.toString()
            }
        }
        value.isJsonArray -> value.asJsonArray.map { jsonPrimitiveToKotlin(it) }
        value.isJsonObject -> value.asJsonObject.entrySet().associate { it.key to jsonPrimitiveToKotlin(it.value) }
        else -> value.toString()
    }
}
