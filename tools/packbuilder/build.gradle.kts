// A developer's command-line tool, not part of the app: it builds a state pack (`<state>.fbpack`) from
// eBird bar chart downloads and Census county outlines kept in `packs-local/`. See docs/packs.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "io.cosgriff.flywaybracket.tools.packbuilder.MainKt"
}

// `packs-local/` is at the repository root, and that is where the tool looks for it.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    implementation(project(":domain"))
    // Reads the GeoJSON of county outlines.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
