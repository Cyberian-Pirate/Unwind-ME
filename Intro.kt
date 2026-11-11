package com.unwind.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Fades and slides its content in after [delayMs]. The space is reserved from the start, so nothing jumps around. */
@Composable
private fun Reveal(delayMs: Long, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        show = true
    }
    val a by animateFloatAsState(if (show) 1f else 0f, animationSpec = tween(900), label = "reveal")
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 22.dp.toPx() }) { content() }
}

/** The leaf mark, drawn as a vector so it can take its color from the wallpaper. */
@Composable
private fun LeafMark() {
    val ui = LocalUi.current
    val accent = ui.wp.accent
    val drift by rememberInfiniteTransition(label = "leaf").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "leafDrift",
    )
    Canvas(Modifier.size(78.dp, 90.dp).graphicsLayer { translationY = (drift - 0.5f) * 8.dp.toPx() }) {
        val w = size.width
        val h = size.height
        val leaf = Path().apply {
            moveTo(w * 0.12f, h * 0.95f)
            cubicTo(w * 0.02f, h * 0.45f, w * 0.40f, h * 0.05f, w * 0.98f, h * 0.02f)
            cubicTo(w * 1.00f, h * 0.55f, w * 0.62f, h * 0.98f, w * 0.12f, h * 0.95f)
            close()
        }
        drawPath(
            leaf,
            Brush.linearGradient(
                listOf(lerp(accent, Color.White, 0.30f), accent),
                start = Offset(w, 0f),
                end = Offset(0f, h),
            ),
        )
        val vein = Path().apply {
            moveTo(w * 0.15f, h * 0.92f)
            cubicTo(w * 0.30f, h * 0.55f, w * 0.55f, h * 0.30f, w * 0.86f, h * 0.14f)
        }
        drawPath(vein, Color(0x55102010), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** The Indian flag, drawn by hand so it looks the same on every TV (no emoji font needed). */
@Composable
private fun IndiaFlag() {
    Canvas(Modifier.size(30.dp, 20.dp).clip(RoundedCornerShape(3.dp))) {
        val band = size.height / 3f
        drawRect(Color(0xFFFF9933), Offset(0f, 0f), Size(size.width, band))
        drawRect(Color.White, Offset(0f, band), Size(size.width, band))
        drawRect(Color(0xFF138808), Offset(0f, band * 2f), Size(size.width, band))
        val c = center
        val r = band * 0.42f
        val navy = Color(0xFF000080)
        drawCircle(navy, r, c, style = Stroke(width = 0.8.dp.toPx()))
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0)
            drawLine(
                navy,
                c,
                Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                strokeWidth = 0.4.dp.toPx(),
            )
        }
    }
}

@Composable
private fun IntroBlock(label: String, body: String) {
    val ui = LocalUi.current
    Text(label.uppercase(), color = ui.wp.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
    Spacer(Modifier.height(4.dp))
    Text(body, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 19.sp)
}

/** The welcome screen shown the first time Unwind opens. OK on "Get started" (or Back) closes it. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val ui = LocalUi.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focus.requestFocus() }
    }
    BackHandler(enabled = true) { onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.78f))))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(760.dp)
                .glass(RoundedCornerShape(36.dp))
                .padding(horizontal = 40.dp, vertical = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left: the mark, the name and a one-line promise
                Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Reveal(200) { LeafMark() }
                    Spacer(Modifier.height(6.dp))
                    Reveal(700) {
                        Text(
                            "unwind",
                            color = Color(0xFFF7F5E8),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp,
                        )
                    }
                    Reveal(1100) {
                        Text("A calmer home screen for your TV", color = ui.wp.accent, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Box(Modifier.width(1.dp).height(150.dp).background(Color.White.copy(alpha = 0.22f)))
                Spacer(Modifier.width(24.dp))
                // Right: what it is, who it's for
                Column(Modifier.weight(1f)) {
                    Reveal(1500) {
                        Column {
                            IntroBlock(
                                "What it is",
                                "Your wallpaper, your apps and a little glass. No rows of recommendations, no ads, nothing shouting for your attention. Just the things you actually open.",
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Reveal(2100) {
                        Column {
                            IntroBlock(
                                "Who it's for",
                                "Anyone who turns the TV on to relax, not to scroll. If you open the same few apps every night, say YouTube and Netflix, they sit right here, one click away.",
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Reveal(2800, Modifier.weight(1f)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Built with Love, and some coding here and there", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("by Hazir from", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            IndiaFlag()
                        }
                    }
                }
                Reveal(3300) {
                    ActionCard("Get started", Modifier.focusRequester(focus), 160.dp, onDone)
                }
            }
        }
    }
}
