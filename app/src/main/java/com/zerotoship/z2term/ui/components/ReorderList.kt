package com.zerotoship.z2term.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch

/**
 * 縦に並んだカードの**ドラッグ並べ替え**（0.8.249）。
 *
 * スニペットタブ（`SnippetsSheet`）の操作感を、**行の高さが可変な一覧でも**使えるようにしたもの。
 * スニペットは行が固定高なので「1 行ぶんのピッチ」を定数で持てたが、常駐サーバー / 自動化の行は
 * 状態表示やログの開閉で高さが変わる。そこで各行が自分の高さを [ReorderState.reportHeight] で
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
 */
@Stable
class ReorderState internal constructor(
    private val spacingPx: Float,
    private val scope: CoroutineScope,
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
     * 掴んでいる間の [offsetY] は「隣を跨ぐたびに 1 行ぶん引く」ので、離した時点では
     * ふつう 0 ではなく**半端に残っている**。そのまま 0 を代入すると、その残りぶんだけ
     * 行が 1 フレームで飛び、「指を離した瞬間にパッと入れ替わった」ように見える。
     * 離したあとも [draggingId] と同じ扱いを続けて、0 まで滑らせるための印。
     */
    var settlingId by mutableStateOf<String?>(null)
        private set

    /** 掴んでいる行の指追従オフセット（px）。 */
    var offsetY by mutableStateOf(0f)
        private set

    /** 行の実測高さ（px）。入れ替えのピッチに使うだけなので State にはしない。 */
    private val heights = HashMap<String, Float>()

    /** 着地アニメーション。次のドラッグが始まったら止める。 */
    private var settleJob: Job? = null

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

    fun reportHeight(id: String, heightPx: Float) {
        heights[id] = heightPx
    }

    fun start(id: String) {
        // 前の行がまだ着地中なら止めて、そこから掴み直す。
        settleJob?.cancel()
        settleJob = null
        settlingId = null
        draggingId = id
        offsetY = 0f
    }

    /** 指の移動ぶん [dy] を足し込み、隣の行を跨いだら入れ替える。 */
    fun drag(dy: Float) {
        val id = draggingId ?: return
        offsetY += dy
        val cur = order.indexOf(id)
        if (cur < 0) return
        if (offsetY > 0f && cur < order.lastIndex) {
            val pitch = (heights[order[cur + 1]] ?: return) + spacingPx
            if (offsetY > pitch) {
                order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                offsetY -= pitch
            }
        } else if (offsetY < 0f && cur > 0) {
            val pitch = (heights[order[cur - 1]] ?: return) + spacingPx
            if (offsetY < -pitch) {
                order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                offsetY += pitch
            }
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
        pending = finalOrder
        onCommit(finalOrder)
        if (id == null || offsetY == 0f) {
            offsetY = 0f
            return
        }
        settlingId = id
        settleJob = scope.launch {
            animate(
                initialValue = offsetY,
                targetValue = 0f,
                animationSpec = tween(durationMillis = REORDER_SETTLE_MS)
            ) { value, _ -> offsetY = value }
            offsetY = 0f
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
 * [ReorderState] を作る。[spacing] は一覧の行間（`Arrangement.spacedBy` に渡している値）。
 * [onCommit] は指を離したときに新しい並び（id の順）で 1 回だけ呼ばれる。
 */
@Composable
fun rememberReorderState(spacing: Dp, onCommit: (List<String>) -> Unit): ReorderState {
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    // 最新のラムダを呼ぶ (state を作り直さずに、呼び出し側が持つ最新の一覧を掴めるように)。
    val commit = rememberUpdatedState(onCommit)
    // 着地アニメーション用。画面から消えれば一緒に止まる。
    val scope = rememberCoroutineScope()
    return remember(spacingPx) { ReorderState(spacingPx, scope) { commit.value(it) } }
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
        .onSizeChanged { state.reportHeight(id, it.height.toFloat()) }
        .zIndex(if (active) 1f else 0f)
        .graphicsLayer { translationY = if (active) state.offsetY else 0f }
}

/** 掴む場所（ハンドル）に付ける。行全体をドラッグにすると、タップ操作と喧嘩する。 */
fun Modifier.reorderHandle(state: ReorderState, id: String): Modifier = this.pointerInput(id) {
    detectDragGestures(
        onDragStart = { state.start(id) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
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
