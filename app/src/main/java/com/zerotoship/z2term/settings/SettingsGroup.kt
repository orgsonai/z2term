package com.zerotoship.z2term.settings

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * 設定ページの項目グループ (アコーディオンの単位)。
 *
 * 設定項目が増えて 1 本の長いリストでは目的の設定に辿り着けなくなったため、関連する
 * セクションをこの 8 グループに束ね、開閉できるようにした (要望)。**宣言順が画面上の
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
    val defaultOpen: Boolean
) {
    /** テーマ / フォントファミリー / フォントサイズ / スクロールバック行数 */
    DISPLAY("display", R.string.settings_group_display, true),

    /** キーボードの大きさ / 独自キーボードスタイル / キーボード位置 (横画面) */
    KEYBOARD("keyboard", R.string.settings_group_keyboard, true),

    /** 日本語 IME 学習履歴 / 言語 */
    INPUT("input", R.string.settings_group_input, false),

    /** ディストロ / OS データの削除 / ログインシェル / 外部ストレージ / GUI ターミナル */
    LINUX("linux", R.string.settings_group_linux, false),

    /** 常駐サーバー / 通知検知 / システムイベント検知 / ロック解除の失敗監視 / プロセス保護 */
    AUTOMATION("automation", R.string.settings_group_automation, false),

    /** 端末リセット / キャッシュ削除 / 設定の初期化 */
    MAINTENANCE("maintenance", R.string.settings_group_maintenance, false),

    /** 実験的・開発者向け / 実行エンジン (裏設定。解放時のみ中身が出る) */
    DEVELOPER("developer", R.string.settings_group_developer, false),

    /** アプリ情報 / OSS ライセンス */
    ABOUT("about", R.string.settings_group_about, false);

    companion object {
        /** 画面上の表示順 (= 宣言順)。 */
        val ALL: List<SettingsGroup> = entries.toList()
    }
}
