package com.only4.cap4k.test.runtime.appsideid

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity
@Table(name = "`runtime_uuid_root`")
open class RuntimeUuidRoot(id: UUID = UUID(0L, 0L), name: String = "") {
    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "`root_id`", nullable = false)
    open var children: MutableList<RuntimeUuidChild> = mutableListOf()

    @Id
    @ApplicationSideId(strategy = "uuid7")
    @Column(name = "`id`", nullable = false, updatable = false)
    open var id: UUID = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

@Entity
@Table(name = "`runtime_uuid_child`")
open class RuntimeUuidChild(id: UUID = UUID(0L, 0L), name: String = "") {
    @Id
    @ApplicationSideId(strategy = "uuid7")
    @Column(name = "`id`", nullable = false, updatable = false)
    open var id: UUID = id
        protected set

    @Column(name = "`name`", nullable = false)
    open var name: String = name
}

interface RuntimeUuidRootRepository : JpaRepository<RuntimeUuidRoot, UUID>
