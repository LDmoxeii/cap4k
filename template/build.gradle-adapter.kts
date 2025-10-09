
plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    api(libs.spring.data)
    api(libs.spring.web)
    api(libs.spring.messaging)
    api(libs.druid)
    api(libs.mysql)

    api(libs.jimmer.starter)
    api(libs.blazebit.persistence.hibernate)

    api(libs.engine.common)
    api(libs.engine.web.starter)
    implementation(libs.blazebit.persistence.querydsl)

    api(project(":${artifactId}-application"))
}
