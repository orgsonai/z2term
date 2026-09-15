package com.zerotoship.z2term.tile

import android.app.Activity
import android.os.Bundle

/**
 * クイック設定パネルを畳むためだけの踏み台。**画面を持たず、開いた瞬間に自分を閉じる**。
 *
 * ⚠ **なぜ要るか**: タイルからマクロを走らせても、パネルが開いたままだと
 * ヘッドアップ通知 (画面上部のバナー) もトーストも**パネルの下に隠れて触れない**。
 * Android がパネルを畳む口は `TileService.startActivityAndCollapse` しか無く、
 * それには Activity が要る — 中身のいらない Activity をここに置く理由がそれ。
 *
 * ⚠ 履歴に残さない (`excludeFromRecents` / `noHistory`)。タスク一覧に空の画面が並ぶと、
 * 押すたびにゴミが増えたように見える。テーマも透明なので視覚的には何も起きない。
 */
class TileCollapseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        if (savedInstanceState == null) intent.getStringExtra("action_macro")?.let { name ->
            runCatching {
                com.zerotoship.z2term.automation.ActionRuntime.start(applicationContext, name, waitForForeground = true)
            }.onFailure { android.widget.Toast.makeText(this, it.message, android.widget.Toast.LENGTH_LONG).show() }
        }
        // 出入りのアニメーションも消す (一瞬の暗転が「何か開いた」と見えてしまうため)。
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
