package com.tua.baul

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una foto o un video del teléfono. */
data class Media(
    val id: Long,
    val uri: Uri,
    val video: Boolean,
    val name: String,
    val date: Long,        // milisegundos
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    val bucketId: Long,
    val bucket: String,
    val path: String,      // carpeta relativa, ej. "DCIM/Camera/"
    val mime: String,
    val expires: Long = 0L // solo en la papelera (segundos)
)

data class Album(val id: Long, val nombre: String, val path: String, val items: List<Media>)

object Repo {

    fun load(c: Context, trashed: Boolean = false): List<Media> =
        (query(c, video = false, trashed = trashed) + query(c, video = true, trashed = trashed))
            .sortedByDescending { it.date }

    private fun query(c: Context, video: Boolean, trashed: Boolean): List<Media> {
        val base = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_EXPIRES
        )
        if (video) cols += MediaStore.MediaColumns.DURATION
        val args = Bundle().apply {
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
            )
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
        }
        val out = mutableListOf<Media>()
        try {
            c.contentResolver.query(base, cols.toTypedArray(), args, null)?.use { cur ->
                val iId = cur.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iTaken = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iSize = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iW = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iB = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val iBn = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iPath = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iMime = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iExp = cur.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                val iDur = if (video) cur.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
                while (cur.moveToNext()) {
                    val id = cur.getLong(iId)
                    val taken = cur.getLong(iTaken)
                    val date = if (taken > 0) taken else cur.getLong(iAdded) * 1000
                    out += Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        video = video,
                        name = cur.getString(iName) ?: "",
                        date = date,
                        size = cur.getLong(iSize),
                        width = cur.getInt(iW),
                        height = cur.getInt(iH),
                        duration = if (iDur >= 0) cur.getLong(iDur) else 0L,
                        bucketId = cur.getLong(iB),
                        bucket = cur.getString(iBn) ?: "Sin carpeta",
                        path = cur.getString(iPath) ?: "",
                        mime = cur.getString(iMime) ?: (if (video) "video/mp4" else "image/jpeg"),
                        expires = cur.getLong(iExp)
                    )
                }
            }
        } catch (e: Exception) {
            // sin permiso o error del proveedor: lista vacía
        }
        return out
    }

    fun albums(all: List<Media>): List<Album> =
        all.groupBy { it.bucketId }
            .map { (id, list) -> Album(id, list.first().bucket, list.first().path, list) }
            .sortedWith(compareByDescending<Album> { esCamara(it) }.thenByDescending { a -> a.items.first().date })

    fun esCamara(a: Album) = a.path.startsWith("DCIM/Camera", ignoreCase = true)

    fun esCaptura(m: Media) =
        m.path.contains("Screenshot", ignoreCase = true) || m.bucket.contains("Screenshot", ignoreCase = true) ||
            m.bucket.contains("Captura", ignoreCase = true)

    /** Carpetas donde Android deja mover fotos y videos. */
    fun destinoValido(path: String) =
        path.startsWith("DCIM/") || path.startsWith("Pictures/") || path.startsWith("Movies/")
}

/** Favoritos guardados por la app (instantáneo, sin pedir permiso). */
object Favoritos {
    private fun prefs(c: Context) = c.getSharedPreferences("favoritos", Context.MODE_PRIVATE)
    fun todos(c: Context): Set<String> = prefs(c).getStringSet("ids", emptySet()) ?: emptySet()
    fun alternar(c: Context, medias: List<Media>, marcar: Boolean) {
        val s = todos(c).toMutableSet()
        medias.forEach { if (marcar) s += it.uri.toString() else s -= it.uri.toString() }
        prefs(c).edit().putStringSet("ids", s).apply()
    }
}

// ---------- Miniaturas ----------

object Miniaturas {
    val cache = LruCache<String, Bitmap>(400)
}

@Composable
fun Miniatura(uri: Uri, modifier: Modifier = Modifier, tam: Int = 320) {
    val context = LocalContext.current
    val key = "$uri@$tam"
    val bmp by produceState<Bitmap?>(Miniaturas.cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(tam, tam), null)
                        .also { Miniaturas.cache.put(key, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(modifier.background(Color(0xFF1E1E22))) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------- Fechas y formatos ----------

private val zona: ZoneId = ZoneId.systemDefault()
private val es: Locale = Locale.forLanguageTag("es")

fun diaDe(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()

fun etiquetaDia(d: LocalDate): String {
    val hoy = LocalDate.now(zona)
    return when {
        d == hoy -> "Hoy"
        d == hoy.minusDays(1) -> "Ayer"
        d.year == hoy.year -> d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es))
            .replaceFirstChar { it.uppercase() }
        else -> d.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", es))
    }
}

fun fechaCompleta(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(zona)
        .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", es))
        .replaceFirstChar { it.uppercase() }

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
    }
}

fun esVideoMime(mime: String) = mime.startsWith("video/")

@Suppress("unused")
private fun noop(r: ContentResolver) = r

// ---------- Búsqueda con fechas: "marzo 2024", "03/2024", "2024", "julio" ----------

data class Consulta(val mes: Int?, val anio: Int?, val resto: String) {

    fun coincideFecha(d: LocalDate): Boolean =
        (mes == null || d.monthValue == mes) && (anio == null || d.year == anio)

    fun coincideTexto(m: Media): Boolean =
        resto.isBlank() || m.name.contains(resto, ignoreCase = true) || m.bucket.contains(resto, ignoreCase = true)

    companion object {
        private val meses = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )

        private fun sinTildes(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        fun de(texto: String): Consulta {
            var mes: Int? = null
            var anio: Int? = null
            val resto = mutableListOf<String>()
            val t = sinTildes(texto.lowercase(Locale.getDefault())).trim()
            // "03/2024" o "3-2024"
            val mmaaaa = Regex("^(\\d{1,2})[/\\-.](\\d{4})$")
            for (palabra in t.split(Regex("\\s+")).filter { it.isNotBlank() }) {
                val mm = mmaaaa.find(palabra)
                when {
                    mm != null && mm.groupValues[1].toInt() in 1..12 -> {
                        mes = mm.groupValues[1].toInt()
                        anio = mm.groupValues[2].toInt()
                    }
                    Regex("^(19|20)\\d{2}$").matches(palabra) -> anio = palabra.toInt()
                    palabra.length >= 3 && meses.indexOfFirst { it.startsWith(palabra) } >= 0 && mes == null ->
                        mes = meses.indexOfFirst { it.startsWith(palabra) } + 1
                    palabra == "de" || palabra == "del" -> {}
                    else -> resto += palabra
                }
            }
            return Consulta(mes, anio, resto.joinToString(" "))
        }
    }
}
