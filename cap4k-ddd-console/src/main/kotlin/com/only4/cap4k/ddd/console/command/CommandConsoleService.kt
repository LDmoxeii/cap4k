package com.only4.cap4k.ddd.console.command

import com.only4.cap4k.ddd.core.application.command.CommandManager
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import jakarta.annotation.PostConstruct
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime

class CommandConsoleService(
    private val jdbcTemplate: JdbcTemplate,
    private val commandManager: CommandManager
) {
    private lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @PostConstruct
    fun init() {
        namedParameterJdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)
    }

    data class CommandInfo(
        var id: Long? = null,
        var uuid: String? = null,
        var type: String? = null,
        var service: String? = null,
        var payload: String? = null,
        var payloadType: String? = null,
        var result: String? = null,
        var resultType: String? = null,
        var scheduleAt: String? = null,
        var state: Int = 0,
        var stateName: String? = null,
        var expireAt: String? = null,
        var lastTryTime: String? = null,
        var nextTryTime: String? = null,
        var retryLimit: Int = 0,
        var retryCount: Int = 0,
        var exception: String? = null
    )

    class SearchParam : PageParam() {
        var uuid: String? = null
        var type: String? = null
        var state: IntArray? = null
        var scheduleAt: Array<LocalDateTime>? = null
    }

    private companion object {
        const val SQL_SELECT = """
            SELECT
                id,
                command_uuid as uuid,
                command_type as type,
                svc_name as service,
                param as payload,
                param_type as payloadType,
                result as result,
                result_type as resultType,
                create_at as scheduleAt,
                command_state as state,
                '' as stateName,
                expire_at as expireAt,
                last_try_time as lastTryTime,
                next_try_time as nextTryTime,
                try_times as retryLimit,
                tried_times as retryCount,
                exception as exception
            FROM __command
            WHERE %s
            ORDER BY id desc
            LIMIT %d,%d
        """
    }

    fun search(param: SearchParam): PageData<CommandInfo> {
        val params = mutableMapOf<String, Any?>()
        val where = buildString {
            append("true")

            param.uuid?.takeIf { it.isNotEmpty() }?.let {
                append(" and command_uuid = :uuid")
                params["uuid"] = it
            }

            param.type?.takeIf { it.isNotEmpty() }?.let {
                append(" and command_type = :type")
                params["type"] = it
            }

            param.state?.takeIf { it.isNotEmpty() }?.let { states ->
                append(" and command_state in (")
                states.forEachIndexed { index, state ->
                    append(if (index == 0) ":state$index" else ",:state$index")
                    params["state$index"] = state
                }
                append(")")
            }

            param.scheduleAt?.takeIf { it.isNotEmpty() }?.let { schedule ->
                append(" and create_at > :schedule0")
                params["schedule0"] = schedule[0]
                if (schedule.size > 1) {
                    append(" and create_at < :schedule1")
                    params["schedule1"] = schedule[1]
                }
            }
        }

        val count = namedParameterJdbcTemplate.queryForObject(
            "SELECT count(*) FROM __command where $where",
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
                BeanPropertyRowMapper(CommandInfo::class.java)
            ).also { commandList ->
                commandList.forEach { info ->
                    info.stateName = getStateName(info.state)
                }
            }
        } else {
            emptyList()
        }

        return PageData.create(param, count, list)
    }

    private fun getStateName(state: Int): String = when (state) {
        0 -> "初始"
        1 -> "完成"
        -1 -> "执行中"
        -2 -> "取消"
        -3 -> "超时"
        -4 -> "超限"
        -9 -> "异常"
        else -> "未知"
    }

    fun retry(uuid: String) {
        commandManager.retry(uuid)
    }
}
