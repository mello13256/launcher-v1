package com.hexa.launcher.ui.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hexa.core.apps.TargetStatus
import com.hexa.core.combo.ComboMatcher
import com.hexa.core.combo.ComboValidator
import com.hexa.core.layout.HexLayoutEngine
import com.hexa.core.layout.LayoutPresets
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.LauncherConfig
import com.hexa.launcher.ui.common.AppIcon
import com.hexa.launcher.ui.common.EditorModel
import com.hexa.launcher.ui.common.Hint
import com.hexa.launcher.ui.common.HiveBadge
import com.hexa.launcher.ui.common.SectionTitle
import com.hexa.launcher.ui.common.ValueRow
import com.hexa.launcher.ui.hex.HexPaths
import com.hexa.launcher.ui.hex.HiveDecor
import com.hexa.launcher.ui.hex.drawHive
import com.hexa.launcher.ui.theme.HexaColors

/** Hives of a combination drawn as small hexagons joined by "+" (any order) or "›" (in order). */
@Composable
fun ComboSequence(editor: EditorModel, combo: ComboConfig, badge: Dp = 26.dp) {
    val config by editor.config.collectAsState()
    val icons = editor.visuals.icons
    FlowRow(verticalArrangement = Arrangement.Center) {
        combo.hives.forEachIndexed { i, id ->
            if (i > 0) Text(if (combo.ordered) "›" else "+", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 3.dp))
            HiveBadge(config.hive(id), icons[id], badge)
        }
    }
}

/**
 * Miniature of the real composition. Tapping a hive appends it to the sequence being recorded, so
 * combinations are defined by tapping the same shapes used on the home screen.
 */
@Composable
fun MiniHoneycomb(editor: EditorModel, config: LauncherConfig, highlight: List<Int>, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val visuals = editor.visuals
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val geometry = remember(config.layout, w, h) {
            HexLayoutEngine.compute(LayoutPresets.of(config.layout.preset), config.layout.copy(maxRadiusDp = 999f, scale = 1f), w, h, density, landscape = false)
        }
        val paths = remember(geometry, config.hives, config.layout.cornerRadius) {
            val bySlot = config.hives.associateBy { it.slot }
            geometry.slots.map { s ->
                Path().also { HexPaths.hexagon(it, s.cx, s.cy, s.radius * (bySlot[s.slot]?.sizeScale ?: 1f), true, config.layout.cornerRadius) }
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geometry, config.hives) {
                    detectTapGestures { pos ->
                        val slot = geometry.slotAt(pos.x, pos.y) ?: return@detectTapGestures
                        config.hiveAtSlot(slot)?.let { onTap(it.id) }
                    }
                },
        ) {
            for (s in geometry.slots) {
                val hive = config.hiveAtSlot(s.slot) ?: continue
                val count = highlight.count { it == hive.id }
                drawHive(
                    path = paths[s.slot],
                    cx = s.cx, cy = s.cy, r = s.radius * hive.sizeScale, pointyTop = true,
                    hive = hive,
                    icon = visuals.icons[hive.id],
                    image = hive.image?.let { visuals.images[it.path] },
                    status = visuals.status[hive.id] ?: TargetStatus.READY,
                    decor = HiveDecor(pending = count > 0, press = if (count > 0) 0.4f else 0f),
                )
            }
        }
    }
}

@Composable
fun ComboEditorDialog(editor: EditorModel, initial: ComboConfig, onDismiss: () -> Unit) {
    val config by editor.config.collectAsState()
    var hives by remember(initial.id) { mutableStateOf(initial.hives) }
    var ordered by remember(initial.id) { mutableStateOf(initial.ordered) }
    var action by remember(initial.id) { mutableStateOf(initial.action) }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var picking by remember { mutableStateOf(false) }
    val exists = config.combos.any { it.id == initial.id }

    val candidate = ComboConfig(initial.id, hives, ordered, action, enabled, name.trim())
    val errors = ComboValidator.errors(candidate, config.combos)
    val warnings = ComboValidator.warnings(candidate, config.combos)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = HexaColors.Background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(if (exists) "Editar combinação" else "Nova combinação", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Hint("Toca nas colmeias pela ordem desejada (podes repetir uma colmeia para um toque duplo).")
                MiniHoneycomb(
                    editor, config, hives,
                    onTap = { id -> if (hives.size < ComboMatcher.MAX_LENGTH) hives = hives + id },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        if (hives.isEmpty()) Text("Nenhuma colmeia escolhida", color = Color.White.copy(alpha = 0.5f))
                        else ComboSequence(editor, candidate, badge = 30.dp)
                    }
                    TextButton(onClick = { hives = hives.dropLast(1) }, enabled = hives.isNotEmpty()) { Text("Apagar última") }
                    TextButton(onClick = { hives = emptyList() }, enabled = hives.isNotEmpty()) { Text("Limpar") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("A ordem dos toques importa", color = Color.White, fontSize = 14.sp)
                        Text(
                            if (ordered) "Só dispara pela ordem indicada" else "Dispara com as mesmas colmeias em qualquer ordem",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                        )
                    }
                    Switch(checked = ordered, onCheckedChange = { ordered = it })
                }
                SectionTitle("Ação")
                val current = action
                ValueRow(
                    label = "Ao reconhecer a combinação",
                    value = if (current is LauncherAction.None) "Escolher…" else editor.actionLabel(current),
                    onClick = { picking = true },
                    leading = if (current is LauncherAction.OpenApp) {
                        { AppIcon(editor.graph, current.appKey, 32.dp) }
                    } else {
                        null
                    },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome (opcional, aparece na pesquisa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Ativa", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                for (e in errors) Text("• " + e.message, color = HexaColors.Error, fontSize = 13.sp)
                for (w in warnings) Text("• " + w.message, color = HexaColors.Warning, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exists) {
                        OutlinedButton(onClick = {
                            editor.deleteCombo(initial.id)
                            onDismiss()
                        }) { Text("Eliminar", color = HexaColors.Error) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = { if (editor.saveCombo(candidate).isEmpty()) onDismiss() },
                        enabled = errors.isEmpty(),
                    ) { Text("Guardar") }
                }
            }
        }
    }
    if (picking) {
        ActionPickerDialog(
            editor = editor,
            title = "Ação da combinação",
            allowCombo = false,
            allowNone = false,
            onPick = {
                action = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
fun ComboListDialog(editor: EditorModel, onEdit: (ComboConfig?) -> Unit, onDismiss: () -> Unit) {
    val config by editor.config.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = HexaColors.Background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Combinações", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
                Hint(
                    "Intervalo atual entre toques: ${config.comboSettings.windowMs} ms. " +
                        "Também podes deslizar o dedo pelas colmeias sem levantar (sem espera).",
                )
                Button(onClick = { onEdit(null) }, modifier = Modifier.padding(vertical = 8.dp)) { Text("Nova combinação") }
                if (config.combos.isEmpty()) {
                    Hint("Exemplo: toca na colmeia A e depois na B para abrir o Discord.")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(config.combos, key = { it.id }) { combo ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { onEdit(combo) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                ComboSequence(editor, combo, badge = 24.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    (combo.name.takeIf { it.isNotEmpty() }?.let { "$it · " } ?: "") + editor.actionLabel(combo.action),
                                    color = Color.White, fontSize = 14.sp,
                                )
                                Text(if (combo.ordered) "Ordem importa" else "Qualquer ordem", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                            Switch(checked = combo.enabled, onCheckedChange = { editor.setComboEnabled(combo.id, it) })
                        }
                    }
                }
            }
        }
    }
}

/** Combinations that include [hiveId], shown in the hive editor. */
@Composable
fun HiveCombos(editor: EditorModel, hiveId: Int, onEdit: (ComboConfig) -> Unit, onNew: () -> Unit) {
    val config by editor.config.collectAsState()
    val combos = config.combos.filter { hiveId in it.hives }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (combos.isEmpty()) Hint("Esta colmeia ainda não faz parte de nenhuma combinação.")
        for (combo in combos) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onEdit(combo) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    ComboSequence(editor, combo, badge = 22.dp)
                    Text(editor.actionLabel(combo.action), color = Color.White, fontSize = 13.sp)
                }
                Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
            }
        }
        OutlinedButton(onClick = onNew) { Text("Nova combinação a começar nesta colmeia") }
    }
}
