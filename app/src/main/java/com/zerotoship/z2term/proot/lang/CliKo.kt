package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * 한국어 (`ko`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 */
internal object CliKo : CliLanguage {

    override val code: String = "ko"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgKo) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgKo(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 GUI 한 벌을 설치합니다",
            cleanInstalling = "🧹 GUI를 처음부터 다시 설치합니다",
            noPackageManager = "❌ 지원하는 패키지 관리자(apk/apt-get/pacman)를 찾지 못했습니다.",
            invalidGeometry = "❌ 해상도를 잘못 적었습니다",
            noXvnc = "❌ Xvnc/Xtigervnc가 없습니다 (tigervnc가 설치되어 있지 않습니다)",
            alreadyRunning = "✅ GUI가 이미 돌고 있습니다",
            startingXvnc = "▶ 시작하는 중",
            xvncFailed = "❌ Xvnc를 띄우지 못했습니다. 로그:",
            ready = "✅ GUI가 준비되었습니다. z2term의 GUI 탭에서 접속하세요.",
            running = "✅ GUI가 돌고 있습니다",
            stopped = "⏹ GUI가 멈춰 있습니다",
            stoppedMsg = "⏹ 멈췄습니다",
            usage = "사용법: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ GUI 한 벌(Xvnc / openbox / D-Bus)을 설치하지 못했습니다.",
            installFailedHint = "   네트워크를 확인하세요. 되살리려면 터미널에서 z2gui clean을 실행할 수 있습니다.",
            audioInstalling = "🔊 GUI 소리: PulseAudio를 설치합니다",
            audioNoPulse = "⚠️ GUI 소리: pulseaudio가 없어 소리 없이 계속합니다",
            audioNoPactl = "⚠️ GUI 소리: pactl이 없어 소리 없이 계속합니다",
            audioStartFailed = "⚠️ GUI 소리: PulseAudio가 시작되지 않았습니다",
            audioReady = "🔊 GUI 소리: z2sink.monitor →",
            menuApps = "애플리케이션",
            menuWindows = "창",
            menuDesktops = "가상 데스크톱",
            menuPreviousDesktop = "이전 데스크톱",
            menuNextDesktop = "다음 데스크톱",
            menuReload = "메뉴 다시 읽기"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ 포트 번호가 올바르지 않습니다",
            configOk = "sshd(dropbear) 설정에 문제가 없습니다",
            installingDropbear = "📦 dropbear가 없어 설치합니다…",
            dropbearInstallFailed = "❌ dropbear를 설치하지 못했습니다. 네트워크와 패키지 이름을 확인하세요.",
            privilegedPortWarn = "⚠️ 특권 포트입니다. proot(root 없이)에서는 열지 못할 가능성이 큽니다 (1024 이상을 쓰세요).",
            lanExposeWarn = "⚠️ LAN/WAN에 여는 모드입니다: 키 인증이 필요하며, 강한 키를 쓰세요.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys가 비어 있습니다. 네트워크에 여는 시작을 취소합니다.",
            noAuthorizedKeysHint = "   공개 키를 ~/.ssh/authorized_keys에 등록한 뒤 다시 실행하세요.",
            loopbackBind = "🔒 loopback에서만 듣습니다. LAN에 열려면 --lan을 붙이거나 Z2_SSHD_LAN=1을 정하세요.",
            foregroundStart = "▶ dropbear를 앞에서 실행합니다 (멈추려면 Ctrl-C)",
            listeningLan = "✅ dropbear listening (root, 키 인증만)",
            listeningLoopback = "✅ dropbear listening (root, 키 인증만, loopback만)",
            startupFailed = "❌ dropbear를 띄우지 못했습니다. 로그:",
            deviceIp = "기기의 IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys가 비어 있습니다. 접속하려면 클라이언트의 공개 키를 등록하세요.",
            authKeysExample = "   예: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 pacman 키링을 준비합니다 (처음 한 번만, 네트워크 없이). 잠시 걸립니다…",
            "keyring.msgOk" to "✅ 키링이 준비되었습니다. 이제 패키지를 설치할 수 있습니다.",
            "keyring.msgInitFail" to "❌ pacman-key --init에 실패했습니다. 이 탭을 다음에 열 때 다시 시도합니다.",
            "keyring.msgNoKeyrings" to "❌ /usr/share/pacman/keyrings 아래에서 함께 실은 키링을 찾지 못했습니다.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate에 실패했습니다. 이 탭을 다음에 열 때 다시 시도합니다.",
            "action.description" to "이름이 있는 Android 동작 매크로입니다. z2-key permission으로 Android 동작을 켜세요.\n저장할 때 정의를 검사하고 바꿉니다. screen=current는 현재 화면 크기를 기록합니다.\nrun은 완료를 기다리고 start는 실행 ID를 반환합니다. run 중단 시 해당 실행을 멈춥니다.\n동시에 1개, 정의마다 최대 64개 명령, 1–300초입니다. 알림이나 stop으로 남은 단계를 멈춥니다.\n좌표 동작은 대상 앱과 화면 크기·방향이 일치해야 합니다. 동작당 최대 3초입니다.\n패널 제스처에서는 start, 셸 매크로나 z2-when에서는 run을 사용하세요.\n저장 위치: /root/.z2term/actions/NAME.actions (공유 홈). z2-macro actions도 같은 명령입니다.",
            "action.controlHelp" to "version=2에서 repeat 횟수|forever ... end, if 조건 ... else ... end, call 이름을 사용할 수 있습니다.\n조건은 z2-state 키 또는 foreground=패키지입니다. 쉼표는 AND, !는 부정입니다.\n조건 상태를 얻지 못하면 실패합니다. 호출할 매크로도 실행 전에 검사하고 고정합니다.\n정의마다 최대 64개 명령, 실행 전체에서 최대 10000개 명령입니다. 상위 및 하위 매크로의 제한 시간이 적용됩니다.",
            "action.uiHelp" to "version=2는 click 선택자, long-click 선택자, wait-ui 밀리초 선택자도 지원합니다.\n선택자 예: text=계속, desc=추가 옵션, id=org.example.app:id/next. 정확히 일치하며 공백을 따옴표로 감싸지 않습니다.\n먼저 target이나 launch를 지정하세요. wait-ui는 250밀리초마다 1–30000밀리초 동안 검색합니다. 여러 항목이 일치하면 실패합니다.\n동작은 한 번만 전송합니다. 다음 화면은 wait-ui로 기다리세요. 입력란과 비밀번호란은 제외됩니다.\ninspect 패키지는 전면 대상 앱의 표시 글자, 설명과 ID를 JSON으로 반환합니다.",
            "adb.mNoPm" to "z2adb: 지원하는 패키지 관리자(apk/apt-get/pacman)를 찾지 못했습니다.",
            "adb.mInstalling" to "z2adb: ${d}PM으로 adb 클라이언트(${d}ADB_PKG)를 설치하는 중 ...",
            "adb.mInstallFail" to "z2adb: adb 설치에 실패했습니다. 직접 설치한 뒤 다시 시도하세요.",
            "adb.mHaveAdb" to "z2adb: adb는 이미 설치되어 있습니다.",
            "adb.mNeedWireless" to "z2adb: 잘 안 되면 설정 > 개발자 옵션 > 무선 디버깅을 켜세요 (Android 11 이상).",
            "adb.mPairPort" to "페어링 포트 (기기 페어링 코드 화면에 나오는 번호): ",
            "adb.mConnPort" to "연결 포트 (무선 디버깅 아래에 나오는 번호): ",
            "adb.usageText" to """
        z2adb - 무선 디버깅으로 이 기기 자신의 adb에 연결합니다 (PC 없이 자기 자신에게 adb).

        준비: 설정 > 개발자 옵션 > 무선 디버깅을 켜세요 (Android 11 이상).

          z2adb setup                adb 클라이언트를 설치합니다 (apk/apt-get/pacman을 알아서 찾습니다)
          z2adb pair  <port> [code]  페어링합니다 (설정에 나오는 페어링 포트 + 6자리 코드)
          z2adb connect <port>       연결합니다 (무선 디버깅 아래에 나오는 포트)
          z2adb status               'adb devices -l'을 보여 줍니다
          z2adb <무엇이든>           그대로 adb에 넘깁니다 (shell, logcat, install, ...)

        대상은 기본값이 127.0.0.1입니다. 바꾸려면 host:port를 넘기세요 (또는 Z2ADB_HOST를 정하세요).
        예:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "GUI를 열지 않고 로컬 Linux 명령의 소리를 Android에서 재생합니다.\ninstall: 현재 Linux OS에 PulseAudio를 설치합니다(패키지 다운로드).\nrun 명령 [인수...]: 오디오를 연결하고 명령을 실행한 뒤 전용 서버를 종료합니다.\n셸을 지정하면 여러 명령에서 소리를 사용할 수 있습니다. 기존 설정 파일은 덮어쓰지 않습니다.\n로컬 터미널 탭과 PulseAudio 호환 플레이어가 필요합니다. SSH는 지원하지 않습니다.",
            "audio.missing" to "오디오 도구가 없습니다. z2-audio install을 실행하세요.",
            "audio.failed" to "오디오 연결에 실패하여 지정한 명령을 시작하지 않았습니다.",
            "doctor.head" to "== z2doctor (이 기기) ==",
            "doctor.secApp" to "-- 앱 --",
            "doctor.secLinux" to "-- Linux 쪽 --",
            "doctor.secPerm" to "-- 권한 --",
            "doctor.secAuto" to "-- 자동화 --",
            "doctor.secReport" to "-- 도움을 청할 때 이것을 복사하세요 --",
            "doctor.lVersion" to "버전",
            "doctor.lEngine" to "엔진",
            "doctor.lDistro" to "배포판",
            "doctor.lKernel" to "커널",
            "doctor.lDisk" to "남은 공간",
            "doctor.lHome" to "홈",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "알림을 낼 수 있음",
            "doctor.lNotifyRead" to "알림을 읽을 수 있음",
            "doctor.lBattOpt" to "배터리 최적화에서 제외됨",
            "doctor.lStorage" to "모든 저장소",
            "doctor.lSms" to "SMS 수신",
            "doctor.lCapture" to "이벤트 감지",
            "doctor.lServers" to "상주 서버",
            "doctor.lRules" to "자동화 규칙",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Android 설정 > 앱 > Z2Term > 알림: 허용",
            "doctor.fixNotifyRead" to "-> 설정 > 상주 서버・자동화 > 알림 감지",
            "doctor.fixBattOpt" to "-> 설정 > 백그라운드 프로세스 보호 > 배터리 최적화에서 제외",
            "doctor.fixStorage" to "-> 설정 > 모든 파일에 대한 접근 허용 (/sdcard를 보려면 필요합니다)",
            "doctor.fixSms" to "-> 설정 > SMS 감지 (sms: 트리거에만 필요합니다)",
            "doctor.fixCapture" to "-> 설정 > 시스템 이벤트 감지 (charge/battery/wifi/sensor/event 트리거가 씁니다)",
            "doctor.fixPaused" to "-> 자동화가 멈춰 있습니다. 'z2-when resume'이나 자동화 탭에서 다시 시작하세요",
            "doctor.fixDisk" to "-> 남은 공간이 500MB도 되지 않습니다. 설정에서 쓰지 않는 OS 데이터를 지우세요",
            "doctor.fixSdcard" to "-> 여기서는 /sdcard가 비어 보입니다. 위의 저장소 권한이 있어야 보입니다",
            "doctor.noteRedacted" to "(SSID / IP / 호스트 이름은 일부러 뺐습니다)",
            "doctor.allGood" to "문제를 찾지 못했습니다.",
            "doctor.someBad" to "살펴볼 것:",
            "doctor.usage" to "사용법: z2doctor [--share | --clip]   (--share는 보고서를 Android 공유 메뉴로 넘깁니다)",
            "doctor.pausedYes" to "멈춤",
            "doctor.secExits" to "-- 지난번에 어떻게 끝났는지 --",
            "doctor.lExitsNote" to "최근의 비정상 종료 (새것부터):",
            "doctor.lExitsNone" to "비정상 종료: 기록된 것이 없습니다",
            "doctor.lExitsFile" to "(전체 기록: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> 메모리가 모자라 종료되었습니다. 무거운 작업을 한 번에 적게 돌리거나, 상주 서버와 탭을 줄이세요",
            "doctor.title" to "'안 돼요'일 때의 자체 진단",
            "doctor.seeAlso" to "함께 보기: z2scan self (보안 점검, 다른 도구입니다)",
            "help.header" to "Z2Term 전용 명령",
            "help.list" to """
        |[버전 / 정보]
        |  z2version [--short]           앱 버전, 엔진, OS(배포판), 커널
        |  z2doctor [--share|--clip]     왜 안 되지? 자체 점검 + 붙여 넣을 보고서
        |
        |[휴대전화 기능]
        |  z2-notify [-h] [-b 라벨]...   알림 (-h: 배너, -b: 응답 버튼)
        |  z2-ask "질문"                 알림으로 묻고, 답을 출력합니다
        |  z2-listen [-w 단어,...]        말을 글자로 (기기 안에서만 인식. z2-listen -h)
        |  z2-toast "메시지"             토스트 (화면에 잠깐 뜨는 짧은 메시지)
        |  z2-share "텍스트"             텍스트를 Android 공유 메뉴로 넘깁니다
        |  z2-open <url|경로>            URL이나 파일을 그 앱으로 엽니다
        |  z2-view <파일.html>            여기서 만든 페이지를 앱 안에서 읽기
        |  z2-edit <파일>                 텍스트 파일을 앱의 편집 화면에서 고치기
        |  z2-qr                         카메라로 QR 읽기 (여는 버튼 하나)
        |  z2-img <파일>                 터미널에 그림을 그립니다 (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [텍스트]    클립보드 읽기 / 넣기 (set에 인수가 없으면 stdin)
        |  z2-file pick | save FILE     Android 파일 선택 및 내보내기 (z2-file --help)
        |  z2-battery                    배터리 잔량과 충전 상태 (JSON)
        |  z2-vibrate [ms]               진동 (기본값 200ms)
        |  z2-say "텍스트"               TTS로 소리 내어 읽습니다 (인수가 없으면 stdin)
        |  z2-torch [on|off|toggle|status]      손전등 (기본값 toggle)
        |  z2-media [play|pause|next]    미디어 키 (previous/stop도 됩니다)
        |  z2-volume up|down|N|N%        미디어 음량 (current/max를 돌려줍니다)
        |  z2-audio run COMMAND ...      Linux 명령의 소리를 Android에서 재생
        |  z2-sensor [light|accel|prox]  센서 값을 한 번 읽습니다 (JSON)
        |  z2-intent -a ACT -d URI ...   어떤 Android Intent든 띄웁니다 (MACRO-GUIDE 참고)
        |  z2-state [키]                 지금 상태를 JSON으로 (키 하나면 그 값만)
        |  z2-screen keepon 1h | off     한동안 화면이 저절로 꺼지지 않게 합니다
        |  z2-tile set 1 backup.sh       매크로나 명령을 타일에 올립니다 (자리 12개)
        |  z2-icon edit 1 | edit notify  타일이나 알림 아이콘을 직접 그립니다 (24/48/64)
        |  z2-alarm at|daily HH:MM [이름] 시각 트리거 -> events.jsonl (list/cancel도)
        |  z2-when <트리거> run <cmd>    충전/배터리/시각/이벤트로 실행 (사용법: z2-when)
        |  z2-macro list|install <이름>  함께 실은 예제 매크로 (MACRO-GUIDE 참고)
        |  z2-session list|new|send|...  이 앱의 탭을 다룹니다 (사용법: z2-session)
        |  z2-usb list | allow [기기]    USB 기기를 Linux에서 씁니다 (Android가 권한을 묻습니다)
        |  z2-update [--check]           GitHub Releases에서 z2term 업데이트 (승인은 직접)
        |
        |[그래픽 앱 (GUI)]
        |  z2gui start [WxH] | stop | status  Linux 데스크톱 (예: z2gui start 1280x720)
        |  z2run <그래픽-앱>             그래픽 앱 열기 (GUI 탭도 함께 열립니다)
        |
        |[연결]
        |  z2adb pair/connect/shell ...  PC 없이 이 휴대전화 자신에게 adb  (z2adb help)
        |  sshd [-p N]                   SSH 서버 (기본값은 127.0.0.1만, 키 인증)
        |
        |[보안]
        |  z2scan self [--save]          이 기기/localhost 자체 점검 (z2scan help)
        |  z2scan diff                   기준에서 달라진 것만 (종료 코드 1 = 새것)
        |  z2scan net|host|cve           localhost에 nmap/lynis/trivy (밖은 명시적 허가)
        |
        |[도움말]
        |  z2help | z2term               이 목록
        |
        |위의 명령은 모두 스스로 설명합니다: --help를 붙이세요 (예: 'z2-tile --help').
        |더 보기: 'z2adb help', 인수 없이 'z2gui', 또는 HANDBOOK의 11장.
    """,
            "help.edge" to "[플로팅 패널]\nz2-edge --help   패널 정의 및 내용 업데이트\nz2-key --help    Android 전역 동작\nz2-shot --help   둘러싼 부분만 캡처\nz2-app --help    앱 목록 및 아이콘 내보내기\nz2-action --help 이름이 있는 Android 동작 매크로\nz2-ui --help     AI 앱 조작(도움말은 AI용 안내서)",
            "macro.rssBody.head" to """
#
# 준비:
#   1) 피드 URL을 한 줄에 하나씩:  ~/.z2term/rss/feeds.txt
#   2) 30분마다 살펴보기:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 선택 - 묻히면 안 되는 피드나 낱말을 한 줄에 하나씩:  ~/.z2term/rss/important.txt
#      맞는 것은 따로 알림을 받으므로, 글이 많은 피드에 덮이지 않습니다.
#      URL의 일부나 제목의 일부를 적으세요 (예: example.org).
#   4) 알림 동작을 자동화에 등록하기:
#        z2-when event:notify_action run '$rssNotificationAction'
#      「열기」는 그 글을 브라우저로, 「목록」은 모은 글을 읽을거리로 만들어 z2term 안에서 엽니다.
#      참고: 알림 버튼에서 화면을 열려면 「다른 앱 위에 표시」 권한이 필요합니다 (엣지 패널과 같은 권한).
#   5) 모은 글의 목록 열기: sh ~/.z2term/macros/rss.sh view
#      명령 목록 -> 자동화 -> 자동화 규칙에서 두 규칙을 관리합니다.
#      알림 규칙이 이미 있으면 새로 추가하지 말고 기존 규칙을 편집하세요.
#   6) 새 글 알림: 처음에는 정기 확인에서는 알리고, 목록에서 가져올 때는 알리지 않습니다.
#        sh ~/.z2term/macros/rss.sh notify off        # 정기 확인 알림: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # 목록의 「새로 고침」「글 목록」 알림: on / off
#      둘 다 목록의 「설정」 스위치로도 언제든 바꿀 수 있습니다.
#
# 필요: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   썸네일 이미지(목록의 설정)를 쓰려면 Pillow도 필요합니다 (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# 배터리: 자주 볼수록 더 듭니다. 30분보다 짧게는 하지 마세요.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "seen/latest에 남길 최대 줄 수",
            "macro.cNoPy" to "python3가 필요합니다 (예: apk add python3)",
            "macro.cWriteFeeds" to "피드 URL을 한 줄에 하나씩 여기에 적으세요:",
            "macro.cFeedsHint" to "# 피드 URL을 한 줄에 하나씩 (#으로 시작하는 줄은 무시합니다)",
            "macro.cSkipFail" to "받아오거나 해석하지 못한 피드는 조용히 건너뜁니다 (나머지는 계속합니다)",
            "macro.cDiff" to "# 이미 본 것을 빼서 새것만 남깁니다 (z2scan의 기준 비교와 같은 수법입니다).\n# 피드의 날짜도 순서도 믿지 않습니다: 둘 다 믿을 것이 못 됩니다.",
            "macro.cPrepend" to "# 새것을 위에 쌓아 위젯의 \"앞 (head)\" 모드에서 제대로 읽히게 합니다.",
            "macro.cNotify" to "%s개 새것",
            "macro.cOpen" to "열기",
            "macro.cListNone" to "아직 모은 것이 없습니다 — 먼저 인수 없이 한 번 실행하세요.",
            "macro.cListHint" to "목록을 보려면: rss.sh list [개수]",
            "macro.cList" to "목록",
            "macro.cPageTitle" to "구독 중인 피드",
            "macro.cPageSites" to "%s개 사이트",
            "macro.cPageArticles" to "글 %s개",
            "macro.cPageDoc" to "# 읽을거리(HTML)를 만듭니다. z2-view가 앱 안에서 엽니다 (서버도 브라우저도 필요 없음).\n" +
            "# 사이트별로 묶고 새것부터, 글마다 「열기」를 둡니다. 휴대전화 테마가 CSS 변수로 옵니다.",
            "macro.cViewHint" to "읽으려면: rss.sh view",
            "macro.cHitMax" to "한 번에 낼 글별 알림의 최대 개수 (나머지는 요약으로 갑니다)",
            "macro.cSplitDoc" to "# 놓치면 안 되는 것을 갈라냅니다. 요약의 본문에는 3줄만 들어가므로, 글이 많은 피드가\n" +
            "# 동시에 갱신되면 중요한 한 건이 밀려납니다. 맞은 글은 **알림을 따로** 냅니다\n" +
            "# (알림 id는 앱이 건마다 따로 매기므로, 나누면 덮어쓰지도 생략하지도 않습니다).",
            "macro.cNameDoc" to "# URL을 알림의 이름에 넣어 둡니다. 버튼을 누르면 notify_action의 {name}으로 그대로\n" +
            "# 돌아오므로, 알림이 여러 개 떠 있어도 어느 것의 「열기」인지 헷갈리지 않습니다 (준비 4).",
            "macro.cListDoc" to "# 모은 것을 읽기 좋은 목록으로 찍습니다 (색 없이. 파일이나 위젯과 똑같이 보여야 합니다).\n" +
            "# 터미널에서는 제목 자체를 OSC 8 링크로 만들고 URL 줄은 넣지 않습니다. 긴 URL은 줄바꿈되어\n" +
            "# 제목과 섞이고, 목록으로 읽을 수 없게 되기 때문입니다. 터미널이 아닐 때(파이프·리다이렉트)는\n" +
            "# 이스케이프가 방해되므로 맨 텍스트로 떨어뜨리고 URL도 보이게 합니다.\n" +
            "# latest.txt는 한 줄 한 글의 날 데이터 그대로 둡니다 (위젯의 tail과 rss-open.sh가 읽습니다).",
            "macro.cSettings" to "설정",
            "macro.cArchive" to "보관함",
            "macro.cDelete" to "삭제",
            "macro.cThumbs" to "썸네일 이미지",
            "macro.cSave" to "지난 글 보관",
            "macro.cNotifyPoll" to "새 글 알림 (정기 확인)",
            "macro.cNotifyView" to "새 글 알림 (목록에서 가져올 때)",
            "macro.cDeleteSite" to "이 사이트의 글 모두 삭제",
            "macro.cConfirmSite" to "%h의 글 %s개를 목록에서 삭제할까요?",
            "macro.cArticles" to "글 목록",
            "macro.cPrev" to "이전",
            "macro.cNext" to "다음",
            "macro.cCount" to "%s개",
            "macro.cUndated" to "날짜 없음",
            "macro.cArchiveEmpty" to "아직 보관한 글이 없습니다. 설정에서 지난 글 보관을 보관으로 바꾸면 모은 글이 여기에 남습니다.",
            "macro.cSearch" to "검색",
            "macro.cSearchWord" to "찾을 말 (띄어 쓰면 모두 포함한 글)",
            "macro.cByMonth" to "월별로 보기",
            "macro.cNoHit" to "찾지 못했습니다.",
            "macro.cDropMonth" to "이 달 삭제",
            "macro.cDropConfirm" to "%m에 보관한 글 %s개를 삭제할까요? 되돌릴 수 없습니다.",
            "macro.cOnOff" to "on 또는 off를 지정하세요",
            "macro.cNoPillow" to "썸네일 이미지에는 Python의 Pillow가 필요합니다 (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# 준비: 모으는 쪽을 먼저 갖추세요
#   z2-macro install rss
# 위젯: 상태 위젯 설정에서 「rss-open」을 버튼에 배정하세요.
#
# z2-run: 상태 위젯의 버튼에 「rss-open」을 배정하세요 (여기서 시험하려면: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "아직 글이 없습니다",
            "macro.cAllRead" to "새로 열 것이 없습니다",
            "macro.cPick" to "# latest.txt의 줄은 \"제목  URL\"입니다. 아직 열지 않은 첫 URL을 고릅니다.",
            "macro.cCap" to "연 글의 목록이 끝없이 늘어나지 않게 제한합니다",
            "macro.copied" to "복사한 코드: ${d}{code}",
            "macro.cleared" to "복사한 코드를 지웠습니다",
            "macro.otpTitle" to "일회용 코드",
            "macro.cTtl" to "복사를 지우기까지의 초 (곧바로 들어갔을 때만 해당합니다)",
            "macro.cCode" to "# 앱이 이미 코드를 뽑아냈습니다. 뽑지 못했을 때는 아무것도 하지 않습니다.",
            "macro.cTry" to "# 먼저 곧바로 넣어 봅니다. 이것은 **앱을 보고 있는 동안**에만 들어갑니다:\n" +
            "# Android 10+ solo deja escribir en el portapapeles a la aplicación de delante, así que lo que\n" +
            "# corre en segundo plano se descarta en silencio. Lo leemos de vuelta para ver si entró.",
            "macro.cFallback" to "  # 들어가지 않았다 = 앱을 보고 있지 않다. 알림의 「복사」 버튼으로 건넵니다\n" +
            "  # (al pulsarlo, la aplicación pasa un instante a primer plano).\n" +
            "  # ⚠ Lo que entra por ahí no lo borra el TTL de abajo: borrar también exige estar delante.",
            "macro.cClear" to "# TTL이 지나면, 우리가 복사한 코드가 아직 남아 있을 때만 클립보드를 비웁니다\n" +
            "# (lo que se haya copiado después se deja en paz).",
            "macro.cHow" to """
# ■ 전화 알림의 전화번호 복사
#   제목과 본문 순서로 전화번호를 찾아 알림의 "복사" 버튼으로 제공합니다.
#   연락처를 조회하지 않으며 저장 여부와 관계없이 번호가 표시되면 대상입니다.
#   이름만 있으면 번호를 얻을 수 없습니다. 알림 접근 권한만 사용합니다.
#   기존 규칙을 유지하기 위해 파일 이름 unknown-call.sh를 그대로 둡니다.
""",
            "macro.cIsNum" to """
# 번호 그대로인가? 전화번호에 들어갈 수 있는 문자를 모두 지웁니다
# (숫자 + - ( ) 공백). **아무것도 남지 않고** 숫자가 7~15자리면 번호로 봅니다.
#   -> 이름이나 다른 글자가 섞인 칸은 번호가 아니므로 다른 칸을 확인합니다.
#   -> 「알 수 없음」 / 「발신번호 표시제한」도 여기로 떨어집니다 (원하면 아래 검사를 느슨하게 하세요).
# ⚠ case [!...]로 쓰지 마세요: 패턴의 )를 case의 구분자로 읽습니다.
""",
            "macro.cScan" to "# 거는 사람은 보통 제목에 들어오지만, 어떤 전화 앱은 본문에 넣습니다. 둘 다 봅니다.",
            "macro.cSkip" to "# 어느 칸에도 번호가 없으면 복사 알림을 띄우지 않습니다.",
            "macro.cWhat" to "# category가 둘 중 어느 쪽이 발생했는지 알려 줍니다 (등록한 notify:category=와 같은 값입니다).",
            "macro.cCopy" to """
# 번호는 알림의 「복사」 버튼(-c)으로 건넵니다. 여기서 z2-clip set을 불러도 들어가지 않습니다:
# Android 10 이상은 앞에 있는 앱만 클립보드에 쓸 수 있는데, 통화 중에는 그것이 전화 앱이기
# 때문입니다. 버튼을 누르는 그 순간에는 z2term이 앞으로 나옵니다.
""",
            "macro.missed" to "부재중 전화",
            "macro.incoming" to "걸려 온 전화",
            "macro.title" to "전화번호 복사",
            "macro.stNew" to "새것",
            "macro.stSame" to "같음",
            "macro.stDiff" to "다름",
            "macro.msgInstalled" to "설치했습니다:",
            "macro.msgSame" to "이미 같은 것이 설치되어 있습니다:",
            "macro.msgOutdated" to "내 것이 함께 실은 것과 다릅니다:",
            "macro.msgHowUpdate" to "먼저 보기: z2-macro diff %s   /   함께 실은 것으로 바꾸기: z2-macro install -f %s (내가 고친 것도 함께 사라집니다)",
            "macro.msgNotInstalled" to "아직 설치되지 않았습니다 (먼저 z2-macro install을 실행하세요):",
            "macro.msgRemoved" to "지웠습니다:",
            "macro.msgAbsent" to "설치되어 있지 않습니다:",
            "macro.msgRemoveDiffers" to "내 것이 함께 실은 것과 달라서 지우지 않았습니다:",
            "macro.msgHowRemove" to "먼저 보기: z2-macro diff %s   /   그래도 지우기: z2-macro remove -f %s (내가 고친 것도 함께 사라집니다)",
            "macro.msgNoDiffTool" to "여기에는 diff가 없습니다. 함께 실은 것은 z2-macro show로 읽을 수 있습니다.",
            "macro.msgSameAsBundled" to "함께 실은 것과 같습니다.",
            "macro.msgNotFound" to "그런 예제가 없습니다:",
            "macro.msgHintResident" to "계속 돌리려면 설정 -> 상주 서버에 이것을 등록하세요:",
            "macro.msgHintRun" to "실행하는 법:",
            "macro.remindBody.head" to """
#
# 준비 (한 번만):
#   sh ~/.z2term/macros/remind.sh setup   훅 둘과 타일 둘을 등록합니다
#
# 문법:
#   remind.sh <언제> <내용>          리마인더를 추가합니다
#   remind.sh list                   목록을 봅니다 (번호가 앞에 옵니다)
#   remind.sh del <n|all>            취소합니다 (rm도 됩니다)
#   remind.sh peek                   목록을 알림으로 보여 줍니다 (삭제 버튼이 붙습니다)
#   remind.sh ask                    알림의 응답 칸으로 묻습니다 (타일용)
#   remind.sh setup                  훅과 타일을 등록합니다 (한 번만)
#   remind.sh help                   이 도움말
#
# <언제> - 한 번만:
#   30m / 90s / 2h                   지금부터
#   18:30                            다음 18:30 (이미 지났으면 내일)
#   sat 21:00                       다음 해당 요일에 한 번 (오늘 시간이 남았으면 오늘)
#   일본어: 土曜日の夜九時 = sat 21:00; 朝七時 = 07:00; 夜九時半 = 21:30
#   夜12時는 모호합니다. 자정은 날짜와 00:00으로 지정하세요.
#   tomorrow 18:30 / 3d 07:00        날짜 단위 (3d = 3일 뒤)
#   07/30 19:00                      월/일 (이미 지났으면 내년)
#   2030 07/30 19:00                 해까지 적기
#   203007301900 / 07301900          숫자만 (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ 시각을 적지 않으면 **지금 시각**이 그대로 쓰입니다 (3d / 07/30 ...)
#
# <언제> - 되풀이:
#   daily 07:00 / weekday 09:00      날마다 / 월~금
#   weekly mon 09:00                 그 요일에
#   monthly 15 09:00                 그 날짜에
#   yearly 07/30 19:00               그 달 그 날에
#   every 19:00 / every wed 19:00    「every」만 써도 됩니다 (다음 낱말이 정합니다)
#   every 15 19:00 / every 07/30 19:00  -> 매일 / 매주 / 매달 / 매년
#
# 예:
#   remind.sh 30m 약 먹기
#   remind.sh tomorrow 18:30 쓰레기 내놓기
#   remind.sh monthly 25 10:00 월세
#   remind.sh list        ->  1  ⏰ 07/30 18:30  쓰레기 내놓기
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (그다음부터는: remind.sh 30m ...)
""",
            "macro.cStore" to """
# 리마인더 하나 = ${d}DIR/<id>.txt의 한 줄, 탭으로 나눔: 종류 / 라벨 / when-id / 내용.
#   종류: once (아직 울리지 않음) / fired (울렸음) / repeat (되풀이)
#   when-id: repeat에만 있습니다 (지울 때 z2-when remove를 부를 수 있도록). 나머지는 '-'.
# 내용을 맨 뒤에 두어 TAB만 아니면 무엇이든 담을 수 있게 했습니다.
""",
            "macro.cParse" to """
# 1~3개의 낱말을 읽고 정합니다:
#   KIND ... once|repeat   PLAN ... 보여 줄 라벨   SPEC ... z2-alarm/z2-when이 받는 것   USED ... 쓴 낱말 수
""",
            "macro.pDaily" to "매일 ",
            "macro.pWeekday" to "평일 ",
            "macro.pWeekly" to "매주 ",
            "macro.pMonthly" to "매달 ",
            "macro.pYearly" to "매년 ",
            "macro.mDay" to "",
            "macro.eDaily" to "매일",
            "macro.eWeekly" to "매주",
            "macro.eMonthly" to "매달",
            "macro.eYearly" to "매년",
            "macro.mBadDate" to "그런 날짜가 없습니다 (예: 07/30):",
            "macro.cEvery" to """
  # 짧은 「every ...」 형태. **다음 낱말의 생김새**로 매일/매주/매달/매년을 고릅니다:
  #   every 19:00 -> 매일 / every wed 19:00 -> 매주 / every 15 19:00 -> 매달 /
  #   every 07/30 19:00 -> 매년
  # ⚠ 낱말의 개수는 달라지지 않으므로(낱말 하나가 다른 하나를 대신할 뿐) USED는 그대로 씁니다.
""",
            "macro.cExpand" to "# \"every\" 뒤에 오는 낱말로 어떤 되풀이인지 정합니다.",
            "macro.cDigits" to """
    # 숫자만으로 된 형태: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ 12자리를 8자리보다 먼저 봅니다 (8자리 패턴은 12자리 문자열에도 맞기 때문입니다).
""",
            "macro.cYmd" to """
  # 날짜로 쓴 형태. ⚠ z2-alarm은 날짜를 받지 못하므로 이것도 지연 시간으로 바꿉니다.
""",
            "macro.cCivil" to """
# 달력 날짜 -> 1970-01-01부터의 일수 (윤년 포함. Howard Hinnant의 days_from_civil).
# ⚠ busybox에서는 date -d "2026-07-30"을 믿을 수 없으므로 여기서 직접 셉니다.
# ⚠ 일수의 차이만 쓰고 시각은 [day_epoch]에 맡기므로 시간대가 서로 지워집니다.
""",
            "macro.cCron" to """
# HH:MM과 요일 칸에서 cron 식(분 시 일 월 요일)을 만듭니다.
# 앞의 0을 지우는 데 expr을 쓰지 마세요: 결과가 0이면 종료 코드 1을 내므로
# `expr "${d}m" + 0 || echo "${d}m"`의 오른쪽까지 돌아 값이 두 줄이 되어 버립니다.
""",
            "macro.afterLabel" to """
# "30m" -> "30m 뒤 (14:35)". 시계 시각을 함께 보여 주면 바로 확인할 수 있습니다.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}u 뒤 (${d}at)" || echo "${d}num${d}u 뒤"
}
""",
            "macro.mUsageAdd" to "사용법: remind.sh <언제> <내용>",
            "macro.mBadWhen" to "시각을 알아듣지 못했습니다 (예: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "시각은 HH:MM으로 쓰세요:",
            "macro.mBadRange" to "시각이 범위를 벗어났습니다 (00:00-23:59):",
            "macro.mNoTime" to "시각을 적지 않았습니다 (예: daily 07:00):",
            "macro.mBadDow" to "모르는 요일입니다:",
            "macro.mPastTime" to "그 시각은 이미 지났습니다:",
            "macro.cDays" to """
    # 날짜 단위의 형태. 시각이 없으면 **지금 시각**을 그대로 씁니다 (기본값을 지어내지 않습니다).
    # 한 낱말로 붙여 올 수도 있으므로 붙여 쓴 형태도 받습니다.
""",
            "macro.cDayEpoch" to """
# 「HH:MM, N일 뒤」를 epoch 초로 바꿉니다. ⚠ busybox에는 date -d "tomorrow"가 없으므로
# 먼저 오늘 자정을 구한 뒤 일수와 시각을 더합니다.
# ⚠ 앞에 0이 붙은 값("08")은 ${d}(())가 8진수로 읽으므로 떼어내야 합니다.
# ⚠ 하루를 86400초로 더하므로, 서머타임이 바뀌는 날에는 한 시간 어긋날 수 있습니다.
""",
            "macro.cFmtAt" to """
# 목록에 보여 줄 라벨. ⚠ 「tomorrow」를 그대로 두면 날짜가 바뀐 뒤에 잘못 읽힙니다.
""",
            "macro.mNoBody" to "무엇을 알려 줄지 말해 주세요",
            "macro.mNoAlarm" to "예약하지 못했습니다",
            "macro.mNone" to "예약된 것이 없습니다",
            "macro.mFired" to " (울렸음)",
            "macro.mRemoved" to "지웠습니다:",
            "macro.mNoSuchNum" to "그런 항목이 없습니다 (remind.sh list를 보세요):",
            "macro.mUsageDel" to "사용법: remind.sh del <n|all>",
            "macro.bDone" to "완료",
            "macro.bDelete" to "삭제",
            "macro.mAskDel" to "어느 것을 지울까요?",
            "macro.mAskDelH" to "번호 / all",
            "macro.mDeletedTitle" to "🗑 지웠습니다",
            "macro.cPeek" to """
# 목록을 알림으로 보여 줍니다 (「목록」 타일에서). **번호를 남깁니다**: 지울 때 가리키는 것이
# 그 번호이기 때문입니다. ⚠ 지울 방법 없이 나열만 한다는 불만에서 나온 버튼입니다.
""",
            "macro.cAskDel" to """
# 「삭제」 버튼: 번호를 물어 [cmd_del]에 넘깁니다 (터미널에서 쓰는 것과 같은 길입니다).
""",
            "macro.bS1" to "+10분",
            "macro.bS2" to "+1시간",
            "macro.mAgain" to "- 다시 알림",
            "macro.mTitle" to "⏰ 리마인더",
            "macro.mAsk1" to "무엇을 알려 드릴까요?",
            "macro.mAsk1H" to "예: 약 먹기",
            "macro.mAsk2" to "언제?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "다시 입력해 주세요",
            "macro.mAskGiveUp" to "${ASK_TRIES}번 알아듣지 못했습니다. 터미널에서 넣을 수도 있습니다: remind.sh 30m 약 먹기",
            "macro.mOkTitle" to "⏰ 리마인더를 맞췄습니다",
            "macro.mNgTitle" to "⚠ 맞추지 못했습니다",
            "macro.cHooks" to """
  # 「리마인더가 울렸다」에 하나, 「알림의 버튼을 눌렀다」에 하나. 리마인더를 아무리 많이
  # 넣어도 이 둘뿐입니다. 어느 쪽도 감지에 기대지 않습니다.
""",
            "macro.cTiles" to """
  # 빈 자리와 이미 우리 것인 자리만 채웁니다 (남의 것은 절대 덮지 않습니다).
  # 인수가 붙은 매크로 이름은 0.8.275부터 됩니다 (그 전에는 sh + 전체 경로로 쓰세요).
""",
            "macro.mSetupHooks" to "등록한 훅:",
            "macro.mSetupTiles" to "타일:",
            "macro.mPathHint" to "참고: 이 탭에서는 아직 remind.sh를 이름만으로 실행할 수 없습니다. 새 탭을 열거나, 여기서 이렇게 실행하세요 " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH.",
            "macro.cPathHint" to """
  # ⚠ 매크로 폴더가 PATH에 들어간 것은 0.8.287부터라서 **그 전에 연 탭**은 예전 PATH를
  # 그대로 씁니다. 이름이 풀리는지 확인하고, 안 되면 어떻게 할지 알려 줍니다
  # (그러지 않으면 `command not found`의 이유가 보이지 않습니다).
""",
            "macro.mPlace" to "참고: 타일은 빠른 설정의 연필(편집) 화면에서 직접 놓아야 합니다.",
            "macro.lRemind" to "리마인드",
            "macro.lList" to "목록",
            "macro.cSelf" to "# 우리 알림에만 반응합니다 (우리 id는 언제나 r로 시작합니다)",
            "macro.cKeepRepeat" to "      # 되풀이되는 것은 여기서 지우지 않습니다: 내일 또 울려야 하기 때문입니다.",
            "macro.cAsk" to """
# 타일이나 알림에서 쓰는 길. ⚠ **언제나 알림으로 답합니다**: 여기서는 아무도 터미널을 보고
# 있지 않으므로, stderr로 실패하면 「눌렀는데 아무 일도 없었다」로 읽힙니다
# (일은 있었습니다. 이유가 타일의 run.log에만 남았을 뿐입니다).
#   알아듣지 못하면 -> 왜인지 말하고 다시 묻습니다. 최대 ${ASK_TRIES}번 (앞의 답은 그대로 둡니다)
#   예약되면      -> 계획과 내용을 알림으로 보여 줍니다
""",
            "macro.cSnoozeCancel" to "완료로 표시할 때 미뤄 둔 알람이 있으면 함께 지웁니다",
            "macro.qrBody.head" to """
#
# 사용법:
#   qr.sh "https://example.com"       문자열을 코드로 만들어 여기에 그립니다
#   qr.sh -f notes.txt                파일의 내용을 코드로 만듭니다
#   z2-clip get | qr.sh               클립보드에 있는 것을 코드로 만듭니다
#   qr.sh -o ~/qr.png "텍스트"        PNG로 저장합니다 (아무것도 그리지 않습니다)
#   qr.sh -t "텍스트"                 그림 대신 블록 문자로 찍습니다
#   qr.sh -s 24 "텍스트"              크기를 정합니다 (열 수. 기본값은 너비에 맞춥니다)
#
# z2-run: qr.sh "https://example.com"   (한 번 설치하면 이름만으로 됩니다. 한 번 돌고 끝납니다)
#
# # 요건 (이것이 없으면 되지 않거나 다르게 보입니다)
#
#   1) 부호화는 앱이 합니다 ... z2term 탭 안에서는 설치할 것이 없습니다 (z2-qr encode).
#      qrencode가 이미 있으면 그쪽을 씁니다. ssh로 들어간 곳처럼 앱에 손이 닿지 않는
#      곳에서는 탭(배포판)마다 한 번 설치하세요:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      둘 다 없을 때만 설치 명령을 찍고 스크립트가 멈춥니다.
#
#   2) 그림(기본값)은 이 앱의 탭 안에서만 보입니다 ... kitty graphics로 그리기 때문입니다.
#      ssh 너머나 그림을 낼 수 없는 터미널에서는 뜻 모를 문자만 쏟아집니다. 거기서는 -t를 쓰세요.
#
#   3) -t (블록 문자)는 터미널의 글꼴을 탑니다 ... 줄 사이가 벌어지는 글꼴이면 눈으로는 읽히지만
#      카메라로는 읽히지 않습니다. 카메라로 스캔하려면 기본 그림이나 -o의 PNG가 낫습니다.
#
#   4) 가로세로 비율은 짐작입니다 ... 아래의 「비율」을 보세요. 눌려 보이면 Z2_QR_ASPECT.
#
# 옵션:
#   -f 파일     입력을 파일에서 읽습니다 (없으면 인수, 그것도 없으면 표준 입력)
#   -o PNG      그리는 대신 PNG로 저장합니다. 코드가 여러 개면 qr-1.png / qr-2.png ...로
#               이름을 붙입니다
#   -t          블록 문자로 찍습니다. 그림을 낼 수 없는 터미널과 ssh 너머용입니다
#   -s N        그릴 때의 열 수. 기본값은 최대 34이며 너비에 따라 줄어듭니다
#   -h          이 도움말
#
# 긴 입력에 대하여:
#   코드 하나에는 정해진 양만 들어갑니다 (많아야 영숫자 2953바이트. 스캔까지 되게 하려면 수백
#   바이트 정도). 900바이트가 넘는 입력은 줄바꿈에서 여러 코드로 나누어 [1/3]처럼 번호를 붙여
#   차례로 찍습니다.
#   ⚠ 한 줄이 코드 하나에 담기지 않을 만큼 길면 코드로 만들 수 없습니다.
#
# 비율:
#   터미널은 칸의 크기를 알려 주지 않으므로, 코드가 정사각형으로 나오도록 문자의 비율을 1:2로
#   짐작합니다. 눌려 보이면 변수로 조정하세요:
#     Z2_QR_ASPECT=0.45 qr.sh "텍스트"     (작을수록 길쭉해집니다. 기본값 0.5)
""",
            "macro.cMaxBytes" to "코드 하나에 담을 최대 바이트 (넘으면 나눕니다)",
            "macro.cPngModule" to "저장하는 PNG에서 모듈 하나당 점의 수",
            "macro.cTargetPx" to "그릴 때 맞출 픽셀 너비",
            "macro.cMargin" to "코드 둘레의 여백 (모듈 단위. 스캔하려면 필요합니다)",
            "macro.cUsageFn" to "앞머리의 주석 덩어리를 도움말로 찍습니다 (줄 수를 정해 두지 않습니다).\n" +
            "# Las líneas en blanco no lo cortan (lo decide NF), así que las secciones separadas por blancos quedan enteras.",
            "macro.cNeedEncoder" to "요건 1: 부호화할 도구. qrencode가 있으면 그것, 없으면 앱(z2-qr)을 씁니다.",
            "macro.mMissing" to "qr.sh: 부호화할 도구가 없습니다 - qrencode도 없고 앱(z2-qr)에도 손이 닿지 않습니다",
            "macro.mInstallOnce" to "  이 탭에서 한 번만 설치하세요:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "PNG를 만듭니다. qrencode는 시험 출력으로 한 칸의 픽셀을 정하고, 앱에는 목표 픽셀을 넘깁니다.",
            "macro.cMakeBlocks" to "블록 문자로 그립니다. 두 경로 모두 자기 색을 내보내므로 테마와 무관하게 읽힙니다.",
            "macro.qrBody.mNoTmp" to "작업 디렉터리를 만들 수 없습니다",
            "macro.qrBody.mUnreadable" to "읽을 수 없습니다:",
            "macro.qrBody.mEmpty" to "입력이 비어 있습니다",
            "macro.mSizeNum" to "-s에는 숫자가 필요합니다",
            "macro.qrBody.mUsageHint" to "qr.sh -h를 보세요",
            "macro.mPieceFail" to "qr.sh: %d번 코드를 만들 수 없습니다: %s",
            "macro.qrBody.cCollect" to "---- 입력을 모읍니다 ----",
            "macro.cSplit" to "---- 너무 길면 줄바꿈에서 나눕니다 ----",
            "macro.cSplit2" to "쌓인 합계가 MAX_BYTES를 넘기 직전에 다음 조각으로 넘어갑니다. 줄을\n# 한가운데서 자르지 않으므로 멀티바이트 문자가 깨지지 않습니다.",
            "macro.cTrim" to "마지막 줄바꿈을 뺍니다 (입력에 없던 줄바꿈은 절대 넣지 않습니다).",
            "macro.cSize" to "---- 터미널 너비에서 표시 크기를 정합니다 ----",
            "macro.cPngWidth" to "PNG의 픽셀 너비를 돌려줍니다 (바이트 16-19, IHDR).",
            "macro.cModulePx" to "맞출 픽셀 너비에서 거꾸로 모듈당 점의 수를 구합니다.",
            "macro.cInline" to "Kitty graphics 프로토콜로 PNG를 그 자리에 그립니다.\n# c/r로 몇 칸을 차지하는지 못박은 뒤, 그만큼 줄바꿈을 찍어 커서를 아래로 옮깁니다.",
            "macro.cEmit" to "---- 내보냅니다 ----",
            "macro.cAnsi" to "ANSIUTF8은 자기 색을 내보내므로, 테마가 무엇이든 밝고 어두움이 제대로 나옵니다.",
            "macro.mdBody.head" to """
#
# 사용법:
#   md.sh README.md                이 터미널에 보기 좋게 내보냅니다
#   md.sh -v README.md             앱 안에서 엽니다 (z2-view)
#   cat notes.md | md.sh           표준 입력에서 읽습니다
#   md.sh -o page.html README.md   HTML만 씁니다 (열지 않음)
#
# z2-run: md.sh README.md   (설치한 뒤에는 이름만으로 됩니다. 한 번 돌고 끝)
#
# 옵션:
#   -v        앱 안에서 엽니다 (z2-view)
#   -o FILE   HTML을 거기에 씁니다. 화면에는 내보내지 않습니다
#   -w N      N칸에서 줄을 바꿉니다 (기본: 화면 너비, 최대 100)
#   -p        색도 장식도 없이 (그냥 글자)
#   -t 제목   읽기 화면의 제목 (기본: 첫 제목, 없으면 파일 이름)
#   -h        이 설명
#
# 긴 문서를 한 화면씩: md.sh README.md | less -R   (-R 이라야 색이 지나갑니다)
#
# 따로 설치할 것은 없습니다. sh 와 awk 면 됩니다.
# 제목·문단·목록(중첩·번호·[ ]/[x])·인용·코드·표·구분선·링크·그림과
# 맨 앞의 --- 머리말을 다룹니다. ⚠ 밑줄식 제목(=== / ---)과
# 참조 링크([글자][1])는 쓴 그대로 나옵니다.
#
# 어떻게 보이는지:
#   - 그림이 본문 자리에 나오는 것은 이 앱의 탭 안에서만입니다 (kitty graphics로
#     z2-img가 그립니다). ssh로 들어간 곳에서는 설명과 경로만 나옵니다.
#   - 링크는 누를 수 있습니다 (OSC 8). 지원하지 않는 터미널에서는 글자 그대로입니다.
#   - 이 터미널은 이탤릭을 그리지 않으므로 *강조* 는 밑줄로 냅니다 (man과 같습니다).
#   - 화면 너비에 안 들어가는 표는 "제목: 내용" 형태로 풀어서 냅니다.
#   - -v 는 기기에 있는 그림을 페이지에 박아 넣습니다 (한 장 1.5MB까지). 읽기 화면은
#     네트워크에서 아무것도 가져오지 않으므로 http(s) 그림은 설명만 남습니다.
""",
            "macro.mdBody.cCollect" to "# 입력을 모읍니다 (여러 파일은 준 순서대로 이어 붙여 한 편으로 만듭니다).",
            "macro.cTitle" to "# 제목: -t → 첫 번째 # 제목 → 파일 이름",
            "macro.cWidth" to "# 화면의 너비",
            "macro.cColorEnv" to "# 색을 파이프(페이저 등)로 보내고 싶을 때 / 보내고 싶지 않을 때의 도피구",
            "macro.cImgWhere" to "# 그림이 본문 자리에 나오는 것은 탭 안(kitty graphics)에 z2-img가 있을 때뿐입니다.",
            "macro.cAmbi" to "# 애매한 너비의 문자(괘선·화살표·동그라미 숫자)를 몇 칸으로 셀지. 설정에서\n" +
            "# '애매한 너비'를 전각으로 둔 사람은 Z2_MD_AMBIWIDTH=2. 기본값은 앱과 같은 1.",
            "macro.cAwkFile" to "# ⚠ awk 본체는 **파일에 써서 -f 로 넘깁니다**. 인수로 넘기면, 다바이트 문자(이 주석의\n" +
            "#   일본어 같은)가 gawk가 4096바이트마다 읽는 경계에 걸쳤을 때 프로그램이 거기서\n" +
            "#   끝난 것으로 취급되어 \"unexpected newline or end of string\" 으로 죽습니다.",
            "macro.cInlineImg" to "# 읽기 화면용 HTML에 그림을 data: URI로 박아 넣습니다.\n" +
            "# ⚠ z2-view는 바깥 그림을 가지러 가지 않으므로(JavaScript도 통신도 꺼 두었습니다)\n" +
            "#   박아 넣지 않으면 그림은 나오지 않습니다. 페이지 전체로 4MB까지.",
            "macro.cImgLoop" to "# 그림이 있는 문서만 한 줄씩 읽습니다 (그림 자리에서 z2-img를 부르기 위해)",
            "macro.mdBody.mUsageHint" to "사용법은 md.sh -h",
            "macro.mdBody.mNoTmp" to "임시 디렉터리를 만들 수 없습니다",
            "macro.mdBody.mUnreadable" to "읽을 수 없는 파일:",
            "macro.mdBody.mEmpty" to "내용이 없습니다",
            "macro.mWidthNum" to "-w 는 숫자로",
            "macro.mNoDir" to "둘 곳을 만들 수 없습니다",
            "macro.mConvFail" to "변환에 실패했습니다",
            "macro.mNoView" to "z2-view가 없습니다 (앱 업데이트가 필요합니다). HTML은 여기 있습니다:",
            "macro.mImgLabel" to "[그림]",
            "menu.noApps" to "(찾은 애플리케이션이 없습니다)",
            "menu.cat.AudioVideo" to "소리와 영상",
            "menu.cat.Development" to "개발",
            "menu.cat.Graphics" to "그래픽",
            "menu.cat.Game" to "게임",
            "menu.cat.Network" to "인터넷",
            "menu.cat.Office" to "사무",
            "menu.cat.System" to "시스템",
            "menu.cat.Utility" to "유틸리티",
            "menu.cat.Other" to "기타",
            "menu.usage" to "사용법: z2menu [list]   (인수 없음: openbox pipe menu XML)",
            "reminderView.labels" to "리마인더|추가|삭제|내용|날짜와 시간|반복|한 번|매일|평일|매주 (선택한 날짜의 요일)|시간 직접 입력 (선택 사항, 날짜와 반복보다 우선)|예정|반복|알림 보냄|리마인더가 없습니다|이 리마인더와 남은 알림 예약을 삭제할까요?",
            "rssFeeds.rssFeedsLabel" to "구독",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM}이(가) 시작되지 않았습니다. /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log를 보세요.",
            "scan.mNoPm" to "z2scan: 지원하는 패키지 관리자(apk/apt-get/pacman)를 찾지 못했습니다.",
            "scan.mInstalling" to "z2scan: 설치하는 중:",
            "scan.mInstallFail" to "z2scan: 설치에 실패했습니다:",
            "scan.mRemoteBlocked" to "z2scan: 바깥 대상은 거절했습니다. localhost만 검사하거나, 시험할 권한이 있는 대상에만 --allow-remote를 붙이세요.",
            "scan.mRemoteWarn" to "z2scan: 경고 바깥 호스트를 검사하려 합니다. 시험할 권한을 명시적으로 받은 시스템에만 하세요.",
            "scan.mNoLynis" to "z2scan: lynis가 없어 내장 자체 점검('z2scan self')으로 대신합니다. 'z2scan setup'으로 설치할 수 있습니다.",
            "scan.mNoCve" to "z2scan: CVE 검사 도구(trivy/grype)를 찾지 못했습니다. trivy나 grype를 설치한 뒤 다시 실행하세요.",
            "scan.mSelfHead" to "== z2scan 자체 점검 (이 기기 / localhost) ==",
            "scan.mUidRoot" to "uid 0으로 돌고 있습니다 (참고: proot/z2root의 root는 흉내입니다)",
            "scan.mPubPort" to "모든 인터페이스에서 듣고 있는 TCP 포트 (localhost 전용이 아님), 포트",
            "scan.mSshdEmpty" to "sshd_config가 빈 비밀번호 로그인을 허용합니다 (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config가 비밀번호 인증을 켜 두었습니다 (PasswordAuthentication yes). 키 인증이 더 안전합니다",
            "scan.mSshdRoot" to "sshd_config가 root 로그인을 허용합니다 (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys의 권한이 너무 열려 있습니다, 모드",
            "scan.mSshDir" to "~/.ssh의 권한이 너무 열려 있습니다, 모드",
            "scan.mWorldWrite" to "누구나 쓸 수 있는 파일이 있습니다 (최대 20개까지 보여 줍니다):",
            "scan.mSuid" to "SUID 바이너리가 있습니다 (참고용. proot/z2root에서는 root가 흉내입니다):",
            "scan.mPath" to "PATH에 빈 항목이나 '.'이 있습니다 (현재 디렉터리를 PATH에 넣는 것은 위험합니다)",
            "scan.mFound" to "발견:",
            "scan.mClean" to "뚜렷한 문제는 찾지 못했습니다.",
            "scan.mSaved" to "z2scan: 기준을 저장했습니다:",
            "scan.mSaveFail" to "z2scan: 기준을 저장하지 못했습니다:",
            "scan.mNoBase" to "z2scan: 아직 기준이 없습니다. 'z2scan self --save'를 한 번 실행해 지금 상태를 남기세요.",
            "scan.mNoChange" to "[ OK ] 기준에서 달라진 것이 없습니다.",
            "scan.mAdded" to "== 기준에 견줘 새로 생긴 것 ==",
            "scan.mRemoved" to "== 기준에 견줘 없어진 것 ==",
            "scan.mCleared" to "z2scan: 기준을 지웠습니다.",
            "scan.mLangDiff" to "z2scan: 경고 기준을 다른 언어로 저장했기 때문에 전부 달라진 것처럼 보입니다. 'z2scan self --save'로 다시 저장하세요.",
            "scan.langTag" to "ko",
            "scan.usageText" to """
        z2scan - 이 기기 / localhost의 보안 점검 (방어용. 밖으로 아무것도 보내지 않습니다).

        기본값은 localhost만 검사합니다. 검사 도구는 배포판의 공식 패키지에서 설치하며
        (nmap/lynis/trivy), 함께 싣는 것은 하나도 없고 결과도 기기 안에만 남습니다.

                  z2scan self [--save]        내장 자체 점검(바깥 도구 없이): 열린 포트,
                                              sshd 설정, SSH 키의 권한, 누구나 쓸 수 있는
                                              파일과 SUID, PATH. --save를 붙이면 결과를
                                              기준으로도 저장합니다.
                  z2scan diff [--quiet]       다시 점검해서 기준에서 달라진 것만
                                              출력합니다. 새것이 있으면 종료 코드 1을
                                              내므로 z2-when에 그대로 넣을 수 있습니다.
                                              --quiet를 붙이면 달라진 것이 없을 때 아무것도
                                              출력하지 않습니다.
                  z2scan baseline [clear]     저장한 기준을 보여 줍니다 (clear는 지웁니다)
                  z2scan setup                apk/apt-get/pacman으로 검사 도구(nmap,
                                              lynis)를 설치합니다
                  z2scan net [--allow-remote] [대상]
                                              nmap으로 TCP를 검사합니다. 기본 대상은
                                              127.0.0.1이며, 바깥 대상에는 --allow-remote가
                                              필요합니다.
                  z2scan host                 lynis로 호스트를 감사합니다 (없으면 'self'로
                                              대신합니다)
                  z2scan cve                  trivy/grype가 설치되어 있으면 그것으로
                                              rootfs의 알려진 CVE를 찾습니다

        시험할 권한을 명시적으로 받은 시스템만 검사하세요.
    """.trimIndent(),
            "translationMacro.description" to "별도로 설치한 명령으로 텍스트를 번역합니다.",
            "translationMacro.missing" to "이 로컬 환경에 trans 명령이 없습니다. 번역 CLI를 직접 설치한 뒤 다시 실행하세요. 자동 다운로드는 하지 않습니다.",
            "translationMacro.network" to "텍스트는 CLI의 외부 서비스로 전송됩니다. 해당 이용 조건과 네트워크 상태가 적용됩니다.",
            "translationSetup.setup" to "같은 로컬 Linux 환경의 터미널에서 다음 명령을 직접 실행한 후 번역을 다시 시도하세요.",
            "translationSetup.alpineRepo" to "패키지가 없으면 /etc/apk/repositories에서 같은 Alpine 버전의 community 저장소를 활성화하세요.",
            "translationSetup.ubuntuRepo" to "Ubuntu에서 패키지가 없으면 universe 저장소를 활성화한 후 다시 시도하세요.",
            "translationSetup.unknown" to "배포판을 확인할 수 없습니다. 사용 중인 로컬 Linux 환경에 맞는 명령을 선택하세요.",
            "voice.head" to """# voice.sh — "음성 조작"을 받는 곳. 말한 내용으로 음악·볼륨·타이머·리마인더·날씨 등을 움직입니다.
#
# 준비 (한 번만):
#   sh ~/.z2term/macros/voice.sh setup    규칙 4개를 등록 (음성 조작 / 음성 조작 (잠금 중) / 음성 타이머 / 걸음 수 세기)
#   걸음 수 세기 규칙은 오늘 걸음 수를 물을 수 있도록 측정을 유지할 뿐, 아무것도 실행하지 않습니다
#   그다음 빠른 설정 타일 "음성 조작"이나 디지털 어시스턴트(전원 버튼 길게 누르기 등)로 말합니다
#
# 터미널에서:
#   voice.sh words                 낱말 표 열기 (~/.z2term/voice/words-<언어>.txt. 말하는 법을 더하거나 바꿈)
#   voice.sh try <말>              말하지 않고 시험 (예: voice.sh try 다음 곡)
#   voice.sh listen                듣기 화면을 열고 말하기 (타일과 같음)
#   voice.sh lang [언어]           낱말 표의 언어 (voice.sh lang - 로 목록 보기)
#   voice.sh ai [off|codex|claude|agy]   AI를 쓸지 (처음에는 off)
#   voice.sh model [이름]          AI의 모델 (비우면 그 AI의 기본값)
#   voice.sh setup / help
#
# AI는 처음에 꺼져 있습니다. 켜도 신호 말("AI", "질문" 등)로 시작할 때만 묻습니다.
# 날씨는 "날씨"라고 말할 때만 wttr.in에 묻습니다 (장소: ~/.z2term/voice/config 의 weather_city=).
# 내가 정한 말: ~/.z2term/voice/commands.txt 에 "말 = 명령" (낱말 표보다 먼저 봅니다)
# 전화할 이름: ~/.z2term/voice/contacts.txt 에 "이름 번호" / 기록: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — 입문용 매크로. 기기에서 일어나는 일에 반응합니다.",
                "# 기다리는 일은 앱(z2-when)이 하므로 상주하지 않습니다: 일이 생기면 한 번 돌고 끝납니다.",
                "# 준비: 설정 -> \"시스템 이벤트 감지\"를 켜기",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (이어폰은 'event:headset_*'로 한 줄 더)",
            ),
            "macro.watchBasic.2" to listOf(
                "# 무슨 일이 있었는지는 Z2_WHEN_EVENT로 들어옵니다 (이름은 z2-when events가 알려 줍니다).",
                "# 직접 해 보려면:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT가 비어 있습니다. 직접 해 보려면: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"충전을 시작했습니다\" ;;",
                "  power_disconnected) z2-toast \"충전을 멈췄습니다\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — 배터리가 적을 때 알리되, 지금 상태에 맞춥니다.",
                "# z2-state로 화면이 켜져 있는지 보고, 켜져 있으면 토스트, 아니면 알림으로 알립니다.",
                "# 준비: 설정 -> \"시스템 이벤트 감지\"를 켜기",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when이 잔량을 넘겨주지만, 직접 실행해도 되도록 z2-state로 되받습니다.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# 충전 중이면 아무 말도 하지 않습니다 (실제로 줄고 있는 것이 아니므로)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"배터리 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"배터리 부족\" \"${d}{level}% 남았습니다\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — 아침마다 배터리와 연결 상태를 읽어 줍니다.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# OS 알람이 깨우므로 Doze 중에도 울립니다 (몇 분 늦을 수 있습니다).",
                "# 감지 스위치와는 상관없습니다.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=모바일; fi",
                "z2-say \"좋은 아침입니다. 배터리 ${d}{level} 퍼센트, 네트워크 ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — 알림에서 일회용 코드를 복사하고, TTL초 뒤에도 클립보드에 같은 값이",
                "# 남아 있으면 지웁니다.",
                "# 준비: 설정 -> \"알림 감지\"를 켜기 + OS의 알림 접근 허용",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15 이상은 민감한 알림의 코드를 가릴 수 있습니다. 확실한 길은 SMS 쪽",
                "#    (otp-sms.sh)입니다 — SMS 본문은 절대 가려지지 않습니다.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — 들어온 SMS에서 일회용 코드를 복사하고, TTL초 뒤에도 클립보드에 같은",
                "# 값이 남아 있으면 지웁니다.",
                "# otp-clip.sh의 SMS 판입니다. 알림과 달리 SMS 본문은 민감한 알림 보호(Android 15 이상)로",
                "# 가려지지 않고 잠긴 상태에서도 들어옵니다.",
                "# 준비: 설정 -> \"SMS 감지\"를 켜기 + OS의 SMS 권한 허용",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — 전화 알림에 포함된 전화번호를 복사 알림으로 보여 줍니다.",
                "# 알림의 \"복사\" 버튼을 누르면 번호가 클립보드에 들어갑니다 (누를 때까지 기다리는 이유는 아래에).",
                "# 준비: 설정 -> \"알림 감지\"를 켜기 + OS의 알림 접근 허용",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 부재중 전화도 잡으려면 규칙을 하나 더 등록하세요 (같은 스크립트, 다른 category):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — 피드를 살펴 새것만 남기고, 알림과 글로 알려 줍니다.",
                "# 다른 예제와 달리 이것은 상주하지 **않습니다**: 시각 트리거로 한 번 돌고",
                "# 끝나는 모양입니다.",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — rss.sh가 모아 둔 것을 새것부터, 누를 때마다 한 편씩 엽니다.",
                "# 상태 위젯의 매크로 버튼으로 올려 두면, 누를 때마다 다음 글이 열립니다.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — 알림으로 일깨워 줍니다: 한 번만(30m 뒤, 18:30에) 또는 되풀이해서",
                "# (daily / weekdays / weekly). 앱을 닫아 두어도 울립니다(OS 알람이 깨우므로 감지",
                "# 스위치가 필요 없습니다). 알림의 버튼으로 미룰 수 있습니다.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — 글이나 파일을 QR 코드로 만듭니다: 여기에 그리거나 PNG로 저장합니다.",
                "# 다시 입력하지 않고 다른 기기로 건네줍니다. 한 번 돌고 끝나며 상주하지 않습니다.",
            ),
            "macro.md" to listOf(
                "# md.sh — Markdown을 이 터미널에 보기 좋게 내보내거나 앱의 읽기 화면에서 엽니다.",
                "# 제목·목록·표·코드·링크를 정리하고, 그림은 본문 자리에 나옵니다. 한 번 돌고 끝납니다.",
            ),
            "macro.cImportantTemplate" to listOf(
            "# 묻히면 안 되는 피드나 낱말을 한 줄에 하나씩 (#으로 시작하는 줄은 무시합니다).",
            "# URL의 일부나 제목의 일부여도 됩니다. 맞는 것마다 따로 알림이 옵니다.",
            "# 예:",
            "#example.org",
        ),
            "macro.usage" to listOf(
            "사용법: z2-macro <하위 명령>",
            "  list               함께 실은 예제를 나열합니다 (new / same / differs로 표시)",
            "  install <이름|all> ~/.z2term/macros/로 복사합니다 (덮어쓰지 않습니다. -f로 강제)",
            "  remove <이름>      설치한 예제를 지웁니다 (함께 실은 것과 다르면 멈춥니다. -f로 강제)",
            "  diff <이름>        내 것과 함께 실은 것의 차이 (왼쪽이 내 것)",
            "  show <이름>        스크립트를 출력합니다",
            "  run <이름>         여기서 실행합니다 (멈추려면 Ctrl-C)",
            "  dir                매크로가 있는 곳을 출력합니다",
                "  actions ...        Android 동작 매크로 (z2-action --help)",
        ),
            "rssFeeds.labels" to listOf(
            "구독", "추가", "글 목록", "변경", "삭제", "피드 URL",
            "이 구독을 삭제할까요? 수집한 글은 유지됩니다.",
            "변경은 다음 수집부터 반영됩니다. 수집한 글은 유지됩니다.",
            "구독이 없습니다. 추가를 눌러 RSS / Atom URL을 입력하세요.",
            "공백이나 인증 정보가 없는 HTTP(S) URL을 입력하세요 (2048자 이내).",
            "이미 등록된 구독입니다.",
            "이 구독이 변경되었습니다. 목록을 새로 고친 후 다시 시도하세요.",
            "다른 변경을 저장 중입니다. 다시 시도하세요.",
            "이전", "다음",
        ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols" to 6,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の 한국어 版。上書きしていない文言は英語で出る。 */
private class ApiMsgKo(d: String) : Z2ApiMsg("ko", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — 문서를 선택하여 홈에 복사하고 경로를 반환합니다.\n# z2-file save FILE [MIME] — 저장 위치를 선택하여 파일 사본을 저장합니다.\n# 취소하면 실패합니다. 최대 512 MiB, 선택 제한 시간 5분."

    override val usbHelp: String get() = """
        |# z2-usb list              … 휴대전화에 연결된 USB 기기를 나열합니다
        |# z2-usb allow [번호]      … Linux 프로그램에서 쓸 수 있도록 Android에 권한을 요청합니다
        |# 기기를 연결한 뒤 allow를 한 번 실행하세요. 권한은 기기를 뽑을 때까지 유지됩니다.
    """.trimMargin()

    override val usbUsage: String get() = "사용법: z2-usb list | z2-usb allow [번호|경로|VID:PID]"

    override val usbNoDevices: String get() = "휴대전화에 USB 기기가 연결되어 있지 않습니다."

    override val usbNeedSelector: String get() = "USB 기기가 여러 개 연결되어 있습니다. z2-usb list에 나오는 번호로 지정하세요."

    override fun usbPermissionRequested(path: String): String = "Android 화면에서 USB 접근을 허용하세요: $path"

    override fun usbAlreadyAllowed(path: String): String = "USB 접근이 이미 허용되어 있습니다: $path"

    override fun usbNotFound(selector: String): String = "USB 기기를 찾지 못했습니다: $selector"

    override val usbStateAllowed: String get() = "허용됨"

    override val usbStateNeedsAllow: String get() = "허용 필요"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n 이름] [-c 텍스트] [-b 라벨]... "제목" "본문"  /  z2-notify [-h] "본문"
        |#   -h / --high / --banner : 화면 위쪽에 배너(헤드업)로 띄웁니다
        |#   -b <라벨>             : 응답 버튼을 답니다 (최대 3개). 누르면 ~/.z2term/events.jsonl에
        |#                            notify_action이 한 줄 늘어납니다
        |#                            ({"event":"notify_action","name":이름,"action":라벨})
        |#   -n <이름>             : 이 알림의 식별자 (어느 물음에 대한 답인지 구별합니다)
        |#   -c <텍스트>           : <텍스트>를 클립보드에 넣는 「복사」 버튼을 답니다.
        |#                            매크로가 백그라운드에서 돌 때는 z2-clip set 대신 이것을 쓰세요:
        |#                            Android 10 이상은 앞에 있는 앱만 클립보드에 쓸 수 있어서,
        |#                            앞에 아무도 없을 때의 z2-clip set은 조용히 버려집니다.
    """.trimMargin()

    override val notifyUsage: String get() = "사용법: z2-notify [-h] [-n 이름] [-c 텍스트] [-b 라벨]... <제목> [본문]"

    override val toastHelp: String get() = """
        |# z2-toast <메시지> … 화면 아래쪽에 잠깐 뜨는 짧은 메시지(토스트)입니다.
        |# 저절로 사라지고 아무것도 남지 않습니다. 남겨야 하거나 누를 버튼이 필요하면
        |# z2-notify를 쓰세요.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … 카메라로 읽는 상태로 QR 도구를 엽니다.
        |# 읽고 나면 내용에 맞는 「열기」 버튼이 하나 나옵니다 (브라우저, LINE 등의 앱, 전화, 메일,
        |# SMS, Wi-Fi, 연락처, 일정). 읽기만 해서는 열리지 않습니다.
        |# 빠른 설정 타일이나 엣지 패널에 둘 수 있습니다.
        |# z2-qr encode [-o 파일.png] [-p 픽셀] [-m 칸] [-t] [텍스트] … QR을 만듭니다.
        |#   설치할 것은 없습니다 (QR 도구 화면과 같은 부호화를 앱이 합니다).
        |#   텍스트를 생략하면 표준 입력에서 읽습니다. -o 는 PNG를 쓸 곳이고
        |#   (기본 ~/.z2term/qr/qr.png), 쓴 곳을 한 줄 출력합니다. -t 는 그림 대신
        |#   블록 문자로 이 터미널에 그립니다. -p 는 목표 한 변의 픽셀 (기본 600),
        |#   -m 은 여백의 칸 수 (기본 4). 2000바이트까지.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <텍스트> … 텍스트를 Android 공유 메뉴로 넘깁니다 (다른 앱으로 보냅니다).
        |# --file 파일 [파일 ...]: 파일 내용을 보냅니다 (최대 32개, 합계 512MiB).
        |# 현재 로컬 셸에서 읽을 수 있는 경로를 지정하세요. --는 텍스트 모드입니다. 24시간이 지난 사본은 다음 공유 시 삭제합니다.
        |# 인수는 모두 이어 붙여 하나의 본문이 됩니다. 보낼 앱은 화면에서 고르므로 누군가 앞에
        |# 있어야 합니다: 혼자 도는 매크로에는 맞지 않습니다.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <파일.html> [제목] … 여기서 만든 페이지를 z2term 안에서 읽습니다.
        |# 서버도 브라우저도 필요 없습니다. 파일을 앱에 넘겨 그대로 보여 줍니다.
        |# JavaScript와 외부 통신을 껐으므로 페이지 쪽에서는 아무것도 실행할 수 없습니다.
        |# http:// 와 https:// 링크는 평소 쓰는 브라우저로 엽니다.
        |# 휴대전화 테마가 CSS 변수로 전달됩니다 (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent). 이를 쓰면 배색이 테마를 따릅니다.
        |# 최대 4 MB. 그림은 페이지 안에 넣어 주세요 (data: URI). 외부 그림은 가져오지 않습니다.
        |# --edge 패널:항목 으로 기존 type=view 항목에 보냅니다.
        |# --controls 파일.json 으로 범용 폼과 작업을 정의하여 설치한 매크로로 전달합니다.
        |# 뷰는 기본적으로 수동 갱신입니다. view-refresh=auto이면 열 때 run=을 실행하고 z2-view는 같은 항목으로 돌아옵니다.
        |# - 또는 파이프 (ls -l | z2-view) 로 표준 입력을 읽습니다. < 로 시작하지 않는 글자는 색 코드를 빼고
        |# 줄바꿈되는 고정폭 글꼴로 보여 줍니다. --text / --html 로 직접 정할 수도 있습니다.
        |# 핀치는 글자 크기를 바꿉니다. --pinch page 이면 페이지 전체를 확대합니다. --text-size 50~300 으로
        |# 이 페이지의 글자 크기(%)를 정합니다. 둘 다 전체 화면용이며 패널 항목은 항목 설정을 따릅니다.
        |# --no-refresh / --no-find 로 전체 화면의 새로고침 / 찾기 버튼을 숨깁니다 (기본은 표시).
    """.trimMargin()

    override val viewUsage: String get() = "사용법: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <파일|-> [제목]"

    override val viewUnreadable: String get() = "z2-view: 읽을 수 없는 파일:"

    override val openHelp: String get() = """
        |# z2-open <url|경로> … 기본 앱으로 엽니다 (https://… 또는 /sdcard/…).
        |# 어떤 앱이 여는지는 Android가 정하며, z2term은 넘겨주기만 합니다.
        |# 경로는 **휴대전화**의 경로(/sdcard/…)이지, 배포판 안의 경로가 아닙니다.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w 열] [-r 행] [--clear] [-f] <파일|->… … 터미널에 그림을 그립니다.
        |#   PNG / JPEG / WebP / GIF / BMP. -를 넘기면 표준 입력에서 그림 하나를 읽습니다.
        |#   -w <열>   터미널의 열 수로 너비 지정 (기본값: 터미널에 맞춥니다)
        |#   -r <행>   터미널의 행 수로 높이 지정 (-w와 함께 쓰면 준 크기를 그대로 씁니다)
        |#   --clear   지금까지 그린 그림을 모두 지웁니다
        |#   -f        표준 출력이 터미널이 아니어도 보냅니다
        |# ⚠ 그림은 z2term의 탭이나 kitty graphics 프로토콜을 말하는 터미널에서만 나옵니다.
        |#   그 밖의 곳(ssh 너머, 페이저 안)에서는 뜻 모를 문자만 쏟아집니다.
        |# ⚠ 가로세로 비율은 한 칸의 높이가 너비의 두 배라고 봅니다. 눌린 것처럼 보이면 조정하세요:
        |#   Z2_IMG_ASPECT=0.45 z2-img photo.jpg (값이 작을수록 길쭉해집니다).
    """.trimMargin()

    override val imgUsage: String get() = "사용법: z2-img [-w 열] [-r 행] [--clear] [-f] <파일|->..."

    override val imgNoFile: String get() = "파일을 읽을 수 없습니다:"

    override val imgNotTty: String get() = "표준 출력이 터미널이 아니라 아무것도 보내지 않았습니다 (-f를 붙이면 그래도 보냅니다)."

    override val imgNoSize: String get() = "픽셀 크기를 읽을 수 없어 정사각형으로 그립니다:"

    override val qrUsage: String get() = "사용법: z2-qr  |  z2-qr encode [-o 파일.png] [-p 픽셀] [-m 칸] [-t] [텍스트]"

    override val qrEncodeFailed: String get() = "z2-qr: QR을 만들지 못했습니다 (글자가 너무 길지 않나요? 2000바이트까지입니다)"

    override val clipHelp: String get() = """
        |# z2-clip get         … 클립보드를 표준 출력으로 내보냅니다
        |# z2-clip set [텍스트] … 텍스트(없으면 표준 입력)를 클립보드에 넣습니다
        |# ⚠ Android 10 이상은 앞에 있는 앱(과 지금 쓰는 입력 방식)만 클립보드를 만질 수 있습니다.
        |#   백그라운드에서 도는 매크로에서는 아무 말 없이 버려지므로, 거기서는
        |#   z2-notify -c <텍스트>를 쓰세요: 언제나 들어가는 「복사」 버튼이 붙습니다.
    """.trimMargin()

    override val batteryHelp: String get() = "# 잔량과 충전 상태를 JSON으로 출력합니다 ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (기본값 200ms)"

    override val editHelp: String get() = "# z2-edit <파일>      … 텍스트 파일을 z2term 편집 화면에서 고칩니다 (일반 입력란, '저장'으로 되돌려 씀)"

    override val sayHelp: String get() = "# z2-say <텍스트>     … 기기의 TTS로 소리 내어 읽습니다 (인수가 없으면 stdin을 읽습니다)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (기본값 toggle). status로 외부 변경을 포함한 실제 상태(on/off)를 조회합니다."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … 둘러싼 부분만 캡처합니다 (기본값 free). Android 11과 z2term Android 동작이 필요합니다."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (기본값 playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   미디어 음량. 그 결과 current/max를 출력합니다."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (기본값 light). 한 번 읽어 JSON으로 돌려줍니다."

    override val intentHelp: String get() = """
        |# z2-intent [-a 액션] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 어떤 Android Intent든 띄웁니다 (기본값은 startActivity). 옵션이 아닌 첫 인수가 액션입니다.
        |# --window full|freeform|split|ask로 창 모드를 선택합니다. freeform은 작업을 재사용하고 크기를 OS에 맡깁니다.
        |# --profile N은 다른 프로필(업무용 등)의 앱(-p/-n)을 엽니다. 화면 분할은 안 됩니다. N은 엣지 패널에 그 앱을 등록하면 저장되는 명령에 있습니다.
        |# --reuse-task / --os-bounds는 호환용입니다. --bounds-only는 80% 크기만 요청하며 --os-bounds와 함께 쓸 수 없습니다.
        |# --no-scale은 Motorola의 축소 자유형 창 요청을 끕니다.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state           … 지금 기기 상태 전체를 JSON으로
        |# z2-state <키>      … 그 값만 그대로 (조건에 그대로 넣을 수 있습니다)
        |# 키: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 예: [ "${d}(z2-state charging)" = "true" ] && echo 충전중
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t 초] [-H 힌트] [-d 기본값] <질문>
        |#   사람에게 묻고, 그 답을 표준 출력으로 내보냅니다.
        |#   질문은 **응답 칸이 달린 알림**으로 도착하므로, 앱을 열지 않고 알림 창에서 답할 수
        |#   있습니다 (백그라운드에서 도는 매크로도 물을 수 있습니다).
        |#   -t <초>    답을 얼마나 기다릴지 (기본값 300 = 5분)
        |#   -H <힌트>  응답 칸에 보여 줄 안내
        |#   -d <텍스트> 미리 넣어 둘 답 (알림에 나옵니다)
        |# 답하지 않고 알림을 지우거나 시간이 다 되면 0이 아닌 코드로 끝나고 아무것도 출력하지
        |# 않으므로, 「아니면 포기」를 그대로 쓸 수 있습니다:
        |#   name=${d}(z2-ask "브랜치 이름은?") || exit 1
        |# 미리 준비한 선택지만 내놓는 z2-notify -b <라벨>과 견줘 보세요.
    """.trimMargin()

    override val askUsage: String get() = "사용법: z2-ask [-t 초] [-H 힌트] [-d 기본값] <질문>"

    override val listenHelp: String get() = """
        |# z2-listen [-w 단어,단어,...] [-l 언어]
        |#   말한 내용을 글자로 바꿔 표준 출력으로 내보냅니다. 인식은 **기기 안에서만** 하며,
        |#   목소리를 네트워크로 보내지 않습니다. Android 12 이상과 기기 내 음성 인식
        |#   (해당 언어의 오프라인 인식 데이터)이 필요합니다.
        |#   z2term을 화면에 띄운 채로 말하세요 (Android는 화면에 있는 앱에만 마이크를 줍니다).
        |#   말을 멈추면 끝납니다. 처음 실행할 때 마이크 권한을 묻습니다.
        |#   -w <단어>  이 단어들만 받고 (쉼표로 구분), 맞은 단어를 출력합니다.
        |#              인식의 1위만이 아니라 다른 후보도 봅니다.
        |#   -l <언어>  인식할 언어 (예: ko-KR, en-US. 기본값은 기기 언어)
        |# 종료 코드: 0 = 성공 / 1 = 못 알아들음·어느 단어도 아님 / 2 = 사용할 수 없음 (이유는 stderr)
        |#   cmd=${d}(z2-listen -w "다음 곡,정지") || exit
    """.trimMargin()

    override val listenUsage: String get() = "사용법: z2-listen [-w 단어,단어,...] [-l 언어]"

    override val listenPrompt: String get() = "말씀하세요… (말을 멈추면 끝납니다)"

    override val listenNeedsAndroid12: String get() = "z2-listen은 Android 12 이상이 필요합니다 (그 이전에는 기기 안에서만 인식하도록 지정할 수 없습니다)."

    override val listenBusy: String get() = "이미 듣는 중입니다 (다른 z2-listen이 실행 중입니다)."

    override val listenEngineBusy: String get() = "기기의 음성 인식이 사용 중입니다 (다른 앱이 사용 중일 수 있습니다). 잠시 후 다시 시도하세요."

    override val listenEngineStuck: String get() = "음성 인식이 응답하지 않습니다. 계속되면 Android 설정 › 앱에서 기기의 음성 인식 앱(Android System Intelligence 등)을 강제 중지하세요."

    override val listenStopped: String get() = "듣기를 멈췄습니다."

    override val listenNoOnDevice: String get() = "이 기기에는 기기 내 음성 인식이 없습니다. z2-listen은 목소리를 네트워크로 보내지 않으므로 여기서는 사용할 수 없습니다."

    override val listenNoPermission: String get() = "마이크 권한이 없습니다. z2term을 화면에 띄운 채 다시 실행하면 권한을 묻습니다 (Android 설정에서도 허용할 수 있습니다)."

    override val listenNotShown: String get() = "z2term을 화면에 띄운 뒤 다시 실행하세요 (Android는 화면에 있는 앱에만 마이크를 줍니다)."

    override val listenNoMic: String get() = "마이크를 사용할 수 없었습니다 (z2term이 화면에서 벗어났거나 다른 앱이 녹음 중)."

    override val listenNoLanguage: String get() = "이 언어의 기기 내 인식 데이터가 없습니다. 기기의 음성 입력 설정 (오프라인 음성 인식)에서 다운로드하세요."

    override val listenNoSpeech: String get() = "알아듣지 못했습니다."

    override val listenTimedOut: String get() = "시간이 초과되었습니다."

    override fun listenNoMatch(heard: String): String = "기다리는 단어가 아닙니다 (들린 말: $heard)"

    override fun listenFailed(code: Int): String = "음성 인식에 실패했습니다 (오류 $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … 그동안 화면이 저절로 꺼지지 않게 합니다
        |# z2-screen keepon off         … 기한을 기다리지 않고 지금 되돌립니다
        |# z2-screen status             … 지금 상태를 JSON으로
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# 이것은 시스템 전체의 화면 꺼짐 설정을 바꾸므로, 앱이 백그라운드에 있어도 유지됩니다.
        |# 앱이 화면에 있는 동안에만 도는 툴바의 🔅와는 다르며, 그쪽은 건드리지 않습니다.
        |# 원래 값은 저장해 두었다가 기한이 되면 반드시 되돌려 씁니다 (앱이 죽거나 기기를 다시
        |# 켜도 마찬가지입니다). 한 번에 최대 24시간입니다.
        |# 「시스템 설정 수정」 권한이 필요합니다 (설정 > 화면 꺼짐 시간 > 허용).
        |# 예: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "사용법: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | 명령...> [--off <명령...>] [-l <라벨>] [-i <그림>]
        |#                           … 빠른 설정의 1-${tiles}번 타일에 무언가를 올립니다
        |# z2-tile add <1-$tiles>         … 그 자리를 패널에 놓아 달라고 요청합니다 (Android 13 이상)
        |# z2-tile list              … ${tiles}개 자리를 TSV로 (자리 / 라벨 / 명령. '-' = 빈 자리)
        |# z2-tile clear <1-$tiles|all>   … 자리를 비웁니다
        |#
        |# 무엇을 배정하나
        |#   ~/.z2term/macros/에 있는 **매크로의 파일 이름**이거나, 그대로 실행되는 **명령**입니다.
        |#   둘 중 어느 쪽인지는 이름으로 가려냅니다.
        |#   매크로에는 인수를 붙일 수 있습니다('remind.sh ask'). 첫 낱말로 매크로인지 정하고
        |#   나머지는 그대로 넘깁니다. 같은 매크로를 두 자리에 두면 라벨이 같아지므로 -l을 주세요.
        |#   명령은 환경에 Z2_TILE=<자리>(매크로면 Z2_TILE_MACRO도)를 넣고 실행합니다.
        |#   ⚠ ~/.z2term/macros/에 없는데 .sh로 끝나는 이름은 여기서 거절합니다: 명령으로 보면
        |#     PATH에서 찾게 되는데(PATH에 매크로 폴더는 없습니다) 타일이 아무 일도 하지 않고
        |#     tile/run.log에만 그 사실이 남기 때문입니다.
        |#
        |# 한 번 누르면
        |#   한 번 누르면 실행하고 다시 누르면 멈춥니다 (위젯 버튼과 같은 대접입니다).
        |#   도는 동안 타일이 「켜짐」으로 보입니다 (색은 시스템의 것이지 우리 것이 아닙니다).
        |#   --off를 주면 **명령이 두 개**가 되어, 누를 때마다 번갈아 실행하고 켜져 있는 동안
        |#   타일도 켜진 모습으로 남습니다. 끄는 것이 따로 명령일 때 쓰세요 (z2-torch on / off).
        |#   일반 명령 쌍은 전환 상태를 기억합니다. z2-torch 직접 할당은 외부 변경을 포함한
        |#   Android 실제 상태를 반영합니다. z2-screen keepon도 실제 활성 상태를 따릅니다.
        |#
        |# 패널에 놓기
        |#   Android 13 이상에서는 `set`이 그 자리에서 타일을 패널에 놓을지 묻고, 그 대화상자에는
        |#   **방금 배정한 이름과 아이콘**이 나옵니다. 아니라고 하면 아무것도 놓이지 않습니다.
        |#   자리는 정확히 ${tiles}개입니다: 개수는 매니페스트에 박혀 있어 도중에 늘릴 수 없습니다.
        |#   배정하지 않은 자리는 편집 화면에 아예 나오지 않으므로, ${tiles}개를 두어도 손해가
        |#   없습니다.
        |#   ⚠ z2term이 앞에 있을 때만 뜨고, 한 번에 하나씩만 물을 수 있습니다: 두 자리를 잇달아
        |#     배정하는 매크로는 첫 번째만 묻습니다. 나머지는 나중에 z2-tile add <자리>로 하세요.
        |#   ⚠ 타일을 놓는 것은 여전히 사람의 몫입니다: Android는 앱이 스스로 놓는 것을 허용하지
        |#     않습니다. 빠른 설정 패널의 연필(편집) 화면에서 **z2term <자리>**를 찾으세요.
        |#   ⚠ 그 목록에는 배정한 이름과 아이콘이 아니라 매니페스트의 것이 나오므로(Android가
        |#     도중에 바꿀 방법을 주지 않습니다), 어느 번호가 무엇인지는 z2-tile list로 보세요.
        |#
        |# 그림 (-i)
        |#   이름으로 짐작이 갈 때만 자리에 어울리는 아이콘이 붙고(remind.sh -> 시계), z2-icon으로
        |#   원하는 것으로 바꿀 수 있습니다. 한 번 바꾸면 그 뒤로는 건드리지 않습니다.
        |#   -i는 그 대화상자가 뜨기 **전에** 그림을 정해 두므로, 한 줄로 이름과 아이콘을 함께
        |#   정할 수 있습니다 (넘길 수 있는 이름은 z2-icon sample이 알려 줍니다).
        |#   ⚠ 모르는 이름은 **아무것도 배정하기 전에** 거절합니다: 배정은 되었는데 그림이 틀린
        |#     자리는 아예 배정되지 않은 자리보다 알아차리기 어렵기 때문입니다.
        |#
        |# 예: z2-tile set 1 backup.sh
        |#        z2-tile set 2 'z2-screen keepon 1h' -l "꺼지지 않게"
        |#        z2-tile set 3 z2-torch on --off z2-torch off -l 손전등
        |#        z2-tile set 4 backup.sh -l 백업 -i sync
    """.trimMargin()

    override val tileUsage: String get() = "사용법: z2-tile set <1-$tiles> <macro.sh|명령...> [--off <명령...>] [-l 라벨] " +
                "[-i dibujo] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: 그런 그림이 없습니다: $name (z2-icon sample이 목록을 보여 줍니다). 아무것도 배정하지 않았습니다."

    override fun tileAddEmpty(n: Int): String = "z2-tile: ${n}번 자리는 아직 비어 있습니다 (먼저 배정하세요: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: 여기서는 타일 추가를 요청할 수 없습니다 (Android 13과 z2term이 앞에 있어야 합니다). " +
                "El hueco queda asignado: colócalo desde la pantalla del lápiz (editar) del panel de ajustes rápidos."

    override val tileAddAsked: String get() = "타일을 추가할지 Android에 물었습니다 — 대화상자에 답하세요. " +
                "(¿No ha salido nada? Colócalo desde la pantalla de edición.)"

    override val iconHelp: String get() = """
        |# 상태 표시줄과 타일의 아이콘을 직접 그린 픽셀 그림으로 바꿉니다.
        |#
        |# 여기서 시작하세요
        |#   z2-icon pick 1     ... 목록에서 그림을 골라 1번 자리에 넣습니다
        |#   z2-icon list       ... 지금 각 자리에 무엇이 있는지 ('-p'를 붙이면 그림도 보여 줍니다)
        |#
        |# <대상>이 뜻하는 것
        |#   notify    이 앱의 모든 알림에 쓰는 아이콘 (상태 표시줄의 것)
        |#   1-$tiles      빠른 설정의 자리 하나: z2-tile이 쓰는 번호와 같습니다
        |#
        |# 그림 넣기
        |#   pick <대상>             목록을 보여 주고 번호로 고르게 합니다 (가장 쉽습니다)
        |#   sample <대상> <이름>    이름을 이미 알 때
        |#   edit <대상>             ${d}EDITOR에서 직접 그리거나 지금 것을 고칩니다
        |#   set <대상> <파일>       파일에서 읽습니다 ('-'는 표준 입력)
        |#   auto <1-$tiles|all>     타일이 실행하는 것에서 다시 고르게 합니다
        |#   clear <대상|all>        기본 아이콘으로 되돌립니다
        |#
        |# 격자를 얼마나 곱게
        |#   grid                    새 그림을 만들 때 쓰는 크기
        |#   grid <$grids>           정해 둡니다 (바꾸기 전까지는 $grid)
        |#   scale <대상> <$grids>   <대상>에 있는 그림을 그 격자에 다시 폅니다 (사선의 계단도
        |#                            함께 다듬습니다). **더 곱게 그릴 자리를 줍니다**: 나오는
        |#                            아이콘은 어차피 부드러워지므로 그것 때문에 쓸 필요는 없습니다.
        |#
        |# 그린 것을 목록에 남기기
        |#   save <대상> <이름>      <대상>에 있는 것에 이름을 붙여 목록에 더합니다
        |#   sample                  고를 수 있는 목록 (번호 / 이름 / 'builtin' = 기본,
        |#                            'mine' = 직접 저장한 것)
        |#   sample <이름>           그 그림을 보여 줍니다
        |#   forget <이름>           직접 만든 것을 목록에서 뺍니다 (이미 어딘가에 넣은
        |#                            것은 그대로 남습니다)
        |#
        |# 어떻게 그리나
        |#   문자로 된 격자입니다. '.' ' ' '0' '-' '_'는 점을 비우고 그 밖의 문자는 채우므로, 눈에
        |#   잘 들어오는 것을 쓰세요. 그림 둘레의 빈 곳은 무시하고 저절로 가운데로 맞추므로,
        |#   줄마다 딱 맞게 채울 필요는 없습니다.
        |#   격자는 ${grids}입니다 ('z2-icon grid').
        |#   ⚠ 격자가 크다고 아이콘이 커지지는 않습니다. 격자를 채우지 않으면 대신하는 아이콘보다
        |#     작게 나옵니다.
        |#
        |# 알아 두면 좋은 것
        |#   ⚠ 넘어가는 것은 모양뿐입니다. Android가 이 아이콘을 자기 색으로 칠하므로(타일은 켜짐과
        |#     꺼짐에서 색이 달라집니다) 고를 색이 없습니다.
        |#   ⚠ **테두리는 나올 때 부드러워집니다.** $grid 점으로 그린 그림도 타일에서는(상태
        |#     표시줄보다 훨씬 크게 그려집니다) 그대로 부드럽게 나오므로, 격자는 평소에 걱정할
        |#     것이 아닙니다. 48 / 64는 **직접** 더 곱게 그리고 싶을 때 씁니다. 'show'는 그린
        |#     그대로 찍고, 타일에는 더 부드럽게 나옵니다.
        |#   ⚠ 타일에 그림이 붙는 것은 이름으로 짐작이 갈 때뿐입니다('z2-tile set 1 remind.sh'는
        |#     거기에 시계를 놓습니다). 여기서 넣은 것이 우선이고 그 뒤로는 건드리지 않습니다:
        |#     'z2-icon auto 1'로 그 자리를 다시 자동 선택으로 되돌립니다.
        |#   ⚠ 바꿀 수 없는 아이콘이 둘 있습니다: 빠른 설정의 **편집** 화면(타일을 끌어오는 곳)의
        |#     것과 런처의 것입니다. Android가 설치할 때 정해 버립니다.
        |#
        |# 예: z2-icon pick 1              1번 자리의 그림을 고릅니다
        |#        z2-icon edit 1              직접 다시 그립니다
        |#        z2-icon save 1 내-얼굴      그 그림에 이름을 붙여 목록에 더합니다
        |#        z2-icon sample 3 내-얼굴    같은 그림을 3번 자리에 넣습니다
        |#        z2-icon grid 64             새 그림을 64x64 격자로 그립니다
        |#        z2-icon scale 1 48          1번 자리를 48x48로 옮겨 더 곱게 그립니다
        |#        z2-icon list -p             어디에 무엇이 있는지 그림까지 확인합니다
        |#        z2-icon clear notify        알림 아이콘을 제자리로 되돌립니다
    """.trimMargin()

    override val iconUsage: String get() = "사용법: z2-icon pick <notify|1-$tiles> | sample [이름|대상 이름] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [archivo|-] | " +
                "save <notify|1-$tiles> <nombre> | forget <nombre> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "그런 파일이 없습니다:"

    override val iconEditUnchanged: String get() = "달라진 것이 없습니다."

    override val iconPickPrompt: String get() = "번호 (또는 이름), 비우면 취소: "

    override val iconPickCancelled: String get() = "취소했습니다."

    override val iconNoEditor: String get() = "편집기를 찾지 못했습니다. ${d}EDITOR를 정하거나 이렇게 쓰세요: z2-icon set <대상> <파일>"

    override val tileNoSuchMacro: String get() = "~/.z2term/macros/에 그런 매크로가 없습니다 (명령으로 실행하려면 전체 경로를 쓰세요):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# 알림 감지를 먼저 켜세요. 5분 동안 최신 128건을 메모리에만 기록합니다.
        |# 형식과 UTF-16 길이만 기록하며 본문 값은 저장하지 않습니다. dump/stop은 JSON, start는 초기화.
        |# z2-noti list  … 지금 화면에 있는 알림을 TSV로
        |#                 (key / 패키지 / 앱 이름 / 제목 / 본문)
        |# 읽기만 합니다. 알림을 누르거나 지우는 방법은 일부러 두지 않았습니다:
        |# 그것은 다른 앱의 결제・전송 버튼까지 누르게 되기 때문입니다.
        |# 알림 접근 권한이 필요합니다 (설정 > 권한・알림).
        |# 함께 보기: z2-when notify:otp / notify:pkg=<일부> / notify:contains=<일부>
    """.trimMargin()

    override val notiUsage: String get() = "사용법: z2-noti list | trace start [package] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [이름]    … 한 번만, 다음 HH:MM에 (이미 지났으면 내일)
        |# z2-alarm daily HH:MM [이름] … 날마다 HH:MM에
        |# z2-alarm in <N|Ns|Nm|Nh> [이름] … 한 번만, N초/분/시간 뒤에
        |# z2-alarm list               … 예약된 것 (JSON. "exact"가 얼마나 정확한지 알려 줍니다)
        |# z2-alarm cancel <id|이름|all> … 취소
        |# 울리면 ~/.z2term/events.jsonl에 {"event":"alarm","name":…} 한 줄이 늘어납니다.
        |# Doze 중에도 기기를 깨웁니다. "exact":true는 분 단위로 정확히 울린다는 뜻이고,
        |# "exact":false는 Doze가 9~15분에 한 번만 틈을 준다는 뜻이라, 화면이 꺼진 휴대전화에서는
        |# 그만큼 늦게 올 수 있습니다. true로 만드는 것은 이 앱을 배터리 최적화에서 빼는 것입니다
        |# (다른 권한은 요구하지 않습니다).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: 쓸 수 있는 date 명령이 없습니다"

    override val sessionHelp: String get() = """
        |# z2-session list                    … 탭을 나열합니다 (색인 / id / 종류 / 표시 / 이름, TSV)
        |#   표시: * = 화면에 있음 / ! = 일하는 중 / ? = 시작 안 됨 / @ = 셸에서 접속 중 / - = 그 밖
        |# z2-session new [이름]              … 터미널 탭을 엽니다 (색인과 id를 돌려줍니다)
        |# z2-session send <탭> <텍스트>...   … 그 탭에 텍스트를 씁니다 (실행하지는 않습니다)
        |# z2-session send <탭> <텍스트> --enter … 쓰고 실행까지 합니다
        |# z2-session key <탭> <키>...        … 그 탭에 **키**를 보냅니다 (C-c / M-x / F5 / Up …)
        |#   수식 키: C- (Ctrl)와 M- (Meta=Alt). C-M-a처럼 이어 쓸 수 있습니다
        |#   특수 키: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   C-S-a처럼 Shift가 붙은 키는 거절합니다 (터미널은 Shift를 구별하지 못해 C-a와 같은 바이트입니다)
        |# z2-session key <탭> --raw '\x1b[A' … 그 밖의 것은 바이트로 (\xHH \e \n \r \t \0)
        |# z2-session capture [탭] [--all]    … 그 탭의 화면을 뜹니다 (--all은 기록까지)
        |# z2-session attach <탭>             … 그 탭에 붙어서 그대로 입력합니다
        |#   나오려면 Ctrl+] (언제든) 또는 줄 첫머리에서 ~. (ssh와 같습니다)
        |#   ⚠ SSH 너머에서는 Ctrl+]만 듣습니다: 앞에 있는 ssh가 ~.을 먹고 SSH 세션을 끊습니다
        |#   줄 첫머리의 ~를 그대로 넣으려면 ~~, Ctrl+]을 그대로 보내려면 ~에 이어 Ctrl+]
        |#   붙어 있는 동안 탭은 내 창 크기를 따르고, 나오면 원래 크기로 돌아갑니다
        |# z2-session close <탭>              … 그 탭을 닫습니다 (마지막 하나는 닫지 않습니다)
        |#
        |# <탭>은 list의 색인, id, 탭 이름 중 아무거나 됩니다. '.'이나 생략은 화면에 있는 탭입니다.
        |# 예: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "사용법: z2-session list | new [이름] | send <탭> <텍스트>... [--enter] | " +
                "key <pest> <tecla>... | key <pest> --raw <bytes> | capture [pest] [--all] | " +
                "attach <pest> | close <pest>"

    override val keyNothing: String get() = "z2-session key: 키를 지정하지 않았습니다"

    override val keyRawEmpty: String get() = "z2-session key: --raw 뒤에 보낼 바이트가 없습니다"

    override val keyUnknown: String get() = "z2-session key: 그런 키가 없습니다 (목록은 \'z2-session -h\'에 있습니다):"

    override val keyBadEscape: String get() = "z2-session key: 이스케이프를 읽을 수 없습니다:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: 터미널은 Shift를 구별하지 못하므로 다음 둘은 똑같은 바이트가 됩니다: " +
            "'$asWritten' = '$equivalentTo'. 그렇게 하려던 것이라면 '$equivalentTo'라고 쓰세요."

    override fun attachNoSuchTab(target: String): String = "그런 탭이 없습니다: '$target' ('z2-session list'로 탭을 볼 수 있습니다)"

    override val attachNotTerminal: String get() = "그것은 GUI 탭이라 붙을 셸이 없습니다. 터미널 탭을 고르세요."

    override val attachNotStarted: String get() = "그 탭은 아직 시작되지 않았습니다 (list에서 '?'로 표시됩니다). 앱에서 한 번 연 뒤에 붙으세요."

    override val attachExited: String get() = "그 탭은 이미 끝났습니다. 화면은 'z2-session capture'로 아직 읽을 수 있습니다."

    override val attachSelf: String get() = "그것은 지금 입력하고 있는 탭입니다. 탭을 자기 자신에게 붙이면 자기 출력이 " +
            "salida vuelva como entrada para siempre, y ya no se puede deshacer. Elige otra pestaña."

    override val attachLoop: String get() = "그 탭은 이미 이쪽으로 붙어 있어서, 둘이 서로를 먹여 " +
            "otra sin parar. Desconecta antes una de ellas (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list              … 등록된 서버 (색인 / id / 상태 / 표시 / 이름, TSV)
        |#   표시: * = 켜짐 / - = 꺼짐
        |# z2-server start <서버>      … 상주 서버로 띄웁니다 (기기에 계속 닿을 수 있게 합니다)
        |# z2-server stop <서버>       … 그것만 멈춥니다 (나머지는 계속 돕니다)
        |# z2-server status [<서버>]   … 상태, pid, 다시 시작한 횟수, 마지막 종료 코드
        |#
        |# <서버>는 list의 색인, id, 앱에서 붙인 이름 중 아무거나 됩니다.
        |# 앱에 등록된 서버만 띄울 수 있으며, 여기서 새로 등록하지는 않습니다.
        |#
        |# 왜 있나: 규칙에서 데몬을 바로 띄우면 상주 서버의 틀 바깥에서 돕니다(WakeLock도 WifiLock도
        |# 포그라운드 서비스도 없이). 그래서 화면이 꺼지는 순간 응답이 끊깁니다. 여기로 띄우면 그
        |# 틀 안에 들어갑니다.
        |# 예: z2-when wifi:connect run 'z2-server start sshd'
        |#        z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "사용법: z2-server list | start <서버> | stop <서버> | status [<서버>]"

    override val serverNotFound: String get() = "z2-server: 그런 서버가 없습니다 ('z2-server list'를 보세요):"

    override val serverAmbiguous: String get() = "z2-server: 그 이름의 서버가 여럿입니다. 'z2-server list'의 id를 쓰세요:"

    override val serverNone: String get() = "z2-server: 아직 등록된 서버가 없습니다 (앱에서 추가하세요: 📜 -> 서버)."

    override val serverLowPowerWarn: String get() = "z2-server: 참고 — 절전 모드가 켜져 있어 WakeLock/WifiLock을 잡지 않습니다. " +
            "El servidor puede dejar de responder con la pantalla apagada " +
            "(Ajustes -> Automatización -> Protección de los procesos en segundo plano)."

    override val whenHelp: String get() = """
        |# z2-when <트리거> run <cmd...>        … 규칙을 등록합니다
        |#   트리거: charge:start | charge:stop  (감지가 켜져 있어야 합니다)
        |#            battery:below=N | battery:above=N  (감지가 켜져 있어야 합니다)
        |#            battery:temp>N | battery:temp<N  … 배터리 온도(℃)가 N을 넘나들었다 (감지가 켜져 있어야 합니다)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='분 시 일 월 요일'  (요일 0-7 / 0,7=일요일. 공백이 있으니 따옴표로 묶으세요)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<이름>  (감지가 켜져 있어야 합니다)
        |#            net:online | net:offline  … 쓸 수 있는 연결이 생겼다 / 사라졌다
        |#            net:wifi | net:mobile | net:ethernet  … 지금 쓰는 연결이 그것으로 바뀌었다
        |#                                        (감지 필요. Wi-Fi뿐 아니라 모바일 데이터도 셉니다)
        |#            share:any | share:text | share:file | share:contains=<일부> | share:ext=<확장자>
        |#                                        … 다른 앱이 z2term으로 무언가를 공유했다
        |#            boot                        … 기기가 다 켜졌다 (감지가 꺼져 있어도 됩니다)
        |#            sms:any | sms:from=<일부> | sms:contains=<일부> | sms:otp  (RECEIVE_SMS 필요)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (감지 필요)
        |#            file:new=<폴더>[,ext=<확장자>]  … 그 폴더에 새 파일이 들어왔다 (감지 필요)
        |#            file:changed=<폴더>[,ext=<확장자>] | file:deleted=<폴더>[,ext=<확장자>]
        |#                                        … 거기 있는 파일이 다시 쓰였다 / 사라졌다 (감지 필요)
        |#            bt:connect | bt:disconnect | bt:connect=<이름> | bt:disconnect=<이름>
        |#                                        … 블루투스 기기가 연결되었다 / 끊겼다 (이름은 그 일부, 또는 주소)
        |#                                        (감지 필요. Android 12부터는 「근처 기기」 권한도 필요)
        |#            app:open=<앱> | app:close=<앱>  … 그 앱이 앞에 나왔다 / 앞에서 벗어났다 (패키지 이름이나 앱 이름의 일부)
        |#                                        (접근성에서 z2term을 켜야 합니다. 감지가 꺼져 있어도 동작)
        |#            steps:reach=N               … 오늘 걸음 수가 N에 닿았다 (감지와 「신체 활동」 권한 필요)
        |#            notify:any | notify:otp | notify:pkg=<일부> | notify:title=<일부> | notify:contains=<일부>
        |#                                        … 알림이 왔다 (알림 접근 권한이 필요합니다)
        |#            event:<이름> | event:<앞부분>* | event:*  … 기기의 어떤 이벤트든 이름으로
        |#              (z2-when events가 목록을 보여 줍니다. events.jsonl에 나오는 이름과 같습니다)
        |# 이름 (선택. 트리거 바로 뒤, run 앞에):
        |#   name=<텍스트>                      … 이 규칙이 무엇을 위한 것인지. 자동화 탭에 나옵니다
        |#                                         (공백이 있으면 따옴표로 묶으세요). 비우면 트리거가 나옵니다.
        |#                                         보이기만 할 뿐, 언제 실행되는지는 달라지지 않습니다.
        |# 거르기 (어느 트리거에나. 트리거 바로 뒤, run 앞에):
        |#   if=<조건>[,<조건>...]              … 기기가 그 상태일 때만 (AND. !는 부정)
        |#                                         키는 z2-state가 출력하는 것들입니다: wifi charging screen
        |#                                         locked idle headset bt_audio airplane plug ssid ringer
        |#                                         level temp volume
        |#                                         예: if=wifi,!screen / if=ssid=집 / if=level<30
        |#   if_any=<조건>[,<조건>...]          … **하나라도** 맞으면 실행합니다 (쉼표가 OR입니다)
        |#                                         if=와 함께 쓰면 「if는 전부, if_any는 하나라도」가 됩니다
        |#   else=<cmd>                         … if / if_any가 맞지 않을 때 **대신** 이것을 실행합니다
        |#                                         ⚠ 여기에 오는 것은 if / if_any뿐입니다. between / days /
        |#                                         cooldown 때문에 걸러진 실행은 아무것도 실행하지 않습니다:
        |#                                         밤에 꺼 두는 규칙은 else까지 포함해 밤에는 조용해야 합니다.
        |#   cooldown=30m                       … 그 시간 안에는 되풀이하지 않습니다 (10s / 30m / 2h)
        |#   between=22:00-07:00                … 그 시간대에만 (자정을 넘겨도 됩니다)
        |#   days=mon-fri                       … 그 요일에만 (이름이나 cron의 숫자 0-7. 0/7 = 일요일)
        |#   answer='<문구>=><답>'    … 명령이 터미널에서 이렇게 물으면 답과 Enter 를 보냅니다
        |#                             (여러 개 가능. 예: answer='[y/N]=>y'). 출력 마지막 줄의 끝과 비교합니다
        |#   secret='<문구>'         … 입력을 숨기는 답 (비밀번호) 용. 내용은 앱의 규칙 편집 화면에서 입력합니다
        |#                             (앱 안에 암호화해 저장하며 규칙 파일에는 쓰지 않습니다)
        |# 걸러진 실행도 기록합니다: z2-when fired가 skip:if / skip:if→else / skip:between / skip:days /
        |# skip:cooldown을 보여 주므로, 한 번도 돌지 않는 규칙의 이유를 댈 수 있습니다.
        |# 앱의 ▶ 버튼은 거르기를 모두 무시합니다 (규칙을 시험해 보라고 있는 것입니다).
        |# z2-when events                       … event:에 쓸 수 있는 이름을 나열합니다
        |# z2-when pause / resume               … 자동 실행을 멈춥니다 / 다시 시작합니다 (규칙은 그대로)
        |# z2-when fired [n]                    … 최근에 발생한 것 (시각 / id / 트리거 / run|paused)
        |# z2-when list                         … 등록된 규칙 (id / on|off / 트리거 / -> / cmd, TSV.
        |#                                         이름과 거르기가 있으면 끝에 []로 붙습니다)
        |# z2-when remove <id|all>  (rm도 됩니다) … 삭제
        |# z2-when on <id> / off <id>           … 켜기 / 끄기
        |# z2-when log <id>                     … 그 규칙의 실행 기록 (끝부분)
        |# 발생하면 명령은 고른 배포판에서 Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL / Z2_WHEN_SSID (wifi) /
        |# Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms) / Z2_WHEN_SENSOR / Z2_WHEN_LUX
        |# (sensor)를 환경에 넣고 실행됩니다.
        |# net:에서는 Z2_WHEN_NET(지금 연결)과 Z2_WHEN_NET_PREV(그전 연결)를 씁니다.
        |# share:에서는 Z2_WHEN_SHARE(텍스트, 또는 받아 둔 파일의 경로)와
        |# Z2_WHEN_SHARE_KIND(text|file)를 씁니다. 공유된 것은 지금까지처럼 입력 줄에도 들어갑니다.
        |# file:에서는 Z2_WHEN_FILE(전체 경로)과 Z2_WHEN_DIR를 씁니다.
        |# notify:에서는 Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY를 씁니다
        |# (notify:otp에서는 Z2_WHEN_OTP도). notify:category=는 정확히 일치해야 합니다: call, missed_call, msg, ...
        |# event:에서는 Z2_WHEN_EVENT(이벤트 이름)가 더 붙고, alarm / notify_action은
        |# Z2_WHEN_EVENT_NAME(등록할 때 붙인 식별자)과 Z2_WHEN_ACTION(누른 버튼)을 더합니다.
        |# battery:temp 이면 Z2_WHEN_TEMP (℃). bt: 이면 Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# app: 이면 Z2_WHEN_APP_PKG (지금 앞에 있는 앱) / Z2_WHEN_APP_PREV (바로 전 앱).
        |# steps: 이면 Z2_WHEN_STEPS (오늘 걸음 수). file: 에는 Z2_WHEN_FILE_ACTION (new|changed|deleted)도 들어갑니다.
        |# app_installed / app_removed / app_updated 에는 Z2_WHEN_PKG, timezone_changed 에는 Z2_WHEN_TZ,
        |# usb_attached / usb_detached 에는 Z2_WHEN_USB (기기 이름) / Z2_WHEN_USB_ID (제조사:제품)가 들어갑니다.
        |# 같은 규칙은 10초 안에 두 번 발생하지 않습니다 (screen_on 같은 이벤트는 자주 옵니다).
        |# 예: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#        z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#        z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#        z2-when 'event:ringer_*' run 'z2-toast "소리 모드: ${d}Z2_WHEN_EVENT"'
        |#        z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#        z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#        z2-when boot run 'sshd --lan'
        |#        z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#        z2-when time:daily=07:00 name='아침 보고' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "자동 실행을 멈췄습니다 (다시 시작하려면 z2-when resume)"

    override val whenResumed: String get() = "자동 실행을 다시 시작했습니다"

    override val whenNoFires: String get() = "(아직 아무것도 발생하지 않았습니다)"

    override val whenPausedNote: String get() = "# 멈춤 (다시 시작하려면 z2-when resume)"

    override val whenNoLog: String get() = "(아직 로그가 없습니다)"

    override val whenWriteFailed: String get() = "z2-when: 규칙을 쓰지 못했습니다"

    override val whenUnknownIfKey: String get() = "z2-when: 모르는 if= 키입니다 (쓸 수 있는 것은 z2-state가 알려 줍니다):"

    override val whenUnknownTrigger: String get() = "z2-when: 모르는 트리거입니다 (인수 없이 z2-when을 실행하면 목록이 나옵니다):"

    override val whenBadTriggerSpec: String get() = "z2-when: 그 트리거는 이 인수를 받지 않습니다:"

    override val whenPausedWarn: String get() = "참고: 자동 실행이 멈춰 있습니다 (다시 시작하려면 z2-when resume)"

    override val whenRunJoined: String get() = "참고: 명령 안의 줄바꿈을 공백으로 바꿨습니다 (규칙은 한 줄만 읽습니다)"

    override val whenEventList: String get() = """
        |screen_on              화면이 켜졌다              [감지 필요]
        |screen_off             화면이 꺼졌다              [감지 필요]
        |unlocked               기기의 잠금이 풀렸다       [감지 필요]
        |power_connected        충전이 시작되었다          [감지 필요]
        |power_disconnected     충전이 멈췄다              [감지 필요]
        |battery_low            배터리가 부족해졌다        [감지 필요]
        |battery_okay           배터리가 회복되었다        [감지 필요]
        |battery_level          잔량이 10% 단위를 넘었다   [감지 필요]
        |wifi_connected         Wi-Fi에 연결되었다         [감지 필요]
        |wifi_disconnected      Wi-Fi에서 떨어졌다         [감지 필요]
        |net_online             쓸 수 있는 연결이 생겼다   [감지 필요]
        |net_offline            쓸 수 있는 연결이 없다     [감지 필요]
        |net_wifi               지금 쓰는 연결이 Wi-Fi다   [감지 필요]
        |net_mobile             지금 쓰는 연결이 모바일이다 [감지 필요]
        |headset_plugged        유선 이어폰을 꽂았다       [감지 필요]
        |headset_unplugged      유선 이어폰을 뽑았다       [감지 필요]
        |bt_audio_connected     블루투스 오디오가 연결되었다 [감지 필요]
        |bt_audio_disconnected  블루투스 오디오가 끊겼다   [감지 필요]
        |airplane_on            비행기 모드를 켰다         [감지 필요]
        |airplane_off           비행기 모드를 껐다         [감지 필요]
        |ringer_normal          소리를 일반으로 바꿨다     [감지 필요]
        |ringer_vibrate         소리를 진동으로 바꿨다     [감지 필요]
        |ringer_silent          소리를 무음으로 바꿨다     [감지 필요]
        |bt_connected           블루투스 기기가 연결되었다 [감지 필요]
        |bt_disconnected        블루투스 기기가 끊겼다     [감지 필요]
        |powersave_on           절전 모드를 켰다           [감지 필요]
        |powersave_off          절전 모드를 껐다           [감지 필요]
        |dnd_on                 방해 금지 모드를 켰다      [감지 필요]
        |dnd_off                방해 금지 모드를 껐다      [감지 필요]
        |app_installed          앱을 설치했다              [감지 필요]
        |app_removed            앱을 삭제했다              [감지 필요]
        |app_updated            앱이 업데이트되었다        [감지 필요]
        |timezone_changed       시간대가 바뀌었다          [감지 필요]
        |usb_attached           USB 기기를 꽂았다          [감지 필요]
        |usb_detached           USB 기기를 뽑았다          [감지 필요]
        |boot                   기기가 켜졌다              [언제나]
        |alarm                  z2-alarm이 울렸다          [언제나]
        |notify_action          알림의 버튼을 눌렀다       [언제나]
        |unlock_failed          잠금 해제에 실패했다       [언제나, 준비가 필요]
        |unlock_succeeded       실패 뒤에 잠금이 풀렸다    [언제나, 준비가 필요]
        |voice                  음성 조작으로 말했다       [언제나, 타일이나 어시스턴트에서]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # event:<이름>에 쓸 수 있는 이름입니다. events.jsonl에 나오는 순서 그대로입니다.
        |    # 위쪽 무리는 감지를 켜 두어야 하고(설정 > 상주 서버・자동화), 아래쪽은 직접 준비하는
        |    # 것이라 감지가 꺼져 있어도 됩니다.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                   … 새 버전을 찾아 내려받고 설치 화면으로 넘깁니다
        |# z2-update --check           … 더 새로운 버전이 있는지만 알려 줍니다
        |# z2-update --keep            … 내려받은 .apk를 남깁니다 (기본값은 지웁니다)
        |# z2-update --dir <폴더>      … 앱 폴더 대신 그 폴더에 내려받습니다
        |#
        |# 어디서 오나
        |#   z2term의 GitHub Releases 페이지에서, 손으로 받을 때와 같은 파일입니다
        |#   (z2term-<버전>.apk, 배포용 키로 서명되어 있습니다). 그 밖의 어디에도 연결하지 않고,
        |#   이것을 실행하기 전에는 아무 데도 연결하지 않습니다.
        |#
        |# 할 수 있는 것과 없는 것
        |#   ⚠ **조용히 설치할 수는 없습니다.** 앱이 자기 자신을 바꿀 때 Android는 언제나 자기
        |#     「설치할까요?」 화면을 띄우므로, 마지막 한 번은 반드시 사람이 누릅니다. 이 명령은
        |#     그 화면까지의 모든 단계를 대신합니다.
        |#   ⚠ 처음에는 z2term에 **「출처를 알 수 없는 앱 설치」**를 허용하세요 (그렇게 알려 주며,
        |#     ⚙설정에도 같은 버튼이 있습니다). 그것이 없으면 설치 화면이 아예 뜨지 않습니다.
        |#   ⚠ F-Droid나 스토어에서 설치했다면 이 명령은 거절하고 거기서 업데이트하라고 알려 줍니다:
        |#     그 버전은 우리가 아니라 그쪽이 바꿔야 하기 때문입니다.
        |#
        |# 내려받은 파일
        |#   기본값으로는 앱 자신의 폴더에 떨어지고, 설치가 잘 끝나면 바로 지웁니다 (그리고 다음에
        |#   켤 때도 지웁니다. 자기 자신을 바꾸는 동안 앱이 죽는 것이 보통이기 때문입니다).
        |#   --keep이나 --dir로 바꿀 수 있고, ⚙설정에도 그 버튼을 위한 같은 설정이 있습니다.
        |#
        |# 예: z2-update --check
        |#        z2-update --dir /sdcard/Download --keep
        |#        z2-when time:daily=03:00 run 'z2-update'   # 밤마다 확인 (물어보는 것은 그대로)
    """.trimMargin()

    override val updateUsage: String get() = "사용법: z2-update [--check] [--keep] [--dir <폴더>]"

    override fun updateUpToDate(current: String): String = "z2-update: ${current}이(가) 이미 최신 버전입니다."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: 더 새로운 버전을 찾는 중 ..."

    override val updateHandedToInstaller: String get() = "z2-update: 기기에 설치 화면이 떠 있습니다 — 허용하면 끝납니다."

    override fun updateNoApk(url: String): String = "z2-update: 그 릴리스에는 .apk가 붙어 있지 않습니다. 릴리스 페이지에서 받으세요: $url"

    override val updateNeedPermission: String get() = "z2-update: 먼저 z2term에 \"출처를 알 수 없는 앱 설치\"를 허용하세요 " +
            "(Ajustes > Aplicaciones > Acceso especial > Instalar aplicaciones desconocidas, o el botón en los Ajustes de z2term)."

    override val updateManagedByStore: String get() = "z2-update: 이 빌드는 스토어(F-Droid / Play)에서 설치한 것입니다. 업데이트도 거기서 하세요."

    override fun updateFailed(reason: String): String = "z2-update: 실패했습니다: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# 음성 조작 — 낱말 표 (한국어). voice.sh가 읽습니다. 고쳐 쓰면 말하는 방법을 더하거나 바꿀 수 있습니다.
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
title_rule_voice_locked = 음성 조작 (잠금 중)
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
say_locked = 잠금을 해제해 주세요
msg_help = 시간·날짜·배터리·걸음 수 / 볼륨 3단계 내려 줘·볼륨 50퍼센트 / 다음 곡·멈춰 / 3분 타이머·7시에 깨워 줘 / 내일 아침 7시에 쓰레기 버리라고 알려 줘·일정 / 뉴스·날씨·내일 날씨 / 메모 우유 사기·메모 읽어 줘 / 손전등 켜 줘·스크린샷 / ◯◯ 열어 줘 / ◯◯에게 전화해 줘 / ◯◯까지 안내해 줘·◯◯ 전화번호 / AI … (켜려면 voice.sh ai) / 말 바꾸기: voice.sh words
msg_unknown = 알아듣지 못한 말입니다: {text}
msg_locked = 잠금을 해제하면 "{text}"을(를) 이어서 합니다
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
msg_setup_steps = 걸음 수 세기 규칙 (steps:reach=1000000) 은 오늘 걸음 수를 음성으로 물을 수 있도록 측정을 유지할 뿐입니다. 도달하지 않는 조건이며 아무것도 실행하지 않습니다.
msg_words_file = 낱말 표: {file}  (고쳐 쓰면 말하는 법을 더할 수 있습니다. sh ~/.z2term/macros/voice.sh words 로 엽니다)"""
