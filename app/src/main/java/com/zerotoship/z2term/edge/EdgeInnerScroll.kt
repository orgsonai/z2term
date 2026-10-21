package com.zerotoship.z2term.edge

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/**
 * パネルの中でさらにスクロールする枠 (メモ・高さを決めた部品)。
 *
 * ⚠ パネル全体も ScrollView なので、何もしないと縦の指の動きは外側が先に取り、
 * 内側は動かない (利用者の報告: 「エッジパネルはスクロール出来てもメモ帳のスクロールが出来ない」)。
 * 中身がはみ出しているときだけ触れた時点で外側に譲らせる。はみ出していなければ外側が動く。
 */
internal class EdgeInnerScrollView(context: Context) : ScrollView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = claimVerticalScroll(this, event) {
        super.dispatchTouchEvent(event)
    }
}

/** [view] が縦にスクロールできるなら、この一連のタッチの間は親にスクロールを取らせない。 */
internal inline fun claimVerticalScroll(view: View, event: MotionEvent, dispatch: () -> Boolean): Boolean {
    val action = event.actionMasked
    // ⚠ DOWN で親の ViewGroup が譲る印を消してから子へ渡すので、ここで付ければ残る。
    if (action == MotionEvent.ACTION_DOWN && (view.canScrollVertically(1) || view.canScrollVertically(-1)))
        view.parent?.requestDisallowInterceptTouchEvent(true)
    return try { dispatch() } finally {
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            view.parent?.requestDisallowInterceptTouchEvent(false)
    }
}
