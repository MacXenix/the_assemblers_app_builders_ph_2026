package com.assemblers.snapout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.ai.AiStatus
import com.assemblers.snapout.ai.EngineStatus
import com.assemblers.snapout.ai.Reframe
import com.assemblers.snapout.ai.ReframeContext
import com.assemblers.snapout.core.Strictness
import com.assemblers.snapout.core.TranceSnapshot
import com.assemblers.snapout.ui.theme.Amber
import com.assemblers.snapout.ui.theme.Mint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun InterventionScreen(
    snap: TranceSnapshot,
    context: ReframeContext,
    strictness: Strictness,
    reframeFlow: StateFlow<Reframe>,
    aiStatusFlow: StateFlow<AiStatus>,
    onReframeDone: (Reframe) -> Unit,
    onGoHome: () -> Unit,
    onContinue: () -> Unit,
) {
    val reframe by reframeFlow.collectAsState()
    val ai by aiStatusFlow.collectAsState()
    val breathSeconds = if (strictness == Strictness.GENTLE) 0 else 10
    val holdMs = when (strictness) {
        Strictness.GENTLE -> 1_500L
        Strictness.BALANCED -> 3_000L
        Strictness.STRICT -> 5_000L
    }
    var breathLeft by remember { mutableIntStateOf(breathSeconds) }
    var spoken by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (breathLeft > 0) {
            delay(1_000)
            breathLeft--
        }
    }
    LaunchedEffect(reframe.done) {
        if (reframe.done && !spoken) {
            spoken = true
            onReframeDone(reframe)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20B1020))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Stage 1 — awareness mirror (instant, no AI needed)
            Text("Pause.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Mint)
            Text(
                buildString {
                    append("${context.swipes} swipes · ${context.minutes} min on ${context.app}")
                    if (snap.isDark) append(" · lights off")
                    append(" · ${context.time}")
                },
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            // Stage 2 — personalized reframe (on-device Gemma, streamed)
            Text(
                reframe.text.ifBlank { "…" },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )
            AiPanel(reframe, ai)

            // Stage 3 — breathing pause
            if (breathSeconds > 0) BreathingCircle(breathLeft)

            // Stage 4 — friction: leaving is easy, continuing takes effort
            AnimatedVisibility(visible = breathLeft == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("I'm done — take me out", fontWeight = FontWeight.SemiBold)
                    }
                    HoldToContinue(holdMs, onContinue)
                }
            }
        }
    }
}

@Composable
private fun AiPanel(reframe: Reframe, ai: AiStatus) {
    var expanded by remember { mutableStateOf(false) }
    val (label, color) = when (reframe.source) {
        Reframe.Source.GEMMA -> "● ${ai.modelName?.substringBefore('.') ?: "Gemma"} · on-device · ${ai.backend ?: ""}" to Mint
        Reframe.Source.FALLBACK -> "○ Built-in message (model ${statusWord(ai.status)})" to Amber
        Reframe.Source.PENDING -> "◌ On-device model thinking…" to Mint
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Text(label, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        val metrics = listOfNotNull(
            reframe.ttftMs?.let { "first token ${it} ms" },
            reframe.tokensPerSec?.let { "≈${"%.0f".format(it)} tok/s" },
            "no network",
        ).joinToString(" · ")
        Text(metrics, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text("Context given to the model:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(reframe.contextJson, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        } else {
            Text("tap to see what the model saw", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
        }
    }
}

private fun statusWord(s: EngineStatus) = when (s) {
    EngineStatus.NO_MODEL -> "not installed"
    EngineStatus.LOADING -> "still loading"
    EngineStatus.ERROR -> "failed to load"
    else -> "unavailable"
}

@Composable
private fun BreathingCircle(secondsLeft: Int) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        while (true) {
            scale.animateTo(1f, tween(4_000, easing = LinearEasing))
            scale.animateTo(0.6f, tween(4_000, easing = LinearEasing))
        }
    }
    val inhaling = scale.targetValue == 1f
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(200.dp)
                .scale(scale.value)
                .clip(CircleShape)
                .background(Mint.copy(alpha = 0.25f)),
        )
        Text(
            if (secondsLeft > 0) "${if (inhaling) "Breathe in" else "Breathe out"}\n$secondsLeft" else "✓",
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun HoldToContinue(holdMs: Long, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = System.currentTimeMillis()
                    var finished = false
                    kotlinx.coroutines.coroutineScope {
                        val job = launch {
                            while (true) {
                                progress = ((System.currentTimeMillis() - start).toFloat() / holdMs).coerceAtMost(1f)
                                if (progress >= 1f) {
                                    finished = true
                                    onDone()
                                    break
                                }
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                    if (!finished) progress = 0f
                })
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Hold ${holdMs / 1000.0}s to keep scrolling", color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Amber)
    }
}
