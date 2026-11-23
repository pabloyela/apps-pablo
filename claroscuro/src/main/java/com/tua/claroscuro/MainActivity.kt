package com.tua.claroscuro

import com.tua.comun.*

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.mutableLongStateOf
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    var glView: CameraGLView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var surfaceTexture: SurfaceTexture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val shutterSound = MediaActionSound()
    private var toneGen: ToneGenerator? = null
    private var orientationListener: OrientationEventListener? = null
    private var sensorRotation = 90

    // ---------- Estado que ve la pantalla ----------
    val permiso = mutableStateOf(false)
    val estilo = mutableStateOf(Estilo.CLASICO)
    val ajustes = mutableStateOf(Ajustes())
    val sonido = mutableStateOf(true)
    val cuadricula = mutableStateOf(false)
    val guardarColor = mutableStateOf(false)
    val teclaVolumen = mutableStateOf(true)
    val temporizador = mutableIntStateOf(0)
    val flash = mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)
    val frontal = mutableStateOf(false)
    val aspecto = mutableFloatStateOf(3f / 4f)
    val miniatura = mutableStateOf<Bitmap?>(null)
    val ultimaUri = mutableStateOf<Uri?>(null)
    val guardando = mutableIntStateOf(0)
    val disparoVolumen = mutableIntStateOf(0)
    val zoom = mutableFloatStateOf(1f)
    val fecha = mutableStateOf(false)
    val formato169 = mutableStateOf(false) // fotos en 16:9 (si no, 4:3)
    val giroUi = mutableIntStateOf(0)       // giro de los íconos según cómo sostienes el teléfono
    val firma = mutableStateOf("")          // nombre que aparece arriba de la fecha
    val editorUri = mutableStateOf<Uri?>(null)     // foto de la galería que se está editando
    val editorBase = mutableStateOf<Bitmap?>(null) // versión reducida para la vista previa

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) abrirEditor(uri)
    }
    val modoVideo = mutableStateOf(false)
    val grabando = mutableStateOf(false)
    val tiempoGrabacion = mutableLongStateOf(0L)
    val procesando = mutableStateOf<Float?>(null) // progreso del video (0..1) o null
    private var ultimaMime = "image/jpeg"
    val visorAbierto = mutableStateOf(false)
    val tomas = mutableStateOf<List<Toma>>(emptyList())

    /** Si Android pide confirmar (p. ej. tras reinstalar), mandamos a la papelera por este camino. */
    private val papeleraLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        cargarTomas()
    }

    private val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) aviso("Sin permiso de micrófono: los videos se grabarán sin sonido")
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permiso.value = ok
        if (ok) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ajustes", MODE_PRIVATE)
        loadPrefs()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
        shutterSound.load(MediaActionSound.START_VIDEO_RECORDING)
        shutterSound.load(MediaActionSound.STOP_VIDEO_RECORDING)
        toneGen = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }

        permiso.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            ClaroTheme {
                when {
                    editorUri.value != null -> EditorScreen(this)
                    visorAbierto.value -> VisorScreen(this)
                    permiso.value -> CameraScreen(this)
                    else -> PermissionScreen { permLauncher.launch(Manifest.permission.CAMERA) }
                }
            }
        }

        // Para que las fotos en horizontal se guarden derechas
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val r = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = r
                videoCapture?.targetRotation = r
                giroUi.intValue = when (r) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_270 -> -90
                    else -> 0
                }
            }
        }

        loadLastPhoto()
        if (permiso.value) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
        orientationListener?.enable()
    }

    override fun onPause() {
        orientationListener?.disable()
        glView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        shutterSound.release()
        toneGen?.release()
        cameraExecutor.shutdown()
    }

    // ---------- Preferencias ----------

    private fun loadPrefs() {
        estilo.value = Estilo.entries.firstOrNull { it.name == prefs.getString("estilo", "") } ?: Estilo.CLASICO
        ajustes.value = Ajustes(
            contraste = prefs.getFloat("contraste", 0f),
            brillo = prefs.getFloat("brillo", 0f),
            grano = prefs.getFloat("grano", estilo.value.grano),
            vineta = prefs.getFloat("vineta", estilo.value.vineta)
        )
        sonido.value = prefs.getBoolean("sonido", true)
        cuadricula.value = prefs.getBoolean("cuadricula", false)
        guardarColor.value = prefs.getBoolean("guardarColor", false)
        teclaVolumen.value = prefs.getBoolean("teclaVolumen", true)
        temporizador.intValue = prefs.getInt("temporizador", 0)
        flash.intValue = prefs.getInt("flash", ImageCapture.FLASH_MODE_OFF)
        fecha.value = prefs.getBoolean("fecha", false)
        formato169.value = prefs.getBoolean("formato169", false)
        firma.value = prefs.getString("firma", "") ?: ""
        ajustes.value = ajustes.value.copy(
            intensidad = prefs.getFloat("intensidad", 1f),
            rojos = prefs.getFloat("rojos", estilo.value.rojos),
            estructura = prefs.getFloat("estructura", estilo.value.estructura),
            virado = prefs.getFloat("virado", estilo.value.virado),
            halacion = prefs.getFloat("halacion", estilo.value.halacion)
        )
    }

    fun savePrefs() {
        val a = ajustes.value
        prefs.edit()
            .putString("estilo", estilo.value.name)
            .putFloat("contraste", a.contraste)
            .putFloat("brillo", a.brillo)
            .putFloat("grano", a.grano)
            .putFloat("vineta", a.vineta)
            .putBoolean("sonido", sonido.value)
            .putBoolean("cuadricula", cuadricula.value)
            .putBoolean("guardarColor", guardarColor.value)
            .putBoolean("teclaVolumen", teclaVolumen.value)
            .putInt("temporizador", temporizador.intValue)
            .putInt("flash", flash.intValue)
            .putBoolean("fecha", fecha.value)
            .putBoolean("formato169", formato169.value)
            .putString("firma", firma.value)
            .putFloat("intensidad", a.intensidad)
            .putFloat("rojos", a.rojos)
            .putFloat("estructura", a.estructura)
            .putFloat("virado", a.virado)
            .putFloat("halacion", a.halacion)
            .apply()
    }

    fun pushUniforms() {
        glView?.renderer?.uniforms = FxUniforms.from(estilo.value, ajustes.value)
    }

    // ---------- Cámara ----------

    /** Lo llama el visor cuando su superficie está lista. */
    fun onPreviewSurface(st: SurfaceTexture) {
        surfaceTexture = st
        pushUniforms()
        bindCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = try { future.get() } catch (e: Exception) { null }
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    fun bindCamera() {
        val provider = cameraProvider ?: return
        val st = surfaceTexture ?: return
        val main = ContextCompat.getMainExecutor(this)

        val video = modoVideo.value
        // Video siempre 16:9; fotos en 4:3 o 16:9 según el selector
        val ancho = video || formato169.value
        val previewSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                if (ancho) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    if (ancho) Size(1920, 1080) else Size(1440, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(previewSelector)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        preview.setSurfaceProvider(main) { request ->
            val res = request.resolution
            st.setDefaultBufferSize(res.width, res.height)
            request.setTransformationInfoListener(main) { info ->
                val rot = info.rotationDegrees
                // Android ya gira (y refleja en la frontal) la imagen dentro de la textura,
                // así que el visor NO debe girarla otra vez. Guardamos el giro del sensor
                // solo para calcular bien el punto de enfoque.
                sensorRotation = rot
                glView?.renderer?.rotation = 0f
                glView?.renderer?.mirror = false
                aspecto.floatValue = if (rot % 180 == 0) res.width.toFloat() / res.height
                else res.height.toFloat() / res.width
            }
            val surface = Surface(st)
            request.provideSurface(surface, main) { surface.release() }
        }

        // 4:3 ≈ 12 MP, 16:9 ≈ 8 MP: buena calidad sin agotar la memoria al procesar
        val photoSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                if (ancho) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    if (ancho) Size(3840, 2160) else Size(4000, 3000),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(photoSelector)
            .setFlashMode(flash.intValue)
            .build()

        val selector = if (frontal.value) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            if (video) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
                    )
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(this, selector, preview, vc)
                videoCapture = vc
                imageCapture = null
            } else {
                camera = provider.bindToLifecycle(this, selector, preview, capture)
                imageCapture = capture
                videoCapture = null
            }
            zoom.floatValue = 1f
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
            if (frontal.value) {
                frontal.value = false
                bindCamera()
            }
        }
    }

    fun switchCamera() {
        if (grabando.value) return
        frontal.value = !frontal.value
        bindCamera()
    }

    fun toggleFormato() {
        if (grabando.value) return
        formato169.value = !formato169.value
        savePrefs()
        if (!modoVideo.value) bindCamera()
    }

    fun setModo(video: Boolean) {
        if (grabando.value || modoVideo.value == video) return
        modoVideo.value = video
        if (video && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        bindCamera()
    }

    fun setFlash(mode: Int) {
        flash.intValue = mode
        imageCapture?.flashMode = mode
        savePrefs()
    }

    /** Enfocar donde se tocó (coordenadas 0..1 del visor, y hacia abajo). */
    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        var u = x
        val v = y
        if (frontal.value) u = 1f - u
        val rot = sensorRotation
        val (bx, by) = when (rot) {
            90 -> v to (1f - u)
            180 -> (1f - u) to (1f - v)
            270 -> (1f - v) to u
            else -> u to v
        }
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(bx, by)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        try {
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            // algunos equipos no lo permiten
        }
    }

    fun zoomBy(scale: Float) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value ?: return
        val r = (zs.zoomRatio * scale).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(r)
        zoom.floatValue = r
    }

    fun beep() {
        if (sonido.value) toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    // ---------- Tomar y guardar la foto ----------

    fun takePhoto() {
        val ic = imageCapture ?: return
        val u = FxUniforms.from(estilo.value, ajustes.value) // el estilo del momento del disparo
        val saveColor = guardarColor.value
        val sello = if (fecha.value) Sello.texto(System.currentTimeMillis(), conSegundos = false) else null
        val miFirma = firma.value
        if (sonido.value) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        guardando.intValue++
        ic.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val bmp = try {
                    image.toBitmap()
                } catch (e: Throwable) {
                    null
                } finally {
                    image.close()
                }
                lifecycleScope.launch {
                    try {
                        if (bmp != null) processAndSave(bmp, rot, u, saveColor, sello, miFirma)
                        else aviso("No se pudo leer la foto")
                    } catch (e: Throwable) {
                        aviso("No se pudo guardar la foto")
                    } finally {
                        guardando.intValue--
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    guardando.intValue--
                    aviso("No se pudo tomar la foto")
                }
            }
        })
    }

    private fun aviso(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private suspend fun processAndSave(
        src: Bitmap,
        rot: Int,
        u: FxUniforms,
        saveColor: Boolean,
        sello: String?,
        miFirma: String
    ) {
        var upright = withContext(Dispatchers.Default) { rotate(src, rot) }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val carpeta = "${Environment.DIRECTORY_PICTURES}/Claroscuro"
        // Primero el blanco y negro (de la imagen limpia), luego el sello en cada versión
        val bw = Fx.apply(upright, u)
        if (sello != null) withContext(Dispatchers.Default) { Sello.dibujar(bw, sello, miFirma) }
        if (saveColor) {
            if (sello != null) {
                if (!upright.isMutable) {
                    val copia = upright.copy(Bitmap.Config.ARGB_8888, true)
                    upright.recycle()
                    upright = copia
                }
                val conSello = upright
                withContext(Dispatchers.Default) { Sello.dibujar(conSello, sello, miFirma) }
            }
            val original = upright
            withContext(Dispatchers.IO) { saveJpeg(original, "BN_${stamp}_color.jpg", "$carpeta/Originales") }
        }
        upright.recycle()
        val uri = withContext(Dispatchers.IO) { saveJpeg(bw, "BN_$stamp.jpg", carpeta) }
        val thumb = withContext(Dispatchers.Default) {
            val tw = 240
            val th = (tw.toFloat() * bw.height / bw.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bw, tw, th, true)
        }
        bw.recycle()
        if (uri != null) {
            miniatura.value = thumb
            ultimaUri.value = uri
            ultimaMime = "image/jpeg"
        } else {
            aviso("No se pudo guardar la foto")
        }
    }

    private fun rotate(src: Bitmap, deg: Int): Bitmap {
        if (deg % 360 == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun saveJpeg(bmp: Bitmap, name: String, relPath: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    /** Muestra la miniatura de la última foto tomada con la app. */
    private fun loadLastPhoto() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    contentResolver.query(
                        base,
                        arrayOf(MediaStore.Images.Media._ID),
                        "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                        arrayOf("${Environment.DIRECTORY_PICTURES}/Claroscuro/"),
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val uri = android.content.ContentUris.withAppendedId(base, c.getLong(0))
                            val thumb = contentResolver.loadThumbnail(uri, Size(240, 320), null)
                            uri to thumb
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) {
                ultimaUri.value = result.first
                miniatura.value = result.second
            }
        }
    }

    // ---------- Visor propio ----------

    /** La miniatura abre el visor de Claroscuro (ya no pregunta con qué app). */
    fun openLastPhoto() {
        if (grabando.value) return
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) { leerTomas() }
            if (lista.isEmpty()) {
                aviso("Aún no hay fotos de Claroscuro")
                return@launch
            }
            tomas.value = lista
            cameraProvider?.unbindAll() // la cámara descansa mientras ves tus fotos
            visorAbierto.value = true
        }
    }

    fun cerrarVisor() {
        visorAbierto.value = false
        // Al volver, el visor de la cámara se crea de nuevo y vuelve a conectar la cámara
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) { leerTomas() }
            val primera = lista.firstOrNull()
            if (primera == null) {
                miniatura.value = null
                ultimaUri.value = null
            } else if (primera.uri != ultimaUri.value) {
                ultimaUri.value = primera.uri
                ultimaMime = primera.mime
                miniatura.value = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.loadThumbnail(primera.uri, Size(240, 320), null)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    private fun cargarTomas() {
        lifecycleScope.launch {
            tomas.value = withContext(Dispatchers.IO) { leerTomas() }
        }
    }

    /** Fotos y videos guardados por Claroscuro (sin los originales a color), del más reciente al más antiguo. */
    private fun leerTomas(): List<Toma> {
        val out = mutableListOf<Toma>()
        fun consulta(base: Uri, carpeta: String, video: Boolean) {
            try {
                contentResolver.query(
                    base,
                    arrayOf(
                        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.MIME_TYPE
                    ),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(carpeta),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val tomada = c.getLong(1)
                        out += Toma(
                            uri = android.content.ContentUris.withAppendedId(base, id),
                            video = video,
                            fecha = if (tomada > 0) tomada else c.getLong(2) * 1000,
                            nombre = c.getString(3) ?: "",
                            mime = c.getString(4) ?: (if (video) "video/mp4" else "image/jpeg")
                        )
                    }
                }
            } catch (e: Exception) {
                // sin acceso: se omite
            }
        }
        consulta(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_PICTURES}/Claroscuro/", false)
        consulta(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MOVIES}/Claroscuro/", true)
        return out.sortedByDescending { it.fecha }
    }

    /** Abre una foto ya derecha con el lado largo limitado. */
    fun abrirImagen(uri: Uri, maxLado: Int): Bitmap? = decodificar(uri, maxLado)

    fun compartirToma(t: Toma) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = t.mime
            putExtra(Intent.EXTRA_STREAM, t.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Compartir"))
    }

    /** Abre la toma en Baúl (para editar, firmar, recortar o mandarla a privado). */
    fun abrirEnBaul(t: Toma) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(t.uri, t.mime)
            .setPackage("com.tua.baul")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (e: Exception) {
            aviso("Baúl no está instalado")
        }
    }

    /** A la papelera (se recupera desde la Papelera de Baúl o de la galería durante 30 días). */
    fun tomaAPapelera(t: Toma) {
        try {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
            contentResolver.update(t.uri, v, null, null)
            tomas.value = tomas.value.filter { it.uri != t.uri }
            aviso("Enviada a la papelera")
        } catch (e: SecurityException) {
            // La foto ya no es "nuestra" (por ejemplo, tras reinstalar): Android pide confirmar
            try {
                val pi = MediaStore.createTrashRequest(contentResolver, listOf(t.uri), true)
                papeleraLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e2: Exception) {
                aviso("No se pudo enviar a la papelera")
            }
        } catch (e: Exception) {
            aviso("No se pudo enviar a la papelera")
        }
    }

    // ---------- Video ----------

    fun toggleRecording() {
        if (grabando.value) {
            recording?.stop()
            recording = null
            return
        }
        if (procesando.value != null) {
            aviso("Espera a que termine de procesar el video anterior")
            return
        }
        val vc = videoCapture ?: return
        val u = FxUniforms.from(estilo.value, ajustes.value) // el estilo al iniciar
        val saveColor = guardarColor.value
        val conFecha = fecha.value
        val miFirma = firma.value
        val tmp = File(cacheDir, "grab_${System.currentTimeMillis()}.mp4")
        var pending = vc.output.prepareRecording(this, FileOutputOptions.Builder(tmp).build())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }
        if (sonido.value) shutterSound.play(MediaActionSound.START_VIDEO_RECORDING)
        var inicio = System.currentTimeMillis()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> {
                    inicio = System.currentTimeMillis()
                    tiempoGrabacion.longValue = 0L
                    grabando.value = true
                }
                is VideoRecordEvent.Status -> {
                    tiempoGrabacion.longValue = ev.recordingStats.recordedDurationNanos / 1_000_000
                }
                is VideoRecordEvent.Finalize -> {
                    grabando.value = false
                    recording = null
                    if (sonido.value) shutterSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    if (tmp.exists() && tmp.length() > 0) {
                        procesarVideo(tmp, u, saveColor, if (conFecha) inicio else null, miFirma)
                    } else {
                        aviso("No se pudo grabar el video")
                    }
                }
                else -> {}
            }
        }
    }

    /** Convierte el video grabado a blanco y negro (con el mismo efecto del visor). */
    private fun procesarVideo(tmp: File, u: FxUniforms, saveColor: Boolean, inicioMs: Long?, miFirma: String) {
        procesando.value = 0f
        val salida = File(cacheDir, "bn_${System.currentTimeMillis()}.mp4")
        val efectos: List<Effect> = listOf(BnVideoEffect(u, inicioMs, miFirma))
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(tmp)))
            .setEffects(Effects(emptyList<AudioProcessor>(), efectos))
            .build()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val carpeta = "${Environment.DIRECTORY_MOVIES}/Claroscuro"

        val transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    lifecycleScope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            if (saveColor) saveVideo(tmp, "BN_${stamp}_color.mp4", "$carpeta/Originales")
                            saveVideo(salida, "BN_$stamp.mp4", carpeta)
                        }
                        tmp.delete()
                        salida.delete()
                        procesando.value = null
                        if (uri != null) {
                            val thumb = withContext(Dispatchers.IO) {
                                try {
                                    contentResolver.loadThumbnail(uri, Size(240, 320), null)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (thumb != null) miniatura.value = thumb
                            ultimaUri.value = uri
                            ultimaMime = "video/mp4"
                        } else {
                            aviso("No se pudo guardar el video")
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    lifecycleScope.launch {
                        // Para no perder el video, guardamos el original a color
                        withContext(Dispatchers.IO) {
                            saveVideo(tmp, "BN_${stamp}_color.mp4", "$carpeta/Originales")
                        }
                        tmp.delete()
                        salida.delete()
                        procesando.value = null
                        aviso("No se pudo convertir a B/N; se guardó el original a color")
                    }
                }
            })
            .build()

        transformer.start(item, salida.absolutePath)

        lifecycleScope.launch {
            val holder = ProgressHolder()
            while (procesando.value != null) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    procesando.value = holder.progress / 100f
                }
                delay(300)
            }
        }
    }

    private fun saveVideo(file: File, name: String, relPath: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    // ---------- Editor de fotos de la galería ----------

    fun elegirFoto() {
        if (grabando.value) return
        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Abre una foto (ya derecha) con el lado largo limitado a [maxLado] píxeles. */
    private fun decodificar(uri: Uri, maxLado: Int): Bitmap? = try {
        val src = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val escala = minOf(1f, maxLado.toFloat() / maxOf(w, h))
            if (escala < 1f) {
                decoder.setTargetSize((w * escala).toInt().coerceAtLeast(1), (h * escala).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } catch (e: Throwable) {
        null
    }

    private fun abrirEditor(uri: Uri) {
        cameraProvider?.unbindAll()
        editorBase.value = null
        editorUri.value = uri
        lifecycleScope.launch {
            val b = withContext(Dispatchers.IO) { decodificar(uri, 1280) }
            if (b == null) {
                aviso("No se pudo abrir la foto")
                cerrarEditor()
            } else {
                editorBase.value = b
            }
        }
    }

    fun cerrarEditor() {
        editorUri.value = null
        editorBase.value = null
        // Al volver a la cámara, el visor se crea de nuevo y vuelve a conectar la cámara
    }

    /** Fecha en que se tomó la foto (si se puede leer); si no, la hora actual. */
    private fun fechaDeFoto(uri: Uri): Long {
        try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) {
                    val t = c.getLong(0)
                    if (t > 0) return t
                }
            }
        } catch (e: Exception) {
            // el selector de fotos no siempre da la fecha
        }
        return System.currentTimeMillis()
    }

    fun guardarEdicion() {
        val uri = editorUri.value ?: return
        val u = FxUniforms.from(estilo.value, ajustes.value)
        val conFecha = fecha.value
        val miFirma = firma.value
        guardando.intValue++
        lifecycleScope.launch {
            try {
                val full = withContext(Dispatchers.IO) { decodificar(uri, 4000) }
                if (full == null) {
                    aviso("No se pudo abrir la foto")
                    return@launch
                }
                val bw = Fx.apply(full, u)
                full.recycle()
                if (conFecha) {
                    val texto = Sello.texto(withContext(Dispatchers.IO) { fechaDeFoto(uri) }, conSegundos = false)
                    withContext(Dispatchers.Default) { Sello.dibujar(bw, texto, miFirma) }
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val out = withContext(Dispatchers.IO) {
                    saveJpeg(bw, "BN_edit_$stamp.jpg", "${Environment.DIRECTORY_PICTURES}/Claroscuro")
                }
                val thumb = withContext(Dispatchers.Default) {
                    val th = (240f * bw.height / bw.width).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bw, 240, th, true)
                }
                bw.recycle()
                if (out != null) {
                    miniatura.value = thumb
                    ultimaUri.value = out
                    ultimaMime = "image/jpeg"
                    aviso("Guardada en Imágenes/Claroscuro")
                } else {
                    aviso("No se pudo guardar la foto")
                }
            } catch (e: Throwable) {
                aviso("No se pudo guardar la foto")
            } finally {
                guardando.intValue--
            }
        }
    }

    // ---------- Botones de volumen como disparador ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (permiso.value && teclaVolumen.value && editorUri.value == null && !visorAbierto.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if ((event?.repeatCount ?: 0) == 0) disparoVolumen.intValue++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (permiso.value && teclaVolumen.value && editorUri.value == null && !visorAbierto.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) return true
        return super.onKeyUp(keyCode, event)
    }
}
