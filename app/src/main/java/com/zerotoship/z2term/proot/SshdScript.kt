package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages

/**
 * `sshd` が設定/引数が無いときに使う既定ポート。
 * 1024 未満は proot(非root) で bind できないため高ポートを既定にする。
 * (sshd_config の Port や `-p` 指定があればそちらが優先)
 */
const val Z2TERM_SSHD_PORT = 2222

/**
 * `sshd` 互換ラッパースクリプト (バックエンドは dropbear)。
 *
 * OpenSSH の `/usr/sbin/sshd` は proot 環境で **権限分離 (privsep) に失敗**し、
 * さらに新しめの OpenSSH では `sshd_config` の `UsePrivilegeSeparation` が
 * 「Bad configuration option」になって起動すらできない。よって proot 下でも
 * 安定動作する dropbear を使う。
 *
 * これを `/usr/local/sbin/sshd` に配置 (ProotLauncher) することで、端末から
 * `sshd` と打つと **通常の sshd のように振る舞う**:
 *  - ポートは `-p` / `-o Port=N` 指定 → なければ `/etc/ssh/sshd_config` の `Port`
 *    → それも無ければ既定 ([Z2TERM_SSHD_PORT]) の優先順で決定
 *  - `-f <config>` で別の設定ファイルを参照、`-D`/`-d` で前景起動、`-t` で設定確認
 *  - dropbear 未導入なら自動 install、既存 dropbear を確実に停止してから起動
 *  - 常駐サーバー (supervisor) 配下 (`Z2_SUPERVISED=1`) では `-D` 相当の**前景常駐**に自動切替。
 *    背景化して即 exit すると supervisor が再起動を繰り返し、そのたび dropbear が kill されて
 *    接続できなくなるため
 *
 * **既定の安全側設定 (法的対応パッチ):**
 *  - **`127.0.0.1` のみで bind** (LAN/WAN に公開しない)。端末の他アプリ間 SSH に限定。
 *  - **空パスワードでの root ログイン禁止** (`-g`)。
 *  - **パスワード認証を全面禁止** (`-s`)。`~/.ssh/authorized_keys` への鍵登録のみ可。
 *  - LAN/WAN 公開を望むときだけ `--lan` 引数または `Z2_SSHD_LAN=1` env で明示有効化
 *    (この場合は鍵認証必須 + 強い警告メッセージを出す)。
 *
 * 注: dropbear が解釈できるのは実質 Port のみ。sshd_config のそれ以外のディレクティブ
 * (PermitRootLogin 等) は反映されない。安全側の制御は本スクリプト引数で完結させる。
 */
/**
 * sshd wrapper が echo する各種メッセージ。アプリ内言語スイッチに追従させる。
 * 生成時 ([dropbearBootstrapScript]) に外から差し込む。
 */
data class SshdScriptStrings(
    val invalidPort: String,
    val configOk: String,
    val installingDropbear: String,
    val dropbearInstallFailed: String,
    val privilegedPortWarn: String,
    val lanExposeWarn: String,
    val noAuthorizedKeys: String,
    val noAuthorizedKeysHint: String,
    val loopbackBind: String,
    val foregroundStart: String,
    val listeningLan: String,
    val listeningLoopback: String,
    val startupFailed: String,
    val deviceIp: String,
    val authKeysHint: String,
    val authKeysExample: String
) {
    companion object {
        fun ja(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ ポート番号が不正です",
            configOk = "sshd(dropbear) 設定 OK",
            installingDropbear = "📦 dropbear が無いので導入します…",
            dropbearInstallFailed = "❌ dropbear を導入できませんでした。ネットワーク接続とパッケージ名を確認してください。",
            privilegedPortWarn = "⚠️ 特権ポート。proot(非root)では bind できない可能性が高いです (1024 以上推奨)。",
            lanExposeWarn = "⚠️ LAN/WAN 公開モード: 鍵認証必須、強い鍵を使うこと。",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys が未設定です。LAN 公開での起動を中止します。",
            noAuthorizedKeysHint = "   公開鍵を ~/.ssh/authorized_keys に登録してから再実行してください。",
            loopbackBind = "🔒 loopback 限定で起動。LAN 公開は --lan か Z2_SSHD_LAN=1。",
            foregroundStart = "▶ dropbear をフォアグラウンド起動 (Ctrl-C で停止)",
            listeningLan = "✅ dropbear listening (root, 鍵認証のみ)",
            listeningLoopback = "✅ dropbear listening (root, 鍵認証のみ, loopback 限定)",
            startupFailed = "❌ dropbear 起動失敗。ログ:",
            deviceIp = "端末IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys が未設定です。クライアントの公開鍵を登録すると接続できます。",
            authKeysExample = "   例: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )
        fun en(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Invalid port number",
            configOk = "sshd(dropbear) config OK",
            installingDropbear = "📦 dropbear is not installed — installing…",
            dropbearInstallFailed = "❌ Could not install dropbear. Check network and package names.",
            privilegedPortWarn = "⚠️ Privileged port; proot (non-root) likely cannot bind (use 1024+).",
            lanExposeWarn = "⚠️ LAN/WAN expose mode: key auth required, use a strong key.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys is empty. Aborting LAN-expose startup.",
            noAuthorizedKeysHint = "   Register your public key in ~/.ssh/authorized_keys and retry.",
            loopbackBind = "🔒 loopback-only. To expose to LAN, pass --lan or set Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear in foreground (Ctrl-C to stop)",
            listeningLan = "✅ dropbear listening (root, key auth only)",
            listeningLoopback = "✅ dropbear listening (root, key auth only, loopback)",
            startupFailed = "❌ dropbear startup failed. Log:",
            deviceIp = "device-ip",
            authKeysHint = "ℹ ~/.ssh/authorized_keys is empty. Register a client public key to connect.",
            authKeysExample = "   e.g. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )
        fun zhCN(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ 端口号不正确",
            configOk = "sshd(dropbear) 配置 OK",
            installingDropbear = "📦 没有 dropbear，正在安装…",
            dropbearInstallFailed = "❌ 无法安装 dropbear。请确认网络连接和软件包名称。",
            privilegedPortWarn = "⚠️ 特权端口。在 proot(非 root) 下很可能 bind 不上 (建议用 1024 以上)。",
            lanExposeWarn = "⚠️ 局域网/广域网开放模式: 必须用密钥认证，并且要用强密钥。",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys 还没有设置。已中止以局域网开放方式启动。",
            noAuthorizedKeysHint = "   请先把公钥登记到 ~/.ssh/authorized_keys 再重新执行。",
            loopbackBind = "🔒 只在 loopback 上启动。要对局域网开放请用 --lan 或 Z2_SSHD_LAN=1。",
            foregroundStart = "▶ 以前台方式启动 dropbear (Ctrl-C 停止)",
            listeningLan = "✅ dropbear listening (root，仅密钥认证)",
            listeningLoopback = "✅ dropbear listening (root，仅密钥认证，仅 loopback)",
            startupFailed = "❌ dropbear 启动失败。日志:",
            deviceIp = "设备IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys 还没有设置。登记客户端的公钥之后就能连接了。",
            authKeysExample = "   例: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )
        fun zhTW(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ 連接埠號不正確",
            configOk = "sshd(dropbear) 設定 OK",
            installingDropbear = "📦 沒有 dropbear，正在安裝…",
            dropbearInstallFailed = "❌ 無法安裝 dropbear。請確認網路連線和套件名稱。",
            privilegedPortWarn = "⚠️ 特權連接埠。在 proot(非 root) 下很可能 bind 不上 (建議用 1024 以上)。",
            lanExposeWarn = "⚠️ 區域網路/廣域網開放模式: 必須用金鑰認證，並且要用強金鑰。",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys 還沒有設定。已中止以區域網路開放方式啟動。",
            noAuthorizedKeysHint = "   請先把公鑰登記到 ~/.ssh/authorized_keys 再重新執行。",
            loopbackBind = "🔒 只在 loopback 上啟動。要對區域網路開放請用 --lan 或 Z2_SSHD_LAN=1。",
            foregroundStart = "▶ 以前景方式啟動 dropbear (Ctrl-C 停止)",
            listeningLan = "✅ dropbear listening (root，僅金鑰認證)",
            listeningLoopback = "✅ dropbear listening (root，僅金鑰認證，僅 loopback)",
            startupFailed = "❌ dropbear 啟動失敗。日誌:",
            deviceIp = "裝置IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys 還沒有設定。登記用戶端的公鑰之後就能連線了。",
            authKeysExample = "   例: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )
        fun es(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Número de puerto no válido",
            configOk = "sshd(dropbear) configuración correcta",
            installingDropbear = "📦 No hay dropbear; instalándolo…",
            dropbearInstallFailed = "❌ No se pudo instalar dropbear. Comprueba la red y el nombre del paquete.",
            privilegedPortWarn = "⚠️ Puerto privilegiado; con proot (sin root) es muy probable que no se pueda enlazar (usa 1024 o más).",
            lanExposeWarn = "⚠️ Modo abierto a LAN/WAN: hace falta autenticación por clave; usa una clave fuerte.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys está vacío. Se cancela el arranque abierto a la red.",
            noAuthorizedKeysHint = "   Registra tu clave pública en ~/.ssh/authorized_keys y vuelve a ejecutarlo.",
            loopbackBind = "🔒 Solo en loopback. Para abrirlo a la LAN, pasa --lan o define Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear en primer plano (Ctrl-C para pararlo)",
            listeningLan = "✅ dropbear listening (root, solo autenticación por clave)",
            listeningLoopback = "✅ dropbear listening (root, solo clave, solo loopback)",
            startupFailed = "❌ Falló el arranque de dropbear. Registro:",
            deviceIp = "IP del dispositivo",
            authKeysHint = "ℹ ~/.ssh/authorized_keys está vacío. Registra la clave pública del cliente para poder conectar.",
            authKeysExample = "   p. ej.: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )
        /**
         * 言語ごとの組。⭐ **3 言語目はここに 1 行足す** (言語コード to その組を返す関数)。
         * 名簿 ([AppLanguages]) にあっても訳が無い言語は英語へ落ちる。
         */
        private val byLang: Map<String, () -> SshdScriptStrings> = mapOf(
            "en" to ::en,
            "ja" to ::ja,
            "zh-CN" to ::zhCN,
            "zh-TW" to ::zhTW,
            "es" to ::es,
        )

        /** ⚠ **知らない言語は英語**。「英語でなければ日本語」と書かないこと。 */
        fun forLang(lang: String): SshdScriptStrings =
            (byLang[AppLanguages.resolve(lang)] ?: byLang.getValue(AppLanguages.FALLBACK))()
    }
}

fun dropbearBootstrapScript(
    defaultPort: Int = Z2TERM_SSHD_PORT,
    strings: SshdScriptStrings = SshdScriptStrings.ja()
): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: sshd 互換ラッパー (バックエンド dropbear。OpenSSH sshd は proot 不可)
        |# 既定で安全側 (127.0.0.1 のみ bind / パスワード認証禁止 / 空パスワード root 禁止)。
        |# LAN/WAN 公開したい場合は `sshd --lan` または env Z2_SSHD_LAN=1 で明示有効化。
        |
        |# ⚠ タブの id を SSH ログインへ持ち込まない。これを手で起動したタブの
        |# Z2_SESSION_ID を dropbear の子 (ログインシェル) が受け継ぐと、別の端末から繋いだ
        |# 人がそのタブへ `z2-session attach` できなくなる (自分自身と誤判定される)。
        |unset Z2_SESSION_ID
        |DEFAULT_PORT=$defaultPort
        |CONFIG=/etc/ssh/sshd_config
        |PORT=""
        |FOREGROUND=""
        |TESTONLY=""
        |# LAN/WAN 公開モード (env または --lan 引数で有効化)。既定は loopback 限定。
        |LAN_EXPOSE="${d}{Z2_SSHD_LAN:-0}"
        |
        |# sshd 互換の主要オプションを解釈する。
        |while [ ${d}# -gt 0 ]; do
        |  case "${d}1" in
        |    -p) PORT="${d}2"; shift 2 ;;
        |    -p?*) PORT="${d}{1#-p}"; shift ;;
        |    -f) CONFIG="${d}2"; shift 2 ;;
        |    -f?*) CONFIG="${d}{1#-f}"; shift ;;
        |    -o) case "${d}2" in [Pp]ort*) PORT=${d}(printf '%s' "${d}2" | tr -cd '0-9') ;; esac; shift 2 ;;
        |    -D|-d) FOREGROUND=1; shift ;;
        |    -t|-T) TESTONLY=1; shift ;;
        |    --lan) LAN_EXPOSE=1; shift ;;
        |    --loopback) LAN_EXPOSE=0; shift ;;
        |    -h) shift 2 ;;
        |    -h?*) shift ;;
        |    --) shift; break ;;
        |    *) shift ;;
        |  esac
        |done
        |
        |# 常駐サーバー (supervisor) 配下では **必ず前景で走らせる**。
        |# dropbear を背景へ逃がして自分が exit すると、supervisor が「サーバーが落ちた」と
        |# 判断して数秒ごとに再起動し、そのたび下の停止処理が既存 dropbear を kill するため
        |# 接続が張れない/切られる (= LAN 公開しても繋がらない主因)。
        |if [ -z "${d}FOREGROUND" ] && [ "${d}{Z2_SUPERVISED:-0}" = "1" ]; then
        |  FOREGROUND=1
        |fi
        |
        |# ポート決定: -p / -o Port=N  →  sshd_config の Port  →  既定
        |if [ -z "${d}PORT" ] && [ -r "${d}CONFIG" ]; then
        |  PORT=${d}(awk 'tolower(${d}1)=="port" && ${d}2 ~ /^[0-9]+${d}/ {print ${d}2; exit}' "${d}CONFIG")
        |fi
        |[ -z "${d}PORT" ] && PORT=${d}DEFAULT_PORT
        |
        |case "${d}PORT" in
        |  ''|*[!0-9]*) echo "${strings.invalidPort}: '${d}PORT'"; exit 1 ;;
        |esac
        |
        |if [ -n "${d}TESTONLY" ]; then
        |  echo "${strings.configOk}: port=${d}PORT (config: ${d}CONFIG)"
        |  exit 0
        |fi
        |
        |if ! command -v dropbear >/dev/null 2>&1; then
        |  echo "${strings.installingDropbear}"
        |  if command -v pacman >/dev/null 2>&1; then
        |    pacman -Sy --noconfirm dropbear
        |  elif command -v apt-get >/dev/null 2>&1; then
        |    apt-get update && { apt-get install -y dropbear-bin || apt-get install -y dropbear; }
        |  elif command -v apk >/dev/null 2>&1; then
        |    apk add --no-cache dropbear
        |  elif command -v dnf >/dev/null 2>&1; then
        |    dnf install -y dropbear
        |  elif command -v zypper >/dev/null 2>&1; then
        |    zypper --non-interactive install dropbear
        |  fi
        |fi
        |if ! command -v dropbear >/dev/null 2>&1; then
        |  echo "${strings.dropbearInstallFailed}"
        |  exit 1
        |fi
        |
        |mkdir -p /etc/dropbear
        |[ -f /etc/dropbear/dropbear_ed25519_host_key ] || dropbearkey -t ed25519 -f /etc/dropbear/dropbear_ed25519_host_key 2>/dev/null
        |[ -f /etc/dropbear/dropbear_rsa_host_key ] || dropbearkey -t rsa -s 2048 -f /etc/dropbear/dropbear_rsa_host_key 2>/dev/null
        |
        |# 既存 dropbear を確実に停止 (ポート競合「Address already in use」の回避)。
        |# pkill / pidof が無い最小 rootfs でも効くよう /proc を直接走査する。
        |pkill -x dropbear 2>/dev/null
        |for p in ${d}(pidof dropbear 2>/dev/null); do kill "${d}p" 2>/dev/null; done
        |[ -f /tmp/dropbear.pid ] && kill "${d}(cat /tmp/dropbear.pid 2>/dev/null)" 2>/dev/null
        |for c in /proc/[0-9]*/comm; do
        |  [ -r "${d}c" ] || continue
        |  if [ "${d}(cat "${d}c" 2>/dev/null)" = dropbear ]; then
        |    pid=${d}{c#/proc/}; pid=${d}{pid%/comm}; kill "${d}pid" 2>/dev/null
        |  fi
        |done
        |rm -f /tmp/dropbear.pid /tmp/dropbear.log
        |sleep 1
        |
        |if [ "${d}PORT" -lt 1024 ]; then
        |  echo "${strings.privilegedPortWarn} (port=${d}PORT)"
        |fi
        |
        |# bind アドレスとセキュリティフラグを既定の安全側で組み立てる:
        |#   -s : パスワード認証禁止 (鍵認証のみ)
        |#   -g : root の空パスワードログイン禁止 (空パスワードでも蹴る)
        |#   -p [addr:]port : LAN_EXPOSE=0 なら 127.0.0.1:port、=1 なら addr 省略で全 NIC
        |SEC_FLAGS="-s -g"
        |if [ "${d}LAN_EXPOSE" = "1" ]; then
        |  BIND_SPEC="${d}PORT"
        |  echo "${strings.lanExposeWarn} (0.0.0.0:${d}PORT)"
        |  if ! [ -s /root/.ssh/authorized_keys ] && ! [ -s ~/.ssh/authorized_keys ]; then
        |    echo "${strings.noAuthorizedKeys}"
        |    echo "${strings.noAuthorizedKeysHint}"
        |    exit 2
        |  fi
        |else
        |  BIND_SPEC="127.0.0.1:${d}PORT"
        |  echo "${strings.loopbackBind} (127.0.0.1:${d}PORT)"
        |fi
        |
        |if [ -n "${d}FOREGROUND" ]; then
        |  echo "${strings.foregroundStart}: ${d}BIND_SPEC"
        |  # 前景では exec で置き換わるため、鍵未設定の案内はここで先に出す。
        |  if ! [ -s /root/.ssh/authorized_keys ] && ! [ -s ~/.ssh/authorized_keys ]; then
        |    echo "${strings.authKeysHint}"
        |    echo "${strings.authKeysExample}"
        |  fi
        |  exec dropbear -F -p "${d}BIND_SPEC" -R -E ${d}SEC_FLAGS
        |fi
        |
        |dropbear -p "${d}BIND_SPEC" -R -E ${d}SEC_FLAGS -P /tmp/dropbear.pid 2>>/tmp/dropbear.log
        |sleep 1
        |if [ -s /tmp/dropbear.pid ] && kill -0 "${d}(cat /tmp/dropbear.pid)" 2>/dev/null; then
        |  if [ "${d}LAN_EXPOSE" = "1" ]; then
        |    IP=${d}(ip -4 addr show 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print ${d}2}' | grep -v '^127' | head -n1)
        |    echo "${strings.listeningLan} on :${d}PORT (${d}{IP:-${strings.deviceIp}})"
        |  else
        |    echo "${strings.listeningLoopback} on 127.0.0.1:${d}PORT"
        |  fi
        |else
        |  echo "${strings.startupFailed}"
        |  cat /tmp/dropbear.log 2>/dev/null
        |fi
        |
        |# 鍵認証必須なので、authorized_keys が空なら接続できない旨を案内する (パスワードログインは禁止済み)。
        |if ! [ -s /root/.ssh/authorized_keys ] && ! [ -s ~/.ssh/authorized_keys ]; then
        |  echo "${strings.authKeysHint}"
        |  echo "${strings.authKeysExample}"
        |fi
    """.trimMargin() + "\n"
}
