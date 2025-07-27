package com.only4.cap4k.ddd.core.archinfo.model

/**
 * 架构信息
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
data class ArchInfo(
    val name: String,
    val version: String,
    val archInfoVersion: String,
    val architecture: Architecture
)
