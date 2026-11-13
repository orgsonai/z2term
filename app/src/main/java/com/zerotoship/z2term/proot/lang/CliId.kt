package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Bahasa Indonesia (`id`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: tab (タブ) / ketuk (タップ) / tekan lama (長押し) / usap (フリック) / file / folder /
 * Setelan (設定) / papan klip / makro / kartu (タイル) / panel tepi (エッジパネル) /
 * server residen (常駐サーバー)。呼びかけは Anda。
 * Android は古い言語コード `in` を使うことがあるので、res は `values-in/` に置く (AppLanguages が `in` → `id` に読み替える)。
 */
internal object CliId : CliLanguage {

    override val code: String = "id"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgId) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgId(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Memasang tumpukan GUI",
            cleanInstalling = "🧹 Memasang ulang tumpukan GUI dari awal",
            noPackageManager = "❌ Tidak ditemukan pengelola paket yang didukung (apk/apt-get/pacman).",
            invalidGeometry = "❌ Resolusi tidak valid",
            noXvnc = "❌ Xvnc/Xtigervnc tidak ada (tigervnc belum terpasang)",
            alreadyRunning = "✅ GUI sudah berjalan",
            startingXvnc = "▶ Memulai",
            xvncFailed = "❌ Xvnc gagal dimulai. Log:",
            ready = "✅ GUI siap. Hubungkan dari tab GUI di z2term.",
            running = "✅ GUI berjalan",
            stopped = "⏹ GUI berhenti",
            stoppedMsg = "⏹ Berhenti",
            usage = "Penggunaan: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Tidak bisa memasang tumpukan GUI (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Periksa jaringan Anda. Untuk memperbaikinya, Anda bisa menjalankan z2gui clean di terminal.",
            audioInstalling = "🔊 Audio GUI: memasang PulseAudio",
            audioNoPulse = "⚠️ Audio GUI: pulseaudio tidak ada, lanjut tanpa suara",
            audioNoPactl = "⚠️ Audio GUI: pactl tidak ada, lanjut tanpa suara",
            audioStartFailed = "⚠️ Audio GUI: PulseAudio tidak dimulai",
            audioReady = "🔊 Audio GUI: z2sink.monitor →",
            menuApps = "Aplikasi",
            menuWindows = "Jendela",
            menuDesktops = "Desktop virtual",
            menuPreviousDesktop = "Desktop sebelumnya",
            menuNextDesktop = "Desktop berikutnya",
            menuReload = "Muat ulang menu"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Nomor port tidak valid",
            configOk = "konfigurasi sshd(dropbear) OK",
            installingDropbear = "📦 dropbear belum terpasang — memasang…",
            dropbearInstallFailed = "❌ Tidak bisa memasang dropbear. Periksa jaringan dan nama paket.",
            privilegedPortWarn = "⚠️ Port istimewa; proot (tanpa root) mungkin tidak bisa memakainya (pakai 1024+).",
            lanExposeWarn = "⚠️ Mode terbuka ke LAN/WAN: autentikasi kunci wajib, pakai kunci yang kuat.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys kosong. Memulai dengan akses LAN dibatalkan.",
            noAuthorizedKeysHint = "   Daftarkan kunci publik Anda di ~/.ssh/authorized_keys dan coba lagi.",
            loopbackBind = "🔒 hanya loopback. Untuk membuka ke LAN, berikan --lan atau atur Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear di latar depan (Ctrl-C untuk berhenti)",
            listeningLan = "✅ dropbear mendengarkan (root, hanya autentikasi kunci)",
            listeningLoopback = "✅ dropbear mendengarkan (root, hanya autentikasi kunci, loopback)",
            startupFailed = "❌ dropbear gagal dimulai. Log:",
            deviceIp = "ip-perangkat",
            authKeysHint = "ℹ ~/.ssh/authorized_keys kosong. Daftarkan kunci publik klien untuk terhubung.",
            authKeysExample = "   mis. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Menyiapkan keyring pacman (hanya pertama kali, tanpa jaringan). Ini butuh sebentar…",
            "keyring.msgOk" to "✅ Keyring siap. Paket kini bisa dipasang.",
            "keyring.msgInitFail" to "❌ pacman-key --init gagal. Akan dicoba lagi saat tab ini dibuka berikutnya.",
            "keyring.msgNoKeyrings" to "❌ Tidak ditemukan keyring bawaan di /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate gagal. Akan dicoba lagi saat tab ini dibuka berikutnya.",
            "action.description" to "Makro aksi Android bernama. Aktifkan z2term Android actions dengan z2-key permission.\nSimpan memvalidasi dan mengganti definisi; screen=current merekam layar.\nrun menunggu sampai selesai; start mengembalikan ID eksekusi. Menginterupsi run menghentikan eksekusi itu.\nSatu eksekusi pada satu waktu, 64 instruksi sumber, 1–300 detik. Notifikasi dan stop membatalkan langkah yang tersisa.\nKoordinat memakai aplikasi latar depan secara bawaan dan memerlukan ukuran/orientasi layar yang sama. Gestur manual: hingga 3 detik; sentuhan terekam: hingga 30 detik.\nPakai start dari gestur panel; pakai run di makro shell atau perintah z2-when.\nDisimpan di /root/.z2term/actions/NAME.actions (home bersama). z2-macro actions adalah alias.",
            "action.controlHelp" to "version=2 menambahkan repeat N|forever ... end, if KONDISI ... else ... end, dan call NAMA.\nKondisi memakai kunci z2-state atau foreground=PAKET; koma berarti DAN, ! membalik satu syarat.\nStatus kondisi yang tidak tersedia membuat eksekusi gagal. Panggilan divalidasi dan dibekukan sebelum dijalankan.\n64 instruksi sumber per definisi; 10000 instruksi dijalankan per eksekusi. Batas waktu induk dan anak berlaku.",
            "action.pauseHelp" to "pause [RUN_ID] menjeda eksekusi; langkah yang sedang berjalan dikirim ulang dari awal. Menarik panel notifikasi juga menjeda.\nresume [RUN_ID] melanjutkan setelah layar selain z2term stabil di depan (hingga 5 detik; jika tidak, tetap dijeda). Batas waktu berhenti selama jeda.",
            "action.uiHelp" to "version=2 juga mendukung click SELEKTOR, long-click SELEKTOR, dan wait-ui MS SELEKTOR.\nSelektor: text=Lanjut, desc=Opsi lainnya, id=org.example.app:id/next. Kecocokan persis; tanpa tanda kutip di sekitar spasi.\nAtur target atau launch dulu. wait-ui mencari setiap 250ms selama 1–30000ms; beberapa kecocokan akan gagal.\nAksi dikirim sekali; pakai wait-ui untuk layar berikutnya. Kolom input/sandi dikecualikan.\ninspect PAKET mengembalikan teks terlihat, deskripsi, dan ID dari aplikasi target yang difokuskan sebagai JSON.",
            "adb.mNoPm" to "z2adb: tidak ditemukan pengelola paket yang didukung (apk/apt-get/pacman).",
            "adb.mInstalling" to "z2adb: memasang klien adb (${d}ADB_PKG) melalui ${d}PM ...",
            "adb.mInstallFail" to "z2adb: pemasangan adb gagal. Pasang secara manual, lalu coba lagi.",
            "adb.mHaveAdb" to "z2adb: adb sudah terpasang.",
            "adb.mNeedWireless" to "z2adb: jika penyandingan gagal, aktifkan Setelan > Opsi developer > Debugging nirkabel (Android 11+).",
            "adb.mPairPort" to "Port penyandingan (Sandingkan perangkat dengan kode penyandingan): ",
            "adb.mConnPort" to "Port koneksi (ditampilkan di bawah Debugging nirkabel): ",
            "adb.usageText" to """
        z2adb - terhubung ke adb perangkat ini melalui Debugging nirkabel (self-adb, tanpa PC).

        Syarat: aktifkan Setelan > Opsi developer > Debugging nirkabel (Android 11+).

          z2adb setup                memasang klien adb (apk/apt-get/pacman, dideteksi otomatis)
          z2adb pair  <port> [code]  menyandingkan (pakai port penyandingan + kode 6 digit dari Setelan)
          z2adb connect <port>       menghubungkan (pakai port di bawah "Debugging nirkabel")
          z2adb status               menampilkan "adb devices -l"
          z2adb <lainnya>            diteruskan langsung ke adb (shell, logcat, install, ...)

        Host bawaan adalah 127.0.0.1; berikan host:port untuk mengubahnya (atau atur Z2ADB_HOST).
        Contoh:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Memutar suara perintah Linux lokal di Android, tanpa membuka GUI.\ninstall: memasang PulseAudio di sistem Linux saat ini (mengunduh paket).\nrun PERINTAH [ARG...]: menghubungkan audio, menjalankan perintah, lalu menghentikan server audionya.\nJalankan shell untuk mengaktifkan audio bagi beberapa perintah. Tidak ada file konfigurasi yang ditimpa.\nMemerlukan tab terminal lokal dan pemutar yang kompatibel dengan PulseAudio. SSH tidak didukung.",
            "audio.missing" to "Alat audio tidak ada. Jalankan: z2-audio install",
            "audio.failed" to "Koneksi audio gagal; perintah tidak dijalankan.",
            "doctor.head" to "== z2doctor (perangkat ini) ==",
            "doctor.secApp" to "-- aplikasi --",
            "doctor.secLinux" to "-- sisi Linux --",
            "doctor.secPerm" to "-- izin --",
            "doctor.secAuto" to "-- otomatisasi --",
            "doctor.secReport" to "-- salin ini untuk meminta bantuan --",
            "doctor.lVersion" to "versi",
            "doctor.lEngine" to "mesin",
            "doctor.lDistro" to "distro",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "ruang kosong",
            "doctor.lHome" to "home",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "notifikasi",
            "doctor.lNotifyRead" to "akses notifikasi",
            "doctor.lBattOpt" to "optimasi baterai",
            "doctor.lStorage" to "penyimpanan (semua file)",
            "doctor.lSms" to "penerimaan SMS",
            "doctor.lCapture" to "deteksi peristiwa",
            "doctor.lServers" to "server residen",
            "doctor.lRules" to "aturan otomatisasi",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Setelan Android > Aplikasi > Z2Term > Notifikasi: izinkan",
            "doctor.fixNotifyRead" to "-> Setelan > Izin dan notifikasi > Deteksi notifikasi",
            "doctor.fixBattOpt" to "-> Setelan > Perlindungan proses latar belakang > kecualikan dari optimasi baterai",
            "doctor.fixStorage" to "-> Setelan > izinkan akses ke semua file (diperlukan untuk melihat /sdcard)",
            "doctor.fixSms" to "-> Setelan > Deteksi SMS (hanya untuk pemicu sms:)",
            "doctor.fixCapture" to "-> Setelan > Deteksi peristiwa sistem (diperlukan pemicu charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> otomatisasi dijeda. \"z2-when resume\" atau tab Otomatisasi",
            "doctor.fixDisk" to "-> kurang dari 500 MB kosong. Hapus data OS yang tidak dipakai di Setelan",
            "doctor.fixSdcard" to "-> /sdcard kosong di sini; izin penyimpanan di atas yang membuatnya terlihat",
            "doctor.noteRedacted" to "(SSID / IP / nama host sengaja dihilangkan)",
            "doctor.allGood" to "Tidak ditemukan masalah.",
            "doctor.someBad" to "masalah ditemukan:",
            "doctor.usage" to "penggunaan: z2doctor [--share | --clip]   (--share menyerahkan laporan ke menu bagikan Android)",
            "doctor.pausedYes" to "dijeda",
            "doctor.secExits" to "-- bagaimana berakhirnya terakhir kali --",
            "doctor.lExitsNote" to "keluar tidak normal terbaru (terbaru dulu):",
            "doctor.lExitsNone" to "keluar tidak normal: tidak ada yang tercatat",
            "doctor.lExitsFile" to "(riwayat lengkap: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> dihentikan karena kekurangan memori. Jalankan lebih sedikit pekerjaan berat sekaligus, atau kurangi server residen / tab",
            "doctor.title" to "diagnosis mandiri untuk \"tidak berfungsi\"",
            "doctor.seeAlso" to "Lihat juga: z2scan self (pemeriksaan keamanan, alat lain)",
            "help.header" to "Perintah khusus Z2Term",
            "help.list" to """
        |[Versi / info]
        |  z2version [--short]            Versi aplikasi, mesin, OS (distro), kernel
|  z2doctor [--share|--clip]      Kenapa tidak berfungsi? Diagnosis mandiri + laporan untuk ditempel
        |
        |[Fitur ponsel]
        |  z2-notify [-h] [-b LABEL]...   Notifikasi (-h: banner, -b: tombol balasan)
        |  z2-ask "pertanyaan"            Bertanya lewat kolom balasan notifikasi; mencetak jawabannya
        |  z2-listen [-w WORD,...]        Suara ke teks, hanya di perangkat (z2-listen -h)
        |  z2-toast "pesan"               Toast (pesan singkat di layar)
        |  z2-share "teks"                Menyerahkan teks ke menu bagikan Android
        |  z2-open <url|path>             Membuka URL/file di aplikasi bawaan
        |  z2-view <file.html>            Membaca di aplikasi halaman yang dibuat di sini
        |  z2-edit <file>                 Menyunting file teks di editor aplikasi
        |  z2-qr                          Memindai kode QR dengan kamera (tombol membukanya)
        |  z2-img <file>                  Menggambar gambar di terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Membaca / mengatur papan klip (set: stdin tanpa argumen)
        |  z2-file pick | save FILE     Pemilih / ekspor dokumen Android (z2-file --help)
        |  z2-battery                     Tingkat baterai / status pengisian (JSON)
        |  z2-vibrate [ms]                Bergetar (bawaan 200ms)
        |  z2-say "teks"                  Berbicara lewat TTS perangkat (stdin tanpa argumen)
        |  z2-torch [on|off|toggle|status]       Senter (bawaan toggle)
        |  z2-media [play|pause|next]     Tombol media (juga previous/stop)
        |  z2-volume up|down|N|N%         Volume media (mencetak saat ini/maks)
        |  z2-audio run COMMAND ...      Menjalankan perintah Linux dengan audio Android
        |  z2-sensor [light|accel|prox]   Membaca satu nilai sensor (JSON)
        |  z2-intent -a ACT -d URI ...    Meluncurkan Intent Android apa pun (lihat MACRO-GUIDE)
        |  z2-state [key]                 Status saat ini sebagai JSON (atau satu kunci mentah)
        |  z2-screen keepon 1h | off      Mencegah layar mati sendiri, untuk sementara
        |  z2-tile set 1 backup.sh        Menaruh makro/perintah di kartu Setelan Cepat (12)
        |  z2-icon edit 1 | edit notify   Gambar sendiri ikon kartu / bilah status (24/48/64 titik)
        |  z2-alarm at|daily HH:MM [name] Pemicu waktu -> events.jsonl (juga list/cancel)
        |  z2-when <trigger> run <cmd>    Berjalan sendiri saat pengisian/baterai/waktu/peristiwa (z2-when: penggunaan)
        |  z2-macro list|install <name>   Contoh otomatisasi bawaan (lihat MACRO-GUIDE)
        |  z2-session list|new|send|...   Mengendalikan tab aplikasi ini (z2-session: penggunaan)
        |  z2-usb list | allow [device]   Memakai perangkat USB dari Linux (Android meminta izin)
        |  z2-update [--check]            Memperbarui z2term dari GitHub Releases (Anda menyetujui)
        |
        |[Aplikasi grafis (GUI)]
        |  z2gui start [WxH] | stop | status   Desktop Linux (mis. z2gui start 1280x720)
        |  z2run <gui-app>                Meluncurkan aplikasi GUI (juga membuka tab GUI)
        |
        |[Koneksi]
        |  z2adb pair/connect/shell ...   adb ke ponsel ini sendiri, tanpa PC  (z2adb help)
        |  sshd [-p N]                    Server SSH (bawaan: hanya 127.0.0.1, kunci)
        |
        |[Keamanan]
        |  z2scan self [--save]           Diagnosis mandiri perangkat/localhost ini (z2scan help)
        |  z2scan diff                    Hanya yang berubah sejak acuan (keluar 1 = baru)
        |  z2scan net|host|cve            nmap/lynis/trivy pada localhost (jarak jauh perlu opt-in)
        |
        |[Bantuan]
        |  z2help | z2term                Daftar ini
        |
        |Setiap perintah di atas menjelaskan dirinya: tambahkan --help (mis. "z2-tile --help").
        |Selain itu: "z2adb help", jalankan "z2gui" tanpa argumen, atau bagian 11 HANDBOOK.
    """,
            "help.edge" to "[Panel mengambang]\nz2-edge --help   Mendefinisikan panel dan memperbarui isinya\nz2-key --help    Aksi global Android\nz2-shot --help   Menangkap hanya bagian yang dilingkari\nz2-app --help    Mendaftar aplikasi dan mengekspor ikon\nz2-action --help Makro aksi Android bernama\nz2-ui --help     Kendali aplikasi oleh AI (bantuannya adalah panduan untuk agen AI)",
            "macro.rssBody.head" to """
#
# Persiapan:
#   1) Satu URL feed per baris di:  ~/.z2term/rss/feeds.txt
#   2) Periksa setiap 30 menit:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Opsional - satu feed atau kata per baris yang tidak boleh tenggelam:  ~/.z2term/rss/important.txt
#      Yang cocok mendapat notifikasinya sendiri, jadi feed yang ramai tidak bisa menggesernya.
#      Tulis bagian dari URL atau bagian dari judul (mis. example.org).
#   4) Daftarkan aksi notifikasi di Otomatisasi:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Buka mengirim artikel ke peramban; Daftar membuka semua yang terkumpul untuk dibaca di z2term.
#      Catatan: membuka layar dari tombol notifikasi memerlukan izin "tampil di atas aplikasi lain".
#   5) Baca daftar yang terkumpul: sh ~/.z2term/macros/rss.sh view
#      Kelola kedua aturan di Daftar perintah -> Otomatisasi -> Aturan otomatisasi.
#      Sunting aturan notifikasi yang ada alih-alih menambah yang lain.
#   6) Notifikasi: secara bawaan pemeriksaan terjadwal memberi notifikasi dan pemeriksaan dari daftar tidak.
#        sh ~/.z2term/macros/rss.sh notify off        # pemeriksaan terjadwal: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Segarkan / Artikel di daftar: on / off
#      Keduanya juga berupa sakelar di Setelan pada daftar, bisa diubah kapan saja.
#
# Memerlukan: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Gambar mini (Setelan pada daftar) juga memerlukan Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Baterai: makin sering diperiksa, makin boros. Jangan kurang dari 30 menit.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "jumlah baris maksimal yang disimpan di seen/latest",
            "macro.cNoPy" to "memerlukan python3 (mis. apk add python3)",
            "macro.cWriteFeeds" to "Tulis satu URL feed per baris di:",
            "macro.cFeedsHint" to "# Satu URL feed per baris (baris yang diawali # diabaikan)",
            "macro.cSkipFail" to "lewati diam-diam feed yang gagal diunduh atau diurai (yang lain tetap berjalan)",
            "macro.cDiff" to "# Buang yang sudah pernah dilihat agar hanya yang baru tersisa (trik yang sama dengan perbandingan acuan z2scan).\n# Tanggal dan urutan feed tidak bisa diandalkan; keduanya tidak pasti.",
            "macro.cPrepend" to "# Tumpuk yang terbaru di depan agar mode \"awal (head)\" widget membacanya dengan benar.",
            "macro.cNotify" to "%s baru",
            "macro.cOpen" to "Buka",
            "macro.cListNone" to "Belum ada yang terkumpul — jalankan dulu sekali tanpa argumen.",
            "macro.cListHint" to "Tampilkan dengan: rss.sh list [jumlah]",
            "macro.cList" to "Daftar",
            "macro.cPageTitle" to "Feed",
            "macro.cPageSites" to "%s situs",
            "macro.cPageArticles" to "%s artikel",
            "macro.cPageDoc" to "# Membangun halaman baca (z2-view menampilkannya di aplikasi; tanpa server, tanpa peramban).\n" +
            "# Dikelompokkan per situs, terbaru dulu, dengan satu tombol Buka per artikel. Tema ponsel\n" +
            "# masuk sebagai variabel CSS, jadi halaman mengikuti warna terminal.",
            "macro.cViewHint" to "Baca dengan: rss.sh view",
            "macro.cHitMax" to "jumlah maksimal notifikasi per artikel dalam satu eksekusi (sisanya masuk ringkasan)",
            "macro.cSplitDoc" to "# Pisahkan yang tidak boleh terlewat. Isi ringkasan hanya memuat 3 baris, jadi feed ramai\n" +
            "# yang diperbarui bersamaan akan menggeser yang penting. Yang cocok mendapat **notifikasinya\n" +
            "# sendiri** (aplikasi memberi id berbeda per notifikasi, jadi tidak ada yang tergantikan).",
            "macro.cNameDoc" to "# Taruh URL di nama notifikasi: menekan tombol mengembalikannya sebagai {name} di\n" +
            "# notify_action, jadi artikel yang benar terbuka meski ada beberapa notifikasi di layar (persiapan 4).",
            "macro.cListDoc" to "# Tampilkan yang terkumpul sebagai daftar yang mudah dibaca (tanpa warna — harus tampak seperti file/widget).\n" +
            "# latest.txt sendiri tetap data mentah, satu artikel per baris (tail widget dan rss-open.sh membacanya).",
            "macro.cSettings" to "Setelan",
            "macro.cArchive" to "Arsip",
            "macro.cDelete" to "Hapus",
            "macro.cThumbs" to "Gambar mini",
            "macro.cSave" to "Simpan artikel lama",
            "macro.cNotifyPoll" to "Beri tahu artikel baru (pemeriksaan terjadwal)",
            "macro.cNotifyView" to "Beri tahu artikel baru (pemeriksaan dari daftar)",
            "macro.cDeleteSite" to "Hapus semua artikel dari situs ini",
            "macro.cConfirmSite" to "Hapus %s artikel dari %h dari daftar?",
            "macro.cArticles" to "Artikel",
            "macro.cPrev" to "Sebelumnya",
            "macro.cNext" to "Berikutnya",
            "macro.cCount" to "%s artikel",
            "macro.cUndated" to "Tanpa tanggal",
            "macro.cArchiveEmpty" to "Belum ada yang disimpan. Aktifkan Simpan artikel lama di Setelan, dan artikel yang terkumpul akan tetap di sini.",
            "macro.cSearch" to "Cari",
            "macro.cSearchWord" to "Kata (pisahkan dengan spasi untuk mewajibkan semuanya)",
            "macro.cByMonth" to "Per bulan",
            "macro.cNoHit" to "Tidak ada yang ditemukan.",
            "macro.cDropMonth" to "Hapus bulan ini",
            "macro.cDropConfirm" to "Hapus %s artikel tersimpan dari %m? Tidak bisa diurungkan.",
            "macro.cOnOff" to "Berikan on atau off",
            "macro.cNoPillow" to "Gambar mini memerlukan Pillow untuk Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Persiapan: pasang dulu sisi pengumpulnya
#   z2-macro install rss
# Widget: tetapkan "rss-open" ke sebuah tombol di setelan widget status.
#
# z2-run: tetapkan "rss-open" ke tombol widget status (untuk mencoba di sini: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Belum ada artikel",
            "macro.cAllRead" to "Tidak ada yang baru untuk dibuka",
            "macro.cPick" to "# Baris latest.txt berbentuk \"judul  URL\". Ambil URL pertama yang belum dibuka.",
            "macro.cCap" to "batasi daftar yang sudah dibuka agar tidak membesar tanpa batas",
            "macro.copied" to "Kode disalin: ${d}{code}",
            "macro.cleared" to "Kode yang disalin sudah dihapus",
            "macro.otpTitle" to "Kode sekali pakai",
            "macro.cTtl" to "detik sebelum salinan dihapus (hanya jika langsung masuk)",
            "macro.cCode" to "# Aplikasi sudah mengambil kodenya. Jangan lakukan apa pun jika tidak berhasil.",
            "macro.cTry" to "# Coba taruh langsung. Ini hanya masuk selama Anda **melihat aplikasi**:\n" +
            "# Android 10+ hanya mengizinkan aplikasi di latar depan menulis ke papan klip, jadi eksekusi\n" +
            "# di latar belakang diabaikan diam-diam. Baca lagi untuk melihat apakah sudah masuk.",
            "macro.cFallback" to "  # Tidak masuk = Anda tidak sedang melihat aplikasi. Serahkan lewat tombol\n" +
            "  # \"Salin\" di notifikasi sebagai gantinya (menekannya membawa aplikasi ke depan).\n" +
            "  # ⚠ Yang masuk dengan cara ini tidak dihapus oleh TTL di bawah: menghapus juga memerlukan latar depan.",
            "macro.cClear" to "# Setelah TTL, hapus papan klip hanya jika masih berisi kode yang disalin\n" +
            "# (yang disalin setelahnya tidak disentuh).",
            "macro.cHow" to """
# ■ Menyalin nomor telepon dari notifikasi panggilan
#   Mencari nomor telepon tunggal di judul lalu di teks, dan menawarkan tombol Salin.
#   Penelepon terdaftar maupun tidak disertakan; kontak tidak ditanyakan.
#   Nama saja tidak cukup untuk mencari nomor. Hanya akses notifikasi yang dipakai.
#   Nama file unknown-call.sh dipertahankan agar aturan yang ada tetap berfungsi.
""",
            "macro.cIsNum" to """
# Apakah ini nomor tunggal? Buang setiap karakter yang bisa ada di nomor (angka + - ( ) spasi);
# jika **tidak tersisa apa pun** dan ada 7 sampai 15 digit, anggap sebagai nomor.
#   -> Kolom yang berisi nama atau huruf lain bukan nomor tunggal; periksa kolom lainnya.
#   -> "Tidak dikenal"/"Nomor pribadi" juga jatuh di sini (longgarkan uji di bawah jika Anda menginginkannya).
# ⚠ Jangan tulis ini sebagai case [!...]: tanda ) di pola dibaca sebagai pemisah case.
""",
            "macro.cScan" to "# Penelepon biasanya ada di judul, tetapi beberapa aplikasi telepon menaruhnya di teks. Periksa keduanya.",
            "macro.cSkip" to "# Tidak ada nomor tunggal di kedua kolom: tidak ada yang disalin.",
            "macro.cWhat" to "# Kategori memberi tahu mana yang memicu (nilai yang sama dengan notify:category= yang terdaftar).",
            "macro.cCopy" to """
# Serahkan nomor lewat tombol "Salin" di notifikasi (-c). Memanggil z2-clip set di sini
# tidak akan masuk: Android 10+ hanya mengizinkan aplikasi di latar depan menulis ke papan klip, dan selama
# panggilan itu adalah aplikasi telepon. Menekan tombol membawa z2term ke depan untuk saat itu.
""",
            "macro.missed" to "Panggilan tak terjawab",
            "macro.incoming" to "Panggilan masuk",
            "macro.title" to "Salin nomor telepon",
            "macro.stNew" to "baru",
            "macro.stSame" to "sama",
            "macro.stDiff" to "beda",
            "macro.msgInstalled" to "terpasang:",
            "macro.msgSame" to "yang sama sudah terpasang:",
            "macro.msgOutdated" to "milik Anda berbeda dari yang bawaan:",
            "macro.msgHowUpdate" to "lihat dulu: z2-macro diff %s   /   ganti dengan yang bawaan: z2-macro install -f %s (suntingan Anda juga hilang)",
            "macro.msgNotInstalled" to "belum terpasang (jalankan dulu z2-macro install):",
            "macro.msgRemoved" to "dihapus:",
            "macro.msgAbsent" to "tidak terpasang:",
            "macro.msgRemoveDiffers" to "milik Anda berbeda dari yang bawaan, jadi tidak dihapus:",
            "macro.msgHowRemove" to "lihat dulu: z2-macro diff %s   /   tetap hapus: z2-macro remove -f %s (suntingan Anda juga hilang)",
            "macro.msgNoDiffTool" to "tidak ada diff di sini. Anda tetap bisa membaca yang bawaan dengan z2-macro show.",
            "macro.msgSameAsBundled" to "sama dengan yang bawaan.",
            "macro.msgNotFound" to "contoh tidak ditemukan:",
            "macro.msgHintResident" to "Agar tetap berjalan, daftarkan ini di Setelan -> Server residen:",
            "macro.msgHintRun" to "Cara menjalankannya:",
            "macro.remindBody.head" to """
#
# Persiapan (sekali):
#   sh ~/.z2term/macros/remind.sh setup   mendaftarkan kedua kait dan kedua kartu
#
# Sintaks:
#   remind.sh <kapan> <teks>         menambah pengingat
#   remind.sh list                   menampilkan daftar (nomor di depan)
#   remind.sh del <n|all>            membatalkan (rm juga bisa)
#   remind.sh peek                   menampilkan daftar di notifikasi (dengan tombol Hapus)
#   remind.sh ask                    bertanya lewat kotak balasan notifikasi (untuk kartu)
#   remind.sh setup                  mendaftarkan kait dan kartu (sekali)
#   remind.sh help                   teks ini
#
# <kapan> - sekali:
#   30m / 90s / 2h                   dari sekarang
#   18:30                            pukul 18:30 berikutnya (besok jika sudah lewat)
#   sat 21:00                       hari yang cocok berikutnya, sekali (nanti hari ini juga dihitung)
#   Jepang: 土曜日の夜九時 = sat 21:00 ; 朝七時 = 07:00 ; 夜九時半 = 21:30
#   Jepang: 夜12時 ambigu; untuk tengah malam pakai 00:00 dengan hari yang jelas.
#   tomorrow 18:30 / 3d 07:00        per hari (3d = 3 hari lagi)
#   07/30 19:00                      bulan/tanggal (tahun depan jika sudah lewat)
#   2030 07/30 19:00                 dengan tahun
#   203007301900 / 07301900          hanya angka (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ tanpa jam, memakai **jam saat ini** (3d / 07/30 ...)
#
# <kapan> - berulang:
#   daily 07:00 / weekday 09:00      setiap hari / Sen-Jum
#   weekly mon 09:00                 hari itu setiap minggu
#   monthly 15 09:00                 tanggal itu setiap bulan
#   yearly 07/30 19:00               bulan dan tanggal itu
#   every 19:00 / every wed 19:00    "every" saja bisa (kata berikutnya yang menentukan)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Contoh:
#   remind.sh 30m minum obat
#   remind.sh tomorrow 18:30 buang sampah
#   remind.sh monthly 25 10:00 sewa
#   remind.sh list        ->  1  ⏰ 07/30 18:30  buang sampah
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (setelah itu cukup: remind.sh 30m ...)
""",
            "macro.cStore" to """# Satu pengingat = satu baris di ${d}DIR/<id>.txt, dipisahkan tab: jenis / label / id-kapan / teks.
#   jenis: once (akan datang) / fired (sudah terpicu) / repeat (berulang)
#   id-kapan: hanya untuk repeat (agar del bisa memanggil z2-when remove). "-" selain itu.
# Teks di akhir agar bisa berisi apa saja kecuali TAB.""",
            "macro.cParse" to """# Membaca 1 sampai 3 kata dan memutuskan:
#   KIND ... once|repeat   PLAN ... label yang ditampilkan   SPEC ... yang diterima z2-alarm/z2-when   USED ... kata yang dipakai""",
            "macro.pDaily" to "setiap hari ",
            "macro.pWeekday" to "hari kerja ",
            "macro.pWeekly" to "setiap minggu ",
            "macro.pMonthly" to "setiap bulan tanggal ",
            "macro.pYearly" to "setiap tahun ",
            "macro.mDay" to "",
            "macro.eDaily" to "harian",
            "macro.eWeekly" to "mingguan",
            "macro.eMonthly" to "bulanan",
            "macro.eYearly" to "tahunan",
            "macro.mBadDate" to "tanggal ini tidak ada (mis. 07/30):",
            "macro.cEvery" to """  # Bentuk singkat "every ...". **Bentuk kata berikutnya** memilih daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Jumlah kata tidak berubah (satu kata diganti kata lain), jadi USED tetap benar.""",
            "macro.cExpand" to "# Memutuskan pengulangan mana yang dimaksud \"every\", dari kata yang mengikutinya.",
            "macro.cDigits" to """    # Bentuk angka saja: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Periksa 12 digit sebelum 8 (pola 8 digit juga cocok dengan untaian 12).""",
            "macro.cYmd" to """  # Ditulis sebagai tanggal. ⚠ z2-alarm tidak menerima tanggal, jadi ini juga menjadi jeda waktu.""",
            "macro.cCivil" to """# Tanggal sipil -> hari sejak 1970-01-01 (termasuk tahun kabisat; days_from_civil dari Howard Hinnant).
# ⚠ date -d "2026-07-30" tidak bisa diandalkan di busybox, jadi perhitungannya dilakukan di sini.
# ⚠ Hanya selisih hari yang dipakai dan jam diserahkan ke [day_epoch], yang meniadakan zona waktu.""",
            "macro.cCron" to """# Membuat ekspresi cron (menit jam tanggal bulan hari-dalam-minggu) dari HH:MM dan satu kolom hari.
# JANGAN pakai expr untuk membuang nol di depan: expr keluar dengan 1 saat hasilnya 0, jadi sisi kanan
# `expr "${d}m" + 0 || echo "${d}m"` juga berjalan dan nilainya menjadi dua baris.""",
            "macro.afterLabel" to """# "30m" -> "dalam 30m (14:35)". Menampilkan jamnya agar bisa langsung diperiksa.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "dalam ${d}num${d}u (${d}at)" || echo "dalam ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "penggunaan: remind.sh <kapan> <teks>",
            "macro.mBadWhen" to "tidak bisa membaca waktunya (coba: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "tulis jam dalam format HH:MM:",
            "macro.mBadRange" to "jam di luar rentang (00:00-23:59):",
            "macro.mNoTime" to "jam tidak diberikan (mis. daily 07:00):",
            "macro.mBadDow" to "hari tidak dikenal:",
            "macro.mPastTime" to "waktu ini sudah lewat:",
            "macro.cDays" to """    # Bentuk per hari. Tanpa jam, pakai **jam saat ini** (jangan pernah mengarang nilai bawaan).
    # Bisa juga datang sebagai satu kata, jadi bentuk yang digabung juga diterima.""",
            "macro.cDayEpoch" to """# Mengubah "HH:MM, N hari lagi" menjadi detik epoch. ⚠ busybox tidak punya date -d "tomorrow",
# jadi hitung dulu tengah malam hari ini lalu tambahkan hari dan jamnya.
# ⚠ Nilai dengan nol di depan ("08") dibaca sebagai oktal oleh ${d}(()), jadi nolnya dibuang.
# ⚠ Satu hari ditambahkan sebagai 86400 detik, jadi bisa bergeser satu jam pada hari pergantian waktu musim panas.""",
            "macro.cFmtAt" to """# Label yang ditampilkan di daftar. ⚠ Mempertahankan "tomorrow" akan salah setelah tanggalnya lewat.""",
            "macro.mNoBody" to "sebutkan apa yang perlu diingatkan",
            "macro.mNoAlarm" to "tidak bisa menjadwalkannya",
            "macro.mNone" to "tidak ada yang dijadwalkan",
            "macro.mFired" to " (sudah terpicu)",
            "macro.mRemoved" to "dihapus:",
            "macro.mNoSuchNum" to "entri tidak ditemukan (periksa remind.sh list):",
            "macro.mUsageDel" to "penggunaan: remind.sh del <n|all>",
            "macro.bDone" to "Selesai",
            "macro.bDelete" to "Hapus",
            "macro.mAskDel" to "Hapus yang mana?",
            "macro.mAskDelH" to "nomor / all",
            "macro.mDeletedTitle" to "🗑 Dihapus",
            "macro.cPeek" to """# Menampilkan daftar di notifikasi (dari kartu "daftar"). **Pertahankan nomornya** — dengan nomor itulah
# yang akan dihapus ditunjuk. ⚠ Menampilkan daftar tanpa cara menghapus adalah keluhannya; karena itu ada tombolnya.""",
            "macro.cAskDel" to """# Tombol "hapus": meminta nomor dan meneruskannya ke [cmd_del] (jalur yang sama dengan CLI).""",
            "macro.bS1" to "+10mnt",
            "macro.bS2" to "+1j",
            "macro.mAgain" to "- lagi dalam",
            "macro.mTitle" to "⏰ Pengingat",
            "macro.mAsk1" to "Ingatkan tentang apa?",
            "macro.mAsk1H" to "mis. minum obat",
            "macro.mAsk2" to "Kapan?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "silakan masukkan lagi",
            "macro.mAskGiveUp" to "Tidak bisa membacanya ${ASK_TRIES} kali. Anda juga bisa menambahkannya dari terminal: remind.sh 30m minum obat",
            "macro.mOkTitle" to "⏰ Pengingat dijadwalkan",
            "macro.mNgTitle" to "⚠ Tidak dijadwalkan",
            "macro.cHooks" to """  # Satu kait untuk "pengingat terpicu", satu untuk "tombol notifikasi ditekan". Hanya
  # dua ini, berapa pun jumlah pengingatnya. Tidak ada yang bergantung pada sakelar deteksi.""",
            "macro.cTiles" to """  # Hanya isi slot kosong dan yang sudah milik kita (jangan pernah menimpa milik orang lain).
  # Nama makro dengan argumen berfungsi mulai 0.8.275 (sebelumnya tulis sh + path lengkap).""",
            "macro.mSetupHooks" to "kait terdaftar:",
            "macro.mSetupTiles" to "kartu:",
            "macro.mPathHint" to "Catatan: tab ini belum bisa menjalankan remind.sh dengan namanya. Buka tab baru, atau jalankan " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH di sini.",
            "macro.cPathHint" to """  # ⚠ Folder makro baru ada di PATH sejak 0.8.287, jadi **tab yang dibuka sebelumnya**
  # masih memakai yang lama. Periksa apakah namanya ditemukan dan beri tahu apa yang harus dilakukan jika tidak
  # (jika tidak, `command not found` tidak punya alasan yang terlihat).""",
            "macro.mPlace" to "Catatan: Anda masih perlu menempatkan kartu sendiri, dari layar sunting (pensil) Setelan Cepat.",
            "macro.lRemind" to "ingatkan",
            "macro.lList" to "daftar",
            "macro.cSelf" to "# Hanya bereaksi pada notifikasi kita sendiri (id kita selalu diawali r)",
            "macro.cKeepRepeat" to "      # Jangan hapus pengingat berulang di sini: pengingat itu harus terpicu lagi besok.",
            "macro.cAsk" to """# Jalur yang dipakai dari kartu / notifikasi. ⚠ **Selalu jawab dengan notifikasi**:
# di sini tidak ada yang melihat terminal, jadi gagal di stderr terbaca sebagai "saya menekan dan
# tidak terjadi apa-apa" (padahal terjadi — alasannya hanya masuk ke run.log kartu).
#   Tidak terbaca -> beri tahu alasannya dan tanya lagi, hingga $ASK_TRIES kali (jawaban sebelumnya dipertahankan)
#   Terjadwal     -> tampilkan rencana dan teksnya di notifikasi""",
            "macro.cSnoozeCancel" to "batalkan alarm tunda jika pengingat sudah ditunda sebelum dikerjakan",
            "macro.qrBody.head" to """
#
# Penggunaan:
#   qr.sh "https://example.com"       mengodekan untaian dan menggambarnya di sini
#   qr.sh -f notes.txt                mengodekan isi file
#   z2-clip get | qr.sh               mengodekan isi papan klip
#   qr.sh -o ~/qr.png "text"          menyimpan PNG (tidak ada yang digambar)
#   qr.sh -t "text"                   menampilkan blok alih-alih gambar
#   qr.sh -s 24 "text"                memilih ukuran (dalam kolom; bawaan muat di lebar layar)
#
# z2-run: qr.sh "https://example.com"   (setelah terpasang, cukup namanya. Berjalan sekali)
#
# # Prasyarat (tanpa ini tidak berfungsi, atau tampak berbeda)
#
#   1) Pengodean dilakukan aplikasi ... di tab z2term tidak ada yang perlu
#      dipasang (z2-qr yang mengodekan). Jika qrencode sudah ada, itu yang dipakai.
#      Di tempat aplikasi tidak terjangkau (lewat ssh), pasang sekali per tab (distro):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Tanpa keduanya, perintah pemasangan ditampilkan dan skrip berhenti.
#
#   2) Gambar (bawaan) hanya muncul di tab aplikasi ini ... digambar dengan
#      grafik Kitty. Lewat ssh, atau di terminal yang tidak menampilkan gambar, Anda mendapat
#      aliran karakter acak sebagai gantinya. Pakai -t di sana.
#
#   3) -t (blok) bergantung pada font terminal ... font yang menyisakan jarak antar
#      baris tetap terbaca oleh mata tetapi tidak oleh kamera. Untuk memindai dengan kamera,
#      pilih gambar bawaan atau PNG dari -o.
#
#   4) Proporsinya diasumsikan ... lihat "Proporsi" di bawah. Gepeng? Z2_QR_ASPECT.
#
# Opsi:
#   -f FILE   membaca input dari file (jika tidak, argumen, jika tidak, stdin)
#   -o PNG    menyimpan PNG alih-alih menggambar. Beberapa kode menjadi
#             qr-1.png / qr-2.png ...
#   -t        menampilkan blok. Untuk terminal tanpa gambar, dan lewat ssh
#   -s N      ukuran dalam kolom saat menggambar. Bawaan hingga 34, dibatasi lebar
#   -h        bantuan ini
#
# Tentang input panjang:
#   Satu kode memuat jumlah tetap (paling banyak 2953 byte alfanumerik; beberapa ratus
#   jika harus tetap terbaca). Input lebih dari 900 byte dipotong di baris baru
#   menjadi beberapa kode, bernomor [1/3] dan ditampilkan berurutan.
#   PERINGATAN satu baris yang terlalu panjang untuk muat di satu kode sama sekali tidak bisa dikodekan.
#
# Proporsi:
#   Terminal tidak memberi tahu ukuran selnya, jadi proporsi karakter 1:2 diasumsikan
#   agar kode menjadi persegi. Jika tampak gepeng, sesuaikan dengan variabel lingkungan:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (lebih kecil = lebih tinggi. Bawaan 0.5)
""",
            "macro.cMaxBytes" to "byte maksimal per kode (dipotong jika lebih)",
            "macro.cPngModule" to "titik per modul di PNG yang disimpan",
            "macro.cTargetPx" to "lebar sasaran dalam piksel saat menggambar",
            "macro.cMargin" to "zona sunyi di sekitar kode (modul; diperlukan untuk memindai)",
            "macro.cUsageFn" to "Menampilkan blok komentar di awal sebagai bantuan (tanpa jumlah baris tetap).\n" +
            "# Baris kosong tidak menghentikannya (NF yang menentukan), jadi bagian yang dipisahkan baris kosong tetap utuh.",
            "macro.cNeedEncoder" to "Prasyarat 1: sesuatu untuk mengodekan. qrencode jika ada, jika tidak, aplikasi.",
            "macro.mMissing" to "qr.sh: tidak ada pengode di sini - tidak ada qrencode, dan aplikasi tidak terjangkau",
            "macro.mInstallOnce" to "  Pasang sekali di tab ini:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Membuat PNG. qrencode memilih ukuran modul dengan percobaan; aplikasi menerima sasaran.",
            "macro.cMakeBlocks" to "Menggambar dengan karakter blok. Kedua jalur mengeluarkan warnanya sendiri, jadi tema apa pun cocok.",
            "macro.qrBody.mNoTmp" to "tidak bisa membuat folder kerja",
            "macro.qrBody.mUnreadable" to "tidak terbaca:",
            "macro.qrBody.mEmpty" to "input kosong",
            "macro.mSizeNum" to "-s menerima angka",
            "macro.qrBody.mUsageHint" to "lihat qr.sh -h",
            "macro.mPieceFail" to "qr.sh: tidak bisa membuat kode %d: %s",
            "macro.qrBody.cCollect" to "---- kumpulkan input ----",
            "macro.cSplit" to "---- potong di baris baru jika terlalu panjang ----",
            "macro.cSplit2" to "Pindah ke potongan berikutnya tepat sebelum total berjalan melewati MAX_BYTES. Baris tidak pernah\n# dipotong di tengah, jadi teks multibyte tetap utuh.",
            "macro.cTrim" to "Buang baris baru di akhir (jangan pernah mengodekan baris baru yang tidak ada di input).",
            "macro.cSize" to "---- pilih ukuran tampilan berdasarkan lebar terminal ----",
            "macro.cPngWidth" to "Mengembalikan lebar piksel PNG (byte 16-19, IHDR).",
            "macro.cModulePx" to "Mencari titik per modul dari lebar sasaran dalam piksel.",
            "macro.cInline" to "Menggambar PNG di tempat dengan protokol grafik Kitty.\n# c/r menetapkan berapa sel yang ditempati, lalu cetak baris baru sebanyak itu agar kursor berada di bawahnya.",
            "macro.cEmit" to "---- keluarkan ----",
            "macro.cAnsi" to "ANSIUTF8 mengeluarkan warnanya sendiri, jadi terang/gelap tetap benar apa pun temanya.",
            "macro.mdBody.head" to """
#
# Penggunaan:
#   md.sh README.md                menampilkannya di sini, di terminal ini
#   md.sh -v README.md             membukanya di aplikasi (z2-view)
#   cat notes.md | md.sh           membaca dari input standar
#   md.sh -o page.html README.md   menulis HTML lalu berhenti (tidak ada yang dibuka)
#
# z2-run: md.sh README.md   (setelah terpasang, cukup namanya. Berjalan sekali)
#
# Opsi:
#   -v        membukanya di aplikasi (z2-view)
#   -o FILE   menulis HTML ke sana. Tidak ada yang ditampilkan di sini
#   -w N      membungkus di N kolom (bawaan: lebar layar, hingga 100)
#   -p        tanpa warna maupun hiasan (teks polos)
#   -t TITLE  judul layar baca (bawaan: judul pertama, jika tidak, nama file)
#   -h        bantuan ini
#
# Dokumen panjang, layar demi layar: md.sh README.md | less -R   (-R mempertahankan warna)
#
# Tidak ada yang perlu dipasang: sh dan awk sudah cukup.
# Judul, paragraf, daftar (bersarang, bernomor, [ ]/[x]), kutipan, kode, tabel,
# pemisah, tautan, gambar, dan front matter (---) ditangani. PERINGATAN judul setext
# (=== / ---) dan tautan rujukan ([teks][1]) keluar apa adanya.
#
# Yang bisa diharapkan:
#   - Gambar hanya muncul di tempat di tab aplikasi ini (digambar dengan grafik
#     kitty lewat z2-img). Lewat ssh Anda mendapat keterangan dan path-nya sebagai gantinya.
#   - Tautan bisa diketuk (OSC 8). Terminal tanpa fitur itu menampilkannya sebagai teks.
#   - Terminal ini tidak menggambar huruf miring, jadi *penekanan* diberi garis bawah (seperti man).
#   - Tabel yang terlalu lebar untuk layar dipecah menjadi baris "judul: nilai".
#   - -v menyematkan gambar yang ada di perangkat ini ke halaman (masing-masing paling besar 1,5 MB).
#     Layar baca tidak mengambil apa pun dari jaringan, jadi gambar http(s)
#     tetap berupa keterangan.
""",
            "macro.mdBody.cCollect" to "# Kumpulkan input (beberapa file menjadi satu bacaan, sesuai urutan yang diberikan).",
            "macro.cTitle" to "# Judul: -t, jika tidak, judul # pertama, jika tidak, nama file",
            "macro.cWidth" to "# Lebar layar",
            "macro.cColorEnv" to "# Jalan keluar saat warna harus (atau tidak boleh) melewati pipa, mis. ke pager",
            "macro.cImgWhere" to "# Gambar hanya bisa muncul di tempat di dalam tab (grafik kitty) dengan z2-img tersedia.",
            "macro.cAmbi" to "# Lebar yang dipakai untuk menghitung karakter ambigu (garis, panah, angka berlingkar). Pakai\n" +
            "# Z2_MD_AMBIWIDTH=2 jika Setelan menghitungnya sebagai lebar penuh. Bawaan 1, seperti aplikasi.",
            "macro.cAwkFile" to "# PERINGATAN program awk ditulis ke FILE dan diberikan dengan -f. Jika diberikan sebagai\n" +
            "#   argumen, gawk memotong program di tempat karakter multibyte (seperti\n" +
            "#   bahasa Jepang di catatan ini) melewati batas baca 4096 byte, dan mati dengan\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Menyematkan gambar ke halaman layar baca, sebagai URI data:.\n" +
            "# PERINGATAN z2-view tidak mengambil apa pun dari jaringan (JavaScript dan pemuatan dimatikan), jadi\n" +
            "#   gambar yang tidak disematkan sama sekali tidak muncul. 4 MB untuk seluruh halaman.",
            "macro.cImgLoop" to "# Hanya dokumen dengan gambar yang dibaca baris demi baris (untuk memanggil z2-img di tempat)",
            "macro.mdBody.mUsageHint" to "lihat md.sh -h",
            "macro.mdBody.mNoTmp" to "tidak bisa membuat folder sementara",
            "macro.mdBody.mUnreadable" to "file tidak terbaca:",
            "macro.mdBody.mEmpty" to "tidak ada yang dibaca",
            "macro.mWidthNum" to "-w menerima angka",
            "macro.mNoDir" to "tidak bisa membuat folder tujuannya",
            "macro.mConvFail" to "konversi gagal",
            "macro.mNoView" to "tidak ada z2-view di sini (aplikasi perlu diperbarui). HTML-nya ada di:",
            "macro.mImgLabel" to "[gambar]",
            "menu.noApps" to "(tidak ada aplikasi ditemukan)",
            "menu.cat.AudioVideo" to "Audio dan video",
            "menu.cat.Development" to "Pengembangan",
            "menu.cat.Graphics" to "Grafis",
            "menu.cat.Game" to "Permainan",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Perkantoran",
            "menu.cat.System" to "Sistem",
            "menu.cat.Utility" to "Utilitas",
            "menu.cat.Other" to "Lainnya",
            "menu.usage" to "penggunaan: z2menu [list]   (tanpa argumen: XML menu pipe openbox)",
            "reminderView.labels" to "Pengingat|Tambah|Hapus|Isi|Tanggal dan waktu|Pengulangan|Sekali|Setiap hari|Hari kerja|Setiap minggu (hari dipilih)|Teks jadwal (opsional; menggantikan tanggal dan pengulangan)|Terjadwal|Berulang|Sudah diberitahukan|Tidak ada pengingat|Hapus pengingat ini dan notifikasinya yang tertunda?",
            "rssFeeds.rssFeedsLabel" to "Langganan",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} tidak dimulai. Lihat /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: tidak ditemukan pengelola paket yang didukung (apk/apt-get/pacman).",
            "scan.mInstalling" to "z2scan: memasang",
            "scan.mInstallFail" to "z2scan: gagal memasang",
            "scan.mRemoteBlocked" to "z2scan: target jarak jauh ditolak. Periksa hanya localhost, atau berikan --allow-remote untuk target yang Anda izinkan diuji.",
            "scan.mRemoteWarn" to "z2scan: PERINGATAN memindai host jarak jauh. Pindai hanya sistem yang secara tegas Anda izinkan diuji.",
            "scan.mNoLynis" to "z2scan: lynis tidak ditemukan; beralih ke diagnosis mandiri bawaan (\"z2scan self\"). Pasang dengan \"z2scan setup\".",
            "scan.mNoCve" to "z2scan: tidak ditemukan pemindai CVE (trivy/grype). Pasang trivy atau grype, lalu coba lagi.",
            "scan.mSelfHead" to "== diagnosis mandiri z2scan (perangkat ini / localhost) ==",
            "scan.mUidRoot" to "berjalan sebagai uid 0 (catatan: proot/z2root memberikan root palsu)",
            "scan.mPubPort" to "port TCP mendengarkan di semua antarmuka (bukan hanya localhost), port",
            "scan.mSshdEmpty" to "sshd_config mengizinkan login dengan sandi kosong (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config mengaktifkan autentikasi sandi (PasswordAuthentication yes); kunci lebih aman",
            "scan.mSshdRoot" to "sshd_config mengizinkan login root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys punya izin terlalu longgar, mode",
            "scan.mSshDir" to "~/.ssh punya izin terlalu longgar, mode",
            "scan.mWorldWrite" to "ditemukan file yang bisa ditulis semua orang (hingga 20 ditampilkan):",
            "scan.mSuid" to "biner SUID ada (sebagai info; root palsu di bawah proot/z2root):",
            "scan.mPath" to "PATH berisi elemen kosong/\".\" (direktori saat ini di PATH berisiko)",
            "scan.mFound" to "temuan:",
            "scan.mClean" to "tidak ditemukan masalah yang jelas.",
            "scan.mSaved" to "z2scan: acuan disimpan:",
            "scan.mSaveFail" to "z2scan: tidak bisa menulis acuan:",
            "scan.mNoBase" to "z2scan: belum ada acuan. Jalankan sekali \"z2scan self --save\" untuk mencatat keadaan saat ini.",
            "scan.mNoChange" to "[ OK ] tidak ada perubahan sejak acuan.",
            "scan.mAdded" to "== baru sejak acuan ==",
            "scan.mRemoved" to "== hilang sejak acuan ==",
            "scan.mCleared" to "z2scan: acuan dihapus.",
            "scan.mLangDiff" to "z2scan: PERINGATAN acuan disimpan dalam bahasa lain, jadi semuanya akan tampak berubah. Simpan ulang dengan \"z2scan self --save\".",
            "scan.langTag" to "id",
            "scan.usageText" to """
        z2scan - uji kerentanan perangkat ini / localhost (defensif, tidak ada data yang keluar).

        Secara bawaan hanya memindai localhost. Pemindai dipasang dari paket resmi
        distro Anda (nmap/lynis/trivy); tidak ada yang disertakan dan hasilnya tetap lokal.

          z2scan self [--save]         diagnosis mandiri bawaan (tanpa alat eksternal): port terbuka,
                                       konfigurasi sshd, izin kunci SSH, bisa ditulis semua/SUID, PATH.
                                       --save juga menyimpan hasilnya sebagai acuan.
          z2scan diff [--quiet]        menjalankan ulang diagnosis mandiri dan hanya menampilkan yang berubah
                                       sejak acuan. Keluar dengan 1 saat ada yang baru,
                                       jadi langsung cocok dengan z2-when. --quiet tidak menampilkan
                                       apa pun saat tidak ada perubahan.
          z2scan baseline [clear]      menampilkan acuan yang tersimpan (atau menghapusnya)
          z2scan setup                 memasang pemindai (nmap, lynis) lewat apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       pemindaian TCP nmap. Target bawaan adalah 127.0.0.1.
                                       Target non-lokal memerlukan --allow-remote.
          z2scan host                  audit host lewat lynis (beralih ke "self" jika tidak ada)
          z2scan cve                   pemindaian CVE yang dikenal pada rootfs lewat trivy/grype jika ada

        Pindai hanya sistem yang secara tegas Anda izinkan diuji.
    """.trimIndent(),
            "translationMacro.description" to "Menerjemahkan teks dengan perintah yang dipasang terpisah.",
            "translationMacro.missing" to "Perintah trans belum terpasang di lingkungan lokal ini. Pasang sendiri alat terjemahan Anda, lalu coba lagi. Tidak ada yang diunduh otomatis.",
            "translationMacro.network" to "Teks yang diterjemahkan dikirim ke layanan eksternal yang dipakai alat Anda. Ketentuan layanan itu dan ketersediaan jaringan berlaku.",
            "translationSetup.setup" to "Buka terminal di lingkungan Linux lokal yang sama, jalankan sendiri perintah di bawah, lalu coba terjemahkan lagi.",
            "translationSetup.alpineRepo" to "Jika paket tidak ditemukan, aktifkan repositori community untuk versi Alpine yang sama di /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Jika paket tidak ditemukan di Ubuntu, aktifkan repositori universe-nya dan coba lagi.",
            "translationSetup.unknown" to "Distro tidak dikenali. Pilih perintah untuk lingkungan Linux lokal Anda:",
            "voice.head" to """# voice.sh — penerima "Perintah suara": ucapan Anda mengendalikan musik, volume, timer, pengingat, cuaca, dan lainnya.
#
# Persiapan (sekali):
#   sh ~/.z2term/macros/voice.sh setup    mendaftarkan 4 aturan (Perintah suara / Perintah suara (terkunci) / Timer suara / Hitung langkah)
#   Hitung langkah hanya menjaga penghitung langkah tetap berjalan untuk "berapa langkah hari ini"; tidak pernah menjalankan apa pun
#   lalu bicaralah lewat kartu "Perintah suara" di Setelan Cepat atau asisten digital (tekan lama tombol daya dll.)
#
# Dari terminal:
#   voice.sh words                 membuka tabel kata (~/.z2term/voice/words-<lang>.txt; tambah atau ubah frasa)
#   voice.sh try <frasa>           mencoba tanpa bicara (mis. voice.sh try lagu berikutnya)
#   voice.sh listen                membuka layar mendengarkan lalu bicaralah (seperti kartu)
#   voice.sh lang [bahasa]         bahasa tabel kata (voice.sh lang - menampilkan daftarnya)
#   voice.sh ai [off|codex|claude|agy]   memakai AI atau tidak (awalnya nonaktif)
#   voice.sh model [nama]          model AI (kosong = bawaan AI tersebut)
#   voice.sh setup / help
#
# AI awalnya nonaktif. Meski aktif, AI hanya ditanya jika Anda memulai dengan kata pemicu ("AI", "pertanyaan" ...).
# Cuaca hanya menanyakan wttr.in jika Anda mengucapkan "cuaca" (lokasi: weather_city= di ~/.z2term/voice/config).
# Kata Anda sendiri: "kata = perintah" di ~/.z2term/voice/commands.txt (diperiksa sebelum tabel kata)
# Nama untuk ditelepon: "nama nomor" di ~/.z2term/voice/contacts.txt / log: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — makro pemula. Bereaksi pada apa yang terjadi di perangkat.",
                "# Aplikasi (z2-when) yang menunggu, jadi ini tidak residen: berjalan sekali lalu berhenti.",
                "# Persiapan: Setelan -> \"Deteksi peristiwa sistem\" aktif",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (headset: aturan kedua dengan 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Yang terjadi masuk ke Z2_WHEN_EVENT (z2-when events menampilkan namanya).",
                "# Untuk mencoba secara manual:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT kosong. Untuk mencoba secara manual: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Pengisian daya dimulai\" ;;",
                "  power_disconnected) z2-toast \"Pengisian daya berhenti\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — memberi tahu saat baterai lemah, menyesuaikan dengan keadaan saat ini.",
                "# Memakai z2-state untuk tahu apakah layar menyala: toast jika ya, notifikasi jika tidak.",
                "# Persiapan: Setelan -> \"Deteksi peristiwa sistem\" aktif",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when meneruskan tingkatnya; jika tidak, z2-state, agar juga berfungsi secara manual.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Diam saja selama mengisi daya (baterai tidak benar-benar berkurang)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Baterai ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Baterai lemah\" \"Tersisa ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — membacakan baterai dan koneksi setiap pagi.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Alarm sistem yang membangunkannya, jadi juga terpicu saat Doze (bisa terlambat beberapa menit).",
                "# Tidak bergantung pada sakelar deteksi.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=seluler; fi",
                "z2-say \"Selamat pagi. Baterai ${d}{level} persen, jaringan ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — menyalin kode sekali pakai dari notifikasi, lalu menghapusnya setelah",
                "# TTL detik jika papan klip masih berisi nilai yang sama.",
                "# Persiapan: Setelan -> \"Deteksi notifikasi\" aktif + berikan akses notifikasi sistem",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ bisa menyembunyikan kode di notifikasi sensitif. Untuk jalur yang andal, pakai",
                "#    varian SMS (otp-sms.sh) — teks SMS tidak pernah disembunyikan.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — menyalin kode sekali pakai dari SMS masuk, lalu menghapusnya setelah",
                "# TTL detik jika papan klip masih berisi nilai yang sama.",
                "# Varian SMS dari otp-clip.sh. Berbeda dengan notifikasi, teks SMS tidak pernah",
                "# disembunyikan oleh perlindungan notifikasi sensitif (Android 15+) dan berfungsi meski terkunci.",
                "# Persiapan: Setelan -> \"Deteksi SMS\" aktif + berikan izin SMS sistem",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — menampilkan notifikasi untuk menyalin nomor dari notifikasi panggilan.",
                "# Tombol \"Salin\"-nya menaruh nomor di papan klip (kenapa menunggu ketukan: lihat di bawah).",
                "# Persiapan: Setelan -> \"Deteksi notifikasi\" aktif + berikan akses notifikasi sistem",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Untuk juga menangkap panggilan tak terjawab, daftarkan aturan kedua (skrip sama, kategori lain):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — memeriksa feed dan hanya menyimpan yang baru, sebagai notifikasi dan teks.",
                "# Berbeda dengan contoh lain, ini **tidak** residen: bentuknya",
                "# \"berjalan sekali dari pemicu waktu lalu berhenti\".",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — membuka yang dikumpulkan rss.sh, terbaru dulu, satu artikel per ketukan.",
                "# Muncul sebagai tombol makro di widget status, jadi setiap ketukan membuka yang berikutnya.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — mengingatkan Anda lewat notifikasi: sekali (dalam 30m, pukul 18:30) atau",
                "# berulang (setiap hari / hari kerja / setiap minggu). Terpicu meski aplikasi ditutup (alarm sistem",
                "# membangunkannya; tidak perlu sakelar deteksi). Tunda dari tombol notifikasi.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — mengubah teks atau file menjadi kode QR: digambar di sini, atau disimpan sebagai PNG.",
                "# Serahkan sesuatu ke perangkat lain tanpa mengetik ulang. Berjalan sekali, tidak residen.",
            ),
            "macro.md" to listOf(
                "# md.sh — membaca file Markdown yang diformat di sini, atau membukanya di aplikasi.",
                "# Judul, daftar, tabel, kode, tautan; gambar muncul di tempat. Berjalan sekali, tidak residen.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Satu feed atau kata per baris yang tidak boleh tenggelam (baris yang diawali # diabaikan).",
                "# Bagian dari URL atau bagian dari judul bisa dipakai. Setiap kecocokan mendapat notifikasinya sendiri.",
                "# mis.", "#example.org",
            ),
            "macro.usage" to listOf(
                "penggunaan: z2-macro <subperintah>",
                "  list                daftar contoh bawaan (ditandai baru / sama / beda)",
                "  install <name|all>  menyalin ke ~/.z2term/macros/ (tidak pernah menimpa; -f untuk memaksa)",
                "  remove <name>       menghapus salinan contoh bawaan Anda (berhenti jika berbeda; -f untuk memaksa)",
                "  diff <name>         perbedaan salinan Anda dengan yang bawaan (milik Anda di kiri)",
                "  show <name>         menampilkan skrip",
                "  run <name>          menjalankannya di sini (Ctrl-C untuk berhenti)",
                "  dir                 menampilkan lokasi makro",
                "  actions ...         makro aksi Android (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Langganan", "Tambah", "Artikel", "Sunting", "Hapus", "URL feed",
                "Hapus langganan ini? Artikel yang terkumpul tetap disimpan.",
                "Perubahan berlaku pada pemeriksaan berikutnya. Artikel yang terkumpul tetap disimpan.",
                "Tidak ada langganan. Pakai Tambah untuk memasukkan URL RSS atau Atom.",
                "Masukkan URL HTTP(S) tanpa spasi atau kredensial (paling banyak 2048 karakter).",
                "Langganan ini sudah ada.",
                "Langganan ini sudah berubah. Segarkan daftar dan coba lagi.",
                "Ada perubahan lain yang sedang disimpan. Coba lagi.",
                "Sebelumnya", "Berikutnya",
            ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols" to 4,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の Bahasa Indonesia 版。上書きしていない文言は英語で出る。 */
private class ApiMsgId(d: String) : Z2ApiMsg("id", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — memilih dokumen; menyalinnya ke HOME dan mencetak path-nya.\n# z2-file save FILE [MIME] — memilih tujuan dan menyimpan salinan.\n# Membatalkan dihitung sebagai gagal. Hingga 512 MiB; pilihan kedaluwarsa setelah 5 menit."

    override val usbHelp: String get() = """
        |# z2-usb list              … daftar perangkat USB yang terhubung ke ponsel
        |# z2-usb allow [nomor]     … meminta Android mengizinkan program Linux memakai perangkat
        |# Jalankan allow sekali setelah memasang perangkat. Izin berlaku sampai perangkat dicabut.
    """.trimMargin()

    override val usbUsage: String get() = "penggunaan: z2-usb list | z2-usb allow [nomor|path|VID:PID]"

    override val usbNoDevices: String get() = "Tidak ada perangkat USB yang terhubung ke ponsel."

    override val usbNeedSelector: String get() = "Lebih dari satu perangkat USB terhubung; berikan nomornya dari z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Setujui akses USB di layar Android: $path"

    override fun usbAlreadyAllowed(path: String): String = "Akses USB sudah diizinkan: $path"

    override fun usbNotFound(selector: String): String = "Perangkat USB tidak ditemukan: $selector"

    override val usbStateAllowed: String get() = "diizinkan"

    override val usbStateNeedsAllow: String get() = "perlu izin"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "judul" "teks"  /  z2-notify [-h] "teks"
        |#   -h / --high / --banner : menampilkannya sebagai banner (prioritas tinggi) di atas layar
        |#   -b <label>             : menambah tombol balasan (hingga 3). Menekannya menambahkan
        |#                            notify_action ke ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <nama>              : pengenal notifikasi ini (untuk membedakan balasan)
        |#   -c <teks>              : menambah tombol "Salin" yang menaruh <teks> di papan klip.
        |#                            Pakai ini sebagai ganti z2-clip set saat makro berjalan di
        |#                            latar belakang: Android 10+ hanya mengizinkan aplikasi di latar depan menulis ke
        |#                            papan klip, jadi z2-clip set tanpa ada orang di depan layar diabaikan.
    """.trimMargin()

    override val notifyUsage: String get() = "penggunaan: z2-notify [-h] [-n nama] [-c teks] [-b label]... <judul> [teks]"

    override val toastHelp: String get() = """
        |# z2-toast <pesan> … pesan singkat di bagian bawah layar (toast).
        |# Hilang sendiri dan tidak meninggalkan apa pun. Pakai z2-notify jika harus tetap ada,
        |# atau jika Anda ingin tombol untuk ditekan.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … membuka alat QR dengan kamera yang sudah memindai.
        |# Setelah pindai, sebuah tombol membuka isinya di aplikasinya (peramban, LINE dan aplikasi lain,
        |# telepon, email, SMS, Wi-Fi, kontak, kalender). Tidak ada yang terbuka sendiri.
        |# Taruh di kartu Setelan Cepat atau di panel tepi.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … membuat kode QR.
        |#   Tidak ada yang perlu dipasang: aplikasi yang mengodekan (pengode yang sama dengan layar QR).
        |#   Tanpa TEXT, membaca input standar. -o adalah tujuan PNG
        |#   (bawaan ~/.z2term/qr/qr.png) dan path-nya dicetak. -t menggambarnya di sini sebagai gantinya,
        |#   dengan karakter blok. -p adalah ukuran sasaran dalam piksel (bawaan 600),
        |#   -m zona sunyi dalam modul (bawaan 4). Hingga 2000 byte.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <teks> … menyerahkan teks ke menu bagikan Android (mengirimnya ke aplikasi lain).
        |# --file FILE [FILE ...] : mengirim isi file (hingga 32 file, total 512 MiB).
        |# Path dibaca di shell lokal saat ini. -- memaksa mode teks. Salinan yang lebih dari 24 jam dibersihkan saat berbagi berikutnya.
        |# Semua argumen menjadi satu teks. Aplikasi tujuan dipilih di layar,
        |# jadi perlu ada orang — ini bukan untuk makro yang berjalan sendiri.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [judul] … membaca di z2term halaman yang dibuat di sini.
        |# Tanpa server maupun peramban: file diserahkan ke aplikasi dan ditampilkan apa adanya.
        |# JavaScript dan pemuatan jaringan dimatikan, jadi tidak ada isi halaman yang bisa berjalan atau menghubungi luar.
        |# Tautan http:// dan https:// terbuka di peramban biasa Anda.
        |# Tema ponsel masuk sebagai variabel CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), jadi halaman yang ditulis dengannya mengikuti warna terminal.
        |# Hingga 4 MB. Gambar harus disematkan (URI data:); gambar jarak jauh tidak dimuat.
        |# Pakai --edge PANEL:ITEM untuk mengirim ke item type=view yang ada di panel tepi.
        |# Pakai --controls FILE.json untuk formulir dan aksi umum yang ditangani makro terpasang.
        |# Item tampilan disegarkan manual secara bawaan. view-refresh=auto menjalankan run= saat dibuka; z2-view kembali ke item itu.
        |# Pakai - atau pipa (ls -l | z2-view) untuk membaca input standar. Teks yang tidak diawali < ditampilkan
        |# terbungkus dengan font monospace, kode warna dibuang; --text / --html memilih secara eksplisit.
        |# Mencubit mengatur teks; --pinch page memperbesar seluruh halaman sebagai gantinya. --text-size 50..300 menetapkan
        |# ukuran teks (persen) halaman ini. Keduanya berlaku untuk layar penuh; item panel punya setelannya sendiri.
        |# --no-refresh / --no-find menyembunyikan tombol Segarkan / Cari di layar penuh (ditampilkan secara bawaan).
    """.trimMargin()

    override val viewUsage: String get() = "penggunaan: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <file|-> [judul]"

    override val viewUnreadable: String get() = "z2-view: file tidak terbaca:"

    override val openHelp: String get() = """
        |# z2-open <url|path> … membukanya dengan aplikasi bawaan (https://… atau /sdcard/…).
        |# Aplikasi yang membuka dipilih Android; z2term hanya meneruskan.
        |# Path adalah path di **ponsel** (/sdcard/…), bukan di dalam distro.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <file|->… … menggambar gambar di terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Berikan - untuk membaca gambar dari stdin.
        |#   -w <cols>  lebar dalam kolom terminal (bawaan: muat di terminal)
        |#   -r <rows>  tinggi dalam baris terminal (dengan -w, ukuran dipakai apa adanya)
        |#   --clear    menghapus semua gambar yang sudah digambar
        |#   -f         mengirim meski stdout bukan terminal
        |# ⚠ Gambar hanya muncul di tab z2term, atau di terminal yang mendukung protokol
        |#   grafik kitty. Di tempat lain (lewat ssh, di pager) Anda mendapat karakter acak.
        |# ⚠ Proporsinya mengasumsikan sel dua kali lebih tinggi daripada lebarnya. Jika gambar
        |#   tampak gepeng, sesuaikan: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (lebih kecil = lebih tinggi).
    """.trimMargin()

    override val imgUsage: String get() = "penggunaan: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->..."

    override val imgNoFile: String get() = "tidak bisa membaca file:"

    override val imgNotTty: String get() = "stdout bukan terminal, jadi tidak ada yang dikirim (-f tetap mengirimnya)."

    override val imgNoSize: String get() = "tidak bisa membaca ukuran piksel; menggambar sebagai persegi:"

    override val qrUsage: String get() = "penggunaan: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: tidak bisa membuat kode (teksnya terlalu panjang? hingga 2000 byte)"

    override val clipHelp: String get() = """
        |# z2-clip get        … mencetak papan klip ke stdout
        |# z2-clip set [text] … menaruh teks (atau stdin jika dihilangkan) di papan klip
        |# ⚠ Android 10+ hanya mengizinkan aplikasi di latar depan (atau metode input yang dipakai) menyentuh
        |#   papan klip. Dari makro di latar belakang, ini diabaikan tanpa pesan,
        |#   jadi pakai z2-notify -c <teks>: ini menambah tombol "Salin" yang selalu berfungsi.
    """.trimMargin()

    override val batteryHelp: String get() = "# Mencetak tingkat / status pengisian sebagai JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (bawaan 200ms)"

    override val editHelp: String get() = "# z2-edit <file>      … menyunting file teks di editor z2term (kotak teks sederhana; Simpan menulisnya ulang)"

    override val sayHelp: String get() = "# z2-say <teks>       … berbicara dengan TTS perangkat (membaca stdin tanpa argumen)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (bawaan toggle). status membaca keadaan Android, termasuk perubahan dari luar (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … menangkap hanya bagian yang dilingkari (bawaan free). Memerlukan Android 11 dan z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (bawaan playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Volume media. Mencetak nilai saat ini/maks yang didapat."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (bawaan light). Membaca satu nilai dan mengembalikan JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Meluncurkan Intent Android apa pun (bawaan startActivity). Argumen pertama yang bukan opsi adalah ACTION.
        |# Jendela bebas secara bawaan memakai ulang tugas dan batas sistem. --reuse-task / --os-bounds tetap diterima.
        |# --bounds-only meminta batas 80% tanpa kunci mode; tidak bisa digabung dengan --os-bounds.
        |# --no-scale menonaktifkan permintaan jendela bebas yang diperkecil dari Motorola untuk peluncuran ini.
        |# -p PKG saja meluncurkan aplikasi itu. --window full|freeform|split|ask memilih permintaan peluncuran (perlu dukungan sistem).
        |# --profile N membuka aplikasi (-p/-n) di profil lain, seperti profil kerja. Tanpa layar terpisah. N ada di perintah yang disimpan panel tepi untuk aplikasi itu.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … keadaan perangkat saat ini, lengkap, sebagai JSON
        |# z2-state <kunci>    … hanya nilai itu, mentah (langsung masuk ke uji)
        |# kunci: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# mis. [ "${d}(z2-state charging)" = "true" ] && echo sedang mengisi daya
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <pertanyaan>
        |#   Mengajukan pertanyaan kepada orang dan mencetak jawabannya ke stdout.
        |#   Pertanyaan datang sebagai notifikasi dengan **kolom balasan**, jadi
        |#   bisa dijawab dari panel notifikasi tanpa membuka aplikasi (makro di
        |#   latar belakang juga bisa bertanya).
        |#   -t <dtk>   berapa lama menunggu jawaban (bawaan 300 = 5 menit)
        |#   -H <hint>  label yang ditampilkan di kolom balasan
        |#   -d <teks>  jawaban yang disarankan (ditampilkan di notifikasi)
        |# Menutup notifikasi tanpa menjawab, atau melewati waktu, keluar dengan kode bukan nol
        |# dan tidak mencetak apa pun — jadi Anda bisa menulis "atau menyerah":
        |#   name=${d}(z2-ask "Nama branch?") || exit 1
        |# Bandingkan dengan z2-notify -b <label>, yang hanya menawarkan pilihan yang Anda siapkan.
    """.trimMargin()

    override val askUsage: String get() = "penggunaan: z2-ask [-t dtk] [-H petunjuk] [-d bawaan] <pertanyaan>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Mencetak ucapan Anda ke stdout. Pengenalan berjalan **hanya di perangkat**; suara Anda
        |#   tidak pernah dikirim ke jaringan. Memerlukan Android 12+ dan pengenalan suara di perangkat
        |#   (untuk bahasa Anda, data suara offline-nya).
        |#   Bicaralah dengan z2term di layar (Android hanya memberi mikrofon ke aplikasi di layar).
        |#   Berhenti saat Anda diam. Pertama kali, akses mikrofon akan diminta.
        |#   -w <kata>  hanya menerima kata-kata ini (dipisahkan koma) dan mencetak yang terdengar.
        |#               Semua hipotesis pengenal diperiksa, bukan hanya yang terbaik.
        |#   -l <bahasa> bahasa yang dikenali (mis. id-ID, en-US; bawaan: bahasa perangkat)
        |# Keluar: 0 = selesai / 1 = tidak terdengar apa pun, atau bukan salah satu kata / 2 = tidak tersedia (alasan di stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "penggunaan: z2-listen [-w kata,kata,...] [-l bahasa]"

    override val listenPrompt: String get() = "Silakan bicara… (berhenti saat Anda diam)"

    override val listenNeedsAndroid12: String get() = "z2-listen memerlukan Android 12 atau lebih baru (sebelumnya pengenalan tidak bisa tetap di perangkat)."

    override val listenBusy: String get() = "Sudah mendengarkan (z2-listen lain sedang berjalan)."

    override val listenEngineBusy: String get() = "Pengenalan suara perangkat sedang sibuk (mungkin dipakai aplikasi lain). Coba lagi nanti."

    override val listenEngineStuck: String get() = "Pengenalan suara tidak merespons. Jika terus terjadi, paksa berhenti aplikasi pengenalan perangkat (seperti Android System Intelligence) di Setelan Android › Aplikasi."

    override val listenStopped: String get() = "Berhenti mendengarkan."

    override val listenNoOnDevice: String get() = "Perangkat ini tidak punya pengenalan suara bawaan. z2-listen tidak pernah mengirim suara Anda ke jaringan, jadi tidak bisa berjalan di sini."

    override val listenNoPermission: String get() = "Akses mikrofon tidak diizinkan. Jalankan lagi z2-listen dengan z2term di layar agar izin diminta, atau izinkan di setelan Android."

    override val listenNotShown: String get() = "Tampilkan z2term di layar dan coba lagi (Android hanya memberi mikrofon ke aplikasi di layar)."

    override val listenNoMic: String get() = "Tidak bisa memakai mikrofon (z2term meninggalkan layar, atau aplikasi lain sedang merekam)."

    override val listenNoLanguage: String get() = "Data suara bahasa ini di perangkat belum terpasang. Unduh di setelan input suara perangkat (pengenalan suara offline)."

    override val listenNoSpeech: String get() = "Tidak terdengar apa pun."

    override val listenTimedOut: String get() = "Waktu habis."

    override fun listenNoMatch(heard: String): String = "Bukan salah satu kata (terdengar: $heard)"

    override fun listenFailed(code: Int): String = "Pengenalan suara gagal (galat $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … mencegah layar mati sendiri selama waktu itu
        |# z2-screen keepon off          … langsung mengembalikan, tanpa menunggu waktunya habis
        |# z2-screen status              … keadaan saat ini sebagai JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Ini mengubah setelan "waktu layar mati" seluruh sistem, jadi tetap berlaku meski aplikasi
        |# di latar belakang. Ini BUKAN penjaga layar di bilah alat, yang hanya bertahan selama
        |# aplikasi di layar — yang itu tidak disentuh.
        |# Nilai asli disimpan dan selalu ditulis kembali saat waktunya habis (meski aplikasi
        |# dihentikan atau perangkat dimulai ulang). Paling lama 24 jam sekaligus.
        |# Memerlukan "ubah setelan sistem" (Setelan > waktu layar mati > izinkan).
        |# mis. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "penggunaan: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | perintah...> [--off <perintah...>] [-l <label>] [-i <gambar>]
        |#                           … menaruh sesuatu di kartu Setelan Cepat 1-$tiles
        |# z2-tile add <1-$tiles>         … meminta untuk menempatkan slot ini di panel (Android 13+)
        |# z2-tile list              … $tiles slot sebagai TSV (slot / label / perintah; "-" = kosong)
        |# z2-tile clear <1-$tiles|all>   … mengosongkan slot
        |#
        |# Yang Anda tetapkan
        |#   Bisa **nama file makro** di ~/.z2term/macros/, atau **perintah** yang dijalankan persis
        |#   seperti diketik — jenisnya disimpulkan dari nama.
        |#   Makro bisa menerima argumen ("remind.sh ask"): kata pertama menentukan apakah itu
        |#   makro, sisanya diteruskan kepadanya. Dua slot dengan makro yang sama punya
        |#   label yang sama, jadi beri keduanya -l.
        |#   Perintah berjalan dengan Z2_TILE=<slot> (dan Z2_TILE_MACRO untuk makro) di lingkungan.
        |#   ⚠ Nama berakhiran .sh yang tidak ada di ~/.z2term/macros/ ditolak di sini — sebagai
        |#     perintah ia akan dicari di PATH (yang tidak memuat folder makro),
        |#     jadi kartu tidak akan melakukan apa pun dan hanya mengatakannya di tile/run.log.
        |#
        |# Yang dilakukan ketukan
        |#   Satu ketukan memulai, ketukan lain menghentikan (seperti tombol widget).
        |#   Kartu tampak "aktif" selama berjalan (warnanya aksen sistem, bukan milik kami).
        |#   Dengan --off Anda punya **dua perintah**: ketukan bergantian di antaranya dan kartu
        |#   tampak "aktif" selama menyala. Pakai saat mematikan adalah perintah terpisah
        |#   (z2-torch on / off).
        |#   Pasangan biasa mengingat pergantian yang berhasil. Penetapan langsung z2-torch mengikuti
        |#   yang diamati Android, termasuk perubahan dari luar. z2-screen keepon mengikuti setelan yang benar-benar aktif.
        |#
        |# Menempatkan di panel
        |#   Di Android 13+, `set` langsung bertanya apakah kartu dimasukkan ke panel, dan
        |#   dialog itu memuat **nama dan ikon yang baru Anda tetapkan**. Jika Anda menolak, tidak ada yang ditempatkan.
        |#   Ada tepat $tiles slot: jumlahnya ditetapkan di manifes dan tidak bisa bertambah
        |#   saat berjalan. Slot tanpa penetapan sama sekali tidak muncul di layar sunting,
        |#   jadi memiliki $tiles slot tidak merugikan apa pun.
        |#   ⚠ Ini hanya muncul selama z2term di latar depan, dan satu per satu — makro
        |#     yang menetapkan dua slot berturut-turut hanya akan bertanya untuk yang pertama. Pakai
        |#     z2-tile add <slot> setelahnya untuk sisanya.
        |#   ⚠ Menempatkan kartu tetap keputusan Anda — Android tidak mengizinkan aplikasi menaruh kartunya
        |#     sendiri di sana. Dari layar sunting (pensil) panel Setelan Cepat, cari
        |#     **z2term <slot>**.
        |#   ⚠ Daftar itu menampilkan nama dan ikon dari manifes, bukan yang ditetapkan (Android tidak
        |#     punya cara mengubahnya saat berjalan), jadi z2-tile list memberi tahu nomor mana yang mana.
        |#
        |# Gambar (-i)
        |#   Slot otomatis mendapat ikon yang cocok saat namanya mengisyaratkannya (remind.sh -> sebuah
        |#   jam), dan z2-icon menggantinya dengan apa pun yang Anda mau — setelah itu tidak disentuh lagi.
        |#   -i menetapkan gambar **sebelum** dialog itu muncul, jadi satu baris menentukan
        |#   nama dan ikon sekaligus (z2-icon sample menampilkan nama yang tersedia).
        |#   ⚠ Nama yang tidak dikenal ditolak **sebelum penetapan apa pun** — slot yang sudah diatur tetapi
        |#     bergambar salah lebih sulit disadari daripada yang tidak pernah diatur.
        |#
        |# mis. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "layar tetap nyala"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l senter
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin()

    override val tileUsage: String get() = "penggunaan: z2-tile set <1-$tiles> <macro.sh|perintah...> [--off <perintah...>] [-l label] " +
                "[-i gambar] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: gambar ini tidak ada: $name (lihat daftarnya dengan z2-icon sample). Tidak ada yang ditetapkan."

    override fun tileAddEmpty(n: Int): String = "z2-tile: slot $n belum berisi apa pun (tetapkan dulu: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: tidak bisa meminta penambahan kartu di sini (memerlukan Android 13, dengan z2term di latar depan). " +
                "Slot sudah ditetapkan — tempatkan dari layar sunting (pensil) panel Setelan Cepat."

    override val tileAddAsked: String get() = "Android sudah diminta menambahkan kartu — jawab dialognya. " +
                "(Tidak muncul apa pun? Tempatkan dari layar sunting sebagai gantinya.)"

    override val iconHelp: String get() = """
        |# Mengganti ikon bilah status dan kartu Setelan Cepat dengan gambar piksel Anda sendiri.
        |#
        |# Mulai di sini
        |#   z2-icon pick 1     ... memilih gambar dari daftar dan menaruhnya di kartu 1
        |#   z2-icon list       ... isi setiap slot saat ini ("-p" juga menampilkan gambarnya)
        |#
        |# Arti <target>
        |#   notify    ikon setiap notifikasi aplikasi ini (yang ada di bilah status)
        |#   1-$tiles      slot kartu Setelan Cepat - nomor yang sama dengan z2-tile
        |#
        |# Menaruh gambar
        |#   pick <target>            tampilkan daftarnya dan pilih dengan nomor (paling mudah)
        |#   sample <target> <nama>   saat Anda sudah tahu namanya
        |#   edit <target>            menggambar, atau memperbaiki yang sekarang, di ${d}EDITOR
        |#   set <target> <file>      membacanya dari file ("-" untuk stdin)
        |#   auto <1-$tiles|all>          memilih lagi berdasarkan apa yang dijalankan kartu
        |#   clear <target|all>       kembali ke ikon bawaan
        |#
        |# Kehalusan kisi
        |#   grid                     ukuran untuk gambar baru
        |#   grid <$grids>          menetapkannya ($grid jika tidak Anda ubah)
        |#   scale <target> <$grids>  memindahkan gambar <target> ke kisi itu
        |#                            (sambil membagi dua anak tangga diagonal). **Ini memberi
        |#                            ruang untuk menggambar lebih halus** - ikon yang dihasilkan
        |#                            tetap dihaluskan, jadi tidak perlu melakukan ini untuk itu.
        |#
        |# Menyimpan gambar Anda di daftar
        |#   save <target> <nama>     memberi nama isi <target> dan menambahkannya ke daftar
        |#   sample                   daftar pilihan (nomor / nama /
        |#                            "builtin" = bawaan, "mine" = disimpan oleh Anda)
        |#   sample <nama>            menampilkan gambar itu
        |#   forget <nama>            menghapus salah satu milik Anda dari daftar (yang sudah Anda
        |#                            taruh di target tetap ada)
        |#
        |# Cara menggambar
        |#   Ini kisi karakter. "." "" "0" "-" "_" membiarkan titik kosong dan karakter lain
        |#   mengisinya, jadi pakai yang paling mudah Anda lihat. Ruang kosong di sekitar
        |#   gambar diabaikan - gambar diletakkan di tengah, tidak perlu mengisi setiap baris
        |#   dengan tepat. Kisinya $grids ("z2-icon grid").
        |#   ⚠ Kisi yang lebih besar tidak membuat ikon lebih besar. Isi kisinya, jika tidak gambar
        |#     keluar lebih kecil daripada yang digantikan.
        |#
        |# Perlu diketahui
        |#   ⚠ Hanya bentuknya yang dipakai. Android mewarnai ikon ini dengan satu warnanya sendiri
        |#     (kartu berganti warna antara aktif dan nonaktif), jadi tidak ada warna yang bisa dipilih.
        |#   ⚠ **Tepinya dihaluskan saat keluar.** Gambar yang dibuat pada $grid titik
        |#     tetap tampak halus di kartu (digambar jauh lebih besar daripada bilah status),
        |#     jadi kisi bukan masalah sehari-hari. 48 / 64 berguna saat **Anda**
        |#     ingin menggambar lebih halus. "show" menampilkan gambar seperti Anda membuatnya; kartunya lebih halus.
        |#   ⚠ Kartu otomatis mendapat gambar saat namanya mengisyaratkannya ("z2-tile set 1
        |#     remind.sh" menaruh jam). Yang Anda atur di sini diutamakan dan tidak pernah disentuh lagi
        |#     — "z2-icon auto 1" mengembalikan slot itu ke pilihan otomatis.
        |#   ⚠ Dua ikon tidak bisa diubah: ikon di layar **sunting** Setelan Cepat (tempat
        |#     Anda menyeret kartu) dan ikon peluncur. Android menetapkannya saat pemasangan.
        |#
        |# mis. z2-icon pick 1               memilih gambar untuk slot 1
        |#      z2-icon edit 1              menggambarnya ulang sendiri
        |#      z2-icon save 1 my-face      memberi nama gambar itu dan menambahkannya ke daftar
        |#      z2-icon sample 3 my-face    menaruh gambar yang sama di slot 3
        |#      z2-icon grid 64             menggambar yang baru di kisi 64x64 mulai sekarang
        |#      z2-icon scale 1 48          memindahkan slot 1 ke kisi 48x48 untuk menggambar lebih halus
        |#      z2-icon list -p             memeriksa apa ada di mana, beserta gambarnya
        |#      z2-icon clear notify        mengembalikan ikon notifikasi
    """.trimMargin()

    override val iconUsage: String get() = "penggunaan: z2-icon pick <notify|1-$tiles> | sample [nama|target nama] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [file|-] | " +
                "save <notify|1-$tiles> <nama> | forget <nama> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "file tidak ditemukan:"

    override val iconEditUnchanged: String get() = "tidak berubah."

    override val iconPickPrompt: String get() = "nomor (atau nama), kosong untuk batal: "

    override val iconPickCancelled: String get() = "dibatalkan."

    override val iconNoEditor: String get() = "tidak ditemukan editor. Atur ${d}EDITOR, atau pakai: z2-icon set <target> <file>"

    override val tileNoSuchMacro: String get() = "makro ini tidak ada di ~/.z2term/macros/ (pakai path lengkap untuk menjalankannya sebagai perintah):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [paket] / trace dump / trace stop
        |# Jejak: aktifkan dulu penangkapan; 5 menit, 128 peristiwa terakhir, hanya di memori.
        |# Jenis kolom dan panjang UTF-16, tidak pernah nilai teksnya. dump/stop mengembalikan JSON; start mengatur ulang.
        |# z2-noti list  … notifikasi yang sedang ditampilkan, sebagai TSV
        |#                 (kunci / paket / nama aplikasi / judul / isi)
        |# Hanya baca. Sengaja tidak ada cara menekan atau menutup notifikasi:
        |# itu juga akan menekan tombol bayar dan kirim di aplikasi lain.
        |# Memerlukan akses notifikasi (Setelan > Izin dan notifikasi).
        |# Lihat juga: z2-when notify:otp / notify:pkg=<bagian> / notify:contains=<bagian>
    """.trimMargin()

    override val notiUsage: String get() = "penggunaan: z2-noti list | trace start [paket] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [nama]     … sekali pada HH:MM berikutnya (besok jika sudah lewat)
        |# z2-alarm daily HH:MM [nama]  … setiap hari pada HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [nama] … sekali, dalam N detik/menit/jam
        |# z2-alarm list                … daftar yang dijadwalkan (JSON; "exact" menunjukkan ketepatan waktu)
        |# z2-alarm cancel <id|nama|all> … membatalkan
        |# Saat terpicu, satu baris {"event":"alarm","name":…} ditambahkan ke ~/.z2term/events.jsonl.
        |# Ini membangunkan perangkat meski dalam Doze. "exact":true berarti terpicu tepat pada menitnya;
        |# "exact":false berarti Doze hanya memberi celah setiap 9-15 menit, jadi ponsel yang dibiarkan
        |# dengan layar mati bisa terlambat sebanyak itu. Menonaktifkan optimasi baterai untuk aplikasi ini
        |# mengubahnya menjadi true (tidak ada izin tambahan yang diminta).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: tidak ada perintah date yang bisa dipakai"

    override val sessionHelp: String get() = """
        |# z2-session list                     … daftar tab (indeks / id / jenis / tanda / nama, TSV)
        |#   tanda: * = di layar / ! = berjalan / ? = belum dimulai / @ = terhubung dari shell / - = lainnya
        |# z2-session new [nama]               … membuka tab terminal (mencetak indeks dan id)
        |# z2-session send <tab> <teks>...     … mengetik teks di tab itu (tanpa menjalankannya)
        |# z2-session send <tab> <teks> --enter … mengetiknya, lalu menjalankannya
        |# z2-session key <tab> <tombol>...    … mengirim **tombol** ke tab itu (C-c / M-x / F5 / Up …)
        |#   pengubah: C- (Ctrl) dan M- (Meta=Alt); bisa digabung, seperti C-M-a
        |#   khusus: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Tombol dengan Shift seperti C-S-a ditolak (terminal tidak membedakan Shift: byte-nya sama dengan C-a)
        |# z2-session key <tab> --raw '\x1b[A' … yang lainnya, dalam byte (\xHH \e \n \r \t \0)
        |# z2-session capture [tab] [--all]    … mengambil layar tab itu (--all termasuk riwayat)
        |# z2-session attach <tab>             … tetap terhubung ke tab itu dan cukup mengetik di sana
        |#   keluar dengan Ctrl+] (kapan saja), atau dengan ~. di awal baris (seperti di ssh)
        |#   ⚠ lewat SSH hanya Ctrl+] yang berfungsi: ssh di depan Anda menelan ~. dan memutus sesi SSH
        |#   tulis ~~ untuk ~ harfiah di awal baris, ~ lalu Ctrl+] untuk Ctrl+] harfiah
        |#   selama terhubung, tab mengikuti ukuran jendela ANDA; kembali saat Anda keluar
        |# z2-session close <tab>              … menutup tab itu (tidak pernah yang terakhir)
        |#
        |# <tab> bisa indeks dari list, id, atau nama tab. "." atau dihilangkan = tab di layar.
        |# mis. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "penggunaan: z2-session list | new [nama] | send <tab> <teks>... [--enter] | " +
                "key <tab> <tombol>... | key <tab> --raw <byte> | capture [tab] [--all] | " +
                "attach <tab> | close <tab>"

    override val keyNothing: String get() = "z2-session key: tidak ada tombol yang diberikan"

    override val keyRawEmpty: String get() = "z2-session key: --raw memerlukan byte yang akan dikirim"

    override val keyUnknown: String get() = "z2-session key: tombol tidak dikenal (lihat daftarnya di \"z2-session -h\"):"

    override val keyBadEscape: String get() = "z2-session key: tidak bisa membaca escape:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: terminal tidak membedakan Shift, jadi '$asWritten' akan menghasilkan " +
            "byte yang persis sama dengan '$equivalentTo'. Tulis '$equivalentTo' jika itu yang Anda maksud."

    override fun attachNoSuchTab(target: String): String = "tab itu tidak ada: '$target' (jalankan \"z2-session list\" untuk melihat tab)"

    override val attachNotTerminal: String get() = "itu tab GUI, tanpa shell untuk dihubungkan. Pilih tab terminal."

    override val attachNotStarted: String get() = "tab itu belum dimulai (bertanda \"?\" di list). Buka sekali di aplikasi, lalu hubungkan."

    override val attachExited: String get() = "tab itu sudah berakhir. Layarnya masih bisa dibaca dengan \"z2-session capture\"."

    override val attachSelf: String get() = "itu tab tempat Anda mengetik. Menghubungkan tab ke dirinya sendiri membuat keluarannya kembali " +
            "sebagai input selamanya, jadi tidak bisa kembali. Pilih tab lain."

    override val attachLoop: String get() = "tab itu sudah terhubung balik ke tab ini, jadi keduanya akan saling mengisi " +
            "tanpa akhir. Putuskan salah satunya dulu (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … server terdaftar (indeks / id / status / tanda / nama, TSV)
        |#   tanda: * = aktif / - = nonaktif
        |# z2-server start <server>      … menjalankannya sebagai server residen (perangkat tetap bisa dijangkau)
        |# z2-server stop <server>       … menghentikan yang ini (yang lain tetap berjalan)
        |# z2-server status [<server>]   … status, pid, mulai ulang, dan kode keluar terakhir
        |#
        |# <server> bisa indeks dari list, id, atau nama yang Anda berikan di aplikasi.
        |# Hanya server yang terdaftar di aplikasi yang bisa dijalankan; ini tidak pernah mendaftarkan yang baru.
        |# Kenapa ini ada: daemon yang dijalankan langsung oleh aturan berjalan DI LUAR kerangka server
        |# residen (tanpa WakeLock, tanpa WifiLock, tanpa layanan latar depan), jadi berhenti merespons setelah
        |# layar mati. Menjalankannya lewat sini memasukkannya ke kerangka itu.
        |# mis. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "penggunaan: z2-server list | start <server> | stop <server> | status [<server>]"

    override val serverNotFound: String get() = "z2-server: server itu tidak ada (lihat \"z2-server list\"):"

    override val serverAmbiguous: String get() = "z2-server: nama itu cocok dengan beberapa server; pakai id dari \"z2-server list\":"

    override val serverNone: String get() = "z2-server: belum ada server terdaftar (tambahkan di aplikasi: 📜 -> Server)."

    override val serverLowPowerWarn: String get() = "z2-server: catatan - mode hemat daya aktif, jadi WakeLock/WifiLock tidak ditahan. " +
            "Server bisa berhenti merespons saat layar mati " +
            "(Setelan -> Otomatisasi -> Perlindungan proses latar belakang)."

    override val whenHelp: String get() = """
        |# z2-when <pemicu> run <cmd...>         … mendaftarkan aturan
        |#   pemicu: charge:start | charge:stop  (perlu deteksi aktif)
        |#            battery:below=N | battery:above=N  (perlu deteksi aktif)
        |#            battery:temp>N | battery:temp<N  … suhu baterai (°C) melewati N (perlu deteksi aktif)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='menit jam tanggal bulan hari'  (hari 0-7 / 0,7=Minggu. Pakai tanda kutip: ada spasi)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<nama>  (perlu deteksi aktif)
        |#            net:online | net:offline  … koneksi yang bisa dipakai muncul / hilang
        |#            net:wifi | net:mobile | net:ethernet  … koneksi yang dipakai beralih ke yang ini
        |#                                        (perlu deteksi aktif; menghitung data seluler, bukan hanya Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<bagian> | share:ext=<ext>
        |#                                        … sesuatu dibagikan ke z2term dari aplikasi lain
        |#            boot                        … perangkat selesai menyala (berfungsi meski deteksi nonaktif)
        |#            sms:any | sms:from=<bagian> | sms:contains=<bagian> | sms:otp  (perlu RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (deteksi aktif)
        |#            file:new=<folder>[,ext=<ext>]  … file baru masuk ke folder ini (perlu deteksi aktif)
        |#            file:changed=<folder>[,ext=<ext>] | file:deleted=<folder>[,ext=<ext>]
        |#                                        … file di sana ditulis ulang / hilang (perlu deteksi aktif)
        |#            bt:connect | bt:disconnect | bt:connect=<nama> | bt:disconnect=<nama>
        |#                                        … perangkat Bluetooth terhubung / terputus (nama: sebagian, atau alamatnya)
        |#                                        (deteksi aktif dan, di Android 12+, izin Perangkat sekitar)
        |#            app:open=<aplikasi> | app:close=<aplikasi>  … aplikasi ini muncul di depan / Anda meninggalkannya (paket atau nama, sebagian)
        |#                                        (perlu z2term aktif di Aksesibilitas; berfungsi meski deteksi nonaktif)
        |#            steps:reach=N               … langkah hari ini mencapai N (deteksi aktif + izin Aktivitas fisik)
        |#            notify:any | notify:otp | notify:pkg=<bagian> | notify:title=<bagian> | notify:contains=<bagian>
        |#                                        … notifikasi masuk (perlu akses notifikasi)
        |#            event:<nama> | event:<awalan>* | event:*  … peristiwa perangkat apa pun, berdasarkan nama
        |#              (z2-when events menampilkan daftarnya; nama yang sama dengan di events.jsonl)
        |# Nama (opsional, tepat setelah pemicu — sebelum run):
        |#   name=<teks>             … kegunaan aturan ini, ditampilkan di tab Otomatisasi
        |#                             (pakai tanda kutip jika ada spasi). Kosong = pemicu yang ditampilkan.
        |#                             Hanya untuk tampilan: tidak mengubah kapan aturan berjalan.
        |# Filter (pemicu apa pun, tepat setelahnya — sebelum run):
        |#   if=<kond>[,<kond>...]   … hanya saat perangkat dalam keadaan ini (DAN; ! membalik)
        |#                             kuncinya sama dengan yang dicetak z2-state: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             mis. if=wifi,!screen / if=ssid=Rumah / if=level<30
        |#   if_any=<kond>[,<kond>...] … berjalan saat **salah satunya** benar (koma berarti ATAU)
        |#                             dengan if=: "semua di if DAN salah satu di if_any"
        |#   else=<cmd>              … menjalankan ini **sebagai gantinya**, saat if / if_any tidak terpenuhi
        |#                             ⚠ hanya if / if_any yang mengarah ke sini. Eksekusi yang dilewati karena between /
        |#                             days / cooldown tidak menjalankan apa pun — aturan yang dimatikan
        |#                             pada malam hari harus tetap diam pada malam hari, termasuk else.
        |#   cooldown=30m            … jangan jalankan lagi dalam selang ini (10s / 30m / 2h)
        |#   between=22:00-07:00     … hanya dalam rentang ini (melewati tengah malam)
        |#   days=mon-fri            … hanya pada hari-hari ini (nama atau angka cron 0-7, 0/7 = Minggu)
        |#   answer='<pertanyaan>=><jawaban>' … saat perintah menanyakan ini di terminal, ketik jawabannya
        |#                             dan Enter (bisa berulang; mis. answer='[y/N]=>y'). Dicocokkan dengan akhir
        |#                             baris keluaran terakhir.
        |#   secret='<pertanyaan>'   … sama untuk jawaban tersembunyi (sandi). Isinya dimasukkan di
        |#                             editor aturan aplikasi dan disimpan terenkripsi di sana.
        |# Eksekusi yang dilewati juga dicatat — z2-when fired menampilkan skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, jadi aturan yang tidak pernah berjalan bisa dijelaskan. Tombol ▶ di aplikasi
        |# mengabaikan semua filter (untuk mencoba aturan).
        |# z2-when events                        … daftar nama yang bisa dipakai dengan event:
        |# z2-when pause / resume                … menghentikan / melanjutkan eksekusi otomatis (aturan tetap ada)
        |# z2-when fired [n]                     … pemicuan terbaru (waktu / id / pemicu / run|paused)
        |# z2-when list                          … aturan terdaftar (id / on|off / pemicu / -> / cmd, TSV;
        |#                                         nama dan filter, jika ada, dalam [] di akhir)
        |# z2-when remove <id|all>  (rm juga)    … menghapus
        |# z2-when on <id> / off <id>            … mengaktifkan / menonaktifkan
        |# z2-when log <id>                      … log eksekusi aturan ini (tail)
        |# Saat terpicu, perintah berjalan di distro yang dipilih dengan Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) di lingkungannya.
        |# Untuk net: tersedia Z2_WHEN_NET (koneksi sekarang) dan Z2_WHEN_NET_PREV (yang sebelumnya).
        |# Untuk share: tersedia Z2_WHEN_SHARE (teks, atau path file yang diterima)
        |# dan Z2_WHEN_SHARE_KIND (text|file). Yang dibagikan juga tetap masuk ke baris input, seperti sebelumnya.
        |# Untuk file: tersedia Z2_WHEN_FILE (path lengkap) dan Z2_WHEN_DIR.
        |# Untuk notify: tersedia Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (dan Z2_WHEN_OTP untuk notify:otp). notify:category= dicocokkan persis: call, missed_call, msg, ...
        |# Untuk event: juga tersedia Z2_WHEN_EVENT (nama peristiwa); alarm / notify_action menambahkan
        |# Z2_WHEN_EVENT_NAME (pengenal yang Anda pakai saat memasangnya) dan Z2_WHEN_ACTION (tombol yang ditekan).
        |# Untuk battery:temp tersedia Z2_WHEN_TEMP (°C). Untuk bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Untuk app: Z2_WHEN_APP_PKG (aplikasi di depan sekarang) / Z2_WHEN_APP_PREV (yang sebelumnya).
        |# Untuk steps: Z2_WHEN_STEPS (langkah hari ini). Untuk file: juga Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated memberi Z2_WHEN_PKG, timezone_changed memberi Z2_WHEN_TZ,
        |# usb_attached / usb_detached memberi Z2_WHEN_USB (nama) / Z2_WHEN_USB_ID (vendor:produk).
        |# Aturan yang sama tidak terpicu dua kali dalam 10 detik (peristiwa seperti screen_on sering datang).
        |# mis. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "dering: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Laporan pagi' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Eksekusi otomatis dijeda (z2-when resume untuk melanjutkan)"

    override val whenResumed: String get() = "Eksekusi otomatis dilanjutkan"

    override val whenNoFires: String get() = "(belum ada yang terpicu)"

    override val whenPausedNote: String get() = "# dijeda (z2-when resume untuk melanjutkan)"

    override val whenNoLog: String get() = "(belum ada log)"

    override val whenWriteFailed: String get() = "z2-when: tidak bisa menulis aturan"

    override val whenUnknownIfKey: String get() = "z2-when: kunci if= tidak dikenal (z2-state menampilkan yang bisa dipakai):"

    override val whenUnknownTrigger: String get() = "z2-when: pemicu tidak dikenal (z2-when tanpa argumen menampilkan daftarnya):"

    override val whenBadTriggerSpec: String get() = "z2-when: pemicu ini tidak menerima argumen itu:"

    override val whenPausedWarn: String get() = "catatan: eksekusi otomatis sedang dijeda (z2-when resume untuk melanjutkan)"

    override val whenRunJoined: String get() = "catatan: baris baru di perintah diubah menjadi spasi (aturan hanya membaca satu baris)"

    override val whenEventList: String get() = """
        |screen_on              layar menyala                          [deteksi aktif]
        |screen_off             layar mati                             [deteksi aktif]
        |unlocked               perangkat dibuka kuncinya              [deteksi aktif]
        |power_connected        pengisian daya dimulai                 [deteksi aktif]
        |power_disconnected     pengisian daya berhenti                [deteksi aktif]
        |battery_low            baterai lemah                          [deteksi aktif]
        |battery_okay           baterai kembali normal                 [deteksi aktif]
        |battery_level          tingkat melewati kelipatan 10%         [deteksi aktif]
        |wifi_connected         terhubung ke Wi-Fi                     [deteksi aktif]
        |wifi_disconnected      keluar dari Wi-Fi                      [deteksi aktif]
        |net_online             koneksi yang berfungsi muncul          [deteksi aktif]
        |net_offline            tidak ada lagi koneksi yang berfungsi  [deteksi aktif]
        |net_wifi               koneksi yang dipakai adalah Wi-Fi      [deteksi aktif]
        |net_mobile             koneksi yang dipakai adalah seluler    [deteksi aktif]
        |headset_plugged        headset kabel dipasang                 [deteksi aktif]
        |headset_unplugged      headset kabel dicabut                  [deteksi aktif]
        |bt_audio_connected     audio Bluetooth terhubung              [deteksi aktif]
        |bt_audio_disconnected  audio Bluetooth terputus               [deteksi aktif]
        |airplane_on            mode pesawat aktif                     [deteksi aktif]
        |airplane_off           mode pesawat nonaktif                  [deteksi aktif]
        |ringer_normal          dering ke mode normal                  [deteksi aktif]
        |ringer_vibrate         dering ke mode getar                   [deteksi aktif]
        |ringer_silent          dering ke mode senyap                  [deteksi aktif]
        |bt_connected           perangkat Bluetooth terhubung          [deteksi aktif]
        |bt_disconnected        perangkat Bluetooth terputus           [deteksi aktif]
        |powersave_on           penghemat baterai aktif                [deteksi aktif]
        |powersave_off          penghemat baterai nonaktif             [deteksi aktif]
        |dnd_on                 Jangan Ganggu aktif                    [deteksi aktif]
        |dnd_off                Jangan Ganggu nonaktif                 [deteksi aktif]
        |app_installed          aplikasi dipasang                      [deteksi aktif]
        |app_removed            aplikasi dicopot                       [deteksi aktif]
        |app_updated            aplikasi diperbarui                    [deteksi aktif]
        |timezone_changed       zona waktu berubah                     [deteksi aktif]
        |usb_attached           perangkat USB dipasang                 [deteksi aktif]
        |usb_detached           perangkat USB dicabut                  [deteksi aktif]
        |boot                   perangkat dinyalakan                   [selalu]
        |alarm                  z2-alarm terpicu                       [selalu]
        |notify_action          tombol notifikasi ditekan              [selalu]
        |unlock_failed          gagal membuka kunci                    [selalu, perlu persiapan]
        |unlock_succeeded       terbuka setelah gagal                  [selalu, perlu persiapan]
        |voice                  perintah suara terdengar               [selalu, dari kartu atau asisten]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Nama yang bisa dipakai di event:<nama>. Urutannya sama dengan di events.jsonl.
        |    # Kelompok atas memerlukan deteksi aktif (Setelan > Izin dan notifikasi),
        |    # kelompok bawah dipasang oleh Anda, jadi berfungsi meski deteksi nonaktif.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … memeriksa, mengunduh, dan menyerahkan versi baru ke pemasang
        |# z2-update --check             … hanya memberi tahu apakah ada versi lebih baru
        |# z2-update --keep              … menyimpan .apk yang diunduh (dihapus secara bawaan)
        |# z2-update --dir <folder>      … mengunduh ke folder ini alih-alih folder aplikasi
        |#
        |# Asalnya
        |#   Halaman GitHub Releases z2term, file yang sama yang akan Anda unduh secara manual
        |#   (z2term-<version>.apk, ditandatangani dengan kunci rilis). Tidak ada tempat lain yang dihubungi, dan
        |#   sama sekali tidak ada sebelum Anda menjalankan ini.
        |#
        |# Yang bisa dan tidak bisa dilakukan
        |#   ⚠ **Tidak bisa memasang diam-diam.** Android selalu menampilkan layar "pasang?" miliknya untuk
        |#     aplikasi yang mengganti dirinya sendiri, jadi ketukan terakhir selalu milik Anda. Perintah ini melakukan semua
        |#     langkah sampai layar itu.
        |#   ⚠ Pertama kali, izinkan **"Instal aplikasi tidak dikenal"** untuk z2term (ia akan memberi tahu, dan
        |#     ⚙Setelan punya tombol yang sama). Tanpa itu layar pemasangan tidak pernah muncul.
        |#   ⚠ Dipasang dari F-Droid atau toko? Maka ini menolak dan menyuruh Anda memperbarui di sana —
        |#     versi yang Anda miliki adalah urusan mereka untuk diganti, bukan kami.
        |#
        |# File yang diunduh
        |#   Secara bawaan masuk ke folder aplikasi dan dihapus setelah pemasangan
        |#   berhasil (dan saat mulai berikutnya, kalau-kalau aplikasi dihentikan di tengah pemasangan — yang
        |#   wajar saat ia mengganti dirinya). --keep atau --dir mengubah itu; ⚙Setelan punya dua
        |#   setelan yang sama untuk tombol di sana.
        |#
        |# mis. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # memeriksa setiap malam (tetap bertanya kepada Anda)
    """.trimMargin()

    override val updateUsage: String get() = "penggunaan: z2-update [--check] [--keep] [--dir <folder>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current adalah versi terbaru."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: mencari versi yang lebih baru ..."

    override val updateHandedToInstaller: String get() = "z2-update: layar pemasangan sudah terbuka di perangkat — setujui untuk menyelesaikan."

    override fun updateNoApk(url: String): String = "z2-update: rilis ini tidak punya lampiran .apk. Ambil dari halaman rilis: $url"

    override val updateNeedPermission: String get() = "z2-update: izinkan dulu \"Instal aplikasi tidak dikenal\" untuk z2term " +
            "(Setelan > Aplikasi > Akses aplikasi khusus > Instal aplikasi tidak dikenal, atau tombol di Setelan z2term)."

    override val updateManagedByStore: String get() = "z2-update: versi ini dipasang dari toko (F-Droid / Play). Perbarui di sana."

    override fun updateFailed(reason: String): String = "z2-update: gagal: $reason"
}
/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Perintah suara — tabel kata (Bahasa Indonesia). voice.sh membacanya. Sunting untuk menambah atau mengubah apa yang bisa Anda ucapkan.
#
# Cara menulis:
#   kunci = satu cara|cara lain|...    pisahkan dengan "|"; diperiksa dari atas ke bawah
#   taruh * untuk mengambil kata di tempat itu (buka * -> "buka peta" memberi "peta")
#   *X* mengambil kata di kedua sisi X
#   kunci =                             kosong di kanan menonaktifkan kunci ini
#   Tanda baca diabaikan, begitu pula huruf besar/kecil dan spasi berlebih
#   Kunci yang tidak ada di sini memakai nilai bawaan (hapus satu baris untuk kembali ke aslinya)
#   Baris yang diawali say_ / msg_ / fmt_ / title_ adalah yang diucapkan atau ditampilkan; {…} menerima nilai
#
# Mencoba tanpa bicara: sh ~/.z2term/macros/voice.sh try lagu berikutnya
# "kata Anda sendiri = perintah" ditulis di ~/.z2term/voice/commands.txt (diperiksa sebelum tabel ini).

# --- Kata yang didengar ---
ai = tanya ai *|hai ai *|halo ai *|ai *|pertanyaan *
media_next = lagu berikutnya|lagu selanjutnya|berikutnya|selanjutnya|lewati|lewati lagu|ganti lagu
media_previous = lagu sebelumnya|sebelumnya|kembali ke lagu sebelumnya|mundur
media_pause = jeda|berhenti|stop|hentikan musik|jeda musik|matikan musik
media_play = putar|lanjutkan|lanjut|putar musik|lanjutkan musik|nyalakan musik
volume_up = besarkan suara|keraskan|naikkan volume|tambah volume|volume naik|lebih keras
volume_down = kecilkan suara|pelankan|turunkan volume|kurangi volume|volume turun|lebih pelan
volume_mute = bisukan|senyap|mode senyap|matikan suara
volume_unmute = nyalakan suara|bunyikan lagi|suara aktif
time = jam berapa|sekarang jam berapa|pukul berapa|jam berapa sekarang|waktu|jam
date = tanggal berapa|hari apa|hari ini hari apa|tanggal berapa hari ini|tanggal|hari apa sekarang
battery = baterai|sisa baterai|berapa baterai|berapa persen baterai|status baterai
steps = langkah|berapa langkah|jumlah langkah|langkah saya|berapa langkah hari ini|langkah hari ini
torch_on = nyalakan senter|senter|hidupkan senter|nyalakan lampu|lampu
torch_off = matikan senter|padamkan senter|matikan lampu
screenshot = tangkapan layar|screenshot|ambil tangkapan layar|tangkap layar
home = beranda|layar utama|kembali ke beranda
news = berita|berita terbaru|bacakan berita|ada berita apa|judul berita|rss
weather = cuaca|bagaimana cuaca|cuaca hari ini|cuacanya bagaimana|prakiraan cuaca
weather_tomorrow = cuaca besok|bagaimana cuaca besok|prakiraan cuaca besok|besok cuacanya bagaimana
memo_read = bacakan catatan|baca catatan|catatan saya|tampilkan catatan|bacakan catatan saya
memo_add = catat bahwa *|catat *|tulis catatan *|catatan *|tulis *
remind_list = pengingat|pengingat saya|daftar pengingat|tampilkan pengingat|apa saja pengingat saya|jadwal saya
remind = ingatkan saya untuk *|ingatkan saya bahwa *|ingatkan saya *|ingatkan aku *|pengingat *
wake = bangunkan saya jam *|bangunkan saya pukul *|bangunkan saya *|bangunkan aku *|pasang alarm jam *|alarm jam *|alarm *
help = apa yang bisa saya ucapkan|apa yang bisa kamu lakukan|bantuan|perintah
timer = timer *|pasang timer *|setel timer *|hitung mundur *
navi = navigasi ke *|pergi ke *|rute ke *|antar saya ke *|bagaimana ke *|saya mau ke *
navi_ask = navigasi|rute|mulai navigasi|petunjuk arah
place_phone = berapa nomor telepon *|nomor telepon *|nomor *
call = telepon *|hubungi *|panggil *
play = putar lagu *|putarkan *|mainkan *
open = buka *|jalankan *|luncurkan *
# Membuka menurut jenis (aplikasi bawaan ponsel). Mis. buka peramban
open_browser = peramban|browser|internet
open_maps = peta|maps
open_mail = email|surel|gmail
open_camera = kamera
open_settings = setelan|pengaturan
open_calendar = kalender|agenda
open_music = musik
open_gallery = galeri|foto
open_contacts = kontak
open_dialer = telepon|aplikasi telepon
open_messages = pesan|sms
open_calculator = kalkulator
# Nama panggilan aplikasi. Jika menyebut namanya tidak membukanya, tulis: panggilan:nama aplikasi|panggilan:nama aplikasi
app_names =

# Volume dengan angka ("turunkan volume 3", "volume 10", "volume 50 persen", "volume maksimal") — cocok jika terkandung
volume_word = volume|suara
volume_up_word = naikkan|besarkan|keraskan|tambah|naik
volume_down_word = turunkan|kecilkan|pelankan|kurangi|turun
volume_max_word = maksimal|maksimum|max|penuh
volume_min_word = minimal|minimum|nol
volume_percent_word = %|persen
volume_mute_word = bisukan|senyap
volume_unmute_word = nyalakan

# Durasi dan waktu (timer, pengingat, alarm)
unit_hour = jam
unit_minute = menit|mnt
unit_second = detik|dtk
unit_day = hari
when_before = dalam
when_after = lagi|dari sekarang
clock_at = pukul|jam
clock_hour = tepat
clock_minute =
clock_half =
clock_am = pagi
clock_pm = siang|sore|malam|nanti malam
day_today = hari ini
day_tomorrow = besok
day_after_tomorrow = lusa
day_daily = setiap hari|tiap hari
day_weekdays = hari kerja|senin sampai jumat
day_every = setiap|tiap
wd_mon = senin
wd_tue = selasa
wd_wed = rabu
wd_thu = kamis
wd_fri = jumat
wd_sat = sabtu
wd_sun = minggu
# Kata kecil yang tersisa di antara teks dan bagian yang dibuang
remind_filler = untuk|bahwa|saya|aku|agar|supaya|pada|di|ke

# --- Yang diucapkan atau ditampilkan ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Perintah suara
title_navi = Navigasi
title_ai = AI
title_timer = Timer
title_news = Berita
title_memo = Catatan
title_remind = Pengingat
title_call = Panggilan
title_help = Yang bisa Anda ucapkan
title_rule_voice = Perintah suara
title_rule_voice_locked = Perintah suara (terkunci)
title_rule_timer = Timer suara
title_rule_steps = Hitung langkah (untuk perintah suara)
fmt_clock = {h}.{mm}
fmt_clock_oclock = pukul {h}
fmt_weekdays = Senin|Selasa|Rabu|Kamis|Jumat|Sabtu|Minggu
fmt_months = Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember
say_time = Sekarang pukul {clock}
say_date = Hari ini {weekday}, {day} {month_name}
say_battery = Baterai {n} persen. {charging}
say_charging = Sedang mengisi daya.
say_steps = Anda sudah berjalan {n} langkah hari ini
say_steps_none = Belum ada langkah yang tercatat. Perlu izin Aktivitas fisik dan aturan langkah
say_news = Ini judul berita terbaru. {titles}
say_news_none = Tidak ada berita baru
fmt_weather_lang = id
say_weather = Saat ini {desc}, {temp} derajat
say_weather_tomorrow = Besok: {desc}, tertinggi {hi} dan terendah {lo} derajat, peluang hujan {rain} persen
say_weather_fail = Saya tidak bisa mendapatkan cuaca
say_memo_saved = Sudah dicatat
say_memo_recent = Catatan terbaru Anda. {memos}
say_memo_none = Tidak ada catatan
say_remind_ok = Pengingat dijadwalkan. {res}
say_remind_fail = Saya tidak bisa menjadwalkannya. {res}
say_remind_unclear = Saya tidak paham kapan atau tentang apa harus mengingatkan. Coba: ingatkan saya buang sampah besok jam 7 pagi
say_remind_missing = Makro pengingat belum terpasang. Jalankan z2-macro install remind di terminal
fmt_wake_label = alarm
say_wake = Waktunya bangun
say_wake_set = Saya akan membangunkan Anda pukul {clock}
say_wake_unclear = Saya tidak paham jam berapa harus membangunkan
fmt_unit_hour = jam
fmt_unit_minute = menit
fmt_unit_second = detik
fmt_timer_label = {n} {unit}
say_timer_set = Timer {label} dimulai
say_timer_done = {label} sudah habis
say_timer_unclear = Saya tidak paham durasi timernya
say_call_missing = Saya tidak menemukan nomor untuk {who}
msg_call_missing = "{who}" tidak ada di {file}. Tulis satu "nama nomor" per baris.
say_open_missing = Saya tidak menemukan aplikasi bernama {name}
say_help = Yang bisa Anda ucapkan ada di notifikasi
say_locked = Silakan buka kunci ponsel
msg_help = jam berapa, tanggal, baterai, langkah / turunkan volume 3, volume 50 persen / lagu berikutnya, jeda / timer 3 menit, bangunkan saya jam 7 / ingatkan saya buang sampah besok jam 7 pagi, pengingat / berita, cuaca, cuaca besok / catat beli susu, bacakan catatan / nyalakan senter, tangkapan layar / buka … / telepon … / navigasi ke …, nomor telepon … / AI … (aktifkan dengan voice.sh ai) / ubah kata: voice.sh words
msg_unknown = Saya tidak paham: {text}
msg_locked = "{text}" akan dilanjutkan setelah ponsel dibuka kuncinya
msg_navi_where = Sebutkan tujuan Anda
msg_navi_hint = Ke mana (nama tempat)
msg_ai_off = AI nonaktif. Ketuk Salin dan tempel di terminal untuk memakai {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI nonaktif. Pasang codex / claude / agy dan masuk ke salah satunya, lalu pilih di terminal, mis. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} tidak ditemukan. Pasang dan masuk, atau pilih yang lain dengan sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Bertanya kepada AI ({ai})…
msg_ai_noanswer = Tidak ada jawaban (log: {log})
msg_ai_usage = ai: berikan salah satu dari off | codex | claude | agy
msg_ai_how = AI nonaktif. Untuk memakainya: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; AI hanya ditanya jika Anda memulai dengan kata pemicu, "AI" atau "pertanyaan"). Model: sh ~/.z2term/macros/voice.sh model <nama> (kosong = bawaan AI tersebut)
fmt_ai_prompt = (Jawaban akan dibacakan. Tanpa memakai perintah atau file, jawab dalam bahasa Indonesia dalam 2 atau 3 kalimat, tanpa simbol maupun daftar berpoin.)
msg_setup_done = Aturan perintah suara terdaftar (yang sudah ada tidak ditambahkan lagi):
msg_setup_steps = Aturan Hitung langkah (steps:reach=1000000) hanya menjaga penghitung langkah tetap aktif agar Anda bisa menanyakan langkah hari ini. Target ini tidak pernah tercapai, jadi tidak menjalankan apa pun.
msg_words_file = Tabel kata: {file}  (sunting untuk menambah cara mengucapkan; sh ~/.z2term/macros/voice.sh words membukanya)"""
