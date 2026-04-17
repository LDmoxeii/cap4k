package com.only.engine.translation.core

interface BatchTranslationInterface<T> {
    fun translationBatch(keys: Collection<Any>, other: String): Map<Any, T?>
}
