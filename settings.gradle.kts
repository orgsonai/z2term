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
        // USB シリアルのライブラリだけ JitPack から取る (Maven Central に無いため)。
        // ⚠ group を限定する。限定しないと、ほかの依存まで JitPack へ探しに行く。
        // F-Droid の scanner は jitpack.io を許可された取得先として扱う。
        maven("https://jitpack.io") {
            content { includeGroup("com.github.mik3y") }
        }
    }
}

rootProject.name = "Z2Term"
include(":app")
