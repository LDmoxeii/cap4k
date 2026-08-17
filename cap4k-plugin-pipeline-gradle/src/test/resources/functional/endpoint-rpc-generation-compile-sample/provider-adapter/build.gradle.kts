dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    implementation(project(":contract"))
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("io.github.ldmoxeii:ddd-endpoint-rpc:0.6.0-dev")
    implementation("org.springframework:spring-context")
}
