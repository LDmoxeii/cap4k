package $

{ basePackage }.domain.aggregates${ package }.specs

import com.only4.cap4k.ddd.domain.repo.Specification
import com.only4.cap4k.ddd.annotation.Aggregate

/**
 * ${CommentEscaped}
 *
 * @author cap4k-ddd-codegen
 * @date ${date}
 */
@Aggregate(aggregate = "${aggregateName}", name = "${Entity}Specification", type = "specification")
class $ {Entity }Specification {

    companion object {
        /**
         * 根据名称查找
         */
        fun nameEquals(name: String): Specification<$ {
            Entity
        }> =
        Specification.where
        {
            root, _, cb ->
            cb.equal(root.get<String>("name"), name)
        }

        /**
         * 根据ID查找
         */
        fun idEquals(id: String): Specification<$ {
            Entity
        }> =
        Specification.where
        {
            root, _, cb ->
            cb.equal(root.get<String>("id"), id)
        }

        /**
         * 名称模糊匹配
         */
        fun nameLike(name: String): Specification<$ {
            Entity
        }> =
        Specification.where
        {
            root, _, cb ->
            cb.like(root.get<String>("name"), "%$name%")
        }
    }
}
