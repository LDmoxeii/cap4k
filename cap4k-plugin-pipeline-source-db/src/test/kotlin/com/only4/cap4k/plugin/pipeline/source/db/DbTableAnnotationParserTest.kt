package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbTableAnnotationParserTest {

    @Test
    fun `parser extracts provider specific table controls from comment`() {
        val metadata = DbTableAnnotationParser.parse(
            "@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;"
        )

        assertEquals(true, metadata.aggregateRoot)
        assertEquals(true, metadata.dynamicInsert)
        assertEquals(true, metadata.dynamicUpdate)
    }

    @Test
    fun `parser extracts table ignore marker from comment`() {
        val metadata = DbTableAnnotationParser.parse("Framework table @I;")

        assertEquals(true, metadata.ignored)
        assertEquals("Framework table", metadata.cleanedComment)
    }

    @Test
    fun `parser rejects valued table ignore marker`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@I=true;")
        }

        assertEquals("invalid @Ignore/@I annotation: explicit values are not supported.", error.message)
    }

    @Test
    fun `parser rejects unsupported table annotation generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post @CustomMarker;")
        }

        assertEquals(
            "unsupported table annotation @CustomMarker. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects value object table annotations through generic unsupported annotation path`() {
        val shortError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@VO;")
        }
        val longError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@ValueObject;")
        }

        assertEquals(
            "unsupported table annotation @VO. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            shortError.message,
        )
        assertEquals(
            "unsupported table annotation @ValueObject. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            longError.message,
        )
    }

    @Test
    fun `parser extracts table parent annotation`() {
        val metadata = DbTableAnnotationParser.parse("@Parent=video_post;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals("", metadata.cleanedComment)
    }

    @Test
    fun `parser extracts short table parent alias with explicit aggregate root false`() {
        val metadata = DbTableAnnotationParser.parse("@P=video_post;@Root=false;")

        assertEquals("video_post", metadata.parentTable)
        assertEquals(false, metadata.aggregateRoot)
        assertEquals("", metadata.cleanedComment)
    }

    @Test
    fun `parser rejects conflicting aggregate root aliases`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@AggregateRoot=true;@R=false;")
        }

        assertEquals("conflicting @AggregateRoot/@Root/@R annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects blank parent value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent=;")
        }

        assertEquals("blank @Parent/@P value is not allowed.", error.message)
    }

    @Test
    fun `parser rejects valueless parent annotation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent;")
        }

        assertEquals("missing value for @Parent/@P annotation.", error.message)
    }

    @Test
    fun `parser rejects parent combined with explicit aggregate root true`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@Parent=video_post;@AggregateRoot=true;")
        }

        assertEquals("conflicting table relation annotations: @Parent/@P cannot be combined with @AggregateRoot=true.", error.message)
    }

    @Test
    fun `parser rejects malformed aggregate root boolean`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@AggregateRoot=maybe;")
        }

        assertEquals("invalid @AggregateRoot/@Root/@R boolean value: maybe", error.message)
    }

    @Test
    fun `parser rejects malformed dynamic insert value`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=maybe;")
        }

        assertEquals("invalid @DynamicInsert value: maybe", error.message)
    }

    @Test
    fun `parser rejects non strict provider boolean casing`() {
        val insertError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=TRUE;")
        }
        val updateError = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicUpdate=FALSE;")
        }

        assertEquals("invalid @DynamicInsert value: TRUE", insertError.message)
        assertEquals("invalid @DynamicUpdate value: FALSE", updateError.message)
    }

    @Test
    fun `parser rejects conflicting duplicate dynamic insert annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicInsert=true;@DynamicInsert=false;")
        }

        assertEquals("conflicting @DynamicInsert annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects conflicting duplicate dynamic update annotations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@DynamicUpdate=true;@DynamicUpdate=false;")
        }

        assertEquals("conflicting @DynamicUpdate annotations on the same table comment.", error.message)
    }

    @Test
    fun `parser rejects unsupported table soft delete marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("@SoftDeleteColumn=deleted;")
        }

        assertEquals(
            "unsupported table annotation @SoftDeleteColumn. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects unsupported table id generator marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IdGenerator. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }

    @Test
    fun `parser rejects unsupported table id generator alias marker generically`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbTableAnnotationParser.parse("Video post root @AggregateRoot=true;@IG=snowflakeIdGenerator;")
        }

        assertEquals(
            "unsupported table annotation @IG. Supported table annotations: @Parent/@P, @AggregateRoot/@Root/@R, @Ignore/@I, @DynamicInsert, @DynamicUpdate.",
            error.message,
        )
    }
}
