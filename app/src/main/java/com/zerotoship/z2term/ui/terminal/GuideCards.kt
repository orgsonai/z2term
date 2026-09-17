package com.zerotoship.z2term.ui.terminal

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeDefaultPanel
import com.zerotoship.z2term.edge.EdgeSamplePanels
import com.zerotoship.z2term.ui.theme.ZtsBgCard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary

/**
 * 手順を追って見せる「案内」(0.8.314)。
 *
 * ## なぜ作ったか
 *
 * 同梱サンプルのマクロは `z2-macro install <名前>` で入れてから使うものだが、**入れる前は
 * 名前すら見えない**。以前はリマインドだけ `remind.sh help` をスニペットに 1 件シードして
 * いたが、これは**入れていない人が押すと「見つからない」と出るだけ**で、そこから入れ方に
 * 辿り着けなかった (利用者の指摘)。スニペットをやめ、**どのサンプルも「案内」として
 * 手順のカードで出す**ことにした。入口は ⚙設定 → メンテナンス → 案内を表示。
 *
 * ## 作法 (はじめの案内 [IntroCards] と共通)
 *
 *  - 端末の上に**薄いカードが数枚**乗るだけ。全画面ウィザードにしない。
 *  - **タップしたら実行する** (`Ctrl-C` → コマンド → ⏎)。以前は入力行に入れるだけで ⏎ は
 *    人が押す作法だったが、**打ちかけの文字と混ざって意図しない行が走る**恐れがあるため、
 *    利用者の判断で「Ctrl-C で行を捨ててから丸ごと送る」に変えた (0.8.314)。
 *  - **カードごとに ✕ が付く。** 要らない手順は送らずに消せる。
 *  - 触った / 消した枚は消え、全部無くなるか見出しの ✕ で閉じる。
 *
 * 文言は「説明」ではなく**打つコマンドそのもの**を見せる。コマンドの無いカード (設定を
 * 触る手順・前提パッケージの案内) は読むだけのカードで、タップすると消える。
 */

/**
 * 案内の 1 手順。
 *
 * @param labelRes 何をする手順かの 1 行。
 * @param command 実行するコマンド。null なら「読むだけ」のカード (⚙設定 を触る手順など)。
 *   ⚠ **言語に依らない形だけを置く**こと。ここは翻訳されないので、本文に日本語を混ぜると
 *   英語環境でも日本語のコマンドが送られる。
 * @param askRes 人に入れてもらう値の見出し。**null 以外なら [command] の `%s` に入れる値を
 *   先に聞く**。自分の値 (フィードの URL・時刻・しきい値) が要る手順は必ずこちらにする —
 *   見本の値をそのまま実行させると、`https://example.com` のような**動くはずのない設定**が
 *   黙って入る (利用者の指摘・0.8.335)。
 * @param askDefault 入力欄の初期値。空なら空欄で出す (入れてもらうまで実行しない)。
 * @param commandOf 端末ごとに中身が変わるコマンドを、案内を出すときに組み立てる (0.8.603)。
 *   ⚠ 組み立てに使うのは**既存のコマンドだけ**。案内のために新しいコマンドを作らない (利用者の指定)。
 */
data class GuideStep(
    @param:StringRes val labelRes: Int,
    val command: String? = null,
    @param:StringRes val askRes: Int? = null,
    val askDefault: String = "",
    val commandOf: ((Context) -> String)? = null,
) {
    /** カードに見せる形。まだ入っていない値は初期値、初期値も無ければ `…` を埋めて見せる。 */
    val preview: String?
        get() = command?.let { if (askRes == null) it else it.format(askDefault.ifEmpty { "…" }) }

    /**
     * [commandOf] をこの端末で組み立てて [command] に入れた形。**組み立てられない手順は出さない** —
     * 押しても何も送らない読むだけのカードに化けると、手順を済ませたと思わせてしまう。
     */
    fun resolved(context: Context): GuideStep? {
        val build = commandOf ?: return this
        return runCatching { copy(command = build(context), commandOf = null) }.getOrNull()
    }
}

/**
 * 同梱サンプルマクロ 1 本、または機能 1 つぶんの案内。
 *
 * [id] は設定画面から呼ぶときの識別子 (= サンプルのファイル名から `.sh` を除いたもの。機能の案内は機能の名前)。
 * **改名しないこと**。
 *
 * ⚠ **名前 ([id]) と説明 ([descRes]) は必ず並べて出す** (0.8.335・利用者の指摘)。説明文だけを
 * 並べていたときは「入門: できごとに反応する」が何のことか分からず、**どのマクロの話かも
 * 読めなかった**。かといって名前だけにすると、今度は**何をするものか分からない**。
 * どちらか片方では足りないので、一覧では名前を主・説明を添えて 2 行で出し ([guideTitle] は
 * 案内の見出し用に 1 行へ畳む)。⚠ 説明の文言に名前を書き込まないこと — 二重管理になる。
 *
 * ⚠ **説明は「何をするか」を言い切る** (0.8.337・利用者の指摘)。「電池が減ったら知らせる」は
 * 何 % でなのか、「毎朝きまった時刻に読み上げる」は何を読み上げるのかが読めなかった。
 * 動かしてみないと分からない値・中身は、説明の側に出す ([guideDesc])。
 */
enum class Guide(
    val id: String,
    @param:StringRes val descRes: Int,
    val steps: List<GuideStep>
) {
    /**
     * 画面の端のバーからアプリ一覧を開く (0.8.601)。マクロではなく、機能を使えるようにするまでの案内。
     *
     * 許可 2 つ → 見本のアプリ一覧パネルを作る → ON の順に並べ、最後に設定の開き方を読むだけの
     * カードで置く (利用者の指定)。
     * ⚠ **ON にしただけではパネルを作らない** (0.8.603・利用者の判断)。0.8.601〜0.8.602 は、パネルが
     * 1 枚も無いと ON のときに自動で作っていた。見本は空ではなくアプリ入りで作る (利用者の指定)。
     * パッケージ名は端末ごとに違うので、行は案内を出すときに既存の `z2-edge panel` / `z2-edge set` で
     * 組み立てる ([com.zerotoship.z2term.edge.EdgeDefaultPanel.command])。
     * ⚠ バーの上下スワイプのスクロールはユーザー補助が無いと動かないので、重ねて表示と並べて許可させる。
     */
    EDGE_PANEL("edge-panel", R.string.guide_desc_edge_panel, listOf(
        GuideStep(R.string.guide_step_edge_overlay, "z2-edge permission"),
        GuideStep(R.string.guide_step_edge_accessibility, "z2-key permission"),
        GuideStep(R.string.guide_step_edge_panel, commandOf = { EdgeDefaultPanel.command(it) }),
        GuideStep(R.string.guide_step_edge_on, "z2-edge on"),
        GuideStep(R.string.guide_step_edge_settings),
    )),

    EDGE_WORKSPACE("edge-workspace", R.string.guide_desc_edge_workspace, listOf(
        GuideStep(R.string.guide_step_edge_local),
        GuideStep(R.string.guide_step_edge_overlay, "z2-edge permission"),
        GuideStep(R.string.guide_step_translation_cli),
        GuideStep(R.string.guide_step_install, "z2-macro install translate"),
        GuideStep(R.string.guide_step_sample_create, commandOf = { EdgeSamplePanels.command(it) }),
        GuideStep(R.string.guide_step_sample_open, "z2-edge on && z2-edge open sample-tools"),
        GuideStep(R.string.guide_step_sample_use),
        GuideStep(R.string.guide_step_edge_settings),
    )),

    /**
     * 充電やイヤホンの抜き差しに反応する。`z2-when` で待ち受ける形の見本。
     *
     * ⚠ **常駐スクリプトに戻さない** (0.8.338・利用者の判断)。0.8.337 まではログを 15 秒ごとに
     * 見に行く形で、**反応が 10 秒近く遅れる**のを説明に書いていた。待ち受けはアプリ側が
     * できる (`event:` はブロードキャストを受けたその場でルールを走らせる) ので、遅れも
     * 待機中の電池消費も消える。⚠ **ワイルドカードで 2 本にまとめる** — 4 つのイベントに
     * 4 本のルールを並べると、自動化タブが同じマクロで埋まる。
     */
    WATCH_BASIC("watch-basic", R.string.guide_desc_watch_basic, listOf(
        GuideStep(R.string.guide_step_events_on),
        GuideStep(R.string.guide_step_install, "z2-macro install watch-basic"),
        GuideStep(
            R.string.guide_step_when_charge,
            "z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh"
        ),
        GuideStep(
            R.string.guide_step_when_headset,
            "z2-when 'event:headset_*' run ~/.z2term/macros/watch-basic.sh"
        ),
        GuideStep(
            R.string.guide_step_try_event,
            "Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh"
        ),
    )),

    /** 電池が減ったら知らせる。z2-when が起こす「使い切り」の形。 */
    BATTERY_ALERT("battery-alert", R.string.guide_desc_battery_alert, listOf(
        GuideStep(R.string.guide_step_events_on),
        GuideStep(R.string.guide_step_install, "z2-macro install battery-alert"),
        GuideStep(
            R.string.guide_step_when,
            "z2-when battery:below=%s run ~/.z2term/macros/battery-alert.sh",
            askRes = R.string.guide_ask_battery_level,
            askDefault = "20"
        ),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/battery-alert.sh"),
    )),

    /** 毎朝きまった時刻に読み上げる。時刻トリガーの見本。 */
    DAILY_REPORT("daily-report", R.string.guide_desc_daily_report, listOf(
        GuideStep(R.string.guide_step_install, "z2-macro install daily-report"),
        GuideStep(
            R.string.guide_step_when,
            "z2-when time:daily=%s run ~/.z2term/macros/daily-report.sh",
            askRes = R.string.guide_ask_daily_time,
            askDefault = "07:00"
        ),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/daily-report.sh"),
    )),

    /** 通知のワンタイムコードを自動でコピーする。 */
    OTP_CLIP("otp-clip", R.string.guide_desc_otp_clip, listOf(
        GuideStep(R.string.guide_step_notify_on),
        GuideStep(R.string.guide_step_install, "z2-macro install otp-clip"),
        GuideStep(R.string.guide_step_when, "z2-when notify:otp run ~/.z2term/macros/otp-clip.sh"),
    )),

    /** SMS のワンタイムコードを自動でコピーする (伏せ字を迂回できる確実な方)。 */
    OTP_SMS("otp-sms", R.string.guide_desc_otp_sms, listOf(
        GuideStep(R.string.guide_step_sms_on),
        GuideStep(R.string.guide_step_install, "z2-macro install otp-sms"),
        GuideStep(R.string.guide_step_when, "z2-when sms:otp run ~/.z2term/macros/otp-sms.sh"),
    )),

    /** 着信通知に含まれる電話番号をコピー通知で渡す。 */
    UNKNOWN_CALL("unknown-call", R.string.guide_desc_unknown_call, listOf(
        GuideStep(R.string.guide_step_notify_on),
        GuideStep(R.string.guide_step_install, "z2-macro install unknown-call"),
        GuideStep(
            R.string.guide_step_when,
            "z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh"
        ),
        GuideStep(
            R.string.guide_step_when_missed,
            "z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh"
        ),
    )),

    /**
     * 通知でリマインドする。**以前スニペットに置いていた `remind.sh help` の行き先**で、
     * 入れる → 受け口とタイル → 使い方 の順に並べてある。
     */
    REMIND("remind", R.string.guide_desc_remind, listOf(
        GuideStep(R.string.guide_step_install, "z2-macro install remind"),
        GuideStep(R.string.guide_step_setup, "sh ~/.z2term/macros/remind.sh setup"),
        GuideStep(R.string.guide_step_help, "remind.sh help"),
        GuideStep(R.string.guide_step_list, "remind.sh list"),
    )),

    /**
     * フィードの新着を通知する。前提 (python3) から、読む道具 (`rss-open`) の用意まで**1 本**。
     *
     * ⚠ `rss` と `rss-open` を別の案内に分けていたのをやめた (0.8.335・利用者の指摘)。
     * 一覧に「フィードの新着を通知する」「集めた記事を 1 本ずつ開く」が並んでいても、
     * **同じ 1 つの購読の話**だとは読めない。集めるのと読むのは続きなので、続けて並べる。
     */
    RSS("rss", R.string.guide_desc_rss, listOf(
        GuideStep(R.string.guide_step_needs_python),
        GuideStep(R.string.guide_step_install, "z2-macro install rss"),
        GuideStep(
            R.string.guide_step_feeds,
            "mkdir -p ~/.z2term/rss && echo \"%s\" >> ~/.z2term/rss/feeds.txt",
            askRes = R.string.guide_ask_feed_url
        ),
        GuideStep(
            R.string.guide_step_when,
            "z2-when time:every=%s run ~/.z2term/macros/rss.sh",
            askRes = R.string.guide_ask_interval,
            askDefault = "30m"
        ),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/rss.sh"),
        GuideStep(R.string.guide_step_rss_open_install, "z2-macro install rss-open"),
        GuideStep(R.string.guide_step_rss_open_try, "sh ~/.z2term/macros/rss-open.sh"),
        GuideStep(R.string.guide_step_widget),
    )),

    /** QR にして別の端末へ渡す。 */
    QR("qr", R.string.guide_desc_qr, listOf(
        GuideStep(R.string.guide_step_needs_qrencode),
        GuideStep(R.string.guide_step_install, "z2-macro install qr"),
        GuideStep(R.string.guide_step_help, "qr.sh -h"),
        GuideStep(
            R.string.guide_step_try,
            "qr.sh \"%s\"",
            askRes = R.string.guide_ask_qr_text
        ),
    ));

    companion object {
        /** 設定画面に並べる順 (= 宣言順)。 */
        val ALL: List<Guide> = entries.toList()

        fun byId(id: String): Guide? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 案内の説明 1 行。**埋める値がある案内はここで入れる**ので、呼ぶ側は値を知らなくてよい。
 * いまはどの説明も埋める値を持たないが、入口を 1 か所にしておく (一覧と見出しで食い違わせない)。
 */
@Composable
fun guideDesc(guide: Guide): String = stringResource(guide.descRes)

/**
 * 案内の見出し 1 行 (`rss — フィードの新着を通知して、1 本ずつ読む`)。
 *
 * 一覧では名前と説明を 2 行に分けて出すが、カードの見出しは 1 行しか無いのでここで畳む。
 * **繋ぎ方はここ 1 か所**にして、`strings.xml` 側に名前を書き込まない。
 */
@Composable
fun guideTitle(guide: Guide): String = "${guide.id} — ${guideDesc(guide)}"

/**
 * いま出している案内。**画面をまたいで 1 つだけ**持つ。
 *
 * GUI タブの ⚙設定 から案内を選んだときは、案内が読める端末タブへ移ってから出したい。
 * 画面ごとの `remember` に置くと移った先へ引き継げないので、ここに置く。
 * アプリを開いている間だけの状態で、永続化はしない (案内は毎回選んで出すもの)。
 */
object GuideHost {
    var current: Guide? by mutableStateOf(null)
}

/**
 * 案内 1 本を端末の上にカードで出す。
 *
 * @param onRun カードのコマンドを実行する (`Ctrl-C` → コマンド → ⏎ は呼び出し側が付ける)。
 * @param onFinish カードが尽きたか、見出しの ✕ が押されたとき。
 */
@Composable
fun GuideCards(
    guide: Guide,
    onRun: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    // 触った / ✕ したカードはその場で消す (どこまで進んだかが見た目で分かる)。
    val remaining = remember(guide) {
        mutableStateListOf(*guide.steps.mapNotNull { it.resolved(context) }.toTypedArray())
    }
    // 値を聞いている最中の手順 ([GuideStep.askRes] 付き)。聞き終わるまで実行しない。
    var asking by remember(guide) { mutableStateOf<GuideStep?>(null) }

    fun done(step: GuideStep) {
        remaining.remove(step)
        if (remaining.isEmpty()) onFinish()
    }

    GuideCardColumn(
        title = guideTitle(guide),
        hint = stringResource(R.string.guide_hint),
        onClose = onFinish
    ) {
        remaining.forEach { step ->
            GuideCardRow(
                label = stringResource(step.labelRes),
                command = step.preview,
                onTap = {
                    if (step.askRes != null && step.command != null) {
                        // 自分の値が要る手順は、聞いてから送る (見本の値のまま実行させない)。
                        asking = step
                    } else {
                        step.command?.let(onRun)
                        done(step)
                    }
                },
                onSkip = { done(step) }
            )
        }
    }

    asking?.let { step ->
        GuideAskDialog(
            title = stringResource(step.askRes ?: return@let),
            initial = step.askDefault,
            onConfirm = { value ->
                asking = null
                step.command?.let { onRun(it.format(value)) }
                done(step)
            },
            onDismiss = { asking = null }
        )
    }
}

/**
 * 手順に入れる値を聞くダイアログ。
 *
 * **空のままでは送れない** (確定ボタンが効かない)。空で送れると `echo "" >> feeds.txt` の
 * ような空振りの行が黙って積まれる。やめたときは手順を消さずに残す — 入れ直せるように。
 */
@Composable
private fun GuideAskDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZtsBgCard,
        titleContentColor = ZtsTextPrimary,
        title = { Text(title, fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(
                    stringResource(R.string.guide_ask_run),
                    color = if (value.isNotBlank()) ZtsGreen else ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.action_cancel),
                    color = ZtsTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

/**
 * 案内カードの外枠 (見出し + ✕ + 中身 + 脚注)。[IntroCards] と見た目を揃えるために共有する。
 */
@Composable
internal fun GuideCardColumn(
    title: String,
    hint: String,
    /**
     * ✕ を押したときの動作。**null なら ✕ を描かない** = 消せないカード (0.8.342)。
     * 消せなくしてよいのは「消すと詰む」ものだけ (OS 未導入の案内 = [NoOsNoticeCard])。
     * 手順の案内 ([GuideCards]) と はじめの案内 ([IntroCards]) には必ず逃げ道を残すこと。
     */
    onClose: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            // ✕ はいつでも出せる逃げ道。押したら丸ごと閉じる。null のときは描かない (消せないカード)。
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✕",
                        color = ZtsTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // カードの並びだけをスクロールさせる。見出しの ✕ と脚注は常に見えている位置に残す。
        // ⚠ 高さの上限を付けないと、長いコマンドのカードが並んだとき端末を押し出して画面から
        // はみ出し、**続きを見る手段が無い**まま上下のスワイプがカードのタップになっていた。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }

        Text(
            text = hint,
            color = ZtsTextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 案内カード 1 枚。左半分 (本文) をタップすると実行、右の ✕ で送らずに消す。
 *
 * [command] が null のときは読むだけのカード。タップは「読んだ」= 消えるだけで何も送らない。
 */
@Composable
internal fun GuideCardRow(
    label: String,
    command: String?,
    onTap: () -> Unit,
    /** 送らずに消す ✕。**null なら描かない** = 消せない行 (0.8.342・[GuideCardColumn] と同じ理由)。 */
    onSkip: (() -> Unit)?,
) {
    // ⚠ `clickable` は**指が枠の外へ出たときしか**タップを取り消さない。カードの上で上下に
    // スワイプすると、離した瞬間にタップとしてコマンドが送られていた (利用者の指摘)。
    // 押した位置から touch slop を超えて動いたジェスチャーは、離してもタップに数えない。
    // 動きは Initial パスで見るだけで消費しないので、外側のスクロールはそのまま効く。
    val slop = LocalViewConfiguration.current.touchSlop
    val moved = remember { BooleanArray(1) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .pointerInput(slop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        moved[0] = false
                        do {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > slop) moved[0] = true
                        } while (change.pressed)
                    }
                }
                .clickable { if (!moved[0]) onTap() }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                color = ZtsTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            // 説明ではなく、実際に送られるコマンドをそのまま見せる。
            if (command != null) {
                Text(
                    text = command,
                    color = ZtsGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        // 要らない手順を「送らずに」消すための ✕ (要望)。null のときは描かない。
        if (onSkip != null) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = ZtsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
