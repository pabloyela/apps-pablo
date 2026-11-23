package com.tua.baul

import com.tua.comun.*

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- Modelo de la edición ----------

/** Recorte en coordenadas 0..1 de la imagen ya girada/enderezada. */
data class Recorte(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f)

data class Geo(
    val rot: Int = 0,          // giros de 90° (0..3)
    val flip: Boolean = false, // espejo horizontal
    val angulo: Float = 0f,    // enderezar (-45..45)
    val recorte: Recorte = Recorte()
)

data class AjustesFoto(
    val exposicion: Float = 0f,
    val brillo: Float = 0f,
    val contraste: Float = 0f,
    val saturacion: Float = 0f,
    val calidez: Float = 0f,
    val luces: Float = 0f,
    val sombras: Float = 0f,
    val vineta: Float = 0f
) {
    val neutro get() = this == AjustesFoto()
}

/** Trazo a mano o de mosaico. Puntos en 0..1; ancho relativo al lado corto. */
data class Trazo(val puntos: List<Offset>, val color: Int, val ancho: Float)

/** Texto sobre la foto. (x, y) es el centro, en 0..1; tam relativo al lado corto. */
data class TextoCapa(val texto: String, val x: Float, val y: Float, val tam: Float, val color: Int)

data class Edicion(
    val geo: Geo = Geo(),
    val aj: AjustesFoto = AjustesFoto(),
    val estilo: Estilo? = null,
    val estiloAj: Ajustes = Ajustes(),
    val textos: List<TextoCapa> = emptyList(),
    val trazos: List<Trazo> = emptyList(),
    val mosaicos: List<Trazo> = emptyList(),
    val sello: Boolean = false,
    val firma: String = "",
    val fechaSello: Long = 0L,
    val desenf: Desenfoque = Desenfoque(),
    val sujetoColor: Boolean = false   // el filtro solo en el fondo; la persona queda a color
) {
    val quiereMascara get() = desenf.auto || (sujetoColor && estilo != null)
}

object Edit {

    /** Gira, voltea y endereza (sin recortar). */
    fun geometria(src: Bitmap, g: Geo): Bitmap {
        var b = src
        if (g.rot % 4 != 0 || g.flip) {
            val m = Matrix()
            m.postRotate(90f * (g.rot % 4))
            if (g.flip) m.postScale(-1f, 1f)
            b = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        if (abs(g.angulo) > 0.05f) {
            val w = b.width
            val h = b.height
            val rad = abs(g.angulo) * PI.toFloat() / 180f
            // escala para que no queden esquinas vacías
            val s = cos(rad) + max(w, h).toFloat() / min(w, h) * sin(rad)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            cv.translate(w / 2f, h / 2f)
            cv.rotate(g.angulo)
            cv.scale(s, s)
            cv.drawBitmap(b, -w / 2f, -h / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            b = out
        }
        return b
    }

    fun recortar(b: Bitmap, r: Recorte): Bitmap {
        if (r == Recorte()) return b
        val x = (r.l * b.width).roundToInt().coerceIn(0, b.width - 1)
        val y = (r.t * b.height).roundToInt().coerceIn(0, b.height - 1)
        val w = ((r.r - r.l) * b.width).roundToInt().coerceIn(1, b.width - x)
        val h = ((r.b - r.t) * b.height).roundToInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    /** Ajustes de luz y color (en todos los núcleos). */
    suspend fun ajustar(src: Bitmap, a: AjustesFoto): Bitmap = withContext(Dispatchers.Default) {
        if (a.neutro) return@withContext src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val expo = 2f.pow(a.exposicion * 1.5f)
        val chunks = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rows = (h + chunks - 1) / chunks
        coroutineScope {
            (0 until chunks).map { c ->
                async {
                    for (yy in c * rows until min(h, (c + 1) * rows)) {
                        val dy = (yy + 0.5f) / h - 0.5f
                        var i = yy * w
                        for (xx in 0 until w) {
                            val p = px[i]
                            var r = ((p shr 16) and 0xFF) / 255f
                            var g = ((p shr 8) and 0xFF) / 255f
                            var b = (p and 0xFF) / 255f
                            r *= expo; g *= expo; b *= expo
                            val br = a.brillo * 0.2f
                            r += br; g += br; b += br
                            val k = 1f + a.contraste * 0.8f
                            r = (r - 0.5f) * k + 0.5f
                            g = (g - 0.5f) * k + 0.5f
                            b = (b - 0.5f) * k + 0.5f
                            if (a.luces != 0f || a.sombras != 0f) {
                                val l = (r * 0.2126f + g * 0.7152f + b * 0.0722f).coerceIn(0f, 1f)
                                val d = a.sombras * 0.35f * (1f - l) * (1f - l) + a.luces * 0.35f * l * l
                                r += d; g += d; b += d
                            }
                            if (a.saturacion != 0f) {
                                val l = r * 0.2126f + g * 0.7152f + b * 0.0722f
                                val s = 1f + a.saturacion
                                r = l + (r - l) * s
                                g = l + (g - l) * s
                                b = l + (b - l) * s
                            }
                            if (a.calidez != 0f) {
                                r += a.calidez * 0.08f
                                b -= a.calidez * 0.08f
                            }
                            if (a.vineta > 0f) {
                                val dx = (xx + 0.5f) / w - 0.5f
                                val d = sqrt(dx * dx + dy * dy)
                                val t = ((d - 0.3f) / 0.45f).coerceIn(0f, 1f)
                                val f = 1f - a.vineta * 0.85f * t * t * (3f - 2f * t)
                                r *= f; g *= f; b *= f
                            }
                            val ir = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val ig = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val ib = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            px[i] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                            i++
                        }
                    }
                }
            }.awaitAll()
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        out
    }

    fun ruta(t: Trazo, w: Float, h: Float): Path {
        val p = Path()
        val pts = t.puntos
        if (pts.isEmpty()) return p
        p.moveTo(pts[0].x * w, pts[0].y * h)
        if (pts.size == 1) p.lineTo(pts[0].x * w + 0.5f, pts[0].y * h + 0.5f)
        for (i in 1 until pts.size) p.lineTo(pts[i].x * w, pts[i].y * h)
        return p
    }

    /** Sello de fecha (y firma arriba), abajo a la izquierda. */
    fun dibujarSello(cv: Canvas, w: Float, h: Float, fecha: String, firma: String) {
        val corto = min(w, h)
        val size = corto * 0.034f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(size * 0.15f, 0f, size * 0.06f, 0xCC000000.toInt())
        }
        val margen = corto * 0.035f
        val lineas = if (firma.isBlank()) listOf(fecha) else listOf(firma.trim(), fecha)
        var y = h - margen - (lineas.size - 1) * size * 1.25f
        for (l in lineas) {
            cv.drawText(l, margen, y, p)
            y += size * 1.25f
        }
    }

    fun textoSello(ms: Long): String =
        java.text.SimpleDateFormat("dd/MM/yyyy  HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    /** Texto, dibujo, mosaico y sello encima de la foto ya editada. */
    fun capas(src: Bitmap, e: Edicion): Bitmap {
        if (e.textos.isEmpty() && e.trazos.isEmpty() && e.mosaicos.isEmpty() && !e.sello) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val corto = min(w, h)
        if (e.mosaicos.isNotEmpty()) {
            val bloque = max(8, (max(w, h) / 45f).toInt())
            val peq = Bitmap.createScaledBitmap(src, max(1, w.toInt() / bloque), max(1, h.toInt() / bloque), false)
            val pix = Bitmap.createScaledBitmap(peq, w.toInt(), h.toInt(), false)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(pix, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            e.mosaicos.forEach { t ->
                p.strokeWidth = t.ancho * corto
                cv.drawPath(ruta(t, w, h), p)
            }
        }
        if (e.trazos.isNotEmpty()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            e.trazos.forEach { t ->
                p.color = t.color
                p.strokeWidth = t.ancho * corto
                cv.drawPath(ruta(t, w, h), p)
            }
        }
        e.textos.forEach { t ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.color
                textSize = t.tam * corto
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0xAA000000.toInt())
            }
            cv.drawText(t.texto, t.x * w, t.y * h - (p.descent() + p.ascent()) / 2f, p)
        }
        if (e.sello) dibujarSello(cv, w, h, textoSello(e.fechaSello), e.firma)
        return out
    }

    /** Toda la edición: geometría → recorte → ajustes → filtro → desenfoque → capas. */
    suspend fun aplicar(src: Bitmap, e: Edicion, ctx: android.content.Context? = null): Bitmap {
        val geo = withContext(Dispatchers.Default) { geometria(src, e.geo) }
        val rec = withContext(Dispatchers.Default) { recortar(geo, e.geo.recorte) }
        val aj = ajustar(rec, e.aj)
        val est = e.estilo
        val masc = if (e.quiereMascara && ctx != null) Desenfoques.mascaraSujeto(ctx, rec) else null
        val fil0 = if (est != null) Fx.apply(aj, FxUniforms.from(est, e.estiloAj)) else aj
        val fil = if (est != null && e.sujetoColor && masc != null) Desenfoques.combinar(fil0, aj, masc) else fil0
        val des = if (e.desenf.activo) {
            Desenfoques.aplicar(fil, e.desenf, if (e.desenf.auto) masc else null)
        } else fil
        return withContext(Dispatchers.Default) { capas(des, e) }
    }
}

// ---------- Proporciones de recorte ----------

private data class Proporcion(val nombre: String, val valor: Float?) // ancho/alto; null = libre

private val proporciones = listOf(
    Proporcion("Libre", null),
    Proporcion("Original", -1f),
    Proporcion("1:1", 1f),
    Proporcion("4:3", 4f / 3f),
    Proporcion("3:4", 3f / 4f),
    Proporcion("16:9", 16f / 9f),
    Proporcion("9:16", 9f / 16f)
)

/** Recorte centrado lo más grande posible con la proporción pedida. */
private fun recorteCentrado(imgW: Int, imgH: Int, ratio: Float): Recorte {
    val imgRatio = imgW.toFloat() / imgH
    return if (imgRatio > ratio) {
        val cw = ratio / imgRatio
        Recorte((1f - cw) / 2f, 0f, (1f + cw) / 2f, 1f)
    } else {
        val ch = imgRatio / ratio
        Recorte(0f, (1f - ch) / 2f, 1f, (1f + ch) / 2f)
    }
}

// ---------- Pantalla del editor ----------

private enum class Pestana(val titulo: String) {
    RECORTAR("Recortar"), AJUSTES("Ajustes"), FILTROS("Filtros"),
    TEXTO("Texto"), DIBUJO("Dibujo"), MOSAICO("Mosaico"), DESENFOQUE("Desenfoque"), SELLO("Sello")
}

private val coloresDibujo = listOf(
    Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFD54F),
    Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFE0B464)
)

@Composable
fun EditorScreen(a: MainActivity, media: Media) {
    var ed by remember { mutableStateOf(Edicion(firma = a.firma.value, fechaSello = media.date)) }
    var colorDibujo by remember { mutableStateOf(Color.White) }
    var grosor by remember { mutableFloatStateOf(0.012f) }
    var grosorMosaico by remember { mutableFloatStateOf(0.06f) }
    var textoNuevo by remember { mutableStateOf("") }
    var textoSel by remember { mutableIntStateOf(-1) }
    var pestana by remember { mutableStateOf(Pestana.RECORTAR) }
    var proporcion by remember { mutableIntStateOf(0) }
    var grosorDesenf by remember { mutableFloatStateOf(0.10f) }
    var pincelEnfoca by remember { mutableStateOf(false) }
    var estadoMascara by remember { mutableIntStateOf(0) } // 0 nada, 1 detectando, 2 listo, 3 falló

    BackHandler { a.atras() }

    // Imagen base reducida para trabajar rápido
    val base by produceState<Bitmap?>(null, media.uri) {
        value = withContext(Dispatchers.IO) { a.decodificar(media.uri, 1600) }
    }
    // Geometría (giro/volteo/enderezar) para la vista de recorte
    val geo by produceState<Bitmap?>(null, base, ed.geo.rot, ed.geo.flip, ed.geo.angulo) {
        val b = base
        if (b != null) {
            delay(30)
            value = withContext(Dispatchers.Default) { Edit.geometria(b, ed.geo.copy(recorte = Recorte())) }
        }
    }
    // Máscara del sujeto (fondo automático / sujeto a color), sobre la foto ya recortada
    val quiereMascara = ed.quiereMascara
    val mascaraAuto by produceState<Bitmap?>(null, geo, ed.geo.recorte, quiereMascara) {
        val g = geo
        if (!quiereMascara) {
            value = null
            estadoMascara = 0
        } else if (g != null) {
            estadoMascara = 1
            val m = Desenfoques.mascaraSujeto(a, withContext(Dispatchers.Default) { Edit.recortar(g, ed.geo.recorte) })
            value = m
            estadoMascara = if (m == null) 3 else 2
        }
    }
    // Resultado final (recortado, ajustado y con filtro)
    val filtrada by produceState<Bitmap?>(null, geo, ed.geo.recorte, ed.aj, ed.estilo, ed.estiloAj, ed.sujetoColor, mascaraAuto) {
        val g = geo
        if (g != null) {
            delay(60)
            val rec = withContext(Dispatchers.Default) { Edit.recortar(g, ed.geo.recorte) }
            val aj = Edit.ajustar(rec, ed.aj)
            val est = ed.estilo
            val m = mascaraAuto
            val fil = if (est != null) Fx.apply(aj, FxUniforms.from(est, ed.estiloAj)) else aj
            value = if (est != null && ed.sujetoColor && m != null) Desenfoques.combinar(fil, aj, m) else fil
        }
    }
    val desenfocada by produceState<Bitmap?>(null, filtrada, ed.desenf, mascaraAuto) {
        val f = filtrada
        if (f != null) {
            if (!ed.desenf.activo) {
                value = f
            } else {
                delay(80)
                value = Desenfoques.aplicar(f, ed.desenf, if (ed.desenf.auto) mascaraAuto else null)
            }
        }
    }
    val vista by produceState<Bitmap?>(null, desenfocada, ed.textos, ed.trazos, ed.mosaicos, ed.sello, ed.firma) {
        val f = desenfocada
        if (f != null) value = withContext(Dispatchers.Default) { Edit.capas(f, ed) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Barra superior
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).clickable { a.atras() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Text(
                "Editar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Button(
                onClick = { a.guardarEdicion(media, ed) },
                enabled = base != null && a.guardando.intValue == 0,
                colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black)
            ) {
                if (a.guardando.intValue > 0) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Guardar copia", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        // Imagen
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val g = geo
            if (pestana == Pestana.RECORTAR && g != null) {
                val ratio = proporciones[proporcion].valor?.let { if (it < 0f) g.width.toFloat() / g.height else it }
                VistaRecorte(g, ed.geo.recorte, ratio) { nuevo ->
                    ed = ed.copy(geo = ed.geo.copy(recorte = nuevo))
                }
            } else if (pestana == Pestana.TEXTO || pestana == Pestana.DIBUJO || pestana == Pestana.MOSAICO || pestana == Pestana.DESENFOQUE) {
                val img = vista ?: filtrada
                if (img != null) {
                    VistaCapas(
                        img = img,
                        modo = pestana,
                        ed = ed,
                        color = colorDibujo,
                        grosor = when (pestana) {
                            Pestana.MOSAICO -> grosorMosaico
                            Pestana.DESENFOQUE -> grosorDesenf
                            else -> grosor
                        },
                        enfoca = pincelEnfoca,
                        textoSel = textoSel,
                        onTextoSel = { textoSel = it },
                        onCambio = { ed = it }
                    )
                }
            } else {
                val img = vista ?: base
                if (img != null) {
                    Image(img.asImageBitmap(), "Vista previa", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (base == null) CircularProgressIndicator(color = Color.White)
        }

        // Pestañas
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(Pestana.entries) { p ->
                val sel = p == pestana
                Text(
                    p.titulo,
                    color = if (sel) Acento else Suave,
                    fontSize = 16.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { pestana = p }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Herramientas de cada pestaña
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            when (pestana) {
                Pestana.RECORTAR -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(proporciones.size) { i ->
                            Pildora(proporciones[i].nombre, i == proporcion) {
                                proporcion = i
                                val g = geo
                                val v = proporciones[i].valor
                                if (g != null) {
                                    val nuevo = when {
                                        v == null -> ed.geo.recorte
                                        v < 0f -> Recorte()
                                        else -> recorteCentrado(g.width, g.height, v)
                                    }
                                    ed = ed.copy(geo = ed.geo.copy(recorte = nuevo))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Herramienta(Icons.Rounded.RotateRight, "Girar") {
                            ed = ed.copy(geo = ed.geo.copy(rot = (ed.geo.rot + 1) % 4, recorte = Recorte()))
                            proporcion = 0
                        }
                        Herramienta(Icons.Rounded.Flip, "Voltear") {
                            ed = ed.copy(geo = ed.geo.copy(flip = !ed.geo.flip))
                        }
                        Herramienta(Icons.Rounded.RestartAlt, "Restablecer") {
                            ed = ed.copy(geo = Geo())
                            proporcion = 0
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Deslizador("Enderezar", ed.geo.angulo, -45f..45f, formato = { "${it.roundToInt()}°" }) {
                        ed = ed.copy(geo = ed.geo.copy(angulo = it))
                    }
                }

                Pestana.AJUSTES -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
                ) {
                    val aj = ed.aj
                    Deslizador("Exposición", aj.exposicion, -1f..1f) { ed = ed.copy(aj = aj.copy(exposicion = it)) }
                    Deslizador("Brillo", aj.brillo, -1f..1f) { ed = ed.copy(aj = aj.copy(brillo = it)) }
                    Deslizador("Contraste", aj.contraste, -1f..1f) { ed = ed.copy(aj = aj.copy(contraste = it)) }
                    Deslizador("Saturación", aj.saturacion, -1f..1f) { ed = ed.copy(aj = aj.copy(saturacion = it)) }
                    Deslizador("Calidez", aj.calidez, -1f..1f) { ed = ed.copy(aj = aj.copy(calidez = it)) }
                    Deslizador("Luces", aj.luces, -1f..1f) { ed = ed.copy(aj = aj.copy(luces = it)) }
                    Deslizador("Sombras", aj.sombras, -1f..1f) { ed = ed.copy(aj = aj.copy(sombras = it)) }
                    Deslizador("Viñeta", aj.vineta, 0f..1f) { ed = ed.copy(aj = aj.copy(vineta = it)) }
                    Row {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Restablecer", color = Acento, fontSize = 15.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable { ed = ed.copy(aj = AjustesFoto()) }.padding(10.dp)
                        )
                    }
                }

                Pestana.FILTROS -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        item {
                            Pildora("Ninguno", ed.estilo == null) { ed = ed.copy(estilo = null) }
                        }
                        items(Estilo.entries) { e ->
                            Pildora(e.nombre, ed.estilo == e) {
                                ed = ed.copy(estilo = e, estiloAj = Ajustes.de(e, ed.estiloAj))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (ed.estilo != null) {
                        val ea = ed.estiloAj
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pildora("Sujeto a color", ed.sujetoColor) { ed = ed.copy(sujetoColor = !ed.sujetoColor) }
                            if (ed.sujetoColor) {
                                Text(
                                    when (estadoMascara) {
                                        1 -> "Detectando…"
                                        3 -> "No se encontró sujeto"
                                        else -> "El filtro va solo en el fondo"
                                    },
                                    color = Suave, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Deslizador("Intensidad", ea.intensidad, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(intensidad = it)) }
                        Deslizador("Rojos", ea.rojos, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(rojos = it)) }
                        Deslizador("Estructura", ea.estructura, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(estructura = it)) }
                        Deslizador("Virado", ea.virado, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(virado = it)) }
                        Deslizador("Halación", ea.halacion, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(halacion = it)) }
                        Deslizador("Grano", ea.grano, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(grano = it)) }
                    } else {
                        Text(
                            "Elige un estilo de Claroscuro: Dramático, Cianotipo, Noir…",
                            color = Suave, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                        )
                    }
                }

                Pestana.TEXTO -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = textoNuevo,
                            onValueChange = { textoNuevo = it.take(60) },
                            singleLine = true,
                            placeholder = { Text("Escribe un texto") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (textoNuevo.isNotBlank()) {
                                    ed = ed.copy(textos = ed.textos + TextoCapa(textoNuevo.trim(), 0.5f, 0.5f, 0.07f, colorDibujo.toArgb()))
                                    textoSel = ed.textos.lastIndex
                                    textoNuevo = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black)
                        ) { Text("Agregar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    SelectorColor(colorDibujo) { c ->
                        colorDibujo = c
                        val i = textoSel
                        if (i in ed.textos.indices) {
                            ed = ed.copy(textos = ed.textos.toMutableList().also { it[i] = it[i].copy(color = c.toArgb()) })
                        }
                    }
                    val i = textoSel
                    if (i in ed.textos.indices) {
                        Deslizador("Tamaño", ed.textos[i].tam, 0.03f..0.2f, formato = { "${(it * 100).roundToInt()}" }) { v ->
                            ed = ed.copy(textos = ed.textos.toMutableList().also { it[i] = it[i].copy(tam = v) })
                        }
                        Text(
                            "Eliminar este texto", color = Color(0xFFFF6B6B), fontSize = 15.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                ed = ed.copy(textos = ed.textos.filterIndexed { k, _ -> k != i })
                                textoSel = -1
                            }.padding(10.dp)
                        )
                    }
                    Text("Arrastra el texto sobre la foto para moverlo.", color = Suave, fontSize = 13.sp)
                }

                Pestana.DIBUJO -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    SelectorColor(colorDibujo) { colorDibujo = it }
                    Deslizador("Grosor", grosor, 0.003f..0.04f, formato = { "${(it * 1000).roundToInt()}" }) { grosor = it }
                    Herramienta(Icons.AutoMirrored.Rounded.Undo, "Deshacer") {
                        if (ed.trazos.isNotEmpty()) ed = ed.copy(trazos = ed.trazos.dropLast(1))
                    }
                    Text("Dibuja con el dedo sobre la foto.", color = Suave, fontSize = 13.sp)
                }

                Pestana.MOSAICO -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Deslizador("Grosor", grosorMosaico, 0.02f..0.15f, formato = { "${(it * 100).roundToInt()}" }) { grosorMosaico = it }
                    Herramienta(Icons.AutoMirrored.Rounded.Undo, "Deshacer") {
                        if (ed.mosaicos.isNotEmpty()) ed = ed.copy(mosaicos = ed.mosaicos.dropLast(1))
                    }
                    Text("Pasa el dedo sobre caras, placas o datos para taparlos.", color = Suave, fontSize = 13.sp)
                }

                Pestana.DESENFOQUE -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
                ) {
                    val d = ed.desenf
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pildora("Fondo automático", d.auto) { ed = ed.copy(desenf = d.copy(auto = !d.auto)) }
                        Pildora("Lente", d.lente) { ed = ed.copy(desenf = d.copy(lente = true)) }
                        Pildora("Suave", !d.lente) { ed = ed.copy(desenf = d.copy(lente = false)) }
                    }
                    val estado = when {
                        !d.auto -> "Pinta con el dedo lo que quieras desenfocar, o activa «Fondo automático»."
                        estadoMascara == 1 -> "Detectando al sujeto… (la primera vez Google descarga el modelo)"
                        estadoMascara == 3 -> "No se pudo detectar al sujeto. Puedes usar el pincel."
                        else -> "Sujeto detectado. Corrige con «Enfocar» donde haga falta (pelo, bordes)."
                    }
                    Text(estado, color = Suave, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Deslizador("Fuerza", d.fuerza, 0f..1f) { ed = ed.copy(desenf = d.copy(fuerza = it)) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pildora("Pincel: desenfocar", !pincelEnfoca) { pincelEnfoca = false }
                        Pildora("Pincel: enfocar", pincelEnfoca) { pincelEnfoca = true }
                    }
                    Deslizador("Grosor", grosorDesenf, 0.03f..0.25f, formato = { "${(it * 100).roundToInt()}" }) { grosorDesenf = it }
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Herramienta(Icons.AutoMirrored.Rounded.Undo, "Deshacer") {
                            if (d.pinceles.isNotEmpty()) ed = ed.copy(desenf = d.copy(pinceles = d.pinceles.dropLast(1)))
                        }
                        Herramienta(Icons.Rounded.RestartAlt, "Quitar todo") {
                            ed = ed.copy(desenf = Desenfoque())
                        }
                    }
                }

                Pestana.SELLO -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pildora("Sin sello", !ed.sello) { ed = ed.copy(sello = false) }
                        Pildora("Con fecha y firma", ed.sello) { ed = ed.copy(sello = true) }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ed.firma,
                        onValueChange = { ed = ed.copy(firma = it.take(30)) },
                        singleLine = true,
                        label = { Text("Firma (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Fecha de la foto: ${Edit.textoSello(media.date)}",
                        color = Suave, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ---------- Vista de recorte con esquinas arrastrables ----------

private enum class Asa { NADA, MOVER, SI, SD, II, ID } // superior/inferior, izquierda/derecha

@Composable
private fun VistaRecorte(img: Bitmap, rec: Recorte, ratio: Float?, onCambio: (Recorte) -> Unit) {
    val density = LocalDensity.current
    val tolPx = with(density) { 36.dp.toPx() }
    var asa by remember { mutableStateOf(Asa.NADA) }
    var actual by remember(rec) { mutableStateOf(rec) }
    val imagen = remember(img) { img.asImageBitmap() }

    // Dónde se dibuja la imagen dentro del área (ajustada sin deformar)
    fun destino(area: Size): Pair<Offset, Size> {
        val s = min(area.width / img.width, area.height / img.height)
        val w = img.width * s
        val h = img.height * s
        return Offset((area.width - w) / 2f, (area.height - h) / 2f) to Size(w, h)
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(img, ratio) {
                detectDragGestures(
                    onDragStart = { p ->
                        val (o, s) = destino(Size(size.width.toFloat(), size.height.toFloat()))
                        val r = actual
                        val x0 = o.x + r.l * s.width
                        val x1 = o.x + r.r * s.width
                        val y0 = o.y + r.t * s.height
                        val y1 = o.y + r.b * s.height
                        fun cerca(x: Float, y: Float) = abs(p.x - x) < tolPx && abs(p.y - y) < tolPx
                        asa = when {
                            cerca(x0, y0) -> Asa.SI
                            cerca(x1, y0) -> Asa.SD
                            cerca(x0, y1) -> Asa.II
                            cerca(x1, y1) -> Asa.ID
                            p.x in x0..x1 && p.y in y0..y1 -> Asa.MOVER
                            else -> Asa.NADA
                        }
                    },
                    onDragEnd = { asa = Asa.NADA },
                    onDragCancel = { asa = Asa.NADA },
                    onDrag = { change, d ->
                        if (asa == Asa.NADA) return@detectDragGestures
                        change.consume()
                        val (_, s) = destino(Size(size.width.toFloat(), size.height.toFloat()))
                        val dx = d.x / s.width
                        val dy = d.y / s.height
                        val r = actual
                        val minimo = 0.08f
                        var n = when (asa) {
                            Asa.MOVER -> {
                                val w = r.r - r.l
                                val h = r.b - r.t
                                val l = (r.l + dx).coerceIn(0f, 1f - w)
                                val t = (r.t + dy).coerceIn(0f, 1f - h)
                                Recorte(l, t, l + w, t + h)
                            }
                            Asa.SI -> r.copy(l = (r.l + dx).coerceIn(0f, r.r - minimo), t = (r.t + dy).coerceIn(0f, r.b - minimo))
                            Asa.SD -> r.copy(r = (r.r + dx).coerceIn(r.l + minimo, 1f), t = (r.t + dy).coerceIn(0f, r.b - minimo))
                            Asa.II -> r.copy(l = (r.l + dx).coerceIn(0f, r.r - minimo), b = (r.b + dy).coerceIn(r.t + minimo, 1f))
                            Asa.ID -> r.copy(r = (r.r + dx).coerceIn(r.l + minimo, 1f), b = (r.b + dy).coerceIn(r.t + minimo, 1f))
                            Asa.NADA -> r
                        }
                        // Proporción fija: el alto sigue al ancho, anclado en la esquina contraria
                        if (ratio != null && asa != Asa.MOVER) {
                            val k = img.width.toFloat() / img.height / ratio // alto normalizado por ancho normalizado
                            var w = n.r - n.l
                            var h = w * k
                            val arriba = asa == Asa.SI || asa == Asa.SD
                            val maxH = if (arriba) n.b else 1f - n.t
                            if (h > maxH) {
                                h = maxH
                                w = h / k
                            }
                            val izquierda = asa == Asa.SI || asa == Asa.II
                            val l = if (izquierda) n.r - w else n.l
                            val rr = if (izquierda) n.r else n.l + w
                            n = if (arriba) Recorte(l, n.b - h, rr, n.b) else Recorte(l, n.t, rr, n.t + h)
                        }
                        actual = n
                        onCambio(n)
                    }
                )
            }
    ) {
        val (o, s) = destino(size)
        drawImage(
            imagen,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize(s.width.roundToInt(), s.height.roundToInt())
        )
        val r = actual
        val x0 = o.x + r.l * s.width
        val x1 = o.x + r.r * s.width
        val y0 = o.y + r.t * s.height
        val y1 = o.y + r.b * s.height
        val sombra = Color(0xAA000000)
        // Oscurecer lo que queda fuera del recorte
        drawRect(sombra, Offset(o.x, o.y), Size(s.width, y0 - o.y))
        drawRect(sombra, Offset(o.x, y1), Size(s.width, o.y + s.height - y1))
        drawRect(sombra, Offset(o.x, y0), Size(x0 - o.x, y1 - y0))
        drawRect(sombra, Offset(x1, y0), Size(o.x + s.width - x1, y1 - y0))
        // Marco y cuadrícula de tercios
        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(width = 3f))
        val guia = Color(0x66FFFFFF)
        for (i in 1..2) {
            val gx = x0 + (x1 - x0) * i / 3f
            val gy = y0 + (y1 - y0) * i / 3f
            drawLine(guia, Offset(gx, y0), Offset(gx, y1), strokeWidth = 1.5f)
            drawLine(guia, Offset(x0, gy), Offset(x1, gy), strokeWidth = 1.5f)
        }
        // Esquinas gruesas
        val L = 48f
        val e = 9f
        listOf(Offset(x0, y0) to Offset(1f, 1f), Offset(x1, y0) to Offset(-1f, 1f),
            Offset(x0, y1) to Offset(1f, -1f), Offset(x1, y1) to Offset(-1f, -1f)).forEach { (pt, dir) ->
            drawLine(Color.White, pt, Offset(pt.x + L * dir.x, pt.y), strokeWidth = e)
            drawLine(Color.White, pt, Offset(pt.x, pt.y + L * dir.y), strokeWidth = e)
        }
    }
}

// ---------- Piezas pequeñas ----------

@Composable
fun Pildora(texto: String, activo: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        color = if (activo) Color.Black else Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (activo) Acento else Color(0xFF2A2A30))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun Herramienta(icon: ImageVector, texto: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(texto, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Deslizador(
    nombre: String,
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    formato: (Float) -> String = { v ->
        val pct = (v * 100).roundToInt()
        if (rango.start < 0f && pct > 0) "+$pct" else "$pct"
    },
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = valor,
            onValueChange = onChange,
            valueRange = rango,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Acento,
                activeTrackColor = Acento,
                inactiveTrackColor = Color(0x55FFFFFF)
            )
        )
        Text(formato(valor), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}

// ---------- Edición de capas (texto, dibujo, mosaico) sobre la foto ----------

@Composable
private fun SelectorColor(actual: Color, onElegir: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        coloresDibujo.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c)
                    .border(if (c == actual) 3.dp else 1.dp, if (c == actual) Acento else Color(0x66FFFFFF), CircleShape)
                    .clickable { onElegir(c) }
            )
        }
    }
}

@Composable
private fun VistaCapas(
    img: Bitmap,
    modo: Pestana,
    ed: Edicion,
    color: Color,
    grosor: Float,
    enfoca: Boolean = false,
    textoSel: Int,
    onTextoSel: (Int) -> Unit,
    onCambio: (Edicion) -> Unit
) {
    val imagen = remember(img) { img.asImageBitmap() }
    val density = LocalDensity.current
    val tolPx = with(density) { 56.dp.toPx() }
    var enCurso by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var arrastrando by remember { mutableIntStateOf(-1) }
    val edActual = androidx.compose.runtime.rememberUpdatedState(ed)
    val colorActual = androidx.compose.runtime.rememberUpdatedState(color)
    val grosorActual = androidx.compose.runtime.rememberUpdatedState(grosor)
    val enfocaActual = androidx.compose.runtime.rememberUpdatedState(enfoca)

    fun area(w: Float, h: Float): Pair<Offset, Size> {
        val s = min(w / img.width, h / img.height)
        val dw = img.width * s
        val dh = img.height * s
        return Offset((w - dw) / 2f, (h - dh) / 2f) to Size(dw, dh)
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(modo, img) {
                if (modo == Pestana.TEXTO) {
                    detectTapGestures { p ->
                        val (o, s) = area(size.width.toFloat(), size.height.toFloat())
                        val i = edActual.value.textos.indexOfLast { t ->
                            abs(o.x + t.x * s.width - p.x) < tolPx * 2 && abs(o.y + t.y * s.height - p.y) < tolPx
                        }
                        onTextoSel(i)
                    }
                }
            }
            .pointerInput(modo, img) {
                detectDragGestures(
                    onDragStart = { p ->
                        val (o, s) = area(size.width.toFloat(), size.height.toFloat())
                        val n = Offset(((p.x - o.x) / s.width).coerceIn(0f, 1f), ((p.y - o.y) / s.height).coerceIn(0f, 1f))
                        if (modo == Pestana.TEXTO) {
                            val i = edActual.value.textos.indexOfLast { t ->
                                abs(o.x + t.x * s.width - p.x) < tolPx * 2 && abs(o.y + t.y * s.height - p.y) < tolPx
                            }
                            arrastrando = i
                            if (i >= 0) onTextoSel(i)
                        } else {
                            enCurso = listOf(n)
                        }
                    },
                    onDragEnd = {
                        val e = edActual.value
                        if (modo == Pestana.DIBUJO && enCurso.isNotEmpty()) {
                            onCambio(e.copy(trazos = e.trazos + Trazo(enCurso, colorActual.value.toArgb(), grosorActual.value)))
                        } else if (modo == Pestana.MOSAICO && enCurso.isNotEmpty()) {
                            onCambio(e.copy(mosaicos = e.mosaicos + Trazo(enCurso, 0, grosorActual.value)))
                        } else if (modo == Pestana.DESENFOQUE && enCurso.isNotEmpty()) {
                            val d = e.desenf
                            val t = Trazo(enCurso, if (enfocaActual.value) 0 else 1, grosorActual.value)
                            onCambio(e.copy(desenf = d.copy(pinceles = d.pinceles + t)))
                        }
                        enCurso = emptyList()
                        arrastrando = -1
                    },
                    onDragCancel = {
                        enCurso = emptyList()
                        arrastrando = -1
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (o, s) = area(size.width.toFloat(), size.height.toFloat())
                        val p = change.position
                        val n = Offset(((p.x - o.x) / s.width).coerceIn(0f, 1f), ((p.y - o.y) / s.height).coerceIn(0f, 1f))
                        if (modo == Pestana.TEXTO) {
                            val i = arrastrando
                            val e = edActual.value
                            if (i in e.textos.indices) {
                                onCambio(e.copy(textos = e.textos.toMutableList().also { it[i] = it[i].copy(x = n.x, y = n.y) }))
                            }
                        } else {
                            enCurso = enCurso + n
                        }
                    }
                )
            }
    ) {
        val (o, s) = area(size.width, size.height)
        drawImage(
            imagen,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize(s.width.roundToInt(), s.height.roundToInt())
        )
        // Trazo que se está dibujando (se ve al instante)
        if (enCurso.size > 1) {
            val corto = min(s.width, s.height)
            val c = when {
                modo == Pestana.MOSAICO -> Color(0x88808080)
                modo == Pestana.DESENFOQUE && enfoca -> Color(0x8800E5FF)
                modo == Pestana.DESENFOQUE -> Color(0x88FFFFFF)
                else -> color
            }
            for (i in 1 until enCurso.size) {
                drawLine(
                    c,
                    Offset(o.x + enCurso[i - 1].x * s.width, o.y + enCurso[i - 1].y * s.height),
                    Offset(o.x + enCurso[i].x * s.width, o.y + enCurso[i].y * s.height),
                    strokeWidth = grosor * corto,
                    cap = StrokeCap.Round
                )
            }
        }
        // Marca del texto seleccionado
        if (modo == Pestana.TEXTO && textoSel in ed.textos.indices) {
            val t = ed.textos[textoSel]
            val cx = o.x + t.x * s.width
            val cy = o.y + t.y * s.height
            val alto = t.tam * min(s.width, s.height)
            drawRect(
                Acento, Offset(cx - alto * 0.4f * t.texto.length.coerceAtLeast(2), cy - alto * 0.75f),
                Size(alto * 0.8f * t.texto.length.coerceAtLeast(2), alto * 1.5f), style = Stroke(width = 3f)
            )
        }
    }
}
