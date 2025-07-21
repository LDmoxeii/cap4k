package com.only4.cap4k.ddd.core.share.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import java.util.concurrent.ConcurrentHashMap

class TextUtilsTest {

    // 创建一个简单的Environment实现，用于测试
    class TestEnvironment : Environment {
        private val properties = mutableMapOf<String, String>()

        fun addProperty(key: String, value: String) {
            properties[key] = value
        }

        override fun getProperty(key: String): String? = properties[key]
        override fun getProperty(key: String, defaultValue: String): String = properties[key] ?: defaultValue
        override fun getRequiredProperty(key: String): String =
            properties[key] ?: throw IllegalArgumentException("Missing property: $key")

        override fun containsProperty(key: String): Boolean = properties.containsKey(key)
        override fun resolvePlaceholders(text: String): String {
            var result = text
            // 简单实现，只处理${property}格式的占位符
            val regex = Regex("\\$\\{([^}]+)}")
            val matches = regex.findAll(text)

            for (match in matches) {
                val placeholder = match.value
                val propertyName = match.groupValues[1]
                val propertyValue = getProperty(propertyName) ?: ""
                result = result.replace(placeholder, propertyValue)
            }

            return result
        }

        override fun resolveRequiredPlaceholders(text: String): String {
            val result = resolvePlaceholders(text)
            if (result.contains("\${")) {
                throw IllegalArgumentException("Could not resolve placeholder in: $text")
            }
            return result
        }

        // 其他未使用的方法，返回空或默认值
        override fun <T : Any> getProperty(key: String, targetType: Class<T>): T? = null
        override fun <T : Any> getProperty(key: String, targetType: Class<T>, defaultValue: T): T = defaultValue
        override fun <T : Any> getRequiredProperty(key: String, targetType: Class<T>): T =
            throw UnsupportedOperationException("Not implemented")

        override fun getActiveProfiles(): Array<String> = emptyArray()
        override fun getDefaultProfiles(): Array<String> = emptyArray()
        override fun acceptsProfiles(vararg profiles: String): Boolean = false
        override fun acceptsProfiles(profiles: org.springframework.core.env.Profiles): Boolean = false
    }

    private lateinit var environment: TestEnvironment

    @BeforeEach
    fun setUp() {
        // 清除缓存
        val cacheField = Class.forName("com.only4.cap4k.ddd.core.share.misc.TextUtils")
            .getDeclaredField("resolvePlaceholderCache")
        cacheField.isAccessible = true
        val cache = cacheField.get(null) as ConcurrentHashMap<*, *>
        cache.clear()

        // 创建测试环境
        environment = TestEnvironment()
        environment.addProperty("app.name", "test-app")
        environment.addProperty("app.version", "1.0.0")
        environment.addProperty("nested.property", "\${app.name}-\${app.version}")
    }

    @Test
    fun `resolvePlaceholderWithCache should resolve placeholders`() {
        // 测试基本占位符解析
        val text = "Application: \${app.name}, Version: \${app.version}"
        val expected = "Application: test-app, Version: 1.0.0"

        val result = resolvePlaceholderWithCache(text, environment)

        assertEquals(expected, result)
    }

    @Test
    fun `resolvePlaceholderWithCache should cache resolved values`() {
        // 第一次调用
        val text = "Application: \${app.name}"
        val result1 = resolvePlaceholderWithCache(text, environment)

        // 修改环境属性
        environment.addProperty("app.name", "modified-app")

        // 第二次调用，应该返回缓存的结果
        val result2 = resolvePlaceholderWithCache(text, environment)

        assertEquals("Application: test-app", result1)
        assertEquals(result1, result2) // 结果应该被缓存，即使环境变量已更改
    }

    @Test
    fun `randomString should generate string with specified length`() {
        // 测试不同长度的随机字符串
        val lengths = listOf(5, 10, 20, 50)

        for (length in lengths) {
            val result = randomString(length, true, true)
            assertEquals(length, result.length, "生成的随机字符串长度应该是 $length")
        }
    }

    @Test
    fun `randomString should generate digits only when hasDigital is true and hasLetter is false`() {
        // 测试只生成数字
        val result = randomString(100, true, false)

        // 检查是否只包含数字
        assertTrue(result.all { it.isDigit() }, "生成的字符串应该只包含数字")
    }

    @Test
    fun `randomString should generate lowercase letters only when hasDigital is false and hasLetter is true and mixLetterCase is false`() {
        // 测试只生成小写字母
        val result = randomString(100, false, true, false)

        // 检查是否只包含小写字母
        assertTrue(result.all { it.isLowerCase() && it.isLetter() }, "生成的字符串应该只包含小写字母")
    }

    @Test
    fun `randomString should generate mixed case letters when hasDigital is false and hasLetter is true and mixLetterCase is true`() {
        // 测试生成大小写混合的字母
        val result = randomString(1000, false, true, true)

        // 检查是否只包含字母且同时包含大写和小写
        assertTrue(result.all { it.isLetter() }, "生成的字符串应该只包含字母")
        assertTrue(result.any { it.isUpperCase() }, "生成的字符串应该包含大写字母")
        assertTrue(result.any { it.isLowerCase() }, "生成的字符串应该包含小写字母")
    }

    @Test
    fun `randomString should generate alphanumeric characters when hasDigital and hasLetter are true`() {
        // 测试生成数字和字母的组合
        val result = randomString(100, true, true)

        // 检查是否只包含字母和数字
        assertTrue(result.all { it.isLetterOrDigit() }, "生成的字符串应该只包含字母和数字")
    }

    @Test
    fun `randomString should include characters from external dictionary`() {
        // 测试使用外部字典
        val externalDictionary = arrayOf('!', '@', '#', '$', '%')
        val result = randomString(1000, false, false, false, externalDictionary)

        // 检查是否只包含外部字典中的字符
        assertTrue(result.all { externalDictionary.contains(it) }, "生成的字符串应该只包含外部字典中的字符")
    }

    @Test
    fun `randomString should generate different strings on multiple calls`() {
        // 测试生成的随机字符串具有随机性
        val results = mutableSetOf<String>()

        // 生成100个随机字符串
        repeat(100) {
            results.add(randomString(10, true, true))
        }

        // 检查生成的字符串是否具有足够的随机性
        assertTrue(results.size > 90, "生成的100个随机字符串中至少应该有90个不同的字符串")
    }

    @Test
    fun `randomString should handle zero length`() {
        // 测试长度为零的情况
        val result = randomString(0, true, true)
        assertEquals("", result, "长度为零的随机字符串应该是空字符串")
    }

    @Test
    fun `randomString should handle all options disabled with external dictionary`() {
        // 测试所有选项禁用但使用外部字典的情况
        val externalDictionary = arrayOf('*', '&')
        val result = randomString(20, false, false, false, externalDictionary)

        // 检查是否只包含外部字典中的字符
        assertTrue(result.all { externalDictionary.contains(it) }, "生成的字符串应该只包含外部字典中的字符")
    }
}
