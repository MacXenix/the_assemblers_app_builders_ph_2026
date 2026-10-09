package com.assemblers.snapout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.ui.theme.Amber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityHistoryScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val version by app.db.version.collectAsState()
    val sessions = remember(version) { app.db.sessions(100) }
    val interventions = remember(version) { app.db.interventions(100) }
    val fmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }

    var selectedFilter by remember { mutableStateOf("all") } // "all", "nudges", "sessions"

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Activity & History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Log of all detected feed sessions and trigger nudges", fontSize = 12.sp, color = Color.Gray)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text("All (${sessions.size + interventions.size})") },
                )
                FilterChip(
                    selected = selectedFilter == "nudges",
                    onClick = { selectedFilter = "nudges" },
                    label = { Text("Nudges (${interventions.size})") },
                )
                FilterChip(
                    selected = selectedFilter == "sessions",
                    onClick = { selectedFilter = "sessions" },
                    label = { Text("Sessions (${sessions.size})") },
                )
            }
        }

        if (selectedFilter != "sessions") {
            item {
                Text("Recent Nudges (${interventions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (interventions.isEmpty()) {
                item { Text("No nudges triggered yet.", fontSize = 13.sp, color = Color.Gray) }
            } else {
                items(interventions, key = { "nudge_${it.id}" }) { i ->
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
            }
        }

        if (selectedFilter != "nudges") {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Feed Sessions (${sessions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (sessions.isEmpty()) {
                item { Text("No feed sessions recorded yet.", fontSize = 13.sp, color = Color.Gray) }
            } else {
                items(sessions, key = { "session_${it.id}" }) { s ->
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
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun outcomeLabel(o: String?) = when (o) {
    "went_home" -> "left the feed"
    "snoozed" -> "snoozed"
    "dismissed" -> "swiped away"
    "continued" -> "kept scrolling"
    else -> "no action"
}
