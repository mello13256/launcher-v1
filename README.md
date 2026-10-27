# Hexa — launcher Android de colmeias hexagonais

Launcher pessoal para Android construído em torno de unidades hexagonais. Cada colmeia abre uma
aplicação com um toque. Combinações de colmeias, a pesquisa instantânea e um widget de música
expansível dão acesso direto a qualquer aplicação instalada.

Kotlin · Jetpack Compose · APIs nativas (`LauncherApps`, `MediaSessionManager`/`MediaController`,
`RoleManager`). Não precisa de root, contas, servidores nem Internet.

## Estrutura

```
core/   Kotlin puro (sem Android), com todos os testes unitários
  model/     configuração serializável (colmeias, colmeia central, combinações, definições)
  layout/    disposições hexagonais, ajuste ao ecrã, hit-testing, reorganização
  combo/     motor de combinações (toques com intervalo, ordem, deslizar), validação
  search/    índice de pesquisa em memória, ranking por uso, pesquisa de combinações
  persist/   escrita atómica e debounced da configuração
  music/     modelo do widget de música
  metrics/   medições de latência (ring buffers)
  home/      estado das camadas do ecrã inicial (pesquisa / música / edição)
app/    aplicação Android
  apps/      índice de apps via LauncherApps (todos os perfis), cache de ícones, atalhos de apps
  launch/    execução de ações (startMainActivity, startShortcut, URIs) com medição
  media/     MusicController (MediaSession) + NotificationListenerService vazio
  images/    imagens personalizadas copiadas/reduzidas para armazenamento privado
  ui/home    ecrã inicial: campo de colmeias, pesquisa, colmeia de música, modo de edição
  ui/edit    editor de colmeia, seletor de ações, editor de combinações, cores
  ui/settings definições avançadas, medições, cópia de segurança
```

## Funcionalidades

- **15 colmeias + colmeia central** (ligeiramente maior, sempre ao centro). Cada colmeia tem a sua
  ação de toque e de toque longo (app, atalho de app, endereço/URI, combinação, ações do launcher),
  cor, opacidade, estilo (ícone, imagem, imagem + ícone, só cor), imagem com recorte
  (preencher/ajustar/esticar, zoom, ponto de foco), tamanho, tamanho do ícone, contorno e
  nome opcional.
- **Disposições** Favo (4 colunas), Torre (3 colunas) e Anel. Têm mais posições do que colmeias,
  por isso o utilizador escolhe onde ficam os espaços livres. A composição ajusta-se ao ecrã e às
  áreas seguras; em paisagem a grelha é transposta (hexágonos flat-top).
- **Modo de edição** (toque longo no fundo, ou numa colmeia): arrastar e largar para mover ou
  trocar colmeias. Apps, cores, imagens e combinações acompanham a colmeia. Pode ser bloqueado.
- **Combinações**: duas a seis colmeias, com ordem relevante ou não, repetições permitidas (toque
  duplo). Introduzem-se por toques dentro do intervalo configurável (100–1000 ms, com controlo
  deslizante e valor em ms) ou **a deslizar o dedo pelas colmeias**, sem qualquer espera.
  Combinações ambíguas (os mesmos toques a corresponder a duas) são rejeitadas. Prefixos de
  combinações mais longas são avisados.
- **Pesquisa universal**: índice em memória, normalização de acentos e maiúsculas, prefixos,
  palavras, iniciais ("ytm" → YouTube Music), subsequência, ranking por uso, resultados
  incrementais, Enter abre o primeiro resultado, combinações relevantes por baixo dos resultados.
- **Widget de música**: pequena colmeia num canto que se transforma (hexágono → retângulo) no
  widget com capa, faixa, artista, estado, progresso (com seek quando suportado) e controlos.
  Sem acesso a notificações, os controlos funcionam por media keys.
- **Persistência**: um único JSON, escrito de forma atómica e com debounce, nunca na thread
  principal, e apenas quando há alterações. Ficheiro corrompido fica em quarentena. Exportar e
  importar disponíveis nas definições.
- **Apps desinstaladas**: a colmeia mantém o aspeto, mostra um aviso e, ao tocar, abre o editor
  para escolher outra app. Se a app voltar a ser instalada, a colmeia volta a funcionar.

## Como funciona a temporização (decisão de arquitetura)

Uma colmeia que **não pode iniciar** nenhuma combinação abre a sua app **imediatamente** ao
levantar o dedo (ou ao pressionar, com "Disparo imediato" ativo). Só as colmeias que podem
começar uma combinação esperam o intervalo configurado. Uma combinação que não pode crescer
dispara no último toque, sem esperar. A janela é medida a partir do tempo do evento de toque.
A espera é feita com uma coroutine, por isso nunca bloqueia a interface. Quando uma combinação é
reconhecida, a abertura pendente da colmeia individual é cancelada, e um toque que não pode
continuar a sequência invalida-a, sem abrir nada. Quem não quiser esperas nenhumas escolhe
"Só deslizar".

## Desempenho e medições

- Todas as colmeias são desenhadas num único `Canvas`, com paths pré-calculados. Toques, arrastos
  e realces apenas invalidam o desenho, sem recomposição.
- Os gestos são resolvidos por hit-testing hexagonal. Abrir uma app é uma chamada síncrona a
  `LauncherApps.startMainActivity` a partir do handler de toque, sem saltos de thread.
- A pesquisa corre em memória, de forma síncrona e incremental (5000 apps ≈ 2,6 ms por consulta a
  frio, medido no teste JVM `staysFastWithManyApps`, nesta máquina de desenvolvimento).
- Disco, ícones e imagens são tratados em `Dispatchers.IO`. A cache LRU de ícones é limitada em
  bytes e a de imagens é reduzida ao importar.
- Os listeners de música só existem entre `onStart` e `onStop`. O launcher não tem nenhum serviço
  próprio a correr.
- Perfil base (`baseline-prof.txt`) e ProfileInstaller.

O launcher mede (em *Definições → Desempenho*, com média/p50/p95/máximo): toque → reconhecimento,
processamento do reconhecimento, reconhecimento de combinações, pedido de abertura → comando
enviado ao Android, toque → comando enviado, processamento da pesquisa, abrir pesquisa →
selecionar, última tecla → selecionar, carregamento do índice e da configuração, e memória (PSS e
heap). O tempo de arranque da app aberta depende do Android e da própria app; o launcher mede até
ao envio do pedido ao sistema.

## Compilar e testar

Requisitos: JDK 17+, Android SDK (plataforma 35) e acesso ao Google Maven.

```bash
./gradlew :core:test                 # testes do núcleo (JVM puro)
./gradlew :app:testDebugUnitTest     # testes Robolectric (manifesto HOME, widget de música)
./gradlew :app:assembleRelease       # APK otimizado (assinado com a chave de debug local)
./gradlew :app:assembleDebug         # APK de debug (id com.hexa.launcher.debug)
```

Só o núcleo, sem Android SDK: `./gradlew -Phexa.coreOnly=true :core:test`.

O workflow `.github/workflows/android.yml` corre os testes e gera os APKs em cada push. Os APKs
ficam disponíveis como artefacto `hexa-launcher-apks`.

## Instalar no telemóvel

1. Instalar o APK (`adb install -r app-release.apk`, ou abrir o ficheiro no telemóvel e permitir a
   instalação de origem desconhecida). Para medir desempenho usar o **release**: os builds de
   debug do Compose são bastante mais lentos.
2. Carregar em Início e escolher **Hexa**, ou em *Definições do Hexa → Definir como ecrã inicial*
   (diálogo de função "Página inicial" no Android 10+).
3. No primeiro arranque, as colmeias são preenchidas com as apps predefinidas (câmara ao centro,
   telefone, mensagens, navegador...). Toque longo no fundo → modo de edição.
4. Para ver a faixa no widget de música: *Definições → Widget de música → Permitir acesso*
   (acesso a notificações; o Hexa não lê notificações, usa-o apenas para a sessão de música).

Os APKs de release do CI são assinados com uma chave de debug gerada em cada execução. Para
atualizar entre builds diferentes do CI pode ser preciso desinstalar primeiro.

## Limitações conhecidas

- Atalhos de aplicações (ex.: "Nova mensagem") só podem ser listados e abertos quando o Hexa é o
  launcher predefinido. É uma regra do Android (`hasShortcutHostPermission`).
- A informação da faixa exige acesso a notificações (regra de `getActiveSessions`). Sem esse
  acesso, só os botões funcionam.
- Os widgets de outras aplicações (AppWidgetHost), as pastas e o arrastar de atalhos pedidos por
  apps (`ACTION_CONFIRM_PIN_SHORTCUT`) não estão implementados.
- A cópia de segurança JSON não inclui as imagens personalizadas.
