package com.unwind.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val MAX_SMALL = 30
private const val MAX_BIG = 20

/**
 * The greeting as it appears on Home. [scale] shrinks it for the live preview in the editor,
 * so what you see while editing is exactly what Home will show.
 * A blank line is simply left out.
 */
@Composable
fun GreetingText(g: Greeting, scale: Float = 1f) {
    val ui = LocalUi.current
    val shadow = TextStyle(shadow = Shadow(Color(0x66000000), blurRadius = 14f))
    Column {
        if (g.top.isNotBlank()) {
            Text(g.top, color = Color.White.copy(alpha = 0.92f), fontSize = (15f * scale).sp, style = shadow)
            Spacer(Modifier.height((6f * scale).dp))
        }
        if (g.line1.isNotBlank()) {
            Text(
                g.line1,
                color = Color.White,
                fontSize = (54f * scale).sp,
                lineHeight = (56f * scale).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-1f * scale).sp,
                style = shadow,
            )
        }
        if (g.line2.isNotBlank()) {
            Text(
                g.line2,
                color = ui.wp.accent,
                fontSize = (54f * scale).sp,
                lineHeight = (56f * scale).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-1f * scale).sp,
                style = shadow,
            )
        }
    }
}

@Composable
private fun FieldCard(
    label: String,
    hint: String,
    value: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    OptionCard(selected = selected, w = 300.dp, h = 56.dp, modifier = modifier, onClick = onClick) {
        Box(
            Modifier.fillMaxSize().glass(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text(
                    label,
                    color = if (selected) ui.wp.accent else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (value.isEmpty()) hint else if (selected) "$value|" else value,
                    color = Color.White.copy(alpha = if (value.isEmpty()) 0.5f else 1f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Edit the three lines shown on Home for one part of the day, with an on-screen keyboard. */
@Composable
fun GreetingEditor(
    part: DayPart,
    initial: Greeting,
    nav: Nav,
    onSave: (Greeting) -> Unit,
    onCancel: () -> Unit,
) {
    var small by remember { mutableStateOf(initial.top) }
    var big1 by remember { mutableStateOf(initial.line1) }
    var big2 by remember { mutableStateOf(initial.line2) }
    var active by remember { mutableIntStateOf(0) }
    var shift by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { nav.content.requestFocus() }
    }

    fun valueOf(i: Int): String = when (i) {
        0 -> small
        1 -> big1
        else -> big2
    }

    fun setValue(i: Int, v: String) {
        when (i) {
            0 -> small = v
            1 -> big1 = v
            else -> big2 = v
        }
    }

    fun type(ch: String) {
        val cur = valueOf(active)
        val max = if (active == 0) MAX_SMALL else MAX_BIG
        if (cur.length < max) setValue(active, cur + (if (shift) ch.uppercase() else ch))
        shift = false
    }

    val allBlank = small.isBlank() && big1.isBlank() && big2.isBlank()
    val labels = listOf("Small line", "Headline", "Highlighted line")
    val hints = listOf("e.g. Good morning", "e.g. Rise and", "e.g. shine")

    Column(Modifier.fillMaxSize().padding(start = 150.dp, top = 84.dp, end = 24.dp)) {
        Text("Edit ${part.label.lowercase()} greeting", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Shows on Home from ${part.hours}. Pick a line, then type with the keyboard.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Left: the three lines, then Save / Reset / Cancel
            Column {
                for (i in 0..2) {
                    FieldCard(
                        label = labels[i],
                        hint = hints[i],
                        value = valueOf(i),
                        selected = active == i,
                        modifier = (if (i == 0) Modifier.focusRequester(nav.content) else Modifier)
                            .dpad(left = nav.rail, up = if (i == 0) nav.pill else null)
                            .onFocusChanged { if (it.isFocused) active = i },
                        onClick = { active = i },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("Save", Modifier.dpad(left = nav.rail), 92.dp) {
                        if (!allBlank) onSave(Greeting(small.trim(), big1.trim(), big2.trim()))
                    }
                    ActionCard("Reset", Modifier, 92.dp) {
                        val d = DEFAULT_GREETINGS.getValue(part)
                        small = d.top
                        big1 = d.line1
                        big2 = d.line2
                    }
                    ActionCard("Cancel", Modifier, 92.dp, onCancel)
                }
                if (allBlank) {
                    Spacer(Modifier.height(8.dp))
                    Text("Add at least one line to save.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }
            // Right: live preview and keyboard
            Column {
                Box(
                    Modifier.width(446.dp).height(104.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { GreetingText(Greeting(small, big1, big2), 0.45f) }
                Spacer(Modifier.height(12.dp))
                val rows = listOf("abcdefghijk", "lmnopqrstuv", "wxyz'.,!?-&", "1234567890:")
                rows.forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { ch ->
                            val shown = if (shift) ch.uppercase() else ch.toString()
                            KeyCap(shown, 36.dp, if (ri == 0) Modifier.dpad(up = nav.pill) else Modifier) { type(ch.toString()) }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    KeyCap("Shift", 90.dp, Modifier, active = shift) { shift = !shift }
                    KeyCap("Space", 150.dp, Modifier) { type(" ") }
                    KeyCap("Delete", 95.dp, Modifier) { setValue(active, valueOf(active).dropLast(1)) }
                    KeyCap("Clear", 95.dp, Modifier) { setValue(active, "") }
                }
            }
        }
    }
}
