pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val localProps = file("local.properties")
        if (localProps.exists()) {
            localProps.inputStream().use { properties.load(it) }
        }
        val localFlutter = properties.getProperty("flutter.sdk")
        if (localFlutter != null) {
            file(localFlutter)
        } else {
            val flutterRoot = System.getenv("FLUTTER_ROOT")
                ?: System.getenv("FLUTTER_HOME")
                ?: System.getenv("FLUTTER_SDK")
            if (flutterRoot != null) {
                file(flutterRoot)
            } else {
                // Last resort: rely on PATH or default location (flutter-action puts it in PATH)
                file(System.getenv("HOME")?.let { "$it/flutter" } ?: "../flutter")
            }
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
