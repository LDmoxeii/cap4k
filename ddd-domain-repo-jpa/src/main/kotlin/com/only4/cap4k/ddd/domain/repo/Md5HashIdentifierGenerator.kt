package com.only4.cap4k.ddd.domain.repo

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import org.springframework.util.DigestUtils
import org.springframework.util.NumberUtils
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * MD5哈希标识符生成器
 *
 * @author LD_moxeii
 * @date 2025/07/29
 */
class Md5HashIdentifierGenerator : IdentifierGenerator {

    companion object {
        private const val DEFAULT_ID_FIELD_NAME = "id"

        private val instance: Md5HashIdentifierGenerator by lazy { Md5HashIdentifierGenerator() }

        var ID_FIELD_NAME: String = DEFAULT_ID_FIELD_NAME

        @JvmStatic
        fun configure(idFieldName: String) {
            ID_FIELD_NAME = idFieldName
        }

        fun hash(obj: Any, idFieldName: String = ID_FIELD_NAME): Serializable {
            val jsonObject = (JSON.toJSON(obj) as JSONObject).apply {
                removeFieldRecursively(idFieldName)
            }
            val json = jsonObject.toString(SerializerFeature.SortField)
            val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)

            val idFieldType = resolveGenericTypeClass(obj, 0, ValueObject::class.java)
            val hashBytes = DigestUtils.md5Digest(jsonBytes)

            return when (idFieldType) {
                String::class.java -> DigestUtils.md5DigestAsHex(jsonBytes)
                Int::class.java, Integer::class.java -> hashBytes.toInt()
                Long::class.java -> hashBytes.toLong()
                BigInteger::class.java -> BigInteger(hashBytes.toLong().toString())
                BigDecimal::class.java -> hashBytes.toLong().toBigDecimal()
                else -> convertToTargetType(hashBytes, idFieldType)
            }
        }

        private fun convertToTargetType(hashBytes: ByteArray, idFieldType: Class<*>): Serializable {
            return when {
                Number::class.java.isAssignableFrom(idFieldType) ->
                    NumberUtils.convertNumberToTargetClass(
                        hashBytes.toLong(),
                        idFieldType as Class<out Number>
                    )

                else -> hashBytes.toLong()
            }
        }

        private fun JSONObject.removeFieldRecursively(fieldName: String) {
            remove(fieldName)
            values.filterIsInstance<JSONObject>().forEach {
                it.removeFieldRecursively(fieldName)
            }
        }

        private fun ByteArray.toLong(): Long {
            require(isNotEmpty()) { "Byte array cannot be empty" }
            var result = 0L
            val limit = minOf(8, size)
            for (i in 0 until limit) {
                result = result or ((this[i].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }

        private fun ByteArray.toInt(): Int {
            require(isNotEmpty()) { "Byte array cannot be empty" }
            var result = 0
            val limit = minOf(4, size)
            for (i in 0 until limit) {
                result = result or ((this[i].toInt() and 0xFF) shl (8 * i))
            }
            return result
        }
    }

    override fun generate(session: SharedSessionContractImplementor, entity: Any): Serializable {
        if (entity is ValueObject<*>) {
            val hashValue = entity.hash()
            if (hashValue is Serializable) {
                return hashValue
            }
        }

        return hash(entity, ID_FIELD_NAME)
    }
}
