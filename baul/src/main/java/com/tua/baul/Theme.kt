package com.tua.baul

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Acento = Color(0xFFE0B464)   // dorado del baúl
val Fondo = Color(0xFF0D0D0F)
val Tarjeta = Color(0xFF1C1C21)
val Suave = Color(0xFFA8A8B0)

@Composable
fun BaulTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Acento,
            onPrimary = Color.Black,
            background = Fondo,
            onBackground = Color.White,
            surface = Tarjeta,
            onSurface = Color.White,
            surfaceVariant = Tarjeta,
            onSurfaceVariant = Suave
        ),
        content = content
    )
}
