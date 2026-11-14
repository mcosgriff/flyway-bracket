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

    // The league server's address (#38), with no slash at the end. -PflywayServerUrl=… sets a release
    // build's, -PflywayDebugServerUrl=… a debug build's. A release build's must be HTTPS: the app's network
    // security config allows no cleartext outside debug builds, so an http:// address would never connect.
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            // The emulator's name for the developer's own machine, where `./gradlew :server:run` listens.
            val url = findProperty("flywayDebugServerUrl") as String? ?: "http://10.0.2.2:8080"
            buildConfigField("String", "SERVER_URL", "\"${url.trimEnd('/')}\"")
        }
        release {
            // Where the server is hosted is not decided yet (#16). Until it is, the default is a name that
            // never resolves (`.invalid` is reserved), so a release build reaches nothing.
            val url = findProperty("flywayServerUrl") as String? ?: "https://server.flywaybracket.invalid"
            require(url.startsWith("https://")) { "flywayServerUrl must be an https:// address: $url" }
            buildConfigField("String", "SERVER_URL", "\"${url.trimEnd('/')}\"")
        }
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
    // LocationManagerCompat: the phone's approximate location from Android itself, not Play services (#27).
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.datastore.preferences)

    // api: Hilt generates the app's component in :app, so it must see these binding types.
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite.framework)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // The league server's client (#38): the messages of :api, sent with Ktor on OkHttp.
    api(project(":api"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Sends sightings and fetches results in the background (#43). api: the app hands WorkManager its worker
    // factory (FlywayApp), so it must see WorkManager's types.
    api(libs.androidx.work.runtime.ktx)
    // The lifecycle version the app ships, in place of the older one WorkManager asks for.
    implementation(libs.androidx.lifecycle.service)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    // The made-up state pack (testStatePack()).
    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
}

// Feeds the merged coverage report (root build.gradle.kts).
kover {
    currentProject {
        createVariant("coverage") { add("debug") }
    }
}
