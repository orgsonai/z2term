package com.zerotoship.z2term.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R

/**
 * 「Linux の OS がまだ 1 つも入っていない」ことを伝える 1 枚 (0.8.314)。
 *
 * ## なぜダウンロード確認ダイアログをやめたのか
 *
 * foss 版は rootfs を同梱しないので、初回起動でいきなり Alpine のダウンロード確認が出ていた。
 * これは**「どの OS から始めるか」を利用者が選ぶ前に、既定の 1 本を押し付ける**形になっていて、
 * 「Arch から始めたい」人は毎回断ることになる。しかも断っても状態は変わらないので、
 * **タブを開くたびに同じダイアログが出る**。ダイアログは画面を塞ぐので、これが一番うるさい。
 *
 * そこで **OS が 1 つも無いときだけ**、塞がない案内カードを出す形に変えた:
 *
 *  - モーダルではないので、閉じなくても端末は触れる。
 *  - **✕ で消せる**。消した状態はアプリを開いている間だけ覚える ([dismissed]) ので、
 *    タブを開き直しても出戻らない。
 *  - ただし**次にアプリを開くと出る**。OS が無ければ端末は本当に使えないので、
 *    「二度と出ない」にはしない (⚙設定から入れれば自然に出なくなる)。
 *
 * OS が 1 つでも入っていれば、この案内も自動ダウンロードの催促も出ない。選んでいる OS が
 * まだ無いときだけ、従来どおりダウンロード確認ダイアログが出る (= 利用者が選んだ結果なので)。
 */
object NoOsNotice {
    /**
     * ✕ で消したか。**アプリを開いている間だけ**の記憶で、プロセスが死ねば戻る。
     *
     * タブをまたいで覚えておく必要があるので、Composable の `remember` ではなくここに置く
     * (タブごとに覚えると「新しいタブを開くたびに出る」という元の不満に戻る)。
     */
    var dismissed by mutableStateOf(false)
}

/**
 * 案内カード本体。押すと ⚙設定 → Linux環境 を開き、✕ で閉じる。
 */
@Composable
fun NoOsNoticeCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    GuideCardColumn(
        title = stringResource(R.string.no_os_title),
        hint = stringResource(R.string.no_os_hint),
        onClose = onDismiss
    ) {
        GuideCardRow(
            label = stringResource(R.string.no_os_action),
            command = null,
            onTap = onOpenSettings,
            onSkip = onDismiss
        )
    }
}
