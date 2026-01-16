package com.only4.cap4k.plugin.codeanalysis.core.io

import com.only4.cap4k.plugin.codeanalysis.core.model.Node
import com.only4.cap4k.plugin.codeanalysis.core.model.Relationship

fun interface MetadataSink {
    fun write(nodes: Sequence<Node>, relationships: Sequence<Relationship>)
}
