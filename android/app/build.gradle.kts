plugins {
    // AGP 9 carries Kotlin support itself; there is no kotlin-android plugin
    // to apply any more. Compose and serialization are still their own.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase, for push (S19), read out of `google-services.json` when the file
// is there: the values the google-services plugin would have turned into
// resources, as BuildConfig fields instead, one set per package. No plugin,
// on purpose — a build without the file is a build without Firebase, and
// nothing else about it changes (services/Push.kt). Register both packages,
// app.readribbon and app.readribbon.debug, in the Firebase project.
val firebaseClients: Map<String, Map<String, String>> = run {
    val config = file("google-services.json")
    if (!config.exists()) return@run emptyMap()
    @Suppress("UNCHECKED_CAST")
    val root = groovy.json.JsonSlurper().parse(config) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val projectInfo = root["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val clients = root["client"] as List<Map<String, Any?>>
    clients.associate { client ->
        @Suppress("UNCHECKED_CAST")
        val info = client["client_info"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val androidInfo = info["android_client_info"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val keys = client["api_key"] as List<Map<String, Any?>>? ?: emptyList()
        (androidInfo["package_name"] as String) to mapOf(
            "APP_ID" to (info["mobilesdk_app_id"] as String),
            "API_KEY" to (keys.firstOrNull()?.get("current_key") as String? ?: ""),
            "PROJECT_ID" to (projectInfo["project_id"] as String),
            "SENDER_ID" to (projectInfo["project_number"] as String),
        )
    }
}

fun com.android.build.api.dsl.VariantDimension.firebase(packageName: String) {
    val values = firebaseClients[packageName].orEmpty()
    listOf("APP_ID", "API_KEY", "PROJECT_ID", "SENDER_ID").forEach { key ->
        buildConfigField("String", "FIREBASE_$key", "\"${values[key].orEmpty()}\"")
    }
}

android {
    namespace = "app.readribbon"
    // Compiled against 37 because the current AndroidX libraries require it.
    // What the build book pins is the *target* — the runtime behaviour the
    // app opts in to — and that stays at 36, Android 16, as §12.2 says.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.readribbon"
        // The build book §12.2 says "Android 16+ (API 36)". We compile and
        // target 36 exactly as it asks, and run from Android 13 — see
        // docs/deviations.md A1.
        //
        // 33 rather than 31 because §11 makes transcripts mandatory and
        // Android cannot transcribe a recorded file below it: the only
        // route from a finished recording to a transcript is
        // RecognizerIntent.EXTRA_AUDIO_SOURCE, which arrived in API 33.
        // Shipping to a release that structurally cannot produce a
        // transcript would be shipping a voice note nobody deaf can read.
        minSdk = 33
        targetSdk = 36
        val buildVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildVersionCode
        versionName = "0.1.$buildVersionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["auth0Domain"] = "dev-m45drxnu73cdtjcn.us.auth0.com"
        manifestPlaceholders["auth0Scheme"] = "app.readribbon.debug"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            firebase("app.readribbon")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            firebase("app.readribbon.debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Dark is the product (§12). There is no light resource set to fall back
    // to, so lint must not be able to quietly add one.
    androidResources {
        localeFilters += listOf("en")
    }

    lint {
        // OldTargetApi wants targetSdk raised to whatever is newest. The
        // build book pins the target at API 36 (§12.2) and that is the
        // number that decides runtime behaviour, so raising it is a product
        // decision rather than housekeeping — see docs/deviations.md A1.
        disable += "OldTargetApi"
        // A lint error should stop CI. Warnings should not: the dependency
        // freshness checks go off on their own as the world moves, and a
        // build that fails because someone else published a release is a
        // build nobody trusts.
        abortOnError = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Scripture, the fonts and the grain are copied out of the shared
// resources at build time — see buildSrc/src/main/kotlin/SyncRibbonAssets.kt.
val syncRibbonAssets = tasks.register<SyncRibbonAssets>("syncRibbonAssets") {
    sharedResources.set(rootProject.layout.projectDirectory.dir("../ios/Ribbon/Resources"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncRibbonAssets,
            SyncRibbonAssets::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.browser)
    // Passkeys (§6.10). CredentialManager runs the WebAuthn ceremony and
    // speaks the W3C JSON verbatim, so nothing here has to understand it.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.auth0)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.media3.exoplayer)
    // Push (S19): the six arrive when they happen, once the build carries a
    // Firebase configuration (see the top of this file, and services/Push.kt).
    implementation(libs.firebase.messaging)
    // The background pull — the route everything took before push, and still
    // the one a build without Firebase takes (services/RoomWatch.kt).
    implementation(libs.androidx.work.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
