package com.zerotoship.z2term.backup

import android.content.Context
import android.net.Uri
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.icon.IconStore
import com.zerotoship.z2term.icon.refreshActiveNotifications
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.tile.TileStore
import com.zerotoship.z2term.tile.Z2TileService
import com.zerotoship.z2term.ui.terminal.keyboard.ImeHistoryStore
import com.zerotoship.z2term.ui.terminal.keyboard.UserDictStore
import com.zerotoship.z2term.widget.WidgetStore
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 設定ごと持ち出す / 戻す (0.8.239)。
 *
 * **なぜ要るか**: 機種変・初期化・入れ直しで**全部消える**のが今の状態だった。だから怖くて
 * 本気の環境を作れない。持ち出せると分かって初めて、腰を据えて積み上げられる。
 *
 * **含めるもの**（= 二度と戻らないもの）: 設定 / SSH 接続先 / スニペット /
 * `~/.z2term/when/<id>.rule` / `~/.z2term/macros/<名前>.sh` /
 * **自作テーマ・タイルの割り当て・アイコンのドット絵・ユーザー辞書・IME の学習履歴**（0.8.379）。
 * (Kotlin のブロックコメントはネストするので、KDoc 内で `/` + `*` を書かない)
 *
 * ⚠ **後から足した 5 つは、どれも「マクロは戻ったのに手で積み上げたものが消えている」を
 * 埋めるためのもの**。テーマは [AppSettings] とは別の DataStore、タイルとアイコンは
 * SharedPreferences（プロセスが生きていない状態で読まれるため）、辞書と学習履歴は
 * `filesDir` のファイルと、置き場がばらばらで**設定を運ぶだけでは 1 つも付いてこなかった**。
 *
 * **含めないもの**: rootfs（数百 MB。入れ直せば戻る）・ログ・`events.jsonl`。
 * 「入れ直せば戻るもの」と「二度と戻らないもの」を分けるのがこの機能の設計そのもの。
 *
 * ⚠ **ホーム画面ウィジェットの割り当ては含めない**。保存のキーが `appWidgetId`（端末が置いた
 * ときに配る番号）なので、**移した先では別のウィジェットを指すか、どれも指さない**。
 * 運ぶには「置き直したウィジェットへ順に当てる」仕組みが要り、それは持ち出しとは別の設計になる。
 *
 * ## 秘密の扱い（ここが一番の判断）
 *
 * SSH のパスワードと秘密鍵は Android Keystore で暗号化して保存されているが、
 * **Keystore の鍵は端末に紐づくので、暗号化済みのまま持ち出しても移した先で復号できない**。
 * つまり持ち出すには一度平文に戻すしかない。そこで:
 *
 *  - **既定では秘密を含めない**（[Options.includeSecrets] = false）。接続先の名前やホストは
 *    運ぶが、パスワードと鍵は空で書き出す。
 *  - 含めるときは**パスフレーズ必須**。合言葉なしで秘密を書き出す経路は**作らない** —
 *    1 つでも残すと、そこから事故る。
 */
object BackupManager {

    /** バックアップの中身の構成 (import 前のプレビューにも使う)。 */
    data class Summary(
        val createdAt: String,
        val appVersion: String,
        val hasSecrets: Boolean,
        val encrypted: Boolean,
        val sshCount: Int,
        val snippetCount: Int,
        val ruleCount: Int,
        val macroCount: Int,
        /** 割り当ての入っているタイルの枠数 (0.8.379)。 */
        val tileCount: Int = 0,
        /** 絵の入っている対象の数 (通知 + タイル枠。0.8.379)。 */
        val iconCount: Int = 0,
        /** 自作テーマ (0 か 1。0.8.379)。 */
        val themeCount: Int = 0,
        /** ユーザー辞書のファイル数 (0.8.379)。 */
        val dictCount: Int = 0,
        /** IME が覚えている語数 (0.8.379)。 */
        val learnedCount: Int = 0,
    )

    /** 書き出しの選択。 */
    data class Options(
        /** SSH のパスワード・秘密鍵を含めるか。含めるなら [passphrase] 必須。 */
        val includeSecrets: Boolean = false,
        /** 秘密を含めるときの合言葉。 */
        val passphrase: String = "",
    )

    private const val MANIFEST = "manifest.json"
    private const val SETTINGS = "settings.json"
    private const val SNIPPETS = "snippets.json"
    private const val SSH_PLAIN = "ssh.json"
    private const val SSH_ENC = "ssh.enc"
    private const val WHEN_DIR = "when/"
    private const val MACRO_DIR = "macros/"
    private const val THEME = "theme.json"
    private const val TILES = "tiles.json"
    private const val ICONS = "icons.json"
    private const val DICT_DIR = "user_dict/"
    private const val IME_HISTORY = "ime_history.json"

    /**
     * ファイル名に使う日時 (`20260725-2130`)。手で作るものと定期バックアップ
     * ([AutoBackup]) で**同じ形に揃える** — 並べたときに時系列で並ぶことが、
     * 世代整理 ([AutoBackup.stale]) の前提になっている。
     */
    fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    /** ファイル名に使う日時 (`z2term-backup-20260725-2130.zip`)。 */
    fun suggestFileName(): String = "z2term-backup-" + stamp() + ".zip"

    /**
     * バックアップを [out] へ書き出す。
     *
     * @throws IllegalArgumentException 秘密を含める指定なのに合言葉が空のとき
     *   (**合言葉なしで秘密を出す経路は用意しない**)。
     */
    suspend fun export(context: Context, out: java.io.OutputStream, options: Options) {
        require(!options.includeSecrets || options.passphrase.isNotEmpty()) {
            "includeSecrets requires a passphrase"
        }
        val app = context.applicationContext
        val settingsJson = AppSettings(app).exportRaw()
        val snippetsJson = SnippetStore(app).exportRaw()
        val sshJson = SshProfileStore(app).exportRaw(includeSecrets = options.includeSecrets)
        val rules = filesIn(WhenManager.whenDir(app), ".rule")
        val macros = filesIn(WidgetStore.macroDir(app), ".sh")
        val themeJson = CustomThemeStore.exportRaw(app)
        val tilesJson = TileStore.exportRaw(app)
        val iconsJson = IconStore.exportRaw(app)
        // 辞書は拡張子を決めていない (利用者が持ち込んだファイル名のまま置く) ので、全部拾う。
        val dicts = filesIn(UserDictStore.dictDir(app), "")
        val imeHistory = ImeHistoryStore.historyFile(app).takeIf { it.isFile }
        // 語数は読み込んでからでないと 0 に見える (数えるためだけに読む)。
        ImeHistoryStore.ensureLoaded(app)

        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject().apply {
                put("format", 1)
                put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                put("appVersion", BuildConfig.VERSION_NAME)
                put("hasSecrets", options.includeSecrets)
                put("encrypted", options.includeSecrets)
                put("sshCount", countJsonArray(sshJson))
                put("snippetCount", countJsonArray(snippetsJson))
                put("ruleCount", rules.size)
                put("macroCount", macros.size)
                put("tileCount", (1..TileStore.COUNT).count { TileStore.get(app, it) != null })
                put("iconCount", IconStore.targets().count { IconStore.text(app, it) != null })
                put("themeCount", if (themeJson.isNotEmpty()) 1 else 0)
                put("dictCount", dicts.size)
                put("learnedCount", ImeHistoryStore.approximateCount())
            }
            zip.putText(MANIFEST, manifest.toString())
            zip.putText(SETTINGS, settingsJson)
            zip.putText(SNIPPETS, snippetsJson)
            if (options.includeSecrets) {
                // 秘密を含むファイルだけを合言葉で暗号化する。設定やスニペットに秘密は無い。
                zip.putBytes(SSH_ENC, BackupCrypt.encrypt(sshJson.toByteArray(), options.passphrase))
            } else {
                zip.putText(SSH_PLAIN, sshJson)
            }
            rules.forEach { zip.putText(WHEN_DIR + it.name, it.readText()) }
            macros.forEach { zip.putText(MACRO_DIR + it.name, it.readText()) }
            if (themeJson.isNotEmpty()) zip.putText(THEME, themeJson)
            zip.putText(TILES, tilesJson)
            zip.putText(ICONS, iconsJson)
            dicts.forEach { zip.putText(DICT_DIR + it.name, it.readText()) }
            imeHistory?.let { zip.putBytes(IME_HISTORY, it.readBytes()) }
        }
    }

    /** バックアップの中身を**適用せずに**読む (「これを入れます」を見せるため)。 */
    fun peek(context: Context, uri: Uri): Summary? {
        val bytes = readAll(context, uri) ?: return null
        val entries = unzip(bytes)
        val manifest = entries[MANIFEST]?.toString(Charsets.UTF_8) ?: return null
        val o = runCatching { JSONObject(manifest) }.getOrNull() ?: return null
        return Summary(
            createdAt = o.optString("createdAt"),
            appVersion = o.optString("appVersion"),
            hasSecrets = o.optBoolean("hasSecrets"),
            encrypted = o.optBoolean("encrypted"),
            sshCount = o.optInt("sshCount"),
            snippetCount = o.optInt("snippetCount"),
            ruleCount = o.optInt("ruleCount"),
            macroCount = o.optInt("macroCount"),
            // 0.8.379 より前のバックアップにはこれらが無い (optInt は 0 を返す = 「入っていない」)。
            tileCount = o.optInt("tileCount"),
            iconCount = o.optInt("iconCount"),
            themeCount = o.optInt("themeCount"),
            dictCount = o.optInt("dictCount"),
            learnedCount = o.optInt("learnedCount"),
        )
    }

    /**
     * バックアップを取り込む。
     *
     * **上書きではなく追加・更新**。同じ id のものは置き換え、バックアップに無いものは
     * そのまま残す（古いバックアップを戻したときに、新しく作ったものが消えないように）。
     *
     * @return 取り込めたら true。合言葉が違う / 壊れているときは false。
     */
    suspend fun import(context: Context, uri: Uri, passphrase: String): Boolean {
        val app = context.applicationContext
        val bytes = readAll(context, uri) ?: return false
        val entries = unzip(bytes)
        if (entries[MANIFEST] == null) return false

        entries[SETTINGS]?.let { AppSettings(app).importRaw(it.toString(Charsets.UTF_8)) }
        entries[SNIPPETS]?.let { SnippetStore(app).importRaw(it.toString(Charsets.UTF_8)) }
        entries[THEME]?.let { CustomThemeStore.importRaw(app, it.toString(Charsets.UTF_8)) }
        // ⚠ タイルは戻すだけでなく一覧の同期まで要る ([TileStore.importRaw] が通している)。
        entries[TILES]?.let { TileStore.importRaw(app, it.toString(Charsets.UTF_8)) }
        entries[ICONS]?.let { IconStore.importRaw(app, it.toString(Charsets.UTF_8)) }

        val ssh = when {
            entries[SSH_ENC] != null -> {
                if (passphrase.isEmpty()) return false
                runCatching { BackupCrypt.decrypt(entries[SSH_ENC]!!, passphrase) }.getOrNull()
                    ?: return false   // 合言葉が違う
            }
            else -> entries[SSH_PLAIN]
        }
        ssh?.let { SshProfileStore(app).importRaw(it.toString(Charsets.UTF_8)) }

        // ルールとマクロはファイルなので、そのまま書き戻す (同名は置き換え)。
        val whenDir = WhenManager.whenDir(app).apply { mkdirs() }
        val macroDir = WidgetStore.macroDir(app).apply { mkdirs() }
        val dictDir = UserDictStore.dictDir(app).apply { mkdirs() }
        var dictRestored = false
        entries.forEach { (name, data) ->
            when {
                name.startsWith(WHEN_DIR) && name.endsWith(".rule") ->
                    safeChild(whenDir, name.removePrefix(WHEN_DIR))?.writeBytes(data)
                name.startsWith(MACRO_DIR) && name.endsWith(".sh") ->
                    safeChild(macroDir, name.removePrefix(MACRO_DIR))?.apply {
                        writeBytes(data)
                        @Suppress("SetWorldReadable")
                        setExecutable(true, false)
                    }
                // 辞書はファイル名が正本 (どの語がどこから来たのかを利用者が追える) ので、
                // 名前ごと戻す。⚠ 拡張子で絞らない — 持ち込んだファイル名のまま置いてある。
                name.startsWith(DICT_DIR) ->
                    safeChild(dictDir, name.removePrefix(DICT_DIR))?.let {
                        it.writeBytes(data)
                        dictRestored = true
                    }
            }
        }
        entries[IME_HISTORY]?.let { File(app.filesDir, IME_HISTORY).writeBytes(it) }

        // 時刻トリガーを貼り直す (取り込んだルールをその場で効かせる)。
        runCatching { WhenManager.reload(app) }
        // ⚠ ここから下は「戻したものを、いま出ているものへ効かせる」ぶん。**省くと、戻した
        // はずのものが次の起動まで出てこない** (アイコンは使い回しの絵、辞書と学習は読み込み
        // 済みの表がそれぞれ手前に残るため)。
        if (entries[ICONS] != null) {
            runCatching { refreshActiveNotifications(app) }
            (1..TileStore.COUNT).forEach { runCatching { Z2TileService.requestUpdate(app, it) } }
        }
        if (dictRestored) runCatching { UserDictStore.reload(app) }
        if (entries[IME_HISTORY] != null) runCatching { ImeHistoryStore.reload(app) }
        return true
    }

    /**
     * zip の中の名前から安全な出力先を作る。`../` を含む名前は**捨てる** —
     * 他人から受け取ったファイルを開く口なので、書き出し先がディレクトリの外へ出ないようにする。
     */
    private fun safeChild(dir: File, name: String): File? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        return File(dir, name)
    }

    private fun filesIn(dir: File, suffix: String): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(suffix) }?.sortedBy { it.name } ?: emptyList()

    private fun countJsonArray(json: String): Int =
        runCatching { org.json.JSONArray(json).length() }.getOrDefault(0)

    private fun readAll(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zin ->
                while (true) {
                    val e: ZipEntry = zin.nextEntry ?: break
                    if (!e.isDirectory) {
                        val buf = ByteArrayOutputStream()
                        zin.copyTo(buf)
                        out[e.name] = buf.toByteArray()
                    }
                    zin.closeEntry()
                }
            }
        }
        return out
    }

    private fun ZipOutputStream.putText(name: String, text: String) = putBytes(name, text.toByteArray())

    private fun ZipOutputStream.putBytes(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }
}
