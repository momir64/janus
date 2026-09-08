plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "rs.moma.janus"
version = "0.0.1"

application {
    mainClass = "rs.moma.janus.kredenac.MainKt"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(files("libs/lokot-0.0.1.jar"))

    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(libs.logback.classic)

    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.koin.ktor)
    implementation(libs.lettuce.core)
    implementation(libs.minio)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)

    testImplementation(libs.bouncycastle.pkix)
}

// todo: will need change when secrets manager is implemented
tasks.test {
    val envFile = rootProject.file("../.env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val value = line.substringAfter("=").trim()
                environment(
                    line.substringBefore("=").trim(),
                    if (value.length >= 2 && value.first() in "\"'" && value.first() == value.last())
                        value.substring(1, value.length - 1) else value
                )
            }
    }
}

val checkLokotJar = tasks.register("checkLokotJar") {
    description = "Check for stale lokot build if sources are available."
    val jar = layout.projectDirectory.file("libs/lokot-0.0.1.jar").asFile
    val sources = layout.projectDirectory.dir("../../lokot/src").asFile
    doLast {
        if (!jar.isFile) throw GradleException("${jar.path} is missing: build lokot's jvmJar and copy it here")
        if (!sources.isDirectory) return@doLast

        val newer = sources.walkTopDown()
            .filter { it.isFile && it.lastModified() > jar.lastModified() }
            .map { it.name }.take(3).toList()
        if (newer.isNotEmpty()) logger.warn(
            "w: libs/${jar.name} is older than lokot's sources (${newer.joinToString()}). " +
                    "Refresh it: cd ../lokot && ./gradlew jvmJar && " +
                    "cp build/*/libs/lokot-jvm-0.0.1.jar ../kredenac/backend/libs/${jar.name}"
        )
    }
}

tasks.named("compileKotlin") { dependsOn(checkLokotJar) }
