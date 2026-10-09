package com.assemblers.snapout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF6EE7B7)
val Amber = Color(0xFFFBBF24)
val Coral = Color(0xFFF87171)
val Ink = Color(0xFF0B1020)
val Panel = Color(0xFF151B2E)

private val scheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Amber,
    background = Ink,
    surface = Panel,
    surfaceVariant = Color(0xFF1E2640),
    error = Coral,
)

@Composable
fun SnapOutTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
