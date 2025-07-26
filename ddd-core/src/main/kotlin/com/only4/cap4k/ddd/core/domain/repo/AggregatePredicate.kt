package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate


/**
 * 聚合检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface AggregatePredicate<AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> :
    Predicate<ENTITY>
