package com.only4.cap4k.plugin.pipeline.generator.design

internal object DesignTypeParser {

    fun parse(type: String): DesignTypeModel {
        val input = type.trim()
        require(input.isNotEmpty()) { "type must not be blank" }

        val parser = Parser(input)
        val parsed = parser.parseType()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            parser.failMismatchedAngles()
        }
        return parsed
    }

    private class Parser(
        private val input: String,
    ) {
        private var index = 0

        fun parseType(): DesignTypeModel {
            skipWhitespace()
            val tokenText = parseTokenText()
            skipWhitespace()

            val arguments = if (peek() == '<') {
                index++
                parseArguments()
            } else {
                emptyList()
            }

            skipWhitespace()
            val nullable = if (peek() == '?') {
                index++
                true
            } else {
                false
            }

            return DesignTypeModel(
                tokenText = tokenText,
                nullable = nullable,
                arguments = arguments,
            )
        }

        private fun parseArguments(): List<DesignTypeModel> {
            val arguments = mutableListOf<DesignTypeModel>()
            skipWhitespace()

            if (peek() == '>') {
                failEmptyGenericArgument()
            }

            while (true) {
                skipWhitespace()
                if (peek() == ',' || peek() == '>') {
                    failEmptyGenericArgument()
                }

                arguments += parseType()
                skipWhitespace()

                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                        if (peek() == ',' || peek() == '>') {
                            failEmptyGenericArgument()
                        }
                    }
                    '>' -> {
                        index++
                        return arguments
                    }
                    null -> failMismatchedAngles()
                    else -> failMismatchedAngles()
                }
            }
        }

        private fun parseTokenText(): String {
            val start = index
            while (true) {
                val char = peek() ?: break
                if (char == '<' || char == '>' || char == ',' || char == '?' || char.isWhitespace()) {
                    break
                }
                index++
            }

            require(index > start) {
                "expected type token in type: $input"
            }

            return input.substring(start, index)
        }

        fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        fun isAtEnd(): Boolean = index >= input.length

        private fun peek(): Char? = input.getOrNull(index)

        fun failMismatchedAngles(): Nothing {
            throw IllegalArgumentException("mismatched angle brackets in type: $input")
        }

        private fun failEmptyGenericArgument(): Nothing {
            throw IllegalArgumentException("empty generic argument in type: $input")
        }
    }
}
