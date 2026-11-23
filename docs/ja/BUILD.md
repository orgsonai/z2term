# Z2Term のビルド

## ビルド要件

| 項目 | バージョン |
|---|---|
| Android Studio | Ladybug 2024.3.1 以上 |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 (AGP 内蔵) |
| Gradle | 9.3.1 |
| NDK | 27.0+ |
| CMake | 3.22.1+ |
| その他 | Python 3、curl、tar、sha256sum |
| 最小 SDK | 29 (Android 10) |
| ターゲット SDK | 35 (Android 15) |

## セットアップ

### 1. 同梱物を 1 コマンドで揃える

APK に同梱されるが **git 管理外**の生成物がいくつかあります（`scripts/` が生成・取得）。
clone や clean の直後には存在しないので、マスタースクリプトでまとめて揃えます。集め方をこれ 1 本に
統一しています。ビルドはPC上で実行してください。

```bash
bash scripts/build-bundle.sh
```

2 つの生成スクリプトを順に実行し、最後に欠落がないか点検します。

1. `build-z2root.sh` → `libz2root.so` / `libz2accept.so`（NDK が必要）
2. `fetch-fonts.sh` → `IBMPlexMono` / `JetBrainsMono` / `FiraCode` の `-Regular.ttf`（1.0.4 から git に入っているので、ふだんは何もしません）

最後の点検で同梱物の `OK` / `MISS` を表示します。rootfs は実行時取得なのでビルド入力ではありません。

同梱物ごとの詳細: [app/src/main/assets/README.md](../../app/src/main/assets/README.md) · [app/src/main/jniLibs/README.md](../../app/src/main/jniLibs/README.md)

### 2. ビルド

```bash
./gradlew assembleRelease
# 出力: app/build/outputs/apk/release/app-release.apk
```

(fork 側で署名鍵が無くても OK — `build.gradle.kts` は `keystore.properties` 不在時 debug 鍵にフォールバックします)

### 3. インストール

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## プロジェクト構造

```
z2term/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                  ← Alpine rootfs を配置
│       ├── cpp/                     ← JNI ネイティブコード
│       │   ├── CMakeLists.txt
│       │   └── pty_jni.cpp
│       ├── java/com/zerotoship/z2term/
│       │   ├── Z2TermApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── channel/             ← ProcessChannel / SshChannel (M5)
│       │   ├── core/                ← TerminalSession + SessionManager
│       │   ├── pty/                 ← PTY 抽象化
│       │   ├── proot/               ← Linux起動（legacy package名）
│       │   ├── distro/              ← rootfs 展開 (Alpine + Ubuntu)
│       │   ├── emulator/            ← VT100/xterm エミュレータコア
│       │   ├── settings/            ← DataStore 永続化
│       │   ├── service/             ← TerminalService / AudioBridge (foreground + WakeLock)
│       │   ├── gui/                  ← GUI (Xvnc + 内蔵 RFB クライアント / GuiSession)
│       │   ├── saf/                  ← SAF DocumentsProvider
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme + カスタムフォント
│       │       ├── settings/        ← 設定 UI
│       │       ├── ssh/             ← SSH プロファイル UI (M5)
│       │       └── terminal/        ← ターミナル UI + Renderer + キーマッパー
│       ├── jniLibs/                 ← z2root生成物
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── ja/                        ← 日本語ドキュメント
│   │   ├── DESIGN-SPEC.md         ← 設計書 兼 仕様書（技術文書）
│   │   └── HANDBOOK.md            ← 利用者向けハンドブック
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   └── HANDBOOK.md            ← getting started handbook
│   ├── images/                    ← スクリーンショット等（共通）
│   ├── RELEASE.md                 ← リリース手順
│   ├── FDROID.md                  ← F-Droid 提出手順
│   └── SSH-INTO-Z2TERM.md
├── metadata/                     ← F-Droid メタデータ（店頭表示）
└── .github/workflows/build.yml   ← CI (ビルド・lint・テスト・署名済み release APK)
```

## ビルドバリアント

違いは build type だけです。配布フレーバー（`full` / `foss`）は 0.8.359 で廃止しました
（全員が同じ 1 本を使い、rootfs は常に実行時取得）。

| Build type | applicationId | ランチャー表示名 |
|---|---|---|
| `release` | `com.zerotoship.z2term` | `Z2Term` |
| `debug` | `com.zerotoship.z2term.debug2` | `Z2Term dbg2` |

接尾辞のおかげで debug を release の隣に入れられ、名前で見分けが付きます。

```bash
./gradlew assembleDebug
```

## 動作確認の流れ

1. ビルド・インストールし、OSを選んで初回取得を完了する。
2. `z2version` が `engine : z2root` を示すことと、各パッケージマネージャーの動作を確認する。

### z2root コマンド群テスト（`scripts/z2root-cmdtest.sh`）

「今後も *壊れやすいコマンド* がエラーなく動く」ことを確認する回帰スモーク。
z2root の難所（ptrace/seccomp・fakeroot 偽装・パス変換・/proc 偽装・pty・大量
fork/exec・ld.so reloc）を踏むコマンドに絞り、cd/ls のような自明系は入れない。
狙いは「systemic な退行を *多数のコマンドが一斉に落ちる* 形で一発検知し、コマンド
ごとの後追い修正をやらないで済む」こと。z2root タブのゲスト内でそのまま実行:

```sh
sh scripts/z2root-cmdtest.sh              # 標準（ネット/ビルド込み）
SKIP_NET=1   sh scripts/z2root-cmdtest.sh # ネット/パッケージ系をスキップ
SKIP_BUILD=1 sh scripts/z2root-cmdtest.sh # cc コンパイル等をスキップ
RUN_SSHD=1   sh scripts/z2root-cmdtest.sh # dropbear ループバック ssh（z2root 単独だとセッションが落ちる可能性）
RUN_PRIV=1   sh scripts/z2root-cmdtest.sh # losetup/mount など真に root が要る操作も実行（非 root では EPERM が正常）
```

POSIX sh／busybox ash 互換で、**未導入コマンドは fail でなく skip** するので
どのディストリでも同じに走る＝各ゲストで回して「非ゼロ終了一覧が空」になれば
OS 差なく健全、と読める。10 グループ: ①ランタイム実起動（claude headless と
`--version` の対比・node spawn・python venv/mp/ssl・ripgrep）②VCS 重い操作
（clone/gc/checkout＝hardlink/pack/rename）③パッケージ管理（apt/apk/dnf/pacman・
pip/venv・npm）④pty/端末（script/tmux/stty・`/dev/pts`・任意で dropbear）
⑤/proc・fakeroot 境界 ⑥ビルド（cc execve chain＋ld.so reloc）⑦パス変換/symlink
canonicalize ⑧ディスク/FS（dd・mkfs・parted をファイル相手に。root 系は
`RUN_PRIV`）⑨IPC/特殊 syscall（AF_UNIX・FIFO・flock・inotify・xattr・
copy_file_range・nested ptrace(strace/gdb)・Go 生 syscall・sqlite3・rsync）
⑩名前解決/TLS（getent・curl TLS・nslookup）。出力は画面と
`/tmp/z2root-cmdtest-<時刻>.log`、末尾に非ゼロ終了一覧。

注: `io_uring`（ptrace/seccomp を丸ごとバイパス）や `statx`/`openat2` のフック漏れ
はコマンドテストでは捕まらない＝seccomp フィルタ側で確認すること。
