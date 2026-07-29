package com.zerotoship.z2term.ui.terminal

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
import androidx.compose.runtime.remember
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
 * 初回だけ端末の上に出る「最初の数枚」(0.8.231・0.8.286 で 3 枚 → 4 枚)。
 *
 * **何のためか**: 入れた直後の画面は黒地に `#` だけで、Linux を知らない人はここで止まる。
 * 知っている人にも「ふつうの端末」に見えて、Z2Term の差 (= Android を触れること) に
 * 気付かないまま終わる。**最初の 90 秒で「うごいた」を 1 回配る**のが目的。
 *
 * **やらないこと**（ここを外すとただのお節介になる）:
 *  - 全画面ウィザードにしない。プロンプトの上に**薄いカードが 3 枚**乗るだけ。
 *  - **勝手に実行しない**。タップで**入力行に入るだけ**で、⏎ は人が押す（共有受け取りと同じ作法）。
 *  - **むやみに増やさない**。増やすなら**同じだけ外す**か、利用者が決めたときだけ
 *    (0.8.286 は `sshd` を外してリマインドの 2 枚を入れた)。並べたいものは `z2help` の仕事。
 *  - 触った枚は消え、3 枚とも消えるか × を押したら**二度と出ない** ([AppSettings.introDone])。
 *
 * 文言は「説明」ではなく**打つコマンドそのもの**を見せる。読ませるのではなく、
 * 1 回動かしてもらうための画面なので。
 */
@Composable
fun IntroCards(
    onInsert: (String) -> Unit,
    onFinish: () -> Unit,
) {
    // 触ったカードはその場で消す (どれを試したかが見た目で分かる)。全部消えたら終わり。
    val remaining = remember { mutableStateListOf(*IntroCard.entries.toTypedArray()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.intro_title),
                color = ZtsTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            // × はいつでも出せる逃げ道。押したら二度と出さない。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onFinish)
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

        remaining.forEach { card ->
            IntroCardRow(
                label = stringResource(card.labelRes),
                command = card.command,
                onTap = {
                    onInsert(card.command)
                    remaining.remove(card)
                    if (remaining.isEmpty()) onFinish()
                }
            )
        }

        Text(
            text = stringResource(R.string.intro_footer),
            color = ZtsTextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 中身。**Android を触れること**を最初に見せる 2 枚と、**リマインド**の 2 枚。
 * どれも 1 行で結果が出るものだけにする (待たされるものを最初に置かない)。
 *
 * ⚠ **`sshd` は外した** (0.8.286・利用者の判断)。「PC からこの端末に入る」は刺さる人には
 * 刺さるが、**最初の 90 秒に置くほど一般的ではない**。代わりに、通知で予定を知らせる
 * リマインドを置く — こちらは誰でも使い道が分かる。
 * ⚠ 入れる (`install`) と使えるようにする (`setup`) は**別の 1 枚**にしてある。1 枚に
 * `&&` で繋ぐと行が長く、何をしているのか読めなくなるため。順番が逆になっても、
 * `setup` 側が「remind.sh が無い」と言うので詰まらない。
 */
private enum class IntroCard(val labelRes: Int, val command: String) {
    NOTIFY(R.string.intro_card_notify, "z2-notify -h \"z2term\" \"Android を触れます\""),
    TORCH(R.string.intro_card_torch, "z2-torch"),
    REMIND_INSTALL(R.string.intro_card_remind_install, "z2-macro install remind"),
    REMIND_SETUP(R.string.intro_card_remind_setup, "sh ~/.z2term/macros/remind.sh setup"),
}

@Composable
private fun IntroCardRow(label: String, command: String, onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ZtsBgCard)
            .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
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
        // 説明ではなく、実際に入るコマンドをそのまま見せる。
        Text(
            text = command,
            color = ZtsGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
