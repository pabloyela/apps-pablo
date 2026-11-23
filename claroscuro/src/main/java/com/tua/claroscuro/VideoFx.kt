package com.tua.claroscuro

import com.tua.comun.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Sello de fecha y hora (marca de agua). */
object Sello {

    fun texto(ms: Long, conSegundos: Boolean): String =
        SimpleDateFormat(if (conSegundos) "dd/MM/yyyy  HH:mm:ss" else "dd/MM/yyyy  HH:mm", Locale.US)
            .format(Date(ms))

    private fun pincel(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = size
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(size * 0.15f, 0f, size * 0.06f, 0xCC000000.toInt())
    }

    /** Líneas del sello: la firma (si hay) arriba y la fecha abajo. */
    fun lineas(fecha: String, firma: String): List<String> =
        if (firma.isBlank()) listOf(fecha) else listOf(firma.trim(), fecha)

    /** Dibuja el sello abajo a la izquierda de una foto (la foto debe ser editable). */
    fun dibujar(bmp: Bitmap, fecha: String, firma: String) {
        val corto = min(bmp.width, bmp.height).toFloat()
        val size = corto * 0.034f
        val p = pincel(size)
        val margen = corto * 0.035f
        val ls = lineas(fecha, firma)
        val canvas = Canvas(bmp)
        var y = bmp.height - margen - (ls.size - 1) * size * 1.25f
        for (l in ls) {
            canvas.drawText(l, margen, y, p)
            y += size * 1.25f
        }
    }

    /** Imagen pequeña con el sello, para pegarla sobre cada cuadro del video. */
    fun imagen(fecha: String, firma: String, size: Float): Bitmap {
        val p = pincel(size)
        val ls = lineas(fecha, firma)
        val w = (ls.maxOf { p.measureText(it) } + size * 0.6f).toInt().coerceAtLeast(1)
        val h = (size * (0.5f + 1.25f * ls.size)).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        var y = size * 1.1f
        for (l in ls) {
            c.drawText(l, size * 0.3f, y, p)
            y += size * 1.25f
        }
        return b
    }
}

/** Efecto de Media3 que aplica el mismo blanco y negro del visor a cada cuadro del video. */
class BnVideoEffect(
    private val u: FxUniforms,
    private val inicioMs: Long?, // hora de inicio de la grabación; null = sin sello
    private val firma: String
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        BnShaderProgram(u, inicioMs, firma)
}

class BnShaderProgram(
    private val u: FxUniforms,
    private val inicioMs: Long?,
    private val firma: String
) : BaseGlShaderProgram(false, 1) {

    private val program: GlProgram = try {
        GlProgram(VIDEO_VERTEX_SHADER, fragmentShader(external = false, mark = inicioMs != null))
    } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }

    private var w = 1
    private var h = 1
    private var frame = 0
    private var markTex = 0
    private var ultimoSegundo = Long.MIN_VALUE
    private var rect = floatArrayOf(0f, 0f, 0f, 0f)

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth.coerceAtLeast(1)
        h = inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uMix", floatArrayOf(u.mixR, u.mixG, u.mixB))
            program.setFloatUniform("uContrast", u.contrast)
            program.setFloatUniform("uBrightness", u.brightness)
            program.setFloatUniform("uCurve", u.curve)
            program.setFloatUniform("uGamma", u.gamma)
            program.setFloatUniform("uFade", u.fade)
            program.setFloatsUniform("uTone", floatArrayOf(u.toneR, u.toneG, u.toneB))
            program.setFloatUniform("uToneAmount", u.toneAmount)
            program.setFloatUniform("uColorKeep", u.colorKeep)
            program.setFloatUniform("uRed", u.red)
            program.setFloatUniform("uIntensity", u.intensity)
            program.setFloatUniform("uEstructura", u.structure)
            program.setFloatUniform("uVirado", u.splitTone)
            program.setFloatUniform("uHalacion", u.halation)
            program.setFloatsUniform("uTexel", floatArrayOf(0.015f, 0.015f * w / h))
            program.setFloatUniform("uGrain", u.grain)
            program.setFloatUniform("uVignette", u.vignette)
            program.setFloatsUniform("uGrainRes", floatArrayOf(GRANOS_ANCHO, GRANOS_ANCHO * h / w))
            program.setFloatUniform("uSeed", ((frame++ % 50) * 37).toFloat())
            if (inicioMs != null) {
                actualizarSello(presentationTimeUs)
                program.setSamplerTexIdUniform("uMark", markTex, 1)
                program.setFloatsUniform("uMarkRect", rect)
            }
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    /** Regenera la imagen del sello solo cuando cambia el segundo. */
    private fun actualizarSello(presentationTimeUs: Long) {
        val inicio = inicioMs ?: return
        val ms = inicio + presentationTimeUs / 1000
        val segundo = ms / 1000
        if (segundo == ultimoSegundo && markTex != 0) return
        ultimoSegundo = segundo

        val corto = min(w, h).toFloat()
        val bmp = Sello.imagen(Sello.texto(ms, conSegundos = true), firma, corto * 0.034f)
        val margen = corto * 0.035f
        val x0 = margen / w
        val x1 = x0 + bmp.width.toFloat() / w
        val y0 = margen / h
        val y1 = y0 + bmp.height.toFloat() / h
        rect = floatArrayOf(x0, y0, x1, y1)

        if (markTex == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            markTex = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, markTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
    }

    override fun release() {
        super.release()
        try {
            program.delete()
            if (markTex != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(markTex), 0)
                markTex = 0
            }
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }
}
