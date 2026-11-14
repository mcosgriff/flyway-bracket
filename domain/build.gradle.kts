// Pure Kotlin — no Android. Game rules (bird packs, expected birds, bracket seeding and scoring) live
// here so they are unit-tested in seconds without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
    // State packs are JSON (domain/pack/StatePackCodec).
    alias(libs.plugins.kotlin.serialization)
    // src/testFixtures: made-up charts and packs that this module's tests and other modules' tests share
    // (`testImplementation(testFixtures(project(":domain")))`).
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
