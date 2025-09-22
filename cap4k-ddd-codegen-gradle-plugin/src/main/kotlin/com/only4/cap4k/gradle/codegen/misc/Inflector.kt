package com.only4.cap4k.gradle.codegen.misc

import java.util.regex.Pattern

/**
 * 单词形态变换工具（单数复数转换）
 * 基于 Java 版本的 Inflector 移植到 Kotlin
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object Inflector {

    /**
     * 规则和替换
     */
    private data class RuleAndReplacement(val rule: String, val replacement: String)

    private val plurals = mutableListOf<RuleAndReplacement>()
    private val singulars = mutableListOf<RuleAndReplacement>()
    private val uncountables = mutableListOf<String>()

    init {
        initialize()
    }

    private fun initialize() {
        // 复数规则
        plural("$", "s")
        plural("s$", "s")
        plural("(ax|test)is$", "$1es")
        plural("(octop|vir)us$", "$1i")
        plural("(alias|status)$", "$1es")
        plural("(bu)s$", "$1es")
        plural("(buffal|tomat)o$", "$1oes")
        plural("([ti])um$", "$1a")
        plural("sis$", "ses")
        plural("(?:([^f])fe|([lr])f)$", "$1$2ves")
        plural("(hive)$", "$1s")
        plural("([^aeiouy]|qu)y$", "$1ies")
        plural("([^aeiouy]|qu)ies$", "$1y")
        plural("(x|ch|ss|sh)$", "$1es")
        plural("(matr|vert|ind)ix|ex$", "$1ices")
        plural("([m|l])ouse$", "$1ice")
        plural("(ox)$", "$1es")
        plural("(quiz)$", "$1zes")

        // 单数规则
        singular("s$", "")
        singular("(n)ews$", "$1ews")
        singular("([ti])a$", "$1um")
        singular("((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$", "$1$2sis")
        singular("(^analy)ses$", "$1sis")
        singular("([^f])ves$", "$1fe")
        singular("(hive)s$", "$1")
        singular("(tive)s$", "$1")
        singular("([lr])ves$", "$1f")
        singular("([^aeiouy]|qu)ies$", "$1y")
        singular("(s)eries$", "$1eries")
        singular("(m)ovies$", "$1ovie")
        singular("(x|ch|ss|sh)es$", "$1")
        singular("([m|l])ice$", "$1ouse")
        singular("(bus)es$", "$1")
        singular("(o)es$", "$1")
        singular("(shoe)s$", "$1")
        singular("(cris|ax|test)es$", "$1is")
        singular("([octop|vir])i$", "$1us")
        singular("(alias|status)es$", "$1")
        singular("^(ox)es", "$1")
        singular("(vert|ind)ices$", "$1ex")
        singular("(matr)ices$", "$1ix")
        singular("(quiz)zes$", "$1")

        // 不规则变化
        irregular("person", "people")
        irregular("man", "men")
        irregular("child", "children")
        irregular("sex", "sexes")
        irregular("move", "moves")

        // 不可数名词
        uncountable("equipment", "information", "rice", "money", "species", "series", "fish", "sheep")
    }

    private fun plural(rule: String, replacement: String) {
        plurals.add(0, RuleAndReplacement(rule, replacement))
    }

    private fun singular(rule: String, replacement: String) {
        singulars.add(0, RuleAndReplacement(rule, replacement))
    }

    private fun irregular(singular: String, plural: String) {
        plural(singular, plural)
        singular(plural, singular)
    }

    private fun uncountable(vararg words: String) {
        uncountables.addAll(words)
    }

    /**
     * 将单词复数化
     *
     * @param word 单词
     * @return 复数形式
     */
    fun pluralize(word: String): String {
        if (uncountables.contains(word.lowercase())) {
            return word
        }
        return replaceWithFirstRule(word, plurals)
    }

    /**
     * 将单词单数化
     *
     * @param word 单词
     * @return 单数形式
     */
    fun singularize(word: String): String {
        if (uncountables.contains(word.lowercase())) {
            return word
        }
        return replaceWithFirstRule(word, singulars)
    }

    private fun replaceWithFirstRule(word: String, ruleAndReplacements: List<RuleAndReplacement>): String {
        for ((rule, replacement) in ruleAndReplacements) {
            val matcher = Pattern.compile(rule, Pattern.CASE_INSENSITIVE).matcher(word)
            if (matcher.find()) {
                return matcher.replaceAll(replacement)
            }
        }
        return word
    }

    /**
     * 将驼峰命名转换为下划线格式
     *
     * @param camelCasedWord 驼峰命名的单词
     * @return 下划线格式的单词
     */
    fun underscore(camelCasedWord: String): String {
        val underscorePattern1 = Pattern.compile("([A-Z]+)([A-Z][a-z])")
        val underscorePattern2 = Pattern.compile("([a-z\\d])([A-Z])")

        var underscoredWord = underscorePattern1.matcher(camelCasedWord).replaceAll("$1_$2")
        underscoredWord = underscorePattern2.matcher(underscoredWord).replaceAll("$1_$2")
        underscoredWord = underscoredWord.replace('-', '_').lowercase()

        return underscoredWord
    }

    /**
     * 表格化（复数形式的下划线格式）
     *
     * @param className 类名
     * @return 表格化的名称
     */
    fun tableize(className: String): String {
        return pluralize(underscore(className))
    }
}