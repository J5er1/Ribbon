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
        // target 36 exactly as it asks, and run from Android 12 — see
        // docs/deviations.md. Everything §12.2 requires is present at 31.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
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
    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
