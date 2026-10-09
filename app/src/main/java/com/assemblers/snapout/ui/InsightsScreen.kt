package com.assemblers.snapout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.ai.EngineStatus
import com.assemblers.snapout.ai.PromptBuilder
import com.assemblers.snapout.core.RuleInsights
import com.assemblers.snapout.core.SampleData
import com.assemblers.snapout.core.UsageSummary
import com.assemblers.snapout.core.render
import com.assemblers.snapout.ui.theme.Amber
import com.assemblers.snapout.ui.theme.Coral
import com.assemblers.snapout.ui.theme.Mint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var showAnalysisModal by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    val (line, color) = when {
                        settings.modelFile == "none" -> "No AI Model selected (Rule-based engine only)" to Amber
                        ai.status == EngineStatus.NO_MODEL -> "No model loaded. Place .litertlm file in app storage." to Coral
                        ai.status == EngineStatus.IDLE -> "${ai.modelName} · ready to load" to Amber
                        ai.status == EngineStatus.LOADING -> "Loading ${ai.modelName}…" to Amber
                        ai.status == EngineStatus.READY -> "${ai.modelName} · ${ai.backend} · loaded in ${ai.loadMs} ms" to Mint
                        ai.status == EngineStatus.ERROR -> "Error: ${ai.error}" to Coral
                        else -> "Idle" to Amber
                    }
                    Text(line, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text("100% private on-device LLM (LiteRT-LM). Select 'No Model' to compare with rule-based heuristics.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))

                    val models = remember(ai) { app.llm.modelCandidates() }
                    Text("Select Model / Mode:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = settings.modelFile == "none",
                            onClick = {
                                app.settings.update { it.copy(modelFile = "none") }
                                app.llm.switchModel()
                            },
                            label = { Text("No Model (Rules)", fontSize = 11.sp) },
                        )
                        models.forEach { f ->
                            FilterChip(
                                selected = settings.modelFile != "none" && (settings.modelFile == f.name || (settings.modelFile.isBlank() && f.name == ai.modelName)),
                                onClick = {
                                    app.settings.update { it.copy(modelFile = f.name) }
                                    app.llm.switchModel()
                                },
                                label = { Text("${f.name.removeSuffix(".litertlm")} (${f.length() / 1_000_000} MB)", fontSize = 11.sp) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { app.llm.warmUp() },
                            enabled = settings.modelFile != "none" && (ai.status == EngineStatus.IDLE || ai.status == EngineStatus.ERROR),
                        ) {
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
            // Dashboard with interactive Stacked Graph & Highlighting
            item { StatsCard(summary, sessions, interventions) }

            // Action Card to Open AI Analysis Modal (eliminating heavy vertical scroll)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("AI Telemetry Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Amber)
                                Text("Deep synthesis of habits, speed, and advice", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                            Button(onClick = { showAnalysisModal = true }) {
                                Text("View Analysis ↗")
                            }
                        }
                        val items = remember(settings.insights) { PromptBuilder.parseInsights(settings.insights) }
                        if (items.isNotEmpty()) {
                            Text(
                                "Latest: \"${items.firstOrNull()?.text ?: ""}\"",
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = Mint,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // Focused Chat Card
        item { ChatCard(app, summary, settings.goal) }
        item { Spacer(Modifier.height(16.dp)) }
    }

    // Modal Sheet for AI Analysis
    if (showAnalysisModal) {
        AlertDialog(
            onDismissRequest = { showAnalysisModal = false },
            confirmButton = {
                TextButton(onClick = { showAnalysisModal = false }) { Text("Close", color = Amber) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Telemetry Analysis", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                AnalysisContent(
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
            },
        )
    }
}

@Composable
private fun StatsCard(
    overall: UsageSummary,
    allSessions: List<com.assemblers.snapout.data.FeedSession>,
    allNudges: List<com.assemblers.snapout.data.Intervention>,
) {
    var selectedApp by remember { mutableStateOf<String?>(null) } // null = All Apps Average
    var selectedHour by remember { mutableStateOf<Int?>(null) } // clicked hour on timeline

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

    Card(
        Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedApp == null) "Dashboard: All Apps Average" else "Dashboard: $selectedApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (selectedApp != null) Amber else Color.White,
                )
                if (selectedApp != null) {
                    Text(
                        "Reset to All",
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

            // Time Distribution (Donut Chart)
            if (overall.apps.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Mint.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Time Distribution Chart", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Mint)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Box(Modifier.size(95.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(85.dp)) {
                                    val strokeWidth = 14.dp.toPx()
                                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                                    var startAngle = -90f
                                    val totalMin = overall.totalMinutes.coerceAtLeast(1)

                                    overall.apps.forEachIndexed { idx, app ->
                                        val sweep = (app.minutes.toFloat() / totalMin) * 360f
                                        val color = chartColors[idx % chartColors.size]
                                        val isAppCur = selectedApp == app.app
                                        drawArc(
                                            color = if (selectedApp != null && !isAppCur) color.copy(alpha = 0.25f) else color,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = if (isAppCur) strokeWidth * 1.3f else strokeWidth, cap = StrokeCap.Butt),
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

                            // Legend & Quick Selectors
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                overall.apps.take(4).forEachIndexed { idx, app ->
                                    val color = chartColors[idx % chartColors.size]
                                    val pct = if (overall.totalMinutes == 0) 0 else (100 * app.minutes / overall.totalMinutes)
                                    val isCur = selectedApp == app.app
                                    Row(
                                        Modifier.fillMaxWidth().clickable { selectedApp = if (isCur) null else app.app },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(8.dp, 8.dp).padding(end = 4.dp), contentAlignment = Alignment.Center) {
                                            Canvas(Modifier.fillMaxSize()) { drawCircle(if (selectedApp != null && !isCur) color.copy(alpha = 0.3f) else color) }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(app.app, Modifier.weight(1f), fontSize = 11.sp, color = if (isCur) Amber else Color.White, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal)
                                        Text("$pct%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Key Telemetry Metrics Grid
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

            // Highlighted Component: 24-Hour Activity Timeline Stacked Bar Graph
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("24-Hour Activity Timeline", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                        if (selectedHour != null) {
                            Text(
                                "Hour %02d:00 (%d min)".format(selectedHour, targetSummary.hourlyMinutes[selectedHour] ?: 0),
                                fontSize = 11.sp,
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            Text("Tap bar to inspect", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    // Compute per-app minutes for each hour so each bar can be stacked by app
                    val hourlyByApp = remember(targetSessions) {
                        val cal = java.util.Calendar.getInstance()
                        val map = mutableMapOf<Int, MutableMap<String, Double>>()
                        targetSessions.forEach { s ->
                            cal.timeInMillis = s.startedAt
                            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            val m = (s.endedAt - s.startedAt) / 60_000.0
                            val appMap = map.getOrPut(h) { mutableMapOf() }
                            appMap[s.app] = (appMap[s.app] ?: 0.0) + m
                        }
                        map
                    }

                    val hourlyTotals = remember(hourlyByApp) {
                        (0..23).associateWith { h -> hourlyByApp[h]?.values?.sum() ?: 0.0 }
                    }
                    val maxHourTotal = remember(hourlyTotals) {
                        (hourlyTotals.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                    }

                    Row(
                        Modifier.fillMaxWidth().height(75.dp).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        (0..23).forEach { h ->
                            val appMap = hourlyByApp[h].orEmpty()
                            val totalHourMin = hourlyTotals[h] ?: 0.0
                            val barHeightFraction = (totalHourMin / maxHourTotal).toFloat().coerceIn(if (totalHourMin > 0) 0.12f else 0.05f, 1f)
                            val isSelected = selectedHour == h
                            val isPeak = h == targetSummary.peakHour && totalHourMin > 0

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                                    .clickable { selectedHour = if (selectedHour == h) null else h }
                                    .padding(horizontal = 1.dp),
                            ) {
                                // Stacked bar container for this hour
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((58 * barHeightFraction).dp)
                                        .border(
                                            width = if (isSelected) 1.5.dp else if (isPeak) 1.dp else 0.dp,
                                            color = if (isSelected) Color.White else if (isPeak) Amber else Color.Transparent,
                                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                                        )
                                        .background(
                                            if (totalHourMin <= 0) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                            RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                                        ),
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    if (appMap.isEmpty()) {
                                        Box(Modifier.fillMaxSize())
                                    } else {
                                        // Stacked segments colored by app
                                        val totalAppMinInHour = appMap.values.sum().coerceAtLeast(0.001)
                                        appMap.entries.forEach { (appName, appMin) ->
                                            val appIdx = overall.apps.indexOfFirst { it.app.equals(appName, ignoreCase = true) }
                                            val segColor = if (appIdx >= 0) chartColors[appIdx % chartColors.size] else Mint
                                            val segFraction = (appMin / totalAppMinInHour).toFloat().coerceAtLeast(0.05f)
                                            val isAppMatched = selectedApp == null || selectedApp.equals(appName, ignoreCase = true)
                                            val isHourMatched = selectedHour == null || isSelected

                                            val alpha = if (isAppMatched && isHourMatched) 0.95f else 0.22f

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(segFraction)
                                                    .background(segColor.copy(alpha = alpha)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("00:00", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("06:00", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("12:00", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("18:00", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("23:00", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Highlighted Component: Feed Time Breakdown (Stacked Bars)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Coral.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Feed Time Breakdown", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Coral)

                    // Stacked Composite Progress Bar
                    if (overall.totalMinutes > 0) {
                        Row(
                            Modifier.fillMaxWidth().height(10.dp).background(Color.DarkGray, RoundedCornerShape(5.dp)),
                        ) {
                            overall.apps.forEachIndexed { idx, a ->
                                val weight = (a.minutes.toFloat() / overall.totalMinutes).coerceAtLeast(0.01f)
                                val isCur = selectedApp == a.app
                                Box(
                                    Modifier
                                        .weight(weight)
                                        .height(10.dp)
                                        .background(
                                            if (selectedApp != null && !isCur) chartColors[idx % chartColors.size].copy(alpha = 0.3f)
                                            else chartColors[idx % chartColors.size],
                                        )
                                        .clickable { selectedApp = if (isCur) null else a.app },
                                )
                            }
                        }
                    }

                    overall.apps.forEachIndexed { idx, a ->
                        val isCur = selectedApp == a.app
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedApp = if (isCur) null else a.app }
                                .padding(vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    a.app + if (isCur) " (highlighted)" else "",
                                    Modifier.weight(0.9f),
                                    fontSize = 13.sp,
                                    color = if (isCur) Amber else Color.White,
                                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                )
                                LinearProgressIndicator(
                                    progress = { if (overall.totalMinutes == 0) 0f else a.minutes.toFloat() / overall.totalMinutes },
                                    modifier = Modifier.weight(1.4f).height(7.dp),
                                    color = if (selectedApp != null && !isCur) chartColors[idx % chartColors.size].copy(alpha = 0.3f) else chartColors[idx % chartColors.size],
                                )
                                Text("  ${a.minutes} min", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
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
private fun AnalysisContent(
    stored: String,
    by: String,
    at: Long,
    running: Boolean,
    partial: String,
    summary: UsageSummary,
    onAnalyze: () -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "The on-device model inspects your sensor physics, app speeds, and bedtime habits to generate 3 observations and 3 micro-action tips.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
        Button(onClick = onAnalyze, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Analyzing telemetry…" else "Run On-Device Analysis Now")
        }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
            if (partial.isNotBlank()) Text(partial, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f))
        }
        val items = remember(stored) { PromptBuilder.parseInsights(stored) }
        items.forEach { item ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(
                    if (item.tip) "Tip  " else "Seen ",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (item.tip) Mint else Coral,
                    fontFamily = FontFamily.Monospace,
                )
                Text(item.text, fontSize = 13.sp)
            }
        }
        if (items.isNotEmpty()) Text(
            (if (by == "rules" || by == "none") "Deterministic Heuristics (Rule-based engine)" else "Analyzed by $by · on-device") + " · ${fmt.format(Date(at))}",
            fontSize = 11.sp, color = if (by == "rules" || by == "none") Amber else Color.Gray,
        )
        Text(
            if (showInput) "Hide formatted model input ▲" else "Inspect formatted input seen by AI ▼",
            fontSize = 12.sp, color = Amber, modifier = Modifier.clickable { showInput = !showInput },
        )
        if (showInput) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
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
            var showAllChat by remember { mutableStateOf(false) }
            val visibleChat = if (showAllChat || chat.size <= 4) chat else chat.takeLast(4)

            if (chat.size > 4) {
                Text(
                    if (showAllChat) "Collapse earlier messages ▲" else "Show earlier messages (${chat.size - 4}) ▼",
                    fontSize = 12.sp,
                    color = Amber,
                    modifier = Modifier.clickable { showAllChat = !showAllChat },
                )
            }

            visibleChat.forEach { m -> Bubble(m.fromUser, m.text, m.by) }
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
