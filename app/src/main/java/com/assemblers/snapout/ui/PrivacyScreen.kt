package com.assemblers.snapout.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assemblers.snapout.SnapOutApp
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
        Text("Privacy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (hasInternet) "INTERNET permission present" else "✓ No network permission",
                    color = if (hasInternet) Coral else Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                )
                Text("Android blocks every network socket for apps without INTERNET. SnapOut physically cannot upload anything.", fontSize = 13.sp)
                Text("Requested permissions:", fontSize = 12.sp)
                Text(
                    permissions.ifEmpty { listOf("(none)") }.joinToString("\n") { it.substringAfterLast('.') },
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What SnapOut reads", fontWeight = FontWeight.SemiBold)
                Text(
                    "• Scroll / tap timing and which feed app is in front\n" +
                        "• Ambient light level and phone tilt while a feed is open\n" +
                        "• Never: screen text, images, video, messages, camera, mic, location",
                    fontSize = 13.sp,
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Stored on this phone", fontWeight = FontWeight.SemiBold)
                Text("${counts.first} feed sessions · ${counts.second} interventions (see History)", fontSize = 13.sp)
                Button(onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                    Text("Purge all data")
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete everything?") },
        text = { Text("All sessions, interventions and settings will be erased.") },
        confirmButton = {
            TextButton(onClick = { app.db.purge(); app.settings.clear(); confirm = false }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
