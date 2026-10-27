package com.hexa.launcher.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hexa.core.model.BUILT_IN_ACTIONS
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.builtInLabel
import com.hexa.core.search.SearchIndex
import com.hexa.core.search.SearchItem
import com.hexa.launcher.apps.AppInfo
import com.hexa.launcher.apps.AppShortcutInfo
import com.hexa.launcher.ui.common.AppIcon
import com.hexa.launcher.ui.common.ChoiceChips
import com.hexa.launcher.ui.common.EditorModel
import com.hexa.launcher.ui.common.Hint
import com.hexa.launcher.ui.theme.HexaColors

private enum class PickerTab(val label: String) {
    APPS("Apps"),
    SHORTCUTS("Atalhos"),
    LAUNCHER("Launcher"),
    URI("Endereço"),
    COMBO("Combinação"),
}

/**
 * Chooses what a hive, the centre hive or a combination does. [allowCombo] adds "run another
 * combination" (not offered when editing a combination itself, to avoid loops). [appsOnly] limits
 * the choice to apps (preferred music player).
 */
@Composable
fun ActionPickerDialog(
    editor: EditorModel,
    title: String,
    allowCombo: Boolean,
    allowNone: Boolean,
    onPick: (LauncherAction) -> Unit,
    onDismiss: () -> Unit,
    appsOnly: Boolean = false,
) {
    var tab by remember { mutableStateOf(PickerTab.APPS) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = HexaColors.Background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
                if (!appsOnly) {
                    val tabs = PickerTab.entries.filter { it != PickerTab.COMBO || allowCombo }
                    ChoiceChips(tabs.map { it to it.label }, tab, { tab = it }, Modifier.padding(vertical = 8.dp))
                }
                when (tab) {
                    PickerTab.APPS -> AppList(editor) { app -> onPick(LauncherAction.OpenApp(app.key, app.label)) }
                    PickerTab.SHORTCUTS -> ShortcutTab(editor, onPick)
                    PickerTab.LAUNCHER -> LauncherActions(allowNone, onPick)
                    PickerTab.URI -> UriTab(onPick)
                    PickerTab.COMBO -> ComboTab(editor, onPick)
                }
            }
        }
    }
}

/** Searchable app list with its own index instance. */
@Composable
fun AppList(editor: EditorModel, onPick: (AppInfo) -> Unit) {
    val snapshot by editor.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val index = remember(snapshot) { SearchIndex(snapshot.apps.map { SearchItem(it.key, it.label, it.packageName) }) }
    val list = remember(snapshot, query) {
        if (query.isBlank()) snapshot.apps else index.search(query, limit = 60).mapNotNull { snapshot.byKey[it.item.key] }
    }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Procurar aplicação") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (!snapshot.loaded) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(list, key = { it.key }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(app) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(editor.graph, app.key, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (app.isWorkProfile) "${app.label} (trabalho)" else app.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutTab(editor: EditorModel, onPick: (LauncherAction) -> Unit) {
    var app by remember { mutableStateOf<AppInfo?>(null) }
    val selected = app
    if (!editor.graph.shortcuts.hasPermission) {
        Hint("Os atalhos de aplicações (ex.: \"Nova mensagem\") só estão disponíveis quando o Hexa é o launcher predefinido.")
    }
    if (selected == null) {
        Hint("Escolhe a aplicação cujos atalhos queres ver:")
        AppList(editor) { app = it }
        return
    }
    var shortcuts by remember(selected.key) { mutableStateOf<List<AppShortcutInfo>?>(null) }
    LaunchedEffect(selected.key) { shortcuts = editor.shortcuts(selected.packageName, selected.userSerial) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        AppIcon(editor.graph, selected.key, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(selected.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { app = null }) { Text("Outra app") }
    }
    val list = shortcuts
    when {
        list == null -> CircularProgressIndicator(Modifier.padding(16.dp))
        list.isEmpty() -> Hint("Esta aplicação não publica atalhos.")
        else -> LazyColumn {
            items(list, key = { it.id }) { s ->
                Text(
                    s.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(LauncherAction.AppShortcut(s.packageName, s.id, s.userSerial, "${selected.label}: ${s.label}")) }
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun LauncherActions(allowNone: Boolean, onPick: (LauncherAction) -> Unit) {
    val actions = (if (allowNone) listOf<LauncherAction>(LauncherAction.None) else emptyList()) + BUILT_IN_ACTIONS
    LazyColumn {
        items(actions) { a ->
            Text(
                a.builtInLabel().orEmpty(),
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(a) }
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun UriTab(onPick: (LauncherAction) -> Unit) {
    var uri by remember { mutableStateOf("https://") }
    var label by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("Abre um endereço web, um número (tel:912345678), um local (geo:…) ou uma ligação direta de uma aplicação.")
        OutlinedTextField(value = uri, onValueChange = { uri = it }, label = { Text("Endereço") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nome (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val valid = uri.contains(':') && uri.length > uri.indexOf(':') + 1
        Button(onClick = { onPick(LauncherAction.OpenUri(uri.trim(), label.trim())) }, enabled = valid) { Text("Usar este endereço") }
    }
}

@Composable
private fun ComboTab(editor: EditorModel, onPick: (LauncherAction) -> Unit) {
    val config by editor.config.collectAsState()
    if (config.combos.isEmpty()) {
        Hint("Ainda não existem combinações. Cria uma em Edição → Combinações.")
        return
    }
    LazyColumn {
        items(config.combos, key = { it.id }) { combo ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .clickable { onPick(LauncherAction.RunCombo(combo.id)) }
                    .padding(12.dp),
            ) {
                ComboSequence(editor, combo, badge = 22.dp)
                Text(combo.name.ifEmpty { editor.actionLabel(combo.action) }, color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.size(6.dp))
        }
    }
}
