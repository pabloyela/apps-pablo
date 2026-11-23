package com.tua.pelis

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Acento = Color(0xFFFFB74D)
val Fondo = Color(0xFF0E0E10)
val Tarjeta = Color(0xFF1C1C22)
val TextoSuave = Color(0xFFB8B8C0)

@Composable
fun PelisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Acento,
            onPrimary = Color.Black,
            background = Fondo,
            onBackground = Color.White,
            surface = Tarjeta,
            onSurface = Color.White,
            surfaceVariant = Tarjeta,
            onSurfaceVariant = TextoSuave
        ),
        content = content
    )
}
