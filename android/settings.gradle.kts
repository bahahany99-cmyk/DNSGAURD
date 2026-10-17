pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        file("local.properties").inputStream().use { properties.load(it) }
        val localFlutter = properties.getProperty("flutter.sdk")
        if (localFlutter != null) {
            file(localFlutter)
        } else {
            // Fallback for CI: rely on PATH/flutter wrapper.
            file(System.getenv("FLUTTER_ROOT") ?: "../flutter")
        }
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

include(":app")
