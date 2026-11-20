import com.github.gradle.node.pnpm.task.PnpmInstallTask
import com.github.gradle.node.pnpm.task.PnpmTask
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("dev.detekt") version "2.0.0-alpha.6"
    id("com.github.node-gradle.node") version "7.1.0"
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
val renderedUiDir = layout.buildDirectory.dir("rendered-ui")

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restdocs")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jsoup:jsoup:1.23.2")
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

// The versions live in a kit file so that a kit update can bring new tool versions along.
val uiToolVersions = Properties().apply { file("config/ui/tool-versions.properties").inputStream().use(::load) }

// Gradle downloads the pinned Node and pnpm, so CI and the runtime image need no Node install.
node {
    version.set(uiToolVersions.getProperty("node"))
    pnpmVersion.set(uiToolVersions.getProperty("pnpm"))
    download.set(true)
}

tasks.named<PnpmInstallTask>("pnpmInstall") {
    args.set(listOf("--frozen-lockfile"))
}

val uiLint =
    tasks.register<PnpmTask>("uiLint") {
        dependsOn("pnpmInstall")
        args.set(listOf("check"))
    }

val uiHtmlCheck =
    tasks.register<PnpmTask>("uiHtmlCheck") {
        dependsOn("pnpmInstall", tasks.test)
        args.set(listOf("run", "lint:html"))
        // An empty glob passes html-validate, which would silently switch this gate off.
        doFirst {
            check(
                renderedUiDir
                    .get()
                    .asFile
                    .walkTopDown()
                    .any { it.extension == "html" },
            ) {
                "No rendered UI pages to validate."
            }
        }
    }

tasks.register("uiCheck") {
    dependsOn(uiLint, uiHtmlCheck)
}

tasks.check {
    dependsOn("detektMain", "detektTest", ":quality-rules:test", "uiCheck")
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
    outputs.dir(renderedUiDir)
    systemProperty("org.springframework.restdocs.outputDir", snippetsDir.get().asFile.absolutePath)
    systemProperty("ui.renderedOutput", renderedUiDir.get().asFile.absolutePath)
    systemProperty(
        "architecture.productionClasses",
        sourceSets.main
            .get()
            .output.classesDirs.asPath,
    )
}

tasks.test {
    dependsOn("detektMain", "detektTest", ":quality-rules:test")
    // Removed tests must not leave stale snippets for Asciidoctor or stale pages for html-validate.
    doFirst {
        delete(snippetsDir, renderedUiDir)
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
