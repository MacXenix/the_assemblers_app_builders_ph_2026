package com.assemblers.snapout.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.assemblers.snapout.R
import com.assemblers.snapout.app
import com.assemblers.snapout.ui.theme.SnapOutTheme

class MainActivity : ComponentActivity() {
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tab = savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val app = app
        setContent {
            SnapOutTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                "Home" to R.drawable.ic_nav_home,
                                "Insights" to R.drawable.ic_nav_history,
                                "Activity" to R.drawable.ic_nav_activity,
                                "Settings" to R.drawable.ic_nav_settings,
                                "Privacy" to R.drawable.ic_nav_privacy,
                            ).forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(painterResource(icon), contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    when (tab) {
                        0 -> HomeScreen(app, m)
                        1 -> InsightsScreen(app, m)
                        2 -> ActivityHistoryScreen(app, m)
                        3 -> SettingsScreen(app, m)
                        else -> PrivacyScreen(app, m)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tab = intent.getIntExtra(EXTRA_TAB, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab)
    }

    override fun onResume() {
        super.onResume()
        app.llm.refreshModelPresence()
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
