package com.zerotoship.z2term.proot

/**
 * `z2scan` — 自端末/localhost 限定の脆弱性試験ヘルパー。
 *
 * z2term の哲学 (自端末・localhost 限定・非侵襲・外部送信なし・distro 公式パッケージのみ) に
 * 沿わせた「脆弱性試験」コマンド。2 本立て:
 *  - **自己診断** (`z2scan self`): 外部ツール不要。`/proc/net/tcp*`・sshd 設定・SSH 鍵の
 *    パーミッション・world-writable / SUID・PATH 衛生など、z2term 稼働環境の安全性を点検する。
 *  - **スキャナ** (`net`/`host`/`cve`): distro 公式の `nmap`/`lynis`/`trivy` を自動導入して叩く
 *    薄いラッパー。ネットワークスキャンの既定対象は `127.0.0.1` に固定し、外部ホストは
 *    `--allow-remote` の明示と警告でゲートする (無許可のマス標的化を構造的に防ぐ)。
 *
 * スキャナ本体は同梱せず distro 公式パッケージを使う (F-Droid 適合・追加同梱物ゼロ)。結果は
 * ローカル出力のみで外部送信しない。[ProotLauncher.ensureZ2ScanScript] が launch 毎に
 * `/usr/local/bin/z2scan` を上書きするので内容は常に最新。
 *
 * 実装メモ: usage は quote 付き heredoc (`<<'Z2SCAN_USAGE'`) でシェル展開なし。本体ロジックの
 * シェル `$` は Kotlin テンプレートと衝突しないよう全て `${'$'}` (= 変数 `d`) で書く。awk 内の
 * フィールド参照 (`$2`/`$4` 等) は直後が数字で Kotlin ではリテラル扱いになるためそのまま。
 */
fun z2scanScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    val en = lang == "en"

    // --- メッセージ (ja/en) ---
    val mNoPm = if (en)
        "z2scan: no supported package manager (apk/apt-get/pacman) found." else
        "z2scan: 対応パッケージマネージャ (apk/apt-get/pacman) が見つかりません。"
    val mInstalling = if (en) "z2scan: installing" else "z2scan: 導入します:"
    val mInstallFail = if (en) "z2scan: failed to install" else "z2scan: 導入に失敗しました:"
    val mRemoteBlocked = if (en)
        "z2scan: remote target refused. Scan localhost only, or pass --allow-remote for a target you are authorized to test." else
        "z2scan: 外部ターゲットは拒否しました。localhost のみを対象にするか、試験許可のある対象に限り --allow-remote を付けてください。"
    val mRemoteWarn = if (en)
        "z2scan: WARNING scanning a remote host. Only scan systems you are explicitly authorized to test." else
        "z2scan: 警告 外部ホストをスキャンします。明示的に試験を許可された対象のみにしてください。"
    val mNoLynis = if (en)
        "z2scan: lynis not found; falling back to built-in self-check ('z2scan self'). Install via 'z2scan setup'." else
        "z2scan: lynis が無いため内蔵自己診断 ('z2scan self') にフォールバックします。'z2scan setup' で導入できます。"
    val mNoCve = if (en)
        "z2scan: no CVE scanner (trivy/grype) found. Install trivy or grype, then retry." else
        "z2scan: CVE スキャナ (trivy/grype) が見つかりません。trivy か grype を導入してから再実行してください。"

    // self 診断のラベル
    val mSelfHead = if (en) "== z2scan self-check (this device / localhost) ==" else "== z2scan 自己診断 (自端末 / localhost) =="
    val mUidRoot = if (en) "running as uid 0 (note: proot/z2root provides a fake root)" else "uid 0 で実行中 (注: proot/z2root の擬似 root)"
    val mPubPort = if (en) "TCP port listening on all interfaces (not localhost-only), port" else "全インタフェースで待ち受け中の TCP ポート (localhost 限定でない), ポート"
    val mSshdEmpty = if (en) "sshd_config allows empty-password login (PermitEmptyPasswords yes)" else "sshd_config が空パスワードログインを許可 (PermitEmptyPasswords yes)"
    val mSshdPass = if (en) "sshd_config enables password auth (PasswordAuthentication yes); key auth is safer" else "sshd_config がパスワード認証を有効化 (PasswordAuthentication yes); 鍵認証が安全"
    val mSshdRoot = if (en) "sshd_config permits root login (PermitRootLogin yes)" else "sshd_config が root ログインを許可 (PermitRootLogin yes)"
    val mKeyPerm = if (en) "~/.ssh/authorized_keys has loose permissions, mode" else "~/.ssh/authorized_keys のパーミッションが緩い, mode"
    val mSshDir = if (en) "~/.ssh has loose permissions, mode" else "~/.ssh のパーミッションが緩い, mode"
    val mWorldWrite = if (en) "world-writable files found (showing up to 20):" else "誰でも書き込めるファイルを検出 (最大20件表示):"
    val mSuid = if (en) "SUID binaries present (informational; fake root under proot/z2root):" else "SUID バイナリあり (参考; proot/z2root では擬似 root):"
    val mPath = if (en) "PATH contains an empty/'.' element (current dir in PATH is risky)" else "PATH に空要素/'.' が含まれる (カレントディレクトリの PATH 混入は危険)"
    val mFound = if (en) "findings:" else "検出件数:"
    val mClean = if (en) "no obvious issues found." else "目立った問題は見つかりませんでした。"

    val usageText = if (en) """
        z2scan - vulnerability testing for this device / localhost (defensive, no data sent out).

        Scans only localhost by default. Scanners are installed from your distro's official
        packages (nmap/lynis/trivy); nothing is bundled and results stay local.

          z2scan self                  built-in self-check (no external tools): open ports,
                                       sshd config, SSH key perms, world-writable/SUID, PATH
          z2scan setup                 install scanners (nmap, lynis) via apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       nmap TCP scan. target defaults to 127.0.0.1.
                                       A non-local target requires --allow-remote.
          z2scan host                  host audit via lynis (falls back to 'self' if absent)
          z2scan cve                   known-CVE scan of the rootfs via trivy/grype if present

        Only scan systems you are explicitly authorized to test.
    """.trimIndent() else """
        z2scan - 自端末 / localhost 向けの脆弱性試験 (防御目的・外部送信なし)。

        既定では localhost のみを対象にします。スキャナは distro 公式パッケージ
        (nmap/lynis/trivy) から導入し、同梱物は増やしません。結果はローカルに留まります。

          z2scan self                  内蔵の自己診断 (外部ツール不要): 公開ポート・sshd 設定・
                                       SSH 鍵の権限・world-writable/SUID・PATH 衛生
          z2scan setup                 スキャナ (nmap, lynis) を apk/apt-get/pacman で導入
          z2scan net [--allow-remote] [対象]
                                       nmap の TCP スキャン。対象の既定は 127.0.0.1。
                                       localhost 以外の対象には --allow-remote が必須。
          z2scan host                  lynis でホスト監査 (無ければ 'self' にフォールバック)
          z2scan cve                   trivy/grype があれば rootfs の既知 CVE をスキャン

        明示的に試験を許可された対象のみをスキャンしてください。
    """.trimIndent()

    val head = """
        |#!/bin/sh
        |# z2term: 自端末/localhost 限定の脆弱性試験ヘルパー (launch 毎にアプリが再生成)。
        |# usage: z2scan {self|setup|net [--allow-remote] [target]|host|cve|help}
        |
        |has() { command -v "${d}1" >/dev/null 2>&1; }
        |
        |# パッケージマネージャを判定 (apk/apt/pacman)。
        |detect_pm() {
        |  if has apk; then PM=apk
        |  elif has apt-get; then PM=apt
        |  elif has pacman; then PM=pacman
        |  else PM=""; fi
        |}
        |
        |# 指定パッケージが無ければ distro 公式パッケージから一度だけ導入する。
        |ensure_pkg() {
        |  has "${d}1" && return 0
        |  detect_pm
        |  if [ -z "${d}PM" ]; then echo "$mNoPm" >&2; return 1; fi
        |  echo "$mInstalling ${d}1"
        |  case "${d}PM" in
        |    apk)    apk add --no-cache "${d}1" ;;
        |    apt)    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y "${d}1" ;;
        |    pacman) pacman -Sy --noconfirm --needed "${d}1" ;;
        |  esac
        |  if has "${d}1"; then return 0; else echo "$mInstallFail ${d}1" >&2; return 1; fi
        |}
        |
        |FINDINGS=0
        |pass() { printf '[ OK ] %s\n' "${d}1"; }
        |warn() { printf '[WARN] %s\n' "${d}1"; FINDINGS=${d}((FINDINGS+1)); }
        |info() { printf '[INFO] %s\n' "${d}1"; }
        |
        |self_check() {
        |  echo "$mSelfHead"
        |  # 擬似 root 注意 (proot/z2root では uid 0 でも実 root ではない)。
        |  [ "${d}(id -u 2>/dev/null)" = "0" ] && info "$mUidRoot"
        |  # 全インタフェース待ち受けの TCP LISTEN を検出 (local_address のIP部が全0 = 0.0.0.0/::)。
        |  ports=${d}(awk 'NR>1 && $4=="0A"{split($2,a,":"); if(a[1] ~ /^0+$/) print a[2]}' /proc/net/tcp /proc/net/tcp6 2>/dev/null | sort -u)
        |  for p in ${d}ports; do warn "$mPubPort ${d}((0x${d}p))"; done
        |  # sshd 設定 (config を置いた場合のみ。z2term 既定の dropbear ラッパーは安全側)。
        |  cfg=/etc/ssh/sshd_config
        |  if [ -f "${d}cfg" ]; then
        |    grep -qiE '^[[:space:]]*PermitEmptyPasswords[[:space:]]+yes' "${d}cfg" && warn "$mSshdEmpty"
        |    grep -qiE '^[[:space:]]*PasswordAuthentication[[:space:]]+yes' "${d}cfg" && warn "$mSshdPass"
        |    grep -qiE '^[[:space:]]*PermitRootLogin[[:space:]]+yes' "${d}cfg" && warn "$mSshdRoot"
        |  fi
        |  # SSH 鍵まわりのパーミッション。
        |  kf="${d}HOME/.ssh/authorized_keys"
        |  if [ -f "${d}kf" ]; then
        |    m=${d}(stat -c '%a' "${d}kf" 2>/dev/null)
        |    case "${d}m" in 600|400|"") ;; *) warn "$mKeyPerm ${d}m" ;; esac
        |  fi
        |  sd="${d}HOME/.ssh"
        |  if [ -d "${d}sd" ]; then
        |    m=${d}(stat -c '%a' "${d}sd" 2>/dev/null)
        |    case "${d}m" in 700|500|"") ;; *) warn "$mSshDir ${d}m" ;; esac
        |  fi
        |  # world-writable な通常ファイル (主要ディレクトリに限定)。
        |  ww=${d}(find /etc /usr/local/bin "${d}HOME" -xdev -type f -perm -0002 2>/dev/null | head -20)
        |  if [ -n "${d}ww" ]; then warn "$mWorldWrite"; printf '%s\n' "${d}ww" | sed 's/^/    /'; fi
        |  # SUID バイナリ (擬似 root 下では実害は薄い。参考表示)。
        |  suid=${d}(find /usr /bin /sbin -xdev -type f -perm -4000 2>/dev/null | head -40)
        |  if [ -n "${d}suid" ]; then info "$mSuid"; printf '%s\n' "${d}suid" | sed 's/^/    /'; fi
        |  # PATH に空要素 / カレントディレクトリが混ざっていないか。
        |  case ":${d}PATH:" in *::*|*:.:*) warn "$mPath" ;; esac
        |  echo
        |  if [ "${d}FINDINGS" -gt 0 ]; then echo "$mFound ${d}FINDINGS"; return 1; else echo "$mClean"; return 0; fi
        |}
        |
        |cmd="${d}1"; [ ${d}# -gt 0 ] && shift
        |case "${d}cmd" in
        |  ""|help|-h|--help)
        |    cat <<'Z2SCAN_USAGE'
    """.trimMargin()

    val tail = """
        |Z2SCAN_USAGE
        |    ;;
        |  self)
        |    self_check; exit ${d}?
        |    ;;
        |  setup)
        |    rc=0
        |    for pkg in nmap lynis; do ensure_pkg "${d}pkg" || rc=1; done
        |    exit ${d}rc
        |    ;;
        |  net)
        |    allow=0
        |    if [ "${d}1" = "--allow-remote" ]; then allow=1; shift; fi
        |    target="${d}{1:-127.0.0.1}"
        |    case "${d}target" in
        |      127.*|localhost|::1|"[::1]"|0.0.0.0) loc=1 ;;
        |      *) loc=0 ;;
        |    esac
        |    if [ "${d}loc" = "0" ] && [ "${d}allow" = "0" ]; then echo "$mRemoteBlocked" >&2; exit 1; fi
        |    [ "${d}loc" = "0" ] && echo "$mRemoteWarn" >&2
        |    ensure_pkg nmap || exit 1
        |    # -sT (TCP connect, root 不要) -Pn (ホスト発見を省略し全ポートへ)。
        |    exec nmap -sT -Pn "${d}target"
        |    ;;
        |  host)
        |    if has lynis; then exec lynis audit system --quick; fi
        |    echo "$mNoLynis" >&2
        |    self_check; exit ${d}?
        |    ;;
        |  cve)
        |    if has trivy; then exec trivy rootfs --scanners vuln --quiet /
        |    elif has grype; then exec grype dir:/
        |    else echo "$mNoCve" >&2; exit 1; fi
        |    ;;
        |  *)
        |    echo "z2scan: unknown subcommand: ${d}cmd" >&2
        |    exec "${d}0" help
        |    ;;
        |esac
    """.trimMargin()

    return head + "\n" + usageText + "\n" + tail + "\n"
}
