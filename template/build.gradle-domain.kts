
plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    compileOnly(libs.spring.data)

    api(libs.annotation)
    api("com.querydsl:querydsl-jpa:5.0.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")

    kapt(libs.hibernate.processor)

    api(libs.ddd.starter)
}

kapt {
    arguments {
        // 开启处理器日志（排查问题很好用）
        arg("debug", "true")
        // 生成 @Generated 注解
        arg("addGeneratedAnnotation", "true")
        // 如果项目用到了 persistence.xml 或 orm.xml，可显式告知
        // arg("persistenceXml", "META-INF/persistence.xml")
        // arg("ormXmlFiles", "META-INF/orm.xml")
        // 若构建路径存在 Jakarta Data，但你只想用于 JPA，可抑制 Jakarta Data 的元模型生成
        // （这对 Spring Data JPA 项目一般是安全的）
        arg("suppressJakartaDataMetamodel", "true")
    }
}
