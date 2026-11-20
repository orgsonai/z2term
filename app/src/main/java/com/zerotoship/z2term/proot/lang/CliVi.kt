package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Tiếng Việt (`vi`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: thẻ (タブ) / chạm (タップ) / nhấn giữ (長押し) / vuốt (フリック) / tệp / thư mục /
 * Cài đặt (設定) / khay nhớ tạm (クリップボード) / macro / ô (タイル) / bảng cạnh (エッジパネル) /
 * máy chủ thường trú (常駐サーバー) / đoạn lệnh (スニペット)。呼びかけは bạn。
 * ⚠ アポストロフィは使わない (シェルの引用符とぶつかる)。
 */
internal object CliVi : CliLanguage {

    override val code: String = "vi"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgVi) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgVi(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Đang cài bộ GUI",
            cleanInstalling = "🧹 Đang cài lại sạch bộ GUI",
            noPackageManager = "❌ Không tìm thấy trình quản lý gói được hỗ trợ (apk/apt-get/pacman).",
            invalidGeometry = "❌ Độ phân giải không hợp lệ",
            noXvnc = "❌ Không có Xvnc/Xtigervnc (chưa cài tigervnc)",
            alreadyRunning = "✅ GUI đã đang chạy",
            startingXvnc = "▶ Đang khởi động",
            xvncFailed = "❌ Khởi động Xvnc thất bại. Nhật ký:",
            ready = "✅ GUI đã sẵn sàng. Hãy kết nối từ một thẻ GUI của z2term.",
            running = "✅ GUI đang chạy",
            stopped = "⏹ GUI đã dừng",
            stoppedMsg = "⏹ Đã dừng",
            usage = "Cách dùng: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Không cài được bộ GUI (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Hãy kiểm tra mạng. Để sửa, bạn có thể chạy z2gui clean trong terminal.",
            audioInstalling = "🔊 Âm thanh GUI: đang cài PulseAudio",
            audioNoPulse = "⚠️ Âm thanh GUI: không có pulseaudio, tiếp tục không có âm thanh",
            audioNoPactl = "⚠️ Âm thanh GUI: không có pactl, tiếp tục không có âm thanh",
            audioStartFailed = "⚠️ Âm thanh GUI: PulseAudio không khởi động",
            audioReady = "🔊 Âm thanh GUI: z2sink.monitor →",
            menuApps = "Ứng dụng",
            menuWindows = "Cửa sổ",
            menuDesktops = "Màn hình ảo",
            menuPreviousDesktop = "Màn hình trước",
            menuNextDesktop = "Màn hình tiếp theo",
            menuReload = "Tải lại menu"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Số cổng không hợp lệ",
            configOk = "cấu hình sshd(dropbear) ổn",
            installingDropbear = "📦 Chưa cài dropbear — đang cài…",
            dropbearInstallFailed = "❌ Không cài được dropbear. Hãy kiểm tra mạng và tên gói.",
            privilegedPortWarn = "⚠️ Cổng đặc quyền; proot (không root) có lẽ không dùng được (chọn 1024+).",
            lanExposeWarn = "⚠️ Chế độ mở ra LAN/WAN: bắt buộc xác thực bằng khóa, hãy dùng khóa mạnh.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys đang trống. Đã hủy khởi động mở ra LAN.",
            noAuthorizedKeysHint = "   Hãy đăng ký khóa công khai của bạn vào ~/.ssh/authorized_keys rồi thử lại.",
            loopbackBind = "🔒 chỉ loopback. Để mở ra LAN, truyền --lan hoặc đặt Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear ở tiền cảnh (Ctrl-C để dừng)",
            listeningLan = "✅ dropbear đang lắng nghe (root, chỉ xác thực bằng khóa)",
            listeningLoopback = "✅ dropbear đang lắng nghe (root, chỉ xác thực bằng khóa, loopback)",
            startupFailed = "❌ Khởi động dropbear thất bại. Nhật ký:",
            deviceIp = "ip-thiết-bị",
            authKeysHint = "ℹ ~/.ssh/authorized_keys đang trống. Hãy đăng ký khóa công khai của máy khách để kết nối.",
            authKeysExample = "   ví dụ cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Đang chuẩn bị chùm khóa pacman (chỉ lần đầu, không cần mạng). Mất một lúc…",
            "keyring.msgOk" to "✅ Chùm khóa đã sẵn sàng. Giờ có thể cài gói.",
            "keyring.msgInitFail" to "❌ pacman-key --init thất bại. Sẽ thử lại ở lần mở thẻ này tiếp theo.",
            "keyring.msgNoKeyrings" to "❌ Không tìm thấy chùm khóa đi kèm trong /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate thất bại. Sẽ thử lại ở lần mở thẻ này tiếp theo.",
            "action.description" to "Macro thao tác Android có tên. Bật z2term Android actions bằng z2-key permission.\nsave kiểm tra và thay thế định nghĩa; screen=current ghi lại màn hình.\nrun chờ đến khi xong; start trả về ID lượt chạy. Ngắt run sẽ dừng lượt chạy đó.\nMỗi lần một lượt chạy, 64 lệnh nguồn, 0–300 giây (0 = không giới hạn). Thông báo và stop hủy các bước còn lại.\nTọa độ mặc định theo ứng dụng ở phía trước và cần cùng kích thước/hướng màn hình. Cử chỉ thủ công: tối đa 3 giây; lần chạm đã ghi: tối đa 30 giây.\nDùng start từ cử chỉ của bảng; dùng run trong macro shell hoặc lệnh z2-when.\nLưu trong /root/.z2term/actions/NAME.actions (thư mục chính dùng chung). z2-macro actions là bí danh.",
            "action.conditionHelp" to "Điều kiện còn dùng được ui BỘ_CHỌN (có phần tử trên màn hình), color X,Y=TÊN|#RRGGBB (một điểm màn hình theo px; cần screen=) và command SHELL (mã thoát 0; phần còn lại được đọc là shell nên phải đặt cuối).\n| nghĩa là HOẶC và gắn yếu hơn dấu phẩy (VÀ). wait-until MS ĐIỀU_KIỆN / wait-while MS ĐIỀU_KIỆN kiểm tra mỗi 250 ms; 0 chờ tới giới hạn của chính macro. Hết giờ thì lần chạy thất bại.\nCác mục đọc màn hình xem ứng dụng đích (hoặc ứng dụng ở trước) và không cần quyền theo từng ứng dụng.",
            "action.controlHelp" to "version=2 thêm repeat N|forever ... end, if ĐIỀU_KIỆN ... else ... end và call TÊN.\nĐiều kiện dùng khóa của z2-state hoặc foreground=GÓI; dấu phẩy nghĩa là VÀ, ! phủ định một vế.\nTrạng thái điều kiện không có sẽ làm lượt chạy thất bại. Lời gọi được kiểm tra và cố định trước khi chạy.\n64 lệnh nguồn mỗi định nghĩa; 10000 lệnh đã chạy mỗi lượt. Thời gian chờ của gốc và con đều áp dụng.",
            "action.pauseHelp" to "pause [RUN_ID] tạm dừng lượt chạy; bước đang dở được hoãn lại từ đầu. Kéo ngăn thông báo xuống cũng tạm dừng.\nresume [RUN_ID] tiếp tục khi một màn hình khác z2term đứng yên ở phía trước (tối đa 5 giây; nếu không thì vẫn tạm dừng). Trả lời sau đó: state running và mã thoát 0, hoặc paused và mã khác 0. Giới hạn thời gian ngừng đếm khi tạm dừng.",
            "action.uiHelp" to "version=2 cũng hỗ trợ click BỘ_CHỌN, long-click BỘ_CHỌN và wait-ui MS BỘ_CHỌN.\nBộ chọn: text=Tiếp tục, desc=Tùy chọn khác, id=org.example.app:id/next. Khớp chính xác; không cần ngoặc kép quanh dấu cách.\nĐặt target hoặc launch trước. wait-ui tìm mỗi 250ms trong 1–30000ms; nhiều kết quả khớp sẽ lỗi.\nThao tác được gửi một lần; dùng wait-ui cho màn hình tiếp theo. Ô nhập/mật khẩu bị loại trừ.\ninspect GÓI trả về JSON gồm văn bản hiển thị, mô tả và ID của ứng dụng đích đang có tiêu điểm.",
            "adb.mNoPm" to "z2adb: không tìm thấy trình quản lý gói được hỗ trợ (apk/apt-get/pacman).",
            "adb.mInstalling" to "z2adb: đang cài máy khách adb (${d}ADB_PKG) qua ${d}PM ...",
            "adb.mInstallFail" to "z2adb: cài adb thất bại. Hãy cài thủ công rồi thử lại.",
            "adb.mHaveAdb" to "z2adb: adb đã được cài.",
            "adb.mNeedWireless" to "z2adb: nếu ghép nối thất bại, hãy bật Cài đặt > Tùy chọn nhà phát triển > Gỡ lỗi không dây (Android 11+).",
            "adb.mPairPort" to "Cổng ghép nối (Ghép nối thiết bị bằng mã ghép nối): ",
            "adb.mConnPort" to "Cổng kết nối (hiện trong Gỡ lỗi không dây): ",
            "adb.usageText" to """
        z2adb - kết nối đến adb của chính thiết bị này qua Gỡ lỗi không dây (self-adb, không cần máy tính).

        Yêu cầu: bật Cài đặt > Tùy chọn nhà phát triển > Gỡ lỗi không dây (Android 11+).

          z2adb setup                cài máy khách adb (apk/apt-get/pacman, tự phát hiện)
          z2adb pair  <port> [code]  ghép nối (dùng cổng ghép nối + mã 6 chữ số trong Cài đặt)
          z2adb connect <port>       kết nối (dùng cổng trong "Gỡ lỗi không dây")
          z2adb status               hiện "adb devices -l"
          z2adb <bất kỳ thứ khác>    chuyển thẳng cho adb (shell, logcat, install, ...)

        Máy chủ mặc định là 127.0.0.1; truyền host:port để đổi (hoặc đặt Z2ADB_HOST).
        Ví dụ:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Phát âm thanh của một lệnh Linux cục bộ trên Android mà không mở GUI.\ninstall: cài PulseAudio vào hệ thống Linux hiện tại (tải gói).\nrun LỆNH [ĐỐI SỐ...]: nối âm thanh, chạy lệnh, rồi dừng máy chủ âm thanh của nó.\nKhởi động một shell để bật âm thanh cho nhiều lệnh. Không tệp cấu hình nào bị ghi đè.\nCần một thẻ terminal cục bộ và trình phát tương thích PulseAudio. Không hỗ trợ SSH.",
            "audio.missing" to "Thiếu công cụ âm thanh. Hãy chạy: z2-audio install",
            "audio.failed" to "Kết nối âm thanh thất bại; lệnh chưa được chạy.",
            "doctor.head" to "== z2doctor (thiết bị này) ==",
            "doctor.secApp" to "-- ứng dụng --",
            "doctor.secLinux" to "-- phía Linux --",
            "doctor.secPerm" to "-- quyền --",
            "doctor.secAuto" to "-- tự động hóa --",
            "doctor.secReport" to "-- sao chép phần này để nhờ giúp đỡ --",
            "doctor.lVersion" to "phiên bản",
            "doctor.lEngine" to "công cụ",
            "doctor.lDistro" to "bản phân phối",
            "doctor.lKernel" to "nhân",
            "doctor.lDisk" to "dung lượng trống",
            "doctor.lHome" to "thư mục chính",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "thông báo",
            "doctor.lNotifyRead" to "truy cập thông báo",
            "doctor.lBattOpt" to "tối ưu hóa pin",
            "doctor.lStorage" to "bộ nhớ (mọi tệp)",
            "doctor.lSms" to "nhận SMS",
            "doctor.lCapture" to "phát hiện sự kiện",
            "doctor.lServers" to "máy chủ thường trú",
            "doctor.lRules" to "quy tắc tự động hóa",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Cài đặt Android > Ứng dụng > Z2Term > Thông báo: cho phép",
            "doctor.fixNotifyRead" to "-> Cài đặt > Quyền và thông báo > Phát hiện thông báo",
            "doctor.fixBattOpt" to "-> Cài đặt > Bảo vệ tiến trình nền > miễn tối ưu hóa pin",
            "doctor.fixStorage" to "-> Cài đặt > cho phép truy cập mọi tệp (cần để thấy /sdcard)",
            "doctor.fixSms" to "-> Cài đặt > Phát hiện SMS (chỉ cho trình kích hoạt sms:)",
            "doctor.fixCapture" to "-> Cài đặt > Phát hiện sự kiện hệ thống (cần cho trình kích hoạt charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> tự động hóa đang tạm dừng. \"z2-when resume\" hoặc thẻ Tự động hóa",
            "doctor.fixDisk" to "-> còn trống dưới 500 MB. Xóa dữ liệu hệ điều hành không dùng trong Cài đặt",
            "doctor.fixSdcard" to "-> /sdcard ở đây trống; quyền bộ nhớ ở trên là thứ làm nó hiện ra",
            "doctor.noteRedacted" to "(SSID / IP / tên máy chủ được cố ý bỏ đi)",
            "doctor.allGood" to "Không phát hiện vấn đề.",
            "doctor.someBad" to "vấn đề phát hiện được:",
            "doctor.usage" to "cách dùng: z2doctor [--share | --clip]   (--share đưa báo cáo vào menu chia sẻ của Android)",
            "doctor.pausedYes" to "đang tạm dừng",
            "doctor.secExits" to "-- lần trước kết thúc thế nào --",
            "doctor.lExitsNote" to "các lần thoát bất thường gần đây (mới nhất trước):",
            "doctor.lExitsNone" to "thoát bất thường: chưa ghi nhận",
            "doctor.lExitsFile" to "(lịch sử đầy đủ: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> bị dừng vì thiếu bộ nhớ. Hãy chạy ít việc nặng cùng lúc hơn, hoặc giảm máy chủ thường trú / thẻ",
            "doctor.title" to "tự chẩn đoán khi \"không hoạt động\"",
            "doctor.seeAlso" to "Xem thêm: z2scan self (kiểm tra bảo mật, một công cụ khác)",
            "help.header" to "Lệnh riêng của Z2Term",
            "help.list" to """
        |[Phiên bản / thông tin]
        |  z2version [--short]            Phiên bản ứng dụng, công cụ, hệ thống (bản phân phối), nhân
|  z2doctor [--share|--clip]      Vì sao không hoạt động? Tự chẩn đoán + báo cáo để dán
        |
        |[Tính năng điện thoại]
        |  z2-notify [-h] [-b LABEL]...   Thông báo (-h: biểu ngữ, -b: nút trả lời)
        |  z2-ask "câu hỏi"               Hỏi qua ô trả lời trong thông báo; in câu trả lời
        |  z2-listen [-w WORD,...]        Giọng nói thành văn bản, chỉ trên thiết bị (z2-listen -h)
        |  z2-toast "thông điệp"          Thông báo ngắn (thông điệp ngắn trên màn hình)
        |  z2-share "văn bản"             Đưa văn bản vào menu chia sẻ của Android
        |  z2-open <url|path>             Mở URL/tệp trong ứng dụng mặc định
        |  z2-view <file.html>            Đọc trang tạo ở đây trong ứng dụng
        |  z2-edit <file>                 Sửa tệp văn bản trong trình soạn của ứng dụng
        |  z2-qr                          Quét mã QR bằng máy ảnh (một nút mở nó)
        |  z2-img <file>                  Vẽ hình ảnh trong terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Đọc / đặt khay nhớ tạm (set: stdin nếu không có đối số)
        |  z2-file pick | save FILE     Trình chọn / xuất tài liệu Android (z2-file --help)
        |  z2-battery                     Mức pin / trạng thái sạc (JSON)
        |  z2-vibrate [ms]                Rung (mặc định 200ms)
        |  z2-say "văn bản"               Đọc bằng TTS của thiết bị (stdin nếu không có đối số)
        |  z2-torch [on|off|toggle|status]       Đèn pin (mặc định toggle)
        |  z2-media [play|pause|next]     Phím đa phương tiện (cả previous/stop)
        |  z2-volume up|down|N|N%         Âm lượng phương tiện (in hiện tại/tối đa)
        |  z2-audio run COMMAND ...      Chạy lệnh Linux với âm thanh Android
        |  z2-sensor [light|accel|prox]   Đọc một giá trị cảm biến (JSON)
        |  z2-intent -a ACT -d URI ...    Mở bất kỳ Intent Android nào (xem MACRO-GUIDE)
        |  z2-state [key]                 Trạng thái hiện tại dạng JSON (hoặc một khóa thô)
        |  z2-screen keepon 1h | off      Ngăn màn hình tự tắt, trong một lúc
        |  z2-tile set 1 backup.sh        Đặt macro/lệnh lên ô Cài đặt nhanh (12)
        |  z2-icon edit 1 | edit notify   Tự vẽ biểu tượng ô / thanh (24/48/64 điểm)
        |  z2-alarm at|daily HH:MM [name] Trình kích hoạt theo giờ -> events.jsonl (cả list/cancel)
        |  z2-when <trigger> run <cmd>    Tự chạy khi sạc/pin/giờ/sự kiện (z2-when: cách dùng)
        |  z2-macro list|install <name>   Ví dụ tự động hóa đi kèm (xem MACRO-GUIDE)
        |  z2-session list|new|send|...   Điều khiển các thẻ của ứng dụng này (z2-session: cách dùng)
        |  z2-usb list | allow [device]   Dùng thiết bị USB từ Linux (Android hỏi đồng ý)
        |  z2-update [--check]            Cập nhật z2term từ GitHub Releases (bạn chấp thuận)
        |
        |[Ứng dụng đồ họa (GUI)]
        |  z2gui start [WxH] | stop | status   Màn hình nền Linux (ví dụ z2gui start 1280x720)
        |  z2run <gui-app>                Mở ứng dụng GUI (mở cả thẻ GUI)
        |
        |[Kết nối]
        |  z2adb pair/connect/shell ...   adb đến chính điện thoại này, không cần máy tính  (z2adb help)
        |  sshd [-p N]                    Máy chủ SSH (mặc định: chỉ 127.0.0.1, khóa)
        |
        |[Bảo mật]
        |  z2scan self [--save]           Tự chẩn đoán thiết bị/localhost này (z2scan help)
        |  z2scan diff                    Chỉ những gì thay đổi so với mốc (thoát 1 = có mới)
        |  z2scan net|host|cve            nmap/lynis/trivy trên localhost (đích từ xa cần đồng ý rõ ràng)
        |
        |[Trợ giúp]
        |  z2help | z2term                Danh sách này
        |
        |Mỗi lệnh ở trên tự giải thích: thêm --help (ví dụ "z2-tile --help").
        |Thêm: "z2adb help", chạy "z2gui" không đối số, hoặc mục 11 của HANDBOOK.
    """,
            "help.edge" to "[Bảng nổi]\nz2-edge --help   Định nghĩa bảng và cập nhật nội dung\nz2-key --help    Thao tác toàn cục của Android\nz2-shot --help   Chỉ chụp phần đã khoanh\nz2-app --help    Liệt kê ứng dụng và xuất biểu tượng\nz2-action --help Macro thao tác Android có tên\nz2-ui --help     AI điều khiển ứng dụng (trợ giúp là hướng dẫn cho tác tử AI)",
            "macro.rssBody.head" to """
#
# Chuẩn bị:
#   1) Mỗi dòng một URL nguồn tin trong:  ~/.z2term/rss/feeds.txt
#   2) Kiểm tra mỗi 30 phút:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Tùy chọn - mỗi dòng một nguồn tin hoặc từ không được để chìm mất:  ~/.z2term/rss/important.txt
#      Mục khớp có thông báo riêng, nên nguồn tin ồn ào không đẩy được nó đi.
#      Viết một phần URL hoặc một phần tiêu đề (ví dụ example.org).
#   4) Đăng ký thao tác thông báo trong Tự động hóa:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Mở gửi bài đến trình duyệt; Danh sách mở mọi thứ đã thu thập để đọc trong z2term.
#      Lưu ý: mở màn hình từ nút thông báo cần quyền "hiển thị trên ứng dụng khác".
#   5) Đọc danh sách đã thu thập: sh ~/.z2term/macros/rss.sh view
#      Quản lý cả hai quy tắc trong Danh sách lệnh -> Tự động hóa -> Quy tắc tự động hóa.
#      Hãy sửa quy tắc thông báo hiện có thay vì thêm cái khác.
#   6) Thông báo: mặc định lần kiểm tra theo lịch có báo, lần kiểm tra từ danh sách thì không.
#        sh ~/.z2term/macros/rss.sh notify off        # kiểm tra theo lịch: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Làm mới / Bài viết trong danh sách: on / off
#      Cả hai cũng là công tắc trong Cài đặt trên danh sách, đổi lúc nào cũng được.
#
# Cần: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Ảnh thu nhỏ (Cài đặt trên danh sách) cần thêm Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Pin: kiểm tra càng thường xuyên càng tốn. Đừng dưới 30 phút.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "số dòng tối đa giữ trong seen/latest",
            "macro.cNoPy" to "cần python3 (ví dụ apk add python3)",
            "macro.cWriteFeeds" to "Viết mỗi dòng một URL nguồn tin vào:",
            "macro.cFeedsHint" to "# Mỗi dòng một URL nguồn tin (dòng bắt đầu bằng # bị bỏ qua)",
            "macro.cSkipFail" to "lặng lẽ bỏ qua nguồn tin không tải hay phân tích được (các nguồn khác vẫn tiếp tục)",
            "macro.cDiff" to "# Bỏ những gì đã thấy để chỉ giữ cái mới (cùng cách so với mốc của z2scan).\n# Ngày và thứ tự của nguồn tin không đáng tin; cả hai đều không chắc chắn.",
            "macro.cPrepend" to "# Xếp mục mới nhất lên trước để chế độ \"đầu (head)\" của widget đọc đúng.",
            "macro.cNotify" to "%s mới",
            "macro.cOpen" to "Mở",
            "macro.cListNone" to "Chưa thu thập được gì — hãy chạy một lần không đối số trước.",
            "macro.cListHint" to "Liệt kê bằng: rss.sh list [số]",
            "macro.cList" to "Danh sách",
            "macro.cPageTitle" to "Nguồn tin",
            "macro.cPageSites" to "%s trang",
            "macro.cPageArticles" to "%s bài",
            "macro.cPageDoc" to "# Dựng trang đọc (z2-view hiện nó trong ứng dụng; không máy chủ, không trình duyệt).\n" +
            "# Nhóm theo trang, mới nhất trước, mỗi bài có nút Mở. Chủ đề của điện thoại\n" +
            "# đến dưới dạng biến CSS, nên trang theo màu của terminal.",
            "macro.cViewHint" to "Đọc bằng: rss.sh view",
            "macro.cHitMax" to "số thông báo riêng từng bài tối đa trong một lần chạy (phần còn lại vào bản tóm tắt)",
            "macro.cSplitDoc" to "# Tách riêng những gì không được mất. Thân bản tóm tắt chỉ có 3 dòng, nên nguồn tin ồn ào\n" +
            "# cập nhật cùng lúc sẽ đẩy mất cái quan trọng. Mục khớp có **thông báo\n" +
            "# riêng** (ứng dụng cấp id riêng cho mỗi thông báo, nên không gì bị thay thế).",
            "macro.cNameDoc" to "# Đặt URL vào tên thông báo: nhấn nút sẽ trả nó về dưới dạng {name} trong\n" +
            "# notify_action, nên đúng bài được mở kể cả khi trên màn hình có nhiều thông báo (chuẩn bị 4).",
            "macro.cListDoc" to "# Hiện những gì đã thu thập thành danh sách dễ đọc (không màu — phải giống tệp/widget).\n" +
            "# latest.txt vẫn là dữ liệu thô, mỗi dòng một bài (tail của widget và rss-open.sh đọc nó).",
            "macro.cSettings" to "Cài đặt",
            "macro.cArchive" to "Lưu trữ",
            "macro.cDelete" to "Xóa",
            "macro.cThumbs" to "Ảnh thu nhỏ",
            "macro.cSave" to "Giữ bài cũ",
            "macro.cNotifyPoll" to "Báo bài mới (kiểm tra theo lịch)",
            "macro.cNotifyView" to "Báo bài mới (kiểm tra từ danh sách)",
            "macro.cDeleteSite" to "Xóa mọi bài của trang này",
            "macro.cConfirmSite" to "Gỡ %s bài của %h khỏi danh sách?",
            "macro.cArticles" to "Bài viết",
            "macro.cPrev" to "Trước",
            "macro.cNext" to "Sau",
            "macro.cCount" to "%s bài",
            "macro.cUndated" to "Không có ngày",
            "macro.cArchiveEmpty" to "Chưa giữ lại gì. Bật Giữ bài cũ trong Cài đặt, và các bài đã thu thập sẽ ở lại đây.",
            "macro.cSearch" to "Tìm",
            "macro.cSearchWord" to "Từ khóa (cách nhau bằng dấu cách để yêu cầu tất cả)",
            "macro.cByMonth" to "Theo tháng",
            "macro.cNoHit" to "Không tìm thấy gì.",
            "macro.cDropMonth" to "Xóa tháng này",
            "macro.cDropConfirm" to "Xóa %s bài đã giữ của %m? Không hoàn tác được.",
            "macro.cOnOff" to "Hãy cho on hoặc off",
            "macro.cNoPillow" to "Ảnh thu nhỏ cần Pillow cho Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Chuẩn bị: cài phần thu thập trước
#   z2-macro install rss
# Widget: gán "rss-open" cho một nút trong cài đặt của widget trạng thái.
#
# z2-run: gán "rss-open" cho một nút của widget trạng thái (để thử ở đây: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Chưa có bài nào",
            "macro.cAllRead" to "Không có gì mới để mở",
            "macro.cPick" to "# Các dòng của latest.txt có dạng \"tiêu đề  URL\". Lấy URL đầu tiên chưa mở.",
            "macro.cCap" to "giới hạn danh sách đã mở để nó không dài mãi",
            "macro.copied" to "Đã sao chép mã: ${d}{code}",
            "macro.cleared" to "Đã xóa mã đã sao chép",
            "macro.otpTitle" to "Mã một lần",
            "macro.cTtl" to "số giây trước khi xóa bản sao (chỉ khi nó vào trực tiếp)",
            "macro.cCode" to "# Ứng dụng đã trích mã sẵn. Không làm gì nếu nó không trích được.",
            "macro.cTry" to "# Thử đặt trực tiếp. Chỉ vào được khi **bạn đang nhìn ứng dụng**:\n" +
            "# Android 10+ chỉ cho ứng dụng ở phía trước ghi khay nhớ tạm, nên lượt chạy\n" +
            "# nền bị bỏ qua lặng lẽ. Đọc lại để xem đã vào chưa.",
            "macro.cFallback" to "  # Không vào = bạn không nhìn ứng dụng. Thay vào đó hãy đưa qua nút\n" +
            "  # \"Sao chép\" của thông báo (nhấn nó sẽ đưa ứng dụng ra phía trước).\n" +
            "  # ⚠ Thứ vào bằng cách này không bị xóa theo TTL bên dưới: xóa cũng cần ở phía trước.",
            "macro.cClear" to "# Sau TTL, chỉ làm trống khay nhớ tạm nếu nó vẫn chứa mã đã sao chép\n" +
            "# (thứ sao chép sau đó không bị đụng đến).",
            "macro.cHow" to """
# ■ Sao chép số điện thoại từ thông báo cuộc gọi
#   Tìm trong tiêu đề, rồi trong nội dung, một số điện thoại đứng riêng và đưa ra nút Sao chép.
#   Gồm cả người gọi đã lưu và chưa lưu; không truy vấn danh bạ.
#   Chỉ có tên thì không tìm ra số. Chỉ dùng quyền truy cập thông báo.
#   Tên tệp unknown-call.sh được giữ để các quy tắc hiện có tiếp tục hoạt động.
""",
            "macro.cIsNum" to """
# Có phải một số đứng riêng? Bỏ mọi ký tự có thể có trong số (chữ số + - ( ) dấu cách);
# nếu **không còn gì** và có từ 7 đến 15 chữ số, coi đó là số.
#   -> Trường có tên hoặc chữ khác không phải số đứng riêng; kiểm tra trường còn lại.
#   -> "Không xác định"/"Số ẩn" cũng rơi vào đây (nới lỏng phép thử bên dưới nếu cần chúng).
# ⚠ Đừng viết điều này thành case [!...]: dấu ) trong mẫu bị đọc là dấu phân cách của case.
""",
            "macro.cScan" to "# Người gọi thường ở tiêu đề, nhưng vài ứng dụng điện thoại đặt trong nội dung. Kiểm tra cả hai.",
            "macro.cSkip" to "# Không trường nào có số đứng riêng: không có gì để sao chép.",
            "macro.cWhat" to "# Danh mục cho biết cái nào đã kích hoạt (cùng giá trị với notify:category= đã đăng ký).",
            "macro.cCopy" to """
# Đưa số qua nút "Sao chép" của thông báo (-c). Gọi z2-clip set ở đây
# sẽ không vào: Android 10+ chỉ cho ứng dụng ở phía trước ghi khay nhớ tạm, và trong
# cuộc gọi đó là ứng dụng điện thoại. Nhấn nút đưa z2term ra phía trước trong khoảnh khắc đó.
""",
            "macro.missed" to "Cuộc gọi nhỡ",
            "macro.incoming" to "Cuộc gọi đến",
            "macro.title" to "Sao chép số điện thoại",
            "macro.stNew" to "mới",
            "macro.stSame" to "giống",
            "macro.stDiff" to "khác",
            "macro.msgInstalled" to "đã cài:",
            "macro.msgSame" to "bản giống hệt đã được cài:",
            "macro.msgOutdated" to "bản của bạn khác bản đi kèm:",
            "macro.msgHowUpdate" to "xem trước: z2-macro diff %s   /   thay bằng bản đi kèm: z2-macro install -f %s (chỉnh sửa của bạn cũng mất)",
            "macro.msgNotInstalled" to "chưa cài (hãy chạy z2-macro install trước):",
            "macro.msgRemoved" to "đã gỡ:",
            "macro.msgAbsent" to "chưa cài:",
            "macro.msgRemoveDiffers" to "bản của bạn khác bản đi kèm nên chưa gỡ:",
            "macro.msgHowRemove" to "xem trước: z2-macro diff %s   /   vẫn gỡ: z2-macro remove -f %s (chỉnh sửa của bạn cũng mất)",
            "macro.msgNoDiffTool" to "ở đây không có diff. Bạn vẫn đọc được bản đi kèm bằng z2-macro show.",
            "macro.msgSameAsBundled" to "giống hệt bản đi kèm.",
            "macro.msgNotFound" to "không tìm thấy ví dụ:",
            "macro.msgHintResident" to "Để giữ nó chạy, hãy đăng ký trong Cài đặt -> Máy chủ thường trú:",
            "macro.msgHintRun" to "Cách chạy:",
            "macro.remindBody.head" to """
#
# Chuẩn bị (một lần):
#   sh ~/.z2term/macros/remind.sh setup   đăng ký hai móc và hai ô
#
# Cú pháp:
#   remind.sh <khi nào> <nội dung>   thêm lời nhắc
#   remind.sh list                   liệt kê (số đứng trước)
#   remind.sh del <n|all>            hủy (rm cũng được)
#   remind.sh peek                   hiện danh sách trong thông báo (có nút Xóa)
#   remind.sh ask                    hỏi qua ô trả lời của thông báo (cho ô Cài đặt nhanh)
#   remind.sh setup                  đăng ký móc và ô (một lần)
#   remind.sh help                   văn bản này
#
# <khi nào> - một lần:
#   30m / 90s / 2h                   tính từ bây giờ
#   18:30                            18:30 tiếp theo (ngày mai nếu đã qua)
#   sat 21:00                       ngày trong tuần phù hợp tiếp theo, một lần (cả lúc muộn hơn hôm nay)
#   Tiếng Nhật: 土曜日の夜九時 = sat 21:00 ; 朝七時 = 07:00 ; 夜九時半 = 21:30
#   Tiếng Nhật: 夜12時 không rõ nghĩa; với nửa đêm hãy dùng 00:00 kèm ngày cụ thể.
#   tomorrow 18:30 / 3d 07:00        theo ngày (3d = 3 ngày nữa)
#   07/30 19:00                      tháng/ngày (năm sau nếu đã qua)
#   2030 07/30 19:00                 kèm năm
#   203007301900 / 07301900          chỉ chữ số (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ không có giờ thì giữ **giờ hiện tại trong ngày** (3d / 07/30 ...)
#
# <khi nào> - lặp lại:
#   daily 07:00 / weekday 09:00      mỗi ngày / thứ 2-thứ 6
#   weekly mon 09:00                 vào ngày đó trong tuần
#   monthly 15 09:00                 vào ngày đó trong tháng
#   yearly 07/30 19:00               vào tháng và ngày đó
#   every 19:00 / every wed 19:00    "every" dùng riêng được (từ tiếp theo quyết định)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Ví dụ:
#   remind.sh 30m uống thuốc
#   remind.sh tomorrow 18:30 đổ rác
#   remind.sh monthly 25 10:00 tiền nhà
#   remind.sh list        ->  1  ⏰ 07/30 18:30  đổ rác
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (sau đó chỉ cần: remind.sh 30m ...)
""",
            "macro.cStore" to """# Một lời nhắc = một dòng trong ${d}DIR/<id>.txt, phân tách bằng tab: loại / nhãn / id-khi / nội dung.
#   loại: once (sắp tới) / fired (đã kích hoạt) / repeat (lặp lại)
#   id-khi: chỉ cho repeat (để del gọi được z2-when remove). Nếu không thì "-".
# Nội dung đứng cuối nên có thể chứa mọi thứ trừ TAB.""",
            "macro.cParse" to """# Đọc 1-3 từ và quyết định:
#   KIND ... once|repeat   PLAN ... nhãn hiển thị   SPEC ... dạng z2-alarm/z2-when nhận   USED ... số từ đã dùng""",
            "macro.pDaily" to "mỗi ngày ",
            "macro.pWeekday" to "ngày thường ",
            "macro.pWeekly" to "mỗi tuần ",
            "macro.pMonthly" to "hằng tháng ngày ",
            "macro.pYearly" to "mỗi năm ",
            "macro.mDay" to "",
            "macro.mBadDate" to "không có ngày đó (ví dụ 07/30):",
            "macro.cEvery" to """  # Dạng ngắn "every ...". **Dạng của từ tiếp theo** chọn daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Số từ không đổi (một từ thay bằng từ khác), nên USED vẫn đúng.""",
            "macro.cExpand" to "# Quyết định \"every\" nghĩa là kiểu lặp nào, dựa vào từ theo sau.",
            "macro.cDigits" to """    # Dạng chỉ chữ số: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Kiểm tra 12 chữ số trước 8 (mẫu 8 chữ số cũng khớp chuỗi 12 chữ số).""",
            "macro.cYmd" to """  # Viết dưới dạng ngày. ⚠ z2-alarm không nhận ngày, nên cái này cũng thành độ trễ.""",
            "macro.cCivil" to """# Ngày dương lịch -> số ngày kể từ 1970-01-01 (có năm nhuận; days_from_civil của Howard Hinnant).
# ⚠ date -d "2026-07-30" không đáng tin với busybox, nên tính ở đây.
# ⚠ Chỉ dùng hiệu số ngày, còn giờ để [day_epoch] lo, nó triệt tiêu múi giờ.""",
            "macro.cCron" to """# Dựng biểu thức cron (phút giờ ngày-trong-tháng tháng ngày-trong-tuần) từ HH:MM và một trường ngày.
# KHÔNG dùng expr để bỏ số 0 đầu: nó thoát với 1 khi kết quả là 0, nên vế phải
# của `expr "${d}m" + 0 || echo "${d}m"` cũng chạy và giá trị thành hai dòng.""",
            "macro.afterLabel" to """# "30m" -> "sau 30m (14:35)". Hiện giờ giúp kiểm tra ngay.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "sau ${d}num${d}u (${d}at)" || echo "sau ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "cách dùng: remind.sh <khi nào> <nội dung>",
            "macro.mBadWhen" to "không đọc được thời gian (thử: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "viết giờ theo dạng HH:MM:",
            "macro.mBadRange" to "giờ ngoài phạm vi (00:00-23:59):",
            "macro.mNoTime" to "chưa cho giờ (ví dụ daily 07:00):",
            "macro.mBadDow" to "ngày trong tuần không rõ:",
            "macro.mPastTime" to "giờ đó đã qua:",
            "macro.cDays" to """    # Dạng theo ngày. Không có giờ thì giữ **giờ hiện tại trong ngày** (không bao giờ tự đặt mặc định).
    # Có thể đến dưới dạng một từ, nên dạng viết liền cũng được chấp nhận.""",
            "macro.cDayEpoch" to """# Đổi "HH:MM, N ngày nữa" thành giây epoch. ⚠ busybox không có date -d "tomorrow",
# nên tính nửa đêm hôm nay trước rồi cộng ngày và giờ.
# ⚠ Giá trị có số 0 đầu ("08") bị ${d}(()) đọc là bát phân, nên số 0 bị bỏ.
# ⚠ Một ngày được cộng là 86400s, nên vào ngày đổi giờ có thể lệch một giờ.""",
            "macro.cFmtAt" to """# Nhãn hiện trong danh sách. ⚠ Giữ "tomorrow" sẽ sai khi đã qua ngày.""",
            "macro.mNoBody" to "hãy nói cần nhắc điều gì",
            "macro.mNoAlarm" to "không lên lịch được",
            "macro.mNone" to "chưa lên lịch gì",
            "macro.mFired" to " (đã kích hoạt)",
            "macro.mRemoved" to "đã gỡ:",
            "macro.mNoSuchNum" to "không tìm thấy mục (xem remind.sh list):",
            "macro.mUsageDel" to "cách dùng: remind.sh del <n|all>",
            "macro.bDone" to "Xong",
            "macro.bDelete" to "Xóa",
            "macro.mAskDel" to "Gỡ cái nào?",
            "macro.mAskDelH" to "số / all",
            "macro.mDeletedTitle" to "🗑 Đã gỡ",
            "macro.cPeek" to """# Hiện danh sách trong thông báo (từ ô "danh sách"). **Giữ các con số** — chúng là thứ
# dùng để nói gỡ cái nào. ⚠ Liệt kê mà không có cách gỡ là điều bị phàn nàn; vì vậy có nút.""",
            "macro.cAskDel" to """# Nút "xóa": hỏi một số và chuyển cho [cmd_del] (cùng đường với CLI).""",
            "macro.bS1" to "+10 phút",
            "macro.bS2" to "+1 giờ",
            "macro.mAgain" to "- nhắc lại sau",
            "macro.mTitle" to "⏰ Lời nhắc",
            "macro.mAsk1" to "Nhắc điều gì?",
            "macro.mAsk1H" to "ví dụ uống thuốc",
            "macro.mAsk2" to "Khi nào?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "vui lòng nhập lại",
            "macro.mAskGiveUp" to "Không đọc được ${ASK_TRIES} lần. Bạn cũng có thể thêm từ terminal: remind.sh 30m uống thuốc",
            "macro.mOkTitle" to "⏰ Đã lên lịch lời nhắc",
            "macro.mNgTitle" to "⚠ Chưa lên lịch",
            "macro.cHooks" to """  # Một móc cho "lời nhắc đã kích hoạt", một móc cho "đã chạm nút thông báo". Chỉ
  # hai cái này, dù có bao nhiêu lời nhắc. Không cái nào phụ thuộc công tắc phát hiện.""",
            "macro.cTiles" to """  # Chỉ điền vị trí trống và vị trí vốn của mình (không bao giờ ghi đè của người khác).
  # Tên macro kèm đối số hoạt động từ 0.8.275 (trước đó phải viết sh + đường dẫn đầy đủ).""",
            "macro.mSetupHooks" to "móc đã đăng ký:",
            "macro.mSetupTiles" to "ô:",
            "macro.mPathHint" to "Lưu ý: thẻ này chưa chạy được remind.sh theo tên. Hãy mở thẻ mới, hoặc chạy ở đây " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH.",
            "macro.cPathHint" to """  # ⚠ Thư mục macro chỉ có trong PATH từ 0.8.287, nên **các thẻ mở trước đó**
  # vẫn dùng PATH cũ. Kiểm tra tên có tìm thấy không và nói cần làm gì nếu không
  # (nếu không thì `command not found` không có lý do nhìn thấy được).""",
            "macro.mPlace" to "Lưu ý: bạn vẫn phải tự đặt các ô, từ màn hình sửa (bút chì) của Cài đặt nhanh.",
            "macro.lRemind" to "nhắc",
            "macro.lList" to "danh sách",
            "macro.cSelf" to "# Chỉ phản hồi thông báo của chính mình (id của chúng ta luôn bắt đầu bằng r)",
            "macro.cKeepRepeat" to "      # Đừng xóa lời nhắc lặp lại ở đây: nó phải kích hoạt lại vào ngày mai.",
            "macro.cAsk" to """# Đường dùng bởi ô / thông báo. ⚠ **Luôn trả lời bằng thông báo**:
# ở đây không ai nhìn terminal, nên lỗi in ra stderr sẽ được hiểu là "tôi đã nhấn mà
# không có gì xảy ra" (thực ra có — lý do chỉ vào run.log của ô).
#   Không đọc được -> nói lý do và hỏi lại, tối đa $ASK_TRIES lần (câu trả lời trước được giữ)
#   Đã lên lịch    -> hiện kế hoạch và nội dung trong thông báo""",
            "macro.cSnoozeCancel" to "bỏ báo thức hoãn nếu lời nhắc đã được hoãn trước khi xong",
            "macro.qrBody.head" to """
#
# Cách dùng:
#   qr.sh "https://example.com"       mã hóa một chuỗi và vẽ ở đây
#   qr.sh -f notes.txt                mã hóa nội dung một tệp
#   z2-clip get | qr.sh               mã hóa nội dung khay nhớ tạm
#   qr.sh -o ~/qr.png "text"          lưu PNG (không vẽ gì)
#   qr.sh -t "text"                   hiện bằng khối thay vì hình ảnh
#   qr.sh -s 24 "text"                chọn kích thước (theo cột; mặc định vừa chiều rộng)
#
# z2-run: qr.sh "https://example.com"   (sau khi cài chỉ cần tên. Chạy một lần)
#
# # Điều kiện trước (thiếu thì không chạy, hoặc trông khác)
#
#   1) Ứng dụng làm việc mã hóa ... trong thẻ z2term không cần cài
#      gì (z2-qr mã hóa). Nếu đã có qrencode thì dùng nó.
#      Nơi không với tới ứng dụng (qua ssh), hãy cài một lần cho mỗi thẻ (bản phân phối):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Nếu không có cả hai, lệnh cài được hiện ra và script dừng.
#
#   2) Hình ảnh (mặc định) chỉ hiện trong thẻ của ứng dụng này ... nó được vẽ bằng
#      đồ họa Kitty. Qua ssh, hoặc trong terminal không hiện ảnh, bạn sẽ nhận
#      một dòng ký tự vô nghĩa. Ở đó hãy dùng -t.
#
#   3) -t (khối) phụ thuộc phông của terminal ... phông để khe giữa các
#      dòng vẫn đọc được bằng mắt nhưng máy ảnh thì không. Để quét bằng máy ảnh,
#      hãy dùng hình ảnh mặc định hoặc PNG của -o.
#
#   4) Tỉ lệ khung được giả định ... xem "Tỉ lệ khung" bên dưới. Bị bẹp? Z2_QR_ASPECT.
#
# Tùy chọn:
#   -f FILE   đọc đầu vào từ tệp (nếu không thì đối số, nếu không nữa thì stdin)
#   -o PNG    lưu PNG thay vì vẽ. Nhiều mã sẽ thành
#             qr-1.png / qr-2.png ...
#   -t        hiện bằng khối. Cho terminal không có ảnh, và qua ssh
#   -s N      kích thước theo cột khi vẽ. Mặc định tối đa 34, giới hạn theo chiều rộng
#   -h        trợ giúp này
#
# Về đầu vào dài:
#   Một mã chứa lượng cố định (tối đa 2953 byte chữ-số; vài trăm nếu
#   muốn đọc được). Đầu vào trên 900 byte được tách tại ngắt dòng
#   thành nhiều mã, đánh số [1/3] và hiện theo thứ tự.
#   CẢNH BÁO một dòng quá dài không vừa một mã thì không mã hóa được.
#
# Tỉ lệ khung:
#   Terminal không cho biết kích thước ô, nên giả định tỉ lệ ký tự 1:2
#   để mã thành hình vuông. Nếu trông bị bẹp, chỉnh bằng biến môi trường:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (nhỏ hơn = cao hơn. Mặc định 0.5)
""",
            "macro.cMaxBytes" to "số byte tối đa mỗi mã (vượt thì tách)",
            "macro.cPngModule" to "số điểm mỗi mô-đun trong PNG đã lưu",
            "macro.cTargetPx" to "chiều rộng mục tiêu tính bằng pixel khi vẽ",
            "macro.cMargin" to "vùng yên tĩnh quanh mã (mô-đun; cần để quét)",
            "macro.cUsageFn" to "Hiện khối chú thích đầu tệp làm trợ giúp (không cố định số dòng).\n" +
            "# Dòng trống không chặn nó (NF quyết định), nên các mục cách nhau bằng dòng trống vẫn giữ nguyên.",
            "macro.cNeedEncoder" to "Điều kiện 1: thứ để mã hóa. qrencode nếu có, nếu không thì ứng dụng.",
            "macro.mMissing" to "qr.sh: ở đây không có gì để mã hóa - không có qrencode, và không với tới ứng dụng",
            "macro.mInstallOnce" to "  Hãy cài một lần trong thẻ này:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Tạo PNG. qrencode chọn kích thước mô-đun bằng cách thử; ứng dụng nhận một mục tiêu.",
            "macro.cMakeBlocks" to "Vẽ bằng ký tự khối. Cả hai đường đều xuất màu riêng, nên chủ đề nào cũng ổn.",
            "macro.qrBody.mNoTmp" to "không tạo được thư mục làm việc",
            "macro.qrBody.mUnreadable" to "không đọc được:",
            "macro.qrBody.mEmpty" to "đầu vào trống",
            "macro.mSizeNum" to "-s cần một số",
            "macro.qrBody.mUsageHint" to "xem qr.sh -h",
            "macro.mPieceFail" to "qr.sh: không tạo được mã thứ %d: %s",
            "macro.qrBody.cCollect" to "---- thu thập đầu vào ----",
            "macro.cSplit" to "---- tách tại ngắt dòng nếu quá dài ----",
            "macro.cSplit2" to "Chuyển sang mảnh tiếp theo ngay trước khi tổng hiện tại vượt MAX_BYTES. Dòng không bao giờ\n# bị cắt giữa chừng, nên văn bản nhiều byte vẫn nguyên vẹn.",
            "macro.cTrim" to "Bỏ ngắt dòng cuối (không bao giờ mã hóa ngắt dòng mà đầu vào không có).",
            "macro.cSize" to "---- chọn kích thước hiển thị theo chiều rộng terminal ----",
            "macro.cPngWidth" to "Trả về chiều rộng pixel của PNG (byte 16-19, IHDR).",
            "macro.cModulePx" to "Suy ra số điểm mỗi mô-đun từ chiều rộng pixel mục tiêu.",
            "macro.cInline" to "Vẽ PNG tại chỗ bằng giao thức đồ họa Kitty.\n# c/r cố định số ô nó chiếm, rồi in ngần ấy ngắt dòng để đặt con trỏ bên dưới.",
            "macro.cEmit" to "---- xuất ----",
            "macro.cAnsi" to "ANSIUTF8 xuất màu riêng, nên sáng/tối đều đúng với chủ đề nào cũng vậy.",
            "macro.mdBody.head" to """
#
# Cách dùng:
#   md.sh README.md                hiện ở đây, trong terminal này
#   md.sh -v README.md             mở trong ứng dụng (z2-view)
#   cat notes.md | md.sh           đọc từ đầu vào chuẩn
#   md.sh -o page.html README.md   ghi HTML rồi dừng (không mở gì)
#
# z2-run: md.sh README.md   (sau khi cài chỉ cần tên. Chạy một lần)
#
# Tùy chọn:
#   -v        mở trong ứng dụng (z2-view)
#   -o FILE   ghi HTML vào đó. Ở đây không hiện gì
#   -w N      xuống dòng ở N cột (mặc định: chiều rộng màn hình, tối đa 100)
#   -p        không màu, không trang trí (văn bản thuần)
#   -t TITLE  tiêu đề màn hình đọc (mặc định: tiêu đề đầu tiên, nếu không thì tên tệp)
#   -h        trợ giúp này
#
# Tài liệu dài, từng màn hình: md.sh README.md | less -R   (-R giữ màu)
#
# Không cần cài gì: sh và awk là đủ.
# Xử lý tiêu đề, đoạn văn, danh sách (lồng nhau, đánh số, [ ]/[x]), trích dẫn, mã, bảng,
# đường kẻ, liên kết, hình ảnh và front matter (---). CẢNH BÁO tiêu đề setext
# (=== / ---) và liên kết tham chiếu ([văn bản][1]) giữ nguyên như đã viết.
#
# Điều cần biết:
#   - Hình ảnh chỉ hiện tại chỗ trong thẻ của ứng dụng này (vẽ bằng đồ họa
#     kitty qua z2-img). Qua ssh bạn sẽ nhận chú thích và đường dẫn.
#   - Liên kết chạm được (OSC 8). Terminal không có tính năng này hiện chúng thành văn bản.
#   - Terminal này không vẽ chữ nghiêng, nên *nhấn mạnh* được gạch chân (như man).
#   - Bảng rộng hơn màn hình được tách thành các dòng "tiêu đề: giá trị".
#   - -v nhúng vào trang các hình có trên thiết bị này (mỗi hình tối đa 1,5 MB).
#     Màn hình đọc không lấy gì từ mạng, nên hình http(s)
#     chỉ còn là chú thích.
""",
            "macro.mdBody.cCollect" to "# Thu thập đầu vào (nhiều tệp thành một lần đọc, theo thứ tự đã cho).",
            "macro.cTitle" to "# Tiêu đề: -t, nếu không thì tiêu đề # đầu tiên, nếu không nữa thì tên tệp",
            "macro.cWidth" to "# Chiều rộng màn hình",
            "macro.cColorEnv" to "# Lối thoát khi màu phải (hoặc không được) đi qua ống dẫn, ví dụ vào trình phân trang",
            "macro.cImgWhere" to "# Hình ảnh chỉ hiện tại chỗ trong một thẻ (đồ họa kitty) khi có z2-img.",
            "macro.cAmbi" to "# Đếm ký tự mơ hồ (đường kẻ, mũi tên, số trong vòng tròn) với độ rộng nào. Dùng\n" +
            "# Z2_MD_AMBIWIDTH=2 nếu Cài đặt đếm chúng là độ rộng đầy đủ. Mặc định 1, như ứng dụng.",
            "macro.cAwkFile" to "# CẢNH BÁO chương trình awk được ghi vào một TỆP và truyền bằng -f. Nếu truyền làm\n" +
            "#   đối số, gawk cắt chương trình ở chỗ một ký tự nhiều byte (như\n" +
            "#   tiếng Nhật trong các ghi chú này) nằm vắt qua giới hạn đọc 4096 byte, và chết với\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Nhúng hình vào trang của màn hình đọc dưới dạng URI data:.\n" +
            "# CẢNH BÁO z2-view không lấy gì từ mạng (tắt JavaScript và tải), nên\n" +
            "#   hình không được nhúng đơn giản là không hiện. 4 MB cho cả trang.",
            "macro.cImgLoop" to "# Chỉ tài liệu có hình mới được đọc từng dòng (để gọi z2-img tại chỗ)",
            "macro.mdBody.mUsageHint" to "xem md.sh -h",
            "macro.mdBody.mNoTmp" to "không tạo được thư mục tạm",
            "macro.mdBody.mUnreadable" to "không đọc được tệp:",
            "macro.mdBody.mEmpty" to "không có gì để đọc",
            "macro.mWidthNum" to "-w cần một số",
            "macro.mNoDir" to "không tạo được thư mục để đặt vào",
            "macro.mConvFail" to "chuyển đổi thất bại",
            "macro.mNoView" to "ở đây không có z2-view (cần cập nhật ứng dụng). HTML nằm ở:",
            "macro.mImgLabel" to "[hình ảnh]",
            "menu.noApps" to "(không tìm thấy ứng dụng)",
            "menu.cat.AudioVideo" to "Âm thanh và video",
            "menu.cat.Development" to "Phát triển",
            "menu.cat.Graphics" to "Đồ họa",
            "menu.cat.Game" to "Trò chơi",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Văn phòng",
            "menu.cat.System" to "Hệ thống",
            "menu.cat.Utility" to "Tiện ích",
            "menu.cat.Other" to "Khác",
            "menu.usage" to "cách dùng: z2menu [list]   (không đối số: XML menu pipe của openbox)",
            "reminderView.labels" to "Lời nhắc|Thêm|Xóa|Nội dung|Ngày giờ|Lặp lại|Một lần|Mỗi ngày|Ngày thường|Mỗi tuần (ngày đã chọn)|Văn bản lịch (tùy chọn; thay cho ngày và lặp lại)|Đã lên lịch|Lặp lại|Đã thông báo|Không có lời nhắc|Xóa lời nhắc này và các thông báo đang chờ của nó?",
            "rssFeeds.rssFeedsLabel" to "Đăng ký",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} không khởi động. Xem /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: không tìm thấy trình quản lý gói được hỗ trợ (apk/apt-get/pacman).",
            "scan.mInstalling" to "z2scan: đang cài",
            "scan.mInstallFail" to "z2scan: cài thất bại",
            "scan.mRemoteBlocked" to "z2scan: đích từ xa bị từ chối. Chỉ kiểm tra localhost, hoặc truyền --allow-remote cho đích mà bạn được phép kiểm tra.",
            "scan.mRemoteWarn" to "z2scan: CẢNH BÁO đang quét máy chủ từ xa. Chỉ quét hệ thống bạn được phép kiểm tra rõ ràng.",
            "scan.mNoLynis" to "z2scan: không tìm thấy lynis; chuyển sang tự chẩn đoán tích hợp (\"z2scan self\"). Cài bằng \"z2scan setup\".",
            "scan.mNoCve" to "z2scan: không tìm thấy trình quét CVE (trivy/grype). Hãy cài trivy hoặc grype rồi thử lại.",
            "scan.mSelfHead" to "== z2scan tự chẩn đoán (thiết bị này / localhost) ==",
            "scan.mUidRoot" to "đang chạy với uid 0 (lưu ý: proot/z2root cho root giả)",
            "scan.mPubPort" to "cổng TCP đang lắng nghe trên mọi giao diện (không chỉ localhost), cổng",
            "scan.mSshdEmpty" to "sshd_config cho phép đăng nhập mật khẩu trống (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config bật xác thực mật khẩu (PasswordAuthentication yes); dùng khóa an toàn hơn",
            "scan.mSshdRoot" to "sshd_config cho phép đăng nhập root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys có quyền quá rộng, chế độ",
            "scan.mSshDir" to "~/.ssh có quyền quá rộng, chế độ",
            "scan.mWorldWrite" to "tìm thấy tệp ai cũng ghi được (hiện tối đa 20):",
            "scan.mSuid" to "có tệp nhị phân SUID (để tham khảo; root giả với proot/z2root):",
            "scan.mPath" to "PATH có phần tử trống/\".\" (thư mục hiện tại trong PATH là rủi ro)",
            "scan.mFound" to "phát hiện:",
            "scan.mClean" to "không thấy vấn đề rõ ràng.",
            "scan.mSaved" to "z2scan: đã lưu mốc:",
            "scan.mSaveFail" to "z2scan: không ghi được mốc:",
            "scan.mNoBase" to "z2scan: chưa có mốc. Chạy \"z2scan self --save\" một lần để ghi trạng thái hiện tại.",
            "scan.mNoChange" to "[ OK ] không thay đổi so với mốc.",
            "scan.mAdded" to "== mới so với mốc ==",
            "scan.mRemoved" to "== đã mất so với mốc ==",
            "scan.mCleared" to "z2scan: đã xóa mốc.",
            "scan.mLangDiff" to "z2scan: CẢNH BÁO mốc được lưu bằng ngôn ngữ khác, nên mọi thứ sẽ trông như đã thay đổi. Hãy lưu lại bằng \"z2scan self --save\".",
            "scan.langTag" to "vi",
            "scan.usageText" to """
        z2scan - kiểm tra lỗ hổng thiết bị này / localhost (phòng thủ, không dữ liệu nào ra ngoài).

        Mặc định chỉ quét localhost. Trình quét được cài từ gói chính thức của
        bản phân phối (nmap/lynis/trivy); không có gì đi kèm và kết quả ở lại cục bộ.

          z2scan self [--save]         tự chẩn đoán tích hợp (không cần công cụ ngoài): cổng mở,
                                       cấu hình sshd, quyền khóa SSH, ai cũng ghi được/SUID, PATH.
                                       --save cũng lưu kết quả làm mốc.
          z2scan diff [--quiet]        chạy lại tự chẩn đoán và chỉ hiện những gì đã đổi
                                       so với mốc. Thoát với 1 khi có gì mới,
                                       nên nối thẳng vào z2-when được. --quiet hoàn toàn
                                       không in gì khi không có thay đổi.
          z2scan baseline [clear]      hiện mốc đã lưu (hoặc xóa nó)
          z2scan setup                 cài trình quét (nmap, lynis) qua apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       quét TCP bằng nmap. Đích mặc định là 127.0.0.1.
                                       Đích không cục bộ cần --allow-remote.
          z2scan host                  kiểm toán máy chủ bằng lynis (nếu không có thì dùng "self")
          z2scan cve                   quét CVE đã biết của rootfs bằng trivy/grype nếu có

        Chỉ quét hệ thống bạn được phép kiểm tra rõ ràng.
    """.trimIndent(),
            "translationMacro.description" to "Dịch văn bản bằng một lệnh cài riêng.",
            "translationMacro.missing" to "Lệnh trans chưa được cài trong môi trường cục bộ này. Hãy tự cài công cụ dịch rồi thử lại. Không có gì được tải tự động.",
            "translationMacro.network" to "Văn bản cần dịch được gửi đến dịch vụ bên ngoài mà công cụ của bạn dùng. Điều khoản của dịch vụ và tình trạng mạng sẽ áp dụng.",
            "translationSetup.setup" to "Mở một terminal trong cùng môi trường Linux cục bộ, tự chạy lệnh bên dưới, rồi thử dịch lại.",
            "translationSetup.alpineRepo" to "Nếu không tìm thấy gói, hãy bật kho community của cùng phiên bản Alpine trong /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Nếu không tìm thấy gói trên Ubuntu, hãy bật kho universe rồi thử lại.",
            "translationSetup.unknown" to "Không nhận ra bản phân phối. Hãy chọn lệnh cho môi trường Linux cục bộ của bạn:",
            "voice.head" to """# voice.sh — bộ nhận của "Lệnh giọng nói": những gì bạn nói điều khiển nhạc, âm lượng, hẹn giờ, lời nhắc, thời tiết và hơn nữa.
#
# Chuẩn bị (một lần):
#   sh ~/.z2term/macros/voice.sh setup    đăng ký 4 quy tắc (Lệnh giọng nói / Lệnh giọng nói (đang khóa) / Hẹn giờ giọng nói / Đếm bước)
#   Đếm bước chỉ giữ bộ đếm bước chạy cho câu "hôm nay đi bao nhiêu bước"; nó không bao giờ chạy gì
#   sau đó nói từ ô "Lệnh giọng nói" trong cài đặt nhanh hoặc trợ lý kỹ thuật số (nhấn giữ nút nguồn v.v.)
#
# Từ terminal:
#   voice.sh words                 mở bảng từ (~/.z2term/voice/words-<lang>.txt; thêm hoặc đổi câu)
#   voice.sh try <câu>             thử một câu mà không cần nói (ví dụ voice.sh try bài tiếp theo)
#   voice.sh listen                mở màn hình nghe và nói (như ô Cài đặt nhanh)
#   voice.sh lang [ngôn ngữ]       ngôn ngữ của bảng từ (voice.sh lang - liệt kê)
#   voice.sh ai [off|codex|claude|agy]   dùng AI hay không (ban đầu tắt)
#   voice.sh model [tên]           mô hình AI (trống = mặc định của AI đó)
#   voice.sh setup / help
#
# AI ban đầu tắt. Kể cả khi bật, nó chỉ được hỏi khi bạn bắt đầu bằng từ khóa ("AI", "câu hỏi" ...).
# Thời tiết chỉ hỏi wttr.in khi bạn nói "thời tiết" (nơi chốn: weather_city= trong ~/.z2term/voice/config).
# Từ của riêng bạn: "từ = lệnh" trong ~/.z2term/voice/commands.txt (được kiểm tra trước bảng từ)
# Tên để gọi: "tên số" trong ~/.z2term/voice/contacts.txt / nhật ký: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — macro khởi đầu. Phản hồi những gì xảy ra trên thiết bị.",
                "# Việc chờ do ứng dụng làm (z2-when), nên nó không thường trú: chạy một lần rồi dừng.",
                "# Chuẩn bị: Cài đặt -> bật \"Phát hiện sự kiện hệ thống\"",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (tai nghe: quy tắc thứ hai với 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Điều đã xảy ra đến trong Z2_WHEN_EVENT (z2-when events liệt kê các tên).",
                "# Để thử bằng tay:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT đang trống. Để thử bằng tay: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Đã bắt đầu sạc\" ;;",
                "  power_disconnected) z2-toast \"Đã ngừng sạc\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — cảnh báo khi pin yếu, tùy theo tình trạng hiện tại.",
                "# Dùng z2-state để biết màn hình có bật không: bật thì thông báo ngắn, không thì thông báo.",
                "# Chuẩn bị: Cài đặt -> bật \"Phát hiện sự kiện hệ thống\"",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when truyền mức pin; nếu không thì z2-state, nên chạy bằng tay cũng được.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Không nói gì khi đang sạc (pin thật ra không giảm)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Pin ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Pin yếu\" \"Còn ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — đọc to pin và kết nối mỗi sáng.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Báo thức hệ thống đánh thức nó, nên kích hoạt cả trong Doze (có thể trễ vài phút).",
                "# Không phụ thuộc công tắc phát hiện.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=\"di động\"; fi",
                "z2-say \"Chào buổi sáng. Pin còn ${d}{level} phần trăm, mạng ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — sao chép mã một lần từ thông báo, rồi xóa sau",
                "# TTL giây nếu khay nhớ tạm vẫn chứa đúng giá trị đó.",
                "# Chuẩn bị: Cài đặt -> bật \"Phát hiện thông báo\" + cấp quyền truy cập thông báo của hệ thống",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ có thể ẩn mã trong thông báo nhạy cảm. Để chắc chắn, hãy dùng",
                "#    bản SMS (otp-sms.sh) — nội dung SMS không bao giờ bị ẩn.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — sao chép mã một lần từ SMS đến, rồi xóa sau",
                "# TTL giây nếu khay nhớ tạm vẫn chứa đúng giá trị đó.",
                "# Bản SMS của otp-clip.sh. Khác với thông báo, nội dung SMS không bao giờ bị",
                "# bảo vệ thông báo nhạy cảm (Android 15+) che đi và hoạt động cả khi điện thoại đang khóa.",
                "# Chuẩn bị: Cài đặt -> bật \"Phát hiện SMS\" + cấp quyền SMS của hệ thống",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — hiện một thông báo để sao chép số từ thông báo cuộc gọi.",
                "# Nút \"Sao chép\" của nó đưa số vào khay nhớ tạm (vì sao chờ một lần chạm: xem bên dưới).",
                "# Chuẩn bị: Cài đặt -> bật \"Phát hiện thông báo\" + cấp quyền truy cập thông báo của hệ thống",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Để bắt cả cuộc gọi nhỡ, đăng ký quy tắc thứ hai (cùng script, danh mục khác):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — kiểm tra nguồn tin và chỉ giữ cái mới, dưới dạng thông báo và văn bản.",
                "# Khác các ví dụ khác, cái này **không** thường trú: nó thuộc dạng",
                "# \"chạy một lần từ trình kích hoạt theo giờ rồi dừng\".",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — mở những gì rss.sh đã thu thập, mới nhất trước, mỗi lần chạm một bài.",
                "# Hiện như một nút macro trên widget trạng thái, nên mỗi lần chạm mở bài tiếp theo.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — nhắc bạn bằng thông báo: một lần (sau 30m, lúc 18:30) hoặc",
                "# lặp lại (mỗi ngày / ngày thường / mỗi tuần). Kích hoạt cả khi ứng dụng đóng (một báo thức hệ thống",
                "# đánh thức nó; không cần công tắc phát hiện). Hoãn bằng các nút của thông báo.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — biến văn bản hoặc tệp thành mã QR: vẽ ở đây, hoặc lưu thành PNG.",
                "# Đưa một thứ sang thiết bị khác mà không phải gõ lại. Chạy một lần, không thường trú.",
            ),
            "macro.md" to listOf(
                "# md.sh — đọc tệp Markdown đã định dạng ở đây, hoặc mở trong ứng dụng.",
                "# Tiêu đề, danh sách, bảng, mã, liên kết; hình hiện tại chỗ. Chạy một lần, không thường trú.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Mỗi dòng một nguồn tin hoặc từ không được để chìm mất (dòng bắt đầu bằng # bị bỏ qua).",
                "# Một phần URL hoặc một phần tiêu đề là đủ. Mỗi mục khớp có thông báo riêng.",
                "# ví dụ", "#example.org",
            ),
            "macro.usage" to listOf(
                "cách dùng: z2-macro <lệnh con>",
                "  list                liệt kê ví dụ đi kèm (đánh dấu mới / giống / khác)",
                "  install <name|all>  sao chép vào ~/.z2term/macros/ (không bao giờ ghi đè; -f để ép)",
                "  remove <name>       gỡ bản sao của bạn cho ví dụ đi kèm (dừng nếu nó khác; -f để ép)",
                "  diff <name>         khác biệt giữa bản của bạn và bản đi kèm (của bạn bên trái)",
                "  show <name>         hiện script",
                "  run <name>          chạy ở đây (Ctrl-C để dừng)",
                "  dir                 hiện nơi chứa macro",
                "  actions ...         macro thao tác Android (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Đăng ký", "Thêm", "Bài viết", "Sửa", "Gỡ", "URL nguồn tin",
                "Gỡ đăng ký này? Các bài đã thu thập sẽ được giữ.",
                "Thay đổi áp dụng ở lần kiểm tra tiếp theo. Các bài đã thu thập được giữ.",
                "Chưa có đăng ký. Dùng Thêm để nhập URL RSS hoặc Atom.",
                "Nhập URL HTTP(S) không có dấu cách hay thông tin đăng nhập (tối đa 2048 ký tự).",
                "Đăng ký này đã có.",
                "Đăng ký này đã thay đổi. Làm mới danh sách và thử lại.",
                "Một thay đổi khác đang được lưu. Hãy thử lại.",
                "Trước", "Sau",
            ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols" to 7,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の Tiếng Việt 版。上書きしていない文言は英語で出る。 */
private class ApiMsgVi(d: String) : Z2ApiMsg("vi", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — chọn một tài liệu; sao chép vào HOME và in đường dẫn.\n# z2-file save FILE [MIME] — chọn nơi lưu và lưu một bản sao.\n# Hủy được tính là lỗi. Tối đa 512 MiB; lựa chọn hết hạn sau 5 phút."

    override val usbHelp: String get() = """
        |# z2-usb list              … liệt kê thiết bị USB cắm vào điện thoại
        |# z2-usb allow [số]        … xin Android cho chương trình Linux dùng một thiết bị
        |# Chạy allow một lần sau khi cắm thiết bị. Quyền có hiệu lực đến khi rút ra.
    """.trimMargin()

    override val usbUsage: String get() = "cách dùng: z2-usb list | z2-usb allow [số|đường dẫn|VID:PID]"

    override val usbNoDevices: String get() = "Không có thiết bị USB nào cắm vào điện thoại."

    override val usbNeedSelector: String get() = "Có nhiều hơn một thiết bị USB; hãy cho số của nó từ z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Hãy chấp thuận truy cập USB trên màn hình Android: $path"

    override fun usbAlreadyAllowed(path: String): String = "Đã cho phép truy cập USB: $path"

    override fun usbNotFound(selector: String): String = "Không tìm thấy thiết bị USB: $selector"

    override val usbStateAllowed: String get() = "đã cho phép"

    override val usbStateNeedsAllow: String get() = "cần cho phép"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "tiêu đề" "nội dung"  /  z2-notify [-h] "nội dung"
        |#   -h / --high / --banner : hiện dạng biểu ngữ (nổi) ở đầu màn hình
        |#   -b <nhãn>              : thêm nút trả lời (tối đa 3). Nhấn một nút sẽ thêm
        |#                            notify_action vào ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <tên>               : định danh của thông báo này (để phân biệt câu trả lời)
        |#   -c <văn bản>           : thêm nút "Sao chép" đưa <văn bản> vào khay nhớ tạm.
        |#                            Dùng thay cho z2-clip set khi macro chạy
        |#                            nền: Android 10+ chỉ cho ứng dụng ở phía trước ghi khay nhớ tạm,
        |#                            nên z2-clip set khi không ai xem sẽ bị bỏ qua.
    """.trimMargin()

    override val notifyUsage: String get() = "cách dùng: z2-notify [-h] [-n tên] [-c văn bản] [-b nhãn]... <tiêu đề> [nội dung]"

    override val toastHelp: String get() = """
        |# z2-toast <thông điệp> … một thông điệp ngắn ở cuối màn hình (toast).
        |# Tự biến mất và không để lại gì. Dùng z2-notify nếu cần nó ở lại,
        |# hoặc muốn có nút để nhấn.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … mở công cụ QR với máy ảnh đang quét sẵn.
        |# Sau khi quét, một nút mở nội dung trong ứng dụng của nó (trình duyệt, LINE và ứng dụng khác,
        |# bàn phím quay số, email, SMS, Wi-Fi, danh bạ, lịch). Không có gì tự mở.
        |# Hãy đặt nó lên ô Cài đặt nhanh hoặc bảng cạnh.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … tạo mã QR.
        |#   Không cần cài gì: ứng dụng mã hóa (cùng bộ mã hóa với màn hình QR).
        |#   Không có TEXT thì đọc đầu vào chuẩn. -o là nơi PNG đến
        |#   (mặc định ~/.z2term/qr/qr.png) và đường dẫn được in ra. -t thay vào đó vẽ ở đây
        |#   bằng ký tự khối. -p là kích thước pixel mục tiêu (mặc định 600),
        |#   -m là vùng yên tĩnh tính bằng mô-đun (mặc định 4). Tối đa 2000 byte.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <văn bản> … đưa văn bản vào menu chia sẻ của Android (gửi sang ứng dụng khác).
        |# --file FILE [FILE ...] : gửi nội dung tệp (tối đa 32 tệp, tổng 512 MiB).
        |# Đường dẫn được đọc trong shell cục bộ hiện tại. -- ép chế độ văn bản. Bản sao cũ hơn 24 giờ được dọn ở lần chia sẻ sau.
        |# Mọi đối số thành một văn bản. Ứng dụng đích được chọn trên màn hình,
        |# nên cần có người ở đó — không dành cho macro tự chạy một mình.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [tiêu đề] … đọc trong z2term một trang tạo ở đây.
        |# Không máy chủ, không trình duyệt: tệp được đưa cho ứng dụng và hiện nguyên trạng.
        |# JavaScript và tải mạng bị tắt, nên không gì trên trang chạy được mã hay liên lạc ra ngoài.
        |# Liên kết http:// và https:// mở trong trình duyệt thường dùng của bạn.
        |# Chủ đề của điện thoại đến dưới dạng biến CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), nên trang viết bằng chúng theo màu của terminal.
        |# Tối đa 4 MB. Hình phải được nhúng (URI data:); hình từ xa không được tải.
        |# Dùng --edge BẢNG:MỤC để gửi đến một mục type=view có sẵn của bảng cạnh.
        |# Dùng --controls FILE.json cho biểu mẫu và thao tác chung do một macro đã cài quản lý.
        |# Mục trang xem mặc định làm mới thủ công. view-refresh=auto chạy run= khi mở; z2-view quay về mục đó.
        |# Dùng - hoặc ống dẫn (ls -l | z2-view) để đọc đầu vào chuẩn. Văn bản không bắt đầu bằng < được hiện
        |# xuống dòng tự động bằng phông đơn cách, bỏ mã màu; --text / --html chọn rõ ràng.
        |# Chụm để đổi cỡ chữ; --pinch page thay vào đó thu phóng cả trang. --text-size 50..300 cố định
        |# cỡ chữ (phần trăm) của trang này. Cả hai áp dụng khi toàn màn hình; mục của bảng có cài đặt riêng.
        |# --no-refresh / --no-find ẩn nút Làm mới / Tìm khi toàn màn hình (mặc định hiện).
    """.trimMargin()

    override val viewUsage: String get() = "cách dùng: z2-view [--edge BẢNG:MỤC] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <file|-> [tiêu đề]"

    override val viewUnreadable: String get() = "z2-view: không đọc được tệp:"

    override val openHelp: String get() = """
        |# z2-open <url|đường dẫn> … mở bằng ứng dụng mặc định (https://… hoặc /sdcard/…).
        |# Android chọn ứng dụng mở nó; z2term chỉ chuyển giao.
        |# Đường dẫn là đường dẫn trên **điện thoại** (/sdcard/…), không phải bên trong bản phân phối.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <file|->… … vẽ hình ảnh trong terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Truyền - để đọc hình từ stdin.
        |#   -w <cols>  chiều rộng theo cột terminal (mặc định: vừa terminal)
        |#   -r <rows>  chiều cao theo dòng terminal (với -w, kích thước dùng nguyên)
        |#   --clear    gỡ mọi hình đã vẽ đến giờ
        |#   -f         gửi kể cả khi stdout không phải terminal
        |# ⚠ Hình chỉ hiện trong thẻ z2term, hoặc terminal hiểu giao thức
        |#   đồ họa kitty. Ở nơi khác (qua ssh, trong trình phân trang) bạn sẽ thấy ký tự vô nghĩa.
        |# ⚠ Tỉ lệ khung giả định một ô cao gấp đôi chiều rộng. Nếu hình
        |#   trông bị bẹp, chỉnh: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (nhỏ hơn = cao hơn).
    """.trimMargin()

    override val imgUsage: String get() = "cách dùng: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->..."

    override val imgNoFile: String get() = "không đọc được tệp:"

    override val imgNotTty: String get() = "stdout không phải terminal, nên không gửi gì (-f vẫn gửi)."

    override val imgNoSize: String get() = "không đọc được kích thước pixel; vẽ dạng hình vuông:"

    override val qrUsage: String get() = "cách dùng: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: không tạo được mã (văn bản quá dài? tối đa 2000 byte)"

    override val clipHelp: String get() = """
        |# z2-clip get        … in khay nhớ tạm ra stdout
        |# z2-clip set [text] … đưa văn bản (hoặc stdin nếu bỏ trống) vào khay nhớ tạm
        |# ⚠ Android 10+ chỉ cho ứng dụng ở phía trước (hoặc phương thức nhập đang dùng)
        |#   đụng đến khay nhớ tạm. Từ macro nền nó bị bỏ qua không một lời,
        |#   nên hãy dùng z2-notify -c <văn bản>: nó thêm nút "Sao chép" luôn hoạt động.
    """.trimMargin()

    override val batteryHelp: String get() = "# In mức pin / trạng thái sạc dạng JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (mặc định 200ms)"

    override val editHelp: String get() = "# z2-edit <file>      … sửa tệp văn bản trong trình soạn của z2term (ô văn bản thuần; Lưu ghi lại tệp)"

    override val sayHelp: String get() = "# z2-say <văn bản>    … đọc bằng TTS của thiết bị (không đối số thì đọc stdin)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (mặc định toggle). status đọc trạng thái của Android, kể cả thay đổi bên ngoài (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … chỉ chụp phần đã khoanh (mặc định free). Cần Android 11 và z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (mặc định playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Âm lượng phương tiện. In giá trị hiện tại/tối đa nhận được."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (mặc định light). Đọc một giá trị và trả về JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Mở bất kỳ Intent Android nào (mặc định startActivity). Đối số đầu tiên không phải tùy chọn là ACTION.
        |# Cửa sổ tự do mặc định dùng lại tác vụ và ranh giới của hệ thống. --reuse-task / --os-bounds vẫn được chấp nhận.
        |# --bounds-only yêu cầu ranh giới 80% không kèm khóa chế độ; không kết hợp được với --os-bounds.
        |# --no-scale tắt yêu cầu cửa sổ tự do thu nhỏ của Motorola cho lần mở này.
        |# Chỉ -p PKG sẽ mở ứng dụng đó. --window full|freeform|split|ask chọn yêu cầu mở (cần hệ thống hỗ trợ).
        |# --profile N mở ứng dụng (-p/-n) trong hồ sơ khác, như hồ sơ công việc. Không chia đôi màn hình. N nằm trong lệnh mà bảng cạnh lưu cho ứng dụng đó.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … toàn bộ trạng thái hiện tại của thiết bị, dạng JSON
        |# z2-state <khóa>     … chỉ giá trị đó, nguyên dạng (dùng thẳng trong phép thử)
        |# khóa: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# ví dụ [ "${d}(z2-state charging)" = "true" ] && echo đang sạc
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <câu hỏi>
        |#   Hỏi người dùng một câu và in câu trả lời ra stdout.
        |#   Câu hỏi đến dưới dạng thông báo có **ô trả lời**, nên có thể
        |#   trả lời từ ngăn thông báo mà không cần mở ứng dụng (macro chạy
        |#   nền cũng hỏi được).
        |#   -t <giây>  chờ trả lời bao lâu (mặc định 300 = 5 phút)
        |#   -H <hint>  nhãn hiện trong ô trả lời
        |#   -d <văn bản> câu trả lời gợi ý (hiện trong thông báo)
        |# Vuốt bỏ thông báo mà không trả lời, hoặc hết giờ, sẽ thoát với mã khác 0
        |# và không in gì — nên bạn có thể viết "hoặc bỏ qua":
        |#   name=${d}(z2-ask "Tên nhánh?") || exit 1
        |# So sánh với z2-notify -b <nhãn>, chỉ đưa ra các lựa chọn bạn đã chuẩn bị.
    """.trimMargin()

    override val askUsage: String get() = "cách dùng: z2-ask [-t giây] [-H gợi ý] [-d mặc định] <câu hỏi>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   In ra stdout những gì bạn nói. Nhận dạng **chỉ chạy trên thiết bị**; giọng nói
        |#   không bao giờ lên mạng. Cần Android 12+ và nhận dạng giọng nói trên thiết bị
        |#   (với ngôn ngữ của bạn là dữ liệu giọng nói ngoại tuyến).
        |#   Hãy nói khi z2term ở trên màn hình (Android chỉ cho micrô ứng dụng trên màn hình).
        |#   Dừng khi bạn ngừng nói. Lần đầu sẽ hỏi quyền micrô.
        |#   -w <từ>    chỉ chấp nhận các từ này (cách nhau bằng dấu phẩy) và in từ đã nghe.
        |#               Mọi phỏng đoán của bộ nhận dạng đều được kiểm tra, không chỉ cái tốt nhất.
        |#   -l <ngôn ngữ> ngôn ngữ cần nhận dạng (ví dụ vi-VN, en-US; mặc định: ngôn ngữ thiết bị)
        |# Thoát: 0 = xong / 1 = không nghe thấy gì, hoặc không có từ nào / 2 = không dùng được (lý do trên stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "cách dùng: z2-listen [-w từ,từ,...] [-l ngôn ngữ]"

    override val listenPrompt: String get() = "Hãy nói… (dừng khi bạn ngừng)"

    override val listenNeedsAndroid12: String get() = "z2-listen cần Android 12 trở lên (trước đó nhận dạng không thể ở lại trên thiết bị)."

    override val listenBusy: String get() = "Đang nghe rồi (một z2-listen khác đang chạy)."

    override val listenEngineBusy: String get() = "Nhận dạng giọng nói của thiết bị đang bận (có thể ứng dụng khác đang dùng). Hãy thử lại sau."

    override val listenEngineStuck: String get() = "Nhận dạng giọng nói không phản hồi. Nếu vẫn tiếp diễn, hãy buộc dừng ứng dụng nhận dạng của thiết bị (như Android System Intelligence) trong Cài đặt Android › Ứng dụng."

    override val listenStopped: String get() = "Đã dừng nghe."

    override val listenNoOnDevice: String get() = "Thiết bị này không có nhận dạng giọng nói tích hợp. z2-listen không bao giờ gửi giọng nói lên mạng, nên không chạy được ở đây."

    override val listenNoPermission: String get() = "Chưa cho phép truy cập micrô. Hãy chạy lại z2-listen khi z2term ở trên màn hình để được hỏi, hoặc cho phép trong cài đặt Android."

    override val listenNotShown: String get() = "Hãy đưa z2term lên màn hình và thử lại (Android chỉ cho micrô ứng dụng trên màn hình)."

    override val listenNoMic: String get() = "Không dùng được micrô (z2term đã rời màn hình, hoặc ứng dụng khác đang ghi âm)."

    override val listenNoLanguage: String get() = "Chưa cài dữ liệu giọng nói trên thiết bị cho ngôn ngữ này. Hãy tải trong cài đặt nhập bằng giọng nói của thiết bị (nhận dạng giọng nói ngoại tuyến)."

    override val listenNoSpeech: String get() = "Không nghe thấy gì."

    override val listenTimedOut: String get() = "Hết giờ."

    override fun listenNoMatch(heard: String): String = "Không có từ nào khớp (đã nghe: $heard)"

    override fun listenFailed(code: Int): String = "Nhận dạng giọng nói thất bại (lỗi $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … ngăn màn hình tự tắt trong khoảng thời gian này
        |# z2-screen keepon off          … khôi phục ngay, không chờ hết hạn
        |# z2-screen status              … trạng thái hiện tại dạng JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Đổi cài đặt "thời gian chờ màn hình" của toàn hệ thống, nên vẫn áp dụng khi ứng dụng
        |# ở chế độ nền. Đây KHÔNG phải giữ màn hình sáng của thanh công cụ, vốn chỉ kéo dài khi
        |# ứng dụng ở trên màn hình — cái đó không bị đụng đến.
        |# Giá trị gốc được giữ và luôn được ghi lại khi hết hạn (kể cả khi ứng dụng
        |# bị dừng hoặc thiết bị khởi động lại). Tối đa 24h mỗi lần.
        |# Cần "sửa đổi cài đặt hệ thống" (Cài đặt > thời gian chờ màn hình > cho phép).
        |# ví dụ z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "cách dùng: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | lệnh...> [--off <lệnh...>] [-l <nhãn>] [-i <hình>]
        |#                           … đặt một thứ lên ô Cài đặt nhanh 1-$tiles
        |# z2-tile add <1-$tiles>         … xin đặt vị trí này vào ngăn (Android 13+)
        |# z2-tile list              … $tiles vị trí dạng TSV (vị trí / nhãn / lệnh; "-" = trống)
        |# z2-tile clear <1-$tiles|all>   … làm trống một vị trí
        |#
        |# Gán gì
        |#   Hoặc **tên tệp của một macro** trong ~/.z2term/macros/, hoặc **một lệnh** chạy đúng như
        |#   đã viết — là cái nào được suy ra từ tên.
        |#   Macro có thể nhận đối số ("remind.sh ask"): từ đầu tiên quyết định đó có phải
        |#   macro không, phần còn lại được truyền cho nó. Hai vị trí cùng macro sẽ cùng
        |#   nhãn, nên hãy cho chúng -l.
        |#   Lệnh chạy với Z2_TILE=<vị trí> (và Z2_TILE_MACRO cho macro) trong môi trường.
        |#   ⚠ Tên kết thúc bằng .sh mà không có trong ~/.z2term/macros/ bị từ chối ở đây — nếu là
        |#     lệnh, nó sẽ được tìm trong PATH (không gồm thư mục macro),
        |#     nên ô sẽ không làm gì và chỉ báo trong tile/run.log.
        |#
        |# Chạm làm gì
        |#   Một lần chạm bắt đầu, lần nữa dừng (như các nút widget).
        |#   Ô trông "đang hoạt động" khi đang chạy (màu là màu nhấn của hệ thống, không phải của chúng tôi).
        |#   Với --off bạn có **hai lệnh**: chạm sẽ chuyển qua lại giữa chúng và ô
        |#   trông "đang hoạt động" khi đang bật. Dùng khi tắt là một lệnh riêng
        |#   (z2-torch on / off).
        |#   Cặp thông thường nhớ các lần chuyển thành công. Gán trực tiếp z2-torch theo
        |#   những gì Android quan sát, kể cả thay đổi bên ngoài. z2-screen keepon theo cài đặt thực sự đang có hiệu lực.
        |#
        |# Đặt vào ngăn
        |#   Trên Android 13+, `set` hỏi ngay có đưa ô vào ngăn không, và
        |#   hộp thoại đó mang **tên và biểu tượng bạn vừa gán**. Nếu bạn từ chối, không có gì được đặt.
        |#   Có đúng $tiles vị trí: con số cố định trong manifest và không tăng được
        |#   khi đang chạy. Vị trí chưa gán hoàn toàn không hiện trên màn hình sửa,
        |#   nên có $tiles vị trí cũng không tốn gì.
        |#   ⚠ Chỉ hiện khi z2term ở phía trước, và mỗi lần một — macro
        |#     gán hai vị trí liên tiếp chỉ hỏi cho cái đầu. Sau đó dùng
        |#     z2-tile add <vị trí> cho phần còn lại.
        |#   ⚠ Đặt ô vẫn là quyết định của bạn — Android không cho ứng dụng tự đặt
        |#     ô của mình ở đó. Từ màn hình sửa (bút chì) của ngăn Cài đặt nhanh, hãy tìm
        |#     **z2term <vị trí>**.
        |#   ⚠ Danh sách đó hiện tên và biểu tượng trong manifest, không phải cái đã gán (Android không có
        |#     cách đổi chúng khi đang chạy), nên z2-tile list cho bạn biết số nào là cái nào.
        |#
        |# Hình vẽ (-i)
        |#   Một vị trí tự nhận biểu tượng phù hợp khi tên tiết lộ (remind.sh -> một
        |#   chiếc đồng hồ), và z2-icon thay nó bằng thứ bạn muốn — sau đó không bị đụng nữa.
        |#   -i đặt hình **trước** khi hộp thoại đó hiện, nên một dòng quyết định
        |#   cả tên và biểu tượng (z2-icon sample liệt kê các tên có sẵn).
        |#   ⚠ Tên không rõ bị từ chối **trước khi gán bất cứ gì** — vị trí đã đặt nhưng
        |#     sai hình khó nhận ra hơn vị trí chưa đặt.
        |#
        |# ví dụ z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "màn hình sáng"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l "đèn pin"
        |#      z2-tile set 4 backup.sh -l "sao lưu" -i sync
    """.trimMargin()

    override val tileUsage: String get() = "cách dùng: z2-tile set <1-$tiles> <macro.sh|lệnh...> [--off <lệnh...>] [-l nhãn] " +
                "[-i hình] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: không có hình đó: $name (liệt kê bằng z2-icon sample). Chưa gán gì."

    override fun tileAddEmpty(n: Int): String = "z2-tile: vị trí $n chưa có gì (hãy gán trước: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: không thể xin thêm ô ở đây (cần Android 13, với z2term ở phía trước). " +
                "Vị trí đã được gán — hãy đặt nó từ màn hình sửa (bút chì) của ngăn Cài đặt nhanh."

    override val tileAddAsked: String get() = "Đã xin Android thêm ô — hãy trả lời hộp thoại. " +
                "(Không thấy gì? Hãy đặt từ màn hình sửa.)"

    override val iconHelp: String get() = """
        |# Thay biểu tượng thanh trạng thái và ô Cài đặt nhanh bằng hình vẽ điểm ảnh của riêng bạn.
        |#
        |# Bắt đầu ở đây
        |#   z2-icon pick 1     ... chọn một hình từ danh sách và đặt lên ô 1
        |#   z2-icon list       ... hiện mỗi vị trí đang có gì ("-p" hiện cả hình)
        |#
        |# <đích> nghĩa là gì
        |#   notify    biểu tượng của mọi thông báo từ ứng dụng này (trên thanh trạng thái)
        |#   1-$tiles      một vị trí ô Cài đặt nhanh - cùng số với z2-tile
        |#
        |# Đặt hình
        |#   pick <đích>              liệt kê và chọn theo số (dễ nhất)
        |#   sample <đích> <tên>      khi bạn đã biết tên
        |#   edit <đích>              vẽ, hoặc sửa hình hiện tại, trong ${d}EDITOR
        |#   set <đích> <tệp>         đọc từ tệp ("-" cho stdin)
        |#   auto <1-$tiles|all>          chọn lại theo những gì ô đang chạy
        |#   clear <đích|all>         quay về biểu tượng tích hợp
        |#
        |# Độ mịn của lưới
        |#   grid                     kích thước cho hình mới
        |#   grid <$grids>          đặt nó ($grid nếu bạn không đổi)
        |#   scale <đích> <$grids>    đưa hình của <đích> sang lưới đó
        |#                            (đồng thời giảm một nửa bậc chéo). **Cho chỗ
        |#                            để vẽ mịn hơn** - biểu tượng tạo ra vốn đã được
        |#                            làm mượt, không cần làm vì điều đó.
        |#
        |# Giữ hình của bạn trong danh sách
        |#   save <đích> <tên>        đặt tên cho thứ trên <đích> và thêm vào danh sách
        |#   sample                   danh sách để chọn (số / tên /
        |#                            "builtin" = đi kèm, "mine" = bạn đã lưu)
        |#   sample <tên>             hiện hình đó
        |#   forget <tên>             gỡ một hình của bạn khỏi danh sách (thứ bạn đã
        |#                            đặt lên đích vẫn còn)
        |#
        |# Cách vẽ
        |#   Đó là lưới ký tự. "." "" "0" "-" "_" để trống một điểm và mọi ký tự khác
        |#   tô kín nó, nên hãy dùng ký tự bạn dễ nhìn nhất. Khoảng trống quanh
        |#   hình bị bỏ qua - nó được căn giữa, không cần điền mỗi dòng
        |#   chính xác. Lưới là $grids ("z2-icon grid").
        |#   ⚠ Lưới lớn hơn không làm biểu tượng lớn hơn. Hãy lấp đầy lưới, nếu không hình
        |#     sẽ nhỏ hơn cái bị thay.
        |#
        |# Nên biết
        |#   ⚠ Chỉ hình dạng được chuyển đi. Android tô các biểu tượng này bằng một màu của nó
        |#     (ô đổi màu giữa hoạt động và không hoạt động), nên không có màu để chọn.
        |#   ⚠ **Đường viền được làm mượt khi xuất.** Hình vẽ trên $grid điểm
        |#     vẫn ra mượt trên ô (được vẽ lớn hơn thanh trạng thái nhiều),
        |#     nên lưới không phải vấn đề khi dùng hằng ngày. 48 / 64 dành cho khi **bạn**
        |#     muốn vẽ mịn hơn. "show" hiện hình như bạn đã vẽ; ô thì mượt hơn.
        |#   ⚠ Một ô tự nhận hình khi tên tiết lộ ("z2-tile set 1
        |#     remind.sh" đặt một đồng hồ). Thứ bạn đặt ở đây được ưu tiên và không bị đụng nữa
        |#     — "z2-icon auto 1" trả vị trí đó về chọn tự động.
        |#   ⚠ Hai biểu tượng không đổi được: biểu tượng trên màn hình **sửa** của Cài đặt nhanh (nơi
        |#     bạn kéo ô từ đó) và biểu tượng trình khởi chạy. Android cố định chúng khi cài đặt.
        |#
        |# ví dụ z2-icon pick 1               chọn hình cho vị trí 1
        |#      z2-icon edit 1              tự vẽ lại
        |#      z2-icon save 1 my-face      đặt tên cho hình đó và thêm vào danh sách
        |#      z2-icon sample 3 my-face    đặt cùng hình lên vị trí 3
        |#      z2-icon grid 64             từ giờ vẽ hình mới trên lưới 64x64
        |#      z2-icon scale 1 48          chuyển vị trí 1 sang lưới 48x48 để vẽ mịn hơn
        |#      z2-icon list -p             kiểm tra cái gì ở đâu, kèm hình
        |#      z2-icon clear notify        khôi phục biểu tượng thông báo
    """.trimMargin()

    override val iconUsage: String get() = "cách dùng: z2-icon pick <notify|1-$tiles> | sample [tên|đích tên] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [tệp|-] | " +
                "save <notify|1-$tiles> <tên> | forget <tên> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "không tìm thấy tệp:"

    override val iconEditUnchanged: String get() = "không thay đổi."

    override val iconPickPrompt: String get() = "số (hoặc tên), để trống để hủy: "

    override val iconPickCancelled: String get() = "đã hủy."

    override val iconNoEditor: String get() = "không tìm thấy trình soạn. Hãy đặt ${d}EDITOR, hoặc dùng: z2-icon set <đích> <tệp>"

    override val tileNoSuchMacro: String get() = "macro đó không có trong ~/.z2term/macros/ (dùng đường dẫn đầy đủ để chạy như lệnh):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [gói] / trace dump / trace stop
        |# Truy vết: bật thu trước; 5 phút, 128 sự kiện gần nhất, chỉ trong bộ nhớ.
        |# Kiểu trường và độ dài UTF-16, không bao giờ là giá trị văn bản. dump/stop trả về JSON; start đặt lại.
        |# z2-noti list  … các thông báo đang hiện, dạng TSV
        |#                 (khóa / gói / tên ứng dụng / tiêu đề / nội dung)
        |# Chỉ đọc. Cố ý không có cách nhấn hay vuốt bỏ thông báo:
        |# điều đó sẽ nhấn cả nút thanh toán và gửi của ứng dụng khác.
        |# Cần quyền truy cập thông báo (Cài đặt > Quyền và thông báo).
        |# Xem thêm: z2-when notify:otp / notify:pkg=<phần> / notify:contains=<phần>
    """.trimMargin()

    override val notiUsage: String get() = "cách dùng: z2-noti list | trace start [gói] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM[:SS] [tên]      … một lần vào HH:MM tiếp theo (ngày mai nếu đã qua)
        |# z2-alarm daily HH:MM[:SS] [tên]   … mỗi ngày lúc HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [tên] … một lần, sau N giây/phút/giờ
        |# z2-alarm list                … liệt kê những gì đã lên lịch (JSON; "exact" cho biết độ đúng giờ)
        |# z2-alarm cancel <id|tên|all> … hủy
        |# Khi kích hoạt, một dòng {"event":"alarm","name":…} được thêm vào ~/.z2term/events.jsonl.
        |# Đánh thức thiết bị cả trong Doze. "exact":true nghĩa là kích hoạt đúng phút;
        |# "exact":false nghĩa là Doze chỉ mở cửa sổ mỗi 9-15 phút, nên điện thoại để
        |# tắt màn hình có thể trễ chừng ấy. Tắt tối ưu hóa pin cho ứng dụng này
        |# sẽ đưa nó thành true (không hỏi thêm quyền nào).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: không có lệnh date dùng được"

    override val sessionHelp: String get() = """
        |# z2-session list                     … liệt kê thẻ (chỉ số / id / loại / dấu / tên, TSV)
        |#   dấu: * = trên màn hình / ! = đang chạy / ? = chưa khởi động / @ = được nối từ shell / - = khác
        |# z2-session new [tên]                … mở một thẻ terminal (in chỉ số và id)
        |# z2-session send <thẻ> <văn bản>...  … gõ văn bản vào thẻ đó (không chạy)
        |# z2-session send <thẻ> <văn bản> --enter … gõ rồi chạy
        |# z2-session key <thẻ> <phím>...      … gửi **phím** đến thẻ đó (C-c / M-x / F5 / Up …)
        |#   phím bổ trợ: C- (Ctrl) và M- (Meta=Alt); kết hợp được, như C-M-a
        |#   đặc biệt: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Phím có Shift như C-S-a bị từ chối (terminal không phân biệt Shift: cùng byte với C-a)
        |# z2-session key <thẻ> --raw '\x1b[A' … mọi thứ khác, dưới dạng byte (\xHH \e \n \r \t \0)
        |# z2-session capture [thẻ] [--all]    … lấy màn hình của thẻ đó (--all gồm cả lịch sử)
        |# z2-session attach <thẻ>             … nối vào thẻ đó và gõ trực tiếp
        |#   thoát bằng Ctrl+] (bất cứ lúc nào), hoặc ~. ở đầu dòng (như ssh)
        |#   ⚠ qua SSH chỉ Ctrl+] hoạt động: ssh phía trước bạn nuốt ~. và đóng phiên SSH
        |#   gõ ~~ cho ký tự ~ ở đầu dòng, ~ rồi Ctrl+] cho Ctrl+] thật
        |#   khi đang nối, thẻ theo kích thước cửa sổ CỦA BẠN; khi thoát sẽ trở lại như cũ
        |# z2-session close <thẻ>              … đóng thẻ đó (không bao giờ đóng thẻ cuối)
        |# z2-session group [list]             … liệt kê nhóm (số / id / tên / số thẻ, TSV)
        |# z2-session group new <tên> [<thẻ>]... … tạo nhóm từ các thẻ đó (trả về số và id)
        |# z2-session group add <nhóm> <thẻ>... … đưa thẻ vào nhóm đó
        |# z2-session group remove <thẻ>...    … đưa thẻ ra khỏi nhóm của nó
        |# z2-session group delete <nhóm>      … bỏ nhóm (các thẻ vẫn mở)
        |# z2-session group show <nhóm>        … hiển thị nhóm đó trên màn hình
        |# z2-session group send <nhóm> None... [--enter] … gõ vào mọi thẻ terminal của nhóm đó (không chạy)
        |# z2-session group broadcast start <nhóm> [--on|--off] | on | off | stop | status … phát 📡: bắt đầu (hiện nhóm đó), bật/tắt gửi, kết thúc, hoặc in stop/off/on
        |# z2-session group broadcast tab <thẻ>... on|off … đánh dấu / bỏ đánh dấu các thẻ đó để phát (sau start)
        |#   <nhóm> có thể là số từ group list, id hoặc tên nhóm. list hiện nhóm của mỗi thẻ ở cột thứ 6
        |#
        |# <thẻ> có thể là chỉ số trong list, id hoặc tên thẻ. "." hoặc bỏ trống = thẻ trên màn hình.
        |# ví dụ n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "cách dùng: z2-session list | new [tên] | send <thẻ> <văn bản>... [--enter] | " +
                "key <thẻ> <phím>... | key <thẻ> --raw <byte> | capture [thẻ] [--all] | " +
                "attach <thẻ> | close <thẻ> | group list|new|add|remove|delete|show|send"

    override val keyNothing: String get() = "z2-session key: chưa cho phím nào"

    override val keyRawEmpty: String get() = "z2-session key: --raw cần các byte để gửi"

    override val keyUnknown: String get() = "z2-session key: phím không rõ (xem danh sách trong \"z2-session -h\"):"

    override val keyBadEscape: String get() = "z2-session key: không đọc được chuỗi thoát:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: terminal không phân biệt Shift, nên '$asWritten' sẽ cho " +
            "đúng các byte giống '$equivalentTo'. Hãy viết '$equivalentTo' nếu đó là ý bạn."

    override fun attachNoSuchTab(target: String): String = "không có thẻ đó: '$target' (chạy \"z2-session list\" để xem các thẻ)"

    override val attachNotTerminal: String get() = "đó là thẻ GUI, không có shell để nối. Hãy chọn thẻ terminal."

    override val attachNotStarted: String get() = "thẻ đó chưa khởi động (đánh dấu \"?\" trong list). Hãy mở nó một lần trong ứng dụng, rồi nối."

    override val attachExited: String get() = "thẻ đó đã kết thúc. Màn hình của nó vẫn đọc được bằng \"z2-session capture\"."

    override val attachSelf: String get() = "đó là thẻ bạn đang gõ. Nối một thẻ vào chính nó sẽ đưa kết quả trở lại " +
            "làm đầu vào mãi mãi, không có đường quay lại. Hãy chọn thẻ khác."

    override val attachLoop: String get() = "thẻ đó đã được nối ngược về thẻ này, nên hai thẻ sẽ nuôi nhau " +
            "không dứt. Hãy tách một cái trước (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … máy chủ đã đăng ký (chỉ số / id / trạng thái / dấu / tên, TSV)
        |#   dấu: * = đã bật / - = đã tắt
        |# z2-server start <máy chủ>     … khởi động nó như máy chủ thường trú (giữ thiết bị truy cập được)
        |# z2-server stop <máy chủ>      … dừng cái này (các cái khác vẫn chạy)
        |# z2-server status [<máy chủ>]  … trạng thái, pid, số lần khởi động lại và mã thoát cuối
        |#
        |# <máy chủ> có thể là chỉ số trong list, id hoặc tên bạn đặt trong ứng dụng.
        |# Chỉ khởi động được máy chủ đã đăng ký trong ứng dụng; lệnh này không bao giờ đăng ký cái mới.
        |# Vì sao có nó: tiến trình nền khởi động thẳng từ quy tắc chạy NGOÀI khuôn khổ máy chủ
        |# thường trú (không WakeLock, không WifiLock, không dịch vụ tiền cảnh), nên ngừng phản hồi khi
        |# màn hình tắt. Khởi động từ đây đưa nó vào khuôn khổ đó.
        |# ví dụ z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "cách dùng: z2-server list | start <máy chủ> | stop <máy chủ> | status [<máy chủ>]"

    override val serverNotFound: String get() = "z2-server: không có máy chủ đó (xem \"z2-server list\"):"

    override val serverAmbiguous: String get() = "z2-server: tên khớp nhiều máy chủ; hãy dùng id từ \"z2-server list\":"

    override val serverNone: String get() = "z2-server: chưa đăng ký máy chủ nào (thêm trong ứng dụng: 📜 -> Máy chủ)."

    override val serverLowPowerWarn: String get() = "z2-server: lưu ý - chế độ tiết kiệm điện đang bật, nên không giữ WakeLock/WifiLock. " +
            "Máy chủ có thể ngừng phản hồi khi màn hình tắt " +
            "(Cài đặt -> Tự động hóa -> Bảo vệ tiến trình nền)."

    override val whenHelp: String get() = """
        |# z2-when <kích hoạt> run <cmd...>     … đăng ký một quy tắc
        |#   kích hoạt: charge:start | charge:stop  (cần BẬT phát hiện)
        |#            battery:below=N | battery:above=N  (cần BẬT phát hiện)
        |#            battery:temp>N | battery:temp<N  … nhiệt độ pin (°C) vượt qua N (cần BẬT phát hiện)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='phút giờ ngày tháng thứ'  (thứ 0-7 / 0,7=Chủ nhật. Trong ngoặc: có dấu cách)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<tên>  (cần BẬT phát hiện)
        |#            net:online | net:offline  … xuất hiện / mất kết nối dùng được
        |#            net:wifi | net:mobile | net:ethernet  … kết nối đang dùng chuyển sang loại này
        |#                                        (cần BẬT phát hiện; tính cả dữ liệu di động, không chỉ Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<phần> | share:ext=<đuôi>
        |#                                        … có thứ được chia sẻ cho z2term từ ứng dụng khác
        |#            boot                        … thiết bị đã khởi động xong (hoạt động khi TẮT phát hiện)
        |#            sms:any | sms:from=<phần> | sms:contains=<phần> | sms:otp  (cần RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (BẬT phát hiện)
        |#            file:new=<thư mục>[,ext=<đuôi>]  … có tệp mới vào thư mục này (cần BẬT phát hiện)
        |#            file:changed=<thư mục>[,ext=<đuôi>] | file:deleted=<thư mục>[,ext=<đuôi>]
        |#                                        … một tệp ở đó được ghi lại / biến mất (cần BẬT phát hiện)
        |#            bt:connect | bt:disconnect | bt:connect=<tên> | bt:disconnect=<tên>
        |#                                        … thiết bị Bluetooth kết nối / ngắt (tên: một phần, hoặc địa chỉ)
        |#                                        (BẬT phát hiện và, trên Android 12+, quyền Thiết bị lân cận)
        |#            app:open=<ứng dụng> | app:close=<ứng dụng>  … ứng dụng này ra phía trước / bạn rời nó (gói hoặc tên, một phần)
        |#                                        (cần bật z2term trong Hỗ trợ tiếp cận; hoạt động khi TẮT phát hiện)
        |#            steps:reach=N               … số bước hôm nay đạt N (BẬT phát hiện + quyền Hoạt động thể chất)
        |#            notify:any | notify:otp | notify:pkg=<phần> | notify:title=<phần> | notify:contains=<phần>
        |#                                        … có thông báo đến (cần quyền truy cập thông báo)
        |#            event:<tên> | event:<tiền tố>* | event:*  … bất kỳ sự kiện thiết bị nào, theo tên
        |#              (z2-when events liệt kê chúng; cùng tên với events.jsonl)
        |# Tên (tùy chọn, ngay sau trình kích hoạt — trước run):
        |#   name=<văn bản>          … quy tắc này để làm gì, hiện trên thẻ Tự động hóa
        |#                             (trong ngoặc nếu có dấu cách). Trống = hiện trình kích hoạt.
        |#                             Chỉ để hiển thị: không đổi thời điểm quy tắc chạy.
        |# Bộ lọc (mọi trình kích hoạt, ngay sau nó — trước run):
        |#   if=<đk>[,<đk>...]       … chỉ khi thiết bị ở trạng thái này (VÀ; ! phủ định)
        |#                             khóa là những gì z2-state in ra: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             ví dụ if=wifi,!screen / if=ssid=Nha / if=level<30
        |#   if_any=<đk>[,<đk>...]   … chạy khi **bất kỳ cái nào** đúng (dấu phẩy nghĩa là HOẶC)
        |#                             cùng với if=: "mọi cái của if VÀ một cái của if_any"
        |#   else=<cmd>              … chạy cái này **thay thế** khi if / if_any không thỏa
        |#                             ⚠ chỉ if / if_any dẫn đến đây. Lần chạy bị bỏ qua bởi between /
        |#                             days / cooldown không chạy gì — quy tắc tắt
        |#                             ban đêm phải im lặng ban đêm, kể cả else.
        |#   cooldown=30m            … không chạy lại trong khoảng này (10s / 30m / 2h)
        |#   between=22:00-07:00     … chỉ trong khung giờ này (qua nửa đêm)
        |#   days=mon-fri            … chỉ vào những ngày này (tên hoặc số cron 0-7, 0/7 = Chủ nhật)
        |#   answer='<câu hỏi>=><trả lời>' … khi lệnh hỏi câu này trong terminal, gõ câu trả lời
        |#                             và Enter (lặp lại được; ví dụ answer='[y/N]=>y'). So với phần cuối
        |#                             của dòng cuối trong kết quả.
        |#   secret='<câu hỏi>'      … như trên cho câu trả lời ẩn (mật khẩu). Nội dung được nhập
        |#                             trong trình sửa quy tắc của ứng dụng và giữ mã hóa ở đó.
        |# Lần chạy bị bỏ qua cũng được ghi — z2-when fired hiện skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, nên quy tắc không bao giờ chạy có thể giải thích được. Nút ▶ trong ứng dụng
        |# bỏ qua mọi bộ lọc (để thử quy tắc).
        |# z2-when events                        … liệt kê tên dùng được với event:
        |# z2-when pause / resume                … dừng / tiếp tục chạy tự động (quy tắc vẫn còn)
        |# z2-when fired [n]                     … lần kích hoạt gần đây (giờ / id / kích hoạt / run|paused)
        |# z2-when list                          … quy tắc đã đăng ký (id / on|off / kích hoạt / -> / cmd, TSV;
        |#                                         tên và bộ lọc, nếu có, trong [] ở cuối)
        |# z2-when remove <id|all>  (cả rm)      … xóa
        |# z2-when on <id> / off <id>            … bật / tắt
        |# z2-when log <id>                      … nhật ký chạy của quy tắc này (tail)
        |# Khi kích hoạt, lệnh chạy trong bản phân phối đã chọn với Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) trong môi trường.
        |# Với net: có Z2_WHEN_NET (kết nối hiện tại) và Z2_WHEN_NET_PREV (cái trước).
        |# Với share: có Z2_WHEN_SHARE (văn bản, hoặc đường dẫn các tệp đã nhận)
        |# và Z2_WHEN_SHARE_KIND (text|file). Nội dung chia sẻ vẫn đi vào dòng nhập như trước.
        |# Với file: có Z2_WHEN_FILE (đường dẫn đầy đủ) và Z2_WHEN_DIR.
        |# Với notify: có Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (và Z2_WHEN_OTP cho notify:otp). notify:category= so khớp chính xác: call, missed_call, msg, ...
        |# Với event: có thêm Z2_WHEN_EVENT (tên sự kiện); alarm / notify_action thêm
        |# Z2_WHEN_EVENT_NAME (định danh bạn đã đặt) và Z2_WHEN_ACTION (nút đã nhấn).
        |# Với battery:temp có Z2_WHEN_TEMP (°C). Với bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Với app: Z2_WHEN_APP_PKG (ứng dụng ở phía trước hiện tại) / Z2_WHEN_APP_PREV (cái trước).
        |# Với steps: Z2_WHEN_STEPS (số bước hôm nay). Với file: thêm Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated cho Z2_WHEN_PKG, timezone_changed cho Z2_WHEN_TZ,
        |# usb_attached / usb_detached cho Z2_WHEN_USB (tên) / Z2_WHEN_USB_ID (nhà sản xuất:sản phẩm).
        |# Cùng một quy tắc không kích hoạt hai lần trong 10 giây (sự kiện như screen_on đến thường xuyên).
        |# ví dụ z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "chuông: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Báo cáo buổi sáng' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Đã tạm dừng chạy tự động (z2-when resume để tiếp tục)"

    override val whenResumed: String get() = "Đã tiếp tục chạy tự động"

    override val whenNoFires: String get() = "(chưa có gì kích hoạt)"

    override val whenPausedNote: String get() = "# đang tạm dừng (z2-when resume để tiếp tục)"

    override val whenNoLog: String get() = "(chưa có nhật ký)"

    override val whenWriteFailed: String get() = "z2-when: không ghi được quy tắc"

    override val whenUnknownIfKey: String get() = "z2-when: khóa if= không rõ (z2-state liệt kê các khóa dùng được):"

    override val whenUnknownTrigger: String get() = "z2-when: trình kích hoạt không rõ (z2-when không đối số sẽ liệt kê):"

    override val whenBadTriggerSpec: String get() = "z2-when: trình kích hoạt này không nhận đối số đó:"

    override val whenPausedWarn: String get() = "lưu ý: chạy tự động đang tạm dừng (z2-when resume để tiếp tục)"

    override val whenRunJoined: String get() = "lưu ý: ngắt dòng trong lệnh đã thành dấu cách (quy tắc chỉ đọc một dòng)"

    override val whenEventList: String get() = """
        |screen_on              màn hình bật                               [BẬT phát hiện]
        |screen_off             màn hình tắt                               [BẬT phát hiện]
        |unlocked               thiết bị được mở khóa                      [BẬT phát hiện]
        |power_connected        bắt đầu sạc                                [BẬT phát hiện]
        |power_disconnected     ngừng sạc                                  [BẬT phát hiện]
        |battery_low            pin yếu                                    [BẬT phát hiện]
        |battery_okay           pin đã hồi phục                            [BẬT phát hiện]
        |battery_level          mức pin vượt một bậc 10%                   [BẬT phát hiện]
        |wifi_connected         đã vào Wi-Fi                               [BẬT phát hiện]
        |wifi_disconnected      đã rời Wi-Fi                               [BẬT phát hiện]
        |net_online             có kết nối hoạt động                       [BẬT phát hiện]
        |net_offline            không có kết nối hoạt động                 [BẬT phát hiện]
        |net_wifi               kết nối đang dùng là Wi-Fi                 [BẬT phát hiện]
        |net_mobile             kết nối đang dùng là di động               [BẬT phát hiện]
        |headset_plugged        đã cắm tai nghe có dây                     [BẬT phát hiện]
        |headset_unplugged      đã rút tai nghe có dây                     [BẬT phát hiện]
        |bt_audio_connected     đã nối âm thanh Bluetooth                  [BẬT phát hiện]
        |bt_audio_disconnected  đã ngắt âm thanh Bluetooth                 [BẬT phát hiện]
        |airplane_on            bật chế độ máy bay                         [BẬT phát hiện]
        |airplane_off           tắt chế độ máy bay                         [BẬT phát hiện]
        |ringer_normal          chuông ở chế độ thường                     [BẬT phát hiện]
        |ringer_vibrate         chuông ở chế độ rung                       [BẬT phát hiện]
        |ringer_silent          chuông ở chế độ im lặng                    [BẬT phát hiện]
        |bt_connected           một thiết bị Bluetooth đã kết nối          [BẬT phát hiện]
        |bt_disconnected        một thiết bị Bluetooth đã ngắt             [BẬT phát hiện]
        |powersave_on           bật tiết kiệm pin                          [BẬT phát hiện]
        |powersave_off          tắt tiết kiệm pin                          [BẬT phát hiện]
        |dnd_on                 bật Không làm phiền                        [BẬT phát hiện]
        |dnd_off                tắt Không làm phiền                        [BẬT phát hiện]
        |app_installed          đã cài một ứng dụng                        [BẬT phát hiện]
        |app_removed            đã gỡ một ứng dụng                         [BẬT phát hiện]
        |app_updated            đã cập nhật một ứng dụng                   [BẬT phát hiện]
        |timezone_changed       múi giờ đã đổi                             [BẬT phát hiện]
        |usb_attached           đã cắm thiết bị USB                        [BẬT phát hiện]
        |usb_detached           đã rút thiết bị USB                        [BẬT phát hiện]
        |boot                   thiết bị đã khởi động                      [luôn luôn]
        |alarm                  một z2-alarm đã kích hoạt                  [luôn luôn]
        |notify_action          một nút thông báo                          [luôn luôn]
        |unlock_failed          mở khóa thất bại                           [luôn luôn, cần chuẩn bị]
        |unlock_succeeded       mở khóa sau lần thất bại                   [luôn luôn, cần chuẩn bị]
        |voice                  đã nghe một lệnh giọng nói                 [luôn luôn, từ ô hoặc trợ lý]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Tên dùng được trong event:<tên>. Cùng thứ tự với events.jsonl.
        |    # Nhóm trên cần BẬT phát hiện (Cài đặt > Quyền và thông báo),
        |    # nhóm dưới do bạn tự đặt, nên hoạt động cả khi TẮT phát hiện.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … kiểm tra, tải về và giao phiên bản mới cho trình cài đặt
        |# z2-update --check             … chỉ cho biết có phiên bản mới hơn không
        |# z2-update --keep              … giữ .apk đã tải (mặc định bị xóa)
        |# z2-update --dir <thư mục>     … tải vào thư mục này thay cho thư mục của ứng dụng
        |#
        |# Lấy từ đâu
        |#   Trang GitHub Releases của z2term, cùng tệp bạn sẽ tải bằng tay
        |#   (z2term-<version>.apk, ký bằng khóa phát hành). Không liên lạc nơi nào khác, và
        |#   hoàn toàn không liên lạc gì cho đến khi bạn chạy lệnh này.
        |#
        |# Làm được gì và không làm được gì
        |#   ⚠ **Không cài lặng lẽ được.** Android luôn hiện màn hình "cài đặt?" riêng cho
        |#     ứng dụng tự thay thế chính nó, nên lần chạm cuối luôn là của bạn. Lệnh này làm mọi
        |#     bước cho đến màn hình đó.
        |#   ⚠ Lần đầu, hãy cho phép **"Cài ứng dụng không rõ nguồn gốc"** cho z2term (lệnh sẽ báo, và
        |#     ⚙Cài đặt có nút tương tự). Không có quyền này, màn hình cài đặt không bao giờ hiện.
        |#   ⚠ Cài từ F-Droid hoặc cửa hàng? Khi đó lệnh này từ chối và bảo bạn cập nhật ở đó —
        |#     thay phiên bản bạn đang có là việc của họ, không phải của chúng tôi.
        |#
        |# Tệp đã tải
        |#   Mặc định nằm trong thư mục của ứng dụng và bị xóa khi cài đặt
        |#   thành công (và ở lần khởi động tiếp theo, phòng khi ứng dụng bị dừng giữa lúc cài — điều
        |#   bình thường khi nó tự thay thế). --keep hoặc --dir đổi điều này; ⚙Cài đặt có cùng hai
        |#   cài đặt cho nút ở đó.
        |#
        |# ví dụ z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # kiểm tra mỗi đêm (vẫn hỏi bạn)
    """.trimMargin()

    override val updateUsage: String get() = "cách dùng: z2-update [--check] [--keep] [--dir <thư mục>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current là phiên bản mới nhất."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: đang tìm phiên bản mới hơn ..."

    override val updateHandedToInstaller: String get() = "z2-update: màn hình cài đặt đã mở trên thiết bị — hãy chấp thuận để hoàn tất."

    override fun updateNoApk(url: String): String = "z2-update: bản phát hành này không có .apk đính kèm. Hãy lấy từ trang phát hành: $url"

    override val updateNeedPermission: String get() = "z2-update: hãy cho phép \"Cài ứng dụng không rõ nguồn gốc\" cho z2term trước " +
            "(Cài đặt > Ứng dụng > Quyền truy cập đặc biệt > Cài ứng dụng không rõ nguồn gốc, hoặc nút trong Cài đặt của z2term)."

    override val updateManagedByStore: String get() = "z2-update: phiên bản này được cài từ cửa hàng (F-Droid / Play). Hãy cập nhật ở đó."

    override fun updateFailed(reason: String): String = "z2-update: thất bại: $reason"
}
/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Lệnh giọng nói — bảng từ (tiếng Việt). voice.sh đọc nó. Sửa để thêm hoặc đổi những gì bạn có thể nói.
#
# Cách viết:
#   khóa = một cách nói|cách nói khác|...   phân tách bằng "|"; kiểm tra từ trên xuống
#   đặt * để lấy các từ ở chỗ đó (mở * -> "mở bản đồ" cho "bản đồ")
#   *X* lấy các từ ở hai bên X
#   khóa =                               để trống bên phải sẽ tắt khóa này
#   Dấu câu được bỏ qua, chữ hoa/thường và dấu cách thừa cũng vậy
#   Khóa không có ở đây dùng giá trị đi kèm (xóa một dòng để trở về mặc định)
#   Dòng bắt đầu bằng say_ / msg_ / fmt_ / title_ là những gì được nói hoặc hiện; {…} nhận một giá trị
#
# Thử không cần nói: sh ~/.z2term/macros/voice.sh try bài tiếp theo
# "từ của bạn = một lệnh" viết trong ~/.z2term/voice/commands.txt (được kiểm tra trước bảng này).

# --- Từ được nghe ---
ai = hỏi ai *|hỏi trí tuệ nhân tạo *|ê ai *|ai ơi *|câu hỏi *
media_next = bài tiếp theo|bài tiếp|bài sau|chuyển bài|qua bài|tiếp theo|bỏ qua
media_previous = bài trước|bài trước đó|quay lại bài trước|lùi lại
media_pause = tạm dừng|dừng|dừng nhạc|tắt nhạc|dừng lại
media_play = phát|phát tiếp|tiếp tục|mở nhạc|bật nhạc|chơi nhạc
volume_up = tăng âm lượng|to lên|vặn to|lớn tiếng hơn|tăng tiếng
volume_down = giảm âm lượng|nhỏ lại|vặn nhỏ|nhỏ tiếng hơn|giảm tiếng
volume_mute = tắt tiếng|im lặng|chế độ im lặng|câm
volume_unmute = bật tiếng|mở tiếng lại
time = mấy giờ rồi|bây giờ là mấy giờ|mấy giờ|giờ là mấy giờ|xem giờ
date = hôm nay là ngày mấy|hôm nay ngày bao nhiêu|hôm nay thứ mấy|ngày bao nhiêu|ngày tháng
battery = pin|pin còn bao nhiêu|mức pin|còn bao nhiêu pin|tình trạng pin
steps = số bước|bao nhiêu bước|bước chân|hôm nay đi bao nhiêu bước|số bước hôm nay
torch_on = bật đèn pin|đèn pin|bật đèn|mở đèn pin
torch_off = tắt đèn pin|tắt đèn
screenshot = chụp màn hình|chụp ảnh màn hình
home = màn hình chính|về màn hình chính|trang chủ
news = tin tức|tin mới|đọc tin|tiêu đề|rss
weather = thời tiết|thời tiết hôm nay|trời thế nào|thời tiết thế nào
weather_tomorrow = thời tiết ngày mai|ngày mai trời thế nào|mai thời tiết thế nào
memo_read = đọc ghi chú|đọc ghi chú của tôi|ghi chú của tôi|xem ghi chú
memo_add = ghi chú *|ghi lại *|ghi nhớ *|viết *
remind_list = lời nhắc|các lời nhắc|danh sách lời nhắc|xem lời nhắc|tôi có lời nhắc gì
remind = nhắc tôi *|nhắc nhở tôi *|nhắc *|đặt lời nhắc *
wake = đánh thức tôi lúc *|đánh thức tôi *|đặt báo thức lúc *|đặt báo thức *|báo thức *
help = tôi có thể nói gì|bạn làm được gì|trợ giúp|các lệnh
timer = hẹn giờ *|đặt hẹn giờ *|bấm giờ *|đếm ngược *
navi = chỉ đường đến *|chỉ đường tới *|dẫn đường đến *|đường đến *|đưa tôi đến *|tôi muốn đi *
navi_ask = chỉ đường|dẫn đường|bắt đầu dẫn đường
place_phone = số điện thoại của *|số của *|tra số *
call = gọi cho *|gọi điện cho *|gọi *
play = phát bài *|mở bài *|phát *
open = mở *|khởi động *|chạy *
# Mở theo loại (ứng dụng mặc định của điện thoại). Ví dụ mở trình duyệt
open_browser = trình duyệt|internet|duyệt web
open_maps = bản đồ|maps
open_mail = email|thư|hộp thư
open_camera = máy ảnh|camera
open_settings = cài đặt
open_calendar = lịch
open_music = nhạc|âm nhạc
open_gallery = thư viện ảnh|ảnh|bộ sưu tập
open_contacts = danh bạ
open_dialer = điện thoại|bàn phím quay số
open_messages = tin nhắn|sms
open_calculator = máy tính|máy tính bỏ túi
# Biệt danh ứng dụng. Khi nói tên không mở được, hãy viết: biệt danh:tên ứng dụng|biệt danh:tên ứng dụng
app_names =

# Âm lượng kèm số ("giảm âm lượng 3", "âm lượng 10", "âm lượng 50 phần trăm", "âm lượng tối đa") - khớp nếu có chứa
volume_word = âm lượng|tiếng
volume_up_word = tăng|to|lớn
volume_down_word = giảm|nhỏ|bớt
volume_max_word = tối đa|lớn nhất|hết cỡ
volume_min_word = tối thiểu|nhỏ nhất|không
volume_percent_word = %|phần trăm
volume_mute_word = tắt tiếng|im lặng
volume_unmute_word = bật tiếng

# Thời lượng và giờ (hẹn giờ, lời nhắc, báo thức)
unit_hour = tiếng|giờ
unit_minute = phút
unit_second = giây
unit_day = ngày
when_before = sau|trong
when_after = nữa
clock_at = lúc|vào lúc
clock_hour = giờ
clock_minute = phút
clock_half = rưỡi
clock_am = sáng|buổi sáng
clock_pm = trưa|chiều|buổi chiều|tối|buổi tối|đêm
day_today = hôm nay
day_tomorrow = ngày mai|mai
day_after_tomorrow = ngày kia|ngày mốt|mốt
day_daily = mỗi ngày|hằng ngày|hàng ngày
day_weekdays = ngày thường|các ngày trong tuần
day_every = mỗi|hằng|hàng|các
wd_mon = thứ hai|thứ 2
wd_tue = thứ ba|thứ 3
wd_wed = thứ tư|thứ 4
wd_thu = thứ năm|thứ 5
wd_fri = thứ sáu|thứ 6
wd_sat = thứ bảy|thứ 7
wd_sun = chủ nhật
# Từ nhỏ còn lại giữa nội dung và các phần đã cắt ra
remind_filler = lúc|vào|vào lúc|là|rằng|để|cho tôi|tôi|nhé|nha

# --- Những gì được nói hoặc hiện ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Lệnh giọng nói
title_navi = Dẫn đường
title_ai = AI
title_timer = Hẹn giờ
title_news = Tin tức
title_memo = Ghi chú
title_remind = Lời nhắc
title_call = Cuộc gọi
title_help = Những gì bạn có thể nói
title_rule_voice = Lệnh giọng nói
title_rule_voice_locked = Lệnh giọng nói (đang khóa)
title_rule_timer = Hẹn giờ giọng nói
title_rule_steps = Đếm bước (cho lệnh giọng nói)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} giờ
fmt_weekdays = thứ hai|thứ ba|thứ tư|thứ năm|thứ sáu|thứ bảy|chủ nhật
fmt_months = tháng một|tháng hai|tháng ba|tháng tư|tháng năm|tháng sáu|tháng bảy|tháng tám|tháng chín|tháng mười|tháng mười một|tháng mười hai
say_time = Bây giờ là {clock}
say_date = Hôm nay là {weekday}, ngày {day} {month_name}
say_battery = Pin còn {n} phần trăm. {charging}
say_charging = Đang sạc.
say_steps = Hôm nay bạn đã đi {n} bước
say_steps_none = Chưa ghi được bước nào. Cần quyền Hoạt động thể chất và một quy tắc đếm bước
say_news = Đây là các tiêu đề mới nhất. {titles}
say_news_none = Không có tin mới
fmt_weather_lang = vi
say_weather = Bây giờ trời {desc}, {temp} độ
say_weather_tomorrow = Ngày mai: {desc}, cao nhất {hi} độ, thấp nhất {lo} độ, khả năng mưa {rain} phần trăm
say_weather_fail = Không lấy được thời tiết
say_memo_saved = Đã ghi lại
say_memo_recent = Các ghi chú gần đây của bạn. {memos}
say_memo_none = Không có ghi chú
say_remind_ok = Đã đặt lời nhắc. {res}
say_remind_fail = Không đặt được. {res}
say_remind_unclear = Tôi chưa hiểu khi nào hoặc nhắc điều gì. Hãy thử: nhắc tôi đổ rác lúc 7 giờ sáng mai
say_remind_missing = Chưa cài macro lời nhắc. Hãy chạy z2-macro install remind trong terminal
fmt_wake_label = báo thức
say_wake = Đến giờ dậy rồi
say_wake_set = Tôi sẽ đánh thức bạn lúc {clock}
say_wake_unclear = Tôi chưa hiểu giờ báo thức
fmt_unit_hour = giờ
fmt_unit_minute = phút
fmt_unit_second = giây
fmt_timer_label = {n} {unit}
say_timer_set = Đã bắt đầu hẹn giờ {label}
say_timer_done = Đã hết {label}
say_timer_unclear = Tôi chưa hiểu thời lượng hẹn giờ
say_call_missing = Không tìm thấy số của {who}
msg_call_missing = "{who}" không có trong {file}. Hãy viết mỗi dòng một "tên số".
say_open_missing = Không tìm thấy ứng dụng tên {name}
say_help = Những gì bạn có thể nói nằm trong một thông báo
say_locked = Hãy mở khóa điện thoại
msg_help = mấy giờ rồi, ngày tháng, pin, số bước / giảm âm lượng 3, âm lượng 50 phần trăm / bài tiếp theo, tạm dừng / hẹn giờ 3 phút, đánh thức tôi lúc 7 giờ / nhắc tôi đổ rác lúc 7 giờ sáng mai, lời nhắc / tin tức, thời tiết, thời tiết ngày mai / ghi chú mua sữa, đọc ghi chú / bật đèn pin, chụp màn hình / mở … / gọi cho … / chỉ đường đến …, số điện thoại của … / AI … (bật bằng voice.sh ai) / đổi từ: voice.sh words
msg_unknown = Tôi chưa hiểu: {text}
msg_locked = "{text}" sẽ tiếp tục khi điện thoại được mở khóa
msg_navi_where = Hãy nói nơi bạn muốn đến
msg_navi_hint = Đi đâu (tên địa điểm)
msg_ai_off = AI đang tắt. Chạm Sao chép rồi dán vào terminal để dùng {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI đang tắt. Hãy cài codex / claude / agy và đăng nhập một cái, rồi chọn nó trong terminal, ví dụ sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = Không tìm thấy {ai}. Hãy cài và đăng nhập, hoặc chọn cái khác bằng sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Đang hỏi AI ({ai})…
msg_ai_noanswer = Không có câu trả lời (nhật ký: {log})
msg_ai_usage = ai: hãy cho một trong off | codex | claude | agy
msg_ai_how = AI đang tắt. Để dùng: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; chỉ được hỏi khi bạn bắt đầu bằng từ khóa "AI" hoặc "câu hỏi"). Mô hình: sh ~/.z2term/macros/voice.sh model <tên> (trống = mặc định của AI đó)
fmt_ai_prompt = (Câu trả lời sẽ được đọc to. Không dùng lệnh hay tệp, hãy trả lời bằng tiếng Việt trong 2-3 câu, không dùng ký hiệu hay gạch đầu dòng.)
msg_setup_done = Đã đăng ký quy tắc lệnh giọng nói (những quy tắc đã có không được thêm lại):
msg_setup_steps = Quy tắc Đếm bước (steps:reach=1000000) chỉ giữ bộ đếm bước chạy để bạn hỏi được số bước hôm nay. Mục tiêu này không bao giờ đạt được, nên nó không chạy gì.
msg_words_file = Bảng từ: {file}  (sửa để thêm cách nói; sh ~/.z2term/macros/voice.sh words mở nó)"""
