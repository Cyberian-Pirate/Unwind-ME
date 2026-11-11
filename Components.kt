package com.unwind.launcher

import android.media.tv.TvInputInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.spring
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.SettingsInputComposite
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- D-pad wiring ----------

/**
 * Sends a D-pad press straight to a chosen target. This makes moving between the dock, the top bar
 * and the page content reliable in both directions, instead of depending on screen geometry.
 */
fun Modifier.dpad(
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) {
        false
    } else {
        val target = when (e.key) {
            Key.DirectionLeft -> left
            Key.DirectionRight -> right
            Key.DirectionUp -> up
            Key.DirectionDown -> down
            else -> null
        }
        if (target == null) false else runCatching { target.requestFocus() }.isSuccess
    }
}

// ---------- Focusable building blocks ----------

@Composable
fun FocusBox(
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, animationSpec = LiquidSpring, label = "focusBox")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.background(ui.wp.accent, shape) else Modifier)
            .liquidFocus(shape, focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** [filled] = false keeps the dark "selected" icon color but leaves the background to something else. */
@Composable
fun FocusButton(
    icon: ImageVector,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    FocusBox(size, shape, modifier, selected && filled, onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) ui.wp.dark else Color.White,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
fun Avatar(photo: ImageBitmap?, initial: String, size: Dp) {
    val ui = LocalUi.current
    Box(
        Modifier.size(size).clip(CircleShape).background(ui.wp.accent),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (initial.isNotBlank()) {
            Text(initial, color = ui.wp.dark, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = ui.wp.dark, modifier = Modifier.size(size * 0.66f))
        }
    }
}

// ---------- App tiles ----------

@Composable
fun Tile(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    highlight: Boolean = false,
    onFocus: () -> Unit = {},
    forceName: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.(Dp) -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, animationSpec = LiquidSpring, label = "tile")
    val d = 76.dp
    Column(
        modifier
            .width(d + 10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onLongPress == null) {
                    Modifier
                } else {
                    // Holding OK on the remote counts as a long press. The release after it must not also open the app.
                    Modifier.onPreviewKeyEvent { e ->
                        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (!ok) {
                            false
                        } else if (e.type == KeyEventType.KeyDown) {
                            val repeat = e.nativeKeyEvent.repeatCount
                            if (repeat == 0) longFired = false
                            if (repeat >= 1) {
                                if (!longFired) {
                                    longFired = true
                                    onLongPress()
                                }
                                true
                            } else {
                                false
                            }
                        } else if (e.type == KeyEventType.KeyUp && longFired) {
                            longFired = false
                            true
                        } else {
                            false
                        }
                    }
                },
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(d).glass(ui.settings.shape.toShape(), focused),
            contentAlignment = Alignment.Center,
        ) { content(this, d) }
        if (ui.settings.showNames || forceName) {
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                color = if (highlight) ui.wp.accent else Color.White.copy(alpha = if (focused) 1f else 0.88f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Maps dark pixels to [dark] and bright pixels to [light]: a two-tone version of any picture. */
private fun duotone(light: Color, dark: Color): ColorFilter {
    val lw = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    fun row(l: Float, d: Float) =
        floatArrayOf((l - d) * lw[0], (l - d) * lw[1], (l - d) * lw[2], 0f, d * 255f)
    val values = row(light.red, dark.red) + row(light.green, dark.green) + row(light.blue, dark.blue) +
        floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(values))
}

/**
 * The app's glyph. Themed: a clean single-color logo in the wallpaper's accent. If a logo can't be
 * isolated, the icon is shown as a small two-tone chip so every app still matches the theme.
 */
@Composable
fun BoxScope.AppGlyph(app: AppEntry, d: Dp) {
    val ui = LocalUi.current
    val mask = app.mask
    val themed = ui.settings.iconStyle == IconStyle.Themed
    when {
        themed && mask != null -> Image(
            mask,
            contentDescription = null,
            modifier = Modifier.size(d * 0.58f),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(ui.wp.accent),
            filterQuality = FilterQuality.High,
        )
        themed -> {
            val filter = remember(ui.wp.accent, ui.wp.dark) { duotone(ui.wp.accent, ui.wp.dark) }
            Image(
                app.icon,
                contentDescription = null,
                modifier = Modifier.size(d * 0.58f).clip(RoundedCornerShape(percent = 22)),
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                filterQuality = FilterQuality.High,
            )
        }
        else -> {
            if (app.bg.alpha > 0f) {
                Box(Modifier.fillMaxSize().clip(ui.settings.shape.toShape()).background(app.bg))
            }
            Image(
                app.icon,
                contentDescription = null,
                modifier = Modifier.size(d * 0.62f),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

// ---------- Left rail and top pill ----------

@Composable
fun Rail(
    selected: Int,
    onClick: (Int) -> Unit,
    reqs: List<FocusRequester>,
    toContent: FocusRequester,
    modifier: Modifier,
) {
    val ui = LocalUi.current
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Apps, Icons.Rounded.Tune)
    val btn = 54.dp
    val gap = 10.dp
    val padV = 12.dp
    // the highlight is one blob that slides (and slightly overshoots) to the current page
    val slide by animateFloatAsState(
        selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        label = "railSlide",
    )
    Box(modifier.width(71.dp).glass(RoundedCornerShape(36.dp))) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (padV.toPx() + slide * (btn + gap).toPx()).roundToInt()) }
                .size(btn)
                .drawBehind {
                    val r = CornerRadius(22.dp.toPx())
                    drawRoundRect(ui.wp.accent, cornerRadius = r)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                        cornerRadius = r,
                    )
                },
        )
        Column(
            Modifier.align(Alignment.TopCenter).padding(vertical = padV),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            icons.forEachIndexed { i, icon ->
                FocusButton(
                    icon,
                    btn,
                    RoundedCornerShape(22.dp),
                    modifier = Modifier.focusRequester(reqs[i]).dpad(right = toContent),
                    selected = selected == i,
                    filled = false,
                    onClick = { onClick(i) },
                )
            }
        }
    }
}

@Composable
fun TopPill(
    acct: AcctState?,
    nav: Nav,
    onSearch: () -> Unit,
    onSystem: () -> Unit,
    onWifi: () -> Unit,
    onInputs: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier,
) {
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var time by remember { mutableStateOf(fmt.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = fmt.format(Date())
            delay(15_000)
        }
    }
    val down = Modifier.dpad(down = nav.content)
    Row(
        modifier.height(46.dp).glass(RoundedCornerShape(23.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FocusButton(Icons.Rounded.Search, 34.dp, CircleShape, modifier = Modifier.focusRequester(nav.pill).then(down), onClick = onSearch)
        FocusButton(Icons.Rounded.Settings, 34.dp, CircleShape, modifier = down, onClick = onSystem)
        FocusButton(Icons.Rounded.Wifi, 34.dp, CircleShape, modifier = down, onClick = onWifi)
        FocusButton(Icons.Rounded.SettingsInputHdmi, 34.dp, CircleShape, modifier = down, onClick = onInputs)
        Text(time, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp))
        FocusBox(34.dp, CircleShape, down, onClick = onAccount) {
            Avatar(acct?.photo, acct?.emails?.firstOrNull()?.take(1)?.uppercase() ?: "", 28.dp)
        }
    }
}

// ---------- Popups ----------

@Composable
fun PopupPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(320.dp).glass(RoundedCornerShape(24.dp)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun PopupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    trailing: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .background(ui.wp.accent.copy(alpha = 0.22f), shape)
                        .border(1.dp, ui.wp.accent.copy(alpha = 0.8f), shape)
                } else {
                    Modifier
                },
            )
            .liquidFocus(shape, focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) ui.wp.accent else Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = ui.wp.accent, modifier = Modifier.size(20.dp))
        }
    }
}

private fun signalText(level: Int): String = when {
    level >= -55 -> "Excellent"
    level >= -67 -> "Good"
    level >= -78 -> "Fair"
    else -> "Weak"
}

@Composable
fun WifiPopup(state: WifiState?, locOk: Boolean, focus: FocusRequester, onGrant: () -> Unit, onSettings: () -> Unit) {
    val nets = state?.nets ?: emptyList()
    val footerFocus = !locOk || nets.isEmpty()
    PopupPanel("Wi-Fi") {
        val status = when {
            state == null -> "Looking for networks..."
            state.connectedSsid != null -> "Connected to ${state.connectedSsid}"
            state.ethernet -> "Connected through Ethernet"
            else -> "Not connected"
        }
        Text(status, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (!locOk) {
            PopupRow(
                "Show nearby networks",
                subtitle = "Needs location permission",
                icon = Icons.Rounded.Wifi,
                modifier = Modifier.focusRequester(focus),
                onClick = onGrant,
            )
        } else if (nets.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(nets, key = { _, n -> n.ssid }) { i, n ->
                    val kind = if (n.connected) "Connected" else if (n.secured) "Secured" else "Open"
                    val extra = if (n.band.isBlank()) "" else " · ${n.band}"
                    PopupRow(
                        n.ssid,
                        subtitle = "$kind · ${signalText(n.level)}$extra",
                        icon = Icons.Rounded.Wifi,
                        highlighted = n.connected,
                        trailing = if (n.connected) Icons.Rounded.Check else if (n.secured) Icons.Rounded.Lock else null,
                        modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
                        onClick = onSettings,
                    )
                }
            }
        } else if (state != null) {
            Text("No networks found. Make sure location is turned on.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        PopupRow(
            "Wi-Fi settings",
            icon = Icons.Rounded.Settings,
            modifier = if (footerFocus && locOk) Modifier.focusRequester(focus) else Modifier,
            onClick = onSettings,
        )
    }
}

private fun inputIcon(item: InputItem): ImageVector = when {
    item.label.contains("usb", ignoreCase = true) -> Icons.Rounded.Usb
    item.type == TvInputInfo.TYPE_HDMI -> Icons.Rounded.SettingsInputHdmi
    item.type == TvInputInfo.TYPE_TUNER -> Icons.Rounded.Tv
    item.type == TvInputInfo.TYPE_COMPOSITE -> Icons.Rounded.SettingsInputComposite
    item.type == TvInputInfo.TYPE_COMPONENT -> Icons.Rounded.SettingsInputComponent
    else -> Icons.Rounded.Input
}

@Composable
fun InputsPopup(items: List<InputItem>, focus: FocusRequester, onPick: (InputItem) -> Unit, onSettings: () -> Unit) {
    PopupPanel("Inputs") {
        if (items.isEmpty()) {
            Text(
                "No inputs found. Use the INPUT or SOURCE button on your remote.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, item ->
                PopupRow(
                    item.label,
                    icon = inputIcon(item),
                    modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
                    onClick = { onPick(item) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        PopupRow(
            "TV settings",
            icon = Icons.Rounded.Settings,
            modifier = if (items.isEmpty()) Modifier.focusRequester(focus) else Modifier,
            onClick = onSettings,
        )
    }
}

@Composable
fun AccountPopup(acct: AcctState?, accOk: Boolean, focus: FocusRequester, onGrant: () -> Unit, onManage: () -> Unit) {
    val emails = acct?.emails ?: emptyList()
    PopupPanel("Account") {
        if (emails.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(acct?.photo, emails[0].take(1).uppercase(), 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(acct?.name ?: "Google account", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(emails[0], color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (emails.size > 1) {
                Spacer(Modifier.height(10.dp))
                for (e in emails.drop(1)) {
                    Text(e, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            PopupRow("Manage accounts", icon = Icons.Rounded.Settings, modifier = Modifier.focusRequester(focus), onClick = onManage)
        } else if (!accOk) {
            Text("Allow account access to show who is signed in.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            PopupRow("Allow access", icon = Icons.Rounded.Person, modifier = Modifier.focusRequester(focus), onClick = onGrant)
        } else {
            Text("No Google account found on this TV.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            PopupRow("Account settings", icon = Icons.Rounded.Settings, modifier = Modifier.focusRequester(focus), onClick = onManage)
        }
    }
}

/** Full-screen layer that holds the popup under the top pill. Tapping outside dismisses it. */
@Composable
fun PopupLayer(popup: Popup, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 24.dp)
                .pointerInput(popup) { detectTapGestures { } },
        ) { content() }
    }
}
