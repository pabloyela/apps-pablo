package com.tua.baul

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cuadrícula de 3x3 puntos para dibujar un patrón.
 * Los puntos van del 0 (arriba a la izquierda) al 8 (abajo a la derecha).
 */
@Composable
fun CuadriculaPatron(modifier: Modifier = Modifier, colorLinea: Color = Acento, onTerminar: (List<Int>) -> Unit) {
    var camino by remember { mutableStateOf<List<Int>>(emptyList()) }
    var dedo by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun centro(i: Int): Offset {
                    val celda = size.width / 3f
                    return Offset(celda * (i % 3) + celda / 2f, celda * (i / 3) + celda / 2f)
                }
                fun puntoEn(p: Offset): Int? {
                    val radio = size.width / 3f * 0.35f
                    return (0 until 9).firstOrNull { (centro(it) - p).getDistance() < radio }
                }
                detectDragGestures(
                    onDragStart = { p ->
                        camino = listOfNotNull(puntoEn(p))
                        dedo = p
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dedo = change.position
                        val i = puntoEn(change.position)
                        if (i != null && i !in camino) camino = camino + i
                    },
                    onDragEnd = {
                        val r = camino
                        camino = emptyList()
                        dedo = null
                        if (r.isNotEmpty()) onTerminar(r)
                    },
                    onDragCancel = {
                        camino = emptyList()
                        dedo = null
                    }
                )
            }
    ) {
        val celda = size.width / 3f
        fun c(i: Int) = Offset(celda * (i % 3) + celda / 2f, celda * (i / 3) + celda / 2f)
        for (k in 1 until camino.size) {
            drawLine(colorLinea, c(camino[k - 1]), c(camino[k]), strokeWidth = 10f, cap = StrokeCap.Round)
        }
        val d = dedo
        if (d != null && camino.isNotEmpty()) {
            drawLine(colorLinea.copy(alpha = 0.5f), c(camino.last()), d, strokeWidth = 8f, cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val activo = i in camino
            drawCircle(if (activo) colorLinea else Color(0x55FFFFFF), radius = if (activo) 20f else 12f, center = c(i))
        }
    }
}

/** Entrada al área privada: solo puntos, sin ningún texto. */
@Composable
fun PatronScreen(a: MainActivity) {
    BackHandler { a.atras() }
    Box(
        Modifier.fillMaxSize().background(Fondo).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        CuadriculaPatron(Modifier.fillMaxWidth().padding(48.dp), colorLinea = Color(0x99FFFFFF)) { p ->
            a.probarPatron(p)
        }
    }
}

/** Configurar el patrón (Ajustes): se dibuja dos veces. */
@Composable
fun CrearPatronScreen(a: MainActivity) {
    BackHandler { a.atras() }
    var primero by remember { mutableStateOf<List<Int>?>(null) }
    var mensaje by remember { mutableStateOf("Dibuja tu patrón (mínimo 4 puntos)") }
    Column(
        Modifier.fillMaxSize().background(Fondo).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(mensaje, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Para entrar: mantén presionado el título «Baúl» y dibújalo.",
            fontSize = 14.sp, color = Suave, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        CuadriculaPatron(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { p ->
            val prev = primero
            when {
                p.size < 4 -> mensaje = "Muy corto: une al menos 4 puntos"
                prev == null -> {
                    primero = p
                    mensaje = "Dibújalo otra vez para confirmar"
                }
                prev == p -> {
                    a.ponerPatron(p)
                    a.atras()
                }
                else -> {
                    primero = null
                    mensaje = "No coincidió. Empieza de nuevo"
                }
            }
        }
    }
}
