package com.zerotoship.z2term.settings

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * 設定ページの項目グループ (アコーディオンの単位)。
 *
 * 設定項目が増えて 1 本の長いリストでは目的の設定に辿り着けなくなったため、関連する
 * セクションをこの 7 グループに束ね、開閉できるようにした (要望)。**宣言順が画面上の
 * 表示順**で、機能の関連が近いものが隣り合うよう並べ替えてある。
 *
 * [id] は開閉状態の永続化キー ([SettingsGroupStore]) に使う固定文字列。**改名しないこと**
 * (改名するとユーザーの開閉状態が既定へ戻る)。
 *
 * [defaultOpen] は保存が無いとき (初回) の状態。開閉はユーザー操作で永続化されるので
 * 重要度は低く、よく触る「表示」「キーボード」だけ開いておく。
 */
enum class SettingsGroup(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val defaultOpen: Boolean
) {
    /** テーマ / フォントファミリー / フォントサイズ / スクロールバック行数 */
    DISPLAY(
        "display",
        R.string.settings_group_display,
        R.string.settings_group_display_desc,
        true
    ),

    /**
     * キーボードの大きさ / 独自キーボードスタイル / キーボード位置 (横画面) /
     * 内蔵キーボードを他でも使う (IME) / 日本語 IME 学習履歴 / 言語。
     *
     * 「入力・言語」グループはここへ統合した (要望)。⚠ どれも**打つときの設定**で、
     * キーボードを探した人が別のグループを開き直すことになっていたため。
     * 旧グループの id `"input"` は [SettingsGroupStore] に残るが、参照が無いので無視される。
     */
    KEYBOARD(
        "keyboard",
        R.string.settings_group_keyboard,
        R.string.settings_group_keyboard_desc,
        true
    ),

    /** ディストロ / OS データの削除 / ログインシェル / 外部ストレージ / GUI ターミナル */
    LINUX(
        "linux",
        R.string.settings_group_linux,
        R.string.settings_group_linux_desc,
        false
    ),

    /** 常駐サーバー / 通知検知 / システムイベント検知 / ロック解除の失敗監視 / プロセス保護 */
    AUTOMATION(
        "automation",
        R.string.settings_group_automation,
        R.string.settings_group_automation_desc,
        false
    ),

    /** 端末リセット / キャッシュ削除 / 設定の初期化 */
    MAINTENANCE(
        "maintenance",
        R.string.settings_group_maintenance,
        R.string.settings_group_maintenance_desc,
        false
    ),

    /** 実験的・開発者向け / 実行エンジン (裏設定。解放時のみ中身が出る) */
    DEVELOPER(
        "developer",
        R.string.settings_group_developer,
        R.string.settings_group_developer_desc,
        false
    ),

    /**
     * 使い方 (Tips) — **画面に出ていない操作**の一覧 (0.8.399)。
     *
     * ⛔ ここに設定は置かない。トグルが混ざると読み物でなくなり、設定を探しに来た人と
     * 使い方を知りたい人の両方が迷う。
     *
     * 既定は閉じた状態。見出しと説明だけ見えていれば「何かある」ことは伝わるので、
     * 毎回開いて設定の上を長くする必要は無い (利用者判断)。
     */
    TIPS(
        "tips",
        R.string.settings_group_tips,
        R.string.settings_group_tips_desc,
        false
    ),

    /** アプリ情報 / OSS ライセンス */
    ABOUT(
        "about",
        R.string.settings_group_about,
        R.string.settings_group_about_desc,
        false
    );

    companion object {
        /** 画面上の表示順 (= 宣言順)。 */
        val ALL: List<SettingsGroup> = entries.toList()
    }
}
