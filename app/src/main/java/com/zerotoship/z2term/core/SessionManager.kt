package com.zerotoship.z2term.core

import android.content.Context
import com.zerotoship.z2term.gui.GuiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * プロセス全体で複数の [AppSession] (端末 / GUI) を保持するシングルトン。
 *
 * - `sessions` : 開いているセッションの順序付きリスト (端末タブと GUI タブが混在)
 * - `activeId` : 現在 UI が表示しているセッション ID
 *
 * UI ViewModel もフォアグラウンドサービスもこの object 経由でセッションに
 * アクセスする。サービスが強参照を保持する間、Activity 破棄でも全セッションは
 * 維持される。
 */
object SessionManager {

    private val lock = Any()
    private val mutableSessions = mutableListOf<AppSession>()

    /**
     * 使用中の VNC ディスプレイ番号 (`:N` → RFB ポート 5900+N)。
     *
     * - P1/P2: GUI タブごとに一意な番号を払い出し、別ポートで複数 GUI を独立起動。
     * - P3: 端末タブにも同じ pool から払い出す。`z2run <gui-app>` 経由で同じ :N の GUI タブを
     *   後付けで開けるよう「端末 ↔ GUI のペア」を成立させる。複数セッションが同じ番号を共有する
     *   可能性があるため、close 時は他に同番号を使うセッションが残っていなければ pool に返却する。
     */
    private val usedDisplays = sortedSetOf<Int>()

    /** 空きの最小ディスプレイ番号 (>=1) を払い出す。1 から順に空きを探す。 */
    private fun allocateDisplay(): Int = synchronized(lock) {
        var n = 1
        while (usedDisplays.contains(n)) n++
        usedDisplays.add(n)
        n
    }

    /**
     * 指定ディスプレイ番号を「予約済み」にする (払い出し済みなら何もしない)。
     * 後から `OPEN N` (z2run 経由) で来たときに、既存番号と矛盾なく追跡できるようにするための補助。
     */
    private fun reserveDisplay(n: Int) = synchronized(lock) { usedDisplays.add(n) }

    private val _sessions = MutableStateFlow<List<AppSession>>(emptyList())
    val sessions: StateFlow<List<AppSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    /** 0 件なら新規端末を生成、それ以外は既存のアクティブを返す */
    fun ensureFirst(context: Context): AppSession = synchronized(lock) {
        active() ?: openNew(context)
    }

    /**
     * 新しい端末セッションを開き、アクティブにする。
     *
     * 端末タブにも display 番号を払い出し、proot env (`DISPLAY=:N`/`Z2_DISPLAY=N`) として
     * 注入する (P3)。これにより端末内で `z2run <gui-app>` が走った瞬間、同じ :N の Xvnc が
     * 自動起動し、対応する GUI タブが z2term 側で開く。既存の単独 GUI タブ (🖥 ボタン) と
     * 番号が被らないよう同じ pool から最小空きを払い出す。
     */
    fun openNew(context: Context): TerminalSession = synchronized(lock) {
        val display = allocateDisplay()
        val s = TerminalSession(context.applicationContext, display = display)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /**
     * 「アクティブな端末と紐づく GUI タブ」を開く (P3 = CUI⇄GUI 連動の表ボタン経路)。
     *
     * アクティブが [TerminalSession] のときは、その端末と**同じ display 番号** で GUI を開く
     * (端末:N ↔ GUI:N ペア成立)。同じ番号の GUI タブが既にあれば前面化のみ (二重起動防止)。
     * アクティブが GUI/未存在のときは、端末との紐付けが取れないので空きディスプレイを払い出して
     * **独立 GUI** として開く (従来 [openNewGui] と等価)。
     *
     * これにより 🖥 ボタンの自然な意味が「今いる端末用の GUI を開く」になり、`z2run` を知らない
     * ユーザーでも端末と GUI が連番でペアになる (例: alpine タブが :1 なら 🖥 で `GUI` :1)。
     */
    fun openLinkedGui(context: Context): GuiSession = synchronized(lock) {
        val activeSession = active()
        val display = if (activeSession is TerminalSession) activeSession.display else allocateDisplay()
        // 同じ display の GUI が既に居れば前面化のみ (端末 ↔ GUI 1:1 を保つ)。
        val existing = mutableSessions.firstOrNull { it is GuiSession && it.display == display } as? GuiSession
        if (existing != null) {
            _activeId.value = existing.id
            return@synchronized existing
        }
        reserveDisplay(display)
        val s = GuiSession(context.applicationContext, display = display)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /**
     * 端末と独立した新規 GUI セッションを開く (空きディスプレイ番号を払い出す)。
     * 2 枚目以降は `:2`/5902, `:3`/5903 … と別ポート・別画面で起動する。
     */
    fun openNewGui(context: Context): GuiSession = synchronized(lock) {
        val display = allocateDisplay()
        val s = GuiSession(context.applicationContext, display = display)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /**
     * display=N の GUI セッションを「あれば前面化」「無ければ新規作成」する。
     *
     * `z2run <gui-app>` から `OPEN N` で呼ばれる経路 (P3 = CUI⇄GUI 連動)。
     * 端末タブが先に display=N を払い出して所有しているのが通常で、その上で同じ番号の
     * GUI タブを後付けする (端末 ↔ GUI のペア成立)。display 番号は端末側が既に reserve 済みの
     * ことが多いが、念のため `reserveDisplay` で取りこぼし無しにする。
     */
    fun openGuiForDisplay(context: Context, display: Int): GuiSession = synchronized(lock) {
        // 既に同じ display の GUI タブがあれば、それを前面化して再利用する (二重起動防止)。
        val existing = mutableSessions.firstOrNull { it is GuiSession && it.display == display } as? GuiSession
        if (existing != null) {
            _activeId.value = existing.id
            return@synchronized existing
        }
        reserveDisplay(display)
        val s = GuiSession(context.applicationContext, display = display)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /**
     * 指定セッションを終了 (アクティブが消えたら次を選ぶ)。
     *
     * display 番号は「同じ番号を使う別セッションが残っていなければ」pool に返却する。
     * P3 では端末 ↔ GUI が同じ番号を共有するので、片方だけ閉じた段階では返却しない
     * (もう片方が proot env や Xvnc で番号を使い続けているため)。
     */
    fun close(id: String) = synchronized(lock) {
        val s = mutableSessions.firstOrNull { it.id == id } ?: return@synchronized
        val displayBeforeRemove = s.display
        s.shutdown()  // AppSession.shutdown (端末=PTY 停止 / GUI=Xvnc 停止)
        mutableSessions.remove(s)
        // 残っているセッションが同じ display を使っていなければ pool に返却。
        if (mutableSessions.none { it.display == displayBeforeRemove }) {
            usedDisplays.remove(displayBeforeRemove)
        }
        _sessions.value = mutableSessions.toList()
        if (_activeId.value == id) {
            _activeId.value = mutableSessions.firstOrNull()?.id
        }
    }

    /** アクティブを切り替える */
    fun setActive(id: String) = synchronized(lock) {
        if (mutableSessions.any { it.id == id }) {
            _activeId.value = id
        }
    }

    /** 全セッション終了 (サービス停止時に呼ばれる) */
    fun shutdown() = synchronized(lock) {
        mutableSessions.forEach { it.shutdown() }
        mutableSessions.clear()
        usedDisplays.clear()
        _sessions.value = emptyList()
        _activeId.value = null
    }

    fun active(): AppSession? = synchronized(lock) {
        val id = _activeId.value ?: return@synchronized null
        mutableSessions.firstOrNull { it.id == id }
    }
}
