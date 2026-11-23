@file:OptIn(UnstableApi::class)

package com.tua.pelis

import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mensajes flotantes que aparecen al usar gestos. */
sealed class Aviso {
    data class Salto(val seconds: Int) : Aviso()
    data class Buscar(val target: Long, val delta: Long) : Aviso()
    data class Brillo(val value: Float) : Aviso()
    data class Volumen(val value: Float) : Aviso()
    data class Texto(val msg: String) : Aviso()
}

private enum class Panel { VELOCIDAD, PISTAS }

private val modosAjuste = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Llenar",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Estirar"
)

// Margen extra para que nada quede sobre el borde curvo de la pantalla
private val MargenBorde = 28.dp

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    host: PlayerHost,
    inPip: Boolean,
    resumedFrom: Long,
    onResumeHandled: () -> Unit
) {
    val exo = player
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // ---------- Estado del reproductor ----------
    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var playbackState by remember { mutableIntStateOf(exo.playbackState) }
    var position by remember { mutableLongStateOf(exo.currentPosition) }
    var duration by remember { mutableLongStateOf(exo.duration.coerceAtLeast(0L)) }
    var title by remember { mutableStateOf(exo.mediaMetadata.title?.toString() ?: "") }
    var hasNext by remember { mutableStateOf(exo.hasNextMediaItem()) }
    var hasPrev by remember { mutableStateOf(exo.hasPreviousMediaItem()) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var speed by remember { mutableFloatStateOf(exo.playbackParameters.speed) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                isPlaying = p.isPlaying
                playbackState = p.playbackState
                title = p.mediaMetadata.title?.toString() ?: ""
                hasNext = p.hasNextMediaItem()
                hasPrev = p.hasPreviousMediaItem()
                tracks = p.currentTracks
                speed = p.playbackParameters.speed
                duration = p.duration.coerceAtLeast(0L)
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    // ---------- Estado de la interfaz ----------
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var ajuste by remember { mutableIntStateOf(0) }
    var aviso by remember { mutableStateOf<Aviso?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var sliderDrag by remember { mutableStateOf<Float?>(null) }
    var showResume by remember(resumedFrom) { mutableStateOf(resumedFrom > 0) }

    // Ocultar los controles solos después de unos segundos
    LaunchedEffect(controlsVisible, lastTouch, isPlaying, panel, sliderDrag != null, locked) {
        if (controlsVisible && isPlaying && panel == null && sliderDrag == null) {
            delay(if (locked) 2_000 else 4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(aviso, dragging) {
        if (aviso != null && !dragging) {
            delay(900)
            aviso = null
        }
    }
    LaunchedEffect(resumedFrom) {
        if (resumedFrom > 0) {
            delay(7_000)
            showResume = false
        }
    }

    // Con la pantalla bloqueada, el botón atrás no saca del video
    BackHandler(enabled = locked || panel != null) {
        if (panel != null) panel = null else controlsVisible = true
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
    }

    fun togglePlay() {
        when {
            exo.playbackState == Player.STATE_ENDED -> {
                exo.seekTo(0)
                exo.play()
            }
            exo.isPlaying -> exo.pause()
            else -> exo.play()
        }
        touch()
    }

    fun jump(ms: Long) {
        val d = exo.duration
        val target = (exo.currentPosition + ms).let { if (d > 0) it.coerceIn(0L, d) else it.coerceAtLeast(0L) }
        exo.seekTo(target)
        aviso = Aviso.Salto((ms / 1000).toInt())
        touch()
    }

    fun skip(next: Boolean) {
        exo.currentMediaItem?.mediaId?.let {
            ProgressStore.save(context, it, exo.currentPosition, exo.duration)
        }
        if (next) exo.seekToNextMediaItem() else exo.seekToPreviousMediaItem()
        touch()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ---------- Video ----------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exo
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = modosAjuste[ajuste].first
                view.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!inPip) {

            // ---------- Capa de gestos ----------
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                touch()
                            },
                            onDoubleTap = { o ->
                                if (!locked) {
                                    val w = size.width
                                    when {
                                        o.x < w * 0.4f -> jump(-10_000)
                                        o.x > w * 0.6f -> jump(10_000)
                                        else -> togglePlay()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        val edge = 36.dp.toPx()
                        val slop = 20.dp.toPx()
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        var mode = 0 // 0 = sin decidir, 1 = adelantar, 2 = brillo, 3 = volumen, -1 = ignorar
                        var startX = 0f
                        var accX = 0f
                        var accY = 0f
                        var startPos = 0L
                        var target = -1L
                        var bright = 0.5f
                        var vol = 0f
                        detectDragGestures(
                            onDragStart = { o ->
                                // Ignorar toques que empiezan pegados al borde (curvo) de la pantalla
                                val nearEdge = o.x < edge || o.x > size.width - edge ||
                                    o.y < edge || o.y > size.height - edge
                                mode = if (nearEdge) -1 else 0
                                startX = o.x
                                accX = 0f
                                accY = 0f
                                startPos = exo.currentPosition
                                target = -1L
                                bright = host.getBrightness()
                                vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                            onDragEnd = {
                                if (mode == 1 && target >= 0) exo.seekTo(target)
                                if (mode > 0) touch()
                                mode = 0
                                dragging = false
                            },
                            onDragCancel = {
                                mode = 0
                                dragging = false
                            },
                            onDrag = { change, amount ->
                                if (mode != -1) {
                                    change.consume()
                                    accX += amount.x
                                    accY += amount.y
                                    if (mode == 0 && (abs(accX) > slop || abs(accY) > slop)) {
                                        mode = when {
                                            abs(accX) > abs(accY) -> 1
                                            startX < size.width / 2f -> 2
                                            else -> 3
                                        }
                                        dragging = true
                                        controlsVisible = false
                                    }
                                    when (mode) {
                                        1 -> {
                                            val d = exo.duration
                                            if (d > 0) {
                                                // Deslizar todo el ancho = 2 minutos
                                                val delta = (accX / size.width * 120_000f).toLong()
                                                target = (startPos + delta).coerceIn(0L, d)
                                                aviso = Aviso.Buscar(target, target - startPos)
                                            }
                                        }
                                        2 -> {
                                            bright = (bright - amount.y / (size.height * 0.7f)).coerceIn(0.01f, 1f)
                                            host.setBrightness(bright)
                                            aviso = Aviso.Brillo(bright)
                                        }
                                        3 -> {
                                            vol = (vol - amount.y / (size.height * 0.7f) * maxVol)
                                                .coerceIn(0f, maxVol.toFloat())
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0)
                                            aviso = Aviso.Volumen(vol / maxVol)
                                        }
                                    }
                                }
                            }
                        )
                    }
            )

            if (playbackState == Player.STATE_BUFFERING) {
                CircularProgressIndicator(
                    color = Acento,
                    modifier = Modifier.align(Alignment.Center).size(64.dp)
                )
            }

            // ---------- Controles ----------
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Sombra suave solo arriba y abajo; el centro del video queda limpio
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.28f)
                            .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.35f)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    )
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = MargenBorde, vertical = 12.dp)
                    ) {
                        val compact = maxWidth < 600.dp

                        val tools: @Composable (Modifier) -> Unit = { m ->
                            ToolButton(Icons.Rounded.Lock, "Bloquear", m) {
                                locked = true
                                controlsVisible = true
                                touch()
                            }
                            ToolButton(Icons.Rounded.Speed, speedLabel(speed), m) { panel = Panel.VELOCIDAD }
                            ToolButton(Icons.Rounded.AspectRatio, modosAjuste[ajuste].second, m) {
                                ajuste = (ajuste + 1) % modosAjuste.size
                                aviso = Aviso.Texto(modosAjuste[ajuste].second)
                                touch()
                            }
                            ToolButton(Icons.Rounded.ScreenRotation, "Girar", m) {
                                host.rotate()
                                touch()
                            }
                            ToolButton(Icons.Rounded.Subtitles, "Pistas", m) { panel = Panel.PISTAS }
                            ToolButton(Icons.Rounded.PictureInPictureAlt, "Flotante", m) { host.enterPip() }
                        }

                        // ---- Arriba: regresar, título y herramientas ----
                        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar", 52.dp) { host.close() }
                                Text(
                                    title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                                )
                                if (!compact) {
                                    Row(Modifier.widthIn(max = 480.dp)) { tools(Modifier.width(76.dp)) }
                                }
                            }
                            if (compact) {
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { tools(Modifier.weight(1f)) }
                            }
                        }

                        // ---- Abajo: controles sobre la barra de tiempo ----
                        val playIcon = when {
                            playbackState == Player.STATE_ENDED -> Icons.Rounded.Replay
                            isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        }
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val playlist = hasPrev || hasNext
                                if (playlist) {
                                    RoundButton(Icons.Rounded.SkipPrevious, "Video anterior", 52.dp, enabled = hasPrev) { skip(false) }
                                }
                                RoundButton(Icons.Rounded.Replay10, "Atrás 10 segundos", 60.dp) { jump(-10_000) }
                                RoundButton(playIcon, "Reproducir o pausar", 76.dp, filled = true) { togglePlay() }
                                RoundButton(Icons.Rounded.Forward10, "Adelante 10 segundos", 60.dp) { jump(10_000) }
                                if (playlist) {
                                    RoundButton(Icons.Rounded.SkipNext, "Siguiente video", 52.dp, enabled = hasNext) { skip(true) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            val drag = sliderDrag
                            val shown = if (drag != null) (drag * duration).toLong() else position
                            Slider(
                                value = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                onValueChange = {
                                    sliderDrag = it
                                    touch()
                                },
                                onValueChangeFinished = {
                                    sliderDrag?.let { exo.seekTo((it * duration).toLong()) }
                                    sliderDrag = null
                                    touch()
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Acento,
                                    activeTrackColor = Acento,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(formatTime(shown), color = Color.White, fontSize = 15.sp)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(duration), color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // ---------- Pantalla bloqueada ----------
            if (locked) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RoundButton(Icons.Rounded.LockOpen, "Desbloquear", 84.dp, filled = true) {
                            locked = false
                            controlsVisible = true
                            touch()
                        }
                        Text(
                            "Toca para desbloquear", color = Color.White, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            // ---------- Avisos (gestos) ----------
            val a = aviso
            if (a != null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) { AvisoContent(a) }
            } else if (showResume) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = MargenBorde)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xE6202026))
                        .padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continuando desde ${formatTime(resumedFrom)}", color = Color.White, fontSize = 15.sp)
                    TextButton(onClick = {
                        exo.seekTo(0)
                        showResume = false
                        onResumeHandled()
                    }) { Text("Desde el inicio", color = Acento, fontSize = 15.sp) }
                }
            }

            // ---------- Paneles (velocidad / pistas) ----------
            val p = panel
            if (p != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { panel = null }
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Tarjeta,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = MargenBorde, vertical = 16.dp)
                        .widthIn(max = 460.dp)
                ) {
                    when (p) {
                        Panel.VELOCIDAD -> SpeedPanel(speed) {
                            exo.setPlaybackSpeed(it)
                            panel = null
                        }
                        Panel.PISTAS -> TracksPanel(tracks, exo) { panel = null }
                    }
                }
            }
        }
    }
}

// ---------- Piezas de la interfaz ----------

@Composable
private fun RoundButton(
    icon: ImageVector,
    desc: String,
    size: Dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Acento.copy(alpha = 0.72f) else Color(0x33FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, desc,
            tint = when {
                !enabled -> Color(0x55FFFFFF)
                filled -> Color(0xE6000000)
                else -> Color(0xE6FFFFFF)
            },
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color(0xE6FFFFFF), modifier = Modifier.size(26.dp))
        Text(
            label, color = Color(0xE6FFFFFF), fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AvisoContent(a: Aviso) {
    val info: Triple<ImageVector, String, Float?> = when (a) {
        is Aviso.Salto -> Triple(
            if (a.seconds < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
            (if (a.seconds > 0) "+" else "") + "${a.seconds} s",
            null
        )
        is Aviso.Buscar -> Triple(
            if (a.delta < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
            "${formatTime(a.target)}   (${if (a.delta < 0) "−" else "+"}${formatTime(abs(a.delta))})",
            null
        )
        is Aviso.Brillo -> Triple(Icons.Rounded.Brightness6, "${(a.value * 100).roundToInt()}%", a.value)
        is Aviso.Volumen -> Triple(Icons.AutoMirrored.Rounded.VolumeUp, "${(a.value * 100).roundToInt()}%", a.value)
        is Aviso.Texto -> Triple(Icons.Rounded.AspectRatio, a.msg, null)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(info.first, null, tint = Acento, modifier = Modifier.size(30.dp))
        Text(
            info.second, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
        val level = info.third
        if (level != null) {
            Box(
                Modifier.padding(start = 14.dp).width(110.dp).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0x44FFFFFF))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(Acento))
            }
        }
    }
}

fun speedLabel(s: Float): String {
    val txt = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()
    return txt + "x"
}

@Composable
private fun SpeedPanel(current: Float, onPick: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    Column(Modifier.padding(20.dp)) {
        Text(
            "Velocidad", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        speeds.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    val sel = abs(s - current) < 0.01f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Acento else Color(0xFF2E2E36))
                            .clickable { onPick(s) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            speedLabel(s), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = if (sel) Color.Black else Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private class TrackOpt(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

private fun trackLabel(f: Format, type: Int, n: Int): String {
    val parts = mutableListOf<String>()
    f.label?.takeIf { it.isNotBlank() }?.let { parts += it }
    val lang = f.language
    if (!lang.isNullOrBlank() && lang != "und") {
        val name = Locale.forLanguageTag(lang).getDisplayLanguage(Locale.forLanguageTag("es"))
        if (name.isNotBlank()) parts += name.replaceFirstChar { it.uppercase() }
    }
    if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) {
        parts += when (f.channelCount) {
            1 -> "Mono"
            2 -> "Estéreo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${f.channelCount} canales"
        }
    }
    return parts.distinct().joinToString(" · ").ifBlank { "Pista $n" }
}

private fun selectTrack(player: Player, opt: TrackOpt) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(opt.group.type, false)
        .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
        .build()
}

@Composable
private fun TracksPanel(tracks: Tracks, player: Player, onDone: () -> Unit) {
    val audioOpts = mutableListOf<TrackOpt>()
    val textOpts = mutableListOf<TrackOpt>()
    for (g in tracks.groups) {
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            when (g.type) {
                C.TRACK_TYPE_AUDIO -> audioOpts += TrackOpt(
                    g, i, trackLabel(g.getTrackFormat(i), g.type, audioOpts.size + 1), g.isTrackSelected(i)
                )
                C.TRACK_TYPE_TEXT -> textOpts += TrackOpt(
                    g, i, trackLabel(g.getTrackFormat(i), g.type, textOpts.size + 1), g.isTrackSelected(i)
                )
            }
        }
    }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
        textOpts.none { it.selected }

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Audio", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (audioOpts.size <= 1) {
            Text(
                if (audioOpts.isEmpty()) "Sin pistas de audio." else "Este video tiene una sola pista de audio.",
                color = TextoSuave, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        audioOpts.forEach { o ->
            OptionRow(o.label, o.selected) {
                selectTrack(player, o)
                onDone()
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Subtítulos", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        OptionRow("Desactivados", textOff) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            onDone()
        }
        textOpts.forEach { o ->
            OptionRow(o.label, o.selected && !textOff) {
                selectTrack(player, o)
                onDone()
            }
        }
        if (textOpts.isEmpty()) {
            Text(
                "Este video no trae subtítulos incluidos.",
                color = TextoSuave, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x33FFB74D) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Acento)
        )
        Text(label, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp))
    }
}
