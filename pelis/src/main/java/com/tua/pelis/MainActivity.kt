package com.tua.pelis

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelisTheme {
                Surface(color = Fondo, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
                    LibraryApp()
                }
            }
        }
    }
}

private val RojoBorrar = Color(0xFFFF6B6B)
private val trashSupported = Build.VERSION.SDK_INT >= 30

private fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, videoPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun LibraryApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it || hasPermission(context)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(videoPermission()) }

    if (granted) LibraryScreen() else PermissionScreen { launcher.launch(videoPermission()) }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = Acento, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Pelis necesita permiso para ver los videos del teléfono.",
            fontSize = 20.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)) {
            Text("Dar permiso", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Si ya lo negaste, actívalo en Ajustes › Apps › Pelis › Permisos.",
            fontSize = 14.sp, color = TextoSuave, textAlign = TextAlign.Center
        )
    }
}

private const val TODOS = Long.MIN_VALUE
private const val PAPELERA = Long.MIN_VALUE + 1

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Video>?>(null) }
    var trash by remember { mutableStateOf<List<Video>?>(null) }
    var progress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var continueIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Recargar al volver del reproductor o del cuadro de confirmación del sistema
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val list = VideoRepo.load(context)
            val prog = list.associate { it.uri.toString() to ProgressStore.fraction(context, it.uri.toString()) }
            val cont = ProgressStore.continueIds(context)
            withContext(Dispatchers.Main) {
                videos = list
                progress = prog
                continueIds = cont
            }
        }
    }
    val inTrash = folderId == PAPELERA
    LaunchedEffect(refreshKey, inTrash) {
        if (inTrash) {
            val t = withContext(Dispatchers.IO) { VideoRepo.load(context, trashed = true) }
            trash = t
        }
    }

    // Cuadro de confirmación del sistema (papelera / restaurar / eliminar)
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { refreshKey++ }

    fun launch(pi: PendingIntent) {
        requestLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    fun moveToTrash(list: List<Video>) {
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            launch(MediaStore.createTrashRequest(context.contentResolver, list.map { it.uri }, true))
        } else {
            Toast.makeText(context, "Tu Android no tiene papelera", Toast.LENGTH_SHORT).show()
        }
    }

    fun restore(list: List<Video>) {
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            launch(MediaStore.createTrashRequest(context.contentResolver, list.map { it.uri }, false))
        }
    }

    fun deleteForever(list: List<Video>) {
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            launch(MediaStore.createDeleteRequest(context.contentResolver, list.map { it.uri }))
        }
    }

    BackHandler(enabled = folderId != null) { folderId = null }

    // Posición de la lista (se conserva al volver del reproductor)
    val estadoInicio = rememberLazyListState()
    val estadoCarpeta = rememberSaveable(folderId, saver = LazyListState.Saver) { LazyListState() }

    val all = videos
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val currentFolder = folderId
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (currentFolder == null) {
            HomeList(all, progress, continueIds, estadoInicio) { folderId = it }
        } else if (currentFolder == PAPELERA) {
            TrashList(
                list = trash,
                onBack = { folderId = null },
                onRestore = { restore(it) },
                onDelete = { deleteForever(it) }
            )
        } else {
            val list = if (currentFolder == TODOS) all else all.filter { it.folderId == currentFolder }
            val title = if (currentFolder == TODOS) "Todos los videos" else list.firstOrNull()?.folderName ?: ""
            FolderList(title, list, progress, estadoCarpeta, onBack = { folderId = null }, onTrash = { moveToTrash(it) })
        }
    }
}

private fun play(context: Context, list: List<Video>, index: Int) {
    val i = Intent(context, PlayerActivity::class.java).apply {
        putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
        putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.name }))
        putExtra(PlayerActivity.EXTRA_INDEX, index)
    }
    context.startActivity(i)
}

private fun share(context: Context, list: List<Video>) {
    if (list.isEmpty()) return
    val intent = if (list.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = list[0].mime.ifBlank { "video/*" }
            putExtra(Intent.EXTRA_STREAM, list[0].uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(list.map { it.uri }))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}

@Composable
private fun HomeList(
    all: List<Video>,
    progress: Map<String, Float>,
    continueIds: List<String>,
    estado: LazyListState,
    onFolder: (Long) -> Unit
) {
    val context = LocalContext.current
    val byUri = remember(all) { all.associateBy { it.uri.toString() } }
    val continuing = remember(all, continueIds) { continueIds.mapNotNull { byUri[it] }.take(15) }
    val folders = remember(all) { VideoRepo.folders(all) }

    LazyColumn(state = estado, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Pelis", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        if (all.isEmpty()) {
            item {
                Text(
                    "No encontré videos en el teléfono.",
                    fontSize = 18.sp, color = TextoSuave, modifier = Modifier.padding(24.dp)
                )
            }
        }
        if (continuing.isNotEmpty()) {
            item { SectionTitle("Continuar viendo") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(continuing, key = { it.id }) { v ->
                        Column(
                            Modifier.width(210.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { play(context, continuing, continuing.indexOf(v)) }
                        ) {
                            ThumbWithProgress(v, progress[v.uri.toString()] ?: 0f, Modifier.width(210.dp).height(118.dp))
                            Text(
                                v.name, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        if (all.isNotEmpty()) {
            item { SectionTitle("Carpetas") }
            item {
                FolderRow("Todos los videos", all.size, all.first(), Icons.Rounded.VideoLibrary) { onFolder(TODOS) }
            }
            items(folders, key = { it.id }) { f ->
                FolderRow(f.name, f.videos.size, f.videos.first(), Icons.Rounded.Folder) { onFolder(f.id) }
            }
        }
        if (trashSupported) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { onFolder(PAPELERA) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(10.dp))
                            .background(Tarjeta),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Delete, null, tint = TextoSuave, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Papelera", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("Restaurar o eliminar para siempre", fontSize = 14.sp, color = TextoSuave)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Acento,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun FolderRow(
    name: String,
    count: Int,
    sample: Video,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            VideoThumb(sample.uri, Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(10.dp)))
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(26.dp)
                    .clip(CircleShape).background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Acento, modifier = Modifier.size(16.dp)) }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 video" else "$count videos", fontSize = 14.sp, color = TextoSuave)
        }
    }
}

// ---------- Encabezados y barra de acciones ----------

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar", modifier = Modifier.size(30.dp)) }
        Text(
            title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        trailing()
    }
}

@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, "Cancelar selección", modifier = Modifier.size(30.dp)) }
        Text(
            if (count == 1) "1 seleccionado" else "$count seleccionados",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Acento,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ActionBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Tarjeta).padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(28.dp))
        Text(label, color = color, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

// ---------- Lista de una carpeta (con selección) ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderList(
    title: String,
    list: List<Video>,
    progress: Map<String, Float>,
    estado: LazyListState,
    onBack: () -> Unit,
    onTrash: (List<Video>) -> Unit
) {
    val context = LocalContext.current
    var selected by remember(list) { mutableStateOf(setOf<Long>()) }
    var details by remember { mutableStateOf<Video?>(null) }
    val selecting = selected.isNotEmpty()
    val chosen = list.filter { it.id in selected }

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize()) {
        if (selecting) SelectionHeader(selected.size) { selected = emptySet() }
        else Header(title, onBack)

        if (list.isEmpty()) {
            Text(
                "Esta carpeta ya no tiene videos.",
                fontSize = 18.sp, color = TextoSuave, modifier = Modifier.padding(24.dp)
            )
        }
        LazyColumn(Modifier.weight(1f), state = estado, contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list.size, key = { list[it].id }) { index ->
                val v = list[index]
                val isSel = v.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSel) Color(0x33FFB74D) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (selecting) selected = selected.toggle(v.id)
                                else play(context, list, index)
                            },
                            onLongClick = { selected = selected.toggle(v.id) }
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        ThumbWithProgress(v, progress[v.uri.toString()] ?: 0f, Modifier.width(144.dp).height(81.dp))
                        if (isSel) SelectedMark(Modifier.align(Alignment.Center))
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(v.name, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val extra = listOf(resolutionLabel(v.width, v.height), formatSize(v.size))
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        Text(extra, fontSize = 13.sp, color = TextoSuave, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        if (selecting) {
            ActionBar {
                ActionButton(Icons.Rounded.SelectAll, "Todos") { selected = list.map { it.id }.toSet() }
                ActionButton(Icons.Rounded.Share, "Compartir") { share(context, chosen) }
                if (chosen.size == 1) {
                    ActionButton(Icons.Rounded.Info, "Detalles") { details = chosen.first() }
                }
                if (trashSupported) {
                    ActionButton(Icons.Rounded.Delete, "Papelera", RojoBorrar) { onTrash(chosen) }
                }
            }
        }
    }

    val d = details
    if (d != null) DetailsDialog(d) { details = null }
}

@Composable
private fun SelectedMark(modifier: Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Rounded.CheckCircle, "Seleccionado", tint = Acento, modifier = Modifier.size(34.dp)) }
}

// ---------- Papelera ----------

@Composable
private fun TrashList(
    list: List<Video>?,
    onBack: () -> Unit,
    onRestore: (List<Video>) -> Unit,
    onDelete: (List<Video>) -> Unit
) {
    var selected by remember(list) { mutableStateOf(setOf<Long>()) }
    val trashItems = list ?: emptyList()
    val chosen = trashItems.filter { it.id in selected }

    Column(Modifier.fillMaxSize()) {
        Header("Papelera", onBack) {
            if (trashItems.isNotEmpty()) {
                TextButton(onClick = { onDelete(trashItems) }) {
                    Text("Vaciar", color = RojoBorrar, fontSize = 17.sp)
                }
            }
        }
        Text(
            "Toca los videos para elegirlos. Los que se queden aquí se eliminan solos después de 30 días.",
            fontSize = 14.sp, color = TextoSuave,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        when {
            list == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            trashItems.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("La papelera está vacía.", fontSize = 18.sp, color = TextoSuave)
            }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(trashItems.size, key = { trashItems[it].id }) { index ->
                    val v = trashItems[index]
                    val isSel = v.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSel) Color(0x33FFB74D) else Color.Transparent)
                            .clickable { selected = selected.toggle(v.id) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(144.dp).height(81.dp).clip(RoundedCornerShape(12.dp))) {
                            VideoThumb(v.uri, Modifier.fillMaxSize())
                            if (isSel) SelectedMark(Modifier.align(Alignment.Center))
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(v.name, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(formatSize(v.size), daysLeft(v.expires)).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                fontSize = 13.sp, color = TextoSuave, modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        if (chosen.isNotEmpty()) {
            ActionBar {
                ActionButton(Icons.Rounded.SelectAll, "Todos") { selected = trashItems.map { it.id }.toSet() }
                ActionButton(Icons.Rounded.RestoreFromTrash, "Restaurar", Acento) { onRestore(chosen) }
                ActionButton(Icons.Rounded.DeleteForever, "Eliminar", RojoBorrar) { onDelete(chosen) }
            }
        }
    }
}

private fun daysLeft(expiresSeconds: Long): String {
    if (expiresSeconds <= 0) return ""
    val days = ((expiresSeconds * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    return when (days) {
        0L -> "se elimina hoy"
        1L -> "se elimina en 1 día"
        else -> "se elimina en $days días"
    }
}

// ---------- Detalles ----------

@Composable
private fun DetailsDialog(v: Video, onClose: () -> Unit) {
    val context = LocalContext.current
    val codecs by produceState("Leyendo…", v.uri) {
        value = withContext(Dispatchers.IO) { readCodecs(context, v.uri) }
    }
    val fecha = remember(v.date) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es")).format(Date(v.date * 1000))
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar", fontSize = 17.sp) } },
        title = { Text(v.name, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                DetailLine("Ubicación", "${v.path}${v.fileName}")
                DetailLine("Duración", formatTime(v.duration))
                DetailLine(
                    "Resolución",
                    if (v.width > 0) "${v.width} × ${v.height}  (${resolutionLabel(v.width, v.height)})" else "—"
                )
                DetailLine("Tamaño", formatSize(v.size))
                DetailLine("Formato", codecs)
                DetailLine("Agregado", fecha)
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, color = Acento)
        Text(value, fontSize = 16.sp)
    }
}

@Composable
private fun ThumbWithProgress(v: Video, fraction: Float, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        VideoThumb(v.uri, Modifier.fillMaxSize())
        if (v.duration > 0) {
            Text(
                formatTime(v.duration), fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x88FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
