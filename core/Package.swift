// swift-tools-version: 5.9
import PackageDescription

// RibbonCore is the platform-independent domain layer: the object model,
// the fire mechanics, time rules, and the Scripture data model.
// It deliberately imports Foundation only, so it can be tested anywhere
// (including Linux CI) and reused by a future web or Android build layer.
let package = Package(
    name: "RibbonCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "RibbonCore", targets: ["RibbonCore"])
    ],
    targets: [
        .target(name: "RibbonCore"),
        .testTarget(name: "RibbonCoreTests", dependencies: ["RibbonCore"]),
    ]
)
