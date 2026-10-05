import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

val exposedVersion = "1.5.0"
val snippetsDir = layout.buildDirectory.dir("generated-snippets")

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restdocs")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    detektPlugins(project(":quality-rules"))
}

ktlint {
    version.set("1.8.0")
}

detekt {
    toolVersion.set("2.0.0-alpha.6")
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig.set(false)
    ignoreFailures.set(false)
    failOnSeverity.set(FailOnSeverity.Error)
}

tasks.withType<Detekt>().configureEach {
    // Analyze every finding without inheriting a convention-based baseline file.
    baseline.unset()
    baseline.unsetConvention()
}

tasks.named<Detekt>("detektTest") {
    config.setFrom(files("config/detekt/detekt.yml", "config/detekt/test.yml"))
    // The plain JAR is disabled; analyze the actual main/test class directories instead.
    classpath.setFrom(configurations.testCompileClasspath, sourceSets.main.get().output, sourceSets.test.get().output)
    friendPaths.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs,
    )
}

// The public entry point must also execute rules that require type resolution.
tasks.named<Detekt>("detekt") {
    setSource(files())
    dependsOn("detektMain", "detektTest")
}

tasks.check {
    dependsOn("detektMain", "detektTest", ":quality-rules:test")
}

asciidoctorj {
    setVersion("3.0.1")
    fatalWarnings(missingIncludes())
}

// Format the entire source tree before checking or compiling to avoid races under --parallel.
tasks.withType<KtLintCheckTask>().configureEach {
    dependsOn(tasks.ktlintFormat)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.ktlintCheck)
}

tasks.withType<Test>().configureEach {
    dependsOn(tasks.ktlintCheck)
    useJUnitPlatform()
    outputs.dir(snippetsDir)
    systemProperty("org.springframework.restdocs.outputDir", snippetsDir.get().asFile.absolutePath)
    systemProperty(
        "architecture.productionClasses",
        sourceSets.main
            .get()
            .output.classesDirs.asPath,
    )
}

tasks.test {
    dependsOn("detektMain", "detektTest", ":quality-rules:test")
    // Removed documentation tests must not leave stale snippets available to Asciidoctor.
    doFirst {
        delete(snippetsDir)
    }
}

tasks.named<AsciidoctorTask>("asciidoctor") {
    dependsOn(tasks.test)
    inputs.dir(snippetsDir)
    setSourceDir(file("src/docs/asciidoc"))
    sources {
        include("index.adoc")
    }
    outputOptions {
        setSeparateOutputDirs(false)
    }
    setOutputDir(
        layout.buildDirectory
            .dir("docs/asciidoc")
            .get()
            .asFile,
    )
    attributes(mapOf("snippets" to snippetsDir.get().asFile.absolutePath))
}

tasks.bootJar {
    dependsOn(tasks.named("asciidoctor"))
    archiveFileName.set("application.jar")
    from(tasks.named("asciidoctor")) {
        into("BOOT-INF/classes/static/docs")
    }
}

tasks.jar {
    enabled = false
}

tasks.wrapper {
    gradleVersion = "9.3.0"
    distributionSha256Sum = "0d585f69da091fc5b2beced877feab55a3064d43b8a1d46aeb07996b0915e0e0"
}
