package com.tua.baul

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Un archivo guardado en el área privada. */
data class VItem(
    val file: String,     // nombre del archivo dentro del área
    val name: String,     // nombre original
    val mime: String,
    val date: Long,       // fecha de la foto (ms)
    val path: String,     // carpeta original, para devolverlo a su lugar
    val album: String = "" // álbum privado ("" = sin álbum)
) {
    val video get() = esVideoMime(mime)
}

/**
 * Áreas privadas: 0 = la verdadera, 1 = la señuelo.
 * Viven en el espacio privado de la app; ninguna otra app las ve.
 */
object Boveda {

    const val REAL = 0
    const val SENUELO = 1

    fun dir(c: Context, esp: Int) = File(c.filesDir, if (esp == REAL) "boveda" else "boveda2").apply { mkdirs() }
    private fun indice(c: Context, esp: Int) = File(dir(c, esp), "indice.json")
    private fun prefs(c: Context) = c.getSharedPreferences("privado", Context.MODE_PRIVATE)
    private fun sufijo(esp: Int) = if (esp == REAL) "" else "1"

    // ---------- Claves y patrón ----------

    private fun derivar(secreto: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secreto.toCharArray(), salt, 20000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun guardarSecreto(c: Context, clave: String, secreto: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString("salt$clave", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash$clave", Base64.encodeToString(derivar(secreto, salt), Base64.NO_WRAP))
            .apply()
    }

    private fun verificar(c: Context, clave: String, secreto: String): Boolean {
        val p = prefs(c)
        val salt = Base64.decode(p.getString("salt$clave", null) ?: return false, Base64.NO_WRAP)
        val hash = Base64.decode(p.getString("hash$clave", null) ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(derivar(secreto, salt), hash)
    }

    private fun quitar(c: Context, clave: String) {
        prefs(c).edit().remove("salt$clave").remove("hash$clave").apply()
    }

    fun tieneClave(c: Context, esp: Int) = prefs(c).contains("hash" + sufijo(esp))
    fun ponerClave(c: Context, esp: Int, pin: String) = guardarSecreto(c, sufijo(esp), pin)
    fun comprobar(c: Context, esp: Int, pin: String) = pin.length >= 4 && verificar(c, sufijo(esp), pin)
    fun quitarClave(c: Context, esp: Int) = quitar(c, sufijo(esp))

    fun tienePatron(c: Context) = prefs(c).contains("hashP")
    fun ponerPatron(c: Context, patron: List<Int>) = guardarSecreto(c, "P", patron.joinToString("-"))
    fun comprobarPatron(c: Context, patron: List<Int>) = patron.size >= 4 && verificar(c, "P", patron.joinToString("-"))
    fun quitarPatron(c: Context) = quitar(c, "P")

    // ---------- Respaldo automático ----------

    fun ultimoRespaldo(c: Context, esp: Int) = prefs(c).getLong("ultimo$esp", 0L)
    fun marcarRespaldo(c: Context, esp: Int) = prefs(c).edit().putLong("ultimo$esp", System.currentTimeMillis()).apply()
    fun carpetaRespaldo(c: Context): String? = prefs(c).getString("carpeta", null)
    fun ponerCarpetaRespaldo(c: Context, uri: String) = prefs(c).edit().putString("carpeta", uri).apply()

    // ---------- Índice ----------

    private fun leer(o: JSONObject) = VItem(
        o.getString("file"), o.optString("name"), o.optString("mime"),
        o.optLong("date"), o.optString("path"), o.optString("album")
    )

    fun items(c: Context, esp: Int): List<VItem> {
        val f = indice(c, esp)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { leer(arr.getJSONObject(it)) }
                .filter { File(dir(c, esp), it.file).exists() }
                .sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun guardar(c: Context, esp: Int, list: List<VItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("file", it.file).put("name", it.name).put("mime", it.mime)
                    .put("date", it.date).put("path", it.path).put("album", it.album)
            )
        }
        val tmp = File(dir(c, esp), "indice.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indice(c, esp))
    }

    fun archivo(c: Context, esp: Int, item: VItem) = File(dir(c, esp), item.file)

    fun albumes(items: List<VItem>): List<String> = items.map { it.album }.filter { it.isNotBlank() }.distinct().sorted()

    fun asignarAlbum(c: Context, esp: Int, files: Set<String>, album: String) {
        guardar(c, esp, items(c, esp).map { if (it.file in files) it.copy(album = album) else it })
    }

    // ---------- Meter y sacar ----------

    fun meter(c: Context, esp: Int, medias: List<Media>): List<Pair<Media, VItem>> {
        val hechos = mutableListOf<Pair<Media, VItem>>()
        for (m in medias) {
            val ext = m.name.substringAfterLast('.', if (m.video) "mp4" else "jpg")
            val nombre = UUID.randomUUID().toString() + "." + ext
            val destino = File(dir(c, esp), nombre)
            try {
                c.contentResolver.openInputStream(m.uri)?.use { input ->
                    destino.outputStream().use { input.copyTo(it) }
                } ?: continue
                hechos += m to VItem(nombre, m.name, m.mime, m.date, m.path)
            } catch (e: Exception) {
                destino.delete()
            }
        }
        if (hechos.isNotEmpty()) guardar(c, esp, items(c, esp) + hechos.map { it.second })
        return hechos
    }

    fun deshacer(c: Context, esp: Int, sel: List<VItem>) {
        sel.forEach { archivo(c, esp, it).delete() }
        val nombres = sel.map { it.file }.toSet()
        guardar(c, esp, items(c, esp).filter { it.file !in nombres })
    }

    fun sacar(c: Context, esp: Int, sel: List<VItem>): Int {
        var ok = 0
        val sacados = mutableSetOf<String>()
        for (v in sel) {
            val coleccion = if (v.video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val ruta = if (Repo.destinoValido(v.path)) v.path else "Pictures/Recuperadas/"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, v.name.ifBlank { v.file })
                put(MediaStore.MediaColumns.MIME_TYPE, v.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, ruta)
                put(MediaStore.MediaColumns.DATE_TAKEN, v.date)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri: Uri = c.contentResolver.insert(coleccion, values) ?: continue
            try {
                c.contentResolver.openOutputStream(uri)?.use { out ->
                    archivo(c, esp, v).inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                c.contentResolver.update(uri, values, null, null)
                archivo(c, esp, v).delete()
                sacados += v.file
                ok++
            } catch (e: Exception) {
                c.contentResolver.delete(uri, null, null)
            }
        }
        guardar(c, esp, items(c, esp).filter { it.file !in sacados })
        return ok
    }

    fun eliminar(c: Context, esp: Int, sel: List<VItem>) {
        sel.forEach { archivo(c, esp, it).delete() }
        val nombres = sel.map { it.file }.toSet()
        guardar(c, esp, items(c, esp).filter { it.file !in nombres })
    }

    fun miniatura(c: Context, esp: Int, item: VItem): Bitmap? = try {
        val f = archivo(c, esp, item)
        if (item.video) ThumbnailUtils.createVideoThumbnail(f, Size(320, 320), null)
        else ThumbnailUtils.createImageThumbnail(f, Size(320, 320), null)
    } catch (e: Exception) {
        null
    }

    // ---------- Respaldo cifrado ----------
    // Formato: "BAUL1" + salt(16) + iv(16) + [AES-CBC: "BAULOK" + ZIP con los archivos]

    private val MAGIA = "BAUL1".toByteArray()
    private val CONTROL = "BAULOK".toByteArray()

    private fun cifrador(modo: Int, pin: String, salt: ByteArray, iv: ByteArray): Cipher {
        val key = SecretKeySpec(derivar(pin, salt), "AES")
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(modo, key, IvParameterSpec(iv)) }
    }

    fun respaldar(c: Context, esp: Int, pin: String, out: OutputStream) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        out.write(MAGIA)
        out.write(salt)
        out.write(iv)
        val cos = CipherOutputStream(out, cifrador(Cipher.ENCRYPT_MODE, pin, salt, iv))
        cos.write(CONTROL)
        val zip = ZipOutputStream(cos)
        val todos = items(c, esp)
        zip.putNextEntry(ZipEntry("indice.json"))
        val idx = indice(c, esp)
        zip.write(if (idx.exists()) idx.readBytes() else "[]".toByteArray())
        zip.closeEntry()
        for (v in todos) {
            zip.putNextEntry(ZipEntry(v.file))
            archivo(c, esp, v).inputStream().use { inp -> inp.copyTo(zip) }
            zip.closeEntry()
        }
        zip.close()
        marcarRespaldo(c, esp)
    }

    /** Devuelve cuántos archivos se recuperaron; -1 clave incorrecta; -2 no es un respaldo. */
    fun restaurar(c: Context, esp: Int, pin: String, input: InputStream): Int {
        val magia = ByteArray(MAGIA.size)
        if (input.leerTodo(magia) != magia.size || !magia.contentEquals(MAGIA)) return -2
        val salt = ByteArray(16)
        val iv = ByteArray(16)
        input.leerTodo(salt)
        input.leerTodo(iv)
        val cis = CipherInputStream(input, cifrador(Cipher.DECRYPT_MODE, pin, salt, iv))
        val control = ByteArray(CONTROL.size)
        try {
            if (cis.leerTodo(control) != control.size || !control.contentEquals(CONTROL)) return -1
        } catch (e: Exception) {
            return -1
        }
        val zip = ZipInputStream(cis)
        var nuevos: List<VItem> = emptyList()
        var n = 0
        while (true) {
            val e = zip.nextEntry ?: break
            val nombre = File(e.name).name
            if (nombre == "indice.json") {
                val arr = JSONArray(String(zip.readBytes()))
                nuevos = (0 until arr.length()).map { leer(arr.getJSONObject(it)) }
            } else {
                File(dir(c, esp), nombre).outputStream().use { zip.copyTo(it) }
                n++
            }
            zip.closeEntry()
        }
        zip.close()
        val actuales = items(c, esp)
        val existentes = actuales.map { it.file }.toSet()
        guardar(c, esp, actuales + nuevos.filter { it.file !in existentes })
        return n
    }
}

/** Lee exactamente buf.size bytes (o menos si se acaba). */
private fun InputStream.leerTodo(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val r = read(buf, total, buf.size - total)
        if (r < 0) break
        total += r
    }
    return total
}
