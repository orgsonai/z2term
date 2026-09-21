package com.zerotoship.z2term.proot

/** Shell-side example of the public viewer protocol; no Android reminder model or UI is involved. */
internal fun reminderViewFunctions(d: String, t: CliText): String {
    val labels = t(
        en = "Reminders|Add|Delete|Content|Date and time|Repeat|Once|Daily|Weekdays|Weekly (selected weekday)|Schedule text (optional; overrides date and repeat)|Scheduled|Repeating|Notified|No reminders|Delete this reminder and its pending notifications?",
        ja = "リマインダー|追加|削除|内容|日時|繰り返し|なし|毎日|平日|毎週（選んだ日付の曜日）|日時を文字で指定（任意・日時と繰り返しより優先）|予定|繰り返し|通知済み|予定はありません|この予定と残っている通知予約を削除しますか？",
        "zh-CN" to "提醒|添加|删除|内容|日期和时间|重复|不重复|每天|工作日|每周（所选日期的星期）|文字指定时间（可选；优先于日期和重复）|已安排|重复|已通知|没有提醒|删除此提醒和待发的通知预约？",
        "zh-TW" to "提醒|新增|刪除|內容|日期與時間|重複|不重複|每天|工作日|每週（所選日期的星期）|文字指定時間（選填；優先於日期和重複）|已安排|重複|已通知|沒有提醒|刪除此提醒與待發的通知預約？",
        "es" to "Recordatorios|Añadir|Eliminar|Contenido|Fecha y hora|Repetición|Una vez|A diario|Días laborables|Semanal (día seleccionado)|Horario escrito (opcional; sustituye fecha y repetición)|Programados|Recurrentes|Notificados|No hay recordatorios|¿Eliminar este recordatorio y sus avisos pendientes?",
        "ko" to "리마인더|추가|삭제|내용|날짜와 시간|반복|한 번|매일|평일|매주 (선택한 날짜의 요일)|시간 직접 입력 (선택 사항, 날짜와 반복보다 우선)|예정|반복|알림 보냄|리마인더가 없습니다|이 리마인더와 남은 알림 예약을 삭제할까요?"
    )
    return """
# Render data and controls separately. All external text is escaped, never treated as markup or code.
cmd_view() (
  tmp=${d}(mktemp -d "${d}DIR/.view-XXXXXXXX") || exit 1
  trap 'rm -rf "${d}tmp"' 0
  trap 'exit 1' 1 2 15
  set -- "${d}DIR"/*.txt
  [ -f "${d}1" ] || set -- /dev/null
  awk -v out="${d}tmp/page.html" -v controls="${d}tmp/controls.json" -v labels='$labels' '
    function html(s,    i,c,r) {
      r=""; for(i=1;i<=length(s);i++) {
        c=substr(s,i,1)
        if(c=="&") c="&amp;"; else if(c=="<") c="&lt;"; else if(c==">") c="&gt;"; else if(c=="\"") c="&quot;"
        r=r c
      }; return r
    }
    function json(s,    i,c,r) {
      r="\""; for(i=1;i<=length(s);i++) {
        c=substr(s,i,1)
        if(c=="\\") c="\\\\"; else if(c=="\"") c="\\\""; else if(c=="\n") c="\\n"
        else if(c=="\r") c="\\r"; else if(c=="\t") c="\\t"; else if(c ~ /[[:cntrl:]]/) c=" "
        r=r c
      }; return r "\""
    }
    BEGIN { FS="\t"; split(labels,l,"|"); n=0 }
    FNR==1 {
      id=FILENAME; sub(/^.*\//,"",id); sub(/\.txt${d}/,"",id)
      current=0
      if(id !~ /^[0-9]+${d}/ || NF<4) next
      current=++n; ids[n]=id; kinds[n]=${d}1; plans[n]=${d}2
      bodies[n]=${d}4; for(i=5;i<=NF;i++) bodies[n]=bodies[n] "\t" ${d}i
      next
    }
    current { bodies[current]=bodies[current] "\n" ${d}0 }
    END {
      print "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" > out
      print "<style>body{margin:0;padding:12px;color:var(--z2-fg,#111);background:var(--z2-bg,#fff);font:16px/1.5 sans-serif}h1{font-size:18px}h2{font-size:14px;color:var(--z2-dim,#666)}article{border-bottom:1px solid var(--z2-line,#ddd);padding:12px 0}p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}small{color:var(--z2-dim,#666)}a{display:inline-block;padding:10px;color:var(--z2-accent,#087f56)}</style></head><body>" > out
      print "<h1>" html(l[1]) "</h1>" > out
      print "{\"handler\":\"remind.sh\",\"refresh\":[\"view\"],\"actions\":[" > controls
      print "{\"id\":\"add\",\"label\":" json(l[2]) ",\"toolbar\":true,\"args\":[\"view-add\"],\"fields\":[" > controls
      print "{\"label\":" json(l[4]) ",\"type\":\"text\"}," > controls
      print "{\"label\":" json(l[5]) ",\"type\":\"datetime\"}," > controls
      print "{\"label\":" json(l[6]) ",\"type\":\"choice\",\"choices\":[" > controls
      split("once daily weekday weekly",repeatValues," ")
      for(j=1;j<=4;j++) print (j>1 ? "," : "") "{\"value\":" json(repeatValues[j]) ",\"label\":" json(l[j+6]) "}" > controls
      print "]},{\"label\":" json(l[11]) ",\"required\":false}]}" > controls
      for(group=1;group<=3;group++) {
        heading=0
        for(i=1;i<=n;i++) {
          g=kinds[i]=="repeat" ? 2 : (kinds[i]=="fired" ? 3 : 1)
          if(g!=group) continue
          if(!heading++) print "<h2>" html(l[group+11]) "</h2>" > out
          print "<article><p>" html(bodies[i]) "</p><small>" html(plans[i]) "</small><br><a href=\"z2-action:delete-" ids[i] "\">" html(l[3]) "</a></article>" > out
          print ",{\"id\":\"delete-" ids[i] "\",\"label\":" json(l[3]) ",\"args\":[\"view-delete\"," json(ids[i]) "],\"confirm\":" json(l[16] "\n" plans[i] "\n" bodies[i]) "}" > controls
        }
      }
      if(n==0) print "<p>" html(l[15]) "</p>" > out
      print "</body></html>" > out
      print "]}" > controls
    }' "${d}@" || exit 1
  z2-view --controls "${d}tmp/controls.json" "${d}tmp/page.html" "${d}(printf '%s' '$labels' | cut -d'|' -f1)"
)

cmd_view_add() {
  [ ${d}# -eq 4 ] || die 'view-add: content datetime repeat schedule-text'
  content=${d}1; stamp=${d}2; repeat_kind=${d}3; words=${d}4
  ensure_hooks || return 1
  if [ -n "${d}words" ]; then
    # Word splitting is intentional; glob expansion and shell evaluation are not.
    set -f
    set -- ${d}words
    set +f
    parse_when "${d}1" "${d}2" "${d}3" || die "${d}WHY"
    [ ${d}# -eq "${d}USED" ] || die 'Unexpected words after the schedule'
    cmd_add "${d}@" "${d}content" || return 1
  else
    case ${d}stamp in *[!0-9]*|'') die 'Invalid datetime' ;; esac
    [ "${d}{#stamp}" -eq 12 ] || die 'Invalid datetime'
    clock="${d}(cut2 "${d}stamp" 9):${d}(cut2 "${d}stamp" 11)"
    case ${d}repeat_kind in
      once) cmd_add "${d}stamp" "${d}content" ;;
      daily|weekday) cmd_add "${d}repeat_kind" "${d}clock" "${d}content" ;;
      weekly)
        year=${d}(printf '%s' "${d}stamp" | cut -c1-4)
        day=${d}(date -d "${d}year-${d}(cut2 "${d}stamp" 5)-${d}(cut2 "${d}stamp" 7)" +%w) || return 1
        case ${d}day in 0) day=sun;; 1) day=mon;; 2) day=tue;; 3) day=wed;; 4) day=thu;; 5) day=fri;; 6) day=sat;; esac
        cmd_add weekly "${d}day" "${d}clock" "${d}content" ;;
      *) die 'Invalid repeat choice' ;;
    esac || return 1
  fi
  cmd_view
}

cmd_view_delete() {
  [ ${d}# -eq 1 ] || die 'view-delete: ID'
  case ${d}1 in *[!0-9]*|'') die 'Invalid reminder ID' ;; esac
  # Stable ID only, never a row number or the special all selector.
  [ -f "${d}DIR/${d}1.txt" ] || { cmd_view; return; }
  del_one 0 "${d}1" "${d}DIR/${d}1.txt" || return 1
  cmd_view
}
"""
}
