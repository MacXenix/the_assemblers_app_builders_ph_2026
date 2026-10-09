package com.assemblers.snapout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.ai.PromptBuilder
import com.assemblers.snapout.core.RuleInsights
import com.assemblers.snapout.core.SampleData
import com.assemblers.snapout.core.UsageSummary
import com.assemblers.snapout.core.render
import com.assemblers.snapout.ui.theme.Amber
import com.assemblers.snapout.ui.theme.Coral
import com.assemblers.snapout.ui.theme.Mint
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.FilterChip
import com.assemblers.snapout.ai.EngineStatus

private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
fun InsightsScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val version by app.db.version.collectAsState()
    val settings by app.settings.state.collectAsState()
    val run by app.llm.insightRun.collectAsState()
    val ai by app.llm.status.collectAsState()
    val interventions = remember(version) { app.db.interventions(500) }
    val sessions = remember(version) { app.db.sessions(500) }
    val summary = remember(version) { UsageSummary.from(sessions, interventions, System.currentTimeMillis()) }

    var showAllNudges by remember { mutableStateOf(false) }
    var showAllSessions by remember { mutableStateOf(false) }

    val displayedNudges = if (showAllNudges) interventions else interventions.take(5)
    val displayedSessions = if (showAllSessions) sessions else sessions.take(5)

    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 20.dp)) {
                Text("Insights & On-Device AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Last ${summary.days} days telemetry · 100% on-device", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // 1. On-Device AI Model Control Card
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local AI Model", fontWeight = FontWeight.SemiBold)
                    val (line, color) = when (ai.status) {
                        EngineStatus.NO_MODEL -> "No model loaded. Place .litertlm file in app storage." to Coral
                        EngineStatus.IDLE -> "${ai.modelName} · ready to load" to Amber
                        EngineStatus.LOADING -> "Loading ${ai.modelName}…" to Amber
                        EngineStatus.READY -> "${ai.modelName} · ${ai.backend} · loaded in ${ai.loadMs} ms" to Mint
                        EngineStatus.ERROR -> "Error: ${ai.error}" to Coral
                    }
                    Text(line, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text("100% private on-device LLM (LiteRT-LM). Zero network access.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))

                    val models = remember(ai) { app.llm.modelCandidates() }
                    if (models.size > 1) {
                        Text("Available models:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            models.forEach { f ->
                                FilterChip(
                                    selected = f.name == ai.modelName,
                                    onClick = {
                                        app.settings.update { it.copy(modelFile = f.name) }
                                        app.llm.switchModel()
                                    },
                                    label = { Text("${f.name.removeSuffix(".litertlm")} (${f.length() / 1_000_000} MB)", fontSize = 11.sp) },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { app.llm.warmUp() }, enabled = ai.status == EngineStatus.IDLE || ai.status == EngineStatus.ERROR) {
                            Text("Load model now")
                        }
                        OutlinedButton(onClick = { app.llm.refreshModelPresence() }) { Text("Rescan") }
                    }
                }
            }
        }

        if (summary.empty) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No feed sessions logged yet.", fontWeight = FontWeight.SemiBold)
                        Text("Scroll in TikTok, YouTube or Instagram with SnapOut on, or tap below to load a sample week for full stats.", fontSize = 13.sp)
                        OutlinedButton(onClick = { SampleData.week(System.currentTimeMillis()).let { (s, i) -> app.db.insertSample(s, i) } }) {
                            Text("Load sample week (demo)")
                        }
                    }
                }
            }
        } else {
            item { StatsCard(summary, sessions, interventions) }
            item {
                AnalysisCard(
                    stored = settings.insights,
                    by = settings.insightsBy,
                    at = settings.insightsAt,
                    running = run.running,
                    partial = run.partial,
                    summary = summary,
                ) {
                    app.llm.analyze(summary.toJson(), settings.goal) { items, model ->
                        val text = items?.render() ?: RuleInsights.from(summary).render()
                        val by = if (items != null) model?.removeSuffix(".litertlm") ?: "on-device model" else "rules"
                        app.settings.update { it.copy(insights = text, insightsAt = System.currentTimeMillis(), insightsBy = by) }
                    }
                }
            }
        }

        item { ChatCard(app, summary, settings.goal) }

        // Nudges Section with View All toggle
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent Nudges (${interventions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (interventions.size > 5) {
                    Text(
                        if (showAllNudges) "Show less" else "See all (${interventions.size})",
                        fontSize = 12.sp,
                        color = Amber,
                        modifier = Modifier.clickable { showAllNudges = !showAllNudges },
                    )
                }
            }
        }

        if (interventions.isEmpty()) item { Text("No nudges triggered yet.", fontSize = 13.sp, color = Color.Gray) }
        items(displayedNudges, key = { "i${it.id}" }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(i.at))} · ${i.app} · score ${i.score}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(2.dp))
                    Text(i.text.ifBlank { "—" }, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${if (i.source == "GEMMA") "On-device AI" else "Built-in"}${i.ttftMs?.let { " · $it ms" } ?: ""} · ${outcomeLabel(i.outcome)}",
                        fontSize = 11.sp, color = Color.Gray,
                    )
                }
            }
        }

        // Feed Sessions Section with View All toggle
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Feed Sessions (${sessions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (sessions.size > 5) {
                    Text(
                        if (showAllSessions) "Show less" else "See all (${sessions.size})",
                        fontSize = 12.sp,
                        color = Amber,
                        modifier = Modifier.clickable { showAllSessions = !showAllSessions },
                    )
                }
            }
        }

        if (sessions.isEmpty()) item { Text("No feed sessions recorded yet.", fontSize = 13.sp, color = Color.Gray) }
        items(displayedSessions, key = { "s${it.id}" }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(s.startedAt))} · ${s.app}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%d min · %d swipes · %d taps · %.1f s/video · peak %d%s%s".format(
                            ((s.endedAt - s.startedAt) / 60_000).toInt(), s.swipes, s.taps, s.avgDwellMs / 1000.0, s.peakScore,
                            if (s.late) " · late" else "", if (s.intervened) " · nudged" else "",
                        ),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun outcomeLabel(o: String?) = when (o) {
    "went_home" -> "left the feed"
    "snoozed" -> "snoozed"
    "dismissed" -> "swiped away"
    "continued" -> "kept scrolling"
    else -> "no action"
}

@Composable
private fun StatsCard(
    overall: UsageSummary,
    allSessions: List<com.assemblers.snapout.data.FeedSession>,
    allNudges: List<com.assemblers.snapout.data.Intervention>,
) {
    var selectedApp by remember { mutableStateOf<String?>(null) } // null = All Apps Average

    // Compute stats for selected app or overall
    val targetSessions = remember(selectedApp, allSessions) {
        if (selectedApp == null) allSessions else allSessions.filter { it.app.equals(selectedApp, ignoreCase = true) }
    }
    val targetNudges = remember(selectedApp, allNudges) {
        if (selectedApp == null) allNudges else allNudges.filter { it.app.equals(selectedApp, ignoreCase = true) }
    }
    val targetSummary = remember(targetSessions, targetNudges) {
        if (selectedApp == null) overall else UsageSummary.from(targetSessions, targetNudges, System.currentTimeMillis())
    }

    val chartColors = listOf(Mint, Amber, Coral, Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedApp == null) "Dashboard: All Apps Average" else "Dashboard: $selectedApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                if (selectedApp != null) {
                    Text(
                        "Show All Apps",
                        fontSize = 12.sp,
                        color = Amber,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { selectedApp = null },
                    )
                }
            }

            // App Filter Chips
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedApp == null,
                    onClick = { selectedApp = null },
                    label = { Text("All Apps (Avg)") },
                )
                overall.apps.forEach { a ->
                    FilterChip(
                        selected = selectedApp == a.app,
                        onClick = { selectedApp = if (selectedApp == a.app) null else a.app },
                        label = { Text("${a.app} (${a.minutes}m)") },
                    )
                }
            }

            // Visual Chart: Circular Distribution (Donut Chart) + Bar Comparison
            if (overall.apps.isNotEmpty()) {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Time Distribution Chart", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            // Donut Chart
                            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(90.dp)) {
                                    val strokeWidth = 14.dp.toPx()
                                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                                    var startAngle = -90f
                                    val totalMin = overall.totalMinutes.coerceAtLeast(1)

                                    overall.apps.forEachIndexed { idx, app ->
                                        val sweep = (app.minutes.toFloat() / totalMin) * 360f
                                        val color = chartColors[idx % chartColors.size]
                                        drawArc(
                                            color = color,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                        )
                                        startAngle += sweep
                                    }
                                }
                                Text(
                                    "${targetSummary.totalMinutes}m",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Mint,
                                )
                            }

                            // Legend & Quick Bars
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                overall.apps.take(4).forEachIndexed { idx, app ->
                                    val color = chartColors[idx % chartColors.size]
                                    val pct = if (overall.totalMinutes == 0) 0 else (100 * app.minutes / overall.totalMinutes)
                                    Row(
                                        Modifier.fillMaxWidth().clickable { selectedApp = app.app },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(8.dp, 8.dp).padding(end = 4.dp), contentAlignment = Alignment.Center) {
                                            Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(app.app, Modifier.weight(1f), fontSize = 11.sp, color = if (selectedApp == app.app) Amber else Color.White)
                                        Text("$pct%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Key Telemetry Metrics Grid for Selected App or All
            Row {
                Stat("${targetSummary.totalMeters} m", "feed mileage", Modifier.weight(1f))
                Stat("${targetSummary.sessions}", "sessions", Modifier.weight(1f))
                Stat("${targetSummary.totalMinutes} min", "total time", Modifier.weight(1f))
            }
            Row {
                Stat("${targetSummary.longestSessionMinutes} min", "longest session", Modifier.weight(1f))
                Stat("${targetSummary.totalSwipes}", "swipes", Modifier.weight(1f))
                Stat("%.1f s".format(targetSummary.avgSecondsPerVideo), "per video", Modifier.weight(1f))
            }
            Row {
                Stat("${targetSummary.lateNightPct}%", "after 22:00", Modifier.weight(1f))
                Stat("${targetSummary.lyingPct}%", "lying down", Modifier.weight(1f))
                Stat(targetSummary.peakHour?.let { "%02d:00".format(it) } ?: "—", "peak hour", Modifier.weight(1f))
            }

            // Bar Breakdown per App
            Text("Feed Time Breakdown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            overall.apps.forEachIndexed { idx, a ->
                val isCur = selectedApp == a.app
                Column(
                    Modifier.fillMaxWidth().clickable { selectedApp = if (isCur) null else a.app }.padding(vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            a.app + if (isCur) " (selected)" else "",
                            Modifier.weight(0.9f),
                            fontSize = 13.sp,
                            color = if (isCur) Amber else Color.White,
                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                        )
                        LinearProgressIndicator(
                            progress = { if (overall.totalMinutes == 0) 0f else a.minutes.toFloat() / overall.totalMinutes },
                            modifier = Modifier.weight(1.4f).height(7.dp),
                            color = chartColors[idx % chartColors.size],
                        )
                        Text("  ${a.minutes} min", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (targetSummary.nudges > 0) {
                Text(
                    "Nudges (${selectedApp ?: "all"}): ${targetSummary.nudges} · left feed ${targetSummary.wentHome} · snoozed ${targetSummary.snoozed} · ignored ${targetSummary.ignored}",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mint)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun AnalysisCard(
    stored: String,
    by: String,
    at: Long,
    running: Boolean,
    partial: String,
    summary: UsageSummary,
    onAnalyze: () -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Dashboard Analysis", fontWeight = FontWeight.SemiBold)
            Text("The local on-device model reads your dashboard telemetry and suggests concrete behavioral improvements.", fontSize = 12.sp)
            Button(onClick = onAnalyze, enabled = !running) { Text(if (running) "Analyzing telemetry…" else "Analyze dashboard data") }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
                if (partial.isNotBlank()) Text(partial, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f))
            }
            val items = remember(stored) { PromptBuilder.parseInsights(stored) }
            items.forEach { item ->
                Row {
                    Text(
                        if (item.tip) "Tip  " else "Seen ",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (item.tip) Mint else Coral,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(item.text, fontSize = 14.sp)
                }
            }
            if (items.isNotEmpty()) Text(
                (if (by == "rules") "Rule-based (no model available)" else "Analyzed by $by · on-device") + " · ${fmt.format(Date(at))}",
                fontSize = 11.sp, color = Color.Gray,
            )
            Text(
                if (showInput) "Hide formatted model input ▲" else "Inspect formatted input seen by AI ▼",
                fontSize = 12.sp, color = Amber, modifier = Modifier.clickable { showInput = !showInput },
            )
            if (showInput) {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• Time window: ${summary.days} days (${summary.sessions} sessions, ${summary.totalMinutes} total mins)", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("• Swiping rate: ${summary.totalSwipes} swipes, avg %.1fs/video".format(summary.avgSecondsPerVideo), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("• Engagement: ${summary.tapsPer100Swipes} taps/100 swipes", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("• Bedtime habits: ${summary.lateNightPct}% late-night, ${summary.lyingPct}% lying down", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("• Nudge response: ${summary.wentHome}/${summary.nudges} left feed", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        if (summary.apps.isNotEmpty()) {
                            Text("• App Telemetry Breakdown:", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Mint)
                            summary.apps.forEach { app ->
                                Text(
                                    "  - ${app.app}: ${app.minutes}m, ${app.meters}m, ${app.sessions} ses, %.1fs/vid, %d%% late".format(app.avgSecondsPerVideo, app.lateNightPct),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SUGGESTIONS = listOf(
    "Which app do I scroll the most?",
    "When do I scroll the most?",
    "How fast do I swipe?",
    "How do I respond to nudges?",
    "How can I improve?",
)

@Composable
private fun ChatCard(app: SnapOutApp, summary: UsageSummary, goal: String) {
    val chat by app.llm.chat.collectAsState()
    val typing by app.llm.chatTyping.collectAsState()
    val ai by app.llm.status.collectAsState()
    var input by remember { mutableStateOf("") }
    val busy = typing != null
    val send: (String) -> Unit = { q ->
        if (q.isNotBlank() && !busy) {
            app.llm.ask(q, summary, goal)
            input = ""
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ask about your scrolling", fontWeight = FontWeight.SemiBold)
            Text(
                "Chat privately with your digital wellbeing coach about your feed habits and goals.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SUGGESTIONS.forEach { q ->
                    OutlinedButton(onClick = { send(q) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(q, fontSize = 12.sp)
                    }
                }
            }
            chat.forEach { m -> Bubble(m.fromUser, m.text, m.by) }
            typing?.let { t ->
                Bubble(false, t.ifBlank { "Thinking on-device…" }, "")
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask a question…", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { send(input) }, enabled = !busy && input.isNotBlank()) { Text("Ask") }
            }
            if (chat.isNotEmpty() && !busy) Text(
                "Clear chat", fontSize = 12.sp, color = Amber, modifier = Modifier.clickable { app.llm.clearChat() },
            )
        }
    }
}

@Composable
private fun Bubble(fromUser: Boolean, text: String, by: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (fromUser) Mint.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text, Modifier.padding(10.dp), fontSize = 14.sp)
        }
        if (by.isNotBlank()) Text(by, fontSize = 11.sp, color = Color.Gray)
    }
}
