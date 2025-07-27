import java.lang.reflect.Constructor

data class TestClass(
    val message: String = "",
    val timestamp: Long = 0L
)

fun main() {
    val clazz = TestClass::class.java

    println("所有构造器:")
    clazz.constructors.forEach { constructor ->
        println("  参数数量: ${constructor.parameterCount}")
        println("  参数类型: ${constructor.parameterTypes.map { it.simpleName }}")
        println("  是否public: ${java.lang.reflect.Modifier.isPublic(constructor.modifiers)}")
        println()
    }

    // 尝试无参构造器
    try {
        val noArgConstructor = clazz.getDeclaredConstructor()
        println("找到无参构造器: $noArgConstructor")
        val instance = noArgConstructor.newInstance()
        println("创建实例成功: $instance")
    } catch (e: Exception) {
        println("无参构造器失败: ${e.message}")
    }
}
