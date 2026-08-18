plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0-dev")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("org.hibernate.orm:hibernate-core")
}

tasks.register<JavaExec>("verifyEnumConverterRoundTrip") {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.acme.demo.domain.smoke.EnumManifestCompileSmokeKt")
}
