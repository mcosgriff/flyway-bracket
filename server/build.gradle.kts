// The league server: Ktor on Netty, Postgres through JDBC. Not part of the app; `:app` must never depend
// on it. docs/server/running.md says how to run it.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "io.cosgriff.flywaybracket.server.MainKt"
}

// The version the health check reports: version.txt, which the release PR keeps current.
val serverVersion = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt")).asText
    .map { it.trim() }

tasks.processResources {
    val version = serverVersion
    inputs.property("version", version)
    filesMatching("server.properties") { expand("version" to version.get()) }
}

dependencies {
    // The same game rules and the same messages as the app (ADR 0001).
    implementation(project(":domain"))
    implementation(project(":api"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Turns a failure into the ApiError body.
    implementation(libs.ktor.server.status.pages)
    // One line per request: status, method and path.
    implementation(libs.ktor.server.call.logging)

    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    // Writes the log to standard error, where a container's log is read.
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.postgresql)
}
