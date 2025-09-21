package $

{ basePackage }.adapter.infra.jpa

import com.blazebit.persistence.Criteria
import com.blazebit.persistence.CriteriaBuilderFactory
import com.blazebit.persistence.querydsl.BlazeJPAQueryFactory
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

/**
 * QueryDslConfig
 *
 * @author cap4k-ddd-codegen
 */
@Configuration
class QueryDslConfig(
    private val entityManagerFactory: EntityManagerFactory,
) {

    @PersistenceContext
    private lateinit var entityManager: EntityManager


    @Bean
    fun jpaQueryFactory(): JPAQueryFactory {
        return JPAQueryFactory(entityManager)
    }

    @Bean
    @Lazy(false)
    fun createCriteriaBuilderFactory(): CriteriaBuilderFactory {
        val config: CriteriaBuilderConfiguration = Criteria.getDefault()
        return config.createCriteriaBuilderFactory(entityManagerFactory)
    }

    @Bean
    fun blazeJPAQueryFactory(criteriaBuilderFactory: CriteriaBuilderFactory): BlazeJPAQueryFactory {
        return BlazeJPAQueryFactory(entityManager, criteriaBuilderFactory)
    }
}
