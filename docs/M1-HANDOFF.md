# M1 ハンドオフドキュメント

最終更新: 2026-05-15
バージョン: 0.1.0-alpha (M1: PoC)

## このドキュメントの目的

Z2Term Milestone 1 (PoC) の実装状態を引き継ぎ可能な形で記録する。
次のセッションや次のマイルストーン (M2) に進む際の参照資料。

## M1 達成範囲

### ✅ 完了

| 項目 | ファイル | 状態 |
|---|---|---|
| Gradle ビルド設定 | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | 完了 |
| AndroidManifest | `app/src/main/AndroidManifest.xml` | M1 で必要な権限のみ宣言 |
| ZTS Theme (Compose) | `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` | 完了 |
| アプリアイコン (アダプティブ) | `res/drawable/ic_launcher_*.xml`, `mipmap-anydpi-v26/` | ZTS グリーン仮ロゴ |
| Splash 画面 | `res/drawable/ic_splash.xml` | Android 12+ API 対応 |
| PTY ネイティブ実装 | `cpp/pty_jni.cpp` + `cpp/CMakeLists.txt` | forkpty / signal / resize 実装済み |
| PTY Kotlin ラッパー | `pty/PtyProcess.kt` | 完了 |
| PRoot 起動マネージャ | `proot/ProotLauncher.kt` | Alpine 起動 + Android sh フォールバック |
| Alpine 展開 | `distro/DistroInstaller.kt` | 手書き tar パーサで実装 |
| ターミナル ViewModel | `ui/terminal/TerminalViewModel.kt` | 状態管理 + 入出力ハンドリング |
| ターミナル UI | `ui/terminal/TerminalScreen.kt` | Compose 実装 |
| Application エントリ | `Z2TermApplication.kt`, `MainActivity.kt` | 完了 |

### ⚠️ 手動配置が必要なもの

| ファイル | 配置先 | 入手元 |
|---|---|---|
| `alpine-minirootfs-aarch64.tar.gz` | `app/src/main/assets/` | https://dl-cdn.alpinelinux.org/alpine/ |
| `alpine-minirootfs-armv7.tar.gz` | `app/src/main/assets/` | 同上 |
| `libproot.so` (aarch64) | `app/src/main/jniLibs/arm64-v8a/` | Termux pkg から抽出 |
| `libproot_loader.so` (aarch64) | 同上 | 同上 |
| `libproot.so` (armv7) | `app/src/main/jniLibs/armeabi-v7a/` | 同上 |
| `libproot_loader.so` (armv7) | 同上 | 同上 |

詳細は `app/src/main/assets/README.md` と `app/src/main/jniLibs/README.md` を参照。

### ❌ M1 ではあえて実装していないもの

- **VT100 / ANSI エスケープシーケンス解釈** → M2
- **マルチタブ** → M2-M3
- **Foreground Service / バックグラウンド維持** → M3
- **マルチディストロ (Ubuntu / Arch / Kali)** → M4
- **コマンドランチャー** → M5
- **WebView / X11 / VNC タブ** → M6
- **設定画面** → M7
- **Play Store 配信** → M8

## アーキテクチャ要点

### PTY 実装の流れ

```
[Kotlin/Java 層]
    ↓ JNI 呼び出し
[Native 層 (cpp/pty_jni.cpp)]
    ↓ forkpty(3)
[親プロセス] master_fd を取得 → Java FileDescriptor 化
    [子プロセス]
        ↓ setsid + ioctl(TIOCSCTTY)
        ↓ chdir
        ↓ setup_terminal_modes (termios 設定)
        ↓ execve(command, argv, envp)
        → /system/bin/sh または proot
```

### PRoot 経由の起動チェーン

```
PtyProcess.create(proot, ["proot", "--kill-on-exit", "-0", "-r", rootfs, "-b", "/dev", ...])
    ↓
proot バイナリ (libproot.so) が PTY 内で起動
    ↓
proot が rootfs にて chroot 相当の処理を実行
    ↓
Alpine の /bin/sh が起動
    ↓
ユーザー入力受付開始
```

### 起動シーケンス (TerminalViewModel.startTerminal())

```
1. ProotLauncher.isProotAvailable() チェック
   ├─ false → Android sh フォールバック
   └─ true → 次へ

2. ProotLauncher.isDistroReady("alpine") チェック
   ├─ false → DistroInstaller.installAlpine() 実行
   │          ├─ 失敗 → Android sh フォールバック
   │          └─ 成功 → 次へ
   └─ true → 次へ

3. ProotLauncher.launch("alpine") 実行
   ├─ 失敗 → Android sh フォールバック
   └─ 成功 → 入出力ループ開始
```

## 動作確認シナリオ

### シナリオ A: proot / rootfs なしで起動

```
期待動作:
1. アプリ起動
2. "⚠ PRoot バイナリが見つかりません。Android sh モードで起動します。" が表示
3. プロンプト "z2term:android $" のような感じで sh が起動
4. `ls /system/bin` 等のコマンドが動作
5. 上部バッジに "android-sh" 表示
```

### シナリオ B: Alpine rootfs を配置、proot なし

```
期待動作:
1. アプリ起動
2. "⚠ PRoot バイナリが見つかりません" でフォールバック
3. (Alpine は使われない)
```

### シナリオ C: proot + Alpine 両方配置

```
期待動作:
1. アプリ起動
2. 初回起動時 "📦 Alpine Linux を初回展開しています…"
3. "✓ Alpine 展開完了" 表示
4. "🚀 Alpine Linux を起動中…" 表示
5. Alpine の `/bin/sh` プロンプト表示 (デフォルトは `/ #`)
6. `cat /etc/alpine-release` で 3.x.x が返る
7. 上部バッジに "alpine" 表示
8. `apk update && apk add bash` で bash 追加可能
```

## 既知の課題

### 1. ANSI エスケープシーケンス未対応

現状の `TerminalScreen.kt` は出力をそのまま append しているだけ。
ターミナル上で色付き出力や `clear` が機能しない。
→ M2 で `termux/terminal-emulator` を移植する必要あり。

### 2. 大きな出力でメモリ膨張

`uiState.output` が単純な String なので、大量出力で OOM 可能性あり。
→ M2 でリングバッファ化 + スクロールバック実装。

### 3. ターミナルサイズ固定 24x80

Compose の現在の View サイズに合わせた動的調整が未実装。
→ M2 で `BoxWithConstraints` + フォントメトリクス計測で対応。

### 4. PRoot v6.x の loader 配置

新しい proot (v6+) は loader を環境変数 `PROOT_LOADER` で指定する。
M1 では設定済みだが、実機で動作確認していない。
→ 動かない場合は `PROOT_LOADER` のパス確認 or proot バージョン下げる。

### 5. SELinux による execve 拒否の可能性

一部の Android 端末で `nativeLibraryDir` 配下のバイナリが
SELinux ポリシーで `execve` 拒否される報告あり。
→ 発生時は `cacheDir` 配下に proot をコピーしてから実行する案を検討。

## ファイル一覧

```
z2term-m1/
├── .gitignore
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── docs/
│   └── M1-HANDOFF.md (このファイル)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   └── README.md
        ├── cpp/
        │   ├── CMakeLists.txt
        │   └── pty_jni.cpp
        ├── java/com/zerotoship/z2term/
        │   ├── MainActivity.kt
        │   ├── Z2TermApplication.kt
        │   ├── distro/
        │   │   └── DistroInstaller.kt
        │   ├── proot/
        │   │   └── ProotLauncher.kt
        │   ├── pty/
        │   │   └── PtyProcess.kt
        │   └── ui/
        │       ├── terminal/
        │       │   ├── TerminalScreen.kt
        │       │   └── TerminalViewModel.kt
        │       └── theme/
        │           ├── Color.kt
        │           ├── Theme.kt
        │           └── Type.kt
        ├── jniLibs/
        │   └── README.md
        └── res/
            ├── drawable/
            │   ├── ic_launcher_background.xml
            │   ├── ic_launcher_foreground.xml
            │   └── ic_splash.xml
            ├── mipmap-anydpi-v26/
            │   ├── ic_launcher.xml
            │   └── ic_launcher_round.xml
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            ├── values-ja/
            │   └── strings.xml
            ├── values-night/
            │   └── (空、M7 で対応)
            └── xml/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

## M2 への引き継ぎ事項

M2 ターミナルエミュレータ実装で対応すべき項目:

1. **`termux/terminal-emulator` の組み込み**
   - GitHub: https://github.com/termux/termux-app/tree/master/terminal-emulator
   - Apache 2.0 ライセンス
   - Java 実装、Kotlin から呼び出し可能
   - `TerminalSession`, `TerminalEmulator`, `TerminalBuffer` 等を移植

2. **VT100 / xterm エスケープシーケンス**
   - CSI シーケンス (`\e[...`)
   - SGR (色指定)
   - カーソル制御
   - スクリーンバッファ操作

3. **動的端末サイズ**
   - Compose の `BoxWithConstraints` で利用可能領域を取得
   - フォントメトリクスから rows/cols を計算
   - `PtyProcess.resize()` で PTY 側に通知

4. **スクロールバック**
   - リングバッファ (デフォルト 2000 行)
   - ユーザー設定で 1000-50000 行に変更可能

5. **コピー & ペースト**
   - 長押しで選択モード
   - クリップボード連携

## 開発時の注意事項

### NDK のインストール

Android Studio の SDK Manager から:
- NDK (Side by side) 最新版
- CMake 3.22.1 以上

### NDK バージョン指定

`app/build.gradle.kts` で固定するならこう:

```kotlin
android {
    ndkVersion = "27.0.12077973"  // 例
}
```

### initial sync で「NDK not configured」エラーが出たら

```
File → Project Structure → SDK Location → Android NDK location
```
で NDK のパスを設定。

### ARM 32bit のテスト

実機 Pixel 6 等は ARM 64bit のみで 32bit ABI は動かない。
32bit テストには ARM 32bit 端末 or Android Emulator (armeabi-v7a イメージ) が必要。
M1 段階では 64bit 実機で動けば OK とする。

### CMake のキャッシュトラブル

ビルドエラー時:
```bash
./gradlew clean
rm -rf app/.cxx app/build
./gradlew assembleDebug
```

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 初版実装 |
