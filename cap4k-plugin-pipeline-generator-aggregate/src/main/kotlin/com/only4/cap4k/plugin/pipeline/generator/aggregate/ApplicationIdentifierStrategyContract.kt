package com.only4.cap4k.plugin.pipeline.generator.aggregate

internal object ApplicationIdentifierStrategyContract {
    fun requireUuid7(value: String, location: String): String {
        require(value == "uuid7") {
            "unsupported application-side Strong ID strategy: rejected value '$value' at $location; " +
                "supported application-side strategy: uuid7"
        }
        return value
    }

    fun rejectRetiredPolicy(value: String, location: String) {
        val normalized = value.trim()
        if (
            normalized.equals("identifier.snowflake", ignoreCase = true) ||
            normalized.equals("snowflake", ignoreCase = true)
        ) {
            throw IllegalArgumentException(
                "unsupported application-side Strong ID strategy: rejected value '$value' at $location; " +
                    "supported application-side strategy: uuid7",
            )
        }
    }
}
