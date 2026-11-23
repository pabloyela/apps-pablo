package com.tua.claroscuro

import com.tua.comun.*

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Visor: recibe la imagen de la cámara y la dibuja ya en blanco y negro. */
class CameraGLView(context: Context) : GLSurfaceView(context) {
    val renderer = BwRenderer(this)

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

class BwRenderer(private val view: GLSurfaceView) : GLSurfaceView.Renderer {

    @Volatile var uniforms: FxUniforms = FxUniforms.from(Estilo.CLASICO, Ajustes())
    @Volatile var rotation: Float = 90f
    @Volatile var mirror: Boolean = false

    /** Se llama (en el hilo principal) cuando hay una superficie lista para la cámara. */
    var onSurfaceReady: ((SurfaceTexture) -> Unit)? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var program = 0
    private var texId = 0
    private val stMatrix = FloatArray(16)
    private var viewW = 1
    private var viewH = 1
    private var frame = 0

    private var aPos = 0
    private var uST = 0
    private var uRot = 0
    private var uMirror = 0
    private var uTex = 0
    private var uMix = 0
    private var uContrast = 0
    private var uBrightness = 0
    private var uCurve = 0
    private var uGamma = 0
    private var uFade = 0
    private var uTone = 0
    private var uToneAmount = 0
    private var uColorKeep = 0
    private var uRed = 0
    private var uIntensity = 0
    private var uEstructura = 0
    private var uVirado = 0
    private var uHalacion = 0
    private var uTexel = 0
    private var uGrain = 0
    private var uVignette = 0
    private var uGrainRes = 0
    private var uSeed = 0

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        program = buildProgram(VERTEX_SHADER, fragmentShader(external = true, mark = false))
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uST = GLES20.glGetUniformLocation(program, "uST")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uMirror = GLES20.glGetUniformLocation(program, "uMirror")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uMix = GLES20.glGetUniformLocation(program, "uMix")
        uContrast = GLES20.glGetUniformLocation(program, "uContrast")
        uBrightness = GLES20.glGetUniformLocation(program, "uBrightness")
        uCurve = GLES20.glGetUniformLocation(program, "uCurve")
        uGamma = GLES20.glGetUniformLocation(program, "uGamma")
        uFade = GLES20.glGetUniformLocation(program, "uFade")
        uTone = GLES20.glGetUniformLocation(program, "uTone")
        uToneAmount = GLES20.glGetUniformLocation(program, "uToneAmount")
        uColorKeep = GLES20.glGetUniformLocation(program, "uColorKeep")
        uRed = GLES20.glGetUniformLocation(program, "uRed")
        uIntensity = GLES20.glGetUniformLocation(program, "uIntensity")
        uEstructura = GLES20.glGetUniformLocation(program, "uEstructura")
        uVirado = GLES20.glGetUniformLocation(program, "uVirado")
        uHalacion = GLES20.glGetUniformLocation(program, "uHalacion")
        uTexel = GLES20.glGetUniformLocation(program, "uTexel")
        uGrain = GLES20.glGetUniformLocation(program, "uGrain")
        uVignette = GLES20.glGetUniformLocation(program, "uVignette")
        uGrainRes = GLES20.glGetUniformLocation(program, "uGrainRes")
        uSeed = GLES20.glGetUniformLocation(program, "uSeed")

        surfaceTexture?.release()
        val st = SurfaceTexture(texId)
        st.setOnFrameAvailableListener { view.requestRender() }
        surfaceTexture = st
        view.post { onSurfaceReady?.invoke(st) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = width.coerceAtLeast(1)
        viewH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
        } catch (e: Exception) {
            return
        }
        st.getTransformMatrix(stMatrix)
        val u = uniforms
        frame = (frame + 1) % 997

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glUniformMatrix4fv(uST, 1, false, stMatrix, 0)
        GLES20.glUniform1f(uRot, rotation)
        GLES20.glUniform1f(uMirror, if (mirror) 1f else 0f)
        GLES20.glUniform3f(uMix, u.mixR, u.mixG, u.mixB)
        GLES20.glUniform1f(uContrast, u.contrast)
        GLES20.glUniform1f(uBrightness, u.brightness)
        GLES20.glUniform1f(uCurve, u.curve)
        GLES20.glUniform1f(uGamma, u.gamma)
        GLES20.glUniform1f(uFade, u.fade)
        GLES20.glUniform3f(uTone, u.toneR, u.toneG, u.toneB)
        GLES20.glUniform1f(uToneAmount, u.toneAmount)
        GLES20.glUniform1f(uColorKeep, u.colorKeep)
        GLES20.glUniform1f(uRed, u.red)
        GLES20.glUniform1f(uIntensity, u.intensity)
        GLES20.glUniform1f(uEstructura, u.structure)
        GLES20.glUniform1f(uVirado, u.splitTone)
        GLES20.glUniform1f(uHalacion, u.halation)
        GLES20.glUniform2f(uTexel, 0.015f, 0.02f)
        GLES20.glUniform1f(uGrain, u.grain)
        GLES20.glUniform1f(uVignette, u.vignette)
        GLES20.glUniform2f(uGrainRes, GRANOS_ANCHO, GRANOS_ANCHO * viewH / viewW)
        GLES20.glUniform1f(uSeed, (frame % 50) * 37f)

        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("Claroscuro", "Error en shader: " + GLES20.glGetShaderInfoLog(s))
        }
        return s
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("Claroscuro", "Error al enlazar: " + GLES20.glGetProgramInfoLog(p))
        }
        return p
    }
}
