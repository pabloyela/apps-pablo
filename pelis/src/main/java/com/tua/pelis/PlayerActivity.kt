package com.tua.pelis

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface PlayerHost {
    fun getBrightness(): Float
    fun setBrightness(value: Float)
    fun rotate()
    fun enterPip()
    fun close()
}

class PlayerActivity : ComponentActivity(), PlayerHost {

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_INDEX = "index"
    }

    private lateinit var player: ExoPlayer
    private val inPip = mutableStateOf(false)
    private val resumedFrom = mutableLongStateOf(0L)
    private var manualOrientation = false
    private var lastMediaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true // pausa si entra una llamada u otra app usa el audio
        )
        player.setHandleAudioBecomingNoisy(true) // pausa al quitar audífonos
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage("es")
            .build()

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                autoOrient(videoSize)
                updatePipParams()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val prev = lastMediaId
                if (prev != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // El anterior terminó: lo marcamos como visto
                    ProgressStore.markDone(this@PlayerActivity, prev)
                }
                val id = mediaItem?.mediaId
                lastMediaId = id
                if (id != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    val pos = ProgressStore.resumePosition(this@PlayerActivity, id)
                    resumedFrom.longValue = pos
                    if (pos > 0) player.seekTo(pos)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePipParams()
            }
        })

        loadFromIntent(intent)

        // Guardar el progreso cada 5 segundos mientras se reproduce
        lifecycleScope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveProgress()
            }
        }

        setContent {
            PelisTheme {
                PlayerScreen(
                    player = player,
                    host = this,
                    inPip = inPip.value,
                    resumedFrom = resumedFrom.longValue,
                    onResumeHandled = { resumedFrom.longValue = 0L }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress()
        setIntent(intent)
        loadFromIntent(intent)
    }

    private fun loadFromIntent(intent: Intent) {
        val uriList = intent.getStringArrayListExtra(EXTRA_URIS)
        val uris: List<Uri>
        val titles: List<String>
        var index = 0
        if (!uriList.isNullOrEmpty()) {
            uris = uriList.map { Uri.parse(it) }
            val t = intent.getStringArrayListExtra(EXTRA_TITLES)
            titles = uris.indices.map { t?.getOrNull(it) ?: "" }
            index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.size - 1)
        } else {
            val data = intent.data
            if (data == null) {
                finish()
                return
            }
            uris = listOf(data)
            titles = listOf(displayName(data))
        }

        val items = uris.mapIndexed { i, u ->
            MediaItem.Builder()
                .setUri(u)
                .setMediaId(u.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(titles[i]).build())
                .build()
        }
        val startId = uris[index].toString()
        val start = ProgressStore.resumePosition(this, startId)
        lastMediaId = startId
        resumedFrom.longValue = start
        manualOrientation = false
        player.setMediaItems(items, index, start)
        player.prepare()
        player.playWhenReady = true
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n.substringBeforeLast('.', n)
                }
            }
        } catch (e: Exception) {
            // sin nombre disponible
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    }

    fun saveProgress() {
        val id = player.currentMediaItem?.mediaId ?: return
        ProgressStore.save(this, id, player.currentPosition, player.duration)
    }

    // ---------- Orientación ----------

    private fun autoOrient(size: VideoSize) {
        if (manualOrientation || size.width <= 0 || size.height <= 0) return
        val w = size.width * size.pixelWidthHeightRatio
        requestedOrientation = if (w >= size.height)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    override fun rotate() {
        manualOrientation = true
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // ---------- Brillo ----------

    override fun getBrightness(): Float {
        val current = window.attributes.screenBrightness
        if (current >= 0f) return current
        return try {
            (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f)
                .coerceIn(0.05f, 1f)
        } catch (e: Exception) {
            0.5f
        }
    }

    override fun setBrightness(value: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = attrs
    }

    // ---------- Pantalla completa ----------

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------- Ventana flotante (PiP) ----------

    private fun pipParams(): PictureInPictureParams {
        val vs = player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) {
            val r = vs.width * vs.pixelWidthHeightRatio / vs.height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 0.42f -> Rational(100, 239)
                else -> Rational((vs.width * vs.pixelWidthHeightRatio).toInt(), vs.height)
            }
        } else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(player.isPlaying)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePipParams() {
        try {
            setPictureInPictureParams(pipParams())
        } catch (e: Exception) {
            // algunos equipos no aceptan ciertos tamaños; lo ignoramos
        }
    }

    override fun enterPip() {
        try {
            enterPictureInPictureMode(pipParams())
        } catch (e: Exception) {
            // PiP desactivado en ajustes
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // En Android 12+ lo hace solo (setAutoEnterEnabled)
        if (Build.VERSION.SDK_INT < 31 && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // Si cerraron la ventana flotante (la X), la actividad queda detenida: cerramos
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            saveProgress()
            player.pause()
            finish()
        }
    }

    // ---------- Ciclo de vida ----------

    override fun close() {
        saveProgress()
        finish()
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player.pause()
    }

    override fun onDestroy() {
        saveProgress()
        player.release()
        super.onDestroy()
    }
}
