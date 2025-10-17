plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.viettel"
version = "0.1"
description = "core"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Redis - High-performance caching
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // Reactor - Reactive programming
    implementation("io.projectreactor:reactor-core:3.6.0")
    implementation("io.projectreactor:reactor-netty:1.1.13")

    // Apache Commons Pool - FTP connection pooling
    implementation("org.apache.commons:commons-pool2:2.12.0")

    // Existing dependencies
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.capnproto:runtime:0.1.3")
    implementation("commons-net:commons-net:3.9.0")
    runtimeOnly("com.mysql:mysql-connector-j")
}

//tasks.withType<Test> {
//    useJUnitPlatform()
//}
