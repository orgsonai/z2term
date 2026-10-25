package com.zerotoship.z2term.service

import android.content.Context
import com.zerotoship.z2term.proot.Z2TERM_SSHD_PORT
import com.zerotoship.z2term.settings.ServerEntry
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 「PC からこの端末へ入るための ssh 接続先」を組み立てる共通ロジック。
 *
 * 設定シートの SSH ヘルパー ([com.zerotoship.z2term.ui.settings.SshAccessHelper]) と
 * ホーム画面ウィジェット ([com.zerotoship.z2term.widget.StatusWidgetProvider]) が同じ値を
 * 出す必要があるため、ここに 1 本化する (片方だけ直して食い違う状態を作らない)。
 *
 * どちらも**表示のみ**で、sshd の起動はターミナルからコマンドで行う。
 */
object SshEndpoint {

    /** ネットワークインターフェース 1 本の IPv4 アドレス。[name] は `wlan0` など。 */
    data class Nic(val name: String, val ip: String)

    /**
     * NetworkInterface を全列挙して、ループバック/リンクローカルでない IPv4 を返す。
     * 通常 Wi-Fi の `wlan0` が `192.168.x.x` を返す。
     */
    fun nics(): List<Nic> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().map { iface.name to it } }
            .filter { (_, addr) -> addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress }
            .mapNotNull { (name, addr) -> addr.hostAddress?.let { Nic(name, it) } }
    }.getOrDefault(emptyList())

    /** OpenSSH が `-p` も `Port` も無いときに使う既定 (ラッパーの既定 [Z2TERM_SSHD_PORT] とは別)。 */
    private const val OPENSSH_DEFAULT_PORT = 22

    /** sshd を起動するコマンドか (`sshd --lan` / `/usr/sbin/sshd -D` など)。 */
    fun isSshdCommand(command: String?): Boolean = command?.contains("sshd") == true

    /** 常駐サーバーに登録した有効な sshd のコマンド (最初の 1 つ)。無ければ null。 */
    fun sshdServerCommand(serverEntries: String?): String? =
        ServerEntry.decode(serverEntries).firstOrNull { it.enabled && isSshdCommand(it.command) }?.command

    /**
     * PC から繋ぐポート。**動いている sshd と同じ順で決める**:
     * [command] の `-p N` / `-pN` / `-o Port=N` → 設定ファイル (`-f` で指定したもの、無ければ
     * `/etc/ssh/sshd_config`) の `Port` → 既定。
     *
     * 1.0.57 までは設定ファイルしか見ておらず、常駐サーバーを `sshd --lan -p 65152` のように
     * 起動していると、実際とは違う既定の 2222 を出していた (実機フィードバック 2026-10-26)。
     *
     * [command] が OpenSSH 本体 (`/usr/sbin/sshd` など、`sshd` ラッパー以外のパス) なら OpenSSH の
     * 決め方に合わせる: 設定ファイルの `Include` も辿り、既定は 22。`sshd` (= dropbear を使う
     * ラッパー `/usr/local/sbin/sshd`) は設定ファイル本体の `Port` だけを読み、既定は 2222。
     */
    fun configuredPort(context: Context, distroId: String, command: String? = null): Int {
        val args = command?.let { sshdArgs(it) }
        val openssh = args?.first?.let { it.endsWith("/sshd") && it != "/usr/local/sbin/sshd" } == true
        val opts = args?.second.orEmpty()
        portFromArgs(opts)?.let { return it }

        val rootfs = File(context.filesDir, "distros/$distroId")
        val configPath = configFromArgs(opts) ?: "/etc/ssh/sshd_config"
        val fromConfig = runCatching {
            portFromConfig(rootfs, configPath, followInclude = openssh, depth = 0)
        }.getOrNull()
        return fromConfig ?: if (openssh) OPENSSH_DEFAULT_PORT else Z2TERM_SSHD_PORT
    }

    /**
     * コマンドから sshd の実行ファイル名とその後ろの引数を取り出す。`sshd` で終わる語が無ければ null。
     * 引用符は外すだけにする (`-o "Port 65152"` は `-o` `Port` `65152` の 3 語になる)。
     */
    internal fun sshdArgs(command: String): Pair<String, List<String>>? {
        val words = command.trim().split(Regex("\\s+")).map { it.trim('"', '\'') }.filter { it.isNotEmpty() }
        val i = words.indexOfFirst { it == "sshd" || it.endsWith("/sshd") }
        if (i < 0) return null
        return words[i] to words.drop(i + 1)
    }

    /** `-p N` / `-pN` / `-o Port=N` / `-o "Port N"`。後に書いたものが勝つ (ラッパーと同じ)。 */
    internal fun portFromArgs(args: List<String>): Int? {
        var port: Int? = null
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-p" -> { port = args.getOrNull(i + 1)?.toIntOrNull() ?: port; i++ }
                a.startsWith("-p") && a.length > 2 && a.drop(2).all(Char::isDigit) -> port = a.drop(2).toInt()
                a == "-o" -> {
                    val o = args.getOrNull(i + 1).orEmpty()
                    if (o.startsWith("port", ignoreCase = true)) {
                        val digits = o.filter(Char::isDigit)
                        port = if (digits.isNotEmpty()) digits.toIntOrNull()
                        else args.getOrNull(i + 2)?.toIntOrNull()
                    }
                    i++
                }
            }
            i++
        }
        return port
    }

    /** `-f <config>` / `-f<config>`。 */
    internal fun configFromArgs(args: List<String>): String? {
        var cfg: String? = null
        args.forEachIndexed { i, a ->
            if (a == "-f") cfg = args.getOrNull(i + 1) ?: cfg
            else if (a.startsWith("-f") && a.length > 2) cfg = a.drop(2)
        }
        return cfg
    }

    /**
     * 設定ファイル ([path] は OS の中のパス) の最初の `Port`。[followInclude] のときは OpenSSH と同じく
     * `Include` を書かれた位置で展開する (相対パスは `/etc/ssh` から、`*` は名前順)。
     */
    private fun portFromConfig(rootfs: File, path: String, followInclude: Boolean, depth: Int): Int? {
        if (depth > 8) return null
        val file = File(rootfs, path.removePrefix("/"))
        if (!file.isFile) return null
        for (line in file.readLines()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2 || parts[0].startsWith("#")) continue
            when {
                parts[0].equals("Port", ignoreCase = true) -> parts[1].toIntOrNull()?.let { return it }
                followInclude && parts[0].equals("Include", ignoreCase = true) -> {
                    for (pattern in parts.drop(1)) {
                        val abs = if (pattern.startsWith("/")) pattern else "/etc/ssh/$pattern"
                        for (inc in expandGlob(rootfs, abs)) {
                            portFromConfig(rootfs, inc, true, depth + 1)?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }

    /** ファイル名の部分だけの `*` / `?` を展開する (OpenSSH の既定 `Include` = sshd_config.d 下の .conf 全部、に足りる範囲)。 */
    private fun expandGlob(rootfs: File, abs: String): List<String> {
        val dir = abs.substringBeforeLast('/')
        val name = abs.substringAfterLast('/')
        if ('*' !in name && '?' !in name) return listOf(abs)
        val re = Regex(name.map { c ->
            when (c) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(c.toString()) }
        }.joinToString(""))
        return File(rootfs, dir.removePrefix("/")).list()
            ?.filter { re.matches(it) }
            ?.sorted()
            ?.map { "$dir/$it" }
            .orEmpty()
    }

    /** `ssh -p <port> root@<ip>` の 1 行。[command] は sshd を起動したコマンド。IP が取れなければ null。 */
    fun sshCommand(context: Context, distroId: String, command: String? = null): String? {
        val ip = nics().firstOrNull()?.ip ?: return null
        return "ssh -p ${configuredPort(context, distroId, command)} root@$ip"
    }

}
