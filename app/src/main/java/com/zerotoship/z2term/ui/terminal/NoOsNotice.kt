package com.zerotoship.z2term.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zerotoship.z2term.R

/**
 * Android標準シェルの利用範囲と、Linuxを追加する入口を案内する。
 * OSが無い間は端末・設定に表示し、OS導入後に消す。
 * 設定側からはLinuxセクションを開いてスクロールする。
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
