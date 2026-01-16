package com.only4.cap4k.plugin.codegen.imports

/**
 * QueryHandler 生成器的 Import 管理器
 */
class QueryHandlerImportManager(private val queryType: QueryType = QueryType.SINGLE) : BaseImportManager() {

    enum class QueryType {
        SINGLE,  // 单个查询，使用 Query
        LIST,    // 列表查询，使用 ListQuery
        PAGE     // 分页查询，使用 PageQuery
    }

    override fun addBaseImports() {
        requiredImports.add("org.springframework.stereotype.Service")

        when (queryType) {
            QueryType.SINGLE -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.query.Query")
            }
            QueryType.LIST -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.query.ListQuery")
            }
            QueryType.PAGE -> {
                requiredImports.add("com.only4.cap4k.ddd.core.application.query.PageQuery")
                requiredImports.add("com.only4.cap4k.ddd.core.share.PageData")
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
