import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

val detektVersion = "2.0.0-alpha.6"

dependencies {
    compileOnly("dev.detekt:detekt-api:$detektVersion")
    compileOnly("dev.detekt:detekt-metrics:$detektVersion")
    testImplementation("dev.detekt:detekt-test:$detektVersion")
    testImplementation("dev.detekt:detekt-metrics:$detektVersion")
    testImplementation("dev.detekt:detekt-test-junit:$detektVersion")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version.set("1.8.0")
}

tasks.withType<KtLintCheckTask>().configureEach {
    dependsOn(tasks.ktlintFormat)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.ktlintCheck)
}

tasks.test {
    useJUnitPlatform()
}
