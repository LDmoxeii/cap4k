package com.only4.cap4k.ddd.core.application.query

interface Query<R : Any>

interface QuerySupervisor {
    fun <R : Any> ask(query: Query<R>): R
}
