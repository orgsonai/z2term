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
            // full と別 applicationId で共存するので、ランチャー表示名も分けて見分けられるようにする。
            // release: "Z2Term FOSS" (full は "Z2Term")。debug は buildType が "Z2Term dbg2" に
            // 上書きする (placeholder 優先順位: buildType > flavor) ので foss debug もそれを使う。
            manifestPlaceholders["appLabel"] = "Z2Term FOSS"
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
     * 実現方法: third-party prebuilt (PRoot/talloc) と Alpine rootfs は `src/full/...`
     * 配下に置き、`src/main/` には共通の Kotlin / リソースと、**ソースから再生成される**
     * z2root/z2accept (build-z2root.sh) だけを残す。AGP の main sourceSet (`src/main/jniLibs`)
     * は全 variant に寄与するため、foss でも z2root は同梱されるが proot は同梱されない。
     *
     * 配置:
     *  - PRoot/talloc prebuilt          → `src/full/jniLibs/`   (full のみ。F-Droid 非適合)
     *  - z2root/z2accept (source build) → `src/main/jniLibs/`   (full+foss 共通。F-Droid 適合)
     *  - `src/main/assets/alpine-minirootfs-*.tgz` → `src/full/assets/` (full のみ。foss は実行時 DL)
     *  - `src/main/assets/fonts/` (OFL) / `z2dict.txt` / `licenses/` は **共通** (src/main 据置)
     */
    sourceSets {
        getByName("full") {
            // src/main/jniLibs (z2root) は main から自動寄与。full は proot を追加するのみ。
            jniLibs.srcDirs("src/full/jniLibs")
            assets.srcDirs("src/main/assets", "src/full/assets")
        }
        getByName("foss") {
            // foss は main の jniLibs (z2root/z2accept のみ) と共通アセットを使う。
            // proot prebuilt と alpine rootfs は src/full にあるため foss には入らない。
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
        versionCode = 251
        versionName = "0.8.243-alpha"

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
            applicationIdSuffix = ".debug2"
            versionNameSuffix = "-debug"
            // ランチャーで release と区別できるよう表示名を変える。
            manifestPlaceholders["appLabel"] = "Z2Term dbg2"
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

    // このプロジェクトでは恒常的に意味を持たない検査だけを無効化し、残る警告を「本物」だけにする。
    // 個別の意図的な箇所 (SetWorldReadable / SdCardPath / Typos 等) はここで一律に消さず、
    // 該当箇所に @Suppress / tools:ignore を付けて理由を残す (他の場所では検出を効かせ続けるため)。
    lint {
        disable += setOf(
            // 「新しい版が出ました」通知。上流がリリースするたび増え、本物の警告を埋もれさせる。
            // 依存の更新は意図的に管理しているので、lint で追跡しない。
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            // Google Play の配布ポリシー前提の検査。z2term は Play 非配布 (GitHub Release 直配布) で、
            // 全ファイルアクセスも電池最適化除外も機能上必要な意図的採用。
            "ScopedStorage",
            "BatteryLife",
            // App Bundle の言語分割前提の指摘。APK 直配布なので該当しない。
            "AppBundleLocaleChanges",
            // targetSdk は実機検証済みの版に意図的に固定している (上げるのは検証とセット)。
            "OldTargetApi",
        )
    }

    testOptions {
        unitTests {
            // JVM unit test で android.util.Log 等の未実装 Android API を例外でなく
            // 既定値 (no-op / 0) にする。純ロジックの JUnit テスト (tar 展開等) を
            // Robolectric 無しで回すため。
            isReturnDefaultValues = true
        }
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

// ---------------------------------------------------------------------------
// 自前 ptrace エンジン (z2root) / accept シム (z2accept) を APK ビルド時に必ず
// 現ソース(z2root.c / z2accept.c)から再生成する。これらは jniLibs に lib*.so 名で
// 置く -static 実行ファイル/shim で、CMake(externalNativeBuild) では生成形態が違う
// ため別タスクで NDK clang を直接叩く(scripts/build-z2root.sh)。
//
// 目的: git では z2root.c だけが同期され .so は .gitignore 対象のため、git pull 後に
// build-z2root.sh を手動実行し忘れると古い .so が APK に同梱される事故(stale .so)が
// 繰り返し起きていた。本タスクを jniLibs マージの前段に挟むことで `./gradlew
// assembleX` だけで常にソースと一致した .so が再生成される(手動手順ゼロ)。
val buildZ2rootNative = tasks.register<Exec>("buildZ2rootNative") {
    group = "build"
    description = "z2root.c / z2accept.c を NDK clang で jniLibs/arm64-v8a の lib*.so へ再ビルド"
    val script = rootProject.file("scripts/build-z2root.sh")
    // z2root.c / z2accept.c / スクリプトのいずれかが変われば再ビルド。変わらなければ up-to-date。
    inputs.file(layout.projectDirectory.file("src/main/cpp/z2root/z2root.c"))
    inputs.file(layout.projectDirectory.file("src/main/cpp/z2accept/z2accept.c"))
    inputs.file(script)
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libz2root.so"))
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libz2accept.so"))
    // NDK は build-z2root.sh が自己解決する(ANDROID_NDK_HOME 等の env / local.properties の
    // sdk.dir+ndk.version / ANDROID_HOME 配下の ndk)。Exec は親 env を継承するため追加指定不要。
    commandLine("bash", script.absolutePath)
}

// 全フレーバーの jniLibs マージ前に必ず z2root を再ビルドさせる(stale .so 同梱を構造的に防ぐ)。
// z2root/z2accept は src/main/jniLibs に出力し full/foss 共通で同梱される(ソースビルドのため
// F-Droid 適合)。foss の実行エンジンは proot ではなくこの z2root。
tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("JniLibFolders")
}.configureEach { dependsOn(buildZ2rootNative) }

// ---------------------------------------------------------------------------
// git 管理外の「同梱必須物」の欠落検出。
//
// fonts(fetch-fonts.sh) / proot バイナリ(build-proot.sh) / Alpine rootfs
// (build-alpine-rootfs.sh) は .gitignore 対象で、clone・clean 後に消えても git
// では復元されない。これらが欠けたまま `assembleX` するとビルドは普通に通り、
// 「フォント無し」「PRoot 無し」の壊れた APK を黙って出荷してしまう事故が起きた。
// 対応する merge タスクの前段で実体を検査し、欠落時は再生成スクリプト名つきの
// メッセージでビルドを止める(緊急時は -PallowMissingBundledAssets=true で警告に格下げ)。
// z2root の .so は上の buildZ2rootNative が常に再生成するため、ここでは検査しない。
//
// 実装上の注意: doLast 内で rootProject / logger 等の「Gradle script オブジェクト」を
// 参照すると configuration cache に直列化できず失敗する。リポジトリルートと格下げフラグは
// 構成時に File / Boolean としてローカルに捕捉し、doLast からはそれだけを使う。
val allowMissingBundled: Boolean =
    (project.findProperty("allowMissingBundledAssets") as String?)?.toBoolean() ?: false
val repoRootDir: File = rootProject.projectDir

// fonts は full / foss 共通(src/main/assets/fonts)。両フレーバーで検査する。
val verifyBundledFonts = tasks.register("verifyBundledFonts") {
    group = "verification"
    description = "プログラミングフォント(scripts/fetch-fonts.sh)の同梱漏れを検査"
    val root = repoRootDir
    val allow = allowMissingBundled
    doLast {
        val missing = listOf(
            "app/src/main/assets/fonts/IBMPlexMono-Regular.ttf",
            "app/src/main/assets/fonts/JetBrainsMono-Regular.ttf",
            "app/src/main/assets/fonts/FiraCode-Regular.ttf",
        ).filter { !File(root, it).exists() }
            .map { "  - $it\n      再生成: bash scripts/fetch-fonts.sh" }
        if (missing.isNotEmpty()) {
            val msg = "\n[fonts] git 管理外の同梱必須ファイルが見つかりません:\n" +
                missing.joinToString("\n") +
                "\n  → 上記スクリプトを実行してから再ビルドしてください。" +
                "\n  → 緊急回避: ./gradlew ... -PallowMissingBundledAssets=true (警告に格下げ)"
            if (allow) println("WARNING:$msg") else throw GradleException(msg)
        }
    }
}

// PRoot バイナリ + Alpine rootfs は full フレーバーのみ APK 同梱(foss は実行時 DL)。
val verifyFullBundled = tasks.register("verifyFullBundledArtifacts") {
    group = "verification"
    description = "full フレーバーの PRoot バイナリ / Alpine rootfs の同梱漏れを検査"
    val root = repoRootDir
    val allow = allowMissingBundled
    doLast {
        val missing = mutableListOf<String>()
        listOf(
            "app/src/full/jniLibs/arm64-v8a/libproot.so",
            "app/src/full/jniLibs/arm64-v8a/libproot_loader.so",
            "app/src/full/jniLibs/arm64-v8a/libtalloc.so",
        ).filter { !File(root, it).exists() }
            .forEach { missing += "  - $it\n      再生成: bash scripts/build-proot.sh" }
        // rootfs は build-alpine-rootfs.sh が src/main/assets に出力し、F-Droid 対応で
        // src/full/assets へ移動する運用。full の sourceSet は両方を読むため両方を検査する。
        val rootfsRegex = Regex("""alpine-minirootfs-.*\.(tgz|tar\.gz)""")
        val hasRootfs = listOf("app/src/main/assets", "app/src/full/assets").any { dir ->
            File(root, dir).listFiles { f -> f.name.matches(rootfsRegex) }?.isNotEmpty() == true
        }
        if (!hasRootfs) {
            missing += "  - app/src/{main,full}/assets/alpine-minirootfs-*.tgz\n      再生成: bash scripts/build-alpine-rootfs.sh"
        }
        if (missing.isNotEmpty()) {
            val msg = "\n[full prebuilt] git 管理外の同梱必須ファイルが見つかりません:\n" +
                missing.joinToString("\n") +
                "\n  → 上記スクリプトを実行してから再ビルドしてください。" +
                "\n  → 緊急回避: ./gradlew ... -PallowMissingBundledAssets=true (警告に格下げ)"
            if (allow) println("WARNING:$msg") else throw GradleException(msg)
        }
    }
}

// fonts は全フレーバーの assets マージ前に検査。
tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("Assets")
}.configureEach { dependsOn(verifyBundledFonts) }

// PRoot / rootfs は full フレーバーの jniLibs / assets マージ前に検査。
tasks.matching {
    it.name.startsWith("merge") && it.name.contains("Full") &&
        (it.name.endsWith("JniLibFolders") || it.name.endsWith("Assets"))
}.configureEach { dependsOn(verifyFullBundled) }

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
