package com.only4.cap4k.codegen.imports

/**
 * Query 生成器的 Import 管理器
 */
class QueryImportManager(private val queryType: QueryType = QueryType.SINGLE) : BaseImportManager() {

    enum class QueryType {
        SINGLE,  // 单个查询，使用 RequestParam
        LIST,    // 列表查询，使用 ListQueryParam
        PAGE     // 分页查询，使用 PageQueryParam
    }

    override fun addBaseImports() {
        when (queryType) {
            QueryType.SINGLE -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.RequestParam")
            }
            QueryType.LIST -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.query.ListQueryParam")
            }
            QueryType.PAGE -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.query.PageQueryParam")
            }
        }
    }

    companion object {
        /**
         * 根据设计名称推断查询类型
         */
        fun inferQueryType(designName: String): QueryType {
            val lowerName = designName.lowercase()
            return when {
                lowerName.contains("page") -> QueryType.PAGE
                lowerName.contains("list") -> QueryType.LIST
                else -> QueryType.SINGLE
            }
        }
    }
}
