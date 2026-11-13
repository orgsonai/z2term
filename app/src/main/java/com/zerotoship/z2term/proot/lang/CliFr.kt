package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Français (`fr`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: onglet (タブ) / appuyer (タップ) / appui long (長押し) / balayer (フリック) / fichier / dossier /
 * Paramètres (設定) / presse-papiers / macro (la) / tuile (タイル) / panneau latéral (エッジパネル) /
 * serveur résident (常駐サーバー)。呼びかけは vous。
 * ⚠ **アポストロフィは活字の ’ を使う** — シェルスクリプトの中に入るので、' だと引用符とぶつかる。
 */
internal object CliFr : CliLanguage {

    override val code: String = "fr"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgFr) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgFr(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Installation de la pile GUI",
            cleanInstalling = "🧹 Réinstallation propre de la pile GUI",
            noPackageManager = "❌ Aucun gestionnaire de paquets compatible (apk/apt-get/pacman) trouvé.",
            invalidGeometry = "❌ Résolution invalide",
            noXvnc = "❌ Xvnc/Xtigervnc absent (tigervnc n’est pas installé)",
            alreadyRunning = "✅ La GUI tourne déjà",
            startingXvnc = "▶ Démarrage",
            xvncFailed = "❌ Échec du démarrage de Xvnc. Journal :",
            ready = "✅ GUI prête. Connectez-vous depuis un onglet GUI de z2term.",
            running = "✅ GUI en cours",
            stopped = "⏹ La GUI est arrêtée",
            stoppedMsg = "⏹ Arrêtée",
            usage = "Utilisation : z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Impossible d’installer la pile GUI (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Vérifiez votre réseau. Pour réparer, vous pouvez exécuter z2gui clean dans un terminal.",
            audioInstalling = "🔊 Son de la GUI : installation de PulseAudio",
            audioNoPulse = "⚠️ Son de la GUI : pas de pulseaudio, on continue sans son",
            audioNoPactl = "⚠️ Son de la GUI : pas de pactl, on continue sans son",
            audioStartFailed = "⚠️ Son de la GUI : PulseAudio n’a pas démarré",
            audioReady = "🔊 Son de la GUI : z2sink.monitor →",
            menuApps = "Applications",
            menuWindows = "Fenêtres",
            menuDesktops = "Bureaux virtuels",
            menuPreviousDesktop = "Bureau précédent",
            menuNextDesktop = "Bureau suivant",
            menuReload = "Recharger le menu"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Numéro de port invalide",
            configOk = "configuration de sshd(dropbear) OK",
            installingDropbear = "📦 dropbear n’est pas installé — installation…",
            dropbearInstallFailed = "❌ Impossible d’installer dropbear. Vérifiez le réseau et les noms des paquets.",
            privilegedPortWarn = "⚠️ Port privilégié ; proot (sans root) ne peut sans doute pas l’utiliser (prenez 1024+).",
            lanExposeWarn = "⚠️ Mode exposé au LAN/WAN : authentification par clé obligatoire, utilisez une clé solide.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys est vide. Démarrage exposé au LAN annulé.",
            noAuthorizedKeysHint = "   Enregistrez votre clé publique dans ~/.ssh/authorized_keys et réessayez.",
            loopbackBind = "🔒 bouclage seulement. Pour exposer au LAN, passez --lan ou définissez Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear au premier plan (Ctrl-C pour arrêter)",
            listeningLan = "✅ dropbear à l’écoute (root, authentification par clé seulement)",
            listeningLoopback = "✅ dropbear à l’écoute (root, authentification par clé seulement, bouclage)",
            startupFailed = "❌ Échec du démarrage de dropbear. Journal :",
            deviceIp = "ip-appareil",
            authKeysHint = "ℹ ~/.ssh/authorized_keys est vide. Enregistrez la clé publique d’un client pour vous connecter.",
            authKeysExample = "   ex. cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Préparation du trousseau pacman (première fois seulement, sans réseau). Cela prend un instant…",
            "keyring.msgOk" to "✅ Trousseau prêt. Les paquets peuvent maintenant être installés.",
            "keyring.msgInitFail" to "❌ pacman-key --init a échoué. Nouvel essai à la prochaine ouverture de cet onglet.",
            "keyring.msgNoKeyrings" to "❌ Aucun trousseau inclus trouvé dans /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate a échoué. Nouvel essai à la prochaine ouverture de cet onglet.",
            "action.description" to "Macros nommées d’actions Android. Activez z2term Android actions avec z2-key permission.\nEnregistrer valide et remplace la définition ; screen=current enregistre l’écran.\nrun attend la fin ; start renvoie un ID d’exécution. Interrompre run arrête cette exécution.\nUne exécution à la fois, 64 instructions source, 1–300 secondes. La notification et stop annulent les étapes restantes.\nLes coordonnées utilisent par défaut l’appli au premier plan et exigent la même taille/orientation d’écran. Gestes manuels : jusqu’à 3 secondes ; touchers enregistrés : jusqu’à 30 secondes.\nUtilisez start depuis un geste de panneau ; run dans une macro shell ou une commande z2-when.\nStocké dans /root/.z2term/actions/NAME.actions (dossier personnel partagé). z2-macro actions est un alias.",
            "action.controlHelp" to "version=2 ajoute repeat N|forever ... end, if CONDITION ... else ... end et call NOM.\nLes conditions utilisent les clés de z2-state ou foreground=PAQUET ; les virgules signifient ET, ! inverse un terme.\nUn état de condition indisponible fait échouer l’exécution. Les appels sont validés et figés avant l’exécution.\n64 instructions source par définition ; 10000 instructions exécutées par exécution. Les délais de la racine et des enfants s’appliquent.",
            "action.pauseHelp" to "pause [RUN_ID] met l’exécution en pause ; l’étape en cours est renvoyée depuis son début. Tirer le volet des notifications met aussi en pause.\nresume [RUN_ID] reprend dès qu’un écran autre que z2term reste stable au premier plan (5 secondes au plus ; sinon elle reste en pause). Le délai s’arrête pendant la pause.",
            "action.uiHelp" to "version=2 accepte aussi click SÉLECTEUR, long-click SÉLECTEUR et wait-ui MS SÉLECTEUR.\nSélecteurs : text=Continuer, desc=Plus d’options, id=org.example.app:id/next. Correspondance exacte ; pas de guillemets autour des espaces.\nDéfinissez d’abord target ou launch. wait-ui cherche toutes les 250ms pendant 1–30000ms ; plusieurs correspondances échouent.\nLes actions sont envoyées une fois ; utilisez wait-ui pour l’écran suivant. Les champs de saisie/mot de passe sont exclus.\ninspect PAQUET renvoie en JSON les textes visibles, descriptions et ID de l’appli cible au premier plan.",
            "adb.mNoPm" to "z2adb : aucun gestionnaire de paquets compatible (apk/apt-get/pacman) trouvé.",
            "adb.mInstalling" to "z2adb : installation du client adb (${d}ADB_PKG) via ${d}PM ...",
            "adb.mInstallFail" to "z2adb : échec de l’installation d’adb. Installez-le à la main, puis réessayez.",
            "adb.mHaveAdb" to "z2adb : adb est déjà installé.",
            "adb.mNeedWireless" to "z2adb : si l’association échoue, activez Paramètres > Options pour les développeurs > Débogage sans fil (Android 11+).",
            "adb.mPairPort" to "Port d’association (Associer l’appareil avec un code d’association) : ",
            "adb.mConnPort" to "Port de connexion (affiché sous Débogage sans fil) : ",
            "adb.usageText" to """
        z2adb - se connecte à l’adb de cet appareil via le Débogage sans fil (self-adb, sans PC).

        Prérequis : activez Paramètres > Options pour les développeurs > Débogage sans fil (Android 11+).

          z2adb setup                installe le client adb (apk/apt-get/pacman, détecté automatiquement)
          z2adb pair  <port> [code]  associe (utilise le port d’association + le code à 6 chiffres des Paramètres)
          z2adb connect <port>       se connecte (utilise le port sous « Débogage sans fil »)
          z2adb status               affiche « adb devices -l »
          z2adb <tout le reste>      passé directement à adb (shell, logcat, install, ...)

        L’hôte par défaut est 127.0.0.1 ; passez hôte:port pour changer (ou définissez Z2ADB_HOST).
        Exemple :  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Joue sur Android le son d’une commande Linux locale, sans ouvrir de GUI.\ninstall : installe PulseAudio dans le système Linux actuel (télécharge des paquets).\nrun COMMANDE [ARG...] : connecte le son, exécute la commande, puis arrête son serveur audio.\nLancez un shell pour activer le son pour plusieurs commandes. Aucun fichier de configuration n’est écrasé.\nNécessite un onglet de terminal local et un lecteur compatible PulseAudio. SSH n’est pas pris en charge.",
            "audio.missing" to "Les outils audio manquent. Exécutez : z2-audio install",
            "audio.failed" to "La connexion audio a échoué ; la commande n’a pas été lancée.",
            "doctor.head" to "== z2doctor (cet appareil) ==",
            "doctor.secApp" to "-- appli --",
            "doctor.secLinux" to "-- côté Linux --",
            "doctor.secPerm" to "-- autorisations --",
            "doctor.secAuto" to "-- automatisation --",
            "doctor.secReport" to "-- copiez ceci pour demander de l’aide --",
            "doctor.lVersion" to "version",
            "doctor.lEngine" to "moteur",
            "doctor.lDistro" to "distribution",
            "doctor.lKernel" to "noyau",
            "doctor.lDisk" to "espace libre",
            "doctor.lHome" to "dossier personnel",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "notifications",
            "doctor.lNotifyRead" to "accès aux notifications",
            "doctor.lBattOpt" to "optimisation de la batterie",
            "doctor.lStorage" to "stockage (tous les fichiers)",
            "doctor.lSms" to "réception des SMS",
            "doctor.lCapture" to "détection des événements",
            "doctor.lServers" to "serveurs résidents",
            "doctor.lRules" to "règles d’automatisation",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Paramètres Android > Applis > Z2Term > Notifications : autoriser",
            "doctor.fixNotifyRead" to "-> Paramètres > Autorisations et notifications > Détection des notifications",
            "doctor.fixBattOpt" to "-> Paramètres > Protection des processus en arrière-plan > exclure de l’optimisation de la batterie",
            "doctor.fixStorage" to "-> Paramètres > autoriser l’accès à tous les fichiers (nécessaire pour voir /sdcard)",
            "doctor.fixSms" to "-> Paramètres > Détection des SMS (seulement pour les déclencheurs sms:)",
            "doctor.fixCapture" to "-> Paramètres > Détection des événements système (nécessaire aux déclencheurs charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> l’automatisation est en pause. « z2-when resume » ou l’onglet Automatisation",
            "doctor.fixDisk" to "-> moins de 500 Mo libres. Supprimez les données de système inutilisées dans les Paramètres",
            "doctor.fixSdcard" to "-> /sdcard est vide ici ; c’est l’autorisation de stockage ci-dessus qui le rend visible",
            "doctor.noteRedacted" to "(SSID / IP / noms d’hôte sont volontairement omis)",
            "doctor.allGood" to "Aucun problème trouvé.",
            "doctor.someBad" to "problèmes trouvés :",
            "doctor.usage" to "utilisation : z2doctor [--share | --clip]   (--share confie le rapport au menu de partage d’Android)",
            "doctor.pausedYes" to "en pause",
            "doctor.secExits" to "-- comment cela s’est terminé la dernière fois --",
            "doctor.lExitsNote" to "sorties anormales récentes (les plus récentes d’abord) :",
            "doctor.lExitsNone" to "sorties anormales : aucune enregistrée",
            "doctor.lExitsFile" to "(historique complet : ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> tué par manque de mémoire. Lancez moins de tâches lourdes à la fois, ou réduisez les serveurs résidents / onglets",
            "doctor.title" to "autodiagnostic pour \"ça ne marche pas\"",
            "doctor.seeAlso" to "Voir aussi : z2scan self (contrôle de sécurité, un autre outil)",
            "help.header" to "Commandes propres à Z2Term",
            "help.list" to """
        |[Version / infos]
        |  z2version [--short]            Version de l’appli, moteur, système (distribution), noyau
|  z2doctor [--share|--clip]      Pourquoi ça ne marche pas ? Autodiagnostic + un rapport à coller
        |
        |[Fonctions du téléphone]
        |  z2-notify [-h] [-b LABEL]...   Notification (-h : bannière, -b : bouton de réponse)
        |  z2-ask "question"              Demande via un champ de réponse de notification ; affiche la réponse
        |  z2-listen [-w WORD,...]        Voix vers texte, sur l’appareil seulement (z2-listen -h)
        |  z2-toast "message"             Toast (court message à l’écran)
        |  z2-share "text"                Confie un texte au menu de partage d’Android
        |  z2-open <url|path>             Ouvre une URL/un fichier dans l’appli par défaut
        |  z2-view <file.html>            Lit dans l’appli une page faite ici
        |  z2-edit <file>                 Modifie un fichier texte dans l’éditeur de l’appli
        |  z2-qr                          Scanne un code QR avec l’appareil photo (un bouton l’ouvre)
        |  z2-img <file>                  Dessine une image dans le terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Lit / définit le presse-papiers (set : stdin sans argument)
        |  z2-file pick | save FILE     Sélecteur / export de documents Android (z2-file --help)
        |  z2-battery                     Niveau de batterie / état de charge (JSON)
        |  z2-vibrate [ms]                Vibre (200ms par défaut)
        |  z2-say "text"                  Parle via le TTS de l’appareil (stdin sans argument)
        |  z2-torch [on|off|toggle|status]       Lampe de poche (toggle par défaut)
        |  z2-media [play|pause|next]     Touches multimédia (aussi previous/stop)
        |  z2-volume up|down|N|N%         Volume multimédia (renvoie actuel/max)
        |  z2-audio run COMMAND ...      Exécute une commande Linux avec le son d’Android
        |  z2-sensor [light|accel|prox]   Lit une mesure de capteur (JSON)
        |  z2-intent -a ACT -d URI ...    Lance n’importe quel Intent Android (voir MACRO-GUIDE)
        |  z2-state [key]                 État actuel en JSON (ou une clé brute)
        |  z2-screen keepon 1h | off      Empêche l’écran de s’éteindre seul, pour un temps
        |  z2-tile set 1 backup.sh        Met une macro/commande sur une tuile des réglages rapides (12)
        |  z2-icon edit 1 | edit notify   Dessinez vous-même l’icône de la tuile / de la barre (24/48/64 points)
        |  z2-alarm at|daily HH:MM [name] Déclencheur horaire -> events.jsonl (aussi list/cancel)
        |  z2-when <trigger> run <cmd>    Exécute seul sur charge/batterie/heure/événements (z2-when : usage)
        |  z2-macro list|install <name>   Exemples d’automatisation inclus (voir MACRO-GUIDE)
        |  z2-session list|new|send|...   Pilote les onglets de cette appli (z2-session : usage)
        |  z2-usb list | allow [device]   Utilise un appareil USB depuis Linux (Android demande l’accord)
        |  z2-update [--check]            Met à jour z2term via GitHub Releases (vous validez)
        |
        |[Applis graphiques (GUI)]
        |  z2gui start [WxH] | stop | status   Bureau Linux (ex. z2gui start 1280x720)
        |  z2run <gui-app>                Lance une appli GUI (ouvre aussi l’onglet GUI)
        |
        |[Connexion]
        |  z2adb pair/connect/shell ...   adb vers ce téléphone lui-même, sans PC  (z2adb help)
        |  sshd [-p N]                    Serveur SSH (par défaut : 127.0.0.1 seulement, clé)
        |
        |[Sécurité]
        |  z2scan self [--save]           Autodiagnostic de cet appareil/localhost (z2scan help)
        |  z2scan diff                    Seulement ce qui a changé depuis la référence (sortie 1 = nouveau)
        |  z2scan net|host|cve            nmap/lynis/trivy sur localhost (le distant demande un opt-in)
        |
        |[Aide]
        |  z2help | z2term                Cette liste
        |
        |Chaque commande ci-dessus s’explique : ajoutez --help (ex. « z2-tile --help »).
        |Plus : « z2adb help », lancez « z2gui » sans argument, ou la section 11 du HANDBOOK.
    """,
            "help.edge" to "[Panneaux flottants]\nz2-edge --help   Définir des panneaux et mettre à jour leur contenu\nz2-key --help    Actions globales Android\nz2-shot --help   Capturer seulement la partie entourée\nz2-app --help    Lister les applications et exporter les icônes\nz2-action --help Macros nommées d’actions Android\nz2-ui --help     Contrôle des applis par l’IA (l’aide est un guide pour les agents IA)",
            "macro.rssBody.head" to """
#
# Préparation :
#   1) Une URL de flux par ligne dans :  ~/.z2term/rss/feeds.txt
#   2) Relever toutes les 30 minutes :
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Facultatif - un flux ou un mot par ligne qui ne doit pas être noyé :  ~/.z2term/rss/important.txt
#      Ce qui correspond a sa propre notification, un flux chargé ne peut donc pas le chasser.
#      Écrivez une partie d’URL ou une partie de titre (ex. example.org).
#   4) Enregistrez les actions de notification dans l’Automatisation :
#        z2-when event:notify_action run '$rssNotificationAction'
#      Ouvrir envoie l’article au navigateur ; Liste ouvre tout ce qui a été collecté, à lire dans z2term.
#      Remarque : ouvrir un écran depuis un bouton de notification nécessite l’autorisation « superposer aux autres applis ».
#   5) Lire la liste collectée : sh ~/.z2term/macros/rss.sh view
#      Gérez les deux règles dans Liste des commandes -> Automatisation -> Règles d’automatisation.
#      Modifiez une règle de notification existante au lieu d’en ajouter une autre.
#   6) Notifications : par défaut le relevé programmé notifie et un relevé depuis la liste non.
#        sh ~/.z2term/macros/rss.sh notify off        # relevé programmé : on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Actualiser / Articles dans la liste : on / off
#      Les deux sont aussi des interrupteurs dans Paramètres, sur la liste, à changer quand vous voulez.
#
# Nécessite : python3 (Alpine : apk add python3 / Debian : apt-get install -y python3 / Arch : pacman -S python)
#   Les miniatures (Paramètres sur la liste) nécessitent aussi Pillow (Alpine : apk add py3-pillow / Debian : apt-get install -y python3-pil / Arch : pacman -S python-pillow)
# Batterie : plus vous relevez souvent, plus ça coûte. Ne descendez pas sous 30 minutes.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "nombre maximal de lignes gardées dans seen/latest",
            "macro.cNoPy" to "python3 est nécessaire (ex. apk add python3)",
            "macro.cWriteFeeds" to "Écrivez une URL de flux par ligne dans :",
            "macro.cFeedsHint" to "# Une URL de flux par ligne (les lignes qui commencent par # sont ignorées)",
            "macro.cSkipFail" to "ignorer en silence un flux qui ne se télécharge ou ne s’analyse pas (les autres continuent)",
            "macro.cDiff" to "# Retirer ce qu’on a déjà vu pour ne garder que le nouveau (même astuce que la comparaison de référence de z2scan).\n# Les dates et l’ordre des flux ne sont pas fiables ; aucun des deux n’est sûr.",
            "macro.cPrepend" to "# Empiler le plus récent en premier pour que le mode « début (head) » du widget lise juste.",
            "macro.cNotify" to "%s nouveaux",
            "macro.cOpen" to "Ouvrir",
            "macro.cListNone" to "Rien de collecté pour l’instant — lancez-le d’abord une fois sans argument.",
            "macro.cListHint" to "Listez-les avec : rss.sh list [nombre]",
            "macro.cList" to "Liste",
            "macro.cPageTitle" to "Flux",
            "macro.cPageSites" to "%s sites",
            "macro.cPageArticles" to "%s articles",
            "macro.cPageDoc" to "# Construit la page de lecture (z2-view l’affiche dans l’appli ; pas de serveur, pas de navigateur).\n" +
            "# Groupée par site, la plus récente d’abord, avec un bouton Ouvrir par article. Le thème du téléphone\n" +
            "# arrive en variables CSS, la page suit donc les couleurs du terminal.",
            "macro.cViewHint" to "Lisez-les avec : rss.sh view",
            "macro.cHitMax" to "nombre maximal de notifications par article en une exécution (le reste va dans le résumé)",
            "macro.cSplitDoc" to "# Séparer ce qui ne doit pas être manqué. Le corps du résumé ne porte que 3 lignes, un flux chargé\n" +
            "# qui se met à jour en même temps chasse donc ce qui comptait. Les correspondances ont **leur propre\n" +
            "# notification** (l’appli donne un id distinct par notification, rien n’est donc remplacé).",
            "macro.cNameDoc" to "# Mettre l’URL dans le nom de la notification : appuyer sur le bouton la rend comme {name} dans\n" +
            "# notify_action, le bon article s’ouvre donc même avec plusieurs notifications à l’écran (préparation 4).",
            "macro.cListDoc" to "# Afficher ce qui a été collecté comme liste lisible (sans couleur — elle doit ressembler au fichier/widget).\n" +
            "# latest.txt lui-même reste des données brutes, un article par ligne (le tail du widget et rss-open.sh le lisent).",
            "macro.cSettings" to "Paramètres",
            "macro.cArchive" to "Archive",
            "macro.cDelete" to "Supprimer",
            "macro.cThumbs" to "Miniatures",
            "macro.cSave" to "Garder les anciens articles",
            "macro.cNotifyPoll" to "Signaler les nouveaux articles (relevé programmé)",
            "macro.cNotifyView" to "Signaler les nouveaux articles (relevés depuis la liste)",
            "macro.cDeleteSite" to "Supprimer tous les articles de ce site",
            "macro.cConfirmSite" to "Retirer de la liste %s articles de %h ?",
            "macro.cArticles" to "Articles",
            "macro.cPrev" to "Précédent",
            "macro.cNext" to "Suivant",
            "macro.cCount" to "%s articles",
            "macro.cUndated" to "Sans date",
            "macro.cArchiveEmpty" to "Rien de gardé pour l’instant. Activez Garder les anciens articles dans Paramètres, et les articles collectés resteront ici.",
            "macro.cSearch" to "Rechercher",
            "macro.cSearchWord" to "Mots (séparez par des espaces pour les exiger tous)",
            "macro.cByMonth" to "Par mois",
            "macro.cNoHit" to "Rien trouvé.",
            "macro.cDropMonth" to "Supprimer ce mois",
            "macro.cDropConfirm" to "Supprimer les %s articles gardés de %m ? Impossible d’annuler.",
            "macro.cOnOff" to "Indiquez on ou off",
            "macro.cNoPillow" to "Les miniatures nécessitent Pillow pour Python (Alpine : apk add py3-pillow / Debian : apt-get install -y python3-pil / Arch : pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Préparation : installez d’abord le côté qui collecte
#   z2-macro install rss
# Widget : attribuez « rss-open » à un bouton dans les réglages du widget d’état.
#
# z2-run: attribuez « rss-open » à un bouton du widget d’état (pour essayer ici : sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Pas encore d’articles",
            "macro.cAllRead" to "Rien de nouveau à ouvrir",
            "macro.cPick" to "# Les lignes de latest.txt sont \"titre  URL\". Prendre la première URL pas encore ouverte.",
            "macro.cCap" to "limiter la liste des ouverts pour qu’elle ne grossisse pas indéfiniment",
            "macro.copied" to "Code copié : ${d}{code}",
            "macro.cleared" to "Le code copié a été effacé",
            "macro.otpTitle" to "Code à usage unique",
            "macro.cTtl" to "secondes avant d’effacer la copie (seulement quand elle est entrée directement)",
            "macro.cCode" to "# L’appli a déjà extrait le code. Ne rien faire si elle n’a pas pu.",
            "macro.cTry" to "# Essayer de le mettre directement. Cela n’entre que pendant que vous **regardez l’appli** :\n" +
            "# Android 10+ ne laisse que l’appli au premier plan écrire dans le presse-papiers, une exécution en\n" +
            "# arrière-plan est donc ignorée en silence. Relire pour voir si c’est entré.",
            "macro.cFallback" to "  # Pas entré = vous ne regardez pas l’appli. Le transmettre plutôt par le bouton\n" +
            "  # « Copier » de la notification (appuyer dessus met l’appli au premier plan).\n" +
            "  # ⚠ Ce qui entre ainsi n’est pas effacé par le TTL ci-dessous : effacer exige aussi le premier plan.",
            "macro.cClear" to "# Après le TTL, effacer le presse-papiers seulement s’il contient encore le code copié\n" +
            "# (ce qui a été copié depuis n’est pas touché).",
            "macro.cHow" to """
# ■ Copier les numéros de téléphone des notifications d’appel
#   Cherche dans le titre puis dans le texte un numéro de téléphone seul et propose un bouton Copier.
#   Les appelants enregistrés ou non sont inclus ; les contacts ne sont pas interrogés.
#   Un nom seul ne suffit pas à retrouver un numéro. Seul l’accès aux notifications est utilisé.
#   Le nom de fichier unknown-call.sh est gardé pour que les règles existantes continuent de marcher.
""",
            "macro.cIsNum" to """
# Est-ce un numéro seul ? Retirer tout caractère qu’un numéro peut contenir (chiffres + - ( ) espace) ;
# s’il **ne reste rien** et qu’il y a 7 à 15 chiffres, le traiter comme un numéro.
#   -> Un champ avec un nom ou d’autres lettres n’est pas un numéro seul ; vérifier l’autre champ.
#   -> "Inconnu"/"Numéro masqué" tombent aussi ici (assouplissez le test ci-dessous si vous les voulez).
# ⚠ N’écrivez pas ceci en case [!...] : la ) dans le motif est lue comme séparateur du case.
""",
            "macro.cScan" to "# L’appelant est en général dans le titre, mais certaines applis téléphone le mettent dans le texte. Vérifier les deux.",
            "macro.cSkip" to "# Aucun numéro seul dans les deux champs : rien à copier.",
            "macro.cWhat" to "# La catégorie dit lequel a déclenché (même valeur que le notify:category= enregistré).",
            "macro.cCopy" to """
# Transmettre le numéro par le bouton « Copier » de la notification (-c). Appeler z2-clip set ici
# n’entrerait pas : Android 10+ ne laisse que l’appli au premier plan écrire dans le presse-papiers, et pendant un
# appel c’est l’appli téléphone. Appuyer sur le bouton met z2term au premier plan pour cet instant.
""",
            "macro.missed" to "Appel manqué",
            "macro.incoming" to "Appel entrant",
            "macro.title" to "Copier le numéro de téléphone",
            "macro.stNew" to "nouveau",
            "macro.stSame" to "égal",
            "macro.stDiff" to "diffère",
            "macro.msgInstalled" to "installé :",
            "macro.msgSame" to "la même chose est déjà installée :",
            "macro.msgOutdated" to "le vôtre diffère de celui inclus :",
            "macro.msgHowUpdate" to "regardez d’abord : z2-macro diff %s   /   remplacer par celui inclus : z2-macro install -f %s (vos modifications partent aussi)",
            "macro.msgNotInstalled" to "pas encore installé (exécutez d’abord z2-macro install) :",
            "macro.msgRemoved" to "retiré :",
            "macro.msgAbsent" to "pas installé :",
            "macro.msgRemoveDiffers" to "le vôtre diffère de celui inclus, il n’a donc pas été retiré :",
            "macro.msgHowRemove" to "regardez d’abord : z2-macro diff %s   /   le retirer quand même : z2-macro remove -f %s (vos modifications partent aussi)",
            "macro.msgNoDiffTool" to "pas de diff ici. Vous pouvez quand même lire celui inclus avec z2-macro show.",
            "macro.msgSameAsBundled" to "identique à celui inclus.",
            "macro.msgNotFound" to "exemple introuvable :",
            "macro.msgHintResident" to "Pour le garder actif, enregistrez ceci dans Paramètres -> Serveurs résidents :",
            "macro.msgHintRun" to "Comment l’exécuter :",
            "macro.remindBody.head" to """
#
# Préparation (une fois) :
#   sh ~/.z2term/macros/remind.sh setup   enregistre les deux accroches et les deux tuiles
#
# Syntaxe :
#   remind.sh <quand> <texte>        ajoute un rappel
#   remind.sh list                   les liste (le numéro vient en premier)
#   remind.sh del <n|all>            annule (rm marche aussi)
#   remind.sh peek                   montre la liste dans une notification (avec un bouton Supprimer)
#   remind.sh ask                    demande dans une zone de réponse de notification (pour la tuile)
#   remind.sh setup                  enregistre les accroches et les tuiles (une fois)
#   remind.sh help                   ce texte
#
# <quand> - une fois :
#   30m / 90s / 2h                   à partir de maintenant
#   18:30                            le prochain 18:30 (demain s’il est passé)
#   sat 21:00                       le prochain jour de la semaine correspondant, une fois (plus tard aujourd’hui compte aussi)
#   Japonais : 土曜日の夜九時 = sat 21:00 ; 朝七時 = 07:00 ; 夜九時半 = 21:30
#   Japonais : 夜12時 est ambigu ; pour minuit, utilisez 00:00 avec le jour explicite.
#   tomorrow 18:30 / 3d 07:00        par jour (3d = dans 3 jours)
#   07/30 19:00                      mois/jour (l’an prochain s’il est passé)
#   2030 07/30 19:00                 avec une année
#   203007301900 / 07301900          chiffres seulement (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ sans l’heure, garde **l’heure actuelle de la journée** (3d / 07/30 ...)
#
# <quand> - répété :
#   daily 07:00 / weekday 09:00      chaque jour / lun-ven
#   weekly mon 09:00                 ce jour de la semaine
#   monthly 15 09:00                 ce jour du mois
#   yearly 07/30 19:00               ce mois et ce jour
#   every 19:00 / every wed 19:00    « every » seul marche (le mot suivant décide)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Exemples :
#   remind.sh 30m prendre les médicaments
#   remind.sh tomorrow 18:30 sortir les poubelles
#   remind.sh monthly 25 10:00 loyer
#   remind.sh list        ->  1  ⏰ 07/30 18:30  sortir les poubelles
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (ensuite juste : remind.sh 30m ...)
""",
            "macro.cStore" to """# Un rappel = une ligne dans ${d}DIR/<id>.txt, séparée par tabulation : type / libellé / id-de-quand / texte.
#   type : once (à venir) / fired (déjà déclenché) / repeat (récurrent)
#   id-de-quand : seulement pour repeat (pour que del puisse appeler z2-when remove). « - » sinon.
# Le texte vient en dernier pour pouvoir contenir n’importe quoi sauf une TAB.""",
            "macro.cParse" to """# Lire 1 à 3 mots et décider :
#   KIND ... once|repeat   PLAN ... libellé à afficher   SPEC ... ce que prend z2-alarm/z2-when   USED ... mots utilisés""",
            "macro.pDaily" to "chaque jour ",
            "macro.pWeekday" to "jours ouvrés ",
            "macro.pWeekly" to "chaque semaine ",
            "macro.pMonthly" to "chaque mois le ",
            "macro.pYearly" to "chaque année ",
            "macro.mDay" to "",
            "macro.eDaily" to "quotidien",
            "macro.eWeekly" to "hebdomadaire",
            "macro.eMonthly" to "mensuel",
            "macro.eYearly" to "annuel",
            "macro.mBadDate" to "cette date n’existe pas (ex. 07/30) :",
            "macro.cEvery" to """  # La forme courte « every ... ». **La forme du mot suivant** choisit daily/weekly/monthly/yearly :
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ Le nombre de mots ne change pas (un mot remplacé par un autre), USED reste donc juste.""",
            "macro.cExpand" to "# Décider quelle récurrence « every » voulait dire, d’après le mot qui le suit.",
            "macro.cDigits" to """    # Formes tout en chiffres : 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Vérifier 12 chiffres avant 8 (le motif à 8 chiffres correspond aussi à une chaîne de 12).""",
            "macro.cYmd" to """  # Écrit comme une date. ⚠ z2-alarm ne prend pas de date, cela devient donc aussi un délai.""",
            "macro.cCivil" to """# Date civile -> jours depuis 1970-01-01 (années bissextiles comprises ; days_from_civil de Howard Hinnant).
# ⚠ date -d "2026-07-30" n’est pas fiable sous busybox, le calcul se fait donc ici.
# ⚠ Seule la différence de jours est utilisée et l’heure est laissée à [day_epoch], ce qui annule le fuseau.""",
            "macro.cCron" to """# Construire une expression cron (min heure jour-du-mois mois jour-de-la-semaine) à partir de HH:MM et d’un champ de jour.
# N’utilisez PAS expr pour retirer un zéro initial : il sort avec 1 quand le résultat vaut 0, le côté droit
# de `expr "${d}m" + 0 || echo "${d}m"` s’exécute donc aussi et la valeur fait deux lignes.""",
            "macro.afterLabel" to """# "30m" -> "dans 30m (14:35)". Montrer l’heure permet de vérifier tout de suite.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "dans ${d}num${d}u (${d}at)" || echo "dans ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "utilisation : remind.sh <quand> <texte>",
            "macro.mBadWhen" to "impossible de lire l’heure (essayez : 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00) :",
            "macro.mBadTime" to "écrivez l’heure au format HH:MM :",
            "macro.mBadRange" to "heure hors limites (00:00-23:59) :",
            "macro.mNoTime" to "aucune heure indiquée (ex. daily 07:00) :",
            "macro.mBadDow" to "jour de la semaine inconnu :",
            "macro.mPastTime" to "cette heure est déjà passée :",
            "macro.cDays" to """    # Forme par jours. Sans heure, garder **l’heure actuelle de la journée** (ne jamais inventer de valeur par défaut).
    # Elle peut aussi arriver en un seul mot, la forme collée est donc acceptée aussi.""",
            "macro.cDayEpoch" to """# Transformer "HH:MM, dans N jours" en secondes epoch. ⚠ busybox n’a pas date -d "tomorrow",
# on calcule donc d’abord minuit aujourd’hui puis on ajoute les jours et l’heure.
# ⚠ Une valeur avec un zéro initial ("08") est lue en octal par ${d}(()), on le retire donc.
# ⚠ Un jour est ajouté comme 86400s, cela peut donc être décalé d’une heure un jour de changement d’heure.""",
            "macro.cFmtAt" to """# Le libellé affiché dans la liste. ⚠ Garder "tomorrow" deviendrait faux une fois la date passée.""",
            "macro.mNoBody" to "dites de quoi vous rappeler",
            "macro.mNoAlarm" to "impossible de le programmer",
            "macro.mNone" to "rien de programmé",
            "macro.mFired" to " (déclenché)",
            "macro.mRemoved" to "retiré :",
            "macro.mNoSuchNum" to "entrée introuvable (vérifiez remind.sh list) :",
            "macro.mUsageDel" to "utilisation : remind.sh del <n|all>",
            "macro.bDone" to "Fait",
            "macro.bDelete" to "Supprimer",
            "macro.mAskDel" to "Retirer lequel ?",
            "macro.mAskDelH" to "numéro / all",
            "macro.mDeletedTitle" to "🗑 Retiré",
            "macro.cPeek" to """# Montrer la liste dans une notification (depuis la tuile « liste »). **Garder les numéros** — c’est par eux
# qu’on désigne celui à retirer. ⚠ Lister sans moyen de retirer était la plainte ; d’où le bouton.""",
            "macro.cAskDel" to """# Le bouton « supprimer » : demander un numéro et le passer à [cmd_del] (le même chemin que la CLI).""",
            "macro.bS1" to "+10min",
            "macro.bS2" to "+1h",
            "macro.mAgain" to "- à nouveau dans",
            "macro.mTitle" to "⏰ Rappels",
            "macro.mAsk1" to "Vous rappeler de quoi ?",
            "macro.mAsk1H" to "ex. prendre les médicaments",
            "macro.mAsk2" to "Quand ?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "saisissez-le à nouveau, s’il vous plaît",
            "macro.mAskGiveUp" to "Impossible de le lire ${ASK_TRIES} fois. Vous pouvez aussi l’ajouter depuis le terminal : remind.sh 30m prendre les médicaments",
            "macro.mOkTitle" to "⏰ Rappel programmé",
            "macro.mNgTitle" to "⚠ Non programmé",
            "macro.cHooks" to """  # Une accroche pour « un rappel s’est déclenché », une pour « un bouton de notification a été touché ». Seulement
  # ces deux, quel que soit le nombre de rappels. Aucune ne dépend des interrupteurs de détection.""",
            "macro.cTiles" to """  # Ne remplir que les emplacements vides et ceux déjà à nous (ne jamais écraser ceux des autres).
  # Un nom de macro avec arguments fonctionne à partir de 0.8.275 (avant, écrire sh + chemin complet).""",
            "macro.mSetupHooks" to "accroches enregistrées :",
            "macro.mSetupTiles" to "tuiles :",
            "macro.mPathHint" to "Remarque : cet onglet ne peut pas encore lancer remind.sh par son nom. Ouvrez un nouvel onglet, ou exécutez " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH ici.",
            "macro.cPathHint" to """  # ⚠ Le dossier des macros n’est dans le PATH que depuis 0.8.287, **les onglets ouverts avant**
  # ont donc encore l’ancien. Vérifier si le nom est trouvé et dire quoi faire sinon
  # (sinon `command not found` n’a pas de raison visible).""",
            "macro.mPlace" to "Remarque : vous devez encore placer les tuiles vous-même, depuis l’écran de modification (crayon) des réglages rapides.",
            "macro.lRemind" to "rappeler",
            "macro.lList" to "liste",
            "macro.cSelf" to "# Ne réagir qu’à nos propres notifications (nos id commencent toujours par r)",
            "macro.cKeepRepeat" to "      # Ne pas supprimer ici un rappel répété : il doit se déclencher à nouveau demain.",
            "macro.cAsk" to """# Le chemin utilisé depuis la tuile / une notification. ⚠ **Toujours répondre par une notification** :
# personne ne regarde un terminal ici, échouer sur stderr se lit donc comme « j’ai appuyé et
# rien ne s’est passé » (si — la raison n’est arrivée que dans le run.log de la tuile).
#   Illisible  -> dire pourquoi et redemander, jusqu’à $ASK_TRIES fois (la réponse précédente est gardée)
#   Programmé  -> montrer le plan et le texte dans une notification""",
            "macro.cSnoozeCancel" to "abandonner l’alarme de report si le rappel a été reporté avant d’être fait",
            "macro.qrBody.head" to """
#
# Utilisation :
#   qr.sh "https://example.com"       encode une chaîne et la dessine ici
#   qr.sh -f notes.txt                encode le contenu d’un fichier
#   z2-clip get | qr.sh               encode ce qu’il y a dans le presse-papiers
#   qr.sh -o ~/qr.png "text"          enregistre un PNG (rien n’est dessiné)
#   qr.sh -t "text"                   affiche des blocs au lieu d’une image
#   qr.sh -s 24 "text"                choisit la taille (en colonnes ; par défaut ça tient dans la largeur)
#
# z2-run: qr.sh "https://example.com"   (une fois installé, le nom seul suffit. S’exécute une fois)
#
# # Prérequis (sans eux ça ne marche pas, ou ça a un autre aspect)
#
#   1) L’encodage est fait par l’appli ... dans un onglet de z2term il n’y a rien
#      à installer (z2-qr encode). Si qrencode est déjà là, il est utilisé à la place.
#      Là où l’appli est hors de portée (par ssh), installez-le une fois par onglet (distribution) :
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Sans aucun des deux, la commande d’installation est affichée et le script s’arrête.
#
#   2) L’image (par défaut) n’apparaît que dans un onglet de cette appli ... elle est dessinée avec
#      les graphismes Kitty. Par ssh, ou dans un terminal qui n’affiche pas d’images, vous obtenez
#      un flot de charabia à la place. Utilisez -t là.
#
#   3) -t (blocs) dépend de la police du terminal ... les polices qui laissent des espaces entre
#      les lignes restent lisibles à l’œil mais pas pour une caméra. Pour le scanner avec une caméra,
#      préférez l’image par défaut ou le PNG de -o.
#
#   4) Les proportions sont supposées ... voir « Proportions » ci-dessous. Écrasé ? Z2_QR_ASPECT.
#
# Options :
#   -f FILE   lit l’entrée depuis un fichier (sinon les arguments, sinon stdin)
#   -o PNG    enregistre un PNG au lieu de dessiner. Plusieurs codes deviennent
#             qr-1.png / qr-2.png ...
#   -t        affiche des blocs. Pour les terminaux sans images, et par ssh
#   -s N      taille en colonnes au dessin. Par défaut jusqu’à 34, limitée par la largeur
#   -h        cette aide
#
# À propos des longues entrées :
#   Un code contient une quantité fixe (2953 octets alphanumériques au plus ; quelques centaines
#   s’il doit rester lisible). Une entrée de plus de 900 octets est coupée aux sauts de ligne
#   en plusieurs codes, numérotés [1/3] et affichés dans l’ordre.
#   AVERTISSEMENT une seule ligne trop longue pour tenir dans un code ne peut pas du tout être encodée.
#
# Proportions :
#   Les terminaux ne donnent pas la taille de leurs cellules, une proportion de caractère 1:2 est donc supposée
#   pour rendre le code carré. S’il paraît écrasé, ajustez avec une variable d’environnement :
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (plus petit = plus haut. 0.5 par défaut)
""",
            "macro.cMaxBytes" to "octets maximum par code (coupé au-delà)",
            "macro.cPngModule" to "points par module dans un PNG enregistré",
            "macro.cTargetPx" to "largeur visée en pixels au dessin",
            "macro.cMargin" to "zone de silence autour du code (modules ; nécessaire pour scanner)",
            "macro.cUsageFn" to "Afficher le bloc de commentaires du début comme aide (sans nombre de lignes fixe).\n" +
            "# Les lignes vides ne l’arrêtent pas (NF décide), les sections séparées par des lignes vides restent donc entières.",
            "macro.cNeedEncoder" to "Prérequis 1 : de quoi encoder. qrencode s’il est là, sinon l’appli.",
            "macro.mMissing" to "qr.sh : rien pour encoder ici - pas de qrencode, et l’appli est hors de portée",
            "macro.mInstallOnce" to "  Installez-le une fois dans cet onglet :",
            "macro.mAnyPm" to "    Arch : pacman -S qrencode / Ubuntu, Kali : apt install qrencode",
            "macro.cMakeFns" to "Faire le PNG. qrencode choisit la taille de module par sondage ; l’appli prend une cible.",
            "macro.cMakeBlocks" to "Dessiner avec des caractères de bloc. Les deux chemins émettent leurs propres couleurs, tout thème convient donc.",
            "macro.qrBody.mNoTmp" to "impossible de créer un dossier de travail",
            "macro.qrBody.mUnreadable" to "illisible :",
            "macro.qrBody.mEmpty" to "entrée vide",
            "macro.mSizeNum" to "-s prend un nombre",
            "macro.qrBody.mUsageHint" to "voir qr.sh -h",
            "macro.mPieceFail" to "qr.sh : impossible de construire le code %d : %s",
            "macro.qrBody.cCollect" to "---- rassembler l’entrée ----",
            "macro.cSplit" to "---- couper aux sauts de ligne si trop long ----",
            "macro.cSplit2" to "Passer au morceau suivant juste avant que le total courant dépasse MAX_BYTES. Les lignes ne sont\n# jamais coupées au milieu, un texte multioctet survit donc.",
            "macro.cTrim" to "Retirer le saut de ligne final (ne jamais encoder un saut de ligne que l’entrée n’avait pas).",
            "macro.cSize" to "---- choisir la taille d’affichage d’après la largeur du terminal ----",
            "macro.cPngWidth" to "Renvoyer la largeur en pixels d’un PNG (octets 16-19, l’IHDR).",
            "macro.cModulePx" to "Retrouver les points par module à partir de la largeur visée en pixels.",
            "macro.cInline" to "Dessiner un PNG sur place avec le protocole graphique Kitty.\n# c/r fixent combien de cellules il occupe, puis afficher autant de sauts de ligne pour placer le curseur dessous.",
            "macro.cEmit" to "---- émettre ----",
            "macro.cAnsi" to "ANSIUTF8 émet ses propres couleurs, clair/sombre est donc juste quel que soit le thème.",
            "macro.mdBody.head" to """
#
# Utilisation :
#   md.sh README.md                l’afficher ici, dans ce terminal
#   md.sh -v README.md             l’ouvrir dans l’appli (z2-view)
#   cat notes.md | md.sh           lire depuis l’entrée standard
#   md.sh -o page.html README.md   écrire le HTML et s’arrêter (rien ne s’ouvre)
#
# z2-run: md.sh README.md   (une fois installé, le nom seul suffit. S’exécute une fois)
#
# Options :
#   -v        l’ouvrir dans l’appli (z2-view)
#   -o FILE   y écrire le HTML. Rien n’est affiché ici
#   -w N      couper à N colonnes (par défaut : la largeur de l’écran, jusqu’à 100)
#   -p        sans couleur ni décoration (texte brut)
#   -t TITLE  titre de l’écran de lecture (par défaut : le premier titre, sinon le nom du fichier)
#   -h        cette aide
#
# Un long document, écran par écran : md.sh README.md | less -R   (-R garde les couleurs)
#
# Rien à installer : sh et awk suffisent.
# Titres, paragraphes, listes (imbriquées, numérotées, [ ]/[x]), citations, code, tableaux,
# séparateurs, liens, images et front matter (---) sont gérés. AVERTISSEMENT les titres setext
# (=== / ---) et les liens de référence ([texte][1]) sortent tels qu’écrits.
#
# À quoi s’attendre :
#   - Les images n’apparaissent sur place que dans un onglet de cette appli (dessinées avec les graphismes
#     kitty via z2-img). Par ssh vous obtenez la légende et le chemin à la place.
#   - Les liens peuvent être touchés (OSC 8). Un terminal sans cela les montre en texte.
#   - Ce terminal ne dessine pas l’italique, l’*emphase* est donc soulignée (comme le fait man).
#   - Un tableau trop large pour l’écran est découpé en lignes « titre : valeur ».
#   - -v intègre à la page les images présentes sur cet appareil (1,5 Mo chacune au plus).
#     L’écran de lecture ne va rien chercher sur le réseau, les images http(s)
#     restent donc sous forme de légende.
""",
            "macro.mdBody.cCollect" to "# Rassembler l’entrée (plusieurs fichiers deviennent une seule lecture, dans l’ordre donné).",
            "macro.cTitle" to "# Titre : -t, sinon le premier titre #, sinon le nom du fichier",
            "macro.cWidth" to "# La largeur de l’écran",
            "macro.cColorEnv" to "# Une échappatoire quand les couleurs doivent (ou non) passer par un pipe, ex. vers un paginateur",
            "macro.cImgWhere" to "# Une image ne peut apparaître sur place que dans un onglet (graphismes kitty) avec z2-img présent.",
            "macro.cAmbi" to "# Avec quelle largeur compter les caractères ambigus (traits, flèches, chiffres cerclés). Utilisez\n" +
            "# Z2_MD_AMBIWIDTH=2 si les Paramètres les comptent en pleine largeur. 1 par défaut, comme l’appli.",
            "macro.cAwkFile" to "# AVERTISSEMENT le programme awk est écrit dans un FICHIER et passé avec -f. Passé en\n" +
            "#   argument, gawk coupe le programme là où un caractère multioctet (comme le\n" +
            "#   japonais de ces notes) chevauche sa limite de lecture de 4096 octets, et meurt avec\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Intégrer les images à la page de l’écran de lecture, en URI data:.\n" +
            "# AVERTISSEMENT z2-view ne va rien chercher sur le réseau (JavaScript et chargements coupés), une\n" +
            "#   image non intégrée n’apparaît donc tout simplement pas. 4 Mo pour toute la page.",
            "macro.cImgLoop" to "# Seul un document avec des images est lu ligne par ligne (pour appeler z2-img sur place)",
            "macro.mdBody.mUsageHint" to "voir md.sh -h",
            "macro.mdBody.mNoTmp" to "impossible de créer un dossier temporaire",
            "macro.mdBody.mUnreadable" to "fichier illisible :",
            "macro.mdBody.mEmpty" to "rien à lire",
            "macro.mWidthNum" to "-w prend un nombre",
            "macro.mNoDir" to "impossible de créer le dossier où le mettre",
            "macro.mConvFail" to "la conversion a échoué",
            "macro.mNoView" to "pas de z2-view ici (l’appli doit être mise à jour). Le HTML est dans :",
            "macro.mImgLabel" to "[image]",
            "menu.noApps" to "(aucune application trouvée)",
            "menu.cat.AudioVideo" to "Son et vidéo",
            "menu.cat.Development" to "Développement",
            "menu.cat.Graphics" to "Graphisme",
            "menu.cat.Game" to "Jeux",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Bureautique",
            "menu.cat.System" to "Système",
            "menu.cat.Utility" to "Utilitaires",
            "menu.cat.Other" to "Autres",
            "menu.usage" to "utilisation : z2menu [list]   (sans argument : XML du menu pipe d’openbox)",
            "reminderView.labels" to "Rappels|Ajouter|Supprimer|Contenu|Date et heure|Répétition|Une fois|Chaque jour|Jours ouvrés|Chaque semaine (jour choisi)|Texte de programmation (facultatif ; remplace la date et la répétition)|Programmés|Répétés|Signalés|Aucun rappel|Supprimer ce rappel et ses notifications en attente ?",
            "rssFeeds.rssFeedsLabel" to "Abonnements",
            "run.errMsg" to "z2run : Xvnc :${d}{DISPLAY_NUM} n’a pas démarré. Voir /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan : aucun gestionnaire de paquets compatible (apk/apt-get/pacman) trouvé.",
            "scan.mInstalling" to "z2scan : installation de",
            "scan.mInstallFail" to "z2scan : échec de l’installation de",
            "scan.mRemoteBlocked" to "z2scan : cible distante refusée. Vérifiez seulement localhost, ou passez --allow-remote pour une cible que vous êtes autorisé à tester.",
            "scan.mRemoteWarn" to "z2scan : AVERTISSEMENT analyse d’un hôte distant. N’analysez que des systèmes que vous êtes explicitement autorisé à tester.",
            "scan.mNoLynis" to "z2scan : lynis introuvable ; repli sur l’autodiagnostic intégré (« z2scan self »). Installez-le avec « z2scan setup ».",
            "scan.mNoCve" to "z2scan : aucun scanner de CVE (trivy/grype) trouvé. Installez trivy ou grype, puis réessayez.",
            "scan.mSelfHead" to "== autodiagnostic z2scan (cet appareil / localhost) ==",
            "scan.mUidRoot" to "tourne en uid 0 (remarque : proot/z2root fournit un faux root)",
            "scan.mPubPort" to "port TCP à l’écoute sur toutes les interfaces (pas seulement localhost), port",
            "scan.mSshdEmpty" to "sshd_config autorise la connexion avec mot de passe vide (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config active l’authentification par mot de passe (PasswordAuthentication yes) ; la clé est plus sûre",
            "scan.mSshdRoot" to "sshd_config autorise la connexion root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys a des droits trop larges, mode",
            "scan.mSshDir" to "~/.ssh a des droits trop larges, mode",
            "scan.mWorldWrite" to "fichiers modifiables par tous trouvés (jusqu’à 20 affichés) :",
            "scan.mSuid" to "binaires SUID présents (pour info ; faux root sous proot/z2root) :",
            "scan.mPath" to "le PATH contient un élément vide/« . » (le dossier courant dans le PATH est risqué)",
            "scan.mFound" to "constats :",
            "scan.mClean" to "aucun problème évident trouvé.",
            "scan.mSaved" to "z2scan : référence enregistrée :",
            "scan.mSaveFail" to "z2scan : impossible d’écrire la référence :",
            "scan.mNoBase" to "z2scan : pas encore de référence. Exécutez une fois « z2scan self --save » pour noter l’état actuel.",
            "scan.mNoChange" to "[ OK ] aucun changement depuis la référence.",
            "scan.mAdded" to "== nouveau depuis la référence ==",
            "scan.mRemoved" to "== disparu depuis la référence ==",
            "scan.mCleared" to "z2scan : référence effacée.",
            "scan.mLangDiff" to "z2scan : AVERTISSEMENT la référence a été enregistrée dans une autre langue, tout va donc paraître changé. Réenregistrez avec « z2scan self --save ».",
            "scan.langTag" to "fr",
            "scan.usageText" to """
        z2scan - test de vulnérabilités de cet appareil / localhost (défensif, aucune donnée ne sort).

        N’analyse que localhost par défaut. Les scanners sont installés depuis les paquets officiels
        de votre distribution (nmap/lynis/trivy) ; rien n’est inclus et les résultats restent locaux.

          z2scan self [--save]         autodiagnostic intégré (sans outil externe) : ports ouverts,
                                       config sshd, droits des clés SSH, modifiable par tous/SUID, PATH.
                                       --save enregistre aussi le résultat comme référence.
          z2scan diff [--quiet]        relance l’autodiagnostic et n’affiche que ce qui a changé
                                       depuis la référence. Sort avec 1 quand il y a du nouveau,
                                       cela s’intègre donc directement à z2-when. --quiet n’affiche
                                       rien du tout quand rien n’a changé.
          z2scan baseline [clear]      affiche la référence enregistrée (ou la supprime)
          z2scan setup                 installe les scanners (nmap, lynis) via apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       analyse TCP nmap. La cible par défaut est 127.0.0.1.
                                       Une cible non locale exige --allow-remote.
          z2scan host                  audit de l’hôte via lynis (repli sur « self » s’il est absent)
          z2scan cve                   analyse des CVE connues du rootfs via trivy/grype s’ils sont présents

        N’analysez que des systèmes que vous êtes explicitement autorisé à tester.
    """.trimIndent(),
            "translationMacro.description" to "Traduit un texte avec une commande installée à part.",
            "translationMacro.missing" to "La commande trans n’est pas installée dans cet environnement local. Installez vous-même votre outil de traduction, puis réessayez. Rien n’est téléchargé automatiquement.",
            "translationMacro.network" to "Le texte à traduire est envoyé au service externe utilisé par votre outil. Ses conditions et la disponibilité du réseau s’appliquent.",
            "translationSetup.setup" to "Ouvrez un terminal dans le même environnement Linux local, exécutez vous-même la commande ci-dessous, puis réessayez la traduction.",
            "translationSetup.alpineRepo" to "Si le paquet est introuvable, activez le dépôt community de la même version d’Alpine dans /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Si le paquet est introuvable sous Ubuntu, activez son dépôt universe et réessayez.",
            "translationSetup.unknown" to "Distribution non reconnue. Choisissez la commande de votre environnement Linux local :",
            "voice.head" to """# voice.sh — le récepteur de la « Commande vocale » : ce que vous dites pilote musique, volume, minuteurs, rappels, météo et plus.
#
# Préparation (une fois) :
#   sh ~/.z2term/macros/voice.sh setup    enregistre 4 règles (commande vocale / commande vocale verrouillé / minuteur vocal / compter les pas)
#   Compter les pas ne fait que garder le podomètre actif pour « combien de pas aujourd’hui » ; il n’exécute jamais rien
#   puis parlez via la tuile « Commande vocale » des réglages rapides ou l’assistant numérique (appui long sur le bouton marche etc.)
#
# Depuis un terminal :
#   voice.sh words                 ouvre la table de mots (~/.z2term/voice/words-<lang>.txt ; ajoutez ou changez des phrases)
#   voice.sh try <phrase>          en essaie une sans parler (ex. voice.sh try chanson suivante)
#   voice.sh listen                ouvre l’écran d’écoute et parlez (comme la tuile)
#   voice.sh lang [langue]         la langue de la table de mots (voice.sh lang - les liste)
#   voice.sh ai [off|codex|claude|agy]   utiliser l’IA ou non (désactivée au départ)
#   voice.sh model [nom]           le modèle de l’IA (vide = celui par défaut de cette IA)
#   voice.sh setup / help
#
# L’IA est désactivée au départ. Même activée, elle n’est sollicitée que si vous commencez par le mot-signal (« IA », « question » ...).
# La météo n’interroge wttr.in que si vous dites « météo » (lieu : weather_city= dans ~/.z2term/voice/config).
# Vos propres mots : « mots = commande » dans ~/.z2term/voice/commands.txt (vus avant la table de mots)
# Noms à appeler : « nom numéro » dans ~/.z2term/voice/contacts.txt / journal : ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — macro de démarrage. Réagit à ce qui se passe sur l’appareil.",
                "# L’appli (z2-when) se charge d’attendre, ceci n’est donc pas résident : il s’exécute une fois et s’arrête.",
                "# Préparation : Paramètres -> \"Détection des événements système\" activée",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (casques : une seconde règle avec 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Ce qui s’est passé arrive dans Z2_WHEN_EVENT (z2-when events liste les noms).",
                "# Pour essayer à la main :  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT est vide. Pour essayer à la main : Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"La charge a commencé\" ;;",
                "  power_disconnected) z2-toast \"La charge s’est arrêtée\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — prévient quand la batterie est faible, en s’adaptant à l’état actuel.",
                "# Utilise z2-state pour savoir si l’écran est allumé : toast si oui, notification sinon.",
                "# Préparation : Paramètres -> \"Détection des événements système\" activée",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when transmet le niveau ; sinon z2-state, pour que cela marche aussi à la main.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Ne rien dire pendant la charge (elle ne se vide pas vraiment)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Batterie ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Batterie faible\" \"Il reste ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — lit à voix haute la batterie et la connexion chaque matin.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# L’alarme du système le réveille, il se déclenche donc aussi en Doze (peut avoir quelques minutes de retard).",
                "# Ne dépend pas des interrupteurs de détection.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=mobile; fi",
                "z2-say \"Bonjour. Batterie ${d}{level} pour cent, réseau ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — copie un code à usage unique depuis les notifications, puis l’efface après",
                "# TTL secondes si le presse-papiers contient encore cette même valeur.",
                "# Préparation : Paramètres -> \"Détection des notifications\" activée + accorder l’accès aux notifications du système",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ peut masquer les codes dans les notifications sensibles. Pour un chemin fiable, utilisez",
                "#    la variante SMS (otp-sms.sh) — le texte des SMS n’est jamais masqué.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — copie un code à usage unique depuis les SMS reçus, puis l’efface après",
                "# TTL secondes si le presse-papiers contient encore cette même valeur.",
                "# La variante SMS de otp-clip.sh. Contrairement aux notifications, le texte des SMS n’est jamais",
                "# masqué par la protection des notifications sensibles (Android 15+) et marche même verrouillé.",
                "# Préparation : Paramètres -> \"Détection des SMS\" activée + accorder l’autorisation SMS du système",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — affiche une notification pour copier les numéros des notifications d’appel.",
                "# Son bouton \"Copier\" met le numéro dans le presse-papiers (pourquoi il attend un appui : voir plus bas).",
                "# Préparation : Paramètres -> \"Détection des notifications\" activée + accorder l’accès aux notifications du système",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Pour attraper aussi les appels manqués, enregistrez une seconde règle (même script, autre catégorie) :",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — relève des flux et ne garde que le nouveau, en notification et en texte.",
                "# Contrairement aux autres exemples, celui-ci ne reste **pas** résident : il est de la forme",
                "# « s’exécute une fois depuis un déclencheur horaire et s’arrête ».",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — ouvre ce que rss.sh a collecté, le plus récent d’abord, un article par appui.",
                "# Il apparaît comme bouton de macro sur le widget d’état, chaque appui ouvre donc le suivant.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — vous rappelle par une notification : une fois (dans 30m, à 18:30) ou",
                "# en répétition (chaque jour / jours ouvrés / chaque semaine). Se déclenche appli fermée (une alarme du système",
                "# le réveille ; aucun interrupteur de détection requis). Reportez depuis les boutons de la notification.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — transforme un texte ou un fichier en code QR : dessiné ici, ou enregistré en PNG.",
                "# Passe quelque chose à un autre appareil sans le retaper. S’exécute une fois, pas résident.",
            ),
            "macro.md" to listOf(
                "# md.sh — lit ici un fichier Markdown mis en forme, ou l’ouvre dans l’appli.",
                "# Titres, listes, tableaux, code, liens ; les images apparaissent sur place. S’exécute une fois, pas résident.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Un flux ou un mot par ligne qui ne doit pas être noyé (les lignes qui commencent par # sont ignorées).",
                "# Une partie d’URL ou une partie de titre marche. Chaque correspondance a sa propre notification.",
                "# ex.", "#example.org",
            ),
            "macro.usage" to listOf(
                "utilisation : z2-macro <sous-commande>",
                "  list                liste les exemples inclus (marqués nouveau / égal / diffère)",
                "  install <name|all>  copie dans ~/.z2term/macros/ (n’écrase jamais ; -f pour forcer)",
                "  remove <name>       supprime votre copie d’un exemple inclus (s’arrête s’il diffère ; -f pour forcer)",
                "  diff <name>         ce qui diffère entre votre copie et celle incluse (la vôtre à gauche)",
                "  show <name>         affiche le script",
                "  run <name>          l’exécute ici (Ctrl-C pour arrêter)",
                "  dir                 affiche où vivent les macros",
                "  actions ...         macros d’actions Android (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Abonnements", "Ajouter", "Articles", "Modifier", "Retirer", "URL du flux",
                "Retirer cet abonnement ? Les articles collectés seront conservés.",
                "Les changements s’appliquent au prochain relevé. Les articles collectés sont conservés.",
                "Aucun abonnement. Utilisez Ajouter pour saisir une URL RSS ou Atom.",
                "Saisissez une URL HTTP(S) sans espace ni identifiants (2048 caractères au plus).",
                "Cet abonnement existe déjà.",
                "Cet abonnement a changé. Actualisez la liste et réessayez.",
                "Une autre modification est en cours d’enregistrement. Réessayez.",
                "Précédent", "Suivant",
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

/** [Z2ApiMsg] の Français 版。上書きしていない文言は英語で出る。 */
private class ApiMsgFr(d: String) : Z2ApiMsg("fr", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — choisit un document ; le copie dans HOME et affiche son chemin.\n# z2-file save FILE [MIME] — choisit une destination et enregistre une copie.\n# Annuler compte comme un échec. Jusqu’à 512 Mio ; la sélection expire après 5 minutes."

    override val usbHelp: String get() = """
        |# z2-usb list              … liste les appareils USB branchés au téléphone
        |# z2-usb allow [numéro]    … demande à Android de laisser les programmes Linux utiliser un appareil
        |# Exécutez allow une fois après avoir branché un appareil. L’autorisation dure jusqu’au débranchement.
    """.trimMargin()

    override val usbUsage: String get() = "utilisation : z2-usb list | z2-usb allow [numéro|chemin|VID:PID]"

    override val usbNoDevices: String get() = "Aucun appareil USB n’est branché au téléphone."

    override val usbNeedSelector: String get() = "Plus d’un appareil USB est branché ; indiquez son numéro d’après z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Validez l’accès USB sur l’écran d’Android : $path"

    override fun usbAlreadyAllowed(path: String): String = "L’accès USB est déjà autorisé : $path"

    override fun usbNotFound(selector: String): String = "Appareil USB introuvable : $selector"

    override val usbStateAllowed: String get() = "autorisé"

    override val usbStateNeedsAllow: String get() = "à autoriser"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "titre" "texte"  /  z2-notify [-h] "texte"
        |#   -h / --high / --banner : l’afficher en bannière (prioritaire) en haut de l’écran
        |#   -b <libellé>           : ajoute un bouton de réponse (jusqu’à 3). En appuyer un ajoute
        |#                            notify_action à ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <nom>               : un identifiant de cette notification (pour distinguer les réponses)
        |#   -c <texte>             : ajoute un bouton « Copier » qui met <texte> dans le presse-papiers.
        |#                            À utiliser au lieu de z2-clip set quand la macro tourne en
        |#                            arrière-plan : Android 10+ ne laisse que l’appli au premier plan écrire dans
        |#                            le presse-papiers, un z2-clip set sans personne devant est donc ignoré.
    """.trimMargin()

    override val notifyUsage: String get() = "utilisation : z2-notify [-h] [-n nom] [-c texte] [-b libellé]... <titre> [texte]"

    override val toastHelp: String get() = """
        |# z2-toast <message> … un court message en bas de l’écran (un toast).
        |# Il s’efface seul et ne laisse rien. Utilisez z2-notify s’il doit rester,
        |# ou si vous voulez un bouton à appuyer.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … ouvre les outils QR avec l’appareil photo déjà en train de scanner.
        |# Après un scan, un bouton ouvre le contenu dans son appli (navigateur, LINE et autres applis,
        |# composeur, e-mail, SMS, Wi-Fi, contacts, agenda). Rien ne s’ouvre seul.
        |# Mettez-le sur une tuile des réglages rapides ou dans un panneau latéral.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … crée un code QR.
        |#   Rien à installer : l’appli encode (le même encodeur que l’écran QR).
        |#   Sans TEXT, il lit l’entrée standard. -o est où va le PNG
        |#   (par défaut ~/.z2term/qr/qr.png) et le chemin est affiché. -t le dessine ici à la place,
        |#   en caractères de bloc. -p est la taille visée en pixels (600 par défaut),
        |#   -m la zone de silence en modules (4 par défaut). Jusqu’à 2000 octets.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <texte> … confie un texte au menu de partage d’Android (l’envoyer à une autre appli).
        |# --file FILE [FILE ...] : envoie le contenu de fichiers (jusqu’à 32 fichiers, 512 Mio au total).
        |# Les chemins sont lus dans le shell local actuel. -- force le mode texte. Les copies de plus de 24 heures sont nettoyées au partage suivant.
        |# Tous les arguments forment un seul texte. L’appli de destination se choisit à l’écran,
        |# il faut donc quelqu’un — ce n’est pas pour une macro qui tourne seule.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [titre] … lit dans z2term une page faite ici.
        |# Pas de serveur ni de navigateur : le fichier est confié à l’appli et affiché tel quel.
        |# JavaScript et les chargements réseau sont coupés, rien sur la page ne peut donc s’exécuter ni appeler l’extérieur.
        |# Les liens http:// et https:// s’ouvrent dans votre navigateur habituel.
        |# Le thème du téléphone arrive en variables CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), une page écrite avec elles suit donc les couleurs du terminal.
        |# Jusqu’à 4 Mo. Les images doivent être intégrées (URI data:) ; celles distantes ne sont pas chargées.
        |# Utilisez --edge PANNEAU:ÉLÉMENT pour envoyer à un élément type=view existant d’un panneau latéral.
        |# Utilisez --controls FILE.json pour des formulaires et actions génériques gérés par une macro installée.
        |# Les éléments de vue s’actualisent manuellement par défaut. view-refresh=auto exécute run= à l’ouverture ; z2-view revient à cet élément.
        |# Utilisez - ou un pipe (ls -l | z2-view) pour lire l’entrée standard. Un texte qui ne commence pas par < s’affiche
        |# replié en police monospace, codes de couleur retirés ; --text / --html choisissent explicitement.
        |# Un pincement règle le texte ; --pinch page zoome plutôt toute la page. --text-size 50..300 fixe la
        |# taille du texte (pour cent) de cette page. Les deux valent pour le plein écran ; un élément de panneau a ses propres réglages.
        |# --no-refresh / --no-find masquent les boutons Actualiser / Rechercher du plein écran (affichés par défaut).
    """.trimMargin()

    override val viewUsage: String get() = "utilisation : z2-view [--edge PANNEAU:ÉLÉMENT] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <fichier|-> [titre]"

    override val viewUnreadable: String get() = "z2-view : fichier illisible :"

    override val openHelp: String get() = """
        |# z2-open <url|chemin> … l’ouvre avec l’appli par défaut (https://… ou /sdcard/…).
        |# L’appli qui l’ouvre est le choix d’Android ; z2term ne fait que transmettre.
        |# Un chemin est un chemin sur le **téléphone** (/sdcard/…), pas dans la distribution.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <fichier|->… … dessine l’image dans le terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Passez - pour lire une image depuis stdin.
        |#   -w <cols>  largeur en colonnes du terminal (par défaut : tenir dans le terminal)
        |#   -r <rows>  hauteur en lignes du terminal (avec -w, la taille est utilisée telle quelle)
        |#   --clear    retire toutes les images dessinées jusqu’ici
        |#   -f         envoie même quand stdout n’est pas un terminal
        |# ⚠ Les images n’apparaissent que dans un onglet z2term, ou dans un terminal qui parle le protocole
        |#   graphique kitty. Ailleurs (par ssh, dans un paginateur) vous obtenez du charabia.
        |# ⚠ Les proportions supposent qu’une cellule est deux fois plus haute que large. Si l’image
        |#   paraît écrasée, ajustez : Z2_IMG_ASPECT=0.45 z2-img photo.jpg (plus petit = plus haut).
    """.trimMargin()

    override val imgUsage: String get() = "utilisation : z2-img [-w cols] [-r rows] [--clear] [-f] <fichier|->..."

    override val imgNoFile: String get() = "impossible de lire le fichier :"

    override val imgNotTty: String get() = "stdout n’est pas un terminal, rien n’a donc été envoyé (-f l’envoie quand même)."

    override val imgNoSize: String get() = "impossible de lire la taille en pixels ; dessin en carré :"

    override val qrUsage: String get() = "utilisation : z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr : impossible de créer le code (le texte est-il trop long ? jusqu’à 2000 octets)"

    override val clipHelp: String get() = """
        |# z2-clip get        … affiche le presse-papiers sur stdout
        |# z2-clip set [text] … met le texte (ou stdin s’il est omis) dans le presse-papiers
        |# ⚠ Android 10+ ne laisse que l’appli au premier plan (ou le mode de saisie utilisé) toucher le
        |#   presse-papiers. Depuis une macro en arrière-plan, c’est ignoré sans un mot,
        |#   utilisez donc z2-notify -c <texte> : il ajoute un bouton « Copier » qui marche toujours.
    """.trimMargin()

    override val batteryHelp: String get() = "# Affiche le niveau / l’état de charge en JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (200ms par défaut)"

    override val editHelp: String get() = "# z2-edit <fichier>   … modifie un fichier texte dans l’éditeur de z2term (simple zone de texte ; Enregistrer le réécrit)"

    override val sayHelp: String get() = "# z2-say <texte>      … parle avec le TTS de l’appareil (lit stdin sans argument)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (toggle par défaut). status lit l’état d’Android, y compris les changements extérieurs (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … capture seulement la partie entourée (free par défaut). Nécessite Android 11 et z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (playpause par défaut)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Volume multimédia. Affiche l’actuel/max obtenu."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (light par défaut). Lit une mesure et renvoie du JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Lance n’importe quel Intent Android (startActivity par défaut). Un premier argument qui n’est pas une option est l’ACTION.
        |# La fenêtre libre réutilise par défaut la tâche et les limites du système. --reuse-task / --os-bounds restent acceptés.
        |# --bounds-only demande des limites de 80 % sans clé de mode ; ne se combine pas avec --os-bounds.
        |# --no-scale désactive pour ce lancement la demande de fenêtre libre réduite de Motorola.
        |# -p PKG seul lance cette appli. --window full|freeform|split|ask choisit la demande de lancement (prise en charge du système nécessaire).
        |# --profile N ouvre l’appli (-p/-n) dans un autre profil, comme le profil professionnel. Pas d’écran partagé. N figure dans la commande que le panneau latéral enregistre pour cette appli.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … l’état actuel de l’appareil, complet, en JSON
        |# z2-state <clé>      … seulement cette valeur, brute (se glisse directement dans un test)
        |# clés : screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# ex. [ "${d}(z2-state charging)" = "true" ] && echo en charge
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <question>
        |#   Pose une question à la personne et affiche sa réponse sur stdout.
        |#   La question arrive en notification avec un **champ de réponse**, on peut donc
        |#   répondre depuis le volet sans ouvrir l’appli (une macro en
        |#   arrière-plan peut aussi demander).
        |#   -t <sec>   combien de temps attendre une réponse (300 par défaut = 5 min)
        |#   -H <indic> libellé affiché dans le champ de réponse
        |#   -d <texte> réponse suggérée (affichée dans la notification)
        |# Écarter la notification sans répondre, ou dépasser le temps, sort avec un code non nul
        |# et n’affiche rien — vous pouvez donc écrire « ou abandonner » :
        |#   name=${d}(z2-ask "Nom de la branche ?") || exit 1
        |# Comparez avec z2-notify -b <libellé>, qui n’offre que les choix que vous avez préparés.
    """.trimMargin()

    override val askUsage: String get() = "utilisation : z2-ask [-t sec] [-H indication] [-d défaut] <question>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Affiche sur stdout ce que vous dites. La reconnaissance tourne **sur l’appareil seulement** ; votre voix
        |#   ne va jamais sur le réseau. Nécessite Android 12+ et la reconnaissance vocale sur l’appareil
        |#   (pour votre langue, ses données vocales hors ligne).
        |#   Parlez avec z2term à l’écran (Android ne donne le micro qu’à l’appli à l’écran).
        |#   Il s’arrête quand vous vous taisez. La première fois, l’accès au micro est demandé.
        |#   -w <mots>  n’accepter que ces mots (séparés par des virgules) et afficher celui entendu.
        |#               Toutes les hypothèses du reconnaisseur sont vérifiées, pas seulement la meilleure.
        |#   -l <langue> langue à reconnaître (ex. fr-FR, en-US ; par défaut : la langue de l’appareil)
        |# Sortie : 0 = fait / 1 = rien entendu, ou aucun des mots / 2 = indisponible (raison sur stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "utilisation : z2-listen [-w mot,mot,...] [-l langue]"

    override val listenPrompt: String get() = "Parlez maintenant… (s’arrête quand vous vous taisez)"

    override val listenNeedsAndroid12: String get() = "z2-listen nécessite Android 12 ou plus récent (avant, la reconnaissance ne peut pas rester sur l’appareil)."

    override val listenBusy: String get() = "Déjà en écoute (un autre z2-listen tourne)."

    override val listenEngineBusy: String get() = "La reconnaissance vocale de l’appareil est occupée (une autre appli l’utilise peut-être). Réessayez plus tard."

    override val listenEngineStuck: String get() = "La reconnaissance vocale ne répond pas. Si cela continue, forcez l’arrêt de l’appli de reconnaissance de l’appareil (comme Android System Intelligence) dans Paramètres Android › Applis."

    override val listenStopped: String get() = "Écoute arrêtée."

    override val listenNoOnDevice: String get() = "Cet appareil n’a pas de reconnaissance vocale intégrée. z2-listen n’envoie jamais votre voix sur le réseau, il ne peut donc pas tourner ici."

    override val listenNoPermission: String get() = "L’accès au micro n’est pas autorisé. Relancez z2-listen avec z2term à l’écran pour qu’on vous le demande, ou autorisez-le dans les paramètres Android."

    override val listenNotShown: String get() = "Mettez z2term à l’écran et réessayez (Android ne donne le micro qu’à l’appli à l’écran)."

    override val listenNoMic: String get() = "Impossible d’utiliser le micro (z2term a quitté l’écran, ou une autre appli enregistre)."

    override val listenNoLanguage: String get() = "Les données vocales de cette langue sur l’appareil ne sont pas installées. Téléchargez-les dans les réglages de saisie vocale de l’appareil (reconnaissance vocale hors ligne)."

    override val listenNoSpeech: String get() = "Rien n’a été entendu."

    override val listenTimedOut: String get() = "Délai dépassé."

    override fun listenNoMatch(heard: String): String = "Aucun des mots (entendu : $heard)"

    override fun listenFailed(code: Int): String = "La reconnaissance vocale a échoué (erreur $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … empêche l’écran de s’éteindre seul pendant ce temps
        |# z2-screen keepon off          … remet tout de suite, sans attendre l’échéance
        |# z2-screen status              … état actuel en JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Ceci change le réglage « mise en veille de l’écran » de tout le système, cela tient donc même appli
        |# en arrière-plan. Ce n’est PAS le maintien d’écran de la barre d’outils, qui ne dure que tant que
        |# l’appli est à l’écran — celui-là n’est pas touché.
        |# La valeur d’origine est gardée et toujours réécrite à l’échéance (même si l’appli
        |# est tuée ou l’appareil redémarre). 24h au maximum d’un coup.
        |# Nécessite « modifier les paramètres système » (Paramètres > mise en veille de l’écran > autoriser).
        |# ex. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "utilisation : z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | commande...> [--off <commande...>] [-l <libellé>] [-i <dessin>]
        |#                           … met quelque chose sur la tuile des réglages rapides 1-$tiles
        |# z2-tile add <1-$tiles>         … demande à placer cet emplacement dans le volet (Android 13+)
        |# z2-tile list              … les $tiles emplacements en TSV (emplacement / libellé / commande ; « - » = vide)
        |# z2-tile clear <1-$tiles|all>   … vide un emplacement
        |#
        |# Ce que vous attribuez
        |#   Soit **le nom de fichier d’une macro** de ~/.z2term/macros/, soit **une commande** exécutée telle
        |#   que tapée — ce que c’est se déduit du nom.
        |#   Une macro peut prendre des arguments (« remind.sh ask ») : le premier mot décide si c’est
        |#   une macro, le reste lui est passé. Deux emplacements avec la même macro ont le même
        |#   libellé, donnez-leur donc -l.
        |#   La commande tourne avec Z2_TILE=<emplacement> (et Z2_TILE_MACRO pour une macro) dans l’environnement.
        |#   ⚠ Un nom finissant par .sh qui n’est pas dans ~/.z2term/macros/ est refusé ici — en tant que
        |#     commande il serait cherché dans le PATH (qui n’inclut pas le dossier des macros),
        |#     la tuile ne ferait donc rien du tout et le dirait seulement dans tile/run.log.
        |#
        |# Ce que fait un appui
        |#   Un appui lance, un autre arrête (comme les boutons du widget).
        |#   La tuile paraît « activée » pendant que ça tourne (la couleur est l’accent du système, pas la nôtre).
        |#   Avec --off vous avez **deux commandes** : l’appui alterne entre elles et la tuile
        |#   paraît « activée » tant que c’est allumé. Utilisez-le quand éteindre est une commande à part
        |#   (z2-torch on / off).
        |#   Les paires ordinaires mémorisent les bascules réussies. Les attributions directes de z2-torch suivent
        |#   ce qu’observe Android, y compris les changements extérieurs. z2-screen keepon suit son réglage réellement actif.
        |#
        |# Placer dans le volet
        |#   Sous Android 13+, `set` demande tout de suite si la tuile va dans le volet, et
        |#   ce dialogue porte **le nom et l’icône que vous venez d’attribuer**. Si vous dites non, rien n’est placé.
        |#   Il y a exactement $tiles emplacements : le nombre est fixé dans le manifeste et ne peut pas croître
        |#   à l’exécution. Les emplacements sans attribution n’apparaissent pas du tout dans l’écran de modification,
        |#   en avoir $tiles ne coûte donc rien.
        |#   ⚠ Cela n’apparaît que tant que z2term est au premier plan, et un seul à la fois — une
        |#     macro qui attribue deux emplacements de suite ne demandera que pour le premier. Utilisez
        |#     z2-tile add <emplacement> ensuite pour le reste.
        |#   ⚠ Placer une tuile reste votre décision — Android ne laisse pas une appli y mettre ses propres
        |#     tuiles. Depuis l’écran de modification (crayon) du volet des réglages rapides, cherchez
        |#     **z2term <emplacement>**.
        |#   ⚠ Cette liste montre le nom et l’icône du manifeste, pas ceux attribués (Android n’a
        |#     aucun moyen de les changer à l’exécution), z2-tile list vous dit donc quel numéro est lequel.
        |#
        |# Le dessin (-i)
        |#   Un emplacement reçoit seul une icône adaptée quand le nom la trahit (remind.sh -> une
        |#   horloge), et z2-icon la remplace par ce que vous voulez — ensuite on n’y touche plus.
        |#   -i fixe le dessin **avant** que ce dialogue apparaisse, une seule ligne décide donc
        |#   du nom et de l’icône ensemble (z2-icon sample liste les noms possibles).
        |#   ⚠ Un nom inconnu est refusé **avant toute attribution** — un emplacement défini mais
        |#     portant le mauvais dessin se remarque moins qu’un qui n’a jamais été défini.
        |#
        |# ex. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "sans veille"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l lampe
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin()

    override val tileUsage: String get() = "utilisation : z2-tile set <1-$tiles> <macro.sh|commande...> [--off <commande...>] [-l libellé] " +
                "[-i dessin] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile : ce dessin n’existe pas : $name (liste avec z2-icon sample). Rien n’a été attribué."

    override fun tileAddEmpty(n: Int): String = "z2-tile : l’emplacement $n n’a encore rien (attribuez-le d’abord : z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile : impossible de demander l’ajout de la tuile ici (nécessite Android 13, avec z2term au premier plan). " +
                "L’emplacement est attribué — placez-le depuis l’écran de modification (crayon) du volet des réglages rapides."

    override val tileAddAsked: String get() = "Android a été interrogé pour ajouter la tuile — répondez au dialogue. " +
                "(Rien n’est apparu ? Placez-la plutôt depuis l’écran de modification.)"

    override val iconHelp: String get() = """
        |# Remplace les icônes de la barre d’état et des tuiles des réglages rapides par votre propre dessin en pixels.
        |#
        |# Commencez ici
        |#   z2-icon pick 1     ... choisit un dessin dans une liste et le met sur la tuile 1
        |#   z2-icon list       ... ce que chaque emplacement a maintenant (« -p » montre aussi les dessins)
        |#
        |# Ce que veut dire <cible>
        |#   notify    l’icône de chaque notification de cette appli (celle de la barre d’état)
        |#   1-$tiles      un emplacement de tuile des réglages rapides - le même numéro que z2-tile
        |#
        |# Mettre un dessin
        |#   pick <cible>             les lister et choisir par numéro (le plus simple)
        |#   sample <cible> <nom>     quand vous connaissez déjà le nom
        |#   edit <cible>             dessiner, ou corriger l’actuel, dans ${d}EDITOR
        |#   set <cible> <fichier>    le lire depuis un fichier (« - » pour stdin)
        |#   auto <1-$tiles|all>          choisir à nouveau d’après ce que la tuile exécute
        |#   clear <cible|all>        revenir à l’icône intégrée
        |#
        |# Finesse de la grille
        |#   grid                     la taille des nouveaux dessins
        |#   grid <$grids>          la définir ($grid si vous ne la changez pas)
        |#   scale <cible> <$grids>   reporter le dessin de <cible> sur cette grille
        |#                            (en divisant par deux les marches diagonales au passage). **Cela donne
        |#                            de la place pour dessiner plus fin** - l’icône produite est
        |#                            lissée de toute façon, inutile de faire ceci pour cela.
        |#
        |# Garder vos dessins dans la liste
        |#   save <cible> <nom>       nommer ce qui est sur <cible> et l’ajouter à la liste
        |#   sample                   la liste au choix (numéro / nom /
        |#                            « builtin » = fourni, « mine » = enregistré par vous)
        |#   sample <nom>             montrer ce dessin
        |#   forget <nom>             retirer un des vôtres de la liste (ce que vous avez déjà
        |#                            mis sur une cible y reste)
        |#
        |# Comment dessiner
        |#   C’est une grille de caractères. « . » « » « 0 » « - » « _ » laissent un point vide et tout autre caractère
        |#   le remplit, utilisez donc celui que vous voyez le mieux. L’espace vide autour
        |#   du dessin est ignoré - il est centré, inutile de remplir chaque ligne
        |#   exactement. La grille est $grids (« z2-icon grid »).
        |#   ⚠ Une grille plus grande ne fait pas une icône plus grande. Remplissez la grille, sinon le dessin
        |#     sort plus petit que celui remplacé.
        |#
        |# Bon à savoir
        |#   ⚠ Seule la forme passe. Android peint ces icônes d’une seule couleur à lui
        |#     (les tuiles changent de couleur entre activé et désactivé), il n’y a donc pas de couleur à choisir.
        |#   ⚠ **Le contour est lissé en sortie.** Un dessin fait sur $grid points
        |#     sort quand même lisse sur une tuile (dessinée bien plus grande que la barre d’état),
        |#     la grille n’est donc pas un souci au quotidien. 48 / 64 servent quand **vous**
        |#     voulez dessiner plus fin. « show » affiche le dessin tel que vous l’avez fait ; la tuile est plus lisse.
        |#   ⚠ Une tuile reçoit seule un dessin quand le nom le trahit (« z2-tile set 1
        |#     remind.sh » y met une horloge). Ce que vous définissez ici l’emporte et n’est plus jamais touché
        |#     — « z2-icon auto 1 » rend cet emplacement au choix automatique.
        |#   ⚠ Deux icônes ne peuvent pas être changées : celle de l’écran de **modification** des réglages rapides (d’où
        |#     vous faites glisser les tuiles) et l’icône du lanceur. Android les fixe à l’installation.
        |#
        |# ex. z2-icon pick 1               choisir le dessin de l’emplacement 1
        |#      z2-icon edit 1              le redessiner vous-même
        |#      z2-icon save 1 my-face      nommer ce dessin et l’ajouter à la liste
        |#      z2-icon sample 3 my-face    mettre le même dessin sur l’emplacement 3
        |#      z2-icon grid 64             dessiner les nouveaux sur une grille 64x64 désormais
        |#      z2-icon scale 1 48          passer l’emplacement 1 sur une grille 48x48 pour dessiner plus fin
        |#      z2-icon list -p             vérifier ce qui est où, avec les dessins
        |#      z2-icon clear notify        remettre l’icône de notification
    """.trimMargin()

    override val iconUsage: String get() = "utilisation : z2-icon pick <notify|1-$tiles> | sample [nom|cible nom] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [fichier|-] | " +
                "save <notify|1-$tiles> <nom> | forget <nom> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "fichier introuvable :"

    override val iconEditUnchanged: String get() = "inchangé."

    override val iconPickPrompt: String get() = "numéro (ou nom), vide pour annuler : "

    override val iconPickCancelled: String get() = "annulé."

    override val iconNoEditor: String get() = "aucun éditeur trouvé. Définissez ${d}EDITOR, ou utilisez : z2-icon set <cible> <fichier>"

    override val tileNoSuchMacro: String get() = "cette macro n’est pas dans ~/.z2term/macros/ (utilisez un chemin complet pour l’exécuter comme commande) :"

    override val notiHelp: String get() = """
        |# z2-noti trace start [paquet] / trace dump / trace stop
        |# Trace : activez d’abord la capture ; 5 minutes, les 128 derniers événements, en mémoire seulement.
        |# Types de champs et longueurs UTF-16, jamais les valeurs du texte. dump/stop renvoient du JSON ; start réinitialise.
        |# z2-noti list  … les notifications actuellement affichées, en TSV
        |#                 (clé / paquet / nom de l’appli / titre / corps)
        |# Lecture seulement. Il n’y a volontairement aucun moyen d’appuyer sur une notification ni de l’écarter :
        |# cela appuierait aussi sur les boutons payer et envoyer des autres applis.
        |# Nécessite l’accès aux notifications (Paramètres > Autorisations et notifications).
        |# Voir aussi : z2-when notify:otp / notify:pkg=<partie> / notify:contains=<partie>
    """.trimMargin()

    override val notiUsage: String get() = "utilisation : z2-noti list | trace start [paquet] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [nom]      … une fois au prochain HH:MM (demain s’il est passé)
        |# z2-alarm daily HH:MM [nom]   … chaque jour à HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [nom] … une fois, dans N secondes/minutes/heures
        |# z2-alarm list                … liste ce qui est programmé (JSON ; « exact » dit la ponctualité)
        |# z2-alarm cancel <id|nom|all> … annule
        |# Au déclenchement, une ligne {"event":"alarm","name":…} est ajoutée à ~/.z2term/events.jsonl.
        |# Cela réveille l’appareil même en Doze. "exact":true signifie que ça se déclenche à la minute ;
        |# "exact":false signifie que Doze n’offre un créneau que toutes les 9-15 min, un téléphone laissé
        |# écran éteint peut donc avoir ce retard. Désactiver l’optimisation de la batterie pour cette appli
        |# le fait passer à true (aucune autorisation supplémentaire n’est demandée).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm : aucune commande date utilisable"

    override val sessionHelp: String get() = """
        |# z2-session list                     … liste les onglets (index / id / type / marque / nom, TSV)
        |#   marques : * = à l’écran / ! = en cours / ? = pas démarré / @ = connecté depuis un shell / - = autre
        |# z2-session new [nom]                … ouvre un onglet de terminal (renvoie l’index et l’id)
        |# z2-session send <onglet> <texte>... … tape le texte dans cet onglet (sans l’exécuter)
        |# z2-session send <onglet> <texte> --enter … le tape, puis l’exécute
        |# z2-session key <onglet> <touche>... … envoie des **touches** à cet onglet (C-c / M-x / F5 / Up …)
        |#   modificateurs : C- (Ctrl) et M- (Meta=Alt) ; ils se cumulent, comme dans C-M-a
        |#   spéciales : Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Les touches avec Shift comme C-S-a sont refusées (un terminal ne distingue pas Shift : mêmes octets que C-a)
        |# z2-session key <onglet> --raw '\x1b[A' … tout le reste, en octets (\xHH \e \n \r \t \0)
        |# z2-session capture [onglet] [--all] … prend l’écran de cet onglet (--all inclut l’historique)
        |# z2-session attach <onglet>          … reste connecté à cet onglet et tapez-y simplement
        |#   quittez avec Ctrl+] (à tout moment), ou avec ~. en début de ligne (comme dans ssh)
        |#   ⚠ par SSH seul Ctrl+] marche : le ssh devant vous mange ~. et coupe la session SSH
        |#   écrivez ~~ pour un ~ littéral en début de ligne, ~ puis Ctrl+] pour un Ctrl+] littéral
        |#   pendant la connexion l’onglet suit la taille de VOTRE fenêtre ; il revient quand vous partez
        |# z2-session close <onglet>           … ferme cet onglet (jamais le dernier)
        |#
        |# <onglet> peut être l’index de list, un id ou un nom d’onglet. « . » ou omis = l’onglet à l’écran.
        |# ex. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "utilisation : z2-session list | new [nom] | send <onglet> <texte>... [--enter] | " +
                "key <onglet> <touche>... | key <onglet> --raw <octets> | capture [onglet] [--all] | " +
                "attach <onglet> | close <onglet>"

    override val keyNothing: String get() = "z2-session key : aucune touche indiquée"

    override val keyRawEmpty: String get() = "z2-session key : --raw a besoin des octets à envoyer"

    override val keyUnknown: String get() = "z2-session key : touche inconnue (voir la liste dans « z2-session -h ») :"

    override val keyBadEscape: String get() = "z2-session key : impossible de lire l’échappement :"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key : le terminal ne distingue pas Shift, '$asWritten' donnerait donc " +
            "exactement les mêmes octets que '$equivalentTo'. Écrivez '$equivalentTo' si c’est ce que vous vouliez."

    override fun attachNoSuchTab(target: String): String = "cet onglet n’existe pas : '$target' (exécutez « z2-session list » pour voir les onglets)"

    override val attachNotTerminal: String get() = "c’est un onglet GUI, sans shell auquel se connecter. Choisissez un onglet de terminal."

    override val attachNotStarted: String get() = "cet onglet n’a pas encore démarré (marqué « ? » dans list). Ouvrez-le une fois dans l’appli, puis connectez-vous."

    override val attachExited: String get() = "cet onglet est déjà terminé. Son écran peut encore être lu avec « z2-session capture »."

    override val attachSelf: String get() = "c’est l’onglet dans lequel vous tapez. Connecter un onglet à lui-même fait revenir sa propre sortie " +
            "comme entrée pour toujours, impossible donc de revenir en arrière. Choisissez un autre onglet."

    override val attachLoop: String get() = "cet onglet est déjà connecté en retour à celui-ci, les deux s’alimenteraient donc " +
            "l’un l’autre sans fin. Déconnectez-en d’abord un (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … serveurs enregistrés (index / id / état / marque / nom, TSV)
        |#   marques : * = activé / - = désactivé
        |# z2-server start <serveur>     … le lance comme serveur résident (garde l’appareil joignable)
        |# z2-server stop <serveur>      … arrête celui-ci (les autres continuent)
        |# z2-server status [<serveur>]  … état, pid, relances et dernier code de sortie
        |#
        |# <serveur> peut être l’index de list, un id ou le nom que vous lui avez donné dans l’appli.
        |# Seuls les serveurs enregistrés dans l’appli peuvent être lancés ; ceci n’en enregistre jamais un nouveau.
        |#
        |# Pourquoi cela existe : un démon lancé directement par une règle tourne HORS du cadre des serveurs
        |# résidents (pas de WakeLock, pas de WifiLock, pas de service au premier plan), il cesse donc de répondre une fois
        |# l’écran éteint. Le lancer par ici le met dans ce cadre.
        |# ex. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "utilisation : z2-server list | start <serveur> | stop <serveur> | status [<serveur>]"

    override val serverNotFound: String get() = "z2-server : ce serveur n’existe pas (voir « z2-server list ») :"

    override val serverAmbiguous: String get() = "z2-server : le nom correspond à plusieurs serveurs ; utilisez l’id de « z2-server list » :"

    override val serverNone: String get() = "z2-server : aucun serveur enregistré pour l’instant (ajoutez-en un dans l’appli : 📜 -> Serveurs)."

    override val serverLowPowerWarn: String get() = "z2-server : remarque - le mode économie d’énergie est activé, aucun WakeLock/WifiLock n’est donc gardé. " +
            "Le serveur peut cesser de répondre écran éteint " +
            "(Paramètres -> Automatisation -> Protection des processus en arrière-plan)."

    override val whenHelp: String get() = """
        |# z2-when <déclencheur> run <cmd...>    … enregistre une règle
        |#   déclencheurs : charge:start | charge:stop  (détection activée requise)
        |#            battery:below=N | battery:above=N  (détection activée requise)
        |#            battery:temp>N | battery:temp<N  … la température de la batterie (°C) a franchi N (détection activée requise)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min heure jour mois jour-semaine'  (jour-semaine 0-7 / 0,7=dimanche. Entre guillemets : il y a des espaces)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<nom>  (détection activée requise)
        |#            net:online | net:offline  … une connexion utilisable est apparue / a disparu
        |#            net:wifi | net:mobile | net:ethernet  … la connexion utilisée est passée à celle-ci
        |#                                        (détection activée requise ; compte les données mobiles, pas seulement le Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<partie> | share:ext=<ext>
        |#                                        … quelque chose a été partagé vers z2term depuis une autre appli
        |#            boot                        … l’appareil a fini de démarrer (marche détection désactivée)
        |#            sms:any | sms:from=<partie> | sms:contains=<partie> | sms:otp  (RECEIVE_SMS requis)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (détection activée)
        |#            file:new=<dossier>[,ext=<ext>]  … un nouveau fichier est arrivé dans ce dossier (détection activée requise)
        |#            file:changed=<dossier>[,ext=<ext>] | file:deleted=<dossier>[,ext=<ext>]
        |#                                        … un fichier y a été réécrit / a disparu (détection activée requise)
        |#            bt:connect | bt:disconnect | bt:connect=<nom> | bt:disconnect=<nom>
        |#                                        … un appareil Bluetooth s’est connecté / déconnecté (nom : une partie, ou l’adresse)
        |#                                        (détection activée et, sous Android 12+, l’autorisation Appareils à proximité)
        |#            app:open=<appli> | app:close=<appli>  … cette appli est passée devant / vous l’avez quittée (paquet ou nom, une partie)
        |#                                        (z2term activé dans l’accessibilité requis ; marche détection désactivée)
        |#            steps:reach=N               … les pas du jour ont atteint N (détection activée + autorisation Activité physique)
        |#            notify:any | notify:otp | notify:pkg=<partie> | notify:title=<partie> | notify:contains=<partie>
        |#                                        … une notification est arrivée (accès aux notifications requis)
        |#            event:<nom> | event:<préfixe>* | event:*  … n’importe quel événement de l’appareil, par nom
        |#              (z2-when events les liste ; les mêmes noms que dans events.jsonl)
        |# Nom (facultatif, juste après le déclencheur — avant run) :
        |#   name=<texte>            … à quoi sert cette règle, affiché dans l’onglet Automatisation
        |#                             (entre guillemets s’il y a des espaces). Vide = le déclencheur s’affiche.
        |#                             Affichage seulement : cela ne change rien au moment où la règle tourne.
        |# Filtres (tout déclencheur, juste après — avant run) :
        |#   if=<cond>[,<cond>...]   … seulement quand l’appareil est dans cet état (ET ; ! inverse)
        |#                             les clés sont celles qu’affiche z2-state : wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             ex. if=wifi,!screen / if=ssid=Maison / if=level<30
        |#   if_any=<cond>[,<cond>...] … tourne quand **l’une d’elles** est vraie (la virgule signifie OU)
        |#                             avec if= : « toutes celles de if ET l’une de if_any »
        |#   else=<cmd>              … exécute ceci **à la place**, quand if / if_any n’a pas tenu
        |#                             ⚠ seuls if / if_any y mènent. Une exécution sautée par between /
        |#                             days / cooldown n’exécute rien — une règle coupée
        |#                             la nuit doit rester muette la nuit, else compris.
        |#   cooldown=30m            … ne pas relancer dans ce délai (10s / 30m / 2h)
        |#   between=22:00-07:00     … seulement dans cette plage (passe minuit)
        |#   days=mon-fri            … seulement ces jours (noms ou nombres cron 0-7, 0/7 = dimanche)
        |#   answer='<question>=><réponse>' … quand la commande pose cette question dans le terminal, taper la réponse
        |#                             et Entrée (répétable ; ex. answer='[y/N]=>y'). Compare avec la fin
        |#                             de la dernière ligne de sortie.
        |#   secret='<question>'     … pareil pour une réponse masquée (un mot de passe). Son contenu se saisit dans
        |#                             l’éditeur de règles de l’appli et y est gardé chiffré.
        |# Les exécutions sautées sont aussi notées — z2-when fired montre skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, une règle qui ne tourne jamais peut donc s’expliquer. Le bouton ▶ de l’appli
        |# ignore tous les filtres (il sert à essayer la règle).
        |# z2-when events                        … liste les noms utilisables avec event:
        |# z2-when pause / resume                … arrête / reprend les exécutions automatiques (les règles restent)
        |# z2-when fired [n]                     … déclenchements récents (heure / id / déclencheur / run|paused)
        |# z2-when list                          … règles enregistrées (id / on|off / déclencheur / -> / cmd, TSV ;
        |#                                         le nom et les filtres, s’il y en a, entre [] à la fin)
        |# z2-when remove <id|all>  (rm marche)  … supprimer
        |# z2-when on <id> / off <id>            … activer / désactiver
        |# z2-when log <id>                      … le journal d’exécution de cette règle (tail)
        |# Au déclenchement, la commande tourne dans la distribution choisie avec Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) dans l’environnement.
        |# Pour net : vous avez Z2_WHEN_NET (la connexion maintenant) et Z2_WHEN_NET_PREV (celle d’avant).
        |# Pour share : vous avez Z2_WHEN_SHARE (le texte, ou les chemins des fichiers reçus)
        |# et Z2_WHEN_SHARE_KIND (text|file). Le partage va toujours aussi sur la ligne de saisie, comme avant.
        |# Pour file : vous avez Z2_WHEN_FILE (chemin complet) et Z2_WHEN_DIR.
        |# Pour notify : vous avez Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (et Z2_WHEN_OTP pour notify:otp). notify:category= compare exactement : call, missed_call, msg, ...
        |# Pour event : vous avez aussi Z2_WHEN_EVENT (le nom de l’événement) ; alarm / notify_action ajoutent
        |# Z2_WHEN_EVENT_NAME (l’identifiant avec lequel vous l’avez armé) et Z2_WHEN_ACTION (bouton appuyé).
        |# Pour battery:temp vous avez Z2_WHEN_TEMP (°C). Pour bt : Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Pour app : Z2_WHEN_APP_PKG (l’appli devant maintenant) / Z2_WHEN_APP_PREV (celle d’avant).
        |# Pour steps : Z2_WHEN_STEPS (pas du jour). Pour file : aussi Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated donnent Z2_WHEN_PKG, timezone_changed donne Z2_WHEN_TZ,
        |# usb_attached / usb_detached donnent Z2_WHEN_USB (nom) / Z2_WHEN_USB_ID (fabricant:produit).
        |# La même règle ne se déclenche pas deux fois en 10 secondes (des événements comme screen_on arrivent souvent).
        |# ex. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "sonnerie : ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Rapport du matin' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Exécutions automatiques en pause (z2-when resume pour reprendre)"

    override val whenResumed: String get() = "Exécutions automatiques reprises"

    override val whenNoFires: String get() = "(rien ne s’est encore déclenché)"

    override val whenPausedNote: String get() = "# en pause (z2-when resume pour reprendre)"

    override val whenNoLog: String get() = "(pas encore de journal)"

    override val whenWriteFailed: String get() = "z2-when : impossible d’écrire la règle"

    override val whenUnknownIfKey: String get() = "z2-when : clé if= inconnue (z2-state liste celles utilisables) :"

    override val whenUnknownTrigger: String get() = "z2-when : déclencheur inconnu (z2-when sans argument les liste) :"

    override val whenBadTriggerSpec: String get() = "z2-when : ce déclencheur ne prend pas cet argument :"

    override val whenPausedWarn: String get() = "remarque : les exécutions automatiques sont en pause (z2-when resume pour reprendre)"

    override val whenRunJoined: String get() = "remarque : les sauts de ligne de la commande sont devenus des espaces (une règle ne lit qu’une ligne)"

    override val whenEventList: String get() = """
        |screen_on              l’écran s’est allumé                    [détection activée]
        |screen_off             l’écran s’est éteint                    [détection activée]
        |unlocked               l’appareil a été déverrouillé           [détection activée]
        |power_connected        la charge a commencé                    [détection activée]
        |power_disconnected     la charge s’est arrêtée                 [détection activée]
        |battery_low            la batterie est faible                  [détection activée]
        |battery_okay           la batterie s’est rétablie              [détection activée]
        |battery_level          le niveau a franchi un palier de 10%    [détection activée]
        |wifi_connected         connecté au Wi-Fi                       [détection activée]
        |wifi_disconnected      Wi-Fi quitté                            [détection activée]
        |net_online             une connexion qui marche est apparue    [détection activée]
        |net_offline            plus de connexion qui marche            [détection activée]
        |net_wifi               la connexion utilisée est le Wi-Fi      [détection activée]
        |net_mobile             la connexion utilisée est mobile        [détection activée]
        |headset_plugged        écouteurs filaires branchés             [détection activée]
        |headset_unplugged      écouteurs filaires débranchés           [détection activée]
        |bt_audio_connected     audio Bluetooth connecté                [détection activée]
        |bt_audio_disconnected  audio Bluetooth parti                   [détection activée]
        |airplane_on            mode avion activé                       [détection activée]
        |airplane_off           mode avion désactivé                    [détection activée]
        |ringer_normal          sonnerie en mode normal                 [détection activée]
        |ringer_vibrate         sonnerie en vibreur                     [détection activée]
        |ringer_silent          sonnerie en silencieux                  [détection activée]
        |bt_connected           un appareil Bluetooth s’est connecté    [détection activée]
        |bt_disconnected        un appareil Bluetooth s’est déconnecté  [détection activée]
        |powersave_on           économiseur de batterie activé          [détection activée]
        |powersave_off          économiseur de batterie désactivé       [détection activée]
        |dnd_on                 Ne pas déranger activé                  [détection activée]
        |dnd_off                Ne pas déranger désactivé               [détection activée]
        |app_installed          une appli a été installée               [détection activée]
        |app_removed            une appli a été désinstallée            [détection activée]
        |app_updated            une appli a été mise à jour             [détection activée]
        |timezone_changed       le fuseau horaire a changé              [détection activée]
        |usb_attached           un appareil USB a été branché           [détection activée]
        |usb_detached           un appareil USB a été débranché         [détection activée]
        |boot                   l’appareil a démarré                    [toujours]
        |alarm                  un z2-alarm s’est déclenché             [toujours]
        |notify_action          un bouton de notification               [toujours]
        |unlock_failed          échec de déverrouillage                 [toujours, préparation requise]
        |unlock_succeeded       déverrouillé après un échec             [toujours, préparation requise]
        |voice                  une commande vocale a été entendue      [toujours, depuis la tuile ou l’assistant]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Noms utilisables dans event:<nom>. Dans le même ordre que dans events.jsonl.
        |    # Le groupe du haut nécessite la détection activée (Paramètres > Autorisations et notifications),
        |    # celui du bas est armé par vous, il marche donc détection désactivée.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … vérifie, télécharge et confie la nouvelle version à l’installateur
        |# z2-update --check             … dit seulement s’il y a une version plus récente
        |# z2-update --keep              … garde le .apk téléchargé (supprimé par défaut)
        |# z2-update --dir <dossier>     … télécharge dans ce dossier plutôt que dans celui de l’appli
        |#
        |# D’où cela vient
        |#   La page GitHub Releases de z2term, le même fichier que vous téléchargeriez à la main
        |#   (z2term-<version>.apk, signé avec la clé de publication). Rien d’autre n’est contacté, et
        |#   rien du tout tant que vous ne lancez pas ceci.
        |#
        |# Ce que cela peut et ne peut pas faire
        |#   ⚠ **Impossible d’installer en silence.** Android montre toujours son propre écran « installer ? » pour
        |#     une appli qui se remplace elle-même, le dernier appui est donc toujours le vôtre. Cette commande fait toutes
        |#     les étapes jusqu’à cet écran.
        |#   ⚠ La première fois, autorisez **« Installer des applis inconnues »** pour z2term (il vous le dira, et
        |#     les ⚙Paramètres ont le même bouton). Sans cela l’écran d’installation n’apparaît jamais.
        |#   ⚠ Installé depuis F-Droid ou une boutique ? Alors ceci refuse et vous dit de mettre à jour là-bas —
        |#     la version que vous avez, c’est à eux de la remplacer, pas à nous.
        |#
        |# Le fichier téléchargé
        |#   Par défaut il arrive dans le dossier de l’appli et est supprimé une fois l’installation
        |#   passée (et au prochain démarrage, au cas où l’appli aurait été tuée en pleine installation — ce qui est
        |#   normal quand elle se remplace). --keep ou --dir changent cela ; les ⚙Paramètres ont les deux mêmes
        |#   réglages pour le bouton de là-bas.
        |#
        |# ex. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # regarder chaque nuit (il vous demande quand même)
    """.trimMargin()

    override val updateUsage: String get() = "utilisation : z2-update [--check] [--keep] [--dir <dossier>]"

    override fun updateUpToDate(current: String): String = "z2-update : $current est la dernière version."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update : $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update : recherche d’une version plus récente ..."

    override val updateHandedToInstaller: String get() = "z2-update : l’écran d’installation est ouvert sur l’appareil — validez-le pour terminer."

    override fun updateNoApk(url: String): String = "z2-update : cette version n’a pas de .apk joint. Prenez-le sur la page de la version : $url"

    override val updateNeedPermission: String get() = "z2-update : autorisez d’abord \"Installer des applis inconnues\" pour z2term " +
            "(Paramètres > Applis > Accès spéciaux des applis > Installer des applis inconnues, ou le bouton dans les Paramètres de z2term)."

    override val updateManagedByStore: String get() = "z2-update : cette version a été installée depuis une boutique (F-Droid / Play). Mettez-la à jour là-bas."

    override fun updateFailed(reason: String): String = "z2-update : échec : $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Commande vocale — table de mots (français). voice.sh la lit. Modifiez-la pour ajouter ou changer ce que vous pouvez dire.
#
# Comment l’écrire :
#   clé = une façon|une autre façon|...   séparez par "|" ; vérifié de haut en bas
#   mettez un * pour prendre les mots à cet endroit (ouvre * -> "ouvre plans" donne "plans")
#   *X* prend les mots des deux côtés de X
#   clé =                             rien à droite désactive celle-ci
#   La ponctuation est ignorée, ainsi que les majuscules/minuscules et les espaces en trop
#   Une clé absente ici utilise la valeur incluse (supprimez une ligne pour retrouver l’original)
#   Les lignes qui commencent par say_ / msg_ / fmt_ / title_ sont ce qui est dit ou affiché ; {…} reçoit une valeur
#
# Essayer sans parler : sh ~/.z2term/macros/voice.sh try chanson suivante
# « vos propres mots = une commande » vont dans ~/.z2term/voice/commands.txt (vus avant cette table).

# --- Mots écoutés ---
ai = demande à l’ia *|demande à l ia *|hé ia *|ia *|question *
media_next = chanson suivante|morceau suivant|suivant|passer|passe la chanson|passer ce morceau|suivante
media_previous = chanson précédente|morceau précédent|précédent|précédente|revenir en arrière
media_pause = pause|stop|arrête la musique|mets en pause|pause la musique|coupe la musique
media_play = lecture|reprendre|continue|joue la musique|reprends la musique|relance la musique
volume_up = monte le son|plus fort|augmente le volume|monte le volume|volume plus fort|hausse le volume
volume_down = baisse le son|moins fort|diminue le volume|baisse le volume|volume moins fort|plus bas
volume_mute = coupe le son|muet|mode silencieux|silence
volume_unmute = remets le son|réactive le son|son activé
time = quelle heure est-il|quelle heure est il|il est quelle heure|l’heure|heure|quelle heure
date = quelle est la date|on est quel jour|quel jour sommes-nous|quel jour sommes nous|la date|date|quel jour on est
battery = batterie|niveau de batterie|combien de batterie|combien il reste de batterie|état de la batterie
steps = pas|combien de pas|nombre de pas|mes pas|combien de pas aujourd’hui|pas aujourd’hui|combien de pas j’ai fait
torch_on = allume la lampe|allume la lampe de poche|lampe de poche|allume la torche|lumière|allume la lumière
torch_off = éteins la lampe|éteins la lampe de poche|éteins la torche|éteins la lumière
screenshot = capture d’écran|capture d’écran s’il te plaît|fais une capture|capture l’écran
home = accueil|écran d’accueil|retour à l’accueil
news = actualités|infos|les infos|lis les actualités|quoi de neuf|titres|rss
weather = météo|quel temps fait-il|quel temps fait il|la météo|météo aujourd’hui|le temps qu’il fait
weather_tomorrow = météo demain|quel temps demain|quel temps fera-t-il demain|quel temps fera t il demain|la météo de demain
memo_read = lis mes notes|lire mes notes|mes notes|montre mes notes|lis les notes
memo_add = note que *|note *|prends note de *|prends note que *|mémo *|écris *
remind_list = rappels|mes rappels|liste des rappels|montre les rappels|quels sont mes rappels|mon agenda
remind = rappelle-moi de *|rappelle moi de *|rappelle-moi que *|rappelle moi que *|rappelle-moi *|rappelle moi *|rappel *
wake = réveille-moi à *|réveille moi à *|réveille-moi *|réveille moi *|mets une alarme à *|alarme à *|réveil à *
help = que puis-je dire|que puis je dire|qu’est-ce que tu sais faire|aide|commandes
timer = minuteur de *|mets un minuteur de *|lance un minuteur de *|un minuteur de *|minuteur *
navi = navigue vers *|va à *|itinéraire vers *|emmène-moi à *|emmène moi à *|comment aller à *|je veux aller à *
navi_ask = navigation|naviguer|itinéraire|lance la navigation
place_phone = quel est le numéro de *|le numéro de téléphone de *|numéro de téléphone de *|numéro de *
call = appelle *|appeler *|téléphone à *|compose *
play = joue *|mets *|lance la musique *
open = ouvre *|ouvrir *|lance *|démarre *
# Ouvrir par type (l’appli par défaut du téléphone). Ex. ouvre le navigateur
open_browser = navigateur|le navigateur|internet
open_maps = plans|carte|la carte|maps
open_mail = mail|e-mail|email|la messagerie
open_camera = appareil photo|caméra|la caméra
open_settings = paramètres|les paramètres|réglages
open_calendar = agenda|l’agenda|calendrier|le calendrier
open_music = musique|la musique
open_gallery = photos|galerie|la galerie
open_contacts = contacts|les contacts
open_dialer = téléphone|le téléphone|composeur
open_messages = messages|les messages|sms
open_calculator = calculatrice|la calculatrice
# Surnoms d’applis. Quand dire le nom ne l’ouvre pas, écrivez : surnom:nom de l’appli|surnom:nom de l’appli
app_names =

# Volume avec un nombre ("baisse le volume 3", "volume 10", "volume 50 pour cent", "volume au maximum") — correspond s’il est contenu
volume_word = volume|son
volume_up_word = monte|augmente|plus fort|hausse|plus
volume_down_word = baisse|diminue|moins fort|réduis|moins
volume_max_word = maximum|max|à fond
volume_min_word = minimum|zéro
volume_percent_word = %|pour cent|pourcent
volume_mute_word = muet|coupe
volume_unmute_word = remets

# Durées et heures (minuteur, rappel, réveil)
unit_hour = heures|heure
unit_minute = minutes|minute|min
unit_second = secondes|seconde|sec
unit_day = jours|jour
when_before = dans
when_after = à partir de maintenant|plus tard
clock_at = à|vers|pour
clock_hour = pile
clock_minute =
clock_half = et demie
clock_am = du matin|le matin
clock_pm = de l’après-midi|l’après-midi|du soir|le soir|ce soir|de la nuit
day_today = aujourd’hui
day_tomorrow = demain
day_after_tomorrow = après-demain|après demain
day_daily = tous les jours|chaque jour|quotidiennement
day_weekdays = en semaine|les jours ouvrés|du lundi au vendredi
day_every = tous les|toutes les|chaque
wd_mon = lundi
wd_tue = mardi
wd_wed = mercredi
wd_thu = jeudi
wd_fri = vendredi
wd_sat = samedi
wd_sun = dimanche
# Petits mots qui restent entre le texte et les parties retirées
remind_filler = de|d|que|qu|à|au|le|la|les|l|pour|moi|me

# --- Ce qui est dit ou affiché ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Commande vocale
title_navi = Navigation
title_ai = IA
title_timer = Minuteur
title_news = Actualités
title_memo = Notes
title_remind = Rappels
title_call = Appel
title_help = Ce que vous pouvez dire
title_rule_voice = Commande vocale
title_rule_voice_locked = Commande vocale (verrouillé)
title_rule_timer = Minuteur vocal
title_rule_steps = Compter les pas (pour la commande vocale)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} heures
fmt_weekdays = lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche
fmt_months = janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre
say_time = Il est {clock}
say_date = Nous sommes {weekday} {day} {month_name}
say_battery = La batterie est à {n} pour cent. {charging}
say_charging = Elle est en charge.
say_steps = Vous avez fait {n} pas aujourd’hui
say_steps_none = Aucun pas enregistré pour l’instant. Il faut l’autorisation Activité physique et une règle de pas
say_news = Voici les derniers titres. {titles}
say_news_none = Il n’y a pas de nouvelles actualités
fmt_weather_lang = fr
say_weather = En ce moment, {desc}, {temp} degrés
say_weather_tomorrow = Demain : {desc}, maximum {hi} et minimum {lo} degrés, {rain} pour cent de risque de pluie
say_weather_fail = Je n’ai pas pu obtenir la météo
say_memo_saved = C’est noté
say_memo_recent = Vos dernières notes. {memos}
say_memo_none = Il n’y a pas de notes
say_remind_ok = Rappel programmé. {res}
say_remind_fail = Je n’ai pas pu le programmer. {res}
say_remind_unclear = Je n’ai pas compris quand ni de quoi vous rappeler. Essayez : rappelle-moi de sortir les poubelles demain à 7 heures du matin
say_remind_missing = La macro de rappels n’est pas installée. Exécutez z2-macro install remind dans un terminal
fmt_wake_label = réveil
say_wake = C’est l’heure de se réveiller
say_wake_set = Je vous réveillerai à {clock}
say_wake_unclear = Je n’ai pas compris l’heure du réveil
fmt_unit_hour = heures
fmt_unit_minute = minutes
fmt_unit_second = secondes
fmt_timer_label = {n} {unit}
say_timer_set = Minuteur de {label} lancé
say_timer_done = Les {label} sont écoulées
say_timer_unclear = Je n’ai pas compris la durée du minuteur
say_call_missing = Je n’ai pas trouvé de numéro pour {who}
msg_call_missing = "{who}" n’est pas dans {file}. Écrivez un « nom numéro » par ligne.
say_open_missing = Je n’ai pas trouvé d’appli nommée {name}
say_help = Ce que vous pouvez dire est dans une notification
say_locked = Déverrouillez le téléphone, s’il vous plaît
msg_help = quelle heure est-il, date, batterie, pas / baisse le volume 3, volume 50 pour cent / chanson suivante, pause / minuteur de 3 minutes, réveille-moi à 7 heures / rappelle-moi de sortir les poubelles demain à 7 heures du matin, rappels / actualités, météo, météo demain / note acheter du lait, lis mes notes / allume la lampe, capture d’écran / ouvre … / appelle … / navigue vers …, numéro de téléphone de … / IA … (activez avec voice.sh ai) / changer les mots : voice.sh words
msg_unknown = Je n’ai pas compris : {text}
msg_locked = "{text}" continuera une fois le téléphone déverrouillé
msg_navi_where = Dites où vous voulez aller
msg_navi_hint = Où (un nom de lieu)
msg_ai_off = L’IA est désactivée. Appuyez sur Copier et collez dans un terminal pour utiliser {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = L’IA est désactivée. Installez codex / claude / agy et connectez-vous à l’un d’eux, puis choisissez-le dans un terminal, ex. sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} est introuvable. Installez-le et connectez-vous, ou choisissez-en un autre avec sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Je demande à l’IA ({ai})…
msg_ai_noanswer = Aucune réponse n’est revenue (journal : {log})
msg_ai_usage = ai : indiquez l’un de off | codex | claude | agy
msg_ai_how = L’IA est désactivée. Pour l’utiliser : sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy ; elle n’est sollicitée que si vous commencez par le mot-signal, « IA » ou « question »). Modèle : sh ~/.z2term/macros/voice.sh model <nom> (vide = celui par défaut de cette IA)
fmt_ai_prompt = (La réponse sera lue à voix haute. Sans utiliser de commandes ni de fichiers, répondez en français en 2 ou 3 phrases, sans symboles ni listes à puces.)
msg_setup_done = Règles de la commande vocale enregistrées (celles déjà présentes n’ont pas été rajoutées) :
msg_setup_steps = La règle Compter les pas (steps:reach=1000000) ne fait que garder le podomètre actif pour que vous puissiez demander les pas du jour. Cet objectif n’est jamais atteint, elle n’exécute donc rien.
msg_words_file = Table de mots : {file}  (modifiez-la pour ajouter des façons de dire ; sh ~/.z2term/macros/voice.sh words l’ouvre)"""
