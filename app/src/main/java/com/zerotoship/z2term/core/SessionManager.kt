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

    private val _sessions = MutableStateFlow<List<AppSession>>(emptyList())
    val sessions: StateFlow<List<AppSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    /** 0 件なら新規端末を生成、それ以外は既存のアクティブを返す */
    fun ensureFirst(context: Context): AppSession = synchronized(lock) {
        active() ?: openNew(context)
    }

    /** 新しい端末セッションを開き、アクティブにする */
    fun openNew(context: Context): TerminalSession = synchronized(lock) {
        val s = TerminalSession(context.applicationContext)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /** 新しい GUI セッション (Xvnc + RFB) を開き、アクティブにする */
    fun openNewGui(context: Context): GuiSession = synchronized(lock) {
        val s = GuiSession(context.applicationContext)
        mutableSessions.add(s)
        _sessions.value = mutableSessions.toList()
        _activeId.value = s.id
        s
    }

    /** 指定セッションを終了 (アクティブが消えたら次を選ぶ) */
    fun close(id: String) = synchronized(lock) {
        val s = mutableSessions.firstOrNull { it.id == id } ?: return@synchronized
        s.shutdown()  // AppSession.shutdown (端末=PTY 停止 / GUI=Xvnc 停止)
        mutableSessions.remove(s)
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
        _sessions.value = emptyList()
        _activeId.value = null
    }

    fun active(): AppSession? = synchronized(lock) {
        val id = _activeId.value ?: return@synchronized null
        mutableSessions.firstOrNull { it.id == id }
    }
}
