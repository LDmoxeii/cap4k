package com.only4.cap4k.ddd.runtime.ownedentitylistfixture

import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "`owned_entity_list_root`")
open class OwnedEntityListRoot protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.LAZY,
        orphanRemoval = true,
    )
    @JoinColumn(name = "`root_id`", nullable = false)
    private var _items: MutableList<OwnedEntityListItem> = mutableListOf()

    @OneToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        fetch = FetchType.LAZY,
        orphanRemoval = true,
    )
    @JoinColumn(name = "`root_id`", nullable = false)
    private var _files: MutableList<OwnedEntityListFile> = mutableListOf()

    @get:Transient
    val items: OwnedEntityList<OwnedEntityListItem>
        get() = OwnedEntityList.of(_items, OwnedEntityListItem::class, "OwnedEntityListRoot.items")

    @get:Transient
    var file: OwnedEntityListFile?
        get() = OwnedEntityList.of(_files, OwnedEntityListFile::class, "OwnedEntityListRoot.file")
            .singleOrNull()
        set(value) {
            OwnedEntityList.of(_files, OwnedEntityListFile::class, "OwnedEntityListRoot.file")
                .replace(value)
        }

    constructor(name: String) : this() {
        this.name = name
    }
}

@Entity
@Table(name = "`owned_entity_list_item`")
open class OwnedEntityListItem protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    constructor(name: String) : this() {
        this.name = name
    }
}

@Entity
@Table(name = "`owned_entity_list_file`")
open class OwnedEntityListFile protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null
        protected set

    @Column(name = "`name`", nullable = false)
    open lateinit var name: String
        protected set

    constructor(name: String) : this() {
        this.name = name
    }
}

interface OwnedEntityListRootRepository : JpaRepository<OwnedEntityListRoot, Long>
