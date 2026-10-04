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
        // The fixtures are read by path from the repository, as the corpus
        // tests read the bundled Scripture, so they are not resources.
        .testTarget(name: "RibbonCoreTests", dependencies: ["RibbonCore"], exclude: ["Fixtures"]),
    ]
)
