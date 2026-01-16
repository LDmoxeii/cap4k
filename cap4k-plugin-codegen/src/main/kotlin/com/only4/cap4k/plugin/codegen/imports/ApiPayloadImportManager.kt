package com.only4.cap4k.plugin.codegen.imports

/**
 * API Payload 导入管理器
 * - 单对象：无需额外导入
 * - 列表：无需额外导入
 * - 分页：需要 PageParam 导入
 */
class ApiPayloadImportManager(private val payloadType: PayloadType = PayloadType.SINGLE) : BaseImportManager() {

    enum class PayloadType {
        SINGLE,  // 单对象 Response
        LIST,    // 列表 Item
        PAGE     // 分页 Item，需要 PageParam
    }

    override fun addBaseImports() {
        when (payloadType) {
            PayloadType.SINGLE -> {
                // no-op
            }
            PayloadType.LIST -> {
                // no-op
            }
            PayloadType.PAGE -> {
                requiredImports.add("com.only4.cap4k.ddd.core.share.PageParam")
            }
        }
    }

    companion object {
        /**
         * 根据名称推断 Payload 类型
         */
        fun inferPayloadType(designName: String): PayloadType {
            val lower = designName.lowercase()
            return when {
                lower.contains("page") -> PayloadType.PAGE
                lower.contains("list") -> PayloadType.LIST
                else -> PayloadType.SINGLE
            }
        }
    }
}

