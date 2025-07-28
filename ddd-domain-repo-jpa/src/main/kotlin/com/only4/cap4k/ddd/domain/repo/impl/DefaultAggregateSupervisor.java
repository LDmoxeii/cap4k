package com.only4.cap4k.ddd.domain.repo.impl;

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate;
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor;
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload;
import com.only4.cap4k.ddd.core.domain.aggregate.Id;
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate;
import com.only4.cap4k.ddd.core.domain.repo.AggregateSupervisor;
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor;
import com.only4.cap4k.ddd.core.share.OrderInfo;
import com.only4.cap4k.ddd.core.share.PageData;
import com.only4.cap4k.ddd.core.share.PageParam;
import com.only4.cap4k.ddd.core.share.misc.ClassUtils;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 默认聚合管理器
 *
 * @author binking338
 * @date 2025/1/12
 */
@RequiredArgsConstructor
public class DefaultAggregateSupervisor implements AggregateSupervisor {
    final RepositorySupervisor repositorySupervisor;

    private static <AGGREGATE extends Aggregate<?>> AGGREGATE newInstance(Class<AGGREGATE> clazz, Object entity) {
        try {
            Aggregate aggregate = clazz.getConstructor().newInstance();
            aggregate._wrap(entity);
            return (AGGREGATE)aggregate;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public <AGGREGATE extends Aggregate<ENTITY>, ENTITY_PAYLOAD extends AggregatePayload<ENTITY>, ENTITY> AGGREGATE create(Class<AGGREGATE> clazz, ENTITY_PAYLOAD payload) {
        ENTITY entity = AggregateFactorySupervisor.getInstance().create(payload);
        return newInstance(clazz, entity);
    }

    @Override
    public <AGGREGATE extends Aggregate<ENTITY>, ENTITY> List<AGGREGATE> getByIds(Iterable<Id<AGGREGATE, ?>> ids, boolean persist) {
        if (!ids.iterator().hasNext()) {
            return Collections.emptyList();
        }
        Class<AGGREGATE> aggregateClass = (Class<AGGREGATE>)ClassUtils.resolveGenericTypeClass(ids.iterator().next(), 0, Id.class, Id.Default.class);
        AggregatePredicate<AGGREGATE, ENTITY> aggregatePredicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicate.byIds(
                aggregateClass,
                StreamSupport.stream(ids.spliterator(), false).map(Id::getValue).collect(Collectors.toList())
        );
        return find(aggregatePredicate, persist);
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> List<AGGREGATE> find(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, Collection<OrderInfo> orders, boolean persist) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        List<?> entities = repositorySupervisor.find(predicate, orders, persist);
        return entities.stream().map(e -> newInstance(clazz, e)).collect(Collectors.toList());
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> List<AGGREGATE> find(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, PageParam pageParam, boolean persist) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        List<?> entities = repositorySupervisor.find(predicate, pageParam, persist);
        return entities.stream().map(e -> newInstance(clazz, e)).collect(Collectors.toList());
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> Optional<AGGREGATE> findOne(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, boolean persist) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        Optional<?> entity = repositorySupervisor.findOne(predicate, persist);
        return entity.map(e -> newInstance(clazz, e));
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> Optional<AGGREGATE> findFirst(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, Collection<OrderInfo> orders, boolean persist) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        Optional<?> entity = repositorySupervisor.findFirst(predicate, orders, persist);
        return entity.map(e -> newInstance(clazz, e));
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> PageData<AGGREGATE> findPage(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, PageParam pageParam, boolean persist) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        PageData<?> entities = repositorySupervisor.findPage(predicate, pageParam, persist);
        return entities.transform(e -> newInstance(clazz, e));
    }

    @Override
    public <AGGREGATE extends Aggregate<ENTITY>, ENTITY> List<AGGREGATE> removeByIds(Iterable<Id<AGGREGATE, ?>> ids) {
        if (!ids.iterator().hasNext()) {
            return Collections.emptyList();
        }
        Class<AGGREGATE> aggregateClass = (Class<AGGREGATE>)ClassUtils.resolveGenericTypeClass(ids.iterator().next(), 0, Id.class, Id.Default.class);
        AggregatePredicate<AGGREGATE, ?> aggregatePredicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicate.byIds(
                aggregateClass,
                StreamSupport.stream(ids.spliterator(), false).map(Id::getValue).collect(Collectors.toList())
        );
        return remove(aggregatePredicate);
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> List<AGGREGATE> remove(AggregatePredicate<AGGREGATE, ?> aggregatePredicate) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        List<?> entities = repositorySupervisor.remove(predicate);
        return entities.stream().map(e -> newInstance(clazz, e)).collect(Collectors.toList());
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> List<AGGREGATE> remove(AggregatePredicate<AGGREGATE, ?> aggregatePredicate, int limit) {
        Class<AGGREGATE> clazz = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate);
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        List<?> entities = repositorySupervisor.remove(predicate, limit);
        return entities.stream().map(e -> newInstance(clazz, e)).collect(Collectors.toList());
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> long count(AggregatePredicate<AGGREGATE, ?> aggregatePredicate) {
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        return repositorySupervisor.count(predicate);
    }

    @Override
    public <AGGREGATE extends Aggregate<?>> boolean exists(AggregatePredicate<AGGREGATE, ?> aggregatePredicate) {
        Predicate<?> predicate = org.netcorepal.cap4j.ddd.domain.repo.JpaAggregatePredicateSupport.getPredicate((AggregatePredicate<?, ?>)aggregatePredicate);
        return repositorySupervisor.exists(predicate);
    }
}
