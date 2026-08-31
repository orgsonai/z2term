package com.zerotoship.z2term.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.icon.setZ2SmallIcon
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 通信量の上限 (0.8.388)。**今期の通信量が決めた量に達したら、z2term 自身の通信を止める**。
 *
 * **なぜ要るか**: 使いすぎに気付くのは、たいてい**通信が絞られてから**。z2term は SSH や
 * ダウンロードで黙って通信し続けられるので、上限を決めて自分で止まれる方がいい。
 *
 * ## どこまで止まるか (ここが一番の判断)
 *
 * ⚠ **止まるのは z2term の通信だけ**で、ほかのアプリのモバイル通信は止まらない。root なしで
 * 端末全体を止める手段は VPN を張って捨てることしかなく、それは**ターミナルアプリが常時
 * 端末の VPN 枠を占有する**という別の重さを持ち込む (ほかの VPN と併用できなくなる)。
 *
 * 止める対象:
 *  - SSH / SFTP の**新しい接続** ([SshSessionFactory] の入口で断る)
 *  - すでに繋がっている SSH (見張りが切る。[TerminalSession.disconnectForNetLimit])
 *  - OS イメージ・GUI パッケージ・アプリ更新の**ダウンロード**
 *
 * ⚠ **端末 (Linux) の中から出ていく通信 (`apk` / `curl` など) は止められない**。アプリが
 * 自分のプロセスの通信だけを選んで止める手段を Android は持たない。**数には入る**ので、
 * 上限に達したこと自体は分かる。この一点は画面にも docs にも正直に書く。
 *
 * ## 止めないもの
 *
 * - ⚠ **同じ家の中への接続は止めない** (利用者の要望)。`192.168.*` / `10.*` / `172.16-31.*` /
 *   `127.*` / `169.254.*` / `fc00::/7` / `fe80::/10` と、`localhost` / 単一ラベルの名前 /
 *   `.local` `.lan` `.home` `.internal`。**モバイル通信を 1 バイトも使わない相手を止める
 *   理由がない**。名前で書かれた相手は引いてみて、私設アドレスならローカル扱いにする。
 * - **Wi-Fi につながっている間は止めない** (既定)。数えるのもモバイルぶんだけ。
 *   OFF にすると Wi-Fi ぶんも合算して数え、つながり方に関係なく止める。
 *
 * ## 測り方 — 数えるのは端末全体
 *
 * ⚠ **数えるのは端末全体の通信量**で、z2term 自身のぶんではない (0.8.389 で直した)。
 * 利用者が知りたいのは「今月あと何 GB 使えるか」であって、そのうち z2term が何バイト
 * 使ったかではない。**自分のぶんだけで止めても、契約の上限には何の関係もない**。
 *
 * `NetworkStatsManager.querySummaryForDevice` で回線ごとの合計を読む。⚠ **これには
 * 「使用状況へのアクセス」の許可が要る** (自分の UID を聞くだけなら要らなかった)。
 * 普通の権限ではなく設定画面で 1 つずつ許すものなので、[hasUsageAccess] で見て、
 * 無ければ設定へ案内する ([openUsageAccessSettings])。
 *
 * ⚠ **許可が無い / 読めないときは止めない** — 測れないことを理由に通信を止めると、
 * 直しようのない締め出しになる。どちらの状態かは設定画面に出す。
 */
object NetGuard {

    private const val TAG = "NetGuard"
    private const val CHANNEL_ID = "z2term_net_limit"
    private const val NOTIFY_ID = 0x2E7

    const val ACTION_CHECK = "com.zerotoship.z2term.NET_LIMIT_CHECK"
    private const val REQUEST_CODE = 0x2E7

    /** 見張りの間隔。⚠ 正確さは要らない (使いすぎを止めるのに秒は要らない) ので不正確側で置く。 */
    private const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    /**
     * 上限として選べる段 (MB)。⚠ **等間隔にしない** — 100MB から 50GB までを等間隔の
     * つまみで選ばせると、よく使う 1〜5GB のあたりが数ミリ幅になって合わせられない。
     */
    val LIMIT_STEPS_MB = listOf(
        100, 200, 300, 500, 700,
        1_000, 1_500, 2_000, 3_000, 5_000, 7_000,
        10_000, 15_000, 20_000, 30_000, 50_000
    )

    /**
     * 手で打つときに受け付ける幅 (MB)。⚠ **つまみの幅 ([LIMIT_STEPS_MB]) より広い** —
     * つまみは目分量で合わせるためのもので、契約が 4.5GB や 100GB の人はそこに無い値を
     * 打つ。打った値をつまみの幅へ丸めると、**打ったのに違う値になる**。
     */
    const val TYPED_MIN_MB = 1
    const val TYPED_MAX_MB = 1_000_000

    /** [mb] にいちばん近い段の位置 (つまみの初期位置)。 */
    fun stepIndexOf(mb: Int): Int {
        var best = 0
        for (i in LIMIT_STEPS_MB.indices) {
            if (kotlin.math.abs(LIMIT_STEPS_MB[i] - mb) < kotlin.math.abs(LIMIT_STEPS_MB[best] - mb)) best = i
        }
        return best
    }

    /** いまの状況。画面にも見張りにも同じものを使う。 */
    data class Status(
        val enabled: Boolean,
        /** 測れたか。false = この端末では使用量を読めない (= 止めない)。 */
        val measurable: Boolean,
        val usedBytes: Long,
        val limitBytes: Long,
        val periodStart: Long,
        val onWifi: Boolean,
        val wifiExempt: Boolean
    ) {
        /** 上限に達しているか。 */
        val over: Boolean get() = enabled && measurable && limitBytes > 0 && usedBytes >= limitBytes

        /**
         * いま実際に止めているか。⚠ Wi-Fi を数えない設定で Wi-Fi につながっているときは
         * **超えていても止めない** (モバイルを使っていないのだから止める理由がない)。
         */
        val blocking: Boolean get() = over && !(wifiExempt && onWifi)
    }

    // --- 判定 (純関数・[NetGuardTest] が固定する) ---

    /**
     * 数字で書かれた私設アドレスか。**名前は引かない** (ここは通信をしない判定)。
     */
    fun isPrivateLiteral(host: String): Boolean {
        val h = host.trim().removeSurrounding("[", "]").lowercase(Locale.US)
        if (h.isEmpty()) return false
        val v4 = h.split('.')
        if (v4.size == 4 && v4.all { (it.toIntOrNull() ?: -1) in 0..255 }) {
            val a = v4[0].toInt()
            val b = v4[1].toInt()
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254)
        }
        if (h == "::1" || h == "::") return true
        // fc00::/7 (私設) と fe80::/10 (リンクローカル)。
        return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")
    }

    /**
     * 家の中を指す**名前**か。⚠ **ドットを含まない名前は LAN の相手とみなす** —
     * `myserver` のような一語の名前はインターネット上の相手にはならない。
     */
    fun isLocalName(host: String): Boolean {
        val h = host.trim().lowercase(Locale.US).trimEnd('.')
        if (h.isEmpty()) return false
        // IPv6 リテラルを「ドットのない LAN 名」と混同しない。私設 IPv6 は
        // isPrivateLiteral() が先に判定し、公開 IPv6 は通常の通信量制限対象にする。
        if (':' in h) return false
        if (h == "localhost") return true
        if (!h.contains('.')) return true
        return h.endsWith(".local") || h.endsWith(".lan") ||
            h.endsWith(".home") || h.endsWith(".internal")
    }

    /**
     * 今期の始まり (epoch ミリ秒)。締め日 [resetDay] のその日の 0:00 から数える。
     *
     * ⚠ 締め日は **1-28 に丸める** (29-31 を許すと、その日が無い月だけ区切りが飛ぶ)。
     */
    fun periodStart(now: Long, resetDay: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, resetDay.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 締め日がまだ来ていない月なら、前の月の締め日から数えている途中。
        if (cal.timeInMillis > now) cal.add(Calendar.MONTH, -1)
        return cal.timeInMillis
    }

    /** 人が読む形 (`1.2 GB` / `340 MB`)。1024 進み。 */
    fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.1f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    // --- 端末に聞く ---

    /** いまの状況を作る。⚠ **通信量の問い合わせは重い**ので、呼ぶのは IO スレッドから。 */
    fun status(context: Context): Status {
        val app = context.applicationContext
        val s = runCatching { runBlocking { AppSettings(app).flow.first() } }.getOrNull()
            ?: return Status(false, false, 0, 0, 0, false, true)
        // ⚠ OFF のときは**測りに行かない**。使用量の問い合わせは軽くないので、使っていない人の
        // 画面や接続をそのぶん遅くしない。⚠ measurable=false は「許可が無い」も含む
        // (画面はその 2 つを [hasUsageAccess] で書き分ける)。
        if (!s.netLimitEnabled) return Status(false, false, 0, 0, 0, onWifi(app), s.netLimitWifiExempt)
        val start = periodStart(System.currentTimeMillis(), s.netLimitResetDay)
        val used = usedBytes(app, start, System.currentTimeMillis(), includeWifi = !s.netLimitWifiExempt)
        return Status(
            enabled = s.netLimitEnabled,
            measurable = used >= 0,
            usedBytes = used.coerceAtLeast(0),
            limitBytes = s.netLimitMb.toLong() * 1024L * 1024L,
            periodStart = start,
            onWifi = onWifi(app),
            wifiExempt = s.netLimitWifiExempt
        )
    }

    /**
     * **端末全体**が [since]〜[until] に使った量。**測れなければ -1** (0 と区別する —
     * 「まだ使っていない」と「読めない」を混ぜると、読めない端末で永久に止まらない or
     * 永久に止まったままになる)。
     *
     * ⚠ z2term のぶんではなく**端末全体**。契約の上限に効くのは端末全体の数字で、
     * アプリ 1 つのぶんを見ても「あと何 GB か」は分からない。
     */
    fun usedBytes(context: Context, since: Long, until: Long, includeWifi: Boolean): Long {
        if (!hasUsageAccess(context)) return -1
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return -1
        @Suppress("DEPRECATION")
        val mobile = sumDevice(nsm, ConnectivityManager.TYPE_MOBILE, since, until)
        if (mobile < 0) return -1
        if (!includeWifi) return mobile
        @Suppress("DEPRECATION")
        val wifi = sumDevice(nsm, ConnectivityManager.TYPE_WIFI, since, until)
        return if (wifi < 0) mobile else mobile + wifi
    }

    private fun sumDevice(
        nsm: NetworkStatsManager,
        networkType: Int,
        since: Long,
        until: Long
    ): Long = runCatching {
        // subscriberId は null (= 全部の回線)。⚠ 回線の識別子は API 29 以降アプリから
        // 読めないので、SIM を選り分けることはできない (2 枚挿しなら合算になる)。
        val bucket: NetworkStats.Bucket = nsm.querySummaryForDevice(networkType, null, since, until)
        bucket.rxBytes + bucket.txBytes
    }.onFailure { Log.w(TAG, "usage unavailable (type=$networkType)", it) }.getOrDefault(-1L)

    /**
     * 端末全体の通信量を読む許可 (「使用状況へのアクセス」) があるか。
     *
     * ⚠ **普通の権限のように求めるダイアログが出せない**。利用者が設定画面で 1 つずつ
     * 許すものなので、[openUsageAccessSettings] で案内する以外に道がない。
     */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 「使用状況へのアクセス」の設定画面を開く。 */
    fun openUsageAccessSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "cannot open usage access settings", it) }
    }

    /** いま Wi-Fi につながっているか (モバイルを使っていない = 止める理由がない)。 */
    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * [host] が家の中の相手か。数字の表記と名前で決まらなければ**引いてみる**
     * (LAN の機器に外向きの名前を付けている人がいる)。⚠ 名前解決はブロッキングなので
     * IO スレッドから呼ぶこと。
     */
    fun isLocalTarget(host: String): Boolean {
        if (isPrivateLiteral(host) || isLocalName(host)) return true
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return addr.isLoopbackAddress || addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress || addr.isAnyLocalAddress ||
            isPrivateLiteral(addr.hostAddress ?: "")
    }

    // --- 入口 ---

    /**
     * [host] へ繋いでよいか。⚠ **ローカル宛は常に通す**。止めているときだけ文言を返す。
     *
     * @return 止める理由 (画面に出す文言)。通してよいなら null。
     */
    fun blockReason(context: Context, host: String): String? {
        val app = context.applicationContext
        val st = status(app)
        if (!st.blocking) return null
        // ⚠ 家の中かどうかは**止めると決まってから**調べる。名前解決を伴うので、
        // 止めていない間に毎回引くと接続が遅くなるだけになる。
        if (host.isNotEmpty() && isLocalTarget(host)) return null
        return app.getString(R.string.net_limit_blocked_connect)
    }

    /**
     * 止めているなら例外を投げる (接続・ダウンロードの入口で使う)。
     *
     * ⚠ 例外にしているのは、**黙って何もしない形にすると「壊れている」としか見えない**ため。
     * 理由の文が画面に出れば、設定を開いて上限を上げるところまで自分でたどり着ける。
     */
    @Throws(IOException::class)
    fun ensureAllowed(context: Context, host: String) {
        blockReason(context, host)?.let { throw IOException(it) }
    }

    // --- 見張り ---

    /** 15 分ごとの見張りを置く / 外す。設定を変えた直後・起動時・端末起動後に呼ぶ (冪等)。 */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val s = runCatching { runBlocking { AppSettings(app).flow.first() } }.getOrNull() ?: return
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        if (!s.netLimitEnabled) {
            runCatching { am.cancel(pending(app)) }
            return
        }
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS,
                pending(app)
            )
        }.onFailure { Log.w(TAG, "schedule failed", it) }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, NetGuardReceiver::class.java).setAction(ACTION_CHECK)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * いま超えているなら、外向きの SSH を切って 1 回だけ知らせる。
     *
     * ⚠ **同じ期間に二度は知らせない**。15 分ごとに通知が積み上がると、肝心の 1 通目ごと
     * 読まれなくなる。期間が変われば (締め日をまたげば) また 1 回だけ知らせる。
     */
    fun enforce(context: Context) {
        val app = context.applicationContext
        val st = status(app)
        if (!st.blocking) return

        // 家の中への接続はそのまま残す (切るのは外向きだけ)。
        runCatching {
            SessionManager.sessions.value.filterIsInstance<TerminalSession>()
                .forEach { it.disconnectForNetLimit() }
        }.onFailure { Log.w(TAG, "disconnect failed", it) }

        val s = runCatching { runBlocking { AppSettings(app).flow.first() } }.getOrNull() ?: return
        if (s.netLimitNotifiedPeriod == st.periodStart) return
        notifyOver(app, st)
        runCatching { runBlocking { AppSettings(app).setNetLimitNotifiedPeriod(st.periodStart) } }
    }

    // POST_NOTIFICATIONS 未許可は下の runCatching で握って Log に流すので、lint の権限チェックは
    // 抑止する (Z2ApiBridge の通知と同じ扱い)。⚠ 通知が出せなくても、止まっていることは
    // 設定画面と接続時のエラー文から分かる。
    @SuppressLint("MissingPermission")
    private fun notifyOver(context: Context, st: Status) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.net_limit_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.net_limit_channel_desc) }
            )
        }
        val text = context.getString(
            R.string.net_limit_notify_text,
            formatBytes(st.usedBytes),
            formatBytes(st.limitBytes)
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setZ2SmallIcon(context)
            .setContentTitle(context.getString(R.string.net_limit_notify_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, builder.build()) }
            .onFailure { Log.w(TAG, "notify failed", it) }
    }
}
