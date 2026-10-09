package com.assemblers.snapout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
fun HistoryScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val version by app.db.version.collectAsState()
    val interventions = remember(version) { app.db.interventions(50) }
    val sessions = remember(version) { app.db.sessions(50) }
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Interventions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp)) }
        if (interventions.isEmpty()) item { Text("None yet.", color = Color.Gray) }
        items(interventions, key = { "i${it.id}" }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(i.at))} · ${i.app} · score ${i.score}", fontSize = 12.sp, color = Color.Gray)
                    Text(i.text.ifBlank { "—" })
                    Text(
                        "${if (i.source == "GEMMA") "Gemma (on-device)" else "Built-in"}${i.ttftMs?.let { " · ${it} ms" } ?: ""} · ${i.outcome ?: "open"}",
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
        }
        item { Text("Feed sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        if (sessions.isEmpty()) item { Text("None yet.", color = Color.Gray) }
        items(sessions, key = { "s${it.id}" }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmt.format(Date(s.startedAt))} · ${s.app}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "%d min · %d swipes · %.1f m scrolled · peak %d%s".format(
                            ((s.endedAt - s.startedAt) / 60_000).toInt(), s.swipes, s.distanceMeters, s.peakScore,
                            if (s.intervened) " · interrupted" else "",
                        ),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
