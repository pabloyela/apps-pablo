package com.tua.baul

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.abs

/** Resultado del análisis de limpieza. */
data class Analisis(
    val duplicados: List<List<Media>>, // grupos idénticos
    val parecidas: List<List<Media>>,  // grupos de fotos casi iguales (ráfagas)
    val videosGrandes: List<Media>,
    val capturasViejas: List<Media>
)

object Limpieza {

    /** Huella rápida: tamaño + primeros y últimos 64 KB. */
    private fun huella(c: Context, m: Media): String? = try {
        c.contentResolver.openInputStream(m.uri)?.use { inp ->
            val md = MessageDigest.getInstance("MD5")
            val buf = ByteArray(65536)
            val n = inp.read(buf)
            if (n > 0) md.update(buf, 0, n)
            val saltar = m.size - 2L * 65536
            if (saltar > 0) inp.skip(saltar)
            val n2 = inp.read(buf)
            if (n2 > 0) md.update(buf, 0, n2)
            md.update(m.size.toString().toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        null
    }

    /** Huella visual (dHash 8x8) de la miniatura: fotos parecidas dan números parecidos. */
    private fun dHash(c: Context, m: Media): Long? = try {
        val t = c.contentResolver.loadThumbnail(m.uri, Size(96, 96), null)
        val s = Bitmap.createScaledBitmap(t, 9, 8, true)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = s.getPixel(x, y)
            val b = s.getPixel(x + 1, y)
            val la = ((a shr 16) and 0xFF) * 3 + ((a shr 8) and 0xFF) * 6 + (a and 0xFF)
            val lb = ((b shr 16) and 0xFF) * 3 + ((b shr 8) and 0xFF) * 6 + (b and 0xFF)
            if (la > lb) h = h or (1L shl bit)
            bit++
        }
        h
    } catch (e: Exception) {
        null
    }

    fun analizar(c: Context, todos: List<Media>, progreso: (Float) -> Unit): Analisis {
        // 1) Duplicados idénticos: mismo tamaño, luego misma huella
        val candidatos = todos.filter { it.size > 0 }.groupBy { it.size to it.mime }.values.filter { it.size > 1 }
        val total = candidatos.sumOf { it.size }.coerceAtLeast(1)
        var hechos = 0
        val duplicados = mutableListOf<List<Media>>()
        for (g in candidatos) {
            val porHuella = g.groupBy { m -> huella(c, m).also { hechos++; progreso(0.5f * hechos / total) } }
            porHuella.forEach { (h, ms) -> if (h != null && ms.size > 1) duplicados += ms.sortedBy { it.date } }
        }
        val yaDuplicadas = duplicados.flatten().map { it.id }.toSet()

        // 2) Parecidas: fotos de la misma carpeta tomadas con pocos segundos de diferencia
        val fotos = todos.filter { !it.video && it.id !in yaDuplicadas }.sortedBy { it.date }
        val rafagas = mutableListOf<List<Media>>()
        var actual = mutableListOf<Media>()
        for (m in fotos) {
            val prev = actual.lastOrNull()
            if (prev != null && m.bucketId == prev.bucketId && m.date - prev.date <= 10_000) actual.add(m)
            else {
                if (actual.size > 1) rafagas += actual
                actual = mutableListOf(m)
            }
        }
        if (actual.size > 1) rafagas += actual
        val parecidas = mutableListOf<List<Media>>()
        val totalR = rafagas.sumOf { it.size }.coerceAtLeast(1)
        var hechosR = 0
        for (r in rafagas) {
            val hashes = r.map { m -> m to dHash(c, m).also { hechosR++; progreso(0.5f + 0.5f * hechosR / totalR) } }
            val usados = mutableSetOf<Long>()
            for ((m, h) in hashes) {
                if (h == null || m.id in usados) continue
                val grupo = hashes.filter { (o, oh) ->
                    o.id !in usados && oh != null && java.lang.Long.bitCount(h xor oh) <= 10
                }.map { it.first }
                if (grupo.size > 1) {
                    parecidas += grupo
                    usados += grupo.map { it.id }
                }
            }
        }

        // 3) Videos grandes (más de 50 MB), del más pesado al más ligero
        val videos = todos.filter { it.video && it.size >= 50L * 1024 * 1024 }.sortedByDescending { it.size }

        // 4) Capturas de pantalla de hace más de 30 días
        val limite = System.currentTimeMillis() - 30L * 86_400_000
        val capturas = todos.filter { Repo.esCaptura(it) && it.date < limite }

        progreso(1f)
        return Analisis(duplicados, parecidas, videos, capturas)
    }

    /** En cada grupo se conserva la mejor (mayor resolución, luego la más antigua); el resto se propone borrar. */
    fun sobrantes(grupo: List<Media>): List<Media> {
        // mayor resolución; si empatan, la más antigua
        val mejor = grupo.maxWithOrNull(compareBy<Media> { it.width.toLong() * it.height }.thenBy { -it.date })
        return grupo.filter { it.id != mejor?.id }
    }
}

private enum class Seccion(val titulo: String) {
    DUPLICADOS("Duplicadas"), PARECIDAS("Parecidas"), VIDEOS("Videos grandes"), CAPTURAS("Capturas viejas")
}

@Composable
fun LimpiezaScreen(a: MainActivity) {
    val todos = a.medios.value ?: emptyList()
    var analisis by remember { mutableStateOf<Analisis?>(null) }
    var progreso by remember { mutableFloatStateOf(0f) }
    var seccion by remember { mutableStateOf(Seccion.DUPLICADOS) }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    BackHandler { a.atras() }

    LaunchedEffect(todos) {
        analisis = null
        val r = withContext(Dispatchers.IO) {
            Limpieza.analizar(a, todos) { p -> progreso = p }
        }
        analisis = r
        // Preselección: en grupos, todo menos la mejor; capturas viejas, todas
        sel = (r.duplicados.flatMap { Limpieza.sobrantes(it) } +
            r.parecidas.flatMap { Limpieza.sobrantes(it) } +
            r.capturasViejas).map { it.id }.toSet()
    }

    val an = analisis
    val visibles: List<Media> = when {
        an == null -> emptyList()
        seccion == Seccion.DUPLICADOS -> an.duplicados.flatten()
        seccion == Seccion.PARECIDAS -> an.parecidas.flatten()
        seccion == Seccion.VIDEOS -> an.videosGrandes
        else -> an.capturasViejas
    }
    val elegidos = visibles.filter { it.id in sel }
    val bytes = elegidos.sumOf { it.size }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Text("Liberar espacio", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        if (an == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Revisando tus fotos y videos…", fontSize = 17.sp)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progreso }, color = Acento, trackColor = Tarjeta, modifier = Modifier.fillMaxWidth())
                Text("${(progreso * 100).toInt()}%", color = Suave, modifier = Modifier.padding(top = 8.dp))
            }
        } else {

        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Seccion.entries) { s ->
                val n = when (s) {
                    Seccion.DUPLICADOS -> an.duplicados.sumOf { it.size }
                    Seccion.PARECIDAS -> an.parecidas.sumOf { it.size }
                    Seccion.VIDEOS -> an.videosGrandes.size
                    Seccion.CAPTURAS -> an.capturasViejas.size
                }
                Pildora("${s.titulo} ($n)", s == seccion) { seccion = s }
            }
        }

        Box(Modifier.weight(1f)) {
            val grupos: List<List<Media>> = when (seccion) {
                Seccion.DUPLICADOS -> an.duplicados
                Seccion.PARECIDAS -> an.parecidas
                Seccion.VIDEOS -> an.videosGrandes.map { listOf(it) }
                Seccion.CAPTURAS -> an.capturasViejas.chunked(4)
            }
            if (grupos.isEmpty()) {
                Text(
                    "¡Nada que limpiar aquí!", color = Suave, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(grupos.size) { gi ->
                        val g = grupos[gi]
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tarjeta).padding(10.dp)) {
                            if (seccion == Seccion.VIDEOS) {
                                Text("${formatSize(g[0].size)} · ${formatTime(g[0].duration)}", fontSize = 14.sp, color = Suave)
                            } else if (seccion != Seccion.CAPTURAS) {
                                Text("${g.size} fotos · se conserva la mejor", fontSize = 14.sp, color = Suave)
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(g) { m ->
                                    val s = m.id in sel
                                    Box(
                                        Modifier.size(104.dp).clip(RoundedCornerShape(10.dp))
                                            .clickable { sel = if (s) sel - m.id else sel + m.id }
                                    ) {
                                        Miniatura(m.uri, Modifier.fillMaxSize(), 256)
                                        if (s) Box(Modifier.fillMaxSize().background(Color(0x55FF6B6B)).border(3.dp, Color(0xFFFF6B6B), RoundedCornerShape(10.dp)))
                                        Icon(
                                            if (s) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                            tint = if (s) Color(0xFFFF6B6B) else Color.White,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                        )
                                        Text(
                                            formatSize(m.size), color = Color.White, fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                                .background(Color(0x99000000)).padding(horizontal = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(Tarjeta).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${elegidos.size} elegidos", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Liberas ${formatSize(bytes)}", fontSize = 13.sp, color = Suave)
            }
            Button(
                onClick = { a.aPapelera(elegidos) { sel = sel - elegidos.map { it.id }.toSet() } },
                enabled = elegidos.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B), contentColor = Color.Black)
            ) { Text("A la papelera") }
        }
        }
    }
}

