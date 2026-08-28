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
 *  - **ベースライン差分** (`self --save` / `diff`, 0.8.243): 毎回フルレポートを出しても、人は
 *    その差に気付けない (毎日読める量ではない)。今の状態を「基準」として保存し、次からは
 *    **増えた・無くなった行だけ**を出す。増えたものがあるときだけ終了コード 1 を返すので、
 *    `z2-when time:daily=…` と組めば「**勝手に増えたものだけ通知**」になる。
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
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)

    // --- メッセージ (ja/en) ---
    val mNoPm = t(
        en = "z2scan: no supported package manager (apk/apt-get/pacman) found.",
        ja = "z2scan: 対応パッケージマネージャ (apk/apt-get/pacman) が見つかりません。",
        "zh-CN" to "z2scan: 找不到支持的包管理器 (apk/apt-get/pacman)。",
        "zh-TW" to "z2scan: 找不到支援的套件管理器 (apk/apt-get/pacman)。"
    )
    val mInstalling = t(en = "z2scan: installing", ja = "z2scan: 導入します:", "zh-CN" to "z2scan: 将要安装:", "zh-TW" to "z2scan: 將要安裝:")
    val mInstallFail = t(en = "z2scan: failed to install", ja = "z2scan: 導入に失敗しました:", "zh-CN" to "z2scan: 安装失败:", "zh-TW" to "z2scan: 安裝失敗:")
    val mRemoteBlocked = t(
        en = "z2scan: remote target refused. Scan localhost only, or pass --allow-remote for a target you are authorized to test.",
        ja = "z2scan: 外部ターゲットは拒否しました。localhost のみを対象にするか、試験許可のある対象に限り --allow-remote を付けてください。",
        "zh-CN" to "z2scan: 已拒绝外部目标。请只扫描 localhost，或者仅对已获授权测试的目标加上 --allow-remote。",
        "zh-TW" to "z2scan: 已拒絕外部目標。請只掃描 localhost，或者僅對已獲授權測試的目標加上 --allow-remote。"
    )
    val mRemoteWarn = t(
        en = "z2scan: WARNING scanning a remote host. Only scan systems you are explicitly authorized to test.",
        ja = "z2scan: 警告 外部ホストをスキャンします。明示的に試験を許可された対象のみにしてください。",
        "zh-CN" to "z2scan: 警告 即将扫描外部主机。请只针对明确获得测试授权的对象。",
        "zh-TW" to "z2scan: 警告 即將掃描外部主機。請只針對明確獲得測試授權的對象。"
    )
    val mNoLynis = t(
        en = "z2scan: lynis not found; falling back to built-in self-check ('z2scan self'). Install via 'z2scan setup'.",
        ja = "z2scan: lynis が無いため内蔵自己診断 ('z2scan self') にフォールバックします。'z2scan setup' で導入できます。",
        "zh-CN" to "z2scan: 没有 lynis，改用内置的自检 ('z2scan self')。可以用 'z2scan setup' 安装。",
        "zh-TW" to "z2scan: 沒有 lynis，改用內建的自檢 ('z2scan self')。可以用 'z2scan setup' 安裝。"
    )
    val mNoCve = t(
        en = "z2scan: no CVE scanner (trivy/grype) found. Install trivy or grype, then retry.",
        ja = "z2scan: CVE スキャナ (trivy/grype) が見つかりません。trivy か grype を導入してから再実行してください。",
        "zh-CN" to "z2scan: 找不到 CVE 扫描器 (trivy/grype)。请先安装 trivy 或 grype 再重新执行。",
        "zh-TW" to "z2scan: 找不到 CVE 掃描器 (trivy/grype)。請先安裝 trivy 或 grype 再重新執行。"
    )

    // self 診断のラベル
    val mSelfHead = t(
        en = "== z2scan self-check (this device / localhost) ==",
        ja = "== z2scan 自己診断 (自端末 / localhost) ==",
        "zh-CN" to "== z2scan 自检 (本设备 / localhost) ==",
        "zh-TW" to "== z2scan 自檢 (本裝置 / localhost) =="
    )
    val mUidRoot = t(
        en = "running as uid 0 (note: proot/z2root provides a fake root)",
        ja = "uid 0 で実行中 (注: proot/z2root の擬似 root)",
        "zh-CN" to "正以 uid 0 运行 (注: proot/z2root 提供的是伪 root)",
        "zh-TW" to "正以 uid 0 執行 (注: proot/z2root 提供的是偽 root)"
    )
    val mPubPort = t(
        en = "TCP port listening on all interfaces (not localhost-only), port",
        ja = "全インタフェースで待ち受け中の TCP ポート (localhost 限定でない), ポート",
        "zh-CN" to "在所有网络接口上监听的 TCP 端口 (不限于 localhost), 端口",
        "zh-TW" to "在所有網路介面上監聽的 TCP 連接埠 (不限於 localhost), 連接埠"
    )
    val mSshdEmpty = t(
        en = "sshd_config allows empty-password login (PermitEmptyPasswords yes)",
        ja = "sshd_config が空パスワードログインを許可 (PermitEmptyPasswords yes)",
        "zh-CN" to "sshd_config 允许空密码登录 (PermitEmptyPasswords yes)",
        "zh-TW" to "sshd_config 允許空密碼登入 (PermitEmptyPasswords yes)"
    )
    val mSshdPass = t(
        en = "sshd_config enables password auth (PasswordAuthentication yes); key auth is safer",
        ja = "sshd_config がパスワード認証を有効化 (PasswordAuthentication yes); 鍵認証が安全",
        "zh-CN" to "sshd_config 启用了密码认证 (PasswordAuthentication yes); 密钥认证更安全",
        "zh-TW" to "sshd_config 啟用了密碼認證 (PasswordAuthentication yes); 金鑰認證更安全"
    )
    val mSshdRoot = t(
        en = "sshd_config permits root login (PermitRootLogin yes)",
        ja = "sshd_config が root ログインを許可 (PermitRootLogin yes)",
        "zh-CN" to "sshd_config 允许 root 登录 (PermitRootLogin yes)",
        "zh-TW" to "sshd_config 允許 root 登入 (PermitRootLogin yes)"
    )
    val mKeyPerm = t(
        en = "~/.ssh/authorized_keys has loose permissions, mode",
        ja = "~/.ssh/authorized_keys のパーミッションが緩い, mode",
        "zh-CN" to "~/.ssh/authorized_keys 的权限过松, mode",
        "zh-TW" to "~/.ssh/authorized_keys 的權限過鬆, mode"
    )
    val mSshDir = t(
        en = "~/.ssh has loose permissions, mode",
        ja = "~/.ssh のパーミッションが緩い, mode",
        "zh-CN" to "~/.ssh 的权限过松, mode",
        "zh-TW" to "~/.ssh 的權限過鬆, mode"
    )
    val mWorldWrite = t(
        en = "world-writable files found (showing up to 20):",
        ja = "誰でも書き込めるファイルを検出 (最大20件表示):",
        "zh-CN" to "发现任何人都可写的文件 (最多显示 20 条):",
        "zh-TW" to "發現任何人都可寫的檔案 (最多顯示 20 條):"
    )
    val mSuid = t(
        en = "SUID binaries present (informational; fake root under proot/z2root):",
        ja = "SUID バイナリあり (参考; proot/z2root では擬似 root):",
        "zh-CN" to "存在 SUID 可执行文件 (仅供参考; 在 proot/z2root 下是伪 root):",
        "zh-TW" to "存在 SUID 可執行檔案 (僅供參考; 在 proot/z2root 下是偽 root):"
    )
    val mPath = t(
        en = "PATH contains an empty/'.' element (current dir in PATH is risky)",
        ja = "PATH に空要素/'.' が含まれる (カレントディレクトリの PATH 混入は危険)",
        "zh-CN" to "PATH 中含有空元素或 '.' (把当前目录混进 PATH 很危险)",
        "zh-TW" to "PATH 中含有空元素或 '.' (把當前目錄混進 PATH 很危險)"
    )
    val mFound = t(en = "findings:", ja = "検出件数:", "zh-CN" to "检出条数:", "zh-TW" to "檢出條數:")
    val mClean = t(en = "no obvious issues found.", ja = "目立った問題は見つかりませんでした。", "zh-CN" to "没有发现明显的问题。", "zh-TW" to "沒有發現明顯的問題。")

    // ベースライン差分 (0.8.243)
    val mSaved = t(en = "z2scan: baseline saved:", ja = "z2scan: 基準を保存しました:", "zh-CN" to "z2scan: 已保存基准:", "zh-TW" to "z2scan: 已儲存基準:")
    val mSaveFail = t(
        en = "z2scan: could not write the baseline:",
        ja = "z2scan: 基準を保存できませんでした:",
        "zh-CN" to "z2scan: 无法保存基准:",
        "zh-TW" to "z2scan: 無法儲存基準:"
    )
    val mNoBase = t(
        en = "z2scan: no baseline yet. Run 'z2scan self --save' once to record the current state.",
        ja = "z2scan: 基準がまだありません。'z2scan self --save' を 1 回実行して今の状態を記録してください。",
        "zh-CN" to "z2scan: 还没有基准。请先执行一次 'z2scan self --save' 记录当前状态。",
        "zh-TW" to "z2scan: 還沒有基準。請先執行一次 'z2scan self --save' 記錄當前狀態。"
    )
    val mNoChange = t(
        en = "[ OK ] no change since the baseline.",
        ja = "[ OK ] 基準から変化はありません。",
        "zh-CN" to "[ OK ] 与基准相比没有变化。",
        "zh-TW" to "[ OK ] 與基準相比沒有變化。"
    )
    val mAdded = t(en = "== new since the baseline ==", ja = "== 基準から増えたもの ==", "zh-CN" to "== 相对基准新增的 ==", "zh-TW" to "== 相對基準新增的 ==")
    val mRemoved = t(en = "== gone since the baseline ==", ja = "== 基準から無くなったもの ==", "zh-CN" to "== 相对基准消失的 ==", "zh-TW" to "== 相對基準消失的 ==")
    val mCleared = t(en = "z2scan: baseline cleared.", ja = "z2scan: 基準を削除しました。", "zh-CN" to "z2scan: 已删除基准。", "zh-TW" to "z2scan: 已刪除基準。")
    val mLangDiff = t(
        en = "z2scan: WARNING the baseline was saved in another language, so everything will look changed. Re-save with 'z2scan self --save'.",
        ja = "z2scan: 警告 基準が別の言語で保存されているため、すべて変化として出ます。'z2scan self --save' で取り直してください。",
        "zh-CN" to "z2scan: 警告 基准是用另一种语言保存的，所以全部都会显示为变化。请用 'z2scan self --save' 重新记录。",
        "zh-TW" to "z2scan: 警告 基準是用另一種語言儲存的，所以全部都會顯示為變化。請用 'z2scan self --save' 重新記錄。"
    )
    val langTag = t(en = "en", ja = "ja", "zh-CN" to "zh-CN", "zh-TW" to "zh-TW")

    val usageText = t(
        en = """
        z2scan - vulnerability testing for this device / localhost (defensive, no data sent out).

        Scans only localhost by default. Scanners are installed from your distro's official
        packages (nmap/lynis/trivy); nothing is bundled and results stay local.

          z2scan self [--save]         built-in self-check (no external tools): open ports,
                                       sshd config, SSH key perms, world-writable/SUID, PATH.
                                       --save also records the result as the baseline.
          z2scan diff [--quiet]        re-run the self-check and print only what changed
                                       since the baseline. Exits 1 when something is new,
                                       so it fits straight into z2-when. --quiet prints
                                       nothing at all when nothing changed.
          z2scan baseline [clear]      show the saved baseline (or delete it)
          z2scan setup                 install scanners (nmap, lynis) via apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       nmap TCP scan. target defaults to 127.0.0.1.
                                       A non-local target requires --allow-remote.
          z2scan host                  host audit via lynis (falls back to 'self' if absent)
          z2scan cve                   known-CVE scan of the rootfs via trivy/grype if present

        Only scan systems you are explicitly authorized to test.
    """.trimIndent(),
        ja = """
        z2scan - 自端末 / localhost 向けの脆弱性試験 (防御目的・外部送信なし)。

        既定では localhost のみを対象にします。スキャナは distro 公式パッケージ
        (nmap/lynis/trivy) から導入し、同梱物は増やしません。結果はローカルに留まります。

          z2scan self [--save]         内蔵の自己診断 (外部ツール不要): 公開ポート・sshd 設定・
                                       SSH 鍵の権限・world-writable/SUID・PATH 衛生。
                                       --save を付けると結果を「基準」として保存します。
          z2scan diff [--quiet]        もう一度診断して、基準から**変わった所だけ**を出します。
                                       増えたものがあるときは終了コード 1 なので、そのまま
                                       z2-when に載せられます。--quiet は変化が無いとき何も
                                       出しません。
          z2scan baseline [clear]      保存した基準を表示 (clear で削除)
          z2scan setup                 スキャナ (nmap, lynis) を apk/apt-get/pacman で導入
          z2scan net [--allow-remote] [対象]
                                       nmap の TCP スキャン。対象の既定は 127.0.0.1。
                                       localhost 以外の対象には --allow-remote が必須。
          z2scan host                  lynis でホスト監査 (無ければ 'self' にフォールバック)
          z2scan cve                   trivy/grype があれば rootfs の既知 CVE をスキャン

        明示的に試験を許可された対象のみをスキャンしてください。
    """.trimIndent(),
        "zh-CN" to """
        z2scan - 面向本设备 / localhost 的漏洞检测 (防御用途，不向外发送任何数据)。

        默认只以 localhost 为对象。扫描器从发行版官方软件包 (nmap/lynis/trivy) 安装，
        不增加随附内容，结果也只留在本地。

          z2scan self [--save]         内置的自检 (不需要外部工具): 开放端口、sshd 配置、
                                       SSH 密钥权限、world-writable/SUID、PATH 卫生。
                                       加上 --save 会把结果保存为“基准”。
          z2scan diff [--quiet]        再检一次，只输出相对基准**变化了的部分**。
                                       有新增时退出码为 1，可以直接挂到 z2-when 上。
                                       --quiet 在没有变化时什么都不输出。
          z2scan baseline [clear]      显示保存的基准 (clear 为删除)
          z2scan setup                 用 apk/apt-get/pacman 安装扫描器 (nmap, lynis)
          z2scan net [--allow-remote] [目标]
                                       nmap 的 TCP 扫描。目标默认是 127.0.0.1。
                                       localhost 以外的目标必须加 --allow-remote。
          z2scan host                  用 lynis 做主机审计 (没有则回退到 'self')
          z2scan cve                   有 trivy/grype 时扫描 rootfs 的已知 CVE

        请只扫描明确获得测试授权的对象。
    """.trimIndent(),
        "zh-TW" to """
        z2scan - 面向本裝置 / localhost 的漏洞偵測 (防禦用途，不向外發送任何資料)。

        預設只以 localhost 為對象。掃描器從發行版官方套件 (nmap/lynis/trivy) 安裝，
        不增加隨附內容，結果也只留在本機。

          z2scan self [--save]         內建的自檢 (不需要外部工具): 開放連接埠、sshd 設定、
                                       SSH 金鑰權限、world-writable/SUID、PATH 衛生。
                                       加上 --save 會把結果儲存為“基準”。
          z2scan diff [--quiet]        再檢一次，只輸出相對基準**變化了的部分**。
                                       有新增時退出碼為 1，可以直接掛到 z2-when 上。
                                       --quiet 在沒有變化時什麼都不輸出。
          z2scan baseline [clear]      顯示儲存的基準 (clear 為刪除)
          z2scan setup                 用 apk/apt-get/pacman 安裝掃描器 (nmap, lynis)
          z2scan net [--allow-remote] [目標]
                                       nmap 的 TCP 掃描。目標預設是 127.0.0.1。
                                       localhost 以外的目標必須加 --allow-remote。
          z2scan host                  用 lynis 做主機審計 (沒有則退回到 'self')
          z2scan cve                   有 trivy/grype 時掃描 rootfs 的已知 CVE

        請只掃描明確獲得測試授權的對象。
    """.trimIndent()
    )

    val head = """
        |#!/bin/sh
        |# z2term: 自端末/localhost 限定の脆弱性試験ヘルパー (launch 毎にアプリが再生成)。
        |# usage: z2scan {self [--save]|diff [--quiet]|baseline [clear]|setup|net [--allow-remote] [target]|host|cve|help}
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
        |# --- ベースライン差分 (0.8.243) -----------------------------------------
        |# 毎回フルレポートを読ませても人は差に気付けない。「前と同じ」を基準として保存し、
        |# 次からは**変わった所だけ**を出す。時刻トリガーと組めば「勝手に増えたものだけ通知」になる。
        |SCAN_DIR="${d}HOME/.z2term/scan"
        |BASE="${d}SCAN_DIR/baseline.txt"
        |
        |# 診断結果から「前回と比べられる事実」だけを取り出す。
        |# [WARN]/[INFO] の行と、その下にぶら下がる字下げ行 (ファイル名の列挙) が対象。
        |# 見出し・[ OK ]・件数・空行は落とす — **実行のたびに変わるものを基準に入れない**。
        |# 並び順で差が出ないよう sort -u で正規化する。
        |state_from() { grep -E '^\[(WARN|INFO)\]|^ ' "${d}1" | sort -u; }
        |
        |# 基準を書く。ヘッダに言語を残すのは、言語を変えた後に「全部変わった」と出る理由が
        |# 読み手に分かるようにするため (メッセージ文字列そのものを比べているので当然そうなる)。
        |write_baseline() {
        |  mkdir -p "${d}SCAN_DIR" 2>/dev/null || { echo "$mSaveFail ${d}BASE" >&2; return 1; }
        |  { printf '# z2scan baseline\n'
        |    printf '# saved: %s\n' "${d}(date '+%Y-%m-%dT%H:%M:%S' 2>/dev/null)"
        |    printf '# lang: %s\n' "$langTag"
        |    state_from "${d}1"
        |  } > "${d}BASE" || { echo "$mSaveFail ${d}BASE" >&2; return 1; }
        |  echo "$mSaved ${d}BASE"
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
        |    # --save のときも診断は 1 回だけ (find が走るので 2 回流すと目に見えて遅い)。
        |    # レポートを一時ファイルへ取り、画面へ出しつつ同じものから基準を作る。
        |    if [ "${d}1" = "--save" ]; then
        |      tmp="${d}{TMPDIR:-/tmp}/z2scan-self.${d}${d}"
        |      self_check > "${d}tmp" 2>&1; rc=${d}?
        |      cat "${d}tmp"
        |      write_baseline "${d}tmp" || rc=1
        |      rm -f "${d}tmp"
        |      exit ${d}rc
        |    fi
        |    self_check; exit ${d}?
        |    ;;
        |  diff)
        |    quiet=0
        |    [ "${d}1" = "--quiet" ] && quiet=1
        |    if [ ! -f "${d}BASE" ]; then echo "$mNoBase" >&2; exit 2; fi
        |    bl=${d}(sed -n 's/^# lang: //p' "${d}BASE" 2>/dev/null | head -1)
        |    [ -n "${d}bl" ] && [ "${d}bl" != "$langTag" ] && echo "$mLangDiff" >&2
        |    tmp="${d}{TMPDIR:-/tmp}/z2scan-diff.${d}${d}"
        |    now="${d}tmp.now"; old="${d}tmp.old"
        |    self_check > "${d}tmp" 2>&1
        |    state_from "${d}tmp" > "${d}now"
        |    grep -v '^#' "${d}BASE" > "${d}old"
        |    # diff コマンドに頼らない (busybox の有無で挙動が割れる)。行の集合の引き算で足りる。
        |    added=${d}(grep -Fxv -f "${d}old" "${d}now" 2>/dev/null)
        |    removed=${d}(grep -Fxv -f "${d}now" "${d}old" 2>/dev/null)
        |    rm -f "${d}tmp" "${d}now" "${d}old"
        |    if [ -z "${d}added" ] && [ -z "${d}removed" ]; then
        |      [ "${d}quiet" = "1" ] || echo "$mNoChange"
        |      exit 0
        |    fi
        |    if [ -n "${d}added" ]; then
        |      echo "$mAdded"; printf '%s\n' "${d}added" | sed 's/^/  + /'
        |    fi
        |    if [ -n "${d}removed" ]; then
        |      echo "$mRemoved"; printf '%s\n' "${d}removed" | sed 's/^/  - /'
        |    fi
        |    # **増えたときだけ 1**。減っただけで通知が飛ぶと、片付けたその日に鳴って信用を失う。
        |    [ -n "${d}added" ] && exit 1
        |    exit 0
        |    ;;
        |  baseline)
        |    case "${d}1" in
        |      clear) rm -f "${d}BASE"; echo "$mCleared"; exit 0 ;;
        |      *) if [ -f "${d}BASE" ]; then cat "${d}BASE"; exit 0; fi
        |         echo "$mNoBase" >&2; exit 2 ;;
        |    esac
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
