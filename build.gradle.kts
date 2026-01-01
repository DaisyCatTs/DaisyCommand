import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ktlint by configurations.creating

plugins {
    kotlin("jvm") version "2.2.21"
    `maven-publish`
    `java-library`
}

group = "com.github.fu3i0n"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val versions =
    mapOf(
        "paperApi" to "1.21.10-R0.1-SNAPSHOT",
        "kotlin" to "2.3.0",
        "ktlint" to "1.8.0",
    )

dependencies {
    compileOnly("io.papermc.paper:paper-api:${versions["paperApi"]}")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${versions["kotlin"]}")

    ktlint("com.pinterest.ktlint:ktlint-cli:${versions["ktlint"]}") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

val targetJavaVersion = 21

kotlin {
    jvmToolchain(targetJavaVersion)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
    }
}

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("**/src/**/*.kt", "**.kts", "!**/build/**")
}

tasks {
    check {
        dependsOn("ktlintFormat")
    }

    register<JavaExec>("ktlintFormat") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check Kotlin code style and format"
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args("-F", "**/src/**/*.kt", "**.kts", "!**/build/**")
    }

    val jarDir = layout.projectDirectory.dir("Jar")
    val projectVersion = version.toString()

    register<Copy>("copyToJar") {
        from(jar)
        into(jarDir)
        rename { "DaisyCommand-$projectVersion.jar" }
    }

    build {
        finalizedBy("copyToJar")
    }
}

// JitPack compatible publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.fu3i0n"
            artifactId = "DaisyCommand"
        }
    }
}
