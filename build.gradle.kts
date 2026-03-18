plugins {
    kotlin("jvm") version "2.3.0"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.register("format") {
    group = "verification"
    description = "Formats Kotlin source and Gradle Kotlin DSL files."
    dependsOn("spotlessApply")
}

tasks.register("lint") {
    group = "verification"
    description = "Runs style checks for Kotlin source and Gradle Kotlin DSL files."
    dependsOn("spotlessCheck")
}

tasks.check {
    dependsOn("lint")
}

tasks.test {
    useJUnitPlatform()
}
