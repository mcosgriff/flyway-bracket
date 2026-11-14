plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
}

// CI passes -PversionName=1.2.0 -PversionCode=<run number>. Local builds use version.txt, which the
// release PR keeps current.
val appVersionName = (findProperty("versionName") as String?)
    ?: rootProject.file("version.txt").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    ?: "0.1.0"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.cosgriff.flywaybracket"
    compileSdk = 37

    defaultConfig {
        // Never change after the first release: Android and Obtainium treat a new ID as a new app.
        applicationId = "io.cosgriff.flywaybracket"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Set by the GitHub release workflow from the `release` environment's secrets.
            val ks = System.getenv("FLYWAYBRACKET_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("FLYWAYBRACKET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLYWAYBRACKET_KEY_ALIAS")
                keyPassword = System.getenv("FLYWAYBRACKET_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("FLYWAYBRACKET_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    androidResources {
        // The app's languages for Android's per-app language setting, from the values-xx folders;
        // res/resources.properties says the default folder is US English.
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME, for Settings' About section (#44).
        buildConfig = true
    }

    // The screenshot tests draw screens under Robolectric, which needs the app's resources and fonts.
    testOptions.unitTests.isIncludeAndroidResources = true
    // Gradle's default 512 MB test heap is too little for Robolectric drawing Compose screens.
    testOptions.unitTests.all { it.maxHeapSize = "2g" }

    lint {
        abortOnError = true
        // No warnings are kept: a new one fails the build, as an error does.
        warningsAsErrors = true
        // Dependabot proposes new versions after its cooldown; lint would flag them on day one.
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // The made-up state pack (testStatePack()).
    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Stands in for the league server in the Settings tests (#44).
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
    // The empty activity the Compose test rule draws into; only in the debug manifest, never the release.
    debugImplementation(libs.compose.ui.test.manifest)
}

// The open-source libraries in the app, for the Licences screen (#44): read at build time from the Maven
// POMs of everything on the release runtime classpath, and written to the asset `licences/libraries.json`.
// No library and no plugin does it, so nothing is added to the app and nothing calls out at run time.
// Debug builds show the release list too: the debug-only tools never ship.
//
// Each library's POM is resolved as a file of its own (`group:name:version@pom`), and so are its parent
// POMs, up to three levels, since some libraries name their licence only in a parent.
val licencePoms = configurations.create("licencePoms") {
    description = "The POMs of the libraries in the release APK."
    isCanBeConsumed = false
    isTransitive = false
    withDependencies {
        val release = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
        release
            // A Kotlin Multiplatform library's root only points at its Android build, which is listed itself.
            .filter { component -> component.variants.none { it.externalVariant.isPresent } }
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .forEach { add(project.dependencies.create("${it.group}:${it.module}:${it.version}@pom")) }
    }
}
val parentPoms = (1..3).fold(listOf<Configuration>()) { levels, level ->
    val below = levels.lastOrNull() ?: licencePoms
    val parents: Configuration = configurations.create("licenceParentPoms$level") {
        description = "The parent POMs, level $level, of the libraries in the release APK."
        isCanBeConsumed = false
        isTransitive = false
        withDependencies {
            below.files.mapNotNull { Pom.read(it).parent }.distinct().forEach { add(project.dependencies.create("$it@pom")) }
        }
    }
    levels + listOf(parents)
}

/** What the licence task reads from a POM. */
data class Pom(
    val coordinates: String,
    val name: String?,
    val packaging: String,
    val parent: String?,
    val licences: List<Pair<String, String?>>,
) {
    companion object {
        fun read(file: File): Pom {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                // A POM never needs a DTD or an outside entity, and reading one could reach the network.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
            val project = factory.newDocumentBuilder().parse(file).documentElement
            fun org.w3c.dom.Element.child(tag: String): org.w3c.dom.Element? =
                (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<org.w3c.dom.Element>().firstOrNull { it.tagName == tag }
            fun org.w3c.dom.Element.text(tag: String): String? = child(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val parent = project.child("parent")?.let { "${it.text("groupId")}:${it.text("artifactId")}:${it.text("version")}" }
            val group = project.text("groupId") ?: project.child("parent")?.text("groupId")
            val version = project.text("version") ?: project.child("parent")?.text("version")
            val licences = project.child("licenses")?.let { list ->
                (0 until list.childNodes.length).map { list.childNodes.item(it) }
                    .filterIsInstance<org.w3c.dom.Element>()
                    .mapNotNull { licence -> licence.text("name")?.let { it to licence.text("url") } }
            }.orEmpty()
            return Pom(
                coordinates = "$group:${project.text("artifactId")}:$version",
                // A name made of a property (`${project.artifactId}`) is no name.
                name = project.text("name")?.takeUnless { "\${" in it },
                packaging = project.text("packaging") ?: "jar",
                parent = parent,
                licences = licences,
            )
        }
    }
}

/** Writes `licences/libraries.json`: each library's name, coordinates, version and licences. */
abstract class LibraryLicences : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val poms: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val parents: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val parents = parents.files.map(Pom::read).associateBy { it.coordinates }
        fun licences(pom: Pom): List<Pair<String, String?>> =
            pom.licences.ifEmpty { pom.parent?.let(parents::get)?.let(::licences).orEmpty() }
        val libraries = poms.files.map(Pom::read)
            // A BOM only sets versions: it puts no code in the app.
            .filter { it.packaging != "pom" }
            .sortedBy { (it.name ?: it.coordinates).lowercase() }
        val unnamed = libraries.filter { licences(it).isEmpty() }.map { it.coordinates }
        check(unnamed.isEmpty()) { "No licence in the POM or its parents of: ${unnamed.joinToString()}" }
        val json = libraries.map { pom ->
            val (group, artifact, version) = pom.coordinates.split(":")
            mapOf(
                "name" to (pom.name ?: artifact),
                "coordinates" to "$group:$artifact",
                "version" to version,
                "licences" to licences(pom).map { (name, url) -> mapOf("name" to name, "url" to url) },
            )
        }
        val file = outputDirectory.file("licences/libraries.json").get().asFile
        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
    }
}

val libraryLicences = tasks.register<LibraryLicences>("libraryLicences") {
    description = "Writes the licences of the libraries in the release APK to an asset."
    poms.from(licencePoms)
    parents.from(parentPoms)
    outputDirectory.set(layout.buildDirectory.dir("generated/licences"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(libraryLicences, LibraryLicences::outputDirectory) }
}

// Feeds the merged coverage report (root build.gradle.kts): the debug unit tests only, so the screenshot
// tests aren't run twice.
kover {
    currentProject {
        createVariant("coverage") { add("debug") }
    }
}
