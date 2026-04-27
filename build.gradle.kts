plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.classpulse"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-redis") {
        exclude(group = "io.lettuce", module = "lettuce-core")
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Redis — Jedis client (replaces Lettuce)
    implementation("redis.clients:jedis")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // JWT (JJWT 0.12.x, HS512)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Springdoc OpenAPI 2.x — Swagger UI tại /swagger-ui.html
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Hypersistence Utils — UUID[] PostgreSQL array mapping (Hibernate 6.x)
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Load .env file vào environment cho bootRun (dev workflow)
fun loadDotenv(): Map<String, String> {
    val dotenv = file(".env")
    if (!dotenv.exists()) return emptyMap()
    return dotenv.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment(loadDotenv())
}
