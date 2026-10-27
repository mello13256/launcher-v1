package com.hexa.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexa.core.model.LauncherAction
import com.hexa.core.model.SearchPosition
import com.hexa.core.search.ComboSearch
import com.hexa.core.search.SearchHit
import com.hexa.launcher.InternalKeys
import com.hexa.launcher.ui.common.AppIcon
import com.hexa.launcher.ui.common.HiveBadge
import com.hexa.launcher.ui.hex.Glyph
import com.hexa.launcher.ui.hex.GlyphIcon
import com.hexa.launcher.ui.hex.LongHexShape
import com.hexa.launcher.ui.theme.HexaColors

/** The always-visible search entry: an elongated hexagon. Separate from the centre hive. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(LongHexShape)
            .background(Color(0xCC14151F))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Pesquisar aplicações" }
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.SEARCH, Color.White.copy(alpha = 0.85f), size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text("Pesquisar", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
    }
}

/**
 * Full-screen search. With the bar at the bottom the list grows upwards, so the best result sits
 * right above the keyboard, under the thumb. Combinations relevant to the query are listed between
 * the results and the field.
 */
@Composable
fun SearchOverlay(vm: HomeViewModel, position: SearchPosition, autoKeyboard: Boolean) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (autoKeyboard) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val bottom = position == SearchPosition.BOTTOM

    Box(
        Modifier
            .fillMaxSize()
            .background(HexaColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeSearch() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val field = @Composable { SearchField(vm, focus) }
            val combos = @Composable {
                if (vm.comboResults.isNotEmpty()) ComboStrip(vm, vm.comboResults)
            }
            val list = @Composable { ResultList(vm, vm.results, reverse = bottom, modifier = Modifier.weight(1f, fill = false)) }
            if (bottom) {
                list()
                combos()
                field()
            } else {
                field()
                combos()
                list()
            }
            if (vm.query.isNotBlank() && vm.results.isEmpty() && vm.comboResults.isEmpty()) {
                Text(
                    "Sem resultados para “${vm.query}”",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(vm: HomeViewModel, focus: FocusRequester) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(LongHexShape)
            .background(Color(0xFF1E2030))
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.SEARCH, HexaColors.Accent, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (vm.query.isEmpty()) Text("Nome da aplicação…", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
            BasicTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(HexaColors.Accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { vm.onSearchSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Campo de pesquisa" },
            )
        }
        if (vm.query.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { vm.onQueryChange("") }
                    .semantics { contentDescription = "Limpar" },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.CLOSE, Color.White.copy(alpha = 0.7f), size = 14.dp) }
        }
    }
}

@Composable
private fun ResultList(vm: HomeViewModel, results: List<SearchHit>, reverse: Boolean, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), reverseLayout = reverse) {
        items(results, key = { it.item.key }) { hit ->
            ResultRow(vm, hit, highlighted = hit === results.firstOrNull() && vm.config.value.search.enterOpensFirst)
        }
    }
}

@Composable
private fun ResultRow(vm: HomeViewModel, hit: SearchHit, highlighted: Boolean) {
    val key = hit.item.key
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(
                onClick = { vm.openResult(hit) },
                onLongClick = { if (!key.startsWith("hexa:")) vm.openAppDetails(key) },
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (key.startsWith("hexa:")) {
            val glyph = when (key) {
                InternalKeys.SETTINGS -> Glyph.GEAR
                InternalKeys.MUSIC -> Glyph.NOTE
                else -> Glyph.EDIT
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { GlyphIcon(glyph, HexaColors.Accent, size = 26.dp) }
        } else {
            AppIcon(vm.graph, key, 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(hit.item.label, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComboStrip(vm: HomeViewModel, hits: List<ComboSearch.ComboHit>) {
    val config = vm.config.value
    val icons = vm.editor.visuals.icons
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(hits, key = { it.combo.id }) { hit ->
            val combo = hit.combo
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF24263A))
                    .clickable { vm.runComboFromSearch(combo) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                combo.hives.forEachIndexed { i, id ->
                    if (i > 0) Text(if (combo.ordered) "›" else "+", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 2.dp))
                    HiveBadge(config.hive(id), icons[id], 22.dp)
                }
                Text("  →  ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                val target = combo.action
                if (target is LauncherAction.OpenApp) {
                    AppIcon(vm.graph, target.appKey, 22.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    combo.name.ifEmpty { vm.editor.actionLabel(target) },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
