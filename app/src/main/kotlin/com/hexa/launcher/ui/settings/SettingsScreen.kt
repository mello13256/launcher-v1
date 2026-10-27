package com.hexa.launcher.ui.settings

import android.os.Debug
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hexa.core.metrics.Metric
import com.hexa.core.model.BackgroundMode
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.ComboInput
import com.hexa.core.model.ComboSettings
import com.hexa.core.model.Corner
import com.hexa.core.model.ImageFit
import com.hexa.core.model.LaunchAnimation
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.core.model.LayoutPreset
import com.hexa.core.model.MusicTapAction
import com.hexa.core.model.SearchPosition
import com.hexa.core.persist.ConfigCodec
import com.hexa.launcher.BuildConfig
import com.hexa.launcher.FirstRun
import com.hexa.launcher.ui.common.ChoiceChips
import com.hexa.launcher.ui.common.EditorModel
import com.hexa.launcher.ui.common.Hint
import com.hexa.launcher.ui.common.LabeledSlider
import com.hexa.launcher.ui.common.SectionTitle
import com.hexa.launcher.ui.common.SwitchRow
import com.hexa.launcher.ui.common.ValueRow
import com.hexa.launcher.ui.edit.ActionPickerDialog
import com.hexa.launcher.ui.edit.ColorPicker
import com.hexa.launcher.ui.edit.ComboEditorDialog
import com.hexa.launcher.ui.edit.ComboListDialog
import com.hexa.launcher.ui.theme.HexaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(editor: EditorModel, activity: SettingsActivity, onClose: () -> Unit) {
    val config by editor.config.collectAsState()
    val update = editor::update

    Column(
        Modifier
            .fillMaxSize()
            .background(HexaColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Definições do Hexa", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Fechar") }
        }
        DefaultHomeSection(activity)
        CombosSection(editor, config, update)
        LayoutSection(editor, config, update)
        CenterSection(editor, config)
        BackgroundSection(editor, config, update)
        SearchSection(config, update)
        MusicSection(editor, config, update)
        AnimationSection(config, update)
        InteractionSection(config, update)
        PerformanceSection(editor)
        BackupSection(editor, activity)
        Spacer(Modifier.height(12.dp))
        Hint("Hexa ${BuildConfig.VERSION_NAME} · tudo guardado localmente, sem contas nem Internet.")
    }
}

@Composable
private fun DefaultHomeSection(activity: SettingsActivity) {
    var isDefault by remember { mutableStateOf(activity.isDefaultHome()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = activity.isDefaultHome() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = activity.isDefaultHome()
    }
    SectionTitle("Launcher predefinido")
    if (isDefault) {
        Hint("O Hexa é o ecrã inicial deste dispositivo.")
    } else {
        Hint("Define o Hexa como aplicação inicial para o botão Início abrir as colmeias (e para usar atalhos de aplicações).")
        Button(onClick = { request.launch(activity.defaultHomeIntent()) }) { Text("Definir como ecrã inicial") }
    }
}

@Composable
private fun CombosSection(editor: EditorModel, config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    var showList by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ComboConfig?>(null) }
    val cs = config.comboSettings
    SectionTitle("Combinações de colmeias")
    LabeledSlider(
        label = "Intervalo máximo entre toques",
        value = cs.windowMs.toFloat(),
        range = ComboSettings.MIN_WINDOW_MS.toFloat()..ComboSettings.MAX_WINDOW_MS.toFloat(),
        valueText = "${cs.windowMs} ms",
        steps = (ComboSettings.MAX_WINDOW_MS - ComboSettings.MIN_WINDOW_MS) / 10 - 1,
        onChange = { v -> update { it.copy(comboSettings = it.comboSettings.copy(windowMs = (v / 10f).toInt() * 10)) } },
    )
    ChoiceChips(
        listOf(
            ComboSettings.FAST_WINDOW_MS to "Rápido",
            ComboSettings.BALANCED_WINDOW_MS to "Equilibrado",
            ComboSettings.COMFORT_WINDOW_MS to "Confortável",
        ),
        cs.windowMs,
        { ms -> update { it.copy(comboSettings = it.comboSettings.copy(windowMs = ms)) } },
    )
    Hint(
        "Só as colmeias que iniciam uma combinação esperam este intervalo antes de abrir a sua aplicação; " +
            "as outras abrem de imediato. Intervalos curtos favorecem a abertura individual, longos dão mais conforto às combinações.",
    )
    Text("Forma de introduzir combinações", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    ChoiceChips(
        listOf(ComboInput.BOTH to "Toques e deslizar", ComboInput.TAPS to "Só toques", ComboInput.SWIPE to "Só deslizar"),
        cs.input,
        { v -> update { it.copy(comboSettings = it.comboSettings.copy(input = v)) } },
    )
    Hint("“Só deslizar” elimina qualquer espera: uma combinação é feita arrastando o dedo pelas colmeias sem o levantar.")
    SwitchRow("Vibração ao reconhecer / rejeitar", cs.hapticFeedback, { v -> update { it.copy(comboSettings = it.comboSettings.copy(hapticFeedback = v)) } })
    SwitchRow("Realçar colmeias em espera", cs.showPending, { v -> update { it.copy(comboSettings = it.comboSettings.copy(showPending = v)) } })
    OutlinedButton(onClick = { showList = true }) { Text("Gerir combinações (${config.combos.size})") }

    if (showList) ComboListDialog(editor, onEdit = { c -> editing = c ?: ComboConfig(editor.newComboId(), emptyList(), action = LauncherAction.None) }, onDismiss = { showList = false })
    editing?.let { c -> ComboEditorDialog(editor, c, onDismiss = { editing = null }) }
}

@Composable
private fun LayoutSection(editor: EditorModel, config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val l = config.layout
    SectionTitle("Disposição")
    ChoiceChips(
        listOf(LayoutPreset.HONEYCOMB to "Favo (4 colunas)", LayoutPreset.TOWER to "Torre (3 colunas)", LayoutPreset.RING to "Anel"),
        l.preset,
        { p -> editor.changePreset(p) },
    )
    Hint("Cada disposição tem mais posições do que colmeias: no modo de edição arrasta as colmeias para escolher onde ficam os espaços livres.")
    LabeledSlider("Espaço entre colmeias", l.gap, 0f..0.3f, "${(l.gap * 100).toInt()}%", { v -> update { it.copy(layout = it.layout.copy(gap = v)) } })
    LabeledSlider("Tamanho da colmeia central", l.centerScale, 1f..1.4f, "${(l.centerScale * 100).toInt()}%", { v -> update { it.copy(layout = it.layout.copy(centerScale = v)) } })
    LabeledSlider("Arredondamento dos cantos", l.cornerRadius, 0f..0.35f, "${(l.cornerRadius * 100).toInt()}%", { v -> update { it.copy(layout = it.layout.copy(cornerRadius = v)) } })
    LabeledSlider("Escala da composição", l.scale, 0.6f..1f, "${(l.scale * 100).toInt()}%", { v -> update { it.copy(layout = it.layout.copy(scale = v)) } })
    LabeledSlider("Tamanho máximo das colmeias", l.maxRadiusDp, 40f..120f, "${l.maxRadiusDp.toInt()} dp", { v -> update { it.copy(layout = it.layout.copy(maxRadiusDp = v)) } })
    LabeledSlider("Posição vertical", l.verticalBias, -1f..1f, when {
        l.verticalBias < -0.33f -> "Em cima"
        l.verticalBias > 0.33f -> "Em baixo"
        else -> "Centro"
    }, { v -> update { it.copy(layout = it.layout.copy(verticalBias = v)) } })
}

@Composable
private fun CenterSection(editor: EditorModel, config: LauncherConfig) {
    var picking by remember { mutableStateOf(false) }
    val center = config.hive(CENTER_HIVE_ID)
    SectionTitle("Colmeia central")
    ValueRow("Ao tocar", editor.actionLabel(center.tapAction).ifEmpty { "Nenhuma ação" }, onClick = { picking = true })
    ColorPicker(center.color) { c -> editor.updateHive(CENTER_HIVE_ID) { it.copy(color = c) } }
    LabeledSlider("Opacidade", center.opacity, 0f..1f, "${(center.opacity * 100).toInt()}%", { v -> editor.updateHive(CENTER_HIVE_ID) { it.copy(opacity = v) } })
    Hint("Imagem, contorno e toque longo da colmeia central: modo de edição → tocar na colmeia central.")
    if (picking) {
        ActionPickerDialog(
            editor, "Colmeia central", allowCombo = true, allowNone = true,
            onPick = { a -> editor.setTapAction(CENTER_HIVE_ID, a); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun BackgroundSection(editor: EditorModel, config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val bg = config.background
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.importBackground(uri) { ok -> if (!ok) Toast.makeText(context, "Não foi possível abrir a imagem", Toast.LENGTH_SHORT).show() }
    }
    SectionTitle("Fundo")
    ChoiceChips(
        listOf(BackgroundMode.WALLPAPER to "Papel de parede", BackgroundMode.COLOR to "Cor", BackgroundMode.IMAGE to "Imagem"),
        bg.mode,
        { m ->
            if (m == BackgroundMode.IMAGE && bg.image == null) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else update { it.copy(background = it.background.copy(mode = m)) }
        },
    )
    when (bg.mode) {
        BackgroundMode.WALLPAPER -> Hint("Usa o papel de parede do sistema.")
        BackgroundMode.COLOR -> ColorPicker(bg.color) { c -> update { it.copy(background = it.background.copy(color = c)) } }
        BackgroundMode.IMAGE -> {
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Trocar imagem") }
            bg.image?.let { img ->
                ChoiceChips(
                    listOf(ImageFit.COVER to "Preencher", ImageFit.CONTAIN to "Ajustar", ImageFit.STRETCH to "Esticar"),
                    img.fit,
                    { f -> update { it.copy(background = it.background.copy(image = img.copy(fit = f))) } },
                )
                LabeledSlider("Zoom", img.zoom, 1f..4f, "%.1fx".format(img.zoom), { v -> update { it.copy(background = it.background.copy(image = img.copy(zoom = v))) } })
                LabeledSlider("Posição horizontal", img.offsetX, -1f..1f, "%+.2f".format(img.offsetX), { v -> update { it.copy(background = it.background.copy(image = img.copy(offsetX = v))) } })
                LabeledSlider("Posição vertical", img.offsetY, -1f..1f, "%+.2f".format(img.offsetY), { v -> update { it.copy(background = it.background.copy(image = img.copy(offsetY = v))) } })
            }
        }
    }
    LabeledSlider("Escurecer fundo", bg.dim, 0f..0.8f, "${(bg.dim * 100).toInt()}%", { v -> update { it.copy(background = it.background.copy(dim = v)) } })
}

@Composable
private fun SearchSection(config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val s = config.search
    fun set(t: (com.hexa.core.model.SearchSettings) -> com.hexa.core.model.SearchSettings) = update { it.copy(search = t(it.search)) }
    SectionTitle("Pesquisa")
    ChoiceChips(listOf(SearchPosition.BOTTOM to "Barra em baixo", SearchPosition.TOP to "Barra em cima"), s.position, { p -> set { it.copy(position = p) } })
    SwitchRow("Abrir o teclado automaticamente", s.autoKeyboard, { v -> set { it.copy(autoKeyboard = v) } })
    SwitchRow("Tecla Enter abre o primeiro resultado", s.enterOpensFirst, { v -> set { it.copy(enterOpensFirst = v) } })
    SwitchRow("Abrir automaticamente quando só há um resultado", s.autoLaunchSingle, { v -> set { it.copy(autoLaunchSingle = v) } }, "Mais rápido, mas pode abrir uma app antes de acabares de escrever.")
    SwitchRow("Ordenar pelas apps mais usadas", s.rankByUsage, { v -> set { it.copy(rankByUsage = v) } })
    SwitchRow("Mostrar combinações nos resultados", s.showCombos, { v -> set { it.copy(showCombos = v) } })
    SwitchRow("Procurar também no nome do pacote", s.matchPackageName, { v -> set { it.copy(matchPackageName = v) } })
    SwitchRow("Deslizar para cima abre a pesquisa", s.swipeUpOpensSearch, { v -> set { it.copy(swipeUpOpensSearch = v) } })
    LabeledSlider("Número de resultados", s.maxResults.toFloat(), 3f..30f, "${s.maxResults}", { v -> set { it.copy(maxResults = v.toInt()) } }, steps = 26)
}

@Composable
private fun MusicSection(editor: EditorModel, config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val m = config.music
    val music = editor.graph.music
    var access by remember { mutableStateOf(music.hasAccess()) }
    var pickPlayer by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = music.hasAccess() }
    fun set(t: (com.hexa.core.model.MusicSettings) -> com.hexa.core.model.MusicSettings) = update { it.copy(music = t(it.music)) }
    SectionTitle("Widget de música")
    SwitchRow("Mostrar colmeia de música", m.enabled, { v -> set { it.copy(enabled = v) } })
    ChoiceChips(
        listOf(Corner.TOP_START to "Cima ←", Corner.TOP_END to "Cima →", Corner.BOTTOM_START to "Baixo ←", Corner.BOTTOM_END to "Baixo →"),
        m.corner,
        { c -> set { it.copy(corner = c) } },
    )
    ChoiceChips(
        listOf(MusicTapAction.EXPAND to "Toque expande", MusicTapAction.PLAY_PAUSE to "Toque reproduz/pausa"),
        m.tapAction,
        { a -> set { it.copy(tapAction = a) } },
    )
    Hint("O toque longo faz a outra ação.")
    if (access) {
        Hint("Acesso a notificações concedido: capa, faixa, artista e progresso disponíveis.")
    } else {
        Hint(
            "Sem acesso a notificações os botões reproduzir/pausar/seguinte/anterior funcionam, mas o Android não deixa ver a faixa atual. " +
                "O Hexa não lê nem guarda notificações; o acesso serve apenas para ler a sessão de música ativa.",
        )
        Button(onClick = {
            try {
                context.startActivity(music.accessSettingsIntent())
            } catch (e: Exception) {
                Toast.makeText(context, "Abre Definições → Notificações → Acesso a notificações", Toast.LENGTH_LONG).show()
            }
        }) { Text("Permitir acesso") }
    }
    ValueRow(
        "Leitor quando nada está a tocar",
        m.preferredPlayerKey?.let { editor.apps.value.label(it) } ?: "Automático",
        onClick = { pickPlayer = true },
    )
    ColorPicker(m.color) { c -> set { it.copy(color = c) } }
    LabeledSlider("Opacidade", m.opacity, 0.2f..1f, "${(m.opacity * 100).toInt()}%", { v -> set { it.copy(opacity = v) } })
    if (pickPlayer) {
        ActionPickerDialog(
            editor, "Leitor de música", allowCombo = false, allowNone = false, appsOnly = true,
            onPick = { a -> if (a is LauncherAction.OpenApp) set { it.copy(preferredPlayerKey = a.appKey) }; pickPlayer = false },
            onDismiss = { pickPlayer = false },
        )
    }
}

@Composable
private fun AnimationSection(config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val a = config.animation
    fun set(t: (com.hexa.core.model.AnimationSettings) -> com.hexa.core.model.AnimationSettings) = update { it.copy(animation = t(it.animation)) }
    SectionTitle("Animações")
    SwitchRow("Animações da interface", a.enabled, { v -> set { it.copy(enabled = v) } }, "Desligadas, tudo muda instantaneamente.")
    LabeledSlider("Duração", a.speed, 0.5f..1.5f, "${(a.speed * 100).toInt()}%", { v -> set { it.copy(speed = v) } }, enabled = a.enabled)
    Text("Abertura de aplicações", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    ChoiceChips(
        listOf(LaunchAnimation.REVEAL_FROM_HIVE to "A partir da colmeia", LaunchAnimation.SYSTEM to "Do sistema", LaunchAnimation.NONE to "Sem animação"),
        a.launchAnimation,
        { v -> set { it.copy(launchAnimation = v) } },
    )
}

@Composable
private fun InteractionSection(config: LauncherConfig, update: ((LauncherConfig) -> LauncherConfig) -> Unit) {
    val i = config.interaction
    fun set(t: (com.hexa.core.model.InteractionSettings) -> com.hexa.core.model.InteractionSettings) = update { it.copy(interaction = t(it.interaction)) }
    SectionTitle("Interação")
    SwitchRow(
        "Disparo imediato ao pressionar",
        i.triggerOnPress,
        { v -> set { it.copy(triggerOnPress = v) } },
        "Colmeias que não iniciam combinações abrem logo ao tocar no ecrã, sem esperar que o dedo levante. Desativa o toque longo nessas colmeias.",
    )
    SwitchRow("Toque longo no fundo abre a edição", i.longPressEdits, { v -> set { it.copy(longPressEdits = v) } })
    SwitchRow("Bloquear disposição", i.lockLayout, { v -> set { it.copy(lockLayout = v) } }, "Impede entrar no modo de edição a partir do ecrã inicial.")
    SwitchRow("Vibração", i.haptics, { v -> set { it.copy(haptics = v) } })
}

@Composable
private fun PerformanceSection(editor: EditorModel) {
    val perf = editor.graph.perf
    var refresh by remember { mutableIntStateOf(0) }
    var memory by remember { mutableStateOf("…") }
    LaunchedEffect(refresh) {
        memory = withContext(Dispatchers.IO) {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val rt = Runtime.getRuntime()
            val heap = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
            "PSS total: ${info.totalPss / 1024} MB · heap Java: $heap MB"
        }
    }
    SectionTitle("Desempenho (medições reais deste dispositivo)")
    Hint("Latências em ms registadas durante a utilização: média · p50 · p95 · máximo (n = amostras).")
    val summaries = remember(refresh) { perf.all() }
    if (summaries.isEmpty()) Hint("Ainda sem medições: usa o launcher e volta aqui.")
    for (s in summaries) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(10.dp),
        ) {
            Text(s.metric.label, color = Color.White, fontSize = 13.sp)
            Text(
                "%.1f · %.1f · %.1f · %.1f  (n=%d, último %.1f)".format(s.mean, s.p50, s.p95, s.max, s.count, s.last),
                color = HexaColors.Accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            )
        }
    }
    if (summaries.any { it.metric == Metric.TOUCH_TO_RECOGNITION }) {
        Hint("“Toque → reconhecimento” inclui a espera do intervalo nas colmeias que iniciam combinações.")
    }
    Text(memory, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { refresh++ }) { Text("Atualizar") }
        OutlinedButton(onClick = { perf.clear(); refresh++ }) { Text("Limpar") }
    }
}

@Composable
private fun BackupSection(editor: EditorModel, activity: SettingsActivity) {
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(ConfigCodec.encode(editor.config.value).toByteArray()) } != null
                }.getOrDefault(false)
            }
            Toast.makeText(activity, if (ok) "Configuração exportada" else "Falha ao exportar", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val config = withContext(Dispatchers.IO) {
                runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { ConfigCodec.decode(it.readBytes().decodeToString()) }
                }.getOrNull()
            }
            if (config != null) {
                editor.graph.config.set(config)
                Toast.makeText(activity, "Configuração importada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Ficheiro inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }
    SectionTitle("Cópia de segurança")
    Hint("Exporta colmeias, combinações e definições para um ficheiro JSON (as imagens personalizadas não são incluídas).")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { export.launch("hexa-launcher.json") }) { Text("Exportar") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Importar") }
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
    OutlinedButton(onClick = { confirmReset = true }) { Text("Repor tudo", color = HexaColors.Error) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Repor configuração?") },
            text = { Text("Todas as colmeias, combinações e definições voltam ao estado inicial.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    editor.graph.config.set(FirstRun.fill(activity, LauncherConfig(), editor.apps.value))
                }) { Text("Repor") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }
}
