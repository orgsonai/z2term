package com.zerotoship.z2term.proot

/**
 * `z2-ui` — AI からのアプリ操作 (設計は docs/ja/AI-BRIDGE.md)。判定はすべてアプリ側
 * ([com.zerotoship.z2term.automation.UiBridge]) で行い、ここは引数を渡して JSON を出すだけ。
 *
 * ⚠ **失敗も標準出力へ JSON で出す。** z2api は失敗の本文を stderr へ出すので、AI が標準出力
 * だけを読む作りでも理由が届くよう 2>&1 でまとめる。アプリが答えないとき (JSON でないとき) は
 * 決まった形の JSON に置き換える。終了コードは成功 0・失敗 非0 のまま。
 *
 * 待ち時間は承認待ち (60 秒) と wait (最大 30 秒) を収める 100 秒。
 *
 * `--help` (引数なしも) は AI 用ガイドを出す ([com.zerotoship.z2term.automation.UiBridgeGuide])。
 * [lang] は他の z2-* と呼び方を揃えるために受け取るだけで使わない。
 */
@Suppress("UNUSED_PARAMETER")
fun z2UiScript(lang: String): String {
    val d = '$'
    // 先頭コメント = AI 用ガイド (UiBridgeGuide)。z2-ui は AI のための命令なので、--help はガイドを出す。
    // 言語で切り替えない (英語 1 本。AI がいちばん取り違えにくい)。空行は `#` だけの行にして、
    // ヘルプを読み上げる awk が途中で止まらないようにする。
    val header = com.zerotoship.z2term.automation.UiBridgeGuide.TEXT.lines()
        .joinToString("\n") { if (it.isEmpty()) "#" else "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |case "${d}{1:-}" in
        |  ''|-h|--help|help)
        |    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0 ;;
        |  mcp)
        |    # 1 行の JSON-RPC をそのままアプリへ渡し、返事 (あれば 1 行) を出す。アプリが答えないときは
        |    # id を拾って JSON-RPC のエラーにする (待っている側を宙吊りにしない)。
        |    while IFS= read -r line || [ -n "${d}line" ]; do
        |      [ -n "${d}line" ] || continue
        |      if out=${d}(Z2API_WAIT=1000 z2api 1 ui mcp "${d}line" 2>/dev/null); then
        |        [ -z "${d}out" ] || printf '%s
' "${d}out"
        |      else
        |        id=${d}(printf '%s' "${d}line" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\("[^"]*"\).*/\1/p')
        |        [ -n "${d}id" ] || id=${d}(printf '%s' "${d}line" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')
        |        [ -z "${d}id" ] || printf '{"jsonrpc":"2.0","id":%s,"error":{"code":-32000,"message":"z2term did not answer"}}
' "${d}id"
        |      fi
        |    done
        |    exit 0 ;;
        |  note)
        |    if [ "${d}#" -eq 3 ] && [ "${d}3" = - ]; then
        |      text=${d}(cat; printf .) || exit 1
        |      set -- note "${d}2" "${d}{text%.}"
        |    fi ;;
        |esac
        |out=${d}(Z2API_WAIT=1000 z2api 1 ui "${d}@" 2>&1)
        |rc=${d}?
        |case "${d}out" in
        |  '{'*) printf '%s\n' "${d}out" ;;
        |  *) printf '%s\n' '{"ok":false,"error":"no_response","message":"z2term did not answer"}'; rc=1 ;;
        |esac
        |exit "${d}rc"
    """.trimMargin() + "\n"
}
