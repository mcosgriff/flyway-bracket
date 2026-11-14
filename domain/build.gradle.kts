// Pure Kotlin — no Android. Game rules (bird packs, expected birds, bracket seeding and scoring) live
// here so they are unit-tested in seconds without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
