import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// keystore.properties が存在すれば release 用署名設定として読み込む
val keystorePropsFile: File = rootProject.file("keystore.properties")
val keystoreProps: Properties? = if (keystorePropsFile.exists()) {
    Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
} else null

// local.properties (環境ごと・git 管理外) から任意設定を読む。
// `ndk.version` を書いておくと、その NDK を使う。書かなければ AGP 既定に任せる。
// PC (x86_64) と スマホ z2term の PRoot (ARM64) で必要な NDK が違うため、
// build.gradle.kts には直書きせず、各環境の local.properties で切り替える。
//   PC      : sdk.dir=/opt/android-sdk            (ndk.version は不要 = 既定)
//   z2term  : sdk.dir=/root/android-sdk
//             ndk.version=29.0.14206865           (termux-ndk r29, ARM64 ホスト用)
val localProps: Properties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.zerotoship.z2term"
    compileSdk = 35

    signingConfigs {
        keystoreProps?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FOSS", "false")
        }
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "IS_FOSS", "true")
        }
    }

    /**
     * F-Droid 適合化 (法的対応パッチ):
     *
     * F-Droid は **prebuilt バイナリの同梱を禁止** している。`full` フレーバーは
     * APK 完結のため Alpine rootfs と PRoot/talloc バイナリを APK に同梱するが、
     * `foss` フレーバーはこれらを APK から完全に外し、ユーザー実行時にダウンロード
     * (DistroDownloader) して動作させる必要がある。
     *
     * 実現方法: 大型 prebuilt は `src/full/...` 配下に置き、`src/main/` には共通の
     * Kotlin / リソースだけを残す。これにより `assembleFossDebug` ではバイナリが
     * APK に入らない。**ファイルの物理移動が必要** (方針書 §法的対応 §F-Droid 参照)。
     *
     * 移動先パス:
     *  - `src/main/jniLibs/`                              → `src/full/jniLibs/`
     *  - `src/main/assets/alpine-minirootfs-*.tgz`        → `src/full/assets/`
     *  - `src/main/assets/fonts/` (OFL) は **共通** なので src/main に残す
     *  - `src/main/assets/z2dict.txt` は **共通**
     *  - `src/main/assets/licenses/` (本パッチ追加) は **共通**
     */
    sourceSets {
        getByName("full") {
            jniLibs.srcDirs("src/main/jniLibs", "src/full/jniLibs")
            assets.srcDirs("src/main/assets", "src/full/assets")
        }
        getByName("foss") {
            // foss は src/main/ の共通アセット (フォント / 辞書 / ライセンス) のみ。
            // src/main/jniLibs と src/main/assets/alpine-* が物理的に存在すると Full のままに
            // なるため、foss ビルドを実際に行う前に上記 src/full/ への移動が必須。
            jniLibs.srcDirs("src/foss/jniLibs")
            assets.srcDirs("src/main/assets", "src/foss/assets")
        }
    }

    // local.properties に ndk.version があればそれを使う (環境ごとに切替)。
    // 無ければ設定せず AGP 既定 NDK に任せる (= PC では普段どおり)。
    localProps.getProperty("ndk.version")?.takeIf { it.isNotBlank() }?.let {
        ndkVersion = it
    }

    defaultConfig {
        applicationId = "com.zerotoship.z2term"
        minSdk = 29  // Android 10
        targetSdk = 35
        versionCode = 24
        versionName = "0.8.16-alpha"

        // ランチャー表示名 (build type で上書き可)。debug は別 applicationId で
        // release と共存できるので、名前を分けて見分けられるようにする。
        manifestPlaceholders["appLabel"] = "Z2Term"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // M7 同梱方針: Alpine rootfs + PRoot は arm64-v8a のみ同梱する。
            // 32bit デバイスは現代の Android では希少なので非対応。
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-29"
                )
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // ランチャーで release と区別できるよう表示名を変える。
            manifestPlaceholders["appLabel"] = "Z2Term debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore.properties が無ければ debug 鍵で署名 (CI なし環境向け)
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.x の DSL: kotlin { compilerOptions { ... } } はトップレベル
    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // ⚠️ M7: PRoot を実ファイルとして実行する必要があるため legacy packaging に。
            //
            // useLegacyPackaging = false (modern default) では .so は APK 内に
            // 非圧縮 mmap 用に格納され、nativeLibraryDir に「実体ファイル」は
            // 配置されない (dlopen 経由で APK から直接マップする最適化)。
            // PtyProcess.create で実ファイルパスから execve を呼ぶ Z2Term では
            // `File.exists()` が false になり PRoot 未検出になる。
            //
            // true にすると install 時に /data/app/.../lib/<abi>/ に展開され、
            // applicationInfo.nativeLibraryDir 配下に通常のファイルとして
            // 存在するようになる。APK インストール直後の最初の起動はやや遅くなる
            // が、execve できないと話にならないのでこちらが必須。
            useLegacyPackaging = true
        }
    }

    androidResources {
        // AGP/aapt は assets 内の `.gz` 拡張子付きファイルを「サーバから配信前提で
        // 解凍して APK 内に格納し直す」最適化をかける (e.g. foo.tar.gz → foo.tar)。
        // これだと `assets.open("alpine-minirootfs-aarch64.tar.gz")` が失敗するので
        // tar.gz と tar を no-compress 指定で素通しに。
        noCompress += listOf("tar.gz", "tar")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // DataStore（設定永続化）
    implementation(libs.androidx.datastore.preferences)

    // SSH クライアント (M5)
    implementation(libs.jsch)
    implementation(libs.bouncycastle)

    // XZ 解凍 (DL distro の .tar.xz、例: Kali rootfs)
    implementation(libs.xz)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
