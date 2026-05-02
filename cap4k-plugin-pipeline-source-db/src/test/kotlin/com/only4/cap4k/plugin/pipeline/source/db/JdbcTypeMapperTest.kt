package com.only4.cap4k.plugin.pipeline.source.db

import java.sql.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JdbcTypeMapperTest {

    @Test
    fun `maps native uuid type names to UUID for vendor specific jdbc types`() {
        assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.OTHER, "uuid"))
        assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.OTHER, "UUID"))
        assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.BINARY, "uuid"))
        assertEquals("UUID", JdbcTypeMapper.toKotlinType(Types.BINARY, "UUID"))
    }

    @Test
    fun `keeps binary and vendor specific non uuid columns as String`() {
        assertEquals("String", JdbcTypeMapper.toKotlinType(Types.OTHER, "jsonb"))
        assertEquals("String", JdbcTypeMapper.toKotlinType(Types.BINARY, "binary"))
    }
}
