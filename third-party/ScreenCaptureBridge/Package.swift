// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ScreenCaptureBridge",
    platforms: [.macOS(.v13)],
    products: [
        .library(name: "ScreenCaptureBridge", type: .dynamic, targets: ["ScreenCaptureBridge"])
    ],
    targets: [
        .target(name: "ScreenCaptureBridge")
    ]
)
