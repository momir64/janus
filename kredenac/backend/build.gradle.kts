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

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.koin.ktor)
    implementation(libs.lettuce.core)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
