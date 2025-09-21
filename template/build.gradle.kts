//<!-- [cap4j-ddd-codegen-maven-plugin:do-not-overwrite] -->

plugins {
    kotlin("jvm")
    id("com.only4.cap4k.ddd.codegen") version "0.2.12-SNAPSHOT"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

// 配置 cap4k 代码生成插件
cap4kCodegen {
    basePackage.set("${basePackage}")
    multiModule.set(true)
    archTemplate.set("C:\\Users\\LD_moxeii\\Documents\\code\\cap\\cap4k\\cap4k-ddd-codegen-template-multi-nested.json")

    database {
        url.set("jdbc:mysql://localhost:3306/demo_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai")
        username.set("root")
        password.set("password")
        schema.set("demo_db")
        tables.set("") // 空表示所有表
        ignoreTables.set("") // 忽略的表
    }

    generation {
        versionField.set("version")
        deletedField.set("deleted")
        readonlyFields.set("id,created_at,updated_at")
        ignoreFields.set("")
        entityBaseClass.set("")
        rootEntityBaseClass.set("")
        generateSchema.set(true)
        generateAggregate.set(true)
        generateParent.set(false)
        generateDefault.set(true)
        generateDbType.set(true)
        fetchType.set("LAZY")
        idGenerator.set("snowflake")
        enumValueField.set("value")
        enumNameField.set("name")
        enumUnmatchedThrowException.set(true)
        datePackage4Java.set("java.time")
        repositoryNameTemplate.set("\${Entity}Repository")
        aggregateNameTemplate.set("Agg\${Entity}")
    }
}
