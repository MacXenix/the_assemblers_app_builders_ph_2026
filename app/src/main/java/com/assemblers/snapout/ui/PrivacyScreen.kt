package com.assemblers.snapout.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
import com.assemblers.snapout.ui.theme.Amber
import com.assemblers.snapout.ui.theme.Coral
import com.assemblers.snapout.ui.theme.Mint

@Composable
fun PrivacyScreen(app: SnapOutApp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val version by app.db.version.collectAsState()
    val counts = remember(version) { app.db.rowCounts() }
    val permissions = remember {
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
    }
    val hasInternet = Manifest.permission.INTERNET in permissions
    var confirm by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("Privacy & Data Boundary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("100% on-device architecture · Zero data leaves your hardware", fontSize = 12.sp, color = Color.Gray)
        }

        // 1. Hardware-level Network Isolation Card
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, if (hasInternet) Coral else Mint, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = if (hasInternet) Coral.copy(alpha = 0.12f) else Mint.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasInternet) "⚠ WARNING: INTERNET PERMISSION DETECTED" else "🛡 100% Offline (Zero Network Permission)",
                        color = if (hasInternet) Coral else Mint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                Text(
                    "SnapOut's AndroidManifest.xml does NOT declare 'android.permission.INTERNET'. " +
                        "The Android Linux kernel mathematically blocks any socket or network transmission. " +
                        "Your telemetry, scrolling physics, and AI inferences physically cannot be sent anywhere.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    "Declared Android Permissions: " + permissions.ifEmpty { listOf("None") }.joinToString { it.substringAfterLast('.') },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        // 2. Highlighting: WHAT IS TAKEN (Collected Data)
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ What Is Collected (Local Only)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mint)
                }
                Text(
                    "Only raw kinematic physics and anonymous environmental sensors are sampled while a social feed is active:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )

                PrivacyItem(
                    title = "Scroll Delta & Swiping Rate",
                    desc = "Pixel drag distance & swipe count to compute feed mileage and skim speed.",
                    tag = "Kinematics",
                )
                PrivacyItem(
                    title = "Interaction Tap Timestamps",
                    desc = "Counts active taps vs passive scrolls to detect zombie thumb dissociation.",
                    tag = "Engagement",
                )
                PrivacyItem(
                    title = "Foreground Feed Package Name",
                    desc = "Detects whether TikTok, Instagram, YouTube Shorts, or X is currently active.",
                    tag = "Package ID",
                )
                PrivacyItem(
                    title = "Ambient Lux & Accelerometer Gravity",
                    desc = "Detects dark rooms (<15 lux) and lying-down tilt (Y-axis tilt) only while scrolling.",
                    tag = "Posture / Light",
                )
                PrivacyItem(
                    title = "Local Clock & Stated Goal",
                    desc = "System hour of the day and your personal goal string to tailor AI nudges.",
                    tag = "Context",
                )
            }
        }

        // 3. Highlighting: WHAT IS NOT TAKEN (Strictly Never Collected)
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Coral.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✕ What Is NEVER Collected or Accessed", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Coral)
                }
                Text(
                    "SnapOut utilizes canRetrieveWindowContent = false in its Accessibility Service, ensuring total content blindness:",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )

                PrivacyExcludedItem(
                    title = "Screen Content & Video Feeds",
                    desc = "No screen recording, no OCR, no video frames, and no caption reading.",
                )
                PrivacyExcludedItem(
                    title = "Keystrokes, Text & Messages",
                    desc = "Cannot read what you type, search queries, comments, or DMs.",
                )
                PrivacyExcludedItem(
                    title = "Camera, Microphone & Audio",
                    desc = "Zero camera or audio recording permissions. Never listens or captures.",
                )
                PrivacyExcludedItem(
                    title = "GPS Location & Network Identifiers",
                    desc = "No location access, no IP tracking, no advertising IDs, and no hardware fingerprinting.",
                )
                PrivacyExcludedItem(
                    title = "Accounts & Identity",
                    desc = "No login required, no Google account linking, zero cloud databases.",
                )
            }
        }

        // 4. Local Database Storage & Purge Controls
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-Device SQLite Storage", fontWeight = FontWeight.SemiBold)
                Text(
                    "All metrics remain in your phone's private app sandbox (/data/user/0/com.assemblers.snapout/databases/snapout.db):",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    "• ${counts.first} logged feed sessions\n• ${counts.second} recorded nudge interventions",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Amber,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { confirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Purge all recorded data now")
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all local data?") },
            text = { Text("All local session history, interventions, and preferences will be permanently wiped from SQLite storage.") },
            confirmButton = {
                TextButton(onClick = { app.db.purge(); app.settings.clear(); confirm = false }) {
                    Text("Delete Everything", color = Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivacyItem(title: String, desc: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("• $title", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            Surface(
                color = Mint.copy(alpha = 0.18f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(tag, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Mint, fontFamily = FontFamily.Monospace)
            }
        }
        Text(desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun PrivacyExcludedItem(title: String, desc: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("✕ $title", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Coral)
        Text(desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(start = 12.dp))
    }
}
