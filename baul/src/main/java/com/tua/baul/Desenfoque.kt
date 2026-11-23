package com.tua.baul

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Desenfoque de fondo o de zonas.
 * pinceles: Trazo.color = 1 → desenfocar esa zona; 0 → dejarla nítida (corrige al automático).
 */
data class Desenfoque(
    val auto: Boolean = false,
    val fuerza: Float = 0.5f,
    val lente: Boolean = true,
    val pinceles: List<Trazo> = emptyList()
) {
    val activo get() = auto || pinceles.any { it.color == 1 }
}

private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

object Desenfoques {

    private val segmentador by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
        )
    }

    /**
     * Detecta al sujeto (personas, mascotas, objetos) dentro del teléfono.
     * Devuelve una máscara reducida: alfa = 255 donde está el sujeto. null si no se pudo.
     * La primera vez Google Play descarga el modelo (unos segundos).
     */
    suspend fun mascaraSujeto(ctx: Context, src: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val lado = 1024f
        val esc = min(1f, lado / max(src.width, src.height))
        val w = max(1, (src.width * esc).roundToInt())
        val h = max(1, (src.height * esc).roundToInt())
        val peq = if (esc < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val img = InputImage.fromBitmap(peq, 0)
        var pedido = false
        repeat(40) { intento ->
            try {
                val res = segmentador.process(img).esperar()
                val buf = res.foregroundConfidenceMask ?: return@withContext null
                buf.rewind()
                val px = IntArray(w * h)
                for (i in 0 until min(px.size, buf.remaining())) {
                    // Bordes un poco más definidos
                    val t = ((buf.get() - 0.25f) / 0.5f).coerceIn(0f, 1f)
                    val a = (t * t * (3f - 2f * t) * 255f).roundToInt()
                    px[i] = (a shl 24) or 0xFFFFFF
                }
                val m = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                m.setPixels(px, 0, w, 0, 0, w, h)
                return@withContext m
            } catch (e: Exception) {
                // Probablemente el modelo aún no está descargado: lo pedimos y esperamos
                if (!pedido) {
                    pedido = true
                    try {
                        ModuleInstall.getClient(ctx).installModules(
                            ModuleInstallRequest.newBuilder().addApi(segmentador).build()
                        ).esperar()
                    } catch (_: Exception) {
                    }
                }
                if (intento >= 39) return@withContext null
                delay(1500)
            }
        }
        null
    }

    /** Máscara de nitidez (1 = nítido, 0 = desenfocado) a resolución reducida. */
    private fun mascaraNitidez(w: Int, h: Int, d: Desenfoque, auto: Bitmap?): Triple<FloatArray, Int, Int> {
        val esc = min(1f, 1024f / max(w, h))
        val mw = max(1, (w * esc).roundToInt())
        val mh = max(1, (h * esc).roundToInt())
        val m = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(m)
        if (d.auto && auto != null) {
            cv.drawBitmap(auto, Rect(0, 0, auto.width, auto.height), Rect(0, 0, mw, mh), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            cv.drawColor(0xFFFFFFFF.toInt())
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        val corto = min(mw, mh).toFloat()
        d.pinceles.forEach { t ->
            p.color = if (t.color == 1) 0x00000000 else 0xFFFFFFFF.toInt()
            p.strokeWidth = t.ancho * corto
            cv.drawPath(Edit.ruta(t, mw.toFloat(), mh.toFloat()), p)
        }
        val px = IntArray(mw * mh)
        m.getPixels(px, 0, mw, 0, 0, mw, mh)
        val out = FloatArray(mw * mh) { ((px[it] ushr 24) and 0xFF) / 255f }
        return Triple(out, mw, mh)
    }

    /** Muestra bilineal de un canal (o de varios intercalados). */
    private fun muestra(a: FloatArray, w: Int, h: Int, ch: Int, c: Int, x: Float, y: Float): Float {
        val fx = (x - 0.5f).coerceIn(0f, (w - 1).toFloat())
        val fy = (y - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a00 = a[(y0 * w + x0) * ch + c]
        val a10 = a[(y0 * w + x1) * ch + c]
        val a01 = a[(y1 * w + x0) * ch + c]
        val a11 = a[(y1 * w + x1) * ch + c]
        return (a00 + (a10 - a00) * tx) + ((a01 + (a11 - a01) * tx) - (a00 + (a10 - a00) * tx)) * ty
    }

    private fun cajaH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2 * r + 1
        for (y in 0 until h) {
            val o = y * w
            var s = 0f
            for (k in -r..r) s += src[o + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[o + x] = s / n
                s += src[o + (x + r + 1).coerceAtMost(w - 1)] - src[o + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun cajaV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val n = 2 * r + 1
        for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) s += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = s / n
                s += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    /** Desenfoca el fondo (o las zonas pintadas) de la imagen ya editada. */
    suspend fun aplicar(src: Bitmap, d: Desenfoque, auto: Bitmap?): Bitmap = withContext(Dispatchers.Default) {
        if (!d.activo) return@withContext src
        val w = src.width
        val h = src.height
        val (msk, mw, mh) = mascaraNitidez(w, h, d, auto)

        // Imagen pequeña donde se hace el desenfoque (radio relativo a la foto)
        val rRel = 0.006f + d.fuerza * 0.04f
        val lado = (8f / rRel).roundToInt().coerceIn(120, 640)
        val esc = min(1f, lado.toFloat() / max(w, h))
        val sw = max(1, (w * esc).roundToInt())
        val sh = max(1, (h * esc).roundToInt())
        val radio = rRel * max(sw, sh)
        val peq = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        peq.getPixels(sp, 0, sw, 0, 0, sw, sh)
        val n = sw * sh

        // Peso de fondo: lo nítido no se "derrama" sobre el fondo (sin halos alrededor del sujeto)
        val peso = FloatArray(n)
        for (y in 0 until sh) for (x in 0 until sw) {
            val s = muestra(msk, mw, mh, 1, 0, (x + 0.5f) * mw / sw, (y + 0.5f) * mh / sh)
            peso[y * sw + x] = 1f - s
        }
        val orig = FloatArray(n * 3)
        for (i in 0 until n) {
            val p = sp[i]
            orig[i * 3] = ((p shr 16) and 0xFF) / 255f
            orig[i * 3 + 1] = ((p shr 8) and 0xFF) / 255f
            orig[i * 3 + 2] = (p and 0xFF) / 255f
        }
        val bor = FloatArray(n * 3)

        if (d.lente) {
            // Desenfoque de lente: disco, en luz "lineal", con brillos que se vuelven círculos
            val lin = FloatArray(n * 3)
            for (i in 0 until n) {
                val r = orig[i * 3]; val g = orig[i * 3 + 1]; val b = orig[i * 3 + 2]
                val l = r * 0.2126f + g * 0.7152f + b * 0.0722f
                val t = ((l - 0.72f) / 0.28f).coerceIn(0f, 1f)
                val realce = 1f + 5f * t * t * peso[i]
                lin[i * 3] = r * r * realce
                lin[i * 3 + 1] = g * g * realce
                lin[i * 3 + 2] = b * b * realce
            }
            val R = ceil(radio).toInt()
            val dx = ArrayList<Int>()
            val dy = ArrayList<Int>()
            for (yy in -R..R) for (xx in -R..R) {
                if (xx * xx + yy * yy <= radio * radio) { dx.add(xx); dy.add(yy) }
            }
            val ox = dx.toIntArray()
            val oy = dy.toIntArray()
            val partes = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val filas = (sh + partes - 1) / partes
            coroutineScope {
                (0 until partes).map { c ->
                    async {
                        for (y in c * filas until min(sh, (c + 1) * filas)) for (x in 0 until sw) {
                            var ar = 0f; var ag = 0f; var ab = 0f; var ws = 0f
                            for (k in ox.indices) {
                                val xx = (x + ox[k]).coerceIn(0, sw - 1)
                                val yy = (y + oy[k]).coerceIn(0, sh - 1)
                                val j = yy * sw + xx
                                val pw = peso[j]
                                if (pw > 0.002f) {
                                    ar += lin[j * 3] * pw; ag += lin[j * 3 + 1] * pw; ab += lin[j * 3 + 2] * pw; ws += pw
                                }
                            }
                            val i = y * sw + x
                            if (ws < 0.01f) {
                                bor[i * 3] = orig[i * 3]; bor[i * 3 + 1] = orig[i * 3 + 1]; bor[i * 3 + 2] = orig[i * 3 + 2]
                            } else {
                                bor[i * 3] = sqrt(ar / ws).coerceAtMost(1f)
                                bor[i * 3 + 1] = sqrt(ag / ws).coerceAtMost(1f)
                                bor[i * 3 + 2] = sqrt(ab / ws).coerceAtMost(1f)
                            }
                        }
                    }
                }.awaitAll()
            }
        } else {
            // Desenfoque suave (parecido a gaussiano: tres pasadas de caja)
            val rc = max(1, (radio / 1.7f).roundToInt())
            val canales = Array(4) { FloatArray(n) }
            for (i in 0 until n) {
                val pw = peso[i]
                canales[0][i] = orig[i * 3] * pw
                canales[1][i] = orig[i * 3 + 1] * pw
                canales[2][i] = orig[i * 3 + 2] * pw
                canales[3][i] = pw
            }
            coroutineScope {
                canales.map { a ->
                    async {
                        val tmp = FloatArray(n)
                        repeat(3) {
                            cajaH(a, tmp, sw, sh, rc)
                            cajaV(tmp, a, sw, sh, rc)
                        }
                    }
                }.awaitAll()
            }
            for (i in 0 until n) {
                val ws = canales[3][i]
                if (ws < 0.01f) {
                    bor[i * 3] = orig[i * 3]; bor[i * 3 + 1] = orig[i * 3 + 1]; bor[i * 3 + 2] = orig[i * 3 + 2]
                } else {
                    bor[i * 3] = (canales[0][i] / ws).coerceIn(0f, 1f)
                    bor[i * 3 + 1] = (canales[1][i] / ws).coerceIn(0f, 1f)
                    bor[i * 3 + 2] = (canales[2][i] / ws).coerceIn(0f, 1f)
                }
            }
        }

        // Mezcla final a tamaño completo
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val partes = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val filas = (h + partes - 1) / partes
        val kx = mw.toFloat() / w
        val ky = mh.toFloat() / h
        val bx = sw.toFloat() / w
        val by = sh.toFloat() / h
        coroutineScope {
            (0 until partes).map { c ->
                async {
                    for (y in c * filas until min(h, (c + 1) * filas)) {
                        val cy = y + 0.5f
                        var i = y * w
                        for (x in 0 until w) {
                            val cx = x + 0.5f
                            val s = muestra(msk, mw, mh, 1, 0, cx * kx, cy * ky)
                            if (s < 0.996f) {
                                val p = px[i]
                                val t = 1f - s
                                val r0 = ((p shr 16) and 0xFF) / 255f
                                val g0 = ((p shr 8) and 0xFF) / 255f
                                val b0 = (p and 0xFF) / 255f
                                val r1 = muestra(bor, sw, sh, 3, 0, cx * bx, cy * by)
                                val g1 = muestra(bor, sw, sh, 3, 1, cx * bx, cy * by)
                                val b1 = muestra(bor, sw, sh, 3, 2, cx * bx, cy * by)
                                val ir = ((r0 + (r1 - r0) * t) * 255f + 0.5f).toInt().coerceIn(0, 255)
                                val ig = ((g0 + (g1 - g0) * t) * 255f + 0.5f).toInt().coerceIn(0, 255)
                                val ib = ((b0 + (b1 - b0) * t) * 255f + 0.5f).toInt().coerceIn(0, 255)
                                px[i] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                            }
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
}
