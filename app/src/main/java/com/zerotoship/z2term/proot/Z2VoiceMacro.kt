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
    val head = t(
        en = """# voice.sh — the receiver for "Voice command": what you say runs music, volume, timers, reminders, weather and more.
#
# Setup (once):
#   sh ~/.z2term/macros/voice.sh setup    register 3 rules (voice command / voice timer / count steps)
#   then speak through the "Voice command" quick settings tile or the digital assistant (long-press power etc.)
#
# From a terminal:
#   voice.sh words                 open the word table (~/.z2term/voice/words-<lang>.txt; add or change phrases)
#   voice.sh try <phrase>          try one without speaking (e.g. voice.sh try next song)
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
#   sh ~/.z2term/macros/voice.sh setup    ルール 3 本を登録 (声で操作 / 声のタイマー / 歩数を数える)
#   あとはクイック設定のタイル「声で操作」か、デジタル アシスタント (電源ボタン長押しなど) で話す
#
# 端末から:
#   voice.sh words                 言葉の表を開く (~/.z2term/voice/words-<言語>.txt。言い方を足す・変える)
#   voice.sh try <言葉>            話さずに試す (例: voice.sh try 次の曲)
#   voice.sh lang [言語]           言葉の表の言語 (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   AI を使うか (最初は off)
#   voice.sh model [名前]          AI のモデル (空でその AI の既定)
#   voice.sh setup / help
#
# AI は最初はオフ。オンにしても、合図の言葉 (「AI」「質問」など) で始めたときだけ聞く。
# 天気は「天気」と言ったときだけ wttr.in に問い合わせる (場所は ~/.z2term/voice/config の weather_city=)。
# 自分で決めた言葉: ~/.z2term/voice/commands.txt に「言葉 = コマンド」 (言葉の表より先に見る)
# 電話の名前: ~/.z2term/voice/contacts.txt に「名前 番号」 / 動いた記録: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        "zh-CN" to """# voice.sh — "语音操作"的接收端。用说出的话来控制音乐、音量、计时器、提醒、天气等。
#
# 准备 (只做一次):
#   sh ~/.z2term/macros/voice.sh setup    登记 3 条规则 (语音操作 / 语音计时器 / 计步)
#   之后用快捷设置磁贴"语音操作"或数字助理 (长按电源键等) 说话
#
# 在终端里:
#   voice.sh words                 打开词表 (~/.z2term/voice/words-<语言>.txt。增加或更换说法)
#   voice.sh try <话>              不说话也能试 (例: voice.sh try 下一首)
#   voice.sh lang [语言]           词表的语言 (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   是否使用 AI (一开始是 off)
#   voice.sh model [名字]          AI 的模型 (留空 = 该 AI 的默认)
#   voice.sh setup / help
#
# AI 一开始是关闭的。即使打开，也只有用提示词 ("AI"、"提问" 等) 开头时才会问。
# 天气只在说"天气"时向 wttr.in 查询 (地点: ~/.z2term/voice/config 的 weather_city=)。
# 自己定的话: 在 ~/.z2term/voice/commands.txt 写"话 = 命令" (比词表先看)
# 要打电话的名字: 在 ~/.z2term/voice/contacts.txt 写"名字 号码" / 记录: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        "zh-TW" to """# voice.sh — "語音操作"的接收端。用說出的話來控制音樂、音量、計時器、提醒、天氣等。
#
# 準備 (只做一次):
#   sh ~/.z2term/macros/voice.sh setup    登記 3 條規則 (語音操作 / 語音計時器 / 計步)
#   之後用快速設定圖塊"語音操作"或數位助理 (長按電源鍵等) 說話
#
# 在終端機裡:
#   voice.sh words                 開啟詞表 (~/.z2term/voice/words-<語言>.txt。增加或更換說法)
#   voice.sh try <話>              不說話也能試 (例: voice.sh try 下一首)
#   voice.sh lang [語言]           詞表的語言 (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   是否使用 AI (一開始是 off)
#   voice.sh model [名字]          AI 的模型 (留空 = 該 AI 的預設)
#   voice.sh setup / help
#
# AI 一開始是關閉的。即使開啟，也只有用提示詞 ("AI"、"提問" 等) 開頭時才會問。
# 天氣只在說"天氣"時向 wttr.in 查詢 (地點: ~/.z2term/voice/config 的 weather_city=)。
# 自己定的話: 在 ~/.z2term/voice/commands.txt 寫"話 = 命令" (比詞表先看)
# 要打電話的名字: 在 ~/.z2term/voice/contacts.txt 寫"名字 號碼" / 記錄: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        "es" to """# voice.sh — recibe las «Órdenes por voz»: lo que dices maneja música, volumen, temporizadores, recordatorios, el tiempo y más.
#
# Preparación (una vez):
#   sh ~/.z2term/macros/voice.sh setup    registra 3 reglas (órdenes por voz / temporizador por voz / contar pasos)
#   luego habla con el icono de ajustes rápidos «Órdenes por voz» o con el asistente digital (mantener el botón de encendido, etc.)
#
# Desde una terminal:
#   voice.sh words                 abre la tabla de palabras (~/.z2term/voice/words-<idioma>.txt; añade o cambia frases)
#   voice.sh try <frase>           prueba una sin hablar (p. ej. voice.sh try siguiente canción)
#   voice.sh lang [idioma]         idioma de la tabla de palabras (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   si se usa la IA (desactivada al principio)
#   voice.sh model [nombre]        el modelo de la IA (vacío = el de esa IA por defecto)
#   voice.sh setup / help
#
# La IA empieza desactivada. Aun activada, solo se le pregunta si empiezas con la palabra clave («IA», «pregunta»...).
# El tiempo consulta wttr.in solo cuando dices «tiempo» (lugar: weather_city= en ~/.z2term/voice/config).
# Tus propias palabras: «palabras = orden» en ~/.z2term/voice/commands.txt (se miran antes que la tabla)
# Nombres para llamar: «nombre número» en ~/.z2term/voice/contacts.txt / registro: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        "ko" to """# voice.sh — "음성 조작"을 받는 곳. 말한 내용으로 음악·볼륨·타이머·리마인더·날씨 등을 움직입니다.
#
# 준비 (한 번만):
#   sh ~/.z2term/macros/voice.sh setup    규칙 3개를 등록 (음성 조작 / 음성 타이머 / 걸음 수 세기)
#   그다음 빠른 설정 타일 "음성 조작"이나 디지털 어시스턴트(전원 버튼 길게 누르기 등)로 말합니다
#
# 터미널에서:
#   voice.sh words                 낱말 표 열기 (~/.z2term/voice/words-<언어>.txt. 말하는 법을 더하거나 바꿈)
#   voice.sh try <말>              말하지 않고 시험 (예: voice.sh try 다음 곡)
#   voice.sh lang [언어]           낱말 표의 언어 (en / ja / zh-CN / zh-TW / es / ko)
#   voice.sh ai [off|codex|claude|agy]   AI를 쓸지 (처음에는 off)
#   voice.sh model [이름]          AI의 모델 (비우면 그 AI의 기본값)
#   voice.sh setup / help
#
# AI는 처음에 꺼져 있습니다. 켜도 신호 말("AI", "질문" 등)로 시작할 때만 묻습니다.
# 날씨는 "날씨"라고 말할 때만 wttr.in에 묻습니다 (장소: ~/.z2term/voice/config 의 weather_city=).
# 내가 정한 말: ~/.z2term/voice/commands.txt 에 "말 = 명령" (낱말 표보다 먼저 봅니다)
# 전화할 이름: ~/.z2term/voice/contacts.txt 에 "이름 번호" / 기록: ~/.z2term/voice/log.txt
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
  has_rule "event:voice" ||
    z2-when event:voice name="${d}W_title_rule_voice" run "sh ${d}SELF" >/dev/null || return 1
  has_rule "event:alarm" ||
    z2-when event:alarm name="${d}W_title_rule_timer" run "sh ${d}SELF" >/dev/null
  # Only there to keep the step counter running for "how many steps today" (it never fires).
  has_rule "steps:" ||
    z2-when steps:reach=1000000 name="${d}W_title_rule_steps" run "sh ${d}SELF steps-keep" >/dev/null
  ensure_words
  msg msg_setup_done; echo
  z2-when list | grep -F "macros/voice.sh" | cut -f1,3
  echo
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

private val VOICE_WORDS: List<Pair<String, String>> = listOf(
    "ja" to VOICE_WORDS_JA,
    "zh-CN" to VOICE_WORDS_ZH_CN,
    "zh-TW" to VOICE_WORDS_ZH_TW,
    "es" to VOICE_WORDS_ES,
    "ko" to VOICE_WORDS_KO,
    "en" to VOICE_WORDS_EN,
)

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
msg_help = 時刻・日付・電池・歩数 / 音量3段階下げて・音量50% / 次の曲・止めて / 3分のタイマー・7時に起こして / 明日の朝7時にゴミ出しをリマインド・予定は / ニュース・天気・明日の天気 / メモ牛乳を買う・メモを読んで / ライトをつけて・スクショ / ◯◯を開いて / ◯◯に電話 / ◯◯までナビ・◯◯の電話番号 / AI 〜 (使うには voice.sh ai) / 言葉を変える: voice.sh words
msg_unknown = わからない言葉でした: {text}
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
msg_words_file = 言葉の表: {file}  (書き換えると言い方を足せます。sh ~/.z2term/macros/voice.sh words で開く)"""

private const val VOICE_WORDS_ZH_CN = """# 语音操作 — 词表 (简体中文)。由 voice.sh 读取。改写这里就能增加或更换说法。
#
# 写法:
#   键 = 说法|另一种说法|…            用"|"隔开，从上往下看
#   放一个 * 就能取到那个位置的话 (打开* -> "打开地图" 取到 "地图")
#   *X* 取 X 两边的话 (*提醒我* -> "明天7点提醒我倒垃圾" 取到 "明天7点 倒垃圾")
#   键 =                              右边留空，这个说法就不再起作用
#   比较时忽略标点、空格和英文大小写
#   这里没有的键，使用自带词表的值 (删掉一行就回到原来的说法)
#   以 say_ / msg_ / fmt_ / title_ 开头的行是要说出或显示的句子，{…} 里会填入值
#
# 不说话也能试: sh ~/.z2term/macros/voice.sh try 下一首
# "自己定的话 = 命令" 写在 ~/.z2term/voice/commands.txt (比这个词表先看)。

# --- 听取的话 ---
ai = 问ai*|问问ai*|ai*|提问*|问一下*
media_next = 下一首|下一曲|下一个|跳过|切歌|下一首歌
media_previous = 上一首|上一曲|上一个|上一首歌
media_pause = 暂停|停止|停|别放了|暂停音乐|停止播放
media_play = 播放|继续|继续播放|放音乐|播放音乐
volume_up = 调高音量|音量调高|音量调大|调大音量|大声点|声音大一点|音量大一点|大声一点
volume_down = 调低音量|音量调低|音量调小|调小音量|小声点|声音小一点|音量小一点|小声一点
volume_mute = 静音|关掉声音
volume_unmute = 取消静音|打开声音|恢复声音
time = 现在几点|几点了|现在几点了|时间|现在时间|几点|现在是几点
date = 今天几号|今天是几号|今天星期几|星期几|日期|今天的日期|今天是星期几
battery = 电量|电池|还有多少电|电池电量|还剩多少电
steps = 步数|今天的步数|今天走了多少步|走了多少步|我走了多少步
torch_on = 打开手电筒|手电筒|开手电筒|打开闪光灯
torch_off = 关闭手电筒|关手电筒|关掉手电筒|关闭闪光灯
screenshot = 截图|截屏|截个图
home = 主屏幕|回到主屏幕|桌面|回到桌面
news = 新闻|读新闻|今天的新闻|最新消息|念一下新闻|rss
weather = 天气|今天天气|今天的天气|天气怎么样|今天天气怎么样
weather_tomorrow = 明天天气|明天的天气|明天天气怎么样|明天的天气怎么样
memo_read = 读备忘录|读一下备忘|看备忘录|我的备忘|念一下备忘
memo_add = 备忘*|记一下*|记下*|*记下来
remind_list = 提醒列表|我的提醒|有什么提醒|日程|今天的日程|有哪些提醒
remind = 提醒我*|*提醒我*|*提醒我|设置提醒*|设个提醒*
wake = *叫醒我|*叫我起床|*点叫我|把闹钟定在*|闹钟定在*
help = 你能做什么|我能说什么|帮助|能做什么
timer = 设一个*的计时器|设个*的计时器|*的计时器|*计时器|*计时|计时*|倒计时*|定时*
navi = 导航到*|导航去*|去*怎么走|带我去*|我想去*|到*的路线
navi_ask = 导航|开始导航|带路
place_phone = 查一下*的电话号码|*的电话号码|查一下*的电话
call = 给*打电话|打电话给*|打给*|呼叫*
play = 播放*|放一首*|我想听*|来一首*
open = 打开*|启动*|开启*
# 用类别名打开 (设备的默认应用)。例: 打开浏览器
open_browser = 浏览器|网页
open_maps = 地图
open_mail = 邮件|电子邮件
open_camera = 相机|照相机
open_settings = 设置
open_calendar = 日历
open_music = 音乐
open_gallery = 相册|图库|照片
open_contacts = 联系人|通讯录
open_dialer = 电话|拨号
open_messages = 短信|信息
open_calculator = 计算器
# 应用的别名。说了也打不开时写上: 别名:应用名称|别名:应用名称
app_names =

# 带数字的音量 ("音量调低3格"、"音量调到10"、"音量百分之五十"、"音量最大") — 只要包含就算
volume_word = 音量|声音
volume_up_word = 调高|调大|大|增加
volume_down_word = 调低|调小|小|减少|降低
volume_max_word = 最大
volume_min_word = 最小
volume_percent_word = %|百分之
volume_mute_word = 静音
volume_unmute_word = 取消

# 时间长度和时刻 (计时器·提醒·闹钟)
unit_hour = 个小时|小时|个钟头|钟头
unit_minute = 分钟|分
unit_second = 秒钟|秒
unit_day = 天
when_before =
when_after = 以后|之后|后
clock_at =
clock_hour = 点钟|点
clock_minute = 分
clock_half = 半
clock_am = 早上|上午|凌晨|早晨
clock_pm = 下午|晚上|傍晚|中午
day_today = 今天
day_tomorrow = 明天
day_after_tomorrow = 后天
day_daily = 每天
day_weekdays = 工作日|每个工作日
day_every = 每
wd_mon = 星期一|周一|礼拜一
wd_tue = 星期二|周二|礼拜二
wd_wed = 星期三|周三|礼拜三
wd_thu = 星期四|周四|礼拜四
wd_fri = 星期五|周五|礼拜五
wd_sat = 星期六|周六|礼拜六
wd_sun = 星期日|星期天|周日|礼拜天
# 去掉"什么时候"之后，留在正文旁边的连接词
remind_filler = 的|在|要|去|我|记得

# --- 说出或显示的句子 ---
fmt_spaces = no
fmt_sentence_end = 。
title_app = 语音操作
title_navi = 导航
title_ai = AI
title_timer = 计时器
title_news = 新闻
title_memo = 备忘
title_remind = 提醒
title_call = 电话
title_help = 可以说的话
title_rule_voice = 语音操作
title_rule_timer = 语音计时器
title_rule_steps = 计步 (语音操作用)
fmt_clock = {h}点{m}分
fmt_clock_oclock = {h}点
fmt_weekdays = 一|二|三|四|五|六|日
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = 现在是{clock}
say_date = 今天是{month}月{day}日，星期{weekday}
say_battery = 电量还有百分之{n}。{charging}
say_charging = 正在充电。
say_steps = 今天走了{n}步
say_steps_none = 还没有记录到步数。需要"身体活动"权限和计步规则
say_news = 最新的新闻。{titles}
say_news_none = 没有新的新闻
fmt_weather_lang = zh-cn
say_weather = 现在的天气是{desc}，气温{temp}度
say_weather_tomorrow = 明天{desc}，最高{hi}度，最低{lo}度，降雨概率百分之{rain}
say_weather_fail = 没能获取天气
say_memo_saved = 记下了
say_memo_recent = 最近的备忘。{memos}
say_memo_none = 没有备忘
say_remind_ok = 提醒已设置。{res}
say_remind_fail = 没能设置。{res}
say_remind_unclear = 没听清什么时候提醒什么。比如可以说：明天早上7点提醒我倒垃圾
say_remind_missing = 还没有安装提醒的宏。请在终端里运行 z2-macro install remind
fmt_wake_label = 起床时间
say_wake = 该起床了
say_wake_set = {clock}叫醒你
say_wake_unclear = 没听清几点叫醒你
fmt_unit_hour = 小时
fmt_unit_minute = 分钟
fmt_unit_second = 秒
fmt_timer_label = {n}{unit}
say_timer_set = 已设置{label}的计时器
say_timer_done = {label}到了
say_timer_unclear = 没听清计时器要多长
say_call_missing = 没找到{who}的号码
msg_call_missing = {file} 里没有"{who}"。请每行写一个"名字 号码"。
say_open_missing = 没找到叫{name}的应用
say_help = 已经把可以说的话放到通知里了
msg_help = 时间·日期·电量·步数 / 音量调低3格·音量百分之五十 / 下一首·暂停 / 3分钟的计时器·7点叫醒我 / 明天早上7点提醒我倒垃圾·我的提醒 / 新闻·天气·明天天气 / 记一下买牛奶·读备忘录 / 打开手电筒·截图 / 打开… / 给…打电话 / 导航到…·…的电话号码 / AI … (用 voice.sh ai 开启) / 改说法: voice.sh words
msg_unknown = 没听懂: {text}
msg_navi_where = 请说出目的地
msg_navi_hint = 目的地 (地名)
msg_ai_off = AI 已关闭。按"复制"并粘贴到终端，就会使用 {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI 已关闭。请先安装并登录 codex / claude / agy 中的一个，再在终端里用 sh ~/.z2term/macros/voice.sh ai codex 这样选择
msg_ai_missing = 找不到 {ai}。请安装并登录，或用 sh ~/.z2term/macros/voice.sh ai 选择别的 AI
msg_ai_asking = 正在问 AI ({ai})…
msg_ai_noanswer = 没有收到回答 (记录: {log})
msg_ai_usage = ai: 请指定 off | codex | claude | agy 之一
msg_ai_how = AI 已关闭。要使用: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy。只有用提示词"AI"或"提问"开头时才会问)。模型: sh ~/.z2term/macros/voice.sh model <名字> (留空 = 该 AI 的默认)
fmt_ai_prompt = (回答会被朗读出来。不要使用命令或文件，用中文回答 2 到 3 句，不要用符号或列表。)
msg_setup_done = 已登记语音操作的规则 (已有的没有重复添加):
msg_words_file = 词表: {file}  (改写它就能增加说法。用 sh ~/.z2term/macros/voice.sh words 打开)"""

private const val VOICE_WORDS_ZH_TW = """# 語音操作 — 詞表 (繁體中文)。由 voice.sh 讀取。改寫這裡就能增加或更換說法。
#
# 寫法:
#   鍵 = 說法|另一種說法|…            用"|"隔開，從上往下看
#   放一個 * 就能取到那個位置的話 (打開* -> "打開地圖" 取到 "地圖")
#   *X* 取 X 兩邊的話 (*提醒我* -> "明天7點提醒我倒垃圾" 取到 "明天7點 倒垃圾")
#   鍵 =                              右邊留空，這個說法就不再起作用
#   比較時忽略標點、空格和英文大小寫
#   這裡沒有的鍵，使用內建詞表的值 (刪掉一行就回到原來的說法)
#   以 say_ / msg_ / fmt_ / title_ 開頭的行是要說出或顯示的句子，{…} 裡會填入值
#
# 不說話也能試: sh ~/.z2term/macros/voice.sh try 下一首
# "自己定的話 = 命令" 寫在 ~/.z2term/voice/commands.txt (比這個詞表先看)。

# --- 聽取的話 ---
ai = 問ai*|問問ai*|ai*|提問*|問一下*
media_next = 下一首|下一曲|下一個|跳過|切歌|下一首歌
media_previous = 上一首|上一曲|上一個|上一首歌
media_pause = 暫停|停止|停|別放了|暫停音樂|停止播放
media_play = 播放|繼續|繼續播放|放音樂|播放音樂
volume_up = 調高音量|音量調高|音量調大|調大音量|大聲點|聲音大一點|音量大一點|大聲一點
volume_down = 調低音量|音量調低|音量調小|調小音量|小聲點|聲音小一點|音量小一點|小聲一點
volume_mute = 靜音|關掉聲音
volume_unmute = 取消靜音|打開聲音|恢復聲音
time = 現在幾點|幾點了|現在幾點了|時間|現在時間|幾點|現在是幾點
date = 今天幾號|今天是幾號|今天星期幾|星期幾|日期|今天的日期|今天是星期幾
battery = 電量|電池|還有多少電|電池電量|還剩多少電
steps = 步數|今天的步數|今天走了多少步|走了多少步|我走了多少步
torch_on = 打開手電筒|手電筒|開手電筒|打開閃光燈
torch_off = 關閉手電筒|關手電筒|關掉手電筒|關閉閃光燈
screenshot = 截圖|截螢幕|螢幕截圖
home = 主畫面|回到主畫面|桌面|回到桌面
news = 新聞|讀新聞|今天的新聞|最新消息|念一下新聞|rss
weather = 天氣|今天天氣|今天的天氣|天氣怎麼樣|今天天氣怎麼樣|天氣如何
weather_tomorrow = 明天天氣|明天的天氣|明天天氣怎麼樣|明天的天氣怎麼樣
memo_read = 讀備忘錄|讀一下備忘|看備忘錄|我的備忘|念一下備忘
memo_add = 備忘*|記一下*|記下*|*記下來
remind_list = 提醒清單|我的提醒|有什麼提醒|行程|今天的行程|有哪些提醒
remind = 提醒我*|*提醒我*|*提醒我|設定提醒*|設個提醒*
wake = *叫醒我|*叫我起床|*點叫我|把鬧鐘定在*|鬧鐘定在*
help = 你能做什麼|我能說什麼|說明|能做什麼
timer = 設一個*的計時器|設個*的計時器|*的計時器|*計時器|*計時|計時*|倒數*|定時*
navi = 導航到*|導航去*|去*怎麼走|帶我去*|我想去*|到*的路線
navi_ask = 導航|開始導航|帶路
place_phone = 查一下*的電話號碼|*的電話號碼|查一下*的電話
call = 給*打電話|打電話給*|打給*|撥給*
play = 播放*|放一首*|我想聽*|來一首*
open = 打開*|開啟*|啟動*
# 用類別名稱開啟 (裝置的預設應用程式)。例: 打開瀏覽器
open_browser = 瀏覽器|網頁
open_maps = 地圖
open_mail = 郵件|電子郵件
open_camera = 相機
open_settings = 設定
open_calendar = 日曆
open_music = 音樂
open_gallery = 相簿|圖庫|照片
open_contacts = 聯絡人|通訊錄
open_dialer = 電話|撥號
open_messages = 簡訊|訊息
open_calculator = 計算機
# 應用程式的別名。說了也開不了時寫上: 別名:應用程式名稱|別名:應用程式名稱
app_names =

# 帶數字的音量 ("音量調低3格"、"音量調到10"、"音量百分之五十"、"音量最大") — 只要包含就算
volume_word = 音量|聲音
volume_up_word = 調高|調大|大|增加
volume_down_word = 調低|調小|小|減少|降低
volume_max_word = 最大
volume_min_word = 最小
volume_percent_word = %|百分之
volume_mute_word = 靜音
volume_unmute_word = 取消

# 時間長度和時刻 (計時器·提醒·鬧鐘)
unit_hour = 個小時|小時|個鐘頭|鐘頭
unit_minute = 分鐘|分
unit_second = 秒鐘|秒
unit_day = 天
when_before =
when_after = 以後|之後|後
clock_at =
clock_hour = 點鐘|點
clock_minute = 分
clock_half = 半
clock_am = 早上|上午|凌晨|早晨
clock_pm = 下午|晚上|傍晚|中午
day_today = 今天
day_tomorrow = 明天
day_after_tomorrow = 後天
day_daily = 每天
day_weekdays = 工作日|平日|每個工作日
day_every = 每
wd_mon = 星期一|週一|禮拜一
wd_tue = 星期二|週二|禮拜二
wd_wed = 星期三|週三|禮拜三
wd_thu = 星期四|週四|禮拜四
wd_fri = 星期五|週五|禮拜五
wd_sat = 星期六|週六|禮拜六
wd_sun = 星期日|星期天|週日|禮拜天
# 去掉"什麼時候"之後，留在正文旁邊的連接詞
remind_filler = 的|在|要|去|我|記得

# --- 說出或顯示的句子 ---
fmt_spaces = no
fmt_sentence_end = 。
title_app = 語音操作
title_navi = 導航
title_ai = AI
title_timer = 計時器
title_news = 新聞
title_memo = 備忘
title_remind = 提醒
title_call = 電話
title_help = 可以說的話
title_rule_voice = 語音操作
title_rule_timer = 語音計時器
title_rule_steps = 計步 (語音操作用)
fmt_clock = {h}點{m}分
fmt_clock_oclock = {h}點
fmt_weekdays = 一|二|三|四|五|六|日
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = 現在是{clock}
say_date = 今天是{month}月{day}日，星期{weekday}
say_battery = 電量還有百分之{n}。{charging}
say_charging = 正在充電。
say_steps = 今天走了{n}步
say_steps_none = 還沒有記錄到步數。需要"體能活動"權限和計步規則
say_news = 最新的新聞。{titles}
say_news_none = 沒有新的新聞
fmt_weather_lang = zh-tw
say_weather = 現在的天氣是{desc}，氣溫{temp}度
say_weather_tomorrow = 明天{desc}，最高{hi}度，最低{lo}度，降雨機率百分之{rain}
say_weather_fail = 沒能取得天氣
say_memo_saved = 記下了
say_memo_recent = 最近的備忘。{memos}
say_memo_none = 沒有備忘
say_remind_ok = 提醒已設定。{res}
say_remind_fail = 沒能設定。{res}
say_remind_unclear = 沒聽清楚什麼時候提醒什麼。比如可以說：明天早上7點提醒我倒垃圾
say_remind_missing = 還沒有安裝提醒的巨集。請在終端機裡執行 z2-macro install remind
fmt_wake_label = 起床時間
say_wake = 該起床了
say_wake_set = {clock}叫醒你
say_wake_unclear = 沒聽清楚幾點叫醒你
fmt_unit_hour = 小時
fmt_unit_minute = 分鐘
fmt_unit_second = 秒
fmt_timer_label = {n}{unit}
say_timer_set = 已設定{label}的計時器
say_timer_done = {label}到了
say_timer_unclear = 沒聽清楚計時器要多長
say_call_missing = 沒找到{who}的號碼
msg_call_missing = {file} 裡沒有"{who}"。請每行寫一個"名字 號碼"。
say_open_missing = 沒找到叫{name}的應用程式
say_help = 已經把可以說的話放到通知裡了
msg_help = 時間·日期·電量·步數 / 音量調低3格·音量百分之五十 / 下一首·暫停 / 3分鐘的計時器·7點叫醒我 / 明天早上7點提醒我倒垃圾·我的提醒 / 新聞·天氣·明天天氣 / 記一下買牛奶·讀備忘錄 / 打開手電筒·截圖 / 打開… / 給…打電話 / 導航到…·…的電話號碼 / AI … (用 voice.sh ai 開啟) / 改說法: voice.sh words
msg_unknown = 沒聽懂: {text}
msg_navi_where = 請說出目的地
msg_navi_hint = 目的地 (地名)
msg_ai_off = AI 已關閉。按"複製"並貼到終端機，就會使用 {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI 已關閉。請先安裝並登入 codex / claude / agy 其中之一，再在終端機裡用 sh ~/.z2term/macros/voice.sh ai codex 這樣選擇
msg_ai_missing = 找不到 {ai}。請安裝並登入，或用 sh ~/.z2term/macros/voice.sh ai 選擇別的 AI
msg_ai_asking = 正在問 AI ({ai})…
msg_ai_noanswer = 沒有收到回答 (記錄: {log})
msg_ai_usage = ai: 請指定 off | codex | claude | agy 之一
msg_ai_how = AI 已關閉。要使用: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy。只有用提示詞"AI"或"提問"開頭時才會問)。模型: sh ~/.z2term/macros/voice.sh model <名字> (留空 = 該 AI 的預設)
fmt_ai_prompt = (回答會被朗讀出來。不要使用命令或檔案，用中文回答 2 到 3 句，不要用符號或清單。)
msg_setup_done = 已登記語音操作的規則 (已有的沒有重複新增):
msg_words_file = 詞表: {file}  (改寫它就能增加說法。用 sh ~/.z2term/macros/voice.sh words 開啟)"""

private const val VOICE_WORDS_ES = """# Órdenes por voz — tabla de palabras (español). La lee voice.sh. Edítala para añadir o cambiar lo que puedes decir.
#
# Cómo se escribe:
#   clave = una forma|otra forma|...   separadas por "|"; se miran de arriba abajo
#   pon un * para recoger las palabras de ese sitio (abre * -> "abre mapas" da "mapas")
#   *X* recoge las palabras a los dos lados de X
#   clave =                            sin nada a la derecha, esa queda desactivada
#   Se ignoran la puntuación, mayúsculas/minúsculas y los espacios de más
#   Una clave que falte aquí usa el valor incluido (borra una línea para recuperar el original)
#   Las líneas que empiezan por say_ / msg_ / fmt_ / title_ son lo que se dice o se muestra; {…} recibe un valor
#
# Prueba una sin hablar: sh ~/.z2term/macros/voice.sh try siguiente canción
# "tus propias palabras = una orden" van en ~/.z2term/voice/commands.txt (se miran antes que esta tabla).

# --- Palabras que se escuchan ---
ai = pregunta a la ia *|oye ia *|ia *|ai *|pregunta *
media_next = siguiente canción|siguiente|salta|salta la canción|pasa de canción|próxima canción|la siguiente
media_previous = canción anterior|anterior|la anterior|vuelve atrás
media_pause = pausa|para|detén la música|para la música|pausar|stop|pausa la música
media_play = reproduce|reanuda|continúa|sigue|play|pon música|reanuda la música
volume_up = sube el volumen|subir volumen|sube volumen|más alto|sube el sonido
volume_down = baja el volumen|bajar volumen|baja volumen|más bajo|baja el sonido
volume_mute = silencio|silenciar|silencia|quita el sonido|silencia el volumen
volume_unmute = quita el silencio|activa el sonido|desactiva el silencio
time = qué hora es|la hora|hora|qué hora|dime la hora
date = qué día es hoy|qué fecha es hoy|la fecha|fecha|qué día es|a qué estamos hoy
battery = batería|cuánta batería|cuánta batería queda|nivel de batería|cómo va la batería
steps = pasos|cuántos pasos|cuántos pasos llevo|pasos de hoy|mis pasos|cuántos pasos he dado
torch_on = enciende la linterna|linterna|activa la linterna|enciende la luz
torch_off = apaga la linterna|desactiva la linterna|apaga la luz
screenshot = captura de pantalla|haz una captura|haz una captura de pantalla|captura
home = inicio|pantalla de inicio|ve al inicio
news = noticias|lee las noticias|las noticias|titulares|noticias de hoy|rss
weather = tiempo|qué tiempo hace|el tiempo|el tiempo de hoy|clima|qué tiempo hace hoy
weather_tomorrow = tiempo de mañana|el tiempo mañana|el tiempo de mañana|qué tiempo hará mañana
memo_read = lee mis notas|mis notas|lee las notas|enséñame mis notas
memo_add = toma nota de que *|toma nota de *|toma nota *|anota que *|anota *|apunta *|nota *
remind_list = recordatorios|mis recordatorios|qué recordatorios tengo|mi agenda|lista de recordatorios
remind = recuérdame que *|recuérdame *|recuerdame que *|recuerdame *|pon un recordatorio para *|crea un recordatorio para *|recordatorio para *
wake = despiértame a las *|despiértame a la *|despiértame *|despiertame a las *|pon una alarma a las *|pon una alarma para las *|alarma a las *
help = qué puedo decir|qué puedes hacer|ayuda
timer = pon un temporizador de *|pon un temporizador *|temporizador de *|temporizador *|cuenta *
navi = navega a *|llévame a *|cómo llego a *|como llego a *|quiero ir a *|ruta a *|indicaciones para *
navi_ask = navegar|navegación|indicaciones|ruta
place_phone = el teléfono de *|teléfono de *|el número de teléfono de *|número de teléfono de *
call = llama a *|llamar a *|llama *
play = reproduce *|pon *|quiero escuchar *
open = abre *|abrir *|inicia *|lanza *
# Abrir por tipo (la app predeterminada del teléfono). Ej.: abre el navegador
open_browser = navegador|el navegador|internet
open_maps = mapas|mapa|el mapa
open_mail = correo|el correo|email
open_camera = cámara|la cámara
open_settings = ajustes|configuración
open_calendar = calendario|el calendario
open_music = música|la música
open_gallery = fotos|galería|la galería
open_contacts = contactos|los contactos
open_dialer = teléfono|el teléfono
open_messages = mensajes|los mensajes|sms
open_calculator = calculadora|la calculadora
# Apodos de apps. Si dices el nombre y no se abre, escríbelo: apodo:nombre de la app|apodo:nombre de la app
app_names =

# Volumen con un número ("baja el volumen 3", "volumen al 50 por ciento", "volumen al máximo") — basta con que aparezca
volume_word = volumen
volume_up_word = sube|subir|más alto|aumenta
volume_down_word = baja|bajar|más bajo|reduce
volume_max_word = máximo
volume_min_word = mínimo|cero
volume_percent_word = %|por ciento
volume_mute_word = silencio|silencia
volume_unmute_word = quita el silencio|activa

# Duraciones y horas (temporizador, recordatorio, alarma)
unit_hour = horas|hora
unit_minute = minutos|minuto|min
unit_second = segundos|segundo|seg
unit_day = días|día
when_before = en|dentro de
when_after =
clock_at = a las|a la|sobre las|para las
clock_hour = en punto
clock_minute =
clock_half = y media
clock_am = de la mañana|de la madrugada
clock_pm = de la tarde|de la noche|esta noche
day_today = hoy
day_tomorrow = mañana
day_after_tomorrow = pasado mañana
day_daily = todos los días|cada día|a diario
day_weekdays = entre semana|los días laborables|días laborables
day_every = todos los|cada
wd_mon = lunes
wd_tue = martes
wd_wed = miércoles|miercoles
wd_thu = jueves
wd_fri = viernes
wd_sat = sábado|sabado
wd_sun = domingo
# Palabras pequeñas que quedan entre el texto y las partes que se quitan
remind_filler = que|de|el|la|a|para|en|los|las

# --- Lo que se dice o se muestra ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Órdenes por voz
title_navi = Navegación
title_ai = IA
title_timer = Temporizador
title_news = Noticias
title_memo = Notas
title_remind = Recordatorios
title_call = Llamar
title_help = Lo que puedes decir
title_rule_voice = Órdenes por voz
title_rule_timer = Temporizador por voz
title_rule_steps = Contar pasos (para Órdenes por voz)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} en punto
fmt_weekdays = lunes|martes|miércoles|jueves|viernes|sábado|domingo
fmt_months = enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre
say_time = Son las {clock}
say_date = Hoy es {weekday}, {day} de {month_name}
say_battery = La batería está al {n} por ciento. {charging}
say_charging = Se está cargando.
say_steps = Hoy llevas {n} pasos
say_steps_none = Todavía no hay pasos registrados. Hace falta el permiso de actividad física y una regla de pasos
say_news = Estos son los últimos titulares. {titles}
say_news_none = No hay noticias nuevas
fmt_weather_lang = es
say_weather = Ahora mismo: {desc}, {temp} grados
say_weather_tomorrow = Mañana: {desc}, máxima de {hi} y mínima de {lo} grados, {rain} por ciento de probabilidad de lluvia
say_weather_fail = No he podido consultar el tiempo
say_memo_saved = Anotado
say_memo_recent = Tus últimas notas. {memos}
say_memo_none = No hay notas
say_remind_ok = Recordatorio creado. {res}
say_remind_fail = No he podido crearlo. {res}
say_remind_unclear = No he entendido cuándo ni de qué avisarte. Prueba: recuérdame mañana a las 7 sacar la basura
say_remind_missing = La macro de recordatorios no está instalada. Ejecuta z2-macro install remind en una terminal
fmt_wake_label = hora de levantarse
say_wake = Es hora de levantarse
say_wake_set = Te despertaré a las {clock}
say_wake_unclear = No he entendido a qué hora despertarte
fmt_unit_hour = horas
fmt_unit_minute = minutos
fmt_unit_second = segundos
fmt_timer_label = {n} {unit}
say_timer_set = Temporizador de {label} en marcha
say_timer_done = Han pasado {label}
say_timer_unclear = No he entendido cuánto debe durar el temporizador
say_call_missing = No he encontrado el número de {who}
msg_call_missing = "{who}" no está en {file}. Escribe un "nombre número" por línea.
say_open_missing = No he encontrado ninguna aplicación llamada {name}
say_help = Tienes en una notificación lo que puedes decir
msg_help = hora, fecha, batería, pasos / baja el volumen 3, volumen al 50 por ciento / siguiente canción, pausa / temporizador de 3 minutos, despiértame a las 7 / recuérdame mañana a las 7 sacar la basura, recordatorios / noticias, tiempo, tiempo de mañana / anota comprar leche, lee mis notas / enciende la linterna, captura / abre … / llama a … / navega a …, teléfono de … / IA … (actívala con voice.sh ai) / cambiar las palabras: voice.sh words
msg_unknown = No he entendido: {text}
msg_navi_where = Di adónde quieres ir
msg_navi_hint = Destino (nombre del lugar)
msg_ai_off = La IA está desactivada. Pulsa Copiar y pégalo en una terminal para usar {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = La IA está desactivada. Instala e inicia sesión en codex / claude / agy y elígela en una terminal, por ejemplo sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = No se encuentra {ai}. Instálalo e inicia sesión, o elige otra con sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Preguntando a la IA ({ai})…
msg_ai_noanswer = No ha llegado ninguna respuesta (registro: {log})
msg_ai_usage = ai: indica off | codex | claude | agy
msg_ai_how = La IA está desactivada. Para usarla: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; solo se le pregunta si empiezas con la palabra clave, "IA" o "pregunta"). Modelo: sh ~/.z2term/macros/voice.sh model <nombre> (vacío = el de esa IA por defecto)
fmt_ai_prompt = (La respuesta se leerá en voz alta. Sin usar órdenes ni archivos, responde en español en 2 o 3 frases, sin símbolos ni listas.)
msg_setup_done = Reglas de Órdenes por voz registradas (las que ya existían no se han añadido otra vez):
msg_words_file = Tabla de palabras: {file}  (edítala para añadir formas de decir las cosas; sh ~/.z2term/macros/voice.sh words la abre)"""

private const val VOICE_WORDS_KO = """# 음성 조작 — 낱말 표 (한국어). voice.sh가 읽습니다. 고쳐 쓰면 말하는 방법을 더하거나 바꿀 수 있습니다.
#
# 쓰는 법:
#   키 = 말하는 법|다른 말하는 법|…     "|"로 나눕니다. 위에서부터 봅니다
#   * 를 하나 넣으면 그 자리의 말을 받습니다 (*열어 줘 -> "지도 열어 줘"의 "지도")
#   *X* 는 X 양쪽의 말을 받습니다
#   키 =                              오른쪽을 비우면 그 말로는 움직이지 않습니다
#   문장 부호, 대소문자, 띄어쓰기는 비교할 때 무시합니다
#   여기에 없는 키는 함께 들어 있는 표의 값을 씁니다 (줄을 지우면 원래 말로 돌아갑니다)
#   say_ / msg_ / fmt_ / title_ 로 시작하는 줄은 말하거나 보여 주는 문장입니다. {…} 에 값이 들어갑니다
#
# 말하지 않고 시험하기: sh ~/.z2term/macros/voice.sh try 다음 곡
# "내가 정한 말 = 명령"은 ~/.z2term/voice/commands.txt 에 씁니다 (이 표보다 먼저 봅니다).

# --- 알아듣는 말 ---
ai = ai한테 물어봐*|ai에게*|ai한테*|ai*|에이아이*|질문*
media_next = 다음 곡|다음 노래|다음|건너뛰기|넘겨|다음 곡 틀어 줘
media_previous = 이전 곡|이전 노래|이전|앞 곡|앞 노래
media_pause = 멈춰|정지|일시정지|음악 꺼 줘|음악 멈춰|그만
media_play = 재생|계속|다시 재생|음악 틀어 줘|음악 재생
volume_up = 볼륨 올려 줘|볼륨 올려|소리 키워 줘|소리 키워|볼륨 높여|소리 크게 해 줘|크게 해 줘
volume_down = 볼륨 내려 줘|볼륨 내려|소리 줄여 줘|소리 줄여|볼륨 낮춰|소리 작게 해 줘|작게 해 줘
volume_mute = 음소거|음소거해 줘|소리 꺼 줘|소리 꺼
volume_unmute = 음소거 해제|음소거 풀어 줘|소리 켜 줘|소리 켜
time = 지금 몇 시야|몇 시야|지금 몇 시|몇 시|시간|지금 시간|시간 알려 줘
date = 오늘 며칠이야|오늘 며칠|오늘 날짜|날짜|무슨 요일이야|오늘 무슨 요일이야|날짜 알려 줘
battery = 배터리|배터리 얼마나 남았어|배터리 잔량|충전 상태|배터리 알려 줘
steps = 걸음 수|오늘 걸음 수|몇 걸음|오늘 몇 걸음 걸었어|걸음 수 알려 줘
torch_on = 손전등 켜 줘|손전등 켜|플래시 켜 줘|플래시 켜|손전등
torch_off = 손전등 꺼 줘|손전등 꺼|플래시 꺼 줘|플래시 꺼
screenshot = 스크린샷|화면 캡처|캡처|스크린샷 찍어 줘
home = 홈|홈 화면|홈으로|홈으로 가 줘
news = 뉴스|뉴스 읽어 줘|오늘 뉴스|새 소식|뉴스 알려 줘|rss
weather = 날씨|오늘 날씨|날씨 어때|오늘 날씨 어때|날씨 알려 줘
weather_tomorrow = 내일 날씨|내일 날씨 어때|내일 날씨는|내일 날씨 알려 줘
memo_read = 메모 읽어 줘|메모 보여 줘|메모 읽어|내 메모
memo_add = 메모 *|*라고 메모해 줘|*라고 메모해|*메모해 줘|*메모해
remind_list = 리마인더 목록|알림 목록|일정|오늘 일정|일정 알려 줘|리마인더 알려 줘
remind_verb = *으라고 알려 줘|*으라고 알려줘|*라고 알려 줘|*라고 알려줘
fmt_remind_verb = {what}기
remind = *을 알려 줘|*를 알려 줘|*알려 줘|*리마인드해 줘|*리마인더 설정해 줘|리마인드 *|*리마인드
wake = *에 깨워 줘|*에 깨워|*에 알람 맞춰 줘|*에 알람 맞춰
help = 뭐 할 수 있어|무엇을 할 수 있어|도움말|할 수 있는 것
timer = *타이머 맞춰 줘|*타이머 설정해 줘|*타이머|타이머 *
navi = *까지 안내해 줘|*까지 길 안내|*로 안내해 줘|*으로 안내해 줘|*에 가고 싶어|*가는 길|*까지 내비
navi_ask = 내비|내비게이션|길 안내|길 안내해 줘
place_phone = *전화번호 알려 줘|*의 전화번호|*전화번호
call = *에게 전화해 줘|*에게 전화해|*한테 전화해 줘|*한테 전화해|*에게 전화|*한테 전화|*에 전화해 줘
play = *을 틀어 줘|*를 틀어 줘|*틀어 줘|*을 재생해 줘|*를 재생해 줘|*재생해 줘|*재생
open = *을 열어 줘|*를 열어 줘|*열어 줘|*열어|*을 실행해 줘|*를 실행해 줘|*실행해 줘|*실행
# 종류로 열기 (기기의 기본 앱). 예: 브라우저 열어 줘
open_browser = 브라우저|인터넷
open_maps = 지도
open_mail = 메일|이메일
open_camera = 카메라
open_settings = 설정
open_calendar = 캘린더|달력
open_music = 음악
open_gallery = 사진|갤러리
open_contacts = 연락처
open_dialer = 전화
open_messages = 메시지|문자
open_calculator = 계산기
# 앱 별명. 말해도 열리지 않을 때 적습니다: 별명:앱 이름|별명:앱 이름
app_names =

# 숫자가 든 볼륨 ("볼륨 3단계 내려 줘", "볼륨 10으로", "볼륨 50퍼센트", "볼륨 최대") — 들어 있기만 하면 맞습니다
volume_word = 볼륨|음량
volume_up_word = 올려|키워|높여|크게
volume_down_word = 내려|줄여|낮춰|작게
volume_max_word = 최대
volume_min_word = 최소
volume_percent_word = %|퍼센트|프로
volume_mute_word = 음소거
volume_unmute_word = 해제

# 시간의 길이와 시각 (타이머·리마인더·알람)
unit_hour = 시간
unit_minute = 분
unit_second = 초
unit_day = 일
when_before =
when_after = 후에|뒤에|후|뒤
clock_at =
clock_hour = 시
clock_minute = 분
clock_half = 반
clock_am = 오전|아침|새벽
clock_pm = 오후|저녁|밤
day_today = 오늘
day_tomorrow = 내일
day_after_tomorrow = 모레
day_daily = 매일
day_weekdays = 평일|주중
day_every = 매주
wd_mon = 월요일
wd_tue = 화요일
wd_wed = 수요일
wd_thu = 목요일
wd_fri = 금요일
wd_sat = 토요일
wd_sun = 일요일
# 언제 부분을 뺀 뒤, 본문과의 사이에 남는 이음말
remind_filler = 에|에는|에게|까지|을|를|은|는|이|가|라고|하라고|마다

# --- 말하거나 보여 주는 문장 ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = 음성 조작
title_navi = 길 안내
title_ai = AI
title_timer = 타이머
title_news = 뉴스
title_memo = 메모
title_remind = 일정
title_call = 전화
title_help = 말할 수 있는 것
title_rule_voice = 음성 조작
title_rule_timer = 음성 타이머
title_rule_steps = 걸음 수 세기 (음성 조작용)
fmt_clock = {h}시 {m}분
fmt_clock_oclock = {h}시
fmt_weekdays = 월|화|수|목|금|토|일
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = 지금은 {clock}입니다
say_date = 오늘은 {month}월 {day}일 {weekday}요일입니다
say_battery = 배터리는 {n}퍼센트입니다. {charging}
say_charging = 충전 중입니다.
say_steps = 오늘은 {n}걸음 걸었습니다
say_steps_none = 아직 걸음 수가 기록되지 않았습니다. 신체 활동 권한과 걸음 수 규칙이 필요합니다
say_news = 새 소식입니다. {titles}
say_news_none = 새 소식이 없습니다
fmt_weather_lang = ko
say_weather = 지금 날씨는 {desc}, 기온 {temp}도입니다
say_weather_tomorrow = 내일은 {desc}, 최고 {hi}도, 최저 {lo}도, 비 올 확률은 {rain}퍼센트입니다
say_weather_fail = 날씨를 가져오지 못했습니다
say_memo_saved = 메모했습니다
say_memo_recent = 최근 메모입니다. {memos}
say_memo_none = 메모가 없습니다
say_remind_ok = 리마인더를 등록했습니다. {res}
say_remind_fail = 등록하지 못했습니다. {res}
say_remind_unclear = 언제, 무엇을 알려 드릴지 알아듣지 못했습니다. 예: 내일 아침 7시에 쓰레기 버리라고 알려 줘
say_remind_missing = 리마인더 매크로가 설치되어 있지 않습니다. 터미널에서 z2-macro install remind 를 실행하세요
fmt_wake_label = 일어날 시간
say_wake = 일어날 시간입니다
say_wake_set = {clock}에 깨워 드릴게요
say_wake_unclear = 깨울 시각을 알아듣지 못했습니다
fmt_unit_hour = 시간
fmt_unit_minute = 분
fmt_unit_second = 초
fmt_timer_label = {n}{unit}
say_timer_set = {label} 타이머를 맞췄습니다
say_timer_done = {label} 타이머가 끝났습니다
say_timer_unclear = 타이머 길이를 알아듣지 못했습니다
say_call_missing = {who}의 번호를 찾지 못했습니다
msg_call_missing = "{who}"이(가) {file} 에 없습니다. 한 줄에 "이름 번호"로 쓰세요.
say_open_missing = {name}이라는 앱을 찾지 못했습니다
say_help = 말할 수 있는 것을 알림으로 보냈습니다
msg_help = 시간·날짜·배터리·걸음 수 / 볼륨 3단계 내려 줘·볼륨 50퍼센트 / 다음 곡·멈춰 / 3분 타이머·7시에 깨워 줘 / 내일 아침 7시에 쓰레기 버리라고 알려 줘·일정 / 뉴스·날씨·내일 날씨 / 메모 우유 사기·메모 읽어 줘 / 손전등 켜 줘·스크린샷 / ◯◯ 열어 줘 / ◯◯에게 전화해 줘 / ◯◯까지 안내해 줘·◯◯ 전화번호 / AI … (켜려면 voice.sh ai) / 말 바꾸기: voice.sh words
msg_unknown = 알아듣지 못한 말입니다: {text}
msg_navi_where = 목적지를 말해 주세요
msg_navi_hint = 목적지 (장소 이름)
msg_ai_off = AI가 꺼져 있습니다. 복사를 눌러 터미널에 붙여 넣으면 {ai}를 씁니다 (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI가 꺼져 있습니다. codex / claude / agy 중 하나를 설치하고 로그인한 뒤, 터미널에서 sh ~/.z2term/macros/voice.sh ai codex 처럼 고르세요
msg_ai_missing = {ai}를 찾을 수 없습니다. 설치하고 로그인하거나, sh ~/.z2term/macros/voice.sh ai 로 다른 AI를 고르세요
msg_ai_asking = AI ({ai})에게 묻는 중…
msg_ai_noanswer = 답을 받지 못했습니다 (기록: {log})
msg_ai_usage = ai: off | codex | claude | agy 중 하나를 지정하세요
msg_ai_how = AI가 꺼져 있습니다. 쓰려면: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy. 신호 말 "AI" 나 "질문"으로 시작할 때만 묻습니다). 모델: sh ~/.z2term/macros/voice.sh model <이름> (비우면 그 AI의 기본값)
fmt_ai_prompt = (답은 소리 내어 읽습니다. 명령이나 파일을 쓰지 말고, 한국어로 2~3문장, 기호나 목록 없이 답해 주세요.)
msg_setup_done = 음성 조작 규칙을 등록했습니다 (이미 있는 것은 더하지 않았습니다):
msg_words_file = 낱말 표: {file}  (고쳐 쓰면 말하는 법을 더할 수 있습니다. sh ~/.z2term/macros/voice.sh words 로 엽니다)"""

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
msg_help = time, date, battery, steps / volume down 3, volume 50 percent / next song, pause / 3 minute timer, wake me up at 7 / remind me to take out the bins tomorrow at 7 am, reminders / news, weather, weather tomorrow / note buy milk, read my notes / flashlight on, screenshot / open … / call … / navigate to …, phone number of … / AI … (turn on with voice.sh ai) / change the words: voice.sh words
msg_unknown = I did not understand: {text}
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
msg_words_file = Word table: {file}  (edit it to add ways to say things; sh ~/.z2term/macros/voice.sh words opens it)"""
