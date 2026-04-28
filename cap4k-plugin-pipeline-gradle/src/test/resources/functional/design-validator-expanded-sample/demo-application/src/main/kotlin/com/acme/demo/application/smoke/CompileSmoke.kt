package com.acme.demo.application.smoke

import com.acme.demo.application.validators.category.CategoryMustExist
import com.acme.demo.application.validators.danmuku.DanmukuDeletePermission

@DanmukuDeletePermission(
    danmukuIdField = "danmukuId",
    operatorIdField = "operatorId",
)
data class DeleteDanmukuRequest(
    val danmukuId: Long,
    val operatorId: Long,
)

data class CategoryRequest(
    @field:CategoryMustExist
    val categoryId: Long? = null,
)
