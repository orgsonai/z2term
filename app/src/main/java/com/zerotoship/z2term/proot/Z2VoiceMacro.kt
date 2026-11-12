package com.zerotoship.z2term.proot

/**
 * 「声で操作」の同梱マクロ `voice.sh` (1.0.65)。
 *
 * 1.0.64 で声の受け口 (タイル・デジタル アシスタント → `voice` イベント) を入れたが、**聞いた言葉で
 * 何をするかは利用者任せ**で、入れただけでは何も起きなかった。`z2-macro install voice` で入る
 * 受け口をここで持つ。
 *
 * 設計上の要点:
 *  - **言葉は言語ごとの表 (データ) にする。** 決まった言葉・時刻の言い方・話す文は
 *    `~/.z2term/voice/words-<言語>.txt` にあり、利用者が書き換えて言い方を足せる (利用者の要望)。
 *    同梱の表は 6 言語ともスクリプトの中に持つので `voice.sh lang ko` で切り替えられる。表に無い
 *    キーは同梱の値に落ちる (古い表でも新しいキーが効く)。⚠ **振り分けの流れは 1 本**で、言語で
 *    変わるのは表だけ ([CliText] と同じ考え方)。
 *  - **AI は既定でオフ** (利用者の決定)。合図の言葉で始めたときにだけ聞き、オフなら通知に
 *    オンにする 1 行を「コピー」ボタンで添える。モデルは指定しなければ各 CLI の既定。
 *  - **リマインドは remind.sh の言語に依らない書き方** (`30m` / `tomorrow 07:00` /
 *    `weekly mon 21:00`) に直してから渡す。日時の言い方は表 (`clock_*` / `day_*` / `wd_*`) から組む。
 *
 * ⚠ 本体の `${'$'}` は全部 `${d}`。中身を直したら、出来上がった voice.sh を偽の `z2-*` で sh として
 * 動かして確かめること (`voice.sh try <言葉>` で話さずに 1 回ぶん流せる。[VoiceScriptTest] も同じ形)。
 */
fun z2VoiceMacro(lang: String): String {
    val t = CliText(lang)
    val d = "${'$'}"
    val head = t("voice.head",
        en = """# voice.sh — the receiver for "Voice command": what you say runs music, volume, timers, reminders, weather and more.
#
# Setup (once):
#   sh ~/.z2term/macros/voice.sh setup    register 4 rules (voice command / voice command while locked / voice timer / count steps)
#   Count steps only keeps the step counter running for "how many steps today"; it never runs anything
#   then speak through the "Voice command" quick settings tile or the digital assistant (long-press power etc.)
#
# From a terminal:
#   voice.sh words                 open the word table (~/.z2term/voice/words-<lang>.txt; add or change phrases)
#   voice.sh try <phrase>          try one without speaking (e.g. voice.sh try next song)
#   voice.sh listen                open the listening screen and speak (the same as the tile)
#   voice.sh lang [lang]           the word table's language (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   whether to use AI (off at first)
#   voice.sh model [name]          the AI's model (empty = that AI's default)
#   voice.sh setup / help
#
# AI is off at first. Even when on, it is only asked when you start with the cue word ("AI", "question" ...).
# The weather asks wttr.in only when you say "weather" (place: weather_city= in ~/.z2term/voice/config).
# Your own words: "words = command" in ~/.z2term/voice/commands.txt (checked before the word table)
# Names to call: "name number" in ~/.z2term/voice/contacts.txt / log: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        ja = """# voice.sh — 「声で操作」の受け口。話した言葉で音楽・音量・タイマー・リマインド・天気などを動かす。
#
# 準備 (1 回だけ):
#   sh ~/.z2term/macros/voice.sh setup    ルール 4 本を登録 (声で操作 / 声で操作（ロック中） / 声のタイマー / 歩数を数える)
#   歩数を数えるルールは、今日の歩数を聞けるように歩数計を動かすだけで、何も実行しません
#   あとはクイック設定のタイル「声で操作」か、デジタル アシスタント (電源ボタン長押しなど) で話す
#
# 端末から:
#   voice.sh words                 言葉の表を開く (~/.z2term/voice/words-<言語>.txt。言い方を足す・変える)
#   voice.sh try <言葉>            話さずに試す (例: voice.sh try 次の曲)
#   voice.sh listen                聞き取りの画面を開いて話す (タイルと同じ)
#   voice.sh lang [言語]           言葉の表の言語 (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   AI を使うか (最初は off)
#   voice.sh model [名前]          AI のモデル (空でその AI の既定)
#   voice.sh setup / help
#
# AI は最初はオフ。オンにしても、合図の言葉 (「AI」「質問」など) で始めたときだけ聞く。
# 天気は「天気」と言ったときだけ wttr.in に問い合わせる (場所は ~/.z2term/voice/config の weather_city=)。
# 自分で決めた言葉: ~/.z2term/voice/commands.txt に「言葉 = コマンド」 (言葉の表より先に見る)
# 電話の名前: ~/.z2term/voice/contacts.txt に「名前 番号」 / 動いた記録: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup"""
    )
    // 言葉の表の既定の言語 = アプリの言語 (表の無い言語は英語)。voice.sh lang で変えられる。
    val defaultLang = when (lang) {
        "ja", "zh-CN", "zh-TW", "es", "ko" -> lang
        else -> "en"
    }
    return "#!/bin/sh\n" + head + "\n\n" + voiceBody(d, defaultLang)
}

/** 振り分けの本体 (言語に依らない 1 本)。 */
private fun voiceBody(d: String, defaultLang: String): String = """VOICE_LANG_DEFAULT=$defaultLang

# Append (not prepend) the usual places: a rule's PATH may lack /usr/local/bin, and commands put
# first on PATH (a test's stand-ins) must still win.
export PATH="${d}PATH:/usr/local/bin:/usr/bin:/bin:${d}HOME/.local/bin"
set -f   # never expand * in words or candidates as file names
SELF="${d}HOME/.z2term/macros/voice.sh"
DIR=${d}{VOICE_DIR:-${d}HOME/.z2term/voice}
REMIND=${d}{VOICE_REMIND:-${d}HOME/.z2term/macros/remind.sh}
mkdir -p "${d}DIR"
LOG=${d}DIR/log.txt
CONFIG=${d}DIR/config
CONTACTS=${d}DIR/contacts.txt
COMMANDS=${d}DIR/commands.txt
# "navi" alone asks where to; the next thing said within a minute is the place.
NAVI_WAIT=${d}DIR/navi-wait
MEMO=${d}DIR/memo.txt
TIMER_PREFIX=voice-timer:
TAB=${d}(printf '\t')
OIFS=${d}IFS

log() { printf '%s %s\n' "${d}(date '+%F %T')" "${d}*" >> "${d}LOG"; }

# The config is key=value lines. Read it with sed, never source it (nothing in it runs).
conf_get() { [ -f "${d}CONFIG" ] && sed -n "s/^${d}1=//p" "${d}CONFIG" | tail -n 1; }
conf_set() {
  touch "${d}CONFIG"
  grep -v "^${d}1=" "${d}CONFIG" > "${d}CONFIG.tmp"
  printf '%s=%s\n' "${d}1" "${d}2" >> "${d}CONFIG.tmp"
  mv "${d}CONFIG.tmp" "${d}CONFIG"
}

known_lang() { case ${d}1 in en|ja|zh-CN|zh-TW|es|ko) return 0 ;; esac; return 1; }
VLANG=${d}(conf_get lang)
known_lang "${d}VLANG" || VLANG=${d}VOICE_LANG_DEFAULT
WORDS=${d}DIR/words-${d}VLANG.txt

${voiceWordTables()}

# Copy the bundled table out once, so there is a file to edit.
ensure_words() { [ -f "${d}WORDS" ] || bundled_words "${d}VLANG" > "${d}WORDS"; }

# Load "key = value" lines into W_<key>. The bundled table comes first and the user's file second:
# a key missing from an older file keeps working, and "key =" with nothing after it turns it off.
# Word lists are lowercased (A-Z only) so they compare with what was heard; say_/msg_/fmt_/title_
# lines are text to show or speak and are kept as written.
load_words() {
  eval "${d}({ bundled_words "${d}VLANG"; [ -f "${d}WORDS" ] && cat "${d}WORDS"; } | awk -v q="'" '
    # The list as an ERE group for grep -E (R_<key>), escaped here once instead of per use.
    function esc(s,  r, i, c) {
      r = ""
      for (i = 1; i <= length(s); i++) { c = substr(s, i, 1); r = r (index(M, c) ? "\\" c : c) }
      return r
    }
    function lc(s,  r, i, c, p) {
      r = ""
      for (i = 1; i <= length(s); i++) { c = substr(s, i, 1); p = index(U, c); r = r (p ? substr(L, p, 1) : c) }
      return r
    }
    BEGIN { U = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; L = "abcdefghijklmnopqrstuvwxyz"; M = "\\.[]*^${d}(){}+?" }
    /^[ \t]*#/ || !/=/ { next }
    {
      k = ${d}0; sub(/=.*/, "", k); gsub(/[ \t]/, "", k)
      if (k !~ /^[a-z0-9_]+${d}/) next
      v = ${d}0; sub(/^[^=]*=/, "", v); sub(/^[ \t]+/, "", v); sub(/[ \t\r]+${d}/, "", v)
      r = ""
      if (k !~ /^(say|msg|fmt|title)_/) { v = lc(v); gsub(/[ \t]*\|[ \t]*/, "|", v); if (v != "") r = "(" esc(v) ")" }
      gsub(q, q "\\" q q, v); gsub(q, q "\\" q q, r)
      print "W_" k "=" q v q
      print "R_" k "=" q r q
    }')"
}

# fill TEMPLATE name value ... — replace {name} with value (no sed, so any text is safe).
fill() {
  s=${d}1; shift
  while [ ${d}# -ge 2 ]; do
    k="{${d}1}"; out=; rest=${d}s
    while [ "${d}{rest#*"${d}k"}" != "${d}rest" ]; do out=${d}out${d}{rest%%"${d}k"*}${d}2; rest=${d}{rest#*"${d}k"}; done
    s=${d}out${d}rest; shift 2
  done
  printf '%s' "${d}s"
}
# say KEY name value ... — speak a filled say_ line.
say() { eval "tpl=\${d}{W_${d}1-}"; k=${d}1; shift; z2-say "${d}(fill "${d}tpl" "${d}@")"; }
msg() { eval "tpl=\${d}{W_${d}1-}"; shift; fill "${d}tpl" "${d}@"; }
first_word() { printf '%s' "${d}{1%%|*}"; }

trim() { s=${d}1; s=${d}{s#"${d}{s%%[! ]*}"}; s=${d}{s%"${d}{s##*[! ]}"}; TRIM=${d}s; }
nosp() {
  r=; s=${d}1
  while [ "${d}{s#*' '}" != "${d}s" ]; do r=${d}r${d}{s%%' '*}; s=${d}{s#*' '}; done
  NOSP=${d}r${d}s
}

# Lowercase A-Z, drop punctuation and squeeze spaces. ⚠ No tr/[] on multibyte characters: they
# work byte by byte and would break Japanese; each mark is removed as a whole string instead.
norm() {
  printf '%s' "${d}1" | tr 'A-Z' 'a-z' | sed \
    -e 's/。//g' -e 's/、//g' -e 's/，//g' -e 's/．//g' -e 's/！//g' -e 's/？//g' \
    -e 's/¿//g' -e 's/¡//g' -e "s/’/'/g" -e 's/[!?,.;"]//g' \
    -e 's/　/ /g' -e "s/${d}TAB/ /g" -e 's/  */ /g' -e 's/ *| */|/g' -e 's/^ //' -e 's/ ${d}//'
}

# Numbers to ASCII digits (三 -> 3, 十五 -> 15, ５ -> 5). Only used on the part that holds a number,
# so a name such as 一郎 is never touched.
digits() {
  printf '%s' "${d}1" | sed \
    -e 's/０/0/g' -e 's/１/1/g' -e 's/２/2/g' -e 's/３/3/g' -e 's/４/4/g' \
    -e 's/５/5/g' -e 's/６/6/g' -e 's/７/7/g' -e 's/８/8/g' -e 's/９/9/g' \
    -e 's/〇/0/g' -e 's/零/0/g' -e 's/一/1/g' -e 's/二/2/g' -e 's/两/2/g' -e 's/兩/2/g' \
    -e 's/三/3/g' -e 's/四/4/g' -e 's/五/5/g' -e 's/六/6/g' -e 's/七/7/g' -e 's/八/8/g' -e 's/九/9/g' \
    -e 's/\([0-9]\)十\([0-9]\)/\1\2/g' -e 's/\([0-9]\)十/\10/g' \
    -e 's/十\([0-9]\)/1\1/g' -e 's/十/10/g'
}
N_RE='(0|1|2|3|4|5|6|7|8|9|０|１|２|３|４|５|６|７|８|９|〇|零|一|二|两|兩|三|四|五|六|七|八|九|十)+'

# try_pat PATTERN TEXT — a pattern is a phrase, or a phrase with one * that takes the rest (-> ARG).
try_pat() {
  case ${d}1 in
    '*'?*'*')
      # *X* — the words on both sides of X (明天7点提醒我倒垃圾 -> 明天7点 倒垃圾).
      mid=${d}{1#'*'}; mid=${d}{mid%'*'}
      case ${d}mid in *'*'*) return 1 ;; esac
      case ${d}2 in
        *"${d}mid"*) trim "${d}{2%%"${d}mid"*} ${d}{2#*"${d}mid"}"; [ -n "${d}TRIM" ] || return 1; ARG=${d}TRIM; return 0 ;;
      esac ;;
    *'*'*)
      pre=${d}{1%%'*'*}; suf=${d}{1#*'*'}
      case ${d}2 in
        "${d}pre"*"${d}suf") a=${d}{2#"${d}pre"}; a=${d}{a%"${d}suf"}; trim "${d}a"; [ -n "${d}TRIM" ] || return 1; ARG=${d}TRIM; return 0 ;;
      esac ;;
    *) [ "${d}2" = "${d}1" ] && { ARG=; return 0; } ;;
  esac
  return 1
}

# match_list "a|b*|..." — true when a candidate fits a pattern; best candidate first.
# A second pass ignores spaces ("次の 曲" = "次の曲", "음악 꺼줘" = "음악 꺼 줘"). A * pattern
# with a space next to the * keeps that space as a word boundary (so "play *" never takes "playlist").
match_list() {
  [ -n "${d}1" ] || return 1
  IFS='|'
  for c in ${d}CANDS; do
    for p in ${d}1; do try_pat "${d}p" "${d}c" && { IFS=${d}OIFS; return 0; }; done
  done
  for c in ${d}CANDS_NS; do
    for p in ${d}1; do
      case ${d}p in '* '*|*' *'|*' * '*) continue ;; esac
      nosp "${d}p"; try_pat "${d}NOSP" "${d}c" && { IFS=${d}OIFS; return 0; }
    done
  done
  IFS=${d}OIFS
  return 1
}
match() { eval "match_list \"\${d}{W_${d}1-}\""; }

# has KEY TEXT — TEXT contains one of the words (spaces ignored on both sides).
has() {
  eval "list=\${d}{W_${d}1-}"
  [ -n "${d}list" ] || return 1
  nosp "${d}2"; hay=${d}NOSP
  IFS='|'
  for p in ${d}list; do
    nosp "${d}p"
    [ -n "${d}NOSP" ] || continue
    case ${d}hay in *"${d}NOSP"*) IFS=${d}OIFS; return 0 ;; esac
  done
  IFS=${d}OIFS
  return 1
}

# re KEY — the word list as an ERE group "(a|b)", or empty when the list is empty.
re() { eval "RE=\${d}{R_${d}1-}"; }
# alt A B ... — join the non-empty ones with | -> ALT.
alt() { ALT=; for x in "${d}@"; do [ -n "${d}x" ] && ALT="${d}{ALT:+${d}ALT|}${d}x"; done; }
# grab TEXT ERE — the first (leftmost-longest) match of ERE in TEXT -> GOT.
grab() { GOT=; [ -n "${d}2" ] && GOT=${d}(printf '%s\n' "${d}1" | grep -oE "${d}2" | head -n 1); [ -n "${d}GOT" ]; }
# cut_out — replace the literal GOT in S with a tab (marks where the part was).
cut_out() { S="${d}{S%%"${d}GOT"*}${d}TAB${d}{S#*"${d}GOT"}"; }

# clock_of TEXT — read a time of day out of TEXT -> HH, MM (24 h). False when there is none.
strip0() { n=${d}{1#"${d}{1%%[!0]*}"}; printf '%s' "${d}{n:-0}"; }
clock_of() {
  src=${d}1
  set -- ${d}(digits "${d}src" | grep -oE '[0-9]+' | head -n 2)
  [ ${d}# -ge 1 ] || return 1
  HH=${d}(strip0 "${d}1"); MM=${d}(strip0 "${d}{2:-0}")
  [ ${d}# -lt 2 ] && has clock_half "${d}src" && MM=30
  if has clock_pm "${d}src"; then [ "${d}HH" -lt 12 ] && HH=${d}((HH + 12))
  elif has clock_am "${d}src"; then [ "${d}HH" -eq 12 ] && HH=0
  fi
  [ "${d}HH" -le 23 ] && [ "${d}MM" -le 59 ]
}
fmt_clock() {
  mm=${d}(printf '%02d' "${d}2")
  if [ "${d}2" -eq 0 ]; then msg fmt_clock_oclock h "${d}1" m "${d}2" mm "${d}mm"
  else msg fmt_clock h "${d}1" m "${d}2" mm "${d}mm"; fi
}

# when_of TEXT — split a reminder into when (remind.sh's own words: 30m / tomorrow 07:00 /
# weekly mon 21:00 ...) and what. -> WHEN, WHAT. False when either is missing.
when_of() {
  S=${d}1; WHEN=; WHAT=; day=; rel=; clk=
  re unit_second; u_s=${d}RE; re unit_minute; u_m=${d}RE; re unit_hour; u_h=${d}RE; re unit_day; u_d=${d}RE
  alt "${d}u_h" "${d}u_m" "${d}u_s" "${d}u_d"; units=${d}ALT
  re when_before; before=${d}RE; re when_after; after=${d}RE
  rel_re=
  [ -n "${d}units" ] && [ -n "${d}before" ] && rel_re="${d}before ?${d}N_RE ?(${d}units)"
  [ -n "${d}units" ] && [ -n "${d}after" ] && rel_re="${d}{rel_re:+${d}rel_re|}${d}N_RE ?(${d}units) ?${d}after"
  if grab "${d}S" "${d}rel_re"; then
    n=${d}(digits "${d}GOT" | grep -oE '[0-9]+' | head -n 1)
    if has unit_hour "${d}GOT"; then rel=${d}{n}h
    elif has unit_minute "${d}GOT"; then rel=${d}{n}m
    elif has unit_second "${d}GOT"; then rel=${d}{n}s
    else rel=${d}{n}d; fi
    cut_out
  fi

  case ${d}rel in *[smh]) ;; *)
    re clock_at; at=${d}RE; re clock_hour; hw=${d}RE; re clock_minute; mw=${d}RE; re clock_half; hf=${d}RE
    re clock_am; am=${d}RE; re clock_pm; pm=${d}RE
    alt "${d}pm" "${d}am"; ap=${d}{ALT:+(${d}ALT)}
    # Before the number: "at" then "pm"-like words, or the other way round ("tonight at 9").
    pre="${d}{at:+(${d}at ?)?}${d}{ap:+(${d}ap ?)?}"
    [ -n "${d}at" ] && [ -n "${d}ap" ] && pre="(${d}pre|${d}ap ?${d}at ?)"
    post="${d}{ap:+( ?${d}ap)?}"
    min=
    if [ -n "${d}mw" ] && [ -n "${d}hf" ]; then min="( ?${d}N_RE ?${d}mw| ?${d}hf)?"
    elif [ -n "${d}mw" ]; then min="( ?${d}N_RE ?${d}mw)?"
    elif [ -n "${d}hf" ]; then min="( ?${d}hf)?"; fi
    clk_re=
    [ -n "${d}hw" ] && clk_re="${d}pre${d}N_RE ?${d}hw${d}min${d}post"
    clk_re="${d}{clk_re:+${d}clk_re|}${d}pre[0-9]{1,2}:[0-9]{2}${d}post"
    # "7 pm" — ASCII digits only, so 每周一上午9点 never reads 一上午 as 1 am.
    [ -n "${d}ap" ] && clk_re="${d}clk_re|${d}{at:+(${d}at ?)?}[0-9]{1,2} ?${d}ap"
    atpre=${d}at; [ -n "${d}at" ] && [ -n "${d}ap" ] && atpre="(${d}at|${d}ap ?${d}at)"
    [ -n "${d}at" ] && clk_re="${d}clk_re|${d}atpre ?${d}N_RE${d}{hf:+( ?${d}hf)?}${d}post"
    if grab "${d}S" "${d}clk_re" && clock_of "${d}GOT"; then clk=${d}(printf '%02d:%02d' "${d}HH" "${d}MM"); cut_out; fi
    ;;
  esac

  re day_today; d0=${d}RE; re day_tomorrow; d1=${d}RE; re day_after_tomorrow; d2=${d}RE
  re day_daily; dd=${d}RE; re day_weekdays; dw=${d}RE; re day_every; every=${d}RE
  wds=
  for w in mon tue wed thu fri sat sun; do re "wd_${d}w"; [ -n "${d}RE" ] && wds="${d}{wds:+${d}wds|}${d}RE"; done
  alt "${d}dd" "${d}dw" "${d}d2" "${d}d1" "${d}d0"; day_re=${d}ALT
  [ -n "${d}wds" ] && day_re="${d}{day_re:+${d}day_re|}${d}{every:+${d}every ?}(${d}wds)|(${d}wds)"
  case ${d}rel in *d) ;; ?*) day_re= ;; esac
  if grab "${d}S" "${d}day_re"; then
    g=${d}GOT
    if has day_daily "${d}g"; then day=daily
    elif has day_weekdays "${d}g"; then day=weekday
    elif has day_after_tomorrow "${d}g"; then day=2d
    elif has day_tomorrow "${d}g"; then day=tomorrow
    elif has day_today "${d}g"; then day=today
    else
      for w in mon tue wed thu fri sat sun; do has "wd_${d}w" "${d}g" && { day=${d}w; break; }; done
      [ -n "${d}every" ] && grab "${d}g" "^${d}every" && day="weekly ${d}day"
      GOT=${d}g
    fi
    cut_out
  fi

  # What is left is the text. Drop the small words that only joined it to the parts cut out
  # ("…at⇥", "⇥to …", "…を⇥の⇥に"), then the tabs.
  fl=${d}{W_remind_filler-}
  i=0
  while [ ${d}i -lt 8 ]; do
    i=${d}((i + 1)); before_s=${d}S
    while [ "${d}{S#*" ${d}TAB"}" != "${d}S" ]; do S="${d}{S%%" ${d}TAB"*}${d}TAB${d}{S#*" ${d}TAB"}"; done
    while [ "${d}{S#*"${d}TAB "}" != "${d}S" ]; do S="${d}{S%%"${d}TAB "*}${d}TAB${d}{S#*"${d}TAB "}"; done
    IFS='|'
    for f in ${d}fl; do
      [ -n "${d}f" ] || continue
      if [ "${d}W_fmt_spaces" = yes ]; then
        case ${d}S in *"${d}TAB${d}f "*) S="${d}{S%%"${d}TAB${d}f "*}${d}TAB${d}{S#*"${d}TAB${d}f "}" ;; esac
        case ${d}S in *" ${d}f${d}TAB"*) S="${d}{S%%" ${d}f${d}TAB"*}${d}TAB${d}{S#*" ${d}f${d}TAB"}" ;; esac
        case ${d}S in "${d}f${d}TAB"*) S="${d}TAB${d}{S#"${d}f${d}TAB"}" ;; esac
        case ${d}S in *"${d}TAB${d}f") S="${d}{S%"${d}TAB${d}f"}${d}TAB" ;; esac
      else
        case ${d}S in *"${d}f${d}TAB"*) S="${d}{S%%"${d}f${d}TAB"*}${d}TAB${d}{S#*"${d}f${d}TAB"}" ;; esac
        case ${d}S in *"${d}TAB${d}f"*) S="${d}{S%%"${d}TAB${d}f"*}${d}TAB${d}{S#*"${d}TAB${d}f"}" ;; esac
      fi
    done
    IFS=${d}OIFS
    [ "${d}S" = "${d}before_s" ] && break
  done
  if [ "${d}W_fmt_spaces" = yes ]; then WHAT=${d}(printf '%s' "${d}S" | sed -e "s/${d}TAB/ /g" -e 's/  */ /g')
  else WHAT=${d}(printf '%s' "${d}S" | sed -e "s/${d}TAB//g"); fi
  trim "${d}WHAT"; WHAT=${d}TRIM

  if [ -n "${d}rel" ]; then
    case ${d}rel in *d) WHEN="${d}rel${d}{clk:+ ${d}clk}" ;; *) WHEN=${d}rel ;; esac
  else
    case ${d}day in
      daily|weekday|weekly*) [ -n "${d}clk" ] && WHEN="${d}day ${d}clk" ;;
      ''|today) WHEN=${d}clk ;;
      *) WHEN="${d}day${d}{clk:+ ${d}clk}" ;;
    esac
  fi
  [ -n "${d}WHEN" ] && [ -n "${d}WHAT" ]
}

# --- AI ---
find_ai() { for a in codex claude agy; do command -v "${d}a" >/dev/null 2>&1 && { printf '%s' "${d}a"; return 0; }; done; return 1; }

ask_ai() {
  q=${d}1
  ai=${d}(conf_get ai); ai=${d}{ai:-off}
  model=${d}(conf_get model)
  if [ "${d}ai" = off ]; then
    # Off by default. Say how to turn it on, with the line ready on a Copy button.
    found=${d}(find_ai)
    if [ -n "${d}found" ]; then
      z2-notify -n voice-ai -c "sh ~/.z2term/macros/voice.sh ai ${d}found" "${d}W_title_ai" "${d}(msg msg_ai_off ai "${d}found")"
    else
      z2-notify -n voice-ai "${d}W_title_ai" "${d}(msg msg_ai_off_none)"
    fi
    log "ai off: ${d}q"; return
  fi
  if ! command -v "${d}ai" >/dev/null 2>&1; then
    z2-notify -n voice-ai "${d}W_title_ai" "${d}(msg msg_ai_missing ai "${d}ai")"
    log "ai(${d}ai): not installed"; return
  fi
  z2-toast "${d}(msg msg_ai_asking ai "${d}ai")"
  # ⚠ No screen and nobody watching: it may only answer (no files, no commands). An empty working
  # folder keeps it from loading project instructions and tools, which is most of the wait.
  WORK=${d}DIR/ai-work; mkdir -p "${d}WORK"
  prompt="${d}q

${d}W_fmt_ai_prompt"
  start=${d}(date +%s)
  # No model given -> each CLI's own default. Change it with: voice.sh model <name>
  case "${d}ai" in
    claude)
      set -- -p --tools "" --setting-sources "" --strict-mcp-config --no-session-persistence
      [ -n "${d}model" ] && set -- "${d}@" --model "${d}model"
      ans=${d}(cd "${d}WORK" && timeout 60 claude "${d}@" "${d}prompt" </dev/null 2>>"${d}LOG")
      ;;
    codex)
      out=${d}WORK/codex-answer.txt; rm -f "${d}out"
      set -- exec --skip-git-repo-check --ephemeral --ignore-user-config -s read-only -c 'model_reasoning_effort="low"'
      [ -n "${d}model" ] && set -- "${d}@" -m "${d}model"
      (cd "${d}WORK" && timeout 60 codex "${d}@" -o "${d}out" "${d}prompt" </dev/null >/dev/null 2>>"${d}LOG")
      ans=${d}(cat "${d}out" 2>/dev/null)
      ;;
    agy)
      # ⚠ -p must be joined with = : a separate argument after -p is taken as the question.
      set --
      [ -n "${d}model" ] && set -- --model "${d}model"
      ans=${d}(cd "${d}WORK" && timeout 60 agy "${d}@" -p="${d}prompt" </dev/null 2>>"${d}LOG")
      ;;
  esac
  took=${d}(( ${d}(date +%s) - start ))
  if [ -z "${d}ans" ]; then
    z2-notify -n voice-ai "${d}W_title_ai (${d}ai)" "${d}(msg msg_ai_noanswer log "${d}LOG")"
    log "ai(${d}ai): no answer (${d}{took}s)"; return
  fi
  z2-notify -n voice-ai -c "${d}ans" "${d}W_title_ai: ${d}q" "${d}ans"
  z2-say "${d}ans"
  log "ai(${d}ai ${d}{model:-default}): answered (${d}{#ans} chars, ${d}{took}s)"
}

# --- setup: the three rules (only the ones not there yet) ---
has_rule() { z2-when list 2>/dev/null | grep -F "macros/voice.sh" | grep -qF "${d}1"; }
cmd_setup() {
  # ⚠ The tab after the name: "event:voice" alone would also find "event:voice_locked".
  has_rule "${d}(printf 'event:voice\t')" ||
    z2-when event:voice name="${d}W_title_rule_voice" run "sh ${d}SELF" >/dev/null || return 1
  # What is said while the phone is locked arrives as voice_locked (only the safe things run).
  has_rule "event:voice_locked" ||
    z2-when event:voice_locked name="${d}W_title_rule_voice_locked" run "sh ${d}SELF" >/dev/null
  has_rule "event:alarm" ||
    z2-when event:alarm name="${d}W_title_rule_timer" run "sh ${d}SELF" >/dev/null
  # Only there to keep the step counter running for "how many steps today" (it never fires).
  has_rule "steps:" ||
    z2-when steps:reach=1000000 name="${d}W_title_rule_steps" run "sh ${d}SELF steps-keep" >/dev/null
  ensure_words
  msg msg_setup_done; echo
  z2-when list | grep -F "macros/voice.sh" | cut -f1,3
  echo
  msg msg_setup_steps; echo
  msg msg_words_file file "${d}WORDS"; echo
}

load_words

# --- Commands typed in a terminal ---
case "${d}{1:-}" in
  ai)
    if [ -n "${d}{2:-}" ]; then
      case "${d}2" in
        # Another AI's model name would fail, so switching AI clears the model (= its default).
        off|claude|codex|agy) [ "${d}2" = "${d}(conf_get ai)" ] || conf_set model ""; conf_set ai "${d}2" ;;
        *) msg msg_ai_usage >&2; echo >&2; exit 2 ;;
      esac
    fi
    a=${d}(conf_get ai); m=${d}(conf_get model)
    echo "ai=${d}{a:-off}"; echo "model=${d}m"
    [ "${d}{a:-off}" = off ] && { found=${d}(find_ai); msg msg_ai_how ai "${d}{found:-codex}"; echo; }
    exit 0
    ;;
  model)
    conf_set model "${d}{2:-}"
    echo "model=${d}(conf_get model)"
    exit 0
    ;;
  lang)
    if [ -n "${d}{2:-}" ]; then
      known_lang "${d}2" || { echo "lang: en | ja | zh-CN | zh-TW | es | ko" >&2; exit 2; }
      conf_set lang "${d}2"; VLANG=${d}2; WORDS=${d}DIR/words-${d}VLANG.txt; load_words
    fi
    ensure_words
    echo "lang=${d}VLANG"
    msg msg_words_file file "${d}WORDS"; echo
    exit 0
    ;;
  words)
    ensure_words
    # z2term の編集画面で開く (ふつうの入力欄。nano や vi はスマホのキーボードでは重い)。
    # 画面で開くので、タイルやパネルから呼んでも開ける。
    command -v z2-edit >/dev/null 2>&1 && exec z2-edit "${d}WORDS"
    ed=${d}{VISUAL:-${d}{EDITOR:-}}
    [ -n "${d}ed" ] || for e in nano vim vi; do command -v "${d}e" >/dev/null 2>&1 && { ed=${d}e; break; }; done
    if [ -t 0 ] && [ -t 1 ] && [ -n "${d}ed" ]; then exec ${d}ed "${d}WORDS"; fi
    msg msg_words_file file "${d}WORDS"; echo
    exit 0
    ;;
  listen)
    # The listening screen, as the tile opens it (for trying it from a terminal, a panel or a widget).
    exec z2-intent -n com.zerotoship.z2term/com.zerotoship.z2term.voice.VoiceCommandActivity
    ;;
  try)
    # Try a phrase without speaking: voice.sh try 次の曲
    shift; Z2_WHEN_EVENT=voice; Z2_WHEN_ACTION=${d}*; Z2_WHEN_EVENT_NAME=${d}*
    ;;
  setup) cmd_setup; exit ${d}? ;;
  steps-keep) exit 0 ;;
  -h|--help|help) awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0 ;;
esac

# --- A timer went off (event:alarm) ---
if [ "${d}{Z2_WHEN_EVENT:-}" = "alarm" ]; then
  case "${d}{Z2_WHEN_EVENT_NAME:-}" in
    "${d}TIMER_PREFIX"*)
      what=${d}{Z2_WHEN_EVENT_NAME#"${d}TIMER_PREFIX"}
      log "timer done: ${d}what"
      z2-vibrate 600
      if [ "${d}what" = "${d}W_fmt_wake_label" ]; then m=${d}(msg say_wake); else m=${d}(msg say_timer_done label "${d}what"); fi
      z2-notify -h -n voice-timer "${d}W_title_timer" "${d}m"
      z2-say "${d}m"
      ;;
  esac
  exit 0
fi

# Said while the phone was locked: only the things in the locked list run (below).
LOCKED=
if [ "${d}{Z2_WHEN_EVENT:-}" = "voice_locked" ]; then LOCKED=1; Z2_WHEN_EVENT=voice; fi
[ "${d}{Z2_WHEN_EVENT:-}" = "voice" ] || { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0; }
text=${d}{Z2_WHEN_ACTION:-}
[ -n "${d}text" ] || exit 0
# Recognition candidates, best first, | separated. Fixed words look at all of them.
CANDS=${d}(norm "${d}{Z2_WHEN_EVENT_NAME:-${d}text}")
CANDS_NS=${d}(printf '%s' "${d}CANDS" | sed 's/ //g')
t=${d}(norm "${d}text")
log "heard: ${d}text  [${d}CANDS]"

# map_search PLACE — the standard "geo:" search, so whichever map app the phone uses opens the place
# (its page for the place has the route and the phone number).
map_search() {
  q=${d}(printf '%s' "${d}1" | python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip()))')
  z2-intent -a android.intent.action.VIEW -d "geo:0,0?q=${d}q" >/dev/null 2>&1
}

# --- Locked: run only what is safe to do for anyone holding the phone ---
# Music, volume, time, battery, steps, flashlight, news, weather, timers and "what can I say" run as
# they are. Anything else (calls, apps, maps, notes, reminders, AI, your own words) waits for the phone
# to be unlocked and then runs, within 2 minutes; the notification asks for the unlock.
if [ -n "${d}LOCKED" ]; then
  ok=
  for k in media_next media_previous media_pause media_play volume_up volume_down volume_unmute volume_mute \
           time date battery steps torch_on torch_off news weather_tomorrow weather wake help timer; do
    match "${d}k" && { ok=1; break; }
  done
  [ -n "${d}ok" ] || ! has volume_word "${d}(digits "${d}t")" || ok=1
  if [ -z "${d}ok" ]; then
    z2-notify -h -n voice-locked "${d}W_title_app" "${d}(msg msg_locked text "${d}text")"
    say say_locked
    log "locked: '${d}text' waits for the unlock"
    (
      i=0
      while [ ${d}i -lt 60 ]; do
        sleep 2; i=${d}((i + 1))
        [ "${d}(z2-state locked 2>/dev/null)" = false ] || continue
        log "unlocked: ${d}text"
        Z2_WHEN_EVENT=voice Z2_WHEN_ACTION=${d}text Z2_WHEN_EVENT_NAME=${d}{Z2_WHEN_EVENT_NAME:-${d}text} sh "${d}SELF"
        exit 0
      done
      log "locked: '${d}text' dropped (not unlocked within 2 minutes)"
    ) >/dev/null 2>&1 &
    exit 0
  fi
fi

# --- The place asked for by "navi" alone (said within a minute) ---
if [ -f "${d}NAVI_WAIT" ]; then
  age=${d}(( ${d}(date +%s) - ${d}(date -r "${d}NAVI_WAIT" +%s 2>/dev/null || echo 0) ))
  rm -f "${d}NAVI_WAIT"
  if [ "${d}age" -le 60 ]; then map_search "${d}text"; log "navi: ${d}text"; exit 0; fi
fi

# --- Your own words (~/.z2term/voice/commands.txt, before everything else) ---
# One line per command: "words = command". "a|b = command" for several ways to say it; a word
# ending in * matches the start and the rest arrives as ${d}1. Lines starting with # are notes.
if [ -f "${d}COMMANDS" ]; then
  while IFS= read -r line || [ -n "${d}line" ]; do
    case "${d}line" in ''|'#'*) continue ;; *' = '*) ;; *) continue ;; esac
    keys=${d}(norm "${d}{line%% = *}"); cmd=${d}{line#* = }
    if match_list "${d}keys"; then
      log "command: ${d}keys -> ${d}cmd ${d}{ARG:+(${d}ARG)}"
      sh -c "${d}cmd" voice "${d}ARG" >>"${d}LOG" 2>&1 || log "command failed: ${d}cmd"
      exit 0
    fi
  done < "${d}COMMANDS"
fi

# --- AI (only when it starts with the cue word) ---
if match ai; then ask_ai "${d}ARG"; exit 0; fi

# --- Media ---
if match media_next; then z2-media next; log "media next"; exit 0; fi
if match media_previous; then z2-media previous; log "media previous"; exit 0; fi
if match media_pause; then z2-media pause; log "media pause"; exit 0; fi
if match media_play; then z2-media play; log "media play"; exit 0; fi

# --- Volume (2 steps at a time) ---
if match volume_up; then z2-volume up >/dev/null; z2-volume up >/dev/null; log "volume up"; exit 0; fi
if match volume_down; then z2-volume down >/dev/null; z2-volume down >/dev/null; log "volume down"; exit 0; fi
if match volume_unmute; then z2-volume unmute >/dev/null; log "volume unmute"; exit 0; fi
if match volume_mute; then z2-volume mute >/dev/null; log "volume mute"; exit 0; fi

# --- Volume with a number (3 steps down / to 10 / 50% / max) ---
tn=${d}(digits "${d}t")
if has volume_word "${d}tn"; then
  n=${d}(printf '%s' "${d}tn" | grep -oE '[0-9]+' | head -n 1)
  if has volume_unmute_word "${d}tn"; then z2-volume unmute >/dev/null; log "volume unmute"; exit 0; fi
  if has volume_mute_word "${d}tn"; then z2-volume mute >/dev/null; log "volume mute"; exit 0; fi
  if has volume_max_word "${d}tn"; then z2-volume 100% >/dev/null; log "volume max"; exit 0; fi
  if has volume_min_word "${d}tn"; then z2-volume 0 >/dev/null; log "volume 0"; exit 0; fi
  if has volume_percent_word "${d}tn" && [ -n "${d}n" ]; then z2-volume "${d}n%" >/dev/null; log "volume ${d}n%"; exit 0; fi
  dir=
  if has volume_up_word "${d}tn"; then dir=up; elif has volume_down_word "${d}tn"; then dir=down; fi
  if [ -n "${d}dir" ]; then
    # No number: 2 steps. Stop at 30 so a misheard number cannot run on and on.
    steps=${d}{n:-2}; [ "${d}steps" -gt 30 ] && steps=30
    i=0; while [ "${d}i" -lt "${d}steps" ]; do z2-volume "${d}dir" >/dev/null; i=${d}((i + 1)); done
    log "volume ${d}dir x${d}steps"; exit 0
  fi
  if [ -n "${d}n" ]; then z2-volume "${d}n" >/dev/null; log "volume ${d}n"; exit 0; fi
fi

# --- Time and date ---
if match time; then
  h=${d}(date +%H); m=${d}(date +%M); h=${d}{h#0}; m=${d}{m#0}
  say say_time clock "${d}(fmt_clock "${d}h" "${d}m")"
  log "time"; exit 0
fi
if match date; then
  wd=${d}(date +%u); mo=${d}(date +%m); mo=${d}{mo#0}; dy=${d}(date +%d); dy=${d}{dy#0}
  wname=${d}(printf '%s' "${d}W_fmt_weekdays" | cut -d'|' -f"${d}wd")
  mname=${d}(printf '%s' "${d}W_fmt_months" | cut -d'|' -f"${d}mo")
  say say_date month "${d}mo" month_name "${d}mname" day "${d}dy" weekday "${d}wname"
  log "date"; exit 0
fi

# --- Battery ---
if match battery; then
  b=${d}(z2-battery 2>/dev/null)
  lv=${d}(printf '%s' "${d}b" | grep -oE '"level":[0-9]+' | grep -oE '[0-9]+')
  case "${d}b" in *'"charging":true'*) ch=${d}W_say_charging ;; *) ch= ;; esac
  say say_battery n "${d}{lv:-?}" charging "${d}ch"
  log "battery ${d}lv"; exit 0
fi

# --- Steps (the app's step counter leaves "date start latest previous" in ~/.z2term/when/.steps) ---
# It only counts while a steps: rule exists (setup adds one) and "Physical activity" is allowed.
if match steps; then
  f=${d}HOME/.z2term/when/.steps
  if [ -s "${d}f" ]; then
    read -r d base last _ < "${d}f"
    if [ "${d}d" = "${d}(date +%F)" ]; then n=${d}((last - base)); [ "${d}n" -lt 0 ] && n=0; else n=0; fi
    say say_steps n "${d}n"; log "steps ${d}n"
  else
    say say_steps_none; log "steps: no state"
  fi
  exit 0
fi

# --- Flashlight / screen (z2-key needs the z2term accessibility service) ---
if match torch_on; then z2-torch on >/dev/null; log "torch on"; exit 0; fi
if match torch_off; then z2-torch off >/dev/null; log "torch off"; exit 0; fi
if match screenshot; then z2-key screenshot >/dev/null 2>&1; log "screenshot"; exit 0; fi
if match home; then z2-key home >/dev/null 2>&1; log "home"; exit 0; fi

# --- News (titles rss.sh collected) ---
if match news; then
  f=${d}HOME/.z2term/rss/latest.txt
  if [ -s "${d}f" ]; then
    n=${d}(conf_get news_count); n=${d}{n:-5}
    titles=${d}(head -n "${d}n" "${d}f" | sed -E 's/[[:space:]]+https?:[^[:space:]]*${d}//')
    z2-notify -n voice-news "${d}W_title_news" "${d}titles"
    say say_news titles "${d}(printf '%s\n' "${d}titles" | sed "s/\${d}/${d}W_fmt_sentence_end/")"
  else
    say say_news_none
  fi
  log "news"; exit 0
fi

# --- Weather (wttr.in, free and no sign-up; asked only when you say it). Place: weather_city= in
# the config; empty lets wttr.in guess from where the request comes from. ---
wday=
if match weather_tomorrow; then wday=1; elif match weather; then wday=0; fi
if [ -n "${d}wday" ]; then
  city=${d}(conf_get weather_city)
  wl=${d}{W_fmt_weather_lang:-en}
  if [ "${d}wday" = 0 ]; then
    w=${d}(curl -s --max-time 10 "https://wttr.in/${d}{city}?format=%C%7C%t&lang=${d}{wl}" 2>/dev/null)
    case ${d}w in *'|'*) ;; *) w= ;; esac
    if [ -n "${d}w" ]; then
      trim "${d}{w%%|*}"; desc=${d}TRIM
      temp=${d}(printf '%s' "${d}{w#*|}" | sed 's/[^0-9-]//g')
      say say_weather desc "${d}desc" temp "${d}temp"
    else say say_weather_fail; fi
  else
    w=${d}(curl -s --max-time 10 "https://wttr.in/${d}{city}?format=j1&lang=${d}{wl}" 2>/dev/null | python3 -c '
import json,sys
d=json.load(sys.stdin)["weather"][1]
h=d["hourly"][4]
desc=(h.get("lang_"+sys.argv[1].lower()) or h["weatherDesc"])[0]["value"]
rain=max(int(x["chanceofrain"]) for x in d["hourly"])
print("\t".join([desc.strip(), d["maxtempC"], d["mintempC"], str(rain)]))' "${d}wl" 2>/dev/null)
    if [ -n "${d}w" ]; then
      say say_weather_tomorrow desc "${d}(printf '%s' "${d}w" | cut -f1)" hi "${d}(printf '%s' "${d}w" | cut -f2)" \
        lo "${d}(printf '%s' "${d}w" | cut -f3)" rain "${d}(printf '%s' "${d}w" | cut -f4)"
    else say say_weather_fail; fi
  fi
  log "weather day+${d}wday: ${d}w"; exit 0
fi

# --- Memo (~/.z2term/voice/memo.txt) ---
if match memo_read; then
  if [ -s "${d}MEMO" ]; then
    z2-notify -n voice-memo "${d}W_title_memo" "${d}(tail -n 10 "${d}MEMO" | cut -f2)"
    say say_memo_recent memos "${d}(tail -n 3 "${d}MEMO" | cut -f2 | sed "s/\${d}/${d}W_fmt_sentence_end/")"
  else say say_memo_none; fi
  log "memo read"; exit 0
fi
if match memo_add; then
  printf '%s\t%s\n' "${d}(date '+%F %H:%M')" "${d}ARG" >> "${d}MEMO"
  say say_memo_saved; log "memo: ${d}ARG"; exit 0
fi

# --- Reminders (handed to remind.sh; it does the alarm and the list) ---
if match remind_list; then
  if [ ! -f "${d}REMIND" ]; then say say_remind_missing; log "remind: no remind.sh"; exit 0; fi
  l=${d}(sh "${d}REMIND" list 2>&1)
  z2-notify -n voice-remind "${d}W_title_remind" "${d}l"
  z2-say "${d}(printf '%s\n' "${d}l" | sed -E 's/^ *[0-9]+ +//; s/[⏰🔁✔] *//' | sed "s/\${d}/${d}W_fmt_sentence_end/" | head -n 5)"
  log "remind list"; exit 0
fi
# remind_verb: patterns that leave a verb stem; fmt_remind_verb turns it into the text (ko 버리 -> 버리기).
verb=
if match remind_verb; then verb=1; elif match remind; then verb=0; fi
if [ -n "${d}verb" ]; then
  if [ ! -f "${d}REMIND" ]; then say say_remind_missing; log "remind: no remind.sh"; exit 0; fi
  if ! when_of "${d}ARG"; then say say_remind_unclear; log "remind: could not read '${d}ARG'"; exit 0; fi
  [ "${d}verb" = 1 ] && WHAT=${d}(msg fmt_remind_verb what "${d}WHAT")
  # shellcheck disable=SC2086 — WHEN is remind.sh's words, split on purpose
  if res=${d}(sh "${d}REMIND" ${d}WHEN "${d}WHAT" 2>&1); then
    say say_remind_ok res "${d}(printf '%s' "${d}res" | head -n 1)"
    log "remind: ${d}WHEN / ${d}WHAT"
  else
    say say_remind_fail res "${d}(printf '%s' "${d}res" | head -n 1)"
    log "remind failed: ${d}WHEN / ${d}WHAT: ${d}res"
  fi
  exit 0
fi

# --- Wake-up alarm (7時に起こして / wake me up at 7) ---
if match wake; then
  if clock_of "${d}ARG"; then
    at=${d}(printf '%02d:%02d' "${d}HH" "${d}MM")
    z2-alarm at "${d}at" "${d}TIMER_PREFIX${d}W_fmt_wake_label" >/dev/null
    say say_wake_set clock "${d}(fmt_clock "${d}HH" "${d}MM")"
    log "alarm ${d}at"
  else
    say say_wake_unclear; log "alarm: '${d}ARG'"
  fi
  exit 0
fi

# --- What can I say ---
if match help; then
  z2-notify -n voice-help "${d}W_title_help" "${d}(msg msg_help)"
  say say_help
  log "help"; exit 0
fi

# --- Timer (3分のタイマー / timer for 5 minutes) ---
if match timer; then
  # The number that stands next to the unit (设一个3分钟 -> 3, not the 一 of 一个).
  re unit_hour; u_h=${d}RE; re unit_minute; u_m=${d}RE; re unit_second; u_s=${d}RE
  alt "${d}u_h" "${d}u_m" "${d}u_s"; units=${d}ALT
  n=; unit=; u=
  if [ -n "${d}units" ] && grab "${d}ARG" "${d}N_RE ?(${d}units)"; then
    n=${d}(digits "${d}GOT" | grep -oE '[0-9]+' | head -n 1)
    if has unit_hour "${d}GOT"; then unit=h; u=${d}(first_word "${d}W_fmt_unit_hour")
    elif has unit_minute "${d}GOT"; then unit=m; u=${d}(first_word "${d}W_fmt_unit_minute")
    else unit=s; u=${d}(first_word "${d}W_fmt_unit_second"); fi
  fi
  if [ -n "${d}n" ] && [ -n "${d}unit" ]; then
    label=${d}(msg fmt_timer_label n "${d}n" unit "${d}u")
    z2-alarm in "${d}n${d}unit" "${d}TIMER_PREFIX${d}label" >/dev/null
    say say_timer_set label "${d}label"
    log "timer ${d}label"
  else
    say say_timer_unclear; log "timer: no length in '${d}ARG'"
  fi
  exit 0
fi

# --- Phone call (opens the dialer; you press call yourself) ---
# ~/.z2term/voice/contacts.txt: one "name number" per line (the last word is the number).
# --- Navigation (◯◯までナビ) and a place's phone number (◯◯の電話番号): the map app opens the place ---
if match navi; then map_search "${d}ARG"; log "navi: ${d}ARG"; exit 0; fi
if match place_phone; then map_search "${d}ARG"; log "place phone: ${d}ARG"; exit 0; fi
# "navi" alone: open the listening screen again for the place. When nothing comes within 20 seconds
# (not heard, or the screen could not open), ask in a notification's reply box instead.
if match navi_ask; then
  : > "${d}NAVI_WAIT"
  z2-toast "${d}(msg msg_navi_where)"
  z2-intent -a android.intent.action.ASSIST -p com.zerotoship.z2term >/dev/null 2>&1
  (
    sleep 20
    [ -f "${d}NAVI_WAIT" ] || exit 0
    rm -f "${d}NAVI_WAIT"
    place=${d}(z2-ask -H "${d}(msg msg_navi_hint)" "${d}W_title_navi") || exit 0
    [ -n "${d}place" ] || exit 0
    map_search "${d}place"; log "navi (typed): ${d}place"
  ) >/dev/null 2>&1 &
  log "navi: asking"
  exit 0
fi

if match call; then
  who=${d}ARG; num=
  case "${d}who" in
    *[!0-9-]*) [ -f "${d}CONTACTS" ] && num=${d}(awk -v n="${d}who" '
        { num = ${d}NF; ${d}NF = ""; sub(/[ \t]+${d}/, ""); if (tolower(${d}0) == tolower(n)) { print num; exit } }' "${d}CONTACTS") ;;
    *) num=${d}who ;;
  esac
  if [ -n "${d}num" ]; then
    z2-intent -a android.intent.action.DIAL -d "tel:${d}num"
    log "dial ${d}who (${d}num)"
  else
    z2-notify -n voice-call "${d}W_title_call" "${d}(msg msg_call_missing who "${d}who" file "${d}CONTACTS")"
    say say_call_missing who "${d}who"
    log "dial: '${d}who' not in contacts"
  fi
  exit 0
fi

# --- Play music by searching (◯◯を再生 / play ...) ---
if match play; then
  z2-intent -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
    --es query "${d}ARG" --es android.intent.extra.focus "vnd.android.cursor.item/*"
  log "play from search: ${d}ARG"
  exit 0
fi

# --- Open an app by its name (◯◯を開いて / open ...) ---
# is_word KEY TEXT — TEXT is exactly one of the words (spaces ignored).
is_word() {
  eval "list=\${d}{W_${d}1-}"
  [ -n "${d}list" ] || return 1
  nosp "${d}2"; w=${d}NOSP
  IFS='|'
  for p in ${d}list; do nosp "${d}p"; [ -n "${d}NOSP" ] && [ "${d}NOSP" = "${d}w" ] && { IFS=${d}OIFS; return 0; }; done
  IFS=${d}OIFS
  return 1
}
# open_kind TEXT — a kind of app (browser, maps, camera ...) opens the phone's default one.
open_kind() {
  for k in browser:APP_BROWSER maps:APP_MAPS mail:APP_EMAIL calendar:APP_CALENDAR music:APP_MUSIC \
           gallery:APP_GALLERY contacts:APP_CONTACTS messages:APP_MESSAGING calculator:APP_CALCULATOR \
           camera:@android.media.action.STILL_IMAGE_CAMERA settings:@android.settings.SETTINGS dialer:@android.intent.action.DIAL; do
    is_word "open_${d}{k%%:*}" "${d}1" || continue
    v=${d}{k#*:}
    case ${d}v in
      @*) z2-intent -a "${d}{v#@}" >/dev/null 2>&1 ;;
      *) z2-intent -a android.intent.action.MAIN -c "android.intent.category.${d}v" >/dev/null 2>&1 ;;
    esac
    log "open kind ${d}{k%%:*}"
    return 0
  done
  return 1
}

# find_app name|rest — the package for ${d}ARG out of ${d}APPS (z2-app list). "name": a nickname in the word
# table or the app's name. "rest": part of the name, then the reading — kana said for a name written in
# letters (ヴィヴァルディ -> Vivaldi).
find_app() {
  printf '%s' "${d}APPS" | python3 -c '
import json,sys,unicodedata
n=lambda s: unicodedata.normalize("NFKC",s).lower().replace(" ","")
want=n(sys.argv[1]); step=sys.argv[3]; apps=json.load(sys.stdin)
for pair in sys.argv[2].split("|"):
    if ":" in pair:
        k,v=pair.split(":",1)
        if n(k)==want: want=n(v)
if step=="name":
    hit=[a for a in apps if n(a["label"])==want or a["package"]==want]
    print(hit[0]["package"] if hit else ""); sys.exit()
hit=[a for a in apps if want and want in n(a["label"])]
if hit: print(hit[0]["package"]); sys.exit()
# --- the reading ---
kata="".join(chr(ord(c)+96) if "ぁ"<=c<="ゖ" else c for c in want)
if not kata or any(not ("ァ"<=c<="ー") for c in kata): print(""); sys.exit()
K=dict(zip("アイウエオカキクケコガギグゲゴサシスセソザジズゼゾタチツテトダヂヅデドナニヌネノハヒフヘホバビブベボパピプペポマミムメモヤユヨラリルレロワヲンヴ",
  "a i u e o ka ki ku ke ko ga gi gu ge go sa shi su se so za ji zu ze zo ta chi tsu te to da ji zu de do na ni nu ne no ha hi fu he ho ba bi bu be bo pa pi pu pe po ma mi mu me mo ya yu yo ra ri ru re ro wa o n vu".split()))
r=""
for c in kata:
    if c in "ャュョ":
        v={"ャ":"a","ュ":"u","ョ":"o"}[c]; b=r[:-1] if r.endswith("i") else r
        r=b+v if b.endswith(("sh","ch","j")) else b+"y"+v
    elif c in "ァィゥェォ":
        v="aiueo"["ァィゥェォ".index(c)]; r=(r[:-1] if r and r[-1] in "aiueo" and len(r)>1 else ("w" if r.endswith("u") else r))+v
    elif c in "ッー": pass
    else: r+=K.get(c,"")
def loose(s):
    s="".join(c for c in s.lower() if "a"<=c<="z")
    for a,b in (("chu","tu"),("ou","u"),("ph","f"),("th","s"),("ck","k"),("c","k"),("q","k"),("x","ks"),("l","r"),("v","b")): s=s.replace(a,b)
    o=""
    for i,c in enumerate(s):
        if c=="h" and o and o[-1] not in "aeiou": continue
        if not o or o[-1]!=c: o+=c
    return o
def bones(s): return s[:1]+"".join(c for c in s[1:] if c not in "aeiouy")
def dist(a,b):
    d=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        p,d[0]=d[0],i
        for j,y in enumerate(b,1): p,d[j]=d[j],min(d[j]+1,d[j-1]+1,p+(x!=y))
    return d[len(b)]
w=loose(r); best=None
for a in apps:
  # the whole name, and each word of it (a name of several words is often said by one of them)
  for f in [a["label"]]+a["label"].split():
    l=loose(f)
    if len(l)<2 or not w: continue
    s=dist(w,l)/max(len(w),len(l))
    if s<=(0.5 if bones(w)==bones(l) else 0.25) and (best is None or s<best[0]): best=(s,a["package"])
print(best[1] if best else "")' "${d}ARG" "${d}{W_app_names-}" "${d}1" 2>/dev/null
}

if match open; then
  # nickname or name -> a kind of app (browser, maps ...) -> part of the name -> the reading
  APPS=${d}(z2-app list 2>/dev/null)
  pkg=${d}(find_app name)
  if [ -z "${d}pkg" ] && open_kind "${d}ARG"; then exit 0; fi
  [ -n "${d}pkg" ] || pkg=${d}(find_app rest)
  if [ -n "${d}pkg" ]; then
    z2-intent -p "${d}pkg" >/dev/null 2>&1; log "open ${d}ARG (${d}pkg)"
  else
    say say_open_missing name "${d}ARG"; log "open: '${d}ARG' not found"
  fi
  exit 0
fi

# --- Anything else: just say what was heard (AI only with the cue word) ---
z2-notify -n voice-ai "${d}W_title_app" "${d}(msg msg_unknown text "${d}text")"
log "unmatched: ${d}text"
"""

/** 同梱の言葉の表を返す関数 `bundled_words <言語>` (sh)。⚠ 英語は `*)` なので必ず最後。 */
private fun voiceWordTables(): String = buildString {
    appendLine("bundled_words() {")
    appendLine("  case ${'$'}1 in")
    for ((code, table) in VOICE_WORDS) {
        appendLine("    ${if (code == "en") "*" else code}) cat <<'VOICE_WORDS'")
        appendLine(table)
        appendLine("VOICE_WORDS")
        appendLine("      ;;")
    }
    appendLine("  esac")
    append("}")
}

/**
 * 言葉の表 (言語コード to 表)。英語と日本語はここ、⭐ **それ以外の言語は言語ファイル**
 * (`proot/lang/Cli<言語>.kt` の `voiceWords`) にある ([CliLanguage])。
 * ⚠ 英語は `*)` になるので必ず最後。表の無い言語は英語の表で動く。
 */
private val VOICE_WORDS: List<Pair<String, String>> by lazy {
    listOf("ja" to VOICE_WORDS_JA) +
        CliLanguages.all.mapNotNull { l -> l.voiceWords?.let { l.code to it } } +
        listOf("en" to VOICE_WORDS_EN)
}

private const val VOICE_WORDS_JA = """# 声で操作 — 言葉の表 (日本語)。voice.sh が読む。ここを書き換えれば言い方を足せる・変えられる。
#
# 書き方:
#   キー = 言い方|別の言い方|…        「|」で区切って並べる。上から順に見る
#   * を 1 つ入れると、そこに入った言葉を受け取る (例: *を開いて → 「地図を開いて」の「地図」)
#   キー =                            右を空にすると、その言葉では動かなくなる
#   空白と「。、」は比べるときに無視する。英字の大文字と小文字は区別しない
#   この表に無いキーは、同梱の表の値が使われる (行を消すと元の言い方に戻る)
#   say_ / msg_ / fmt_ / title_ で始まる行は、話す・見せる文。{h} のような {…} に値が入る
#
# 試すには (話さずに): sh ~/.z2term/macros/voice.sh try 次の曲
# 決まった言葉より先に見る「自分で決めた言葉 = コマンド」は ~/.z2term/voice/commands.txt に書く。

# --- 聞き取る言葉 ---
ai = aiに聞いて*|aiで*|aiに*|ai*|ａｉ*|エーアイに聞いて*|エーアイ*|えーあい*|質問*
media_next = 次の曲|次|スキップ|曲を飛ばして
media_previous = 前の曲|前|戻して
media_pause = 止めて|停止|ストップ|一時停止|ポーズ|音楽を止めて
media_play = 再生|続けて|再開|音楽をかけて
volume_up = 音量を上げて|音量上げて|ボリュームを上げて|大きくして
volume_down = 音量を下げて|音量下げて|ボリュームを下げて|小さくして
volume_mute = 消音|ミュート|音を消して
volume_unmute = 消音解除|ミュート解除|音を戻して|音を出して
time = 今何時|何時|時間|時刻|今の時間|今何時ですか
date = 今日は何日|今日何日|今日の日付|日付|何曜日|今日は何曜日|今日何曜日
battery = 電池|バッテリー|電池残量|バッテリー残量|電池は|充電は
steps = 歩数|今日の歩数|何歩|今日何歩|何歩歩いた|今日は何歩|今日何歩歩いた|歩数は|今日の歩数は
torch_on = ライトをつけて|ライトつけて|ライトオン|懐中電灯|懐中電灯をつけて|電気つけて
torch_off = ライトを消して|ライト消して|ライトオフ|懐中電灯を消して|電気消して
screenshot = スクショ|スクリーンショット|画面を撮って
home = ホーム|ホームに戻って|ホーム画面
news = ニュース|ニュースを読んで|ニュース読んで|新着|新着を読んで|rss|rssを読んで|今日のニュース
weather = 天気|今日の天気|天気は|天気を教えて|今日の天気は
weather_tomorrow = 明日の天気|明日の天気は|明日の天気を教えて
memo_read = メモを読んで|メモ読んで|メモを見せて|メモは
memo_add = メモ*|*をメモして|*をメモ|*ってメモして|*ってメモ|*とメモして|*とメモ
remind_list = リマインド一覧|リマインダー一覧|予定|予定は|予定を教えて|予定を読んで|今日の予定
remind = *のをリマインドして|*のをリマインド|*をリマインドして|*をリマインド|*ってリマインドして|*ってリマインド|*とリマインド|*リマインドして|*リマインド|リマインドして*|リマインド*|*を思い出させて|*って思い出させて|*思い出させて
wake = *に起こして|*に起こしてください|*に起こしてね
help = 何ができる|何ができますか|ヘルプ|使い方|できること
timer = *のタイマーをセットして|*のタイマー|*タイマー|タイマー*|*測って|*計って|*はかって
navi = *までナビして|*までナビ|*へナビ|*にナビ|ナビで*|*までの行き方|*への行き方|*に行きたい|*へ行きたい|*までの道|*まで案内して
navi_ask = ナビ|ナビして|ナビを始めて|道案内|道案内して
place_phone = *の電話番号を調べて|*の電話番号|*の番号を調べて
call = *さんに電話して|*さんに電話|*に電話して|*に電話|*へ電話
play = *を再生して|*を再生|*をかけて|*を流して
open = *を開いて|*開いて|*を起動して|*を起動|*起動
# 種類の言葉で開く (端末の既定のアプリ)。「ブラウザを開いて」「地図開いて」
open_browser = ブラウザ|ウェブ|インターネット
open_maps = 地図|マップ
open_mail = メール
open_camera = カメラ
open_settings = 設定
open_calendar = カレンダー
open_music = 音楽|ミュージック
open_gallery = 写真|ギャラリー|アルバム
open_contacts = 連絡先|電話帳
open_dialer = 電話
open_messages = メッセージ|sms
open_calculator = 電卓|計算機
# アプリの呼び名。名前が英字などで、言っても開けないときに書く: 呼び名:アプリの名前|呼び名:アプリの名前
app_names =

# 数の入った音量 (「音量3段階下げて」「音量を10にして」「音量50%」「音量最大」) — 含まれていれば当たる
volume_word = 音量|ボリューム
volume_up_word = 上げ|あげ|大きく
volume_down_word = 下げ|さげ|小さく
volume_max_word = 最大|マックス|いっぱい
volume_min_word = 最小|ゼロ
volume_percent_word = %|パーセント
volume_mute_word = 消して|ミュート|消音
volume_unmute_word = 解除

# 時間の長さと時刻 (タイマー・リマインド・アラーム)
unit_hour = 時間
unit_minute = 分
unit_second = 秒
unit_day = 日
when_before =
when_after = 後に|後|経ったら
clock_at =
clock_hour = 時
clock_minute = 分
clock_half = 半
clock_am = 朝|午前
clock_pm = 午後|夜|夕方|晩
day_today = 今日
day_tomorrow = 明日|あした
day_after_tomorrow = 明後日|あさって
day_daily = 毎日
day_weekdays = 平日|毎週平日
day_every = 毎週
wd_mon = 月曜日|月曜
wd_tue = 火曜日|火曜
wd_wed = 水曜日|水曜
wd_thu = 木曜日|木曜
wd_fri = 金曜日|金曜
wd_sat = 土曜日|土曜
wd_sun = 日曜日|日曜
# いつの部分を抜いたあと、本文との間に残るつなぎの言葉
remind_filler = の|に|を|は|で|って|と

# --- 話す・見せる文 ---
fmt_spaces = no
fmt_sentence_end = 。
title_app = 声で操作
title_navi = ナビ
title_ai = AI
title_timer = タイマー
title_news = ニュース
title_memo = メモ
title_remind = 予定
title_call = 電話
title_help = 声で言えること
title_rule_voice = 声で操作
title_rule_voice_locked = 声で操作（ロック中）
title_rule_timer = 声のタイマー
title_rule_steps = 歩数を数える（声で操作用）
fmt_clock = {h}時{m}分
fmt_clock_oclock = {h}時
fmt_weekdays = 月|火|水|木|金|土|日
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = {clock}です
say_date = {month}月{day}日、{weekday}曜日です
say_battery = 電池は{n}パーセントです。{charging}
say_charging = 充電中です
say_steps = 今日は{n}歩です
say_steps_none = 歩数がまだ記録されていません。身体活動の許可と、歩数のルールが要ります
say_news = 新着のニュースです。{titles}
say_news_none = 新着のニュースはありません
fmt_weather_lang = ja
say_weather = 今の天気は{desc}、気温{temp}度です
say_weather_tomorrow = 明日は{desc}、最高{hi}度、最低{lo}度、雨の確率は{rain}パーセントです
say_weather_fail = 天気を取れませんでした
say_memo_saved = メモしました
say_memo_recent = 最近のメモです。{memos}
say_memo_none = メモはありません
say_remind_ok = リマインドを登録しました。{res}
say_remind_fail = 登録できませんでした。{res}
say_remind_unclear = いつ、何をリマインドするか聞き取れませんでした。例えば、明日の朝7時にゴミ出しをリマインド、と言ってください
say_remind_missing = リマインドのマクロが入っていません。端末で z2-macro install remind を実行してください
fmt_wake_label = 起きる時間
say_wake = 起きる時間です
say_wake_set = {clock}に起こします
say_wake_unclear = 起こす時刻が聞き取れませんでした
fmt_unit_hour = 時間
fmt_unit_minute = 分
fmt_unit_second = 秒
fmt_timer_label = {n}{unit}
say_timer_set = {label}のタイマーをセットしました
say_timer_done = {label}たちました
say_timer_unclear = タイマーの長さが聞き取れませんでした
say_call_missing = {who}さんの番号が見つかりませんでした
msg_call_missing = 「{who}」が {file} にありません。1 行に「名前 番号」で書いてください。
say_open_missing = {name}というアプリが見つかりませんでした
say_help = 言えることを通知に出しました
say_locked = ロックを解除してください
msg_help = 時刻・日付・電池・歩数 / 音量3段階下げて・音量50% / 次の曲・止めて / 3分のタイマー・7時に起こして / 明日の朝7時にゴミ出しをリマインド・予定は / ニュース・天気・明日の天気 / メモ牛乳を買う・メモを読んで / ライトをつけて・スクショ / ◯◯を開いて / ◯◯に電話 / ◯◯までナビ・◯◯の電話番号 / AI 〜 (使うには voice.sh ai) / 言葉を変える: voice.sh words
msg_unknown = わからない言葉でした: {text}
msg_locked = 「{text}」はロックを解除すると続けます
msg_navi_where = 行き先を話してください
msg_navi_hint = 行き先（場所の名前）
msg_ai_off = AI はオフです。コピーを押して端末に貼ると {ai} を使うようになります (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI はオフです。codex / claude / agy のどれかを入れてログインしてから、端末で sh ~/.z2term/macros/voice.sh ai codex のように選んでください
msg_ai_missing = {ai} が見つかりません。入れてログインするか、sh ~/.z2term/macros/voice.sh ai で別の AI を選んでください
msg_ai_asking = AI ({ai}) に聞いています…
msg_ai_noanswer = 答えを受け取れませんでした (記録: {log})
msg_ai_usage = ai: off | codex | claude | agy のどれかを指定してください
msg_ai_how = AI はオフです。使うには: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy。合図の言葉「AI」「質問」で始めたときだけ聞きます)。モデルは sh ~/.z2term/macros/voice.sh model <名前> (空でその AI の既定)
fmt_ai_prompt = (答えは音声で読み上げます。コマンドやファイルは使わずに、日本語で 2〜3 文、記号や箇条書きを使わずに答えてください)
msg_setup_done = 声で操作のルールを登録しました (既にあるものは足していません):
msg_setup_steps = 歩数を数えるルール (steps:reach=1000000) は、今日の歩数を声で聞けるように歩数計を動かすだけです。100 万歩には届かない前提の条件で、何も実行しません。
msg_words_file = 言葉の表: {file}  (書き換えると言い方を足せます。sh ~/.z2term/macros/voice.sh words で開く)"""

private const val VOICE_WORDS_EN = """# Voice command — word table (English). voice.sh reads it. Edit it to add or change what you can say.
#
# How to write it:
#   key = one way|another way|...     separate with "|"; checked from the top
#   put one * in to take the words there (open * -> "open maps" gives "maps")
#   *X* takes the words on both sides of X
#   key =                             nothing on the right turns that one off
#   Punctuation is ignored, and so are upper/lower case and extra spaces
#   A key missing here uses the bundled value (delete a line to get the original back)
#   Lines starting with say_ / msg_ / fmt_ / title_ are what is spoken or shown; {…} gets a value
#
# Try one without speaking: sh ~/.z2term/macros/voice.sh try next song
# "your own words = a command" go in ~/.z2term/voice/commands.txt (checked before this table).

# --- Words to listen for ---
ai = ask ai *|hey ai *|ai *|question *
media_next = next song|next track|next|skip|skip this song|skip song
media_previous = previous song|previous track|previous|last song|go back
media_pause = pause|stop|stop the music|pause the music|pause music|stop music
media_play = play|resume|continue|play music|resume music|resume the music
volume_up = volume up|turn it up|turn up the volume|turn the volume up|louder|raise the volume
volume_down = volume down|turn it down|turn down the volume|turn the volume down|quieter|lower the volume
volume_mute = mute|mute the volume|silence
volume_unmute = unmute|unmute the volume|sound on
time = what time is it|what's the time|what is the time|time|the time|current time|what time
date = what's the date|what is the date|what day is it|what day is today|today's date|date|what's today
battery = battery|battery level|how much battery|how much battery is left|battery status|how's the battery
steps = steps|how many steps|step count|my steps|how many steps today|steps today|how many steps have i walked
torch_on = flashlight on|turn on the flashlight|torch on|turn on the torch|light on|turn on the light|flashlight
torch_off = flashlight off|turn off the flashlight|torch off|turn off the torch|light off|turn off the light
screenshot = screenshot|take a screenshot|capture the screen
home = home|go home|home screen
news = news|read the news|what's the news|today's news|headlines|rss
weather = weather|what's the weather|how's the weather|weather today|today's weather|what's the weather like
weather_tomorrow = weather tomorrow|tomorrow's weather|what's the weather tomorrow|how's the weather tomorrow|what's the weather like tomorrow
memo_read = read my notes|read notes|my notes|show my notes|read the notes
memo_add = take a note that *|take a note *|make a note that *|make a note *|note that *|note *|memo *|write down *
remind_list = reminders|my reminders|list reminders|show reminders|what are my reminders|what's on my schedule|my schedule
remind = remind me to *|remind me that *|remind me about *|remind me *|set a reminder to *|set a reminder for *|reminder *
wake = wake me up at *|wake me at *|wake me up *|set an alarm for *|set alarm for *|alarm at *|alarm for *
help = what can i say|what can you do|help|commands
timer = set a timer for *|set timer for *|start a timer for *|timer for *|set a * timer|start a * timer|a * timer|* timer
navi = navigate to *|directions to *|take me to *|how do i get to *|i want to go to *|navigate *
navi_ask = navigate|navigation|directions|start navigation
place_phone = what is the phone number of *|the phone number of *|phone number of *|look up the number of *|* phone number
call = call *|phone *|dial *
play = play *|put on *
open = open *|launch *|start *
# Open by kind (the phone's default app). E.g. open the browser
open_browser = browser|the browser|web browser|internet
open_maps = maps|map|the map
open_mail = mail|email|the mail
open_camera = camera|the camera
open_settings = settings|the settings
open_calendar = calendar|the calendar
open_music = music|the music
open_gallery = photos|gallery|the gallery
open_contacts = contacts|the contacts
open_dialer = phone|dialer|the phone
open_messages = messages|the messages|sms
open_calculator = calculator|the calculator
# Nicknames for apps. When saying the name does not open it, write: nickname:app name|nickname:app name
app_names =

# Volume with a number ("volume down 3", "volume 10", "volume 50 percent", "volume max") — matches when contained
volume_word = volume
volume_up_word = up|louder|raise|increase
volume_down_word = down|lower|quieter|decrease|reduce
volume_max_word = max|maximum|full
volume_min_word = minimum|zero
volume_percent_word = %|percent
volume_mute_word = mute|off
volume_unmute_word = unmute

# Lengths of time and times of day (timer, reminder, alarm)
unit_hour = hours|hour|hrs|hr
unit_minute = minutes|minute|mins|min
unit_second = seconds|second|secs|sec
unit_day = days|day
when_before = in|after
when_after = from now|later
clock_at = at|by|around
clock_hour = o'clock|oclock
clock_minute =
clock_half =
clock_am = am|a m|in the morning
clock_pm = pm|p m|in the evening|in the afternoon|at night|tonight
day_today = today
day_tomorrow = tomorrow
day_after_tomorrow = the day after tomorrow|day after tomorrow
day_daily = every day|daily|everyday
day_weekdays = every weekday|on weekdays|weekdays
day_every = every|each
wd_mon = monday
wd_tue = tuesday
wd_wed = wednesday
wd_thu = thursday
wd_fri = friday
wd_sat = saturday
wd_sun = sunday
# Small words left between the text and the parts taken out
remind_filler = to|at|on|in|about|that|for|me|by|the

# --- What is spoken or shown ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Voice command
title_navi = Navigation
title_ai = AI
title_timer = Timer
title_news = News
title_memo = Notes
title_remind = Reminders
title_call = Call
title_help = What you can say
title_rule_voice = Voice command
title_rule_voice_locked = Voice command (locked)
title_rule_timer = Voice timer
title_rule_steps = Count steps (for voice command)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} o'clock
fmt_weekdays = Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday
fmt_months = January|February|March|April|May|June|July|August|September|October|November|December
say_time = It's {clock}
say_date = Today is {weekday}, {month_name} {day}
say_battery = The battery is at {n} percent. {charging}
say_charging = It is charging.
say_steps = You have walked {n} steps today
say_steps_none = No steps are recorded yet. It needs the Physical activity permission and a steps rule
say_news = Here are the latest headlines. {titles}
say_news_none = There is no new news
fmt_weather_lang = en
say_weather = Right now it's {desc}, {temp} degrees
say_weather_tomorrow = Tomorrow: {desc}, a high of {hi} and a low of {lo} degrees, {rain} percent chance of rain
say_weather_fail = I could not get the weather
say_memo_saved = Noted
say_memo_recent = Your latest notes. {memos}
say_memo_none = There are no notes
say_remind_ok = Reminder set. {res}
say_remind_fail = I could not set it. {res}
say_remind_unclear = I did not catch when or what to remind you about. Try: remind me to take out the bins tomorrow at 7 am
say_remind_missing = The reminder macro is not installed. Run z2-macro install remind in a terminal
fmt_wake_label = wake up
say_wake = Time to wake up
say_wake_set = I will wake you at {clock}
say_wake_unclear = I did not catch the time to wake you
fmt_unit_hour = hours
fmt_unit_minute = minutes
fmt_unit_second = seconds
fmt_timer_label = {n} {unit}
say_timer_set = Timer set for {label}
say_timer_done = {label} is up
say_timer_unclear = I did not catch how long the timer should be
say_call_missing = I could not find a number for {who}
msg_call_missing = "{who}" is not in {file}. Write one "name number" per line.
say_open_missing = I could not find an app called {name}
say_help = What you can say is in a notification
say_locked = Please unlock the phone
msg_help = time, date, battery, steps / volume down 3, volume 50 percent / next song, pause / 3 minute timer, wake me up at 7 / remind me to take out the bins tomorrow at 7 am, reminders / news, weather, weather tomorrow / note buy milk, read my notes / flashlight on, screenshot / open … / call … / navigate to …, phone number of … / AI … (turn on with voice.sh ai) / change the words: voice.sh words
msg_unknown = I did not understand: {text}
msg_locked = "{text}" will go on once you unlock the phone
msg_navi_where = Say where you want to go
msg_navi_hint = Where to (a place name)
msg_ai_off = AI is off. Press Copy and paste it into a terminal to use {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI is off. Install and sign in to one of codex / claude / agy, then choose it in a terminal, e.g. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} was not found. Install it and sign in, or choose another with sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Asking AI ({ai})…
msg_ai_noanswer = No answer came back (log: {log})
msg_ai_usage = ai: give one of off | codex | claude | agy
msg_ai_how = AI is off. To use it: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; it is only asked when you start with the cue word, "AI" or "question"). Model: sh ~/.z2term/macros/voice.sh model <name> (empty = that AI's default)
fmt_ai_prompt = (The answer will be read aloud. Without using commands or files, answer in English in 2 or 3 sentences, with no symbols or bullet lists.)
msg_setup_done = Voice command rules registered (ones already there were not added again):
msg_setup_steps = The Count steps rule (steps:reach=1000000) only keeps the step counter running so you can ask for today's steps. That goal is never reached, so it runs nothing.
msg_words_file = Word table: {file}  (edit it to add ways to say things; sh ~/.z2term/macros/voice.sh words opens it)"""
