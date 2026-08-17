plugins { application }

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    implementation(project(":consumer"))
    implementation("io.github.ldmoxeii:cap4k-ddd-core-starter:0.6.0-dev")
    implementation("io.github.ldmoxeii:cap4k-ddd-endpoint-rpc-http-starter:0.6.0-dev")
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}

application { mainClass.set("demo.consumerstart.ConsumerApplicationKt") }
