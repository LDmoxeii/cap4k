package com.only.engine.translation.core

interface TranslationInterface<T> {
    fun translation(key: Any, other: String): T?
}
