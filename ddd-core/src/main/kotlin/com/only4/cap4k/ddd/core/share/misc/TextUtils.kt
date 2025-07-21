@file:JvmName("TextUtils")

package com.only4.cap4k.ddd.core.share.misc

import org.springframework.core.env.Environment
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 文本辅助工具类
 */
private val resolvePlaceholderCache = ConcurrentHashMap<String, String>()

/**
 * 解析环境变量占位符，并缓存结果
 */
fun resolvePlaceholderWithCache(origin: String, environment: Environment): String {
    return resolvePlaceholderCache.computeIfAbsent(origin) { environment.resolvePlaceholders(it) }
}

private val random = Random(System.currentTimeMillis())
private val RANDOM_DICTIONARY = arrayOf(
    '0',
    '1',
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '9',
    'a',
    'b',
    'c',
    'd',
    'e',
    'f',
    'g',
    'h',
    'i',
    'j',
    'k',
    'l',
    'm',
    'n',
    'o',
    'p',
    'q',
    'r',
    's',
    't',
    'u',
    'v',
    'w',
    'x',
    'y',
    'z',
    'A',
    'B',
    'C',
    'D',
    'E',
    'F',
    'G',
    'H',
    'I',
    'J',
    'K',
    'L',
    'M',
    'N',
    'O',
    'P',
    'Q',
    'R',
    'S',
    'T',
    'U',
    'V',
    'W',
    'X',
    'Y',
    'Z'
)

/**
 * 生成随机字符串
 *
 * @param length 长度
 * @param hasDigital 包含数字
 * @param hasLetter 包含字母
 * @param mixLetterCase 混合大小写，不混合则只使用小写字母
 * @param externalDictionary 外部字典
 */
fun randomString(
    length: Int,
    hasDigital: Boolean,
    hasLetter: Boolean,
    mixLetterCase: Boolean = false,
    externalDictionary: Array<Char> = emptyArray()
): String {
    val externalCapacity = externalDictionary.size
    var offset = 0
    var capacity = 0

    if (hasDigital) {
        capacity += 10
    } else {
        offset += 10
    }

    if (hasLetter && mixLetterCase) {
        capacity += 52
    } else if (hasLetter) {
        capacity += 26
    }

    // 确保有可选择的字符
    if (capacity + externalCapacity <= 0) {
        if (externalCapacity > 0) {
            // 如果有外部字典，使用外部字典
            capacity = 0
        } else {
            // 如果没有外部字典，默认使用小写字母
            capacity = 26
            offset = 10
        }
    }

    val stringBuilder = StringBuilder()
    var count = length

    while (count-- > 0) {
        val index = random.nextInt(capacity + externalCapacity)
        if (index >= capacity) {
            stringBuilder.append(externalDictionary[index - capacity])
        } else {
            stringBuilder.append(RANDOM_DICTIONARY[index + offset])
        }
    }

    return stringBuilder.toString()
}
