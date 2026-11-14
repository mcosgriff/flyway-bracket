// Saves things on the phone: the database (Room 3) and the player's settings (DataStore), mapped to :domain.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.cosgriff.flywaybracket.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // MigrationTest reads every exported schema and runs on the JVM (Robolectric), so it is part of `build`.
    sourceSets {
        named("test") {
            assets.directories.add("$projectDir/schemas")
        }
    }
    testOptions.unitTests.isIncludeAndroidResources = true

    // As in :app: no warnings are kept, and version news is left to Dependabot.
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    jvmToolchain(17)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore.preferences)

    // api: Hilt generates the app's component in :app, so it must see these binding types.
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite.framework)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // The made-up state pack (testStatePack()).
    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Feeds the merged coverage report (root build.gradle.kts).
kover {
    currentProject {
        createVariant("coverage") { add("debug") }
    }
}
