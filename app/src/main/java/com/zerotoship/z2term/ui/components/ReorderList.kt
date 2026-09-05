package com.zerotoship.z2term.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 縦に並んだカードの**ドラッグ並べ替え**（0.8.249）。
 *
 * スニペットタブ（`SnippetsSheet`）の操作感を、**行の高さが可変な一覧でも**使えるようにしたもの。
 * スニペットは行が固定高なので「1 行ぶんのピッチ」を定数で持てたが、常駐サーバー / 自動化の行は
 * 状態表示やログの開閉で高さが変わる。そこで各行が自分の高さを [ReorderState.reportSize] で
 * 報告し、入れ替えの判定には**隣の行の実測高さ**を使う。
 *
 * 使い方（呼び出し側は id のリストだけを扱う）:
 * ```
 * val reorder = rememberReorderState(spacing = 10.dp) { ids -> 並びを保存する(ids) }
 * reorder.sync(entries.map { it.id })
 * val byId = entries.associateBy { it.id }
 * reorder.order.mapNotNull { byId[it] }.forEach { e ->
 *     key(e.id) { Row(modifier = Modifier.reorderItem(reorder, e.id)) { …; ReorderHandle(reorder, e.id) } }
 * }
 * ```
 * `key(id)` を付けること — ノードの identity が固定されないと、並べ替え中に掴んだ行から
 * ポインタが外れる。
 *
 * **横並びにも使える**（0.8.509）: `rememberReorderState(spacing, vertical = false)` にすると
 * 幅と `translationX` で同じことをする。掴み方は、ハンドルを置ける一覧なら [reorderHandle]、
 * ツールバーのように置けないものは [reorderLongPressHandle]（長押ししてから動かす）。
 */
@Stable
class ReorderState internal constructor(
    private val spacingPx: Float,
    /**
     * 並ぶ向き。true = 縦に積む一覧 (既定)、false = 横に並ぶ帯 (ツールバー)。
     *
     * 入れ替えの判定は「隣の中心を跨いだか」だけなので、**見る軸が変わるだけで式は同じ**。
     * 縦なら高さと `translationY`、横なら幅と `translationX` を使う。
     */
    val isVertical: Boolean,
    private val scope: CoroutineScope,
    /**
     * 並びが入っているスクロール領域。渡すと、掴んだまま端へ寄せている間**自動でスクロール**する。
     * 渡さなければ従来どおり（画面に見えている範囲でだけ動かせる）。
     */
    private val scrollState: ScrollState?,
    private val onCommit: (List<String>) -> Unit,
) {

    /** いま表示している並び（ドラッグ中はこちらが真実で、外の一覧では上書きしない）。 */
    var order by mutableStateOf<List<String>>(emptyList())
        private set

    /** 掴んでいる行の id。掴んでいなければ null。 */
    var draggingId by mutableStateOf<String?>(null)
        private set

    /**
     * 指を離したあと、残っているズレを 0 へ戻している最中の行（0.8.272）。
     *
     * 掴んでいる間の [offset] は「隣を跨ぐたびに 1 行ぶん引く」ので、離した時点では
     * ふつう 0 ではなく**半端に残っている**。そのまま 0 を代入すると、その残りぶんだけ
     * 行が 1 フレームで飛び、「指を離した瞬間にパッと入れ替わった」ように見える。
     * 離したあとも [draggingId] と同じ扱いを続けて、0 まで滑らせるための印。
     */
    var settlingId by mutableStateOf<String?>(null)
        private set

    /** 掴んでいる項目の指追従オフセット（px・主軸ぶん）。 */
    var offset by mutableStateOf(0f)
        private set

    /** 項目の実測サイズ（px・主軸ぶん）。入れ替えのピッチに使うだけなので State にはしない。 */
    private val sizes = HashMap<String, Float>()

    /**
     * 項目の主軸方向の位置（**スクロール内容の座標**・px）。自動スクロールの判定だけに使う。
     * `graphicsLayer` の移動は含まない配置上の位置なので、掴んでいる項目の見かけの位置は
     * これに [offset] を足したもの。
     */
    private val positions = HashMap<String, Float>()

    /** 着地アニメーション。次のドラッグが始まったら止める。 */
    private var settleJob: Job? = null

    /** 端に寄せている間のスクロール。掴んでいる間だけ回る。 */
    private var autoScrollJob: Job? = null

    /**
     * [onCommit] で渡した並び。外の一覧がこれに追いつくまで [sync] を無視するために持つ。
     *
     * 保存は呼び出し側で非同期に走る（ファイルへ書く → 読み直す）ので、指を離した直後の
     * 数フレームは**外の一覧がまだ古い並び**を返す。そのまま取り込むと、並べ替えたものが
     * 一度元へ戻ってからまた入れ替わる — これも「勝手に入れ替わった」ように見える。
     */
    private var pending: List<String>? = null

    /**
     * 外の一覧が変わったら取り込む。**ドラッグ中と、保存が反映されるまでは無視する** —
     * 掴んでいる最中に元の並びで上書きされると指の下から行が飛び、離した直後に上書き
     * されると並びが往復して見えるため。
     */
    fun sync(ids: List<String>) {
        if (draggingId != null) return
        val p = pending
        if (p != null) {
            // 中身が変わった（追加・削除された）なら、こちらの都合より外の一覧が正しい。
            if (ids.size != p.size || ids.toSet() != p.toSet()) pending = null
            // 並びが追いついた。以後はふつうに取り込む。
            else if (ids == p) pending = null
            // 同じ顔ぶれで並びだけ違う = まだ保存が反映されていない。自分の並びを保つ。
            else return
        }
        if (order != ids) order = ids
    }

    fun reportSize(id: String, sizePx: Float) {
        sizes[id] = sizePx
    }

    fun reportPosition(id: String, posPx: Float) {
        positions[id] = posPx
    }

    fun start(id: String) {
        // 前の行がまだ着地中なら止めて、そこから掴み直す。
        settleJob?.cancel()
        settleJob = null
        settlingId = null
        draggingId = id
        offset = 0f
        startAutoScroll()
    }

    /** 指の移動ぶん [amount] のうち**主軸ぶん**を足し込み、跨いだぶんだけ入れ替える。 */
    fun drag(amount: Offset) {
        val id = draggingId ?: return
        offset += if (isVertical) amount.y else amount.x
        swapWhilePossible(id)
    }

    /**
     * [offset] が隣を跨いでいる**間ずっと**入れ替える（0.8.510）。
     *
     * ⭐ **1 回のイベントで 1 つしか動かさないこと。** ドラッグのイベントは指の動きより粗い
     * 間隔で届くので、少し勢いよく動かすと 1 回に 2 個ぶん以上の移動が乗る。1 つで打ち切ると
     * **指だけ先へ行って順番が追いつかず**、「一気に運べない・1 個ずつしか動かない」と見える
     * （ボタンが 10 個並ぶ設定画面のツールバーで顕著だった）。
     *
     * ⚠ 実測サイズが 0 の項目 (`pitch <= spacingPx`) では回さない。無限に近い回数まわる。
     */
    private fun swapWhilePossible(id: String) {
        while (true) {
            val cur = order.indexOf(id)
            if (cur < 0) return
            if (offset > 0f && cur < order.lastIndex) {
                val pitch = (sizes[order[cur + 1]] ?: return) + spacingPx
                if (pitch > spacingPx && offset > pitch) {
                    order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                    offset -= pitch
                    continue
                }
            } else if (offset < 0f && cur > 0) {
                val pitch = (sizes[order[cur - 1]] ?: return) + spacingPx
                if (pitch > spacingPx && offset < -pitch) {
                    order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                    offset += pitch
                    continue
                }
            }
            return
        }
    }

    /**
     * 掴んだ項目が可視範囲の端に居る間、そちらへスクロールし続ける（0.8.510）。
     *
     * 指は画面の中でしか動かせないので、これが無いと**画面に見えている範囲より先へは運べない**
     * （設定画面のツールバーは 10 個並び、一度に見えるのは 6 個ほど）。
     *
     * ⚠ **スクロールしたぶんは [offset] にも足す。** 指は止まっているのに内容だけが流れるため、
     * 足さないと掴んだ項目が指から置き去りになる。足せば「指の下に留まったまま、下を流れていく
     * 列との相対位置が変わる」= そのぶん順番が進む、という自然な動きになる。
     */
    private fun startAutoScroll() {
        val ss = scrollState ?: return
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (true) {
                val id = draggingId ?: break
                val pos = positions[id]
                val size = sizes[id]
                if (pos != null && size != null && size > 0f && ss.viewportSize > 0) {
                    // 掴んでいる項目の見かけの中心 (スクロール内容の座標)。
                    val center = pos + offset + size / 2f
                    val head = ss.value.toFloat()
                    val tail = head + ss.viewportSize
                    // 端から 1 項目ぶんに入ったら、そちらへ流す。1 フレームで 1/5 項目ぶん。
                    val delta = when {
                        center < head + size && ss.value > 0 -> -size / 5f
                        center > tail - size && ss.value < ss.maxValue -> size / 5f
                        else -> 0f
                    }
                    if (delta != 0f) {
                        val moved = ss.scrollBy(delta)
                        if (moved != 0f) {
                            offset += moved
                            swapWhilePossible(id)
                        }
                    }
                }
                delay(16)
            }
            autoScrollJob = null
        }
    }

    /**
     * 指を離した。今の並びを 1 回だけ保存し、**残っているズレは 0 まで滑らせて**畳む。
     *
     * 保存はここで即座に呼ぶ（着地の終わりまで待たない）— 待つと、離してすぐ画面を閉じた
     * ときに並びが消える。見た目の着地と、並びの保存は別々に進めてよい。
     */
    fun end() {
        val finalOrder = order
        val id = draggingId
        draggingId = null
        autoScrollJob?.cancel()
        autoScrollJob = null
        pending = finalOrder
        onCommit(finalOrder)
        if (id == null || offset == 0f) {
            offset = 0f
            return
        }
        settlingId = id
        settleJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = tween(durationMillis = REORDER_SETTLE_MS)
            ) { value, _ -> offset = value }
            offset = 0f
            settlingId = null
            settleJob = null
        }
    }
}

/**
 * 指を離したあとの着地にかける時間 (ms)。
 *
 * 行 1 つぶんの半端なズレを埋めるだけなので短くてよい — 長いと「離したのにまだ動いている」
 * ともたつきに見える。スニペットタブは並べ替えを独自に持っているので、そちらからも同じ値を使う
 * (操作感がタブごとに違うのは避ける)。
 */
internal const val REORDER_SETTLE_MS = 140

/**
 * [ReorderState] を作る。[spacing] は並びの間隔（`Arrangement.spacedBy` に渡している値）。
 * [vertical] = false で横並び（ツールバー）になる。
 * [onCommit] は指を離したときに新しい並び（id の順）で 1 回だけ呼ばれる。
 */
@Composable
fun rememberReorderState(
    spacing: Dp,
    vertical: Boolean = true,
    /** 並びを入れているスクロール領域。渡すと掴んだまま端へ寄せたときに自動スクロールする。 */
    scrollState: ScrollState? = null,
    onCommit: (List<String>) -> Unit,
): ReorderState {
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    // 最新のラムダを呼ぶ (state を作り直さずに、呼び出し側が持つ最新の一覧を掴めるように)。
    val commit = rememberUpdatedState(onCommit)
    // 着地アニメーション・自動スクロール用。画面から消えれば一緒に止まる。
    val scope = rememberCoroutineScope()
    return remember(spacingPx, vertical, scrollState) {
        ReorderState(spacingPx, vertical, scope, scrollState) { commit.value(it) }
    }
}

/**
 * 並べ替え対象の 1 行に付ける。高さの計測・指追従・掴んだ行を前面に出すところまで。
 *
 * 指を離したあとも**着地し終わるまで**は掴んでいるときと同じ扱いにする
 * ([ReorderState.settlingId])。ここで先に元へ戻すと、残っていたズレのぶんだけ行が飛ぶ。
 */
fun Modifier.reorderItem(state: ReorderState, id: String): Modifier {
    val active = state.draggingId == id || state.settlingId == id
    return this
        // 実測は**主軸ぶんだけ**覚える (縦なら高さ・横なら幅)。入れ替えの判定に使うのはそれだけ。
        .onSizeChanged { state.reportSize(id, (if (state.isVertical) it.height else it.width).toFloat()) }
        // 自動スクロールの判定用に配置位置も控える (スクロール内容の座標)。
        .onPlaced { c ->
            val p = c.positionInParent()
            state.reportPosition(id, if (state.isVertical) p.y else p.x)
        }
        .zIndex(if (active) 1f else 0f)
        .graphicsLayer {
            val off = if (active) state.offset else 0f
            if (state.isVertical) translationY = off else translationX = off
        }
}

/** 掴む場所（ハンドル）に付ける。行全体をドラッグにすると、タップ操作と喧嘩する。 */
fun Modifier.reorderHandle(state: ReorderState, id: String): Modifier = this.pointerInput(id) {
    detectDragGestures(
        onDragStart = { state.start(id) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount)
        }
    )
}

/**
 * ハンドルを置く余地が無いもの（ツールバーのボタン等）を**長押しから**掴ませる。
 *
 * 単タップはそのボタンの動作、長押ししてから動かすと並べ替え。ハンドル版
 * ([reorderHandle]) と違い項目そのものに付けるので、`clickable` を持つ中身と同居できる。
 */
fun Modifier.reorderLongPressHandle(state: ReorderState, id: String): Modifier =
    this.pointerInput(id, state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(id) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount)
            }
        )
    }

/** ドラッグハンドル `≡`（見た目もスニペットタブと揃える）。 */
@Composable
fun ReorderHandle(state: ReorderState, id: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .reorderHandle(state, id)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "≡",
            color = if (state.draggingId == id) ZtsGreen else ZtsTextSecondary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
