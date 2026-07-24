package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ライブ tail (D2) の「1 つ上のフォルダ」計算 ([TailStore.parentOf]) の検証。
 *
 * ここを間違えると `~` より上へ辿れてしまう / `~` に戻れなくなる。パス解決本体
 * ([TailStore.resolve]) は Context が要るのでここでは扱わない (`~` の外を弾く判定は
 * canonicalPath 比較で行っている)。
 */
class TailStoreTest {

    @Test fun parentOfNestedPath() {
        assertEquals(".z2term", TailStore.parentOf(".z2term/when"))
        assertEquals(".z2term/when", TailStore.parentOf(".z2term/when/w1.log"))
    }

    @Test fun parentOfTopLevelIsHome() {
        // `~` 直下の 1 つ上は `~` 自身 (空文字)。
        assertEquals("", TailStore.parentOf(".z2term"))
    }

    @Test fun homeHasNoParent() {
        // `~` より上へは辿らせない。
        assertNull(TailStore.parentOf(""))
    }

    @Test fun trailingSlashIsIgnored() {
        assertEquals(".z2term", TailStore.parentOf(".z2term/when/"))
    }
}
