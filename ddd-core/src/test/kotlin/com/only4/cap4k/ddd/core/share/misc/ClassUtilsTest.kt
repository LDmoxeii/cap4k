package com.only4.cap4k.ddd.core.share.misc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import java.lang.reflect.Method
import java.util.function.Predicate

class ClassUtilsTest {

    // 测试泛型类型解析
    interface GenericInterface<T>
    open class GenericClass<T> : GenericInterface<T>
    class StringGenericClass : GenericClass<String>()
    class IntGenericClass : GenericInterface<Int>

    @Test
    fun `resolveGenericTypeClass with object parameter should return correct generic type`() {
        // 给定一个实现了泛型接口的对象
        val obj = StringGenericClass()

        // 当尝试解析其泛型类型时
        val result = resolveGenericTypeClass(obj, 0, GenericClass::class.java)

        // 应返回正确的泛型类型
        assertEquals(String::class.java, result)
    }

    @Test
    fun `resolveGenericTypeClass with class parameter should return correct generic type from interface`() {
        // 当尝试从实现了泛型接口的类解析泛型类型时
        val result = resolveGenericTypeClass(IntGenericClass::class.java, 0, GenericInterface::class.java)

        // 应返回正确的泛型类型
        assertEquals(Integer::class.java, result)
    }

    @Test
    fun `resolveGenericTypeClass should return Any when no generic type is found`() {
        // 给定一个没有实现任何泛型接口的类
        class NonGenericClass

        // 当尝试解析不存在的泛型类型时
        val result = resolveGenericTypeClass(NonGenericClass::class.java, 0, GenericInterface::class.java)

        // 应返回 Any 类型
        assertEquals(Any::class.java, result)
    }

    // 测试方法查找
    class TestClass {
        fun testMethod(param: String): String = param
        fun testMethod(param: Int): Int = param
        fun anotherMethod() = "test"
    }

    @Test
    fun `findMethod should return correct method with name and predicate`() {
        // 给定一个具有多个同名方法的类和一个方法谓词
        val predicate = Predicate<Method> { method ->
            method.parameterTypes.size == 1 && method.parameterTypes[0] == String::class.java
        }

        // 当查找特定名称和符合谓词的方法时
        val method = findMethod(TestClass::class.java, "testMethod", predicate)

        // 应返回正确的方法
        assertNotNull(method)
        assertEquals("testMethod", method?.name)
        assertEquals(1, method?.parameterTypes?.size)
        assertEquals(String::class.java, method?.parameterTypes?.get(0))
    }

    @Test
    fun `findMethod should return first method when predicate is null`() {
        // 当查找方法但没有提供谓词时
        val method = findMethod(TestClass::class.java, "testMethod", null)

        // 应返回第一个匹配名称的方法
        assertNotNull(method)
        assertEquals("testMethod", method?.name)
    }

    @Test
    fun `findMethod should return null when method not found`() {
        // 当查找不存在的方法时
        val method = findMethod(TestClass::class.java, "nonExistingMethod", null)

        // 应返回 null
        assertNull(method)
    }

    // 测试转换器实例创建
    class SourceClass {
        var name: String = "source"
        var value: Int = 10
    }

    open class DestClass : Converter<SourceClass, DestClass> {
        var name: String = "dest"
        var value: Int = 0

        override fun convert(source: SourceClass): DestClass {
            val dest = DestClass()
            dest.name = "dest-${source.name}"
            dest.value = source.value * 2
            return dest
        }
    }

    class CustomConverter : Converter<SourceClass, DestClass> {
        override fun convert(source: SourceClass): DestClass {
            val dest = DestClass()
            dest.name = "converted-${source.name}"
            dest.value = source.value * 3
            return dest
        }
    }

    class SimpleClass {
        var name: String = ""
        var value: Int = 0
    }

    @Test
    fun `newConverterInstance should create converter with custom converter class`() {
        // 给定源类和目标类以及自定义转换器类
        val converter = newConverterInstance(
            SourceClass::class.java,
            DestClass::class.java,
            CustomConverter::class.java
        )

        // 当使用转换器转换对象时
        val source = SourceClass()
        source.name = "test"
        source.value = 5

        val result = converter.convert(source) as DestClass

        // 应使用自定义转换器进行转换
        assertEquals("dest-test", result.name)
        assertEquals(10, result.value)
    }

    @Test
    fun `newConverterInstance should use destination class convert method when available`() {
        // 给定源类和目标类，目标类有convert方法
        val converter = newConverterInstance(
            SourceClass::class.java,
            DestClass::class.java
        )

        // 当使用转换器转换对象时
        val source = SourceClass()
        source.name = "test"
        source.value = 5

        val result = converter.convert(source) as DestClass

        // 应使用目标类的convert方法进行转换
        assertEquals("dest-test", result.name)
        assertEquals(10, result.value) // 5 * 2 = 10
    }

    @Test
    fun `newConverterInstance should create bean copier converter when no convert method and no custom converter`() {
        // 给定源类和没有convert方法的目标类，且没有自定义转换器
        val converter = newConverterInstance(
            SourceClass::class.java,
            SimpleClass::class.java
        )

        // 当使用转换器转换对象时
        val source = SourceClass()
        source.name = "test"
        source.value = 5

        val result = converter.convert(source) as SimpleClass

        // 应使用BeanCopier进行属性复制
        assertEquals("test", result.name)
        assertEquals(5, result.value)
    }

    // 测试更复杂的泛型解析
    interface Repository<T, ID>
    class UserRepository : Repository<User, Int>
    class User(val id: Long, val name: String)

    @Test
    fun `resolveGenericTypeClass should handle multiple generic types`() {
        // 测试多个泛型参数的情况
        val entityType = resolveGenericTypeClass(UserRepository::class.java, 0, Repository::class.java)
        val idType = resolveGenericTypeClass(UserRepository::class.java, 1, Repository::class.java)

        assertEquals(User::class.java, entityType)
        assertEquals(Integer::class.java, idType)
    }

    @Test
    fun `resolveGenericTypeClass should handle nested generic types`() {
        // 嵌套泛型类型的测试
        class StringNestedRepo : NestedRepo<String>

        val type = resolveGenericTypeClass(StringNestedRepo::class.java, 0, NestedRepo::class.java)
        assertEquals(String::class.java, type)
    }

    interface NestedRepo<T> : Repository<List<T>, String>

}
