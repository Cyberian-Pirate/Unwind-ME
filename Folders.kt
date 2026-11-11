package com.unwind.launcher

import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- Which folder each app is in ----------

private const val SEP = "\u001F"

/** Folders you picked by hand. Anything not in here follows its automatic folder. */
fun loadFolderMap(p: SharedPreferences): Map<String, String> {
    val raw = p.getString("folderMap", null) ?: return emptyMap()
    return raw.split("\n").mapNotNull {
        val parts = it.split(SEP)
        if (parts.size == 2 && parts[1] in FOLDER_ORDER) parts[0] to parts[1] else null
    }.toMap()
}

fun saveFolderMap(p: SharedPreferences, m: Map<String, String>) {
    p.edit().putString("folderMap", m.entries.joinToString("\n") { "${it.key}$SEP${it.value}" }).apply()
}

// ---------- The folders page ----------

/** A folder: a glass square with up to four of its icons, and its name underneath. */
@Composable
private fun FolderCard(name: String, apps: List<AppEntry>, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, animationSpec = LiquidSpring, label = "folder")
    val box = 108.dp
    Column(
        modifier
            .width(box + 14.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(box).glass(RoundedCornerShape(30.dp), focused),
            contentAlignment = Alignment.Center,
        ) {
            val shown = apps.take(4)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0..1) {
                            val a = shown.getOrNull(r * 2 + c)
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                if (a != null) AppGlyph(a, 44.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            color = Color.White.copy(alpha = if (focused) 1f else 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${apps.size} apps", color = ui.wp.accent.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

/**
 * All apps, sorted into folders like the iOS App Library. OK opens a folder; inside one, OK opens an
 * app and holding OK moves it to another folder.
 */
@Composable
fun AppFolders(
    apps: List<AppEntry>,
    folderMap: Map<String, String>,
    openFolder: String?,
    focusKick: Int,
    nav: Nav,
    onOpenFolder: (String?) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMove: (AppEntry) -> Unit,
) {
    val groups = FOLDER_ORDER.map { f -> f to apps.filter { (folderMap[it.pkg] ?: it.folder) == f } }
        .filter { it.second.isNotEmpty() }
    val current = groups.firstOrNull { it.first == openFolder }

    // If the open folder just became empty, go back to the folder list
    LaunchedEffect(openFolder, current == null) {
        if (openFolder != null && current == null) onOpenFolder(null)
    }
    LaunchedEffect(apps.size, focusKick, openFolder) {
        delay(200)
        if (apps.isNotEmpty()) runCatching { nav.content.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 163.dp, top = 84.dp, end = 40.dp)) {
        if (current == null) {
            Text("Press OK to open a folder.", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            val columns = 6
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            ) {
                itemsIndexed(groups, key = { _, g -> g.first }) { i, g ->
                    FolderCard(
                        g.first,
                        g.second,
                        (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                            .dpad(
                                left = if (i % columns == 0) nav.rail else null,
                                up = if (i < columns) nav.pill else null,
                            ),
                    ) { onOpenFolder(g.first) }
                }
            }
        } else {
            Text(
                "${current.first}  \u00B7  hold OK on an app to move it to another folder. Back returns to the folders.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            val columns = 7
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            ) {
                itemsIndexed(current.second, key = { _, a -> a.pkg }) { i, app ->
                    Tile(
                        app.label,
                        (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                            .dpad(
                                left = if (i % columns == 0) nav.rail else null,
                                up = if (i < columns) nav.pill else null,
                            ),
                        onClick = { onLaunch(app) },
                        forceName = true,
                        onLongPress = { onMove(app) },
                    ) { d, focused -> AppGlyph(app, d, focused) }
                }
            }
        }
    }
}

// ---------- "Move to" dialog ----------

@Composable
fun MoveAppDialog(
    app: AppEntry,
    currentFolder: String,
    isManual: Boolean,
    focus: FocusRequester,
    onPick: (String) -> Unit,
    onAuto: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(app.pkg) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
            PopupPanel("Move ${app.label} to") {
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FOLDER_ORDER.forEachIndexed { i, f ->
                        PopupRow(
                            f,
                            highlighted = f == currentFolder,
                            trailing = if (f == currentFolder) Icons.Rounded.Check else null,
                            modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
                            onClick = { onPick(f) },
                        )
                    }
                    if (isManual) {
                        PopupRow(
                            "Back to automatic (${app.folder})",
                            icon = Icons.Rounded.Refresh,
                            onClick = onAuto,
                        )
                    }
                }
            }
        }
    }
}
