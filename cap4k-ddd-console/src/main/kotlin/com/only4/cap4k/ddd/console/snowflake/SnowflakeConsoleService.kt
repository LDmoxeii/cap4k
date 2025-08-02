package com.only4.cap4k.ddd.console.snowflake

import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import jakarta.annotation.PostConstruct
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class SnowflakeConsoleService(
    private val jdbcTemplate: JdbcTemplate
) {
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @PostConstruct
    fun init() {
        namedParameterJdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)
    }

    data class WorkerIdInfo(
        var datacenterId: Int = 0,
        var workerId: Int = 0,
        var free: Boolean = false,
        var dispatchTo: String? = null,
        var dispatchAt: String? = null,
        var expireAt: String? = null
    )

    class SearchParam : PageParam() {
        var free: Boolean? = null
        var dispatchTo: String? = null
    }

    private companion object {
        const val SQL_SELECT = """
            SELECT 
                datacenter_id as datacenterId, 
                worker_id as workerId, 
                expire_at<=now() as free, 
                dispatch_to as dispatchTo, 
                dispatch_at as dispatchAt, 
                expire_at as expireAt 
            FROM __worker_id 
            WHERE %s 
            ORDER BY id 
            LIMIT %d,%d
        """
    }

    fun search(param: SearchParam): PageData<WorkerIdInfo> {
        val params = mutableMapOf<String, Any?>()
        val where = buildString {
            append("true")

            param.free?.let { freeStatus ->
                append(if (freeStatus) " and expire_at <= now()" else " and expire_at > now()")
            }

            param.dispatchTo?.takeIf { it.isNotEmpty() }?.let {
                append(" and dispatch_to like concat('%', :dispatchTo, '%')")
                params["dispatchTo"] = it
            }
        }

        val count = namedParameterJdbcTemplate.queryForObject(
            "SELECT count(*) FROM __worker_id where $where",
            params,
            Long::class.java
        ) ?: 0L

        val list = if (count > 0) {
            namedParameterJdbcTemplate.query(
                SQL_SELECT.format(
                    where,
                    param.pageSize * (param.pageNum - 1),
                    param.pageSize
                ),
                params,
                BeanPropertyRowMapper(WorkerIdInfo::class.java)
            )
        } else {
            emptyList()
        }

        return PageData.create(param, count, list)
    }
}
