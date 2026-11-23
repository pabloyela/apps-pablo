package com.tua.claroscuro

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una foto o video tomado con Claroscuro. */
data class Toma(val uri: Uri, val video: Boolean, val fecha: Long, val nombre: String, val mime: String)

private val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es"))

/** Visor de las tomas: deslizar, zoom, videos, compartir, papelera y abrir en Baúl. */
@Composable
fun VisorScreen(a: MainActivity) {
    val tomas = a.tomas.value
    BackHandler { a.cerrarVisor() }
    if (tomas.isEmpty()) {
        LaunchedEffect(Unit) { a.cerrarVisor() }
        return
    }
    val pager = rememberPagerState { tomas.size }
    var ui by remember { mutableStateOf(true) }
    val actual = tomas[pager.currentPage.coerceIn(0, tomas.size - 1)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { tomas[it].uri.toString() }, modifier = Modifier.fillMaxSize()) { page ->
            val t = tomas[page]
            if (t.video) PaginaVideo(t.uri, pager.currentPage == page) { ui = !ui }
            else FotoZoom(t.uri, a) { ui = !ui }
        }

        if (ui) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).clickable { a.cerrarVisor() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver a la cámara", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        formatoFecha.format(Date(actual.fecha)).replaceFirstChar { it.uppercase() },
                        color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${pager.currentPage + 1} de ${tomas.size}", color = Color(0xAAFFFFFF), fontSize = 12.sp)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                BotonVisor(Icons.Rounded.Share, "Compartir") { a.compartirToma(actual) }
                BotonVisor(Icons.Rounded.OpenInNew, "Abrir en Baúl") { a.abrirEnBaul(actual) }
                BotonVisor(Icons.Rounded.Delete, "Papelera", Color(0xFFFF6B6B)) { a.tomaAPapelera(actual) }
            }
        }
    }
}

@Composable
private fun RowScope.BotonVisor(icon: ImageVector, texto: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = color, modifier = Modifier.size(26.dp))
        Text(texto, color = color, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Foto con zoom: pellizca o toca dos veces. Sin zoom, deslizar cambia de foto. */
@Composable
private fun FotoZoom(uri: Uri, a: MainActivity, onToque: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { a.abrirImagen(uri, 2560) }
    }
    var escala by remember(uri) { mutableFloatStateOf(1f) }
    var desp by remember(uri) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onToque() },
                    onDoubleTap = { p ->
                        if (escala > 1.05f) {
                            escala = 1f
                            desp = Offset.Zero
                        } else {
                            escala = 2.5f
                            desp = (Offset(size.width / 2f, size.height / 2f) - p) * 1.5f
                        }
                    }
                )
            }
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val dedos = ev.changes.count { it.pressed }
                        if (dedos >= 2 || escala > 1.05f) {
                            escala = (escala * ev.calculateZoom()).coerceIn(1f, 6f)
                            val pan = ev.calculatePan()
                            val mx = (escala - 1f) * size.width / 2f
                            val my = (escala - 1f) * size.height / 2f
                            desp = Offset((desp.x + pan.x).coerceIn(-mx, mx), (desp.y + pan.y).coerceIn(-my, my))
                            if (escala <= 1.01f) desp = Offset.Zero
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                b.asImageBitmap(), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = desp.x
                    translationY = desp.y
                }
            )
        }
    }
}

/** Video: portada con botón de play; al tocarlo se reproduce ahí mismo. */
@Composable
private fun PaginaVideo(uri: Uri, visible: Boolean, onToque: () -> Unit) {
    val context = LocalContext.current
    var reproduciendo by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) reproduciendo = false }

    if (reproduciendo) {
        val player = remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(player, owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
            owner.lifecycle.addObserver(obs)
            onDispose {
                owner.lifecycle.removeObserver(obs)
                player.release()
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val portada by produceState<Bitmap?>(null, uri) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(1280, 1280), null)
                } catch (e: Exception) {
                    null
                }
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(uri) { detectTapGestures(onTap = { onToque() }) },
            contentAlignment = Alignment.Center
        ) {
            val p = portada
            if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Color(0x99000000)).clickable { reproduciendo = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.PlayCircle, "Reproducir", tint = Color.White, modifier = Modifier.size(80.dp)) }
        }
    }
}
