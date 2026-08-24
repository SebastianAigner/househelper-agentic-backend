plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "io.sebi"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.koog.agents)
    implementation(libs.koog.spring.ai.starter.model.chat)
    implementation(libs.spring.ai.starter.model.openai)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}

dependencyManagement {
    imports {
        // Spring AI's own starters (e.g. spring-ai-starter-model-openai) don't declare a
        // version; they rely on this BOM, pinned to the Spring AI version koog-spring-ai-starter-model-chat
        // was built against.
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai.get()}")
    }
    dependencies {
        // Spring Boot's dependency-management BOM pins kotlinx-serialization to an older
        // version than Koog's generated serializers require; override the managed version.
        dependency("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
        dependency("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")
        dependency("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

springBoot {
    mainClass.set("io.sebi.househelper.HouseHelperBackendApplicationKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val keyFile = rootProject.file("antkey.txt")
    if (keyFile.exists()) {
        environment("ANTHROPIC_API_KEY", keyFile.readText().trim())
    }
    val openaiKeyFile = rootProject.file("oaikey.txt")
    if (openaiKeyFile.exists()) {
        environment("OPENAI_API_KEY", openaiKeyFile.readText().trim())
    }
}
