# R8 for the release build.
#
# **This file existing is the point.** `build.gradle.kts` has named it in
# `proguardFiles` since minification was turned on, and it was never created —
# so `./gradlew :app:assembleRelease` failed at `minifyReleaseWithR8` with
# "Supplied proguard configuration does not exist", and had done for as long as
# that line has been there. CI builds debug, so nothing ever noticed: the app
# could not be built for release at all (A49).
#
# **And almost nothing belongs in it**, which is worth stating because the
# first draft of this file did not believe that and added two keeps for
# kotlinx.serialization. Reading the merged configuration R8 actually ran
# (`app/build/outputs/mapping/release/configuration.txt`) settled it: the AGP
# default already keeps `InnerClasses` and the runtime annotations,
# kotlinx.serialization ships its own rules for the serializers, and every
# other dependency ships consumer rules inside its AAR. The justification
# offered for those keeps — a shape decoded at runtime — was not even true of
# this app, which decodes one static `AppState`. They are gone.
#
# What is left is the one thing nothing else in that merged file supplies.

# The line numbers in a crash report are worth more than the bytes they cost,
# and `-renamesourcefileattribute` is what stops the kept file name from being
# the only unobfuscated string in the build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
