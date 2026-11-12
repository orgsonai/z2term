package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Deutsch (`de`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: Tab (タブ) / tippen (タップ) / lange drücken (長押し) / wischen (フリック) / Datei / Ordner /
 * Einstellungen (設定) / Zwischenablage / Makro (das) / Kachel (タイル) / Randpanel (エッジパネル) /
 * Hintergrundserver (常駐サーバー)。呼びかけは du。打つ語は英語のまま、説明だけ訳す。
 */
internal object CliDe : CliLanguage {

    override val code: String = "de"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgDe) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgDe(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 GUI-Stapel wird installiert",
            cleanInstalling = "🧹 GUI-Stapel wird neu installiert",
            noPackageManager = "❌ Kein unterstützter Paketmanager (apk/apt-get/pacman) gefunden.",
            invalidGeometry = "❌ Ungültige Auflösung",
            noXvnc = "❌ Xvnc/Xtigervnc nicht vorhanden (tigervnc ist nicht installiert)",
            alreadyRunning = "✅ GUI läuft bereits",
            startingXvnc = "▶ Startet",
            xvncFailed = "❌ Start von Xvnc fehlgeschlagen. Protokoll:",
            ready = "✅ GUI bereit. Verbinde dich über einen GUI-Tab von z2term.",
            running = "✅ GUI läuft",
            stopped = "⏹ GUI ist gestoppt",
            stoppedMsg = "⏹ Gestoppt",
            usage = "Verwendung: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Der GUI-Stapel (Xvnc / openbox / D-Bus) konnte nicht installiert werden.",
            installFailedHint = "   Prüfe dein Netzwerk. Zur Rettung kannst du in einem Terminal z2gui clean ausführen.",
            audioInstalling = "🔊 GUI-Ton: PulseAudio wird installiert",
            audioNoPulse = "⚠️ GUI-Ton: kein pulseaudio, weiter ohne Ton",
            audioNoPactl = "⚠️ GUI-Ton: kein pactl, weiter ohne Ton",
            audioStartFailed = "⚠️ GUI-Ton: PulseAudio startet nicht",
            audioReady = "🔊 GUI-Ton: z2sink.monitor →",
            menuApps = "Anwendungen",
            menuWindows = "Fenster",
            menuDesktops = "Virtuelle Desktops",
            menuPreviousDesktop = "Vorheriger Desktop",
            menuNextDesktop = "Nächster Desktop",
            menuReload = "Menü neu laden"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Ungültige Portnummer",
            configOk = "sshd(dropbear)-Konfiguration OK",
            installingDropbear = "📦 dropbear ist nicht installiert — wird installiert…",
            dropbearInstallFailed = "❌ dropbear konnte nicht installiert werden. Prüfe Netzwerk und Paketnamen.",
            privilegedPortWarn = "⚠️ Privilegierter Port; proot (ohne Root) kann ihn wahrscheinlich nicht binden (nimm 1024+).",
            lanExposeWarn = "⚠️ LAN/WAN-Freigabemodus: Schlüsselauthentifizierung Pflicht, nimm einen starken Schlüssel.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys ist leer. Start mit LAN-Freigabe abgebrochen.",
            noAuthorizedKeysHint = "   Trage deinen öffentlichen Schlüssel in ~/.ssh/authorized_keys ein und versuche es erneut.",
            loopbackBind = "🔒 nur Loopback. Für LAN-Freigabe gib --lan an oder setze Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear im Vordergrund (Strg-C zum Stoppen)",
            listeningLan = "✅ dropbear lauscht (Root, nur Schlüsselauthentifizierung)",
            listeningLoopback = "✅ dropbear lauscht (Root, nur Schlüsselauthentifizierung, Loopback)",
            startupFailed = "❌ Start von dropbear fehlgeschlagen. Protokoll:",
            deviceIp = "geraete-ip",
            authKeysHint = "ℹ ~/.ssh/authorized_keys ist leer. Trage den öffentlichen Schlüssel eines Clients ein, um dich zu verbinden.",
            authKeysExample = "   z. B. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 pacman-Schlüsselbund wird eingerichtet (nur beim ersten Mal, ohne Netz). Das dauert einen Moment…",
            "keyring.msgOk" to "✅ Schlüsselbund bereit. Pakete können jetzt installiert werden.",
            "keyring.msgInitFail" to "❌ pacman-key --init fehlgeschlagen. Beim nächsten Öffnen dieses Tabs wird es erneut versucht.",
            "keyring.msgNoKeyrings" to "❌ Unter /usr/share/pacman/keyrings wurden keine mitgelieferten Schlüsselbunde gefunden.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate fehlgeschlagen. Beim nächsten Öffnen dieses Tabs wird es erneut versucht.",
            "action.description" to "Benannte Android-Aktionsmakros. Aktiviere z2term Android actions mit z2-key permission.\nSpeichern prüft und ersetzt die Definition; screen=current zeichnet das Display auf.\nrun wartet auf das Ende; start gibt eine Ausführungs-ID zurück. Ein Abbruch von run stoppt diese Ausführung.\nEine Ausführung gleichzeitig, 64 Quellanweisungen, 1–300 Sekunden. Benachrichtigung und stop brechen die restlichen Schritte ab.\nKoordinaten nutzen standardmäßig die vordere App und brauchen gleiche Bildschirmgröße/-ausrichtung. Manuelle Gesten: bis 3 Sekunden; aufgezeichnete Berührungen: bis 30 Sekunden.\nNutze start aus einer Panelgeste; nutze run in einem Shell-Makro oder z2-when-Befehl.\nGespeichert in /root/.z2term/actions/NAME.actions (gemeinsamer Home-Ordner). z2-macro actions ist ein Alias.",
            "action.controlHelp" to "version=2 ergänzt repeat N|forever ... end, if BEDINGUNG ... else ... end und call NAME.\nBedingungen nutzen z2-state-Schlüssel oder foreground=PAKET; Kommas bedeuten UND, ! verneint einen Term.\nEin nicht verfügbarer Bedingungszustand lässt die Ausführung scheitern. Aufrufe werden vor der Ausführung geprüft und eingefroren.\n64 Quellanweisungen pro Definition; 10000 ausgeführte Anweisungen pro Ausführung. Zeitlimits von Wurzel und Kindern gelten.",
            "action.uiHelp" to "version=2 unterstützt auch click SELEKTOR, long-click SELEKTOR und wait-ui MS SELEKTOR.\nSelektoren: text=Weiter, desc=Weitere Optionen, id=org.example.app:id/next. Exakte Treffer; keine Anführungszeichen um Leerzeichen.\nLege zuerst target oder launch fest. wait-ui sucht alle 250ms bis zu 1–30000ms; mehrere Treffer schlagen fehl.\nAktionen werden einmal gesendet; nutze wait-ui für den nächsten Bildschirm. Eingabe-/Passwortfelder sind ausgenommen.\ninspect PAKET gibt sichtbare Texte, Beschreibungen und IDs der fokussierten Ziel-App als JSON zurück.",
            "adb.mNoPm" to "z2adb: kein unterstützter Paketmanager (apk/apt-get/pacman) gefunden.",
            "adb.mInstalling" to "z2adb: adb-Client (${d}ADB_PKG) wird über ${d}PM installiert ...",
            "adb.mInstallFail" to "z2adb: adb konnte nicht installiert werden. Installiere es von Hand und versuche es erneut.",
            "adb.mHaveAdb" to "z2adb: adb ist schon installiert.",
            "adb.mNeedWireless" to "z2adb: Schlägt das Koppeln fehl, aktiviere Einstellungen > Entwickleroptionen > Debugging über WLAN (Android 11+).",
            "adb.mPairPort" to "Kopplungsport (Gerät über Kopplungscode koppeln): ",
            "adb.mConnPort" to "Verbindungsport (unter Debugging über WLAN angezeigt): ",
            "adb.usageText" to """
        z2adb - verbindet sich über Debugging über WLAN mit dem adb dieses Geräts (self-adb, ohne PC).

        Voraussetzung: Einstellungen > Entwickleroptionen > Debugging über WLAN aktivieren (Android 11+).

          z2adb setup                adb-Client installieren (apk/apt-get/pacman, automatisch erkannt)
          z2adb pair  <port> [code]  koppeln (nutzt den Kopplungsport + 6-stelligen Code aus den Einstellungen)
          z2adb connect <port>       verbinden (nutzt den Port unter 'Debugging über WLAN')
          z2adb status               'adb devices -l' zeigen
          z2adb <alles andere>       direkt an adb weitergegeben (shell, logcat, install, ...)

        Standard-Host ist 127.0.0.1; host:port übergeben, um das zu ändern (oder Z2ADB_HOST setzen).
        Beispiel:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Spielt den Ton eines lokalen Linux-Befehls auf Android ab, ohne eine GUI zu öffnen.\ninstall: installiert PulseAudio im aktuellen Linux-System (lädt Pakete).\nrun BEFEHL [ARG...]: verbindet den Ton, führt den Befehl aus und stoppt dann seinen Tonserver.\nStarte eine Shell, um Ton für mehrere Befehle zu aktivieren. Keine Konfigurationsdateien werden überschrieben.\nBraucht einen lokalen Terminal-Tab und einen PulseAudio-kompatiblen Player. SSH wird nicht unterstützt.",
            "audio.missing" to "Ton-Werkzeuge fehlen. Führe aus: z2-audio install",
            "audio.failed" to "Tonverbindung fehlgeschlagen; der Befehl wurde nicht gestartet.",
            "doctor.head" to "== z2doctor (dieses Gerät) ==",
            "doctor.secApp" to "-- App --",
            "doctor.secLinux" to "-- Linux-Seite --",
            "doctor.secPerm" to "-- Berechtigungen --",
            "doctor.secAuto" to "-- Automatisierung --",
            "doctor.secReport" to "-- kopiere das, wenn du um Hilfe bittest --",
            "doctor.lVersion" to "Version",
            "doctor.lEngine" to "Umgebung",
            "doctor.lDistro" to "Distribution",
            "doctor.lKernel" to "Kernel",
            "doctor.lDisk" to "freier Speicher",
            "doctor.lHome" to "Home",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "Benachrichtigungen",
            "doctor.lNotifyRead" to "Benachrichtigungszugriff",
            "doctor.lBattOpt" to "Akkuoptimierung",
            "doctor.lStorage" to "Speicher (alle Dateien)",
            "doctor.lSms" to "SMS-Empfang",
            "doctor.lCapture" to "Ereigniserkennung",
            "doctor.lServers" to "Hintergrundserver",
            "doctor.lRules" to "Automatisierungsregeln",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Android-Einstellungen > Apps > Z2Term > Benachrichtigungen: erlauben",
            "doctor.fixNotifyRead" to "-> Einstellungen > Berechtigungen und Benachrichtigungen > Benachrichtigungserkennung",
            "doctor.fixBattOpt" to "-> Einstellungen > Schutz von Hintergrundprozessen > von der Akkuoptimierung ausnehmen",
            "doctor.fixStorage" to "-> Einstellungen > Zugriff auf alle Dateien erlauben (nötig, um /sdcard zu sehen)",
            "doctor.fixSms" to "-> Einstellungen > SMS-Erkennung (nur für sms:-Auslöser nötig)",
            "doctor.fixCapture" to "-> Einstellungen > Erkennung von Systemereignissen (nötig für charge/battery/wifi/sensor/event-Auslöser)",
            "doctor.fixPaused" to "-> Automatisierung ist pausiert. 'z2-when resume' oder der Tab Automatisierung",
            "doctor.fixDisk" to "-> weniger als 500MB frei. Lösche ungenutzte Systemdaten in den Einstellungen",
            "doctor.fixSdcard" to "-> /sdcard ist hier leer; die Speicherberechtigung oben macht es sichtbar",
            "doctor.noteRedacted" to "(SSID / IP / Hostnamen sind absichtlich weggelassen)",
            "doctor.allGood" to "Keine Probleme gefunden.",
            "doctor.someBad" to "Probleme gefunden:",
            "doctor.usage" to "Verwendung: z2doctor [--share | --clip]   (--share gibt den Bericht an das Teilen-Menü von Android)",
            "doctor.pausedYes" to "pausiert",
            "doctor.secExits" to "-- wie es zuletzt endete --",
            "doctor.lExitsNote" to "letzte unnormale Beendigungen (neueste zuerst):",
            "doctor.lExitsNone" to "unnormale Beendigungen: keine aufgezeichnet",
            "doctor.lExitsFile" to "(ganzer Verlauf: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> wegen Speichermangel beendet. Führe weniger schwere Aufgaben gleichzeitig aus oder reduziere Hintergrundserver / Tabs",
            "doctor.title" to "Selbstprüfung für \"es geht nicht\"",
            "doctor.seeAlso" to "Siehe auch: z2scan self (Sicherheitsprüfung, ein anderes Werkzeug)",
            "help.header" to "Eigene Befehle von Z2Term",
            "help.list" to """
        |[Version / Info]
        |  z2version [--short]            App-Version, Umgebung, System (Distribution), Kernel
|  z2doctor [--share|--clip]      Warum geht es nicht? Selbstprüfung + ein Bericht zum Einfügen
        |
        |[Smartphone-Funktionen]
        |  z2-notify [-h] [-b LABEL]...   Benachrichtigung (-h: Banner, -b: Antwortschaltfläche)
        |  z2-ask "question"              Fragt über ein Antwortfeld der Benachrichtigung; gibt die Antwort aus
        |  z2-listen [-w WORD,...]        Sprache zu Text, nur auf dem Gerät (z2-listen -h)
        |  z2-toast "message"             Toast (kurze Meldung auf dem Bildschirm)
        |  z2-share "text"                Gibt Text an das Teilen-Menü von Android
        |  z2-open <url|path>             Öffnet eine URL/Datei in der Standard-App
        |  z2-view <file.html>            Liest eine hier erstellte Seite in der App
        |  z2-edit <file>                 Bearbeitet eine Textdatei im eigenen Editor der App
        |  z2-qr                          Scannt einen QR-Code mit der Kamera (eine Schaltfläche öffnet ihn)
        |  z2-img <file>                  Zeichnet ein Bild im Terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Zwischenablage lesen / setzen (set: stdin ohne Argument)
        |  z2-file pick | save FILE     Android-Dokumentauswahl / Export (z2-file --help)
        |  z2-battery                     Akkustand / Ladezustand (JSON)
        |  z2-vibrate [ms]                Vibrieren (Standard 200ms)
        |  z2-say "text"                  Spricht über das TTS des Geräts (stdin ohne Argument)
        |  z2-torch [on|off|toggle|status]       Taschenlampe (Standard toggle)
        |  z2-media [play|pause|next]     Medientasten (auch previous/stop)
        |  z2-volume up|down|N|N%         Medienlautstärke (gibt aktuell/max zurück)
        |  z2-audio run COMMAND ...      Führt einen Linux-Befehl mit Android-Ton aus
        |  z2-sensor [light|accel|prox]   Liest einen Sensorwert (JSON)
        |  z2-intent -a ACT -d URI ...    Löst einen beliebigen Android-Intent aus (siehe MACRO-GUIDE)
        |  z2-state [key]                 Aktueller Zustand als JSON (oder ein Schlüssel roh)
        |  z2-screen keepon 1h | off      Verhindert eine Weile, dass der Bildschirm von selbst ausgeht
        |  z2-tile set 1 backup.sh        Legt ein Makro/einen Befehl auf eine Schnelleinstellungs-Kachel (12)
        |  z2-icon edit 1 | edit notify   Zeichne das Kachel-/Statusleistensymbol selbst (24/48/64 Punkte)
        |  z2-alarm at|daily HH:MM [name] Zeitauslöser -> events.jsonl (auch list/cancel)
        |  z2-when <trigger> run <cmd>    Automatisch bei Laden/Akku/Zeit/Ereignissen (z2-when: Verwendung)
        |  z2-macro list|install <name>   Mitgelieferte Automatisierungsbeispiele (siehe MACRO-GUIDE)
        |  z2-session list|new|send|...   Steuert die Tabs dieser App (z2-session: Verwendung)
        |  z2-usb list | allow [device]   Nutzt ein USB-Gerät unter Linux (Android fragt nach)
        |  z2-update [--check]            Aktualisiert z2term über GitHub Releases (du bestätigst)
        |
        |[Grafische Apps (GUI)]
        |  z2gui start [WxH] | stop | status   Linux-Desktop (z. B. z2gui start 1280x720)
        |  z2run <gui-app>                Startet eine GUI-App (öffnet auch den GUI-Tab)
        |
        |[Verbinden]
        |  z2adb pair/connect/shell ...   adb zu diesem Smartphone selbst, ohne PC  (z2adb help)
        |  sshd [-p N]                    SSH-Server (Standard: nur 127.0.0.1, Schlüsselauthentifizierung)
        |
        |[Sicherheit]
        |  z2scan self [--save]           Selbstprüfung dieses Geräts/localhost (z2scan help)
        |  z2scan diff                    Nur was sich seit der Basis geändert hat (Exit 1 = neu)
        |  z2scan net|host|cve            nmap/lynis/trivy auf localhost (entfernt nur mit opt-in)
        |
        |[Hilfe]
        |  z2help | z2term                Diese Liste
        |
        |Jeder Befehl oben erklärt sich selbst: --help anhängen (z. B. 'z2-tile --help').
        |Mehr: 'z2adb help', 'z2gui' ohne Argumente ausführen, oder HANDBOOK Abschnitt 11.
    """,
            "help.edge" to "[Schwebende Panels]\nz2-edge --help   Panels festlegen und ihren Inhalt aktualisieren\nz2-key --help    Globale Android-Aktionen\nz2-shot --help   Nimmt nur den umfahrenen Teil auf\nz2-app --help    Anwendungen auflisten und Symbole exportieren\nz2-action --help Benannte Android-Aktionsmakros\nz2-ui --help     App-Steuerung durch KI (die Hilfe ist eine Anleitung für KI-Agenten)",
            "macro.rssBody.head" to """
#
# Einrichtung:
#   1) Eine Feed-URL pro Zeile in:  ~/.z2term/rss/feeds.txt
#   2) Alle 30 Minuten abrufen:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Optional - ein Feed oder Wort pro Zeile, das nicht untergehen darf:  ~/.z2term/rss/important.txt
#      Alles Passende bekommt eine eigene Benachrichtigung, ein vielbeschäftigter Feed kann es also nicht verdrängen.
#      Schreibe einen Teil einer URL oder eines Titels (z. B. example.org).
#   4) Benachrichtigungsaktionen in der Automatisierung eintragen:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Öffnen schickt den Artikel an deinen Browser; Liste öffnet alles Gesammelte zum Lesen in z2term.
#      Hinweis: Einen Bildschirm über eine Benachrichtigungsschaltfläche zu öffnen braucht die Berechtigung "Über anderen Apps einblenden".
#   5) Die gesammelte Liste lesen: sh ~/.z2term/macros/rss.sh view
#      Beide Regeln verwaltest du unter Befehlsliste -> Automatisierung -> Automatisierungsregeln.
#      Bearbeite eine vorhandene Benachrichtigungsregel, statt eine weitere hinzuzufügen.
#   6) Benachrichtigungen: standardmäßig benachrichtigt der geplante Abruf, ein Abruf aus der Liste nicht.
#        sh ~/.z2term/macros/rss.sh notify off        # geplanter Abruf: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Aktualisieren / Artikel in der Liste: on / off
#      Beides sind auch Schalter unter Einstellungen in der Liste, jederzeit änderbar.
#
# Braucht: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Vorschaubilder (Einstellungen in der Liste) brauchen auch Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Akku: Je öfter du abrufst, desto mehr kostet es. Nicht unter 30 Minuten gehen.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "maximal in seen/latest behaltene Zeilen",
            "macro.cNoPy" to "python3 wird benötigt (z. B. apk add python3)",
            "macro.cWriteFeeds" to "Schreibe eine Feed-URL pro Zeile in:",
            "macro.cFeedsHint" to "# Eine Feed-URL pro Zeile (Zeilen, die mit # beginnen, werden ignoriert)",
            "macro.cSkipFail" to "einen Feed, der sich nicht laden oder lesen lässt, still überspringen (der Rest läuft weiter)",
            "macro.cDiff" to "# Ziehe ab, was wir schon gesehen haben, sodass nur Neues bleibt (derselbe Trick wie der Basis-Vergleich von z2scan).\n# Feed-Daten und -Reihenfolge werden nicht vertraut; beides ist unzuverlässig.",
            "macro.cPrepend" to "# Neueste zuerst stapeln, damit der Widget-Modus \"Anfang (head)\" richtig liest.",
            "macro.cNotify" to "%s neu",
            "macro.cOpen" to "Öffnen",
            "macro.cListNone" to "Noch nichts gesammelt — führe es zuerst einmal ohne Argumente aus.",
            "macro.cListHint" to "Auflisten mit: rss.sh list [Anzahl]",
            "macro.cList" to "Liste",
            "macro.cPageTitle" to "Feeds",
            "macro.cPageSites" to "%s Seiten",
            "macro.cPageArticles" to "%s Artikel",
            "macro.cPageDoc" to "# Baut die Leseseite (z2-view zeigt sie in der App; ohne Server, ohne Browser).\n" +
            "# Nach Seite gruppiert, neueste zuerst, mit einer Schaltfläche Öffnen pro Artikel. Das Theme des\n" +
            "# Smartphones kommt als CSS-Variablen an, die Seite folgt also den Terminalfarben.",
            "macro.cViewHint" to "Lesen mit: rss.sh view",
            "macro.cHitMax" to "maximale Zahl einzelner Artikelbenachrichtigungen pro Lauf (der Rest geht in die Zusammenfassung)",
            "macro.cSplitDoc" to "# Trenne ab, was nicht verpasst werden darf. Der Text der Zusammenfassung trägt nur 3 Zeilen, ein\n" +
            "# vielbeschäftigter Feed, der gleichzeitig aktualisiert, verdrängt also das Wichtige. Treffer bekommen **eine eigene\n" +
            "# Benachrichtigung** (die App vergibt pro Benachrichtigung eine eigene id, es wird also nichts ersetzt).",
            "macro.cNameDoc" to "# Die URL in den Namen der Benachrichtigung legen: Ein Druck auf die Schaltfläche gibt sie als {name} in\n" +
            "# notify_action zurück, sodass auch bei mehreren Benachrichtigungen der richtige Artikel öffnet (Einrichtung 4).",
            "macro.cListDoc" to "# Gibt das Gesammelte als lesbare Liste aus (ohne Farbe — sie soll wie die Datei/das Widget aussehen).\n" +
            "# latest.txt selbst bleibt Rohdaten mit einem Artikel pro Zeile (das Widget-tail und rss-open.sh lesen sie).",
            "macro.cSettings" to "Einstellungen",
            "macro.cArchive" to "Archiv",
            "macro.cDelete" to "Löschen",
            "macro.cThumbs" to "Vorschaubilder",
            "macro.cSave" to "Alte Artikel behalten",
            "macro.cNotifyPoll" to "Neue Artikel melden (geplanter Abruf)",
            "macro.cNotifyView" to "Neue Artikel melden (aus der Liste abgerufen)",
            "macro.cDeleteSite" to "Alle Artikel dieser Seite löschen",
            "macro.cConfirmSite" to "%s Artikel von %h aus der Liste entfernen?",
            "macro.cArticles" to "Artikel",
            "macro.cPrev" to "Zurück",
            "macro.cNext" to "Weiter",
            "macro.cCount" to "%s Artikel",
            "macro.cUndated" to "Ohne Datum",
            "macro.cArchiveEmpty" to "Noch nichts behalten. Schalte Alte Artikel behalten in den Einstellungen ein, dann bleiben gesammelte Artikel hier.",
            "macro.cSearch" to "Suchen",
            "macro.cSearchWord" to "Wörter (mit Leerzeichen trennen, um alle zu verlangen)",
            "macro.cByMonth" to "Nach Monat",
            "macro.cNoHit" to "Nichts gefunden.",
            "macro.cDropMonth" to "Diesen Monat löschen",
            "macro.cDropConfirm" to "Die %s behaltenen Artikel von %m löschen? Das lässt sich nicht rückgängig machen.",
            "macro.cOnOff" to "Gib on oder off an",
            "macro.cNoPillow" to "Vorschaubilder brauchen Pillow für Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Einrichtung: zuerst die sammelnde Seite installieren
#   z2-macro install rss
# Widget: weise "rss-open" in den Einstellungen des Status-Widgets einer Schaltfläche zu.
#
# z2-run: weise "rss-open" einer Status-Widget-Schaltfläche zu (zum Ausprobieren hier: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Noch keine Artikel",
            "macro.cAllRead" to "Nichts Neues zum Öffnen",
            "macro.cPick" to "# Zeilen in latest.txt sind \"Titel  URL\". Nimm die erste URL, die noch nicht geöffnet wurde.",
            "macro.cCap" to "die Liste der geöffneten begrenzen, damit sie nicht endlos wächst",
            "macro.copied" to "Code kopiert: ${d}{code}",
            "macro.cleared" to "Der kopierte Code wurde gelöscht",
            "macro.otpTitle" to "Einmalcode",
            "macro.cTtl" to "Sekunden, bis die Kopie gelöscht wird (gilt nur, wenn sie direkt hineinkam)",
            "macro.cCode" to "# Die App hat den Code schon herausgezogen. Nichts tun, wenn es nicht ging.",
            "macro.cTry" to "# Versuche, ihn direkt einzulegen. Das klappt nur, während du **die App ansiehst**:\n" +
            "# Android 10+ lässt nur die vordere App in die Zwischenablage schreiben, ein Lauf im\n" +
            "# Hintergrund wird also still verworfen. Lies zurück, um zu sehen, ob er ankam.",
            "macro.cFallback" to "  # Nicht angekommen = du siehst die App nicht an. Übergib ihn stattdessen über die Schaltfläche\n" +
            "  # \"Kopieren\" der Benachrichtigung (ein Druck holt die App nach vorne).\n" +
            "  # ⚠ Was so hineinkommt, löscht das TTL unten nicht: Löschen braucht ebenfalls den Vordergrund.",
            "macro.cClear" to "# Nach dem TTL die Zwischenablage nur löschen, wenn sie noch den kopierten Code enthält\n" +
            "# (alles, was seitdem kopiert wurde, bleibt unberührt).",
            "macro.cHow" to """
# ■ Telefonnummern aus Anrufbenachrichtigungen kopieren
#   Prüft Titel und dann Text auf eine reine Telefonnummer und bietet eine Schaltfläche Kopieren an.
#   Gespeicherte und ungespeicherte Anrufer sind beide dabei; Kontakte werden nicht abgefragt.
#   Ein Name allein reicht nicht, um eine Nummer zu ermitteln. Nur der Benachrichtigungszugriff wird genutzt.
#   Der Dateiname unknown-call.sh bleibt, damit vorhandene Regeln weiter funktionieren.
""",
            "macro.cIsNum" to """
# Ist es eine reine Nummer? Entferne alle Zeichen, die eine Telefonnummer haben darf (Ziffern + - ( ) Leerzeichen);
# bleibt **nichts übrig** und sind es 7-15 Ziffern, gilt es als Nummer.
#   -> Ein Feld mit einem Namen oder anderen Buchstaben ist keine reine Nummer; prüfe das andere Feld.
#   -> "Unbekannt"/"Private Nummer" fallen hier auch heraus (lockere den Test unten, wenn du die willst).
# ⚠ Schreibe das nicht als case [!...]: Die ) im Muster wird als case-Trenner gelesen.
""",
            "macro.cScan" to "# Der Anrufer steht meist im Titel, manche Telefon-Apps setzen ihn aber in den Text. Prüfe beides.",
            "macro.cSkip" to "# In keinem Feld eine reine Nummer: Es gibt nichts zu kopieren.",
            "macro.cWhat" to "# Die Kategorie sagt, welche ausgelöst hat (derselbe Wert wie das eingetragene notify:category=).",
            "macro.cCopy" to """
# Übergib die Nummer über die Schaltfläche "Kopieren" der Benachrichtigung (-c). z2-clip set hier aufzurufen
# käme nicht an: Android 10+ lässt nur die vordere App in die Zwischenablage schreiben, und während eines
# Anrufs ist das die Telefon-App. Ein Druck auf die Schaltfläche holt z2term für diesen Moment nach vorne.
""",
            "macro.missed" to "Verpasster Anruf",
            "macro.incoming" to "Eingehender Anruf",
            "macro.title" to "Telefonnummer kopieren",
            "macro.stNew" to "neu",
            "macro.stSame" to "gleich",
            "macro.stDiff" to "anders",
            "macro.msgInstalled" to "installiert:",
            "macro.msgSame" to "dasselbe ist schon installiert:",
            "macro.msgOutdated" to "deines weicht vom mitgelieferten ab:",
            "macro.msgHowUpdate" to "erst ansehen: z2-macro diff %s   /   durch das mitgelieferte ersetzen: z2-macro install -f %s (deine eigenen Änderungen gehen mit)",
            "macro.msgNotInstalled" to "noch nicht installiert (zuerst z2-macro install ausführen):",
            "macro.msgRemoved" to "entfernt:",
            "macro.msgAbsent" to "nicht installiert:",
            "macro.msgRemoveDiffers" to "deines weicht vom mitgelieferten ab und wurde daher nicht entfernt:",
            "macro.msgHowRemove" to "erst ansehen: z2-macro diff %s   /   trotzdem entfernen: z2-macro remove -f %s (deine eigenen Änderungen gehen mit)",
            "macro.msgNoDiffTool" to "hier gibt es kein diff. Das mitgelieferte kannst du trotzdem mit z2-macro show lesen.",
            "macro.msgSameAsBundled" to "identisch mit dem mitgelieferten.",
            "macro.msgNotFound" to "kein solches Beispiel:",
            "macro.msgHintResident" to "Damit es weiterläuft, trage das unter Einstellungen -> Hintergrundserver ein:",
            "macro.msgHintRun" to "So führst du es aus:",
            "macro.remindBody.head" to """
#
# Einrichtung (einmal):
#   sh ~/.z2term/macros/remind.sh setup   die zwei Haken und zwei Kacheln eintragen
#
# Syntax:
#   remind.sh <wann> <text>          eine Erinnerung hinzufügen
#   remind.sh list                   auflisten (die Nummer steht vorne)
#   remind.sh del <n|all>            abbrechen (rm geht auch)
#   remind.sh peek                   die Liste in einer Benachrichtigung zeigen (mit Schaltfläche Löschen)
#   remind.sh ask                    in einem Antwortfeld der Benachrichtigung fragen (für die Kachel)
#   remind.sh setup                  Haken und Kacheln eintragen (einmal)
#   remind.sh help                   dieser Text
#
# <wann> - einmalig:
#   30m / 90s / 2h                   ab jetzt
#   18:30                            das nächste 18:30 (morgen, wenn vorbei)
#   sat 21:00                       nächster passender Wochentag, einmal (später heute zählt auch)
#   Japanisch: 土曜日の夜九時 = sat 21:00; 朝七時 = 07:00; 夜九時半 = 21:30
#   Japanisch: 夜12時 ist mehrdeutig; nimm für Mitternacht 00:00 mit ausdrücklichem Tag.
#   tomorrow 18:30 / 3d 07:00        nach Tagen (3d = in 3 Tagen)
#   07/30 19:00                      Monat/Tag (nächstes Jahr, wenn vorbei)
#   2030 07/30 19:00                 mit Jahr
#   203007301900 / 07301900          nur Ziffern (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ ohne Uhrzeit bleibt **die aktuelle Tageszeit** (3d / 07/30 ...)
#
# <wann> - wiederholt:
#   daily 07:00 / weekday 09:00      jeden Tag / Mo-Fr
#   weekly mon 09:00                 an diesem Wochentag
#   monthly 15 09:00                 an diesem Tag des Monats
#   yearly 07/30 19:00               an diesem Monat und Tag
#   every 19:00 / every wed 19:00    "every" allein geht (das nächste Wort entscheidet)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Beispiele:
#   remind.sh 30m Tabletten nehmen
#   remind.sh tomorrow 18:30 den Müll rausbringen
#   remind.sh monthly 25 10:00 Miete
#   remind.sh list        ->  1  ⏰ 07/30 18:30  den Müll rausbringen
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (danach einfach: remind.sh 30m ...)
""",
            "macro.cStore" to """# Eine Erinnerung = eine Zeile in ${d}DIR/<id>.txt, tabgetrennt: Art / Beschriftung / Zeit-ID / Text.
#   Art: once (steht noch aus) / fired (schon ausgelöst) / repeat (wiederkehrend)
#   Zeit-ID: nur für repeat (damit del z2-when remove aufrufen kann). Sonst '-'.
# Der Text kommt zuletzt, damit er alles außer einem TAB enthalten kann.""",
            "macro.cParse" to """# 1-3 Wörter lesen und entscheiden:
#   KIND ... once|repeat   PLAN ... anzuzeigende Beschriftung   SPEC ... was z2-alarm/z2-when nimmt   USED ... genutzte Wörter""",
            "macro.pDaily" to "täglich ",
            "macro.pWeekday" to "werktags ",
            "macro.pWeekly" to "wöchentlich ",
            "macro.pMonthly" to "monatlich ",
            "macro.pYearly" to "jährlich ",
            "macro.mDay" to ".",
            "macro.eDaily" to "täglich",
            "macro.eWeekly" to "wöchentlich",
            "macro.eMonthly" to "monatlich",
            "macro.eYearly" to "jährlich",
            "macro.mBadDate" to "dieses Datum gibt es nicht (z. B. 07/30):",
            "macro.cEvery" to """  # Die Kurzform "every ...". **Die Form des nächsten Worts** wählt daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Die Wortzahl ändert sich nicht (ein Wort wird gegen ein anderes getauscht), USED stimmt also weiter.""",
            "macro.cExpand" to "# Entscheide anhand des folgenden Worts, welche Wiederholung \"every\" meinte.",
            "macro.cDigits" to """    # Reine Ziffernformen: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ 12 Ziffern vor 8 prüfen (das 8-Ziffern-Muster passt auch auf eine 12-stellige Zeichenkette).""",
            "macro.cYmd" to """  # Als Datum geschrieben. ⚠ z2-alarm nimmt kein Datum, daher wird auch das zu einer Verzögerung.""",
            "macro.cCivil" to """# Kalenderdatum -> Tage seit 1970-01-01 (mit Schaltjahren; days_from_civil von Howard Hinnant).
# ⚠ date -d "2026-07-30" ist unter busybox nicht verlässlich, daher wird hier gezählt.
# ⚠ Nur der Tagesunterschied wird genutzt und die Uhrzeit [day_epoch] überlassen, was die Zeitzone aufhebt.""",
            "macro.cCron" to """# Baue einen cron-Ausdruck (min stunde tag monat wochentag) aus HH:MM und einem Wochentagsfeld.
# Führende Null NICHT mit expr entfernen: Es endet mit 1, wenn das Ergebnis 0 ist, also läuft auch die rechte
# Seite von `expr "${d}m" + 0 || echo "${d}m"` und der Wert wird zweizeilig.""",
            "macro.afterLabel" to """# "30m" -> "in 30m (14:35)". Die Uhrzeit zu zeigen lässt dich sofort prüfen.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "in ${d}num${d}u (${d}at)" || echo "in ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "Verwendung: remind.sh <wann> <text>",
            "macro.mBadWhen" to "die Zeit ist nicht lesbar (versuche: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "schreibe die Uhrzeit als HH:MM:",
            "macro.mBadRange" to "Uhrzeit außerhalb des Bereichs (00:00-23:59):",
            "macro.mNoTime" to "keine Uhrzeit angegeben (z. B. daily 07:00):",
            "macro.mBadDow" to "unbekannter Wochentag:",
            "macro.mPastTime" to "diese Zeit ist schon vorbei:",
            "macro.cDays" to """    # Angabe nach Tagen. Ohne Uhrzeit bleibt **die aktuelle Tageszeit** (nie einen Standard erfinden).
    # Sie kann auch als ein Wort ankommen, daher wird auch die zusammengeschriebene Form angenommen.""",
            "macro.cDayEpoch" to """# Wandle "HH:MM, in N Tagen" in Epoch-Sekunden um. ⚠ busybox hat kein date -d "tomorrow",
# daher wird zuerst die heutige Mitternacht ermittelt und Tage und Uhrzeit draufgerechnet.
# ⚠ Ein Wert mit führender Null ("08") wird von ${d}(()) oktal gelesen, also entfernen.
# ⚠ Ein Tag wird als 86400s addiert, an einem Tag der Zeitumstellung kann es also eine Stunde abweichen.""",
            "macro.cFmtAt" to """# Die in der Liste gezeigte Beschriftung. ⚠ "tomorrow" zu behalten wäre nach dem Datumswechsel falsch.""",
            "macro.mNoBody" to "sag, woran ich dich erinnern soll",
            "macro.mNoAlarm" to "konnte nicht eingeplant werden",
            "macro.mNone" to "nichts geplant",
            "macro.mFired" to " (ausgelöst)",
            "macro.mRemoved" to "entfernt:",
            "macro.mNoSuchNum" to "kein solcher Eintrag (prüfe remind.sh list):",
            "macro.mUsageDel" to "Verwendung: remind.sh del <n|all>",
            "macro.bDone" to "Erledigt",
            "macro.bDelete" to "Löschen",
            "macro.mAskDel" to "Welche entfernen?",
            "macro.mAskDelH" to "Nummer / all",
            "macro.mDeletedTitle" to "🗑 Entfernt",
            "macro.cPeek" to """# Die Liste in einer Benachrichtigung zeigen (von der Kachel "Liste"). **Die Nummern behalten** — mit ihnen
# zeigst du auf die zu entfernende. ⚠ Auflisten ohne Möglichkeit zum Entfernen war die Beschwerde; daher die Schaltfläche.""",
            "macro.cAskDel" to """# Die Schaltfläche "Löschen": nach einer Nummer fragen und sie an [cmd_del] geben (derselbe Weg wie in der CLI).""",
            "macro.bS1" to "+10min",
            "macro.bS2" to "+1h",
            "macro.mAgain" to "- erneut in",
            "macro.mTitle" to "⏰ Erinnerungen",
            "macro.mAsk1" to "Woran soll ich dich erinnern?",
            "macro.mAsk1H" to "z. B. Tabletten nehmen",
            "macro.mAsk2" to "Wann?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "bitte erneut eingeben",
            "macro.mAskGiveUp" to "Konnte es ${ASK_TRIES} Mal nicht lesen. Du kannst es auch im Terminal hinzufügen: remind.sh 30m Tabletten nehmen",
            "macro.mOkTitle" to "⏰ Erinnerung gesetzt",
            "macro.mNgTitle" to "⚠ Nicht gesetzt",
            "macro.cHooks" to """  # Ein Haken für "eine Erinnerung wurde ausgelöst", einer für "eine Benachrichtigungsschaltfläche wurde gedrückt". Nur
  # diese zwei, egal wie viele Erinnerungen du anlegst. Keiner hängt von den Erkennungsschaltern ab.""",
            "macro.cTiles" to """  # Nur leere und schon eigene Plätze füllen (nie die von jemand anderem überschreiben).
  # Ein Makroname mit Argumenten geht ab 0.8.275 (davor sh + vollen Pfad schreiben).""",
            "macro.mSetupHooks" to "Haken eingetragen:",
            "macro.mSetupTiles" to "Kacheln:",
            "macro.mPathHint" to "Hinweis: Dieser Tab kann remind.sh noch nicht per Name ausführen. Öffne einen neuen Tab oder führe " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH hier aus.",
            "macro.cPathHint" to """  # ⚠ Der Makroordner ist erst seit 0.8.287 im PATH, **vorher geöffnete Tabs**
  # haben also noch den alten. Prüfe, ob der Name gefunden wird, und sag sonst, was zu tun ist
  # (sonst hat `command not found` keinen sichtbaren Grund).""",
            "macro.mPlace" to "Hinweis: Die Kacheln musst du noch selbst platzieren, im Bearbeitungsbildschirm (Stift) der Schnelleinstellungen.",
            "macro.lRemind" to "erinnern",
            "macro.lList" to "Liste",
            "macro.cSelf" to "# Nur auf unsere eigenen Benachrichtigungen reagieren (unsere ids beginnen immer mit r)",
            "macro.cKeepRepeat" to "      # Eine wiederkehrende hier nicht löschen: Sie soll morgen wieder auslösen.",
            "macro.cAsk" to """# Der Weg von der Kachel / einer Benachrichtigung. ⚠ **Immer mit einer Benachrichtigung antworten**:
# Hier schaut niemand aufs Terminal, ein Fehler nach stderr wirkt also wie "ich habe getippt und
# nichts ist passiert" (es ist passiert — der Grund landete nur im run.log der Kachel).
#   Unlesbar -> sag warum und frag erneut, bis zu $ASK_TRIES Mal (die vorige Antwort bleibt)
#   Geplant  -> Plan und Text in einer Benachrichtigung zeigen""",
            "macro.cSnoozeCancel" to "den Schlummer-Alarm verwerfen, wenn vor dem Erledigen geschlummert wurde",
            "macro.qrBody.head" to """
#
# Verwendung:
#   qr.sh "https://example.com"       eine Zeichenkette codieren und hier zeichnen
#   qr.sh -f notes.txt                den Inhalt einer Datei codieren
#   z2-clip get | qr.sh               den Inhalt der Zwischenablage codieren
#   qr.sh -o ~/qr.png "text"          ein PNG speichern (nichts wird gezeichnet)
#   qr.sh -t "text"                   Blöcke statt eines Bildes ausgeben
#   qr.sh -s 24 "text"                die Größe wählen (in Spalten; Standard passt in die Breite)
#
# z2-run: qr.sh "https://example.com"   (nach der Installation reicht der Name. Läuft einmal)
#
# # Voraussetzungen (ohne sie läuft es nicht oder sieht anders aus)
#
#   1) Das Codieren übernimmt die App ... in einem Tab von z2term ist nichts
#      zu installieren (z2-qr encode). Ist qrencode schon da, wird es stattdessen genutzt.
#      Wo die App nicht erreichbar ist (über ssh), installiere es einmal pro Tab (Distribution):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Ohne beides wird der Installationsbefehl ausgegeben und das Skript stoppt.
#
#   2) Das Bild (Standard) erscheint nur in einem Tab dieser App ... es wird mit
#      Kitty-Grafik gezeichnet. Über ssh oder in einem Terminal ohne Bilder bekommst du
#      stattdessen Zeichensalat. Nimm dort -t.
#
#   3) -t (Blöcke) hängt von der Terminalschrift ab ... Schriften mit Lücken zwischen
#      den Zeilen bleiben fürs Auge lesbar, aber nicht für eine Kamera. Zum Scannen mit einer Kamera
#      nimm lieber das Standardbild oder das PNG von -o.
#
#   4) Das Seitenverhältnis wird angenommen ... siehe "Seitenverhältnis" unten. Gestaucht? Z2_QR_ASPECT.
#
# Optionen:
#   -f FILE   die Eingabe aus einer Datei lesen (sonst die Argumente, sonst stdin)
#   -o PNG    ein PNG speichern statt zu zeichnen. Mehrere Codes werden zu
#             qr-1.png / qr-2.png ...
#   -t        Blöcke ausgeben. Für Terminals ohne Bilder und über ssh
#   -s N      Größe in Spalten beim Zeichnen. Standard bis 34, begrenzt durch die Breite
#   -h        diese Hilfe
#
# Zu langer Eingabe:
#   Ein Code fasst eine feste Menge (höchstens 2953 Bytes alphanumerisch; ein paar hundert,
#   wenn er noch scanbar sein soll). Eingaben über 900 Bytes werden an Zeilenumbrüchen
#   in mehrere Codes geteilt, als [1/3] nummeriert und der Reihe nach ausgegeben.
#   WARNUNG eine einzelne Zeile, die zu lang für einen Code ist, lässt sich gar nicht codieren.
#
# Seitenverhältnis:
#   Terminals melden ihre Zellgröße nicht, daher wird ein Zeichenverhältnis von 1:2 angenommen,
#   damit der Code quadratisch wird. Sieht er gestaucht aus, passe es mit einer Umgebungsvariable an:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (kleiner = höher. Standard 0.5)
""",
            "macro.cMaxBytes" to "maximale Bytes pro Code (darüber wird geteilt)",
            "macro.cPngModule" to "Punkte pro Modul in einem gespeicherten PNG",
            "macro.cTargetPx" to "angestrebte Pixelbreite beim Zeichnen",
            "macro.cMargin" to "Ruhezone um den Code (Module; zum Scannen nötig)",
            "macro.cUsageFn" to "Den Kommentarblock am Anfang als Hilfetext ausgeben (ohne feste Zeilenzahl).\n" +
            "# Leerzeilen stoppen ihn nicht (NF entscheidet), durch Leerzeilen getrennte Abschnitte bleiben also ganz.",
            "macro.cNeedEncoder" to "Voraussetzung 1: etwas zum Codieren. qrencode, wenn vorhanden, sonst die App.",
            "macro.mMissing" to "qr.sh: hier gibt es nichts zum Codieren - kein qrencode, und die App ist nicht erreichbar",
            "macro.mInstallOnce" to "  Einmal in diesem Tab installieren:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Das PNG erzeugen. qrencode wählt die Modulgröße per Probe; die App nimmt ein Ziel.",
            "macro.cMakeBlocks" to "Mit Blockzeichen zeichnen. Beide Wege geben eigene Farben aus, jedes Theme geht also.",
            "macro.qrBody.mNoTmp" to "Arbeitsordner kann nicht angelegt werden",
            "macro.qrBody.mUnreadable" to "nicht lesbar:",
            "macro.qrBody.mEmpty" to "leere Eingabe",
            "macro.mSizeNum" to "-s nimmt eine Zahl",
            "macro.qrBody.mUsageHint" to "siehe qr.sh -h",
            "macro.mPieceFail" to "qr.sh: Code %d kann nicht gebaut werden: %s",
            "macro.qrBody.cCollect" to "---- die Eingabe sammeln ----",
            "macro.cSplit" to "---- bei zu langer Eingabe an Zeilenumbrüchen teilen ----",
            "macro.cSplit2" to "Zum nächsten Stück wechseln, kurz bevor die laufende Summe MAX_BYTES übersteigt. Zeilen werden\n# nie mittendrin geschnitten, Multibyte-Text überlebt also.",
            "macro.cTrim" to "Den abschließenden Zeilenumbruch entfernen (nie einen Zeilenumbruch codieren, den die Eingabe nicht hatte).",
            "macro.cSize" to "---- die Anzeigegröße aus der Terminalbreite wählen ----",
            "macro.cPngWidth" to "Die Pixelbreite eines PNG zurückgeben (Bytes 16-19, der IHDR).",
            "macro.cModulePx" to "Aus der angestrebten Pixelbreite die Punkte pro Modul zurückrechnen.",
            "macro.cInline" to "Ein PNG an Ort und Stelle mit dem Kitty-Grafikprotokoll zeichnen.\n# c/r legen fest, wie viele Zellen es belegt, dann so viele Zeilenumbrüche ausgeben, dass der Cursor darunter steht.",
            "macro.cEmit" to "---- ausgeben ----",
            "macro.cAnsi" to "ANSIUTF8 gibt eigene Farben aus, hell/dunkel stimmt also bei jedem Theme.",
            "macro.mdBody.head" to """
#
# Verwendung:
#   md.sh README.md                hier in diesem Terminal darstellen
#   md.sh -v README.md             in der App öffnen (z2-view)
#   cat notes.md | md.sh           von der Standardeingabe lesen
#   md.sh -o page.html README.md   das HTML schreiben und stoppen (nichts öffnet sich)
#
# z2-run: md.sh README.md   (nach der Installation reicht der Name. Läuft einmal)
#
# Optionen:
#   -v        in der App öffnen (z2-view)
#   -o FILE   das HTML dorthin schreiben. Hier wird nichts gezeigt
#   -w N      bei N Spalten umbrechen (Standard: die Bildschirmbreite, bis 100)
#   -p        ohne Farbe und Verzierung (reiner Text)
#   -t TITLE  Titel des Lesebildschirms (Standard: die erste Überschrift, sonst der Dateiname)
#   -h        diese Hilfe
#
# Ein langes Dokument bildschirmweise: md.sh README.md | less -R   (-R behält die Farben)
#
# Nichts zu installieren: sh und awk genügen.
# Überschriften, Absätze, Listen (verschachtelt, nummeriert, [ ]/[x]), Zitate, Code, Tabellen,
# Linien, Links, Bilder und Front Matter (---) werden behandelt. WARNUNG Setext-Überschriften
# (=== / ---) und Referenzlinks ([Text][1]) kommen so heraus, wie sie geschrieben sind.
#
# Was dich erwartet:
#   - Bilder erscheinen an Ort und Stelle nur in einem Tab dieser App (mit Kitty-Grafik
#     über z2-img gezeichnet). Über ssh bekommst du stattdessen Bildunterschrift und Pfad.
#   - Links lassen sich antippen (OSC 8). Ein Terminal ohne das zeigt sie als Text.
#   - Dieses Terminal zeichnet keine Kursivschrift, *Betonung* wird daher unterstrichen (wie bei man).
#   - Eine für den Bildschirm zu breite Tabelle wird in Zeilen "Überschrift: Wert" zerlegt.
#   - -v bettet Bilder dieses Geräts in die Seite ein (je höchstens 1,5 MB).
#     Der Lesebildschirm holt nichts aus dem Netz, http(s)-Bilder
#     bleiben daher als ihre Bildunterschrift.
""",
            "macro.mdBody.cCollect" to "# Die Eingabe sammeln (mehrere Dateien werden zu einem Lesen, in der angegebenen Reihenfolge).",
            "macro.cTitle" to "# Titel: -t, sonst die erste #-Überschrift, sonst der Dateiname",
            "macro.cWidth" to "# Die Bildschirmbreite",
            "macro.cColorEnv" to "# Ein Ausweg, wenn die Farben durch eine Pipe gehen sollen (oder nicht), z. B. in einen Pager",
            "macro.cImgWhere" to "# Ein Bild kann nur in einem Tab (Kitty-Grafik) mit vorhandenem z2-img an Ort und Stelle erscheinen.",
            "macro.cAmbi" to "# Wie breit mehrdeutige Zeichen (Linien, Pfeile, eingekreiste Ziffern) zählen. Nimm\n" +
            "# Z2_MD_AMBIWIDTH=2, wenn die Einstellungen sie als volle Breite führen. Standard 1, wie in der App.",
            "macro.cAwkFile" to "# WARNUNG das awk-Programm wird in eine DATEI geschrieben und mit -f übergeben. Als\n" +
            "#   Argument übergeben schneidet gawk das Programm dort ab, wo ein Multibyte-Zeichen (wie das\n" +
            "#   Japanische in diesen Notizen) seine 4096-Byte-Lesegrenze überspannt, und stirbt mit\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Die Bilder für den Lesebildschirm als data:-URIs in die Seite einbetten.\n" +
            "# WARNUNG z2-view holt nichts aus dem Netz (JavaScript und Laden sind aus), ein\n" +
            "#   nicht eingebettetes Bild erscheint also einfach nicht. 4 MB für die ganze Seite.",
            "macro.cImgLoop" to "# Nur ein Dokument mit Bildern wird zeilenweise gelesen (um z2-img an Ort und Stelle aufzurufen)",
            "macro.mdBody.mUsageHint" to "siehe md.sh -h",
            "macro.mdBody.mNoTmp" to "temporärer Ordner kann nicht angelegt werden",
            "macro.mdBody.mUnreadable" to "nicht lesbare Datei:",
            "macro.mdBody.mEmpty" to "nichts zu lesen",
            "macro.mWidthNum" to "-w nimmt eine Zahl",
            "macro.mNoDir" to "der Zielordner kann nicht angelegt werden",
            "macro.mConvFail" to "die Umwandlung ist fehlgeschlagen",
            "macro.mNoView" to "hier gibt es kein z2-view (die App muss aktualisiert werden). Das HTML liegt unter:",
            "macro.mImgLabel" to "[Bild]",
            "menu.noApps" to "(keine Anwendung gefunden)",
            "menu.cat.AudioVideo" to "Ton und Video",
            "menu.cat.Development" to "Entwicklung",
            "menu.cat.Graphics" to "Grafik",
            "menu.cat.Game" to "Spiele",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Büro",
            "menu.cat.System" to "System",
            "menu.cat.Utility" to "Zubehör",
            "menu.cat.Other" to "Sonstiges",
            "menu.usage" to "Verwendung: z2menu [list]   (ohne Argument: openbox-Pipe-Menü-XML)",
            "reminderView.labels" to "Erinnerungen|Hinzufügen|Löschen|Inhalt|Datum und Uhrzeit|Wiederholung|Einmal|Täglich|Werktags|Wöchentlich (gewählter Wochentag)|Plantext (optional; ersetzt Datum und Wiederholung)|Geplant|Wiederkehrend|Gemeldet|Keine Erinnerungen|Diese Erinnerung und ihre ausstehenden Benachrichtigungen löschen?",
            "rssFeeds.rssFeedsLabel" to "Abos",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} ist nicht gestartet. Siehe /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: kein unterstützter Paketmanager (apk/apt-get/pacman) gefunden.",
            "scan.mInstalling" to "z2scan: installiere",
            "scan.mInstallFail" to "z2scan: Installation fehlgeschlagen",
            "scan.mRemoteBlocked" to "z2scan: entferntes Ziel abgelehnt. Prüfe nur localhost oder gib --allow-remote für ein Ziel an, das du testen darfst.",
            "scan.mRemoteWarn" to "z2scan: WARNUNG prüfe einen entfernten Host. Prüfe nur Systeme, die du ausdrücklich testen darfst.",
            "scan.mNoLynis" to "z2scan: lynis nicht gefunden; weiter mit der eingebauten Selbstprüfung ('z2scan self'). Installieren mit 'z2scan setup'.",
            "scan.mNoCve" to "z2scan: kein CVE-Scanner (trivy/grype) gefunden. Installiere trivy oder grype und versuche es erneut.",
            "scan.mSelfHead" to "== z2scan-Selbstprüfung (dieses Gerät / localhost) ==",
            "scan.mUidRoot" to "läuft als uid 0 (Hinweis: proot/z2root stellt ein falsches Root bereit)",
            "scan.mPubPort" to "TCP-Port lauscht auf allen Schnittstellen (nicht nur localhost), Port",
            "scan.mSshdEmpty" to "sshd_config erlaubt Anmeldung mit leerem Passwort (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config erlaubt Passwortauthentifizierung (PasswordAuthentication yes); Schlüssel sind sicherer",
            "scan.mSshdRoot" to "sshd_config erlaubt Root-Anmeldung (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys hat lockere Rechte, Modus",
            "scan.mSshDir" to "~/.ssh hat lockere Rechte, Modus",
            "scan.mWorldWrite" to "für alle beschreibbare Dateien gefunden (bis zu 20 gezeigt):",
            "scan.mSuid" to "SUID-Binaries vorhanden (zur Info; falsches Root unter proot/z2root):",
            "scan.mPath" to "PATH enthält ein leeres/'.'-Element (aktueller Ordner im PATH ist riskant)",
            "scan.mFound" to "Befunde:",
            "scan.mClean" to "keine offensichtlichen Probleme gefunden.",
            "scan.mSaved" to "z2scan: Basis gespeichert:",
            "scan.mSaveFail" to "z2scan: Basis konnte nicht geschrieben werden:",
            "scan.mNoBase" to "z2scan: noch keine Basis. Führe einmal 'z2scan self --save' aus, um den aktuellen Zustand festzuhalten.",
            "scan.mNoChange" to "[ OK ] keine Änderung seit der Basis.",
            "scan.mAdded" to "== neu seit der Basis ==",
            "scan.mRemoved" to "== weg seit der Basis ==",
            "scan.mCleared" to "z2scan: Basis gelöscht.",
            "scan.mLangDiff" to "z2scan: WARNUNG die Basis wurde in einer anderen Sprache gespeichert, alles wird daher als geändert erscheinen. Speichere neu mit 'z2scan self --save'.",
            "scan.langTag" to "de",
            "scan.usageText" to """
        z2scan - Schwachstellentest für dieses Gerät / localhost (defensiv, keine Daten gehen hinaus).

        Prüft standardmäßig nur localhost. Scanner werden aus den offiziellen Paketen deiner
        Distribution installiert (nmap/lynis/trivy); nichts ist mitgeliefert, Ergebnisse bleiben lokal.

          z2scan self [--save]         eingebaute Selbstprüfung (ohne externe Werkzeuge): offene Ports,
                                       sshd-Konfiguration, SSH-Schlüsselrechte, für alle beschreibbar/SUID, PATH.
                                       --save hält das Ergebnis zusätzlich als Basis fest.
          z2scan diff [--quiet]        die Selbstprüfung erneut ausführen und nur zeigen, was sich
                                       seit der Basis geändert hat. Endet mit 1, wenn etwas neu ist,
                                       passt also direkt in z2-when. --quiet gibt gar nichts
                                       aus, wenn sich nichts geändert hat.
          z2scan baseline [clear]      die gespeicherte Basis zeigen (oder löschen)
          z2scan setup                 Scanner (nmap, lynis) über apk/apt-get/pacman installieren
          z2scan net [--allow-remote] [target]
                                       TCP-Scan mit nmap. Ziel ist standardmäßig 127.0.0.1.
                                       Ein nicht lokales Ziel braucht --allow-remote.
          z2scan host                  Host-Prüfung mit lynis (fällt auf 'self' zurück, wenn es fehlt)
          z2scan cve                   Prüfung des rootfs auf bekannte CVEs mit trivy/grype, falls vorhanden

        Prüfe nur Systeme, die du ausdrücklich testen darfst.
    """.trimIndent(),
            "translationMacro.description" to "Übersetzt Text mit einem separat installierten Befehl.",
            "translationMacro.missing" to "Der Befehl trans ist in dieser lokalen Umgebung nicht installiert. Installiere dein Übersetzungswerkzeug selbst und versuche es erneut. Nichts wird automatisch heruntergeladen.",
            "translationMacro.network" to "Der zu übersetzende Text wird an den externen Dienst deines Werkzeugs gesendet. Dessen Bedingungen und die Netzverfügbarkeit gelten.",
            "translationSetup.setup" to "Öffne ein Terminal in derselben lokalen Linux-Umgebung, führe den Befehl unten selbst aus und versuche dann erneut zu übersetzen.",
            "translationSetup.alpineRepo" to "Wird das Paket nicht gefunden, aktiviere das community-Repository derselben Alpine-Version in /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Wird das Paket unter Ubuntu nicht gefunden, aktiviere sein universe-Repository und versuche es erneut.",
            "translationSetup.unknown" to "Distribution nicht erkannt. Wähle den Befehl für deine lokale Linux-Umgebung:",
            "voice.head" to """# voice.sh — der Empfänger für "Sprachbefehl": Was du sagst, steuert Musik, Lautstärke, Timer, Erinnerungen, Wetter und mehr.
#
# Einrichtung (einmal):
#   sh ~/.z2term/macros/voice.sh setup    4 Regeln eintragen (Sprachbefehl / Sprachbefehl bei gesperrtem Bildschirm / Sprach-Timer / Schritte zählen)
#   Schritte zählen hält nur den Schrittzähler am Laufen für "wie viele Schritte heute"; es führt nie etwas aus
#   dann über die Schnelleinstellungs-Kachel "Sprachbefehl" oder den digitalen Assistenten (Ein/Aus lange drücken usw.) sprechen
#
# Aus einem Terminal:
#   voice.sh words                 die Wörtertabelle öffnen (~/.z2term/voice/words-<lang>.txt; Sätze hinzufügen oder ändern)
#   voice.sh try <satz>            einen ohne Sprechen ausprobieren (z. B. voice.sh try nächstes Lied)
#   voice.sh listen                den Zuhörbildschirm öffnen und sprechen (wie die Kachel)
#   voice.sh lang [sprache]        die Sprache der Wörtertabelle (voice.sh lang - listet sie)
#   voice.sh ai [off|codex|claude|agy]   ob KI genutzt wird (anfangs aus)
#   voice.sh model [name]          das Modell der KI (leer = Standard dieser KI)
#   voice.sh setup / help
#
# KI ist anfangs aus. Auch eingeschaltet wird sie nur gefragt, wenn du mit dem Signalwort beginnst ("KI", "Frage" ...).
# Das Wetter fragt wttr.in nur, wenn du "Wetter" sagst (Ort: weather_city= in ~/.z2term/voice/config).
# Eigene Wörter: "Wörter = Befehl" in ~/.z2term/voice/commands.txt (vor der Wörtertabelle geprüft)
# Namen zum Anrufen: "Name Nummer" in ~/.z2term/voice/contacts.txt / Protokoll: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — Einstiegsmakro. Reagiert auf das, was auf dem Gerät passiert.",
                "# Das Warten übernimmt die App (z2-when), es läuft also nicht dauerhaft: einmal laufen und beenden.",
                "# Einrichtung: Einstellungen -> \"Erkennung von Systemereignissen\" AN",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (Kopfhörer: eine zweite Regel mit 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Was passiert ist, kommt in Z2_WHEN_EVENT an (z2-when events listet die Namen).",
                "# Zum Ausprobieren von Hand:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT ist leer. Zum Ausprobieren von Hand: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Laden begonnen\" ;;",
                "  power_disconnected) z2-toast \"Laden beendet\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — warnt bei niedrigem Akku, passt sich aber dem aktuellen Zustand an.",
                "# Prüft mit z2-state, ob der Bildschirm an ist: Toast, wenn ja, Benachrichtigung, wenn nicht.",
                "# Einrichtung: Einstellungen -> \"Erkennung von Systemereignissen\" AN",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when übergibt den Stand; sonst z2-state, damit es auch von Hand läuft.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Beim Laden nichts sagen (es wird ja nicht wirklich leer)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Akku ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Akku schwach\" \"Noch ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — liest jeden Morgen Akku und Verbindung vor.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Der System-Alarm weckt es, es läuft also auch im Doze (vielleicht ein paar Minuten zu spät).",
                "# Hängt nicht von den Erkennungsschaltern ab.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=WLAN; else net=Mobilfunk; fi",
                "z2-say \"Guten Morgen. Akku ${d}{level} Prozent, Netzwerk ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — kopiert einen Einmalcode aus Benachrichtigungen und löscht ihn nach",
                "# TTL Sekunden, wenn die Zwischenablage noch denselben Wert enthält.",
                "# Einrichtung: Einstellungen -> \"Benachrichtigungserkennung\" AN + System-Benachrichtigungszugriff gewähren",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ kann Codes in sensiblen Benachrichtigungen schwärzen. Für einen sicheren Weg nimm",
                "#    die SMS-Variante (otp-sms.sh) — SMS-Texte werden nie geschwärzt.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — kopiert einen Einmalcode aus eingehenden SMS und löscht ihn nach",
                "# TTL Sekunden, wenn die Zwischenablage noch denselben Wert enthält.",
                "# Die SMS-Variante von otp-clip.sh. Anders als Benachrichtigungen werden SMS-Texte nie",
                "# vom Schutz sensibler Benachrichtigungen (Android 15+) geschwärzt und funktionieren bei Sperre.",
                "# Einrichtung: Einstellungen -> \"SMS-Erkennung\" AN + System-SMS-Berechtigung gewähren",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — zeigt eine Kopier-Benachrichtigung für Telefonnummern aus Anrufbenachrichtigungen.",
                "# Ihre Schaltfläche \"Kopieren\" legt die Nummer in die Zwischenablage (warum sie auf einen Druck wartet: siehe unten).",
                "# Einrichtung: Einstellungen -> \"Benachrichtigungserkennung\" AN + System-Benachrichtigungszugriff gewähren",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Um auch verpasste Anrufe zu erfassen, trage eine zweite Regel ein (dasselbe Skript, andere Kategorie):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — ruft Feeds ab und behält nur Neues, als Benachrichtigung und als Text.",
                "# Anders als die anderen Beispiele läuft dieses **nicht** dauerhaft: Es hat die",
                "# Form 'einmal per Zeitauslöser laufen und beenden'.",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — öffnet, was rss.sh gesammelt hat, neueste zuerst, ein Artikel pro Tippen.",
                "# Es erscheint als Makroschaltfläche im Status-Widget, jedes Tippen öffnet also den nächsten.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — erinnert dich mit einer Benachrichtigung: einmalig (in 30m, um 18:30) oder",
                "# wiederholt (täglich / werktags / wöchentlich). Löst bei geschlossener App aus (ein System-Alarm",
                "# weckt es; kein Erkennungsschalter nötig). Schlummern über die Schaltflächen der Benachrichtigung.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — macht aus Text oder einer Datei einen QR-Code: hier gezeichnet oder als PNG gespeichert.",
                "# Übergibt etwas an ein anderes Gerät, ohne es abzutippen. Läuft einmal, nicht dauerhaft.",
            ),
            "macro.md" to listOf(
                "# md.sh — liest eine Markdown-Datei hier formatiert oder öffnet sie in der App.",
                "# Überschriften, Listen, Tabellen, Code, Links; Bilder erscheinen an Ort und Stelle. Läuft einmal, nicht dauerhaft.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Ein Feed oder Wort pro Zeile, das nicht untergehen darf (Zeilen, die mit # beginnen, werden ignoriert).",
                "# Ein Teil einer URL oder eines Titels geht. Jeder Treffer bekommt eine eigene Benachrichtigung.",
                "# z. B.", "#example.org",
            ),
            "macro.usage" to listOf(
                "Verwendung: z2-macro <Unterbefehl>",
                "  list                mitgelieferte Beispiele auflisten (markiert neu / gleich / anders)",
                "  install <name|all>  nach ~/.z2term/macros/ kopieren (überschreibt nie; -f erzwingt)",
                "  remove <name>       deine Kopie eines mitgelieferten Beispiels löschen (stoppt bei Abweichung; -f erzwingt)",
                "  diff <name>         was zwischen deiner Kopie und der mitgelieferten abweicht (deine links)",
                "  show <name>         das Skript ausgeben",
                "  run <name>          hier ausführen (Strg-C zum Stoppen)",
                "  dir                 ausgeben, wo die Makros liegen",
                "  actions ...         Android-Aktionsmakros (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Abos", "Hinzufügen", "Artikel", "Ändern", "Entfernen", "Feed-URL",
                "Dieses Abo entfernen? Gesammelte Artikel bleiben erhalten.",
                "Änderungen gelten beim nächsten Abruf. Gesammelte Artikel bleiben erhalten.",
                "Keine Abos. Gib mit Hinzufügen eine RSS- oder Atom-URL ein.",
                "Gib eine HTTP(S)-URL ohne Leerzeichen oder Zugangsdaten ein (bis 2048 Zeichen).",
                "Dieses Abo gibt es schon.",
                "Dieses Abo hat sich geändert. Aktualisiere die Liste und versuche es erneut.",
                "Eine andere Änderung wird gerade gespeichert. Versuche es erneut.",
                "Zurück", "Weiter",
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

/** [Z2ApiMsg] の Deutsch 版。上書きしていない文言は英語で出る。 */
private class ApiMsgDe(d: String) : Z2ApiMsg("de", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — ein Dokument wählen; in HOME kopieren und den Pfad ausgeben.\n# z2-file save FILE [MIME] — ein Ziel wählen und eine Kopie speichern.\n# Abbrechen zählt als Fehler. Bis 512 MiB; die Auswahl läuft nach 5 Minuten ab."

    override val usbHelp: String get() = """
        |# z2-usb list              … mit dem Smartphone verbundene USB-Geräte auflisten
        |# z2-usb allow [Nummer]    … Android bitten, Linux-Programmen ein Gerät zu erlauben
        |# Führe allow nach dem Anschließen eines Geräts einmal aus. Die Erlaubnis gilt bis zum Abziehen.
    """.trimMargin()

    override val usbUsage: String get() = "Verwendung: z2-usb list | z2-usb allow [Nummer|Pfad|VID:PID]"

    override val usbNoDevices: String get() = "Kein USB-Gerät ist mit dem Smartphone verbunden."

    override val usbNeedSelector: String get() = "Mehr als ein USB-Gerät ist verbunden; gib seine Nummer aus z2-usb list an."

    override fun usbPermissionRequested(path: String): String = "Bestätige den USB-Zugriff auf dem Android-Bildschirm: $path"

    override fun usbAlreadyAllowed(path: String): String = "Der USB-Zugriff ist schon erlaubt: $path"

    override fun usbNotFound(selector: String): String = "USB-Gerät nicht gefunden: $selector"

    override val usbStateAllowed: String get() = "erlaubt"

    override val usbStateNeedsAllow: String get() = "braucht Erlaubnis"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "Titel" "Text"  /  z2-notify [-h] "Text"
        |#   -h / --high / --banner : als Banner (Heads-up) oben auf dem Bildschirm zeigen
        |#   -b <Beschriftung>      : eine Antwortschaltfläche hinzufügen (bis 3). Ein Druck hängt
        |#                            notify_action an ~/.z2term/events.jsonl an
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <Name>              : eine Kennung dieser Benachrichtigung (um Antworten zu unterscheiden)
        |#   -c <Text>              : eine Schaltfläche "Kopieren" hinzufügen, die <Text> in die Zwischenablage legt.
        |#                            Nimm das statt z2-clip set, wenn das Makro im
        |#                            Hintergrund läuft: Android 10+ lässt nur die vordere App in
        |#                            die Zwischenablage schreiben, ein unbeaufsichtigtes z2-clip set wird verworfen.
    """.trimMargin()

    override val notifyUsage: String get() = "Verwendung: z2-notify [-h] [-n Name] [-c Text] [-b Beschriftung]... <Titel> [Text]"

    override val toastHelp: String get() = """
        |# z2-toast <Meldung> … eine kurze Meldung unten auf dem Bildschirm (ein Toast).
        |# Sie verblasst von selbst und hinterlässt nichts. Nimm z2-notify, wenn sie bleiben soll
        |# oder du eine Schaltfläche zum Drücken willst.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … die QR-Werkzeuge mit schon scannender Kamera öffnen.
        |# Nach dem Scan öffnet eine Schaltfläche den Inhalt in seiner App (Browser, LINE und andere Apps,
        |# Telefon, Mail, SMS, WLAN, Kontakte, Kalender). Nichts öffnet sich von selbst.
        |# Leg es auf eine Kachel in den Schnelleinstellungen oder in ein Randpanel.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … einen QR-Code erzeugen.
        |#   Nichts zu installieren: Die App codiert (derselbe Codierer wie im QR-Bildschirm).
        |#   Ohne TEXT liest es die Standardeingabe. -o ist, wohin das PNG geht
        |#   (Standard ~/.z2term/qr/qr.png), der Pfad wird ausgegeben. -t zeichnet es stattdessen hier
        |#   mit Blockzeichen. -p ist die angestrebte Größe in Pixeln (Standard 600),
        |#   -m die Ruhezone in Modulen (Standard 4). Bis 2000 Bytes.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <Text> … Text an das Teilen-Menü von Android geben (an eine andere App weiterreichen).
        |# --file FILE [FILE ...]: Dateiinhalte senden (bis 32 Dateien, 512 MiB insgesamt).
        |# Pfade werden in der aktuellen lokalen Shell gelesen. -- erzwingt den Textmodus. Kopien älter als 24 Stunden werden beim nächsten Teilen aufgeräumt.
        |# Alle Argumente werden zu einem Text verbunden. Die Ziel-App wird auf dem Bildschirm gewählt,
        |# es muss also jemand da sein — nichts für ein unbeaufsichtigtes Makro.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [Titel] … eine hier erstellte Seite in z2term lesen.
        |# Kein Server und kein Browser: Die Datei wird an die App übergeben und so gezeigt, wie sie ist.
        |# JavaScript und Laden aus dem Netz sind aus, nichts auf der Seite kann laufen oder nach Hause telefonieren.
        |# http://- und https://-Links öffnen sich in deinem üblichen Browser.
        |# Das Theme des Smartphones kommt als CSS-Variablen an (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), eine damit geschriebene Seite folgt also den Terminalfarben.
        |# Bis 4 MB. Bilder müssen eingebettet sein (data:-URIs); entfernte werden nicht geholt.
        |# Mit --edge PANEL:ITEM an einen vorhandenen Randpanel-Eintrag type=view senden.
        |# Mit --controls FILE.json für allgemeine Formulare und Aktionen, die ein installiertes Makro behandelt.
        |# Ansichtseinträge aktualisieren standardmäßig manuell. view-refresh=auto führt run= beim Öffnen aus; z2-view kehrt zu diesem Eintrag zurück.
        |# Mit - oder einer Pipe (ls -l | z2-view) die Standardeingabe lesen. Text, der nicht mit < beginnt, wird
        |# umbrochen in Monospace ohne Farbcodes gezeigt; --text / --html wählen ausdrücklich.
        |# Ein Zwei-Finger-Zoom ändert die Textgröße; --pinch page zoomt stattdessen die ganze Seite. --text-size 50..300 setzt die
        |# Textgröße (Prozent) dieser Seite. Beides gilt für den Vollbildschirm; ein Panel-Eintrag nutzt seine eigenen Einstellungen.
        |# --no-refresh / --no-find verbergen die Schaltflächen Aktualisieren / Suchen des Vollbildschirms (standardmäßig sichtbar).
    """.trimMargin()

    override val viewUsage: String get() = "Verwendung: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <Datei|-> [Titel]"

    override val viewUnreadable: String get() = "z2-view: nicht lesbare Datei:"

    override val openHelp: String get() = """
        |# z2-open <url|Pfad> … mit der Standard-App öffnen (https://… oder /sdcard/…).
        |# Welche App öffnet, entscheidet Android; z2term übergibt nur.
        |# Ein Pfad ist ein Pfad auf dem **Smartphone** (/sdcard/…), nicht in der Distribution.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <Datei|->… … das Bild im Terminal zeichnen.
        |#   PNG / JPEG / WebP / GIF / BMP. Gib - an, um ein Bild von stdin zu lesen.
        |#   -w <cols>  Breite in Terminalspalten (Standard: an das Terminal anpassen)
        |#   -r <rows>  Höhe in Terminalzeilen (mit -w wird die Größe wie angegeben genutzt)
        |#   --clear    alle bisher gezeichneten Bilder entfernen
        |#   -f         auch senden, wenn stdout kein Terminal ist
        |# ⚠ Bilder erscheinen nur in einem z2term-Tab oder einem Terminal, das das Kitty-
        |#   Grafikprotokoll spricht. Überall sonst (über ssh, in einem Pager) bekommst du Zeichensalat.
        |# ⚠ Das Seitenverhältnis nimmt an, dass eine Zelle doppelt so hoch wie breit ist. Sieht es
        |#   gestaucht aus, justiere: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (kleiner = höher).
    """.trimMargin()

    override val imgUsage: String get() = "Verwendung: z2-img [-w cols] [-r rows] [--clear] [-f] <Datei|->..."

    override val imgNoFile: String get() = "die Datei kann nicht gelesen werden:"

    override val imgNotTty: String get() = "stdout ist kein Terminal, daher wurde nichts gesendet (-f sendet trotzdem)."

    override val imgNoSize: String get() = "die Pixelgröße ist nicht lesbar; als Quadrat gezeichnet:"

    override val qrUsage: String get() = "Verwendung: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: Der Code konnte nicht erzeugt werden (ist der Text zu lang? bis 2000 Bytes)"

    override val clipHelp: String get() = """
        |# z2-clip get        … die Zwischenablage auf stdout ausgeben
        |# z2-clip set [text] … Text (oder stdin, wenn weggelassen) in die Zwischenablage legen
        |# ⚠ Android 10+ lässt nur die vordere App (oder die genutzte Eingabemethode) an die
        |#   Zwischenablage. Aus einem Makro im Hintergrund wird das kommentarlos verworfen,
        |#   nimm dort z2-notify -c <Text>: Es fügt eine Schaltfläche "Kopieren" hinzu, die immer funktioniert.
    """.trimMargin()

    override val batteryHelp: String get() = "# Stand / Ladezustand als JSON ausgeben ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (Standard 200ms)"

    override val editHelp: String get() = "# z2-edit <Datei>     … eine Textdatei im eigenen Editor von z2term bearbeiten (einfaches Textfeld; Speichern schreibt zurück)"

    override val sayHelp: String get() = "# z2-say <Text>       … mit dem TTS des Geräts sprechen (liest stdin ohne Argument)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (Standard toggle). status liest den Android-Zustand, auch Änderungen von außen (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … nur den umfahrenen Teil aufnehmen (Standard free). Braucht Android 11 und z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (Standard playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Medienlautstärke. Gibt das sich ergebende aktuell/max aus."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (Standard light). Liest einen Wert und gibt JSON zurück."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Einen beliebigen Android-Intent auslösen (standardmäßig startActivity). Ein erstes Argument ohne Option ist die ACTION.
        |# Freies Fenster nutzt standardmäßig Aufgabenwiederverwendung und Systemgrenzen. --reuse-task / --os-bounds werden weiter angenommen.
        |# --bounds-only fordert 80%-Grenzen ohne Modusschlüssel an; nicht mit --os-bounds kombinierbar.
        |# --no-scale schaltet die verkleinerte Freiform-Anfrage von Motorola für diesen Start ab.
        |# -p PKG allein startet diese App. --window full|freeform|split|ask wählt die Startanfrage (Systemunterstützung nötig).
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … der aktuelle Zustand des Geräts, komplett, als JSON
        |# z2-state <Schlüssel> … nur dieser Wert, roh (passt direkt in einen Test)
        |# Schlüssel: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# z. B. [ "${d}(z2-state charging)" = "true" ] && echo lädt
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <Frage>
        |#   Der Person eine Frage stellen und ihre Antwort auf stdout ausgeben.
        |#   Die Frage kommt als Benachrichtigung mit **Antwortfeld**, sie lässt sich also
        |#   aus der Benachrichtigungsleiste beantworten, ohne die App zu öffnen (auch ein Makro im
        |#   Hintergrund kann fragen).
        |#   -t <Sek>   wie lange auf eine Antwort gewartet wird (Standard 300 = 5 Min.)
        |#   -H <Hinweis> Beschriftung im Antwortfeld
        |#   -d <Text>  vorgeschlagene Antwort (in der Benachrichtigung gezeigt)
        |# Wird die Benachrichtigung ohne Antwort weggewischt oder läuft die Zeit ab, endet es
        |# ungleich null und gibt nichts aus — du kannst also "oder aufgeben" schreiben:
        |#   name=${d}(z2-ask "Branch-Name?") || exit 1
        |# Vergleiche mit z2-notify -b <Beschriftung>, das nur die vorbereiteten Auswahlen anbietet.
    """.trimMargin()

    override val askUsage: String get() = "Verwendung: z2-ask [-t Sek] [-H Hinweis] [-d Standard] <Frage>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Gibt auf stdout aus, was du sagst. Die Erkennung läuft **nur auf dem Gerät**; deine Stimme
        |#   geht nie ins Netz. Braucht Android 12+ und Spracherkennung auf dem Gerät
        |#   (für deine Sprache deren Offline-Sprachdaten).
        |#   Sprich mit z2term auf dem Bildschirm (Android gibt das Mikrofon nur der App auf dem Bildschirm).
        |#   Es stoppt, wenn du still wirst. Beim ersten Mal wird nach Mikrofonzugriff gefragt.
        |#   -w <Wörter>  nur diese Wörter annehmen (kommagetrennt) und das gehörte ausgeben.
        |#               Alle Vermutungen der Erkennung werden geprüft, nicht nur die beste.
        |#   -l <Sprache> zu erkennende Sprache (z. B. de-DE, en-US; Standard: die Gerätesprache)
        |# Exit: 0 = fertig / 1 = nichts gehört oder keines der Wörter / 2 = nicht verfügbar (Grund auf stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "Verwendung: z2-listen [-w Wort,Wort,...] [-l Sprache]"

    override val listenPrompt: String get() = "Sprich jetzt… (stoppt, wenn du still wirst)"

    override val listenNeedsAndroid12: String get() = "z2-listen braucht Android 12 oder neuer (davor lässt sich die Erkennung nicht auf dem Gerät halten)."

    override val listenBusy: String get() = "Hört schon zu (ein anderes z2-listen läuft)."

    override val listenEngineBusy: String get() = "Die Spracherkennung des Geräts ist belegt (vielleicht nutzt eine andere App sie). Versuche es später erneut."

    override val listenEngineStuck: String get() = "Die Spracherkennung antwortet nicht. Passiert das öfter, beende die Erkennungs-App des Geräts (etwa Android System Intelligence) in den Android-Einstellungen › Apps erzwungen."

    override val listenStopped: String get() = "Zuhören beendet."

    override val listenNoOnDevice: String get() = "Dieses Gerät hat keine Spracherkennung auf dem Gerät. z2-listen schickt deine Stimme nie ins Netz und kann hier daher nicht laufen."

    override val listenNoPermission: String get() = "Mikrofonzugriff ist nicht erlaubt. Führe z2-listen mit z2term auf dem Bildschirm erneut aus, um gefragt zu werden, oder erlaube es in den Android-Einstellungen."

    override val listenNotShown: String get() = "Hol z2term auf den Bildschirm und versuche es erneut (Android gibt das Mikrofon nur der App auf dem Bildschirm)."

    override val listenNoMic: String get() = "Das Mikrofon konnte nicht genutzt werden (z2term hat den Bildschirm verlassen oder eine andere App nimmt auf)."

    override val listenNoLanguage: String get() = "Die Sprachdaten auf dem Gerät für diese Sprache sind nicht installiert. Lade sie in den Spracheingabe-Einstellungen des Geräts herunter (Offline-Spracherkennung)."

    override val listenNoSpeech: String get() = "Nichts gehört."

    override val listenTimedOut: String get() = "Zeit abgelaufen."

    override fun listenNoMatch(heard: String): String = "Keines der Wörter (gehört: $heard)"

    override fun listenFailed(code: Int): String = "Spracherkennung fehlgeschlagen (Fehler $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … verhindern, dass der Bildschirm so lange von selbst ausgeht
        |# z2-screen keepon off          … jetzt zurückstellen, ohne auf die Frist zu warten
        |# z2-screen status              … aktueller Zustand als JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Das ändert die systemweite Einstellung "Bildschirm-Zeitüberschreitung", es hält also auch, wenn die App
        |# im Hintergrund ist. Es ist NICHT die Bildschirmsperre der Symbolleiste, die nur gilt, solange die
        |# App auf dem Bildschirm ist — die bleibt unberührt.
        |# Der ursprüngliche Wert wird gespeichert und zur Frist immer zurückgeschrieben (auch wenn die App
        |# beendet wird oder das Gerät neu startet). Höchstens 24h am Stück.
        |# Braucht "Systemeinstellungen ändern" (Einstellungen > Bildschirm-Zeitüberschreitung > erlauben).
        |# z. B. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "Verwendung: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | Befehl...> [--off <Befehl...>] [-l <Beschriftung>] [-i <Zeichnung>]
        |#                           … etwas auf die Schnelleinstellungs-Kachel 1-$tiles legen
        |# z2-tile add <1-$tiles>         … bitten, diesen Platz ins Panel zu legen (Android 13+)
        |# z2-tile list              … alle $tiles Plätze als TSV (Platz / Beschriftung / Befehl; '-' = leer)
        |# z2-tile clear <1-$tiles|all>   … einen Platz leeren
        |#
        |# Was du zuweist
        |#   Entweder **den Dateinamen eines Makros** in ~/.z2term/macros/ oder **einen Befehl**, der so
        |#   ausgeführt wird, wie getippt — was es ist, wird aus dem Namen ermittelt.
        |#   Ein Makro darf Argumente nehmen ('remind.sh ask'): Das erste Wort entscheidet, ob es
        |#   ein Makro ist, der Rest wird übergeben. Zwei Plätze mit demselben Makro bekommen dieselbe
        |#   Beschriftung, gib ihnen also -l.
        |#   Der Befehl läuft mit Z2_TILE=<Platz> (und Z2_TILE_MACRO bei einem Makro) in der Umgebung.
        |#   ⚠ Ein auf .sh endender Name, der nicht in ~/.z2term/macros/ liegt, wird hier abgelehnt — als
        |#     Befehl würde er im PATH gesucht (der den Makroordner nicht enthält),
        |#     die Kachel täte also gar nichts und sagte das nur in tile/run.log.
        |#
        |# Was ein Tippen tut
        |#   Tippen startet, erneutes Tippen stoppt (wie bei den Schaltflächen des Widgets).
        |#   Die Kachel sieht "an" aus, solange es läuft (die Farbe ist der Systemakzent, nicht unsere).
        |#   Mit --off bekommst du **zwei Befehle**: Tippen wechselt zwischen ihnen, und die Kachel
        |#   sieht "an" aus, solange es an ist. Nimm das, wo Ausschalten ein eigener Befehl ist
        |#   (z2-torch on / off).
        |#   Normale Paare merken sich erfolgreiche Umschaltungen. Direkte z2-torch-Zuweisungen folgen den
        |#   Beobachtungen von Android, auch Änderungen von außen. z2-screen keepon folgt seiner tatsächlich aktiven Einstellung.
        |#
        |# Ins Panel legen
        |#   Unter Android 13+ fragt `set` sofort, ob die Kachel ins Panel soll, und
        |#   dieser Dialog trägt **den gerade zugewiesenen Namen und das Symbol**. Sagst du nein, wird nichts platziert.
        |#   Es gibt genau $tiles Plätze: Die Zahl steht fest im Manifest und kann zur Laufzeit nicht
        |#   wachsen. Plätze ohne Zuweisung tauchen im Bearbeitungsbildschirm gar nicht auf,
        |#   $tiles davon kosten dich also nichts.
        |#   ⚠ Es erscheint nur, solange z2term vorne ist, und nur einer kann gleichzeitig gefragt werden — ein
        |#     Makro, das zwei Plätze hintereinander zuweist, fragt nur nach dem ersten. Nimm
        |#     danach z2-tile add <Platz> für den Rest.
        |#   ⚠ Eine Kachel zu platzieren bleibt deine Entscheidung — Android lässt eine App ihre eigenen Kacheln nicht
        |#     dorthin legen. Suche im Bearbeitungsbildschirm (Stift) der Schnelleinstellungen nach
        |#     **z2term <Platz>**.
        |#   ⚠ Diese Liste zeigt Name und Symbol aus dem Manifest, nicht die zugewiesenen (Android hat
        |#     keine Möglichkeit, sie zur Laufzeit zu ändern), daher sagt dir z2-tile list, welche Nummer welche ist.
        |#
        |# Die Zeichnung (-i)
        |#   Ein Platz bekommt von selbst ein passendes Symbol, wenn der Name es verrät (remind.sh -> eine
        |#   Uhr), und z2-icon ersetzt es durch alles, was du willst — danach bleibt es in Ruhe.
        |#   -i legt die Zeichnung fest, **bevor** dieser Dialog erscheint, eine einzige Zeile entscheidet also
        |#   Name und Symbol zusammen (z2-icon sample listet die übergebbaren Namen).
        |#   ⚠ Ein unbekannter Name wird **vor jeder Zuweisung** abgelehnt — ein gesetzter Platz mit der
        |#     falschen Zeichnung fällt schwerer auf als einer, der nie gesetzt wurde.
        |#
        |# z. B. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "kein Schlaf"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l Lampe
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin()

    override val tileUsage: String get() = "Verwendung: z2-tile set <1-$tiles> <macro.sh|Befehl...> [--off <Befehl...>] [-l Beschriftung] " +
                "[-i Zeichnung] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: keine solche Zeichnung: $name (Liste mit z2-icon sample). Nichts wurde zugewiesen."

    override fun tileAddEmpty(n: Int): String = "z2-tile: auf Platz $n liegt noch nichts (zuerst zuweisen: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: Hier kann nicht um das Hinzufügen der Kachel gebeten werden (braucht Android 13, mit z2term im Vordergrund). " +
                "Der Platz ist zugewiesen — platziere ihn über den Bearbeitungsbildschirm (Stift) der Schnelleinstellungen."

    override val tileAddAsked: String get() = "Android wurde gefragt, ob die Kachel hinzugefügt wird — beantworte den Dialog. " +
                "(Nichts erschienen? Platziere sie stattdessen über den Bearbeitungsbildschirm.)"

    override val iconHelp: String get() = """
        |# Ersetzt die Symbole der Statusleiste und der Schnelleinstellungs-Kacheln durch deine eigene Pixelzeichnung.
        |#
        |# Hier anfangen
        |#   z2-icon pick 1     ... eine Zeichnung aus einer Liste wählen und auf Kachelplatz 1 legen
        |#   z2-icon list       ... was jeder Platz jetzt hat ('-p' zeigt auch die Zeichnungen)
        |#
        |# Was <Ziel> bedeutet
        |#   notify    das Symbol jeder Benachrichtigung dieser App (das in der Statusleiste)
        |#   1-$tiles      ein Kachelplatz der Schnelleinstellungen - dieselbe Nummer wie bei z2-tile
        |#
        |# Eine Zeichnung einlegen
        |#   pick <Ziel>              auflisten und per Nummer wählen (am einfachsten)
        |#   sample <Ziel> <Name>     wenn du den Namen schon kennst
        |#   edit <Ziel>              in ${d}EDITOR zeichnen oder die aktuelle korrigieren
        |#   set <Ziel> <Datei>       aus einer Datei lesen ('-' für stdin)
        |#   auto <1-$tiles|all>          erneut nach dem wählen, was die Kachel ausführt
        |#   clear <Ziel|all>         zurück zum eingebauten Symbol
        |#
        |# Wie fein das Raster ist
        |#   grid                     die Größe, in der neue Zeichnungen entstehen
        |#   grid <$grids>          festlegen ($grid, wenn du es nicht änderst)
        |#   scale <Ziel> <$grids>    die Zeichnung auf <Ziel> auf dieses Raster übertragen
        |#                            (dabei die diagonalen Stufen halbieren). **Das gibt dir
        |#                            Platz, feiner zu zeichnen** - das ausgegebene Symbol wird
        |#                            ohnehin geglättet, dafür musst du das also nicht ausführen.
        |#
        |# Eigene Zeichnungen in der Liste behalten
        |#   save <Ziel> <Name>       benennen, was jetzt auf <Ziel> liegt, und zur Liste hinzufügen
        |#   sample                   die Liste zur Auswahl (Nummer / Name /
        |#                            'builtin' = mitgeliefert, 'mine' = von dir gespeichert)
        |#   sample <Name>            diese Zeichnung zeigen
        |#   forget <Name>            eine deiner Zeichnungen aus der Liste nehmen (was du schon
        |#                            auf ein Ziel gelegt hast, bleibt dort)
        |#
        |# Wie man zeichnet
        |#   Es ist ein Raster aus Zeichen. '.' ' ' '0' '-' '_' lassen einen Punkt leer, alles andere
        |#   füllt ihn, nimm also das Zeichen, das du am besten siehst. Leerraum um
        |#   die Zeichnung wird ignoriert - sie wird zentriert, du musst also nicht jede Zeile
        |#   genau füllen. Das Raster ist $grids ('z2-icon grid').
        |#   ⚠ Ein größeres Raster macht kein größeres Symbol. Fülle das Raster, sonst wird die Zeichnung
        |#     kleiner als die ersetzte.
        |#
        |# Gut zu wissen
        |#   ⚠ Nur die Form kommt durch. Android färbt diese Symbole in einer eigenen Farbe
        |#     (Kacheln wechseln die Farbe zwischen an und aus), es gibt also keine Farbe zu wählen.
        |#   ⚠ **Der Umriss wird bei der Ausgabe geglättet.** Eine auf $grid Punkten gemachte Zeichnung
        |#     kommt auf einer Kachel (die viel größer als die Statusleiste gezeichnet wird) trotzdem glatt heraus, das
        |#     Raster ist also nichts, worum du dich im Alltag kümmern musst. 48 / 64 sind dafür da, wenn **du**
        |#     feiner zeichnen willst. 'show' gibt die Zeichnung so aus, wie du sie gemacht hast; die Kachel ist glatter.
        |#   ⚠ Eine Kachel bekommt von selbst eine Zeichnung, wenn der Name es verrät ('z2-tile set 1
        |#     remind.sh' legt dort eine Uhr hin). Was du hier setzt, gewinnt und wird nie wieder angefasst
        |#     — 'z2-icon auto 1' gibt diesen Platz an die automatische Wahl zurück.
        |#   ⚠ Zwei Symbole lassen sich nicht ändern: das im **Bearbeitungs**bildschirm der Schnelleinstellungen (aus dem
        |#     du Kacheln ziehst) und das Launcher-Symbol. Android legt sie bei der Installation fest.
        |#
        |# z. B. z2-icon pick 1             die Zeichnung für Platz 1 wählen
        |#      z2-icon edit 1              selbst neu zeichnen
        |#      z2-icon save 1 my-face      diese Zeichnung benennen und zur Liste hinzufügen
        |#      z2-icon sample 3 my-face    dieselbe Zeichnung auf Platz 3 legen
        |#      z2-icon grid 64             neue ab jetzt auf einem 64x64-Raster zeichnen
        |#      z2-icon scale 1 48          Platz 1 auf ein 48x48-Raster bringen, um feiner zu zeichnen
        |#      z2-icon list -p             prüfen, was wo liegt, mit Zeichnungen
        |#      z2-icon clear notify        das Benachrichtigungssymbol zurückholen
    """.trimMargin()

    override val iconUsage: String get() = "Verwendung: z2-icon pick <notify|1-$tiles> | sample [Name|Ziel Name] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [Datei|-] | " +
                "save <notify|1-$tiles> <Name> | forget <Name> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "keine solche Datei:"

    override val iconEditUnchanged: String get() = "unverändert."

    override val iconPickPrompt: String get() = "Nummer (oder Name), leer zum Abbrechen: "

    override val iconPickCancelled: String get() = "abgebrochen."

    override val iconNoEditor: String get() = "kein Editor gefunden. Setze ${d}EDITOR oder nimm: z2-icon set <Ziel> <Datei>"

    override val tileNoSuchMacro: String get() = "kein solches Makro in ~/.z2term/macros/ (nimm einen vollen Pfad, um es als Befehl auszuführen):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [Paket] / trace dump / trace stop
        |# Trace: zuerst die Erfassung einschalten; 5 Minuten, die neuesten 128 Ereignisse, nur im Speicher.
        |# Feldtypen und UTF-16-Längen, nie Textwerte. dump/stop geben JSON zurück; start setzt zurück.
        |# z2-noti list  … die gerade angezeigten Benachrichtigungen als TSV
        |#                 (Schlüssel / Paket / App-Name / Titel / Text)
        |# Nur Lesen. Absichtlich gibt es keinen Weg, eine Benachrichtigung zu drücken oder wegzuwischen:
        |# das würde auch Bezahl- und Senden-Schaltflächen anderer Apps drücken.
        |# Braucht Benachrichtigungszugriff (Einstellungen > Berechtigungen und Benachrichtigungen).
        |# Siehe auch: z2-when notify:otp / notify:pkg=<Teil> / notify:contains=<Teil>
    """.trimMargin()

    override val notiUsage: String get() = "Verwendung: z2-noti list | trace start [Paket] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [Name]     … einmal zum nächsten HH:MM (morgen, wenn schon vorbei)
        |# z2-alarm daily HH:MM [Name]  … jeden Tag um HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [Name] … einmal, in N Sekunden/Minuten/Stunden
        |# z2-alarm list                … das Geplante auflisten (JSON; "exact" sagt, wie pünktlich)
        |# z2-alarm cancel <id|Name|all> … abbrechen
        |# Beim Auslösen wird eine Zeile {"event":"alarm","name":…} an ~/.z2term/events.jsonl angehängt.
        |# Es weckt das Gerät auch im Doze. "exact":true heißt, es löst minutengenau aus;
        |# "exact":false heißt, Doze bietet nur alle 9-15 Min. ein Fenster, ein Smartphone mit
        |# ausgeschaltetem Bildschirm kann also so spät dran sein. Die Akkuoptimierung für diese App abzuschalten
        |# kippt es auf true (keine zusätzliche Berechtigung wird verlangt).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: kein nutzbarer date-Befehl"

    override val sessionHelp: String get() = """
        |# z2-session list                     … Tabs auflisten (Index / id / Art / Markierung / Name, TSV)
        |#   Markierungen: * = auf dem Bildschirm / ! = läuft / ? = nicht gestartet / @ = von einer Shell verbunden / - = sonst
        |# z2-session new [Name]               … einen Terminal-Tab öffnen (gibt Index und id zurück)
        |# z2-session send <Tab> <Text>...     … Text in diesen Tab tippen (führt ihn nicht aus)
        |# z2-session send <Tab> <Text> --enter … tippen, dann ausführen
        |# z2-session key <Tab> <Taste>...    … **Tasten** an diesen Tab senden (C-c / M-x / F5 / Up …)
        |#   Modifikatoren: C- (Strg) und M- (Meta=Alt); sie stapeln sich, wie in C-M-a
        |#   besondere: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Tasten mit Shift wie C-S-a werden abgelehnt (ein Terminal unterscheidet Shift nicht: dieselben Bytes wie C-a)
        |# z2-session key <Tab> --raw '\x1b[A' … alles andere, als Bytes (\xHH \e \n \r \t \0)
        |# z2-session capture [Tab] [--all]    … den Bildschirm dieses Tabs holen (--all mit Verlauf)
        |# z2-session attach <Tab>             … mit diesem Tab verbunden bleiben und einfach darin tippen
        |#   verlassen mit Strg+] (jederzeit) oder mit ~. am Zeilenanfang (wie bei ssh)
        |#   ⚠ über SSH geht nur Strg+]: Das ssh vor dir frisst ~. und beendet die SSH-Sitzung
        |#   schreibe ~~ für ein wörtliches ~ am Zeilenanfang, ~ und dann Strg+] für ein wörtliches Strg+]
        |#   solange verbunden folgt der Tab DEINER Fenstergröße; beim Verlassen geht er zurück
        |# z2-session close <Tab>              … diesen Tab schließen (nie den letzten)
        |#
        |# <Tab> kann der Index aus list, eine id oder ein Tabname sein. '.' oder weggelassen = der Tab auf dem Bildschirm.
        |# z. B. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "Verwendung: z2-session list | new [Name] | send <Tab> <Text>... [--enter] | " +
                "key <Tab> <Taste>... | key <Tab> --raw <Bytes> | capture [Tab] [--all] | " +
                "attach <Tab> | close <Tab>"

    override val keyNothing: String get() = "z2-session key: keine Taste angegeben"

    override val keyRawEmpty: String get() = "z2-session key: --raw braucht die zu sendenden Bytes"

    override val keyUnknown: String get() = "z2-session key: unbekannte Taste (Liste in 'z2-session -h'):"

    override val keyBadEscape: String get() = "z2-session key: die Escape-Sequenz ist nicht lesbar:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: Das Terminal unterscheidet Shift nicht, '$asWritten' wären also " +
            "genau dieselben Bytes wie '$equivalentTo'. Schreibe '$equivalentTo', wenn du das meinst."

    override fun attachNoSuchTab(target: String): String = "kein solcher Tab: '$target' (führe 'z2-session list' aus, um die Tabs zu sehen)"

    override val attachNotTerminal: String get() = "das ist ein GUI-Tab ohne Shell zum Verbinden. Wähle einen Terminal-Tab."

    override val attachNotStarted: String get() = "dieser Tab ist noch nicht gestartet (in list mit '?' markiert). Öffne ihn einmal in der App und verbinde dich dann."

    override val attachExited: String get() = "dieser Tab ist schon beendet. Seinen Bildschirm kannst du noch mit 'z2-session capture' lesen."

    override val attachSelf: String get() = "das ist der Tab, in dem du tippst. Einen Tab mit sich selbst zu verbinden lässt seine eigene Ausgabe " +
            "für immer als Eingabe zurückkommen, das lässt sich nicht rückgängig machen. Wähle einen anderen Tab."

    override val attachLoop: String get() = "dieser Tab ist schon zurück mit diesem verbunden, die beiden würden sich also " +
            "endlos füttern. Trenne zuerst einen davon (Strg+])."

    override val serverHelp: String get() = """
        |# z2-server list                … eingetragene Server (Index / id / Zustand / Markierung / Name, TSV)
        |#   Markierungen: * = aktiviert / - = deaktiviert
        |# z2-server start <Server>      … als Hintergrundserver starten (hält das Gerät erreichbar)
        |# z2-server stop <Server>       … diesen stoppen (die anderen laufen weiter)
        |# z2-server status [<Server>]   … Zustand, pid, Neustarts und letzter Exit-Code
        |#
        |# <Server> kann der Index aus list, eine id oder der Name sein, den du in der App vergeben hast.
        |# Nur in der App eingetragene Server lassen sich starten; das trägt nie einen neuen ein.
        |#
        |# Warum es das gibt: Ein direkt aus einer Regel gestarteter Dienst läuft AUSSERHALB des Rahmens für
        |# Hintergrundserver (kein WakeLock, kein WifiLock, kein Vordergrunddienst) und antwortet daher nicht mehr, sobald der
        |# Bildschirm ausgeht. Über hier gestartet kommt er in diesen Rahmen.
        |# z. B. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "Verwendung: z2-server list | start <Server> | stop <Server> | status [<Server>]"

    override val serverNotFound: String get() = "z2-server: kein solcher Server (siehe 'z2-server list'):"

    override val serverAmbiguous: String get() = "z2-server: Der Name passt auf mehr als einen Server; nimm die id aus 'z2-server list':"

    override val serverNone: String get() = "z2-server: noch keine Server eingetragen (füge einen in der App hinzu: 📜 -> Server)."

    override val serverLowPowerWarn: String get() = "z2-server: Hinweis - der Stromsparmodus ist an, es wird also kein WakeLock/WifiLock gehalten. " +
            "Der Server antwortet bei ausgeschaltetem Bildschirm eventuell nicht mehr " +
            "(Einstellungen -> Automatisierung -> Schutz von Hintergrundprozessen)."

    override val whenHelp: String get() = """
        |# z2-when <Auslöser> run <cmd...>       … eine Regel eintragen
        |#   Auslöser: charge:start | charge:stop  (braucht Erkennung AN)
        |#            battery:below=N | battery:above=N  (braucht Erkennung AN)
        |#            battery:temp>N | battery:temp<N  … Akkutemperatur (°C) hat N überschritten (braucht Erkennung AN)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min stunde tag monat wochentag'  (Wochentag 0-7 / 0,7=Sonntag. In Anführungszeichen: es hat Leerzeichen)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<Name>  (braucht Erkennung AN)
        |#            net:online | net:offline  … eine nutzbare Verbindung ist da / weg
        |#            net:wifi | net:mobile | net:ethernet  … die genutzte Verbindung wechselte dorthin
        |#                                        (braucht Erkennung AN; zählt mobile Daten, nicht nur WLAN)
        |#            share:any | share:text | share:file | share:contains=<Teil> | share:ext=<Endung>
        |#                                        … etwas wurde aus einer anderen App an z2term geteilt
        |#            boot                        … das Gerät ist fertig gestartet (geht mit Erkennung AUS)
        |#            sms:any | sms:from=<Teil> | sms:contains=<Teil> | sms:otp  (braucht RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (Erkennung AN)
        |#            file:new=<Ordner>[,ext=<Endung>]  … eine neue Datei kam in diesem Ordner an (braucht Erkennung AN)
        |#            file:changed=<Ordner>[,ext=<Endung>] | file:deleted=<Ordner>[,ext=<Endung>]
        |#                                        … eine Datei dort wurde überschrieben / ist weg (braucht Erkennung AN)
        |#            bt:connect | bt:disconnect | bt:connect=<Name> | bt:disconnect=<Name>
        |#                                        … ein Bluetooth-Gerät verband / trennte sich (Name: ein Teil davon oder die Adresse)
        |#                                        (braucht Erkennung AN und unter Android 12+ die Berechtigung Geräte in der Nähe)
        |#            app:open=<App> | app:close=<App>  … diese App kam nach vorne / du hast sie verlassen (Paket oder App-Name, ein Teil davon)
        |#                                        (braucht z2term in den Bedienungshilfen; geht mit Erkennung AUS)
        |#            steps:reach=N               … die heutige Schrittzahl erreichte N (Erkennung AN + Berechtigung Körperliche Aktivität)
        |#            notify:any | notify:otp | notify:pkg=<Teil> | notify:title=<Teil> | notify:contains=<Teil>
        |#                                        … eine Benachrichtigung kam an (braucht Benachrichtigungszugriff)
        |#            event:<Name> | event:<Präfix>* | event:*  … ein beliebiges Geräteereignis, nach Name
        |#              (z2-when events listet sie; dieselben Namen wie in events.jsonl)
        |# Name (optional, direkt nach dem Auslöser — vor run):
        |#   name=<Text>             … wofür diese Regel ist, gezeigt im Tab Automatisierung
        |#                             (in Anführungszeichen, wenn Leerzeichen). Leer = der Auslöser wird gezeigt.
        |#                             Nur Anzeige: Es ändert nichts daran, wann die Regel läuft.
        |# Filter (jeder Auslöser, direkt danach — vor run):
        |#   if=<Bed>[,<Bed>...]     … nur wenn das Gerät in diesem Zustand ist (UND; ! verneint)
        |#                             Schlüssel sind die von z2-state: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             z. B. if=wifi,!screen / if=ssid=Zuhause / if=level<30
        |#   if_any=<Bed>[,<Bed>...] … läuft, wenn **eine davon** gilt (das Komma bedeutet ODER)
        |#                             zusammen mit if=: "alle aus if UND eine aus if_any"
        |#   else=<cmd>              … dies **stattdessen** ausführen, wenn if / if_any nicht galt
        |#                             ⚠ nur if / if_any führen hierher. Ein durch between /
        |#                             days / cooldown übersprungener Lauf führt gar nichts aus — eine nachts
        |#                             ausgeschaltete Regel muss nachts still bleiben, else eingeschlossen.
        |#   cooldown=30m            … innerhalb dieser Zeit nicht erneut ausführen (10s / 30m / 2h)
        |#   between=22:00-07:00     … nur in diesem Fenster (über Mitternacht hinweg)
        |#   days=mon-fri            … nur an diesen Tagen (Namen oder cron-Zahlen 0-7, 0/7 = Sonntag)
        |#   answer='<Frage>=><Antwort>' … wenn der Befehl dies im Terminal fragt, die Antwort
        |#                             und Enter tippen (wiederholbar; z. B. answer='[y/N]=>y'). Vergleicht mit dem Ende
        |#                             der letzten Ausgabezeile.
        |#   secret='<Frage>'        … dasselbe für eine verborgene Antwort (ein Passwort). Ihr Inhalt wird im
        |#                             Regeleditor der App eingegeben und verschlüsselt in der App aufbewahrt.
        |# Übersprungene Läufe werden auch aufgezeichnet — z2-when fired zeigt skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, eine nie laufende Regel lässt sich also erklären. Die Schaltfläche ▶ in der App
        |# ignoriert alle Filter (sie ist zum Ausprobieren der Regel da).
        |# z2-when events                        … die mit event: nutzbaren Namen auflisten
        |# z2-when pause / resume                … automatische Läufe stoppen / fortsetzen (Regeln bleiben)
        |# z2-when fired [n]                     … letzte Auslösungen (Zeit / id / Auslöser / run|paused)
        |# z2-when list                          … eingetragene Regeln (id / on|off / Auslöser / -> / cmd, TSV;
        |#                                         Name und Filter, falls vorhanden, in [] am Ende)
        |# z2-when remove <id|all>  (rm geht)    … löschen
        |# z2-when on <id> / off <id>            … aktivieren / deaktivieren
        |# z2-when log <id>                      … das Ausführungsprotokoll dieser Regel (tail)
        |# Beim Auslösen läuft der Befehl in der gewählten Distribution mit Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) in der Umgebung.
        |# Für net: du bekommst Z2_WHEN_NET (die Verbindung jetzt) und Z2_WHEN_NET_PREV (die davor).
        |# Für share: du bekommst Z2_WHEN_SHARE (den Text oder die Pfade der übernommenen Dateien)
        |# und Z2_WHEN_SHARE_KIND (text|file). Das Geteilte kommt wie bisher auch in die Eingabezeile.
        |# Für file: du bekommst Z2_WHEN_FILE (voller Pfad) und Z2_WHEN_DIR.
        |# Für notify: du bekommst Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (und Z2_WHEN_OTP für notify:otp). notify:category= passt genau: call, missed_call, msg, ...
        |# Für event: du bekommst auch Z2_WHEN_EVENT (den Ereignisnamen); alarm / notify_action ergänzen
        |# Z2_WHEN_EVENT_NAME (die Kennung, mit der du es scharf gemacht hast) und Z2_WHEN_ACTION (gedrückte Schaltfläche).
        |# Für battery:temp bekommst du Z2_WHEN_TEMP (°C). Für bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Für app: Z2_WHEN_APP_PKG (die App vorne jetzt) / Z2_WHEN_APP_PREV (die davor).
        |# Für steps: Z2_WHEN_STEPS (heutige Schritte). Für file: auch Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated liefern Z2_WHEN_PKG, timezone_changed liefert Z2_WHEN_TZ,
        |# usb_attached / usb_detached liefern Z2_WHEN_USB (Name) / Z2_WHEN_USB_ID (Hersteller:Produkt).
        |# Dieselbe Regel löst nicht zweimal binnen 10 Sekunden aus (Ereignisse wie screen_on kommen oft).
        |# z. B. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "Klingel: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Morgenbericht' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Automatische Läufe pausiert (z2-when resume zum Fortsetzen)"

    override val whenResumed: String get() = "Automatische Läufe fortgesetzt"

    override val whenNoFires: String get() = "(noch nichts ausgelöst)"

    override val whenPausedNote: String get() = "# pausiert (z2-when resume zum Fortsetzen)"

    override val whenNoLog: String get() = "(noch kein Protokoll)"

    override val whenWriteFailed: String get() = "z2-when: die Regel konnte nicht geschrieben werden"

    override val whenUnknownIfKey: String get() = "z2-when: unbekannter if=-Schlüssel (z2-state listet die nutzbaren):"

    override val whenUnknownTrigger: String get() = "z2-when: unbekannter Auslöser (z2-when ohne Argumente listet sie):"

    override val whenBadTriggerSpec: String get() = "z2-when: dieser Auslöser nimmt dieses Argument nicht:"

    override val whenPausedWarn: String get() = "Hinweis: automatische Läufe sind pausiert (z2-when resume zum Fortsetzen)"

    override val whenRunJoined: String get() = "Hinweis: Zeilenumbrüche im Befehl wurden zu Leerzeichen (eine Regel liest nur eine Zeile)"

    override val whenEventList: String get() = """
        |screen_on              Bildschirm eingeschaltet                [Erkennung AN]
        |screen_off             Bildschirm ausgeschaltet                [Erkennung AN]
        |unlocked               Gerät wurde entsperrt                   [Erkennung AN]
        |power_connected        Laden begonnen                          [Erkennung AN]
        |power_disconnected     Laden beendet                           [Erkennung AN]
        |battery_low            Akku wurde schwach                      [Erkennung AN]
        |battery_okay           Akku hat sich erholt                    [Erkennung AN]
        |battery_level          Stand hat eine 10%-Marke überschritten  [Erkennung AN]
        |wifi_connected         mit WLAN verbunden                      [Erkennung AN]
        |wifi_disconnected      WLAN verlassen                          [Erkennung AN]
        |net_online             eine funktionierende Verbindung ist da  [Erkennung AN]
        |net_offline            keine funktionierende Verbindung        [Erkennung AN]
        |net_wifi               die genutzte Verbindung ist WLAN        [Erkennung AN]
        |net_mobile             die genutzte Verbindung ist mobil       [Erkennung AN]
        |headset_plugged        Kabelkopfhörer eingesteckt              [Erkennung AN]
        |headset_unplugged      Kabelkopfhörer ausgesteckt              [Erkennung AN]
        |bt_audio_connected     Bluetooth-Audio verbunden               [Erkennung AN]
        |bt_audio_disconnected  Bluetooth-Audio weg                     [Erkennung AN]
        |airplane_on            Flugmodus an                            [Erkennung AN]
        |airplane_off           Flugmodus aus                           [Erkennung AN]
        |ringer_normal          Klingelton auf normal                   [Erkennung AN]
        |ringer_vibrate         Klingelton auf Vibration                [Erkennung AN]
        |ringer_silent          Klingelton auf lautlos                  [Erkennung AN]
        |bt_connected           ein Bluetooth-Gerät hat sich verbunden  [Erkennung AN]
        |bt_disconnected        ein Bluetooth-Gerät hat sich getrennt   [Erkennung AN]
        |powersave_on           Energiesparmodus an                     [Erkennung AN]
        |powersave_off          Energiesparmodus aus                    [Erkennung AN]
        |dnd_on                 Nicht stören an                         [Erkennung AN]
        |dnd_off                Nicht stören aus                        [Erkennung AN]
        |app_installed          eine App wurde installiert              [Erkennung AN]
        |app_removed            eine App wurde deinstalliert            [Erkennung AN]
        |app_updated            eine App wurde aktualisiert             [Erkennung AN]
        |timezone_changed       die Zeitzone hat sich geändert          [Erkennung AN]
        |usb_attached           ein USB-Gerät wurde eingesteckt         [Erkennung AN]
        |usb_detached           ein USB-Gerät wurde ausgesteckt         [Erkennung AN]
        |boot                   das Gerät wurde gestartet               [immer]
        |alarm                  ein z2-alarm hat ausgelöst              [immer]
        |notify_action          eine Benachrichtigungsschaltfläche      [immer]
        |unlock_failed          Entsperren fehlgeschlagen               [immer, braucht Einrichtung]
        |unlock_succeeded       entsperrt nach einem Fehlschlag         [immer, braucht Einrichtung]
        |voice                  ein Sprachbefehl wurde gehört           [immer, über die Kachel oder den Assistenten]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Namen für event:<Name>. In derselben Reihenfolge wie in events.jsonl.
        |    # Die obere Gruppe braucht Erkennung AN (Einstellungen > Berechtigungen und Benachrichtigungen),
        |    # die untere scharf schaltest du selbst, sie geht also mit Erkennung AUS.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … prüfen, herunterladen und die neue Version an den Installer geben
        |# z2-update --check             … nur sagen, ob es eine neuere Version gibt
        |# z2-update --keep              … die heruntergeladene .apk behalten (standardmäßig gelöscht)
        |# z2-update --dir <Ordner>      … in diesen Ordner statt in den der App herunterladen
        |#
        |# Woher es kommt
        |#   Von der GitHub-Releases-Seite von z2term, dieselbe Datei, die du von Hand laden würdest
        |#   (z2term-<Version>.apk, mit dem Release-Schlüssel signiert). Nichts anderes wird kontaktiert, und
        |#   überhaupt nichts, bis du das ausführst.
        |#
        |# Was es kann und was nicht
        |#   ⚠ **Es kann nicht still installieren.** Android zeigt für eine App, die sich selbst ersetzt, immer
        |#     den eigenen Bildschirm "installieren?", das letzte Tippen ist also immer deins. Dieser Befehl erledigt jeden
        |#     Schritt bis zu diesem Bildschirm.
        |#   ⚠ Erlaube beim ersten Mal **"Unbekannte Apps installieren"** für z2term (es sagt es dir, und
        |#     ⚙Einstellungen hat dieselbe Schaltfläche). Ohne das erscheint der Installationsbildschirm nie.
        |#   ⚠ Aus F-Droid oder einem Store installiert? Dann lehnt es ab und sagt dir, dort zu aktualisieren —
        |#     deine Version zu ersetzen ist deren Sache, nicht unsere.
        |#
        |# Die heruntergeladene Datei
        |#   Standardmäßig landet sie im Ordner der App und wird gelöscht, sobald die Installation
        |#   durch ist (und beim nächsten Start, falls die App mitten in der Installation beendet wurde — was
        |#   beim Ersetzen seiner selbst normal ist). --keep oder --dir ändern das; ⚙Einstellungen hat dieselben
        |#   zwei Einstellungen für die Schaltfläche dort.
        |#
        |# z. B. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # jede Nacht schauen (fragt dich trotzdem)
    """.trimMargin()

    override val updateUsage: String get() = "Verwendung: z2-update [--check] [--keep] [--dir <Ordner>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current ist die neueste Version."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: suche nach einer neueren Version ..."

    override val updateHandedToInstaller: String get() = "z2-update: Der Installationsbildschirm ist auf dem Gerät offen — bestätige ihn zum Abschließen."

    override fun updateNoApk(url: String): String = "z2-update: Dieses Release hat keine .apk angehängt. Hol sie dir von der Release-Seite: $url"

    override val updateNeedPermission: String get() = "z2-update: Erlaube zuerst \"Unbekannte Apps installieren\" für z2term " +
            "(Einstellungen > Apps > Spezieller App-Zugriff > Unbekannte Apps installieren, oder die Schaltfläche in den Einstellungen von z2term)."

    override val updateManagedByStore: String get() = "z2-update: Dieser Build wurde aus einem Store installiert (F-Droid / Play). Aktualisiere dort."

    override fun updateFailed(reason: String): String = "z2-update: fehlgeschlagen: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Sprachbefehl — Wörtertabelle (Deutsch). voice.sh liest sie. Bearbeite sie, um hinzuzufügen oder zu ändern, was du sagen kannst.
#
# So schreibst du sie:
#   Schlüssel = eine Art|eine andere Art|...   mit "|" trennen; von oben geprüft
#   ein * nimmt die Wörter an dieser Stelle (öffne * -> "öffne Karten" ergibt "Karten")
#   *X* nimmt die Wörter auf beiden Seiten von X
#   Schlüssel =                       nichts rechts schaltet diesen aus
#   Satzzeichen werden ignoriert, ebenso Groß-/Kleinschreibung und zusätzliche Leerzeichen
#   Ein hier fehlender Schlüssel nutzt den mitgelieferten Wert (lösche eine Zeile, um das Original zurückzubekommen)
#   Zeilen mit say_ / msg_ / fmt_ / title_ am Anfang sind das, was gesprochen oder gezeigt wird; {…} bekommt einen Wert
#
# Ohne Sprechen ausprobieren: sh ~/.z2term/macros/voice.sh try nächstes Lied
# "eigene Wörter = ein Befehl" kommen in ~/.z2term/voice/commands.txt (vor dieser Tabelle geprüft).

# --- Wörter, auf die gehört wird ---
ai = frag die ki *|frage die ki *|hey ki *|ki *|frage *
media_next = nächstes lied|nächster titel|nächstes|weiter|überspringen|lied überspringen|nächster song
media_previous = vorheriges lied|vorheriger titel|vorheriges|zurück|letztes lied
media_pause = pause|pausieren|stopp|stop|musik stoppen|musik pausieren|halt die musik an
media_play = abspielen|weiterspielen|fortsetzen|musik abspielen|musik fortsetzen|spiel weiter|play
volume_up = lauter|lautstärke hoch|mach lauter|mach es lauter|dreh lauter|lautstärke erhöhen
volume_down = leiser|lautstärke runter|mach leiser|mach es leiser|dreh leiser|lautstärke verringern
volume_mute = stumm|stummschalten|ton aus|ruhe
volume_unmute = stummschaltung aufheben|ton an|ton wieder an
time = wie spät ist es|wie viel uhr ist es|uhrzeit|die uhrzeit|wie spät|aktuelle uhrzeit
date = welches datum ist heute|welcher tag ist heute|das datum|datum|was ist heute für ein tag|heutiges datum
battery = akku|akkustand|wie viel akku|wie viel akku habe ich noch|akkuzustand|wie voll ist der akku
steps = schritte|wie viele schritte|schrittzahl|meine schritte|wie viele schritte heute|schritte heute|wie viele schritte bin ich gelaufen
torch_on = taschenlampe an|taschenlampe einschalten|licht an|licht einschalten|lampe an|taschenlampe
torch_off = taschenlampe aus|taschenlampe ausschalten|licht aus|licht ausschalten|lampe aus
screenshot = screenshot|bildschirmfoto|mach einen screenshot|bildschirm aufnehmen
home = startbildschirm|zum startbildschirm|home
news = nachrichten|lies die nachrichten|was gibt es neues|neuigkeiten|schlagzeilen|rss
weather = wetter|wie ist das wetter|wetter heute|das wetter|wie wird das wetter|wettervorhersage
weather_tomorrow = wetter morgen|wie wird das wetter morgen|wettervorhersage für morgen|morgen wetter
memo_read = lies meine notizen|notizen vorlesen|meine notizen|zeig meine notizen|lies die notizen
memo_add = notiere dass *|notiere *|notiz dass *|notiz *|merk dir dass *|merk dir *|schreib auf *
remind_list = erinnerungen|meine erinnerungen|erinnerungen auflisten|zeig erinnerungen|welche erinnerungen habe ich|mein kalender|meine termine
remind = erinnere mich daran *|erinnere mich an *|erinnere mich dass *|erinnere mich *|erinnerung für *|erinnerung *
wake = weck mich um *|wecke mich um *|weck mich *|wecker auf *|wecker für *|stell einen wecker auf *|alarm um *
help = was kann ich sagen|was kannst du|hilfe|befehle
timer = timer für *|stell einen timer auf *|stell einen timer für *|starte einen timer für *|timer auf *|* timer
navi = navigiere zu *|navigiere nach *|route zu *|route nach *|bring mich zu *|wie komme ich zu *|ich will nach *|ich möchte nach *
navi_ask = navigation|navigieren|route|navigation starten
place_phone = wie ist die telefonnummer von *|die telefonnummer von *|telefonnummer von *|nummer von *|* telefonnummer
call = ruf * an|rufe * an|anrufen *|ruf *|anruf bei *
play = spiel *|spiele *|abspielen *
open = öffne *|öffnen *|starte *|start *
# Nach Art öffnen (die Standard-App des Smartphones). Z. B. öffne den Browser
open_browser = browser|den browser|webbrowser|internet
open_maps = karten|karte|die karte|maps
open_mail = mail|e-mail|email|die mail
open_camera = kamera|die kamera
open_settings = einstellungen|die einstellungen
open_calendar = kalender|den kalender
open_music = musik|die musik
open_gallery = fotos|galerie|die galerie|bilder
open_contacts = kontakte|die kontakte
open_dialer = telefon|das telefon|wählen
open_messages = nachrichten app|sms|die nachrichten app|mitteilungen
open_calculator = taschenrechner|rechner|den taschenrechner
# Spitznamen für Apps. Öffnet der gesprochene Name sie nicht, schreibe: Spitzname:App-Name|Spitzname:App-Name
app_names =

# Lautstärke mit Zahl ("leiser 3", "lautstärke 10", "lautstärke 50 prozent", "lautstärke maximal") — passt, wenn enthalten
volume_word = lautstärke
volume_up_word = hoch|lauter|erhöhen|mehr
volume_down_word = runter|leiser|verringern|weniger
volume_max_word = maximal|maximum|voll
volume_min_word = minimal|minimum|null
volume_percent_word = %|prozent
volume_mute_word = stumm|aus
volume_unmute_word = an

# Zeitdauern und Uhrzeiten (Timer, Erinnerung, Wecker)
unit_hour = stunden|stunde|std
unit_minute = minuten|minute|min
unit_second = sekunden|sekunde|sek
unit_day = tagen|tage|tag
when_before = in|nach
when_after = ab jetzt|später
clock_at = um|gegen
clock_hour = uhr
clock_minute =
clock_half =
clock_am = morgens|am morgen|vormittags|früh
clock_pm = abends|am abend|nachmittags|am nachmittag|nachts|heute abend
day_today = heute
day_tomorrow = morgen
day_after_tomorrow = übermorgen
day_daily = jeden tag|täglich
day_weekdays = an werktagen|werktags|wochentags
day_every = jeden|jede|jedes
wd_mon = montag
wd_tue = dienstag
wd_wed = mittwoch
wd_thu = donnerstag
wd_fri = freitag
wd_sat = samstag
wd_sun = sonntag
# Kleine Wörter, die zwischen dem Text und den herausgenommenen Teilen übrig bleiben
remind_filler = zu|um|am|an|in|über|dass|daran|für|mich|mir|den|die|das

# --- Was gesprochen oder gezeigt wird ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Sprachbefehl
title_navi = Navigation
title_ai = KI
title_timer = Timer
title_news = Nachrichten
title_memo = Notizen
title_remind = Erinnerungen
title_call = Anruf
title_help = Was du sagen kannst
title_rule_voice = Sprachbefehl
title_rule_voice_locked = Sprachbefehl (gesperrt)
title_rule_timer = Sprach-Timer
title_rule_steps = Schritte zählen (für den Sprachbefehl)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} Uhr
fmt_weekdays = Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag
fmt_months = Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember
say_time = Es ist {clock}
say_date = Heute ist {weekday}, der {day}. {month_name}
say_battery = Der Akku steht bei {n} Prozent. {charging}
say_charging = Er wird geladen.
say_steps = Du bist heute {n} Schritte gegangen
say_steps_none = Noch keine Schritte aufgezeichnet. Dafür braucht es die Berechtigung Körperliche Aktivität und eine Schrittregel
say_news = Hier die neuesten Schlagzeilen. {titles}
say_news_none = Es gibt keine neuen Nachrichten
fmt_weather_lang = de
say_weather = Gerade ist es {desc}, {temp} Grad
say_weather_tomorrow = Morgen: {desc}, höchstens {hi} und mindestens {lo} Grad, Regenwahrscheinlichkeit {rain} Prozent
say_weather_fail = Ich konnte das Wetter nicht abrufen
say_memo_saved = Notiert
say_memo_recent = Deine letzten Notizen. {memos}
say_memo_none = Es gibt keine Notizen
say_remind_ok = Erinnerung gesetzt. {res}
say_remind_fail = Ich konnte sie nicht setzen. {res}
say_remind_unclear = Ich habe nicht verstanden, wann oder woran ich dich erinnern soll. Versuche: erinnere mich morgen um 7 Uhr morgens daran, den Müll rauszubringen
say_remind_missing = Das Erinnerungsmakro ist nicht installiert. Führe in einem Terminal z2-macro install remind aus
fmt_wake_label = aufstehen
say_wake = Zeit zum Aufstehen
say_wake_set = Ich wecke dich um {clock}
say_wake_unclear = Ich habe die Weckzeit nicht verstanden
fmt_unit_hour = Stunden
fmt_unit_minute = Minuten
fmt_unit_second = Sekunden
fmt_timer_label = {n} {unit}
say_timer_set = Timer für {label} gestellt
say_timer_done = {label} sind um
say_timer_unclear = Ich habe nicht verstanden, wie lang der Timer sein soll
say_call_missing = Ich habe keine Nummer für {who} gefunden
msg_call_missing = "{who}" steht nicht in {file}. Schreibe pro Zeile ein "Name Nummer".
say_open_missing = Ich habe keine App namens {name} gefunden
say_help = Was du sagen kannst, steht in einer Benachrichtigung
say_locked = Bitte entsperre das Smartphone
msg_help = wie spät ist es, Datum, Akku, Schritte / leiser 3, Lautstärke 50 Prozent / nächstes Lied, Pause / Timer für 3 Minuten, weck mich um 7 / erinnere mich morgen um 7 Uhr daran, den Müll rauszubringen, Erinnerungen / Nachrichten, Wetter, Wetter morgen / notiere Milch kaufen, lies meine Notizen / Taschenlampe an, Screenshot / öffne … / ruf … an / navigiere zu …, Telefonnummer von … / KI … (einschalten mit voice.sh ai) / Wörter ändern: voice.sh words
msg_unknown = Nicht verstanden: {text}
msg_locked = "{text}" geht weiter, sobald du das Smartphone entsperrst
msg_navi_where = Sag, wohin du willst
msg_navi_hint = Wohin (ein Ortsname)
msg_ai_off = KI ist aus. Drücke Kopieren und füge es in ein Terminal ein, um {ai} zu nutzen (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = KI ist aus. Installiere eines von codex / claude / agy, melde dich an und wähle es dann in einem Terminal, z. B. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} wurde nicht gefunden. Installiere es und melde dich an, oder wähle ein anderes mit sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Frage die KI ({ai})…
msg_ai_noanswer = Keine Antwort zurückgekommen (Protokoll: {log})
msg_ai_usage = ai: gib eines von off | codex | claude | agy an
msg_ai_how = KI ist aus. Zum Nutzen: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; sie wird nur gefragt, wenn du mit dem Signalwort beginnst, "KI" oder "Frage"). Modell: sh ~/.z2term/macros/voice.sh model <Name> (leer = Standard dieser KI)
fmt_ai_prompt = (Die Antwort wird vorgelesen. Antworte ohne Befehle oder Dateien auf Deutsch in 2 oder 3 Sätzen, ohne Symbole oder Aufzählungen.)
msg_setup_done = Regeln für den Sprachbefehl eingetragen (schon vorhandene wurden nicht erneut hinzugefügt):
msg_setup_steps = Die Regel Schritte zählen (steps:reach=1000000) hält nur den Schrittzähler am Laufen, damit du nach den heutigen Schritten fragen kannst. Dieses Ziel wird nie erreicht, sie führt also nichts aus.
msg_words_file = Wörtertabelle: {file}  (bearbeite sie, um Ausdrucksweisen hinzuzufügen; sh ~/.z2term/macros/voice.sh words öffnet sie)"""
