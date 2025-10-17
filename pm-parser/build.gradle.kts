plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.viettel"
version = "0.1"
description = "pm-parser"

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
    implementation("org.springframework:spring-context:6.1.0")
    implementation("org.springframework:spring-jdbc:6.1.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc:3.2.0")
    compileOnly("org.projectlombok:lombok:1.18.30")
    runtimeOnly("com.mysql:mysql-connector-j:8.2.0")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}


