package com.zerotoship.z2term.snippets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * よく使うコマンド (スニペット) 1 件。
 *
 * - `label` が空なら command 先頭をリストに表示。
 * - 実行時は常に「挿入のみ」(末尾 Enter は付けない)。ユーザーが Enter キーで確定する。
 *   仮に shell の補完や中間引数追記をしたいケースに対応するため。
 */
data class Snippet(
    val id: String,
    val label: String,
    val command: String,
    /**
     * 入っているグループ ([SnippetGroup.id])。**空 = どのグループにも入っていない** (0.8.387)。
     *
     * ⚠ **名前ではなく id で持つ**。名前で持つと、グループ名を直すたびに中身を全部書き換える
     * ことになり、書き換えの途中で落ちると**どこにも出てこないスニペット**が残る。
     */
    val groupId: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("command", command)
        put("groupId", groupId)
    }

    companion object {
        fun fromJson(o: JSONObject): Snippet = Snippet(
            id = o.optString("id"),
            label = o.optString("label"),
            command = o.optString("command"),
            // 0.8.387 より前に書き出したものには無い = 未分類 (「すべて」には出る)。
            groupId = o.optString("groupId")
        )
    }
}

/**
 * スニペットのグループ (0.8.387)。「日常」「git」のように**まとめて畳んでおく棚**。
 *
 * **なぜ要るか**: スニペットは増えるほど下へ伸び、**よく使うものほど下に埋まる**
 * (利用者の指摘: 「量が増えると下の方に行ってしまい選択するのが難しくなってくる」)。
 * ページのように機械的に区切るのではなく、**自分で決めた棚**に置けることが要点。
 *
 * ⚠ **グループを消してもスニペットは消さない**。中身は未分類 ([Snippet.groupId] が空) へ
 * 戻して「すべて」に出す — 棚を片付けたつもりで中身ごと失うのが一番困る。
 */
data class SnippetGroup(val id: String, val name: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
    }

    companion object {
        fun fromJson(o: JSONObject): SnippetGroup = SnippetGroup(
            id = o.optString("id"),
            name = o.optString("name")
        )
    }
}

private val Context.snippetDataStore: DataStore<Preferences> by preferencesDataStore(name = "z2term_snippets")

/**
 * スニペット永続化。SshProfileStore と同じパターン (JSONArray を 1 つの String キーに保存)。
 *
 * 初回のみサンプル 1 件 (`ls -la --color=auto`) をシードする ([ensureSeeded])。
 * 以降はユーザーが「+ 新規」で積み上げ。ユーザーが全削除してもシードは復活しない。
 */
class SnippetStore(private val context: Context) {

    val snippets: Flow<List<Snippet>> = context.snippetDataStore.data.map { p ->
        readList(p[KEY])
    }

    /** グループ一覧 (帯に出す順)。空 = まだ 1 つも作っていない (0.8.387)。 */
    val groups: Flow<List<SnippetGroup>> = context.snippetDataStore.data.map { p ->
        readGroups(p[KEY_GROUPS])
    }

    /**
     * 初回だけサンプルスニペットを投入する (冪等)。`SEEDED` フラグで二度目以降は何もしない
     * → ユーザーが消したサンプルが再出現しない。既存スニペットがあれば上書きもしない。
     *
     * 既に `SEEDED=true` で過去にシード済みのユーザーには、別フラグ [SEEDED_APK] で
     * Alpine の apk サンプルだけ後追い投入する (既存スニペットに「追記」のみ、上書きしない)。
     *
     * ⚠ **ここに「入れてから使うコマンド」を置かないこと** (0.8.314)。スニペットは押せば
     * そのまま走る場所なので、前提のあるコマンドを置くとエラーが出るだけになる。手順が要る
     * ものは案内 ([com.zerotoship.z2term.ui.terminal.Guide]) の仕事。
     */
    suspend fun ensureSeeded() {
        context.snippetDataStore.edit { p ->
            val firstTime = p[SEEDED] != true
            if (firstTime) {
                p[SEEDED] = true
                if (p[KEY] == null) {
                    p[KEY] = serialize(defaultSeeds())
                }
            } else if (p[SEEDED_APK] != true) {
                // 過去にシード済みのユーザーへ apk サンプルを追記投入 (1 回だけ)。
                val list = readList(p[KEY]).toMutableList()
                for (s in apkSeeds()) {
                    if (list.none { it.id == s.id }) list.add(s)
                }
                p[KEY] = serialize(list)
            }
            p[SEEDED_APK] = true
            // 既にシード済みの人へ z2-update を追記投入 (1 回だけ・既存は上書きしない)。
            if (p[SEEDED_UPDATE] != true) {
                val list = readList(p[KEY]).toMutableList()
                for (s in updateSeeds()) {
                    if (list.none { it.id == s.id }) list.add(s)
                }
                p[KEY] = serialize(list)
                p[SEEDED_UPDATE] = true
            }
            // ⚠ **`remind.sh help` のシードは撤去した** (0.8.314・利用者の指摘)。0.8.286 で
            // 「書き方の一覧をすぐ開ける場所」として入れたが、**マクロを入れていない人が押すと
            // 「見つからない」と出るだけ**で、そこから入れ方に辿り着けなかった。手順は
            // ⚙設定 → メンテナンス → 案内を表示 (com.zerotoship.z2term.ui.terminal.Guide) に移した。
            //
            // 既にシード済みの人からは、**手を入れていない場合に限り**取り除く。編集した人の
            // スニペットは触らない (自分で書き換えたものが黙って消えるのが一番困る)。
            if (p[SEEDED_REMIND_REMOVED] != true) {
                val list = readList(p[KEY])
                val pruned = list.filterNot { it.id == LEGACY_REMIND_ID && it.command == LEGACY_REMIND_CMD }
                if (pruned.size != list.size) p[KEY] = serialize(pruned)
                p[SEEDED_REMIND_REMOVED] = true
            }
        }
    }

    /** 初回シードに投入するサンプル一覧 (ラベルは投入時点のアプリ言語に追従)。 */
    private fun defaultSeeds(): List<Snippet> = buildList {
        add(Snippet(
            id = "sample:ls",
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_ls_label),
            command = "ls -la --color=auto"
        ))
        addAll(apkSeeds())
        addAll(updateSeeds())
    }

    /** Alpine 用 apk サンプル。pacman/apt は対象外 (Alpine 専用)。 */
    private fun apkSeeds(): List<Snippet> = listOf(
        Snippet(
            id = "sample:apk-update",
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_apk_update_label),
            command = "apk update",
        ),
        Snippet(
            id = "sample:apk-add",
            // command 末尾を半角スペースで止めてユーザーがパッケージ名を追記できる形に。
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_apk_add_label),
            command = "apk add ",
        ),
    )

    /**
     * z2term 自身の更新 (0.8.371)。⚠ **同梱コマンドなので前提が要らない** — 押せばそのまま動く、
     * というこの場所の約束を満たす (入れてから使うものは置かない)。
     */
    private fun updateSeeds(): List<Snippet> = listOf(
        Snippet(
            id = "sample:z2-update",
            label = context.getString(com.zerotoship.z2term.R.string.snippet_sample_update_label),
            command = "z2-update",
        ),
    )

    suspend fun upsert(snippet: Snippet) {
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            val idx = list.indexOfFirst { it.id == snippet.id }
            if (idx >= 0) list[idx] = snippet else list.add(snippet)
            p[KEY] = serialize(list)
        }
    }

    suspend fun delete(id: String) {
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).filterNot { it.id == id }
            p[KEY] = serialize(list)
        }
    }

    /** リスト全体を指定順で保存する (ドラッグ並べ替えの確定用)。 */
    suspend fun replaceAll(list: List<Snippet>) {
        context.snippetDataStore.edit { p -> p[KEY] = serialize(list) }
    }

    /**
     * 見えている並び [visible] だけを保存する (グループで絞っているときの並べ替え用・0.8.387)。
     *
     * ⚠ **絞り込んだ並びをそのまま [replaceAll] に渡してはいけない** — 出ていないグループの
     * スニペットが全部消える。[reorderWithin] が「見えている行が占めている位置」にだけ
     * 新しい並びを流し込む。
     */
    suspend fun replaceVisible(visible: List<Snippet>) {
        context.snippetDataStore.edit { p ->
            p[KEY] = serialize(reorderWithin(readList(p[KEY]), visible))
        }
    }

    /** グループを作る / 名前を直す。 */
    suspend fun upsertGroup(group: SnippetGroup) {
        context.snippetDataStore.edit { p ->
            val list = readGroups(p[KEY_GROUPS]).toMutableList()
            val idx = list.indexOfFirst { it.id == group.id }
            if (idx >= 0) list[idx] = group else list.add(group)
            p[KEY_GROUPS] = serializeGroups(list)
        }
    }

    /**
     * グループを消す。⚠ **中のスニペットは消さず、未分類へ戻す** — 棚を片付けたつもりで
     * 中身ごと失うのが一番困る。
     */
    suspend fun deleteGroup(id: String) {
        context.snippetDataStore.edit { p ->
            p[KEY_GROUPS] = serializeGroups(readGroups(p[KEY_GROUPS]).filterNot { it.id == id })
            val moved = readList(p[KEY]).map { if (it.groupId == id) it.copy(groupId = "") else it }
            p[KEY] = serialize(moved)
        }
    }

    /** グループをまるごと JSON にする (持ち出し用・0.8.387)。 */
    suspend fun exportGroups(): String = serializeGroups(groups.first())

    /** 持ち出したグループを取り込む (同じ id は置き換え・無いものは追加)。 */
    suspend fun importGroups(json: String) {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        context.snippetDataStore.edit { p ->
            val list = readGroups(p[KEY_GROUPS]).toMutableList()
            for (i in 0 until arr.length()) {
                val g = runCatching { SnippetGroup.fromJson(arr.getJSONObject(i)) }.getOrNull() ?: continue
                val idx = list.indexOfFirst { it.id == g.id }
                if (idx >= 0) list[idx] = g else list.add(g)
            }
            p[KEY_GROUPS] = serializeGroups(list)
        }
    }

    /** スニペットをまるごと JSON にする (持ち出し用・0.8.239)。秘密は含まれない。 */
    suspend fun exportRaw(): String = serialize(snippets.first())

    /** 持ち出したスニペットを取り込む (同じ id は置き換え・無いものは追加)。 */
    suspend fun importRaw(json: String) {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        context.snippetDataStore.edit { p ->
            val list = readList(p[KEY]).toMutableList()
            for (i in 0 until arr.length()) {
                val s = runCatching { Snippet.fromJson(arr.getJSONObject(i)) }.getOrNull() ?: continue
                val idx = list.indexOfFirst { it.id == s.id }
                if (idx >= 0) list[idx] = s else list.add(s)
            }
            p[KEY] = serialize(list)
        }
    }

    private fun readGroups(raw: String?): List<SnippetGroup> {
        if (raw == null) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return List(arr.length()) { SnippetGroup.fromJson(arr.getJSONObject(it)) }
    }

    private fun serializeGroups(list: List<SnippetGroup>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun readList(raw: String?): List<Snippet> {
        if (raw == null) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return List(arr.length()) { Snippet.fromJson(arr.getJSONObject(it)) }
    }

    private fun serialize(list: List<Snippet>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    companion object {
        private val KEY = stringPreferencesKey("snippets")
        private val KEY_GROUPS = stringPreferencesKey("snippet_groups")

        /**
         * [all] のうち [visible] に含まれる行の位置だけを、[visible] の並びで置き換える
         * (**純関数**・[SnippetGroupTest] が固定する)。
         *
         * 例: 全体が `[a1, b1, a2]`、グループ a を `[a2, a1]` に並べ替えたなら `[a2, b1, a1]`。
         * b1 は動かない。**絞って並べ替えても、出ていないものの前後関係は変わらない**。
         */
        fun reorderWithin(all: List<Snippet>, visible: List<Snippet>): List<Snippet> {
            val ids = visible.map { it.id }.toSet()
            // 見えている行のうち、全体にまだ在るものだけを順に流し込む (消えた行は飛ばす)。
            val queue = ArrayDeque(visible.filter { v -> all.any { it.id == v.id } })
            return all.map { s -> if (s.id in ids && queue.isNotEmpty()) queue.removeFirst() else s }
        }
    
        private val SEEDED = booleanPreferencesKey("seeded")
        private val SEEDED_APK = booleanPreferencesKey("seeded_apk")
        private val SEEDED_UPDATE = booleanPreferencesKey("seeded_z2_update")
        /**
         * `remind.sh help` を取り除いたか (取り除きは 1 回だけ = 自分で足し直した人には戻らない)。
         * ⚠ 投入側のフラグ `seeded_remind` (0.8.286〜0.8.313) は DataStore に残るが、参照は無い。
         */
        private val SEEDED_REMIND_REMOVED = booleanPreferencesKey("seeded_remind_removed")
        private const val LEGACY_REMIND_ID = "sample:remind-help"
        private const val LEGACY_REMIND_CMD = "remind.sh help"
    }
}
