import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.parser.Feature

data class UserEvent(
    val userId: String = "",
    val username: String = "",
    val email: String = ""
)

fun main() {
    // 创建原始对象
    val original = UserEvent("user123", "john", "john@test.com")
    println("原始对象: $original")

    // 序列化为JSON
    val json = JSON.toJSONString(original)
    println("JSON字符串: $json")

    // 尝试反序列化
    try {
        val deserialized = JSON.parseObject(json, UserEvent::class.java, Feature.SupportNonPublicField)
        println("反序列化成功: $deserialized")
        println("类型: ${deserialized?.javaClass}")
    } catch (e: Exception) {
        println("反序列化失败: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace()

        // 尝试反序列化为JSONObject
        try {
            val jsonObj = JSON.parseObject(json)
            println("作为JSONObject成功: $jsonObj")
        } catch (e2: Exception) {
            println("JSONObject也失败: ${e2.message}")
        }
    }
}
