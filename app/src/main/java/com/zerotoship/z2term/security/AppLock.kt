package com.zerotoship.z2term.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリロック (0.8.421)。画面を出す前に**端末の本人確認**を挟む。
 *
 * ## 何を守り、何を守らないか
 *
 * ⛔ **守れるのは「画面」だけ。** 裏で走っているセッション・常駐サーバー・`z2-session attach`
 * は**ロック中も動き続ける**。止めてしまうと自動化と常駐が壊れ、「ロックを掛けたら
 * 朝までのビルドが死んでいた」になる。**端末を人に渡したときに画面を見られないため**の
 * ものであって、遠隔からの侵入を防ぐものではない。⚠ この線引きを曖昧にしたまま
 * 「セキュリティ機能」と書かない。
 *
 * ## 依存を増やしていない
 *
 * ⭐ `androidx.biometric` は**入れていない**。OS の [BiometricPrompt] (API 28+ / 本アプリの
 * 下限は 29) をそのまま使う。同梱物を増やさない方針と、F-Droid 提出を控えている事情に
 * 沿う。⚠ 代わりに版差 (API 30 で `setAllowedAuthenticators` へ移行) は自分で吸う。
 *
 * ## 画面ロック (PIN/パターン) も通す
 *
 * ⛔ **指紋だけに絞らない。** 指が濡れている・センサーが壊れた・生体を登録していない、の
 * どれでも締め出しになる。`DEVICE_CREDENTIAL` を許すと**「キャンセル」ボタンが不要になる**
 * ので (両方を指定すると [BiometricPrompt.Builder.build] が例外を投げる)、作りも単純になる。
 */
object AppLock {

    /**
     * ⚠ **3 つある。** `UNKNOWN` は「設定をまだ読めていない」。ここで「掛かっていない」に
     * 倒すと、設定が届くまでの数フレーム**端末の中身が見えてしまう** (その一瞬が履歴画面の
     * 縮小画像として残ることもある)。読めるまでは何も出さない。
     */
    enum class State { UNKNOWN, LOCKED, UNLOCKED }

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var enabled = false
    @Volatile private var graceSec = AppSettings.DEFAULT_APP_LOCK_GRACE
    @Volatile private var policyLoaded = false

    /** 画面を離れた時刻 (`elapsedRealtime`)。0 = 離れていない。 */
    @Volatile private var leftAt = 0L

    /**
     * 設定を反映する。画面から設定が流れてくるたびに呼ぶ (初回もここで決まる)。
     *
     * ⚠ **使っている最中に掛け直さない。** ON にした瞬間にロック画面が出ると、設定を
     * いじった本人が閉め出される。ON は「次に離れて戻ってきたとき」から効かせる。
     * OFF はその場で解く (解けないと OFF にした意味がない)。
     */
    fun applyPolicy(enabled: Boolean, graceSec: Int) {
        this.enabled = enabled
        this.graceSec = graceSec
        if (!policyLoaded) {
            policyLoaded = true
            // プロセスが立ち上がった直後。⚠ 猶予に関係なく**起動時は必ず掛ける**。
            _state.value = if (enabled) State.LOCKED else State.UNLOCKED
            return
        }
        if (!enabled) _state.value = State.UNLOCKED
    }

    /**
     * 画面が見えなくなった (`onStop`)。
     *
     * ⚠ **画面回転や言語切替では呼ばない** (呼び元が `isChangingConfigurations` で弾く)。
     * Activity の作り直しも `onStop` を通るので、弾かないと**猶予「すぐ」のとき画面を
     * 回すたびにロックが掛かる**。
     */
    fun onLeaveForeground() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** 画面が見えた (`onStart`)。離れていた時間が猶予を超えていたら掛け直す。 */
    fun onEnterForeground() {
        if (!enabled || _state.value != State.UNLOCKED) return
        if (graceSec == AppSettings.APP_LOCK_GRACE_LAUNCH_ONLY) return
        val since = leftAt
        if (since == 0L) return
        if (SystemClock.elapsedRealtime() - since >= graceSec * 1000L) _state.value = State.LOCKED
    }

    /**
     * ロックを使う設定になっているか。⚠ **掛かっているか**とは別物 — 履歴画面の縮小画像を
     * 隠すかどうかは「使う設定か」で決める (解除して使っている最中に離れた場合も隠したい)。
     */
    fun isEnabledNow(): Boolean = enabled

    /** 本人確認が通った。 */
    fun unlock() {
        leftAt = 0L
        _state.value = State.UNLOCKED
    }

    // --- 本人確認 --------------------------------------------------------------

    /**
     * この端末で本人確認ができるか。⛔ **できない端末では設定を ON にさせない**
     * (ON にできてしまうと、二度と開けないアプリが 1 つ増える)。
     */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            val kinds = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return bm.canAuthenticate(kinds) == BiometricManager.BIOMETRIC_SUCCESS
        }
        // API 29 は認証方式を指定して問い合わせられない。画面ロックが掛かっていれば
        // `setDeviceCredentialAllowed(true)` の口が必ず開くので、それで判断する。
        val km = context.getSystemService(KeyguardManager::class.java)
        return km?.isDeviceSecure == true
    }

    /**
     * 本人確認を求める。[onResult] は主スレッドで**必ず 1 回**呼ぶ
     * (true = 通った / false = 通らなかった)。
     *
     * ⚠ 断られたときに黙って呼び直さない。センサーを叩き続けると端末側が
     * 一定時間ロックアウトし、**PIN でしか開けなくなる**。呼び直しは人が押したときだけ。
     */
    fun authenticate(
        activity: android.app.Activity,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit
    ) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setDescription(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        // ⚠ ここで「キャンセル」ボタン (setNegativeButton) を付けない。画面ロックを
        //   許しているときに付けると build() が例外を投げる。閉じるのは戻るキーで足りる。
        val prompt = runCatching { builder.build() }.getOrElse {
            onResult(false)
            return
        }
        val executor = activity.mainExecutor
        var answered = false
        val answer = { ok: Boolean ->
            if (!answered) {
                answered = true
                onResult(ok)
            }
        }
        runCatching {
            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) = answer(true)

                    override fun onAuthenticationError(code: Int, msg: CharSequence?) =
                        answer(false)

                    // ⚠ 1 回外しただけでは終わらせない (指を置き直せる)。
                    //    終わりを決めるのは成功か [onAuthenticationError]。
                    override fun onAuthenticationFailed() = Unit
                }
            )
        }.onFailure { answer(false) }
    }
}
