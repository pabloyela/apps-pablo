package com.tua.pelis

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class Video(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long,
    val folderId: Long,
    val folderName: String,
    val date: Long,
    val width: Int,
    val height: Int,
    val fileName: String = "",
    val path: String = "",
    val mime: String = "",
    val expires: Long = 0L
)

data class Carpeta(val id: Long, val name: String, val videos: List<Video>)

object VideoRepo {
    /** Carga los videos. Con [trashed] = true carga solo los que están en la papelera. */
    fun load(context: Context, trashed: Boolean = false): List<Video> {
        val base = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val trashOk = Build.VERSION.SDK_INT >= 30
        if (trashed && !trashOk) return emptyList()
        val cols = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (trashOk) cols += MediaStore.MediaColumns.DATE_EXPIRES
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Video.Media.DATE_ADDED} DESC")
            if (trashOk) {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
                )
            }
        }
        val result = mutableListOf<Video>()
        try {
            context.contentResolver.query(base, cols.toTypedArray(), args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val iExp = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = c.getString(iName) ?: "Video"
                    result += Video(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = name.substringBeforeLast('.', name)
                            .removePrefix(".trashed-").replace(Regex("^\\d{10}-"), ""),
                        duration = c.getLong(iDur),
                        size = c.getLong(iSize),
                        folderId = c.getLong(iBucket),
                        folderName = c.getString(iBucketName) ?: "Sin carpeta",
                        date = c.getLong(iDate),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        fileName = name,
                        path = c.getString(iPath) ?: "",
                        mime = c.getString(iMime) ?: "",
                        expires = if (iExp >= 0) c.getLong(iExp) else 0L
                    )
                }
            }
        } catch (e: Exception) {
            // Si falla la consulta, mostramos la lista vacía
        }
        return result
    }

    fun folders(videos: List<Video>): List<Carpeta> =
        videos.groupBy { it.folderId }
            .map { (id, list) -> Carpeta(id, list.first().folderName, list) }
            .sortedByDescending { c -> c.videos.maxOf { it.date } }
}

/** Guarda dónde te quedaste en cada video. */
object ProgressStore {
    private fun prefs(c: Context) = c.getSharedPreferences("progreso", Context.MODE_PRIVATE)

    fun save(c: Context, id: String, pos: Long, dur: Long) {
        if (dur <= 0) return
        val done = pos > dur - 15_000 || pos > dur * 0.97
        val e = prefs(c).edit()
        e.putLong("dur_$id", dur)
        if (done || pos < 5_000) {
            e.remove("pos_$id")
            e.remove("time_$id")
        } else {
            e.putLong("pos_$id", pos)
            e.putLong("time_$id", System.currentTimeMillis())
        }
        e.apply()
    }

    fun markDone(c: Context, id: String) {
        prefs(c).edit().remove("pos_$id").remove("time_$id").apply()
    }

    fun resumePosition(c: Context, id: String): Long = prefs(c).getLong("pos_$id", 0L)

    fun fraction(c: Context, id: String): Float {
        val p = prefs(c)
        val pos = p.getLong("pos_$id", 0L)
        val dur = p.getLong("dur_$id", 0L)
        return if (pos <= 0 || dur <= 0) 0f else (pos.toFloat() / dur).coerceIn(0f, 1f)
    }

    /** Videos a medias, del más reciente al más antiguo. */
    fun continueIds(c: Context): List<String> {
        val p = prefs(c)
        return p.all.keys
            .filter { it.startsWith("time_") }
            .map { it.removePrefix("time_") to p.getLong(it, 0L) }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

object ThumbCache {
    val cache = LruCache<String, Bitmap>(150)
}

@Composable
fun VideoThumb(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = uri.toString()
    val bmp by produceState<Bitmap?>(ThumbCache.cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(384, 216), null)
                        .also { ThumbCache.cache.put(key, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(modifier.background(Color(0xFF2A2A32))) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFF6A6A75),
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }
    }
}

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
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024)
    else String.format(Locale.US, "%.0f MB", mb)
}

fun resolutionLabel(w: Int, h: Int): String {
    val short = minOf(w, h)
    return when {
        short >= 2000 -> "4K"
        short >= 1400 -> "1440p"
        short >= 1000 -> "1080p"
        short >= 700 -> "720p"
        short > 0 -> "${short}p"
        else -> ""
    }
}

/** Lee los formatos (códecs) de video y audio de un archivo. */
fun readCodecs(context: Context, uri: Uri): String {
    val ex = MediaExtractor()
    return try {
        ex.setDataSource(context, uri, null)
        (0 until ex.trackCount)
            .mapNotNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            .map { codecName(it) }
            .distinct()
            .joinToString(", ")
            .ifBlank { "No disponible" }
    } catch (e: Exception) {
        "No disponible"
    } finally {
        ex.release()
    }
}

private fun codecName(mime: String): String = when (mime.lowercase(Locale.US)) {
    "video/avc" -> "H.264"
    "video/hevc" -> "H.265 (HEVC)"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/av01" -> "AV1"
    "video/mp4v-es" -> "MPEG-4"
    "audio/mp4a-latm" -> "AAC"
    "audio/ac3" -> "AC3 (Dolby)"
    "audio/eac3" -> "E-AC3 (Dolby)"
    "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
    "audio/mpeg" -> "MP3"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    else -> if (mime.startsWith("text/") || mime.startsWith("application/")) "Subtítulos" else mime
}
