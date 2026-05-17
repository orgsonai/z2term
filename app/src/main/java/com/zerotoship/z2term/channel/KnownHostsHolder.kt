package com.zerotoship.z2term.channel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * アプリプロセスごとに 1 つの known_hosts ストア + JSch HostKeyRepository を保持する
 * シングルトン。SshChannel.connect から参照される。
 */
object KnownHostsHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _store: KnownHostsStore? = null
    @Volatile
    private var _repo: DataStoreHostKeyRepository? = null

    fun store(context: Context): KnownHostsStore {
        _store?.let { return it }
        synchronized(this) {
            _store?.let { return it }
            val s = KnownHostsStore(context.applicationContext)
            _store = s
            return s
        }
    }

    fun repository(context: Context): DataStoreHostKeyRepository {
        _repo?.let { return it }
        synchronized(this) {
            _repo?.let { return it }
            val r = DataStoreHostKeyRepository(store(context), scope)
            _repo = r
            return r
        }
    }
}
