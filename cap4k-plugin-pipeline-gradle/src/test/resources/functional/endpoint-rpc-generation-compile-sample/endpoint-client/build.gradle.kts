dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    api(project(":contract"))
    api("io.github.ldmoxeii:ddd-endpoint-rpc:0.6.0-dev")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
}
