package com.hexa.launcher.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexa.core.apps.TargetStatus
import com.hexa.core.model.CENTER_HIVE_ID
import com.hexa.core.model.ComboConfig
import com.hexa.core.model.HiveConfig
import com.hexa.core.model.HiveVisual
import com.hexa.core.model.ImageFit
import com.hexa.core.model.LauncherAction
import com.hexa.launcher.ui.common.AppIcon
import com.hexa.launcher.ui.common.ChoiceChips
import com.hexa.launcher.ui.common.EditorModel
import com.hexa.launcher.ui.common.Hint
import com.hexa.launcher.ui.common.HiveBadge
import com.hexa.launcher.ui.common.LabeledSlider
import com.hexa.launcher.ui.common.SectionTitle
import com.hexa.launcher.ui.common.SwitchRow
import com.hexa.launcher.ui.common.ValueRow
import com.hexa.launcher.ui.theme.HexaColors

private enum class EditorTab(val label: String) { ACTION("Ação"), LOOK("Aspeto"), IMAGE("Imagem"), COMBOS("Combinações") }

private enum class PickTarget { TAP, LONG }

/**
 * Per-hive editor. Changes apply immediately to the live composition above the sheet (the scrim is
 * transparent), so the user sees colour, opacity, size and image crop while adjusting them.
 */
@Composable
fun HiveEditorSheet(
    editor: EditorModel,
    hiveId: Int,
    onDismiss: () -> Unit,
    onEditCombo: (ComboConfig) -> Unit,
    onNewCombo: (Int) -> Unit,
    toast: (String) -> Unit,
) {
    val config by editor.config.collectAsState()
    val hive = config.hive(hiveId)
    val isCenter = hiveId == CENTER_HIVE_ID
    var tab by rememberSaveable { mutableStateOf(EditorTab.ACTION) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val status = editor.visuals.status[hiveId] ?: TargetStatus.READY

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.importHiveImage(hiveId, uri) { ok -> if (!ok) toast("Não foi possível abrir a imagem") }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = HexaColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.15f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiveBadge(hive, editor.visuals.icons[hiveId], 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isCenter) "Colmeia central" else "Colmeia ${hiveId + 1}",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        editor.hiveLabel(hiveId).ifEmpty { "Sem ação" },
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
            }
            ChoiceChips(EditorTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(vertical = 10.dp))

            when (tab) {
                EditorTab.ACTION -> ActionTab(editor, hive, isCenter, status) { picking = it }
                EditorTab.LOOK -> LookTab(editor, hive)
                EditorTab.IMAGE -> ImageTab(editor, hive) {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                EditorTab.COMBOS -> HiveCombos(editor, hiveId, onEdit = onEditCombo, onNew = { onNewCombo(hiveId) })
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    picking?.let { target ->
        ActionPickerDialog(
            editor = editor,
            title = if (target == PickTarget.TAP) "Ao tocar" else "Toque longo",
            allowCombo = true,
            allowNone = true,
            onPick = { action ->
                if (target == PickTarget.TAP) editor.setTapAction(hiveId, action) else editor.setLongPressAction(hiveId, action)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ActionTab(editor: EditorModel, hive: HiveConfig, isCenter: Boolean, status: TargetStatus, onPick: (PickTarget) -> Unit) {
    if (isCenter) {
        Hint("A colmeia central pode abrir uma aplicação, um atalho de uma aplicação, um endereço ou executar uma combinação existente.")
    }
    if (status == TargetStatus.MISSING) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HexaColors.Warning.copy(alpha = 0.15f))
                .padding(12.dp),
        ) {
            Text(
                "“${(hive.tapAction as? LauncherAction.OpenApp)?.label ?: "A aplicação"}” já não está instalada.",
                color = HexaColors.Warning, fontSize = 14.sp,
            )
            Text("As cores, imagem e combinações desta colmeia mantêm-se. Escolhe outra aplicação.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Button(onClick = { onPick(PickTarget.TAP) }, modifier = Modifier.padding(top = 8.dp)) { Text("Escolher aplicação") }
        }
    }
    val tapApp = hive.tapAction as? LauncherAction.OpenApp
    ValueRow(
        label = "Ao tocar",
        value = editor.actionLabel(hive.tapAction).ifEmpty { "Nenhuma ação" },
        onClick = { onPick(PickTarget.TAP) },
        leading = if (tapApp != null && status != TargetStatus.MISSING) {
            { AppIcon(editor.graph, tapApp.appKey, 32.dp) }
        } else {
            null
        },
    )
    ValueRow(
        label = "Toque longo",
        value = if (hive.longPressAction is LauncherAction.None) "Abrir editor (predefinição)" else editor.actionLabel(hive.longPressAction),
        onClick = { onPick(PickTarget.LONG) },
    )
    if (editor.config.value.interaction.triggerOnPress) {
        Hint("“Disparo imediato” está ativo: colmeias que não iniciam combinações abrem ao pressionar e não têm toque longo.")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { editor.setTapAction(hive.id, LauncherAction.None) }) { Text("Remover ação") }
        OutlinedButton(onClick = { editor.resetHive(hive.id) }) { Text("Repor colmeia") }
    }
}

@Composable
private fun LookTab(editor: EditorModel, hive: HiveConfig) {
    val id = hive.id
    SectionTitle("Estilo")
    ChoiceChips(
        listOf(
            HiveVisual.ICON to "Ícone",
            HiveVisual.IMAGE_WITH_ICON to "Imagem + ícone",
            HiveVisual.IMAGE to "Só imagem",
            HiveVisual.COLOR to "Só cor",
        ),
        hive.visual,
        { v -> editor.updateHive(id) { it.copy(visual = v) } },
    )
    if ((hive.visual == HiveVisual.IMAGE || hive.visual == HiveVisual.IMAGE_WITH_ICON) && hive.image == null) {
        Hint("Escolhe uma imagem no separador Imagem.")
    }
    SectionTitle("Cor")
    ColorPicker(hive.color) { c -> editor.updateHive(id) { it.copy(color = c) } }
    LabeledSlider("Opacidade", hive.opacity, 0f..1f, "${(hive.opacity * 100).toInt()}%", { v -> editor.updateHive(id) { it.copy(opacity = v) } })
    SectionTitle("Tamanho")
    LabeledSlider("Tamanho da colmeia", hive.sizeScale, 0.6f..1f, "${(hive.sizeScale * 100).toInt()}%", { v -> editor.updateHive(id) { it.copy(sizeScale = v) } })
    LabeledSlider("Tamanho do ícone", hive.iconScale, 0.3f..0.9f, "${(hive.iconScale * 100).toInt()}%", { v -> editor.updateHive(id) { it.copy(iconScale = v) } })
    SectionTitle("Contorno")
    LabeledSlider("Espessura", hive.outlineWidth, 0f..6f, if (hive.outlineWidth < 0.25f) "Sem contorno" else "%.1f dp".format(hive.outlineWidth), { v ->
        editor.updateHive(id) { it.copy(outlineWidth = if (v < 0.25f) 0f else v) }
    })
    if (hive.outlineWidth > 0f) ColorPicker(hive.outlineColor) { c -> editor.updateHive(id) { it.copy(outlineColor = c) } }
    SwitchRow("Mostrar nome", hive.showLabel, { v -> editor.updateHive(id) { it.copy(showLabel = v) } }, "Por omissão as colmeias reconhecem-se pela cor e pelo ícone.")
}

@Composable
private fun ImageTab(editor: EditorModel, hive: HiveConfig, onPick: () -> Unit) {
    val id = hive.id
    val img = hive.image
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPick) { Text(if (img == null) "Escolher imagem" else "Trocar imagem") }
        if (img != null) OutlinedButton(onClick = { editor.removeHiveImage(id) }) { Text("Remover") }
    }
    Hint("A imagem é copiada e reduzida para o armazenamento do launcher; não são precisas permissões.")
    if (img == null) return
    SectionTitle("Recorte")
    ChoiceChips(
        listOf(ImageFit.COVER to "Preencher", ImageFit.CONTAIN to "Ajustar", ImageFit.STRETCH to "Esticar"),
        img.fit,
        { f -> editor.updateHive(id) { h -> h.copy(image = h.image?.copy(fit = f)) } },
    )
    LabeledSlider("Zoom", img.zoom, 1f..4f, "%.1fx".format(img.zoom), { v -> editor.updateHive(id) { h -> h.copy(image = h.image?.copy(zoom = v)) } })
    LabeledSlider("Posição horizontal", img.offsetX, -1f..1f, "%+.2f".format(img.offsetX), { v -> editor.updateHive(id) { h -> h.copy(image = h.image?.copy(offsetX = v)) } })
    LabeledSlider("Posição vertical", img.offsetY, -1f..1f, "%+.2f".format(img.offsetY), { v -> editor.updateHive(id) { h -> h.copy(image = h.image?.copy(offsetY = v)) } })
    LabeledSlider("Opacidade", hive.opacity, 0f..1f, "${(hive.opacity * 100).toInt()}%", { v -> editor.updateHive(id) { it.copy(opacity = v) } })
}
