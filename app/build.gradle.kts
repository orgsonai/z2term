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

    // 配布は 1 種類だけ (0.8.359)。以前は full (Alpine rootfs を APK 同梱) と
    // foss (実行時取得) の 2 フレーバーに分けていたが、full は初回ダウンロードが省ける以外の
    // 価値が無く、利用者に「どちらを入れるのか」を選ばせるだけだったので廃止した。
    // 実行エンジン z2root もディストロも全ビルド共通で、rootfs は初回に公式 CDN から取得する。

    // local.properties に ndk.version があればそれを使う (環境ごとに切替)。
    // 無ければ設定せず AGP 既定 NDK に任せる (= PC では普段どおり)。
    localProps.getProperty("ndk.version")?.takeIf { it.isNotBlank() }?.let {
        ndkVersion = it
    }

    defaultConfig {
        applicationId = "com.zerotoship.z2term"
        minSdk = 29  // Android 10
        targetSdk = 35
        versionCode = 412
        versionName = "0.8.404-alpha"

        // ランチャー表示名 (build type で上書き可)。debug は別 applicationId で
        // release と共存できるので、名前を分けて見分けられるようにする。
        manifestPlaceholders["appLabel"] = "Z2Term"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // z2root は arm64-v8a を対象とする。
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

            // ⛔ fork される test JVM のヒープ上限を必ず明示する。
            // 既定は **物理メモリの 1/4** なので、メモリの少ない端末では Gradle
            // デーモンの隣に 2GB 近い JVM がもう 1 本立ち、OS 側のメモリ回収に
            // 巻き込まれて **テストが無言で固まる**（オンデバイス開発で実際に踏んだ。
            // コンパイルは通るのに testDebugUnitTest だけが進まなくなる）。
            // ここのテストは Android に触れない純ロジックなので 512MB で足りる。
            all { it.maxHeapSize = "512m" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // ⚠ **`+` を外さないこと (3.22.1 以上、の意)。** 厳密固定にすると AGP は
            // 「その版ちょうど」を探し、無ければ SDK から自動ダウンロードする。配布されて
            // いるのは x86-64 版だけなので、**aarch64 の実機ビルドでは動かないものを
            // 掴んで exit 127 で落ちる**（local.properties の `cmake.dir` を書いても、
            // 版の厳密一致がそちらに勝つ）。`+` にしておけばシステムの cmake
            // (実機では pacman の aarch64 版) が採用される。aapt2 を
            // `android.aapt2FromMavenOverride` で差し替えているのと同じ事情。
            version = "3.22.1+"
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
    description = "z2root.c / z2accept.c / z2attach.c を NDK clang で jniLibs/arm64-v8a の lib*.so へ再ビルド"
    val script = rootProject.file("scripts/build-z2root.sh")
    // z2root.c / z2accept.c / z2attach.c / スクリプトのいずれかが変われば再ビルド。変わらなければ up-to-date。
    // ⚠ **スクリプトがビルドする .c は 1 つ残らずここに並べる (0.8.370)**。z2attach.c が漏れていたため、
    //    直しても入力が変わらず up-to-date で飛ばされ、**古い libz2attach.so が APK に入り続けた**。
    //    「ソースは直っているのに実機の挙動が変わらない」という、原因の見えない形で出る。
    inputs.file(layout.projectDirectory.file("src/main/cpp/z2root/z2root.c"))
    inputs.file(layout.projectDirectory.file("src/main/cpp/z2accept/z2accept.c"))
    inputs.file(layout.projectDirectory.file("src/main/cpp/z2attach/z2attach.c"))
    inputs.file(script)
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libz2root.so"))
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libz2accept.so"))
    outputs.file(layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libz2attach.so"))
    // NDK は build-z2root.sh が自己解決する(ANDROID_NDK_HOME 等の env / local.properties の
    // sdk.dir+ndk.version / ANDROID_HOME 配下の ndk)。Exec は親 env を継承するため追加指定不要。
    commandLine("bash", script.absolutePath)
}

// jniLibs マージ前に必ず z2root を再ビルドさせる(stale .so 同梱を構造的に防ぐ)。
// z2root/z2accept は src/main/jniLibs に出力する(ソースビルドのため F-Droid 適合)。
// 実行エンジンはこの z2root だけで、proot prebuilt は 0.8.328 で削除済み。
tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("JniLibFolders")
}.configureEach { dependsOn(buildZ2rootNative) }

// ---------------------------------------------------------------------------
// git 管理外の「同梱必須物」の欠落検出。
//
// fonts(fetch-fonts.sh) は .gitignore 対象で、clone・clean 後に消えても git では
// 復元されない。欠けたまま `assembleX` するとフォント無しの APK ができるため、
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

// fonts は src/main/assets/fonts に置く。
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

// fonts は assets マージ前に検査。
tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("Assets")
}.configureEach { dependsOn(verifyBundledFonts) }

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
    // 入力メソッド (Z2ImeService) が ComposeView を出すのに要る 3 つのオーナーのうち
    // SavedStateRegistry のぶん。Activity 経由では推移的に入るが、Service からは自分で持つ。
    implementation(libs.androidx.savedstate.ktx)

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
