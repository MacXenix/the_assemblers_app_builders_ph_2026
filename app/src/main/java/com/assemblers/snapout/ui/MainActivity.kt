package com.assemblers.snapout.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.assemblers.snapout.R
import com.assemblers.snapout.app
import com.assemblers.snapout.ui.theme.SnapOutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = app
        setContent {
            SnapOutTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                "Home" to R.drawable.ic_nav_home,
                                "History" to R.drawable.ic_nav_history,
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
                        1 -> HistoryScreen(app, m)
                        else -> PrivacyScreen(app, m)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.llm.refreshModelPresence()
    }
}
