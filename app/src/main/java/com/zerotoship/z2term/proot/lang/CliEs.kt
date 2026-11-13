package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Español (`es`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 */
internal object CliEs : CliLanguage {

    override val code: String = "es"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgEs) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgEs(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Instalando el entorno gráfico",
            cleanInstalling = "🧹 Instalando el entorno gráfico desde cero",
            noPackageManager = "❌ No se encontró ningún gestor de paquetes compatible (apk/apt-get/pacman).",
            invalidGeometry = "❌ Resolución mal escrita",
            noXvnc = "❌ No hay Xvnc/Xtigervnc (tigervnc no está instalado)",
            alreadyRunning = "✅ El entorno gráfico ya está en marcha",
            startingXvnc = "▶ Iniciando",
            xvncFailed = "❌ Falló el arranque de Xvnc. Registro:",
            ready = "✅ Entorno gráfico listo. Conéctate desde una pestaña gráfica de z2term.",
            running = "✅ Entorno gráfico en marcha",
            stopped = "⏹ El entorno gráfico está parado",
            stoppedMsg = "⏹ Parado",
            usage = "Uso: z2gui [start [AxA] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ No se pudo instalar el entorno gráfico (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Comprueba la red. Para recuperarlo, puedes ejecutar z2gui clean en una terminal.",
            audioInstalling = "🔊 Audio del entorno gráfico: instalando PulseAudio",
            audioNoPulse = "⚠️ Audio del entorno gráfico: no hay pulseaudio; se sigue sin sonido",
            audioNoPactl = "⚠️ Audio del entorno gráfico: no hay pactl; se sigue sin sonido",
            audioStartFailed = "⚠️ Audio del entorno gráfico: PulseAudio no arrancó",
            audioReady = "🔊 Audio del entorno gráfico: z2sink.monitor →",
            menuApps = "Aplicaciones",
            menuWindows = "Ventanas",
            menuDesktops = "Escritorios virtuales",
            menuPreviousDesktop = "Escritorio anterior",
            menuNextDesktop = "Escritorio siguiente",
            menuReload = "Recargar el menú"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Número de puerto no válido",
            configOk = "sshd(dropbear) configuración correcta",
            installingDropbear = "📦 No hay dropbear; instalándolo…",
            dropbearInstallFailed = "❌ No se pudo instalar dropbear. Comprueba la red y el nombre del paquete.",
            privilegedPortWarn = "⚠️ Puerto privilegiado; con proot (sin root) es muy probable que no se pueda enlazar (usa 1024 o más).",
            lanExposeWarn = "⚠️ Modo abierto a LAN/WAN: hace falta autenticación por clave; usa una clave fuerte.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys está vacío. Se cancela el arranque abierto a la red.",
            noAuthorizedKeysHint = "   Registra tu clave pública en ~/.ssh/authorized_keys y vuelve a ejecutarlo.",
            loopbackBind = "🔒 Solo en loopback. Para abrirlo a la LAN, pasa --lan o define Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear en primer plano (Ctrl-C para pararlo)",
            listeningLan = "✅ dropbear listening (root, solo autenticación por clave)",
            listeningLoopback = "✅ dropbear listening (root, solo clave, solo loopback)",
            startupFailed = "❌ Falló el arranque de dropbear. Registro:",
            deviceIp = "IP del dispositivo",
            authKeysHint = "ℹ ~/.ssh/authorized_keys está vacío. Registra la clave pública del cliente para poder conectar.",
            authKeysExample = "   p. ej.: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Preparando el llavero de pacman (solo la primera vez, sin red). Esto tarda un momento…",
            "keyring.msgOk" to "✅ Llavero listo. Ya se pueden instalar paquetes.",
            "keyring.msgInitFail" to "❌ pacman-key --init falló. Se volverá a intentar la próxima vez que abras esta pestaña.",
            "keyring.msgNoKeyrings" to "❌ No se encontró ningún llavero incluido en /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate falló. Se volverá a intentar la próxima vez que abras esta pestaña.",
            "action.description" to "Macros de acciones Android con nombre. Actívalas con z2-key permission.\nAl guardar se valida y sustituye la definición; screen=current registra la pantalla.\nrun espera al final; start devuelve un ID. Interrumpir run detiene esa ejecución.\nUna ejecución a la vez, 64 instrucciones por definición, 1–300 segundos. La notificación y stop detienen los pasos restantes.\nLas coordenadas requieren la aplicación destino y el mismo tamaño y orientación. Máximo 3 segundos por gesto.\nUsa start desde gestos del panel y run desde macros de shell o z2-when.\nSe guarda en /root/.z2term/actions/NAME.actions (carpeta personal compartida). Alias: z2-macro actions.",
            "action.controlHelp" to "version=2 añade repeat N|forever ... end, if CONDICIÓN ... else ... end y call NOMBRE.\nLas condiciones usan claves de z2-state o foreground=PAQUETE; coma significa AND y ! niega.\nSi falta el estado, la ejecución falla. Las macros llamadas se validan y fijan antes de empezar.\nMáximo 64 instrucciones por definición y 10000 por ejecución. Se aplican los plazos de la macro raíz y las llamadas.",
            "action.pauseHelp" to "pause [RUN_ID] pausa la ejecución; el paso en curso se vuelve a enviar desde el principio. Bajar el panel de notificaciones también pausa.\nresume [RUN_ID] continúa cuando una pantalla que no sea z2term queda estable delante (hasta 5 segundos; si no, sigue en pausa). Responde después: state running y código de salida 0, o paused y un código distinto de 0. El límite de tiempo se detiene durante la pausa.",
            "action.uiHelp" to "version=2 admite click SELECTOR, long-click SELECTOR y wait-ui MS SELECTOR.\nSelectores: text=Continuar, desc=Más opciones, id=org.example.app:id/next. Coincidencia exacta; espacios sin comillas.\nDefine primero target o launch. wait-ui consulta cada 250ms durante 1–30000ms; varias coincidencias causan un error.\nLa acción se envía una vez; usa wait-ui para la siguiente pantalla. Se excluyen campos de entrada y contraseñas.\ninspect PAQUETE devuelve como JSON el texto visible, las descripciones y los IDs de la aplicación destino enfocada.",
            "adb.mNoPm" to "z2adb: no se encontró ningún gestor de paquetes compatible (apk/apt-get/pacman).",
            "adb.mInstalling" to "z2adb: instalando el cliente adb (${d}ADB_PKG) con ${d}PM ...",
            "adb.mInstallFail" to "z2adb: falló la instalación de adb. Instálalo a mano y vuelve a intentarlo.",
            "adb.mHaveAdb" to "z2adb: adb ya está instalado.",
            "adb.mNeedWireless" to "z2adb: si falla, activa Ajustes > Opciones de desarrollador > Depuración inalámbrica (Android 11+).",
            "adb.mPairPort" to "Puerto de emparejamiento (el que muestra «Vincular dispositivo con un código»): ",
            "adb.mConnPort" to "Puerto de conexión (el que aparece justo bajo «Depuración inalámbrica»): ",
            "adb.usageText" to """
        z2adb - conecta con el adb del propio dispositivo por depuración inalámbrica (adb sobre sí mismo, sin PC).

        Requisito: activa Ajustes > Opciones de desarrollador > Depuración inalámbrica (Android 11+).

          z2adb setup                instala el cliente adb (detecta apk/apt-get/pacman)
          z2adb pair  <port> [code]  empareja (puerto de emparejamiento + código de 6 dígitos de Ajustes)
          z2adb connect <port>       conecta (el puerto que aparece bajo «Depuración inalámbrica»)
          z2adb status               muestra 'adb devices -l'
          z2adb <lo que sea>         se pasa tal cual a adb (shell, logcat, install, ...)

        El destino es 127.0.0.1 por omisión; pasa host:port para cambiarlo (o define Z2ADB_HOST).
        Ejemplo:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """,
            "audio.description" to "Reproduce en Android el sonido de un comando Linux local sin abrir el escritorio.\ninstall: instala PulseAudio en el sistema Linux actual (descarga paquetes).\nrun COMANDO [ARG...]: conecta el audio, ejecuta el comando y detiene su servidor al terminar.\nEjecuta un shell para usar sonido en varios comandos. No sobrescribe archivos de configuración.\nRequiere una pestaña local y un reproductor compatible con PulseAudio. No admite SSH.",
            "audio.missing" to "Faltan herramientas de audio. Ejecuta: z2-audio install",
            "audio.failed" to "Falló la conexión de audio; no se inició el comando.",
            "doctor.head" to "== z2doctor (este dispositivo) ==",
            "doctor.secApp" to "-- aplicación --",
            "doctor.secLinux" to "-- lado Linux --",
            "doctor.secPerm" to "-- permisos --",
            "doctor.secAuto" to "-- automatización --",
            "doctor.secReport" to "-- copia esto al pedir ayuda --",
            "doctor.lVersion" to "versión",
            "doctor.lEngine" to "motor",
            "doctor.lDistro" to "distribución",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "espacio libre",
            "doctor.lHome" to "carpeta personal",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "puede notificar",
            "doctor.lNotifyRead" to "puede leer notificaciones",
            "doctor.lBattOpt" to "fuera de la optimización de batería",
            "doctor.lStorage" to "todo el almacenamiento",
            "doctor.lSms" to "recepción de SMS",
            "doctor.lCapture" to "detección de eventos",
            "doctor.lServers" to "servidores permanentes",
            "doctor.lRules" to "reglas de automatización",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Ajustes de Android > Aplicaciones > Z2Term > Notificaciones: permitir",
            "doctor.fixNotifyRead" to "-> Ajustes > servidores permanentes y automatización > detección de notificaciones",
            "doctor.fixBattOpt" to "-> Ajustes > protección de los procesos en segundo plano > excluir de la optimización de batería",
            "doctor.fixStorage" to "-> Ajustes > permitir el acceso a todos los archivos (hace falta para ver /sdcard)",
            "doctor.fixSms" to "-> Ajustes > detección de SMS (solo hace falta para los disparadores sms:)",
            "doctor.fixCapture" to "-> Ajustes > detección de eventos del sistema (la necesitan los disparadores charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> la automatización está en pausa. Reanúdala con 'z2-when resume' o en la pestaña de automatización",
            "doctor.fixDisk" to "-> quedan menos de 500 MB libres. Borra en los ajustes los datos de los SO que no uses",
            "doctor.fixSdcard" to "-> aquí /sdcard se ve vacío; lo que lo hace visible es el permiso de almacenamiento de arriba",
            "doctor.noteRedacted" to "(el SSID, la IP y los nombres de host se omiten a propósito)",
            "doctor.allGood" to "No se han encontrado problemas.",
            "doctor.someBad" to "puntos a revisar:",
            "doctor.usage" to "uso: z2doctor [--share | --clip]   (--share pasa el informe al menú de compartir de Android)",
            "doctor.pausedYes" to "en pausa",
            "doctor.secExits" to "-- cómo terminó la última vez --",
            "doctor.lExitsNote" to "salidas anormales recientes (de la más nueva a la más antigua):",
            "doctor.lExitsNone" to "salidas anormales: no hay ninguna anotada",
            "doctor.lExitsFile" to "(historial completo: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> terminó por falta de memoria. Ejecuta menos trabajos pesados a la vez, o reduce servidores permanentes y pestañas",
            "doctor.title" to "diagnóstico para «no funciona»",
            "doctor.seeAlso" to "Ver también: z2scan self (comprobación de seguridad, otra herramienta)",
            "help.header" to "Comandos propios de Z2Term",
            "help.list" to """
        |[Versión / información]
        |  z2version [--short]            Versión de la app, motor, SO (distro), kernel
        |  z2doctor [--share|--clip]      ¿No arranca? Autocomprobación + informe para pegar
        |
        |[Funciones del teléfono]
        |  z2-notify [-h] [-b TEXTO]...   Notificación (-h: banner, -b: botón de respuesta)
        |  z2-ask "pregunta"              Pregunta en una notificación; imprime la respuesta
        |  z2-listen [-w PALABRA,...]     Voz a texto, solo en el dispositivo (z2-listen -h)
        |  z2-toast "mensaje"             Toast (mensaje corto en pantalla)
        |  z2-share "texto"               Pasa el texto al menú de compartir de Android
        |  z2-open <url|ruta>             Abre una URL o un archivo en su aplicación
        |  z2-view <archivo.html>         Lee en la app una página hecha aquí
        |  z2-edit <archivo>              Edita un archivo de texto en el editor de la app
        |  z2-qr                          Lee un código QR con la cámara (un botón lo abre)
        |  z2-img <archivo>               Dibuja una imagen en la terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [texto]      Lee / fija el portapapeles (set sin argumento: stdin)
        |  z2-file pick | save FILE     Selector y exportación de documentos Android (z2-file --help)
        |  z2-battery                     Nivel de batería y estado de carga (JSON)
        |  z2-vibrate [ms]                Vibra (200 ms por omisión)
        |  z2-say "texto"                 Lee en voz alta con el TTS (stdin si no hay argumento)
        |  z2-torch [on|off|toggle|status]       Linterna (toggle por omisión)
        |  z2-media [play|pause|next]     Teclas de medios (también previous/stop)
        |  z2-volume up|down|N|N%         Volumen de medios (devuelve current/max)
        |  z2-audio run COMMAND ...      Audio de comandos Linux en Android
        |  z2-sensor [light|accel|prox]   Lee una muestra de un sensor (JSON)
        |  z2-intent -a ACT -d URI ...    Lanza cualquier Intent de Android (ver MACRO-GUIDE)
        |  z2-state [clave]               Estado actual en JSON (o el valor de una sola clave)
        |  z2-screen keepon 1h | off      Impide un rato que la pantalla se apague sola
        |  z2-tile set 1 backup.sh        Pon una macro o un comando en un mosaico (12 huecos)
        |  z2-icon edit 1 | edit notify   Dibuja tú el icono del mosaico o de la barra (24/48/64)
        |  z2-alarm at|daily HH:MM [nom]  Disparador por hora -> events.jsonl (list/cancel también)
        |  z2-when <disparo> run <cmd>    Ejecuta con carga/batería/hora/eventos (uso: z2-when)
        |  z2-macro list|install <nom>    Macros de ejemplo incluidas (ver MACRO-GUIDE)
        |  z2-session list|new|send|...   Maneja las pestañas de esta app (uso: z2-session)
        |  z2-usb list | allow [equipo]   Usa un equipo USB desde Linux (Android pide permiso)
        |  z2-update [--check]            Actualiza z2term desde GitHub Releases (lo apruebas tú)
        |
        |[Aplicaciones gráficas (GUI)]
        |  z2gui start [AxA] | stop | status   Escritorio Linux (p. ej. z2gui start 1280x720)
        |  z2run <app-gráfica>            Abre una app gráfica (abre también la pestaña GUI)
        |
        |[Conectar]
        |  z2adb pair/connect/shell ...   adb a este mismo teléfono, sin PC  (z2adb help)
        |  sshd [-p N]                    Servidor SSH (por omisión solo 127.0.0.1, con clave)
        |
        |[Seguridad]
        |  z2scan self [--save]           Autocomprobación de este equipo/localhost (z2scan help)
        |  z2scan diff                    Solo lo que cambió desde la referencia (salida 1 = nuevo)
        |  z2scan net|host|cve            nmap/lynis/trivy en localhost (fuera: permiso expreso)
        |
        |[Ayuda]
        |  z2help | z2term                Esta lista
        |
        |Todos los comandos de arriba se explican solos: añade --help (p. ej. 'z2-tile --help').
        |Más: 'z2adb help', 'z2gui' sin argumentos, o la sección 11 del HANDBOOK.
    """,
            "help.edge" to "[Paneles flotantes]\nz2-edge --help   Definir paneles y actualizar su contenido\nz2-key --help    Acciones globales de Android\nz2-shot --help   Capturar solo la parte que rodeas\nz2-app --help    Listar aplicaciones y exportar iconos\nz2-action --help Macros de acciones Android con nombre\nz2-ui --help     Control de apps por IA (la ayuda es una guía para IA)",
            "macro.rssBody.head" to """
#
# Preparación:
#   1) Una URL de feed por línea en:  ~/.z2term/rss/feeds.txt
#   2) Consultar cada 30 minutos:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Opcional - un feed o una palabra por línea que no deba quedar enterrada:  ~/.z2term/rss/important.txt
#      Lo que coincida recibe una notificación propia, así que un feed movido no puede taparlo.
#      Escribe parte de una URL o parte de un título (p. ej. example.org).
#   4) Registra las acciones de notificación en Automatización:
#        z2-when event:notify_action run '$rssNotificationAction'
#      «Abrir» manda ese artículo a tu navegador; «Lista» abre todo lo reunido para leerlo dentro de z2term.
#      Nota: abrir una pantalla desde un botón de notificación necesita el permiso «mostrar sobre otras aplicaciones».
#   5) Abrir la lista de artículos reunidos: sh ~/.z2term/macros/rss.sh view
#      Gestiona ambas reglas en Lista de comandos -> Automatización -> Reglas de automatización.
#      Si ya existe una regla de notificaciones, edítala en lugar de añadir otra.
#   6) Avisos: por defecto la consulta periódica avisa y lo traído desde la lista no.
#        sh ~/.z2term/macros/rss.sh notify off        # consulta periódica: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Actualizar / Artículos en la lista: on / off
#      Ambos son también interruptores en Ajustes de la lista, para cambiarlos cuando quieras.
#
# Necesita: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   Las miniaturas (Ajustes en la lista) necesitan además Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Batería: cuanto más a menudo consultes, más cuesta. No bajes de 30 minutos.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "máximo de líneas que se guardan en seen/latest",
            "macro.cNoPy" to "hace falta python3 (p. ej. apk add python3)",
            "macro.cWriteFeeds" to "Escribe una URL de feed por línea en:",
            "macro.cFeedsHint" to "# Una URL de feed por línea (las líneas que empiezan por # se ignoran)",
            "macro.cSkipFail" to "se salta en silencio el feed que no se pueda descargar o analizar (los demás siguen)",
            "macro.cDiff" to "# Restamos lo ya visto para quedarnos solo con lo nuevo (el mismo truco que el diff de z2scan).\n# No nos fiamos de las fechas ni del orden del feed: ninguno de los dos es de fiar.",
            "macro.cPrepend" to "# Apila lo más nuevo arriba para que el modo «principio (head)» del widget se lea bien.",
            "macro.cNotify" to "%s nuevos",
            "macro.cOpen" to "Abrir",
            "macro.cListNone" to "Todavía no se ha reunido nada: ejecútalo antes una vez sin argumentos.",
            "macro.cListHint" to "Lístalos con: rss.sh list [cantidad]",
            "macro.cList" to "Lista",
            "macro.cPageTitle" to "Feeds suscritos",
            "macro.cPageSites" to "%s sitios",
            "macro.cPageArticles" to "%s artículos",
            "macro.cPageDoc" to "# Construye la página de lectura (z2-view la muestra dentro de la aplicación; sin servidor).\n" +
            "# Agrupada por sitio, lo más nuevo primero, con un botón Abrir por artículo.",
            "macro.cViewHint" to "Léelos con: rss.sh view",
            "macro.cHitMax" to "máximo de notificaciones por artículo en una pasada (el resto va al resumen)",
            "macro.cSplitDoc" to "# Separa lo que no debe perderse. El cuerpo del resumen solo lleva 3 líneas, así que un feed\n" +
            "# movido que se actualice a la vez empuja fuera al que importaba. Las coincidencias reciben\n" +
            "# **su propia notificación** (la aplicación da un id distinto a cada una, así que nada se sustituye).",
            "macro.cNameDoc" to "# Mete la URL en el nombre de la notificación: al pulsar el botón vuelve como {name} en\n" +
            "# notify_action, así que se abre el artículo correcto aunque haya varias en pantalla (preparación 4).",
            "macro.cListDoc" to "# Imprime lo reunido como una lista legible (sin color: debe verse igual que el archivo y el widget).\n" +
            "# En una terminal, el título mismo se vuelve un enlace (OSC 8) en vez de poner la URL en otra línea:\n" +
            "# una URL larga se parte y se mezcla con los títulos, y la lista deja de leerse. Fuera de una terminal\n" +
            "# (tubería, redirección) los escapes estorban, así que sale texto pelado y la URL a la vista.\n" +
            "# latest.txt sigue siendo datos en bruto, un artículo por línea (los leen el tail del widget y rss-open.sh).",
            "macro.cSettings" to "Ajustes",
            "macro.cArchive" to "Archivo",
            "macro.cDelete" to "Eliminar",
            "macro.cThumbs" to "Miniaturas",
            "macro.cSave" to "Conservar artículos anteriores",
            "macro.cNotifyPoll" to "Avisar de artículos nuevos (consulta periódica)",
            "macro.cNotifyView" to "Avisar de artículos nuevos (al traerlos desde la lista)",
            "macro.cDeleteSite" to "Eliminar todos los artículos de este sitio",
            "macro.cConfirmSite" to "¿Quitar de la lista %s artículos de %h?",
            "macro.cArticles" to "Artículos",
            "macro.cPrev" to "Anterior",
            "macro.cNext" to "Siguiente",
            "macro.cCount" to "%s artículos",
            "macro.cUndated" to "Sin fecha",
            "macro.cArchiveEmpty" to "Aún no hay artículos guardados. Activa Conservar artículos anteriores en Ajustes y los artículos reunidos quedarán aquí.",
            "macro.cSearch" to "Buscar",
            "macro.cSearchWord" to "Palabras (separadas por espacios: deben aparecer todas)",
            "macro.cByMonth" to "Por mes",
            "macro.cNoHit" to "No se encontró nada.",
            "macro.cDropMonth" to "Eliminar este mes",
            "macro.cDropConfirm" to "¿Eliminar los %s artículos guardados de %m? No se puede deshacer.",
            "macro.cOnOff" to "Indica on u off",
            "macro.cNoPillow" to "Las miniaturas necesitan Pillow para Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Preparación: pon antes en marcha la parte que recoge
#   z2-macro install rss
# Widget: asigna «rss-open» a un botón en los ajustes del widget de estado.
#
# z2-run: asigna «rss-open» a un botón del widget de estado (para probarlo aquí: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Todavía no hay artículos",
            "macro.cAllRead" to "No hay nada nuevo que abrir",
            "macro.cPick" to "# Las líneas de latest.txt son «título  URL». Coge la primera URL que aún no se haya abierto.",
            "macro.cCap" to "limita la lista de artículos abiertos para que no crezca sin fin",
            "macro.copied" to "Código copiado: ${d}{code}",
            "macro.cleared" to "Se ha borrado el código copiado",
            "macro.otpTitle" to "Código de un solo uso",
            "macro.cTtl" to "segundos hasta borrar la copia (solo cuenta cuando entró directamente)",
            "macro.cCode" to "# La aplicación ya extrajo el código. No hacer nada cuando no pudo.",
            "macro.cTry" to "# Primero intenta meterlo directamente. Esto solo entra mientras **estás mirando la app**:\n",
            "macro.cFallback" to "  # No entró = no estás mirando la aplicación. Pásalo con el botón «Copiar» de la notificación\n",
            "macro.cClear" to "# Pasado el TTL, vacía el portapapeles solo si todavía tiene el código que copiamos\n",
            "macro.cHow" to """
# ■ Copiar números de las notificaciones de llamadas
#   Busca un número primero en el título y luego en el texto y ofrece el botón «Copiar».
#   Incluye números guardados y no guardados; no consulta los contactos.
#   Un nombre solo no permite obtener el número. Solo usa el acceso a notificaciones.
#   Se conserva el nombre unknown-call.sh para mantener las reglas existentes.
""",
            "macro.cIsNum" to """
# ¿Es un número pelado? Quita todos los caracteres que puede llevar un número de teléfono
# (dígitos + - ( ) espacio); si **no queda nada** y quedan entre 7 y 15 dígitos, trátalo como número.
#   -> Un campo con nombres u otras letras no es un número; comprueba el otro campo.
#   -> «Desconocido» / «Número privado» también se caen aquí (afloja la prueba de abajo si los quieres).
# ⚠ No lo escribas como case [!...]: el ) del patrón se lee como el separador del case.
""",
            "macro.cScan" to "# Quien llama suele venir en el título, pero algunas aplicaciones de teléfono lo ponen en el texto. Mira los dos.",
            "macro.cSkip" to "# Si ninguno de los campos contiene un número, no hay nada que copiar.",
            "macro.cWhat" to "# La categoría dice cuál de las dos saltó (el mismo valor del notify:category= que registraste).",
            "macro.cCopy" to """
# El número se pasa con el botón «Copiar» de la notificación (-c). Llamar aquí a z2-clip set no
# entraría: Android 10+ solo deja escribir en el portapapeles a la aplicación de delante, y durante
# una llamada esa es la del teléfono. Al pulsar el botón, z2term pasa delante justo ese instante.
""",
            "macro.missed" to "Llamada perdida",
            "macro.incoming" to "Llamada entrante",
            "macro.title" to "Copiar número de teléfono",
            "macro.stNew" to "nueva",
            "macro.stSame" to "igual",
            "macro.stDiff" to "distinta",
            "macro.msgInstalled" to "instalada:",
            "macro.msgSame" to "ya está instalado lo mismo:",
            "macro.msgOutdated" to "la tuya difiere de la incluida:",
            "macro.msgHowUpdate" to "míralo antes: z2-macro diff %s   /   sustitúyela por la incluida: z2-macro install -f %s (tus cambios se van con ella)",
            "macro.msgNotInstalled" to "todavía no está instalada (ejecuta antes z2-macro install):",
            "macro.msgRemoved" to "borrada:",
            "macro.msgAbsent" to "no está instalada:",
            "macro.msgRemoveDiffers" to "la tuya difiere de la incluida, así que no se ha borrado:",
            "macro.msgHowRemove" to "míralo antes: z2-macro diff %s   /   borrarla igualmente: z2-macro remove -f %s (tus cambios se van con ella)",
            "macro.msgNoDiffTool" to "aquí no hay diff. La incluida se puede leer igualmente con z2-macro show.",
            "macro.msgSameAsBundled" to "idéntica a la incluida.",
            "macro.msgNotFound" to "no existe esa muestra:",
            "macro.msgHintResident" to "Para mantenerla en marcha, registra esto en Ajustes -> Servidores permanentes:",
            "macro.msgHintRun" to "Cómo se ejecuta:",
            "macro.remindBody.head" to """
#
# Preparación (una vez):
#   sh ~/.z2term/macros/remind.sh setup   registra los dos enganches y los dos mosaicos
#
# Sintaxis:
#   remind.sh <cuándo> <texto>       añade un recordatorio
#   remind.sh list                   los lista (el número va primero)
#   remind.sh del <n|all>            cancelar (también vale rm)
#   remind.sh peek                   muestra la lista en una notificación (con botón para borrar)
#   remind.sh ask                    pregunta en el campo de respuesta de una notificación (para el mosaico)
#   remind.sh setup                  registra los enganches y los mosaicos (una vez)
#   remind.sh help                   este texto
#
# <cuándo> - una sola vez:
#   30m / 90s / 2h                   a partir de ahora
#   18:30                            las próximas 18:30 (mañana si ya pasaron)
#   sat 21:00                       próximo día indicado, una vez (hoy si aún no pasó)
#   Japonés: 土曜日の夜九時 = sat 21:00; 朝七時 = 07:00; 夜九時半 = 21:30
#   夜12時 es ambiguo; para medianoche, indica el día y 00:00.
#   tomorrow 18:30 / 3d 07:00        por días (3d = dentro de 3 días)
#   07/30 19:00                      mes/día (el año que viene si ya pasó)
#   2030 07/30 19:00                 con año
#   203007301900 / 07301900          solo dígitos (AAAAMMDDHHMM / MMDDHHMM)
#   ⚠ si no pones la hora, se queda **la hora actual del día** (3d / 07/30 ...)
#
# <cuándo> - repetido:
#   daily 07:00 / weekday 09:00      cada día / de lunes a viernes
#   weekly mon 09:00                 ese día de la semana
#   monthly 15 09:00                 ese día del mes
#   yearly 07/30 19:00               ese mes y ese día
#   every 19:00 / every wed 19:00    «every» a secas también vale (lo decide la palabra siguiente)
#   every 15 19:00 / every 07/30 19:00  -> diario / semanal / mensual / anual
#
# Ejemplos:
#   remind.sh 30m tomar las pastillas
#   remind.sh tomorrow 18:30 sacar la basura
#   remind.sh monthly 25 10:00 el alquiler
#   remind.sh list        ->  1  ⏰ 07/30 18:30  sacar la basura
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (después basta con: remind.sh 30m ...)
""",
            "macro.cStore" to """# Un recordatorio = una línea en ${d}DIR/<id>.txt, separada por tabuladores: tipo / etiqueta / id-de-when / texto.
#   tipo: once (aún por saltar) / fired (ya saltó) / repeat (se repite)
#   id-de-when: solo en repeat (para que al borrar se pueda llamar a z2-when remove). '-' en los demás.
# El texto va al final para que pueda llevar cualquier cosa menos un TAB.""",
            "macro.cParse" to """# Lee de 1 a 3 palabras y decide:
#   KIND ... once|repeat   PLAN ... etiqueta que se muestra   SPEC ... lo que toma z2-alarm/z2-when   USED ... palabras usadas""",
            "macro.pDaily" to "diario ",
            "macro.pWeekday" to "laborables ",
            "macro.pWeekly" to "semanal ",
            "macro.pMonthly" to "mensual ",
            "macro.pYearly" to "anual ",
            "macro.mDay" to "",
            "macro.eDaily" to "diario",
            "macro.eWeekly" to "semanal",
            "macro.eMonthly" to "mensual",
            "macro.eYearly" to "anual",
            "macro.mBadDate" to "no existe esa fecha (p. ej. 07/30):",
            "macro.cEvery" to """  # La forma corta «every ...». **La forma de la palabra siguiente** elige diario/semanal/mensual/anual:
  #   every 19:00 -> diario / every wed 19:00 -> semanal / every 15 19:00 -> mensual /
  #   every 07/30 19:00 -> anual
  # ⚠ El número de palabras no cambia (una palabra sustituye a otra), así que USED sigue valiendo.""",
            "macro.cExpand" to "# Decide, por la palabra que viene detrás, qué repetición quería decir «every».",
            "macro.cDigits" to """    # Formas de solo dígitos: 202607301900 = AAAAMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Comprueba 12 dígitos antes que 8 (el patrón de 8 también encaja en una cadena de 12).""",
            "macro.cYmd" to """  # Escrito como fecha. ⚠ z2-alarm no admite una fecha, así que esto también se reduce a un retardo.""",
            "macro.cCivil" to """# Fecha civil -> días desde el 1970-01-01 (bisiestos incluidos; days_from_civil de Howard Hinnant).
# ⚠ date -d "2026-07-30" no es de fiar en busybox, así que la cuenta se hace aquí.
# ⚠ Solo se usa la diferencia de días y la hora se deja a [day_epoch], con lo que la zona se cancela.""",
            "macro.cCron" to """# Construye una expresión cron (min hora dm mes ds) a partir de HH:MM y un campo de día de la semana.
# NO uses expr para quitar el cero inicial: sale con 1 cuando el resultado es 0, así que también
# corre la parte derecha de `expr "${d}m" + 0 || echo "${d}m"` y el valor acaba ocupando dos líneas.""",
            "macro.afterLabel" to """# "30m" -> "dentro de 30m (14:35)". Ver la hora del reloj deja comprobarlo al momento.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "dentro de ${d}num${d}u (${d}at)" || echo "dentro de ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "uso: remind.sh <cuándo> <texto>",
            "macro.mBadWhen" to "no se entiende la hora (prueba: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "escribe la hora como HH:MM:",
            "macro.mBadRange" to "hora fuera de rango (00:00-23:59):",
            "macro.mNoTime" to "no has puesto la hora (p. ej. daily 07:00):",
            "macro.mBadDow" to "día de la semana desconocido:",
            "macro.mPastTime" to "esa hora ya ha pasado:",
            "macro.cDays" to """    # Formas por días. Sin hora, se queda **la hora actual del día** (nunca inventamos una por omisión).
    # También puede llegar en una sola palabra, así que se acepta la forma pegada.""",
            "macro.cDayEpoch" to """# Convierte «HH:MM, dentro de N días» en segundos epoch. ⚠ busybox no tiene date -d "tomorrow",
# así que primero se saca la medianoche de hoy y luego se le suman los días y la hora.
# ⚠ Un valor con cero delante ("08") lo lee ${d}(()) como octal, así que hay que quitarlo.
# ⚠ Un día se suma como 86400 s, así que puede irse una hora el día del cambio horario.""",
            "macro.cFmtAt" to """# La etiqueta que se muestra en la lista. ⚠ Dejar «tomorrow» se leería mal en cuanto cambie la fecha.""",
            "macro.mNoBody" to "di de qué te tengo que avisar",
            "macro.mNoAlarm" to "no se ha podido programar",
            "macro.mNone" to "no hay nada programado",
            "macro.mFired" to " (ya saltó)",
            "macro.mRemoved" to "borrado:",
            "macro.mNoSuchNum" to "no existe esa entrada (mira remind.sh list):",
            "macro.mUsageDel" to "uso: remind.sh del <n|all>",
            "macro.bDone" to "Hecho",
            "macro.bDelete" to "Borrar",
            "macro.mAskDel" to "¿Cuál borro?",
            "macro.mAskDelH" to "número / all",
            "macro.mDeletedTitle" to "🗑 Borrado",
            "macro.cPeek" to """# Muestra la lista en una notificación (desde el mosaico «lista»). **Conserva los números**: son a lo
# que apuntas para borrar uno. ⚠ La queja era listar sin forma de borrar; de ahí el botón.""",
            "macro.cAskDel" to """# El botón «borrar»: pide un número y se lo pasa a [cmd_del] (el mismo camino que el de la terminal).""",
            "macro.bS1" to "+10min",
            "macro.bS2" to "+1h",
            "macro.mAgain" to "- otra vez en",
            "macro.mTitle" to "⏰ Recordatorios",
            "macro.mAsk1" to "¿De qué te aviso?",
            "macro.mAsk1H" to "p. ej. tomar las pastillas",
            "macro.mAsk2" to "¿Cuándo?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "vuelve a escribirlo",
            "macro.mAskGiveUp" to "No se ha entendido ${ASK_TRIES} veces. También puedes añadirlo desde la terminal: remind.sh 30m tomar las pastillas",
            "macro.mOkTitle" to "⏰ Recordatorio programado",
            "macro.mNgTitle" to "⚠ No se ha programado",
            "macro.cHooks" to """  # Un enganche para «ha saltado un recordatorio» y otro para «se ha pulsado el botón de una
  # notificación». Solo estos dos, por muchos recordatorios que añadas. Ninguno depende de la detección.""",
            "macro.cTiles" to """  # Solo rellena los huecos vacíos y los que ya son nuestros (nunca pisa lo de otro).
  # Un nombre de macro con argumentos vale desde 0.8.275 (antes de eso, escribe sh + la ruta completa).""",
            "macro.mSetupHooks" to "enganches registrados:",
            "macro.mSetupTiles" to "mosaicos:",
            "macro.mPathHint" to "Nota: en esta pestaña todavía no se puede ejecutar remind.sh por su nombre. Abre una pestaña nueva, o ejecuta aquí ",
            "macro.cPathHint" to """  # ⚠ La carpeta de macros solo está en el PATH desde 0.8.287, así que **las pestañas abiertas antes**
  # siguen con el de antes. Comprobamos si el nombre se resuelve y decimos qué hacer si no
  # (si no, el `command not found` no tiene ninguna razón visible).""",
            "macro.mPlace" to "Nota: los mosaicos todavía tienes que colocarlos tú, desde la pantalla del lápiz (editar) de los ajustes rápidos.",
            "macro.lRemind" to "recordar",
            "macro.lList" to "lista",
            "macro.cSelf" to "# Reaccionar solo a nuestras propias notificaciones (nuestros ids empiezan siempre por r)",
            "macro.cKeepRepeat" to "      # No borrar aquí uno que se repite: mañana tiene que volver a saltar.",
            "macro.cAsk" to """# El camino que se usa desde el mosaico o una notificación. ⚠ **Responde siempre con una notificación**:
# aquí nadie está mirando una terminal, así que fallar por stderr se lee como «lo he tocado y no ha
# pasado nada» (sí pasó: la razón solo llegó al run.log del mosaico).
#   Si no se entiende -> di por qué y vuelve a preguntar, hasta $ASK_TRIES veces (la respuesta anterior se conserva)
#   Si se programa    -> muestra el plan y el texto en una notificación""",
            "macro.cSnoozeCancel" to "quita la alarma de la posposición si se marcó como hecho estando pospuesto",
            "macro.qrBody.head" to """
#
# Uso:
#   qr.sh "https://example.com"       codifica una cadena y la dibuja aquí
#   qr.sh -f notas.txt                codifica el contenido de un archivo
#   z2-clip get | qr.sh               codifica lo que hay en el portapapeles
#   qr.sh -o ~/qr.png "texto"         guarda un PNG (no se dibuja nada)
#   qr.sh -t "texto"                  imprime bloques en vez de una imagen
#   qr.sh -s 24 "texto"               elige el tamaño (en columnas; por omisión se ajusta al ancho)
#
# z2-run: qr.sh "https://example.com"   (una vez instalada basta el nombre. Corre una vez)
#
# # Requisitos (sin esto no funciona, o se ve distinto)
#
#   1) Codifica la aplicación ... dentro de una pestaña de z2term no hay nada que
#      instalar (z2-qr encode). Si ya tienes qrencode, se usa ese. Donde la app no
#      llega (por ssh), instálalo una vez por pestaña (distribución):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Solo si no hay ninguno se imprime el comando y el script se para.
#
#   2) La imagen (por omisión) solo se ve dentro de una pestaña de esta aplicación ... se dibuja
#      con gráficos de Kitty. Por ssh, o en cualquier terminal que no pueda mostrar imágenes,
#      lo que sale es un chorro de caracteres sin sentido. Allí usa -t.
#
#   3) -t (bloques) depende de la fuente de la terminal ... las fuentes que dejan huecos entre
#      filas se siguen leyendo a ojo, pero no con una cámara. Para escanearlo con una cámara,
#      es mejor la imagen por omisión o el PNG de -o.
#
#   4) La proporción se supone ... mira «Proporción» más abajo. ¿Aplastado? Z2_QR_ASPECT.
#
# Opciones:
#   -f ARCHIVO  lee la entrada de un archivo (si no, los argumentos; si no, la entrada estándar)
#   -o PNG      guarda un PNG en vez de dibujar. Si salen varios códigos se llaman
#               qr-1.png / qr-2.png ...
#   -t          imprime bloques. Para terminales que no pueden mostrar imágenes, y por ssh
#   -s N        tamaño en columnas al dibujar. Por omisión hasta 34, limitado por el ancho
#   -h          esta ayuda
#
# Sobre las entradas largas:
#   En un código cabe una cantidad fija (como mucho 2953 bytes alfanuméricos; unos pocos cientos
#   si además tiene que poder escanearse). Una entrada de más de 900 bytes se parte por los saltos
#   de línea en varios códigos, numerados [1/3] e impresos en orden.
#   AVISO una sola línea demasiado larga para caber en un código no se puede codificar.
#
# Proporción:
#   Las terminales no dicen el tamaño de su celda, así que se supone una proporción de carácter
#   de 1:2 para que el código salga cuadrado. Si se ve aplastado, ajústalo con una variable:
#     Z2_QR_ASPECT=0.45 qr.sh "texto"     (cuanto menor, más alto. Por omisión 0,5)
""",
            "macro.cMaxBytes" to "máximo de bytes por código (a partir de ahí se parte)",
            "macro.cPngModule" to "puntos por módulo en el PNG que se guarda",
            "macro.cTargetPx" to "ancho en píxeles al que se apunta al dibujar",
            "macro.cMargin" to "zona de silencio alrededor del código (en módulos; hace falta para escanearlo)",
            "macro.cUsageFn" to "Imprime el bloque de comentarios del principio como texto de ayuda (sin fijar el número de líneas).\n",
            "macro.cNeedEncoder" to "Requisito 1: algo con lo que codificar: qrencode si está, si no la aplicación.",
            "macro.mMissing" to "qr.sh: aquí no hay con qué codificar: ni qrencode ni acceso a la aplicación (z2-qr)",
            "macro.mInstallOnce" to "  Instálalo una vez en esta pestaña:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Crea el PNG. qrencode elige el tamaño de módulo probando; la app recibe el objetivo.",
            "macro.cMakeBlocks" to "Lo dibuja con caracteres de bloque. Ambos emiten su propio color, valga el tema que valga.",
            "macro.qrBody.mNoTmp" to "no se puede crear un directorio de trabajo",
            "macro.qrBody.mUnreadable" to "no se puede leer:",
            "macro.qrBody.mEmpty" to "entrada vacía",
            "macro.mSizeNum" to "-s toma un número",
            "macro.qrBody.mUsageHint" to "mira qr.sh -h",
            "macro.mPieceFail" to "qr.sh: no se puede construir el código %d: %s",
            "macro.qrBody.cCollect" to "---- reunir la entrada ----",
            "macro.cSplit" to "---- partir por los saltos de línea cuando es demasiado larga ----",
            "macro.cSplit2" to "Pasa a la pieza siguiente justo antes de que el total acumulado supere MAX_BYTES. Las líneas\n# nunca se cortan por la mitad, así que el texto multibyte sobrevive.",
            "macro.cTrim" to "Quita el salto de línea final (nunca codificar un salto que la entrada no tenía).",
            "macro.cSize" to "---- elegir el tamaño de la imagen a partir del ancho de la terminal ----",
            "macro.cPngWidth" to "Devuelve el ancho en píxeles de un PNG (bytes 16-19, el IHDR).",
            "macro.cModulePx" to "Calcula hacia atrás, desde el ancho en píxeles al que se apunta, los puntos por módulo.",
            "macro.cInline" to "Dibuja un PNG en su sitio con el protocolo de gráficos de Kitty.\n# c/r fijan cuántas celdas ocupa; después se imprimen esos saltos de línea para llevar el cursor debajo.",
            "macro.cEmit" to "---- emitir ----",
            "macro.cAnsi" to "ANSIUTF8 emite sus propios colores, así que el claro/oscuro sale bien sea cual sea el tema.",
            "macro.mdBody.head" to """
#
# Uso:
#   md.sh README.md                lo formatea y lo muestra aquí
#   md.sh -v README.md             lo abre dentro de la aplicación (z2-view)
#   cat notes.md | md.sh           lee de la entrada estándar
#   md.sh -o page.html README.md   solo escribe el HTML (no abre nada)
#
# z2-run: md.sh README.md   (una vez instalada, basta el nombre. Corre una vez)
#
# Opciones:
#   -v        lo abre dentro de la aplicación (z2-view)
#   -o FILE   escribe ahí el HTML. No muestra nada aquí
#   -w N      corta las líneas a N columnas (por defecto: el ancho de la pantalla, hasta 100)
#   -p        sin color ni adornos (texto tal cual)
#   -t TÍTULO título de la pantalla de lectura (por defecto: el primer encabezado, si no el nombre)
#   -h        esta ayuda
#
# Un documento largo, pantalla a pantalla: md.sh README.md | less -R   (-R conserva el color)
#
# No hay que instalar nada: con sh y awk basta.
# Admite encabezados, párrafos, listas (anidadas, numeradas, [ ]/[x]), citas, código,
# tablas, filetes, enlaces, imágenes y el encabezamiento --- del principio.
# ATENCIÓN los encabezados subrayados (=== / ---) y los enlaces por referencia
# ([texto][1]) salen tal y como están escritos.
#
# Qué esperar:
#   - Las imágenes salen en su sitio solo dentro de una pestaña de esta aplicación
#     (las dibuja z2-img con kitty graphics). Por ssh verás el pie y la ruta.
#   - Los enlaces se pueden tocar (OSC 8). Un terminal sin eso los muestra como texto.
#   - Este terminal no dibuja cursiva, así que *el énfasis* va subrayado (como en man).
#   - Una tabla que no cabe se despliega en líneas «encabezado: valor».
#   - -v incrusta en la página las imágenes que están en el dispositivo (1,5 MB cada una).
#     La pantalla de lectura no descarga nada, así que las de http(s) quedan como pie.
""",
            "macro.mdBody.cCollect" to "# Reúne la entrada (varios archivos se encadenan en una sola lectura).",
            "macro.cTitle" to "# Título: -t, si no el primer encabezado #, si no el nombre del archivo",
            "macro.cWidth" to "# El ancho de la pantalla",
            "macro.cColorEnv" to "# Salida para cuando el color sí (o no) debe pasar por una tubería, p. ej. a un paginador",
            "macro.cImgWhere" to "# Una imagen solo sale en su sitio dentro de una pestaña (kitty graphics) y con z2-img.",
            "macro.cAmbi" to "# Cuánto miden los caracteres ambiguos (filetes, flechas, dígitos en círculo). Usa\n" +
            "# Z2_MD_AMBIWIDTH=2 si en Ajustes están como ancho completo. Por defecto 1, como la app.",
            "macro.cAwkFile" to "# ATENCIÓN el programa awk se escribe en un ARCHIVO y se pasa con -f. Si se pasa como\n" +
            "#   argumento, gawk lo corta donde un carácter multibyte (como el japonés de estas\n" +
            "#   notas) cruza su límite de lectura de 4096 bytes, y muere con\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Incrusta las imágenes en la página de lectura como URI data:.\n" +
            "# ATENCIÓN z2-view no descarga nada (JavaScript y cargas están apagados), así que una\n" +
            "#   imagen sin incrustar simplemente no aparece. 4 MB para la página entera.",
            "macro.cImgLoop" to "# Solo un documento con imágenes se lee línea a línea (para llamar a z2-img en su sitio)",
            "macro.mdBody.mUsageHint" to "consulta md.sh -h",
            "macro.mdBody.mNoTmp" to "no se puede crear un directorio temporal",
            "macro.mdBody.mUnreadable" to "archivo ilegible:",
            "macro.mdBody.mEmpty" to "no hay nada que leer",
            "macro.mWidthNum" to "-w necesita un número",
            "macro.mNoDir" to "no se puede crear la carpeta donde dejarlo",
            "macro.mConvFail" to "la conversión ha fallado",
            "macro.mNoView" to "aquí no hay z2-view (hace falta actualizar la aplicación). El HTML está en:",
            "macro.mImgLabel" to "[imagen]",
            "menu.noApps" to "(no se encontró ninguna aplicación)",
            "menu.cat.AudioVideo" to "Sonido y vídeo",
            "menu.cat.Development" to "Desarrollo",
            "menu.cat.Graphics" to "Gráficos",
            "menu.cat.Game" to "Juegos",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Oficina",
            "menu.cat.System" to "Sistema",
            "menu.cat.Utility" to "Utilidades",
            "menu.cat.Other" to "Otros",
            "menu.usage" to "uso: z2menu [list]   (sin argumentos: XML de pipe menu de openbox)",
            "reminderView.labels" to "Recordatorios|Añadir|Eliminar|Contenido|Fecha y hora|Repetición|Una vez|A diario|Días laborables|Semanal (día seleccionado)|Horario escrito (opcional; sustituye fecha y repetición)|Programados|Recurrentes|Notificados|No hay recordatorios|¿Eliminar este recordatorio y sus avisos pendientes?",
            "rssFeeds.rssFeedsLabel" to "Suscripciones",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} no se inició. Consulta /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: no se encontró ningún gestor de paquetes compatible (apk/apt-get/pacman).",
            "scan.mInstalling" to "z2scan: instalando:",
            "scan.mInstallFail" to "z2scan: falló la instalación:",
            "scan.mRemoteBlocked" to "z2scan: objetivo externo rechazado. Analiza solo localhost, o añade --allow-remote solo para un objetivo que tengas autorización para probar.",
            "scan.mRemoteWarn" to "z2scan: AVISO vas a analizar un host externo. Hazlo solo con sistemas que tengas autorización expresa para probar.",
            "scan.mNoLynis" to "z2scan: no hay lynis; se recurre a la autocomprobación integrada ('z2scan self'). Puedes instalarlo con 'z2scan setup'.",
            "scan.mNoCve" to "z2scan: no se encontró ningún escáner de CVE (trivy/grype). Instala trivy o grype y vuelve a ejecutarlo.",
            "scan.mSelfHead" to "== autocomprobación de z2scan (este dispositivo / localhost) ==",
            "scan.mUidRoot" to "ejecutándose con uid 0 (nota: proot/z2root da un root simulado)",
            "scan.mPubPort" to "puerto TCP a la escucha en todas las interfaces (no solo en localhost), puerto",
            "scan.mSshdEmpty" to "sshd_config permite iniciar sesión con contraseña vacía (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config habilita la autenticación por contraseña (PasswordAuthentication yes); la de clave es más segura",
            "scan.mSshdRoot" to "sshd_config permite iniciar sesión como root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys tiene permisos demasiado abiertos, modo",
            "scan.mSshDir" to "~/.ssh tiene permisos demasiado abiertos, modo",
            "scan.mWorldWrite" to "archivos que puede escribir cualquiera (se muestran hasta 20):",
            "scan.mSuid" to "hay binarios SUID (informativo; bajo proot/z2root el root es simulado):",
            "scan.mPath" to "PATH contiene un elemento vacío o '.' (meter el directorio actual en PATH es arriesgado)",
            "scan.mFound" to "detecciones:",
            "scan.mClean" to "no se han encontrado problemas evidentes.",
            "scan.mSaved" to "z2scan: referencia guardada:",
            "scan.mSaveFail" to "z2scan: no se pudo guardar la referencia:",
            "scan.mNoBase" to "z2scan: todavía no hay referencia. Ejecuta 'z2scan self --save' una vez para anotar el estado actual.",
            "scan.mNoChange" to "[ OK ] sin cambios respecto a la referencia.",
            "scan.mAdded" to "== nuevo respecto a la referencia ==",
            "scan.mRemoved" to "== ya no está respecto a la referencia ==",
            "scan.mCleared" to "z2scan: referencia eliminada.",
            "scan.mLangDiff" to "z2scan: AVISO la referencia se guardó en otro idioma, así que todo aparecerá como cambiado. Vuelve a guardarla con 'z2scan self --save'.",
            "scan.langTag" to "es",
            "scan.usageText" to """
        z2scan - pruebas de seguridad de este dispositivo / localhost (defensivo, sin enviar nada fuera).

        Por omisión solo analiza localhost. Los escáneres se instalan desde los paquetes
        oficiales de tu distribución (nmap/lynis/trivy); no se incluye ninguno y los
        resultados se quedan en el dispositivo.

                  z2scan self [--save]         autocomprobación integrada (sin herramientas
                                               externas): puertos abiertos, configuración de
                                               sshd, permisos de las claves SSH, archivos
                                               world-writable/SUID, PATH. Con --save guarda
                                               además el resultado como referencia.
                  z2scan diff [--quiet]        repite la comprobación e imprime solo lo que
                                               ha cambiado desde la referencia. Sale con 1
                                               cuando hay algo nuevo, así que encaja tal cual
                                               en z2-when. Con --quiet no imprime nada si no
                                               ha cambiado nada.
                  z2scan baseline [clear]      muestra la referencia guardada (clear la borra)
                  z2scan setup                 instala los escáneres (nmap, lynis) con
                                               apk/apt-get/pacman
                  z2scan net [--allow-remote] [objetivo]
                                               análisis TCP con nmap. El objetivo por omisión
                                               es 127.0.0.1. Uno no local exige --allow-remote.
                  z2scan host                  auditoría del host con lynis (si no está,
                                               recurre a 'self')
                  z2scan cve                   busca CVE conocidos en el rootfs con
                                               trivy/grype, si están instalados

        Analiza únicamente sistemas que tengas autorización expresa para probar.
    """,
            "translationMacro.description" to "Traduce texto con un comando instalado por separado.",
            "translationMacro.missing" to "Falta el comando trans en este entorno local. Instala la CLI de traducción y reintenta. No hay descarga automática.",
            "translationMacro.network" to "El texto se envía al servicio externo de la CLI. Se aplican sus condiciones y disponibilidad de red.",
            "translationSetup.setup" to "Abre una terminal en el mismo entorno Linux local, ejecuta el comando y vuelve a traducir.",
            "translationSetup.alpineRepo" to "Si falta el paquete, activa community para la misma versión de Alpine en /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Si falta el paquete en Ubuntu, activa el repositorio universe y reintenta.",
            "translationSetup.unknown" to "No se reconoce la distribución. Elige el comando para tu entorno Linux local:",
            "voice.head" to """# voice.sh — recibe las «Órdenes por voz»: lo que dices maneja música, volumen, temporizadores, recordatorios, el tiempo y más.
#
# Preparación (una vez):
#   sh ~/.z2term/macros/voice.sh setup    registra 4 reglas (órdenes por voz / órdenes por voz con bloqueo / temporizador por voz / contar pasos)
#   Contar pasos solo mantiene activo el contador para preguntar por los pasos de hoy; no ejecuta nada
#   luego habla con el icono de ajustes rápidos «Órdenes por voz» o con el asistente digital (mantener el botón de encendido, etc.)
#
# Desde una terminal:
#   voice.sh words                 abre la tabla de palabras (~/.z2term/voice/words-<idioma>.txt; añade o cambia frases)
#   voice.sh try <frase>           prueba una sin hablar (p. ej. voice.sh try siguiente canción)
#   voice.sh listen                abre la pantalla de escucha y habla (igual que el icono)
#   voice.sh lang [idioma]         idioma de la tabla de palabras (voice.sh lang - los lista)
#   voice.sh ai [off|codex|claude|agy]   si se usa la IA (desactivada al principio)
#   voice.sh model [nombre]        el modelo de la IA (vacío = el de esa IA por defecto)
#   voice.sh setup / help
#
# La IA empieza desactivada. Aun activada, solo se le pregunta si empiezas con la palabra clave («IA», «pregunta»...).
# El tiempo consulta wttr.in solo cuando dices «tiempo» (lugar: weather_city= en ~/.z2term/voice/config).
# Tus propias palabras: «palabras = orden» en ~/.z2term/voice/commands.txt (se miran antes que la tabla)
# Nombres para llamar: «nombre número» en ~/.z2term/voice/contacts.txt / registro: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — macro de iniciación. Reacciona a lo que pasa en el dispositivo.",
                "# La espera la hace la aplicación (z2-when), así que no queda residente: corre una vez y termina.",
                "# Preparación: Ajustes -> «Detección de eventos del sistema» activada",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (auriculares: otra regla con 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# Lo que ha pasado llega en Z2_WHEN_EVENT (z2-when events lista los nombres).",
                "# Para probarlo a mano:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT está vacío. Para probarlo a mano: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"Ha empezado la carga\" ;;",
                "  power_disconnected) z2-toast \"Se ha detenido la carga\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — avisa cuando queda poca batería, mirando antes cómo está el dispositivo.",
                "# Usa z2-state para ver si la pantalla está encendida: toast si lo está, notificación si no.",
                "# Preparación: Ajustes -> «Detección de eventos del sistema» activada",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# z2-when entrega el nivel; recurrimos a z2-state para que esto también corra a mano.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# No decir nada mientras carga (no se está gastando de verdad)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Batería ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Batería baja\" \"Queda un ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — lee en voz alta la batería y la conexión cada mañana.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# Lo despierta la alarma del sistema, así que también salta en Doze (puede llegar unos minutos tarde).",
                "# No depende de los interruptores de detección.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=móvil; fi",
                "z2-say \"Buenos días. Batería al ${d}{level} por ciento, conexión por ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — copia un código de un solo uso sacado de las notificaciones y lo borra",
                "# a los TTL segundos si el portapapeles todavía tiene ese mismo valor.",
                "# Preparación: Ajustes -> «Detección de notificaciones» activada + acceso a las notificaciones del sistema",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ puede tapar los códigos en notificaciones sensibles. Para un camino fiable usa",
                "#    la variante de SMS (otp-sms.sh): el cuerpo de un SMS nunca se tapa.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — copia un código de un solo uso sacado de los SMS que llegan y lo borra",
                "# a los TTL segundos si el portapapeles todavía tiene ese mismo valor.",
                "# Es la variante de SMS de otp-clip.sh. A diferencia de las notificaciones, el cuerpo de un SMS",
                "# no lo tapa la protección de notificaciones sensibles (Android 15+) y funciona con el móvil bloqueado.",
                "# Preparación: Ajustes -> «Detección de SMS» activada + permiso de SMS del sistema",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — muestra una notificación para copiar números de las notificaciones de llamadas.",
                "# Su botón «Copiar» pone el número en el portapapeles (por qué espera a que lo pulses: más abajo).",
                "# Preparación: Ajustes -> «Detección de notificaciones» activada + acceso a las notificaciones del sistema",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Para recoger también las llamadas perdidas, registra otra regla (mismo script, otra categoría):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — consulta los feeds y se queda solo con lo nuevo, como notificación y como texto.",
                "# A diferencia de las demás muestras, esta **no** queda residente: es la forma",
                "# «corre una vez desde un disparador de hora y termina».",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — abre lo que reunió rss.sh, de lo más nuevo a lo más viejo, un artículo por toque.",
                "# Aparece como botón de macro en el widget de estado, así que cada toque abre el siguiente.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — te avisa con una notificación: una sola vez (en 30m, a las 18:30) o",
                "# repetido (cada día / entre semana / cada semana). Salta con la aplicación cerrada (lo despierta",
                "# una alarma del sistema; no hace falta ningún interruptor de detección). Pospón desde los botones.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — convierte texto o un archivo en un código QR: dibujado aquí o guardado como PNG.",
                "# Pasa algo a otro dispositivo sin volver a teclearlo. Corre una vez, sin quedar residente.",
            ),
            "macro.md" to listOf(
                "# md.sh — lee aquí un archivo Markdown ya formateado, o ábrelo dentro de la aplicación.",
                "# Títulos, listas, tablas, código y enlaces; las imágenes salen en su sitio. Corre una vez.",
            ),
            "macro.cImportantTemplate" to listOf(
            "# Un feed o una palabra por línea que no deba quedar enterrada (las líneas que empiezan por # se ignoran).",
            "# Vale parte de una URL o parte de un título. Cada coincidencia recibe su propia notificación.",
            "# p. ej.",
            "#example.org",
        ),
            "macro.usage" to listOf(
            "uso: z2-macro <subcomando>",
            "  list                  lista las muestras incluidas (marcadas nueva / igual / distinta)",
            "  install <nombre|all>  copia en ~/.z2term/macros/ (nunca sobrescribe; -f fuerza)",
            "  remove <nombre>       borra tu copia de una muestra incluida (se detiene si difiere; -f fuerza)",
            "  diff <nombre>         diferencias entre tu copia y la incluida (la tuya a la izquierda)",
            "  show <nombre>         imprime el script",
            "  run <nombre>          lo ejecuta aquí (Ctrl-C para pararlo)",
            "  dir                   imprime dónde viven las macros",
                "  actions ...           Macros de acciones Android (z2-action --help)",
        ),
            "rssFeeds.labels" to listOf(
            "Suscripciones", "Añadir", "Artículos", "Cambiar", "Eliminar", "URL del feed",
            "¿Eliminar esta suscripción? Los artículos guardados se conservarán.",
            "Los cambios se aplican en la próxima consulta. Se conservan los artículos guardados.",
            "No hay suscripciones. Usa Añadir para introducir una URL RSS / Atom.",
            "Introduce una URL HTTP(S) sin espacios ni credenciales (máximo 2048 caracteres).",
            "Esta suscripción ya existe.",
            "Esta suscripción ha cambiado. Actualiza la lista e inténtalo de nuevo.",
            "Se está guardando otro cambio. Inténtalo de nuevo.",
            "Anterior", "Siguiente",
        ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols" to 8,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の Español 版。上書きしていない文言は英語で出る。 */
private class ApiMsgEs(d: String) : Z2ApiMsg("es", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — elegir un documento, copiarlo a HOME y devolver su ruta.\n# z2-file save FILE [MIME] — elegir destino y guardar una copia.\n# Cancelar devuelve error. Máximo 512 MiB; selección de hasta 5 minutos."

    override val usbHelp: String get() = """
        |# z2-usb list              … lista los dispositivos USB conectados al teléfono
        |# z2-usb allow [número]    … pide a Android permiso para usarlo desde un programa Linux
        |# Ejecuta allow una vez después de conectar el dispositivo. El permiso dura hasta desconectarlo.
    """.trimMargin()

    override val usbUsage: String get() = "uso: z2-usb list | z2-usb allow [número|ruta|VID:PID]"

    override val usbNoDevices: String get() = "No hay ningún dispositivo USB conectado al teléfono."

    override val usbNeedSelector: String get() = "Hay más de un dispositivo USB conectado; indica su número según z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Aprueba el acceso USB en la pantalla de Android: $path"

    override fun usbAlreadyAllowed(path: String): String = "El acceso USB ya está permitido: $path"

    override fun usbNotFound(selector: String): String = "No se encontró el dispositivo USB: $selector"

    override val usbStateAllowed: String get() = "permitido"

    override val usbStateNeedsAllow: String get() = "falta permiso"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NOMBRE] [-c TEXTO] [-b ETIQUETA]... "título" "texto"  /  z2-notify [-h] "texto"
        |#   -h / --high / --banner : mostrarla como banner (heads-up) en lo alto de la pantalla
        |#   -b <etiqueta>          : añade un botón de respuesta (hasta 3). Al pulsarlo se añade
        |#                            notify_action a ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NOMBRE,"action":ETIQUETA})
        |#   -n <nombre>            : identificador de esta notificación (para distinguir respuestas)
        |#   -c <texto>             : añade un botón «Copiar» que pone <texto> en el portapapeles.
        |#                            Úsalo en vez de z2-clip set cuando la macro corra en segundo
        |#                            plano: Android 10+ solo deja escribir en el portapapeles a la
        |#                            aplicación que está delante, así que un z2-clip set sin nadie
        |#                            delante se descarta en silencio.
    """.trimMargin()

    override val notifyUsage: String get() = "uso: z2-notify [-h] [-n nombre] [-c texto] [-b etiqueta]... <título> [texto]"

    override val toastHelp: String get() = """
        |# z2-toast <mensaje> … un mensaje corto abajo en la pantalla (un toast).
        |# Se desvanece solo y no deja nada. Usa z2-notify cuando tenga que quedarse,
        |# o cuando quieras un botón que pulsar.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … abre las herramientas QR con la cámara ya leyendo.
        |# Tras leer, un botón abre el contenido en su app (navegador, LINE y otras apps, teléfono,
        |# correo, SMS, Wi-Fi, contactos, calendario). Nada se abre solo.
        |# Puedes ponerlo en un mosaico de ajustes rápidos o en un panel lateral.
        |# z2-qr encode [-o ARCHIVO.png] [-p PÍXELES] [-m MÓDULOS] [-t] [TEXTO] … crea un QR.
        |#   No hay que instalar nada: lo codifica la aplicación (el mismo codificador que la
        |#   pantalla de QR). Sin TEXTO lee la entrada estándar. -o es dónde va el PNG
        |#   (por defecto ~/.z2term/qr/qr.png) y se imprime la ruta. -t lo dibuja aquí con
        |#   caracteres de bloque. -p es el tamaño buscado en píxeles (600) y -m el margen
        |#   en módulos (4). Hasta 2000 bytes.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <texto> … pasa el texto al menú de compartir de Android (lo envía a otra app).
        |# --file ARCHIVO [ARCHIVO ...]: envía archivos (hasta 32, 512 MiB en total).
        |# Las rutas se leen en el shell local actual. -- fuerza texto. Las copias de más de 24 horas se borran al volver a compartir.
        |# Todos los argumentos se unen en un solo cuerpo. La aplicación de destino se elige en la
        |# pantalla, así que hace falta alguien delante: no es para una macro que corre sola.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <archivo.html> [título] … lee dentro de z2term una página hecha aquí.
        |# Sin servidor ni navegador: el archivo se entrega a la aplicación y se muestra tal cual.
        |# JavaScript y las cargas de red están desactivados, así que la página no ejecuta nada.
        |# Los enlaces http:// y https:// se abren en tu navegador habitual.
        |# El tema del teléfono llega como variables CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), así la página sigue los colores del terminal.
        |# Hasta 4 MB. Las imágenes deben ir incrustadas (data: URI); las remotas no se descargan.
        |# --edge PANEL:ELEMENTO envía a un elemento existente de tipo type=view.
        |# --controls ARCHIVO.json define formularios y acciones para una macro instalada.
        |# La vista se actualiza manualmente por defecto. view-refresh=auto ejecuta run= al abrir; z2-view vuelve al mismo elemento.
        |# Con - o una tubería (ls -l | z2-view) lee la entrada estándar. El texto que no empieza por < se muestra
        |# ajustado en letra monoespaciada y sin códigos de color; --text / --html lo fijan.
        |# Pellizcar cambia el tamaño del texto; --pinch page amplía la página entera. --text-size 50..300 fija
        |# el tamaño del texto (%) de esta página. Ambos son para pantalla completa; un elemento de panel usa sus ajustes.
        |# --no-refresh / --no-find ocultan los botones Actualizar / Buscar de la pantalla completa (visibles por defecto).
    """.trimMargin()

    override val viewUsage: String get() = "uso: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <archivo|-> [título]"

    override val viewUnreadable: String get() = "z2-view: archivo ilegible:"

    override val openHelp: String get() = """
        |# z2-open <url|ruta> … lo abre con la aplicación predeterminada (https://… o /sdcard/…).
        |# Qué aplicación lo abre lo decide Android; z2term solo se lo entrega.
        |# La ruta es una ruta del **teléfono** (/sdcard/…), no de dentro de la distribución.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLUMNAS] [-r FILAS] [--clear] [-f] <archivo|->… … dibuja la imagen en la terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Pasa - para leer una imagen de la entrada estándar.
        |#   -w <columnas>  ancho en columnas de la terminal (por omisión: ajustarla a la terminal)
        |#   -r <filas>     alto en filas de la terminal (junto con -w se usa el tamaño tal cual)
        |#   --clear        borra todas las imágenes dibujadas hasta ahora
        |#   -f             envíala aunque la salida estándar no sea una terminal
        |# ⚠ Las imágenes solo salen en una pestaña de z2term o en una terminal que hable el protocolo
        |#   de gráficos de kitty. En cualquier otro sitio (por ssh, dentro de un paginador) solo
        |#   verás caracteres sin sentido.
        |# ⚠ La proporción supone que una celda es el doble de alta que de ancha. Si se ve aplastada,
        |#   ajústala: Z2_IMG_ASPECT=0.45 z2-img foto.jpg (cuanto menor, más alta).
    """.trimMargin()

    override val imgUsage: String get() = "uso: z2-img [-w columnas] [-r filas] [--clear] [-f] <archivo|->..."

    override val imgNoFile: String get() = "no se puede leer el archivo:"

    override val imgNotTty: String get() = "la salida estándar no es una terminal, así que no se envió nada (-f lo envía igualmente)."

    override val imgNoSize: String get() = "no se puede leer el tamaño en píxeles; se dibuja como un cuadrado:"

    override val qrUsage: String get() = "uso: z2-qr  |  z2-qr encode [-o ARCHIVO.png] [-p PÍXELES] [-m MÓDULOS] [-t] [TEXTO]"

    override val qrEncodeFailed: String get() = "z2-qr: no se ha podido crear el código (¿texto demasiado largo? hasta 2000 bytes)"

    override val clipHelp: String get() = """
        |# z2-clip get         … imprime el portapapeles en la salida estándar
        |# z2-clip set [texto] … pone el texto (o la entrada estándar si se omite) en el portapapeles
        |# ⚠ Android 10+ solo deja tocar el portapapeles a la aplicación que está delante (y al método
        |#   de entrada en uso). Desde una macro en segundo plano se descarta sin decir nada, así que
        |#   allí se usa z2-notify -c <texto>: añade un botón «Copiar» que siempre entra.
    """.trimMargin()

    override val batteryHelp: String get() = "# Imprime el nivel y el estado de carga en JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (200 ms por omisión)"

    override val editHelp: String get() = "# z2-edit <archivo>   … edita un archivo de texto en el editor de z2term (caja de texto normal; Guardar lo escribe)"

    override val sayHelp: String get() = "# z2-say <texto>      … lee en voz alta con el TTS del sistema (lee stdin si no hay argumento)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (toggle por omisión). status consulta el estado real (on/off), incluidos cambios externos."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … captura solo la parte que rodeas (free por omisión). Requiere Android 11 y las acciones Android de z2term."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (playpause por omisión)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Volumen de medios. Imprime el current/max resultante."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (light por omisión). Lee una muestra y devuelve JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACCIÓN] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Lanza cualquier Intent de Android (startActivity por omisión). El primer argumento que no sea una opción es la ACCIÓN.
        |# --window full|freeform|split|ask elige el modo. Freeform reutiliza tareas y deja el tamaño al sistema.
        |# --profile N abre la app (-p/-n) en otro perfil, como el de trabajo. Sin pantalla dividida. N figura en el comando que el panel lateral guarda para esa app.
        |# --reuse-task / --os-bounds siguen aceptándose; --bounds-only pide un tamaño del 80% y excluye --os-bounds.
        |# --no-scale desactiva la solicitud de ventana libre reducida de Motorola.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … el estado actual del dispositivo, entero, en JSON
        |# z2-state <clave>    … solo ese valor, en bruto (se mete tal cual en una condición)
        |# claves: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# p. ej. [ "${d}(z2-state charging)" = "true" ] && echo cargando
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEG] [-H PISTA] [-d PREDET] <pregunta>
        |#   Hazle una pregunta a la persona e imprime su respuesta en la salida estándar.
        |#   La pregunta llega como una notificación con **campo de respuesta**, así que se contesta
        |#   desde el panel sin abrir la aplicación (también puede preguntar una macro que corre en
        |#   segundo plano).
        |#   -t <seg>    cuánto esperar la respuesta (300 = 5 min por omisión)
        |#   -H <pista>  rótulo que se muestra en el campo de respuesta
        |#   -d <texto>  respuesta propuesta (se muestra en la notificación)
        |# Si se descarta la notificación sin contestar, o se acaba el tiempo, sale con un código
        |# distinto de cero y no imprime nada, así que el «o me rindo» se escribe tal cual:
        |#   nombre=${d}(z2-ask "¿Nombre de la rama?") || exit 1
        |# Compáralo con z2-notify -b <etiqueta>, que solo ofrece las opciones que prepares.
    """.trimMargin()

    override val askUsage: String get() = "uso: z2-ask [-t seg] [-H pista] [-d predeterminada] <pregunta>"

    override val listenHelp: String get() = """
        |# z2-listen [-w PALABRA,PALABRA,...] [-l IDIOMA]
        |#   Imprime en la salida estándar lo que dices. El reconocimiento se hace **solo en el
        |#   dispositivo**; tu voz nunca sale a la red. Requiere Android 12+ y reconocimiento de voz
        |#   en el dispositivo (los datos sin conexión de tu idioma).
        |#   Habla con z2term en pantalla (Android solo da el micrófono a la app que está en pantalla).
        |#   Termina al quedarte en silencio. La primera vez pide permiso para el micrófono.
        |#   -w <palabras>  acepta solo estas palabras (separadas por comas) e imprime la oída.
        |#                  Se revisan todas las propuestas del reconocedor, no solo la primera.
        |#   -l <idioma>    idioma a reconocer (p. ej. es-ES, en-US; por omisión el del dispositivo)
        |# Salida: 0 = hecho / 1 = no se oyó nada o ninguna de las palabras / 2 = no disponible (motivo en stderr)
        |#   cmd=${d}(z2-listen -w "siguiente,para") || exit
    """.trimMargin()

    override val listenUsage: String get() = "uso: z2-listen [-w palabra,palabra,...] [-l idioma]"

    override val listenPrompt: String get() = "Habla ahora… (termina al quedarte en silencio)"

    override val listenNeedsAndroid12: String get() = "z2-listen requiere Android 12 o posterior (antes no se puede exigir que el reconocimiento se quede en el dispositivo)."

    override val listenBusy: String get() = "Ya está escuchando (hay otro z2-listen en marcha)."

    override val listenEngineBusy: String get() = "El reconocimiento de voz del dispositivo está ocupado (quizá lo use otra app). Inténtalo más tarde."

    override val listenEngineStuck: String get() = "El reconocimiento de voz no responde. Si sigue pasando, fuerza la detención de la app de reconocimiento del dispositivo (como Android System Intelligence) en Ajustes de Android › Aplicaciones."

    override val listenStopped: String get() = "Se dejó de escuchar."

    override val listenNoOnDevice: String get() = "Este dispositivo no tiene reconocimiento de voz en el dispositivo. z2-listen nunca envía tu voz a la red, así que aquí no puede funcionar."

    override val listenNoPermission: String get() = "No hay permiso para el micrófono. Vuelve a ejecutar z2-listen con z2term en pantalla para que lo pida, o permítelo en los ajustes de Android."

    override val listenNotShown: String get() = "Pon z2term en pantalla y vuelve a intentarlo (Android solo da el micrófono a la app que está en pantalla)."

    override val listenNoMic: String get() = "No se pudo usar el micrófono (z2term salió de la pantalla u otra app está grabando)."

    override val listenNoLanguage: String get() = "No están instalados los datos de reconocimiento en el dispositivo para este idioma. Descárgalos en los ajustes de entrada de voz (reconocimiento sin conexión)."

    override val listenNoSpeech: String get() = "No se oyó nada."

    override val listenTimedOut: String get() = "Se acabó el tiempo."

    override fun listenNoMatch(heard: String): String = "Ninguna de las palabras (se oyó: $heard)"

    override fun listenFailed(code: Int): String = "Falló el reconocimiento de voz (error $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … impide que la pantalla se apague sola durante ese tiempo
        |# z2-screen keepon off          … devuélvelo ahora, sin esperar al plazo
        |# z2-screen status              … estado actual en JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Esto cambia el ajuste de apagado de pantalla de todo el sistema, así que se mantiene con la
        |# aplicación en segundo plano. NO es el 🔅 de la barra de herramientas, que solo dura mientras
        |# la aplicación está en pantalla: a ese no se le toca.
        |# El valor original se guarda y siempre se vuelve a escribir al llegar el plazo (aunque maten
        |# la aplicación o se reinicie el dispositivo). Como mucho 24 h de una vez.
        |# Hace falta «modificar los ajustes del sistema» (Ajustes > apagado de pantalla > permitir).
        |# p. ej. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "uso: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | comando...> [--off <comando...>] [-l <rótulo>] [-i <dibujo>]
        |#                           … pon algo en el mosaico 1-$tiles de los ajustes rápidos
        |# z2-tile add <1-$tiles>         … pide colocar ese hueco en el panel (Android 13+)
        |# z2-tile list              … los $tiles huecos en TSV (hueco / rótulo / comando; '-' = vacío)
        |# z2-tile clear <1-$tiles|all>   … vacía un hueco
        |#
        |# Qué se asigna
        |#   O bien **el nombre de archivo de una macro** de ~/.z2term/macros/, o bien **un comando**
        |#   que se ejecuta tal cual: cuál de los dos es se deduce del nombre.
        |#   Una macro puede llevar argumentos ('remind.sh ask'): la primera palabra decide si es una
        |#   macro y el resto se le pasa. Dos huecos con la misma macro acaban con el mismo rótulo,
        |#   así que dales -l.
        |#   El comando se ejecuta con Z2_TILE=<hueco> (y Z2_TILE_MACRO si es una macro) en el entorno.
        |#   ⚠ Un nombre acabado en .sh que no esté en ~/.z2term/macros/ se rechaza aquí: como comando
        |#     se buscaría en el PATH (que no incluye la carpeta de macros), así que el mosaico no
        |#     haría absolutamente nada y solo lo diría en tile/run.log.
        |#
        |# Qué hace un toque
        |#   Un toque lo ejecuta y otro lo para (el mismo trato que los botones del widget).
        |#   El mosaico se ve «encendido» mientras corre (el color es el del sistema, no nuestro).
        |#   Con --off tienes **dos comandos**: cada toque alterna entre ellos y el mosaico se queda
        |#   con aspecto de encendido mientras lo esté. Úsalo cuando apagar sea su propio comando
        |#   (z2-torch on / off).
        |#   Los pares normales recuerdan el estado. Las asignaciones directas de z2-torch reflejan
        |#   el estado real de Android y cambios externos; z2-screen keepon sigue su ajuste activo.
        |#
        |# Colocarlo en el panel
        |#   En Android 13+, `set` te pregunta al momento si colocar el mosaico en el panel, y ese
        |#   diálogo lleva **el nombre y el icono que acabas de asignar**. Si dices que no, no se
        |#   coloca nada.
        |#   Hay exactamente $tiles huecos: el número está fijado en el manifiesto y no puede crecer en
        |#   marcha. Los huecos sin asignar no aparecen siquiera en la pantalla de edición, así que
        |#   tener $tiles no te cuesta nada.
        |#   ⚠ Solo aparece con z2term delante, y solo se puede preguntar por uno a la vez: una macro
        |#     que asigne dos huecos seguidos solo preguntará por el primero. Usa después
        |#     z2-tile add <hueco> para el resto.
        |#   ⚠ Colocar un mosaico sigue siendo cosa tuya: Android no deja que una aplicación coloque
        |#     los suyos. Desde la pantalla del lápiz (editar) del panel de ajustes rápidos, busca
        |#     **z2term <hueco>**.
        |#   ⚠ Esa lista muestra el nombre y el icono del manifiesto, no los que asignaste (Android no
        |#     ofrece forma de cambiarlos en marcha), así que z2-tile list te dice qué número es cuál.
        |#
        |# El dibujo (-i)
        |#   Un hueco recibe solo un icono acorde cuando el nombre lo delata (remind.sh -> un reloj), y
        |#   z2-icon lo cambia por el que quieras; una vez lo cambias, ya no se toca.
        |#   -i deja fijado el dibujo **antes** de que salga ese diálogo, así que una sola línea decide
        |#   el nombre y el icono a la vez (z2-icon sample lista los nombres que puedes pasar).
        |#   ⚠ Un nombre desconocido se rechaza **antes de asignar nada**: un hueco asignado pero con el
        |#     dibujo equivocado cuesta más de notar que uno que nunca se asignó.
        |#
        |# p. ej. z2-tile set 1 backup.sh
        |#        z2-tile set 2 'z2-screen keepon 1h' -l "sin apagar"
        |#        z2-tile set 3 z2-torch on --off z2-torch off -l linterna
        |#        z2-tile set 4 backup.sh -l copia -i sync
    """.trimMargin()

    override val tileUsage: String get() = "uso: z2-tile set <1-$tiles> <macro.sh|comando...> [--off <comando...>] [-l rótulo] "

    override fun tileNoSuchIcon(name: String): String = "z2-tile: no existe ese dibujo: $name (los lista z2-icon sample). No se asignó nada."

    override fun tileAddEmpty(n: Int): String = "z2-tile: el hueco $n todavía está vacío (asígnalo antes: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: aquí no se puede pedir que se añada el mosaico (hace falta Android 13 y z2term delante). "

    override val tileAddAsked: String get() = "se le ha preguntado a Android si añadir el mosaico: responde al diálogo. "

    override val iconHelp: String get() = """
        |# Cambia los iconos de la barra de estado y de los mosaicos por un dibujo de píxeles tuyo.
        |#
        |# Empieza por aquí
        |#   z2-icon pick 1     ... elige un dibujo de una lista y ponlo en el hueco 1
        |#   z2-icon list       ... qué tiene ahora cada hueco ('-p' muestra también los dibujos)
        |#
        |# Qué significa <destino>
        |#   notify    el icono de todas las notificaciones de esta aplicación (el de la barra de estado)
        |#   1-$tiles      un hueco de los ajustes rápidos: el mismo número que usa z2-tile
        |#
        |# Poner un dibujo
        |#   pick <destino>            los lista y eliges por número (lo más fácil)
        |#   sample <destino> <nombre> cuando ya sabes el nombre
        |#   edit <destino>            dibújalo, o arregla el actual, en ${d}EDITOR
        |#   set <destino> <archivo>   léelo de un archivo ('-' para la entrada estándar)
        |#   auto <1-$tiles|all>           vuelve a elegirlo a partir de lo que ejecuta el mosaico
        |#   clear <destino|all>       vuelve al icono de serie
        |#
        |# Cómo de fina es la cuadrícula
        |#   grid                      el tamaño con el que se crean los dibujos nuevos
        |#   grid <$grids>           fíjalo ($grid mientras no lo cambies)
        |#   scale <destino> <$grids>  vuelve a tender en esa cuadrícula el dibujo que hay en <destino>
        |#                             (suavizando de paso los escalones diagonales). **Te da sitio para
        |#                             dibujar más fino**: el icono que sale se suaviza de todos modos,
        |#                             así que no hace falta esto para conseguirlo.
        |#
        |# Guardar tus dibujos en la lista
        |#   save <destino> <nombre>   ponle nombre a lo que hay ahora en <destino> y añádelo a la lista
        |#   sample                    la lista entre la que puedes elegir (número / nombre /
        |#                             'builtin' = de serie, 'mine' = guardado por ti)
        |#   sample <nombre>           muestra ese dibujo
        |#   forget <nombre>           quita uno de los tuyos de la lista (lo que ya pusiste en un
        |#                             destino se queda donde está)
        |#
        |# Cómo se dibuja
        |#   Es una cuadrícula de caracteres. '.' ' ' '0' '-' '_' dejan el punto vacío y cualquier otro
        |#   carácter lo rellena, así que usa el que mejor veas. El espacio en blanco alrededor del
        |#   dibujo se ignora: se centra solo, no tienes que rellenar cada línea al milímetro.
        |#   La cuadrícula es $grids ('z2-icon grid').
        |#   ⚠ Una cuadrícula mayor no hace un icono mayor. Llena la cuadrícula o el dibujo saldrá más
        |#     pequeño que el que sustituye.
        |#
        |# Conviene saber
        |#   ⚠ Solo pasa la forma. Android pinta estos iconos de un color suyo (los mosaicos cambian de
        |#     color entre encendido y apagado), así que no hay ningún color que elegir.
        |#   ⚠ **El contorno se suaviza al salir.** Un dibujo hecho sobre $grid puntos sale igualmente
        |#     suave en un mosaico (que se dibuja mucho más grande que la barra de estado), así que la
        |#     cuadrícula no es algo que preocupe a diario. 48 / 64 son para cuando **tú** quieras
        |#     dibujar más fino. 'show' imprime el dibujo tal como lo hiciste; el mosaico sale más suave.
        |#   ⚠ Un mosaico recibe un dibujo solo cuando el nombre lo delata ('z2-tile set 1 remind.sh'
        |#     pone allí un reloj). Lo que pongas aquí manda y ya no se vuelve a tocar: 'z2-icon auto 1'
        |#     devuelve ese hueco a la elección automática.
        |#   ⚠ Hay dos iconos que no se pueden cambiar: el de la pantalla de **edición** de los ajustes
        |#     rápidos (de donde arrastras los mosaicos) y el del lanzador. Android los fija al instalar.
        |#
        |# p. ej. z2-icon pick 1              elige el dibujo del hueco 1
        |#        z2-icon edit 1              vuelve a dibujarlo tú
        |#        z2-icon save 1 mi-cara      ponle nombre a ese dibujo y añádelo a la lista
        |#        z2-icon sample 3 mi-cara    pon el mismo dibujo en el hueco 3
        |#        z2-icon grid 64             dibuja los nuevos en una cuadrícula de 64x64
        |#        z2-icon scale 1 48          lleva el hueco 1 a 48x48 para dibujarlo más fino
        |#        z2-icon list -p             comprueba qué hay en cada sitio, con dibujos incluidos
        |#        z2-icon clear notify        devuelve el icono de las notificaciones a su sitio
    """.trimMargin()

    override val iconUsage: String get() = "uso: z2-icon pick <notify|1-$tiles> | sample [nombre|destino nombre] | "

    override val iconNoSuchFile: String get() = "no existe ese archivo:"

    override val iconEditUnchanged: String get() = "sin cambios."

    override val iconPickPrompt: String get() = "número (o nombre), en blanco para cancelar: "

    override val iconPickCancelled: String get() = "cancelado."

    override val iconNoEditor: String get() = "no se encontró ningún editor. Define ${d}EDITOR o usa: z2-icon set <destino> <archivo>"

    override val tileNoSuchMacro: String get() = "esa macro no está en ~/.z2term/macros/ (usa una ruta completa para ejecutarla como comando):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# Activa la captura primero: 5 minutos, últimos 128 eventos, solo en memoria.
        |# Tipos y longitudes UTF-16, sin valores de texto. dump/stop dan JSON; start borra lo anterior.
        |# z2-noti list  … las notificaciones que hay ahora en pantalla, en TSV
        |#                 (key / paquete / nombre de la app / título / cuerpo)
        |# Solo lectura. A propósito no hay forma de pulsar ni de descartar una notificación:
        |# eso pulsaría también los botones de pagar y enviar de otras aplicaciones.
        |# Hace falta el acceso a las notificaciones (Ajustes > Permisos y notificaciones).
        |# Ver también: z2-when notify:otp / notify:pkg=<parte> / notify:contains=<parte>
    """.trimMargin()

    override val notiUsage: String get() = "uso: z2-noti list | trace start [package] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [nombre]     … una vez, a la próxima HH:MM (mañana si ya pasó)
        |# z2-alarm daily HH:MM [nombre]  … todos los días a las HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [nombre] … una vez, dentro de N segundos/minutos/horas
        |# z2-alarm list                  … lo que hay programado (JSON; "exact" dice cómo de puntual es)
        |# z2-alarm cancel <id|nombre|all> … cancelar
        |# Al saltar se añade una línea {"event":"alarm","name":…} a ~/.z2term/events.jsonl.
        |# Despierta el dispositivo incluso en Doze. "exact":true significa que salta al minuto;
        |# "exact":false significa que Doze solo ofrece un hueco cada 9-15 min, así que un teléfono
        |# con la pantalla apagada puede llegar con ese retraso. Lo que lo pone en true es quitarle a
        |# esta aplicación la optimización de batería (no se pide ningún permiso más).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: no hay ningún comando date utilizable"

    override val sessionHelp: String get() = """
        |# z2-session list                     … lista las pestañas (índice / id / tipo / marca / nombre, TSV)
        |#   marcas: * = en pantalla / ! = trabajando / ? = sin arrancar / @ = conectada desde un shell / - = otra
        |# z2-session new [nombre]             … abre una pestaña de terminal (devuelve índice e id)
        |# z2-session send <pest> <texto>...   … escribe el texto en esa pestaña (no lo ejecuta)
        |# z2-session send <pest> <texto> --enter … lo escribe y lo ejecuta
        |# z2-session key <pest> <tecla>...   … envía **teclas** a esa pestaña (C-c / M-x / F5 / Up …)
        |#   modificadores: C- (Ctrl) y M- (Meta=Alt); se pueden encadenar, como en C-M-a
        |#   especiales: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Las teclas con Shift como C-S-a se rechazan (una terminal no distingue Shift: son los mismos bytes que C-a)
        |# z2-session key <pest> --raw '\x1b[A' … cualquier otra cosa, en bytes (\xHH \e \n \r \t \0)
        |# z2-session capture [pest] [--all]   … toma la pantalla de esa pestaña (--all incluye el historial)
        |# z2-session attach <pest>            … quédate conectado a esa pestaña y escribe en ella sin más
        |#   se sale con Ctrl+] (en cualquier momento) o con ~. al principio de una línea (como en ssh)
        |#   ⚠ por SSH solo funciona Ctrl+]: el ssh que tienes delante se come el ~. y corta la sesión SSH
        |#   escribe ~~ para un ~ literal al principio de línea, y ~ seguido de Ctrl+] para un Ctrl+] literal
        |#   mientras estás conectado, la pestaña sigue el tamaño de TU ventana; vuelve al suyo al salir
        |# z2-session close <pest>             … cierra esa pestaña (nunca la última)
        |#
        |# <pest> puede ser el índice de list, un id o el nombre de una pestaña. '.' u omitido = la que está en pantalla.
        |# p. ej. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "uso: z2-session list | new [nombre] | send <pest> <texto>... [--enter] | "

    override val keyNothing: String get() = "z2-session key: no se indicó ninguna tecla"

    override val keyRawEmpty: String get() = "z2-session key: después de --raw faltan los bytes que enviar"

    override val keyUnknown: String get() = "z2-session key: no existe esa tecla (la lista está en 'z2-session -h'):"

    override val keyBadEscape: String get() = "z2-session key: no se puede leer el escape:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: la terminal no distingue Shift, así que '$asWritten' daría exactamente " +
            "los mismos bytes que '$equivalentTo'. Escribe '$equivalentTo' si es lo que querías."

    override fun attachNoSuchTab(target: String): String = "no existe esa pestaña: '$target' (ejecuta 'z2-session list' para verlas)"

    override val attachNotTerminal: String get() = "esa es una pestaña gráfica y no tiene shell al que conectarse. Elige una pestaña de terminal."

    override val attachNotStarted: String get() = "esa pestaña todavía no ha arrancado (marcada '?' en list). Ábrela una vez en la aplicación y conéctate después."

    override val attachExited: String get() = "esa pestaña ya ha terminado. Su pantalla todavía se puede leer con 'z2-session capture'."

    override val attachSelf: String get() = "esa es la pestaña en la que estás escribiendo. Conectarla consigo misma hace que su propia "

    override val attachLoop: String get() = "esa pestaña ya está conectada de vuelta a esta, así que las dos se alimentarían la una a la "

    override val serverHelp: String get() = """
        |# z2-server list                … servidores registrados (índice / id / estado / marca / nombre, TSV)
        |#   marcas: * = activado / - = desactivado
        |# z2-server start <servidor>    … lo arranca como servidor permanente (mantiene el equipo accesible)
        |# z2-server stop <servidor>     … para ese (los demás siguen en marcha)
        |# z2-server status [<servidor>] … estado, pid, reinicios y último código de salida
        |#
        |# <servidor> puede ser el índice de list, un id o el nombre que le pusiste en la aplicación.
        |# Solo se pueden arrancar servidores registrados en la aplicación; esto nunca registra uno nuevo.
        |#
        |# Por qué existe: un demonio arrancado directamente desde una regla corre FUERA del marco de los
        |# servidores permanentes (sin WakeLock, sin WifiLock, sin servicio en primer plano), así que deja
        |# de responder en cuanto se apaga la pantalla. Arrancarlo por aquí lo mete dentro de ese marco.
        |# p. ej. z2-when wifi:connect run 'z2-server start sshd'
        |#        z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "uso: z2-server list | start <servidor> | stop <servidor> | status [<servidor>]"

    override val serverNotFound: String get() = "z2-server: no existe ese servidor (mira 'z2-server list'):"

    override val serverAmbiguous: String get() = "z2-server: hay más de un servidor con ese nombre; usa el id de 'z2-server list':"

    override val serverNone: String get() = "z2-server: todavía no hay ningún servidor registrado (añade uno en la aplicación: 📜 -> Servidores)."

    override val serverLowPowerWarn: String get() = "z2-server: aviso — el modo de bajo consumo está activado, así que no se retiene WakeLock/WifiLock. "

    override val whenHelp: String get() = """
        |# z2-when <disparador> run <cmd...>     … registra una regla
        |#   disparadores: charge:start | charge:stop  (hace falta la detección)
        |#            battery:below=N | battery:above=N  (hace falta la detección)
        |#            battery:temp>N | battery:temp<N  … la temperatura de la batería (°C) cruzó N (hace falta la detección)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min hora dm mes ds'  (ds 0-7 / 0,7=domingo. Entrecomíllalo: lleva espacios)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<nombre>  (hace falta la detección)
        |#            net:online | net:offline  … apareció / desapareció una conexión utilizable
        |#            net:wifi | net:mobile | net:ethernet  … el enlace en uso pasó a ser ese
        |#                                        (hace falta la detección; cuenta los datos móviles, no solo el Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<parte> | share:ext=<ext>
        |#                                        … otra aplicación compartió algo con z2term
        |#            boot                        … el dispositivo terminó de arrancar (va con la detección apagada)
        |#            sms:any | sms:from=<parte> | sms:contains=<parte> | sms:otp  (hace falta RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (con detección)
        |#            file:new=<carpeta>[,ext=<ext>]  … llegó un archivo nuevo a esa carpeta (con detección)
        |#            file:changed=<carpeta>[,ext=<ext>] | file:deleted=<carpeta>[,ext=<ext>]
        |#                                        … un archivo de ahí se reescribió / desapareció (con detección)
        |#            bt:connect | bt:disconnect | bt:connect=<nombre> | bt:disconnect=<nombre>
        |#                                        … se conectó / desconectó un dispositivo Bluetooth (nombre: una parte, o su dirección)
        |#                                        (con detección y, desde Android 12, el permiso de Dispositivos cercanos)
        |#            app:open=<app> | app:close=<app>  … esa app pasó al frente / dejó el frente (paquete o nombre, una parte)
        |#                                        (hay que activar z2term en Accesibilidad; funciona sin detección)
        |#            steps:reach=N               … los pasos de hoy llegaron a N (con detección y el permiso de Actividad física)
        |#            notify:any | notify:otp | notify:pkg=<parte> | notify:title=<parte> | notify:contains=<parte>
        |#                                        … llegó una notificación (hace falta el acceso a las notificaciones)
        |#            event:<nombre> | event:<prefijo>* | event:*  … cualquier evento del dispositivo, por nombre
        |#              (z2-when events los lista; los mismos nombres que en events.jsonl)
        |# Nombre (opcional, justo tras el disparador y antes de run):
        |#   name=<texto>            … para qué es esta regla; se muestra en la pestaña de automatización
        |#                             (entrecomíllalo si lleva espacios). Vacío = se muestra el disparador.
        |#                             Solo aparece: no cambia nada sobre cuándo se ejecuta la regla.
        |# Filtros (con cualquier disparador, justo tras él y antes de run):
        |#   if=<cond>[,<cond>...]   … solo cuando el dispositivo está en ese estado (Y lógico; ! niega)
        |#                             las claves son las que imprime z2-state: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             p. ej. if=wifi,!screen / if=ssid=Casa / if=level<30
        |#   if_any=<cond>[,<cond>...] … se ejecuta si se cumple **cualquiera** (la coma es un O lógico)
        |#                             junto con if=: «todas las de if Y una cualquiera de if_any»
        |#   else=<cmd>              … ejecuta esto **en su lugar** cuando if / if_any no se cumplieron
        |#                             ⚠ solo llegan aquí if / if_any. Una ejecución descartada por
        |#                             between / days / cooldown no ejecuta nada en absoluto: una regla
        |#                             que de noche está apagada tiene que callar de noche, else incluido.
        |#   cooldown=30m            … no repetirla dentro de ese tiempo (10s / 30m / 2h)
        |#   between=22:00-07:00     … solo dentro de esa franja (puede cruzar la medianoche)
        |#   days=mon-fri            … solo esos días (nombres o números de cron 0-7, 0/7 = domingo)
        |#   answer='<texto>=><respuesta>' … cuando el comando pregunta esto en el terminal, escribe la
        |#                             respuesta y Enter (repetible; p. ej. answer='[y/N]=>y'). Se compara
        |#                             con el final de la última línea de salida.
        |#   secret='<texto>'        … lo mismo para una respuesta oculta (una contraseña). Su contenido se
        |#                             introduce en el editor de reglas de la app y se guarda cifrado en ella.
        |# Las ejecuciones descartadas también se anotan: z2-when fired muestra skip:if / skip:if→else /
        |# skip:between / skip:days / skip:cooldown, así que una regla que nunca corre se puede explicar.
        |# El botón ▶ de la aplicación ignora todos los filtros (está para probar la regla).
        |# z2-when events                        … lista los nombres que valen con event:
        |# z2-when pause / resume                … para / reanuda las ejecuciones automáticas (las reglas se conservan)
        |# z2-when fired [n]                     … disparos recientes (hora / id / disparador / run|paused)
        |# z2-when list                          … reglas registradas (id / on|off / disparador / -> / cmd, TSV;
        |#                                         el nombre y los filtros, si los hay, al final entre [])
        |# z2-when remove <id|all>  (vale rm)    … eliminar
        |# z2-when on <id> / off <id>            … activar / desactivar
        |# z2-when log <id>                      … el registro de ejecuciones de esa regla (final)
        |# Al dispararse, el comando corre en la distribución seleccionada con Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) en el entorno.
        |# Con net: tienes Z2_WHEN_NET (el enlace de ahora) y Z2_WHEN_NET_PREV (el anterior).
        |# Con share: tienes Z2_WHEN_SHARE (el texto, o las rutas de los archivos recogidos)
        |# y Z2_WHEN_SHARE_KIND (text|file). Lo compartido se sigue poniendo en la línea de entrada como antes.
        |# Con file: tienes Z2_WHEN_FILE (ruta completa) y Z2_WHEN_DIR.
        |# Con notify: tienes Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (y Z2_WHEN_OTP con notify:otp). notify:category= es coincidencia exacta: call, missed_call, msg, ...
        |# Con event: tienes además Z2_WHEN_EVENT (el nombre del evento); alarm / notify_action añaden
        |# Z2_WHEN_EVENT_NAME (el identificador con el que lo armaste) y Z2_WHEN_ACTION (el botón pulsado).
        |# Con battery:temp tienes Z2_WHEN_TEMP (°C). Con bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Con app: Z2_WHEN_APP_PKG (la app al frente ahora) / Z2_WHEN_APP_PREV (la anterior).
        |# Con steps: Z2_WHEN_STEPS (pasos de hoy). Con file: también Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated dan Z2_WHEN_PKG, timezone_changed da Z2_WHEN_TZ,
        |# usb_attached / usb_detached dan Z2_WHEN_USB (nombre) / Z2_WHEN_USB_ID (fabricante:producto).
        |# Una misma regla no se dispara dos veces en 10 segundos (eventos como screen_on llegan a menudo).
        |# p. ej. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#        z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#        z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#        z2-when 'event:ringer_*' run 'z2-toast "modo de sonido: ${d}Z2_WHEN_EVENT"'
        |#        z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#        z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#        z2-when boot run 'sshd --lan'
        |#        z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#        z2-when time:daily=07:00 name='Informe matinal' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Ejecuciones automáticas en pausa (z2-when resume para volver a empezar)"

    override val whenResumed: String get() = "Ejecuciones automáticas reanudadas"

    override val whenNoFires: String get() = "(todavía no se ha disparado nada)"

    override val whenPausedNote: String get() = "# en pausa (z2-when resume para volver a empezar)"

    override val whenNoLog: String get() = "(todavía no hay registro)"

    override val whenWriteFailed: String get() = "z2-when: no se pudo escribir la regla"

    override val whenUnknownIfKey: String get() = "z2-when: clave de if= desconocida (z2-state lista las que puedes usar):"

    override val whenUnknownTrigger: String get() = "z2-when: disparador desconocido (z2-when sin argumentos los lista):"

    override val whenBadTriggerSpec: String get() = "z2-when: ese disparador no admite este argumento:"

    override val whenPausedWarn: String get() = "aviso: las ejecuciones automáticas están en pausa (z2-when resume para volver a empezar)"

    override val whenRunJoined: String get() = "aviso: los saltos de línea del comando se convirtieron en espacios (una regla solo lee una línea)"

    override val whenEventList: String get() = """
        |screen_on              se encendió la pantalla           [con detección]
        |screen_off             se apagó la pantalla              [con detección]
        |unlocked               se desbloqueó el dispositivo      [con detección]
        |power_connected        empezó la carga                   [con detección]
        |power_disconnected     se detuvo la carga                [con detección]
        |battery_low            la batería se quedó baja          [con detección]
        |battery_okay           la batería se recuperó            [con detección]
        |battery_level          el nivel cruzó un múltiplo de 10% [con detección]
        |wifi_connected         se conectó al Wi-Fi               [con detección]
        |wifi_disconnected      se salió del Wi-Fi                [con detección]
        |net_online             apareció un enlace que funciona   [con detección]
        |net_offline            no hay ningún enlace que funcione [con detección]
        |net_wifi               el enlace en uso es Wi-Fi         [con detección]
        |net_mobile             el enlace en uso es móvil         [con detección]
        |headset_plugged        auriculares con cable puestos     [con detección]
        |headset_unplugged      auriculares con cable quitados    [con detección]
        |bt_audio_connected     audio Bluetooth conectado         [con detección]
        |bt_audio_disconnected  audio Bluetooth desconectado      [con detección]
        |airplane_on            modo avión activado               [con detección]
        |airplane_off           modo avión desactivado            [con detección]
        |ringer_normal          sonido puesto en normal           [con detección]
        |ringer_vibrate         sonido puesto en vibración        [con detección]
        |ringer_silent          sonido puesto en silencio         [con detección]
        |bt_connected           se conectó un dispositivo Bluetooth [con detección]
        |bt_disconnected        se desconectó un dispositivo Bluetooth [con detección]
        |powersave_on           ahorro de batería activado        [con detección]
        |powersave_off          ahorro de batería desactivado     [con detección]
        |dnd_on                 No molestar activado              [con detección]
        |dnd_off                No molestar desactivado           [con detección]
        |app_installed          se instaló una app                [con detección]
        |app_removed            se desinstaló una app             [con detección]
        |app_updated            se actualizó una app              [con detección]
        |timezone_changed       cambió la zona horaria            [con detección]
        |usb_attached           se conectó un dispositivo USB     [con detección]
        |usb_detached           se desconectó un dispositivo USB  [con detección]
        |boot                   el dispositivo arrancó            [siempre]
        |alarm                  saltó una z2-alarm                [siempre]
        |notify_action          un botón de una notificación      [siempre]
        |unlock_failed          falló el desbloqueo               [siempre, hay que prepararlo]
        |unlock_succeeded       desbloqueado tras un fallo        [siempre, hay que prepararlo]
        |voice                  se oyó una orden por voz          [siempre, desde el mosaico o el asistente]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Nombres que puedes poner en event:<nombre>. En el mismo orden en que salen en events.jsonl.
        |    # El grupo de arriba necesita la detección activada (Ajustes > servidores permanentes y
        |    # automatización); el de abajo lo armas tú, así que funciona con la detección apagada.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … busca, descarga y entrega la versión nueva al instalador
        |# z2-update --check             … solo dice si hay una versión más reciente
        |# z2-update --keep              … deja el .apk descargado (por omisión se borra)
        |# z2-update --dir <carpeta>     … descárgalo en esa carpeta en vez de en la de la aplicación
        |#
        |# De dónde viene
        |#   De la página de GitHub Releases de z2term, el mismo archivo que te descargarías a mano
        |#   (z2term-<versión>.apk, firmado con la clave de publicación). No se contacta con nada más,
        |#   y no se contacta con nada en absoluto hasta que ejecutas esto.
        |#
        |# Qué puede y qué no puede hacer
        |#   ⚠ **No puede instalar en silencio.** Android siempre muestra su propia pantalla de
        |#     «¿instalar?» cuando una aplicación se sustituye a sí misma, así que el último toque
        |#     siempre es tuyo. Este comando hace todos los pasos hasta esa pantalla.
        |#   ⚠ La primera vez, permite **«Instalar aplicaciones desconocidas»** para z2term (te lo dirá,
        |#     y los ⚙Ajustes tienen el mismo botón). Sin eso, la pantalla de instalación no llega a salir.
        |#   ⚠ ¿Instalada desde F-Droid o una tienda? Entonces esto se niega y te dice que la actualices
        |#     allí: esa versión les toca sustituirla a ellos, no a nosotros.
        |#
        |# El archivo descargado
        |#   Por omisión cae en la carpeta de la propia aplicación y se borra en cuanto la instalación
        |#   sale bien (y al arrancar la próxima vez, por si mataron la aplicación a mitad, que es lo
        |#   normal al sustituirse a sí misma). --keep o --dir lo cambian; los ⚙Ajustes tienen esos dos
        |#   mismos ajustes para el botón de allí.
        |#
        |# p. ej. z2-update --check
        |#        z2-update --dir /sdcard/Download --keep
        |#        z2-when time:daily=03:00 run 'z2-update'   # mirar cada noche (te sigue preguntando)
    """.trimMargin()

    override val updateUsage: String get() = "uso: z2-update [--check] [--keep] [--dir <carpeta>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current ya es la última versión."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: buscando una versión más reciente ..."

    override val updateHandedToInstaller: String get() = "z2-update: la pantalla de instalación está abierta en el dispositivo; apruébala para terminar."

    override fun updateNoApk(url: String): String = "z2-update: esa versión no lleva ningún .apk adjunto. Cógelo de la página de la versión: $url"

    override val updateNeedPermission: String get() = "z2-update: permite antes «Instalar aplicaciones desconocidas» para z2term "

    override val updateManagedByStore: String get() = "z2-update: esta versión se instaló desde una tienda (F-Droid / Play). Actualízala allí."

    override fun updateFailed(reason: String): String = "z2-update: falló: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Órdenes por voz — tabla de palabras (español). La lee voice.sh. Edítala para añadir o cambiar lo que puedes decir.
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
title_rule_voice_locked = Órdenes por voz (bloqueado)
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
say_locked = Desbloquea el teléfono
msg_help = hora, fecha, batería, pasos / baja el volumen 3, volumen al 50 por ciento / siguiente canción, pausa / temporizador de 3 minutos, despiértame a las 7 / recuérdame mañana a las 7 sacar la basura, recordatorios / noticias, tiempo, tiempo de mañana / anota comprar leche, lee mis notas / enciende la linterna, captura / abre … / llama a … / navega a …, teléfono de … / IA … (actívala con voice.sh ai) / cambiar las palabras: voice.sh words
msg_unknown = No he entendido: {text}
msg_locked = «{text}» seguirá cuando desbloquees el teléfono
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
msg_setup_steps = La regla Contar pasos (steps:reach=1000000) solo mantiene activo el contador para preguntar por los pasos de hoy. Esa cifra no se alcanza y no ejecuta nada.
msg_words_file = Tabla de palabras: {file}  (edítala para añadir formas de decir las cosas; sh ~/.z2term/macros/voice.sh words la abre)"""
