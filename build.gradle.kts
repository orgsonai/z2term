// Z2Term ルート build.gradle.kts
// AGP 9.1.1 / Kotlin 2.2.10 (AGP 内蔵)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
