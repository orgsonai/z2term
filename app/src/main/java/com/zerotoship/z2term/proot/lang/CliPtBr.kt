package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * Português do Brasil (`pt-BR`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 *
 * 用語: aba (タブ) / tocar (タップ) / arquivo (ファイル) / tela (画面) / Salvar (保存) /
 * Configurações (設定) / área de transferência (クリップボード) / macro (a) / bloco (タイル) /
 * painel lateral (エッジパネル)。⚠ ブラジルの言い方に揃える (pt-PT の ficheiro / ecrã / Guardar は使わない)。
 * 打つ語 (サブコマンド・引数・daily / weekday 等) は英語のまま、説明だけ訳す。
 */
internal object CliPtBr : CliLanguage {

    override val code: String = "pt-BR"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgPtBr) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgPtBr(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Instalando o conjunto da GUI",
            cleanInstalling = "🧹 Reinstalando do zero o conjunto da GUI",
            noPackageManager = "❌ Nenhum gerenciador de pacotes compatível (apk/apt-get/pacman) foi encontrado.",
            invalidGeometry = "❌ Resolução inválida",
            noXvnc = "❌ Xvnc/Xtigervnc não encontrado (o tigervnc não está instalado)",
            alreadyRunning = "✅ A GUI já está rodando",
            startingXvnc = "▶ Iniciando",
            xvncFailed = "❌ Falha ao iniciar o Xvnc. Registro:",
            ready = "✅ GUI pronta. Conecte por uma aba de GUI do z2term.",
            running = "✅ GUI rodando",
            stopped = "⏹ A GUI está parada",
            stoppedMsg = "⏹ Parada",
            usage = "Uso: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Não foi possível instalar o conjunto da GUI (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Confira a sua rede. Para recuperar, você pode executar z2gui clean num terminal.",
            audioInstalling = "🔊 Áudio da GUI: instalando o PulseAudio",
            audioNoPulse = "⚠️ Áudio da GUI: sem pulseaudio, continuando sem som",
            audioNoPactl = "⚠️ Áudio da GUI: sem pactl, continuando sem som",
            audioStartFailed = "⚠️ Áudio da GUI: o PulseAudio não iniciou",
            audioReady = "🔊 Áudio da GUI: z2sink.monitor →",
            menuApps = "Aplicativos",
            menuWindows = "Janelas",
            menuDesktops = "Áreas de trabalho virtuais",
            menuPreviousDesktop = "Área de trabalho anterior",
            menuNextDesktop = "Próxima área de trabalho",
            menuReload = "Recarregar o menu"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ Número de porta inválido",
            configOk = "configuração do sshd(dropbear) OK",
            installingDropbear = "📦 O dropbear não está instalado — instalando…",
            dropbearInstallFailed = "❌ Não foi possível instalar o dropbear. Confira a rede e os nomes dos pacotes.",
            privilegedPortWarn = "⚠️ Porta privilegiada; o proot (sem root) provavelmente não consegue usá-la (use 1024+).",
            lanExposeWarn = "⚠️ Modo exposto à LAN/WAN: autenticação por chave obrigatória, use uma chave forte.",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys está vazio. Início exposto à LAN cancelado.",
            noAuthorizedKeysHint = "   Registre a sua chave pública em ~/.ssh/authorized_keys e tente de novo.",
            loopbackBind = "🔒 só loopback. Para expor à LAN, passe --lan ou defina Z2_SSHD_LAN=1.",
            foregroundStart = "▶ dropbear em primeiro plano (Ctrl-C para parar)",
            listeningLan = "✅ dropbear escutando (root, só autenticação por chave)",
            listeningLoopback = "✅ dropbear escutando (root, só autenticação por chave, loopback)",
            startupFailed = "❌ Falha ao iniciar o dropbear. Registro:",
            deviceIp = "ip-do-dispositivo",
            authKeysHint = "ℹ ~/.ssh/authorized_keys está vazio. Registre a chave pública de um cliente para conectar.",
            authKeysExample = "   ex.: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 Preparando o chaveiro do pacman (só na primeira vez, sem rede). Isto leva um instante…",
            "keyring.msgOk" to "✅ Chaveiro pronto. Já dá para instalar pacotes.",
            "keyring.msgInitFail" to "❌ pacman-key --init falhou. Vai tentar de novo na próxima vez que esta aba abrir.",
            "keyring.msgNoKeyrings" to "❌ Nenhum chaveiro incluído foi encontrado em /usr/share/pacman/keyrings.",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate falhou. Vai tentar de novo na próxima vez que esta aba abrir.",
            "action.description" to "Macros nomeadas de ações do Android. Ative o z2term Android actions com z2-key permission.\nSalvar valida e substitui a definição; screen=current registra a tela.\nrun espera terminar; start devolve um ID de execução. Interromper o run para essa execução.\nUma execução por vez, 64 instruções no código, 1–300 segundos. A notificação e o stop cancelam os passos restantes.\nAs coordenadas usam o app da frente por padrão e exigem o mesmo tamanho/orientação de tela. Gestos manuais: até 3 segundos; toques gravados: até 30 segundos.\nUse start a partir de um gesto do painel; use run numa macro de shell ou num comando do z2-when.\nGuardado em /root/.z2term/actions/NAME.actions (pasta pessoal compartilhada). z2-macro actions é um apelido.",
            "action.controlHelp" to "version=2 acrescenta repeat N|forever ... end, if CONDIÇÃO ... else ... end e call NOME.\nAs condições usam as chaves do z2-state ou foreground=PACOTE; vírgulas significam E, ! nega um termo.\nUm estado de condição indisponível faz a execução falhar. As chamadas são validadas e congeladas antes de executar.\n64 instruções no código por definição; 10000 instruções executadas por execução. Valem os tempos limite da raiz e dos filhos.",
            "action.pauseHelp" to "pause [RUN_ID] pausa a execução; o passo em andamento é enviado de novo desde o início. Puxar a aba de notificações também pausa.\nresume [RUN_ID] continua quando uma tela que não seja o z2term fica estável na frente (até 5 segundos; senão continua pausada). A resposta vem depois: state running e código de saída 0, ou paused e um código diferente de 0. O limite de tempo para durante a pausa.",
            "action.uiHelp" to "version=2 também aceita click SELETOR, long-click SELETOR e wait-ui MS SELETOR.\nSeletores: text=Continuar, desc=Mais opções, id=org.example.app:id/next. Correspondência exata; sem aspas em volta dos espaços.\nDefina target ou launch antes. O wait-ui procura a cada 250ms por até 1–30000ms; várias correspondências falham.\nAs ações são enviadas uma vez; use wait-ui para a próxima tela. Os campos de entrada/senha ficam de fora.\ninspect PACOTE devolve em JSON os textos visíveis, as descrições e os IDs do app de destino em foco.",
            "adb.mNoPm" to "z2adb: nenhum gerenciador de pacotes compatível (apk/apt-get/pacman) foi encontrado.",
            "adb.mInstalling" to "z2adb: instalando o cliente adb (${d}ADB_PKG) via ${d}PM ...",
            "adb.mInstallFail" to "z2adb: falha ao instalar o adb. Instale-o manualmente e tente de novo.",
            "adb.mHaveAdb" to "z2adb: o adb já está instalado.",
            "adb.mNeedWireless" to "z2adb: se o pareamento falhar, ative Configurações > Opções do desenvolvedor > Depuração por Wi-Fi (Android 11+).",
            "adb.mPairPort" to "Porta de pareamento (Parear o dispositivo com um código de pareamento): ",
            "adb.mConnPort" to "Porta de conexão (mostrada em Depuração por Wi-Fi): ",
            "adb.usageText" to """
        z2adb - conecta ao adb deste próprio dispositivo pela Depuração por Wi-Fi (self-adb, sem PC).

        Requisito: ative Configurações > Opções do desenvolvedor > Depuração por Wi-Fi (Android 11+).

          z2adb setup                instala o cliente adb (apk/apt-get/pacman, detectado sozinho)
          z2adb pair  <port> [code]  pareia (usa a porta de pareamento + o código de 6 dígitos das Configurações)
          z2adb connect <port>       conecta (usa a porta mostrada em 'Depuração por Wi-Fi')
          z2adb status               mostra 'adb devices -l'
          z2adb <qualquer outra coisa>  passado direto ao adb (shell, logcat, install, ...)

        O host padrão é 127.0.0.1; passe host:porta para mudar (ou defina Z2ADB_HOST).
        Exemplo:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
            "audio.description" to "Toca no Android o som de um comando Linux local, sem abrir uma GUI.\ninstall: instala o PulseAudio no sistema Linux atual (baixa pacotes).\nrun COMANDO [ARG...]: conecta o áudio, executa o comando e depois para o servidor de áudio dele.\nExecute um shell para ativar o som em vários comandos. Nenhum arquivo de configuração é sobrescrito.\nPrecisa de uma aba de terminal local e de um player compatível com PulseAudio. SSH não é aceito.",
            "audio.missing" to "Faltam as ferramentas de áudio. Execute: z2-audio install",
            "audio.failed" to "A conexão do áudio falhou; o comando não foi iniciado.",
            "doctor.head" to "== z2doctor (este dispositivo) ==",
            "doctor.secApp" to "-- app --",
            "doctor.secLinux" to "-- lado Linux --",
            "doctor.secPerm" to "-- permissões --",
            "doctor.secAuto" to "-- automação --",
            "doctor.secReport" to "-- copie isto ao pedir ajuda --",
            "doctor.lVersion" to "versão",
            "doctor.lEngine" to "mecanismo",
            "doctor.lDistro" to "distribuição",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "espaço livre",
            "doctor.lHome" to "pasta pessoal",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "notificações",
            "doctor.lNotifyRead" to "acesso às notificações",
            "doctor.lBattOpt" to "otimização de bateria",
            "doctor.lStorage" to "armazenamento (todos os arquivos)",
            "doctor.lSms" to "recebimento de SMS",
            "doctor.lCapture" to "detecção de eventos",
            "doctor.lServers" to "servidores residentes",
            "doctor.lRules" to "regras de automação",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> Configurações do Android > Apps > Z2Term > Notificações: permitir",
            "doctor.fixNotifyRead" to "-> Configurações > Permissões e notificações > Detecção de notificações",
            "doctor.fixBattOpt" to "-> Configurações > Proteção dos processos em segundo plano > isentar da otimização de bateria",
            "doctor.fixStorage" to "-> Configurações > permitir o acesso a todos os arquivos (necessário para ver /sdcard)",
            "doctor.fixSms" to "-> Configurações > Detecção de SMS (só necessário para os gatilhos sms:)",
            "doctor.fixCapture" to "-> Configurações > Detecção de eventos do sistema (necessária aos gatilhos charge/battery/wifi/sensor/event)",
            "doctor.fixPaused" to "-> a automação está pausada. 'z2-when resume' ou a aba Automação",
            "doctor.fixDisk" to "-> menos de 500MB livres. Exclua os dados de sistema que não usa nas Configurações",
            "doctor.fixSdcard" to "-> /sdcard está vazio aqui; é a permissão de armazenamento acima que o torna visível",
            "doctor.noteRedacted" to "(SSID / IP / nomes de host ficam de fora de propósito)",
            "doctor.allGood" to "Nenhum problema encontrado.",
            "doctor.someBad" to "problemas encontrados:",
            "doctor.usage" to "uso: z2doctor [--share | --clip]   (--share entrega o relatório ao menu de compartilhamento do Android)",
            "doctor.pausedYes" to "pausada",
            "doctor.secExits" to "-- como terminou da última vez --",
            "doctor.lExitsNote" to "saídas anormais recentes (mais novas primeiro):",
            "doctor.lExitsNone" to "saídas anormais: nenhuma registrada",
            "doctor.lExitsFile" to "(histórico completo: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> encerrado por falta de memória. Rode menos tarefas pesadas ao mesmo tempo, ou reduza os servidores residentes / abas",
            "doctor.title" to "autodiagnóstico para \"não funciona\"",
            "doctor.seeAlso" to "Veja também: z2scan self (verificação de segurança, outra ferramenta)",
            "help.header" to "Comandos próprios do Z2Term",
            "help.list" to """
        |[Versão / informações]
        |  z2version [--short]            Versão do app, mecanismo, sistema (distribuição), kernel
|  z2doctor [--share|--clip]      Por que não funciona? Autodiagnóstico + um relatório para colar
        |
        |[Recursos do celular]
        |  z2-notify [-h] [-b LABEL]...   Notificação (-h: banner, -b: botão de resposta)
        |  z2-ask "question"              Pergunta por um campo de resposta na notificação; imprime a resposta
        |  z2-listen [-w WORD,...]        Voz para texto, só no dispositivo (z2-listen -h)
        |  z2-toast "message"             Toast (mensagem curta na tela)
        |  z2-share "text"                Entrega um texto ao menu de compartilhamento do Android
        |  z2-open <url|path>             Abre uma URL/arquivo no app padrão
        |  z2-view <file.html>            Lê dentro do app uma página que você fez aqui
        |  z2-edit <file>                 Edita um arquivo de texto no editor do próprio app
        |  z2-qr                          Lê um código QR com a câmera (um botão o abre)
        |  z2-img <file>                  Desenha uma imagem no terminal (PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [text]       Lê / define a área de transferência (set: stdin sem argumento)
        |  z2-file pick | save FILE     Seletor / exportação de documentos do Android (z2-file --help)
        |  z2-battery                     Nível da bateria / estado de carga (JSON)
        |  z2-vibrate [ms]                Vibra (padrão 200ms)
        |  z2-say "text"                  Fala pelo TTS do dispositivo (stdin sem argumento)
        |  z2-torch [on|off|toggle|status]       Lanterna (padrão: toggle)
        |  z2-media [play|pause|next]     Teclas de mídia (também previous/stop)
        |  z2-volume up|down|N|N%         Volume da mídia (devolve atual/máximo)
        |  z2-audio run COMMAND ...      Executa um comando Linux com o áudio do Android
        |  z2-sensor [light|accel|prox]   Lê uma amostra de um sensor (JSON)
        |  z2-intent -a ACT -d URI ...    Dispara qualquer Intent do Android (veja o MACRO-GUIDE)
        |  z2-state [key]                 Estado atual em JSON (ou uma chave crua)
        |  z2-screen keepon 1h | off      Impede a tela de desligar sozinha por um tempo
        |  z2-tile set 1 backup.sh        Põe uma macro/comando num bloco de Configurações rápidas (12)
        |  z2-icon edit 1 | edit notify   Desenhe você mesmo o ícone do bloco / da barra (24/48/64 pontos)
        |  z2-alarm at|daily HH:MM [name] Gatilho de horário -> events.jsonl (também list/cancel)
        |  z2-when <trigger> run <cmd>    Executa sozinho com carga/bateria/horário/eventos (z2-when: uso)
        |  z2-macro list|install <name>   Exemplos de automação incluídos (veja o MACRO-GUIDE)
        |  z2-session list|new|send|...   Controla as abas deste app (z2-session: uso)
        |  z2-usb list | allow [device]   Usa um dispositivo USB pelo Linux (o Android pede permissão)
        |  z2-update [--check]            Atualiza o z2term pelos GitHub Releases (você aprova)
        |
        |[Apps gráficos (GUI)]
        |  z2gui start [WxH] | stop | status   Área de trabalho Linux (ex.: z2gui start 1280x720)
        |  z2run <gui-app>                Abre um app de GUI (também abre a aba de GUI)
        |
        |[Conexão]
        |  z2adb pair/connect/shell ...   adb para este próprio celular, sem PC  (z2adb help)
        |  sshd [-p N]                    Servidor SSH (padrão: só 127.0.0.1, autenticação por chave)
        |
        |[Segurança]
        |  z2scan self [--save]           Autodiagnóstico deste dispositivo/localhost (z2scan help)
        |  z2scan diff                    Só o que mudou desde a referência (saída 1 = novo)
        |  z2scan net|host|cve            nmap/lynis/trivy no localhost (o remoto precisa de opt-in)
        |
        |[Ajuda]
        |  z2help | z2term                Esta lista
        |
        |Cada comando acima se explica: acrescente --help (ex.: 'z2-tile --help').
        |Mais: 'z2adb help', execute 'z2gui' sem argumentos, ou a seção 11 do HANDBOOK.
    """,
            "help.edge" to "[Painéis flutuantes]\nz2-edge --help   Define painéis e atualiza o conteúdo deles\nz2-key --help    Ações globais do Android\nz2-shot --help   Captura só a parte que você contornar\nz2-app --help    Lista os aplicativos e exporta os ícones\nz2-action --help Macros nomeadas de ações do Android\nz2-ui --help     Controle de apps pela IA (a ajuda é um guia para agentes de IA)",
            "macro.rssBody.head" to """
#
# Preparação:
#   1) Uma URL de feed por linha em:  ~/.z2term/rss/feeds.txt
#   2) Ler a cada 30 minutos:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) Opcional - um feed ou uma palavra por linha que não pode ficar enterrado:  ~/.z2term/rss/important.txt
#      O que corresponder ganha uma notificação própria, então um feed movimentado não o empurra para fora.
#      Escreva parte de uma URL ou parte de um título (ex.: example.org).
#   4) Registre as ações de notificação na Automação:
#        z2-when event:notify_action run '$rssNotificationAction'
#      Abrir manda esse artigo ao navegador; Lista abre tudo o que foi coletado, para ler dentro do z2term.
#      Atenção: abrir uma tela por um botão de notificação precisa da permissão "sobrepor a outros apps".
#   5) Ler a lista coletada: sh ~/.z2term/macros/rss.sh view
#      Gerencie as duas regras em Lista de comandos -> Automação -> Regras de automação.
#      Edite uma regra de notificação existente em vez de adicionar outra.
#   6) Notificações: por padrão a leitura programada notifica e uma busca pela lista não.
#        sh ~/.z2term/macros/rss.sh notify off        # leitura programada: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # Atualizar / Artigos na lista: on / off
#      Os dois também são interruptores em Configurações, na lista, para mudar quando quiser.
#
# Precisa: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   As miniaturas (Configurações na lista) também precisam do Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# Bateria: quanto mais vezes você lê, mais gasta. Não vá abaixo de 30 minutos.
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "máximo de linhas mantidas em seen/latest",
            "macro.cNoPy" to "é preciso o python3 (ex.: apk add python3)",
            "macro.cWriteFeeds" to "Escreva uma URL de feed por linha em:",
            "macro.cFeedsHint" to "# Uma URL de feed por linha (as linhas que começam com # são ignoradas)",
            "macro.cSkipFail" to "pula em silêncio um feed que falha ao baixar ou ao analisar (os outros continuam)",
            "macro.cDiff" to "# Subtrai o que já vimos para sobrar só o que é novo (o mesmo truque da diferença de referência do z2scan).\n# As datas e a ordem dos feeds não são confiáveis; nenhuma das duas é segura.",
            "macro.cPrepend" to "# Empilha o mais novo primeiro para o modo \"começo (head)\" do widget ler certo.",
            "macro.cNotify" to "%s novos",
            "macro.cOpen" to "Abrir",
            "macro.cListNone" to "Nada coletado ainda — execute uma vez sem argumentos antes.",
            "macro.cListHint" to "Liste com: rss.sh list [quantidade]",
            "macro.cList" to "Lista",
            "macro.cPageTitle" to "Feeds",
            "macro.cPageSites" to "%s sites",
            "macro.cPageArticles" to "%s artigos",
            "macro.cPageDoc" to "# Monta a página de leitura (o z2-view a mostra dentro do app; sem servidor, sem navegador).\n" +
            "# Agrupada por site, mais nova primeiro, com um botão Abrir por artigo. O tema do celular\n" +
            "# chega como variáveis CSS, então a página segue as cores do terminal.",
            "macro.cViewHint" to "Leia com: rss.sh view",
            "macro.cHitMax" to "número máximo de notificações por artigo numa execução (o resto vai para o resumo)",
            "macro.cSplitDoc" to "# Separa o que não pode passar despercebido. O corpo do resumo só leva 3 linhas, então um feed\n" +
            "# movimentado que atualiza ao mesmo tempo empurra para fora o que importava. As correspondências ganham **a sua\n" +
            "# própria notificação** (o app dá um id separado por notificação, então nada é substituído).",
            "macro.cNameDoc" to "# Põe a URL no nome da notificação: apertar o botão a devolve como {name} no\n" +
            "# notify_action, então o artigo certo abre mesmo com várias notificações na tela (preparação 4).",
            "macro.cListDoc" to "# Imprime o que foi coletado como uma lista legível (sem cor — deve ficar igual ao arquivo/widget).\n" +
            "# O próprio latest.txt continua sendo dados crus, um artigo por linha (o tail do widget e o rss-open.sh o leem).",
            "macro.cSettings" to "Configurações",
            "macro.cArchive" to "Arquivo",
            "macro.cDelete" to "Excluir",
            "macro.cThumbs" to "Imagens em miniatura",
            "macro.cSave" to "Manter os artigos antigos",
            "macro.cNotifyPoll" to "Avisar artigos novos (leitura programada)",
            "macro.cNotifyView" to "Avisar artigos novos (buscados pela lista)",
            "macro.cDeleteSite" to "Excluir todos os artigos deste site",
            "macro.cConfirmSite" to "Tirar da lista %s artigos de %h?",
            "macro.cArticles" to "Artigos",
            "macro.cPrev" to "Anterior",
            "macro.cNext" to "Próxima",
            "macro.cCount" to "%s artigos",
            "macro.cUndated" to "Sem data",
            "macro.cArchiveEmpty" to "Nada guardado ainda. Ative Manter os artigos antigos em Configurações, e os artigos coletados vão ficar aqui.",
            "macro.cSearch" to "Buscar",
            "macro.cSearchWord" to "Palavras (separe com espaços para exigir todas)",
            "macro.cByMonth" to "Por mês",
            "macro.cNoHit" to "Nada encontrado.",
            "macro.cDropMonth" to "Excluir este mês",
            "macro.cDropConfirm" to "Excluir os %s artigos guardados de %m? Isto não pode ser desfeito.",
            "macro.cOnOff" to "Indique on ou off",
            "macro.cNoPillow" to "As imagens em miniatura precisam do Pillow para Python (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# Preparação: coloque antes o lado que coleta
#   z2-macro install rss
# Widget: atribua "rss-open" a um botão nas configurações do widget de estado.
#
# z2-run: atribua "rss-open" a um botão do widget de estado (para testar aqui: sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "Ainda não há artigos",
            "macro.cAllRead" to "Nada novo para abrir",
            "macro.cPick" to "# As linhas do latest.txt são \"título  URL\". Pega a primeira URL que ainda não foi aberta.",
            "macro.cCap" to "limita a lista de abertos para ela não crescer para sempre",
            "macro.copied" to "Código copiado: ${d}{code}",
            "macro.cleared" to "O código copiado foi apagado",
            "macro.otpTitle" to "Código de uso único",
            "macro.cTtl" to "segundos até apagar a cópia (só vale quando ela entrou diretamente)",
            "macro.cCode" to "# O app já extraiu o código. Não faz nada quando não conseguiu.",
            "macro.cTry" to "# Tenta colocá-lo diretamente. Isso só entra enquanto você está **olhando para o app**:\n" +
            "# o Android 10+ só deixa o app da frente gravar na área de transferência, então uma execução em\n" +
            "# segundo plano é descartada em silêncio. Lê de volta para ver se entrou.",
            "macro.cFallback" to "  # Não entrou = você não está olhando para o app. Entrega pelo botão \"Copiar\"\n" +
            "  # da notificação (apertá-lo traz o app para a frente).\n" +
            "  # ⚠ O que entra desse jeito não é apagado pelo TTL abaixo: apagar também precisa estar na frente.",
            "macro.cClear" to "# Depois do TTL, apaga a área de transferência só se ela ainda tiver o código que copiamos\n" +
            "# (o que foi copiado depois disso fica como está).",
            "macro.cHow" to """
# ■ Copiar números de telefone das notificações de chamada
#   Procura no título e depois no texto um número de telefone puro e oferece um botão Copiar.
#   Quem está e quem não está nos contatos entra; os contatos não são consultados.
#   Só um nome não basta para recuperar um número. Só o acesso às notificações é usado.
#   O nome de arquivo unknown-call.sh é mantido para as regras existentes continuarem funcionando.
""",
            "macro.cIsNum" to """
# É um número puro? Tira todo caractere que um telefone pode usar (dígitos + - ( ) espaço);
# se **não sobrar nada** e houver de 7 a 15 dígitos, trata como número.
#   -> Um campo com um nome ou outras letras não é um número puro; confere o outro campo.
#   -> "Desconhecido"/"Número privado" também caem aqui (afrouxe o teste abaixo se quiser esses).
# ⚠ Não escreva isto como case [!...]: o ) dentro do padrão é lido como o separador do case.
""",
            "macro.cScan" to "# Quem liga costuma estar no título, mas alguns apps de telefone o põem no texto. Confere os dois.",
            "macro.cSkip" to "# Nenhum número puro em nenhum dos campos: não há nada para copiar.",
            "macro.cWhat" to "# A categoria diz qual disparou (o mesmo valor do notify:category= que você registrou).",
            "macro.cCopy" to """
# Entrega o número pelo botão "Copiar" da notificação (-c). Chamar z2-clip set aqui
# não entraria: o Android 10+ só deixa o app da frente gravar na área de transferência, e durante uma
# chamada esse é o app de telefone. Apertar o botão traz o z2term para a frente naquele instante.
""",
            "macro.missed" to "Chamada perdida",
            "macro.incoming" to "Chamada recebida",
            "macro.title" to "Copiar o número de telefone",
            "macro.stNew" to "nova",
            "macro.stSame" to "igual",
            "macro.stDiff" to "difere",
            "macro.msgInstalled" to "instalada:",
            "macro.msgSame" to "a mesma coisa já está instalada:",
            "macro.msgOutdated" to "a sua é diferente da incluída:",
            "macro.msgHowUpdate" to "olhe antes: z2-macro diff %s   /   substituir pela incluída: z2-macro install -f %s (as suas edições também vão)",
            "macro.msgNotInstalled" to "ainda não instalada (execute z2-macro install antes):",
            "macro.msgRemoved" to "removida:",
            "macro.msgAbsent" to "não instalada:",
            "macro.msgRemoveDiffers" to "a sua é diferente da incluída, então não foi removida:",
            "macro.msgHowRemove" to "olhe antes: z2-macro diff %s   /   remover mesmo assim: z2-macro remove -f %s (as suas edições também vão)",
            "macro.msgNoDiffTool" to "não há diff aqui. Você ainda pode ler a incluída com z2-macro show.",
            "macro.msgSameAsBundled" to "idêntica à incluída.",
            "macro.msgNotFound" to "não existe esse exemplo:",
            "macro.msgHintResident" to "Para mantê-la rodando, registre isto em Configurações -> Servidores residentes:",
            "macro.msgHintRun" to "Como executar:",
            "macro.remindBody.head" to """
#
# Preparação (uma vez):
#   sh ~/.z2term/macros/remind.sh setup   registra os dois ganchos e os dois blocos
#
# Sintaxe:
#   remind.sh <quando> <texto>       adiciona um lembrete
#   remind.sh list                   lista (o número vem primeiro)
#   remind.sh del <n|all>            cancela (rm também funciona)
#   remind.sh peek                   mostra a lista numa notificação (com um botão Excluir)
#   remind.sh ask                    pergunta numa caixa de resposta da notificação (para o bloco)
#   remind.sh setup                  registra os ganchos e os blocos (uma vez)
#   remind.sh help                   este texto
#
# <quando> - uma vez:
#   30m / 90s / 2h                   a partir de agora
#   18:30                            o próximo 18:30 (amanhã se já passou)
#   sat 21:00                       o próximo dia da semana igual, uma vez (mais tarde hoje também conta)
#   Japonês: 土曜日の夜九時 = sat 21:00; 朝七時 = 07:00; 夜九時半 = 21:30
#   Japonês: 夜12時 é ambíguo; para meia-noite use 00:00 com o dia explícito.
#   tomorrow 18:30 / 3d 07:00        por dia (3d = daqui a 3 dias)
#   07/30 19:00                      mês/dia (ano que vem se já passou)
#   2030 07/30 19:00                 com o ano
#   203007301900 / 07301900          só dígitos (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ sem o horário, mantém **a hora atual do dia** (3d / 07/30 ...)
#
# <quando> - repetindo:
#   daily 07:00 / weekday 09:00      todo dia / seg-sex
#   weekly mon 09:00                 aquele dia da semana
#   monthly 15 09:00                 aquele dia do mês
#   yearly 07/30 19:00               aquele mês e dia
#   every 19:00 / every wed 19:00    "every" sozinho funciona (a palavra seguinte decide)
#   every 15 19:00 / every 07/30 19:00  -> daily / weekly / monthly / yearly
#
# Exemplos:
#   remind.sh 30m tomar o remédio
#   remind.sh tomorrow 18:30 o lixo
#   remind.sh monthly 25 10:00 aluguel
#   remind.sh list        ->  1  ⏰ 07/30 18:30  o lixo
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (depois é só: remind.sh 30m ...)
""",
            "macro.cStore" to """# Um lembrete = uma linha em ${d}DIR/<id>.txt, separada por tab: tipo / rótulo / id-do-quando / texto.
#   tipo: once (ainda vai disparar) / fired (já disparou) / repeat (recorrente)
#   id-do-quando: só para repeat (para o del poder chamar z2-when remove). '-' nos outros.
# O texto vai por último para poder conter qualquer coisa menos TAB.""",
            "macro.cParse" to """# Lê 1-3 palavras e decide:
#   KIND ... once|repeat   PLAN ... rótulo a mostrar   SPEC ... o que o z2-alarm/z2-when recebe   USED ... palavras usadas""",
            "macro.pDaily" to "diário ",
            "macro.pWeekday" to "dias úteis ",
            "macro.pWeekly" to "semanal ",
            "macro.pMonthly" to "mensal ",
            "macro.pYearly" to "anual ",
            "macro.mDay" to "",
            "macro.eDaily" to "diário",
            "macro.eWeekly" to "semanal",
            "macro.eMonthly" to "mensal",
            "macro.eYearly" to "anual",
            "macro.mBadDate" to "essa data não existe (ex.: 07/30):",
            "macro.cEvery" to """  # A forma curta "every ...". **O formato da palavra seguinte** escolhe daily/weekly/monthly/yearly:
  #   every 19:00 -> daily / every wed 19:00 -> weekly / every 15 19:00 -> monthly /
  #   every 07/30 19:00 -> yearly
  # ⚠ O número de palavras não muda (uma palavra trocada por outra), então USED continua valendo.""",
            "macro.cExpand" to "# Decide qual recorrência o \"every\" quis dizer, pela palavra que vem depois.",
            "macro.cDigits" to """    # Formas só com dígitos: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM.
    # ⚠ Confere 12 dígitos antes de 8 (o padrão de 8 dígitos também casa com uma sequência de 12).""",
            "macro.cYmd" to """  # Escrito como data. ⚠ O z2-alarm não aceita data, então isto também vira um atraso.""",
            "macro.cCivil" to """# Data civil -> dias desde 1970-01-01 (anos bissextos incluídos; days_from_civil de Howard Hinnant).
# ⚠ date -d "2026-07-30" não é confiável no busybox, então a conta é feita aqui.
# ⚠ Só a diferença de dias é usada e o horário fica para o [day_epoch], que anula o fuso.""",
            "macro.cCron" to """# Monta uma expressão cron (min hora dia-do-mês mês dia-da-semana) a partir de HH:MM e de um campo de dia da semana.
# NÃO use expr para tirar um zero à esquerda: ele sai com 1 quando o resultado é 0, então o lado direito
# de `expr "${d}m" + 0 || echo "${d}m"` também roda e o valor fica com duas linhas.""",
            "macro.afterLabel" to """# "30m" -> "em 30m (14:35)". Mostrar a hora do relógio deixa você conferir na hora.
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num ;; m) sec=${d}((num*60)) ;; h) sec=${d}((num*3600)) ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "em ${d}num${d}u (${d}at)" || echo "em ${d}num${d}u"
}""",
            "macro.mUsageAdd" to "uso: remind.sh <quando> <texto>",
            "macro.mBadWhen" to "não consegui ler o horário (tente: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "escreva o horário como HH:MM:",
            "macro.mBadRange" to "horário fora do intervalo (00:00-23:59):",
            "macro.mNoTime" to "nenhum horário informado (ex.: daily 07:00):",
            "macro.mBadDow" to "dia da semana desconhecido:",
            "macro.mPastTime" to "esse horário já passou:",
            "macro.cDays" to """    # Forma por dias. Sem horário, mantém **a hora atual do dia** (nunca inventa um padrão).
    # Também pode chegar como uma palavra só, então a forma grudada também é aceita.""",
            "macro.cDayEpoch" to """# Transforma "HH:MM, daqui a N dias" em segundos epoch. ⚠ O busybox não tem date -d "tomorrow",
# então primeiro se tira a meia-noite de hoje e se somam os dias e o horário em cima.
# ⚠ Um valor com zero à esquerda ("08") é lido como octal pelo ${d}(()), então ele é tirado.
# ⚠ Um dia é somado como 86400s, então pode ficar uma hora errado num dia de mudança de horário de verão.""",
            "macro.cFmtAt" to """# O rótulo mostrado na lista. ⚠ Manter "tomorrow" ficaria errado quando a data virasse.""",
            "macro.mNoBody" to "diga do que devo lembrar você",
            "macro.mNoAlarm" to "não foi possível agendar",
            "macro.mNone" to "nada agendado",
            "macro.mFired" to " (disparado)",
            "macro.mRemoved" to "removido:",
            "macro.mNoSuchNum" to "essa entrada não existe (confira com remind.sh list):",
            "macro.mUsageDel" to "uso: remind.sh del <n|all>",
            "macro.bDone" to "Feito",
            "macro.bDelete" to "Excluir",
            "macro.mAskDel" to "Remover qual?",
            "macro.mAskDelH" to "número / all",
            "macro.mDeletedTitle" to "🗑 Removido",
            "macro.cPeek" to """# Mostra a lista numa notificação (pelo bloco "lista"). **Mantém os números** — é por eles que
# você aponta qual remover. ⚠ Listar sem um jeito de remover era a reclamação; daí o botão.""",
            "macro.cAskDel" to """# O botão "excluir": pede um número e o entrega ao [cmd_del] (o mesmo caminho da linha de comando).""",
            "macro.bS1" to "+10min",
            "macro.bS2" to "+1h",
            "macro.mAgain" to "- de novo em",
            "macro.mTitle" to "⏰ Lembretes",
            "macro.mAsk1" to "Lembrar você do quê?",
            "macro.mAsk1H" to "ex.: tomar o remédio",
            "macro.mAsk2" to "Quando?",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "digite de novo, por favor",
            "macro.mAskGiveUp" to "Não consegui ler ${ASK_TRIES} vezes. Você também pode adicionar pelo terminal: remind.sh 30m tomar o remédio",
            "macro.mOkTitle" to "⏰ Lembrete definido",
            "macro.mNgTitle" to "⚠ Não definido",
            "macro.cHooks" to """  # Um gancho para "um lembrete disparou" e um para "um botão da notificação foi tocado". Só
  # esses dois, não importa quantos lembretes você adicionar. Nenhum depende dos interruptores de detecção.""",
            "macro.cTiles" to """  # Só preenche os espaços vazios e os que já são nossos (nunca sobrescreve os de outros).
  # Um nome de macro com argumentos funciona a partir do 0.8.275 (antes disso, escreva sh + caminho completo).""",
            "macro.mSetupHooks" to "ganchos registrados:",
            "macro.mSetupTiles" to "blocos:",
            "macro.mPathHint" to "Atenção: esta aba ainda não consegue executar remind.sh pelo nome. Abra uma aba nova, ou execute " +
            "export PATH=${d}HOME/.z2term/macros:${d}PATH aqui.",
            "macro.cPathHint" to """  # ⚠ A pasta das macros só está no PATH desde o 0.8.287, então **as abas abertas antes disso**
  # ainda têm o antigo. Confere se o nome é encontrado e diz o que fazer se não for
  # (senão o `command not found` não tem razão visível).""",
            "macro.mPlace" to "Atenção: você ainda precisa colocar os blocos você mesmo, pela tela de edição (lápis) das Configurações rápidas.",
            "macro.lRemind" to "lembrar",
            "macro.lList" to "lista",
            "macro.cSelf" to "# Só reage às nossas próprias notificações (os nossos ids sempre começam com r)",
            "macro.cKeepRepeat" to "      # Não exclui aqui um repetido: ele deve disparar de novo amanhã.",
            "macro.cAsk" to """# O caminho usado pelo bloco / por uma notificação. ⚠ **Sempre responda com uma notificação**:
# ninguém está olhando para um terminal aqui, então falhar no stderr é lido como "toquei e
# nada aconteceu" (aconteceu — o motivo só chegou ao run.log do bloco).
#   Ilegível  -> diz por quê e pergunta de novo, até $ASK_TRIES vezes (a resposta anterior é mantida)
#   Agendado  -> mostra o plano e o texto numa notificação""",
            "macro.cSnoozeCancel" to "descarta o alarme de adiamento se ele foi adiado antes de ser feito",
            "macro.qrBody.head" to """
#
# Uso:
#   qr.sh "https://example.com"       codifica um texto e o desenha aqui
#   qr.sh -f notes.txt                codifica o conteúdo de um arquivo
#   z2-clip get | qr.sh               codifica o que está na área de transferência
#   qr.sh -o ~/qr.png "text"          salva um PNG (nada é desenhado)
#   qr.sh -t "text"                   imprime blocos em vez de uma imagem
#   qr.sh -s 24 "text"                escolhe o tamanho (em colunas; o padrão cabe na largura)
#
# z2-run: qr.sh "https://example.com"   (depois de instalar, basta o nome. Roda uma vez)
#
# # Requisitos (sem eles não roda, ou fica diferente)
#
#   1) Quem codifica é o app ... dentro de uma aba do z2term não há nada
#      para instalar (z2-qr encode). Se o qrencode já estiver lá, ele é usado no lugar.
#      Onde o app não alcança (por ssh), instale uma vez por aba (distribuição):
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      Sem nenhum dos dois, o comando de instalação é impresso e o script para.
#
#   2) A imagem (padrão) só aparece dentro de uma aba deste app ... ela é desenhada com
#      os gráficos Kitty. Por ssh, ou num terminal que não mostra imagens, você recebe
#      uma enxurrada de lixo. Lá, use -t.
#
#   3) O -t (blocos) depende da fonte do terminal ... fontes que deixam espaço entre
#      as linhas ficam legíveis para o olho, mas não para uma câmera. Para ler com uma câmera,
#      prefira a imagem padrão ou o PNG do -o.
#
#   4) A proporção é suposta ... veja "Proporção" abaixo. Achatado? Z2_QR_ASPECT.
#
# Opções:
#   -f FILE   lê a entrada de um arquivo (senão os argumentos, senão a stdin)
#   -o PNG    salva um PNG em vez de desenhar. Vários códigos viram
#             qr-1.png / qr-2.png ...
#   -t        imprime blocos. Para terminais que não mostram imagens, e por ssh
#   -s N      tamanho em colunas ao desenhar. O padrão é até 34, limitado pela largura
#   -h        esta ajuda
#
# Sobre entradas longas:
#   Um código guarda uma quantidade fixa (no máximo 2953 bytes alfanuméricos; algumas centenas
#   se ainda tiver de ser legível). Uma entrada acima de 900 bytes é dividida nas quebras de linha
#   em vários códigos, numerados [1/3] e impressos em ordem.
#   AVISO uma linha só, longa demais para caber num código, não pode ser codificada.
#
# Proporção:
#   Os terminais não informam o tamanho da célula, então uma proporção de caractere 1:2 é suposta
#   para o código ficar quadrado. Se parecer achatado, ajuste com uma variável de ambiente:
#     Z2_QR_ASPECT=0.45 qr.sh "text"     (menor = mais alto. Padrão 0.5)
""",
            "macro.cMaxBytes" to "máximo de bytes por código (divide acima disso)",
            "macro.cPngModule" to "pontos por módulo num PNG salvo",
            "macro.cTargetPx" to "largura em pixels pretendida ao desenhar",
            "macro.cMargin" to "zona de silêncio em volta do código (módulos; necessária para ler)",
            "macro.cUsageFn" to "Imprime o bloco de comentário do começo como texto de ajuda (sem número fixo de linhas).\n" +
            "# As linhas em branco não o interrompem (o NF decide), então as seções separadas por linhas em branco ficam inteiras.",
            "macro.cNeedEncoder" to "Requisito 1: algo para codificar. O qrencode se estiver aqui, senão o app.",
            "macro.mMissing" to "qr.sh: não há com o que codificar aqui - sem qrencode, e o app não alcança",
            "macro.mInstallOnce" to "  Instale uma vez nesta aba:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode",
            "macro.cMakeFns" to "Faz o PNG. O qrencode escolhe o tamanho do módulo por uma sondagem; o app recebe um alvo.",
            "macro.cMakeBlocks" to "Desenha com caracteres de bloco. Os dois caminhos emitem as próprias cores, então qualquer tema funciona.",
            "macro.qrBody.mNoTmp" to "não foi possível criar uma pasta de trabalho",
            "macro.qrBody.mUnreadable" to "não foi possível ler:",
            "macro.qrBody.mEmpty" to "entrada vazia",
            "macro.mSizeNum" to "-s recebe um número",
            "macro.qrBody.mUsageHint" to "veja qr.sh -h",
            "macro.mPieceFail" to "qr.sh: não foi possível montar o código %d: %s",
            "macro.qrBody.cCollect" to "---- junta a entrada ----",
            "macro.cSplit" to "---- divide nas quebras de linha quando for longo demais ----",
            "macro.cSplit2" to "Passa para o próximo pedaço logo antes do total acumulado passar de MAX_BYTES. As linhas\n# nunca são cortadas no meio, então um texto multibyte sobrevive.",
            "macro.cTrim" to "Tira a quebra de linha do fim (nunca codifica uma quebra de linha que a entrada não tinha).",
            "macro.cSize" to "---- escolhe o tamanho de exibição pela largura do terminal ----",
            "macro.cPngWidth" to "Devolve a largura em pixels de um PNG (bytes 16-19, o IHDR).",
            "macro.cModulePx" to "Calcula de volta, a partir da largura pretendida, os pontos por módulo.",
            "macro.cInline" to "Desenha um PNG no lugar com o protocolo de gráficos Kitty.\n# c/r fixam quantas células ele ocupa, depois imprime esse número de quebras de linha para levar o cursor para baixo dele.",
            "macro.cEmit" to "---- emite ----",
            "macro.cAnsi" to "O ANSIUTF8 emite as próprias cores, então claro/escuro sai certo seja qual for o tema.",
            "macro.mdBody.head" to """
#
# Uso:
#   md.sh README.md                mostra aqui, neste terminal
#   md.sh -v README.md             abre dentro do app (z2-view)
#   cat notes.md | md.sh           lê da entrada padrão
#   md.sh -o page.html README.md   grava o HTML e para (nada abre)
#
# z2-run: md.sh README.md   (depois de instalar, basta o nome. Roda uma vez)
#
# Opções:
#   -v        abre dentro do app (z2-view)
#   -o FILE   grava o HTML ali. Nada é mostrado aqui
#   -w N      quebra em N colunas (padrão: a largura da tela, até 100)
#   -p        sem cor e sem enfeite (texto puro)
#   -t TITLE  título da tela de leitura (padrão: o primeiro título, senão o nome do arquivo)
#   -h        esta ajuda
#
# Um documento longo, uma tela por vez: md.sh README.md | less -R   (-R mantém as cores)
#
# Nada para instalar: sh e awk bastam.
# Títulos, parágrafos, listas (aninhadas, numeradas, [ ]/[x]), citações, código, tabelas,
# linhas horizontais, links, imagens e front matter (---) são tratados. AVISO os títulos setext
# (=== / ---) e os links de referência ([texto][1]) saem como foram escritos.
#
# O que esperar:
#   - As imagens só aparecem no lugar dentro de uma aba deste app (desenhadas com os gráficos
#     kitty pelo z2-img). Por ssh você recebe a legenda e o caminho.
#   - Os links podem ser tocados (OSC 8). Um terminal sem isso os mostra como texto.
#   - Este terminal não desenha itálico, então a *ênfase* fica sublinhada (como faz o man).
#   - Uma tabela larga demais para a tela é quebrada em linhas "título: valor".
#   - O -v embute na página as imagens que estão neste dispositivo (no máximo 1,5 MB cada).
#     A tela de leitura não busca nada na rede, então as imagens http(s)
#     ficam como a legenda.
""",
            "macro.mdBody.cCollect" to "# Junta a entrada (vários arquivos viram uma leitura só, na ordem dada).",
            "macro.cTitle" to "# Título: -t, senão o primeiro título #, senão o nome do arquivo",
            "macro.cWidth" to "# A largura da tela",
            "macro.cColorEnv" to "# Uma saída para quando as cores devem (ou não devem) passar por um pipe, ex.: para um paginador",
            "macro.cImgWhere" to "# Uma imagem só aparece no lugar dentro de uma aba (gráficos kitty) com o z2-img presente.",
            "macro.cAmbi" to "# Com que largura contar os caracteres ambíguos (linhas, setas, dígitos em círculo). Use\n" +
            "# Z2_MD_AMBIWIDTH=2 se as Configurações os tratam como largura cheia. Padrão 1, como o app.",
            "macro.cAwkFile" to "# AVISO o programa awk é gravado num ARQUIVO e entregue com -f. Passado como\n" +
            "#   argumento, o gawk corta o programa onde um caractere multibyte (como o\n" +
            "#   japonês destas notas) cruza o limite de leitura de 4096 bytes, e morre com\n" +
            "#   \"unexpected newline or end of string\".",
            "macro.cInlineImg" to "# Embute as imagens na página da tela de leitura, como URIs data:.\n" +
            "# AVISO o z2-view não busca nada na rede (JavaScript e carregamentos estão desligados), então\n" +
            "#   uma imagem que não for embutida simplesmente não aparece. 4 MB para a página inteira.",
            "macro.cImgLoop" to "# Só um documento com imagens é lido linha por linha (para chamar o z2-img no lugar)",
            "macro.mdBody.mUsageHint" to "veja md.sh -h",
            "macro.mdBody.mNoTmp" to "não foi possível criar uma pasta temporária",
            "macro.mdBody.mUnreadable" to "arquivo ilegível:",
            "macro.mdBody.mEmpty" to "nada para ler",
            "macro.mWidthNum" to "-w recebe um número",
            "macro.mNoDir" to "não foi possível criar a pasta onde colocá-lo",
            "macro.mConvFail" to "a conversão falhou",
            "macro.mNoView" to "não há z2-view aqui (o app precisa ser atualizado). O HTML está em:",
            "macro.mImgLabel" to "[imagem]",
            "menu.noApps" to "(nenhum aplicativo encontrado)",
            "menu.cat.AudioVideo" to "Som e vídeo",
            "menu.cat.Development" to "Desenvolvimento",
            "menu.cat.Graphics" to "Gráficos",
            "menu.cat.Game" to "Jogos",
            "menu.cat.Network" to "Internet",
            "menu.cat.Office" to "Escritório",
            "menu.cat.System" to "Sistema",
            "menu.cat.Utility" to "Utilitários",
            "menu.cat.Other" to "Outros",
            "menu.usage" to "uso: z2menu [list]   (sem argumento: XML do menu pipe do openbox)",
            "reminderView.labels" to "Lembretes|Adicionar|Excluir|Conteúdo|Data e hora|Repetição|Uma vez|Diário|Dias úteis|Semanal (dia da semana escolhido)|Texto do agendamento (opcional; substitui a data e a repetição)|Agendados|Repetindo|Avisados|Nenhum lembrete|Excluir este lembrete e as notificações pendentes dele?",
            "rssFeeds.rssFeedsLabel" to "Assinaturas",
            "run.errMsg" to "z2run: o Xvnc :${d}{DISPLAY_NUM} não iniciou. Veja /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log.",
            "scan.mNoPm" to "z2scan: nenhum gerenciador de pacotes compatível (apk/apt-get/pacman) foi encontrado.",
            "scan.mInstalling" to "z2scan: instalando",
            "scan.mInstallFail" to "z2scan: falha ao instalar",
            "scan.mRemoteBlocked" to "z2scan: alvo remoto recusado. Verifique só o localhost, ou passe --allow-remote para um alvo que você está autorizado a testar.",
            "scan.mRemoteWarn" to "z2scan: AVISO verificando um host remoto. Só verifique sistemas que você está explicitamente autorizado a testar.",
            "scan.mNoLynis" to "z2scan: lynis não encontrado; voltando ao autodiagnóstico integrado ('z2scan self'). Instale com 'z2scan setup'.",
            "scan.mNoCve" to "z2scan: nenhum verificador de CVE (trivy/grype) encontrado. Instale o trivy ou o grype e tente de novo.",
            "scan.mSelfHead" to "== autodiagnóstico do z2scan (este dispositivo / localhost) ==",
            "scan.mUidRoot" to "rodando como uid 0 (atenção: o proot/z2root fornece um root falso)",
            "scan.mPubPort" to "porta TCP escutando em todas as interfaces (não só no localhost), porta",
            "scan.mSshdEmpty" to "o sshd_config permite login com senha vazia (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "o sshd_config ativa a autenticação por senha (PasswordAuthentication yes); a autenticação por chave é mais segura",
            "scan.mSshdRoot" to "o sshd_config permite o login do root (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys tem permissões frouxas, modo",
            "scan.mSshDir" to "~/.ssh tem permissões frouxas, modo",
            "scan.mWorldWrite" to "arquivos graváveis por todos encontrados (mostrando até 20):",
            "scan.mSuid" to "binários SUID presentes (informativo; root falso no proot/z2root):",
            "scan.mPath" to "o PATH contém um elemento vazio/'.' (a pasta atual no PATH é arriscada)",
            "scan.mFound" to "achados:",
            "scan.mClean" to "nenhum problema óbvio encontrado.",
            "scan.mSaved" to "z2scan: referência salva:",
            "scan.mSaveFail" to "z2scan: não foi possível gravar a referência:",
            "scan.mNoBase" to "z2scan: ainda não há referência. Execute 'z2scan self --save' uma vez para registrar o estado atual.",
            "scan.mNoChange" to "[ OK ] nada mudou desde a referência.",
            "scan.mAdded" to "== novo desde a referência ==",
            "scan.mRemoved" to "== sumiu desde a referência ==",
            "scan.mCleared" to "z2scan: referência apagada.",
            "scan.mLangDiff" to "z2scan: AVISO a referência foi salva em outro idioma, então tudo vai parecer mudado. Salve de novo com 'z2scan self --save'.",
            "scan.langTag" to "pt-BR",
            "scan.usageText" to """
        z2scan - teste de vulnerabilidades deste dispositivo / localhost (defensivo, nenhum dado sai).

        Verifica só o localhost por padrão. Os verificadores são instalados pelos pacotes oficiais
        da sua distribuição (nmap/lynis/trivy); nada vem incluído e os resultados ficam locais.

          z2scan self [--save]         autodiagnóstico integrado (sem ferramentas externas): portas abertas,
                                       configuração do sshd, permissões das chaves SSH, graváveis por todos/SUID, PATH.
                                       --save também registra o resultado como referência.
          z2scan diff [--quiet]        roda de novo o autodiagnóstico e imprime só o que mudou
                                       desde a referência. Sai com 1 quando há algo novo,
                                       então cabe direto no z2-when. --quiet não imprime
                                       nada quando nada mudou.
          z2scan baseline [clear]      mostra a referência salva (ou a apaga)
          z2scan setup                 instala os verificadores (nmap, lynis) via apk/apt-get/pacman
          z2scan net [--allow-remote] [target]
                                       verificação TCP com nmap. O alvo padrão é 127.0.0.1.
                                       Um alvo não local exige --allow-remote.
          z2scan host                  auditoria do host com o lynis (volta ao 'self' se não houver)
          z2scan cve                   verificação de CVEs conhecidos no rootfs com trivy/grype, se houver

        Só verifique sistemas que você está explicitamente autorizado a testar.
    """.trimIndent(),
            "translationMacro.description" to "Traduz um texto usando um comando instalado à parte.",
            "translationMacro.missing" to "O comando trans não está instalado neste ambiente local. Instale você mesmo a sua ferramenta de tradução e tente de novo. Nada é baixado automaticamente.",
            "translationMacro.network" to "O texto a traduzir é enviado ao serviço externo usado pela sua ferramenta. Valem os termos dele e a disponibilidade da rede.",
            "translationSetup.setup" to "Abra um terminal no mesmo ambiente Linux local, execute você mesmo o comando abaixo e depois tente traduzir de novo.",
            "translationSetup.alpineRepo" to "Se o pacote não for encontrado, ative o repositório community da mesma versão do Alpine em /etc/apk/repositories.",
            "translationSetup.ubuntuRepo" to "Se o pacote não for encontrado no Ubuntu, ative o repositório universe dele e tente de novo.",
            "translationSetup.unknown" to "Distribuição não reconhecida. Escolha o comando do seu ambiente Linux local:",
            "voice.head" to """# voice.sh — o receptor do "Comando de voz": o que você diz controla música, volume, timers, lembretes, tempo e mais.
#
# Preparação (uma vez):
#   sh ~/.z2term/macros/voice.sh setup    registra 4 regras (comando de voz / comando de voz com a tela bloqueada / timer por voz / contar passos)
#   Contar passos só mantém o contador de passos funcionando para "quantos passos hoje"; nunca executa nada
#   depois fale pelo bloco "Comando de voz" das Configurações rápidas ou pelo assistente digital (manter o botão liga/desliga pressionado etc.)
#
# Pelo terminal:
#   voice.sh words                 abre a tabela de palavras (~/.z2term/voice/words-<lang>.txt; adicione ou mude frases)
#   voice.sh try <frase>           testa uma sem falar (ex.: voice.sh try próxima música)
#   voice.sh listen                abre a tela de escuta e fale (o mesmo que o bloco)
#   voice.sh lang [idioma]         o idioma da tabela de palavras (voice.sh lang - lista os idiomas)
#   voice.sh ai [off|codex|claude|agy]   se usa IA (desativada no início)
#   voice.sh model [nome]          o modelo da IA (vazio = o padrão daquela IA)
#   voice.sh setup / help
#
# A IA começa desativada. Mesmo ativada, só é chamada quando você começa com a palavra de sinal ("IA", "pergunta" ...).
# O tempo só consulta o wttr.in quando você diz "tempo" (lugar: weather_city= em ~/.z2term/voice/config).
# As suas próprias palavras: "palavras = comando" em ~/.z2term/voice/commands.txt (vistas antes da tabela de palavras)
# Nomes para ligar: "nome número" em ~/.z2term/voice/contacts.txt / registro: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — macro inicial. Reage ao que acontece no dispositivo.",
                "# O app (z2-when) é quem espera, então isto não fica residente: roda uma vez e sai.",
                "# Preparação: Configurações -> \"Detecção de eventos do sistema\" ativada",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (fones: uma segunda regra com 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# O que aconteceu chega em Z2_WHEN_EVENT (z2-when events lista os nomes).",
                "# Para testar à mão:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT está vazio. Para testar à mão: Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"O carregamento começou\" ;;",
                "  power_disconnected) z2-toast \"O carregamento parou\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — avisa da bateria fraca, mas se adapta ao estado atual.",
                "# Usa o z2-state para ver se a tela está ligada: toast se estiver, notificação se não.",
                "# Preparação: Configurações -> \"Detecção de eventos do sistema\" ativada",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# O z2-when entrega o nível; se faltar, usa o z2-state para isto também rodar à mão.",
            ),
            "macro.batteryAlert.2" to listOf(
                "# Não diz nada enquanto carrega (não está gastando de verdade)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"Bateria ${d}{level}%\"",
                "else",
                "  z2-notify -h \"Bateria fraca\" \"Restam ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — lê em voz alta a bateria e a conexão toda manhã.",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# O alarme do sistema o acorda, então dispara também no Doze (pode atrasar alguns minutos).",
                "# Não depende dos interruptores de detecção.",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=móvel; fi",
                "z2-say \"Bom dia. Bateria ${d}{level} por cento, rede ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — copia um código de uso único das notificações e o apaga depois de",
                "# TTL segundos se a área de transferência ainda tiver esse mesmo valor.",
                "# Preparação: Configurações -> \"Detecção de notificações\" ativada + conceda ao sistema o acesso às notificações",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ O Android 15+ pode ocultar os códigos em notificações sensíveis. Para um caminho seguro, use",
                "#    a variante por SMS (otp-sms.sh) — o texto dos SMS nunca é ocultado.",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — copia um código de uso único dos SMS recebidos e o apaga depois de",
                "# TTL segundos se a área de transferência ainda tiver esse mesmo valor.",
                "# A variante por SMS do otp-clip.sh. Ao contrário das notificações, o texto dos SMS nunca",
                "# é ocultado pela proteção de notificações sensíveis (Android 15+) e funciona com a tela bloqueada.",
                "# Preparação: Configurações -> \"Detecção de SMS\" ativada + conceda ao sistema a permissão de SMS",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — mostra uma notificação para copiar os números de telefone das notificações de chamada.",
                "# O botão \"Copiar\" dela põe o número na área de transferência (por que espera um toque: veja abaixo).",
                "# Preparação: Configurações -> \"Detecção de notificações\" ativada + conceda ao sistema o acesso às notificações",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# Para pegar também as chamadas perdidas, registre uma segunda regra (o mesmo script, outra categoria):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — lê feeds e guarda só o que é novo, como notificação e como texto.",
                "# Ao contrário dos outros exemplos, este **não** fica residente: é do tipo",
                "# 'roda uma vez a partir de um gatilho de horário e sai'.",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — abre o que o rss.sh coletou, o mais novo primeiro, um artigo por toque.",
                "# Aparece como um botão de macro no widget de estado, então cada toque abre o próximo.",
            ),
            "macro.remind" to listOf(
                "# remind.sh — lembra você com uma notificação: uma vez (em 30m, às 18:30) ou",
                "# repetindo (diário / dias úteis / semanal). Dispara com o app fechado (um alarme do sistema",
                "# o acorda; não precisa de interruptor de detecção). Adie pelos botões da notificação.",
            ),
            "macro.qr" to listOf(
                "# qr.sh — transforma um texto ou um arquivo num código QR: desenhado aqui, ou salvo como PNG.",
                "# Passa algo para outro dispositivo sem redigitar. Roda uma vez, sem ficar residente.",
            ),
            "macro.md" to listOf(
                "# md.sh — lê aqui um arquivo Markdown formatado, ou o abre dentro do app.",
                "# Títulos, listas, tabelas, código, links; as imagens aparecem no lugar. Roda uma vez, sem ficar residente.",
            ),
            "macro.cImportantTemplate" to listOf(
                "# Um feed ou uma palavra por linha que não pode ficar enterrado (as linhas que começam com # são ignoradas).",
                "# Parte de uma URL ou parte de um título funciona. Cada correspondência ganha uma notificação própria.",
                "# ex.:", "#example.org",
            ),
            "macro.usage" to listOf(
                "uso: z2-macro <subcomando>",
                "  list                lista os exemplos incluídos (marcados nova / igual / difere)",
                "  install <name|all>  copia para ~/.z2term/macros/ (nunca sobrescreve; -f para forçar)",
                "  remove <name>       exclui a sua cópia de um exemplo incluído (para se for diferente; -f para forçar)",
                "  diff <name>         o que difere entre a sua cópia e a incluída (a sua à esquerda)",
                "  show <name>         imprime o script",
                "  run <name>          executa aqui (Ctrl-C para parar)",
                "  dir                 imprime onde as macros ficam",
                "  actions ...         macros de ações do Android (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
                "Assinaturas", "Adicionar", "Artigos", "Mudar", "Remover", "URL do feed",
                "Remover esta assinatura? Os artigos coletados serão mantidos.",
                "As mudanças valem na próxima leitura. Os artigos coletados são mantidos.",
                "Nenhuma assinatura. Use Adicionar para digitar uma URL RSS ou Atom.",
                "Digite uma URL HTTP(S) sem espaços nem credenciais (até 2048 caracteres).",
                "Esta assinatura já existe.",
                "Esta assinatura mudou. Atualize a lista e tente de novo.",
                "Outra mudança está sendo salva. Tente de novo.",
                "Anterior", "Próxima",
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

/** [Z2ApiMsg] の Português do Brasil 版。上書きしていない文言は英語で出る。 */
private class ApiMsgPtBr(d: String) : Z2ApiMsg("pt-BR", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — escolhe um documento; copia para a HOME e imprime o caminho.\n# z2-file save FILE [MIME] — escolhe um destino e salva uma cópia.\n# Cancelar conta como falha. Até 512 MiB; a escolha expira depois de 5 minutos."

    override val usbHelp: String get() = """
        |# z2-usb list              … lista os dispositivos USB conectados ao celular
        |# z2-usb allow [número]    … pede ao Android que os programas Linux possam usar um dispositivo
        |# Execute allow uma vez depois de conectar um dispositivo. A permissão vale até desconectá-lo.
    """.trimMargin()

    override val usbUsage: String get() = "uso: z2-usb list | z2-usb allow [número|caminho|VID:PID]"

    override val usbNoDevices: String get() = "Nenhum dispositivo USB está conectado ao celular."

    override val usbNeedSelector: String get() = "Há mais de um dispositivo USB conectado; indique o número dele conforme o z2-usb list."

    override fun usbPermissionRequested(path: String): String = "Aprove o acesso USB na tela do Android: $path"

    override fun usbAlreadyAllowed(path: String): String = "O acesso USB já está permitido: $path"

    override fun usbNotFound(selector: String): String = "Dispositivo USB não encontrado: $selector"

    override val usbStateAllowed: String get() = "permitido"

    override val usbStateNeedsAllow: String get() = "falta permitir"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "título" "texto"  /  z2-notify [-h] "texto"
        |#   -h / --high / --banner : mostra em destaque (heads-up) no topo da tela
        |#   -b <rótulo>            : adiciona um botão de resposta (até 3). Apertar um acrescenta
        |#                            notify_action a ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <nome>              : um identificador desta notificação (para diferenciar as respostas)
        |#   -c <texto>             : adiciona um botão "Copiar" que põe <texto> na área de transferência.
        |#                            Use no lugar do z2-clip set quando a macro roda em
        |#                            segundo plano: o Android 10+ só deixa o app da frente gravar na
        |#                            área de transferência, então um z2-clip set sem ninguém olhando é descartado.
    """.trimMargin()

    override val notifyUsage: String get() = "uso: z2-notify [-h] [-n nome] [-c texto] [-b rótulo]... <título> [texto]"

    override val toastHelp: String get() = """
        |# z2-toast <mensagem> … uma mensagem curta embaixo da tela (um toast).
        |# Some sozinha e não deixa nada. Use o z2-notify quando ela tiver de ficar,
        |# ou quando quiser um botão para apertar.
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … abre as ferramentas de QR com a câmera já lendo.
        |# Depois de ler, um botão abre o conteúdo no app dele (navegador, LINE e outros apps,
        |# discador, e-mail, SMS, Wi-Fi, contatos, agenda). Nada abre sozinho.
        |# Coloque num bloco de Configurações rápidas ou num painel lateral.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … cria um código QR.
        |#   Nada para instalar: o app codifica (o mesmo codificador da tela de QR).
        |#   Sem TEXT, lê a entrada padrão. -o é onde o PNG vai
        |#   (padrão ~/.z2term/qr/qr.png) e o caminho é impresso. -t desenha aqui no lugar,
        |#   com caracteres de bloco. -p é o tamanho pretendido em pixels (padrão 600),
        |#   -m a zona de silêncio em módulos (padrão 4). Até 2000 bytes.
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <texto> … entrega um texto ao menu de compartilhamento do Android (envia a outro app).
        |# --file FILE [FILE ...]: envia o conteúdo de arquivos (até 32 arquivos, 512 MiB no total).
        |# Os caminhos são lidos no shell local atual. -- força o modo texto. As cópias com mais de 24 horas são limpas no próximo compartilhamento.
        |# Todos os argumentos viram um corpo só. O app de destino é escolhido na tela,
        |# então isto precisa de alguém presente — não é para uma macro rodando sozinha.
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <file.html> [título] … lê dentro do z2term uma página que você fez aqui.
        |# Sem servidor e sem navegador: o arquivo é entregue ao app e mostrado como está.
        |# JavaScript e carregamentos pela rede ficam desligados, então nada da página pode rodar nem se comunicar.
        |# Os links http:// e https:// abrem no seu navegador de sempre.
        |# O tema do celular chega como variáveis CSS (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), então uma página escrita com elas segue as cores do terminal.
        |# Até 4 MB. As imagens precisam vir embutidas (URIs data:); as remotas não são buscadas.
        |# Use --edge PAINEL:ITEM para enviar a um item type=view existente do painel lateral.
        |# Use --controls FILE.json para formulários e ações genéricos tratados por uma macro instalada.
        |# Os itens de visualização usam atualização manual por padrão. view-refresh=auto executa o run= ao abrir; o z2-view volta para esse item.
        |# Use - ou um pipe (ls -l | z2-view) para ler a entrada padrão. Um texto que não começa com < é mostrado
        |# com quebra de linha numa fonte monoespaçada, sem os códigos de cor; --text / --html escolhem explicitamente.
        |# Uma pinça ajusta o texto; --pinch page amplia a página inteira no lugar. --text-size 50..300 define o
        |# tamanho do texto (por cento) desta página. Os dois valem para a tela cheia; um item de painel usa as configurações dele.
        |# --no-refresh / --no-find escondem os botões Atualizar / Localizar da tela cheia (mostrados por padrão).
    """.trimMargin()

    override val viewUsage: String get() = "uso: z2-view [--edge PAINEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <arquivo|-> [título]"

    override val viewUnreadable: String get() = "z2-view: arquivo ilegível:"

    override val openHelp: String get() = """
        |# z2-open <url|caminho> … abre com o app padrão (https://… ou /sdcard/…).
        |# Qual app abre é escolha do Android; o z2term só entrega.
        |# Um caminho é um caminho no **celular** (/sdcard/…), não dentro da distribuição.
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <arquivo|->… … desenha a imagem no terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Passe - para ler uma imagem da stdin.
        |#   -w <cols>  largura em colunas do terminal (padrão: caber no terminal)
        |#   -r <rows>  altura em linhas do terminal (com -w, o tamanho é usado como foi dado)
        |#   --clear    remove todas as imagens desenhadas até agora
        |#   -f         envia mesmo quando a stdout não é um terminal
        |# ⚠ As imagens só aparecem numa aba do z2term, ou num terminal que fala o protocolo
        |#   de gráficos kitty. Em qualquer outro lugar (por ssh, num paginador) você recebe lixo.
        |# ⚠ A proporção supõe que uma célula é duas vezes mais alta do que larga. Se parecer
        |#   achatada, ajuste: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (menor = mais alto).
    """.trimMargin()

    override val imgUsage: String get() = "uso: z2-img [-w cols] [-r rows] [--clear] [-f] <arquivo|->..."

    override val imgNoFile: String get() = "não foi possível ler o arquivo:"

    override val imgNotTty: String get() = "a stdout não é um terminal, então nada foi enviado (-f envia mesmo assim)."

    override val imgNoSize: String get() = "não foi possível ler o tamanho em pixels; desenhando como quadrado:"

    override val qrUsage: String get() = "uso: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]"

    override val qrEncodeFailed: String get() = "z2-qr: não foi possível criar o código (o texto é longo demais? até 2000 bytes)"

    override val clipHelp: String get() = """
        |# z2-clip get        … imprime a área de transferência na stdout
        |# z2-clip set [text] … põe o texto (ou a stdin, se omitido) na área de transferência
        |# ⚠ O Android 10+ só deixa o app da frente (ou o método de entrada em uso) mexer na
        |#   área de transferência. De uma macro rodando em segundo plano isto é descartado sem aviso,
        |#   então ali use z2-notify -c <texto>: ele adiciona um botão "Copiar" que sempre funciona.
    """.trimMargin()

    override val batteryHelp: String get() = "# Imprime o nível / estado de carga em JSON ({\"level\":N,\"charging\":bool})."

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (padrão 200ms)"

    override val editHelp: String get() = "# z2-edit <arquivo>   … edita um arquivo de texto no editor do próprio z2term (caixa de texto simples; Salvar grava de volta)"

    override val sayHelp: String get() = "# z2-say <texto>      … fala com o TTS do dispositivo (lê a stdin sem argumento)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (padrão toggle). status lê o estado do Android, incluindo mudanças de fora (on/off)."

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … captura só a parte que você contornar (padrão free). Precisa do Android 11 e do z2term Android actions."

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (padrão playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   Volume da mídia. Imprime o atual/máximo resultante."

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (padrão light). Lê uma amostra e devolve JSON."

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Dispara qualquer Intent do Android (startActivity por padrão). Um primeiro argumento que não é opção é a ACTION.
        |# A janela livre usa por padrão a reutilização da tarefa e os limites do sistema. --reuse-task / --os-bounds continuam aceitos.
        |# --bounds-only pede limites de 80% sem uma chave de modo; não combina com --os-bounds.
        |# --no-scale desativa o pedido de janela livre reduzida da Motorola nesta abertura.
        |# -p PKG sozinho abre esse app. --window full|freeform|split|ask escolhe o pedido de abertura (precisa de suporte do sistema).
        |# --profile N abre o app (-p/-n) em outro perfil, como o de trabalho. Sem tela dividida. N está no comando que o painel lateral salva para esse app.
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … o estado atual do dispositivo, completo, em JSON
        |# z2-state <chave>    … só esse valor, cru (entra direto num teste)
        |# chaves: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# ex.: [ "${d}(z2-state charging)" = "true" ] && echo carregando
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <pergunta>
        |#   Faz uma pergunta à pessoa e imprime a resposta dela na stdout.
        |#   A pergunta chega como uma notificação com um **campo de resposta**, então dá para
        |#   responder pela aba de notificações sem abrir o app (uma macro rodando em
        |#   segundo plano também pode perguntar).
        |#   -t <seg>   quanto esperar uma resposta (padrão 300 = 5 min)
        |#   -H <dica>  rótulo mostrado no campo de resposta
        |#   -d <texto> resposta sugerida (mostrada na notificação)
        |# Dispensar a notificação sem responder, ou esgotar o tempo, sai com um código
        |# diferente de zero e não imprime nada — então você pode escrever "ou desistir":
        |#   name=${d}(z2-ask "Nome do branch?") || exit 1
        |# Compare com z2-notify -b <rótulo>, que só oferece as escolhas que você preparou.
    """.trimMargin()

    override val askUsage: String get() = "uso: z2-ask [-t seg] [-H dica] [-d padrão] <pergunta>"

    override val listenHelp: String get() = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Imprime na stdout o que você diz. O reconhecimento roda **só no dispositivo**; a sua voz
        |#   nunca vai para a rede. Precisa do Android 12+ e do reconhecimento de voz no dispositivo
        |#   (para o seu idioma, os dados de voz offline dele).
        |#   Fale com o z2term na tela (o Android só dá o microfone ao app que está na tela).
        |#   Para quando você fica em silêncio. A primeira execução pede acesso ao microfone.
        |#   -w <palavras>  aceita só estas palavras (separadas por vírgula) e imprime a ouvida.
        |#               Todos os palpites do reconhecedor são conferidos, não só o primeiro.
        |#   -l <idioma> idioma a reconhecer (ex.: pt-BR, en-US; padrão: o idioma do dispositivo)
        |# Saída: 0 = feito / 1 = nada ouvido, ou nenhuma das palavras / 2 = indisponível (motivo na stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin()

    override val listenUsage: String get() = "uso: z2-listen [-w palavra,palavra,...] [-l idioma]"

    override val listenPrompt: String get() = "Fale agora… (para quando você fica em silêncio)"

    override val listenNeedsAndroid12: String get() = "O z2-listen precisa do Android 12 ou mais recente (antes disso, o reconhecimento não pode ficar no dispositivo)."

    override val listenBusy: String get() = "Já está escutando (outro z2-listen está rodando)."

    override val listenEngineBusy: String get() = "O reconhecimento de voz do dispositivo está ocupado (outro app pode estar usando). Tente de novo mais tarde."

    override val listenEngineStuck: String get() = "O reconhecimento de voz não está respondendo. Se continuar, force a parada do app de reconhecimento do dispositivo (como o Android System Intelligence) em Configurações do Android › Apps."

    override val listenStopped: String get() = "Parou de escutar."

    override val listenNoOnDevice: String get() = "Este dispositivo não tem reconhecimento de voz no próprio aparelho. O z2-listen nunca manda a sua voz para a rede, então não pode rodar aqui."

    override val listenNoPermission: String get() = "O acesso ao microfone não está permitido. Execute o z2-listen de novo com o z2term na tela para receber o pedido, ou permita nas configurações do Android."

    override val listenNotShown: String get() = "Traga o z2term para a tela e tente de novo (o Android só dá o microfone ao app que está na tela)."

    override val listenNoMic: String get() = "Não foi possível usar o microfone (o z2term saiu da tela, ou outro app está gravando)."

    override val listenNoLanguage: String get() = "Os dados de voz no dispositivo para este idioma não estão instalados. Baixe-os nas configurações de entrada de voz do dispositivo (reconhecimento de voz offline)."

    override val listenNoSpeech: String get() = "Nada foi ouvido."

    override val listenTimedOut: String get() = "Tempo esgotado."

    override fun listenNoMatch(heard: String): String = "Nenhuma das palavras (ouvido: $heard)"

    override fun listenFailed(code: Int): String = "O reconhecimento de voz falhou (erro $code)."

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … impede a tela de desligar sozinha durante esse tempo
        |# z2-screen keepon off          … volta agora, sem esperar o prazo
        |# z2-screen status              … estado atual em JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# Isto muda a configuração "tempo limite da tela" do sistema inteiro, então vale mesmo com o app
        |# em segundo plano. NÃO é a trava de tela da barra de ferramentas, que só dura enquanto o
        |# app está na tela — essa fica intacta.
        |# O valor original é guardado e sempre regravado no prazo (mesmo se o app
        |# for encerrado ou o dispositivo reiniciar). No máximo 24h de uma vez.
        |# Precisa de "modificar as configurações do sistema" (Configurações > tempo limite da tela > permitir).
        |# ex.: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "uso: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <macro.sh | comando...> [--off <comando...>] [-l <rótulo>] [-i <desenho>]
        |#                           … põe algo no bloco de Configurações rápidas 1-$tiles
        |# z2-tile add <1-$tiles>         … pede para colocar esse espaço no painel (Android 13+)
        |# z2-tile list              … os $tiles espaços em TSV (espaço / rótulo / comando; '-' = vazio)
        |# z2-tile clear <1-$tiles|all>   … esvazia um espaço
        |#
        |# O que você atribui
        |#   Ou **o nome de arquivo de uma macro** em ~/.z2term/macros/, ou **um comando** executado
        |#   como foi digitado — qual dos dois é, é deduzido pelo nome.
        |#   Uma macro pode receber argumentos ('remind.sh ask'): a primeira palavra decide se é
        |#   uma macro, o resto é passado a ela. Dois espaços com a mesma macro ficam com o mesmo
        |#   rótulo, então dê -l a eles.
        |#   O comando roda com Z2_TILE=<espaço> (e Z2_TILE_MACRO para uma macro) no ambiente.
        |#   ⚠ Um nome terminado em .sh que não está em ~/.z2term/macros/ é recusado aqui — como
        |#     comando ele seria procurado no PATH (que não inclui a pasta das macros),
        |#     então o bloco não faria nada e só diria isso no tile/run.log.
        |#
        |# O que um toque faz
        |#   Toque para executar, toque de novo para parar (o mesmo esquema dos botões do widget).
        |#   O bloco fica com cara de "ativado" enquanto roda (a cor é o destaque do sistema, não a nossa).
        |#   Com --off você tem **dois comandos**: o toque alterna entre eles e o bloco
        |#   fica com cara de "ativado" enquanto está ligado. Use quando desligar é um comando próprio
        |#   (z2-torch on / off).
        |#   Os pares comuns lembram as alternâncias bem-sucedidas. As atribuições diretas de z2-torch seguem o que
        |#   o Android observa, incluindo mudanças de fora. O z2-screen keepon segue a configuração realmente ativa.
        |#
        |# Colocar no painel
        |#   No Android 13+ o `set` pergunta na hora se quer colocar o bloco no painel, e
        |#   esse diálogo traz **o nome e o ícone que você acabou de atribuir**. Se disser não, nada é colocado.
        |#   Há exatamente $tiles espaços: o número é fixo no manifesto e não pode crescer
        |#   em execução. Os espaços sem atribuição nem aparecem na tela de edição,
        |#   então ter $tiles deles não custa nada.
        |#   ⚠ Só aparece enquanto o z2term está na frente, e só um pode ser pedido por vez — uma
        |#     macro que atribui dois espaços seguidos só vai perguntar pelo primeiro. Use
        |#     z2-tile add <espaço> depois para os outros.
        |#   ⚠ Colocar um bloco continua sendo decisão sua — o Android não deixa um app pôr os próprios blocos
        |#     lá. Na tela de edição (lápis) do painel de Configurações rápidas, procure
        |#     **z2term <espaço>**.
        |#   ⚠ Essa lista mostra o nome e o ícone do manifesto, não os que você atribuiu (o Android não
        |#     tem como mudá-los em execução), então o z2-tile list diz qual número é qual.
        |#
        |# O desenho (-i)
        |#   Um espaço ganha sozinho um ícone adequado quando o nome o denuncia (remind.sh -> um
        |#   relógio), e o z2-icon o troca pelo que você quiser — depois disso, ele fica como está.
        |#   -i define o desenho **antes** de esse diálogo aparecer, então uma linha só decide o
        |#   nome e o ícone juntos (z2-icon sample lista os nomes que dá para passar).
        |#   ⚠ Um nome desconhecido é recusado **antes de qualquer atribuição** — um espaço definido mas
        |#     com o desenho errado é mais difícil de notar do que um que nunca foi definido.
        |#
        |# ex.: z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "sem dormir"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l lanterna
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin()

    override val tileUsage: String get() = "uso: z2-tile set <1-$tiles> <macro.sh|comando...> [--off <comando...>] [-l rótulo] " +
                "[-i desenho] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: esse desenho não existe: $name (liste com z2-icon sample). Nada foi atribuído."

    override fun tileAddEmpty(n: Int): String = "z2-tile: o espaço $n ainda não tem nada (atribua antes: z2-tile set $n <...>)"

    override val tileAddUnsupported: String get() = "z2-tile: não é possível pedir para adicionar o bloco aqui (precisa do Android 13, com o z2term em primeiro plano). " +
                "O espaço está atribuído — coloque-o pela tela de edição (lápis) do painel de Configurações rápidas."

    override val tileAddAsked: String get() = "perguntei ao Android se adiciona o bloco — responda o diálogo. " +
                "(Não apareceu nada? Coloque-o pela tela de edição.)"

    override val iconHelp: String get() = """
        |# Troca os ícones da barra de status e dos blocos de Configurações rápidas por um desenho seu em pixels.
        |#
        |# Comece aqui
        |#   z2-icon pick 1     ... escolhe um desenho numa lista e o põe no bloco 1
        |#   z2-icon list       ... o que cada espaço tem agora ('-p' mostra também os desenhos)
        |#
        |# O que <alvo> quer dizer
        |#   notify    o ícone de toda notificação que este app mostra (o da barra de status)
        |#   1-$tiles      um espaço de bloco de Configurações rápidas - o mesmo número que o z2-tile usa
        |#
        |# Colocar um desenho
        |#   pick <alvo>              lista e escolhe pelo número (o mais fácil)
        |#   sample <alvo> <nome>     quando você já sabe o nome
        |#   edit <alvo>              desenha, ou corrige o atual, no ${d}EDITOR
        |#   set <alvo> <arquivo>     lê de um arquivo ('-' para a stdin)
        |#   auto <1-$tiles|all>          escolhe de novo pelo que o bloco executa
        |#   clear <alvo|all>         volta ao ícone integrado
        |#
        |# Quão fina é a grade
        |#   grid                     o tamanho em que os desenhos novos são feitos
        |#   grid <$grids>          define o tamanho ($grid se você não mudar)
        |#   scale <alvo> <$grids>    passa o desenho que está em <alvo> para essa grade
        |#                            (dividindo pela metade os degraus diagonais no caminho). **Dá
        |#                            espaço para desenhar mais fino** - o ícone que sai é
        |#                            suavizado de qualquer jeito, então não precisa disto para isso.
        |#
        |# Guardar os seus desenhos na lista
        |#   save <alvo> <nome>       dá nome ao que está em <alvo> agora e o adiciona à lista
        |#   sample                   a lista de onde escolher (número / nome /
        |#                            'builtin' = incluído, 'mine' = salvo por você)
        |#   sample <nome>            mostra esse desenho
        |#   forget <nome>            tira um dos seus da lista (o que você já
        |#                            colocou num alvo fica onde está)
        |#
        |# Como desenhar
        |#   É uma grade de caracteres. '.' ' ' '0' '-' '_' deixam um ponto vazio e qualquer outro
        |#   o preenche, então use o caractere que achar mais fácil de ver. O espaço em branco em volta
        |#   do desenho é ignorado - ele é centralizado, então você não precisa preencher cada linha
        |#   certinho. A grade é $grids ('z2-icon grid').
        |#   ⚠ Uma grade maior não faz um ícone maior. Preencha a grade, senão o desenho
        |#     sai menor do que o que você substituiu.
        |#
        |# Bom saber
        |#   ⚠ Só a forma passa. O Android pinta esses ícones com uma cor única dele
        |#     (os blocos mudam de cor entre ligado e desligado), então não há cor para escolher.
        |#   ⚠ **O contorno é suavizado na saída.** Um desenho feito em $grid pontos ainda
        |#     sai suave num bloco (que é desenhado bem maior do que a barra de status), então
        |#     a grade não é algo para se preocupar no dia a dia. 48 / 64 são para quando **você**
        |#     quer desenhar mais fino. 'show' imprime o desenho como você o fez; o bloco fica mais suave.
        |#   ⚠ Um bloco ganha um desenho sozinho quando o nome o denuncia ('z2-tile set 1
        |#     remind.sh' põe um relógio ali). O que você define aqui ganha e nunca mais é tocado
        |#     — 'z2-icon auto 1' devolve esse espaço à escolha automática.
        |#   ⚠ Dois ícones não podem ser mudados: o da tela de **edição** das Configurações rápidas (de onde
        |#     você arrasta os blocos) e o ícone do launcher. O Android os fixa na instalação.
        |#
        |# ex.: z2-icon pick 1              escolhe o desenho do espaço 1
        |#      z2-icon edit 1              redesenha você mesmo
        |#      z2-icon save 1 my-face      dá nome a esse desenho e o adiciona à lista
        |#      z2-icon sample 3 my-face    põe o mesmo desenho no espaço 3
        |#      z2-icon grid 64             desenha os novos numa grade 64x64 daqui em diante
        |#      z2-icon scale 1 48          passa o espaço 1 para uma grade 48x48 para desenhar mais fino
        |#      z2-icon list -p             confere o que está onde, com os desenhos
        |#      z2-icon clear notify        volta o ícone da notificação
    """.trimMargin()

    override val iconUsage: String get() = "uso: z2-icon pick <notify|1-$tiles> | sample [nome|alvo nome] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [arquivo|-] | " +
                "save <notify|1-$tiles> <nome> | forget <nome> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "esse arquivo não existe:"

    override val iconEditUnchanged: String get() = "sem mudanças."

    override val iconPickPrompt: String get() = "número (ou nome), em branco para cancelar: "

    override val iconPickCancelled: String get() = "cancelado."

    override val iconNoEditor: String get() = "nenhum editor encontrado. Defina ${d}EDITOR, ou use: z2-icon set <alvo> <arquivo>"

    override val tileNoSuchMacro: String get() = "essa macro não está em ~/.z2term/macros/ (use um caminho completo para executá-la como comando):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [pacote] / trace dump / trace stop
        |# Rastreamento: ative a captura antes; 5 minutos, os 128 eventos mais recentes, só na memória.
        |# Tipos de campo e tamanhos em UTF-16, nunca os valores de texto. dump/stop devolvem JSON; start o reinicia.
        |# z2-noti list  … as notificações que estão na tela agora, em TSV
        |#                 (chave / pacote / nome do app / título / corpo)
        |# Só leitura. De propósito, não há como apertar nem dispensar uma notificação:
        |# isso também apertaria os botões de pagar e de enviar de outros apps.
        |# Precisa do acesso às notificações (Configurações > Permissões e notificações).
        |# Veja também: z2-when notify:otp / notify:pkg=<parte> / notify:contains=<parte>
    """.trimMargin()

    override val notiUsage: String get() = "uso: z2-noti list | trace start [pacote] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [nome]     … uma vez no próximo HH:MM (amanhã se já passou)
        |# z2-alarm daily HH:MM [nome]  … todo dia às HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [nome] … uma vez, daqui a N segundos/minutos/horas
        |# z2-alarm list                … lista o que está agendado (JSON; "exact" diz quão pontual)
        |# z2-alarm cancel <id|nome|all> … cancela
        |# Quando dispara, uma linha {"event":"alarm","name":…} é acrescentada a ~/.z2term/events.jsonl.
        |# Acorda o dispositivo mesmo no Doze. "exact":true significa que dispara no minuto certo;
        |# "exact":false significa que o Doze só oferece uma vaga a cada 9-15 min, então um celular deixado com a
        |# tela desligada pode atrasar isso. Desativar a otimização de bateria para este app é o que
        |# o vira para true (nenhuma permissão extra é pedida).
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: nenhum comando date utilizável"

    override val sessionHelp: String get() = """
        |# z2-session list                     … lista as abas (índice / id / tipo / marca / nome, TSV)
        |#   marcas: * = na tela / ! = rodando / ? = não iniciada / @ = conectada por um shell / - = outra
        |# z2-session new [nome]               … abre uma aba de terminal (devolve o índice e o id)
        |# z2-session send <aba> <texto>...    … digita o texto nessa aba (não executa)
        |# z2-session send <aba> <texto> --enter … digita e depois executa
        |# z2-session key <aba> <tecla>...    … envia **teclas** a essa aba (C-c / M-x / F5 / Up …)
        |#   modificadores: C- (Ctrl) e M- (Meta=Alt); se empilham, como em C-M-a
        |#   especiais: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Teclas com Shift como C-S-a são recusadas (um terminal não diferencia o Shift: os mesmos bytes de C-a)
        |# z2-session key <aba> --raw '\x1b[A' … qualquer outra coisa, como bytes (\xHH \e \n \r \t \0)
        |# z2-session capture [aba] [--all]    … pega a tela dessa aba (--all inclui a rolagem)
        |# z2-session attach <aba>             … fica conectado a essa aba e só digita nela
        |#   saia com Ctrl+] (a qualquer momento), ou com ~. no começo de uma linha (como no ssh)
        |#   ⚠ por SSH só o Ctrl+] funciona: o ssh na sua frente come o ~. e derruba a sessão SSH
        |#   escreva ~~ para um ~ literal no começo de uma linha, ~ e depois Ctrl+] para um Ctrl+] literal
        |#   enquanto conectada, a aba segue o tamanho da SUA janela; volta quando você sai
        |# z2-session close <aba>              … fecha essa aba (nunca a última)
        |#
        |# <aba> pode ser o índice do list, um id ou o nome de uma aba. '.' ou omitido = a aba na tela.
        |# ex.: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "uso: z2-session list | new [nome] | send <aba> <texto>... [--enter] | " +
                "key <aba> <tecla>... | key <aba> --raw <bytes> | capture [aba] [--all] | " +
                "attach <aba> | close <aba>"

    override val keyNothing: String get() = "z2-session key: nenhuma tecla indicada"

    override val keyRawEmpty: String get() = "z2-session key: --raw precisa dos bytes a enviar"

    override val keyUnknown: String get() = "z2-session key: tecla desconhecida (veja a lista em 'z2-session -h'):"

    override val keyBadEscape: String get() = "z2-session key: não foi possível ler o escape:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: o terminal não diferencia o Shift, então '$asWritten' daria " +
            "exatamente os mesmos bytes de '$equivalentTo'. Escreva '$equivalentTo' se era isso que você queria."

    override fun attachNoSuchTab(target: String): String = "essa aba não existe: '$target' (execute 'z2-session list' para ver as abas)"

    override val attachNotTerminal: String get() = "essa é uma aba de GUI, que não tem shell para conectar. Escolha uma aba de terminal."

    override val attachNotStarted: String get() = "essa aba ainda não iniciou (marcada com '?' no list). Abra-a uma vez no app e depois conecte."

    override val attachExited: String get() = "essa aba já terminou. A tela dela ainda pode ser lida com 'z2-session capture'."

    override val attachSelf: String get() = "essa é a aba em que você está digitando. Conectar uma aba a ela mesma faz a própria saída " +
            "voltar como entrada para sempre, então não tem como desfazer. Escolha outra aba."

    override val attachLoop: String get() = "essa aba já está conectada de volta a esta, então as duas se alimentariam " +
            "para sempre. Desconecte uma delas antes (Ctrl+])."

    override val serverHelp: String get() = """
        |# z2-server list                … servidores registrados (índice / id / estado / marca / nome, TSV)
        |#   marcas: * = ativado / - = desativado
        |# z2-server start <servidor>    … executa como servidor residente (mantém o dispositivo acessível)
        |# z2-server stop <servidor>     … para esse (os outros continuam rodando)
        |# z2-server status [<servidor>] … estado, pid, reinícios e último código de saída
        |#
        |# <servidor> pode ser o índice do list, um id ou o nome que você deu no app.
        |# Só os servidores registrados no app podem ser iniciados; isto nunca registra um novo.
        |#
        |# Por que isto existe: um daemon iniciado direto por uma regra roda FORA do esquema de servidor
        |# residente (sem WakeLock, sem WifiLock, sem serviço em primeiro plano), então para de responder quando a
        |# tela apaga. Iniciá-lo por aqui o coloca dentro desse esquema.
        |# ex.: z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "uso: z2-server list | start <servidor> | stop <servidor> | status [<servidor>]"

    override val serverNotFound: String get() = "z2-server: esse servidor não existe (veja 'z2-server list'):"

    override val serverAmbiguous: String get() = "z2-server: o nome corresponde a mais de um servidor; use o id de 'z2-server list':"

    override val serverNone: String get() = "z2-server: ainda não há servidores registrados (adicione um no app: 📜 -> Servidores)."

    override val serverLowPowerWarn: String get() = "z2-server: atenção - o modo de baixo consumo está ativado, então nenhum WakeLock/WifiLock é segurado. " +
            "O servidor pode parar de responder com a tela desligada " +
            "(Configurações -> Automação -> Proteção dos processos em segundo plano)."

    override val whenHelp: String get() = """
        |# z2-when <gatilho> run <cmd...>        … registra uma regra
        |#   gatilhos: charge:start | charge:stop  (precisa da detecção ativada)
        |#            battery:below=N | battery:above=N  (precisa da detecção ativada)
        |#            battery:temp>N | battery:temp<N  … a temperatura da bateria (°C) cruzou N (precisa da detecção ativada)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min hora dia-do-mês mês dia-da-semana'  (dia da semana 0-7 / 0,7=domingo. Use aspas: tem espaços)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<nome>  (precisa da detecção ativada)
        |#            net:online | net:offline  … uma conexão utilizável apareceu / sumiu
        |#            net:wifi | net:mobile | net:ethernet  … a conexão em uso passou a ser essa
        |#                                        (precisa da detecção ativada; conta os dados móveis, não só o Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<parte> | share:ext=<ext>
        |#                                        … algo foi compartilhado com o z2term a partir de outro app
        |#            boot                        … o dispositivo terminou de ligar (funciona com a detecção desativada)
        |#            sms:any | sms:from=<parte> | sms:contains=<parte> | sms:otp  (precisa de RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (detecção ativada)
        |#            file:new=<pasta>[,ext=<ext>]  … um arquivo novo chegou a essa pasta (precisa da detecção ativada)
        |#            file:changed=<pasta>[,ext=<ext>] | file:deleted=<pasta>[,ext=<ext>]
        |#                                        … um arquivo dali foi regravado / sumiu (precisa da detecção ativada)
        |#            bt:connect | bt:disconnect | bt:connect=<nome> | bt:disconnect=<nome>
        |#                                        … um dispositivo Bluetooth conectou / desconectou (nome: parte dele, ou o endereço)
        |#                                        (precisa da detecção ativada e, no Android 12+, da permissão Dispositivos por perto)
        |#            app:open=<app> | app:close=<app>  … esse app veio para a frente / você saiu dele (pacote ou nome do app, parte dele)
        |#                                        (precisa do z2term ativado na Acessibilidade; funciona com a detecção desativada)
        |#            steps:reach=N               … os passos de hoje chegaram a N (detecção ativada + permissão Atividade física)
        |#            notify:any | notify:otp | notify:pkg=<parte> | notify:title=<parte> | notify:contains=<parte>
        |#                                        … chegou uma notificação (precisa do acesso às notificações)
        |#            event:<nome> | event:<prefixo>* | event:*  … qualquer evento do dispositivo, pelo nome
        |#              (z2-when events os lista; os mesmos nomes do events.jsonl)
        |# Nome (opcional, logo depois do gatilho — antes do run):
        |#   name=<texto>            … para que serve esta regra, mostrado na aba Automação
        |#                             (use aspas se tiver espaços). Vazio = mostra o gatilho.
        |#                             Só exibição: não muda nada de quando a regra roda.
        |# Filtros (qualquer gatilho, logo depois dele — antes do run):
        |#   if=<cond>[,<cond>...]   … só quando o dispositivo está nesse estado (E; ! nega)
        |#                             as chaves são as que o z2-state imprime: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             ex.: if=wifi,!screen / if=ssid=Casa / if=level<30
        |#   if_any=<cond>[,<cond>...] … roda quando **qualquer uma** vale (a vírgula significa OU)
        |#                             junto com if=: "todas do if E qualquer uma do if_any"
        |#   else=<cmd>              … roda isto **no lugar**, quando o if / if_any não valeu
        |#                             ⚠ só o if / if_any chegam aqui. Uma execução pulada por between /
        |#                             days / cooldown não roda nada — uma regra desligada
        |#                             à noite tem de ficar quieta à noite, else incluído.
        |#   cooldown=30m            … não roda de novo dentro desse tempo (10s / 30m / 2h)
        |#   between=22:00-07:00     … só dentro dessa janela (passa da meia-noite)
        |#   days=mon-fri            … só nesses dias (nomes ou números cron 0-7, 0/7 = domingo)
        |#   answer='<prompt>=><resposta>' … quando o comando pergunta isto no terminal, digita a resposta
        |#                             e Enter (pode repetir; ex.: answer='[y/N]=>y'). Compara com o fim
        |#                             da última linha da saída.
        |#   secret='<prompt>'       … o mesmo para uma resposta oculta (uma senha). O conteúdo é digitado
        |#                             no editor de regras do app e guardado criptografado dentro do app.
        |# As execuções puladas também são registradas — z2-when fired mostra skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, então uma regra que nunca roda pode ser explicada. O botão ▶ do app
        |# ignora todos os filtros (ele existe para testar a regra).
        |# z2-when events                        … lista os nomes que dá para usar com event:
        |# z2-when pause / resume                … para / retoma as execuções automáticas (as regras ficam)
        |# z2-when fired [n]                     … disparos recentes (hora / id / gatilho / run|paused)
        |# z2-when list                          … regras registradas (id / on|off / gatilho / -> / cmd, TSV;
        |#                                         o nome e os filtros, se houver, entre [] no fim)
        |# z2-when remove <id|all>  (rm funciona) … exclui
        |# z2-when on <id> / off <id>            … ativa / desativa
        |# z2-when log <id>                      … o registro de execuções dessa regra (tail)
        |# Ao disparar, o comando roda na distribuição escolhida com Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) no ambiente.
        |# Para net: você recebe Z2_WHEN_NET (a conexão agora) e Z2_WHEN_NET_PREV (a de antes).
        |# Para share: você recebe Z2_WHEN_SHARE (o texto, ou os caminhos dos arquivos recebidos)
        |# e Z2_WHEN_SHARE_KIND (text|file). O compartilhamento continua indo para a linha de entrada como antes.
        |# Para file: você recebe Z2_WHEN_FILE (caminho completo) e Z2_WHEN_DIR.
        |# Para notify: você recebe Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (e Z2_WHEN_OTP para notify:otp). notify:category= compara exatamente: call, missed_call, msg, ...
        |# Para event: você também recebe Z2_WHEN_EVENT (o nome do evento); alarm / notify_action acrescentam
        |# Z2_WHEN_EVENT_NAME (o identificador com que você o armou) e Z2_WHEN_ACTION (o botão apertado).
        |# Para battery:temp você recebe Z2_WHEN_TEMP (°C). Para bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# Para app: Z2_WHEN_APP_PKG (o app na frente agora) / Z2_WHEN_APP_PREV (o de antes).
        |# Para steps: Z2_WHEN_STEPS (os passos de hoje). Para file: também Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated dão Z2_WHEN_PKG, timezone_changed dá Z2_WHEN_TZ,
        |# usb_attached / usb_detached dão Z2_WHEN_USB (nome) / Z2_WHEN_USB_ID (fabricante:produto).
        |# A mesma regra não dispara duas vezes em 10 segundos (eventos como screen_on vêm com frequência).
        |# ex.: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "campainha: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Relatório da manhã' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "Execuções automáticas pausadas (z2-when resume para voltar)"

    override val whenResumed: String get() = "Execuções automáticas retomadas"

    override val whenNoFires: String get() = "(nada disparou ainda)"

    override val whenPausedNote: String get() = "# pausado (z2-when resume para voltar)"

    override val whenNoLog: String get() = "(ainda sem registro)"

    override val whenWriteFailed: String get() = "z2-when: não foi possível gravar a regra"

    override val whenUnknownIfKey: String get() = "z2-when: chave if= desconhecida (o z2-state lista o que dá para usar):"

    override val whenUnknownTrigger: String get() = "z2-when: gatilho desconhecido (z2-when sem argumentos os lista):"

    override val whenBadTriggerSpec: String get() = "z2-when: esse gatilho não aceita este argumento:"

    override val whenPausedWarn: String get() = "atenção: as execuções automáticas estão pausadas (z2-when resume para voltar)"

    override val whenRunJoined: String get() = "atenção: as quebras de linha do comando viraram espaços (uma regra lê só uma linha)"

    override val whenEventList: String get() = """
        |screen_on              a tela ligou                          [detecção ON]
        |screen_off             a tela desligou                       [detecção ON]
        |unlocked               o dispositivo foi desbloqueado        [detecção ON]
        |power_connected        o carregamento começou                [detecção ON]
        |power_disconnected     o carregamento parou                  [detecção ON]
        |battery_low            a bateria ficou fraca                 [detecção ON]
        |battery_okay           a bateria se recuperou                [detecção ON]
        |battery_level          o nível cruzou uma marca de 10%       [detecção ON]
        |wifi_connected         entrou no Wi-Fi                       [detecção ON]
        |wifi_disconnected      saiu do Wi-Fi                         [detecção ON]
        |net_online             apareceu uma conexão que funciona     [detecção ON]
        |net_offline            nenhuma conexão funciona              [detecção ON]
        |net_wifi               a conexão em uso é Wi-Fi              [detecção ON]
        |net_mobile             a conexão em uso é móvel              [detecção ON]
        |headset_plugged        fone com fio conectado                [detecção ON]
        |headset_unplugged      fone com fio desconectado             [detecção ON]
        |bt_audio_connected     áudio Bluetooth conectado             [detecção ON]
        |bt_audio_disconnected  áudio Bluetooth sumiu                 [detecção ON]
        |airplane_on            modo avião ativado                    [detecção ON]
        |airplane_off           modo avião desativado                 [detecção ON]
        |ringer_normal          campainha no normal                   [detecção ON]
        |ringer_vibrate         campainha no vibrar                   [detecção ON]
        |ringer_silent          campainha no silencioso               [detecção ON]
        |bt_connected           um dispositivo Bluetooth conectou     [detecção ON]
        |bt_disconnected        um dispositivo Bluetooth desconectou  [detecção ON]
        |powersave_on           economia de bateria ativada           [detecção ON]
        |powersave_off          economia de bateria desativada        [detecção ON]
        |dnd_on                 Não perturbe ativado                  [detecção ON]
        |dnd_off                Não perturbe desativado               [detecção ON]
        |app_installed          um app foi instalado                  [detecção ON]
        |app_removed            um app foi desinstalado               [detecção ON]
        |app_updated            um app foi atualizado                 [detecção ON]
        |timezone_changed       o fuso horário mudou                  [detecção ON]
        |usb_attached           um dispositivo USB foi conectado      [detecção ON]
        |usb_detached           um dispositivo USB foi desconectado   [detecção ON]
        |boot                   o dispositivo ligou                   [sempre]
        |alarm                  um z2-alarm disparou                  [sempre]
        |notify_action          um botão de notificação               [sempre]
        |unlock_failed          falha ao desbloquear                  [sempre, precisa de preparação]
        |unlock_succeeded       desbloqueado depois de uma falha      [sempre, precisa de preparação]
        |voice                  um comando de voz foi ouvido          [sempre, pelo bloco ou pelo assistente]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # Nomes que dá para pôr em event:<nome>. Na mesma ordem em que aparecem no events.jsonl.
        |    # O grupo de cima precisa da detecção ativada (Configurações > Permissões e notificações),
        |    # o grupo de baixo é armado por você, então funciona com a detecção desativada.
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … procura, baixa e entrega a nova versão ao instalador
        |# z2-update --check             … só diz se há uma versão mais nova
        |# z2-update --keep              … deixa o .apk baixado (por padrão ele é excluído)
        |# z2-update --dir <pasta>       … baixa nessa pasta em vez da pasta do próprio app
        |#
        |# De onde vem
        |#   A página GitHub Releases do z2term, o mesmo arquivo que você baixaria à mão
        |#   (z2term-<versão>.apk, assinado com a chave das versões). Nada mais é contatado, e
        |#   nada é contatado até você executar isto.
        |#
        |# O que pode e o que não pode fazer
        |#   ⚠ **Não consegue instalar em silêncio.** O Android sempre mostra a própria tela "instalar?" para
        |#     um app que substitui a si mesmo, então o último toque é sempre seu. Este comando faz todos
        |#     os passos até essa tela.
        |#   ⚠ Na primeira vez, permita **"Instalar apps desconhecidos"** para o z2term (ele avisa, e
        |#     as ⚙Configurações têm o mesmo botão). Sem isso a tela de instalação nunca aparece.
        |#   ⚠ Instalado pelo F-Droid ou por uma loja? Então isto recusa e diz para atualizar por lá —
        |#     a versão que você tem é deles para substituir, não nossa.
        |#
        |# O arquivo baixado
        |#   Por padrão ele fica na pasta do próprio app e é excluído quando a instalação
        |#   termina (e no próximo início, caso o app tenha sido encerrado no meio da instalação — o que é
        |#   normal ao substituir a si mesmo). --keep ou --dir mudam isso; as ⚙Configurações têm as mesmas
        |#   duas opções para o botão de lá.
        |#
        |# ex.: z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # olha toda noite (continua perguntando a você)
    """.trimMargin()

    override val updateUsage: String get() = "uso: z2-update [--check] [--keep] [--dir <pasta>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current é a versão mais recente."

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current -> $latest ($size)"

    override val updateChecking: String get() = "z2-update: procurando uma versão mais nova ..."

    override val updateHandedToInstaller: String get() = "z2-update: a tela de instalação está aberta no dispositivo — aprove para terminar."

    override fun updateNoApk(url: String): String = "z2-update: essa versão não tem um .apk anexado. Pegue-o na página da versão: $url"

    override val updateNeedPermission: String get() = "z2-update: permita antes \"Instalar apps desconhecidos\" para o z2term " +
            "(Configurações > Apps > Acesso especial de apps > Instalar apps desconhecidos, ou o botão nas Configurações do z2term)."

    override val updateManagedByStore: String get() = "z2-update: esta versão foi instalada por uma loja (F-Droid / Play). Atualize por lá."

    override fun updateFailed(reason: String): String = "z2-update: falhou: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# Comando de voz — tabela de palavras (português do Brasil). O voice.sh a lê. Edite para adicionar ou mudar o que você pode dizer.
#
# Como escrever:
#   chave = um jeito|outro jeito|...  separe com "|"; conferido de cima para baixo
#   ponha um * para pegar as palavras ali (abrir * -> "abrir mapas" dá "mapas")
#   *X* pega as palavras dos dois lados de X
#   chave =                           nada à direita desativa aquela
#   A pontuação é ignorada, assim como maiúsculas/minúsculas e espaços a mais
#   Uma chave que falte aqui usa o valor incluído (apague uma linha para voltar ao original)
#   As linhas que começam com say_ / msg_ / fmt_ / title_ são o que é falado ou mostrado; {…} recebe um valor
#
# Teste uma sem falar: sh ~/.z2term/macros/voice.sh try próxima música
# "as suas próprias palavras = um comando" vão em ~/.z2term/voice/commands.txt (conferidas antes desta tabela).

# --- Palavras a escutar ---
ai = pergunte para a ia *|pergunte à ia *|ei ia *|ia *|pergunta *
media_next = próxima música|próxima faixa|próxima|pular|pular esta música|pular música|passar a música
media_previous = música anterior|faixa anterior|anterior|voltar a música|volta a música
media_pause = pausar|pausa|parar|parar a música|pausar a música|para a música|pausa a música
media_play = tocar|continuar|retomar|tocar música|continuar a música|retomar a música|toca a música
volume_up = aumentar o volume|aumenta o volume|aumentar volume|volume mais alto|mais alto|sobe o volume|subir o volume
volume_down = diminuir o volume|diminui o volume|diminuir volume|volume mais baixo|mais baixo|abaixa o volume|abaixar o volume
volume_mute = silenciar|silencia|tirar o som|mudo|modo silencioso
volume_unmute = ativar o som|tirar do mudo|liga o som|ligar o som
time = que horas são|que hora é|horas|a hora|hora atual|me diz as horas
date = que dia é hoje|qual é a data|qual a data de hoje|data de hoje|data|que dia é
battery = bateria|nível da bateria|quanto de bateria|quanta bateria tem|quanta bateria resta|como está a bateria
steps = passos|quantos passos|contagem de passos|meus passos|quantos passos hoje|passos de hoje|quantos passos eu dei
torch_on = ligar a lanterna|liga a lanterna|lanterna ligada|acender a luz|acende a luz|lanterna
torch_off = desligar a lanterna|desliga a lanterna|apagar a lanterna|apaga a lanterna|apagar a luz|apaga a luz
screenshot = captura de tela|tirar uma captura de tela|tira um print|print da tela|printar a tela
home = início|tela inicial|ir para o início|volta para o início
news = notícias|ler as notícias|quais são as notícias|notícias de hoje|manchetes|rss
weather = tempo|previsão do tempo|como está o tempo|como está o clima|clima|tempo hoje|previsão de hoje
weather_tomorrow = tempo amanhã|previsão de amanhã|como vai estar o tempo amanhã|clima amanhã|previsão do tempo para amanhã
memo_read = ler minhas notas|ler as notas|minhas notas|mostrar minhas notas|lê as notas
memo_add = anote que *|anota que *|anote *|anota *|tome nota de *|faça uma nota *|nota *|escreva *
remind_list = lembretes|meus lembretes|listar lembretes|mostrar lembretes|quais são meus lembretes|minha agenda
remind = me lembre de *|me lembra de *|me lembre que *|me lembra que *|lembre-me de *|criar um lembrete para *|lembrete *
wake = me acorde às *|me acorda às *|me acorde *|me acorda *|despertador para as *|alarme para as *|alarme às *|alarme para *
help = o que posso dizer|o que eu posso dizer|o que você faz|ajuda|comandos
timer = timer de *|temporizador de *|cronômetro de *|um timer de *|um temporizador de *|crie um timer de *
navi = navegar até *|navegar para *|como chego a *|como chegar a *|me leve até *|leve-me até *|quero ir para *|rota para *
navi_ask = navegar|navegação|rota|como chegar|iniciar a navegação
place_phone = qual é o telefone de *|qual o telefone de *|telefone de *|número de telefone de *|o número de *
call = ligar para *|liga para *|ligue para *|chamar *|telefonar para *
play = tocar *|toca *|toque *|coloca *|coloque *
open = abrir *|abre *|abra *|iniciar *|inicia *
# Abrir por tipo (o app padrão do celular). Ex.: abrir o navegador
open_browser = navegador|o navegador|internet|navegador da web
open_maps = mapas|mapa|o mapa|os mapas
open_mail = e-mail|email|o e-mail|correio
open_camera = câmera|a câmera|camera
open_settings = configurações|as configurações|ajustes
open_calendar = agenda|a agenda|calendário|o calendário
open_music = música|a música|músicas
open_gallery = fotos|galeria|a galeria|as fotos
open_contacts = contatos|os contatos
open_dialer = telefone|o telefone|discador
open_messages = mensagens|as mensagens|sms
open_calculator = calculadora|a calculadora
# Apelidos para apps. Quando dizer o nome não o abre, escreva: apelido:nome do app|apelido:nome do app
app_names =

# Volume com número ("diminuir o volume 3", "volume 10", "volume 50 por cento", "volume no máximo") — vale quando estiver contido
volume_word = volume
volume_up_word = aumentar|aumenta|subir|sobe|mais alto|mais
volume_down_word = diminuir|diminui|abaixar|abaixa|mais baixo|menos
volume_max_word = máximo|no máximo|tudo
volume_min_word = mínimo|zero
volume_percent_word = %|por cento|porcento
volume_mute_word = mudo|desligar|silenciar
volume_unmute_word = tirar do mudo|ativar

# Tempos e horários do dia (timer, lembrete, alarme)
unit_hour = horas|hora
unit_minute = minutos|minuto|min
unit_second = segundos|segundo|seg
unit_day = dias|dia
when_before = daqui a|em|dentro de
when_after = a partir de agora|depois
clock_at = às|à|por volta das|lá pelas
clock_hour = em ponto
clock_minute =
clock_half = e meia
clock_am = da manhã|de manhã|da madrugada
clock_pm = da tarde|à tarde|de tarde|da noite|à noite|de noite|hoje à noite
day_today = hoje
day_tomorrow = amanhã
day_after_tomorrow = depois de amanhã
day_daily = todo dia|todos os dias|diariamente
day_weekdays = nos dias úteis|dias úteis|de segunda a sexta
day_every = toda|todo|todas as|todos os|cada
wd_mon = segunda-feira|segunda feira|segunda
wd_tue = terça-feira|terça feira|terça
wd_wed = quarta-feira|quarta feira|quarta
wd_thu = quinta-feira|quinta feira|quinta
wd_fri = sexta-feira|sexta feira|sexta
wd_sat = sábado
wd_sun = domingo
# Palavrinhas que sobram entre o texto e as partes retiradas
remind_filler = de|que|para|pra|às|as|à|a|o|em|no|na|me|sobre

# --- O que é falado ou mostrado ---
fmt_spaces = yes
fmt_sentence_end = .
title_app = Comando de voz
title_navi = Navegação
title_ai = IA
title_timer = Timer
title_news = Notícias
title_memo = Notas
title_remind = Lembretes
title_call = Ligação
title_help = O que você pode dizer
title_rule_voice = Comando de voz
title_rule_voice_locked = Comando de voz (bloqueado)
title_rule_timer = Timer por voz
title_rule_steps = Contar passos (para o comando de voz)
fmt_clock = {h}:{mm}
fmt_clock_oclock = {h} horas
fmt_weekdays = segunda-feira|terça-feira|quarta-feira|quinta-feira|sexta-feira|sábado|domingo
fmt_months = janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro
say_time = São {clock}
say_date = Hoje é {weekday}, {day} de {month_name}
say_battery = A bateria está em {n} por cento. {charging}
say_charging = Está carregando.
say_steps = Você andou {n} passos hoje
say_steps_none = Ainda não há passos registrados. É preciso a permissão de Atividade física e uma regra de passos
say_news = Aqui estão as últimas manchetes. {titles}
say_news_none = Não há notícias novas
fmt_weather_lang = pt
say_weather = Agora está {desc}, {temp} graus
say_weather_tomorrow = Amanhã: {desc}, máxima de {hi} e mínima de {lo} graus, {rain} por cento de chance de chuva
say_weather_fail = Não consegui pegar a previsão do tempo
say_memo_saved = Anotado
say_memo_recent = As suas últimas notas. {memos}
say_memo_none = Não há notas
say_remind_ok = Lembrete criado. {res}
say_remind_fail = Não consegui criar. {res}
say_remind_unclear = Não entendi quando nem do que lembrar você. Tente: me lembre de tirar o lixo amanhã às 7 da manhã
say_remind_missing = A macro de lembretes não está instalada. Execute z2-macro install remind num terminal
fmt_wake_label = acordar
say_wake = Hora de acordar
say_wake_set = Vou acordar você às {clock}
say_wake_unclear = Não entendi a hora de acordar você
fmt_unit_hour = horas
fmt_unit_minute = minutos
fmt_unit_second = segundos
fmt_timer_label = {n} {unit}
say_timer_set = Timer de {label} iniciado
say_timer_done = Acabaram os {label}
say_timer_unclear = Não entendi quanto tempo o timer deve durar
say_call_missing = Não encontrei um número para {who}
msg_call_missing = "{who}" não está em {file}. Escreva um "nome número" por linha.
say_open_missing = Não encontrei um app chamado {name}
say_help = O que você pode dizer está numa notificação
say_locked = Desbloqueie o celular, por favor
msg_help = que horas são, data, bateria, passos / diminuir o volume 3, volume 50 por cento / próxima música, pausar / timer de 3 minutos, me acorde às 7 / me lembre de tirar o lixo amanhã às 7 da manhã, lembretes / notícias, tempo, tempo amanhã / anote comprar leite, ler minhas notas / ligar a lanterna, captura de tela / abrir … / ligar para … / navegar até …, telefone de … / IA … (ative com voice.sh ai) / mudar as palavras: voice.sh words
msg_unknown = Não entendi: {text}
msg_locked = "{text}" continua quando você desbloquear o celular
msg_navi_where = Diga aonde você quer ir
msg_navi_hint = Para onde (o nome de um lugar)
msg_ai_off = A IA está desativada. Aperte Copiar e cole num terminal para usar {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = A IA está desativada. Instale e entre num destes: codex / claude / agy, e depois escolha-o num terminal, ex.: sh ~/.z2term/macros/voice.sh ai codex
msg_ai_missing = {ai} não foi encontrado. Instale e entre, ou escolha outro com sh ~/.z2term/macros/voice.sh ai
msg_ai_asking = Perguntando à IA ({ai})…
msg_ai_noanswer = Nenhuma resposta voltou (registro: {log})
msg_ai_usage = ai: indique um de off | codex | claude | agy
msg_ai_how = A IA está desativada. Para usar: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy; ela só é chamada quando você começa com a palavra de sinal, "IA" ou "pergunta"). Modelo: sh ~/.z2term/macros/voice.sh model <nome> (vazio = o padrão daquela IA)
fmt_ai_prompt = (A resposta vai ser lida em voz alta. Sem usar comandos nem arquivos, responda em português do Brasil em 2 ou 3 frases, sem símbolos nem listas.)
msg_setup_done = Regras do comando de voz registradas (as que já existiam não foram adicionadas de novo):
msg_setup_steps = A regra Contar passos (steps:reach=1000000) só mantém o contador de passos funcionando para você perguntar os passos de hoje. Essa meta nunca é atingida, então ela não executa nada.
msg_words_file = Tabela de palavras: {file}  (edite para adicionar jeitos de dizer as coisas; sh ~/.z2term/macros/voice.sh words a abre)"""
