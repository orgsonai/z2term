package com.zerotoship.z2term.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R

/**
 * 初回だけ端末の上に出る「最初の数枚」(0.8.231・0.8.286 で 3 枚 → 4 枚・0.8.314 で 3 枚)。
 *
 * **何のためか**: 入れた直後の画面は黒地に `#` だけで、Linux を知らない人はここで止まる。
 * 知っている人にも「ふつうの端末」に見えて、Z2Term の差 (= Android を触れること) に
 * 気付かないまま終わる。**最初の 90 秒で「うごいた」を 1 回配る**のが目的。
 *
 * **やらないこと**（ここを外すとただのお節介になる）:
 *  - 全画面ウィザードにしない。プロンプトの上に**薄いカードが数枚**乗るだけ。
 *  - **むやみに増やさない**。増やすなら**同じだけ外す**か、利用者が決めたときだけ。
 *    並べたいものは `z2help` と [Guide] (案内) の仕事。
 *  - 触った / ✕ した枚は消え、全部消えるか見出しの ✕ を押したら**二度と出ない**
 *    ([com.zerotoship.z2term.settings.AppSettings.introDone])。
 *
 * ⚠ **出すのは Linux の OS が 1 つ入ってから** (0.8.339・利用者の指摘)。まっさらな端末に出すと、
 * カードを押しても走る先が無い ([com.zerotoship.z2term.core.TerminalSession.hasAnyDistro] が false の
 * 間は端末が動いていない) のに**押した枚から消えていき**、3 枚とも消えた時点で `introDone` が立って
 * **一度も動かないまま二度と出なくなる**。出す条件は [com.zerotoship.z2term.ui.terminal] の
 * TerminalScreen 側が持ち、ここは「出すと決まった後」の見た目と送り方だけを持つ。
 * OS が無い間に出るのは [NoOsNoticeCard] の方。
 *
 * ⚠ **タップしたら実行する** (0.8.314・利用者の判断)。以前は「入力行に入れるだけで ⏎ は
 * 人が押す」作法だったが、**打ちかけの文字と混ざって意図しない行が走る**恐れがあるため、
 * `Ctrl-C` で行を捨ててから丸ごと送る形に変えた。送り方は [GuideCards] と共通。
 *
 * 文言は「説明」ではなく**打つコマンドそのもの**を見せる。読ませるのではなく、
 * 1 回動かしてもらうための画面なので。
 */
@Composable
fun IntroCards(
    onRun: (String) -> Unit,
    onOpenGuide: (Guide) -> Unit,
    onFinish: () -> Unit,
) {
    // 触ったカードはその場で消す (どれを試したかが見た目で分かる)。全部消えたら終わり。
    val remaining = remember { mutableStateListOf(*IntroCard.entries.toTypedArray()) }

    GuideCardColumn(
        title = stringResource(R.string.intro_title),
        hint = stringResource(R.string.intro_footer),
        onClose = onFinish
    ) {
        remaining.forEach { card ->
            GuideCardRow(
                label = stringResource(card.labelRes),
                command = card.command,
                onTap = {
                    card.command?.let(onRun)
                    card.guide?.let(onOpenGuide)
                    remaining.remove(card)
                    if (remaining.isEmpty()) onFinish()
                },
                onSkip = {
                    remaining.remove(card)
                    if (remaining.isEmpty()) onFinish()
                }
            )
        }
    }
}

/**
 * 中身。**Android を触れること**を最初に見せる 2 枚と、**リマインドの案内**を開く 1 枚。
 * どれも 1 行で結果が出るものだけにする (待たされるものを最初に置かない)。
 *
 * ⚠ **`sshd` は外した** (0.8.286・利用者の判断)。「PC からこの端末に入る」は刺さる人には
 * 刺さるが、**最初の 90 秒に置くほど一般的ではない**。代わりに、通知で予定を知らせる
 * リマインドを置く — こちらは誰でも使い道が分かる。
 * ⚠ そのリマインドは **0.8.314 で「案内を開く」1 枚に畳んだ**。以前は `install` と `setup` の
 * 2 枚を並べていたが、実際には使い方 (`help`) とタイル登録まで要る。手順は [Guide.REMIND] に
 * 一本化し、ここからはその入口だけを出す。
 */
private enum class IntroCard(
    val labelRes: Int,
    /** 実行するコマンド。[guide] を開くだけのカードなら null。 */
    val command: String? = null,
    /** 押したら開く案内。コマンドのカードなら null。 */
    val guide: Guide? = null,
) {
    NOTIFY(R.string.intro_card_notify, command = "z2-notify -h \"z2term\" \"Android を触れます\""),
    TORCH(R.string.intro_card_torch, command = "z2-torch"),
    REMIND_GUIDE(R.string.intro_card_remind_guide, guide = Guide.REMIND),
}
