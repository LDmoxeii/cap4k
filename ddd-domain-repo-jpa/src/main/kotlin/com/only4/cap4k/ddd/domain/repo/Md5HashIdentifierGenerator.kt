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

        @Volatile
        private var instance: Md5HashIdentifierGenerator? = null
            get() = field ?: synchronized(this) {
                field ?: Md5HashIdentifierGenerator().also { field = it }
            }

        var ID_FIELD_NAME: String = DEFAULT_ID_FIELD_NAME
            private set

        fun configure(idFieldName: String) {
            ID_FIELD_NAME = idFieldName
        }

        fun hash(obj: Any, idFieldName: String = ID_FIELD_NAME): Serializable {
            val jsonObject = JSON.toJSON(obj) as JSONObject

            jsonObject.removeFieldRecursively(idFieldName)
            val json = jsonObject.toString(SerializerFeature.SortField)

            val idFieldType = resolveGenericTypeClass(obj, 0, ValueObject::class.java)

            val hashBytes = DigestUtils.md5Digest(json.toByteArray(StandardCharsets.UTF_8))

            return when (idFieldType) {
                String::class.java -> DigestUtils.md5DigestAsHex(json.toByteArray(StandardCharsets.UTF_8))
                Int::class.java, Integer::class.java -> bytesToInteger(hashBytes)
                Long::class.java -> bytesToLong(hashBytes)
                BigInteger::class.java -> BigInteger.valueOf(bytesToLong(hashBytes))
                BigDecimal::class.java -> BigDecimal.valueOf(bytesToLong(hashBytes))
                else -> convertToTargetType(hashBytes, idFieldType)
            }
        }

        private fun convertToTargetType(hashBytes: ByteArray, idFieldType: Class<*>): Serializable {
            return if (Number::class.java.isAssignableFrom(idFieldType)) {
                    NumberUtils.convertNumberToTargetClass(bytesToLong(hashBytes), idFieldType as Class<out Number>)
            } else {
                bytesToLong(hashBytes)
            }
        }

        private fun JSONObject.removeFieldRecursively(fieldName: String) {
            remove(fieldName)
            values.filterIsInstance<JSONObject>().forEach {
                it.removeFieldRecursively(fieldName)
            }
        }

        private fun bytesToLong(bytes: ByteArray): Long {
            require(bytes.isNotEmpty()) { "Byte array cannot be empty" }
            var result = 0L
            val limit = minOf(8, bytes.size)
            for (i in 0 until limit) {
                result = result or ((bytes[i].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }

        private fun bytesToInteger(bytes: ByteArray): Int {
            require(bytes.isNotEmpty()) { "Byte array cannot be empty" }
            var result = 0
            val limit = minOf(4, bytes.size)
            for (i in 0 until limit) {
                result = result or ((bytes[i].toInt() and 0xFF) shl (8 * i))
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
