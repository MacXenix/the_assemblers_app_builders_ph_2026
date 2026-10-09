package com.assemblers.snapout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.core.Strictness

@Composable
fun SettingsScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val settings by app.settings.state.collectAsState()
    val connected by app.engine.serviceConnected.collectAsState()
    val diag by app.engine.diagnostics.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Preferences, monitored feeds, and demo simulation", fontSize = 12.sp, color = Color.Gray)
        }

        // Monitored Feeds Selection
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Monitored Feeds", fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose which short-form video and social feeds SnapOut should watch for scrolling loops.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                // Distinct feed app labels
                val feedOptions = listOf(
                    "TikTok" to setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
                    "Instagram" to setOf("com.instagram.android"),
                    "YouTube Shorts" to setOf("com.google.android.youtube"),
                    "X (Twitter)" to setOf("com.twitter.android"),
                    "Facebook" to setOf("com.facebook.katana"),
                    "Reddit" to setOf("com.reddit.frontpage"),
                )
                feedOptions.forEach { (label, pkgSet) ->
                    val isChecked = pkgSet.any { it in settings.enabledFeeds }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, fontSize = 14.sp)
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { enable ->
                                app.settings.update { s ->
                                    val updated = if (enable) {
                                        s.enabledFeeds + pkgSet
                                    } else {
                                        s.enabledFeeds - pkgSet
                                    }
                                    s.copy(enabledFeeds = updated)
                                }
                            },
                        )
                    }
                }
            }
        }

        // Nudge Tone & Audio Settings
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nudge tone & behavior", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Strictness.entries.forEach { s ->
                        FilterChip(
                            selected = settings.strictness == s,
                            onClick = { app.settings.update { it.copy(strictness = s) } },
                            label = { Text(s.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }
                Text(
                    "Gentle asks open questions; Strict delivers direct, firm somatic circuit-breakers. " +
                        "Repeated nudges within the same night become progressively firmer.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read nudge aloud (Speech synthesis)", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(settings.speak, onCheckedChange = { v -> app.settings.update { it.copy(speak = v) } })
                }
            }
        }

        // Demo Controls & Testing Simulation
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Demo controls", fontWeight = FontWeight.SemiBold)
                Text(
                    "Useful for live presentations or verifying notifications without waiting for real 20-minute sessions.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Demo mode (5s trigger, low thresholds)", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(settings.demoMode, onCheckedChange = { v -> app.settings.update { it.copy(demoMode = v) } })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pretend it's late & dark (bedtime boost)", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(settings.forceNight, onCheckedChange = { v -> app.settings.update { it.copy(forceNight = v) } })
                }

                Button(
                    onClick = { app.engine.forceTrigger() },
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⚡ Send test nudge notification now")
                }

                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Live Diagnostic Counters:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "• Feed scrolls: ${diag.feedScrollEvents} (total scrolls: ${diag.scrollEvents})\n" +
                                "• Clicks: ${diag.clickEvents} · Windows: ${diag.windowEvents}\n" +
                                "• Last package: ${diag.lastPackage ?: "none"}\n" +
                                "• Last scroll: ${diag.lastScroll ?: "none"}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
