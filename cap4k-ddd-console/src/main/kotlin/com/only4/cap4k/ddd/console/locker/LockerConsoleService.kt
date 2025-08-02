package com.only4.cap4k.ddd.console.locker

import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import jakarta.annotation.PostConstruct
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class LockerConsoleService(
    private val jdbcTemplate: JdbcTemplate
) {
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @PostConstruct
    fun init() {
        namedParameterJdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)
    }

    data class LockerInfo(
        var name: String? = null,
        var lock: Boolean = false,
        var lockAt: String? = null,
        var unlockAt: String? = null,
        var pwd: String? = null
    )

    class SearchParam : PageParam() {
        var name: String? = null
        var lock: Boolean? = null
    }

    private companion object {
        const val SQL_SELECT = """
            SELECT 
                name, 
                unlock_at>now() as `lock`, 
                lock_at as lockAt, 
                unlock_at as unlockAt, 
                pwd as pwd 
            FROM __locker 
            WHERE %s 
            ORDER BY id 
            LIMIT %d,%d
        """
    }

    fun search(param: SearchParam): PageData<LockerInfo> {
        val params = mutableMapOf<String, Any?>()
        val where = buildString {
            append("true")

            param.name?.takeIf { it.isNotEmpty() }?.let {
                append(" and name like concat('%', :name, '%')")
                params["name"] = it
            }

            param.lock?.let { lockStatus ->
                append(if (lockStatus) " and unlock_at > now()" else " and unlock_at <= now()")
            }
        }

        val count = namedParameterJdbcTemplate.queryForObject(
            "SELECT count(*) FROM __locker where $where",
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
                BeanPropertyRowMapper(LockerInfo::class.java)
            )
        } else {
            emptyList()
        }

        return PageData.create(param, count, list)
    }

    fun unlock(name: String, pwd: String): Boolean {
        return jdbcTemplate.update(
            "UPDATE __locker SET unlock_at=now() WHERE name=? AND pwd=? AND unlock_at>now()",
            name, pwd
        ) > 0
    }
}
