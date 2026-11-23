package com.tua.baul

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantallas de la app. */
sealed class Pant {
    object Inicio : Pant()
    data class AlbumP(val titulo: String, val filtro: (Media) -> Boolean) : Pant()
    data class Visor(val lista: List<Media>, val inicio: Int) : Pant()
    data class Editor(val media: Media) : Pant()
    object Papelera : Pant()
    object Ajustes : Pant()
    object Boveda : Pant()
    data class VisorBoveda(val inicio: Int) : Pant()
    data class Reproductor(val uri: Uri, val titulo: String) : Pant()
    object Limpieza : Pant()
    object Patron : Pant()          // entrada con patrón (sin ningún texto)
    object CrearPatron : Pant()     // configurar patrón en Ajustes
}

class MainActivity : ComponentActivity(), PlayerHost {

    // ---------- Estado ----------
    val permiso = mutableStateOf(false)
    val pila = mutableStateListOf<Pant>(Pant.Inicio)
    val medios = mutableStateOf<List<Media>?>(null)
    val papelera = mutableStateOf<List<Media>?>(null)
    val favoritos = mutableStateOf<Set<String>>(emptySet())
    val pestana = mutableIntStateOf(0)   // 0 = Fotos, 1 = Álbumes
    val columnas = mutableIntStateOf(4)
    val guardando = mutableIntStateOf(0)
    val bovedaAbierta = mutableStateOf(false)
    val bovedaItems = mutableStateOf<List<VItem>>(emptyList())
    var espacio = Boveda.REAL                         // área abierta: real o señuelo
    private var pinSesion: String? = null             // clave usada para abrir (para respaldos)
    val diasSinRespaldo = mutableStateOf<Int?>(null)  // aviso dentro del área privada
    val aperturas = mutableIntStateOf(0)              // cada vez que se entra al área privada
    val ocultos = mutableStateOf<Set<String>>(emptySet())  // álbumes ocultos de la línea de tiempo
    val fijados = mutableStateOf<Set<String>>(emptySet())  // álbumes fijados arriba
    val firma = mutableStateOf("")
    val quitarMeta = mutableStateOf(true)             // quitar ubicación al compartir

    private val carpetaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Boveda.ponerCarpetaRespaldo(this, uri.toString())
                aviso("Carpeta de respaldo guardada")
                val pin = pinSesion
                if (pin != null) respaldoAutomatico(pin, forzar = true)
            } catch (e: Exception) {
                aviso("No se pudo usar esa carpeta")
            }
        }
    }

    private var alTerminar: ((Boolean) -> Unit)? = null
    private var respaldoPin: String = ""
    private var salidaInterna = false // abrimos Pelis o el selector de archivos nosotros mismos

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permiso.value = tienePermiso()
        if (permiso.value) recargar()
    }

    /** Resultado de los cuadros de confirmación del sistema (borrar, mover, papelera). */
    private val pedirLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val cb = alTerminar
        alTerminar = null
        cb?.invoke(r.resultCode == RESULT_OK)
        recargar()
    }

    private val exportarLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) exportarBoveda(uri) }

    private val importarLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importarBoveda(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pr = getSharedPreferences("ajustes", MODE_PRIVATE)
        columnas.intValue = pr.getInt("columnas", 4)
        ocultos.value = pr.getStringSet("ocultos", emptySet()) ?: emptySet()
        fijados.value = pr.getStringSet("fijados", emptySet()) ?: emptySet()
        firma.value = pr.getString("firma", "") ?: ""
        quitarMeta.value = pr.getBoolean("quitarMeta", true)
        permiso.value = tienePermiso()
        favoritos.value = Favoritos.todos(this)

        setContent {
            BaulTheme { App(this) }
        }

        if (!permiso.value) pedirPermiso() else recargar()
        abrirDesdeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        salidaInterna = false
        if (permiso.value) recargar()
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app, el área privada se cierra sola
        // (salvo que la hayamos dejado nosotros para ver un video o elegir un archivo)
        if (!salidaInterna) cerrarBoveda()
    }

    // ---------- Permisos ----------

    private fun tienePermiso(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun pedirPermiso() {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permLauncher.launch(perms)
    }

    /** ¿Tiene el permiso especial para borrar/mover sin confirmar cada vez? */
    fun gestionaMedios(): Boolean = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)

    fun abrirPermisoGestion() {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            aviso("Búscalo en Ajustes › Apps › Acceso especial › Administración de medios")
        }
    }

    // ---------- Navegación ----------

    fun ir(p: Pant) {
        pila.add(p)
    }

    fun atras() {
        if (pila.size > 1) pila.removeAt(pila.lastIndex) else finish()
        if (pila.none { it is Pant.Boveda || it is Pant.VisorBoveda || it is Pant.Patron }) seguridad(false)
    }

    // ---------- Datos ----------

    fun recargar() {
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) { Repo.load(this@MainActivity) }
            medios.value = lista
            if (pila.any { it is Pant.Papelera }) cargarPapelera()
        }
    }

    fun cargarPapelera() {
        lifecycleScope.launch {
            papelera.value = withContext(Dispatchers.IO) { Repo.load(this@MainActivity, trashed = true) }
        }
    }

    fun guardarColumnas(n: Int) {
        columnas.intValue = n
        getSharedPreferences("ajustes", MODE_PRIVATE).edit().putInt("columnas", n).apply()
    }

    private fun prefs() = getSharedPreferences("ajustes", MODE_PRIVATE)

    fun alternarOculto(bucketId: Long) {
        val k = bucketId.toString()
        ocultos.value = if (k in ocultos.value) ocultos.value - k else ocultos.value + k
        prefs().edit().putStringSet("ocultos", ocultos.value).apply()
    }

    fun alternarFijado(bucketId: Long) {
        val k = bucketId.toString()
        fijados.value = if (k in fijados.value) fijados.value - k else fijados.value + k
        prefs().edit().putStringSet("fijados", fijados.value).apply()
    }

    fun guardarFirma(f: String) {
        firma.value = f.trim()
        prefs().edit().putString("firma", firma.value).apply()
    }

    fun alternarQuitarMeta() {
        quitarMeta.value = !quitarMeta.value
        prefs().edit().putBoolean("quitarMeta", quitarMeta.value).apply()
    }

    fun aviso(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun pedir(pi: PendingIntent, despues: (Boolean) -> Unit) {
        alTerminar = despues
        pedirLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    // ---------- Acciones sobre fotos ----------

    fun compartir(lista: List<Media>) {
        if (lista.isEmpty()) return
        if (!quitarMeta.value || lista.none { esJpeg(it) }) {
            enviar(lista.map { it.uri }, lista)
            return
        }
        lifecycleScope.launch {
            val uris = withContext(Dispatchers.IO) { lista.map { m -> if (esJpeg(m)) copiaSinUbicacion(m) ?: m.uri else m.uri } }
            enviar(uris, lista)
        }
    }

    private fun esJpeg(m: Media) = !m.video && (m.mime == "image/jpeg" || m.mime == "image/jpg")

    /** Copia temporal sin GPS ni datos del teléfono (no se recomprime la foto). */
    private fun copiaSinUbicacion(m: Media): Uri? = try {
        val dir = java.io.File(cacheDir, "compartir").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val f = java.io.File(dir, m.name.ifBlank { "foto.jpg" })
        contentResolver.openInputStream(m.uri)?.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        val ex = androidx.exifinterface.media.ExifInterface(f)
        listOf(
            androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_IMG_DIRECTION,
            androidx.exifinterface.media.ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            androidx.exifinterface.media.ExifInterface.TAG_MAKE,
            androidx.exifinterface.media.ExifInterface.TAG_MODEL,
            androidx.exifinterface.media.ExifInterface.TAG_BODY_SERIAL_NUMBER
        ).forEach { ex.setAttribute(it, null) }
        ex.saveAttributes()
        FileProvider.getUriForFile(this, "com.tua.baul.archivos", f)
    } catch (e: Exception) {
        null
    }

    private fun enviar(uris: List<Uri>, lista: List<Media>) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = lista[0].mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (lista.all { it.video }) "video/*" else if (lista.none { it.video }) "image/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Compartir"))
    }

    fun aPapelera(lista: List<Media>, despues: () -> Unit = {}) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createTrashRequest(contentResolver, lista.map { it.uri }, true)) { ok ->
            if (ok) {
                aviso(if (lista.size == 1) "Enviada a la papelera" else "${lista.size} elementos a la papelera")
                despues()
            }
        }
    }

    fun restaurar(lista: List<Media>) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createTrashRequest(contentResolver, lista.map { it.uri }, false)) { cargarPapelera() }
    }

    fun eliminarDefinitivo(lista: List<Media>) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createDeleteRequest(contentResolver, lista.map { it.uri })) { cargarPapelera() }
    }

    fun alternarFavorito(lista: List<Media>) {
        if (lista.isEmpty()) return
        val marcar = !lista.all { it.uri.toString() in favoritos.value }
        Favoritos.alternar(this, lista, marcar)
        favoritos.value = Favoritos.todos(this)
    }

    /** Mueve fotos/videos a una carpeta (álbum). */
    fun mover(lista: List<Media>, carpeta: String) {
        if (lista.isEmpty()) return
        val destino = if (carpeta.endsWith("/")) carpeta else "$carpeta/"
        pedir(MediaStore.createWriteRequest(contentResolver, lista.map { it.uri })) { ok ->
            if (!ok) return@pedir
            var n = 0
            for (m in lista) {
                try {
                    val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, destino) }
                    if (contentResolver.update(m.uri, v, null, null) > 0) n++
                } catch (e: Exception) {
                    // algunas carpetas no aceptan ciertos tipos de archivo
                }
            }
            aviso(if (n == lista.size) "Movido a ${destino.trimEnd('/').substringAfterLast('/')}" else "Se movieron $n de ${lista.size}")
            recargar()
        }
    }

    /** Los videos se ven dentro de Baúl (no salen a otras apps). */
    fun abrirVideo(m: Media) = ir(Pant.Reproductor(m.uri, m.name))

    // ---------- Reproductor (PlayerHost) ----------

    var giroManual = false

    /** Entra o sale del modo reproductor: pantalla completa y giro libre. */
    fun modoReproductor(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            giroManual = false
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val attrs = window.attributes
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
        }
    }

    override fun getBrightness(): Float {
        val actual = window.attributes.screenBrightness
        if (actual >= 0f) return actual
        return try {
            (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f).coerceIn(0.05f, 1f)
        } catch (e: Exception) {
            0.5f
        }
    }

    override fun setBrightness(value: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = attrs
    }

    override fun rotate() {
        giroManual = true
        val horizontal = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (horizontal) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun enterPip() {
        // Sin ventana flotante dentro de Baúl (para no exponer videos privados)
    }

    override fun close() = atras()

    // ---------- Abrir fotos que vienen de otras apps ----------

    private fun abrirDesdeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val mime = intent.type ?: contentResolver.getType(uri) ?: "image/jpeg"
        var nombre = "Foto"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) nombre = it.getString(0) ?: nombre
            }
        } catch (e: Exception) {
            // sin nombre
        }
        val m = Media(-1, uri, esVideoMime(mime), nombre, System.currentTimeMillis(), 0, 0, 0, 0, -1, "", "", mime)
        ir(Pant.Visor(listOf(m), 0))
    }

    // ---------- Imágenes ----------

    /** Abre una foto ya derecha, con el lado largo limitado a [maxLado]. */
    fun decodificar(uri: Uri, maxLado: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val esc = minOf(1f, maxLado.toFloat() / maxOf(w, h))
            if (esc < 1f) dec.setTargetSize((w * esc).toInt().coerceAtLeast(1), (h * esc).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.isMutableRequired = true
        }
    } catch (e: Throwable) {
        null
    }

    fun decodificarArchivo(f: java.io.File, maxLado: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { dec, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val esc = minOf(1f, maxLado.toFloat() / maxOf(w, h))
            if (esc < 1f) dec.setTargetSize((w * esc).toInt().coerceAtLeast(1), (h * esc).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Throwable) {
        null
    }

    /** Guarda la edición como una copia nueva, junto a la original. */
    fun guardarEdicion(m: Media, e: Edicion) {
        guardando.intValue++
        lifecycleScope.launch {
            try {
                val full = withContext(Dispatchers.IO) { decodificar(m.uri, 4000) }
                if (full == null) {
                    aviso("No se pudo abrir la foto")
                    return@launch
                }
                if (e.sello && e.firma != firma.value) guardarFirma(e.firma)
                val out = Edit.aplicar(full, e, this@MainActivity)
                val carpeta = if (Repo.destinoValido(m.path)) m.path else "Pictures/Editadas/"
                val base = m.name.substringBeforeLast('.').ifBlank { "foto" }
                val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
                val uri = withContext(Dispatchers.IO) { guardarJpeg(out, "${base}_editada_$stamp.jpg", carpeta) }
                if (uri != null) {
                    aviso("Copia guardada")
                    recargar()
                    atras()
                } else {
                    aviso("No se pudo guardar")
                }
            } catch (t: Throwable) {
                aviso("No se pudo guardar")
            } finally {
                guardando.intValue--
            }
        }
    }

    private fun guardarJpeg(bmp: Bitmap, nombre: String, carpeta: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, carpeta)
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
        } catch (ex: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    // ---------- Área privada ----------

    /** Se llama al escribir en el buscador: la clave real o la señuelo abren su área. */
    fun probarClave(texto: String) {
        if (texto.length < 4) return
        if (!Boveda.tieneClave(this, Boveda.REAL) && !Boveda.tieneClave(this, Boveda.SENUELO)) return
        lifecycleScope.launch {
            val esp = withContext(Dispatchers.Default) {
                when {
                    Boveda.comprobar(this@MainActivity, Boveda.REAL, texto) -> Boveda.REAL
                    Boveda.comprobar(this@MainActivity, Boveda.SENUELO, texto) -> Boveda.SENUELO
                    else -> -1
                }
            }
            if (esp >= 0) abrirBoveda(esp, texto)
        }
    }

    /** Entrada con patrón (abre el área real). Si falla, se cierra sin decir nada. */
    fun probarPatron(patron: List<Int>) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) { Boveda.comprobarPatron(this@MainActivity, patron) }
            if (pila.lastOrNull() is Pant.Patron) pila.removeAt(pila.lastIndex)
            if (ok) abrirBoveda(Boveda.REAL, null) else seguridad(false)
        }
    }

    fun irPatron() {
        if (!Boveda.tienePatron(this)) return
        seguridad(true)
        ir(Pant.Patron)
    }

    private suspend fun abrirBoveda(esp: Int, pin: String?) {
        aperturas.intValue++
        if (bovedaAbierta.value && espacio == esp) {
            if (pin != null) pinSesion = pin
            if (pila.lastOrNull() !is Pant.Boveda) ir(Pant.Boveda)
            return
        }
        espacio = esp
        pinSesion = pin
        bovedaAbierta.value = true
        bovedaItems.value = withContext(Dispatchers.IO) { Boveda.items(this@MainActivity, esp) }
        seguridad(true)
        ir(Pant.Boveda)
        actualizarAvisoRespaldo()
        if (pin != null) respaldoAutomatico(pin, forzar = false)
    }

    private fun actualizarAvisoRespaldo() {
        val ultimo = Boveda.ultimoRespaldo(this, espacio)
        val dias = if (ultimo == 0L) Int.MAX_VALUE else ((System.currentTimeMillis() - ultimo) / 86_400_000L).toInt()
        diasSinRespaldo.value = if (bovedaItems.value.isNotEmpty() && dias >= 7) dias else null
    }

    /** Si hay carpeta elegida y pasaron 7 días o más, respalda solo. */
    private fun respaldoAutomatico(pin: String, forzar: Boolean) {
        val carpeta = Boveda.carpetaRespaldo(this) ?: return
        if (!forzar && diasSinRespaldo.value == null) return
        if (bovedaItems.value.isEmpty()) return
        val esp = espacio
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val arbol = Uri.parse(carpeta)
                    val dirDoc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        arbol, android.provider.DocumentsContract.getTreeDocumentId(arbol)
                    )
                    val nombre = "privado_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".baul"
                    val doc = android.provider.DocumentsContract.createDocument(
                        contentResolver, dirDoc, "application/octet-stream", nombre
                    ) ?: return@withContext false
                    contentResolver.openOutputStream(doc)?.use { Boveda.respaldar(this@MainActivity, esp, pin, it) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                aviso("Respaldo automático guardado")
                actualizarAvisoRespaldo()
            }
        }
    }

    fun elegirCarpetaRespaldo() {
        salidaInterna = true
        carpetaLauncher.launch(null)
    }

    fun tieneCarpetaRespaldo() = Boveda.carpetaRespaldo(this) != null

    /** Regresar al área privada mientras la sesión sigue abierta. */
    fun volverABoveda() {
        if (bovedaAbierta.value && pila.lastOrNull() !is Pant.Boveda) ir(Pant.Boveda)
    }

    fun cerrarBoveda() {
        if (!bovedaAbierta.value) return
        bovedaAbierta.value = false
        bovedaItems.value = emptyList()
        pinSesion = null
        diasSinRespaldo.value = null
        while (pila.size > 1 && pila.any { it is Pant.Boveda || it is Pant.VisorBoveda || it is Pant.Patron }) {
            pila.removeAt(pila.lastIndex)
        }
        seguridad(false)
    }

    /** Sin capturas de pantalla ni vista en "recientes" mientras el área privada está abierta. */
    private fun seguridad(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Claves (Ajustes)
    fun tieneClave(esp: Int = Boveda.REAL) = Boveda.tieneClave(this, esp)
    fun claveCorrecta(pin: String, esp: Int = Boveda.REAL) = Boveda.comprobar(this, esp, pin)
    fun ponerClave(pin: String, esp: Int = Boveda.REAL) {
        Boveda.ponerClave(this, esp, pin)
        aviso("Clave guardada")
    }
    fun quitarClaveSenuelo() {
        Boveda.quitarClave(this, Boveda.SENUELO)
        aviso("Clave secundaria eliminada")
    }
    fun tienePatron() = Boveda.tienePatron(this)
    fun ponerPatron(p: List<Int>) {
        Boveda.ponerPatron(this, p)
        aviso("Patrón guardado")
    }
    fun quitarPatron() {
        Boveda.quitarPatron(this)
        aviso("Patrón eliminado")
    }

    fun miniaturaBoveda(item: VItem): Bitmap? = Boveda.miniatura(this, espacio, item)
    fun archivoBoveda(item: VItem): java.io.File = Boveda.archivo(this, espacio, item)

    /** Mueve fotos/videos al área privada abierta: los copia y luego borra los originales. */
    fun ocultar(lista: List<Media>, despues: () -> Unit = {}) {
        if (!bovedaAbierta.value || lista.isEmpty()) return
        val esp = espacio
        guardando.intValue++
        lifecycleScope.launch {
            val hechos = withContext(Dispatchers.IO) { Boveda.meter(this@MainActivity, esp, lista) }
            guardando.intValue--
            if (hechos.isEmpty()) {
                aviso("No se pudo mover")
                return@launch
            }
            pedir(MediaStore.createDeleteRequest(contentResolver, hechos.map { it.first.uri })) { ok ->
                if (ok) {
                    aviso("${hechos.size} a privado")
                    despues()
                } else {
                    Boveda.deshacer(this@MainActivity, esp, hechos.map { it.second })
                }
                if (bovedaAbierta.value) bovedaItems.value = Boveda.items(this@MainActivity, esp)
            }
        }
    }

    fun sacarDeBoveda(sel: List<VItem>) {
        val esp = espacio
        guardando.intValue++
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) { Boveda.sacar(this@MainActivity, esp, sel) }
            guardando.intValue--
            bovedaItems.value = Boveda.items(this@MainActivity, esp)
            aviso("$n devueltos a la galería")
            recargar()
        }
    }

    fun eliminarDeBoveda(sel: List<VItem>) {
        Boveda.eliminar(this, espacio, sel)
        bovedaItems.value = Boveda.items(this, espacio)
    }

    fun albumBoveda(sel: List<VItem>, album: String) {
        Boveda.asignarAlbum(this, espacio, sel.map { it.file }.toSet(), album.trim())
        bovedaItems.value = Boveda.items(this, espacio)
    }

    fun abrirVideoBoveda(item: VItem) {
        ir(Pant.Reproductor(Uri.fromFile(Boveda.archivo(this, espacio, item)), item.name))
    }

    fun pedirRespaldo(pin: String) {
        respaldoPin = pin
        salidaInterna = true
        val fecha = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        exportarLauncher.launch("privado_$fecha.baul")
    }

    fun pedirImportar(pin: String) {
        respaldoPin = pin
        salidaInterna = true
        importarLauncher.launch(arrayOf("*/*"))
    }

    private fun exportarBoveda(uri: Uri) {
        val pin = respaldoPin
        val esp = espacio
        guardando.intValue++
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { Boveda.respaldar(this@MainActivity, esp, pin, it) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            guardando.intValue--
            actualizarAvisoRespaldo()
            aviso(if (ok) "Respaldo guardado. Cópialo a tu computadora." else "No se pudo crear el respaldo")
        }
    }

    private fun importarBoveda(uri: Uri) {
        val pin = respaldoPin
        val esp = espacio
        guardando.intValue++
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { Boveda.restaurar(this@MainActivity, esp, pin, it) } ?: -3
                } catch (e: Exception) {
                    -3
                }
            }
            guardando.intValue--
            bovedaItems.value = Boveda.items(this@MainActivity, esp)
            aviso(
                when (n) {
                    -1 -> "La clave no corresponde a ese respaldo"
                    -2 -> "Ese archivo no es un respaldo de Baúl"
                    -3 -> "No se pudo leer el respaldo"
                    else -> "$n archivos recuperados"
                }
            )
        }
    }

    @Suppress("unused")
    private fun ctx(): Context = this
}
