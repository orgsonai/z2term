package com.zerotoship.z2term.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R

/**
 * 「Linux の OS がまだ 1 つも入っていない」ことを伝える 1 枚 (0.8.314)。
 *
 * ## なぜダウンロード確認ダイアログをやめたのか
 *
 * rootfs を同梱しないので、初回起動でいきなり Alpine のダウンロード確認が出ていた。
 * これは**「どの OS から始めるか」を利用者が選ぶ前に、既定の 1 本を押し付ける**形になっていて、
 * 「Arch から始めたい」人は毎回断ることになる。しかも断っても状態は変わらないので、
 * **タブを開くたびに同じダイアログが出る**。ダイアログは画面を塞ぐので、これが一番うるさい。
 *
 * そこで **OS が 1 つも無いときだけ**、塞がない案内カードを出す形に変えた。モーダルではないので、
 * 出ていても端末は触れる。OS が 1 つでも入っていれば、この案内も自動ダウンロードの催促も出ない。
 * 選んでいる OS がまだ無いときだけ、従来どおりダウンロード確認ダイアログが出る
 * (= 利用者が選んだ結果なので)。
 *
 * ## ⚠ 消せなくした (0.8.342・利用者の判断)
 *
 * 0.8.341 まではこのカードに ✕ が付いていた。だが**ここは「⚙設定 › Linux環境」を教える唯一の口**
 * なので、消すと**黒い画面と `#` だけが残り、何を押せば Linux が入るのか画面のどこにも出ていない**
 * 状態になる (rootfs を同梱しないので**全員がこの状態から始まる**)。
 *
 * 0.8.340 では「消せるまま + OS が無い間だけツールバーに 📥」で戻り道を作ったが、
 * **📥 を押しても設定画面が開くだけで次に何をすればいいか分からない**と実機で指摘され、撤回した。
 * 今は:
 *
 *  - **OS が 1 つも無い間は消せない** (✕ を出さない)。塞がないカードなので、消せなくても端末は触れる。
 *  - **⚙設定 の中でも同じ案内を上部に固定する** ([NoOsSettingsNotice])。設定画面まで来た人が
 *    「どの項目か」で迷わないよう、押すと **Linux環境 のセクションまで運ぶ**。
 *  - **OS が 1 つ入れば両方とも出ない。**
 *
 * ⚠ **消せなくしてよいのは「消すと詰む」ものだけ。** 手順の案内 ([GuideCards]) と
 * はじめの案内 ([IntroCards]) には ✕ を残すこと — あちらは消しても端末が使える。
 */
object NoOsNotice

/**
 * 端末の上に出す案内カード。押すと ⚙設定 (→ Linux環境) を開く。**消せない** (0.8.342)。
 */
@Composable
fun NoOsNoticeCard(
    onOpenSettings: () -> Unit,
) {
    GuideCardColumn(
        title = stringResource(R.string.no_os_title),
        hint = stringResource(R.string.no_os_hint),
        onClose = null
    ) {
        GuideCardRow(
            label = stringResource(R.string.no_os_action),
            command = null,
            onTap = onOpenSettings,
            onSkip = null
        )
    }
}

/**
 * ⚙設定 の上部に固定する同じ案内 (0.8.342)。
 *
 * 端末側のカードを押して設定画面に来ても、**項目が多いのでどこが「Linux環境」なのか分からない**
 * (実機の指摘)。そこで設定画面でも同じ見た目の案内を上部に出し、押したら
 * **Linux環境 のセクションまでスクロールして運ぶ** ([onGoToDistro])。
 * スクロール領域の**外**に置くこと — 中に入れると下へスクロールした時点で見えなくなる。
 */
@Composable
fun NoOsSettingsNotice(
    onGoToDistro: () -> Unit,
) {
    GuideCardColumn(
        title = stringResource(R.string.no_os_title),
        hint = stringResource(R.string.no_os_settings_hint),
        onClose = null
    ) {
        GuideCardRow(
            label = stringResource(R.string.no_os_settings_action),
            command = null,
            onTap = onGoToDistro,
            onSkip = null
        )
    }
}
