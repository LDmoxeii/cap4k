package com.only4.cap4k.plugin.codegen.misc

/**
 * ��词形态变换工具（单数复数转换）
 * 基于 Java 版本的 Inflector 移植到 Kotlin
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object Inflector {

    // 规则（已预编译）
    private data class Rule(val regex: Regex, val replacement: String)

    private val plurals = mutableListOf<Rule>()
    private val singulars = mutableListOf<Rule>()
    private val uncountables = hashSetOf<String>()

    // 预编译用于 underscore 的两个模式
    private val UNDERSCORE_1 = Regex("([A-Z]+)([A-Z][a-z])")
    private val UNDERSCORE_2 = Regex("([a-z\\d])([A-Z])")

    init {
        initialize()
    }

    private fun initialize() {
        fun plural(rule: String, replacement: String) =
            plurals.add(0, Rule(rule.toRegex(RegexOption.IGNORE_CASE), replacement))

        fun singular(rule: String, replacement: String) =
            singulars.add(0, Rule(rule.toRegex(RegexOption.IGNORE_CASE), replacement))

        fun irregular(singular: String, plural: String) {
            plural(singular, plural)
            singular(plural, singular)
        }

        fun uncountable(vararg words: String) = uncountables.addAll(words.map { it.lowercase() })

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

        // 不��数名词
        uncountable("equipment", "information", "rice", "money", "species", "series", "fish", "sheep")
    }

    private fun applyFirstRule(word: String, rules: List<Rule>): String {
        for (r in rules) {
            if (r.regex.containsMatchIn(word)) {
                return word.replace(r.regex, r.replacement)
            }
        }
        return word
    }

    /**
     * 将单词复数化
     *
     * @param word 单词
     * @return 复数形式
     */
    fun pluralize(word: String): String =
        if (word.lowercase() in uncountables) word else applyFirstRule(word, plurals)

    /**
     * 将单词单数化
     *
     * @param word 单词
     * @return 单数形式
     */
    fun singularize(word: String): String =
        if (word.lowercase() in uncountables) word else applyFirstRule(word, singulars)

    /**
     * 将驼峰命名转换为下划线格式
     *
     * @param camelCasedWord 驼峰命名的单词
     * @return 下划线格式的单词
     */
    fun underscore(camelCasedWord: String): String =
        camelCasedWord
            .replace(UNDERSCORE_1, "$1_$2")
            .replace(UNDERSCORE_2, "$1_$2")
            .replace('-', '_')
            .lowercase()

    /**
     * 表格化（复数形式的下划线格式）
     *
     * @param className 类名
     * @return 表格化的名称
     */
    fun tableize(className: String): String = pluralize(underscore(className))
}
