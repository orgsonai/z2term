#!/bin/bash

# =================================================================
# Matrix Rain Bash Script (Final Logic Fix)
# - Corrected the drawing logic to respect the GRADIENT_LENGTH parameter.
# - All customizations now work as intended.
# =================================================================

# --- CONFIGURATION AREA ---

# 1. 色のカスタマイズ (Color Customization)
#    下の行のコメント(#)を付け替えることで、色を変更できます。
#    ANSI 256カラーコードを直接編集して、自分だけの色を作ることも可能です。

# 標準 (Green)
gradient_colors=(46 40 34 28 22 236 235 234 233 232)
# サンプル (Blue)
# gradient_colors=(21 20 19 18 17 235 234 233 232)
# サンプル (Red)
# gradient_colors=(196 160 124 88 52 235 234 233 232)
# サンプル (White/Ghost)
# gradient_colors=(255 252 248 244 240 238 236 234 232)

# 2. 軌跡の長さ（文字数）
MIN_TRAIL_LENGTH=10
MAX_TRAIL_LENGTH=30
# 3. グラデーションの見た目
GRADIENT_LENGTH=30 # この長さがグラデーション部分になります
GRADIENT_STEP=4   # 何文字ごとに色を変えるか (1=滑らか, 3=ブロック状)
# 4. 雨量（密度）
STREAM_SPACING=1
# 5. 速度のばらつきの範囲
MIN_SPEED_LEVEL=1
MAX_SPEED_LEVEL=4
# 6. 全体の基準速度
BASE_SPEED=0.02

# --- END OF CONFIGURATION ---


cleanup() {
    printf "\033[?25h"; printf "\033[0m"; tput clear; exit 0
}
trap cleanup INT

lines=$(tput lines); cols=$(tput cols)
chars="0123456789abcdefghijklmnopqrstuvwxyz"
chars_len=${#chars}
gradient_len=${#gradient_colors[@]}

declare -a drops speeds lengths

get_random() {
    local min=$1; local max=$2
    if (( min > max )); then max=$min; fi
    local range=$((max - min + 1))
    echo $((RANDOM % range + min))
}

for ((i=0; i<$cols; i++)); do
    drops[$i]=-$((RANDOM % (lines * 2) ))
    speeds[$i]=$(get_random $MIN_SPEED_LEVEL $MAX_SPEED_LEVEL)
    lengths[$i]=$(get_random $MIN_TRAIL_LENGTH $MAX_TRAIL_LENGTH)
done

frame_count=0
printf "\033[?25l"; tput clear

while true; do
    frame_count=$((frame_count + 1))
    for ((c=0; c<$cols; c+=STREAM_SPACING)); do
        if (( frame_count % speeds[c] == 0 )); then
            y=${drops[c]}
            length=${lengths[c]}

            # 1. Draw the gradient trail
            for ((i=0; i < length; i++)); do
                char_y=$((y - i))
                if (( char_y > 0 && char_y <= lines )); then
                    # ★★★ 修正点 ★★★
                    # 固定の「8」ではなく、設定した GRADIENT_LENGTH を使うように修正
                    if (( i < GRADIENT_LENGTH )); then
                        color_index=$((i / GRADIENT_STEP))
                        if (( color_index >= gradient_len )); then
                            color_index=$((gradient_len - 1))
                        fi
                        color_code=${gradient_colors[color_index]}
                    else # グラデーション以降は一番暗い色
                        color_code=${gradient_colors[@]: -1}
                    fi
                    printf "\033[${char_y};${c}H\033[38;5;${color_code}m%s\033[0m" "${chars:RANDOM%chars_len:1}"
                fi
            done

            # 2. Erase the character directly behind the trail
            erase_y=$((y - length))
            if (( erase_y > 0 && erase_y <= lines )); then
                printf "\033[${erase_y};${c}H "
            fi

            drops[$c]=$((y + 1))

            # 3. Reset logic
            if (( erase_y > lines )); then
                drops[$c]=-$((RANDOM % 20))
                speeds[$c]=$(get_random $MIN_SPEED_LEVEL $MAX_SPEED_LEVEL)
                lengths[$c]=$(get_random $MIN_TRAIL_LENGTH $MAX_TRAIL_LENGTH)
            fi
        fi
    done

    if [[ $BASE_SPEED > 0 ]]; then
        sleep $BASE_SPEED
    fi
done
