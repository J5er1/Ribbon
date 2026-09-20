plugins {
    // AGP 9 carries Kotlin support itself; there is no kotlin-android plugin
    // to apply any more. Compose and serialization are still their own.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
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

// Robolectric does not resolve the framework jar it runs the tests on through
// Gradle. It fetches `android-all-instrumented` itself, over the network, from
// inside the test JVM, the first time a Robolectric class initialises — so
// Gradle's dependency cache never covered it and every cold run made a live
// call to Maven Central. When one of those calls failed, all eight
// Robolectric-backed classes failed at `classMethod`, before a single test body
// ran, on a diff that had not touched Android at all. A hiccup at somebody
// else's CDN is not a test result, and CI said it was one.
//
// So the jars are declared here, where every other dependency already lives:
// Gradle resolves them, Gradle's cache holds them, and `setup-gradle` restores
// them on a PR run with the rest of that cache. The two system properties on
// the test task below then take Robolectric's own fetcher out of the path
// entirely, rather than merely making it likely to find a warm cache — offline,
// there is no live call left to fail.
//
// **Two configurations rather than one** because these are the same module at
// two versions, and a single configuration would resolve that conflict down to
// one jar. Both are wanted: API 34 for the seven classes pinned with
// `@Config(sdk = [34])`, and API 36 — `targetSdk`, which is Robolectric's
// default — for `SettingsSurviveADamagedFileTest`, which pins nothing.
//
// **Not `testImplementation`**, which would be the obvious place and is the
// wrong one: `android-all-instrumented` is the whole framework, and on the unit
// test classpath it would shadow the stubbed `android.jar` AGP puts there.
// Robolectric wants these in a directory of their own, which is all
// `gatherRobolectricSdks` is.
//
// **The version strings move with Robolectric**, and they are not invented:
// each is the one `org.robolectric.plugins.DefaultSdkProvider` names for that
// API level in the release `libs.versions.toml` pins. A test that asks for an
// SDK not listed here now fails the way an offline resolver fails — loudly,
// naming the jar it wanted — which is the moment to add it, not to let the
// fetcher back in.
val robolectricSdkApi34 = configurations.create("robolectricSdkApi34") { isTransitive = false }
val robolectricSdkApi36 = configurations.create("robolectricSdkApi36") { isTransitive = false }

val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdks")

val gatherRobolectricSdks = tasks.register<Sync>("gatherRobolectricSdks") {
    description = "Collects Robolectric's android-all jars where its offline resolver looks for them."
    from(robolectricSdkApi34, robolectricSdkApi36)
    into(robolectricSdkDir)
}

tasks.withType<Test>().configureEach {
    inputs.files(gatherRobolectricSdks)
        .withPropertyName("robolectricSdks")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    // `robolectric.dependency.dir` alone already selects the offline resolver
    // (LegacyDependencyResolver.pickOne); `robolectric.offline` is set beside
    // it so that a reader — and a future Robolectric — cannot mistake this for
    // a cache hint.
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)
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
    // The only way a notification can arrive while Ribbon is closed (S19).
    // There is no FCM here and no foreground service — see services/RoomWatch.kt
    // for why neither, and what this route can and cannot carry.
    implementation(libs.androidx.work.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    robolectricSdkApi34(libs.android.all.instrumented.api34)
    robolectricSdkApi36(libs.android.all.instrumented.api36)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
