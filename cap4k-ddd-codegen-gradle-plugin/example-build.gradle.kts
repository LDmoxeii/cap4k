// 示例配置文件，展示如何在项目中使用 cap4k-ddd-codegen-gradle-plugin

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("com.only4.cap4k.ddd.codegen") version "1.0.0-SNAPSHOT"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    // 如果插件发布到私有仓库，添加仓库配置
}

dependencies {
    // cap4k 核心依赖
    implementation("com.only4:ddd-core:0.2.12-SNAPSHOT")

    // Spring Boot 依赖
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // 数据库驱动
    runtimeOnly("mysql:mysql-connector-java")

    // 其他依赖...
}

// 配置 cap4k 代码生成插件
cap4kCodegen {
    // 基础配置
    basePackage.set("com.example.ecommerce")
    multiModule.set(false)
    archTemplateEncoding.set("UTF-8")
    outputEncoding.set("UTF-8")

    // 数据库连接配置
    database {
        url.set("jdbc:mysql://localhost:3306/ecommerce")
        username.set("root")
        password.set("123456")
        schema.set("ecommerce")

        // 只处理以下表
        tables.set("user_*,order_*,product_*,payment_*")

        // 忽略临时表和日志表
        ignoreTables.set("*_temp,*_log,flyway_*")
    }

    // 代码生成配置
    generation {
        // 基础配置
        versionField.set("version")
        deletedField.set("deleted")
        readonlyFields.set("created_at,updated_at")
        ignoreFields.set("internal_flag")

        // 实体配置
        entityBaseClass.set("com.example.common.BaseEntity")
        rootEntityBaseClass.set("com.example.common.AggregateRoot")

        // 生成选项
        generateSchema.set(true)
        generateAggregate.set(true)
        generateParent.set(false)
        generateDefault.set(true)
        generateDbType.set(true)

        // JPA 配置
        fetchType.set("LAZY")
        idGenerator.set("IDENTITY")
        idGenerator4ValueObject.set("UUID")

        // 枚举配置
        enumValueField.set("value")
        enumNameField.set("name")
        enumUnmatchedThrowException.set(true)

        // 日期类型配置
        datePackage4Java.set("java.time")

        // 命名模板
        repositoryNameTemplate.set("\${Entity}Repository")
        aggregateNameTemplate.set("Agg\${Entity}")
    }
}

// 可以在特定任务前后执行自定义逻辑
tasks.named("genEntity") {
    doFirst {
        println("开始生成实体类...")
    }
    doLast {
        println("实体类生成完成！")
    }
}

// 确保在编译前生成代码
tasks.named("compileKotlin") {
    dependsOn("genEntity")
}

kotlin {
    jvmToolchain(17)
}