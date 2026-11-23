package com.tua.baul

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/** Lo que el reproductor necesita de la actividad (brillo, girar, cerrar). */
interface PlayerHost {
    fun getBrightness(): Float
    fun setBrightness(value: Float)
    fun rotate()
    fun enterPip()
    fun close()
}

/**
 * Reproductor dentro de Baúl (los mismos controles grandes de Pelis).
 * Los videos privados nunca salen de la app.
 */
@Composable
fun ReproductorScreen(a: MainActivity, uri: Uri, titulo: String) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredAudioLanguage("es").build()
                setMediaItem(
                    MediaItem.Builder().setUri(uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).build())
                        .build()
                )
                prepare()
                playWhenReady = true
            }
    }

    BackHandler { a.atras() }

    // Pantalla completa, giro automático según el video, y todo de vuelta al salir
    DisposableEffect(player) {
        a.modoReproductor(true)
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0 && !a.giroManual) {
                    a.requestedOrientation =
                        if (videoSize.width * videoSize.pixelWidthHeightRatio >= videoSize.height)
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
            a.modoReproductor(false)
        }
    }

    // Pausar si la app pasa a segundo plano
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    ReproductorUI(
        player = player,
        host = a,
        inPip = false,
        resumedFrom = 0L,
        onResumeHandled = {}
    )
}
