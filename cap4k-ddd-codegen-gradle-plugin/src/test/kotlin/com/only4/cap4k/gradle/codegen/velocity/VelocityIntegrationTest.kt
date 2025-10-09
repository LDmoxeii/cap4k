package com.only4.cap4k.gradle.codegen.velocity

import com.only4.cap4k.gradle.codegen.velocity.tools.DateUtils
import com.only4.cap4k.gradle.codegen.velocity.tools.StringUtils
import com.only4.cap4k.gradle.codegen.velocity.tools.TypeUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Velocity 集成测试")
class VelocityIntegrationTest {

    @Test
    @DisplayName("测试简单变量替换")
    fun testSimpleVariableSubstitution() {
        // 初始化 Velocity
        VelocityInitializer.initVelocity()

        // 创建上下文
        val context = VelocityContextBuilder()
            .put("name", "User")
            .put("package", "com.example")
            .build()

        // 渲染模板
        val template = "package \${package};\n\npublic class \${name} {}"
        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        // 验证
        assertTrue(result.contains("package com.example;"))
        assertTrue(result.contains("public class User {}"))
    }

    @Test
    @DisplayName("测试 foreach 循环")
    fun testForeachLoop() {
        VelocityInitializer.initVelocity()

        val fields = listOf(
            mapOf("type" to "String", "name" to "id"),
            mapOf("type" to "String", "name" to "name"),
            mapOf("type" to "Integer", "name" to "age")
        )

        val context = VelocityContextBuilder()
            .put("fields", fields)
            .build()

        val template = """
            |#foreach(${'$'}field in ${'$'}fields)
            |    private ${'$'}{field.type} ${'$'}{field.name};
            |#end
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("private String id;"))
        assertTrue(result.contains("private String name;"))
        assertTrue(result.contains("private Integer age;"))
    }

    @Test
    @DisplayName("测试 StringUtils 工具类")
    fun testStringUtilsInTemplate() {
        VelocityInitializer.initVelocity()

        val context = VelocityContextBuilder()
            .put("fieldName", "userName")
            .putTool("StringUtils", StringUtils)
            .build()

        val template = """
            |Capitalize: ${'$'}StringUtils.capitalize(${'$'}fieldName)
            |Snake: ${'$'}StringUtils.camelToSnake(${'$'}fieldName)
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("Capitalize: UserName"))
        assertTrue(result.contains("Snake: user_name"))
    }

    @Test
    @DisplayName("测试 DateUtils 工具类")
    fun testDateUtilsInTemplate() {
        VelocityInitializer.initVelocity()

        val context = VelocityContextBuilder()
            .putTool("DateUtils", DateUtils)
            .build()

        val template = "Generated on: ${'$'}DateUtils.now()"
        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("Generated on:"))
        // 验证日期格式 yyyy-MM-dd
        assertTrue(result.matches(Regex(".*Generated on: \\d{4}-\\d{2}-\\d{2}.*")))
    }

    @Test
    @DisplayName("测试 TypeUtils 工具类")
    fun testTypeUtilsInTemplate() {
        VelocityInitializer.initVelocity()

        val context = VelocityContextBuilder()
            .put("type1", "String")
            .put("type2", "Integer")
            .put("type3", "Date")
            .putTool("TypeUtils", TypeUtils)
            .build()

        val template = """
            |#if(${'$'}TypeUtils.isString(${'$'}type1))
            |type1 is String
            |#end
            |#if(${'$'}TypeUtils.isNumber(${'$'}type2))
            |type2 is Number
            |#end
            |#if(${'$'}TypeUtils.isDate(${'$'}type3))
            |type3 is Date
            |#end
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("type1 is String"))
        assertTrue(result.contains("type2 is Number"))
        assertTrue(result.contains("type3 is Date"))
    }

    @Test
    @DisplayName("测试条件判断")
    fun testConditionalRendering() {
        VelocityInitializer.initVelocity()

        val context = VelocityContextBuilder()
            .put("isEntity", true)
            .put("hasFields", false)
            .build()

        val template = """
            |#if(${'$'}isEntity)
            |@Entity
            |#end
            |public class Test {
            |#if(${'$'}hasFields)
            |    private String field;
            |#else
            |    // No fields
            |#end
            |}
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("@Entity"))
        assertTrue(result.contains("// No fields"))
        assertTrue(!result.contains("private String field;"))
    }

    @Test
    @DisplayName("测试宏定义和使用")
    fun testMacroDefinitionAndUsage() {
        VelocityInitializer.initVelocity()

        val context = VelocityContextBuilder()
            .put("className", "User")
            .build()

        val template = """
            |#macro(header ${'$'}name)
            |/**
            | * ${'$'}name class
            | */
            |#end
            |
            |#header(${'$'}className)
            |public class ${'$'}className {}
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        assertTrue(result.contains("/**"))
        assertTrue(result.contains(" * User class"))
        assertTrue(result.contains(" */"))
        assertTrue(result.contains("public class User {}"))
    }

    @Test
    @DisplayName("测试复杂实体类生成")
    fun testComplexEntityGeneration() {
        VelocityInitializer.initVelocity()

        val fields = listOf(
            mapOf("type" to "String", "name" to "userName", "comment" to "用户名"),
            mapOf("type" to "Integer", "name" to "age", "comment" to "年龄"),
            mapOf("type" to "Date", "name" to "createdAt", "comment" to "创建时间")
        )

        val context = VelocityContextBuilder()
            .put("package", "com.example.domain")
            .put("Entity", "User")
            .put("fields", fields)
            .putTool("StringUtils", StringUtils)
            .putTool("TypeUtils", TypeUtils)
            .build()

        val template = """
            |package ${'$'}{package};
            |
            |import jakarta.persistence.*;
            |
            |@Entity
            |@Table(name = "${'$'}StringUtils.camelToSnake(${'$'}Entity)")
            |public class ${'$'}Entity {
            |    @Id
            |    private String id;
            |
            |#foreach(${'$'}field in ${'$'}fields)
            |    /**
            |     * ${'$'}{field.comment}
            |     */
            |#if(${'$'}TypeUtils.isString(${'$'}field.type))
            |    @Column(name = "${'$'}StringUtils.camelToSnake(${'$'}field.name)")
            |#elseif(${'$'}TypeUtils.isDate(${'$'}field.type))
            |    @Temporal(TemporalType.TIMESTAMP)
            |    @Column(name = "${'$'}StringUtils.camelToSnake(${'$'}field.name)")
            |#else
            |    @Column(name = "${'$'}StringUtils.camelToSnake(${'$'}field.name)")
            |#end
            |    private ${'$'}{field.type} ${'$'}{field.name};
            |
            |#end
            |}
        """.trimMargin()

        val result = VelocityTemplateRenderer.INSTANCE.renderString(template, context, "test")

        // 验证包名
        assertTrue(result.contains("package com.example.domain;"))

        // 验证注解
        assertTrue(result.contains("@Entity"))
        assertTrue(result.contains("@Table(name = \"user\")"))

        // 验证字段
        assertTrue(result.contains("用户名"))
        assertTrue(result.contains("@Column(name = \"user_name\")"))
        assertTrue(result.contains("private String userName;"))

        assertTrue(result.contains("年龄"))
        assertTrue(result.contains("@Column(name = \"age\")"))
        assertTrue(result.contains("private Integer age;"))

        assertTrue(result.contains("创建时间"))
        assertTrue(result.contains("@Temporal(TemporalType.TIMESTAMP)"))
        assertTrue(result.contains("@Column(name = \"created_at\")"))
        assertTrue(result.contains("private Date createdAt;"))
    }
}
