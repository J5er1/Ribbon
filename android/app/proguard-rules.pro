# R8 for the release build.
#
# **This file existing is the point.** `build.gradle.kts` has named it in
# `proguardFiles` since minification was turned on, and it was never created —
# so `./gradlew :app:assembleRelease` failed at `minifyReleaseWithR8` with
# "Supplied proguard configuration does not exist", and had done for as long as
# that line has been there. CI builds debug, so nothing ever noticed: the app
# could not be built for release at all (A49).
#
# Almost nothing belongs here. Every library this app depends on ships its own
# consumer rules inside its AAR — androidx.credentials, kotlinx.serialization,
# Compose, WorkManager and the Supabase HTTP path are all covered that way, and
# duplicating their keeps here would only be a second place for them to go
# stale. What is left is the handful of things R8 cannot see for itself.

# Kotlin reflection on the serializable model. kotlinx.serialization's own
# consumer rules keep the generated serializers; these two keep the metadata
# the plugin's lookup path reads, which matters for the one place the app
# decodes into a shape chosen at runtime (the state file's nested models).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# The line numbers in a crash report are worth more than the bytes they cost.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
