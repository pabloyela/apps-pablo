package com.tua.comun

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cada estilo define cómo se convierte el color a gris (mezcla de canales,
 * como los filtros de color de las cámaras de rollo) y el "carácter" de la imagen.
 */
enum class Estilo(
    val nombre: String,
    val mix: FloatArray,
    val contraste: Float = 0f,
    val curva: Float = 0.15f,
    val gamma: Float = 1f,
    val fade: Float = 0f,
    val grano: Float = 0f,
    val vineta: Float = 0f,
    val tono: FloatArray? = null,
    val tonoCantidad: Float = 0f,
    val color: Float = 0f,      // cuánto color original conserva (0 = B/N puro)
    val rojos: Float = 0f,      // cuánto destacan los rojos (0..1)
    val estructura: Float = 0f, // contraste local: "esculpe" texturas y músculos (0..1)
    val virado: Float = 0f,     // sombras frías y luces cálidas (0..1)
    val halacion: Float = 0f    // brillo rojizo alrededor de las luces fuertes (0..1)
) {
    CLASICO("Clásico", floatArrayOf(0.2126f, 0.7152f, 0.0722f)),
    NOIR(
        "Noir", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.35f, curva = 0.6f, gamma = 1.2f, grano = 0.1f, vineta = 0.55f
    ),
    DRAMATICO(
        "Dramático", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.22f, curva = 0.45f, gamma = 1.1f, fade = 0.02f, grano = 0.06f, vineta = 0.6f,
        color = 0.28f
    ),
    GRIT(
        "Grit", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.12f, curva = 0.35f, gamma = 1.05f, grano = 0.05f, vineta = 0.45f,
        color = 0.45f, estructura = 0.8f
    ),
    CINE(
        "Cine oscuro", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.08f, curva = 0.3f, gamma = 1.2f, fade = 0.05f, vineta = 0.5f,
        color = 0.35f, rojos = 0.8f, estructura = 0.2f, virado = 0.8f
    ),
    CINE_PELICULA(
        "Cine película", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.08f, curva = 0.3f, gamma = 1.2f, fade = 0.05f, grano = 0.2f, vineta = 0.5f,
        color = 0.35f, rojos = 0.8f, estructura = 0.2f, virado = 0.8f, halacion = 0.6f
    ),
    ACERO(
        "Acero", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.3f, curva = 0.5f, gamma = 1.05f, vineta = 0.4f,
        tono = floatArrayOf(0.46f, 0.50f, 0.55f), tonoCantidad = 0.6f,
        color = 0.3f, estructura = 0.45f
    ),
    DUOTONO(
        "Duotono rojo", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.2f, curva = 0.4f, vineta = 0.4f,
        tono = floatArrayOf(0.68f, 0.07f, 0.07f), tonoCantidad = 1f
    ),
    ROJO(
        "Filtro rojo", floatArrayOf(0.85f, 0.25f, -0.10f),
        contraste = 0.15f, curva = 0.35f, gamma = 1.05f, vineta = 0.15f
    ),
    AMARILLO(
        "Filtro amarillo", floatArrayOf(0.45f, 0.50f, 0.05f),
        contraste = 0.05f, curva = 0.2f
    ),
    VERDE(
        "Filtro verde", floatArrayOf(0.15f, 0.80f, 0.05f),
        contraste = 0.05f, curva = 0.2f
    ),
    INFRARROJO(
        "Infrarrojo", floatArrayOf(0.5f, 1.1f, -0.6f),
        contraste = 0.2f, curva = 0.35f, grano = 0.08f, vineta = 0.3f
    ),
    PELICULA(
        "Película", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.1f, curva = 0.3f, fade = 0.06f, grano = 0.45f, vineta = 0.25f
    ),
    SELECTIVO(
        "Rojo selectivo", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.1f, curva = 0.25f, vineta = 0.15f, rojos = 1f
    ),
    SEPIA(
        "Sepia", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        curva = 0.2f, fade = 0.03f, grano = 0.1f, vineta = 0.25f,
        tono = floatArrayOf(0.66f, 0.48f, 0.32f), tonoCantidad = 0.9f
    ),
    CIANOTIPO(
        "Cianotipo", floatArrayOf(0.2126f, 0.7152f, 0.0722f),
        contraste = 0.05f, curva = 0.2f, fade = 0.02f, vineta = 0.2f,
        tono = floatArrayOf(0.30f, 0.52f, 0.82f), tonoCantidad = 1f
    );
}

/** Ajustes que el usuario mueve con los deslizadores. */
data class Ajustes(
    val contraste: Float = 0f,  // -1..1
    val brillo: Float = 0f,     // -1..1
    val grano: Float = 0f,      // 0..1
    val vineta: Float = 0f,     // 0..1
    val intensidad: Float = 1f, // 0 = foto original, 1 = efecto completo
    val rojos: Float = 0f,      // 0..1
    val estructura: Float = 0f, // 0..1
    val virado: Float = 0f,     // 0..1
    val halacion: Float = 0f    // 0..1
) {
    companion object {
        fun de(e: Estilo, base: Ajustes = Ajustes()) =
            base.copy(
                grano = e.grano, vineta = e.vineta, rojos = e.rojos,
                estructura = e.estructura, virado = e.virado, halacion = e.halacion
            )
    }
}

/** Valores finales que usan el shader (visor) y el procesador (foto). */
data class FxUniforms(
    val mixR: Float, val mixG: Float, val mixB: Float,
    val contrast: Float,
    val brightness: Float,
    val curve: Float,
    val gamma: Float,
    val fade: Float,
    val toneR: Float, val toneG: Float, val toneB: Float,
    val toneAmount: Float,
    val colorKeep: Float,
    val red: Float,
    val intensity: Float,
    val structure: Float,
    val splitTone: Float,
    val halation: Float,
    val grain: Float,
    val vignette: Float
) {
    companion object {
        fun from(e: Estilo, a: Ajustes): FxUniforms {
            val t = e.tono ?: floatArrayOf(0.5f, 0.5f, 0.5f)
            return FxUniforms(
                mixR = e.mix[0], mixG = e.mix[1], mixB = e.mix[2],
                contrast = e.contraste + a.contraste * 0.8f,
                brightness = a.brillo * 0.25f,
                curve = e.curva,
                gamma = e.gamma,
                fade = e.fade,
                toneR = t[0], toneG = t[1], toneB = t[2],
                toneAmount = if (e.tono != null) e.tonoCantidad else 0f,
                colorKeep = e.color,
                red = a.rojos,
                intensity = a.intensidad,
                structure = a.estructura,
                splitTone = a.virado,
                halation = a.halacion,
                grain = a.grano,
                vignette = a.vineta
            )
        }
    }
}

/** Cantidad de "granos" a lo ancho de la imagen (igual en visor y foto). */
const val GRANOS_ANCHO = 650f

private fun fract(x: Float) = x - floor(x)

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Mismo hash que el shader (hash12 de Dave Hoskins). */
private fun hash12(x: Float, y: Float): Float {
    var px = fract(x * 0.1031f)
    var py = fract(y * 0.1031f)
    var pz = fract(x * 0.1031f)
    val d = px * (py + 33.33f) + py * (pz + 33.33f) + pz * (px + 33.33f)
    px += d; py += d; pz += d
    return fract((px + py) * pz)
}

object Fx {

    private fun toneCurve(y0: Float, u: FxUniforms): Float {
        var y = y0 + u.brightness
        y = (y - 0.5f) * (1f + u.contrast) + 0.5f
        y = y.coerceIn(0f, 1f)
        y += (y * y * (3f - 2f * y) - y) * u.curve
        if (u.gamma != 1f) y = y.pow(u.gamma)
        y = u.fade + y * (1f - u.fade)
        return y
    }

    /** Aplica el estilo a una foto completa (en paralelo, usando todos los núcleos). */
    suspend fun apply(src: Bitmap, u: FxUniforms): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // Tabla precalculada: gris de entrada -> color de salida (curva + virado)
        val n = 1024
        val lr = FloatArray(n)
        val lg = FloatArray(n)
        val lb = FloatArray(n)
        val ly = FloatArray(n)
        for (i in 0 until n) {
            val y = toneCurve(i / (n - 1f), u)
            ly[i] = y
            var r = y
            var g = y
            var b = y
            if (u.toneAmount > 0f) {
                val tr: Float
                val tg: Float
                val tb: Float
                if (y < 0.5f) {
                    val k = y * 2f
                    tr = u.toneR * k; tg = u.toneG * k; tb = u.toneB * k
                } else {
                    val k = (y - 0.5f) * 2f
                    tr = u.toneR + (1f - u.toneR) * k
                    tg = u.toneG + (1f - u.toneG) * k
                    tb = u.toneB + (1f - u.toneB) * k
                }
                r += (tr - r) * u.toneAmount
                g += (tg - g) * u.toneAmount
                b += (tb - b) * u.toneAmount
            }
            lr[i] = r; lg[i] = g; lb[i] = b
        }

        // Luminancia desenfocada (≈2 % del ancho) para la Estructura
        var blur: FloatArray? = null
        var bw = 0
        var bh = 0
        if (u.structure > 0f) {
            var b = src
            while (b.width > 96) b = Bitmap.createScaledBitmap(b, b.width / 2, max(1, b.height / 2), true)
            bw = 48
            bh = max(1, (48f * h / w).toInt())
            val peq = Bitmap.createScaledBitmap(b, bw, bh, true)
            val bp = IntArray(bw * bh)
            peq.getPixels(bp, 0, bw, 0, 0, bw, bh)
            blur = FloatArray(bw * bh) { k ->
                val q = bp[k]
                (((q shr 16) and 0xFF) / 255f * u.mixR + ((q shr 8) and 0xFF) / 255f * u.mixG + (q and 0xFF) / 255f * u.mixB)
                    .coerceIn(0f, 1f)
            }
        }
        val blurArr = blur

        // Halación: luces fuertes desenfocadas (≈5 % del ancho)
        var halo: FloatArray? = null
        var hw = 0
        var hh = 0
        if (u.halation > 0f) {
            var b = src
            while (b.width > 96) b = Bitmap.createScaledBitmap(b, b.width / 2, max(1, b.height / 2), true)
            hw = 20
            hh = max(1, (20f * h / w).toInt())
            // máscara de luces fuertes en la imagen pequeña y luego promedio a 20 columnas
            val sw = b.width
            val sh = b.height
            val sp = IntArray(sw * sh)
            b.getPixels(sp, 0, sw, 0, 0, sw, sh)
            val suma = FloatArray(hw * hh)
            val cuenta = IntArray(hw * hh)
            for (yy in 0 until sh) for (xx in 0 until sw) {
                val q = sp[yy * sw + xx]
                val l = ((q shr 16) and 0xFF) / 255f * 0.2126f + ((q shr 8) and 0xFF) / 255f * 0.7152f + (q and 0xFF) / 255f * 0.0722f
                val m = smoothstep(0.65f, 1f, l)
                val k = min(hh - 1, yy * hh / sh) * hw + min(hw - 1, xx * hw / sw)
                suma[k] += m
                cuenta[k]++
            }
            halo = FloatArray(hw * hh) { k -> if (cuenta[k] > 0) suma[k] / cuenta[k] else 0f }
        }
        val haloArr = halo

        val resX = GRANOS_ANCHO
        val resY = GRANOS_ANCHO * h / w
        val chunks = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPer = (h + chunks - 1) / chunks

        coroutineScope {
            (0 until chunks).map { c ->
                async {
                    val y0 = c * rowsPer
                    val y1 = min(h, y0 + rowsPer)
                    for (yy in y0 until y1) {
                        val v = (yy + 0.5f) / h
                        val dy = v - 0.5f
                        val gy = floor(v * resY)
                        var i = yy * w
                        for (xx in 0 until w) {
                            val p = px[i]
                            val cr = ((p shr 16) and 0xFF) / 255f
                            val cg = ((p shr 8) and 0xFF) / 255f
                            val cb = (p and 0xFF) / 255f
                            var lum = (cr * u.mixR + cg * u.mixG + cb * u.mixB).coerceIn(0f, 1f)
                            if (blurArr != null) {
                                // muestreo bilineal de la luminancia desenfocada
                                val fx = ((xx + 0.5f) / w * bw - 0.5f).coerceIn(0f, bw - 1f)
                                val fy = ((yy + 0.5f) / h * bh - 0.5f).coerceIn(0f, bh - 1f)
                                val x0 = fx.toInt()
                                val y0b = fy.toInt()
                                val x1 = min(x0 + 1, bw - 1)
                                val y1b = min(y0b + 1, bh - 1)
                                val tx = fx - x0
                                val ty = fy - y0b
                                val top = blurArr[y0b * bw + x0] * (1 - tx) + blurArr[y0b * bw + x1] * tx
                                val bot = blurArr[y1b * bw + x0] * (1 - tx) + blurArr[y1b * bw + x1] * tx
                                val bl = top * (1 - ty) + bot * ty
                                val det = lum - bl
                                lum = (lum + u.structure * 2f * det * (1f - abs(lum - 0.5f))).coerceIn(0f, 1f)
                            }
                            val idx = (lum * (n - 1)).toInt()
                            var r = lr[idx]
                            var g = lg[idx]
                            var b = lb[idx]

                            val yF = ly[idx]
                            if (u.colorKeep > 0f || u.red > 0f) {
                                val lum0 = maxOf(cr * 0.2126f + cg * 0.7152f + cb * 0.0722f, 0.001f)
                                val k = yF / lum0
                                val colR = (cr * k).coerceIn(0f, 1f)
                                val colG = (cg * k).coerceIn(0f, 1f)
                                val colB = (cb * k).coerceIn(0f, 1f)
                                if (u.colorKeep > 0f) {
                                    r += (colR - r) * u.colorKeep
                                    g += (colG - g) * u.colorKeep
                                    b += (colB - b) * u.colorKeep
                                }
                                if (u.red > 0f) {
                                    val mx = maxOf(cr, cg, cb)
                                    val mn = minOf(cr, cg, cb)
                                    val sat = mx - mn
                                    val red = (cr - maxOf(cg, cb)) / maxOf(cr, 0.001f)
                                    val keep = smoothstep(0.15f, 0.4f, red) * smoothstep(0.06f, 0.18f, sat)
                                    // el rojo conserva su brillo original y gana saturación
                                    val l = cr * 0.2126f + cg * 0.7152f + cb * 0.0722f
                                    val sv = 1f + 0.6f * u.red
                                    val vr = (l + (cr - l) * sv).coerceIn(0f, 1f)
                                    val vg = (l + (cg - l) * sv).coerceIn(0f, 1f)
                                    val vb = (l + (cb - l) * sv).coerceIn(0f, 1f)
                                    val wgt = keep * minOf(1f, u.red * 1.5f)
                                    r += (vr - r) * wgt
                                    g += (vg - g) * wgt
                                    b += (vb - b) * wgt
                                }
                            }

                            if (u.splitTone > 0f) {
                                // sombras frías (azul verdoso) y luces cálidas
                                val ws = (1f - yF) * (yF * 4f).coerceIn(0f, 1f) // negros profundos neutros
                                r += u.splitTone * (ws * -0.07f + yF * 0.06f)
                                g += u.splitTone * (ws * 0.015f + yF * 0.02f)
                                b += u.splitTone * (ws * 0.06f + yF * -0.05f)
                            }

                            if (haloArr != null) {
                                val fx = ((xx + 0.5f) / w * hw - 0.5f).coerceIn(0f, hw - 1f)
                                val fy = ((yy + 0.5f) / h * hh - 0.5f).coerceIn(0f, hh - 1f)
                                val x0 = fx.toInt()
                                val y0h = fy.toInt()
                                val x1 = min(x0 + 1, hw - 1)
                                val y1h = min(y0h + 1, hh - 1)
                                val tx = fx - x0
                                val ty = fy - y0h
                                val top = haloArr[y0h * hw + x0] * (1 - tx) + haloArr[y0h * hw + x1] * tx
                                val bot = haloArr[y1h * hw + x0] * (1 - tx) + haloArr[y1h * hw + x1] * tx
                                val hl = (top * (1 - ty) + bot * ty) * u.halation * 1.6f
                                r += hl * 0.9f
                                g += hl * 0.25f
                                b += hl * 0.12f
                            }

                            val uu = (xx + 0.5f) / w
                            if (u.vignette > 0f) {
                                val dx = uu - 0.5f
                                val vv = smoothstep(0.3f, 0.75f, sqrt(dx * dx + dy * dy))
                                val f = 1f - u.vignette * 0.85f * vv
                                r *= f; g *= f; b *= f
                            }
                            if (u.grain > 0f) {
                                val nz = (hash12(floor(uu * resX), gy) - 0.5f) * u.grain * 0.3f
                                r += nz; g += nz; b += nz
                            }

                            if (u.intensity < 1f) {
                                r = cr + (r.coerceIn(0f, 1f) - cr) * u.intensity
                                g = cg + (g.coerceIn(0f, 1f) - cg) * u.intensity
                                b = cb + (b.coerceIn(0f, 1f) - cb) * u.intensity
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
}

// ---------- Shaders del visor (OpenGL ES 2.0) ----------

const val VERTEX_SHADER = """
attribute vec2 aPos;
uniform mat4 uST;
uniform float uRot;
uniform float uMirror;
varying vec2 vUv;
varying vec2 vTex;
void main() {
    vec2 uv = aPos * 0.5 + 0.5;
    vUv = uv;
    vec2 p = uv;
    if (uMirror > 0.5) p.x = 1.0 - p.x;
    vec2 t;
    if (abs(uRot - 90.0) < 1.0) t = vec2(1.0 - p.y, p.x);
    else if (abs(uRot - 180.0) < 1.0) t = vec2(1.0 - p.x, 1.0 - p.y);
    else if (abs(uRot - 270.0) < 1.0) t = vec2(p.y, 1.0 - p.x);
    else t = p;
    vTex = (uST * vec4(t, 0.0, 1.0)).xy;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

/** Cuerpo común del efecto (visor y video). */
private const val FX_BODY = """
varying vec2 vUv;
varying vec2 vTex;
uniform vec3 uMix;
uniform float uContrast;
uniform float uBrightness;
uniform float uCurve;
uniform float uGamma;
uniform float uFade;
uniform vec3 uTone;
uniform float uToneAmount;
uniform float uColorKeep;
uniform float uRed;
uniform float uIntensity;
uniform float uEstructura;
uniform float uVirado;
uniform float uHalacion;
uniform vec2 uTexel;
uniform float uGrain;
uniform float uVignette;
uniform vec2 uGrainRes;
uniform float uSeed;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float luzFuerte(vec2 t) {
    return smoothstep(0.65, 1.0, dot(texture2D(uTex, t).rgb, vec3(0.2126, 0.7152, 0.0722)));
}

void main() {
    vec3 c = texture2D(uTex, vTex).rgb;
    float y = clamp(dot(c, uMix), 0.0, 1.0);
    if (uEstructura > 0.0) {
        // contraste local: compara con el promedio de los vecinos
        vec2 r = uTexel;
        float b = dot(texture2D(uTex, vTex + vec2(r.x, 0.0)).rgb, uMix)
                + dot(texture2D(uTex, vTex - vec2(r.x, 0.0)).rgb, uMix)
                + dot(texture2D(uTex, vTex + vec2(0.0, r.y)).rgb, uMix)
                + dot(texture2D(uTex, vTex - vec2(0.0, r.y)).rgb, uMix)
                + dot(texture2D(uTex, vTex + r * 0.7).rgb, uMix)
                + dot(texture2D(uTex, vTex - r * 0.7).rgb, uMix)
                + dot(texture2D(uTex, vTex + vec2(r.x, -r.y) * 0.7).rgb, uMix)
                + dot(texture2D(uTex, vTex + vec2(-r.x, r.y) * 0.7).rgb, uMix);
        b = clamp(b / 8.0, 0.0, 1.0);
        y = clamp(y + uEstructura * 2.0 * (y - b) * (1.0 - abs(y - 0.5)), 0.0, 1.0);
    }
    y += uBrightness;
    y = (y - 0.5) * (1.0 + uContrast) + 0.5;
    y = clamp(y, 0.0, 1.0);
    y = mix(y, y * y * (3.0 - 2.0 * y), uCurve);
    y = pow(y, uGamma);
    y = uFade + y * (1.0 - uFade);
    vec3 g = vec3(y);
    if (uToneAmount > 0.0) {
        vec3 t;
        if (y < 0.5) t = mix(vec3(0.0), uTone, y * 2.0);
        else t = mix(uTone, vec3(1.0), (y - 0.5) * 2.0);
        g = mix(g, t, uToneAmount);
    }
    if (uColorKeep > 0.0 || uRed > 0.0) {
        float lum0 = max(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.001);
        vec3 col = clamp(c * (y / lum0), 0.0, 1.0);
        if (uColorKeep > 0.0) g = mix(g, col, uColorKeep);
        if (uRed > 0.0) {
            float mx = max(c.r, max(c.g, c.b));
            float mn = min(c.r, min(c.g, c.b));
            float sat = mx - mn;
            float red = (c.r - max(c.g, c.b)) / max(c.r, 0.001);
            float keep = smoothstep(0.15, 0.4, red) * smoothstep(0.06, 0.18, sat);
            float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
            vec3 vivo = clamp(mix(vec3(l), c, 1.0 + 0.6 * uRed), 0.0, 1.0);
            g = mix(g, vivo, keep * min(1.0, uRed * 1.5));
        }
    }
    if (uVirado > 0.0) {
        float ws = (1.0 - y) * clamp(y * 4.0, 0.0, 1.0); // los negros profundos quedan neutros
        g += uVirado * (ws * vec3(-0.07, 0.015, 0.06) + y * vec3(0.06, 0.02, -0.05));
    }
    if (uHalacion > 0.0) {
        // brillo rojizo alrededor de las luces fuertes (como en el cine de rollo)
        vec2 r2 = uTexel * 2.4;
        float hl = luzFuerte(vTex + vec2(r2.x, 0.0)) + luzFuerte(vTex - vec2(r2.x, 0.0))
                 + luzFuerte(vTex + vec2(0.0, r2.y)) + luzFuerte(vTex - vec2(0.0, r2.y))
                 + luzFuerte(vTex + r2 * 0.7) + luzFuerte(vTex - r2 * 0.7)
                 + luzFuerte(vTex + vec2(r2.x, -r2.y) * 0.7) + luzFuerte(vTex + vec2(-r2.x, r2.y) * 0.7)
                 + luzFuerte(vTex);
        hl = hl / 9.0 * uHalacion * 1.6;
        g += hl * vec3(0.9, 0.25, 0.12);
    }
    vec2 d = vUv - 0.5;
    float vv = smoothstep(0.3, 0.75, length(d));
    g *= 1.0 - uVignette * 0.85 * vv;
    float n = hash12(floor(vUv * uGrainRes) + uSeed) - 0.5;
    g += n * uGrain * 0.3;
    g = clamp(g, 0.0, 1.0);
    g = mix(c, g, uIntensity);
    //MARCA
    gl_FragColor = vec4(g, 1.0);
}
"""

private const val MARK_CODE = """
    if (vUv.x >= uMarkRect.x && vUv.x <= uMarkRect.z && vUv.y >= uMarkRect.y && vUv.y <= uMarkRect.w) {
        vec2 mUv = vec2((vUv.x - uMarkRect.x) / (uMarkRect.z - uMarkRect.x),
                        1.0 - (vUv.y - uMarkRect.y) / (uMarkRect.w - uMarkRect.y));
        vec4 m = texture2D(uMark, mUv);
        g = g * (1.0 - m.a) + m.rgb;
    }
"""

/**
 * Arma el fragment shader.
 * external = true para el visor (textura de cámara); false para procesar video.
 * mark = true agrega el sello de fecha y hora.
 */
fun fragmentShader(external: Boolean, mark: Boolean): String {
    val head = if (external) {
        "#extension GL_OES_EGL_image_external : require\nprecision highp float;\nuniform samplerExternalOES uTex;\n"
    } else {
        "precision highp float;\nuniform sampler2D uTex;\n"
    }
    val markDecl = if (mark) "uniform sampler2D uMark;\nuniform vec4 uMarkRect;\n" else ""
    return head + markDecl + FX_BODY.replace("//MARCA", if (mark) MARK_CODE else "")
}

/** Vertex shader para procesar video con Media3. */
const val VIDEO_VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vUv;
varying vec2 vTex;
void main() {
    gl_Position = aFramePosition;
    vUv = aFramePosition.xy * 0.5 + 0.5;
    vTex = vUv;
}
"""
