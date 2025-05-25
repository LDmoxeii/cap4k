package com.only4.core.domain.repo

import com.only4.core.domain.aggregate.Aggregate


/**
 * 聚合检索断言
 *
 * @author binking338
 * @date 2025/1/12
 */
interface AggregatePredicate<ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> :
    Predicate<ENTITY>
