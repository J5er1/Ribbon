// Ribbon for Android. Two modules on purpose:
//   :core  a plain Kotlin/JVM library — the port of RibbonCore. No Android
//          types in it, so the fire engine and the time rules are tested on
//          the JVM in milliseconds, exactly as the Swift side is tested on
//          Linux CI without a Mac.
//   :app   the Compose application (build book §12.2).
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ribbon"
include(":app")
include(":core")
