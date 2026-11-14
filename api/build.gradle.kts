// Pure Kotlin — no Android. The messages the app and the server exchange (docs/server/api.md), in one
// place so the two cannot drift apart.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // In the messages' own signatures: Round, Season and Decision.Reason, and the Json they are read with.
    api(project(":domain"))
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
