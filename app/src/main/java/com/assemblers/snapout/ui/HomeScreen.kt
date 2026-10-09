package com.assemblers.snapout.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.ai.EngineStatus
import com.assemblers.snapout.core.FeedApps
import com.assemblers.snapout.core.Strictness
import com.assemblers.snapout.core.TranceState
import com.assemblers.snapout.ui.theme.Amber
import com.assemblers.snapout.ui.theme.Coral
import com.assemblers.snapout.ui.theme.Mint

@Composable
fun HomeScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val settings by app.settings.state.collectAsState()
    val snap by app.engine.snapshot.collectAsState()
    val connected by app.engine.serviceConnected.collectAsState()
    val diag by app.engine.diagnostics.collectAsState()
    val ai by app.llm.status.collectAsState()

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SnapOut", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (!settings.onboarded) Onboarding(settings.goal) { goal ->
            app.settings.update { it.copy(goal = goal, onboarded = true) }
        }

        if (!connected) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn on scroll detection", fontWeight = FontWeight.SemiBold)
                Text(
                    "SnapOut uses Android's Accessibility API only to notice scroll timing in feed apps " +
                        "(TikTok, Reels, Shorts…). It cannot read what's on your screen, and nothing leaves the phone.",
                    fontSize = 14.sp,
                )
                Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Open Accessibility settings")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protection", fontWeight = FontWeight.SemiBold)
                    Text(if (connected) "Service running" else "Service off", fontSize = 13.sp, color = if (connected) Mint else Coral)
                }
                Switch(settings.enabled, onCheckedChange = { v -> app.settings.update { it.copy(enabled = v) } })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreDial(snap.score, snap.state)
                Text(
                    snap.feedPackage?.let { "Watching scroll rhythm on ${FeedApps.label(it)}" } ?: "Not in a feed app",
                    fontSize = 13.sp,
                )
                if (snap.feedPackage != null) {
                    Text(
                        "%.1f swipes/min · dwell %.1fs · %.1f min · %d swipes".format(
                            snap.swipesPerMinute, snap.medianDwellMs / 1000.0, snap.sessionMinutes, snap.sessionSwipes,
                        ),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                    val b = snap.breakdown
                    Text(
                        "rate %.2f · dwell %.2f · length %.2f · taps %.2f · dark/late %.2f · lying %.2f".format(
                            b.swipeRate, b.shortDwell, b.sessionLength, b.lowTapRatio, b.darkLate, b.lyingDown,
                        ),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("On-device AI", fontWeight = FontWeight.SemiBold)
                val (line, color) = when (ai.status) {
                    EngineStatus.NO_MODEL -> "No model found. Push a .litertlm file (see README)." to Coral
                    EngineStatus.IDLE -> "${ai.modelName} · ready to load" to Amber
                    EngineStatus.LOADING -> "Loading ${ai.modelName}…" to Amber
                    EngineStatus.READY -> "${ai.modelName} · ${ai.backend} · loaded in ${ai.loadMs} ms" to Mint
                    EngineStatus.ERROR -> "Failed: ${ai.error}" to Coral
                }
                Text(line, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("Inference runs locally with LiteRT-LM. The app has no INTERNET permission.", fontSize = 12.sp)
                OutlinedButton(onClick = { app.llm.warmUp() }, enabled = ai.status == EngineStatus.IDLE || ai.status == EngineStatus.ERROR) {
                    Text("Load model now")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Strictness", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Strictness.entries.forEach { s ->
                        FilterChip(
                            selected = settings.strictness == s,
                            onClick = { app.settings.update { it.copy(strictness = s) } },
                            label = { Text(s.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }
                ToggleRow("Read message aloud", settings.speak) { v -> app.settings.update { it.copy(speak = v) } }
                Text("Goal: ${settings.goal.ifBlank { "—" }}", fontSize = 13.sp)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demo", fontWeight = FontWeight.SemiBold)
                ToggleRow("Demo mode (low thresholds, 5 s trigger)", settings.demoMode) { v -> app.settings.update { it.copy(demoMode = v) } }
                ToggleRow("Pretend it's late & dark", settings.forceNight) { v -> app.settings.update { it.copy(forceNight = v) } }
                Button(onClick = { app.engine.forceTrigger() }, enabled = connected) { Text("Trigger intervention now") }
                Text(
                    "Raw events — scroll ${diag.scrollEvents} · click ${diag.clickEvents} · window ${diag.windowEvents} · last ${diag.lastPackage ?: "-"}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun Onboarding(initial: String, onSave: (String) -> Unit) {
    var goal by remember { mutableStateOf(initial) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What do you want your evenings to be about?", fontWeight = FontWeight.SemiBold)
            Text("Gemma uses this (on your phone only) to make each nudge personal.", fontSize = 13.sp)
            OutlinedTextField(goal, { goal = it }, placeholder = { Text("e.g. be asleep by midnight") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(goal.trim()) }) { Text("Save goal") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(value, onCheckedChange = onChange)
    }
}

@Composable
private fun ScoreDial(score: Int, state: TranceState) {
    val color = when (state) {
        TranceState.FOCUSED -> Mint
        TranceState.DRIFTING -> Amber
        TranceState.ZOMBIE -> Coral
    }
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            drawArc(Color.White.copy(alpha = 0.1f), 135f, 270f, false, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * score / 100f, false, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = color)
            Text(state.label, fontSize = 14.sp)
        }
    }
}
