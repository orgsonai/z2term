package com.zerotoship.z2term.qr

/** Explicit QR share target; inherits app lock and the same review screen as the in-app tool. */
internal class QrReceiveActivity : QrToolsActivity() {
    override val receivesExternalContent = true
}
