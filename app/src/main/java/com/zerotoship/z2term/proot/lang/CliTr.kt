package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Türkçe (`tr`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: sekme (タブ) / dokunmak (タップ) / uzun basmak (長押し) / kaydırmak (フリック) / dosya / klasör /
 * Ayarlar (設定) / pano (クリップボード) / makro / kutucuk (タイル) / kenar paneli (エッジパネル) /
 * kalıcı sunucu (常駐サーバー)。呼びかけは丁寧な命令形 (-in / -ın)。
 * ⚠ **アポストロフィ (固有名詞の接尾辞 z2term’ü など) は活字の ’ を使う** — シェルスクリプトの中に入るので、' だと引用符とぶつかる。
 */
internal object CliTr : CliLanguage {

    override val code: String = "tr"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgTr) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgTr(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 GUI yığını kuruluyor",
            cleanInstalling = "🧹 GUI yığını temiz olarak yeniden kuruluyor",
            noPackageManager = "❌ Desteklenen paket yöneticisi (apk/apt-get/pacman) bulunamadı.",
            invalidGeometry = "❌ Geçersiz çözünürlük",
            noXvnc = "❌ Xvnc/Xtigervnc yok (tigervnc kurulu değil)",
            alreadyRunning = "✅ GUI zaten çalışıyor",
            startingXvnc = "▶ Başlatılıyor",
            xvncFailed = "❌ Xvnc başlatılamadı. Kayıt:",
            ready = "✅ GUI hazır. z2term’ün bir GUI sekmesinden bağlanın.",
            running = "✅ GUI çalışıyor",
            stopped = "⏹ GUI durdu",
            stoppedMsg = "⏹ Durduruldu",
            usage = "Kullanım: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ GUI yığını (Xvnc / openbox / D-Bus) kurulamadı.",
            installFailedHint = "   Ağı kontrol edin. Onarmak için terminalde z2gui clean çalıştırabilirsiniz.",
            audioInstalling = "🔊 GUI sesi: PulseAudio kuruluyor",
            audioNoPulse = "⚠️ GUI sesi: pulseaudio yok, sessiz devam ediliyor",
            audioNoPactl = "⚠️ GUI sesi: pactl yok, sessiz devam ediliyor",
            audioStartFailed = "⚠️ GUI sesi: PulseAudio başlamadı",
            audioReady = "🔊 GUI sesi: z2sink.monitor →",
            menuApps = "Uygulamalar",
            menuWindows = "Pencereler",
            menuDesktops = "Sanal masaüstleri",
            menuPreviousDesktop = "Önceki masaüstü",
            menuNextDesktop = "Sonraki masaüstü",
            menuReload = "Menüyü yeniden yükle"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Geçersiz bağlantı noktası numarası",
            configOk = "sshd(dropbear) yapılandırması tamam",
            installingDropbear = "📦 dropbear kurulu değil — kuruluyor…",
            dropbearInstallFailed = "❌ dropbear kurulamadı. Ağı ve paket adlarını kontrol edin.",
            privilegedPortWarn = "⚠️ Ayrıcalıklı bağlantı noktası; proot (root’suz) büyük olasılıkla kullanamaz (1024+ seçin).",
            lanExposeWarn = "⚠️ LAN/WAN’a açık mod: anahtarla kimlik doğrulama zorunlu, güçlü bir anahtar kullanın.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys boş. LAN’a açık başlatma iptal edildi.",
            noAuthorizedKeysHint = "   Açık anahtarınızı ~/.ssh/authorized_keys içine kaydedip yeniden deneyin.",
            loopbackBind = "🔒 yalnızca loopback. LAN’a açmak için --lan verin veya Z2_SSHD_LAN=1 ayarlayın.",
            foregroundStart = "▶ dropbear ön planda (durdurmak için Ctrl-C)",
            listeningLan = "✅ dropbear dinliyor (root, yalnızca anahtarla kimlik doğrulama)",
            listeningLoopback = "✅ dropbear dinliyor (root, yalnızca anahtarla kimlik doğrulama, loopback)",
            startupFailed = "❌ dropbear başlatılamadı. Kayıt:",
            deviceIp = "cihaz-ip",
            authKeysHint = "ℹ ~/.ssh/authorized_keys boş. Bağlanmak için bir istemcinin açık anahtarını kaydedin.",
            authKeysExample = "   örn. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 pacman anahtarlığı hazırlanıyor (yalnızca ilk kez, ağ olmadan). Biraz sürer…",
            "keyring.msgOk" to "✅ Anahtarlık hazır. Artık paket kurulabilir.",
            "keyring.msgInitFail" to "❌ pacman-key --init başarısız oldu. Bu sekme bir sonraki açılışta yeniden denenecek.",
            "keyring.msgNoKeyrings" to "❌ /usr/share/pacman/keyrings içinde paketlenmiş anahtarlık bulunamadı.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate başarısız oldu. Bu sekme bir sonraki açılışta yeniden denenecek.",
            "action.description" to "Adlandırılmış Android işlem makroları. z2term Android actions’ı z2-key permission ile açın.\nsave tanımı doğrular ve değiştirir; screen=current ekranı kaydeder.\nrun bitişi bekler; start bir çalıştırma kimliği döndürür. run’ı kesmek o çalıştırmayı durdurur.\nAynı anda tek çalıştırma, 64 kaynak talimatı, 0–300 saniye (0 = sınırsız). Bildirim ve stop kalan adımları iptal eder.\nKoordinatlar varsayılan olarak öndeki uygulamaya göredir ve aynı ekran boyutu/yönü gerektirir. Elle hareketler: en fazla 3 saniye; kaydedilen dokunuşlar: en fazla 30 saniye.\nBir panel hareketinden start, bir kabuk makrosunda veya z2-when komutunda run kullanın.\n/root/.z2term/actions/NAME.actions içinde saklanır (paylaşılan ana klasör). z2-macro actions bir takma addır.",
            "action.conditionHelp" to "Koşullarda ui SEÇİCİ (ekranda bir öğe var), color X,Y=AD|#RRGGBB (px cinsinden ekran noktası; screen= gerekir) ve command KABUK (çıkış durumu 0; kalanı kabuk olarak okur, bu yüzden en sonda) da kullanılabilir.\n| VEYA demektir ve virgülden (VE) daha zayıf bağlanır. wait-until MS KOŞUL / wait-while MS KOŞUL her 250 ms'de denetler; 0 makronun kendi sınırına kadar bekler. Süre dolarsa çalıştırma başarısız olur.\nEkran terimleri hedef uygulamayı (yoksa öndekini) okur ve uygulama başına izin gerektirmez.",
            "action.watchHelp" to "watch KOŞUL call AD, bloğu bitene kadar (en üst düzeyde tüm makro) koşulu her 300 ms'de denetler. Koşul sağlanınca AD çalışır, ardından makro devam eder; bekleyen adım kesilip yeniden gönderilir, diğer adımlar önce biter. watch koşullarında command kullanılamaz.\nrun-output KABUK komutu çalıştırır, sonra yazdırdığı adımları (önce denetlenir; başlık, call, run-output ve watch olmaz) yürütür. 0 dışı çıkış durumu çalıştırmayı başarısız kılar.",
            "action.humanHelp" to "human=1..20 (on = 5, off veya 0 = yok; büyüdükçe daha çok oynar) dokunma, kaydırma, sürükleme ve beklemeyi daha az mekanik yapar: küçük konum sapmaları, yavaş başlayıp yavaş biten kavisli kaydırmalar ve biraz değişen süreler. Durdurma çubuğundaki ~ düğmesi çalışırken bunu değiştirir.",
            "action.loopHelp" to "repeat until KOŞUL ... end (veya repeat N until KOŞUL) her turdan önce koşulu denetler ve sağlanınca çıkar; break en içteki repeat'ten hemen çıkar.\ntext~=PARÇA / desc~=PARÇA, metin veya açıklama PARÇA içeriyorsa eşleşir (click, wait-ui ve ui koşulları).",
            "action.inputHelp" to "tap ui SEÇİCİ öğenin ortasına parmakla dokunur (click'in işlemediği ekranlar için); giriş alanları ipucu metniyle bulunur.\ntype METİN odaktaki giriş alanının içeriğini değiştirir (boş temizler); enter klavyenin Enter tuşuna basar. Parola alanları reddedilir. version=2.",
            "action.tryHelp" to "try ... else ... end: içindeki bir adım başarısız olursa (öğe bulunamadı, wait-ui/wait-until zaman aşımı, kabuğun 0 dışında çıkması, çağrılan makronun hatası) kalanı atlanır ve else adımları çalışır; else yoksa end sonrasından devam edilir. Durdurma, duraklatma, süre sınırı, komut sınırı ve kilitli ya da dönen ekran yakalanmaz. version=2.",
            "action.argsHelp" to "call AD DEĞER ... en fazla 9 değer (boşluksuz) iletir. Çağrılan makroda kabuk kısımları dışındaki \$1-\$9 ve \$# (sayı) değiştirilir; kabuk adımları değerleri kendi \$1 ... olarak alır. Her çağrı başlamadan önce kendi değerleriyle denetlenir. z2-action run AD DEĞER ... ilk makroya değer iletir.",
            "action.varHelp" to "set AD DEĞER / add AD SAYI bir değişken atar ya da artırır (harf, rakam, _; en fazla 32). Değişkenler her çalıştırmada boş başlar (atanmamış = 0) ve çağrılan makrolarla paylaşılır. Koşullar: var AD=DEĞER, var AD<SAYI, var AD>SAYI. Kabuk adımları bunları \$Z2_VAR_AD olarak görür.",
            "action.readColorsHelp" to "read-colors AD X,Y ... tek bir ekran görüntüsünden en çok 32 noktayı (px; screen= gerekir) okur ve yazıldığı sırayla, boşlukla ayrılmış RRGGBB olarak AD değişkenine koyar. Renk koşulları her terim için bir görüntü alır (her biri yaklaşık bir saniye), bu yüzden çok nokta gerektiğinde bunu kullanın. Kabuk adımları onu \$Z2_VAR_AD olarak okur.",
            "action.controlHelp" to "version=2, repeat N|forever ... end, if KOŞUL ... else ... end ve call AD ekler.\nKoşullar z2-state anahtarlarını veya foreground=PAKET kullanır; virgül VE demektir, ! bir terimi olumsuzlar.\nKullanılamayan bir koşul durumu çalıştırmayı başarısız yapar. Çağrılar çalıştırmadan önce doğrulanır ve dondurulur.\nTanım başına 64 kaynak talimatı; çalıştırma başına 10000 yürütülen talimat. Kök ve alt zaman aşımları geçerlidir.",
            "action.pauseHelp" to "pause [RUN_ID] çalıştırmayı duraklatır; süren adım baştan ertelenir. Bildirim panelini aşağı çekmek de duraklatır.\nresume [RUN_ID], z2term dışındaki bir ekran önde sabit durunca devam eder (en fazla 5 saniye; yoksa duraklatılmış kalır). Sonra yanıt verir: state running ve çıkış kodu 0 ya da paused ve 0 olmayan bir kod. Duraklatılmışken süre sınırı işlemez.",
            "action.uiHelp" to "version=2 ayrıca click SEÇİCİ, long-click SEÇİCİ ve wait-ui MS SEÇİCİ destekler.\nSeçiciler: text=Devam, desc=Diğer seçenekler, id=org.example.app:id/next. Tam eşleşme; boşlukların çevresinde tırnak yok.\nÖnce target veya launch ayarlayın. wait-ui 1–30000ms boyunca her 250ms’de arar; birden çok eşleşme hata verir.\nİşlemler bir kez gönderilir; sonraki ekran için wait-ui kullanın. Giriş/şifre alanları hariçtir.\ninspect PAKET, odaktaki hedef uygulamanın görünür metinlerini, açıklamalarını ve kimliklerini JSON olarak döndürür.",
            "adb.mNoPm" to "z2adb: desteklenen paket yöneticisi (apk/apt-get/pacman) bulunamadı.",
            "adb.mInstalling" to "z2adb: adb istemcisi (${d}ADB_PKG) ${d}PM ile kuruluyor ...",
            "adb.mInstallFail" to "z2adb: adb kurulamadı. Elle kurup yeniden deneyin.",
            "adb.mHaveAdb" to "z2adb: adb zaten kurulu.",
            "adb.mNeedWireless" to "z2adb: eşleştirme başarısız olursa Ayarlar > Geliştirici seçenekleri > Kablosuz hata ayıklama’yı açın (Android 11+).",
            "adb.mPairPort" to "Eşleştirme bağlantı noktası (Cihazı eşleştirme koduyla eşle): ",
            "adb.mConnPort" to "Bağlantı noktası (Kablosuz hata ayıklama altında gösterilir): ",
            "adb.usageText" to """
        z2adb - bu cihazın adb’sine Kablosuz hata ayıklama üzerinden bağlanır (self-adb, bilgisayarsız).

        Gereksinim: Ayarlar > Geliştirici seçenekleri > Kablosuz hata ayıklama’yı açın (Android 11+).

          z2adb setup                adb istemcisini kurar (apk/apt-get/pacman, otomatik algılanır)
          z2adb pair  <port> [code]  eşleştirir (Ayarlar’daki eşleştirme bağlantı noktası + 6 haneli kodu kullanın)
          z2adb connect <port>       bağlanır ("Kablosuz hata ayıklama" altındaki bağlantı noktasını kullanın)
          z2adb status               "adb devices -l" gösterir
          z2adb <başka bir şey>      doğrudan adb’ye geçer (shell, logcat, install, ...)

        Varsayılan ana bilgisayar 127.0.0.1’dir; değiştirmek için host:port verin (veya Z2ADB_HOST ayarlayın).
        Örnek:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Yerel bir Linux komutunun sesini GUI açmadan Android’de çalar.\ninstall: geçerli Linux sistemine PulseAudio kurar (paket indirir).\nrun KOMUT [ARG...]: sesi bağlar, komutu çalıştırır, sonra ses sunucusunu durdurur.\nBirden çok komut için sesi açmak üzere bir kabuk başlatın. Hiçbir yapılandırma dosyasının üzerine yazılmaz.\nYerel bir terminal sekmesi ve PulseAudio uyumlu bir oynatıcı gerekir. SSH desteklenmez.",
            "audio.missing" to "Ses araçları eksik. Çalıştırın: z2-audio install",
            "audio.failed" to "Ses bağlantısı başarısız oldu; komut başlatılmadı.",
            "doctor.head" to "== z2doctor (bu cihaz) ==",
            "doctor.secApp" to "-- uygulama --",
            "doctor.secLinux" to "-- Linux tarafı --",
            "doctor.secPerm" to "-- izinler --",
            "doctor.secAuto" to "-- otomasyon --",
            "doctor.secReport" to "-- yardım istemek için bunu kopyalayın --",
            "doctor.lVersion" to "sürüm",
            "doctor.lEngine" to "motor",
            "doctor.lDistro" to "dağıtım",
            "doctor.lKernel" to "çekirdek",
            "doctor.lDisk" to "boş alan",
            "doctor.lHome" to "ana klasör",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "bildirimler",
            "doctor.lNotifyRead" to "bildirim erişimi",
            "doctor.lBattOpt" to "pil optimizasyonu",
            "doctor.lStorage" to "depolama (tüm dosyalar)",
            "doctor.lSms" to "SMS alma",
            "doctor.lCapture" to "olay algılama",
            "doctor.lServers" to "kalıcı sunucular",
            "doctor.lRules" to "otomasyon kuralları",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Android Ayarları > Uygulamalar > Z2Term > Bildirimler: izin verin",
            "doctor.fixNotifyRead" to "-> Ayarlar > İzinler ve bildirimler > Bildirim algılama",
            "doctor.fixBattOpt" to "-> Ayarlar > Arka plan işlem koruması > pil optimizasyonundan muaf tutun",
            "doctor.fixStorage" to "-> Ayarlar > tüm dosyalara erişime izin verin (/sdcard’ı görmek için gerekir)",
            "doctor.fixSms" to "-> Ayarlar > SMS algılama (yalnızca sms: tetikleyicileri için)",
            "doctor.fixCapture" to "-> Ayarlar > Sistem olayı algılama (charge/battery/wifi/sensor/event tetikleyicileri için gerekir)",
            "doctor.fixPaused" to "-> otomasyon duraklatıldı. \"z2-when resume\" veya Otomasyon sekmesi",
            "doctor.fixDisk" to "-> 500 MB’den az boş alan. Kullanılmayan işletim sistemi verilerini Ayarlar’dan silin",
            "doctor.fixSdcard" to "-> /sdcard burada boş; onu görünür yapan yukarıdaki depolama iznidir",
            "doctor.noteRedacted" to "(SSID / IP / ana bilgisayar adları bilerek çıkarıldı)",
            "doctor.allGood" to "Sorun bulunamadı.",
            "doctor.someBad" to "bulunan sorunlar:",
            "doctor.usage" to "kullanım: z2doctor [--share | --clip]   (--share raporu Android paylaşım menüsüne verir)",
            "doctor.pausedYes" to "duraklatıldı",
            "doctor.secExits" to "-- geçen sefer nasıl bitti --",
            "doctor.lExitsNote" to "son anormal çıkışlar (en yeni önce):",
            "doctor.lExitsNone" to "anormal çıkışlar: kayıt yok",
            "doctor.lExitsFile" to "(tam geçmiş: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> bellek yetersizliğinden sonlandırıldı. Aynı anda daha az ağır iş çalıştırın veya kalıcı sunucuları / sekmeleri azaltın",
            "doctor.title" to "\"çalışmıyor\" için kendi kendine tanılama",
            "doctor.seeAlso" to "Ayrıca bkz.: z2scan self (güvenlik kontrolü, ayrı bir araç)",
            "help.header" to "Z2Term’ün kendi komutları",
            "help.list" to """
        |[Sürüm / bilgi]
        |  z2version [--short]            Uygulama sürümü, motor, sistem (dağıtım), çekirdek
|  z2doctor [--share|--clip]      Neden çalışmıyor? Kendi kendine tanılama + yapıştırılacak bir rapor
        |
        |[Telefon özellikleri]
        |  z2-notify [-h] [-b LABEL]...   Bildirim (-h: afiş, -b: yanıt düğmesi)
        |  z2-ask "soru"                  Bildirimdeki yanıt alanıyla sorar; yanıtı yazdırır
        |  z2-listen [-w WORD,...]        Konuşmayı metne çevirir, yalnızca cihazda (z2-listen -h)
        |  z2-toast "mesaj"               Kısa mesaj (ekranda kısa bir mesaj)
        |  z2-share "metin"               Bir metni Android paylaşım menüsüne verir
        |  z2-open <url|path>             Bir URL’yi/dosyayı varsayılan uygulamada açar
        |  z2-view <file.html>            Burada yapılan bir sayfayı uygulamada okur
        |  z2-edit <file>                 Bir metin dosyasını uygulamanın düzenleyicisinde düzenler
        |  z2-qr                          Kamerayla QR kodu tarar (bir düğme onu açar)
        |  z2-img <file>                  Terminalde bir görüntü çizer (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Panoyu okur / ayarlar (set: bağımsız değişken yoksa stdin)
        |  z2-file pick | save FILE     Android belge seçici / dışa aktarma (z2-file --help)
        |  z2-battery                     Pil seviyesi / şarj durumu (JSON)
        |  z2-vibrate [ms]                Titreşim (varsayılan 200ms)
        |  z2-say "metin"                 Cihazın TTS’siyle konuşur (bağımsız değişken yoksa stdin)
        |  z2-torch [on|off|toggle|status]       El feneri (varsayılan toggle)
        |  z2-media [play|pause|next]     Medya tuşları (previous/stop da)
        |  z2-volume up|down|N|N%         Medya ses düzeyi (geçerli/en yüksek değeri yazdırır)
        |  z2-audio run COMMAND ...      Bir Linux komutunu Android sesiyle çalıştırır
        |  z2-sensor [light|accel|prox]   Bir sensör değeri okur (JSON)
        |  z2-intent -a ACT -d URI ...    Herhangi bir Android Intent’i başlatır (bkz. MACRO-GUIDE)
        |  z2-state [key]                 Geçerli durum JSON olarak (veya tek bir ham anahtar)
        |  z2-screen keepon 1h | off      Ekranın bir süre kendiliğinden kapanmasını önler
        |  z2-tile set 1 backup.sh        Bir makroyu/komutu Hızlı Ayarlar kutucuğuna koyar (12)
        |  z2-icon edit 1 | edit notify   Kutucuk / çubuk simgesini kendiniz çizin (24/48/64 nokta)
        |  z2-alarm at|daily HH:MM [name] Zaman tetikleyicisi -> events.jsonl (list/cancel da)
        |  z2-when <trigger> run <cmd>    Şarj/pil/saat/olaylarda kendiliğinden çalıştırır (z2-when: kullanım)
        |  z2-macro list|install <name>   Paketlenmiş otomasyon örnekleri (bkz. MACRO-GUIDE)
        |  z2-session list|new|send|...   Bu uygulamanın sekmelerini yönetir (z2-session: kullanım)
        |  z2-usb list | allow [device]   Linux’tan bir USB cihazı kullanır (Android onay ister)
        |  z2-update [--check]            z2term’ü GitHub Releases’tan günceller (siz onaylarsınız)
        |
        |[Grafik uygulamalar (GUI)]
        |  z2gui start [WxH] | stop | status   Linux masaüstü (örn. z2gui start 1280x720)
        |  z2run <gui-app>                Bir GUI uygulaması başlatır (GUI sekmesini de açar)
        |
        |[Bağlantı]
        |  z2adb pair/connect/shell ...   Bu telefonun kendisine adb, bilgisayarsız  (z2adb help)
        |  sshd [-p N]                    SSH sunucusu (varsayılan: yalnızca 127.0.0.1, anahtar)
        |
        |[Güvenlik]
        |  z2scan self [--save]           Bu cihazın/localhost’un kendi kendine tanılaması (z2scan help)
        |  z2scan diff                    Yalnızca temel durumdan beri değişenler (çıkış 1 = yeni)
        |  z2scan net|host|cve            localhost üzerinde nmap/lynis/trivy (uzak hedef açık onay ister)
        |
        |[Yardım]
        |  z2help | z2term                Bu liste
        |
        |Yukarıdaki her komut kendini açıklar: --help ekleyin (örn. "z2-tile --help").
        |Daha fazlası: "z2adb help", bağımsız değişkensiz "z2gui" veya HANDBOOK bölüm 11.
    """,
            "help.edge" to "[Kayan paneller]\nz2-edge --help   Panelleri tanımla ve içeriklerini güncelle\nz2-key --help    Genel Android işlemleri\nz2-shot --help   Yalnızca çizilen bölümü yakala\nz2-app --help    Uygulamaları listele ve simgeleri dışa aktar\nz2-action --help Adlandırılmış Android işlem makroları\nz2-ui --help     YZ ile uygulama kontrolü (yardım, YZ ajanları için bir kılavuzdur)",
            "macro.rssBody.head" to """
#
# Hazırlık:
#   1) Satır başına bir akış URL’si:  ~/.z2term/rss/feeds.txt
#   2) Her 30 dakikada bir kontrol:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) İsteğe bağlı - gömülmemesi gereken satır başına bir akış veya kelime:  ~/.z2term/rss/important.txt
#      Eşleşenler kendi bildirimini alır, böylece kalabalık bir akış onları itemez.
#      URL’nin veya başlığın bir parçasını yazın (örn. example.org).
#   4) Bildirim işlemlerini Otomasyon’a kaydedin:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Aç makaleyi tarayıcıya gönderir; Liste toplanan her şeyi z2term’de okunmak üzere açar.
#      Not: bir bildirim düğmesinden ekran açmak "diğer uygulamaların üzerinde göster" izni gerektirir.
#   5) Toplanan listeyi okuyun: sh ~/.z2term/macros/rss.sh view
#      İki kuralı da Komut listesi -> Otomasyon -> Otomasyon kuralları’ndan yönetin.
#      Başka bir tane eklemek yerine mevcut bildirim kuralını düzenleyin.
#   6) Bildirimler: varsayılan olarak zamanlanmış kontrol bildirir, listeden yapılan bildirmez.
#        sh ~/.z2term/macros/rss.sh notify off        # zamanlanmış kontrol: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # listedeki Yenile / Makaleler: on / off
#      İkisi de listedeki Ayarlar’da istediğiniz zaman değiştirebileceğiniz anahtarlardır.
#
# Gerekir: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Küçük resimler (listedeki Ayarlar) Pillow da gerektirir (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Pil: ne kadar sık kontrol ederseniz o kadar maliyetli. 30 dakikanın altına inmeyin.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "seen/latest içinde tutulacak en fazla satır",
            "macro.cNoPy" to "python3 gerekir (örn. apk add python3)",
            "macro.cWriteFeeds" to "Satır başına bir akış URL’si yazın:",
            "macro.cFeedsHint" to "# Satır başına bir akış URL’si (# ile başlayan satırlar atlanır)",
            "macro.cSkipFail" to "indirilemeyen veya ayrıştırılamayan bir akışı sessizce atla (diğerleri devam eder)",
            "macro.cDiff" to "# Yalnızca yenileri tutmak için zaten görüleni çıkar (z2scan’in temel durumla karşılaştırmasıyla aynı yöntem).\n# Akış tarihlerine ve sırasına güvenilemez; ikisi de kesin değildir.",
            "macro.cPrepend" to "# Widget’ın \"baş (head)\" modu doğru okusun diye en yeniyi önce yığ.",
            "macro.cNotify" to "%s yeni",
            "macro.cOpen" to "Aç",
            "macro.cListNone" to "Henüz toplanan bir şey yok — önce bağımsız değişkensiz bir kez çalıştırın.",
            "macro.cListHint" to "Listelemek için: rss.sh list [sayı]",
            "macro.cList" to "Liste",
            "macro.cPageTitle" to "Akışlar",
            "macro.cPageSites" to "%s site",
            "macro.cPageArticles" to "%s makale",
            "macro.cPageDoc" to "# Okuma sayfasını kurar (z2-view onu uygulamada gösterir; sunucu yok, tarayıcı yok).\n" +
            "# Siteye göre gruplanmış, en yeni önce, her makalede bir Aç düğmesiyle. Telefonun teması\n" +
            "# CSS değişkenleri olarak gelir, böylece sayfa terminalin renklerini izler.",
            "macro.cViewHint" to "Okumak için: rss.sh view",
            "macro.cHitMax" to "bir çalıştırmadaki makale başına en fazla bildirim (geri kalanı özete gider)",
            "macro.cSplitDoc" to "# Kaybolmaması gerekeni ayır. Özetin gövdesi yalnızca 3 satır taşır, bu yüzden aynı anda\n" +
            "# güncellenen kalabalık bir akış önemli olanı iter. Eşleşmeler **kendi\n" +
            "# bildirimini** alır (uygulama her bildirime ayrı id verir, böylece hiçbir şey değiştirilmez).",
            "macro.cNameDoc" to "# URL’yi bildirim adına koy: düğmeye basmak onu notify_action içinde {name} olarak\n" +
            "# geri verir, böylece ekranda birkaç bildirim olsa da doğru makale açılır (hazırlık 4).",
            "macro.cListDoc" to "# Toplananları okunabilir bir liste olarak göster (renksiz — dosyaya/widget’a benzemeli).\n" +
            "# latest.txt ham veri olarak kalır, satır başına bir makale (widget’ın tail’i ve rss-open.sh onu okur).",
            "macro.cSettings" to "Ayarlar",
            "macro.cArchive" to "Arşiv",
            "macro.cDelete" to "Sil",
            "macro.cThumbs" to "Küçük resimler",
            "macro.cSave" to "Eski makaleleri sakla",
            "macro.cNotifyPoll" to "Yeni makaleleri bildir (zamanlanmış kontrol)",
            "macro.cNotifyView" to "Yeni makaleleri bildir (listeden kontroller)",
            "macro.cDeleteSite" to "Bu sitenin tüm makalelerini sil",
            "macro.cConfirmSite" to "%h sitesinin %s makalesi listeden kaldırılsın mı?",
            "macro.cArticles" to "Makaleler",
            "macro.cPrev" to "Önceki",
            "macro.cNext" to "Sonraki",
            "macro.cCount" to "%s makale",
            "macro.cUndated" to "Tarihsiz",
            "macro.cArchiveEmpty" to "Henüz saklanan bir şey yok. Ayarlar’da Eski makaleleri sakla’yı açın, toplanan makaleler burada kalır.",
            "macro.cSearch" to "Ara",
            "macro.cSearchWord" to "Kelimeler (hepsini istemek için boşlukla ayırın)",
            "macro.cByMonth" to "Aya göre",
            "macro.cNoHit" to "Hiçbir şey bulunamadı.",
            "macro.cDropMonth" to "Bu ayı sil",
            "macro.cDropConfirm" to "%m ayının saklanan %s makalesi silinsin mi? Bu geri alınamaz.",
            "macro.cOnOff" to "on veya off verin",
            "macro.cNoPillow" to "Küçük resimler Python için Pillow gerektirir (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Hazırlık: önce toplayan parçayı kurun
#   z2-macro install rss
# Widget: durum widget’ının ayarlarında "rss-open"ı bir düğmeye atayın.
#
# z2-run: "rss-open"ı durum widget’ının bir düğmesine atayın (burada denemek için: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Henüz makale yok",
            "macro.cAllRead" to "Açılacak yeni bir şey yok",
            "macro.cPick" to "# latest.txt satırları \"başlık  URL\" biçimindedir. Henüz açılmamış ilk URL’yi al.",
            "macro.cCap" to "açılanlar listesini sonsuza dek büyümesin diye sınırla",
            "macro.copied" to "Kod kopyalandı: ${d}{code}",
            "macro.cleared" to "Kopyalanan kod temizlendi",
            "macro.otpTitle" to "Tek kullanımlık kod",
            "macro.cTtl" to "kopyayı temizlemeden önceki saniye (yalnızca doğrudan girdiğinde)",
            "macro.cCode" to "# Uygulama kodu zaten çıkardı. Çıkaramadıysa hiçbir şey yapma.",
            "macro.cTry" to "# Doğrudan koymayı dene. Yalnızca **uygulamaya bakarken** girer:\n" +
            "# Android 10+ panoya yalnızca öndeki uygulamanın yazmasına izin verir, bu yüzden arka plandaki\n" +
            "# bir çalıştırma sessizce yok sayılır. Girip girmediğini görmek için geri oku.",
            "macro.cFallback" to "  # Girmedi = uygulamaya bakmıyorsunuz. Bunun yerine bildirimin \"Kopyala\" düğmesiyle\n" +
            "  # ver (ona basmak uygulamayı öne getirir).\n" +
            "  # ⚠ Böyle giren şey aşağıdaki TTL ile temizlenmez: temizlemek de ön planı gerektirir.",
            "macro.cClear" to "# TTL’den sonra panoyu yalnızca hâlâ kopyalanan kodu tutuyorsa boşalt\n" +
            "# (sonradan kopyalanana dokunulmaz).",
            "macro.cHow" to """
# ■ Arama bildirimlerinden telefon numaralarını kopyala
#   Başlıkta, sonra metinde tek başına duran bir telefon numarası arar ve bir Kopyala düğmesi sunar.
#   Kayıtlı ve kayıtsız arayanlar dahildir; kişiler sorgulanmaz.
#   Yalnızca bir adla numara bulunamaz. Yalnızca bildirim erişimi kullanılır.
#   Mevcut kurallar çalışmaya devam etsin diye dosya adı unknown-call.sh olarak kalır.
""",
            "macro.cIsNum" to """
# Tek başına bir numara mı? Bir numaranın içerebileceği her karakteri çıkar (rakamlar + - ( ) boşluk);
# **hiçbir şey kalmazsa** ve 7 ila 15 rakam varsa numara say.
#   -> Ad veya başka harf içeren bir alan tek başına numara değildir; diğer alana bak.
#   -> "Bilinmeyen"/"Gizli numara" da buraya düşer (onları istiyorsanız aşağıdaki testi gevşetin).
# ⚠ Bunu case [!...] olarak yazmayın: desendeki ) case ayırıcısı olarak okunur.
""",
            "macro.cScan" to "# Arayan genelde başlıktadır ama bazı telefon uygulamaları metne koyar. İkisini de kontrol et.",
            "macro.cSkip" to "# İki alanda da tek başına numara yok: kopyalanacak bir şey yok.",
            "macro.cWhat" to "# Kategori hangisinin tetiklendiğini söyler (kaydedilen notify:category= ile aynı değer).",
            "macro.cCopy" to """
# Numarayı bildirimin "Kopyala" düğmesiyle (-c) ver. Burada z2-clip set çağırmak
# girmez: Android 10+ panoya yalnızca öndeki uygulamanın yazmasına izin verir ve bir
# arama sırasında o, telefon uygulamasıdır. Düğmeye basmak z2term’ü o an öne getirir.
""",
            "macro.missed" to "Cevapsız arama",
            "macro.incoming" to "Gelen arama",
            "macro.title" to "Telefon numarasını kopyala",
            "macro.stNew" to "yeni",
            "macro.stSame" to "aynı",
            "macro.stDiff" to "farklı",
            "macro.stUpdate" to "yeni sürüm",
            "macro.stEdited" to "düzenlendi",
            "macro.stHeld" to "sabit",
            "macro.msgUpdated" to "güncellendi:",
            "macro.msgNothingToUpdate" to "güncellenecek bir şey yok.",
            "macro.msgEditedSkip" to "bunu sen düzenledin, olduğu gibi bırakıldı:",
            "macro.msgUnknownSkip" to "bunu düzenleyip düzenlemediğin anlaşılamıyor (1.0.198 öncesi kuruldu), olduğu gibi bırakıldı:",
            "macro.msgHeldSkip" to "sabitlenmiş, olduğu gibi bırakıldı (kaldırmak için: z2-macro unhold %s):",
            "macro.msgHeld" to "sabitlendi (güncellenmez, bildirilmez):",
            "macro.msgUnheld" to "artık sabit değil:",
            "macro.msgNotHeld" to "sabit değildi:",
            "macro.noticeTitle" to "Yerleşik makrolarda güncelleme var",
            "macro.noticeBody" to "%s. Bunlar düzenlemediğin kopyalar. Komutu kopyalayıp bir terminal sekmesinde çalıştırarak güncelle (birini olduğu gibi bırakmak için: z2-macro hold <ad>).",
            "macro.msgInstalled" to "kuruldu:",
            "macro.msgSame" to "aynısı zaten kurulu:",
            "macro.msgOutdated" to "sizinki paketlenmiş olandan farklı:",
            "macro.msgHowUpdate" to "önce bakın: z2-macro diff %s   /   paketlenmiş olanla değiştirin: z2-macro install -f %s (düzenlemeleriniz de gider)",
            "macro.msgNotInstalled" to "henüz kurulmadı (önce z2-macro install çalıştırın):",
            "macro.msgRemoved" to "kaldırıldı:",
            "macro.msgAbsent" to "kurulu değil:",
            "macro.msgRemoveDiffers" to "sizinki paketlenmiş olandan farklı, bu yüzden kaldırılmadı:",
            "macro.msgHowRemove" to "önce bakın: z2-macro diff %s   /   yine de kaldırın: z2-macro remove -f %s (düzenlemeleriniz de gider)",
            "macro.msgNoDiffTool" to "burada diff yok. Paketlenmiş olanı yine de z2-macro show ile okuyabilirsiniz.",
            "macro.msgSameAsBundled" to "paketlenmiş olanla aynı.",
            "macro.msgNotFound" to "örnek bulunamadı:",
            "macro.msgHintResident" to "Çalışır tutmak için bunu Ayarlar -> Kalıcı sunucular’a kaydedin:",
            "macro.msgHintRun" to "Nasıl çalıştırılır:",
            "macro.remindBody.head" to """
#
# Hazırlık (bir kez):
#   sh ~/.z2term/macros/remind.sh setup   iki kancayı ve iki kutucuğu kaydeder
#
# Söz dizimi:
#   remind.sh <ne zaman> <metin>     bir hatırlatıcı ekler
#   remind.sh list                   listeler (önce numara)
#   remind.sh del <n|all>            iptal eder (rm de çalışır)
#   remind.sh peek                   listeyi bir bildirimde gösterir (Sil düğmesiyle)
#   remind.sh ask                    bildirimdeki yanıt kutusuyla sorar (kutucuk için)
#   remind.sh setup                  kancaları ve kutucukları kaydeder (bir kez)
#   remind.sh help                   bu metin
#
# <ne zaman> - bir kez:
#   30m / 90s / 2h                   şimdiden itibaren
#   18:30                            bir sonraki 18:30 (geçtiyse yarın)
#   sat 21:00                       eşleşen bir sonraki hafta günü, bir kez (bugün daha sonrası da sayılır)
#   Japonca: 土曜日の夜九時 = sat 21:00 ; 朝七時 = 07:00 ; 夜九時半 = 21:30
#   Japonca: 夜12時 belirsizdir; gece yarısı için günü açıkça belirterek 00:00 kullanın.
#   tomorrow 18:30 / 3d 07:00        güne göre (3d = 3 gün sonra)
#   07/30 19:00                      ay/gün (geçtiyse gelecek yıl)
#   2030 07/30 19:00                 yılla birlikte
#   203007301900 / 07301900          yalnızca rakam (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ saat verilmezse **günün şu anki saati** korunur (3d / 07/30 ...)
#
# <ne zaman> - tekrarlı:
#   daily 07:00 / weekday 09:00      her gün / pzt-cum
#   weekly mon 09:00                 o hafta günü
#   monthly 15 09:00                 ayın o günü
#   yearly 07/30 19:00               o ay ve gün
#   every 19:00 / every wed 19:00    "every" tek başına çalışır (sonraki kelime karar verir)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Örnekler:
#   remind.sh 30m ilacımı al
#   remind.sh tomorrow 18:30 çöpü çıkar
#   remind.sh monthly 25 10:00 kira
#   remind.sh list        ->  1  ⏰ 07/30 18:30  çöpü çıkar
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (sonra yalnızca: remind.sh 30m ...)
""",
            "macro.cStore" to """# Bir hatırlatıcı = ${d}DIR/<id>.txt içinde bir satır, sekmeyle ayrılmış: tür / etiket / ne-zaman-id / metin.
#   tür: once (yaklaşan) / fired (tetiklendi) / repeat (tekrarlı)
#   ne-zaman-id: yalnızca repeat için (del z2-when remove çağırabilsin diye). Aksi halde "-".
# Metin en sonda gelir, böylece TAB dışında her şeyi içerebilir.""",
            "macro.cParse" to """# 1-3 kelime oku ve karar ver:
#   KIND ... once|repeat   PLAN ... gösterilecek etiket   SPEC ... z2-alarm/z2-when’in aldığı   USED ... kullanılan kelime""",
            "macro.pDaily" to "her gün ",
            "macro.pWeekday" to "hafta içi ",
            "macro.pWeekly" to "her hafta ",
            "macro.pMonthly" to "her ay ",
            "macro.pYearly" to "her yıl ",
            "macro.mDay" to ".",
            "macro.mBadDate" to "böyle bir tarih yok (örn. 07/30):",
            "macro.cEvery" to """  # Kısa biçim "every ...". **Sonraki kelimenin biçimi** daily/weekly/monthly/yearly’yi seçer:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Kelime sayısı değişmez (bir kelime başkasıyla değişir), bu yüzden USED doğru kalır.""",
            "macro.cExpand" to "# \"every\"nin hangi tekrarı kastettiğini ardından gelen kelimeden karar ver.",
            "macro.cDigits" to """    # Yalnızca rakam biçimleri: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ 12 rakamı 8’den önce kontrol et (8 rakamlı desen 12’lik bir diziyle de eşleşir).""",
            "macro.cYmd" to """  # Tarih olarak yazılmış. ⚠ z2-alarm tarih almaz, bu yüzden bu da bir gecikmeye dönüşür.""",
            "macro.cCivil" to """# Takvim tarihi -> 1970-01-01’den beri gün (artık yıllar dahil; Howard Hinnant’ın days_from_civil’i).
# ⚠ date -d "2026-07-30" busybox’ta güvenilir değildir, bu yüzden burada hesaplanır.
# ⚠ Yalnızca gün farkı kullanılır, saat ise saat dilimini nötrleyen [day_epoch]’a bırakılır.""",
            "macro.cCron" to """# HH:MM ve bir gün alanından cron ifadesi kur (dk saat ayın-günü ay haftanın-günü).
# Baştaki sıfırı atmak için expr KULLANMAYIN: sonuç 0 olunca 1 ile çıkar, bu yüzden
# `expr "${d}m" + 0 || echo "${d}m"` ifadesinin sağ tarafı da çalışır ve değer iki satır olur.""",
            "macro.afterLabel" to """# "30m" -> "30m sonra (14:35)". Saati göstermek hemen kontrol etmeyi sağlar.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}u sonra (${d}at)" || echo "${d}num${d}u sonra"
}""",
            "macro.mUsageAdd" to "kullanım: remind.sh <ne zaman> <metin>",
            "macro.mBadWhen" to "saat okunamadı (deneyin: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "saati HH:MM olarak yazın:",
            "macro.mBadRange" to "saat aralık dışında (00:00-23:59):",
            "macro.mNoTime" to "saat verilmedi (örn. daily 07:00):",
            "macro.mBadDow" to "bilinmeyen hafta günü:",
            "macro.mPastTime" to "o saat zaten geçti:",
            "macro.cDays" to """    # Güne göre biçim. Saat yoksa **günün şu anki saatini** koru (asla varsayılan uydurma).
    # Tek kelime olarak da gelebilir, bu yüzden bitişik biçim de kabul edilir.""",
            "macro.cDayEpoch" to """# "HH:MM, N gün sonra"yı epoch saniyesine çevir. ⚠ busybox’ta date -d "tomorrow" yoktur,
# bu yüzden önce bugünün gece yarısı hesaplanır, sonra günler ve saat eklenir.
# ⚠ Baştaki sıfırlı bir değer ("08") ${d}(()) tarafından sekizlik okunur, bu yüzden sıfır atılır.
# ⚠ Bir gün 86400s olarak eklenir, bu yüzden saat değişimi gününde bir saat kayabilir.""",
            "macro.cFmtAt" to """# Listede gösterilen etiket. ⚠ "tomorrow"u tutmak tarih geçince yanlış olurdu.""",
            "macro.mNoBody" to "neyi hatırlatacağımı söyleyin",
            "macro.mNoAlarm" to "zamanlanamadı",
            "macro.mNone" to "zamanlanmış bir şey yok",
            "macro.mFired" to " (tetiklendi)",
            "macro.mRemoved" to "kaldırıldı:",
            "macro.mNoSuchNum" to "kayıt bulunamadı (remind.sh list’e bakın):",
            "macro.mUsageDel" to "kullanım: remind.sh del <n|all>",
            "macro.bDone" to "Bitti",
            "macro.bDelete" to "Sil",
            "macro.mAskDel" to "Hangisi kaldırılsın?",
            "macro.mAskDelH" to "numara / all",
            "macro.mDeletedTitle" to "🗑 Kaldırıldı",
            "macro.cPeek" to """# Listeyi bir bildirimde göster ("liste" kutucuğundan). **Numaraları koru** — hangisinin
# kaldırılacağı onlarla söylenir. ⚠ Kaldırma yolu olmadan listelemek şikâyetti; düğme bu yüzden.""",
            "macro.cAskDel" to """# "Sil" düğmesi: bir numara sor ve onu [cmd_del]’e ver (CLI ile aynı yol).""",
            "macro.bS1" to "+10dk",
            "macro.bS2" to "+1sa",
            "macro.mAgain" to "- yeniden",
            "macro.mTitle" to "⏰ Hatırlatıcı",
            "macro.mAsk1" to "Ne hatırlatılsın?",
            "macro.mAsk1H" to "örn. ilacımı al",
            "macro.mAsk2" to "Ne zaman?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "lütfen yeniden girin",
            "macro.mAskGiveUp" to "${ASK_TRIES} kez okunamadı. Terminalden de ekleyebilirsiniz: remind.sh 30m ilacımı al",
            "macro.mOkTitle" to "⏰ Hatırlatıcı zamanlandı",
            "macro.mNgTitle" to "⚠ Zamanlanmadı",
            "macro.cHooks" to """  # "bir hatırlatıcı tetiklendi" için bir kanca, "bir bildirim düğmesine dokunuldu" için bir kanca. Kaç
  # hatırlatıcı olursa olsun yalnızca bu ikisi. İkisi de algılama anahtarlarına bağlı değildir.""",
            "macro.cTiles" to """  # Yalnızca boş yuvaları ve zaten bizim olanları doldur (başkalarınınkinin üzerine asla yazma).
  # Bağımsız değişkenli bir makro adı 0.8.275’ten beri çalışır (önceden sh + tam yol yazılırdı).""",
            "macro.mSetupHooks" to "kaydedilen kancalar:",
            "macro.mSetupTiles" to "kutucuklar:",
            "macro.mPathHint" to "Not: bu sekme remind.sh’yi henüz adıyla çalıştıramıyor. Yeni bir sekme açın veya burada şunu çalıştırın: " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH.",
            "macro.cPathHint" to """  # ⚠ Makro klasörü yalnızca 0.8.287’den beri PATH’te, bu yüzden **daha önce açılmış sekmelerde**
  # hâlâ eskisi var. Adın bulunup bulunmadığını kontrol et, bulunmuyorsa ne yapılacağını söyle
  # (yoksa `command not found`ın görünür bir nedeni olmaz).""",
            "macro.mPlace" to "Not: kutucukları hâlâ Hızlı Ayarlar düzenleme ekranından (kalem) kendiniz yerleştirmelisiniz.",
            "macro.lRemind" to "hatırlat",
            "macro.lList" to "liste",
            "macro.cSelf" to "# Yalnızca kendi bildirimlerimize tepki ver (id’lerimiz her zaman r ile başlar)",
            "macro.cKeepRepeat" to "      # Tekrarlı bir hatırlatıcıyı burada silme: yarın yeniden tetiklenmeli.",
            "macro.cAsk" to """# Kutucuğun / bir bildirimin kullandığı yol. ⚠ **Her zaman bildirimle yanıt ver**:
# burada kimse terminale bakmıyor, bu yüzden stderr’e başarısızlık "bastım ve
# hiçbir şey olmadı" gibi okunur (oysa oldu — neden yalnızca kutucuğun run.log’una gitti).
#   Okunamadı  -> nedenini söyle ve yeniden sor, en fazla $ASK_TRIES kez (önceki yanıt korunur)
#   Zamanlandı -> planı ve metni bir bildirimde göster""",
            "macro.cSnoozeCancel" to "hatırlatıcı yapılmadan ertelendiyse erteleme alarmını bırak",
            "macro.qrBody.head" to """
#
# Kullanım:
#   qr.sh "https://example.com"       bir dizeyi kodlar ve burada çizer
#   qr.sh -f notes.txt                bir dosyanın içeriğini kodlar
#   z2-clip get | qr.sh               panodakini kodlar
#   qr.sh -o ~/qr.png "text"          PNG kaydeder (hiçbir şey çizilmez)
#   qr.sh -t "text"                   görüntü yerine bloklar gösterir
#   qr.sh -s 24 "text"                boyutu seçer (sütun olarak; varsayılan genişliğe sığar)
#
# z2-run: qr.sh "https://example.com"   (kurulduktan sonra ad yeterli. Bir kez çalışır)
#
# # Ön koşullar (bunlar olmadan çalışmaz veya farklı görünür)
#
#   1) Kodlamayı uygulama yapar ... bir z2term sekmesinde kurulacak bir şey
#      yoktur (z2-qr kodlar). qrencode zaten varsa onun yerine o kullanılır.
#      Uygulamaya ulaşılamayan yerde (ssh üzerinden) sekme (dağıtım) başına bir kez kurun:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      İkisi de yoksa kurulum komutu gösterilir ve betik durur.
#
#   2) Görüntü (varsayılan) yalnızca bu uygulamanın bir sekmesinde görünür ... Kitty
#      grafikleriyle çizilir. ssh üzerinden veya görüntü göstermeyen bir terminalde
#      bunun yerine anlamsız bir karakter akışı alırsınız. Orada -t kullanın.
#
#   3) -t (bloklar) terminalin yazı tipine bağlıdır ... satırlar arasında boşluk bırakan
#      yazı tipleri gözle okunur ama kamerayla okunmaz. Kamerayla taramak için
#      varsayılan görüntüyü veya -o’nun PNG’sini tercih edin.
#
#   4) En boy oranı varsayılır ... aşağıdaki "En boy oranı"na bakın. Basık mı? Z2_QR_ASPECT.
#
# Seçenekler:
#   -f FILE   girdiyi bir dosyadan okur (yoksa bağımsız değişkenler, yoksa stdin)
#   -o PNG    çizmek yerine PNG kaydeder. Birden çok kod
#             qr-1.png / qr-2.png ... olur
#   -t        blokları gösterir. Görüntüsüz terminaller ve ssh için
#   -s N      çizerken sütun olarak boyut. Varsayılan en fazla 34, genişlikle sınırlı
#   -h        bu yardım
#
# Uzun girdiler hakkında:
#   Bir kod sabit bir miktar tutar (en fazla 2953 alfasayısal bayt; okunabilir kalacaksa
#   birkaç yüz). 900 baytı aşan bir girdi satır sonlarından birden çok koda bölünür,
#   [1/3] olarak numaralanır ve sırayla gösterilir.
#   UYARI tek bir koda sığmayacak kadar uzun tek bir satır hiç kodlanamaz.
#
# En boy oranı:
#   Terminaller hücre boyutlarını söylemez, bu yüzden kodu kare yapmak için 1:2 karakter
#   oranı varsayılır. Basık görünüyorsa bir ortam değişkeniyle ayarlayın:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (küçük = daha uzun. Varsayılan 0.5)
""",
            "macro.cMaxBytes" to "kod başına en fazla bayt (fazlası bölünür)",
            "macro.cPngModule" to "kaydedilen PNG’de modül başına nokta",
            "macro.cTargetPx" to "çizerken hedef piksel genişliği",
            "macro.cMargin" to "kodun çevresindeki sessiz bölge (modül; tarama için gerekir)",
            "macro.cUsageFn" to "Baştaki yorum bloğunu yardım olarak göster (sabit satır sayısı yok).\n" +
            "# Boş satırlar onu durdurmaz (NF karar verir), bu yüzden boş satırlarla ayrılan bölümler bütün kalır.",
            "macro.cNeedEncoder" to "Ön koşul 1: kodlayacak bir şey. Varsa qrencode, yoksa uygulama.",
            "macro.mMissing" to "qr.sh: burada kodlayacak bir şey yok - qrencode yok ve uygulamaya ulaşılamıyor",
            "macro.mInstallOnce" to "  Bu sekmede bir kez kurun:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "PNG’yi yap. qrencode modül boyutunu deneyerek seçer; uygulama bir hedef kabul eder.",
            "macro.cMakeBlocks" to "Blok karakterlerle çiz. İki yol da kendi renklerini çıkarır, bu yüzden her tema uyar.",
            "macro.qrBody.mNoTmp" to "çalışma klasörü oluşturulamadı",
            "macro.qrBody.mUnreadable" to "okunamıyor:",
            "macro.qrBody.mEmpty" to "boş girdi",
            "macro.mSizeNum" to "-s bir sayı ister",
            "macro.qrBody.mUsageHint" to "bkz. qr.sh -h",
            "macro.mPieceFail" to "qr.sh: %d. kod oluşturulamadı: %s",
            "macro.qrBody.cCollect" to "---- girdiyi topla ----",
            "macro.cSplit" to "---- çok uzunsa satır sonlarından böl ----",
            "macro.cSplit2" to "Toplam MAX_BYTES’ı aşmadan hemen önce sonraki parçaya geç. Satırlar asla\n# ortadan kesilmez, bu yüzden çok baytlı metin bozulmaz.",
            "macro.cTrim" to "Sondaki satır sonunu at (girdide olmayan bir satır sonunu asla kodlama).",
            "macro.cSize" to "---- gösterim boyutunu terminal genişliğinden seç ----",
            "macro.cPngWidth" to "Bir PNG’nin piksel genişliğini döndür (16-19. baytlar, IHDR).",
            "macro.cModulePx" to "Hedef piksel genişliğinden modül başına noktayı türet.",
            "macro.cInline" to "Bir PNG’yi Kitty grafik protokolüyle yerinde çiz.\n# c/r kaç hücre kaplayacağını sabitler, sonra imleci altına koymak için o kadar satır sonu yazdır.",
            "macro.cEmit" to "---- çıktı ----",
            "macro.cAnsi" to "ANSIUTF8 kendi renklerini çıkarır, bu yüzden açık/koyu her temada doğrudur.",
            "macro.mdBody.head" to """
#
# Kullanım:
#   md.sh README.md                burada, bu terminalde gösterir
#   md.sh -v README.md             uygulamada açar (z2-view)
#   cat notes.md | md.sh           standart girdiden okur
#   md.sh -o page.html README.md   HTML yazar ve durur (hiçbir şey açılmaz)
#
# z2-run: md.sh README.md   (kurulduktan sonra ad yeterli. Bir kez çalışır)
#
# Seçenekler:
#   -v        uygulamada açar (z2-view)
#   -o FILE   HTML’yi oraya yazar. Burada hiçbir şey gösterilmez
#   -w N      N sütunda satır kaydırır (varsayılan: ekran genişliği, en fazla 100)
#   -p        renksiz ve süssüz (düz metin)
#   -t TITLE  okuma ekranı başlığı (varsayılan: ilk başlık, yoksa dosya adı)
#   -h        bu yardım
#
# Uzun bir belge, ekran ekran: md.sh README.md | less -R   (-R renkleri korur)
#
# Kurulacak bir şey yok: sh ve awk yeter.
# Başlıklar, paragraflar, listeler (iç içe, numaralı, [ ]/[x]), alıntılar, kod, tablolar,
# ayırıcılar, bağlantılar, görüntüler ve front matter (---) işlenir. UYARI setext başlıklar
# (=== / ---) ve başvuru bağlantıları ([metin][1]) yazıldığı gibi çıkar.
#
# Ne beklenmeli:
#   - Görüntüler yalnızca bu uygulamanın bir sekmesinde yerinde görünür (z2-img ile kitty
#     grafikleriyle çizilir). ssh üzerinden bunun yerine başlık ve yol gelir.
#   - Bağlantılara dokunulabilir (OSC 8). Bu özelliği olmayan bir terminal onları metin olarak gösterir.
#   - Bu terminal italik çizmez, bu yüzden *vurgu* altı çizili olur (man’in yaptığı gibi).
#   - Ekrana sığmayacak kadar geniş bir tablo "başlık: değer" satırlarına bölünür.
#   - -v, bu cihazdaki görüntüleri sayfaya gömer (her biri en fazla 1,5 MB).
#     Okuma ekranı ağdan hiçbir şey almaz, bu yüzden http(s) görüntüleri
#     başlık olarak kalır.
""",
            "macro.mdBody.cCollect" to "# Girdiyi topla (birden çok dosya verilen sırayla tek bir okuma olur).",
            "macro.cTitle" to "# Başlık: -t, yoksa ilk # başlığı, yoksa dosya adı",
            "macro.cWidth" to "# Ekran genişliği",
            "macro.cColorEnv" to "# Renklerin bir borudan geçmesi gerektiğinde (veya gerekmediğinde) bir çıkış yolu, örn. bir sayfalayıcıya",
            "macro.cImgWhere" to "# Bir görüntü yalnızca z2-img varken bir sekmede (kitty grafikleri) yerinde görünebilir.",
            "macro.cAmbi" to "# Belirsiz karakterlerin (çizgiler, oklar, daire içi rakamlar) hangi genişlikle sayılacağı. Ayarlar\n" +
            "# onları tam genişlikte sayıyorsa Z2_MD_AMBIWIDTH=2 kullanın. Varsayılan 1, uygulama gibi.",
            "macro.cAwkFile" to "# UYARI awk programı bir DOSYAYA yazılır ve -f ile verilir. Bağımsız değişken olarak\n" +
            "#   verilirse gawk, çok baytlı bir karakterin (bu notlardaki Japonca gibi)\n" +
            "#   4096 baytlık okuma sınırına denk geldiği yerde programı keser ve\n" +
            "#   \"unexpected newline or end of string\" ile ölür.",
            "macro.cInlineImg" to "# Görüntüleri okuma ekranının sayfasına data: URI olarak göm.\n" +
            "# UYARI z2-view ağdan hiçbir şey almaz (JavaScript ve yüklemeler kapalı), bu yüzden\n" +
            "#   gömülmeyen bir görüntü basitçe görünmez. Tüm sayfa için 4 MB.",
            "macro.cImgLoop" to "# Yalnızca görüntülü bir belge satır satır okunur (z2-img’yi yerinde çağırmak için)",
            "macro.mdBody.mUsageHint" to "bkz. md.sh -h",
            "macro.mdBody.mNoTmp" to "geçici klasör oluşturulamadı",
            "macro.mdBody.mUnreadable" to "dosya okunamıyor:",
            "macro.mdBody.mEmpty" to "okunacak bir şey yok",
            "macro.mWidthNum" to "-w bir sayı ister",
            "macro.mNoDir" to "konulacağı klasör oluşturulamadı",
            "macro.mConvFail" to "dönüştürme başarısız oldu",
            "macro.mNoView" to "burada z2-view yok (uygulamanın güncellenmesi gerekir). HTML şurada:",
            "macro.mImgLabel" to "[görüntü]",
            "menu.noApps" to "(uygulama bulunamadı)",
            "menu.cat.AudioVideo" to "Ses ve video",
            "menu.cat.Development" to "Geliştirme",
            "menu.cat.Graphics" to "Grafik",
            "menu.cat.Game" to "Oyunlar",
            "menu.cat.Network" to "İnternet",
            "menu.cat.Office" to "Ofis",
            "menu.cat.System" to "Sistem",
            "menu.cat.Utility" to "Donatılar",
            "menu.cat.Other" to "Diğer",
            "menu.usage" to "kullanım: z2menu [list]   (bağımsız değişkensiz: openbox boru menüsü XML’i)",
            "reminderView.labels" to "Hatırlatıcılar|Ekle|Sil|İçerik|Tarih ve saat|Tekrar|Bir kez|Her gün|Hafta içi|Her hafta (seçilen gün)|Zamanlama metni (isteğe bağlı; tarih ve tekrarın yerine geçer)|Zamanlanmış|Tekrarlı|Bildirildi|Hatırlatıcı yok|Bu hatırlatıcı ve bekleyen bildirimleri silinsin mi?",
            "rssFeeds.rssFeedsLabel" to "Abonelikler",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} başlamadı. Bkz. /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: desteklenen paket yöneticisi (apk/apt-get/pacman) bulunamadı.",
            "scan.mInstalling" to "z2scan: kuruluyor:",
            "scan.mInstallFail" to "z2scan: kurulamadı:",
            "scan.mRemoteBlocked" to "z2scan: uzak hedef reddedildi. Yalnızca localhost’u kontrol edin veya test etme yetkiniz olan bir hedef için --allow-remote verin.",
            "scan.mRemoteWarn" to "z2scan: UYARI uzak bir ana bilgisayar taranıyor. Yalnızca açıkça test etme yetkiniz olan sistemleri tarayın.",
            "scan.mNoLynis" to "z2scan: lynis bulunamadı; yerleşik kendi kendine tanılamaya (\"z2scan self\") geçiliyor. \"z2scan setup\" ile kurun.",
            "scan.mNoCve" to "z2scan: CVE tarayıcısı (trivy/grype) bulunamadı. trivy veya grype kurup yeniden deneyin.",
            "scan.mSelfHead" to "== z2scan kendi kendine tanılama (bu cihaz / localhost) ==",
            "scan.mUidRoot" to "uid 0 olarak çalışıyor (not: proot/z2root sahte bir root verir)",
            "scan.mPubPort" to "TCP bağlantı noktası tüm arayüzlerde dinliyor (yalnızca localhost değil), bağlantı noktası",
            "scan.mSshdEmpty" to "sshd_config boş şifreyle girişe izin veriyor (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config şifreyle kimlik doğrulamayı açıyor (PasswordAuthentication yes); anahtar daha güvenli",
            "scan.mSshdRoot" to "sshd_config root girişine izin veriyor (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys izinleri fazla geniş, mod",
            "scan.mSshDir" to "~/.ssh izinleri fazla geniş, mod",
            "scan.mWorldWrite" to "herkesin yazabildiği dosyalar bulundu (en fazla 20 gösterilir):",
            "scan.mSuid" to "SUID ikili dosyaları var (bilgi için; proot/z2root ile root sahtedir):",
            "scan.mPath" to "PATH boş bir öğe/\".\" içeriyor (PATH’te geçerli klasör risklidir)",
            "scan.mFound" to "bulgular:",
            "scan.mClean" to "belirgin bir sorun bulunamadı.",
            "scan.mSaved" to "z2scan: temel durum kaydedildi:",
            "scan.mSaveFail" to "z2scan: temel durum yazılamadı:",
            "scan.mNoBase" to "z2scan: henüz temel durum yok. Geçerli durumu kaydetmek için bir kez \"z2scan self --save\" çalıştırın.",
            "scan.mNoChange" to "[ OK ] temel durumdan beri değişiklik yok.",
            "scan.mAdded" to "== temel durumdan beri yeni ==",
            "scan.mRemoved" to "== temel durumdan beri kayboldu ==",
            "scan.mCleared" to "z2scan: temel durum temizlendi.",
            "scan.mLangDiff" to "z2scan: UYARI temel durum başka bir dilde kaydedildi, bu yüzden her şey değişmiş görünecek. \"z2scan self --save\" ile yeniden kaydedin.",
            "scan.langTag" to "tr",
            "scan.usageText" to """
        z2scan - bu cihazın / localhost’un güvenlik açığı testi (savunma amaçlı, hiçbir veri dışarı çıkmaz).

        Varsayılan olarak yalnızca localhost’u tarar. Tarayıcılar dağıtımınızın resmî paketlerinden
        kurulur (nmap/lynis/trivy); hiçbir şey paketlenmemiştir ve sonuçlar yerelde kalır.

          z2scan self [--save]         yerleşik kendi kendine tanılama (harici araç yok): açık bağlantı noktaları,
                                       sshd yapılandırması, SSH anahtar izinleri, herkesin yazabildikleri/SUID, PATH.
                                       --save sonucu temel durum olarak da kaydeder.
          z2scan diff [--quiet]        kendi kendine tanılamayı yeniden çalıştırır ve yalnızca temel durumdan
                                       beri değişeni gösterir. Yeni bir şey olduğunda 1 ile çıkar,
                                       böylece doğrudan z2-when’e bağlanır. --quiet hiçbir şey
                                       değişmediğinde hiç yazdırmaz.
          z2scan baseline [clear]      kayıtlı temel durumu gösterir (veya siler)
          z2scan setup                 tarayıcıları (nmap, lynis) apk/apt-get/pacman ile kurar
          z2scan net [--allow-remote] [target]
                                       nmap TCP taraması. Varsayılan hedef 127.0.0.1’dir.
                                       Yerel olmayan bir hedef --allow-remote gerektirir.
          z2scan host                  lynis ile ana bilgisayar denetimi (yoksa "self"e geçer)
          z2scan cve                   varsa trivy/grype ile rootfs’in bilinen CVE taraması

        Yalnızca açıkça test etme yetkiniz olan sistemleri tarayın.
    """.trimIndent(),
            "translationMacro.description" to "Bir metni ayrıca kurulmuş bir komutla çevirir.",
            "translationMacro.missing" to "trans komutu bu yerel ortamda kurulu değil. Çeviri aracınızı kendiniz kurup yeniden deneyin. Otomatik olarak hiçbir şey indirilmez.",
            "translationMacro.network" to "Çevrilecek metin aracınızın kullandığı harici hizmete gönderilir. O hizmetin koşulları ve ağın kullanılabilirliği geçerlidir.",
            "translationSetup.setup" to "Aynı yerel Linux ortamında bir terminal açın, aşağıdaki komutu kendiniz çalıştırın, sonra çeviriyi yeniden deneyin.",
            "translationSetup.alpineRepo" to "Paket bulunamazsa /etc/apk/repositories içinde aynı Alpine sürümünün community deposunu açın.",
            "translationSetup.ubuntuRepo" to "Ubuntu’da paket bulunamazsa universe deposunu açıp yeniden deneyin.",
            "translationSetup.unknown" to "Dağıtım tanınmadı. Yerel Linux ortamınız için komutu seçin:",
            "voice.head" to """# voice.sh — "Sesli komut"un alıcısı: söyledikleriniz müziği, ses düzeyini, zamanlayıcıları, hatırlatıcıları, hava durumunu ve daha fazlasını kontrol eder.
#
# Hazırlık (bir kez):
#   sh ~/.z2term/macros/voice.sh setup    4 kural kaydeder (Sesli komut / Sesli komut (kilitli) / Sesli zamanlayıcı / Adım say)
#   Adım say yalnızca "bugün kaç adım" için adım sayacını çalışır tutar; hiçbir şey çalıştırmaz
#   sonra hızlı ayarlardaki "Sesli komut" kutucuğundan veya dijital asistandan (güç tuşuna uzun basma vb.) konuşun
#
# Terminalden:
#   voice.sh words                 kelime tablosunu açar (~/.z2term/voice/words-<lang>.txt; ifade ekleyin veya değiştirin)
#   voice.sh try <ifade>           konuşmadan bir ifadeyi dener (örn. voice.sh try sonraki şarkı)
#   voice.sh listen                dinleme ekranını açar ve konuşursunuz (kutucuk gibi)
#   voice.sh lang [dil]            kelime tablosunun dili (voice.sh lang - listeler)
#   voice.sh ai [off|codex|claude|agy]   YZ kullanılsın mı (başta kapalı)
#   voice.sh model [ad]            YZ modeli (boş = o YZ’nin varsayılanı)
#   voice.sh setup / help
#
# YZ başta kapalıdır. Açıkken bile yalnızca anahtar kelimeyle ("YZ", "soru" ...) başlarsanız sorulur.
# Hava durumu yalnızca "hava durumu" derseniz wttr.in’e sorulur (yer: ~/.z2term/voice/config içinde weather_city=).
# Kendi kelimeleriniz: ~/.z2term/voice/commands.txt içinde "kelimeler = komut" (kelime tablosundan önce kontrol edilir)
# Aranacak adlar: ~/.z2term/voice/contacts.txt içinde "ad numara" / kayıt: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — başlangıç makrosu. Cihazda olanlara tepki verir.",
                "# Beklemeyi uygulama yapar (z2-when), bu yüzden kalıcı değildir: bir kez çalışır ve durur.",
                "# Hazırlık: Ayarlar -> \"Sistem olayı algılama\" açık",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (kulaklık: 'event:headset_*' ile ikinci bir kural)",
            ),
            "macro.watchBasic.2" to listOf(
                "# Olan şey Z2_WHEN_EVENT içinde gelir (z2-when events adları listeler).",
                "# Elle denemek için:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT boş. Elle denemek için: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Şarj başladı\" ;;",
                "  power_disconnected) z2-toast \"Şarj durdu\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — pil azaldığında geçerli duruma göre uyarır.",
                "# Ekranın açık olup olmadığını z2-state ile öğrenir: açıksa kısa mesaj, değilse bildirim.",
                "# Hazırlık: Ayarlar -> \"Sistem olayı algılama\" açık",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when seviyeyi verir; yoksa z2-state, böylece elle de çalışır.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Şarj olurken bir şey söyleme (gerçekte azalmıyor)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Pil %${d}{level}\"",
                "else",
                "  z2-notify -h \"Pil azaldı\" \"%${d}{level} kaldı\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — her sabah pili ve bağlantıyı sesli okur.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Sistem alarmı uyandırır, bu yüzden Doze’da da tetiklenir (birkaç dakika gecikebilir).",
                "# Algılama anahtarlarına bağlı değildir.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobil; fi",
                "z2-say \"Günaydın. Pil yüzde ${d}{level}, ağ ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — bildirimlerden tek kullanımlık bir kodu kopyalar, sonra pano hâlâ",
                "# aynı değeri tutuyorsa TTL saniye sonra temizler.",
                "# Hazırlık: Ayarlar -> \"Bildirim algılama\" açık + sistemin bildirim erişimini verin",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ hassas bildirimlerdeki kodları gizleyebilir. Güvenilir bir yol için",
                "#    SMS türevini (otp-sms.sh) kullanın — SMS metni asla gizlenmez.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — alınan SMS’lerden tek kullanımlık bir kodu kopyalar, sonra pano hâlâ",
                "# aynı değeri tutuyorsa TTL saniye sonra temizler.",
                "# otp-clip.sh’nin SMS türevi. Bildirimlerin aksine SMS metni hassas bildirim korumasıyla",
                "# (Android 15+) asla gizlenmez ve telefon kilitliyken de çalışır.",
                "# Hazırlık: Ayarlar -> \"SMS algılama\" açık + sistemin SMS iznini verin",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — arama bildirimlerinden numaraları kopyalamak için bir bildirim gösterir.",
                "# \"Kopyala\" düğmesi numarayı panoya koyar (neden dokunuş bekliyor: aşağıya bakın).",
                "# Hazırlık: Ayarlar -> \"Bildirim algılama\" açık + sistemin bildirim erişimini verin",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Cevapsız aramaları da yakalamak için ikinci bir kural kaydedin (aynı betik, başka kategori):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — akışları kontrol eder ve yalnızca yenileri bildirim ve metin olarak tutar.",
                "# Diğer örneklerin aksine bu **kalıcı değildir**: \"bir zaman tetikleyicisinden",
                "# bir kez çalışır ve durur\" türündendir.",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — rss.sh’nin topladıklarını en yeniden başlayarak, her dokunuşta bir makale açar.",
                "# Durum widget’ında bir makro düğmesi olarak görünür, böylece her dokunuş sonrakini açar.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — size bildirimle hatırlatır: bir kez (30m sonra, 18:30’da) veya",
                "# tekrarlı (her gün / hafta içi / her hafta). Uygulama kapalıyken de tetiklenir (bir sistem",
                "# alarmı uyandırır; algılama anahtarı gerekmez). Bildirimin düğmeleriyle ertelenir.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — bir metni veya dosyayı QR koduna çevirir: burada çizilir veya PNG olarak kaydedilir.",
                "# Bir şeyi yeniden yazmadan başka bir cihaza verin. Bir kez çalışır, kalıcı değildir.",
            ),
            "macro.md" to listOf(
                "# md.sh — bir Markdown dosyasını burada biçimlendirilmiş okur veya uygulamada açar.",
                "# Başlıklar, listeler, tablolar, kod, bağlantılar; görüntüler yerinde görünür. Bir kez çalışır, kalıcı değildir.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Gömülmemesi gereken satır başına bir akış veya kelime (# ile başlayan satırlar atlanır).",
                "# URL’nin veya başlığın bir parçası yeterli. Her eşleşme kendi bildirimini alır.",
                "# örn.", "#example.org",
            ),
            "macro.usageUpdate" to listOf(
                "  update [ad|all]     düzenlemediğin kopyaları daha yeni yerleşik sürümle değiştir",
                "  hold <ad>           olduğu gibi bırak (güncelleme ya da bildirim yok)",
                "  unhold <ad>         olduğu gibi bırakmayı bırak (liste: ~/.z2term/macros/.hold)",
            ),
            "macro.usage" to listOf(
                "kullanım: z2-macro <alt komut>",
                "  list                paketlenmiş örnekleri listeler (yeni / aynı / farklı olarak işaretli)",
                "  install <name|all>  ~/.z2term/macros/ içine kopyalar (asla üzerine yazmaz; zorlamak için -f)",
                "  remove <name>       paketlenmiş bir örneğin kopyanızı kaldırır (farklıysa durur; zorlamak için -f)",
                "  diff <name>         kopyanızla paketlenmiş olan arasındaki fark (sizinki solda)",
                "  show <name>         betiği gösterir",
                "  run <name>          burada çalıştırır (durdurmak için Ctrl-C)",
                "  dir                 makroların nerede olduğunu gösterir",
                "  actions ...         Android işlem makroları (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Abonelikler", "Ekle", "Makaleler", "Düzenle", "Kaldır", "Akış URL’si",
                "Bu abonelik kaldırılsın mı? Toplanan makaleler korunur.",
                "Değişiklikler sonraki kontrolde uygulanır. Toplanan makaleler korunur.",
                "Abonelik yok. Bir RSS veya Atom URL’si girmek için Ekle’yi kullanın.",
                "Boşluk veya kimlik bilgisi içermeyen bir HTTP(S) URL’si girin (en fazla 2048 karakter).",
                "Bu abonelik zaten var.",
                "Bu abonelik değişti. Listeyi yenileyip yeniden deneyin.",
                "Başka bir değişiklik kaydediliyor. Yeniden deneyin.",
                "Önceki", "Sonraki",
            ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols2" to 12,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の Türkçe 版。上書きしていない文言は英語で出る。 */
private class ApiMsgTr(d: String) : Z2ApiMsg("tr", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — bir belge seçer; onu HOME’a kopyalar ve yolunu yazdırır.\n# z2-file save FILE [MIME] — bir hedef seçer ve bir kopya kaydeder.\n# İptal etmek hata sayılır. En fazla 512 MiB; seçim 5 dakika sonra zaman aşımına uğrar."

    override val usbHelp: String get() = """
        |# z2-usb list              … telefona bağlı USB cihazlarını listeler
        |# z2-usb allow [numara]    … Android’den bir cihazı Linux programlarının kullanmasına izin vermesini ister
        |# Bir cihazı bağladıktan sonra allow’u bir kez çalıştırın. İzin bağlantı kesilene kadar sürer.
    """.trimMargin()

    override val usbUsage: String get() = "kullanım: z2-usb list | z2-usb allow [numara|yol|VID:PID]"

    override val usbNoDevices: String get() = "Telefona bağlı USB cihazı yok."

    override val usbNeedSelector: String get() = "Birden fazla USB cihazı bağlı; z2-usb list’teki numarasını verin."

    override fun usbPermissionRequested(path: String): String = "USB erişimini Android ekranında onaylayın: $path"

    override fun usbAlreadyAllowed(path: String): String = "USB erişimine zaten izin verildi: $path"

    override fun usbNotFound(selector: String): String = "USB cihazı bulunamadı: $selector"

    override val usbStateAllowed: String get() = "izin verildi"

    override val usbStateNeedsAllow: String get() = "izin gerekiyor"

    override val notifyHelp: String get() = """
        |# z2-notify [-h|-l] [-n NAME] [-c TEXT] [-b LABEL]... "başlık" "metin"  /  z2-notify [-h|-l] "metin"
        |#   -h / --high / --banner : ekranın üstünde bir afiş (açılır) olarak gösterir
        |#   -l / --low / --quiet   : sessizce gösterir (ses, titreşim ve afiş olmadan)
        |#   -b <etiket>            : bir yanıt düğmesi ekler (en fazla 3). Birine basmak
        |#                            ~/.z2term/events.jsonl’e notify_action ekler
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <ad>                : bu bildirimin tanımlayıcısı (yanıtları ayırt etmek için)
        |#   -c <metin>             : <metin>’i panoya koyan bir "Kopyala" düğmesi ekler.
        |#                            Makro arka planda çalışırken z2-clip set yerine bunu
        |#                            kullanın: Android 10+ panoya yalnızca öndeki uygulamanın yazmasına izin verir,
        |#                            bu yüzden kimse bakmıyorken z2-clip set yok sayılır.
    """.trimMargin()

    override val notifyUsage: String get() = "kullanım: z2-notify [-h|-l] [-n ad] [-c metin] [-b etiket]... <başlık> [metin]"

    override val toastHelp: String get() = """
        |# z2-toast <mesaj> … ekranın altında kısa bir mesaj (toast).
        |# Kendiliğinden kaybolur ve geride bir şey bırakmaz. Kalması gerekiyorsa
        |# veya basılacak bir düğme istiyorsanız z2-notify kullanın.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … QR araçlarını kamera zaten tararken açar.
        |# Taramadan sonra bir düğme içeriği kendi uygulamasında açar (tarayıcı, LINE ve diğer uygulamalar,
        |# tuş takımı, e-posta, SMS, Wi-Fi, kişiler, takvim). Hiçbir şey kendiliğinden açılmaz.
        |# Bunu bir Hızlı Ayarlar kutucuğuna veya kenar paneline koyun.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … bir QR kodu oluşturur.
        |#   Kurulacak bir şey yok: kodlamayı uygulama yapar (QR ekranıyla aynı kodlayıcı).
        |#   TEXT olmadan standart girdiyi okur. -o PNG’nin gideceği yerdir
        |#   (varsayılan ~/.z2term/qr/qr.png) ve yol yazdırılır. -t bunun yerine onu burada
        |#   blok karakterlerle çizer. -p hedef piksel boyutudur (varsayılan 600),
        |#   -m modül olarak sessiz bölgedir (varsayılan 4). En fazla 2000 bayt.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <metin> … bir metni Android paylaşım menüsüne verir (başka bir uygulamaya göndermek için).
        |# --file FILE [FILE ...] : dosya içeriklerini gönderir (en fazla 32 dosya, toplam 512 MiB).
        |# Yollar geçerli yerel kabukta okunur. -- metin modunu zorlar. 24 saatten eski kopyalar sonraki paylaşımda temizlenir.
        |# Tüm bağımsız değişkenler tek bir metin olur. Hedef uygulama ekranda seçilir,
        |# bu yüzden birinin orada olması gerekir — kendi başına çalışan bir makro için değildir.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [başlık] … burada yapılan bir sayfayı z2term’de okur.
        |# Sunucu yok, tarayıcı yok: dosya uygulamaya verilir ve olduğu gibi gösterilir.
        |# JavaScript ve ağ yüklemeleri kapalıdır, bu yüzden sayfadaki hiçbir şey kod çalıştıramaz veya dışarıya ulaşamaz.
        |# http:// ve https:// bağlantıları her zamanki tarayıcınızda açılır.
        |# Telefonun teması CSS değişkenleri olarak gelir (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), böylece onlarla yazılmış bir sayfa terminalin renklerini izler.
        |# En fazla 4 MB. Görüntüler gömülü olmalıdır (data: URI); uzaktakiler yüklenmez.
        |# Bir kenar panelindeki mevcut bir type=view öğesine göndermek için --edge PANEL:ÖĞE kullanın.
        |# Kurulu bir makronun yönettiği genel formlar ve işlemler için --controls FILE.json kullanın.
        |# Görünüm öğeleri varsayılan olarak elle yenilenir. view-refresh=auto açılışta run= çalıştırır; z2-view o öğeye geri döner.
        |# Standart girdiyi okumak için - veya bir boru (ls -l | z2-view) kullanın. < ile başlamayan metin eş aralıklı yazıyla,
        |# satır kaydırılarak ve renk kodları atılarak gösterilir; --text / --html açıkça seçer.
        |# İki parmak hareketi metni boyutlandırır; --pinch page bunun yerine tüm sayfayı yakınlaştırır. --text-size 50..300 bu sayfanın
        |# metin boyutunu (yüzde) sabitler. İkisi de tam ekranda geçerlidir; bir panel öğesinin kendi ayarları vardır.
        |# --no-refresh / --no-find tam ekranda Yenile / Bul düğmelerini gizler (varsayılan olarak gösterilir).
    """.trimMargin()

    override val viewUsage: String get() = "kullanım: z2-view [--edge PANEL:ÖĞE] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <file|-> [başlık]"

    override val viewUnreadable: String get() = "z2-view: dosya okunamıyor:"

    override val openHelp: String get() = """
        |# z2-open <url|yol> … varsayılan uygulamayla açar (https://… veya /sdcard/…).
        |# Hangi uygulamanın açacağını Android seçer; z2term yalnızca onu verir.
        |# Yol **telefondaki** bir yoldur (/sdcard/…), dağıtımın içindeki değil.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <file|->… … görüntüyü terminalde çizer.
        |#   PNG / JPEG / WebP / GIF / BMP. Görüntüyü stdin’den okumak için - verin.
        |#   -w <cols>  terminal sütunu olarak genişlik (varsayılan: terminale sığdır)
        |#   -r <rows>  terminal satırı olarak yükseklik (-w ile boyut olduğu gibi kullanılır)
        |#   --clear    şimdiye kadar çizilen tüm görüntüleri kaldırır
        |#   -f         stdout terminal değilken de gönderir
        |# ⚠ Görüntüler yalnızca bir z2term sekmesinde veya kitty grafik protokolünü
        |#   konuşan bir terminalde görünür. Başka yerde (ssh üzerinden, bir sayfalayıcıda) anlamsız karakterler çıkar.
        |# ⚠ En boy oranı bir hücrenin genişliğinin iki katı yüksek olduğunu varsayar. Görüntü
        |#   basık görünüyorsa ayarlayın: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (küçük = daha uzun).
    """.trimMargin()

    override val imgUsage: String get() = "kullanım: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->..."

    override val imgNoFile: String get() = "dosya okunamadı:"

    override val imgNotTty: String get() = "stdout terminal değil, bu yüzden hiçbir şey gönderilmedi (-f yine de gönderir)."

    override val imgNoSize: String get() = "piksel boyutu okunamadı; kare olarak çiziliyor:"

    override val qrUsage: String get() = "kullanım: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: kod oluşturulamadı (metin çok mu uzun? en fazla 2000 bayt)"

    override val clipHelp: String get() = """
        |# z2-clip get        … panoyu stdout’a yazdırır
        |# z2-clip set [text] … metni (verilmezse stdin’i) panoya koyar
        |# ⚠ Android 10+ panoya yalnızca öndeki uygulamanın (veya kullanılan giriş yönteminin)
        |#   dokunmasına izin verir. Arka plandaki bir makrodan tek kelime söylenmeden yok sayılır,
        |#   bu yüzden z2-notify -c <metin> kullanın: her zaman çalışan bir "Kopyala" düğmesi ekler.
    """.trimMargin()

    override val batteryHelp: String get() = "# Seviyeyi / şarj durumunu JSON olarak yazdırır ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (varsayılan 200ms)"

    override val editHelp: String get() = "# z2-edit <file>      … bir metin dosyasını z2term’ün düzenleyicisinde düzenler (düz metin kutusu; Kaydet onu yeniden yazar)"

    override val sayHelp: String get() = "# z2-say <metin>      … cihazın TTS’siyle konuşur (bağımsız değişken yoksa stdin okur)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (varsayılan toggle). status, dış değişiklikler dahil Android’in durumunu okur (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … yalnızca çizilen bölümü yakalar (varsayılan free). Android 11 ve z2term Android actions gerekir."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (varsayılan playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Medya ses düzeyi. Elde edilen geçerli/en yüksek değeri yazdırır."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (varsayılan light). Bir değer okur ve JSON döndürür."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Herhangi bir Android Intent’i başlatır (varsayılan startActivity). Seçenek olmayan ilk bağımsız değişken ACTION’dır.
        |# Serbest pencere varsayılan olarak görevi ve sistem sınırlarını yeniden kullanır. --reuse-task / --os-bounds hâlâ kabul edilir.
        |# --bounds-only mod anahtarları olmadan %80 sınır ister; --os-bounds ile birleştirilemez.
        |# --no-scale bu başlatma için Motorola’nın küçültülmüş serbest pencere isteğini kapatır.
        |# Tek başına -p PKG o uygulamayı başlatır. --window full|freeform|split|ask başlatma isteğini seçer (sistem desteği gerekir).
        |# --profile N uygulamayı (-p/-n) iş profili gibi başka bir profilde açar. Bölünmüş ekran yok. N, kenar panelinin o uygulama için kaydettiği komuttadır.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … cihazın geçerli durumunun tamamı, JSON olarak
        |# z2-state <anahtar>  … yalnızca o değer, ham (doğrudan bir teste girer)
        |# anahtarlar: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# örn. [ "${d}(z2-state charging)" = "true" ] && echo şarj oluyor
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <soru>
        |#   Kişiye bir soru sorar ve yanıtı stdout’a yazdırır.
        |#   Soru **yanıt alanı** olan bir bildirim olarak gelir, böylece
        |#   uygulamayı açmadan bildirim panelinden yanıtlanabilir (arka plandaki
        |#   bir makro da sorabilir).
        |#   -t <sn>    yanıt için ne kadar bekleneceği (varsayılan 300 = 5 dk)
        |#   -H <hint>  yanıt alanında gösterilen etiket
        |#   -d <metin> önerilen yanıt (bildirimde gösterilir)
        |# Bildirimi yanıtlamadan kaydırıp kapatmak veya süreyi aşmak sıfır olmayan bir kodla çıkar
        |# ve hiçbir şey yazdırmaz — böylece "ya da vazgeç" yazabilirsiniz:
        |#   name=${d}(z2-ask "Dal adı?") || exit 1
        |# Yalnızca sizin hazırladığınız seçenekleri sunan z2-notify -b <etiket> ile karşılaştırın.
    """.trimMargin()

    override val askUsage: String get() = "kullanım: z2-ask [-t sn] [-H ipucu] [-d varsayılan] <soru>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Söylediğinizi stdout’a yazdırır. Tanıma **yalnızca cihazda** çalışır; sesiniz
        |#   asla ağa gitmez. Android 12+ ve cihaz içi konuşma tanıma gerekir
        |#   (diliniz için çevrimdışı konuşma verileri).
        |#   z2term ekrandayken konuşun (Android mikrofonu yalnızca ekrandaki uygulamaya verir).
        |#   Konuşmayı bırakınca durur. İlk seferde mikrofon erişimi istenir.
        |#   -w <kelimeler> yalnızca bu kelimeleri (virgülle ayrılmış) kabul eder ve duyulanı yazdırır.
        |#               Yalnızca en iyisi değil, tanıyıcının tüm tahminleri kontrol edilir.
        |#   -l <dil>   tanınacak dil (örn. tr-TR, en-US; varsayılan: cihazın dili)
        |# Çıkış: 0 = tamam / 1 = hiçbir şey duyulmadı veya kelimelerin hiçbiri / 2 = kullanılamıyor (neden stderr’de)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "kullanım: z2-listen [-w kelime,kelime,...] [-l dil]"

    override val listenPrompt: String get() = "Şimdi konuşun… (bırakınca durur)"

    override val listenNeedsAndroid12: String get() = "z2-listen Android 12 veya üstü gerektirir (daha öncesinde tanıma cihazda kalamaz)."

    override val listenBusy: String get() = "Zaten dinleniyor (başka bir z2-listen çalışıyor)."

    override val listenEngineBusy: String get() = "Cihazın konuşma tanıması meşgul (belki başka bir uygulama kullanıyor). Sonra yeniden deneyin."

    override val listenEngineStuck: String get() = "Konuşma tanıma yanıt vermiyor. Sürerse Android Ayarları › Uygulamalar’dan cihazın tanıma uygulamasını (Android System Intelligence gibi) durmaya zorlayın."

    override val listenStopped: String get() = "Dinleme durduruldu."

    override val listenNoOnDevice: String get() = "Bu cihazda yerleşik konuşma tanıma yok. z2-listen sesinizi asla ağa göndermez, bu yüzden burada çalışamaz."

    override val listenNoPermission: String get() = "Mikrofon erişimine izin verilmedi. Sorulması için z2term ekrandayken z2-listen’i yeniden çalıştırın veya Android ayarlarından izin verin."

    override val listenNotShown: String get() = "z2term’ü ekrana getirip yeniden deneyin (Android mikrofonu yalnızca ekrandaki uygulamaya verir)."

    override val listenNoMic: String get() = "Mikrofon kullanılamadı (z2term ekrandan çıktı veya başka bir uygulama kayıt yapıyor)."

    override val listenNoLanguage: String get() = "Bu dilin cihaz içi konuşma verileri kurulu değil. Cihazın sesli giriş ayarlarından indirin (çevrimdışı konuşma tanıma)."

    override val listenNoSpeech: String get() = "Hiçbir şey duyulmadı."

    override val listenTimedOut: String get() = "Süre doldu."

    override fun listenNoMatch(heard: String): String = "Kelimelerin hiçbiri (duyulan: $heard)"

    override fun listenFailed(code: Int): String = "Konuşma tanıma başarısız oldu (hata $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … ekranın bu süre boyunca kendiliğinden kapanmasını önler
        |# z2-screen keepon off          … süre dolmayı beklemeden hemen geri alır
        |# z2-screen status              … geçerli durum JSON olarak
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Tüm sistemin "ekran zaman aşımı" ayarını değiştirir, bu yüzden uygulama arka plandayken
        |# de geçerlidir. Bu, yalnızca uygulama ekrandayken süren araç çubuğundaki ekranı açık
        |# tutma DEĞİLDİR — ona dokunulmaz.
        |# Özgün değer saklanır ve süre dolunca her zaman geri yazılır (uygulama sonlandırılsa
        |# veya cihaz yeniden başlasa bile). Tek seferde en fazla 24h.
        |# "Sistem ayarlarını değiştir" gerekir (Ayarlar > ekran zaman aşımı > izin ver).
        |# örn. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "kullanım: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | komut...> [--off <komut...>] [-l <etiket>] [-i <çizim>]
        |#                           … 1-$tiles numaralı Hızlı Ayarlar kutucuğuna bir şey koyar
        |# z2-tile add <1-$tiles>         … bu yuvanın panele yerleştirilmesini ister (Android 13+)
        |# z2-tile list              … $tiles yuva TSV olarak (yuva / etiket / komut; "-" = boş)
        |# z2-tile clear <1-$tiles|all>   … bir yuvayı boşaltır
        |#
        |# Ne atarsınız
        |#   Ya ~/.z2term/macros/ içindeki **bir makronun dosya adını** ya da yazıldığı gibi çalışan
        |#   **bir komutu** — hangisi olduğu addan anlaşılır.
        |#   Bir makro bağımsız değişken alabilir ("remind.sh ask"): ilk kelime makro olup olmadığına
        |#   karar verir, gerisi ona verilir. Aynı makroyu taşıyan iki yuvanın etiketi aynı
        |#   olur, bu yüzden onlara -l verin.
        |#   Komut ortamda Z2_TILE=<yuva> (makro için Z2_TILE_MACRO da) ile çalışır.
        |#   ⚠ .sh ile biten ve ~/.z2term/macros/ içinde olmayan bir ad burada reddedilir — komut
        |#     olarak PATH’te aranırdı (makro klasörünü içermez),
        |#     böylece kutucuk hiçbir şey yapmaz ve bunu yalnızca tile/run.log’da söylerdi.
        |#
        |# Dokunma ne yapar
        |#   Bir dokunuş başlatır, bir sonraki durdurur (widget düğmeleri gibi).
        |#   Çalışırken kutucuk "etkin" görünür (renk sistemin vurgusudur, bizim değil).
        |#   --off ile **iki komutunuz** olur: dokunma aralarında geçiş yapar ve kutucuk
        |#   açıkken "etkin" görünür. Kapatma ayrı bir komut olduğunda kullanın
        |#   (z2-torch on / off).
        |#   Normal çiftler başarılı geçişleri hatırlar. Doğrudan z2-torch atamaları dış değişiklikler
        |#   dahil Android’in gözlemlediğini izler. z2-screen keepon gerçekten etkin olan ayarını izler.
        |#
        |# Panele yerleştirme
        |#   Android 13+’ta `set`, kutucuğun panele girip girmeyeceğini hemen sorar ve
        |#   o iletişim kutusu **az önce atadığınız adı ve simgeyi** taşır. Hayır derseniz hiçbir şey yerleştirilmez.
        |#   Tam olarak $tiles yuva vardır: sayı manifestte sabittir ve çalışma sırasında
        |#   artamaz. Ataması olmayan yuvalar düzenleme ekranında hiç görünmez,
        |#   bu yüzden $tiles tane olması bir şeye mal olmaz.
        |#   ⚠ Yalnızca z2term öndeyken ve birer birer görünür — iki yuvayı art arda
        |#     atayan bir makro yalnızca ilki için sorar. Kalanlar için sonra
        |#     z2-tile add <yuva> kullanın.
        |#   ⚠ Kutucuğu yerleştirmek hâlâ sizin kararınızdır — Android bir uygulamanın kendi kutucuklarını
        |#     oraya koymasına izin vermez. Hızlı Ayarlar panelinin düzenleme ekranından (kalem)
        |#     **z2term <yuva>** arayın.
        |#   ⚠ O liste atananı değil, manifestteki adı ve simgeyi gösterir (Android’in
        |#     bunları çalışırken değiştirme yolu yoktur), bu yüzden hangi numaranın hangisi olduğunu z2-tile list söyler.
        |#
        |# Çizim (-i)
        |#   Ad belli ettiğinde bir yuva kendiliğinden uygun bir simge alır (remind.sh -> bir
        |#   saat) ve z2-icon onu istediğinizle değiştirir — sonra bir daha dokunulmaz.
        |#   -i, o iletişim kutusu görünmeden **önce** çizimi ayarlar, böylece tek satır
        |#   adı ve simgeyi birlikte belirler (z2-icon sample kullanılabilir adları listeler).
        |#   ⚠ Bilinmeyen bir ad **herhangi bir atamadan önce** reddedilir — ayarlanmış ama
        |#     yanlış çizimli bir yuva hiç ayarlanmamış olandan daha az fark edilir.
        |#
        |# örn. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "ekran açık"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l fener
        |#      z2-tile set 4 backup.sh -l yedek -i sync
    """.trimMargin()

    override val tileUsage: String get() = "kullanım: z2-tile set <1-$tiles> <macro.sh|komut...> [--off <komut...>] [-l etiket] " +
                "[-i çizim] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: böyle bir çizim yok: $name (z2-icon sample ile listeleyin). Hiçbir şey atanmadı."

    override fun tileAddEmpty(n: Int): String = "z2-tile: $n numaralı yuvada henüz bir şey yok (önce atayın: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: burada kutucuk eklenmesi istenemiyor (Android 13 ve z2term’ün önde olması gerekir). " +
                "Yuva atandı — onu Hızlı Ayarlar panelinin düzenleme ekranından (kalem) yerleştirin."

    override val tileAddAsked: String get() = "Android’den kutucuğu eklemesi istendi — iletişim kutusunu yanıtlayın. " +
                "(Bir şey görünmedi mi? Bunun yerine düzenleme ekranından yerleştirin.)"

    override val iconHelp: String get() = """
        |# Durum çubuğu ve Hızlı Ayarlar kutucuğu simgelerini kendi piksel çiziminizle değiştirir.
        |#
        |# Buradan başlayın
        |#   z2-icon pick 1     ... listeden bir çizim seçip 1. kutucuğa koyar
        |#   z2-icon list       ... şu an her yuvada ne var ("-p" çizimleri de gösterir)
        |#
        |# <hedef> ne demek
        |#   notify    bu uygulamanın her bildiriminin simgesi (durum çubuğundaki)
        |#   1-$tiles      Hızlı Ayarlar kutucuklarının bir yuvası - z2-tile ile aynı numara
        |#
        |# Çizim koyma
        |#   pick <hedef>             listeleyip numarayla seçin (en kolay yol)
        |#   sample <hedef> <ad>      adı zaten biliyorsanız
        |#   edit <hedef>             ${d}EDITOR içinde çizin veya mevcut olanı düzeltin
        |#   set <hedef> <dosya>      bir dosyadan okuyun (stdin için "-")
        |#   auto <1-$tiles|all>          kutucuğun çalıştırdığına göre yeniden seçin
        |#   clear <hedef|all>        yerleşik simgeye dönün
        |#
        |# Izgaranın inceliği
        |#   grid                     yeni çizimlerin boyutu
        |#   grid <$grids>          ayarlayın (değiştirmezseniz $grid)
        |#   scale <hedef> <$grids>   <hedef>in çizimini o ızgaraya taşıyın
        |#                            (bu sırada çapraz basamakları yarıya indirerek). **Daha ince
        |#                            çizmeye yer açar** - üretilen simge zaten
        |#                            yumuşatılır, bunun için yapmaya gerek yok.
        |#
        |# Kendi çizimlerinizi listede tutma
        |#   save <hedef> <ad>        <hedef>teki şeye bir ad verip listeye ekleyin
        |#   sample                   seçilecek liste (numara / ad /
        |#                            "builtin" = paketlenmiş, "mine" = sizin kaydettiğiniz)
        |#   sample <ad>              o çizimi gösterin
        |#   forget <ad>              kendinizinkilerden birini listeden çıkarın (zaten bir
        |#                            hedefe koyduğunuz kalır)
        |#
        |# Nasıl çizilir
        |#   Bir karakter ızgarasıdır. "." "" "0" "-" "_" bir noktayı boş bırakır, diğer her karakter
        |#   onu doldurur, bu yüzden en iyi gördüğünüzü kullanın. Çizimin etrafındaki boşluk
        |#   yok sayılır - ortalanır, her satırı tam doldurmaya
        |#   gerek yok. Izgara $grids’dir ("z2-icon grid").
        |#   ⚠ Daha büyük ızgara daha büyük simge yapmaz. Izgarayı doldurun, yoksa çizim
        |#     değiştirdiğinden daha küçük çıkar.
        |#
        |# Bilmekte fayda var
        |#   ⚠ Yalnızca şekil geçer. Android bu simgeleri kendi tek rengiyle boyar
        |#     (kutucuklar etkin ve devre dışı arasında renk değiştirir), bu yüzden seçilecek renk yoktur.
        |#   ⚠ **Kenar çıkışta yumuşatılır.** $grid noktada yapılan bir çizim
        |#     bir kutucukta (durum çubuğundan çok daha büyük çizilir) yine pürüzsüz çıkar,
        |#     bu yüzden ızgara günlük kullanımda sorun değildir. 48 / 64, **siz** daha ince
        |#     çizmek istediğinizde içindir. "show" çizimi yaptığınız gibi gösterir; kutucuk daha pürüzsüzdür.
        |#   ⚠ Ad belli ettiğinde bir kutucuk kendiliğinden çizim alır ("z2-tile set 1
        |#     remind.sh" bir saat koyar). Burada ayarladığınız kazanır ve bir daha dokunulmaz
        |#     — "z2-icon auto 1" o yuvayı otomatik seçime geri verir.
        |#   ⚠ İki simge değiştirilemez: Hızlı Ayarlar **düzenleme** ekranındaki (kutucukları oradan
        |#     sürüklersiniz) ve başlatıcı simgesi. Android bunları kurulumda sabitler.
        |#
        |# örn. z2-icon pick 1               1. yuvanın çizimini seç
        |#      z2-icon edit 1              kendiniz yeniden çizin
        |#      z2-icon save 1 my-face      o çizime ad verip listeye ekleyin
        |#      z2-icon sample 3 my-face    aynı çizimi 3. yuvaya koyun
        |#      z2-icon grid 64             bundan sonra yenileri 64x64 ızgarada çizin
        |#      z2-icon scale 1 48          daha ince çizmek için 1. yuvayı 48x48 ızgaraya taşıyın
        |#      z2-icon list -p             neyin nerede olduğunu çizimlerle kontrol edin
        |#      z2-icon clear notify        bildirim simgesini geri yükleyin
    """.trimMargin()

    override val iconUsage: String get() = "kullanım: z2-icon pick <notify|1-$tiles> | sample [ad|hedef ad] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [dosya|-] | " +
                "save <notify|1-$tiles> <ad> | forget <ad> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "dosya bulunamadı:"

    override val iconEditUnchanged: String get() = "değişmedi."

    override val iconPickPrompt: String get() = "numara (veya ad), iptal için boş: "

    override val iconPickCancelled: String get() = "iptal edildi."

    override val iconNoEditor: String get() = "düzenleyici bulunamadı. ${d}EDITOR ayarlayın veya şunu kullanın: z2-icon set <hedef> <dosya>"

    override val tileNoSuchMacro: String get() = "bu makro ~/.z2term/macros/ içinde değil (komut olarak çalıştırmak için tam yol kullanın):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [paket] / trace dump / trace stop
        |# İzleme: önce yakalamayı açın; 5 dakika, son 128 olay, yalnızca bellekte.
        |# Alan türleri ve UTF-16 uzunlukları, asla metin değerleri değil. dump/stop JSON döndürür; start sıfırlar.
        |# z2-noti list  … şu an gösterilen bildirimler, TSV olarak
        |#                 (anahtar / paket / uygulama adı / başlık / gövde)
        |# Yalnızca okuma. Bir bildirime basmanın veya kapatmanın bilerek hiçbir yolu yok:
        |# diğer uygulamaların öde ve gönder düğmelerine de basardı.
        |# Bildirim erişimi gerekir (Ayarlar > İzinler ve bildirimler).
        |# Ayrıca bkz.: z2-when notify:otp / notify:pkg=<parça> / notify:contains=<parça>
    """.trimMargin()

    override val notiUsage: String get() = "kullanım: z2-noti list | trace start [paket] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM[:SS] [ad]       … bir kez, bir sonraki HH:MM’de (geçtiyse yarın)
        |# z2-alarm daily HH:MM[:SS] [ad]    … her gün HH:MM’de
        |# z2-alarm in <N|Ns|Nm|Nh> [ad] … bir kez, N saniye/dakika/saat sonra
        |# z2-alarm list                … zamanlananları listeler (JSON; "exact" dakikliği gösterir)
        |# z2-alarm cancel <id|ad|all>  … iptal eder
        |# Tetiklenince ~/.z2term/events.jsonl’e bir {"event":"alarm","name":…} satırı eklenir.
        |# Cihazı Doze’dan da uyandırır. "exact":true dakikasında tetiklendiği anlamına gelir;
        |# "exact":false, Doze’un yalnızca 9-15 dakikada bir pencere sunduğu anlamına gelir, bu yüzden ekranı
        |# kapalı bırakılan bir telefon o kadar gecikebilir. Bu uygulama için pil optimizasyonunu kapatmak
        |# onu true yapar (ek izin istenmez).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: kullanılabilir date komutu yok"

    override val sessionHelp: String get() = """
        |# z2-session list                     … sekmeleri listeler (dizin / id / tür / işaret / ad, TSV)
        |#   işaretler: * = ekranda / ! = çalışıyor / ? = başlamadı / @ = bir kabuktan bağlı / - = diğer
        |# z2-session new [ad]                 … bir terminal sekmesi açar (dizini ve id’yi yazdırır)
        |# z2-session send <sekme> <metin>...  … metni o sekmeye yazar (çalıştırmadan)
        |# z2-session send <sekme> <metin> --enter … yazar, sonra çalıştırır
        |# z2-session key <sekme> <tuş>...     … o sekmeye **tuş** gönderir (C-c / M-x / F5 / Up …)
        |#   değiştiriciler: C- (Ctrl) ve M- (Meta=Alt); C-M-a gibi birleşir
        |#   özel: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   C-S-a gibi Shift’li tuşlar reddedilir (terminal Shift’i ayırt edemez: C-a ile aynı baytlar)
        |# z2-session key <sekme> --raw '\x1b[A' … geri kalan her şey, bayt olarak (\xHH \e \n \r \t \0)
        |# z2-session capture [sekme] [--all]  … o sekmenin ekranını alır (--all geçmişi de içerir)
        |# z2-session attach <sekme>           … o sekmeye bağlı kalır ve doğrudan ona yazarsınız
        |#   Ctrl+] ile (her an) veya satır başında ~. ile çıkın (ssh’deki gibi)
        |#   ⚠ SSH üzerinden yalnızca Ctrl+] çalışır: öndeki ssh ~.’yı yutar ve SSH oturumunu kapatır
        |#   satır başında düz ~ için ~~, düz Ctrl+] için ~ sonra Ctrl+] yazın
        |#   bağlıyken sekme SİZİN pencerenizin boyutunu izler; çıkınca eski hâline döner
        |# z2-session close <sekme>            … o sekmeyi kapatır (asla sonuncusunu değil)
        |# z2-session group [list]             … grupları listeler (numara / id / ad / sekme numaraları, TSV)
        |# z2-session group new <ad> [<sekme>]... … bu sekmelerden bir grup oluşturur (numarasını ve id'sini verir)
        |# z2-session group add <grup> <sekme>... … sekmeleri o gruba koyar
        |# z2-session group remove <sekme>...  … sekmeleri gruplarından çıkarır
        |# z2-session group delete <grup>      … grubu dağıtır (sekmeler açık kalır)
        |# z2-session group show <grup>        … o grubu ekranda gösterir
        |# z2-session group send <grup> <metin>... [--enter] … o grubun tüm terminal sekmelerine yazar (çalıştırmaz)
        |# z2-session group broadcast start <grup> [--on|--off] | on | off | stop | status … 📡 yayın: başlat (o grubu gösterir), göndermeyi aç/kapat, bitir veya stop/off/on yazdır
        |# z2-session group broadcast tab <sekme>... on|off … o sekmeleri yayın için işaretle / işareti kaldır (start sonrası)
        |#   <grup>, group list numarası, bir id veya grup adı olabilir. list her sekmenin grubunu 6. sütunda gösterir
        |#
        |# <sekme> list’teki dizin, bir id veya sekme adı olabilir. "." veya verilmezse = ekrandaki sekme.
        |# örn. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "kullanım: z2-session list | new [ad] | send <sekme> <metin>... [--enter] | " +
                "key <sekme> <tuş>... | key <sekme> --raw <baytlar> | capture [sekme] [--all] | " +
                "attach <sekme> | close <sekme> | group list|new|add|remove|delete|show|send"

    override val keyNothing: String get() = "z2-session key: tuş verilmedi"

    override val keyRawEmpty: String get() = "z2-session key: --raw gönderilecek baytları ister"

    override val keyUnknown: String get() = "z2-session key: bilinmeyen tuş (\"z2-session -h\" içindeki listeye bakın):"

    override val keyBadEscape: String get() = "z2-session key: kaçış okunamadı:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: terminal Shift’i ayırt edemez, bu yüzden '$asWritten' " +
            "'$equivalentTo' ile tamamen aynı baytları verirdi. Kastettiğiniz buysa '$equivalentTo' yazın."

    override fun attachNoSuchTab(target: String): String = "böyle bir sekme yok: '$target' (sekmeleri görmek için \"z2-session list\" çalıştırın)"

    override val attachNotTerminal: String get() = "bu bir GUI sekmesi, bağlanılacak kabuğu yok. Bir terminal sekmesi seçin."

    override val attachNotStarted: String get() = "o sekme henüz başlamadı (list’te \"?\" ile işaretli). Uygulamada bir kez açın, sonra bağlanın."

    override val attachExited: String get() = "o sekme zaten bitti. Ekranı \"z2-session capture\" ile hâlâ okunabilir."

    override val attachSelf: String get() = "bu, yazmakta olduğunuz sekme. Bir sekmeyi kendine bağlamak çıktısını sonsuza dek " +
            "girdi olarak geri besler, bu yüzden geri dönüş yolu olmaz. Başka bir sekme seçin."

    override val attachLoop: String get() = "o sekme zaten bu sekmeye geri bağlı, bu yüzden ikisi birbirini " +
            "sonsuza dek beslerdi. Önce birini ayırın (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … kayıtlı sunucular (dizin / id / durum / işaret / ad, TSV)
        |#   işaretler: * = etkin / - = devre dışı
        |# z2-server start <sunucu>      … onu kalıcı sunucu olarak başlatır (cihazı erişilebilir tutar)
        |# z2-server stop <sunucu>       … bunu durdurur (diğerleri çalışmaya devam eder)
        |# z2-server status [<sunucu>]   … durum, pid, yeniden başlatmalar ve son çıkış kodu
        |#
        |# <sunucu> list’teki dizin, bir id veya uygulamada ona verdiğiniz ad olabilir.
        |# Yalnızca uygulamada kayıtlı sunucular başlatılabilir; bu asla yeni bir tane kaydetmez.
        |# Neden var: doğrudan bir kuraldan başlatılan bir arka plan programı kalıcı sunucu çerçevesinin
        |# DIŞINDA çalışır (WakeLock yok, WifiLock yok, ön plan hizmeti yok), bu yüzden ekran kapanınca
        |# yanıt vermeyi bırakır. Buradan başlatmak onu o çerçeveye koyar.
        |# örn. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "kullanım: z2-server list | start <sunucu> | stop <sunucu> | status [<sunucu>]"

    override val serverNotFound: String get() = "z2-server: böyle bir sunucu yok (\"z2-server list\"e bakın):"

    override val serverAmbiguous: String get() = "z2-server: ad birden fazla sunucuyla eşleşiyor; \"z2-server list\"teki id’yi kullanın:"

    override val serverNone: String get() = "z2-server: henüz kayıtlı sunucu yok (uygulamada ekleyin: 📜 -> Sunucular)."

    override val serverLowPowerWarn: String get() = "z2-server: not - düşük güç modu açık, bu yüzden WakeLock/WifiLock tutulmuyor. " +
            "Sunucu ekran kapalıyken yanıt vermeyi bırakabilir " +
            "(Ayarlar -> Otomasyon -> Arka plan işlem koruması)."

    override val whenHelp: String get() = """
        |# z2-when <tetikleyici> run <cmd...>   … bir kural kaydeder
        |#   tetikleyiciler: charge:start | charge:stop  (algılama AÇIK gerekir)
        |#            battery:below=N | battery:above=N  (algılama AÇIK gerekir)
        |#            battery:temp>N | battery:temp<N  … pil sıcaklığı (°C) N’yi geçti (algılama AÇIK gerekir)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='dk saat gün ay haftagünü'  (haftagünü 0-7 / 0,7=Pazar. Tırnak içinde: boşluk var)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<ad>  (algılama AÇIK gerekir)
        |#            net:online | net:offline  … kullanılabilir bir bağlantı belirdi / kayboldu
        |#            net:wifi | net:mobile | net:ethernet  … kullanılan bağlantı buna geçti
        |#                                        (algılama AÇIK gerekir; yalnızca Wi-Fi değil, mobil veriyi de sayar)
        |#            share:any | share:text | share:file | share:contains=<parça> | share:ext=<uzantı>
        |#                                        … başka bir uygulamadan z2term’e bir şey paylaşıldı
        |#            boot                        … cihaz açılışı tamamladı (algılama KAPALI’yken çalışır)
        |#            sms:any | sms:from=<parça> | sms:contains=<parça> | sms:otp  (RECEIVE_SMS gerekir)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (algılama AÇIK)
        |#            file:new=<klasör>[,ext=<uzantı>]  … bu klasöre yeni bir dosya geldi (algılama AÇIK gerekir)
        |#            file:changed=<klasör>[,ext=<uzantı>] | file:deleted=<klasör>[,ext=<uzantı>]
        |#                                        … oradaki bir dosya yeniden yazıldı / kayboldu (algılama AÇIK gerekir)
        |#            bt:connect | bt:disconnect | bt:connect=<ad> | bt:disconnect=<ad>
        |#                                        … bir Bluetooth cihazı bağlandı / ayrıldı (ad: bir parça veya adres)
        |#                                        (algılama AÇIK ve Android 12+’ta Yakındaki cihazlar izni)
        |#            app:open=<uyg> | app:close=<uyg>  … bu uygulama öne geldi / ondan ayrıldınız (paket veya ad, bir parça)
        |#                                        (z2term’ün Erişilebilirlik’te açık olması gerekir; algılama KAPALI’yken çalışır)
        |#            steps:reach=N               … bugünkü adımlar N’ye ulaştı (algılama AÇIK + Fiziksel etkinlik izni)
        |#            notify:any | notify:otp | notify:pkg=<parça> | notify:title=<parça> | notify:contains=<parça>
        |#                                        … bir bildirim geldi (bildirim erişimi gerekir)
        |#            event:<ad> | event:<önek>* | event:*  … ada göre herhangi bir cihaz olayı
        |#              (z2-when events onları listeler; events.jsonl ile aynı adlar)
        |# Ad (isteğe bağlı, tetikleyiciden hemen sonra — run’dan önce):
        |#   name=<metin>            … bu kuralın ne için olduğu, Otomasyon sekmesinde gösterilir
        |#                             (boşluk varsa tırnak içinde). Boş = tetikleyici gösterilir.
        |#                             Yalnızca gösterim içindir: kuralın ne zaman çalıştığını değiştirmez.
        |# Filtreler (herhangi bir tetikleyici, hemen sonra — run’dan önce):
        |#   if=<koşul>[,<koşul>...] … yalnızca cihaz bu durumdayken (VE; ! olumsuzlar)
        |#                             anahtarlar z2-state’in yazdırdıklarıdır: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             örn. if=wifi,!screen / if=ssid=Ev / if=level<30
        |#   if_any=<koşul>[,<koşul>...] … **herhangi biri** doğruysa çalışır (virgül VEYA demektir)
        |#                             if= ile: "if’in hepsi VE if_any’den biri"
        |#   else=<cmd>              … if / if_any karşılanmadığında **bunun yerine** bunu çalıştırır
        |#                             ⚠ buraya yalnızca if / if_any getirir. between /
        |#                             days / cooldown ile atlanan bir çalıştırma hiçbir şey çalıştırmaz — gece
        |#                             kapalı bir kural gece sessiz kalmalıdır, else dahil.
        |#   cooldown=30m            … bu süre içinde yeniden çalıştırma (10s / 30m / 2h)
        |#   between=22:00-07:00     … yalnızca bu aralıkta (gece yarısını geçer)
        |#   days=mon-fri            … yalnızca bu günlerde (adlar veya cron sayıları 0-7, 0/7 = Pazar)
        |#   answer='<soru>=><yanıt>' … komut terminalde bu soruyu sorunca yanıtı ve Enter’ı
        |#                             yazar (tekrarlanabilir; örn. answer='[y/N]=>y'). Çıktının son
        |#                             satırının sonuyla karşılaştırır.
        |#   secret='<soru>'         … gizli bir yanıt (şifre) için aynısı. İçerik uygulamanın
        |#                             kural düzenleyicisine girilir ve orada şifreli kalır.
        |# Atlanan çalıştırmalar da kaydedilir — z2-when fired skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown gösterir, böylece hiç çalışmayan bir kural açıklanabilir. Uygulamanın ▶ düğmesi
        |# tüm filtreleri yok sayar (kuralı denemek içindir).
        |# z2-when events                        … event: ile kullanılabilecek adları listeler
        |# z2-when pause / resume                … otomatik çalıştırmaları durdurur / sürdürür (kurallar kalır)
        |# z2-when fired [n]                     … son tetiklenmeler (saat / id / tetikleyici / run|paused)
        |# z2-when list                          … kayıtlı kurallar (id / on|off / tetikleyici / -> / cmd, TSV;
        |#                                         varsa ad ve filtreler sonda [] içinde)
        |# z2-when remove <id|all>  (rm de)      … siler
        |# z2-when on <id> / off <id>            … etkinleştirir / devre dışı bırakır
        |# z2-when log <id>                      … bu kuralın çalıştırma kaydı (tail)
        |# Tetiklenince komut seçili dağıtımda ortamda Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) ile çalışır.
        |# net: için Z2_WHEN_NET (şimdiki bağlantı) ve Z2_WHEN_NET_PREV (öncekisi) vardır.
        |# share: için Z2_WHEN_SHARE (metin veya alınan dosyaların yolları)
        |# ve Z2_WHEN_SHARE_KIND (text|file) vardır. Paylaşılan yine eskisi gibi giriş satırına da gider.
        |# file: için Z2_WHEN_FILE (tam yol) ve Z2_WHEN_DIR vardır.
        |# notify: için Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY vardır
        |# (ve notify:otp için Z2_WHEN_OTP). notify:category= tam karşılaştırır: call, missed_call, msg, ...
        |# event: için Z2_WHEN_EVENT (olay adı) da vardır; alarm / notify_action ayrıca
        |# Z2_WHEN_EVENT_NAME (ayarlarken verdiğiniz tanımlayıcı) ve Z2_WHEN_ACTION (basılan düğme) ekler.
        |# battery:temp için Z2_WHEN_TEMP (°C) vardır. bt: için Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# app: için Z2_WHEN_APP_PKG (şimdi öndeki uygulama) / Z2_WHEN_APP_PREV (öncekisi).
        |# steps: için Z2_WHEN_STEPS (bugünkü adımlar). file: için ayrıca Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated Z2_WHEN_PKG verir, timezone_changed Z2_WHEN_TZ verir,
        |# usb_attached / usb_detached Z2_WHEN_USB (ad) / Z2_WHEN_USB_ID (üretici:ürün) verir.
        |# Aynı kural 10 saniye içinde iki kez tetiklenmez (screen_on gibi olaylar sık gelir).
        |# örn. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "zil: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Sabah raporu' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Otomatik çalıştırmalar duraklatıldı (sürdürmek için z2-when resume)"

    override val whenResumed: String get() = "Otomatik çalıştırmalar sürdürüldü"

    override val whenNoFires: String get() = "(henüz hiçbir şey tetiklenmedi)"

    override val whenPausedNote: String get() = "# duraklatıldı (sürdürmek için z2-when resume)"

    override val whenNoLog: String get() = "(henüz kayıt yok)"

    override val whenWriteFailed: String get() = "z2-when: kural yazılamadı"

    override val whenUnknownIfKey: String get() = "z2-when: bilinmeyen if= anahtarı (z2-state kullanılabilenleri listeler):"

    override val whenUnknownTrigger: String get() = "z2-when: bilinmeyen tetikleyici (bağımsız değişkensiz z2-when onları listeler):"

    override val whenBadTriggerSpec: String get() = "z2-when: bu tetikleyici o bağımsız değişkeni almaz:"

    override val whenPausedWarn: String get() = "not: otomatik çalıştırmalar duraklatıldı (sürdürmek için z2-when resume)"

    override val whenRunJoined: String get() = "not: komuttaki satır sonları boşluğa çevrildi (bir kural yalnızca tek satır okur)"

    override val whenEventList: String get() = """
        |screen_on              ekran açıldı                               [algılama AÇIK]
        |screen_off             ekran kapandı                              [algılama AÇIK]
        |unlocked               cihazın kilidi açıldı                      [algılama AÇIK]
        |power_connected        şarj başladı                               [algılama AÇIK]
        |power_disconnected     şarj durdu                                 [algılama AÇIK]
        |battery_low            pil azaldı                                 [algılama AÇIK]
        |battery_okay           pil düzeldi                                [algılama AÇIK]
        |battery_level          seviye %10’luk bir adımı geçti             [algılama AÇIK]
        |wifi_connected         Wi-Fi’ye katıldı                           [algılama AÇIK]
        |wifi_disconnected      Wi-Fi’den ayrıldı                          [algılama AÇIK]
        |net_online             çalışan bir bağlantı belirdi               [algılama AÇIK]
        |net_offline            çalışan bağlantı yok                       [algılama AÇIK]
        |net_wifi               kullanılan bağlantı Wi-Fi                  [algılama AÇIK]
        |net_mobile             kullanılan bağlantı mobil                  [algılama AÇIK]
        |headset_plugged        kablolu kulaklık takıldı                   [algılama AÇIK]
        |headset_unplugged      kablolu kulaklık çıkarıldı                 [algılama AÇIK]
        |bt_audio_connected     Bluetooth ses bağlandı                     [algılama AÇIK]
        |bt_audio_disconnected  Bluetooth ses ayrıldı                      [algılama AÇIK]
        |airplane_on            uçak modu açıldı                           [algılama AÇIK]
        |airplane_off           uçak modu kapandı                          [algılama AÇIK]
        |ringer_normal          zil normal moda geçti                      [algılama AÇIK]
        |ringer_vibrate         zil titreşime geçti                        [algılama AÇIK]
        |ringer_silent          zil sessize geçti                          [algılama AÇIK]
        |bt_connected           bir Bluetooth cihazı bağlandı              [algılama AÇIK]
        |bt_disconnected        bir Bluetooth cihazı ayrıldı               [algılama AÇIK]
        |powersave_on           güç tasarrufu açıldı                       [algılama AÇIK]
        |powersave_off          güç tasarrufu kapandı                      [algılama AÇIK]
        |dnd_on                 Rahatsız Etmeyin açıldı                    [algılama AÇIK]
        |dnd_off                Rahatsız Etmeyin kapandı                   [algılama AÇIK]
        |app_installed          bir uygulama kuruldu                       [algılama AÇIK]
        |app_removed            bir uygulama kaldırıldı                    [algılama AÇIK]
        |app_updated            bir uygulama güncellendi                   [algılama AÇIK]
        |timezone_changed       saat dilimi değişti                        [algılama AÇIK]
        |usb_attached           bir USB cihazı takıldı                     [algılama AÇIK]
        |usb_detached           bir USB cihazı çıkarıldı                   [algılama AÇIK]
        |boot                   cihaz açıldı                               [her zaman]
        |alarm                  bir z2-alarm tetiklendi                    [her zaman]
        |notify_action          bir bildirim düğmesi                       [her zaman]
        |unlock_failed          kilit açma başarısız oldu                  [her zaman, hazırlık gerekir]
        |unlock_succeeded       bir hatadan sonra kilit açıldı             [her zaman, hazırlık gerekir]
        |voice                  bir sesli komut duyuldu                    [her zaman, kutucuktan veya asistandan]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # event:<ad> içinde kullanılabilecek adlar. events.jsonl ile aynı sırada.
        |    # Üstteki grup algılama AÇIK gerektirir (Ayarlar > İzinler ve bildirimler),
        |    # alttakini kendiniz ayarlarsınız, bu yüzden algılama KAPALI’yken çalışır.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … denetler, indirir ve yeni sürümü yükleyiciye verir
        |# z2-update --check             … yalnızca daha yeni bir sürüm olup olmadığını söyler
        |# z2-update --keep              … indirilen .apk’yi saklar (varsayılan olarak silinir)
        |# z2-update --dir <klasör>      … uygulamanın klasörü yerine bu klasöre indirir
        |#
        |# Nereden gelir
        |#   z2term’ün GitHub Releases sayfası, elle indireceğiniz dosyanın aynısı
        |#   (z2term-<version>.apk, sürüm anahtarıyla imzalı). Başka hiçbir yere bağlanılmaz,
        |#   bunu çalıştırana kadar da hiçbir yere.
        |#
        |# Ne yapabilir, ne yapamaz
        |#   ⚠ **Sessizce kuramaz.** Kendini değiştiren bir uygulama için Android her zaman kendi
        |#     "kurulsun mu?" ekranını gösterir, bu yüzden son dokunuş her zaman sizindir. Bu komut o
        |#     ekrana kadar olan tüm adımları yapar.
        |#   ⚠ İlk seferde z2term için **"Bilinmeyen uygulamaları yükle"**ye izin verin (size söyler ve
        |#     ⚙Ayarlar’da aynı düğme vardır). Bu olmadan kurulum ekranı hiç görünmez.
        |#   ⚠ F-Droid’den veya bir mağazadan mı kurdunuz? O zaman bu reddeder ve oradan güncellemenizi söyler —
        |#     sahip olduğunuz sürümü değiştirmek onların işidir, bizim değil.
        |#
        |# İndirilen dosya
        |#   Varsayılan olarak uygulamanın klasörüne gider ve kurulum başarılı olunca silinir
        |#   (ve uygulama kurulumun ortasında sonlandırıldıysa sonraki başlatmada — kendini değiştirirken
        |#   bu normaldir). --keep veya --dir bunu değiştirir; ⚙Ayarlar’da oradaki düğme için aynı iki
        |#   ayar vardır.
        |#
        |# örn. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # her gece denetle (yine de size sorar)
    """.trimMargin()

    override val updateUsage: String get() = "kullanım: z2-update [--check] [--keep] [--dir <klasör>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current en son sürüm."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: daha yeni bir sürüm aranıyor ..."

    override val updateHandedToInstaller: String get() = "z2-update: kurulum ekranı cihazda açık — bitirmek için onaylayın."

    override fun updateNoApk(url: String): String = "z2-update: bu sürüme ekli .apk yok. Sürüm sayfasından alın: $url"

    override val updateNeedPermission: String get() = "z2-update: önce z2term için \"Bilinmeyen uygulamaları yükle\"ye izin verin " +
            "(Ayarlar > Uygulamalar > Özel uygulama erişimi > Bilinmeyen uygulamaları yükle veya z2term Ayarları’ndaki düğme)."

    override val updateManagedByStore: String get() = "z2-update: bu sürüm bir mağazadan kuruldu (F-Droid / Play). Oradan güncelleyin."

    override fun updateFailed(reason: String): String = "z2-update: başarısız: $reason"
}
/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Sesli komut — kelime tablosu (Türkçe). voice.sh onu okur. Söyleyebileceklerinizi eklemek veya değiştirmek için düzenleyin.
#
# Nasıl yazılır:
#   anahtar = bir söyleyiş|başka bir söyleyiş|...   "|" ile ayırın; yukarıdan aşağı kontrol edilir
#   o yerdeki kelimeleri almak için * koyun (* aç -> "haritalar aç" "haritalar" verir)
#   *X* X’in iki yanındaki kelimeleri alır
#   anahtar =                            sağ tarafın boş olması bu anahtarı kapatır
#   Noktalama, büyük/küçük harf ve fazla boşluklar yok sayılır
#   Burada olmayan bir anahtar paketlenmiş değeri kullanır (özgün değere dönmek için satırı silin)
#   say_ / msg_ / fmt_ / title_ ile başlayan satırlar söylenen veya gösterilendir; {…} bir değer alır
#
# Konuşmadan denemek için: sh ~/.z2term/macros/voice.sh try sonraki şarkı
# "kendi kelimeleriniz = bir komut" ~/.z2term/voice/commands.txt içine yazılır (bu tablodan önce kontrol edilir).

# --- Dinlenen kelimeler ---
ai = yapay zekaya sor *|yapay zekâya sor *|yapay zeka *|yapay zekâ *|yz *|soru *
media_next = sonraki şarkı|sonraki parça|sonraki|şarkıyı geç|atla|geç|ileri
media_previous = önceki şarkı|önceki parça|önceki|geri dön|geri
media_pause = duraklat|dur|durdur|müziği durdur|müziği kapat
media_play = oynat|devam et|devam|müzik çal|müziği aç|müziği başlat
volume_up = sesi aç|sesi yükselt|sesi artır|daha yüksek|sesi biraz aç
volume_down = sesi kıs|sesi azalt|sesi düşür|daha alçak|sesi biraz kıs
volume_mute = sessize al|sesi kapat|sessiz mod|sessiz
volume_unmute = sesi geri aç|sessizden çıkar
time = saat kaç|saat kaç oldu|şu an saat kaç|saati söyle
date = bugün günlerden ne|bugünün tarihi|bugün ayın kaçı|tarih ne|tarih
battery = pil|şarj|pil seviyesi|ne kadar şarj var|pil durumu|şarj durumu
steps = adım|adımlar|kaç adım|adım sayısı|adımlarım|bugün kaç adım attım|bugünkü adımlar
torch_on = feneri aç|el fenerini aç|fener|ışığı aç
torch_off = feneri kapat|el fenerini kapat|ışığı kapat
screenshot = ekran görüntüsü|ekran görüntüsü al|ekranı kaydet
home = ana ekran|ana ekrana dön|ana sayfa
news = haberler|son haberler|haberleri oku|başlıklar|rss
weather = hava durumu|hava nasıl|bugün hava nasıl|hava
weather_tomorrow = yarın hava nasıl|yarın hava durumu|yarının hava durumu|yarın hava
memo_read = notlarımı oku|notları oku|notlarım|notları göster
memo_add = not al *|not et *|şunu not et *|* not al|* not et
remind_list = hatırlatıcılar|hatırlatıcılarım|hatırlatıcı listesi|hatırlatıcıları göster|neler hatırlatacaksın
remind = bana *hatırlat|*hatırlat|hatırlat *|hatırlatıcı *
wake = beni *uyandır|*uyandır|alarmı *kur|*alarm kur|alarm *
help = ne söyleyebilirim|ne yapabilirsin|yardım|komutlar
timer = *zamanlayıcı kur|*zamanlayıcı başlat|*zamanlayıcı|zamanlayıcı *|*sayaç
navi = *yol tarifi|*nasıl giderim|*gitmek istiyorum|beni *götür|*git|yol tarifi *
navi_ask = navigasyon|yol tarifi|navigasyonu başlat
place_phone = *telefon numarası ne|*telefon numarası|*numarası ne|*numarası
web_search = *internette ara|*internetten ara|*google'da ara|*googleda ara|*googlela|*araştır
call = *ara|ara *|*arama yap
play = *şarkısını çal|*çal|*oynat
open = *aç|*başlat|aç *
# Türüne göre aç (telefonun varsayılan uygulaması). Örn. tarayıcıyı aç
open_browser = tarayıcı|tarayıcıyı|internet|interneti
open_maps = haritalar|haritaları|harita|haritayı
open_mail = e-posta|postayı|posta|maili|mail
open_camera = kamera|kamerayı
open_settings = ayarlar|ayarları
open_calendar = takvim|takvimi
open_music = müzik|müziği
open_gallery = galeri|galeriyi|fotoğraflar|fotoğrafları
open_contacts = kişiler|kişileri|rehber|rehberi
open_dialer = telefon|telefonu|tuş takımı|tuş takımını
open_messages = mesajlar|mesajları|sms
open_calculator = hesap makinesi|hesap makinesini
# Uygulama takma adları. Ad söylemek uygulamayı açmıyorsa şöyle yazın: takma ad:uygulama adı|takma ad:uygulama adı
app_names =

# Sayılı ses düzeyi ("sesi 3 kıs", "ses 10", "ses yüzde 50", "sesi sonuna kadar aç") — içeriyorsa eşleşir
volume_word = ses|sesi|ses düzeyi
volume_up_word = aç|yükselt|artır|yüksek
volume_down_word = kıs|azalt|düşür|alçak
volume_max_word = sonuna kadar|en yüksek|maksimum|tam
volume_min_word = en düşük|minimum|sıfır
volume_percent_word = %|yüzde
volume_mute_word = sessize|kapat
volume_unmute_word = geri aç

# Süreler ve saatler (zamanlayıcı, hatırlatıcı, alarm)
unit_hour = saat|sa
unit_minute = dakika|dk
unit_second = saniye|sn
unit_day = gün
when_before =
when_after = sonra|içinde
clock_at = saat
clock_hour =
clock_minute =
clock_half = buçuk|buçukta
clock_am = sabah|sabahleyin|öğleden önce
clock_pm = öğleden sonra|akşam|akşamleyin|gece
day_today = bugün
day_tomorrow = yarın
day_after_tomorrow = öbür gün|yarından sonra
day_daily = her gün
day_weekdays = hafta içi|hafta içleri|iş günleri
day_every = her
wd_mon = pazartesi
wd_tue = salı
wd_wed = çarşamba
wd_thu = perşembe
wd_fri = cuma
wd_sat = cumartesi
wd_sun = pazar
# Metinle çıkarılan parçalar arasında kalan küçük kelimeler ve ekler
remind_filler = ’de|’da|’te|’ta|de|da|te|ta|için|bana|beni|diye|ki

# --- Söylenen veya gösterilen ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Sesli komut
title_navi = Navigasyon
title_ai = YZ
title_timer = Zamanlayıcı
title_news = Haberler
title_memo = Notlar
title_remind = Hatırlatıcılar
title_call = Arama
title_help = Neler söyleyebilirsiniz
title_rule_voice = Sesli komut
title_rule_voice_locked = Sesli komut (kilitli)
title_rule_timer = Sesli zamanlayıcı
title_rule_steps = Adım say (sesli komut için)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h}:00
fmt_weekdays = pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar
fmt_months = ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık
say_time = Saat {clock}
say_date = Bugün {day} {month_name}, {weekday}
say_battery = Pil yüzde {n}. {charging}
say_charging = Şarj oluyor.
say_steps = Bugün {n} adım attınız
say_steps_none = Henüz adım kaydedilmedi. Fiziksel etkinlik izni ve bir adım kuralı gerekir
say_news = Son başlıklar şunlar. {titles}
say_news_none = Yeni haber yok
fmt_weather_lang = tr
say_weather = Şu an {desc}, {temp} derece
say_weather_tomorrow = Yarın: {desc}, en yüksek {hi}, en düşük {lo} derece, yağmur olasılığı yüzde {rain}
say_weather_fail = Hava durumu alınamadı
say_memo_saved = Not alındı
say_memo_recent = Son notlarınız. {memos}
say_memo_none = Not yok
say_remind_ok = Hatırlatıcı kuruldu. {res}
say_remind_fail = Kurulamadı. {res}
say_remind_unclear = Ne zaman veya neyi hatırlatacağımı anlamadım. Deneyin: yarın sabah saat 7 çöpü çıkarmayı hatırlat
say_remind_missing = Hatırlatıcı makrosu kurulu değil. Bir terminalde z2-macro install remind çalıştırın
fmt_wake_label = alarm
say_wake = Kalkma zamanı
say_wake_set = Sizi saat {clock} uyandıracağım
say_wake_unclear = Alarm saatini anlamadım
fmt_unit_hour = saat
fmt_unit_minute = dakika
fmt_unit_second = saniye
fmt_timer_label = {n} {unit}
say_timer_set = {label} zamanlayıcı başladı
say_timer_done = {label} doldu
say_timer_unclear = Zamanlayıcının süresini anlamadım
say_call_missing = {who} için numara bulamadım
msg_call_missing = "{who}", {file} içinde yok. Satır başına bir "ad numara" yazın.
say_open_missing = {name} adlı bir uygulama bulamadım
say_help = Söyleyebilecekleriniz bir bildirimde
say_locked = Lütfen telefonun kilidini açın
msg_help = saat kaç, tarih, pil, adımlar / sesi 3 kıs, ses yüzde 50 / sonraki şarkı, duraklat / 3 dakika zamanlayıcı, beni saat 7 uyandır / yarın sabah saat 7 çöpü çıkarmayı hatırlat, hatırlatıcılar / haberler, hava durumu, yarın hava / süt almayı not al, notlarımı oku / feneri aç, ekran görüntüsü / … aç / … ara / … yol tarifi, … telefon numarası / … araştır / YZ … (voice.sh ai ile açın) / kelimeleri değiştirmek: voice.sh words
msg_unknown = Anlamadım: {text}
msg_locked = "{text}" telefonun kilidi açılınca devam edecek
msg_navi_where = Nereye gitmek istediğinizi söyleyin
msg_navi_hint = Nereye (bir yer adı)
msg_ai_off = YZ kapalı. {ai} kullanmak için Kopyala’ya dokunup bir terminale yapıştırın (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = YZ kapalı. codex / claude / agy kurup birinde oturum açın, sonra bir terminalde seçin, örn. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} bulunamadı. Kurup oturum açın veya sh ~/.z2term/macros/voice.sh ai ile başka birini seçin
msg_ai_asking = YZ’ye soruluyor ({ai})…
msg_ai_noanswer = Yanıt gelmedi (kayıt: {log})
msg_ai_usage = ai: off | codex | claude | agy değerlerinden birini verin
msg_ai_how = YZ kapalı. Kullanmak için: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; yalnızca "YZ" veya "soru" anahtar kelimesiyle başlarsanız sorulur). Model: sh ~/.z2term/macros/voice.sh model <ad> (boş = o YZ’nin varsayılanı)
fmt_ai_prompt = (Yanıt sesli okunacak. Komut veya dosya kullanmadan, sembol ya da madde işareti olmadan Türkçe 2-3 cümleyle yanıtlayın.)
msg_setup_done = Sesli komut kuralları kaydedildi (zaten olanlar yeniden eklenmedi):
msg_setup_steps = Adım say kuralı (steps:reach=1000000) yalnızca bugünkü adımları sorabilmeniz için adım sayacını çalışır tutar. Bu hedefe asla ulaşılmaz, bu yüzden hiçbir şey çalıştırmaz.
msg_words_file = Kelime tablosu: {file}  (söyleyiş eklemek için düzenleyin; sh ~/.z2term/macros/voice.sh words onu açar)"""
