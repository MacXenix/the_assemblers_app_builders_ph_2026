package com.assemblers.snapout.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.assemblers.snapout.core.ScoreBreakdown
import com.assemblers.snapout.core.Strictness
import com.assemblers.snapout.core.TranceState
import com.assemblers.snapout.service.NudgeNotifier
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
    val version by app.db.version.collectAsState()
    val interventions = remember(version) { app.db.interventions(500) }
    val sessions = remember(version) { app.db.sessions(500) }
    val summary = remember(version) { com.assemblers.snapout.core.UsageSummary.from(sessions, interventions, System.currentTimeMillis()) }

    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SnapOut", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Attention Coach & Doomscroll Detector", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))

        Spacer(Modifier.height(4.dp))

        // 1. Focus Score Gauge / Trance Meter at the very top
        ScoreDial(snap.score, snap.state)

        Text(
            snap.feedPackage?.let { "Watching scroll rhythm on ${FeedApps.label(it)}" } ?: "Not currently scrolling in a feed app",
            fontSize = 13.sp,
            color = if (snap.feedPackage != null) Mint else Color.White.copy(alpha = 0.7f),
        )

        // 2. Monitoring switch card directly below the meter
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monitoring", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (connected && settings.enabled) "Active — watching for mindless scrolls"
                        else if (!settings.enabled) "Monitoring paused"
                        else "Service off — enable in Accessibility",
                        fontSize = 13.sp,
                        color = if (connected && settings.enabled) Mint else Coral,
                    )
                }
                Switch(settings.enabled, onCheckedChange = { v -> app.settings.update { it.copy(enabled = v) } })
            }
        }

        // 3. Followed by Goal Card (tied directly to on-device AI)
        if (!settings.onboarded) Onboarding(settings.goal) { goal ->
            app.settings.update { it.copy(goal = goal, onboarded = true) }
        } else GoalCard(settings.goal) { goal -> app.settings.update { it.copy(goal = goal) } }

        if (!connected) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn on scroll detection", fontWeight = FontWeight.SemiBold)
                Text(
                    "SnapOut uses Android's Accessibility API only to measure scroll cadence and pauses " +
                        "(TikTok, Reels, Shorts…). It cannot read what's on your screen, and nothing leaves your phone.",
                    fontSize = 13.sp,
                )
                Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Open Accessibility settings")
                }
            }
        }

        if (!NudgeNotifier.canPost(ctx)) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Allow notifications", fontWeight = FontWeight.SemiBold)
                Text("Nudges arrive as gentle notifications. Without this permission you won't see them.", fontSize = 13.sp)
                Button(onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                }) { Text("Open notification settings") }
            }
        }

        // 4. Quick Stats Row (including Feed Mileage from ZombieThumb)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val liveMeters = if (snap.feedPackage != null && snap.sessionDistanceMeters > 0) {
                summary.totalMeters + snap.sessionDistanceMeters.toInt()
            } else {
                summary.totalMeters
            }
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = "$liveMeters m",
                label = "Feed mileage",
                color = Mint,
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = "${summary.totalMinutes} m",
                label = "Feed time",
                color = Amber,
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = "${summary.totalSwipes}",
                label = "Swipes",
                color = Color(0xFF60A5FA),
            )
            QuickStatCard(
                modifier = Modifier.weight(1f),
                value = "${summary.nudges}",
                label = "Nudges",
                color = Coral,
            )
        }

        // 5. Dropdown toggle for breakdown info
        Card(
            Modifier.fillMaxWidth().clickable { showDetails = !showDetails },
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showDetails) "Hide score breakdown" else "View score breakdown & physics",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber,
                    )
                    Text(if (showDetails) "▲" else "▼", fontSize = 12.sp, color = Amber)
                }
                if (showDetails) {
                    Spacer(Modifier.height(10.dp))
                    if (snap.feedPackage != null) {
                        Text(
                            "%.1f swipes/min · dwell %.1fs · %.1f min · %d swipes".format(
                                snap.swipesPerMinute, snap.medianDwellMs / 1000.0, snap.sessionMinutes, snap.sessionSwipes,
                            ),
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    ScoreBreakdownList(snap.breakdown, settings.demoMode)
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color,
) {
    Card(modifier) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Onboarding(initial: String, onSave: (String) -> Unit) {
    var goal by remember { mutableStateOf(initial) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What do you want your evenings to be about?", fontWeight = FontWeight.SemiBold)
            Text("The on-device AI uses this (on your phone only) to make each nudge personal.", fontSize = 13.sp)
            OutlinedTextField(goal, { goal = it }, placeholder = { Text("e.g. be asleep by midnight") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(goal.trim()) }) { Text("Save goal") }
        }
    }
}

@Composable
private fun GoalCard(goal: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(goal) { mutableStateOf(goal) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your goal", fontWeight = FontWeight.SemiBold)
            if (editing) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("e.g. be asleep by midnight") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(text.trim()); editing = false }) { Text("Save") }
                    OutlinedButton(onClick = { text = goal; editing = false }) { Text("Cancel") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.ifBlank { "—" }, Modifier.weight(1f), fontSize = 14.sp)
                    OutlinedButton(onClick = { editing = true }) { Text("Edit goal") }
                }
            }
        }
    }
}

private data class Metric(val name: String, val value: Double, val weight: Int, val help: String, val behaviour: Boolean)

@Composable
private fun ScoreBreakdownList(b: ScoreBreakdown, demo: Boolean) {
    var open by remember { mutableStateOf<String?>(null) }
    val metrics = listOf(
        Metric("Swipe speed", b.swipeRate, 25, if (demo) "Swipes in the last minute. 2/min scores 0, 10/min or more scores full (demo thresholds)."
            else "Swipes in the last minute. 6/min scores 0, 20/min or more scores full.", true),
        Metric("Short views", b.shortDwell, 20, "Typical time on each video before swiping. 20 s or more scores 0, 4 s or less scores full.", true),
        Metric("Session length", b.sessionLength, 20, if (demo) "Minutes in this feed without leaving. Full after 1 min (demo)."
            else "Minutes in this feed without leaving. Full after 20 min.", true),
        Metric("Few taps", b.lowTapRatio, 10, "Passive watching: taps (like, comment, open) compared with swipes. Tapping 30% of the time or more scores 0, 5% or less scores full.", true),
        Metric("Dark + late", b.darkLate, 15, "Light sensor under 10 lux and/or between 22:00 and 05:00. One of them = half, both = full." +
            if (demo) " Not counted in demo mode." else "", false),
        Metric("Lying down", b.lyingDown, 10, "Gravity sensor shows the phone held flat or overhead, like in bed." +
            if (demo) " Not counted in demo mode." else "", false),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Why this score (tap a row)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        metrics.forEach { m ->
            val counted = m.behaviour || !demo
            Column(Modifier.fillMaxWidth().clickable { open = if (open == m.name) null else m.name }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, Modifier.weight(1f), fontSize = 13.sp, color = if (counted) Color.White else Color.White.copy(alpha = 0.4f))
                    LinearProgressIndicator(progress = { m.value.toFloat() }, modifier = Modifier.weight(1f).height(6.dp), color = Amber)
                    Text(
                        " %2d/%d".format((m.value * m.weight).toInt(), m.weight),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
                if (open == m.name) Text(m.help, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
        if (demo) Text("Demo mode scales the four behaviour rows up to 100.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
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
