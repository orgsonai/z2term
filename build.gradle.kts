// Z2Term ルート build.gradle.kts
// AGP 9.1.1 / Kotlin 2.2.10 (Kotlin は AGP 9 が内蔵するので kotlin-android は適用しない)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
