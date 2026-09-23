package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.view.Gravity

/**
 * Overlay windows and the views placed by translation inside them use absolute screen pixels.
 * Their origin is the left edge whatever the reading direction, so START (which flips in
 * right-to-left locales) would move every window and toolbar to the wrong side.
 */
internal object ScreenGravity {
    @SuppressLint("RtlHardcoded") const val LEFT = Gravity.LEFT
    const val TOP_LEFT = Gravity.TOP or LEFT
}
