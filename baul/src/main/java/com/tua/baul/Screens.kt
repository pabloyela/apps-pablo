package com.tua.baul

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

// ======================================================================
// Raíz
// ======================================================================

@Composable
fun App(a: MainActivity) {
    val guardian = rememberSaveableStateHolder()
    Surface(color = Fondo, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        if (!a.permiso.value) PermisoScreen(a)
        else when (val p = a.pila.last()) {
            // Fotos y álbumes recuerdan dónde te quedaste al volver del visor
            is Pant.Inicio -> guardian.SaveableStateProvider("inicio") { InicioScreen(a) }
            is Pant.AlbumP -> guardian.SaveableStateProvider("album:" + p.titulo) { AlbumScreen(a, p.titulo, p.filtro) }
            is Pant.Visor -> VisorScreen(a, p.lista, p.inicio)
            is Pant.Editor -> EditorScreen(a, p.media)
            is Pant.Papelera -> PapeleraScreen(a)
            is Pant.Ajustes -> AjustesScreen(a)
            is Pant.Boveda -> BovedaScreen(a)
            is Pant.VisorBoveda -> VisorBovedaScreen(a, p.inicio)
            is Pant.Reproductor -> ReproductorScreen(a, p.uri, p.titulo)
            is Pant.Limpieza -> LimpiezaScreen(a)
            is Pant.Patron -> PatronScreen(a)
            is Pant.CrearPatron -> CrearPatronScreen(a)
        }
    }
}

@Composable
private fun PermisoScreen(a: MainActivity) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = Acento, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text("Baúl necesita permiso para ver tus fotos y videos.", fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { a.pedirPermiso() },
            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) { Text("Dar permiso", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Elige \"Permitir todo\". Si ya lo negaste: Ajustes › Apps › Baúl › Permisos.",
            color = Suave, fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }
}

// ======================================================================
// Inicio: Fotos y Álbumes
// ======================================================================

private enum class Filtro(val nombre: String) { TODO("Todo"), FOTOS("Fotos"), VIDEOS("Videos"), FAVORITOS("Favoritos"), CAPTURAS("Capturas") }

@Composable
private fun InicioScreen(a: MainActivity) {
    val todos = a.medios.value
    var sel by remember { mutableStateOf(setOf<Long>()) }
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODO) }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var texto by rememberSaveable { mutableStateOf("") }
    var mes by rememberSaveable { mutableStateOf<java.time.YearMonth?>(null) } // filtro por mes y año
    var elegirMes by remember { mutableStateOf(false) }
    val favs = a.favoritos.value

    BackHandler(enabled = sel.isNotEmpty() || buscando) {
        if (sel.isNotEmpty()) sel = emptySet() else { buscando = false; texto = "" }
    }

    // La clave escrita en el buscador abre el área privada
    LaunchedEffect(texto) {
        if (texto.length >= 4) {
            delay(250)
            a.probarClave(texto)
        }
    }
    LaunchedEffect(a.aperturas.intValue) {
        if (a.aperturas.intValue > 0) {
            texto = ""
            buscando = false
        }
    }

    val ocultos = a.ocultos.value
    val consulta = remember(texto) { Consulta.de(texto) }
    val lista = remember(todos, filtro, consulta, favs, ocultos, mes) {
        (todos ?: emptyList()).filter { m ->
            val sinBusqueda = texto.isBlank() && mes == null
            if (filtro != Filtro.FAVORITOS && sinBusqueda && m.bucketId.toString() in ocultos) return@filter false
            val d = diaDe(m.date)
            val mf = mes
            if (mf != null && (d.year != mf.year || d.monthValue != mf.monthValue)) return@filter false
            if (!consulta.coincideFecha(d)) return@filter false
            val pasa = when (filtro) {
                Filtro.TODO -> true
                Filtro.FOTOS -> !m.video
                Filtro.VIDEOS -> m.video
                Filtro.FAVORITOS -> m.uri.toString() in favs
                Filtro.CAPTURAS -> Repo.esCaptura(m)
            }
            pasa && consulta.coincideTexto(m)
        }
    }
    val elegidos = remember(lista, sel) { lista.filter { it.id in sel } }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (sel.isNotEmpty()) {
            CabeceraSeleccion(sel.size, onCerrar = { sel = emptySet() }, onTodo = { sel = lista.map { it.id }.toSet() })
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Baúl", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).pointerInput(Unit) {
                        detectTapGestures(onLongPress = { a.irPatron() })
                    }
                )
                if (a.bovedaAbierta.value) {
                    IconoBoton(Icons.Rounded.Lock, "Volver a privado") { a.volverABoveda() }
                }
                IconoBoton(Icons.Rounded.CleaningServices, "Liberar espacio") { a.ir(Pant.Limpieza) }
                IconoBoton(Icons.Rounded.CalendarMonth, "Buscar por fecha") { elegirMes = true }
                IconoBoton(Icons.Rounded.Search, "Buscar") { buscando = !buscando; if (!buscando) texto = "" }
                IconoBoton(Icons.Rounded.Settings, "Ajustes") { a.ir(Pant.Ajustes) }
            }
        }
        if (buscando && sel.isEmpty()) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                placeholder = { Text("Nombre, álbum o fecha (ej. marzo 2024)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Tarjeta, unfocusedContainerColor = Tarjeta,
                    focusedIndicatorColor = Acento
                )
            )
        }

        Box(Modifier.weight(1f)) {
            if (todos == null) {
                CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
            } else if (a.pestana.intValue == 0) {
                MediaGrid(a, lista, sel, { sel = it }, arriba = {
                    Column {
                    val mf = mes
                    if (mf != null) {
                        Row(
                            Modifier.padding(start = 12.dp, top = 6.dp).clip(RoundedCornerShape(20.dp))
                                .background(Acento).clickable { mes = null }
                                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(nombreMes(mf), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Rounded.Close, "Quitar fecha", tint = Color.Black, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        }
                    }
                    if (filtro == Filtro.TODO && texto.isBlank() && mf == null) Recuerdos(a, todos)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(Filtro.entries) { f -> Pildora(f.nombre, f == filtro) { filtro = f; sel = emptySet() } }
                    }
                    }
                })
            } else {
                AlbumesGrid(a, todos)
            }
        }

        if (sel.isNotEmpty()) {
            BarraSeleccion(a, elegidos) { sel = emptySet() }
        } else {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(vertical = 4.dp)) {
                PestanaInferior(Icons.Rounded.Photo, "Fotos", a.pestana.intValue == 0) { a.pestana.intValue = 0 }
                PestanaInferior(Icons.Rounded.Folder, "Álbumes", a.pestana.intValue == 1) { a.pestana.intValue = 1 }
            }
        }
    }
    if (elegirMes) {
        SelectorMes(todos ?: emptyList(), onElegir = { ym ->
            mes = ym
            elegirMes = false
            a.pestana.intValue = 0
            sel = emptySet()
        }, onCerrar = { elegirMes = false })
    }
}

@Composable
private fun RowScope.PestanaInferior(icon: ImageVector, texto: String, activo: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = if (activo) Acento else Suave, modifier = Modifier.size(26.dp))
        Text(texto, color = if (activo) Acento else Suave, fontSize = 13.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

// ======================================================================
// Cuadrícula con selección cómoda
// ======================================================================

private sealed class Celda {
    data class Cabecera(val dia: LocalDate, val ids: List<Long>) : Celda()
    data class Item(val m: Media) : Celda()
}

/**
 * Cuadrícula de fotos:
 * - Toque: abre (o, si estás seleccionando, marca/desmarca tocando CUALQUIER parte).
 * - Mantener presionado y arrastrar: selecciona muchas de un solo trazo.
 * - Pellizcar: cambia el tamaño de las miniaturas.
 */
@Composable
fun MediaGrid(
    a: MainActivity,
    lista: List<Media>,
    sel: Set<Long>,
    onSel: (Set<Long>) -> Unit,
    agrupar: Boolean = true,
    arriba: (@Composable () -> Unit)? = null
) {
    val cols = a.columnas.intValue
    val celdas = remember(lista, agrupar) {
        if (!agrupar) lista.map { Celda.Item(it) }
        else {
            val out = mutableListOf<Celda>()
            lista.groupBy { diaDe(it.date) }.forEach { (dia, ms) ->
                out += Celda.Cabecera(dia, ms.map { it.id })
                ms.forEach { out += Celda.Item(it) }
            }
            out
        }
    }
    val state = rememberLazyGridState()
    val selActual by rememberUpdatedState(sel)
    val onSelActual by rememberUpdatedState(onSel)
    val listaActual by rememberUpdatedState(lista)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val bordePx = with(densidad) { 80.dp.toPx() }
    var ignorarToque by remember { mutableStateOf(false) }
    val favs = a.favoritos.value

    fun idEn(pos: Offset): Long? = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        pos.x >= info.offset.x && pos.x < info.offset.x + info.size.width &&
            pos.y >= info.offset.y && pos.y < info.offset.y + info.size.height
    }?.key as? Long

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = state,
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            // Cada gesto empieza "limpio"
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    ignorarToque = false
                }
            }
            // Pellizcar para cambiar columnas (solo con dos dedos)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var acumulado = 1f
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.count { it.pressed } >= 2) {
                            acumulado *= ev.calculateZoom()
                            val c = a.columnas.intValue
                            if (acumulado > 1.3f && c > 2) {
                                a.guardarColumnas(c - 1); acumulado = 1f
                            } else if (acumulado < 0.77f && c < 6) {
                                a.guardarColumnas(c + 1); acumulado = 1f
                            }
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            // Toque
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    if (ignorarToque) return@detectTapGestures
                    val id = idEn(pos) ?: return@detectTapGestures
                    val s = selActual
                    if (s.isNotEmpty()) {
                        onSelActual(if (id in s) s - id else s + id)
                    } else {
                        val l = listaActual
                        val i = l.indexOfFirst { it.id == id }
                        if (i >= 0) a.ir(Pant.Visor(l, i))
                    }
                })
            }
            // Mantener presionado + arrastrar = seleccionar varias
            .pointerInput(Unit) {
                var ancla = -1
                var base = emptySet<Long>()
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val id = idEn(pos) ?: return@detectDragGesturesAfterLongPress
                        ignorarToque = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ancla = listaActual.indexOfFirst { it.id == id }
                        base = selActual
                        onSelActual(base + id)
                    },
                    onDragEnd = { ancla = -1 },
                    onDragCancel = { ancla = -1 },
                    onDrag = { change, _ ->
                        if (ancla < 0) return@detectDragGesturesAfterLongPress
                        change.consume()
                        val id = idEn(change.position)
                        if (id != null) {
                            val l = listaActual
                            val i = l.indexOfFirst { it.id == id }
                            if (i >= 0) {
                                val rango = if (i >= ancla) ancla..i else i..ancla
                                onSelActual(base + rango.map { l[it].id })
                            }
                        }
                        // Desplazamiento automático cerca de los bordes
                        val y = change.position.y
                        if (y > size.height - bordePx) scope.launch { state.scrollBy(28f) }
                        else if (y < bordePx) scope.launch { state.scrollBy(-28f) }
                    }
                )
            }
    ) {
        if (arriba != null) {
            item(key = "arriba", span = { GridItemSpan(maxLineSpan) }) { arriba() }
        }
        if (lista.isEmpty()) {
            item(key = "vacio", span = { GridItemSpan(maxLineSpan) }) {
                Text("Nada por aquí.", color = Suave, fontSize = 16.sp, modifier = Modifier.padding(24.dp))
            }
        }
        celdas.forEach { c ->
            when (c) {
                is Celda.Cabecera -> item(key = "d" + c.dia.toString(), span = { GridItemSpan(maxLineSpan) }) {
                    val todosDia = c.ids.all { it in sel }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(etiquetaDia(c.dia), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        // Seleccionar todo el día de un toque
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable {
                                onSel(if (todosDia) sel - c.ids.toSet() else sel + c.ids)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (todosDia) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                "Seleccionar el día",
                                tint = if (todosDia) Acento else Color(0x66FFFFFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                is Celda.Item -> item(key = c.m.id) {
                    CeldaMedia(c.m, c.m.id in sel, sel.isNotEmpty(), c.m.uri.toString() in favs)
                }
            }
        }
    }
    if (agrupar && lista.size > 60) {
        BarraFechas(state, celdas, if (arriba != null) 1 else 0, Modifier.align(Alignment.TopEnd))
    }
    }
}

/** Barra lateral para saltar rápido entre meses. Arrastra la pestaña dorada. */
@Composable
private fun BarraFechas(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    celdas: List<Celda>,
    desfase: Int,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val pastillaPx = with(densidad) { 56.dp.toPx() }
    var altoPx by remember { mutableFloatStateOf(1f) }
    var arrastrando by remember { mutableStateOf(false) }
    var yArrastre by remember { mutableFloatStateOf(0f) }
    val fraccion by remember {
        derivedStateOf {
            val tot = state.layoutInfo.totalItemsCount.coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / tot).coerceIn(0f, 1f)
        }
    }
    val recorrido = (altoPx - pastillaPx).coerceAtLeast(1f)
    val y = if (arrastrando) yArrastre else fraccion * recorrido
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es")) }

    fun etiqueta(): String {
        val idx = ((y / recorrido) * (celdas.size - 1)).toInt().coerceIn(0, (celdas.size - 1).coerceAtLeast(0))
        val c = celdas.getOrNull(idx) ?: return ""
        val d = when (c) {
            is Celda.Cabecera -> c.dia
            is Celda.Item -> diaDe(c.m.date)
        }
        return d.format(fmt).replaceFirstChar { it.uppercase() }
    }

    Box(modifier.fillMaxHeight().width(150.dp).onSizeChanged { altoPx = it.height.toFloat() }) {
        if (arrastrando) {
            Text(
                etiqueta(), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(-with(densidad) { 44.dp.roundToPx() }, (y + pastillaPx / 4).toInt()) }
                    .clip(RoundedCornerShape(14.dp)).background(Acento)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, y.toInt()) }
                .size(width = 36.dp, height = 56.dp)
                .pointerInput(celdas) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            yArrastre = fraccion * recorrido
                            arrastrando = true
                        },
                        onDragEnd = { arrastrando = false },
                        onDragCancel = { arrastrando = false },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            yArrastre = (yArrastre + delta).coerceIn(0f, recorrido)
                            val total = celdas.size + desfase
                            val idx = ((yArrastre / recorrido) * (total - 1)).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
                            scope.launch { state.scrollToItem(idx) }
                        }
                    )
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier.padding(end = 4.dp).width(if (arrastrando) 8.dp else 6.dp).height(44.dp)
                    .clip(RoundedCornerShape(4.dp)).background(if (arrastrando) Acento else Color(0x99FFFFFF))
            )
        }
    }
}

@Composable
private fun CeldaMedia(m: Media, seleccionado: Boolean, seleccionando: Boolean, favorito: Boolean) {
    Box(Modifier.aspectRatio(1f).padding(1.dp)) {
        Miniatura(m.uri, Modifier.fillMaxSize())
        if (m.video) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatTime(m.duration), color = Color.White, fontSize = 11.sp)
            }
        }
        if (favorito) {
            Icon(
                Icons.Rounded.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp)
            )
        }
        if (seleccionado) {
            Box(Modifier.fillMaxSize().background(Acento.copy(alpha = 0.28f)).border(3.dp, Acento))
        }
        if (seleccionando) {
            Icon(
                if (seleccionado) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (seleccionado) Acento else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
                    .clip(CircleShape).background(if (seleccionado) Color.Black else Color(0x55000000))
            )
        }
    }
}

@Composable
private fun CabeceraSeleccion(n: Int, onCerrar: () -> Unit, onTodo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoBoton(Icons.Rounded.Close, "Cancelar", onCerrar)
        Text(
            if (n == 1) "1 seleccionado" else "$n seleccionados",
            color = Acento, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        TextButton(onClick = onTodo) { Text("Todo", color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun BarraSeleccion(a: MainActivity, elegidos: List<Media>, limpiar: () -> Unit) {
    var mover by remember { mutableStateOf(false) }
    var firmar by remember { mutableStateOf(false) }
    val favs = a.favoritos.value
    val todosFav = elegidos.isNotEmpty() && elegidos.all { it.uri.toString() in favs }
    Row(Modifier.fillMaxWidth().background(Tarjeta).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Accion(Icons.Rounded.Share, "Compartir") { a.compartir(elegidos) }
        Accion(Icons.Rounded.DriveFileMove, "Mover") { mover = true }
        Accion(if (todosFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito") {
            a.alternarFavorito(elegidos)
        }
        if (a.bovedaAbierta.value) {
            Accion(Icons.Rounded.Lock, "Privado") { a.ocultar(elegidos) { limpiar() } }
        }
        if (elegidos.any { !it.video }) Accion(Icons.Rounded.Draw, "Firmar") { firmar = true }
        Accion(Icons.Rounded.Delete, "Papelera", Color(0xFFFF6B6B)) { a.aPapelera(elegidos) { limpiar() } }
    }
    if (firmar) {
        val nFotos = elegidos.count { !it.video }
        var f by remember { mutableStateOf(a.firma.value) }
        AlertDialog(
            onDismissRequest = { firmar = false },
            title = { Text(if (nFotos == 1) "Firmar 1 foto" else "Firmar $nFotos fotos") },
            text = {
                Column {
                    Text(
                        "Se guarda una copia de cada foto con la fecha en que se tomó y tu firma, abajo a la izquierda. " +
                            "Las originales no se tocan." +
                            (if (nFotos < elegidos.size) " Los videos se omiten." else ""),
                        fontSize = 14.sp, color = Suave
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = f, onValueChange = { f = it.take(30) }, singleLine = true,
                        label = { Text("Firma (opcional)") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    firmar = false
                    a.firmarLote(elegidos, f.trim())
                    limpiar()
                }) { Text("Firmar", color = Acento) }
            },
            dismissButton = { TextButton(onClick = { firmar = false }) { Text("Cancelar", color = Suave) } }
        )
    }
    if (mover) {
        MoverDialog(a, onElegir = { carpeta ->
            mover = false
            a.mover(elegidos, carpeta)
            limpiar()
        }, onCerrar = { mover = false })
    }
}

@Composable
fun RowScope.Accion(icon: ImageVector, texto: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = color, modifier = Modifier.size(26.dp))
        Text(texto, color = color, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun IconoBoton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun MoverDialog(a: MainActivity, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val albums = remember(a.medios.value) {
        Repo.albums(a.medios.value ?: emptyList()).filter { Repo.destinoValido(it.path) }
    }
    var nuevo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Mover a…") },
        text = {
            Column {
                OutlinedTextField(
                    value = nuevo,
                    onValueChange = { nuevo = it.replace("/", "").take(40) },
                    singleLine = true,
                    placeholder = { Text("Nuevo álbum") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (nuevo.isNotBlank()) {
                    TextButton(onClick = { onElegir("Pictures/${nuevo.trim()}/") }) {
                        Text("Crear «${nuevo.trim()}» y mover", color = Acento)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(albums) { al ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onElegir(al.path) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Miniatura(al.items.first().uri, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), 160)
                            Text(al.nombre, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Suave) } }
    )
}

// ======================================================================
// Búsqueda por mes y año
// ======================================================================

fun nombreMes(ym: java.time.YearMonth): String =
    ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es")))
        .replaceFirstChar { it.uppercase() }

@Composable
private fun SelectorMes(todos: List<Media>, onElegir: (java.time.YearMonth) -> Unit, onCerrar: () -> Unit) {
    // Cuántas fotos hay en cada mes de cada año
    val porAnio = remember(todos) {
        todos.groupBy { val d = diaDe(it.date); java.time.YearMonth.of(d.year, d.monthValue) }
            .mapValues { it.value.size }
            .entries.groupBy { it.key.year }
            .toSortedMap(compareByDescending { it })
    }
    val corto = remember { java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es")) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Ir a una fecha") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                porAnio.forEach { (anio, meses) ->
                    item(key = anio) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("$anio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Acento)
                            Spacer(Modifier.height(6.dp))
                            meses.sortedBy { it.key.monthValue }.chunked(4).forEach { fila ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                    fila.forEach { (ym, n) ->
                                        Column(
                                            Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).background(Tarjeta)
                                                .clickable { onElegir(ym) }.padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(ym.format(corto).trimEnd('.').replaceFirstChar { it.uppercase() }, fontSize = 15.sp)
                                            Text("$n", fontSize = 12.sp, color = Suave)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = Suave) } }
    )
}

// ======================================================================
// Un día como hoy
// ======================================================================

@Composable
private fun Recuerdos(a: MainActivity, todos: List<Media>) {
    val hoy = remember { LocalDate.now() }
    val recuerdos = remember(todos) {
        todos.filter { m ->
            val d = diaDe(m.date)
            d.monthValue == hoy.monthValue && d.dayOfMonth == hoy.dayOfMonth && d.year < hoy.year
        }
    }
    if (recuerdos.isEmpty()) return
    val anios = recuerdos.map { diaDe(it.date).year }.distinct()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp))
            .background(Tarjeta).clickable {
                a.ir(Pant.AlbumP("Un día como hoy") { m ->
                    val d = diaDe(m.date)
                    d.monthValue == hoy.monthValue && d.dayOfMonth == hoy.dayOfMonth && d.year < hoy.year
                })
            }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Miniatura(recuerdos.first().uri, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), 256)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = Acento, modifier = Modifier.size(18.dp))
                Text("  Un día como hoy", color = Acento, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            val hace = hoy.year - anios.max()
            Text(
                if (anios.size == 1) "Hace $hace ${if (hace == 1) "año" else "años"} · ${recuerdos.size} elementos"
                else "${anios.size} años distintos · ${recuerdos.size} elementos",
                fontSize = 14.sp, color = Suave
            )
        }
    }
}

// ======================================================================
// Álbumes
// ======================================================================

@Composable
private fun AlbumesGrid(a: MainActivity, todos: List<Media>) {
    val fijados = a.fijados.value
    val ocultosA = a.ocultos.value
    val albums = remember(todos, fijados) {
        Repo.albums(todos).sortedByDescending { it.id.toString() in fijados }
    }
    val favs = a.favoritos.value
    var menu by remember { mutableStateOf<Album?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val favoritos = todos.filter { it.uri.toString() in favs }
        if (favoritos.isNotEmpty()) {
            item(key = "fav") {
                TarjetaAlbum("Favoritos", favoritos.size, favoritos.first().uri, Icons.Rounded.Favorite) {
                    a.ir(Pant.AlbumP("Favoritos") { m -> m.uri.toString() in a.favoritos.value })
                }
            }
        }
        val videos = todos.filter { it.video }
        if (videos.isNotEmpty()) {
            item(key = "vid") {
                TarjetaAlbum("Videos", videos.size, videos.first().uri, Icons.Rounded.Videocam) {
                    a.ir(Pant.AlbumP("Videos") { it.video })
                }
            }
        }
        items(albums.size, key = { albums[it].id }) { i ->
            val al = albums[i]
            val marca = when {
                al.id.toString() in fijados -> Icons.Rounded.PushPin
                al.id.toString() in ocultosA -> Icons.Rounded.VisibilityOff
                else -> null
            }
            TarjetaAlbum(al.nombre, al.items.size, al.items.first().uri, marca, onLargo = { menu = al }) {
                val id = al.id
                a.ir(Pant.AlbumP(al.nombre) { it.bucketId == id })
            }
        }
        item(key = "limpieza") {
            TarjetaAlbum("Liberar espacio", -1, null, Icons.Rounded.CleaningServices) { a.ir(Pant.Limpieza) }
        }
        item(key = "papelera") {
            TarjetaAlbum("Papelera", -1, null, Icons.Rounded.Delete) { a.ir(Pant.Papelera) }
        }
    }
    val m = menu
    if (m != null) {
        val fij = m.id.toString() in fijados
        val oc = m.id.toString() in ocultosA
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(m.nombre) },
            text = {
                Column {
                    TextButton(onClick = { a.alternarFijado(m.id); menu = null }) {
                        Text(if (fij) "Quitar de fijados" else "Fijar arriba", color = Color.White, fontSize = 16.sp)
                    }
                    TextButton(onClick = { a.alternarOculto(m.id); menu = null }) {
                        Text(if (oc) "Mostrar en Fotos" else "Ocultar de Fotos", color = Color.White, fontSize = 16.sp)
                    }
                    Text(
                        "«Ocultar de Fotos» solo lo quita de la línea de tiempo; el álbum sigue aquí.",
                        fontSize = 13.sp, color = Suave, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("Cerrar", color = Acento) } }
        )
    }
}

@Composable
private fun TarjetaAlbum(
    nombre: String,
    n: Int,
    portada: Uri?,
    icono: ImageVector?,
    onLargo: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLargo)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Tarjeta)) {
            if (portada != null) Miniatura(portada, Modifier.fillMaxSize(), 400)
            if (icono != null) {
                Box(
                    Modifier.align(if (portada == null) Alignment.Center else Alignment.TopStart).padding(8.dp)
                        .size(34.dp).clip(CircleShape).background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) { Icon(icono, null, tint = Acento, modifier = Modifier.size(20.dp)) }
            }
        }
        Text(nombre, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        if (n >= 0) Text("$n", fontSize = 13.sp, color = Suave, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun AlbumScreen(a: MainActivity, titulo: String, filtro: (Media) -> Boolean) {
    val todos = a.medios.value ?: emptyList()
    val lista = remember(todos, a.favoritos.value) { todos.filter(filtro) }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val elegidos = remember(lista, sel) { lista.filter { it.id in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (sel.isNotEmpty()) {
            CabeceraSeleccion(sel.size, { sel = emptySet() }, { sel = lista.map { it.id }.toSet() })
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${lista.size} elementos", fontSize = 13.sp, color = Suave)
                }
            }
        }
        Box(Modifier.weight(1f)) { MediaGrid(a, lista, sel, { sel = it }) }
        if (sel.isNotEmpty()) BarraSeleccion(a, elegidos) { sel = emptySet() }
    }
}

// ======================================================================
// Visor con zoom
// ======================================================================

@Composable
private fun VisorScreen(a: MainActivity, lista0: List<Media>, inicio: Int) {
    // Si algo se borró, seguimos con la lista actualizada
    val vivos = a.medios.value
    val lista = remember(lista0, vivos) {
        if (vivos == null || lista0.firstOrNull()?.id == -1L) lista0
        else {
            val ids = vivos.map { it.id }.toSet()
            lista0.filter { it.id in ids }
        }
    }
    if (lista.isEmpty()) {
        LaunchedEffect(Unit) { a.atras() }
        return
    }
    val pager = rememberPagerState(initialPage = inicio.coerceIn(0, lista.size - 1)) { lista.size }
    var ui by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<Media?>(null) }
    BackHandler { a.atras() }
    val actual = lista[pager.currentPage.coerceIn(0, lista.size - 1)]
    val externo = actual.id == -1L

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { lista[it].uri.toString() }, modifier = Modifier.fillMaxSize()) { page ->
            val m = lista[page]
            if (m.video) {
                PaginaVideo(m.uri, { a.abrirVideo(m) }) { ui = !ui }
            } else {
                FotoZoom(m.uri.toString(), { a.decodificar(m.uri, 2560) }) { ui = !ui }
            }
        }

        if (ui) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(if (externo) actual.name else fechaCompleta(actual.date), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!externo) Text("${pager.currentPage + 1} de ${lista.size}", fontSize = 12.sp, color = Suave)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Accion(Icons.Rounded.Share, "Compartir") { a.compartir(listOf(actual)) }
                if (!actual.video) Accion(Icons.Rounded.Edit, "Editar") { a.ir(Pant.Editor(actual)) }
                if (!externo) {
                    val fav = actual.uri.toString() in a.favoritos.value
                    Accion(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito") {
                        a.alternarFavorito(listOf(actual))
                    }
                    Accion(Icons.Rounded.Info, "Info") { info = actual }
                    if (a.bovedaAbierta.value) Accion(Icons.Rounded.Lock, "Privado") { a.ocultar(listOf(actual)) }
                    Accion(Icons.Rounded.Delete, "Papelera", Color(0xFFFF6B6B)) { a.aPapelera(listOf(actual)) }
                }
            }
        }
    }
    val i = info
    if (i != null) InfoDialog(a, i) { info = null }
}

@Composable
private fun PaginaVideo(uri: Uri, onPlay: () -> Unit, onToque: () -> Unit) {
    val context = LocalContext.current
    val portada by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, android.util.Size(1280, 1280), null)
            } catch (e: Exception) {
                null
            }
        }
    }
    PortadaVideo(portada, onPlay, onToque)
}

@Composable
private fun PortadaVideo(portada: Bitmap?, onPlay: () -> Unit, onToque: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onToque() }) }, contentAlignment = Alignment.Center) {
        if (portada != null) {
            Image(portada.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PlayCircle, "Reproducir", tint = Color.White, modifier = Modifier.size(80.dp)) }
    }
}

/** Foto con zoom: pellizca o toca dos veces. Sin zoom, deslizar cambia de foto. */
@Composable
private fun FotoZoom(clave: String, cargar: () -> Bitmap?, onToque: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, clave) {
        value = withContext(Dispatchers.IO) { cargar() }
    }
    var escala by remember(clave) { mutableFloatStateOf(1f) }
    var desp by remember(clave) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(clave) {
                detectTapGestures(
                    onTap = { onToque() },
                    onDoubleTap = { p ->
                        if (escala > 1.05f) {
                            escala = 1f
                            desp = Offset.Zero
                        } else {
                            escala = 2.5f
                            val c = Offset(size.width / 2f, size.height / 2f)
                            desp = (c - p) * 1.5f
                        }
                    }
                )
            }
            .pointerInput(clave) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val dedos = ev.changes.count { it.pressed }
                        if (dedos >= 2 || escala > 1.05f) {
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            escala = (escala * z).coerceIn(1f, 6f)
                            val mx = (escala - 1f) * size.width / 2f
                            val my = (escala - 1f) * size.height / 2f
                            desp = Offset(
                                (desp.x + pan.x).coerceIn(-mx, mx),
                                (desp.y + pan.y).coerceIn(-my, my)
                            )
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
            CircularProgressIndicator(color = Acento)
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

@Composable
private fun InfoDialog(a: MainActivity, m: Media, onCerrar: () -> Unit) {
    val exif by produceState<List<Pair<String, String>>>(emptyList(), m.uri) {
        value = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<String, String>>()
            if (!m.video) try {
                a.contentResolver.openInputStream(m.uri)?.use { s ->
                    val e = ExifInterface(s)
                    val cam = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                        .joinToString(" ").trim()
                    if (cam.isNotBlank()) out += "Cámara" to cam
                    val f = e.getAttribute(ExifInterface.TAG_F_NUMBER)
                    val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    val iso = e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    val partes = mutableListOf<String>()
                    if (f != null) partes += "f/$f"
                    if (t > 0) partes += if (t < 1) "1/${(1 / t).toInt()} s" else "${t} s"
                    if (iso != null) partes += "ISO $iso"
                    if (partes.isNotEmpty()) out += "Exposición" to partes.joinToString("  ·  ")
                    val ll = e.latLong
                    if (ll != null) out += "Ubicación" to String.format(Locale.US, "%.5f, %.5f", ll[0], ll[1])
                }
            } catch (ex: Exception) {
                // sin datos EXIF
            }
            out
        }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = Acento) } },
        title = { Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Dato("Fecha", fechaCompleta(m.date))
                Dato("Ubicación del archivo", m.path + m.name)
                if (m.width > 0) Dato("Resolución", "${m.width} × ${m.height}  (${String.format(Locale.US, "%.1f", m.width * m.height / 1_000_000f)} MP)")
                Dato("Tamaño", formatSize(m.size))
                if (m.video) Dato("Duración", formatTime(m.duration))
                exif.forEach { (k, v) -> Dato(k, v) }
            }
        }
    )
}

@Composable
private fun Dato(k: String, v: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(k, fontSize = 13.sp, color = Acento)
        Text(v, fontSize = 15.sp)
    }
}

// ======================================================================
// Papelera
// ======================================================================

@Composable
private fun PapeleraScreen(a: MainActivity) {
    LaunchedEffect(Unit) { a.cargarPapelera() }
    val lista = a.papelera.value
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val elegidos = remember(lista, sel) { (lista ?: emptyList()).filter { it.id in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Text("Papelera", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (!lista.isNullOrEmpty()) {
                TextButton(onClick = { a.eliminarDefinitivo(lista) }) { Text("Vaciar", color = Color(0xFFFF6B6B), fontSize = 16.sp) }
            }
        }
        Text(
            "Toca para elegir. Lo que quede aquí se elimina solo a los 30 días.",
            color = Suave, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Box(Modifier.weight(1f)) {
            when {
                lista == null -> CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
                lista.isEmpty() -> Text("La papelera está vacía.", color = Suave, fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                    items(lista.size, key = { lista[it].id }) { i ->
                        val m = lista[i]
                        val s = m.id in sel
                        Box(Modifier.clickable { sel = if (s) sel - m.id else sel + m.id }) {
                            CeldaMedia(m, s, true, false)
                            val dias = ((m.expires * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                            Text(
                                "${dias}d", color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                    .clip(RoundedCornerShape(5.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (elegidos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(8.dp)) {
                Accion(Icons.Rounded.SelectAll, "Todo") { sel = (lista ?: emptyList()).map { it.id }.toSet() }
                Accion(Icons.Rounded.RestoreFromTrash, "Restaurar", Acento) { a.restaurar(elegidos); sel = emptySet() }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDefinitivo(elegidos); sel = emptySet() }
            }
        }
    }
}

// ======================================================================
// Ajustes
// ======================================================================

@Composable
private fun AjustesScreen(a: MainActivity) {
    BackHandler { a.atras() }
    var dialogoClave by remember { mutableStateOf(false) }
    var dialogoSenuelo by remember { mutableStateOf(false) }
    var dialogoFirma by remember { mutableStateOf(false) }
    var refrescar by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1500); refrescar++ } // por si regresas de Ajustes de Android
    }
    val gestiona = remember(refrescar) { a.gestionaMedios() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            TarjetaAjuste(
                "Borrar y mover sin confirmar",
                if (gestiona) "Activado: Baúl puede borrar y mover sin preguntarte cada vez."
                else "Android pide confirmar cada vez. Actívalo en «Administración de medios» para evitarlo.",
                if (gestiona) null else "Activar"
            ) { a.abrirPermisoGestion() }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Clave privada",
                if (a.tieneClave()) "Configurada." else "Sin configurar.",
                if (a.tieneClave()) "Cambiar" else "Crear"
            ) { dialogoClave = true }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Clave secundaria",
                if (a.tieneClave(1)) "Configurada." else "Opcional.",
                if (a.tieneClave(1)) "Cambiar" else "Crear"
            ) { dialogoSenuelo = true }
            if (a.tieneClave(1)) {
                TextButton(onClick = { a.quitarClaveSenuelo(); refrescar++ }) {
                    Text("Quitar clave secundaria", color = Color(0xFFFF6B6B))
                }
            }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Patrón",
                if (a.tienePatron()) "Configurado." else "Opcional.",
                if (a.tienePatron()) "Cambiar" else "Crear"
            ) { a.ir(Pant.CrearPatron) }
            if (a.tienePatron()) {
                TextButton(onClick = { a.quitarPatron(); refrescar++ }) {
                    Text("Quitar patrón", color = Color(0xFFFF6B6B))
                }
            }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Quitar ubicación al compartir",
                if (a.quitarMeta.value) "Activado: las fotos se envían sin GPS ni modelo del teléfono."
                else "Desactivado: las fotos se envían con todos sus datos.",
                if (a.quitarMeta.value) "Desactivar" else "Activar"
            ) { a.alternarQuitarMeta() }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Firma para el sello",
                if (a.firma.value.isBlank()) "Sin firma." else a.firma.value,
                "Cambiar"
            ) { dialogoFirma = true }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste("Liberar espacio", "Duplicadas, fotos parecidas, videos grandes y capturas viejas.", "Abrir") {
                a.ir(Pant.Limpieza)
            }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste("Tamaño de miniaturas", "Pellizca la cuadrícula de fotos para hacerlas más grandes o pequeñas.", null) {}
            Spacer(Modifier.height(24.dp))
            Text("Baúl 2.8 · Tecnología Universal Avanzada", color = Suave, fontSize = 13.sp)
        }
    }
    if (dialogoClave) ClaveDialog(a, 0) { dialogoClave = false; refrescar++ }
    if (dialogoSenuelo) ClaveDialog(a, 1) { dialogoSenuelo = false; refrescar++ }
    if (dialogoFirma) {
        var f by remember { mutableStateOf(a.firma.value) }
        AlertDialog(
            onDismissRequest = { dialogoFirma = false },
            title = { Text("Firma para el sello") },
            text = {
                OutlinedTextField(
                    value = f, onValueChange = { f = it.take(30) }, singleLine = true,
                    placeholder = { Text("Ej. MalditoX") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { a.guardarFirma(f); dialogoFirma = false }) { Text("Guardar", color = Acento) } },
            dismissButton = { TextButton(onClick = { dialogoFirma = false }) { Text("Cancelar", color = Suave) } }
        )
    }
}

@Composable
private fun TarjetaAjuste(titulo: String, texto: String, boton: String?, onBoton: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tarjeta).padding(16.dp)) {
        Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, fontSize = 14.sp, color = Suave, modifier = Modifier.padding(top = 4.dp))
        if (boton != null) {
            Button(
                onClick = onBoton,
                colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black),
                modifier = Modifier.padding(top = 10.dp)
            ) { Text(boton) }
        }
    }
}

@Composable
private fun CampoClave(valor: String, etiqueta: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onCambio(it.take(32)) },
        singleLine = true,
        label = { Text(etiqueta) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun ClaveDialog(a: MainActivity, esp: Int, onCerrar: () -> Unit) {
    val tiene = a.tieneClave(esp)
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text((if (tiene) "Cambiar clave" else "Crear clave") + if (esp == 1) " secundaria" else "") },
        text = {
            Column {
                if (tiene) CampoClave(actual, "Clave actual") { actual = it }
                CampoClave(nueva, "Clave nueva (mínimo 4)") { nueva = it }
                CampoClave(repetir, "Repetir clave") { repetir = it }
                Text(
                    "Usa algo que no escribirías al buscar fotos. Si la olvidas no hay forma de recuperarla.",
                    fontSize = 13.sp, color = Suave, modifier = Modifier.padding(top = 6.dp)
                )
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    tiene && !a.claveCorrecta(actual, esp) -> "La clave actual no es correcta"
                    a.claveCorrecta(nueva, 1 - esp) -> "Debe ser distinta de la otra clave"
                    nueva.length < 4 -> "Debe tener al menos 4 caracteres"
                    nueva != repetir -> "Las claves no coinciden"
                    else -> ""
                }
                if (error.isBlank()) {
                    a.ponerClave(nueva, esp)
                    onCerrar()
                }
            }) { Text("Guardar", color = Acento) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Suave) } }
    )
}

// ======================================================================
// Área privada (bóveda)
// ======================================================================

private val miniBoveda = LruCache<String, Bitmap>(200)

@Composable
private fun MiniaturaBoveda(a: MainActivity, item: VItem, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(miniBoveda.get("${a.espacio}/" + item.file), item.file) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { a.miniaturaBoveda(item)?.also { miniBoveda.put("${a.espacio}/" + item.file, it) } }
        }
    }
    Box(modifier.background(Tarjeta)) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private enum class DialogoBoveda { NADA, RESPALDAR, IMPORTAR }

/** Tarjeta de álbum privado con portada (la foto más reciente). n < 0 = no mostrar cantidad. */
@Composable
private fun AlbumPrivado(
    a: MainActivity, nombre: String, portada: VItem?, n: Int, activo: Boolean, icono: ImageVector,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).background(Tarjeta)
                .then(if (activo) Modifier.border(2.dp, Acento, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (portada != null) MiniaturaBoveda(a, portada, Modifier.fillMaxSize())
            else Icon(icono, null, tint = if (activo) Acento else Suave, modifier = Modifier.size(30.dp))
        }
        Text(
            nombre, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (activo) Acento else Color.White,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (n >= 0) Text("$n", fontSize = 11.sp, color = Suave)
    }
}

@Composable
private fun BovedaScreen(a: MainActivity) {
    val todosItems = a.bovedaItems.value
    var sel by remember { mutableStateOf(setOf<String>()) }
    var dialogo by remember { mutableStateOf(DialogoBoveda.NADA) }
    var albumSel by a.albumPrivado // "" = sin álbum, null = ver todo
    var dialogoAlbum by remember { mutableStateOf(false) }
    var nuevoAlbum by remember { mutableStateOf(false) }
    var opcionesAlbum by remember { mutableStateOf<String?>(null) }
    val versionAl = a.versionAlbumes.intValue
    val albumes = remember(todosItems, versionAl) { a.albumesBoveda(todosItems) }
    val items = remember(todosItems, albumSel) {
        val f = albumSel
        if (f == null) todosItems else todosItems.filter { it.album == f }
    }
    val elegidos = remember(items, sel) { items.filter { it.file in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Privado", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${items.size} elementos", fontSize = 13.sp, color = Suave)
            }
            IconoBoton(Icons.Rounded.FolderOpen, "Carpeta de respaldo automático") { a.elegirCarpetaRespaldo() }
            IconoBoton(Icons.Rounded.Backup, "Respaldar") { dialogo = DialogoBoveda.RESPALDAR }
            IconoBoton(Icons.Rounded.UploadFile, "Importar respaldo") { dialogo = DialogoBoveda.IMPORTAR }
            IconoBoton(Icons.Rounded.Lock, "Cerrar") { a.cerrarBoveda() }
        }
        val dias = a.diasSinRespaldo.value
        if (dias != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33E0B464)).clickable { dialogo = DialogoBoveda.RESPALDAR }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Backup, null, tint = Acento, modifier = Modifier.size(22.dp))
                Text(
                    (if (dias == Int.MAX_VALUE) "Nunca has respaldado esta área." else "Último respaldo hace $dias días.") +
                        (if (a.tieneCarpetaRespaldo()) " Toca para respaldar." else " Toca para respaldar o elige una carpeta automática."),
                    fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        // Álbumes con portada (mantén presionado uno para renombrarlo o quitarlo)
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                val sueltos = todosItems.filter { it.album.isBlank() }
                AlbumPrivado(a, "Sin álbum", sueltos.firstOrNull(), sueltos.size, albumSel == "", Icons.Rounded.Inbox,
                    onClick = { albumSel = ""; sel = emptySet() }, onLongClick = {})
            }
            items(albumes) { al ->
                val delAlbum = todosItems.filter { it.album == al }
                AlbumPrivado(a, al, delAlbum.firstOrNull(), delAlbum.size, albumSel == al, Icons.Rounded.Folder,
                    onClick = { albumSel = al; sel = emptySet() }, onLongClick = { opcionesAlbum = al })
            }
            item {
                AlbumPrivado(a, "Nuevo", null, -1, false, Icons.Rounded.CreateNewFolder,
                    onClick = { nuevoAlbum = true }, onLongClick = {})
            }
            item {
                AlbumPrivado(a, "Ver todo", null, todosItems.size, albumSel == null, Icons.Rounded.PhotoLibrary,
                    onClick = { albumSel = null; sel = emptySet() }, onLongClick = {})
            }
        }
        Box(Modifier.weight(1f)) {
            if (items.isEmpty() && albumSel == "" && todosItems.isNotEmpty()) {
                Text(
                    "Todo está guardado en álbumes.\n\nLo nuevo que pases a privado aparecerá aquí.",
                    color = Suave, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else if (items.isEmpty() && !albumSel.isNullOrEmpty()) {
                Text(
                    "Álbum vacío.\n\nPara llenarlo: ve a «Sin álbum» o «Ver todo», selecciona fotos o videos y toca «Álbum».",
                    color = Suave, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else if (items.isEmpty()) {
                Text(
                    "Vacío.\n\nPara guardar aquí: regresa con la flecha, selecciona fotos o videos en la galería " +
                        "y toca «Privado». Luego vuelve con el candado de arriba.",
                    color = Suave, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                    items(items.size, key = { items[it].file }) { i ->
                        val v = items[i]
                        val s = v.file in sel
                        Box(
                            Modifier.aspectRatio(1f).padding(1.dp).combinedClickable(
                                onClick = {
                                    if (sel.isNotEmpty()) sel = if (s) sel - v.file else sel + v.file
                                    else a.ir(Pant.VisorBoveda(i))
                                },
                                onLongClick = { sel = if (s) sel - v.file else sel + v.file }
                            )
                        ) {
                            MiniaturaBoveda(a, v, Modifier.fillMaxSize())
                            if (v.video) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp))
                            }
                            if (s) Box(Modifier.fillMaxSize().background(Acento.copy(alpha = 0.28f)).border(3.dp, Acento))
                            if (sel.isNotEmpty()) {
                                Icon(
                                    if (s) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (s) Acento else Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (a.guardando.intValue > 0) CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
        }
        if (elegidos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(8.dp)) {
                Accion(Icons.Rounded.SelectAll, "Todo") { sel = items.map { it.file }.toSet() }
                Accion(Icons.Rounded.Share, "Compartir") { a.compartirBoveda(elegidos) }
                Accion(Icons.Rounded.CreateNewFolder, "Álbum") { dialogoAlbum = true }
                Accion(Icons.Rounded.Unarchive, "Sacar", Acento) { a.sacarDeBoveda(elegidos); sel = emptySet() }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDeBoveda(elegidos); sel = emptySet() }
            }
        }
    }

    if (dialogoAlbum) {
        var nuevo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogoAlbum = false },
            title = { Text("Poner en álbum") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nuevo, onValueChange = { nuevo = it.take(30) }, singleLine = true,
                        placeholder = { Text("Nuevo álbum") }, modifier = Modifier.fillMaxWidth()
                    )
                    if (nuevo.isNotBlank()) {
                        TextButton(onClick = { a.albumBoveda(elegidos, nuevo); sel = emptySet(); dialogoAlbum = false }) {
                            Text("Crear «${nuevo.trim()}»", color = Acento)
                        }
                    }
                    albumes.forEach { al ->
                        TextButton(onClick = { a.albumBoveda(elegidos, al); sel = emptySet(); dialogoAlbum = false }) {
                            Text(al, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    TextButton(onClick = { a.albumBoveda(elegidos, ""); sel = emptySet(); dialogoAlbum = false }) {
                        Text("Sin álbum", color = Suave)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialogoAlbum = false }) { Text("Cancelar", color = Suave) } }
        )
    }

    if (nuevoAlbum) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nuevoAlbum = false },
            title = { Text("Nuevo álbum privado") },
            text = {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it.take(30) }, singleLine = true,
                    placeholder = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nombre.isNotBlank()) {
                        a.crearAlbumBoveda(nombre.trim())
                        albumSel = nombre.trim()
                        sel = emptySet()
                    }
                    nuevoAlbum = false
                }) { Text("Crear", color = Acento) }
            },
            dismissButton = { TextButton(onClick = { nuevoAlbum = false }) { Text("Cancelar", color = Suave) } }
        )
    }

    val opAl = opcionesAlbum
    if (opAl != null) {
        var nombre by remember(opAl) { mutableStateOf(opAl) }
        var confirmarQuitar by remember(opAl) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { opcionesAlbum = null },
            title = { Text(if (confirmarQuitar) "¿Quitar «$opAl»?" else "Álbum «$opAl»") },
            text = {
                if (confirmarQuitar) {
                    Text("Las fotos y videos NO se borran: quedan en «Todo», sin álbum.", fontSize = 14.sp, color = Suave)
                } else {
                    Column {
                        OutlinedTextField(
                            value = nombre, onValueChange = { nombre = it.take(30) }, singleLine = true,
                            label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = { confirmarQuitar = true }) {
                            Text("Quitar álbum", color = Color(0xFFFF6B6B))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmarQuitar) {
                        a.borrarAlbumBoveda(opAl)
                        if (albumSel == opAl) albumSel = ""
                    } else if (nombre.isNotBlank() && nombre.trim() != opAl) {
                        a.renombrarAlbumBoveda(opAl, nombre.trim())
                        if (albumSel == opAl) albumSel = nombre.trim()
                    }
                    opcionesAlbum = null
                }) { Text(if (confirmarQuitar) "Quitar" else "Guardar", color = if (confirmarQuitar) Color(0xFFFF6B6B) else Acento) }
            },
            dismissButton = { TextButton(onClick = { opcionesAlbum = null }) { Text("Cancelar", color = Suave) } }
        )
    }

    if (dialogo != DialogoBoveda.NADA) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val respaldar = dialogo == DialogoBoveda.RESPALDAR
        AlertDialog(
            onDismissRequest = { dialogo = DialogoBoveda.NADA },
            title = { Text(if (respaldar) "Respaldar" else "Importar respaldo") },
            text = {
                Column {
                    Text(
                        if (respaldar) "Se crea un archivo protegido con tu clave. Guárdalo en tu computadora: " +
                            "si desinstalas Baúl o borras sus datos, este espacio se pierde."
                        else "Elige un archivo .baul y escribe la clave con la que se hizo ese respaldo.",
                        fontSize = 14.sp, color = Suave
                    )
                    CampoClave(pin, "Clave") { pin = it }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (respaldar) {
                        if (!a.claveCorrecta(pin, a.espacio)) {
                            error = "Clave incorrecta"
                        } else {
                            a.pedirRespaldo(pin)
                            dialogo = DialogoBoveda.NADA
                        }
                    } else {
                        if (pin.length < 4) error = "Escribe la clave del respaldo"
                        else {
                            a.pedirImportar(pin)
                            dialogo = DialogoBoveda.NADA
                        }
                    }
                }) { Text("Continuar", color = Acento) }
            },
            dismissButton = { TextButton(onClick = { dialogo = DialogoBoveda.NADA }) { Text("Cancelar", color = Suave) } }
        )
    }
}

@Composable
private fun VisorBovedaScreen(a: MainActivity, inicio: Int) {
    val todos = a.bovedaItems.value
    val filtro = a.albumPrivado.value
    val items = remember(todos, filtro) { a.itemsVistaPrivada() }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { a.atras() }
        return
    }
    val pager = rememberPagerState(initialPage = inicio.coerceIn(0, items.size - 1)) { items.size }
    var ui by remember { mutableStateOf(true) }
    BackHandler { a.atras() }
    val actual = items[pager.currentPage.coerceIn(0, items.size - 1)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { items[it].file }, modifier = Modifier.fillMaxSize()) { page ->
            val v = items[page]
            if (v.video) {
                val portada by produceState<Bitmap?>(null, v.file) {
                    value = withContext(Dispatchers.IO) { a.miniaturaBoveda(v) }
                }
                PortadaVideo(portada, { a.abrirVideoBoveda(v) }) { ui = !ui }
            } else {
                FotoZoom(v.file, { a.decodificarArchivo(a.archivoBoveda(v), 2560) }) { ui = !ui }
            }
        }
        if (ui) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Text(fechaCompleta(actual.date), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
            ) {
                Accion(Icons.Rounded.Share, "Compartir") { a.compartirBoveda(listOf(actual)) }
                Accion(Icons.Rounded.Unarchive, "Sacar", Acento) { a.sacarDeBoveda(listOf(actual)) }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDeBoveda(listOf(actual)) }
            }
        }
    }
}

@Suppress("unused")
private fun noUsado(w: Int) = Modifier.width(w.dp)
