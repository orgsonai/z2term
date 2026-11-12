package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Italiano (`it`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: scheda (タブ) / toccare (タップ) / tenere premuto (長押し) / scorrere (フリック) / file / cartella /
 * Impostazioni (設定) / appunti / macro (la) / riquadro (タイル) / pannello laterale (エッジパネル) /
 * server residente (常駐サーバー)。呼びかけは tu。
 * ⚠ **アポストロフィは活字の ’ を使う** — シェルスクリプトの中に入るので、' だと引用符とぶつかる。
 */
internal object CliIt : CliLanguage {

    override val code: String = "it"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgIt) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgIt(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Installazione della pila GUI",
            cleanInstalling = "🧹 Reinstallazione pulita della pila GUI",
            noPackageManager = "❌ Nessun gestore di pacchetti supportato (apk/apt-get/pacman) trovato.",
            invalidGeometry = "❌ Risoluzione non valida",
            noXvnc = "❌ Xvnc/Xtigervnc assente (tigervnc non è installato)",
            alreadyRunning = "✅ La GUI è già in esecuzione",
            startingXvnc = "▶ Avvio",
            xvncFailed = "❌ Avvio di Xvnc non riuscito. Registro:",
            ready = "✅ GUI pronta. Connettiti da una scheda GUI di z2term.",
            running = "✅ GUI in esecuzione",
            stopped = "⏹ La GUI è ferma",
            stoppedMsg = "⏹ Fermata",
            usage = "Uso: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Impossibile installare la pila GUI (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Controlla la rete. Per ripararla puoi eseguire z2gui clean in un terminale.",
            audioInstalling = "🔊 Audio della GUI: installazione di PulseAudio",
            audioNoPulse = "⚠️ Audio della GUI: niente pulseaudio, si continua senza audio",
            audioNoPactl = "⚠️ Audio della GUI: niente pactl, si continua senza audio",
            audioStartFailed = "⚠️ Audio della GUI: PulseAudio non è partito",
            audioReady = "🔊 Audio della GUI: z2sink.monitor →",
            menuApps = "Applicazioni",
            menuWindows = "Finestre",
            menuDesktops = "Desktop virtuali",
            menuPreviousDesktop = "Desktop precedente",
            menuNextDesktop = "Desktop successivo",
            menuReload = "Ricarica il menu"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Numero di porta non valido",
            configOk = "configurazione di sshd(dropbear) OK",
            installingDropbear = "📦 dropbear non è installato — installazione…",
            dropbearInstallFailed = "❌ Impossibile installare dropbear. Controlla la rete e i nomi dei pacchetti.",
            privilegedPortWarn = "⚠️ Porta privilegiata; proot (senza root) probabilmente non può usarla (scegli 1024+).",
            lanExposeWarn = "⚠️ Modalità esposta a LAN/WAN: autenticazione a chiave obbligatoria, usa una chiave robusta.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys è vuoto. Avvio esposto alla LAN annullato.",
            noAuthorizedKeysHint = "   Registra la tua chiave pubblica in ~/.ssh/authorized_keys e riprova.",
            loopbackBind = "🔒 solo loopback. Per esporre alla LAN, passa --lan o imposta Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear in primo piano (Ctrl-C per fermare)",
            listeningLan = "✅ dropbear in ascolto (root, solo autenticazione a chiave)",
            listeningLoopback = "✅ dropbear in ascolto (root, solo autenticazione a chiave, loopback)",
            startupFailed = "❌ Avvio di dropbear non riuscito. Registro:",
            deviceIp = "ip-dispositivo",
            authKeysHint = "ℹ ~/.ssh/authorized_keys è vuoto. Registra la chiave pubblica di un client per connetterti.",
            authKeysExample = "   es. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Preparazione del portachiavi di pacman (solo la prima volta, senza rete). Ci vuole un momento…",
            "keyring.msgOk" to "✅ Portachiavi pronto. Ora si possono installare i pacchetti.",
            "keyring.msgInitFail" to "❌ pacman-key --init non è riuscito. Verrà ritentato alla prossima apertura di questa scheda.",
            "keyring.msgNoKeyrings" to "❌ Nessun portachiavi incluso trovato in /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate non è riuscito. Verrà ritentato alla prossima apertura di questa scheda.",
            "action.description" to "Macro nominate di azioni Android. Attiva z2term Android actions con z2-key permission.\nSalva convalida e sostituisce la definizione; screen=current registra lo schermo.\nrun attende la fine; start restituisce un ID di esecuzione. Interrompere run ferma quell’esecuzione.\nUn’esecuzione alla volta, 64 istruzioni sorgente, 1–300 secondi. La notifica e stop annullano i passaggi rimanenti.\nLe coordinate usano per impostazione predefinita l’app in primo piano e richiedono la stessa dimensione/orientamento dello schermo. Gesti manuali: fino a 3 secondi; tocchi registrati: fino a 30 secondi.\nUsa start da un gesto del pannello; usa run in una macro shell o in un comando z2-when.\nSalvate in /root/.z2term/actions/NAME.actions (home condivisa). z2-macro actions è un alias.",
            "action.controlHelp" to "version=2 aggiunge repeat N|forever ... end, if CONDIZIONE ... else ... end e call NOME.\nLe condizioni usano le chiavi di z2-state o foreground=PACCHETTO; la virgola significa E, ! nega un termine.\nUno stato di condizione non disponibile fa fallire l’esecuzione. Le chiamate vengono convalidate e congelate prima dell’esecuzione.\n64 istruzioni sorgente per definizione; 10000 istruzioni eseguite per esecuzione. Valgono i timeout della radice e dei figli.",
            "action.uiHelp" to "version=2 supporta anche click SELETTORE, long-click SELETTORE e wait-ui MS SELETTORE.\nSelettori: text=Continua, desc=Altre opzioni, id=org.example.app:id/next. Corrispondenza esatta; niente virgolette attorno agli spazi.\nImposta prima target o launch. wait-ui cerca ogni 250ms per 1–30000ms; più corrispondenze danno errore.\nLe azioni vengono inviate una volta; usa wait-ui per la schermata successiva. I campi di inserimento/password sono esclusi.\ninspect PACCHETTO restituisce in JSON i testi visibili, le descrizioni e gli ID dell’app di destinazione a fuoco.",
            "adb.mNoPm" to "z2adb: nessun gestore di pacchetti supportato (apk/apt-get/pacman) trovato.",
            "adb.mInstalling" to "z2adb: installazione del client adb (${d}ADB_PKG) tramite ${d}PM ...",
            "adb.mInstallFail" to "z2adb: installazione di adb non riuscita. Installalo a mano, poi riprova.",
            "adb.mHaveAdb" to "z2adb: adb è già installato.",
            "adb.mNeedWireless" to "z2adb: se l’accoppiamento non riesce, attiva Impostazioni > Opzioni sviluppatore > Debug wireless (Android 11+).",
            "adb.mPairPort" to "Porta di accoppiamento (Accoppia dispositivo con codice di accoppiamento): ",
            "adb.mConnPort" to "Porta di connessione (mostrata sotto Debug wireless): ",
            "adb.usageText" to """
        z2adb - si connette all’adb di questo dispositivo tramite Debug wireless (self-adb, senza PC).

        Requisito: attiva Impostazioni > Opzioni sviluppatore > Debug wireless (Android 11+).

          z2adb setup                installa il client adb (apk/apt-get/pacman, rilevato in automatico)
          z2adb pair  <port> [code]  accoppia (usa la porta di accoppiamento + il codice a 6 cifre delle Impostazioni)
          z2adb connect <port>       si connette (usa la porta sotto "Debug wireless")
          z2adb status               mostra "adb devices -l"
          z2adb <qualsiasi altro>    passato direttamente ad adb (shell, logcat, install, ...)

        L’host predefinito è 127.0.0.1; passa host:porta per cambiarlo (o imposta Z2ADB_HOST).
        Esempio:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Riproduce su Android l’audio di un comando Linux locale, senza aprire una GUI.\ninstall: installa PulseAudio nel sistema Linux attuale (scarica pacchetti).\nrun COMANDO [ARG...]: collega l’audio, esegue il comando, poi ferma il suo server audio.\nAvvia una shell per attivare l’audio per più comandi. Nessun file di configurazione viene sovrascritto.\nRichiede una scheda del terminale locale e un lettore compatibile con PulseAudio. SSH non è supportato.",
            "audio.missing" to "Mancano gli strumenti audio. Esegui: z2-audio install",
            "audio.failed" to "La connessione audio non è riuscita; il comando non è stato avviato.",
            "doctor.head" to "== z2doctor (questo dispositivo) ==",
            "doctor.secApp" to "-- app --",
            "doctor.secLinux" to "-- lato Linux --",
            "doctor.secPerm" to "-- autorizzazioni --",
            "doctor.secAuto" to "-- automazione --",
            "doctor.secReport" to "-- copia questo per chiedere aiuto --",
            "doctor.lVersion" to "versione",
            "doctor.lEngine" to "motore",
            "doctor.lDistro" to "distribuzione",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "spazio libero",
            "doctor.lHome" to "home",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "notifiche",
            "doctor.lNotifyRead" to "accesso alle notifiche",
            "doctor.lBattOpt" to "ottimizzazione della batteria",
            "doctor.lStorage" to "archivio (tutti i file)",
            "doctor.lSms" to "ricezione SMS",
            "doctor.lCapture" to "rilevamento eventi",
            "doctor.lServers" to "server residenti",
            "doctor.lRules" to "regole di automazione",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Impostazioni Android > App > Z2Term > Notifiche: consenti",
            "doctor.fixNotifyRead" to "-> Impostazioni > Autorizzazioni e notifiche > Rilevamento notifiche",
            "doctor.fixBattOpt" to "-> Impostazioni > Protezione dei processi in background > escludi dall’ottimizzazione della batteria",
            "doctor.fixStorage" to "-> Impostazioni > consenti l’accesso a tutti i file (necessario per vedere /sdcard)",
            "doctor.fixSms" to "-> Impostazioni > Rilevamento SMS (solo per gli attivatori sms:)",
            "doctor.fixCapture" to "-> Impostazioni > Rilevamento degli eventi di sistema (necessario per gli attivatori charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> l’automazione è in pausa. \"z2-when resume\" o la scheda Automazione",
            "doctor.fixDisk" to "-> meno di 500 MB liberi. Elimina i dati di sistema non usati nelle Impostazioni",
            "doctor.fixSdcard" to "-> /sdcard qui è vuota; è l’autorizzazione all’archivio qui sopra a renderla visibile",
            "doctor.noteRedacted" to "(SSID / IP / nomi host sono omessi di proposito)",
            "doctor.allGood" to "Nessun problema trovato.",
            "doctor.someBad" to "problemi trovati:",
            "doctor.usage" to "uso: z2doctor [--share | --clip]   (--share passa il rapporto al menu di condivisione di Android)",
            "doctor.pausedYes" to "in pausa",
            "doctor.secExits" to "-- come è finita l’ultima volta --",
            "doctor.lExitsNote" to "uscite anomale recenti (prima le più recenti):",
            "doctor.lExitsNone" to "uscite anomale: nessuna registrata",
            "doctor.lExitsFile" to "(cronologia completa: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> terminato per mancanza di memoria. Esegui meno lavori pesanti insieme, o riduci server residenti / schede",
            "doctor.title" to "autodiagnosi per \"non funziona\"",
            "doctor.seeAlso" to "Vedi anche: z2scan self (controllo di sicurezza, un altro strumento)",
            "help.header" to "Comandi propri di Z2Term",
            "help.list" to """
        |[Versione / informazioni]
        |  z2version [--short]            Versione dell’app, motore, sistema (distribuzione), kernel
|  z2doctor [--share|--clip]      Perché non funziona? Autodiagnosi + un rapporto da incollare
        |
        |[Funzioni del telefono]
        |  z2-notify [-h] [-b LABEL]...   Notifica (-h: banner, -b: pulsante di risposta)
        |  z2-ask "domanda"               Chiede tramite un campo di risposta della notifica; stampa la risposta
        |  z2-listen [-w WORD,...]        Voce in testo, solo sul dispositivo (z2-listen -h)
        |  z2-toast "messaggio"           Toast (breve messaggio sullo schermo)
        |  z2-share "testo"               Passa un testo al menu di condivisione di Android
        |  z2-open <url|path>             Apre un URL/file nell’app predefinita
        |  z2-view <file.html>            Legge nell’app una pagina creata qui
        |  z2-edit <file>                 Modifica un file di testo nell’editor dell’app
        |  z2-qr                          Scansiona un codice QR con la fotocamera (un pulsante lo apre)
        |  z2-img <file>                  Disegna un’immagine nel terminale (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Legge / imposta gli appunti (set: stdin senza argomento)
        |  z2-file pick | save FILE     Selettore / esportazione di documenti Android (z2-file --help)
        |  z2-battery                     Livello della batteria / stato di carica (JSON)
        |  z2-vibrate [ms]                Vibra (200ms predefiniti)
        |  z2-say "testo"                 Parla tramite il TTS del dispositivo (stdin senza argomento)
        |  z2-torch [on|off|toggle|status]       Torcia (toggle predefinito)
        |  z2-media [play|pause|next]     Tasti multimediali (anche previous/stop)
        |  z2-volume up|down|N|N%         Volume multimediale (stampa attuale/max)
        |  z2-audio run COMMAND ...      Esegue un comando Linux con l’audio di Android
        |  z2-sensor [light|accel|prox]   Legge un valore di un sensore (JSON)
        |  z2-intent -a ACT -d URI ...    Avvia qualsiasi Intent Android (vedi MACRO-GUIDE)
        |  z2-state [key]                 Stato attuale in JSON (o una chiave grezza)
        |  z2-screen keepon 1h | off      Impedisce allo schermo di spegnersi da solo, per un po’
        |  z2-tile set 1 backup.sh        Mette una macro/comando su un riquadro delle impostazioni rapide (12)
        |  z2-icon edit 1 | edit notify   Disegna tu l’icona del riquadro / della barra (24/48/64 punti)
        |  z2-alarm at|daily HH:MM [name] Attivatore orario -> events.jsonl (anche list/cancel)
        |  z2-when <trigger> run <cmd>    Esegue da solo su carica/batteria/orario/eventi (z2-when: uso)
        |  z2-macro list|install <name>   Esempi di automazione inclusi (vedi MACRO-GUIDE)
        |  z2-session list|new|send|...   Controlla le schede di questa app (z2-session: uso)
        |  z2-usb list | allow [device]   Usa un dispositivo USB da Linux (Android chiede il consenso)
        |  z2-update [--check]            Aggiorna z2term da GitHub Releases (approvi tu)
        |
        |[App grafiche (GUI)]
        |  z2gui start [WxH] | stop | status   Desktop Linux (es. z2gui start 1280x720)
        |  z2run <gui-app>                Avvia un’app GUI (apre anche la scheda GUI)
        |
        |[Connessione]
        |  z2adb pair/connect/shell ...   adb verso questo stesso telefono, senza PC  (z2adb help)
        |  sshd [-p N]                    Server SSH (predefinito: solo 127.0.0.1, chiave)
        |
        |[Sicurezza]
        |  z2scan self [--save]           Autodiagnosi di questo dispositivo/localhost (z2scan help)
        |  z2scan diff                    Solo ciò che è cambiato dal riferimento (uscita 1 = nuovo)
        |  z2scan net|host|cve            nmap/lynis/trivy su localhost (il remoto richiede un consenso esplicito)
        |
        |[Aiuto]
        |  z2help | z2term                Questo elenco
        |
        |Ogni comando qui sopra si spiega da solo: aggiungi --help (es. "z2-tile --help").
        |Altro: "z2adb help", esegui "z2gui" senza argomenti, o la sezione 11 dell’HANDBOOK.
    """,
            "help.edge" to "[Pannelli mobili]\nz2-edge --help   Definire i pannelli e aggiornarne il contenuto\nz2-key --help    Azioni globali di Android\nz2-shot --help   Catturare solo la parte cerchiata\nz2-app --help    Elencare le applicazioni ed esportare le icone\nz2-action --help Macro nominate di azioni Android\nz2-ui --help     Controllo delle app tramite IA (l’aiuto è una guida per agenti IA)",
            "macro.rssBody.head" to """
#
# Preparazione:
#   1) Un URL di feed per riga in:  ~/.z2term/rss/feeds.txt
#   2) Controlla ogni 30 minuti:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Facoltativo - un feed o una parola per riga che non deve finire sommerso:  ~/.z2term/rss/important.txt
#      Ciò che corrisponde ha la sua notifica, così un feed affollato non può scalzarlo.
#      Scrivi una parte dell’URL o una parte del titolo (es. example.org).
#   4) Registra le azioni di notifica in Automazione:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Apri manda l’articolo al browser; Elenco apre tutto ciò che è stato raccolto, da leggere in z2term.
#      Nota: aprire una schermata da un pulsante di notifica richiede l’autorizzazione "mostra sopra altre app".
#   5) Leggi l’elenco raccolto: sh ~/.z2term/macros/rss.sh view
#      Gestisci entrambe le regole in Elenco dei comandi -> Automazione -> Regole di automazione.
#      Modifica una regola di notifica esistente invece di aggiungerne un’altra.
#   6) Notifiche: per impostazione predefinita il controllo programmato notifica e quello dall’elenco no.
#        sh ~/.z2term/macros/rss.sh notify off        # controllo programmato: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Aggiorna / Articoli nell’elenco: on / off
#      Entrambi sono anche interruttori in Impostazioni, nell’elenco, da cambiare quando vuoi.
#
# Richiede: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Le miniature (Impostazioni nell’elenco) richiedono anche Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Batteria: più spesso controlli, più costa. Non scendere sotto i 30 minuti.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "numero massimo di righe conservate in seen/latest",
            "macro.cNoPy" to "serve python3 (es. apk add python3)",
            "macro.cWriteFeeds" to "Scrivi un URL di feed per riga in:",
            "macro.cFeedsHint" to "# Un URL di feed per riga (le righe che iniziano con # vengono ignorate)",
            "macro.cSkipFail" to "salta in silenzio un feed che non si scarica o non si analizza (gli altri continuano)",
            "macro.cDiff" to "# Togliere ciò che è già stato visto per tenere solo il nuovo (stesso trucco del confronto con il riferimento di z2scan).\n# Le date e l’ordine dei feed non sono affidabili; nessuno dei due è sicuro.",
            "macro.cPrepend" to "# Impilare prima il più recente così che la modalità \"inizio (head)\" del widget legga giusto.",
            "macro.cNotify" to "%s nuovi",
            "macro.cOpen" to "Apri",
            "macro.cListNone" to "Ancora niente di raccolto — eseguilo prima una volta senza argomenti.",
            "macro.cListHint" to "Elencali con: rss.sh list [numero]",
            "macro.cList" to "Elenco",
            "macro.cPageTitle" to "Feed",
            "macro.cPageSites" to "%s siti",
            "macro.cPageArticles" to "%s articoli",
            "macro.cPageDoc" to "# Costruisce la pagina di lettura (z2-view la mostra nell’app; niente server, niente browser).\n" +
            "# Raggruppata per sito, prima la più recente, con un pulsante Apri per articolo. Il tema del telefono\n" +
            "# arriva come variabili CSS, così la pagina segue i colori del terminale.",
            "macro.cViewHint" to "Leggili con: rss.sh view",
            "macro.cHitMax" to "numero massimo di notifiche per articolo in un’esecuzione (il resto va nel riepilogo)",
            "macro.cSplitDoc" to "# Separare ciò che non va perso. Il corpo del riepilogo porta solo 3 righe, quindi un feed affollato\n" +
            "# che si aggiorna nello stesso momento scalza ciò che contava. Le corrispondenze hanno **la loro\n" +
            "# notifica** (l’app dà un id distinto per notifica, quindi nulla viene sostituito).",
            "macro.cNameDoc" to "# Mettere l’URL nel nome della notifica: premere il pulsante lo restituisce come {name} in\n" +
            "# notify_action, così si apre l’articolo giusto anche con più notifiche sullo schermo (preparazione 4).",
            "macro.cListDoc" to "# Mostrare ciò che è stato raccolto come elenco leggibile (senza colori — deve somigliare al file/widget).\n" +
            "# latest.txt resta dati grezzi, un articolo per riga (il tail del widget e rss-open.sh lo leggono).",
            "macro.cSettings" to "Impostazioni",
            "macro.cArchive" to "Archivio",
            "macro.cDelete" to "Elimina",
            "macro.cThumbs" to "Miniature",
            "macro.cSave" to "Conserva gli articoli vecchi",
            "macro.cNotifyPoll" to "Segnala i nuovi articoli (controllo programmato)",
            "macro.cNotifyView" to "Segnala i nuovi articoli (controlli dall’elenco)",
            "macro.cDeleteSite" to "Elimina tutti gli articoli di questo sito",
            "macro.cConfirmSite" to "Togliere dall’elenco %s articoli di %h?",
            "macro.cArticles" to "Articoli",
            "macro.cPrev" to "Precedente",
            "macro.cNext" to "Successivo",
            "macro.cCount" to "%s articoli",
            "macro.cUndated" to "Senza data",
            "macro.cArchiveEmpty" to "Ancora niente di conservato. Attiva Conserva gli articoli vecchi in Impostazioni, e gli articoli raccolti resteranno qui.",
            "macro.cSearch" to "Cerca",
            "macro.cSearchWord" to "Parole (separale con spazi per richiederle tutte)",
            "macro.cByMonth" to "Per mese",
            "macro.cNoHit" to "Niente trovato.",
            "macro.cDropMonth" to "Elimina questo mese",
            "macro.cDropConfirm" to "Eliminare i %s articoli conservati di %m? Non si può annullare.",
            "macro.cOnOff" to "Indica on oppure off",
            "macro.cNoPillow" to "Le miniature richiedono Pillow per Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Preparazione: installa prima la parte che raccoglie
#   z2-macro install rss
# Widget: assegna "rss-open" a un pulsante nelle impostazioni del widget di stato.
#
# z2-run: assegna "rss-open" a un pulsante del widget di stato (per provarlo qui: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Ancora nessun articolo",
            "macro.cAllRead" to "Niente di nuovo da aprire",
            "macro.cPick" to "# Le righe di latest.txt sono \"titolo  URL\". Prendere il primo URL non ancora aperto.",
            "macro.cCap" to "limitare l’elenco degli aperti perché non cresca all’infinito",
            "macro.copied" to "Codice copiato: ${d}{code}",
            "macro.cleared" to "Il codice copiato è stato cancellato",
            "macro.otpTitle" to "Codice monouso",
            "macro.cTtl" to "secondi prima di cancellare la copia (solo quando entra direttamente)",
            "macro.cCode" to "# L’app ha già estratto il codice. Non fare nulla se non c’è riuscita.",
            "macro.cTry" to "# Provare a metterlo direttamente. Entra solo mentre **stai guardando l’app**:\n" +
            "# Android 10+ lascia scrivere negli appunti solo all’app in primo piano, quindi un’esecuzione\n" +
            "# in background viene ignorata in silenzio. Rileggere per vedere se è entrato.",
            "macro.cFallback" to "  # Non entrato = non stai guardando l’app. Passarlo invece tramite il pulsante\n" +
            "  # \"Copia\" della notifica (premerlo porta l’app in primo piano).\n" +
            "  # ⚠ Ciò che entra così non viene cancellato dal TTL qui sotto: anche cancellare richiede il primo piano.",
            "macro.cClear" to "# Dopo il TTL, svuotare gli appunti solo se contengono ancora il codice copiato\n" +
            "# (ciò che è stato copiato dopo non viene toccato).",
            "macro.cHow" to """
# ■ Copiare i numeri di telefono dalle notifiche di chiamata
#   Cerca nel titolo e poi nel testo un numero di telefono da solo e offre un pulsante Copia.
#   Sono inclusi chiamanti registrati e non; i contatti non vengono interrogati.
#   Un nome da solo non basta a trovare un numero. Si usa solo l’accesso alle notifiche.
#   Il nome di file unknown-call.sh resta così perché le regole esistenti continuino a funzionare.
""",
            "macro.cIsNum" to """
# È un numero da solo? Togliere ogni carattere che un numero può contenere (cifre + - ( ) spazio);
# se **non resta nulla** e ci sono da 7 a 15 cifre, trattarlo come numero.
#   -> Un campo con un nome o altre lettere non è un numero da solo; controllare l’altro campo.
#   -> "Sconosciuto"/"Numero privato" finiscono anche qui (allenta il test qui sotto se li vuoi).
# ⚠ Non scriverlo come case [!...]: la ) nel motivo viene letta come separatore del case.
""",
            "macro.cScan" to "# Il chiamante di solito è nel titolo, ma alcune app telefono lo mettono nel testo. Controllare entrambi.",
            "macro.cSkip" to "# Nessun numero da solo in nessuno dei due campi: niente da copiare.",
            "macro.cWhat" to "# La categoria dice quale è scattata (stesso valore del notify:category= registrato).",
            "macro.cCopy" to """
# Passare il numero tramite il pulsante "Copia" della notifica (-c). Chiamare qui z2-clip set
# non entrerebbe: Android 10+ lascia scrivere negli appunti solo all’app in primo piano, e durante una
# chiamata è l’app telefono. Premere il pulsante porta z2term in primo piano per quell’istante.
""",
            "macro.missed" to "Chiamata persa",
            "macro.incoming" to "Chiamata in arrivo",
            "macro.title" to "Copia il numero di telefono",
            "macro.stNew" to "nuovo",
            "macro.stSame" to "uguale",
            "macro.stDiff" to "diverso",
            "macro.msgInstalled" to "installato:",
            "macro.msgSame" to "lo stesso è già installato:",
            "macro.msgOutdated" to "il tuo è diverso da quello incluso:",
            "macro.msgHowUpdate" to "guarda prima: z2-macro diff %s   /   sostituisci con quello incluso: z2-macro install -f %s (anche le tue modifiche se ne vanno)",
            "macro.msgNotInstalled" to "non ancora installato (esegui prima z2-macro install):",
            "macro.msgRemoved" to "rimosso:",
            "macro.msgAbsent" to "non installato:",
            "macro.msgRemoveDiffers" to "il tuo è diverso da quello incluso, quindi non è stato rimosso:",
            "macro.msgHowRemove" to "guarda prima: z2-macro diff %s   /   rimuovilo comunque: z2-macro remove -f %s (anche le tue modifiche se ne vanno)",
            "macro.msgNoDiffTool" to "qui non c’è diff. Puoi comunque leggere quello incluso con z2-macro show.",
            "macro.msgSameAsBundled" to "identico a quello incluso.",
            "macro.msgNotFound" to "esempio non trovato:",
            "macro.msgHintResident" to "Per tenerlo attivo, registra questo in Impostazioni -> Server residenti:",
            "macro.msgHintRun" to "Come eseguirlo:",
            "macro.remindBody.head" to """
#
# Preparazione (una volta):
#   sh ~/.z2term/macros/remind.sh setup   registra i due agganci e i due riquadri
#
# Sintassi:
#   remind.sh <quando> <testo>       aggiunge un promemoria
#   remind.sh list                   li elenca (il numero viene prima)
#   remind.sh del <n|all>            annulla (funziona anche rm)
#   remind.sh peek                   mostra l’elenco in una notifica (con un pulsante Elimina)
#   remind.sh ask                    chiede in una casella di risposta della notifica (per il riquadro)
#   remind.sh setup                  registra agganci e riquadri (una volta)
#   remind.sh help                   questo testo
#
# <quando> - una volta:
#   30m / 90s / 2h                   da adesso
#   18:30                            le prossime 18:30 (domani se sono passate)
#   sat 21:00                       il prossimo giorno della settimana corrispondente, una volta (conta anche più tardi oggi)
#   Giapponese: 土曜日の夜九時 = sat 21:00 ; 朝七時 = 07:00 ; 夜九時半 = 21:30
#   Giapponese: 夜12時 è ambiguo; per la mezzanotte usa 00:00 con il giorno esplicito.
#   tomorrow 18:30 / 3d 07:00        per giorno (3d = fra 3 giorni)
#   07/30 19:00                      mese/giorno (l’anno prossimo se è passato)
#   2030 07/30 19:00                 con un anno
#   203007301900 / 07301900          solo cifre (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ senza l’ora, mantiene **l’ora attuale del giorno** (3d / 07/30 ...)
#
# <quando> - ripetuto:
#   daily 07:00 / weekday 09:00      ogni giorno / lun-ven
#   weekly mon 09:00                 quel giorno della settimana
#   monthly 15 09:00                 quel giorno del mese
#   yearly 07/30 19:00               quel mese e giorno
#   every 19:00 / every wed 19:00    "every" da solo funziona (decide la parola successiva)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Esempi:
#   remind.sh 30m prendere le medicine
#   remind.sh tomorrow 18:30 portare fuori la spazzatura
#   remind.sh monthly 25 10:00 affitto
#   remind.sh list        ->  1  ⏰ 07/30 18:30  portare fuori la spazzatura
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (poi semplicemente: remind.sh 30m ...)
""",
            "macro.cStore" to """# Un promemoria = una riga in ${d}DIR/<id>.txt, separata da tabulazioni: tipo / etichetta / id-quando / testo.
#   tipo: once (in arrivo) / fired (già scattato) / repeat (ricorrente)
#   id-quando: solo per repeat (così del può chiamare z2-when remove). "-" altrimenti.
# Il testo viene per ultimo così può contenere qualsiasi cosa tranne una TAB.""",
            "macro.cParse" to """# Leggere da 1 a 3 parole e decidere:
#   KIND ... once|repeat   PLAN ... etichetta da mostrare   SPEC ... ciò che accetta z2-alarm/z2-when   USED ... parole usate""",
            "macro.pDaily" to "ogni giorno ",
            "macro.pWeekday" to "giorni feriali ",
            "macro.pWeekly" to "ogni settimana ",
            "macro.pMonthly" to "ogni mese il ",
            "macro.pYearly" to "ogni anno ",
            "macro.mDay" to "",
            "macro.eDaily" to "giornaliero",
            "macro.eWeekly" to "settimanale",
            "macro.eMonthly" to "mensile",
            "macro.eYearly" to "annuale",
            "macro.mBadDate" to "questa data non esiste (es. 07/30):",
            "macro.cEvery" to """  # La forma breve "every ...". **La forma della parola successiva** sceglie daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Il numero di parole non cambia (una parola sostituita da un’altra), quindi USED resta giusto.""",
            "macro.cExpand" to "# Decidere quale ricorrenza intendeva \"every\", dalla parola che lo segue.",
            "macro.cDigits" to """    # Forme di sole cifre: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Controllare 12 cifre prima di 8 (il motivo a 8 cifre corrisponde anche a una stringa di 12).""",
            "macro.cYmd" to """  # Scritto come data. ⚠ z2-alarm non accetta una data, quindi diventa anch’essa un ritardo.""",
            "macro.cCivil" to """# Data civile -> giorni dal 1970-01-01 (anni bisestili compresi; days_from_civil di Howard Hinnant).
# ⚠ date -d "2026-07-30" non è affidabile con busybox, quindi il calcolo si fa qui.
# ⚠ Si usa solo la differenza di giorni e l’ora è lasciata a [day_epoch], che annulla il fuso orario.""",
            "macro.cCron" to """# Costruire un’espressione cron (min ora giorno-del-mese mese giorno-della-settimana) da HH:MM e un campo giorno.
# NON usare expr per togliere uno zero iniziale: esce con 1 quando il risultato è 0, quindi il lato destro
# di `expr "${d}m" + 0 || echo "${d}m"` viene eseguito anche lui e il valore diventa di due righe.""",
            "macro.afterLabel" to """# "30m" -> "fra 30m (14:35)". Mostrare l’ora permette di controllare subito.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "fra ${d}num${d}u (${d}at)" || echo "fra ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "uso: remind.sh <quando> <testo>",
            "macro.mBadWhen" to "impossibile leggere l’orario (prova: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "scrivi l’ora nel formato HH:MM:",
            "macro.mBadRange" to "ora fuori intervallo (00:00-23:59):",
            "macro.mNoTime" to "nessuna ora indicata (es. daily 07:00):",
            "macro.mBadDow" to "giorno della settimana sconosciuto:",
            "macro.mPastTime" to "quell’ora è già passata:",
            "macro.cDays" to """    # Forma per giorni. Senza ora, mantenere **l’ora attuale del giorno** (mai inventare un valore predefinito).
    # Può anche arrivare come parola unica, quindi è accettata anche la forma attaccata.""",
            "macro.cDayEpoch" to """# Trasformare "HH:MM, fra N giorni" in secondi epoch. ⚠ busybox non ha date -d "tomorrow",
# quindi si calcola prima la mezzanotte di oggi e poi si aggiungono i giorni e l’ora.
# ⚠ Un valore con zero iniziale ("08") viene letto in ottale da ${d}(()), quindi lo zero viene tolto.
# ⚠ Un giorno si aggiunge come 86400s, quindi può spostarsi di un’ora nel giorno del cambio d’ora.""",
            "macro.cFmtAt" to """# L’etichetta mostrata nell’elenco. ⚠ Tenere "tomorrow" diventerebbe sbagliato una volta passata la data.""",
            "macro.mNoBody" to "di’ di cosa ricordarti",
            "macro.mNoAlarm" to "impossibile programmarlo",
            "macro.mNone" to "niente di programmato",
            "macro.mFired" to " (scattato)",
            "macro.mRemoved" to "rimosso:",
            "macro.mNoSuchNum" to "voce non trovata (controlla remind.sh list):",
            "macro.mUsageDel" to "uso: remind.sh del <n|all>",
            "macro.bDone" to "Fatto",
            "macro.bDelete" to "Elimina",
            "macro.mAskDel" to "Quale togliere?",
            "macro.mAskDelH" to "numero / all",
            "macro.mDeletedTitle" to "🗑 Rimosso",
            "macro.cPeek" to """# Mostrare l’elenco in una notifica (dal riquadro "elenco"). **Tenere i numeri** — è con quelli
# che si indica quale togliere. ⚠ Elencare senza modo di togliere era la lamentela; da qui il pulsante.""",
            "macro.cAskDel" to """# Il pulsante "elimina": chiedere un numero e passarlo a [cmd_del] (lo stesso percorso della CLI).""",
            "macro.bS1" to "+10min",
            "macro.bS2" to "+1h",
            "macro.mAgain" to "- di nuovo fra",
            "macro.mTitle" to "⏰ Promemoria",
            "macro.mAsk1" to "Di cosa ricordarti?",
            "macro.mAsk1H" to "es. prendere le medicine",
            "macro.mAsk2" to "Quando?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "inseriscilo di nuovo, per favore",
            "macro.mAskGiveUp" to "Impossibile leggerlo ${ASK_TRIES} volte. Puoi anche aggiungerlo dal terminale: remind.sh 30m prendere le medicine",
            "macro.mOkTitle" to "⏰ Promemoria programmato",
            "macro.mNgTitle" to "⚠ Non programmato",
            "macro.cHooks" to """  # Un aggancio per "è scattato un promemoria", uno per "è stato toccato un pulsante di notifica". Solo
  # questi due, qualunque sia il numero di promemoria. Nessuno dipende dagli interruttori di rilevamento.""",
            "macro.cTiles" to """  # Riempire solo gli slot vuoti e quelli già nostri (mai sovrascrivere quelli altrui).
  # Un nome di macro con argomenti funziona da 0.8.275 (prima, scrivere sh + percorso completo).""",
            "macro.mSetupHooks" to "agganci registrati:",
            "macro.mSetupTiles" to "riquadri:",
            "macro.mPathHint" to "Nota: questa scheda non può ancora eseguire remind.sh per nome. Apri una nuova scheda, o esegui " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH qui.",
            "macro.cPathHint" to """  # ⚠ La cartella delle macro è nel PATH solo da 0.8.287, quindi **le schede aperte prima**
  # hanno ancora quello vecchio. Controllare se il nome viene trovato e dire cosa fare se no
  # (altrimenti `command not found` non ha una ragione visibile).""",
            "macro.mPlace" to "Nota: devi ancora posizionare tu i riquadri, dalla schermata di modifica (matita) delle impostazioni rapide.",
            "macro.lRemind" to "ricorda",
            "macro.lList" to "elenco",
            "macro.cSelf" to "# Reagire solo alle nostre notifiche (i nostri id iniziano sempre con r)",
            "macro.cKeepRepeat" to "      # Non eliminare qui un promemoria ripetuto: deve scattare di nuovo domani.",
            "macro.cAsk" to """# Il percorso usato dal riquadro / da una notifica. ⚠ **Rispondere sempre con una notifica**:
# qui nessuno guarda un terminale, quindi fallire su stderr si legge come "ho premuto e
# non è successo niente" (invece sì — la ragione è finita solo nel run.log del riquadro).
#   Illeggibile  -> dire perché e richiedere, fino a $ASK_TRIES volte (la risposta precedente viene mantenuta)
#   Programmato  -> mostrare il piano e il testo in una notifica""",
            "macro.cSnoozeCancel" to "lasciar cadere l’allarme di rinvio se il promemoria è stato rinviato prima di essere fatto",
            "macro.qrBody.head" to """
#
# Uso:
#   qr.sh "https://example.com"       codifica una stringa e la disegna qui
#   qr.sh -f notes.txt                codifica il contenuto di un file
#   z2-clip get | qr.sh               codifica ciò che c’è negli appunti
#   qr.sh -o ~/qr.png "text"          salva un PNG (non viene disegnato nulla)
#   qr.sh -t "text"                   mostra blocchi invece di un’immagine
#   qr.sh -s 24 "text"                sceglie la dimensione (in colonne; di base sta nella larghezza)
#
# z2-run: qr.sh "https://example.com"   (una volta installato basta il nome. Si esegue una volta)
#
# # Prerequisiti (senza di essi non funziona, o ha un aspetto diverso)
#
#   1) La codifica la fa l’app ... in una scheda di z2term non c’è nulla
#      da installare (codifica z2-qr). Se qrencode c’è già, viene usato al suo posto.
#      Dove l’app non è raggiungibile (via ssh), installalo una volta per scheda (distribuzione):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Senza nessuno dei due, viene mostrato il comando di installazione e lo script si ferma.
#
#   2) L’immagine (predefinita) compare solo in una scheda di questa app ... è disegnata con
#      la grafica Kitty. Via ssh, o in un terminale che non mostra immagini, ottieni
#      invece un flusso di caratteri senza senso. Lì usa -t.
#
#   3) -t (blocchi) dipende dal carattere del terminale ... i caratteri che lasciano spazi tra
#      le righe restano leggibili all’occhio ma non a una fotocamera. Per scansionarlo con una fotocamera,
#      preferisci l’immagine predefinita o il PNG di -o.
#
#   4) Le proporzioni sono presunte ... vedi "Proporzioni" qui sotto. Schiacciato? Z2_QR_ASPECT.
#
# Opzioni:
#   -f FILE   legge l’input da un file (altrimenti gli argomenti, altrimenti stdin)
#   -o PNG    salva un PNG invece di disegnare. Più codici diventano
#             qr-1.png / qr-2.png ...
#   -t        mostra blocchi. Per terminali senza immagini, e via ssh
#   -s N      dimensione in colonne quando disegna. Di base fino a 34, limitata dalla larghezza
#   -h        questo aiuto
#
# Sugli input lunghi:
#   Un codice contiene una quantità fissa (al massimo 2953 byte alfanumerici; qualche centinaio
#   se deve restare leggibile). Un input di oltre 900 byte viene diviso agli a capo
#   in più codici, numerati [1/3] e mostrati in ordine.
#   AVVISO una singola riga troppo lunga per stare in un codice non può essere codificata affatto.
#
# Proporzioni:
#   I terminali non dicono la dimensione delle loro celle, quindi si presume una proporzione dei caratteri di 1:2
#   per rendere il codice quadrato. Se sembra schiacciato, regola con una variabile d’ambiente:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (più piccolo = più alto. 0.5 predefinito)
""",
            "macro.cMaxBytes" to "byte massimi per codice (diviso oltre)",
            "macro.cPngModule" to "punti per modulo in un PNG salvato",
            "macro.cTargetPx" to "larghezza obiettivo in pixel quando disegna",
            "macro.cMargin" to "zona di silenzio attorno al codice (moduli; necessaria per la scansione)",
            "macro.cUsageFn" to "Mostrare come aiuto il blocco di commenti iniziale (senza numero fisso di righe).\n" +
            "# Le righe vuote non lo fermano (decide NF), quindi le sezioni separate da righe vuote restano intere.",
            "macro.cNeedEncoder" to "Prerequisito 1: qualcosa per codificare. qrencode se c’è, altrimenti l’app.",
            "macro.mMissing" to "qr.sh: niente per codificare qui - niente qrencode, e l’app non è raggiungibile",
            "macro.mInstallOnce" to "  Installalo una volta in questa scheda:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Creare il PNG. qrencode sceglie la dimensione del modulo per tentativi; l’app accetta un obiettivo.",
            "macro.cMakeBlocks" to "Disegnare con caratteri a blocchi. Entrambi i percorsi emettono i propri colori, quindi va bene qualsiasi tema.",
            "macro.qrBody.mNoTmp" to "impossibile creare una cartella di lavoro",
            "macro.qrBody.mUnreadable" to "illeggibile:",
            "macro.qrBody.mEmpty" to "input vuoto",
            "macro.mSizeNum" to "-s richiede un numero",
            "macro.qrBody.mUsageHint" to "vedi qr.sh -h",
            "macro.mPieceFail" to "qr.sh: impossibile costruire il codice %d: %s",
            "macro.qrBody.cCollect" to "---- raccogliere l’input ----",
            "macro.cSplit" to "---- dividere agli a capo se troppo lungo ----",
            "macro.cSplit2" to "Passare al pezzo successivo appena prima che il totale corrente superi MAX_BYTES. Le righe non vengono\n# mai tagliate a metà, quindi un testo multibyte sopravvive.",
            "macro.cTrim" to "Togliere l’a capo finale (mai codificare un a capo che l’input non aveva).",
            "macro.cSize" to "---- scegliere la dimensione di visualizzazione dalla larghezza del terminale ----",
            "macro.cPngWidth" to "Restituire la larghezza in pixel di un PNG (byte 16-19, l’IHDR).",
            "macro.cModulePx" to "Ricavare i punti per modulo dalla larghezza obiettivo in pixel.",
            "macro.cInline" to "Disegnare un PNG sul posto con il protocollo grafico Kitty.\n# c/r fissano quante celle occupa, poi stampare altrettanti a capo per mettere il cursore sotto.",
            "macro.cEmit" to "---- emettere ----",
            "macro.cAnsi" to "ANSIUTF8 emette i propri colori, quindi chiaro/scuro è giusto con qualsiasi tema.",
            "macro.mdBody.head" to """
#
# Uso:
#   md.sh README.md                lo mostra qui, in questo terminale
#   md.sh -v README.md             lo apre nell’app (z2-view)
#   cat notes.md | md.sh           legge dall’input standard
#   md.sh -o page.html README.md   scrive l’HTML e si ferma (non si apre nulla)
#
# z2-run: md.sh README.md   (una volta installato basta il nome. Si esegue una volta)
#
# Opzioni:
#   -v        lo apre nell’app (z2-view)
#   -o FILE   ci scrive l’HTML. Qui non viene mostrato nulla
#   -w N      va a capo a N colonne (predefinito: la larghezza dello schermo, fino a 100)
#   -p        senza colori né decorazioni (testo semplice)
#   -t TITLE  titolo della schermata di lettura (predefinito: il primo titolo, altrimenti il nome del file)
#   -h        questo aiuto
#
# Un documento lungo, schermata per schermata: md.sh README.md | less -R   (-R mantiene i colori)
#
# Niente da installare: bastano sh e awk.
# Sono gestiti titoli, paragrafi, elenchi (annidati, numerati, [ ]/[x]), citazioni, codice, tabelle,
# separatori, link, immagini e front matter (---). AVVISO i titoli setext
# (=== / ---) e i link di riferimento ([testo][1]) escono così come sono scritti.
#
# Cosa aspettarsi:
#   - Le immagini compaiono sul posto solo in una scheda di questa app (disegnate con la grafica
#     kitty tramite z2-img). Via ssh ottieni invece la didascalia e il percorso.
#   - I link si possono toccare (OSC 8). Un terminale senza questa funzione li mostra come testo.
#   - Questo terminale non disegna il corsivo, quindi l’*enfasi* è sottolineata (come fa man).
#   - Una tabella troppo larga per lo schermo viene spezzata in righe "titolo: valore".
#   - -v incorpora nella pagina le immagini presenti su questo dispositivo (al massimo 1,5 MB ciascuna).
#     La schermata di lettura non prende nulla dalla rete, quindi le immagini http(s)
#     restano come didascalia.
""",
            "macro.mdBody.cCollect" to "# Raccogliere l’input (più file diventano una sola lettura, nell’ordine dato).",
            "macro.cTitle" to "# Titolo: -t, altrimenti il primo titolo #, altrimenti il nome del file",
            "macro.cWidth" to "# La larghezza dello schermo",
            "macro.cColorEnv" to "# Una via d’uscita quando i colori devono (o non devono) passare per una pipe, es. verso un paginatore",
            "macro.cImgWhere" to "# Un’immagine può comparire sul posto solo in una scheda (grafica kitty) con z2-img presente.",
            "macro.cAmbi" to "# Con quale larghezza contare i caratteri ambigui (linee, frecce, numeri cerchiati). Usa\n" +
            "# Z2_MD_AMBIWIDTH=2 se le Impostazioni li contano a larghezza piena. 1 predefinito, come l’app.",
            "macro.cAwkFile" to "# AVVISO il programma awk viene scritto in un FILE e passato con -f. Passato come\n" +
            "#   argomento, gawk taglia il programma dove un carattere multibyte (come il\n" +
            "#   giapponese di queste note) si trova a cavallo del suo limite di lettura di 4096 byte, e muore con\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Incorporare le immagini nella pagina della schermata di lettura, come URI data:.\n" +
            "# AVVISO z2-view non prende nulla dalla rete (JavaScript e caricamenti disattivati), quindi\n" +
            "#   un’immagine non incorporata semplicemente non compare. 4 MB per l’intera pagina.",
            "macro.cImgLoop" to "# Solo un documento con immagini viene letto riga per riga (per chiamare z2-img sul posto)",
            "macro.mdBody.mUsageHint" to "vedi md.sh -h",
            "macro.mdBody.mNoTmp" to "impossibile creare una cartella temporanea",
            "macro.mdBody.mUnreadable" to "file illeggibile:",
            "macro.mdBody.mEmpty" to "niente da leggere",
            "macro.mWidthNum" to "-w richiede un numero",
            "macro.mNoDir" to "impossibile creare la cartella in cui metterlo",
            "macro.mConvFail" to "la conversione non è riuscita",
            "macro.mNoView" to "qui non c’è z2-view (l’app va aggiornata). L’HTML è in:",
            "macro.mImgLabel" to "[immagine]",
            "menu.noApps" to "(nessuna applicazione trovata)",
            "menu.cat.AudioVideo" to "Audio e video",
            "menu.cat.Development" to "Sviluppo",
            "menu.cat.Graphics" to "Grafica",
            "menu.cat.Game" to "Giochi",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Ufficio",
            "menu.cat.System" to "Sistema",
            "menu.cat.Utility" to "Accessori",
            "menu.cat.Other" to "Altro",
            "menu.usage" to "uso: z2menu [list]   (senza argomenti: XML del menu pipe di openbox)",
            "reminderView.labels" to "Promemoria|Aggiungi|Elimina|Contenuto|Data e ora|Ripetizione|Una volta|Ogni giorno|Giorni feriali|Ogni settimana (giorno scelto)|Testo di programmazione (facoltativo; sostituisce data e ripetizione)|Programmati|Ripetuti|Notificati|Nessun promemoria|Eliminare questo promemoria e le sue notifiche in sospeso?",
            "rssFeeds.rssFeedsLabel" to "Iscrizioni",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} non è partito. Vedi /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: nessun gestore di pacchetti supportato (apk/apt-get/pacman) trovato.",
            "scan.mInstalling" to "z2scan: installazione di",
            "scan.mInstallFail" to "z2scan: installazione non riuscita di",
            "scan.mRemoteBlocked" to "z2scan: destinazione remota rifiutata. Controlla solo localhost, o passa --allow-remote per una destinazione che sei autorizzato a testare.",
            "scan.mRemoteWarn" to "z2scan: AVVISO scansione di un host remoto. Scansiona solo sistemi che sei esplicitamente autorizzato a testare.",
            "scan.mNoLynis" to "z2scan: lynis non trovato; ripiego sull’autodiagnosi integrata (\"z2scan self\"). Installalo con \"z2scan setup\".",
            "scan.mNoCve" to "z2scan: nessuno scanner di CVE (trivy/grype) trovato. Installa trivy o grype, poi riprova.",
            "scan.mSelfHead" to "== autodiagnosi z2scan (questo dispositivo / localhost) ==",
            "scan.mUidRoot" to "gira come uid 0 (nota: proot/z2root fornisce un root finto)",
            "scan.mPubPort" to "porta TCP in ascolto su tutte le interfacce (non solo localhost), porta",
            "scan.mSshdEmpty" to "sshd_config consente l’accesso con password vuota (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config attiva l’autenticazione con password (PasswordAuthentication yes); la chiave è più sicura",
            "scan.mSshdRoot" to "sshd_config consente l’accesso come root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys ha permessi troppo larghi, modo",
            "scan.mSshDir" to "~/.ssh ha permessi troppo larghi, modo",
            "scan.mWorldWrite" to "trovati file scrivibili da tutti (fino a 20 mostrati):",
            "scan.mSuid" to "binari SUID presenti (per informazione; root finto con proot/z2root):",
            "scan.mPath" to "il PATH contiene un elemento vuoto/\".\" (la cartella attuale nel PATH è rischiosa)",
            "scan.mFound" to "riscontri:",
            "scan.mClean" to "nessun problema evidente trovato.",
            "scan.mSaved" to "z2scan: riferimento salvato:",
            "scan.mSaveFail" to "z2scan: impossibile scrivere il riferimento:",
            "scan.mNoBase" to "z2scan: ancora nessun riferimento. Esegui una volta \"z2scan self --save\" per registrare lo stato attuale.",
            "scan.mNoChange" to "[ OK ] nessun cambiamento dal riferimento.",
            "scan.mAdded" to "== nuovo dal riferimento ==",
            "scan.mRemoved" to "== sparito dal riferimento ==",
            "scan.mCleared" to "z2scan: riferimento cancellato.",
            "scan.mLangDiff" to "z2scan: AVVISO il riferimento è stato salvato in un’altra lingua, quindi tutto sembrerà cambiato. Salvalo di nuovo con \"z2scan self --save\".",
            "scan.langTag" to "it",
            "scan.usageText" to """
        z2scan - test di vulnerabilità di questo dispositivo / localhost (difensivo, nessun dato esce).

        Per impostazione predefinita scansiona solo localhost. Gli scanner vengono installati dai pacchetti ufficiali
        della tua distribuzione (nmap/lynis/trivy); nulla è incluso e i risultati restano locali.

          z2scan self [--save]         autodiagnosi integrata (senza strumenti esterni): porte aperte,
                                       configurazione di sshd, permessi delle chiavi SSH, scrivibili da tutti/SUID, PATH.
                                       --save salva anche il risultato come riferimento.
          z2scan diff [--quiet]        riesegue l’autodiagnosi e mostra solo ciò che è cambiato
                                       dal riferimento. Esce con 1 quando c’è qualcosa di nuovo,
                                       quindi si collega direttamente a z2-when. --quiet non stampa
                                       proprio nulla quando niente è cambiato.
          z2scan baseline [clear]      mostra il riferimento salvato (o lo elimina)
          z2scan setup                 installa gli scanner (nmap, lynis) tramite apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       scansione TCP nmap. La destinazione predefinita è 127.0.0.1.
                                       Una destinazione non locale richiede --allow-remote.
          z2scan host                  audit dell’host tramite lynis (ripiego su "self" se assente)
          z2scan cve                   scansione delle CVE note del rootfs tramite trivy/grype se presenti

        Scansiona solo sistemi che sei esplicitamente autorizzato a testare.
    """.trimIndent(),
            "translationMacro.description" to "Traduce un testo con un comando installato a parte.",
            "translationMacro.missing" to "Il comando trans non è installato in questo ambiente locale. Installa tu il tuo strumento di traduzione, poi riprova. Nulla viene scaricato automaticamente.",
            "translationMacro.network" to "Il testo da tradurre viene inviato al servizio esterno usato dal tuo strumento. Valgono le sue condizioni e la disponibilità della rete.",
            "translationSetup.setup" to "Apri un terminale nello stesso ambiente Linux locale, esegui tu il comando qui sotto, poi riprova la traduzione.",
            "translationSetup.alpineRepo" to "Se il pacchetto non viene trovato, attiva il repository community della stessa versione di Alpine in /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Se il pacchetto non viene trovato su Ubuntu, attiva il suo repository universe e riprova.",
            "translationSetup.unknown" to "Distribuzione non riconosciuta. Scegli il comando per il tuo ambiente Linux locale:",
            "voice.head" to """# voice.sh — il ricevitore del "Comando vocale": ciò che dici controlla musica, volume, timer, promemoria, meteo e altro.
#
# Preparazione (una volta):
#   sh ~/.z2term/macros/voice.sh setup    registra 4 regole (Comando vocale / Comando vocale (bloccato) / Timer vocale / Conta passi)
#   Conta passi tiene solo attivo il contapassi per "quanti passi oggi"; non esegue mai nulla
#   poi parla dal riquadro "Comando vocale" delle impostazioni rapide o dall’assistente digitale (pressione prolungata del tasto di accensione ecc.)
#
# Da un terminale:
#   voice.sh words                 apre la tabella delle parole (~/.z2term/voice/words-<lang>.txt; aggiungi o cambia frasi)
#   voice.sh try <frase>           ne prova una senza parlare (es. voice.sh try prossima canzone)
#   voice.sh listen                apre la schermata di ascolto e parla (come il riquadro)
#   voice.sh lang [lingua]         la lingua della tabella delle parole (voice.sh lang - le elenca)
#   voice.sh ai [off|codex|claude|agy]   usare l’IA o no (disattivata all’inizio)
#   voice.sh model [nome]          il modello dell’IA (vuoto = quello predefinito di quell’IA)
#   voice.sh setup / help
#
# L’IA è disattivata all’inizio. Anche attiva, viene interpellata solo se inizi con la parola chiave ("IA", "domanda" ...).
# Il meteo interroga wttr.in solo se dici "meteo" (luogo: weather_city= in ~/.z2term/voice/config).
# Le tue parole: "parole = comando" in ~/.z2term/voice/commands.txt (controllate prima della tabella delle parole)
# Nomi da chiamare: "nome numero" in ~/.z2term/voice/contacts.txt / registro: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — macro di partenza. Reagisce a ciò che succede sul dispositivo.",
                "# L’attesa la fa l’app (z2-when), quindi non è residente: si esegue una volta e si ferma.",
                "# Preparazione: Impostazioni -> \"Rilevamento degli eventi di sistema\" attivo",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (cuffie: una seconda regola con 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Ciò che è successo arriva in Z2_WHEN_EVENT (z2-when events elenca i nomi).",
                "# Per provarlo a mano:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT è vuoto. Per provarlo a mano: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Ricarica iniziata\" ;;",
                "  power_disconnected) z2-toast \"Ricarica interrotta\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — avvisa quando la batteria è scarica, adattandosi allo stato attuale.",
                "# Usa z2-state per sapere se lo schermo è acceso: toast se sì, notifica se no.",
                "# Preparazione: Impostazioni -> \"Rilevamento degli eventi di sistema\" attivo",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when passa il livello; altrimenti z2-state, così funziona anche a mano.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Non dire nulla durante la ricarica (non si sta davvero scaricando)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Batteria ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Batteria scarica\" \"Resta il ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — legge ad alta voce batteria e connessione ogni mattina.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Lo sveglia la sveglia di sistema, quindi scatta anche in Doze (può tardare di qualche minuto).",
                "# Non dipende dagli interruttori di rilevamento.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi",
                "z2-say \"Buongiorno. Batteria al ${d}{level} per cento, rete ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — copia un codice monouso dalle notifiche, poi lo cancella dopo",
                "# TTL secondi se gli appunti contengono ancora lo stesso valore.",
                "# Preparazione: Impostazioni -> \"Rilevamento notifiche\" attivo + concedi l’accesso alle notifiche del sistema",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ può nascondere i codici nelle notifiche sensibili. Per una via affidabile, usa",
                "#    la variante SMS (otp-sms.sh) — il testo degli SMS non viene mai nascosto.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — copia un codice monouso dagli SMS ricevuti, poi lo cancella dopo",
                "# TTL secondi se gli appunti contengono ancora lo stesso valore.",
                "# La variante SMS di otp-clip.sh. A differenza delle notifiche, il testo degli SMS non viene mai",
                "# nascosto dalla protezione delle notifiche sensibili (Android 15+) e funziona anche a telefono bloccato.",
                "# Preparazione: Impostazioni -> \"Rilevamento SMS\" attivo + concedi l’autorizzazione SMS del sistema",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — mostra una notifica per copiare i numeri dalle notifiche di chiamata.",
                "# Il suo pulsante \"Copia\" mette il numero negli appunti (perché attende un tocco: vedi sotto).",
                "# Preparazione: Impostazioni -> \"Rilevamento notifiche\" attivo + concedi l’accesso alle notifiche del sistema",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Per catturare anche le chiamate perse, registra una seconda regola (stesso script, altra categoria):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — controlla i feed e tiene solo il nuovo, come notifica e come testo.",
                "# A differenza degli altri esempi, questo **non** è residente: è della forma",
                "# \"si esegue una volta da un attivatore orario e si ferma\".",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — apre ciò che rss.sh ha raccolto, prima il più recente, un articolo per tocco.",
                "# Compare come pulsante macro sul widget di stato, quindi ogni tocco apre il successivo.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — te lo ricorda con una notifica: una volta (fra 30m, alle 18:30) o",
                "# ripetuto (ogni giorno / giorni feriali / ogni settimana). Scatta anche ad app chiusa (una sveglia di sistema",
                "# lo sveglia; nessun interruttore di rilevamento necessario). Rinvia dai pulsanti della notifica.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — trasforma un testo o un file in codice QR: disegnato qui, o salvato come PNG.",
                "# Passa qualcosa a un altro dispositivo senza riscriverlo. Si esegue una volta, non residente.",
            ),
            "macro.md" to listOf(
                "# md.sh — legge qui un file Markdown formattato, o lo apre nell’app.",
                "# Titoli, elenchi, tabelle, codice, link; le immagini compaiono sul posto. Si esegue una volta, non residente.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Un feed o una parola per riga che non deve finire sommerso (le righe che iniziano con # vengono ignorate).",
                "# Va bene una parte dell’URL o una parte del titolo. Ogni corrispondenza ha la sua notifica.",
                "# es.", "#example.org",
            ),
            "macro.usage" to listOf(
                "uso: z2-macro <sottocomando>",
                "  list                elenca gli esempi inclusi (segnati nuovo / uguale / diverso)",
                "  install <name|all>  copia in ~/.z2term/macros/ (non sovrascrive mai; -f per forzare)",
                "  remove <name>       rimuove la tua copia di un esempio incluso (si ferma se è diversa; -f per forzare)",
                "  diff <name>         cosa cambia tra la tua copia e quella inclusa (la tua a sinistra)",
                "  show <name>         mostra lo script",
                "  run <name>          lo esegue qui (Ctrl-C per fermare)",
                "  dir                 mostra dove stanno le macro",
                "  actions ...         macro di azioni Android (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Iscrizioni", "Aggiungi", "Articoli", "Modifica", "Rimuovi", "URL del feed",
                "Rimuovere questa iscrizione? Gli articoli raccolti verranno conservati.",
                "Le modifiche si applicano al prossimo controllo. Gli articoli raccolti vengono conservati.",
                "Nessuna iscrizione. Usa Aggiungi per inserire un URL RSS o Atom.",
                "Inserisci un URL HTTP(S) senza spazi né credenziali (al massimo 2048 caratteri).",
                "Questa iscrizione esiste già.",
                "Questa iscrizione è cambiata. Aggiorna l’elenco e riprova.",
                "Un’altra modifica è in fase di salvataggio. Riprova.",
                "Precedente", "Successivo",
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

/** [Z2ApiMsg] の Italiano 版。上書きしていない文言は英語で出る。 */
private class ApiMsgIt(d: String) : Z2ApiMsg("it", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — sceglie un documento; lo copia nella HOME e ne stampa il percorso.\n# z2-file save FILE [MIME] — sceglie una destinazione e salva una copia.\n# Annullare conta come errore. Fino a 512 MiB; la scelta scade dopo 5 minuti."

    override val usbHelp: String get() = """
        |# z2-usb list              … elenca i dispositivi USB collegati al telefono
        |# z2-usb allow [numero]    … chiede ad Android di lasciar usare un dispositivo ai programmi Linux
        |# Esegui allow una volta dopo aver collegato un dispositivo. L’autorizzazione dura fino allo scollegamento.
    """.trimMargin()

    override val usbUsage: String get() = "uso: z2-usb list | z2-usb allow [numero|percorso|VID:PID]"

    override val usbNoDevices: String get() = "Nessun dispositivo USB è collegato al telefono."

    override val usbNeedSelector: String get() = "È collegato più di un dispositivo USB; indica il suo numero da z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Approva l’accesso USB sulla schermata di Android: $path"

    override fun usbAlreadyAllowed(path: String): String = "L’accesso USB è già consentito: $path"

    override fun usbNotFound(selector: String): String = "Dispositivo USB non trovato: $selector"

    override val usbStateAllowed: String get() = "consentito"

    override val usbStateNeedsAllow: String get() = "da consentire"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "titolo" "testo"  /  z2-notify [-h] "testo"
        |#   -h / --high / --banner : la mostra come banner (in evidenza) in cima allo schermo
        |#   -b <etichetta>         : aggiunge un pulsante di risposta (fino a 3). Premerne uno aggiunge
        |#                            notify_action a ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <nome>              : un identificativo di questa notifica (per distinguere le risposte)
        |#   -c <testo>             : aggiunge un pulsante "Copia" che mette <testo> negli appunti.
        |#                            Usalo al posto di z2-clip set quando la macro gira in
        |#                            background: Android 10+ lascia scrivere negli appunti solo all’app in primo piano,
        |#                            quindi uno z2-clip set senza nessuno davanti viene ignorato.
    """.trimMargin()

    override val notifyUsage: String get() = "uso: z2-notify [-h] [-n nome] [-c testo] [-b etichetta]... <titolo> [testo]"

    override val toastHelp: String get() = """
        |# z2-toast <messaggio> … un breve messaggio in fondo allo schermo (un toast).
        |# Svanisce da solo e non lascia nulla. Usa z2-notify se deve restare,
        |# o se vuoi un pulsante da premere.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … apre gli strumenti QR con la fotocamera già in scansione.
        |# Dopo una scansione, un pulsante apre il contenuto nella sua app (browser, LINE e altre app,
        |# tastierino, email, SMS, Wi-Fi, contatti, calendario). Nulla si apre da solo.
        |# Mettilo su un riquadro delle impostazioni rapide o in un pannello laterale.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … crea un codice QR.
        |#   Niente da installare: codifica l’app (lo stesso codificatore della schermata QR).
        |#   Senza TEXT, legge l’input standard. -o è dove va il PNG
        |#   (predefinito ~/.z2term/qr/qr.png) e il percorso viene stampato. -t lo disegna invece qui,
        |#   in caratteri a blocchi. -p è la dimensione obiettivo in pixel (600 predefinito),
        |#   -m la zona di silenzio in moduli (4 predefinito). Fino a 2000 byte.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <testo> … passa un testo al menu di condivisione di Android (inviarlo a un’altra app).
        |# --file FILE [FILE ...] : invia il contenuto di file (fino a 32 file, 512 MiB in totale).
        |# I percorsi vengono letti nella shell locale attuale. -- forza la modalità testo. Le copie di oltre 24 ore vengono ripulite alla condivisione successiva.
        |# Tutti gli argomenti diventano un unico testo. L’app di destinazione si sceglie sullo schermo,
        |# quindi serve qualcuno — non è per una macro che gira da sola.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [titolo] … legge in z2term una pagina creata qui.
        |# Niente server né browser: il file viene passato all’app e mostrato così com’è.
        |# JavaScript e i caricamenti di rete sono disattivati, quindi nulla nella pagina può eseguire codice o contattare l’esterno.
        |# I link http:// e https:// si aprono nel tuo browser abituale.
        |# Il tema del telefono arriva come variabili CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), quindi una pagina scritta con esse segue i colori del terminale.
        |# Fino a 4 MB. Le immagini devono essere incorporate (URI data:); quelle remote non vengono caricate.
        |# Usa --edge PANNELLO:ELEMENTO per inviare a un elemento type=view esistente di un pannello laterale.
        |# Usa --controls FILE.json per moduli e azioni generiche gestiti da una macro installata.
        |# Gli elementi di vista si aggiornano a mano per impostazione predefinita. view-refresh=auto esegue run= all’apertura; z2-view torna a quell’elemento.
        |# Usa - o una pipe (ls -l | z2-view) per leggere l’input standard. Un testo che non inizia con < viene mostrato
        |# a capo automatico in carattere monospaziato, codici colore rimossi; --text / --html scelgono esplicitamente.
        |# Un pizzico regola il testo; --pinch page ingrandisce invece l’intera pagina. --text-size 50..300 fissa la
        |# dimensione del testo (per cento) di questa pagina. Entrambi valgono a schermo intero; un elemento del pannello ha le sue impostazioni.
        |# --no-refresh / --no-find nascondono i pulsanti Aggiorna / Trova a schermo intero (mostrati per impostazione predefinita).
    """.trimMargin()

    override val viewUsage: String get() = "uso: z2-view [--edge PANNELLO:ELEMENTO] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <file|-> [titolo]"

    override val viewUnreadable: String get() = "z2-view: file illeggibile:"

    override val openHelp: String get() = """
        |# z2-open <url|percorso> … lo apre con l’app predefinita (https://… o /sdcard/…).
        |# L’app che lo apre la sceglie Android; z2term si limita a passarlo.
        |# Un percorso è un percorso sul **telefono** (/sdcard/…), non dentro la distribuzione.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <file|->… … disegna l’immagine nel terminale.
        |#   PNG / JPEG / WebP / GIF / BMP. Passa - per leggere un’immagine da stdin.
        |#   -w <cols>  larghezza in colonne del terminale (predefinito: stare nel terminale)
        |#   -r <rows>  altezza in righe del terminale (con -w, la dimensione viene usata così com’è)
        |#   --clear    toglie tutte le immagini disegnate finora
        |#   -f         invia anche quando stdout non è un terminale
        |# ⚠ Le immagini compaiono solo in una scheda di z2term, o in un terminale che parla il protocollo
        |#   grafico kitty. Altrove (via ssh, in un paginatore) ottieni caratteri senza senso.
        |# ⚠ Le proporzioni presumono che una cella sia alta il doppio della larghezza. Se l’immagine
        |#   sembra schiacciata, regola: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (più piccolo = più alta).
    """.trimMargin()

    override val imgUsage: String get() = "uso: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->..."

    override val imgNoFile: String get() = "impossibile leggere il file:"

    override val imgNotTty: String get() = "stdout non è un terminale, quindi non è stato inviato nulla (-f lo invia comunque)."

    override val imgNoSize: String get() = "impossibile leggere la dimensione in pixel; disegno come quadrato:"

    override val qrUsage: String get() = "uso: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: impossibile creare il codice (il testo è troppo lungo? fino a 2000 byte)"

    override val clipHelp: String get() = """
        |# z2-clip get        … stampa gli appunti su stdout
        |# z2-clip set [text] … mette il testo (o stdin se omesso) negli appunti
        |# ⚠ Android 10+ lascia toccare gli appunti solo all’app in primo piano (o al metodo di inserimento
        |#   in uso). Da una macro in background viene ignorato senza una parola,
        |#   quindi usa z2-notify -c <testo>: aggiunge un pulsante "Copia" che funziona sempre.
    """.trimMargin()

    override val batteryHelp: String get() = "# Stampa il livello / lo stato di carica in JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (200ms predefiniti)"

    override val editHelp: String get() = "# z2-edit <file>      … modifica un file di testo nell’editor di z2term (semplice casella di testo; Salva lo riscrive)"

    override val sayHelp: String get() = "# z2-say <testo>      … parla con il TTS del dispositivo (legge stdin senza argomento)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (toggle predefinito). status legge lo stato di Android, compresi i cambiamenti esterni (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … cattura solo la parte cerchiata (free predefinito). Richiede Android 11 e z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (playpause predefinito)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Volume multimediale. Stampa l’attuale/max ottenuto."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (light predefinito). Legge un valore e restituisce JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Avvia qualsiasi Intent Android (startActivity predefinito). Un primo argomento che non è un’opzione è l’ACTION.
        |# La finestra libera riusa per impostazione predefinita l’attività e i limiti del sistema. --reuse-task / --os-bounds restano accettati.
        |# --bounds-only chiede limiti dell’80% senza chiavi di modalità; non si combina con --os-bounds.
        |# --no-scale disattiva per questo avvio la richiesta di finestra libera ridotta di Motorola.
        |# -p PKG da solo avvia quell’app. --window full|freeform|split|ask sceglie la richiesta di avvio (serve il supporto del sistema).
        |# --profile N apre l’app (-p/-n) in un altro profilo, come quello di lavoro. Niente schermo diviso. N è nel comando che il pannello laterale salva per quell’app.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … lo stato attuale del dispositivo, completo, in JSON
        |# z2-state <chiave>   … solo quel valore, grezzo (entra direttamente in un test)
        |# chiavi: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# es. [ "${d}(z2-state charging)" = "true" ] && echo in carica
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <domanda>
        |#   Fa una domanda alla persona e stampa la risposta su stdout.
        |#   La domanda arriva come notifica con un **campo di risposta**, quindi si può
        |#   rispondere dall’area notifiche senza aprire l’app (anche una macro in
        |#   background può chiedere).
        |#   -t <sec>   quanto attendere una risposta (300 predefinito = 5 min)
        |#   -H <hint>  etichetta mostrata nel campo di risposta
        |#   -d <testo> risposta suggerita (mostrata nella notifica)
        |# Scartare la notifica senza rispondere, o superare il tempo, esce con un codice diverso da zero
        |# e non stampa nulla — quindi puoi scrivere "oppure rinuncia":
        |#   name=${d}(z2-ask "Nome del branch?") || exit 1
        |# Confronta con z2-notify -b <etichetta>, che offre solo le scelte che hai preparato.
    """.trimMargin()

    override val askUsage: String get() = "uso: z2-ask [-t sec] [-H suggerimento] [-d predefinito] <domanda>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Stampa su stdout ciò che dici. Il riconoscimento gira **solo sul dispositivo**; la tua voce
        |#   non va mai in rete. Richiede Android 12+ e il riconoscimento vocale sul dispositivo
        |#   (per la tua lingua, i suoi dati vocali offline).
        |#   Parla con z2term sullo schermo (Android dà il microfono solo all’app sullo schermo).
        |#   Si ferma quando smetti di parlare. La prima volta viene chiesto l’accesso al microfono.
        |#   -w <parole> accetta solo queste parole (separate da virgole) e stampa quella sentita.
        |#               Vengono controllate tutte le ipotesi del riconoscitore, non solo la migliore.
        |#   -l <lingua> lingua da riconoscere (es. it-IT, en-US; predefinito: la lingua del dispositivo)
        |# Uscita: 0 = fatto / 1 = non sentito nulla, o nessuna delle parole / 2 = non disponibile (motivo su stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "uso: z2-listen [-w parola,parola,...] [-l lingua]"

    override val listenPrompt: String get() = "Parla ora… (si ferma quando smetti)"

    override val listenNeedsAndroid12: String get() = "z2-listen richiede Android 12 o successivi (prima il riconoscimento non può restare sul dispositivo)."

    override val listenBusy: String get() = "Già in ascolto (un altro z2-listen è in esecuzione)."

    override val listenEngineBusy: String get() = "Il riconoscimento vocale del dispositivo è occupato (forse lo usa un’altra app). Riprova più tardi."

    override val listenEngineStuck: String get() = "Il riconoscimento vocale non risponde. Se continua, forza l’arresto dell’app di riconoscimento del dispositivo (come Android System Intelligence) in Impostazioni Android › App."

    override val listenStopped: String get() = "Ascolto interrotto."

    override val listenNoOnDevice: String get() = "Questo dispositivo non ha un riconoscimento vocale integrato. z2-listen non invia mai la tua voce in rete, quindi qui non può funzionare."

    override val listenNoPermission: String get() = "L’accesso al microfono non è consentito. Esegui di nuovo z2-listen con z2term sullo schermo perché venga chiesto, o consentilo nelle impostazioni di Android."

    override val listenNotShown: String get() = "Porta z2term sullo schermo e riprova (Android dà il microfono solo all’app sullo schermo)."

    override val listenNoMic: String get() = "Impossibile usare il microfono (z2term ha lasciato lo schermo, o un’altra app sta registrando)."

    override val listenNoLanguage: String get() = "I dati vocali di questa lingua sul dispositivo non sono installati. Scaricali nelle impostazioni di input vocale del dispositivo (riconoscimento vocale offline)."

    override val listenNoSpeech: String get() = "Non si è sentito nulla."

    override val listenTimedOut: String get() = "Tempo scaduto."

    override fun listenNoMatch(heard: String): String = "Nessuna delle parole (sentito: $heard)"

    override fun listenFailed(code: Int): String = "Il riconoscimento vocale non è riuscito (errore $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … impedisce allo schermo di spegnersi da solo per questo tempo
        |# z2-screen keepon off          … ripristina subito, senza attendere la scadenza
        |# z2-screen status              … stato attuale in JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Cambia l’impostazione "spegnimento schermo" dell’intero sistema, quindi vale anche con l’app
        |# in background. NON è il mantenimento dello schermo della barra degli strumenti, che dura solo finché
        |# l’app è sullo schermo — quello non viene toccato.
        |# Il valore originale viene conservato e sempre riscritto alla scadenza (anche se l’app
        |# viene terminata o il dispositivo si riavvia). Al massimo 24h alla volta.
        |# Richiede "modifica impostazioni di sistema" (Impostazioni > spegnimento schermo > consenti).
        |# es. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "uso: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | comando...> [--off <comando...>] [-l <etichetta>] [-i <disegno>]
        |#                           … mette qualcosa sul riquadro delle impostazioni rapide 1-$tiles
        |# z2-tile add <1-$tiles>         … chiede di posizionare questo slot nell’area (Android 13+)
        |# z2-tile list              … i $tiles slot in TSV (slot / etichetta / comando; "-" = vuoto)
        |# z2-tile clear <1-$tiles|all>   … svuota uno slot
        |#
        |# Cosa assegni
        |#   O **il nome di file di una macro** in ~/.z2term/macros/, o **un comando** eseguito così
        |#   come scritto — cosa sia si deduce dal nome.
        |#   Una macro può prendere argomenti ("remind.sh ask"): la prima parola decide se è
        |#   una macro, il resto le viene passato. Due slot con la stessa macro hanno la stessa
        |#   etichetta, quindi dai loro -l.
        |#   Il comando gira con Z2_TILE=<slot> (e Z2_TILE_MACRO per una macro) nell’ambiente.
        |#   ⚠ Un nome che finisce in .sh e non è in ~/.z2term/macros/ viene rifiutato qui — come
        |#     comando verrebbe cercato nel PATH (che non include la cartella delle macro),
        |#     quindi il riquadro non farebbe nulla e lo direbbe solo in tile/run.log.
        |#
        |# Cosa fa un tocco
        |#   Un tocco avvia, un altro ferma (come i pulsanti del widget).
        |#   Il riquadro appare "attivo" mentre gira (il colore è l’accento del sistema, non il nostro).
        |#   Con --off hai **due comandi**: il tocco alterna tra loro e il riquadro
        |#   appare "attivo" finché è acceso. Usalo quando spegnere è un comando a parte
        |#   (z2-torch on / off).
        |#   Le coppie normali ricordano i cambi riusciti. Le assegnazioni dirette di z2-torch seguono
        |#   ciò che osserva Android, compresi i cambiamenti esterni. z2-screen keepon segue la sua impostazione effettivamente attiva.
        |#
        |# Posizionare nell’area
        |#   Su Android 13+, `set` chiede subito se il riquadro va nell’area, e
        |#   quella finestra porta **il nome e l’icona che hai appena assegnato**. Se dici di no, non viene posizionato nulla.
        |#   Ci sono esattamente $tiles slot: il numero è fissato nel manifest e non può crescere
        |#   durante l’esecuzione. Gli slot senza assegnazione non compaiono affatto nella schermata di modifica,
        |#   quindi averne $tiles non costa nulla.
        |#   ⚠ Compare solo finché z2term è in primo piano, e uno alla volta — una
        |#     macro che assegna due slot di fila chiederà solo per il primo. Usa poi
        |#     z2-tile add <slot> per il resto.
        |#   ⚠ Posizionare un riquadro resta una tua decisione — Android non lascia a un’app mettere lì i propri
        |#     riquadri. Dalla schermata di modifica (matita) dell’area delle impostazioni rapide, cerca
        |#     **z2term <slot>**.
        |#   ⚠ Quell’elenco mostra il nome e l’icona del manifest, non quelli assegnati (Android non ha
        |#     modo di cambiarli durante l’esecuzione), quindi z2-tile list ti dice quale numero è quale.
        |#
        |# Il disegno (-i)
        |#   Uno slot riceve da solo un’icona adatta quando il nome la tradisce (remind.sh -> un
        |#   orologio), e z2-icon la sostituisce con ciò che vuoi — dopo non viene più toccata.
        |#   -i fissa il disegno **prima** che compaia quella finestra, quindi una sola riga decide
        |#   insieme nome e icona (z2-icon sample elenca i nomi disponibili).
        |#   ⚠ Un nome sconosciuto viene rifiutato **prima di qualsiasi assegnazione** — uno slot impostato ma
        |#     con il disegno sbagliato si nota meno di uno mai impostato.
        |#
        |# es. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "schermo acceso"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l torcia
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin()

    override val tileUsage: String get() = "uso: z2-tile set <1-$tiles> <macro.sh|comando...> [--off <comando...>] [-l etichetta] " +
                "[-i disegno] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: questo disegno non esiste: $name (elenco con z2-icon sample). Non è stato assegnato nulla."

    override fun tileAddEmpty(n: Int): String = "z2-tile: lo slot $n non ha ancora nulla (assegnalo prima: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: impossibile chiedere qui l’aggiunta del riquadro (serve Android 13, con z2term in primo piano). " +
                "Lo slot è assegnato — posizionalo dalla schermata di modifica (matita) dell’area delle impostazioni rapide."

    override val tileAddAsked: String get() = "Ad Android è stato chiesto di aggiungere il riquadro — rispondi alla finestra. " +
                "(Non è comparso nulla? Posizionalo invece dalla schermata di modifica.)"

    override val iconHelp: String get() = """
        |# Sostituisce le icone della barra di stato e dei riquadri delle impostazioni rapide con il tuo disegno a pixel.
        |#
        |# Inizia qui
        |#   z2-icon pick 1     ... sceglie un disegno da un elenco e lo mette sul riquadro 1
        |#   z2-icon list       ... cosa ha ora ogni slot ("-p" mostra anche i disegni)
        |#
        |# Cosa significa <destinazione>
        |#   notify    l’icona di ogni notifica di questa app (quella della barra di stato)
        |#   1-$tiles      uno slot dei riquadri delle impostazioni rapide - lo stesso numero di z2-tile
        |#
        |# Mettere un disegno
        |#   pick <dest>              elencarli e scegliere per numero (il modo più semplice)
        |#   sample <dest> <nome>     quando conosci già il nome
        |#   edit <dest>              disegnare, o correggere quello attuale, in ${d}EDITOR
        |#   set <dest> <file>        leggerlo da un file ("-" per stdin)
        |#   auto <1-$tiles|all>          scegliere di nuovo in base a ciò che esegue il riquadro
        |#   clear <dest|all>         tornare all’icona integrata
        |#
        |# Finezza della griglia
        |#   grid                     la dimensione dei nuovi disegni
        |#   grid <$grids>          impostarla ($grid se non la cambi)
        |#   scale <dest> <$grids>    riportare il disegno di <dest> su quella griglia
        |#                            (dimezzando nel frattempo i gradini diagonali). **Dà
        |#                            spazio per disegnare più fine** - l’icona prodotta è
        |#                            comunque levigata, non serve farlo per quello.
        |#
        |# Tenere i tuoi disegni nell’elenco
        |#   save <dest> <nome>       dare un nome a ciò che c’è su <dest> e aggiungerlo all’elenco
        |#   sample                   l’elenco tra cui scegliere (numero / nome /
        |#                            "builtin" = incluso, "mine" = salvato da te)
        |#   sample <nome>            mostrare quel disegno
        |#   forget <nome>            togliere uno dei tuoi dall’elenco (ciò che hai già
        |#                            messo su una destinazione resta)
        |#
        |# Come disegnare
        |#   È una griglia di caratteri. "." "" "0" "-" "_" lasciano un punto vuoto e qualsiasi altro carattere
        |#   lo riempie, quindi usa quello che vedi meglio. Lo spazio vuoto attorno
        |#   al disegno viene ignorato - viene centrato, non serve riempire ogni riga
        |#   esattamente. La griglia è $grids ("z2-icon grid").
        |#   ⚠ Una griglia più grande non fa un’icona più grande. Riempi la griglia, altrimenti il disegno
        |#     esce più piccolo di quello sostituito.
        |#
        |# Buono a sapersi
        |#   ⚠ Passa solo la forma. Android colora queste icone con un unico colore suo
        |#     (i riquadri cambiano colore tra attivo e disattivo), quindi non c’è un colore da scegliere.
        |#   ⚠ **Il contorno viene levigato in uscita.** Un disegno fatto su $grid punti
        |#     esce comunque liscio su un riquadro (disegnato molto più grande della barra di stato),
        |#     quindi la griglia non è un problema nell’uso quotidiano. 48 / 64 servono quando **tu**
        |#     vuoi disegnare più fine. "show" mostra il disegno come l’hai fatto; il riquadro è più liscio.
        |#   ⚠ Un riquadro riceve da solo un disegno quando il nome lo tradisce ("z2-tile set 1
        |#     remind.sh" ci mette un orologio). Ciò che imposti qui vince e non viene più toccato
        |#     — "z2-icon auto 1" restituisce quello slot alla scelta automatica.
        |#   ⚠ Due icone non si possono cambiare: quella della schermata di **modifica** delle impostazioni rapide (da cui
        |#     trascini i riquadri) e l’icona del launcher. Android le fissa all’installazione.
        |#
        |# es. z2-icon pick 1               scegliere il disegno dello slot 1
        |#      z2-icon edit 1              ridisegnarlo da te
        |#      z2-icon save 1 my-face      dare un nome a quel disegno e aggiungerlo all’elenco
        |#      z2-icon sample 3 my-face    mettere lo stesso disegno sullo slot 3
        |#      z2-icon grid 64             disegnare i nuovi su una griglia 64x64 d’ora in poi
        |#      z2-icon scale 1 48          passare lo slot 1 a una griglia 48x48 per disegnare più fine
        |#      z2-icon list -p             controllare cosa c’è dove, con i disegni
        |#      z2-icon clear notify        ripristinare l’icona delle notifiche
    """.trimMargin()

    override val iconUsage: String get() = "uso: z2-icon pick <notify|1-$tiles> | sample [nome|dest nome] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [file|-] | " +
                "save <notify|1-$tiles> <nome> | forget <nome> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "file non trovato:"

    override val iconEditUnchanged: String get() = "invariato."

    override val iconPickPrompt: String get() = "numero (o nome), vuoto per annullare: "

    override val iconPickCancelled: String get() = "annullato."

    override val iconNoEditor: String get() = "nessun editor trovato. Imposta ${d}EDITOR, o usa: z2-icon set <dest> <file>"

    override val tileNoSuchMacro: String get() = "questa macro non è in ~/.z2term/macros/ (usa un percorso completo per eseguirla come comando):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [pacchetto] / trace dump / trace stop
        |# Traccia: attiva prima la cattura; 5 minuti, gli ultimi 128 eventi, solo in memoria.
        |# Tipi di campo e lunghezze UTF-16, mai i valori del testo. dump/stop restituiscono JSON; start reimposta.
        |# z2-noti list  … le notifiche mostrate ora, in TSV
        |#                 (chiave / pacchetto / nome dell’app / titolo / corpo)
        |# Solo lettura. Di proposito non c’è modo di premere o scartare una notifica:
        |# premerebbe anche i pulsanti paga e invia delle altre app.
        |# Richiede l’accesso alle notifiche (Impostazioni > Autorizzazioni e notifiche).
        |# Vedi anche: z2-when notify:otp / notify:pkg=<parte> / notify:contains=<parte>
    """.trimMargin()

    override val notiUsage: String get() = "uso: z2-noti list | trace start [pacchetto] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [nome]     … una volta alle prossime HH:MM (domani se sono passate)
        |# z2-alarm daily HH:MM [nome]  … ogni giorno alle HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [nome] … una volta, fra N secondi/minuti/ore
        |# z2-alarm list                … elenca ciò che è programmato (JSON; "exact" indica la puntualità)
        |# z2-alarm cancel <id|nome|all> … annulla
        |# Allo scatto, una riga {"event":"alarm","name":…} viene aggiunta a ~/.z2term/events.jsonl.
        |# Sveglia il dispositivo anche in Doze. "exact":true significa che scatta al minuto;
        |# "exact":false significa che Doze offre una finestra solo ogni 9-15 min, quindi un telefono lasciato
        |# a schermo spento può tardare così tanto. Disattivare l’ottimizzazione della batteria per questa app
        |# lo porta a true (non viene chiesta alcuna autorizzazione aggiuntiva).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: nessun comando date utilizzabile"

    override val sessionHelp: String get() = """
        |# z2-session list                     … elenca le schede (indice / id / tipo / segno / nome, TSV)
        |#   segni: * = sullo schermo / ! = in esecuzione / ? = non avviata / @ = collegata da una shell / - = altro
        |# z2-session new [nome]               … apre una scheda del terminale (stampa indice e id)
        |# z2-session send <scheda> <testo>... … scrive il testo in quella scheda (senza eseguirlo)
        |# z2-session send <scheda> <testo> --enter … lo scrive, poi lo esegue
        |# z2-session key <scheda> <tasto>...  … invia **tasti** a quella scheda (C-c / M-x / F5 / Up …)
        |#   modificatori: C- (Ctrl) e M- (Meta=Alt); si combinano, come in C-M-a
        |#   speciali: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   I tasti con Shift come C-S-a vengono rifiutati (un terminale non distingue Shift: stessi byte di C-a)
        |# z2-session key <scheda> --raw '\x1b[A' … tutto il resto, in byte (\xHH \e \n \r \t \0)
        |# z2-session capture [scheda] [--all] … prende lo schermo di quella scheda (--all include la cronologia)
        |# z2-session attach <scheda>          … resta collegato a quella scheda e scrivici direttamente
        |#   esci con Ctrl+] (in qualsiasi momento), o con ~. a inizio riga (come in ssh)
        |#   ⚠ via SSH funziona solo Ctrl+]: lo ssh davanti a te si mangia ~. e chiude la sessione SSH
        |#   scrivi ~~ per un ~ letterale a inizio riga, ~ poi Ctrl+] per un Ctrl+] letterale
        |#   durante il collegamento la scheda segue la dimensione della TUA finestra; torna com’era quando esci
        |# z2-session close <scheda>           … chiude quella scheda (mai l’ultima)
        |#
        |# <scheda> può essere l’indice di list, un id o il nome di una scheda. "." o omesso = la scheda sullo schermo.
        |# es. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "uso: z2-session list | new [nome] | send <scheda> <testo>... [--enter] | " +
                "key <scheda> <tasto>... | key <scheda> --raw <byte> | capture [scheda] [--all] | " +
                "attach <scheda> | close <scheda>"

    override val keyNothing: String get() = "z2-session key: nessun tasto indicato"

    override val keyRawEmpty: String get() = "z2-session key: --raw ha bisogno dei byte da inviare"

    override val keyUnknown: String get() = "z2-session key: tasto sconosciuto (vedi l’elenco in \"z2-session -h\"):"

    override val keyBadEscape: String get() = "z2-session key: impossibile leggere l’escape:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: il terminale non distingue Shift, quindi '$asWritten' darebbe " +
            "esattamente gli stessi byte di '$equivalentTo'. Scrivi '$equivalentTo' se è ciò che intendevi."

    override fun attachNoSuchTab(target: String): String = "questa scheda non esiste: '$target' (esegui \"z2-session list\" per vedere le schede)"

    override val attachNotTerminal: String get() = "è una scheda GUI, senza una shell a cui collegarsi. Scegli una scheda del terminale."

    override val attachNotStarted: String get() = "quella scheda non si è ancora avviata (segnata \"?\" in list). Aprila una volta nell’app, poi collegati."

    override val attachExited: String get() = "quella scheda è già terminata. Il suo schermo si può ancora leggere con \"z2-session capture\"."

    override val attachSelf: String get() = "è la scheda in cui stai scrivendo. Collegare una scheda a se stessa fa tornare il suo output " +
            "come input per sempre, quindi non si può tornare indietro. Scegli un’altra scheda."

    override val attachLoop: String get() = "quella scheda è già collegata di ritorno a questa, quindi le due si alimenterebbero " +
            "a vicenda senza fine. Scollegane prima una (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … server registrati (indice / id / stato / segno / nome, TSV)
        |#   segni: * = attivo / - = disattivato
        |# z2-server start <server>      … lo avvia come server residente (mantiene il dispositivo raggiungibile)
        |# z2-server stop <server>       … ferma questo (gli altri continuano)
        |# z2-server status [<server>]   … stato, pid, riavvii e ultimo codice di uscita
        |#
        |# <server> può essere l’indice di list, un id o il nome che gli hai dato nell’app.
        |# Si possono avviare solo i server registrati nell’app; questo non ne registra mai uno nuovo.
        |# Perché esiste: un demone avviato direttamente da una regola gira FUORI dal quadro dei server
        |# residenti (niente WakeLock, niente WifiLock, niente servizio in primo piano), quindi smette di rispondere una volta
        |# spento lo schermo. Avviarlo da qui lo mette in quel quadro.
        |# es. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "uso: z2-server list | start <server> | stop <server> | status [<server>]"

    override val serverNotFound: String get() = "z2-server: questo server non esiste (vedi \"z2-server list\"):"

    override val serverAmbiguous: String get() = "z2-server: il nome corrisponde a più server; usa l’id di \"z2-server list\":"

    override val serverNone: String get() = "z2-server: ancora nessun server registrato (aggiungine uno nell’app: 📜 -> Server)."

    override val serverLowPowerWarn: String get() = "z2-server: nota - la modalità a basso consumo è attiva, quindi non viene tenuto alcun WakeLock/WifiLock. " +
            "Il server potrebbe smettere di rispondere a schermo spento " +
            "(Impostazioni -> Automazione -> Protezione dei processi in background)."

    override val whenHelp: String get() = """
        |# z2-when <attivatore> run <cmd...>     … registra una regola
        |#   attivatori: charge:start | charge:stop  (rilevamento attivo richiesto)
        |#            battery:below=N | battery:above=N  (rilevamento attivo richiesto)
        |#            battery:temp>N | battery:temp<N  … la temperatura della batteria (°C) ha superato N (rilevamento attivo richiesto)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min ora giorno mese giorno-settimana'  (giorno-settimana 0-7 / 0,7=domenica. Fra virgolette: ci sono spazi)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<nome>  (rilevamento attivo richiesto)
        |#            net:online | net:offline  … una connessione utilizzabile è comparsa / sparita
        |#            net:wifi | net:mobile | net:ethernet  … la connessione in uso è passata a questa
        |#                                        (rilevamento attivo richiesto; conta i dati mobili, non solo il Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<parte> | share:ext=<ext>
        |#                                        … qualcosa è stato condiviso con z2term da un’altra app
        |#            boot                        … il dispositivo ha finito di avviarsi (funziona con rilevamento disattivato)
        |#            sms:any | sms:from=<parte> | sms:contains=<parte> | sms:otp  (RECEIVE_SMS richiesto)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (rilevamento attivo)
        |#            file:new=<cartella>[,ext=<ext>]  … è arrivato un nuovo file in questa cartella (rilevamento attivo richiesto)
        |#            file:changed=<cartella>[,ext=<ext>] | file:deleted=<cartella>[,ext=<ext>]
        |#                                        … un file lì è stato riscritto / è sparito (rilevamento attivo richiesto)
        |#            bt:connect | bt:disconnect | bt:connect=<nome> | bt:disconnect=<nome>
        |#                                        … un dispositivo Bluetooth si è connesso / disconnesso (nome: una parte, o l’indirizzo)
        |#                                        (rilevamento attivo e, su Android 12+, l’autorizzazione Dispositivi nelle vicinanze)
        |#            app:open=<app> | app:close=<app>  … questa app è passata in primo piano / l’hai lasciata (pacchetto o nome, una parte)
        |#                                        (z2term attivo in Accessibilità richiesto; funziona con rilevamento disattivato)
        |#            steps:reach=N               … i passi di oggi hanno raggiunto N (rilevamento attivo + autorizzazione Attività fisica)
        |#            notify:any | notify:otp | notify:pkg=<parte> | notify:title=<parte> | notify:contains=<parte>
        |#                                        … è arrivata una notifica (accesso alle notifiche richiesto)
        |#            event:<nome> | event:<prefisso>* | event:*  … qualsiasi evento del dispositivo, per nome
        |#              (z2-when events li elenca; gli stessi nomi di events.jsonl)
        |# Nome (facoltativo, subito dopo l’attivatore — prima di run):
        |#   name=<testo>            … a cosa serve questa regola, mostrato nella scheda Automazione
        |#                             (fra virgolette se ci sono spazi). Vuoto = viene mostrato l’attivatore.
        |#                             Solo per la visualizzazione: non cambia quando la regola gira.
        |# Filtri (qualsiasi attivatore, subito dopo — prima di run):
        |#   if=<cond>[,<cond>...]   … solo quando il dispositivo è in questo stato (E; ! nega)
        |#                             le chiavi sono quelle stampate da z2-state: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             es. if=wifi,!screen / if=ssid=Casa / if=level<30
        |#   if_any=<cond>[,<cond>...] … gira quando **una di esse** è vera (la virgola significa O)
        |#                             con if=: "tutte quelle di if E una di if_any"
        |#   else=<cmd>              … esegue questo **al suo posto**, quando if / if_any non è soddisfatto
        |#                             ⚠ solo if / if_any portano qui. Un’esecuzione saltata da between /
        |#                             days / cooldown non esegue nulla — una regola spenta
        |#                             di notte deve restare muta di notte, else compreso.
        |#   cooldown=30m            … non rieseguire entro questo tempo (10s / 30m / 2h)
        |#   between=22:00-07:00     … solo in questa fascia (passa la mezzanotte)
        |#   days=mon-fri            … solo in questi giorni (nomi o numeri cron 0-7, 0/7 = domenica)
        |#   answer='<domanda>=><risposta>' … quando il comando fa questa domanda nel terminale, scrive la risposta
        |#                             e Invio (ripetibile; es. answer='[y/N]=>y'). Confronta con la fine
        |#                             dell’ultima riga dell’output.
        |#   secret='<domanda>'      … lo stesso per una risposta nascosta (una password). Il contenuto si inserisce
        |#                             nell’editor delle regole dell’app e vi resta cifrato.
        |# Anche le esecuzioni saltate vengono registrate — z2-when fired mostra skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, quindi una regola che non gira mai si può spiegare. Il pulsante ▶ dell’app
        |# ignora tutti i filtri (serve a provare la regola).
        |# z2-when events                        … elenca i nomi utilizzabili con event:
        |# z2-when pause / resume                … ferma / riprende le esecuzioni automatiche (le regole restano)
        |# z2-when fired [n]                     … attivazioni recenti (ora / id / attivatore / run|paused)
        |# z2-when list                          … regole registrate (id / on|off / attivatore / -> / cmd, TSV;
        |#                                         il nome e i filtri, se presenti, fra [] alla fine)
        |# z2-when remove <id|all>  (anche rm)   … elimina
        |# z2-when on <id> / off <id>            … attiva / disattiva
        |# z2-when log <id>                      … il registro di esecuzione di questa regola (tail)
        |# Allo scatto, il comando gira nella distribuzione scelta con Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) nell’ambiente.
        |# Per net: hai Z2_WHEN_NET (la connessione ora) e Z2_WHEN_NET_PREV (quella di prima).
        |# Per share: hai Z2_WHEN_SHARE (il testo, o i percorsi dei file ricevuti)
        |# e Z2_WHEN_SHARE_KIND (text|file). La condivisione va comunque anche sulla riga di inserimento, come prima.
        |# Per file: hai Z2_WHEN_FILE (percorso completo) e Z2_WHEN_DIR.
        |# Per notify: hai Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (e Z2_WHEN_OTP per notify:otp). notify:category= confronta esattamente: call, missed_call, msg, ...
        |# Per event: hai anche Z2_WHEN_EVENT (il nome dell’evento); alarm / notify_action aggiungono
        |# Z2_WHEN_EVENT_NAME (l’identificativo con cui l’hai impostato) e Z2_WHEN_ACTION (pulsante premuto).
        |# Per battery:temp hai Z2_WHEN_TEMP (°C). Per bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Per app: Z2_WHEN_APP_PKG (l’app in primo piano ora) / Z2_WHEN_APP_PREV (quella di prima).
        |# Per steps: Z2_WHEN_STEPS (passi di oggi). Per file: anche Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated danno Z2_WHEN_PKG, timezone_changed dà Z2_WHEN_TZ,
        |# usb_attached / usb_detached danno Z2_WHEN_USB (nome) / Z2_WHEN_USB_ID (produttore:prodotto).
        |# La stessa regola non scatta due volte in 10 secondi (eventi come screen_on arrivano spesso).
        |# es. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "suoneria: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Rapporto del mattino' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Esecuzioni automatiche in pausa (z2-when resume per riprendere)"

    override val whenResumed: String get() = "Esecuzioni automatiche riprese"

    override val whenNoFires: String get() = "(non è ancora scattato nulla)"

    override val whenPausedNote: String get() = "# in pausa (z2-when resume per riprendere)"

    override val whenNoLog: String get() = "(ancora nessun registro)"

    override val whenWriteFailed: String get() = "z2-when: impossibile scrivere la regola"

    override val whenUnknownIfKey: String get() = "z2-when: chiave if= sconosciuta (z2-state elenca quelle utilizzabili):"

    override val whenUnknownTrigger: String get() = "z2-when: attivatore sconosciuto (z2-when senza argomenti li elenca):"

    override val whenBadTriggerSpec: String get() = "z2-when: questo attivatore non accetta quell’argomento:"

    override val whenPausedWarn: String get() = "nota: le esecuzioni automatiche sono in pausa (z2-when resume per riprendere)"

    override val whenRunJoined: String get() = "nota: gli a capo del comando sono diventati spazi (una regola legge una sola riga)"

    override val whenEventList: String get() = """
        |screen_on              lo schermo si è acceso                     [rilevamento attivo]
        |screen_off             lo schermo si è spento                     [rilevamento attivo]
        |unlocked               il dispositivo è stato sbloccato           [rilevamento attivo]
        |power_connected        la ricarica è iniziata                     [rilevamento attivo]
        |power_disconnected     la ricarica si è interrotta                [rilevamento attivo]
        |battery_low            la batteria è scarica                      [rilevamento attivo]
        |battery_okay           la batteria si è ripresa                   [rilevamento attivo]
        |battery_level          il livello ha superato un passo del 10%    [rilevamento attivo]
        |wifi_connected         connesso al Wi-Fi                          [rilevamento attivo]
        |wifi_disconnected      uscito dal Wi-Fi                           [rilevamento attivo]
        |net_online             è comparsa una connessione funzionante     [rilevamento attivo]
        |net_offline            nessuna connessione funzionante            [rilevamento attivo]
        |net_wifi               la connessione in uso è il Wi-Fi           [rilevamento attivo]
        |net_mobile             la connessione in uso è mobile             [rilevamento attivo]
        |headset_plugged        cuffie con filo collegate                  [rilevamento attivo]
        |headset_unplugged      cuffie con filo scollegate                 [rilevamento attivo]
        |bt_audio_connected     audio Bluetooth connesso                   [rilevamento attivo]
        |bt_audio_disconnected  audio Bluetooth scollegato                 [rilevamento attivo]
        |airplane_on            modalità aereo attivata                    [rilevamento attivo]
        |airplane_off           modalità aereo disattivata                 [rilevamento attivo]
        |ringer_normal          suoneria in modalità normale               [rilevamento attivo]
        |ringer_vibrate         suoneria in vibrazione                     [rilevamento attivo]
        |ringer_silent          suoneria in silenzioso                     [rilevamento attivo]
        |bt_connected           un dispositivo Bluetooth si è connesso     [rilevamento attivo]
        |bt_disconnected        un dispositivo Bluetooth si è disconnesso  [rilevamento attivo]
        |powersave_on           risparmio energetico attivato              [rilevamento attivo]
        |powersave_off          risparmio energetico disattivato           [rilevamento attivo]
        |dnd_on                 Non disturbare attivato                    [rilevamento attivo]
        |dnd_off                Non disturbare disattivato                 [rilevamento attivo]
        |app_installed          è stata installata un’app                  [rilevamento attivo]
        |app_removed            è stata disinstallata un’app               [rilevamento attivo]
        |app_updated            è stata aggiornata un’app                  [rilevamento attivo]
        |timezone_changed       il fuso orario è cambiato                  [rilevamento attivo]
        |usb_attached           è stato collegato un dispositivo USB       [rilevamento attivo]
        |usb_detached           è stato scollegato un dispositivo USB      [rilevamento attivo]
        |boot                   il dispositivo si è avviato                [sempre]
        |alarm                  è scattato uno z2-alarm                    [sempre]
        |notify_action          un pulsante di notifica                    [sempre]
        |unlock_failed          sblocco non riuscito                       [sempre, serve preparazione]
        |unlock_succeeded       sbloccato dopo un errore                   [sempre, serve preparazione]
        |voice                  è stato sentito un comando vocale          [sempre, dal riquadro o dall’assistente]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Nomi utilizzabili in event:<nome>. Nello stesso ordine di events.jsonl.
        |    # Il gruppo in alto richiede il rilevamento attivo (Impostazioni > Autorizzazioni e notifiche),
        |    # quello in basso lo imposti tu, quindi funziona con il rilevamento disattivato.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … controlla, scarica e passa la nuova versione al programma di installazione
        |# z2-update --check             … dice solo se c’è una versione più recente
        |# z2-update --keep              … conserva il .apk scaricato (eliminato per impostazione predefinita)
        |# z2-update --dir <cartella>    … scarica in questa cartella invece che in quella dell’app
        |#
        |# Da dove viene
        |#   La pagina GitHub Releases di z2term, lo stesso file che scaricheresti a mano
        |#   (z2term-<version>.apk, firmato con la chiave di rilascio). Non viene contattato nient’altro, e
        |#   proprio nulla finché non esegui questo.
        |#
        |# Cosa può e non può fare
        |#   ⚠ **Non può installare in silenzio.** Android mostra sempre la propria schermata "installare?" per
        |#     un’app che sostituisce se stessa, quindi l’ultimo tocco è sempre tuo. Questo comando fa tutti
        |#     i passaggi fino a quella schermata.
        |#   ⚠ La prima volta, consenti **"Installa app sconosciute"** per z2term (te lo dirà, e
        |#     le ⚙Impostazioni hanno lo stesso pulsante). Senza, la schermata di installazione non compare mai.
        |#   ⚠ Installato da F-Droid o da uno store? Allora questo rifiuta e ti dice di aggiornare lì —
        |#     la versione che hai spetta a loro sostituirla, non a noi.
        |#
        |# Il file scaricato
        |#   Per impostazione predefinita finisce nella cartella dell’app e viene eliminato una volta che l’installazione
        |#   è andata a buon fine (e al prossimo avvio, nel caso l’app fosse terminata a metà installazione — il che è
        |#   normale quando sostituisce se stessa). --keep o --dir lo cambiano; le ⚙Impostazioni hanno le stesse due
        |#   impostazioni per il pulsante che si trova lì.
        |#
        |# es. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # controlla ogni notte (te lo chiede comunque)
    """.trimMargin()

    override val updateUsage: String get() = "uso: z2-update [--check] [--keep] [--dir <cartella>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current è l’ultima versione."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: ricerca di una versione più recente ..."

    override val updateHandedToInstaller: String get() = "z2-update: la schermata di installazione è aperta sul dispositivo — approvala per finire."

    override fun updateNoApk(url: String): String = "z2-update: questa release non ha un .apk allegato. Prendilo dalla pagina della release: $url"

    override val updateNeedPermission: String get() = "z2-update: consenti prima \"Installa app sconosciute\" per z2term " +
            "(Impostazioni > App > Accesso speciale per le app > Installa app sconosciute, o il pulsante nelle Impostazioni di z2term)."

    override val updateManagedByStore: String get() = "z2-update: questa versione è stata installata da uno store (F-Droid / Play). Aggiornala lì."

    override fun updateFailed(reason: String): String = "z2-update: non riuscito: $reason"
}
/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Comando vocale — tabella delle parole (italiano). voice.sh la legge. Modificala per aggiungere o cambiare ciò che puoi dire.
#
# Come scriverla:
#   chiave = un modo|un altro modo|...   separa con "|"; controllata dall’alto in basso
#   metti un * per prendere le parole in quel punto (apri * -> "apri mappe" dà "mappe")
#   *X* prende le parole su entrambi i lati di X
#   chiave =                             niente a destra disattiva questa chiave
#   La punteggiatura viene ignorata, così come maiuscole/minuscole e spazi in più
#   Una chiave assente qui usa il valore incluso (elimina una riga per tornare all’originale)
#   Le righe che iniziano con say_ / msg_ / fmt_ / title_ sono ciò che viene detto o mostrato; {…} riceve un valore
#
# Provare senza parlare: sh ~/.z2term/macros/voice.sh try prossima canzone
# "le tue parole = un comando" vanno in ~/.z2term/voice/commands.txt (controllate prima di questa tabella).

# --- Parole ascoltate ---
ai = chiedi all’ia *|chiedi all ia *|ehi ia *|ia *|domanda *
media_next = prossima canzone|canzone successiva|brano successivo|prossimo brano|avanti|salta|salta la canzone|successiva
media_previous = canzone precedente|brano precedente|precedente|torna indietro|indietro
media_pause = pausa|stop|ferma la musica|metti in pausa|metti in pausa la musica|spegni la musica
media_play = riproduci|riprendi|continua|metti la musica|fai partire la musica|riprendi la musica
volume_up = alza il volume|più forte|aumenta il volume|alza|volume più alto
volume_down = abbassa il volume|più piano|diminuisci il volume|abbassa|volume più basso
volume_mute = silenzia|muto|modalità silenziosa|silenzio|togli l’audio
volume_unmute = riattiva l’audio|rimetti l’audio|audio attivo
time = che ore sono|che ora è|ora|l’ora|dimmi l’ora
date = che giorno è oggi|che giorno è|qual è la data|la data|data|quanti ne abbiamo oggi
battery = batteria|livello della batteria|quanta batteria|quanta batteria resta|stato della batteria
steps = passi|quanti passi|numero di passi|i miei passi|quanti passi oggi|passi di oggi|quanti passi ho fatto
torch_on = accendi la torcia|torcia|accendi la luce|luce|accendi la lampada
torch_off = spegni la torcia|spegni la luce|spegni la lampada
screenshot = screenshot|cattura schermo|fai uno screenshot|cattura lo schermo
home = home|schermata home|torna alla home
news = notizie|ultime notizie|leggi le notizie|novità|titoli|rss
weather = meteo|che tempo fa|il meteo|meteo di oggi|com’è il tempo
weather_tomorrow = meteo domani|che tempo farà domani|il meteo di domani|com’è il tempo domani
memo_read = leggi le mie note|leggi le note|le mie note|mostra le note|leggimi le note
memo_add = annota che *|annota *|prendi nota di *|prendi nota che *|nota *|scrivi *
remind_list = promemoria|i miei promemoria|elenco dei promemoria|mostra i promemoria|quali sono i miei promemoria|la mia agenda
remind = ricordami di *|ricordami che *|ricordami *|promemoria *
wake = svegliami alle *|svegliami *|metti la sveglia alle *|sveglia alle *|sveglia *
help = cosa posso dire|cosa sai fare|aiuto|comandi
timer = timer di *|imposta un timer di *|avvia un timer di *|un timer di *|timer *
navi = naviga verso *|portami a *|vai a *|percorso per *|come arrivo a *|voglio andare a *
navi_ask = navigazione|naviga|percorso|avvia la navigazione
place_phone = qual è il numero di *|il numero di telefono di *|numero di telefono di *|numero di *
call = chiama *|telefona a *|componi *
play = riproduci la canzone *|metti la canzone *|suona *
open = apri *|avvia *|lancia *
# Aprire per tipo (l’app predefinita del telefono). Es. apri il browser
open_browser = browser|il browser|internet
open_maps = mappe|la mappa|mappa|maps
open_mail = email|posta|la posta
open_camera = fotocamera|la fotocamera|camera
open_settings = impostazioni|le impostazioni
open_calendar = calendario|il calendario|agenda
open_music = musica|la musica
open_gallery = galleria|foto|la galleria
open_contacts = contatti|i contatti|rubrica
open_dialer = telefono|il telefono|tastierino
open_messages = messaggi|i messaggi|sms
open_calculator = calcolatrice|la calcolatrice
# Soprannomi delle app. Quando dire il nome non la apre, scrivi: soprannome:nome dell’app|soprannome:nome dell’app
app_names =

# Volume con un numero ("abbassa il volume 3", "volume 10", "volume 50 per cento", "volume al massimo") — corrisponde se contenuto
volume_word = volume|audio
volume_up_word = alza|aumenta|più forte|su
volume_down_word = abbassa|diminuisci|più piano|riduci|giù
volume_max_word = massimo|max|tutto
volume_min_word = minimo|zero
volume_percent_word = %|per cento|percento
volume_mute_word = muto|silenzia
volume_unmute_word = riattiva

# Durate e orari (timer, promemoria, sveglia)
unit_hour = ore|ora
unit_minute = minuti|minuto|min
unit_second = secondi|secondo|sec
unit_day = giorni|giorno
when_before = fra|tra
when_after = da adesso|da ora
clock_at = alle|verso le|per le
clock_hour = in punto
clock_minute =
clock_half = e mezza|e mezzo
clock_am = di mattina|del mattino
clock_pm = del pomeriggio|di pomeriggio|di sera|stasera|di notte
day_today = oggi
day_tomorrow = domani
day_after_tomorrow = dopodomani
day_daily = ogni giorno|tutti i giorni
day_weekdays = nei giorni feriali|dal lunedì al venerdì|giorni feriali
day_every = ogni|tutti i|tutte le
wd_mon = lunedì
wd_tue = martedì
wd_wed = mercoledì
wd_thu = giovedì
wd_fri = venerdì
wd_sat = sabato
wd_sun = domenica
# Parole piccole che restano tra il testo e le parti tolte
remind_filler = di|che|a|il|la|le|lo|per|mi|me

# --- Ciò che viene detto o mostrato ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Comando vocale
title_navi = Navigazione
title_ai = IA
title_timer = Timer
title_news = Notizie
title_memo = Note
title_remind = Promemoria
title_call = Chiamata
title_help = Cosa puoi dire
title_rule_voice = Comando vocale
title_rule_voice_locked = Comando vocale (bloccato)
title_rule_timer = Timer vocale
title_rule_steps = Conta passi (per il comando vocale)
fmt_clock = {h}:{mm}
fmt_clock_oclock = le {h}
fmt_weekdays = lunedì|martedì|mercoledì|giovedì|venerdì|sabato|domenica
fmt_months = gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre
say_time = Sono le {clock}
say_date = Oggi è {weekday} {day} {month_name}
say_battery = La batteria è al {n} per cento. {charging}
say_charging = È in carica.
say_steps = Oggi hai fatto {n} passi
say_steps_none = Ancora nessun passo registrato. Servono l’autorizzazione Attività fisica e una regola dei passi
say_news = Ecco gli ultimi titoli. {titles}
say_news_none = Non ci sono notizie nuove
fmt_weather_lang = it
say_weather = In questo momento {desc}, {temp} gradi
say_weather_tomorrow = Domani: {desc}, massima {hi} e minima {lo} gradi, {rain} per cento di probabilità di pioggia
say_weather_fail = Non sono riuscito a ottenere il meteo
say_memo_saved = Annotato
say_memo_recent = Le tue ultime note. {memos}
say_memo_none = Non ci sono note
say_remind_ok = Promemoria impostato. {res}
say_remind_fail = Non sono riuscito a impostarlo. {res}
say_remind_unclear = Non ho capito quando o di cosa ricordarti. Prova: ricordami di portare fuori la spazzatura domani alle 7 di mattina
say_remind_missing = La macro dei promemoria non è installata. Esegui z2-macro install remind in un terminale
fmt_wake_label = sveglia
say_wake = È ora di alzarsi
say_wake_set = Ti sveglierò alle {clock}
say_wake_unclear = Non ho capito l’ora della sveglia
fmt_unit_hour = ore
fmt_unit_minute = minuti
fmt_unit_second = secondi
fmt_timer_label = {n} {unit}
say_timer_set = Timer di {label} avviato
say_timer_done = Sono passati {label}
say_timer_unclear = Non ho capito la durata del timer
say_call_missing = Non ho trovato un numero per {who}
msg_call_missing = "{who}" non è in {file}. Scrivi un "nome numero" per riga.
say_open_missing = Non ho trovato un’app chiamata {name}
say_help = Ciò che puoi dire è in una notifica
say_locked = Sblocca il telefono, per favore
msg_help = che ore sono, data, batteria, passi / abbassa il volume 3, volume 50 per cento / prossima canzone, pausa / timer di 3 minuti, svegliami alle 7 / ricordami di portare fuori la spazzatura domani alle 7 di mattina, promemoria / notizie, meteo, meteo domani / annota comprare il latte, leggi le mie note / accendi la torcia, screenshot / apri … / chiama … / naviga verso …, numero di telefono di … / IA … (attivala con voice.sh ai) / cambiare le parole: voice.sh words
msg_unknown = Non ho capito: {text}
msg_locked = "{text}" continuerà quando il telefono sarà sbloccato
msg_navi_where = Di’ dove vuoi andare
msg_navi_hint = Dove (un nome di luogo)
msg_ai_off = L’IA è disattivata. Tocca Copia e incolla in un terminale per usare {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = L’IA è disattivata. Installa codex / claude / agy e accedi a uno di essi, poi sceglilo in un terminale, es. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} non è stato trovato. Installalo e accedi, o scegline un altro con sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Chiedo all’IA ({ai})…
msg_ai_noanswer = Non è arrivata nessuna risposta (registro: {log})
msg_ai_usage = ai: indica uno tra off | codex | claude | agy
msg_ai_how = L’IA è disattivata. Per usarla: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; viene interpellata solo se inizi con la parola chiave, "IA" o "domanda"). Modello: sh ~/.z2term/macros/voice.sh model <nome> (vuoto = quello predefinito di quell’IA)
fmt_ai_prompt = (La risposta verrà letta ad alta voce. Senza usare comandi né file, rispondi in italiano in 2 o 3 frasi, senza simboli né elenchi puntati.)
msg_setup_done = Regole del comando vocale registrate (quelle già presenti non sono state aggiunte di nuovo):
msg_setup_steps = La regola Conta passi (steps:reach=1000000) tiene solo attivo il contapassi così puoi chiedere i passi di oggi. Questo obiettivo non viene mai raggiunto, quindi non esegue nulla.
msg_words_file = Tabella delle parole: {file}  (modificala per aggiungere modi di dire; sh ~/.z2term/macros/voice.sh words la apre)"""
