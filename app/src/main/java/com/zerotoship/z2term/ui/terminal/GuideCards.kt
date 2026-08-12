package com.zerotoship.z2term.ui.terminal

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
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
 */
data class GuideStep(@param:StringRes val labelRes: Int, val command: String? = null)

/**
 * 同梱サンプルマクロ 1 本ぶんの案内。
 *
 * [id] は設定画面から呼ぶときの識別子 (= サンプルのファイル名から `.sh` を除いたもの)。
 * **改名しないこと**。
 */
enum class Guide(
    val id: String,
    @param:StringRes val titleRes: Int,
    val steps: List<GuideStep>
) {
    /** 入門: できごとに反応する。常駐させて使う形の見本。 */
    WATCH_BASIC("watch-basic", R.string.guide_title_watch_basic, listOf(
        GuideStep(R.string.guide_step_events_on),
        GuideStep(R.string.guide_step_install, "z2-macro install watch-basic"),
        GuideStep(R.string.guide_step_run_here, "sh ~/.z2term/macros/watch-basic.sh"),
        GuideStep(R.string.guide_step_resident),
    )),

    /** 電池が減ったら知らせる。z2-when が起こす「使い切り」の形。 */
    BATTERY_ALERT("battery-alert", R.string.guide_title_battery_alert, listOf(
        GuideStep(R.string.guide_step_events_on),
        GuideStep(R.string.guide_step_install, "z2-macro install battery-alert"),
        GuideStep(
            R.string.guide_step_when,
            "z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh"
        ),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/battery-alert.sh"),
    )),

    /** 毎朝きまった時刻に読み上げる。時刻トリガーの見本。 */
    DAILY_REPORT("daily-report", R.string.guide_title_daily_report, listOf(
        GuideStep(R.string.guide_step_install, "z2-macro install daily-report"),
        GuideStep(
            R.string.guide_step_when,
            "z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh"
        ),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/daily-report.sh"),
    )),

    /** 通知のワンタイムコードを自動でコピーする。 */
    OTP_CLIP("otp-clip", R.string.guide_title_otp_clip, listOf(
        GuideStep(R.string.guide_step_notify_on),
        GuideStep(R.string.guide_step_install, "z2-macro install otp-clip"),
        GuideStep(R.string.guide_step_when, "z2-when notify:otp run ~/.z2term/macros/otp-clip.sh"),
    )),

    /** SMS のワンタイムコードを自動でコピーする (伏せ字を迂回できる確実な方)。 */
    OTP_SMS("otp-sms", R.string.guide_title_otp_sms, listOf(
        GuideStep(R.string.guide_step_sms_on),
        GuideStep(R.string.guide_step_install, "z2-macro install otp-sms"),
        GuideStep(R.string.guide_step_when, "z2-when sms:otp run ~/.z2term/macros/otp-sms.sh"),
    )),

    /** 電話帳に無い番号の着信を控える。 */
    UNKNOWN_CALL("unknown-call", R.string.guide_title_unknown_call, listOf(
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
    REMIND("remind", R.string.guide_title_remind, listOf(
        GuideStep(R.string.guide_step_install, "z2-macro install remind"),
        GuideStep(R.string.guide_step_setup, "sh ~/.z2term/macros/remind.sh setup"),
        GuideStep(R.string.guide_step_help, "remind.sh help"),
        GuideStep(R.string.guide_step_list, "remind.sh list"),
    )),

    /** フィードの新着を通知する。前提 (python3) と置き場の用意まで並べる。 */
    RSS("rss", R.string.guide_title_rss, listOf(
        GuideStep(R.string.guide_step_needs_python),
        GuideStep(R.string.guide_step_install, "z2-macro install rss"),
        GuideStep(
            R.string.guide_step_feeds,
            "mkdir -p ~/.z2term/rss && echo https://example.com/feed >> ~/.z2term/rss/feeds.txt"
        ),
        GuideStep(R.string.guide_step_when, "z2-when time:every=30m run ~/.z2term/macros/rss.sh"),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/rss.sh"),
    )),

    /** 集めた記事を 1 本ずつ開く (ウィジェットのボタン用)。 */
    RSS_OPEN("rss-open", R.string.guide_title_rss_open, listOf(
        GuideStep(R.string.guide_step_install, "z2-macro install rss-open"),
        GuideStep(R.string.guide_step_try, "sh ~/.z2term/macros/rss-open.sh"),
        GuideStep(R.string.guide_step_widget),
    )),

    /** QR にして別の端末へ渡す。 */
    QR("qr", R.string.guide_title_qr, listOf(
        GuideStep(R.string.guide_step_needs_qrencode),
        GuideStep(R.string.guide_step_install, "z2-macro install qr"),
        GuideStep(R.string.guide_step_help, "qr.sh -h"),
        GuideStep(R.string.guide_step_try, "qr.sh \"https://example.com\""),
    ));

    companion object {
        /** 設定画面に並べる順 (= 宣言順)。 */
        val ALL: List<Guide> = entries.toList()

        fun byId(id: String): Guide? = entries.firstOrNull { it.id == id }
    }
}

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
    // 触った / ✕ したカードはその場で消す (どこまで進んだかが見た目で分かる)。
    val remaining = remember(guide) { mutableStateListOf(*guide.steps.toTypedArray()) }

    GuideCardColumn(
        title = stringResource(guide.titleRes),
        hint = stringResource(R.string.guide_hint),
        onClose = onFinish
    ) {
        remaining.forEach { step ->
            GuideCardRow(
                label = stringResource(step.labelRes),
                command = step.command,
                onTap = {
                    step.command?.let(onRun)
                    remaining.remove(step)
                    if (remaining.isEmpty()) onFinish()
                },
                onSkip = {
                    remaining.remove(step)
                    if (remaining.isEmpty()) onFinish()
                }
            )
        }
    }
}

/**
 * 案内カードの外枠 (見出し + ✕ + 中身 + 脚注)。[IntroCards] と見た目を揃えるために共有する。
 */
@Composable
internal fun GuideCardColumn(
    title: String,
    hint: String,
    onClose: () -> Unit,
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
            // ✕ はいつでも出せる逃げ道。押したら丸ごと閉じる。
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

        content()

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
    onSkip: () -> Unit,
) {
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
                .clickable(onClick = onTap)
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
        // 要らない手順を「送らずに」消すための ✕ (要望)。
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
