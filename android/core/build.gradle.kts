plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// RibbonCore, in Kotlin. The port of core/Sources/RibbonCore — the object
// model, the fire mechanics (§4.1), the time rules (§4.9), the 66-book
// table and the Scripture data model.
//
// It depends on the Kotlin standard library and kotlinx-serialization and
// nothing else: no Android types, no Compose. That is the same discipline
// the Swift package keeps (Foundation only) and it is what lets both
// implementations be checked against one shared body of tests.
dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
