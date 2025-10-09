// [cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.only4.cap4k.ddd.codegen") version "0.3.12-SNAPSHOT"
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

cap4kCodegen {
    basePackage.set("${basePackage}")
    archTemplate.set("${archTemplate}")

    database {
        url.set("${dbUrl}")
        username.set("${dbUsername}")
        password.set("${dbPassword}")
        schema.set("${dbSchema}")
        tables.set("${dbTables}")
        ignoreTables.set("${dbIgnoreTables}")
    }

    generation {
        versionField.set("${versionField}")
        deletedField.set("${deletedField}")
        readonlyFields.set("${readonlyFields}")
        ignoreFields.set("${ignoreFields}")
    }
}
