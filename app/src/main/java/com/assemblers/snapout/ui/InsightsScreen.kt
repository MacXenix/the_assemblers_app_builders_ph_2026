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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
fun InsightsScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val version by app.db.version.collectAsState()
    val settings by app.settings.state.collectAsState()
    val run by app.llm.insightRun.collectAsState()
    val interventions = remember(version) { app.db.interventions(500) }
    val sessions = remember(version) { app.db.sessions(500) }
    val summary = remember(version) { UsageSummary.from(sessions, interventions, System.currentTimeMillis()) }

    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 20.dp)) {
                Text("Insights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Last ${summary.days} days · computed on this phone only", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (summary.empty) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No feed sessions logged yet.", fontWeight = FontWeight.SemiBold)
                    Text("Scroll in TikTok, YouTube or Instagram with SnapOut on, or load a sample week to try Insights.", fontSize = 13.sp)
                    OutlinedButton(onClick = { SampleData.week(System.currentTimeMillis()).let { (s, i) -> app.db.insertSample(s, i) } }) {
                        Text("Load sample week (demo)")
                    }
                }
            }
        } else {
            item { StatsCard(summary) }
            item {
                AnalysisCard(
                    stored = settings.insights,
                    by = settings.insightsBy,
                    at = settings.insightsAt,
                    running = run.running,
                    partial = run.partial,
                    summaryJson = summary.toJson(),
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

        item { Text("Nudges", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (interventions.isEmpty()) item { Text("None yet.", color = Color.Gray) }
        items(interventions.take(30), key = { "i${it.id}" }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(i.at))} · ${i.app} · score ${i.score}", fontSize = 12.sp, color = Color.Gray)
                    Text(i.text.ifBlank { "—" })
                    Text(
                        "${if (i.source == "GEMMA") "On-device AI" else "Built-in"}${i.ttftMs?.let { " · $it ms" } ?: ""} · ${outcomeLabel(i.outcome)}",
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
        }
        item { Text("Feed sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (sessions.isEmpty()) item { Text("None yet.", color = Color.Gray) }
        items(sessions.take(30), key = { "s${it.id}" }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(s.startedAt))} · ${s.app}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "%d min · %d swipes · %d taps · %.1f s/video · peak %d%s%s".format(
                            ((s.endedAt - s.startedAt) / 60_000).toInt(), s.swipes, s.taps, s.avgDwellMs / 1000.0, s.peakScore,
                            if (s.late) " · late" else "", if (s.intervened) " · nudged" else "",
                        ),
                        fontSize = 13.sp,
                    )
                }
            }
        }
        item { Text("", Modifier.height(12.dp)) }
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
private fun StatsCard(s: UsageSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Stat("${s.minutesPerDay} min", "per active day", Modifier.weight(1f))
                Stat("${s.sessions}", "sessions", Modifier.weight(1f))
                Stat("${s.totalSwipes}", "swipes", Modifier.weight(1f))
            }
            Row {
                Stat("%.1f s".format(s.avgSecondsPerVideo), "per video", Modifier.weight(1f))
                Stat("${s.tapsPer100Swipes}", "taps / 100 swipes", Modifier.weight(1f))
                Stat("${s.longestSessionMinutes} min", "longest session", Modifier.weight(1f))
            }
            Row {
                Stat("${s.lateNightPct}%", "after 22:00", Modifier.weight(1f))
                Stat("${s.lyingPct}%", "lying down", Modifier.weight(1f))
                Stat(s.peakHour?.let { "%02d:00".format(it) } ?: "—", "peak hour", Modifier.weight(1f))
            }
            Text("By app", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            s.apps.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.app, Modifier.weight(0.9f), fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = { if (s.totalMinutes == 0) 0f else a.minutes.toFloat() / s.totalMinutes },
                        modifier = Modifier.weight(1.4f).height(6.dp), color = Amber,
                    )
                    Text("  ${a.minutes} min", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (s.nudges > 0) Text(
                "Nudges: ${s.nudges} · left the feed ${s.wentHome} · snoozed ${s.snoozed} · ignored ${s.ignored}",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
            )
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
    summaryJson: String,
    onAnalyze: () -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On-device analysis", fontWeight = FontWeight.SemiBold)
            Text("The local model reads the numbers above (not your screen) and suggests ways to improve.", fontSize = 12.sp)
            Button(onClick = onAnalyze, enabled = !running) { Text(if (running) "Analyzing…" else "Analyze my scrolling") }
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
                (if (by == "rules") "Rule-based (no model available)" else "Written by $by · on-device") + " · ${fmt.format(Date(at))}",
                fontSize = 11.sp, color = Color.Gray,
            )
            Text(
                if (showInput) "Hide what the model saw" else "What the model sees",
                fontSize = 12.sp, color = Amber, modifier = Modifier.clickable { showInput = !showInput },
            )
            if (showInput) Text(summaryJson, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f))
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
                "Answers come from the on-device model using only the stats above. " +
                    "Model: ${ai.modelName?.removeSuffix(".litertlm") ?: "none found (built-in rules answer)"}",
                fontSize = 12.sp,
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
