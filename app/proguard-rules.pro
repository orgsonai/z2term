# Z2Term ProGuard rules
# Release は minify + shrinkResources を有効化。

# ───────── JNI ─────────
# native メソッドが参照する Kotlin/Java シンボル
-keepclasseswithmembernames class * {
    native <methods>;
}
# PtyProcess は JNI コードから createFileDescriptor をリフレクションで触る可能性
-keep class com.zerotoship.z2term.pty.PtyProcess { *; }
-keep class com.zerotoship.z2term.pty.PtyProcess$Companion { *; }

# ───────── Compose ─────────
# Compose Compiler が生成するクラスは reflection で参照される
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# ───────── Kotlinx Coroutines ─────────
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.flow.** { *; }

# ───────── DataStore ─────────
-keep class androidx.datastore.** { *; }

# ───────── JSch (SSH) ─────────
# JSch は内部で reflection を多用 (KeyExchange / Cipher 動的解決)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
# JZlib は JSch から optional に呼ばれる
-dontwarn com.jcraft.jzlib.**

# ───────── Data classes ─────────
# JSON シリアライズで使う data class
-keepclassmembers class com.zerotoship.z2term.channel.SshProfile { *; }
-keepclassmembers class com.zerotoship.z2term.distro.DistroSpec { *; }

# ───────── Crash logs ─────────
# Stack trace に行番号を残す
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
