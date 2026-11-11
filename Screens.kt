package com.unwind.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

// ---------- Home ----------

@Composable
private fun HomeTile(app: AppEntry?, modifier: Modifier, onLaunch: (AppEntry) -> Unit, onAdd: () -> Unit) {
    val ui = LocalUi.current
    if (app != null) {
        Tile(app.label, modifier, onClick = { onLaunch(app) }) { d, focused -> AppGlyph(app, d, focused) }
    } else {
        Tile("Add apps", modifier, onClick = onAdd) { _, _ ->
            Icon(Icons.Rounded.Add, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
fun HomeContent(
    favApps: List<AppEntry>,
    focusKick: Int,
    nav: Nav,
    prayer: PrayerDay?,
    headlines: List<String>,
    dateLine: String = "",
    onLaunch: (AppEntry) -> Unit,
    onAdd: () -> Unit,
) {
    val ui = LocalUi.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val part = dayPartFor(hour)
    val greeting = ui.settings.greetings[part] ?: DEFAULT_GREETINGS.getValue(part)
    LaunchedEffect(favApps.size, focusKick) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }

    val stacked = ui.settings.homeLayout == HomeLayout.Stacked
    val dock = ui.settings.dock
    val dockShape = RoundedCornerShape(34.dp)
    val dockPad = 18.dp
    // the "Add apps" tile is the last item in the flow
    val items: List<AppEntry?> = favApps + null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // How many tiles fit across, worked out from the screen so it adapts to any TV
        val tileW = 86.dp
        val gap = 16.dp
        val usable = maxWidth - 163.dp - 48.dp - (if (dock) dockPad * 2 else 0.dp)
        val cols = maxOf(1, ((usable + gap) / (tileW + gap)).toInt())
        val rows = items.chunked(cols)
        val tall = stacked && rows.size > 1
        val topPad by animateDpAsState(if (tall) 104.dp else 140.dp, label = "homeTop")
        // Dock height setting: how far the row sits above the bottom edge
        val lift = 78 - ui.settings.dockPos.bottom
        // The news ticker sits below the app row, so the row is lifted by
        // the ticker height (21 dp) plus its gap (12 dp) when it is shown.
        val tickerPad = if (ui.settings.showTicker) 33.dp else 0.dp
        val bottomPad by animateDpAsState(
            if (tall) maxOf(8, 28 - lift / 2).dp + tickerPad else ui.settings.dockPos.bottom.dp + tickerPad,
            label = "homeBottom",
        )

        Column(Modifier.fillMaxSize().padding(start = 163.dp, top = topPad, bottom = bottomPad)) {
            GreetingText(greeting, dateLine = dateLine)
            if (!stacked) {
                Spacer(Modifier.weight(1f))
                // Endless row. With the dock on, the glass bar stays put and the apps slide across it.
                Box(
                    if (dock) Modifier.padding(end = 24.dp).offset(x = -dockPad).glass(dockShape) else Modifier,
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        // vertical padding leaves room for the focused tile to swell without being clipped
                        contentPadding = PaddingValues(
                            start = if (dock) dockPad else 0.dp,
                            top = 18.dp,
                            bottom = 18.dp,
                            end = if (dock) dockPad else 48.dp,
                        ),
                    ) {
                        itemsIndexed(items, key = { _, a -> a?.pkg ?: "__add" }) { i, app ->
                            HomeTile(
                                app,
                                (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                                    .dpad(left = if (i == 0) nav.rail else null, up = nav.pill),
                                onLaunch,
                                onAdd,
                            )
                        }
                    }
                }
            } else {
                // Stacked rows: the first row fills the width, then apps continue in rows below it.
                // The whole block starts a little to the left so the dock bar and the focus swell have room.
                val side = if (dock) dockPad else 14.dp
                Box(
                    Modifier.weight(1f).fillMaxWidth().offset(x = -side),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 18.dp)) {
                        rows.forEachIndexed { ri, row ->
                            if (ri > 0) Spacer(Modifier.height(14.dp))
                            Row(
                                if (ri == 0 && dock) {
                                    Modifier.glass(dockShape).padding(horizontal = dockPad, vertical = 14.dp)
                                } else {
                                    Modifier.padding(start = side)
                                },
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                row.forEachIndexed { ci, app ->
                                    val isFirst = ri == 0 && ci == 0
                                    HomeTile(
                                        app,
                                        (if (isFirst) Modifier.focusRequester(nav.content) else Modifier)
                                            .dpad(left = if (ci == 0) nav.rail else null, up = if (ri == 0) nav.pill else null),
                                        onLaunch,
                                        onAdd,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Prayer card and news ticker are part of Home itself, so they only
        // ever appear on this screen. Their glass and colors come from the
        // same wallpaper and glass style as the rest of Home, so changing
        // either one in Preferences recolors them with no extra setting.
        if (ui.settings.showPrayer) {
            PrayerCard(
                prayer,
                Modifier.align(Alignment.TopEnd).padding(top = 97.dp, end = 24.dp),
            )
        }
        if (ui.settings.showTicker) {
            NewsTicker(
                headlines,
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 19.dp, end = 19.dp, bottom = 12.dp)
                    .height(21.dp),
            )
        }
    }
}

// ---------- Prayer card and news ticker ----------

@Composable
fun PrayerCard(prayer: PrayerDay?, modifier: Modifier) {
    val ui = LocalUi.current
    val times = prayer?.display ?: List(5) { "--:--" }
    val next = prayer?.nextIndex ?: -1
    Column(
        modifier.width(262.dp).glass(RoundedCornerShape(20.dp)).padding(vertical = 12.dp),
    ) {
        Text(
            "WAKTU SOLAT",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("PUTRAJAYA", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        PRAYER_NAMES.forEachIndexed { i, name ->
            val isNext = i == next
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (isNext) Modifier.background(ui.wp.accent, RoundedCornerShape(10.dp)) else Modifier)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        name,
                        color = if (isNext) ui.wp.dark else Color.White,
                        fontSize = 14.sp,
                    )
                    Text(
                        times[i],
                        color = if (isNext) ui.wp.dark else Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun NewsTicker(headlines: List<String>, modifier: Modifier) {
    val single = remember(headlines) { headlines.joinToString("  \u2022  ") }
    val doubled = remember(single) { "$single  \u2022  $single" }
    val textStyle = TextStyle(fontSize = 11.sp, color = Color.White)
    val measurer = rememberTextMeasurer()
    val singleW = remember(single) {
        if (single.isBlank()) 0f else measurer.measure(single, textStyle).size.width.toFloat()
    }
    val period = remember(single) {
        if (single.isBlank()) 0f else measurer.measure("$single  \u2022  ", textStyle).size.width.toFloat()
    }
    // The doubled string's full width. A plain Text inside the narrow box
    // is measured AT_MOST the box width, so the long string was truncated
    // to one screenful: it slid out in seconds and the bar sat empty for
    // the rest of the cycle, exactly the fault Glass TV's NewsTicker
    // documents. The scrolling Text is therefore given its full measured
    // width explicitly (see below), and moved with graphicsLayer
    // translationX, the Compose equivalent of Glass TV's setTranslationX.
    val doubledW = remember(doubled) {
        measurer.measure(doubled, textStyle).size.width.toFloat()
    }
    var holderW by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val fits = singleW <= holderW - with(density) { 16.dp.toPx() }
    val speedPx = with(density) { 35.dp.toPx() }
    // Scroll driven by the display's own frame clock (withFrameNanos),
    // not a 16 ms timer: delay(16) fires off-vsync, so steps landed
    // between frames and the motion stuttered on this TV. Each frame
    // moves the text by speed * real frame time (dt clamped to 0.1 s),
    // so it stays smooth even if a frame is late. Speed 35 dp/s, down
    // from 60, at Azuan's request: easier to read, and fewer pixels per
    // frame. The offset wraps by exactly one period, so the loop has
    // no gap.
    var scrollX by remember { mutableStateOf(0f) }
    LaunchedEffect(single, period, speedPx) {
        scrollX = 0f
        android.util.Log.d("UnwindNews", "ticker singleLen=${single.length} singleW=$singleW period=$period holderW=$holderW")
        if (period <= 0f || speedPx <= 0f) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last < 0L) { last = now; return@withFrameNanos }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                scrollX -= speedPx * dt
                if (scrollX <= -period) scrollX += period
            }
        }
    }
    // Nothing to show until the first fetch lands. The return sits after
    // every remember call above, so the hook order never changes.
    if (single.isBlank()) return
    Row(
        modifier.glass(RoundedCornerShape(10.dp)).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BREAKING NEWS  |  ",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            Modifier.weight(1f).fillMaxHeight().clipToBounds()
                .onSizeChanged { holderW = it.width },
        ) {
            if (fits) {
                Text(
                    single,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Text(
                    doubled,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .requiredWidth(with(density) { doubledW.toDp() })
                        .graphicsLayer { translationX = scrollX },
                )
            }
        }
    }
}

// ---------- All apps / pick favorites ----------

@Composable
fun AppGrid(
    apps: List<AppEntry>,
    editing: Boolean,
    favs: Set<String>,
    focusKick: Int,
    nav: Nav,
    onClick: (AppEntry) -> Unit,
) {
    val columns = 7
    LaunchedEffect(apps.size, focusKick) {
        delay(200)
        if (apps.isNotEmpty()) runCatching { nav.content.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = 163.dp, top = 84.dp, end = 40.dp)) {
        Text(
            if (editing) "Press OK on an app to add it to Home or remove it." else "Press OK to open an app.",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                val isFav = editing && app.pkg in favs
                Tile(
                    if (isFav) "\u2713 ${app.label}" else app.label,
                    (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                        .dpad(
                            left = if (i % columns == 0) nav.rail else null,
                            up = if (i < columns) nav.pill else null,
                        ),
                    onClick = { onClick(app) },
                    highlight = isFav,
                    forceName = true,
                ) { d, focused -> AppGlyph(app, d, focused) }
            }
        }
    }
}

// ---------- In-launcher app search ----------

@Composable
fun KeyCap(label: String, w: Dp, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, animationSpec = LiquidSpring, label = "key")
    Box(
        modifier
            .size(w, 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(10.dp), focused, blur = false)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused || active) ui.wp.accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SearchScreen(apps: List<AppEntry>, focusKick: Int, nav: Nav, onLaunch: (AppEntry) -> Unit) {
    val ui = LocalUi.current
    var query by remember { mutableStateOf("") }
    LaunchedEffect(focusKick) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    Row(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp, bottom = 24.dp)) {
        Column(Modifier.width(392.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (query.isEmpty()) "Search your apps" else query,
                    color = Color.White.copy(alpha = if (query.isEmpty()) 0.65f else 1f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            val rows = listOf("abcdefghi", "jklmnopqr", "stuvwxyz0", "123456789")
            rows.forEachIndexed { ri, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { ci, ch ->
                        var m: Modifier = Modifier
                        if (ri == 0 && ci == 0) m = m.focusRequester(nav.content)
                        m = m.dpad(left = if (ci == 0) nav.rail else null, up = if (ri == 0) nav.pill else null)
                        KeyCap(ch.uppercase(), 38.dp, m) { query += ch }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap("Space", 126.dp, Modifier.dpad(left = nav.rail)) { query += " " }
                KeyCap("Delete", 126.dp, Modifier) { query = query.dropLast(1) }
                KeyCap("Clear", 126.dp, Modifier) { query = "" }
            }
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxSize()) {
            if (results.isEmpty()) {
                Text("No apps match \"${query.trim()}\"", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(results, key = { _, a -> a.pkg }) { _, app ->
                        Tile(app.label, Modifier.dpad(up = nav.pill), onClick = { onLaunch(app) }, forceName = true) { d, focused -> AppGlyph(app, d, focused) }
                    }
                }
            }
        }
    }
}

// ---------- Shared bits for the settings pages ----------

@Composable
fun OptionCard(
    selected: Boolean,
    w: Dp,
    h: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, animationSpec = LiquidSpring, label = "option")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(w, h)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .border(
                if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                if (focused) Color.White else if (selected) ui.wp.accent else Color.White.copy(alpha = 0.25f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, sub: String) {
    val ui = LocalUi.current
    Text(title, color = ui.wp.accent, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun ActionCard(label: String, modifier: Modifier = Modifier, w: Dp = 240.dp, onClick: () -> Unit) {
    OptionCard(selected = false, w = w, h = 52.dp, modifier = modifier, onClick = onClick) {
        Box(Modifier.fillMaxSize().glass(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WallCard(
    label: String,
    thumb: ImageBitmap?,
    placeholder: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    Column {
        OptionCard(selected = selected, w = 150.dp, h = 84.dp, modifier = modifier, onClick = onClick) {
            if (thumb != null) {
                Image(thumb, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ui.wp.tint, ui.wp.dark))),
                    contentAlignment = Alignment.Center,
                ) { Text(placeholder, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ui.wp.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = ui.wp.dark, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}

@Composable
private fun GuideStep(n: Int, text: String) {
    val ui = LocalUi.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(ui.wp.accent),
            contentAlignment = Alignment.Center,
        ) { Text("$n", color = ui.wp.dark, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ToggleCard(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    OptionCard(selected = false, w = 260.dp, h = 56.dp, modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier
                    .size(48.dp, 26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) ui.wp.accent else Color.White.copy(alpha = 0.28f)),
            ) {
                Box(
                    Modifier
                        .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (on) ui.wp.dark else Color.White),
                )
            }
        }
    }
}

/** A tiny drawing of the layout: one row fading off to the side, or rows wrapping below. */
@Composable
private fun LayoutGlyph(stacked: Boolean) {
    val ui = LocalUi.current
    val c = ui.wp.accent
    Canvas(Modifier.size(52.dp, 28.dp)) {
        val r = 5.dp.toPx()
        val g = 4.dp.toPx()
        if (stacked) {
            for (row in 0..1) for (col in 0..3) {
                drawCircle(c.copy(alpha = if (row == 0) 1f else 0.6f), r, Offset(r + col * (2 * r + g), r + row * (2 * r + g)))
            }
        } else {
            for (col in 0..3) {
                drawCircle(c.copy(alpha = if (col == 3) 0.35f else 1f), r, Offset(r + col * (2 * r + g), size.height / 2f))
            }
        }
    }
}

// ---------- Preferences ----------

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    focusKick: Int,
    nav: Nav,
    wallVersion: Int,
    wallStatus: String,
    onChange: (LauncherSettings) -> Unit,
    onPickCustom: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onEditGreeting: (DayPart) -> Unit,
    onReplayIntro: () -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var thumbs by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    var customThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var bingThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        thumbs = withContext(Dispatchers.IO) { WALLPAPERS.map { decodeRes(ctx, it.key, 4)?.asImageBitmap() } }
    }
    LaunchedEffect(wallVersion) {
        val pair = withContext(Dispatchers.IO) {
            Pair(
                decodeFileScaled(customFile(ctx), 320, 180)?.asImageBitmap(),
                decodeFileScaled(bingFile(ctx), 320, 180)?.asImageBitmap(),
            )
        }
        customThumb = pair.first
        bingThumb = pair.second
    }
    LaunchedEffect(focusKick, thumbs.size) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }

    val isBing = settings.wallSource == WallSource.Bing
    val isCustom = settings.wallSource == WallSource.Custom
    val isStock = settings.wallSource == WallSource.Stock
    val toRail = Modifier.dpad(left = nav.rail)

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 150.dp, top = 84.dp, end = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 60.dp),
    ) {
        // Wallpaper
        SectionTitle("Wallpaper", "Icons and glass pick up its colors, whatever picture you choose")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WallCard(
                "Daily wallpaper", bingThumb, "Daily", isBing,
                (if (isBing) Modifier.focusRequester(nav.content) else Modifier).dpad(left = nav.rail, up = nav.pill),
            ) { onChange(settings.copy(wallSource = WallSource.Bing)) }
            WallCard(
                "My photo", customThumb, "+ Choose", isCustom,
                (if (isCustom) Modifier.focusRequester(nav.content) else Modifier).dpad(up = nav.pill),
                onPickCustom,
            )
            WALLPAPERS.forEachIndexed { i, w ->
                val sel = isStock && settings.wallpaper == i
                WallCard(
                    w.name, thumbs.getOrNull(i), "", sel,
                    (if (sel) Modifier.focusRequester(nav.content) else Modifier).dpad(up = nav.pill),
                ) { onChange(settings.copy(wallSource = WallSource.Stock, wallpaper = i)) }
            }
        }
        if (isBing && wallStatus.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(wallStatus, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (isCustom) {
            Spacer(Modifier.height(8.dp))
            Text("Select \"My photo\" again to pick a different picture.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))

        // Glass style
        SectionTitle("Glass style", "How panels, the dock and tiles look")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassStyle.values().forEachIndexed { gi, g ->
                OptionCard(
                    selected = settings.glass == g,
                    w = 160.dp,
                    h = 72.dp,
                    modifier = if (gi == 0) toRail else Modifier,
                    onClick = { onChange(settings.copy(glass = g)) },
                ) {
                    Box(Modifier.fillMaxSize().glass(RoundedCornerShape(16.dp), style = g), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(g.blurb, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Icon shape
        SectionTitle("Icon shape", "Applies to the home row and all apps")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconShape.values().forEachIndexed { si, s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OptionCard(
                        selected = settings.shape == s,
                        w = 84.dp,
                        h = 76.dp,
                        modifier = if (si == 0) toRail else Modifier,
                        onClick = { onChange(settings.copy(shape = s)) },
                    ) {
                        Box(Modifier.align(Alignment.Center).size(46.dp).glass(s.toShape()), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Icon style
        SectionTitle("Icon style", "Themed icons are recolored to match the wallpaper")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconStyle.values().forEachIndexed { ii, st ->
                OptionCard(
                    selected = settings.iconStyle == st,
                    w = 200.dp,
                    h = 64.dp,
                    modifier = if (ii == 0) toRail else Modifier,
                    onClick = { onChange(settings.copy(iconStyle = st)) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                        Text(st.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(st.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Home layout
        SectionTitle("Home layout", "How your pinned apps sit on Home")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeLayout.values().forEachIndexed { li, l ->
                OptionCard(
                    selected = settings.homeLayout == l,
                    w = 260.dp,
                    h = 72.dp,
                    modifier = if (li == 0) toRail else Modifier,
                    onClick = { onChange(settings.copy(homeLayout = l)) },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        LayoutGlyph(l == HomeLayout.Stacked)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(l.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(l.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Dock
        SectionTitle("Dock", "Put the first row on a glass bar, in your chosen glass style")
        ToggleCard(if (settings.dock) "Dock on" else "Dock off", settings.dock, toRail) {
            onChange(settings.copy(dock = !settings.dock))
        }
        Spacer(Modifier.height(24.dp))

        // Dock height
        SectionTitle("Dock height", "How close to the bottom of the screen the home row sits")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DockPos.values().forEachIndexed { di, d ->
                OptionCard(
                    selected = settings.dockPos == d,
                    w = 140.dp,
                    h = 56.dp,
                    modifier = if (di == 0) toRail else Modifier,
                    onClick = { onChange(settings.copy(dockPos = d)) },
                ) {
                    Text(
                        d.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // All apps view
        SectionTitle("All apps page", "List every app, or sort them into folders by type (hold OK on an app to move it)")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppsView.values().forEachIndexed { ai, v ->
                OptionCard(
                    selected = settings.appsView == v,
                    w = 260.dp,
                    h = 64.dp,
                    modifier = if (ai == 0) toRail else Modifier,
                    onClick = { onChange(settings.copy(appsView = v)) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 10.dp)) {
                        Text(v.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(v.blurb, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // App names
        SectionTitle("App names", "Show the name under each icon on Home")
        ToggleCard(if (settings.showNames) "Names on" else "Names off", settings.showNames, toRail) {
            onChange(settings.copy(showNames = !settings.showNames))
        }
        Spacer(Modifier.height(24.dp))

        // Prayer times and news ticker
        SectionTitle("Waktu solat", "Prayer times for Putrajaya and the breaking news ticker on Home")
        ToggleCard(if (settings.showPrayer) "Prayer card on" else "Prayer card off", settings.showPrayer, toRail) {
            onChange(settings.copy(showPrayer = !settings.showPrayer))
        }
        Spacer(Modifier.height(14.dp))
        ToggleCard(if (settings.showTicker) "News ticker on" else "News ticker off", settings.showTicker, toRail) {
            onChange(settings.copy(showTicker = !settings.showTicker))
        }
        if (!Settings.canDrawOverlays(ctx)) {
            Spacer(Modifier.height(14.dp))
            ActionCard("Benarkan banner solat", toRail, 240.dp) {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + ctx.packageName),
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Greetings
        SectionTitle("Greetings", "Change the words on Home for each part of the day")
        val nowPart = dayPartFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DayPart.values().forEachIndexed { gi, dp ->
                val g = settings.greetings[dp] ?: DEFAULT_GREETINGS.getValue(dp)
                OptionCard(
                    selected = dp == nowPart,
                    w = 180.dp,
                    h = 84.dp,
                    modifier = if (gi == 0) toRail else Modifier,
                    onClick = { onEditGreeting(dp) },
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)) {
                        Text(
                            if (dp == nowPart) "${dp.label} \u00B7 now" else dp.label,
                            color = ui.wp.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${g.line1} ${g.line2}".trim().ifEmpty { g.top },
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(dp.hours, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Home screen replacement guide
        SectionTitle("Make Unwind your Home screen", "So the Home button on your remote opens Unwind")
        Column(Modifier.width(560.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp)) {
            GuideStep(1, "Press the Home button on your remote.")
            GuideStep(2, "If the TV asks which Home app to use, pick Unwind, then choose Always.")
            GuideStep(3, "No question? Tap the button below and pick Unwind there.")
            Spacer(Modifier.height(6.dp))
            Text(
                "To switch back: pick your TV's original Home app in the same place, or uninstall Unwind.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        ActionCard("Open Home settings", toRail, 240.dp, onOpenHomeSettings)
        Spacer(Modifier.height(24.dp))

        // Welcome screen
        SectionTitle("Welcome screen", "See the intro that shows the first time Unwind opens")
        ActionCard("Replay welcome screen", toRail, 240.dp, onReplayIntro)
    }
}

// ---------- Choosing a picture from the TV ----------

@Composable
private fun GalleryThumb(item: GalleryItem, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { decodeThumb(ctx, item.uri, 480)?.asImageBitmap() }
    }
    OptionCard(selected = false, w = 160.dp, h = 90.dp, modifier = modifier, onClick = onClick) {
        val t = thumb
        if (t != null) {
            Image(t, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun GalleryScreen(
    items: List<GalleryItem>?,
    hasPerm: Boolean,
    nav: Nav,
    onGrant: () -> Unit,
    onBrowse: () -> Unit,
    onPick: (GalleryItem) -> Unit,
) {
    LaunchedEffect(items?.size, hasPerm) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }
    val hasGrid = hasPerm && !items.isNullOrEmpty()
    Column(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp)) {
        Text("Choose a wallpaper", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Pick any picture stored on this TV or on a USB drive.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        when {
            !hasPerm -> {
                Text("Allow Unwind to see the pictures on this TV.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                ActionCard("Allow access", Modifier.focusRequester(nav.content).dpad(left = nav.rail), 240.dp, onGrant)
            }
            items == null -> Text("Loading pictures...", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            items.isEmpty() -> {
                Text(
                    "No pictures found. Copy a JPG or PNG to this TV (or plug in a USB drive), then come back.",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
        if (hasPerm && items != null) {
            Spacer(Modifier.height(10.dp))
            ActionCard(
                "Browse files...",
                (if (!hasGrid) Modifier.focusRequester(nav.content) else Modifier).dpad(left = nav.rail),
                200.dp,
                onBrowse,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (hasGrid && items != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            ) {
                itemsIndexed(items, key = { _, g -> g.uri.toString() }) { i, item ->
                    GalleryThumb(
                        item,
                        if (i == 0) Modifier.focusRequester(nav.content) else Modifier,
                    ) { onPick(item) }
                }
            }
        }
    }
}
