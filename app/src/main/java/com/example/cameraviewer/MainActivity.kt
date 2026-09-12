package com.example.cameraviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

data class Camera(
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val transport: String = "TCP"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContent { CameraViewerApp(this) }
    }
}

@Composable
fun CameraViewerApp(context: Context) {
    var cameras by remember { mutableStateOf(loadCameras(context)) }
    var screen by remember { mutableStateOf("home") }
    var selectedCamera by remember { mutableStateOf<Camera?>(null) }

    MaterialTheme {
        when (screen) {
            "settings" -> SettingsScreen(
                cameras = cameras,
                onBack = { screen = "home" },
                onAddCamera = { name, url, username, password, transport ->
                    cameras = cameras + Camera(name, url, username, password, transport)
                    saveCameras(context, cameras)
                },
                onDeleteCamera = { camera ->
                    cameras = cameras.filterNot { it == camera }
                    saveCameras(context, cameras)
                },
                onOpenCamera = { camera ->
                    selectedCamera = camera
                    screen = "player"
                }
            )
            "player" -> selectedCamera?.let { camera ->
                CameraPlayerScreen(camera = camera, onBack = {
                    selectedCamera = null
                    screen = "home"
                })
            }
            "multiview" -> MultiViewScreen(
                cameras = cameras,
                onBack = { screen = "home" },
                onOpenCamera = { camera ->
                    selectedCamera = camera
                    screen = "player"
                }
            )
            else -> HomeScreen(
                cameras = cameras,
                onSettings = { screen = "settings" },
                onMultiView = { screen = "multiview" },
                onOpenCamera = { camera ->
                    selectedCamera = camera
                    screen = "player"
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    cameras: List<Camera>,
    onSettings: () -> Unit,
    onMultiView: () -> Unit,
    onOpenCamera: (Camera) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("CamViewer", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Câmeras", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(18.dp))

        if (cameras.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Nenhuma câmera configurada")
                Spacer(Modifier.height(16.dp))
                TvFocusButton("Configurar câmeras", onSettings)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(cameras) { camera ->
                    CameraCard(camera, onOpenCamera)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TvFocusButton("▶ MultiView", onMultiView, Modifier.weight(1f))
                TvFocusButton("⚙ Configurações", onSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CameraCard(camera: Camera, onOpenCamera: (Camera) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .focusable()
            .clickable { onOpenCamera(camera) },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
            Text(camera.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("RTSP • ${camera.transport}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("OK para abrir", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TvFocusButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .focusable()
            .height(54.dp)
    ) { Text(text) }
}

@Composable
fun MultiViewScreen(
    cameras: List<Camera>,
    onBack: () -> Unit,
    onOpenCamera: (Camera) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.focusable()) { Text("← Voltar") }
            Spacer(Modifier.width(12.dp))
            Text("MultiView", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        if (cameras.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma câmera configurada")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cameras) { camera ->
                    MultiViewTile(camera, onOpenCamera)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MultiViewTile(camera: Camera, onOpenCamera: (Camera) -> Unit) {
    val context = LocalContext.current
    val player = remember(camera) {
        val mediaItem = MediaItem.fromUri(buildRtspUri(camera))
        val factory = RtspMediaSource.Factory()
            .setForceUseRtpTcp(camera.transport == "TCP")
            .setTimeoutMs(10000)
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(factory.createMediaSource(mediaItem))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Card(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .focusable()
            .clickable { onOpenCamera(camera) },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                } },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                camera.name,
                modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(8.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun SettingsScreen(
    cameras: List<Camera>,
    onBack: () -> Unit,
    onAddCamera: (String, String, String, String, String) -> Unit,
    onDeleteCamera: (Camera) -> Unit,
    onOpenCamera: (Camera) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("TCP") }
    var editingField by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    fun isConfirm(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)

    fun fieldModifier(field: String): Modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { state ->
            if (!state.isFocused && editingField == field) {
                editingField = null
                keyboardController?.hide()
            }
        }
        .onPreviewKeyEvent { event ->
            if (isConfirm(event)) {
                editingField = field
                keyboardController?.show()
                true
            } else false
        }

    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.focusable()) { Text("← Voltar") }
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(20.dp))
        Text("Adicionar câmera", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = fieldModifier("name"), label = { Text("Nome da câmera") }, singleLine = true, readOnly = editingField != "name")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = fieldModifier("url"), label = { Text("URL RTSP") }, placeholder = { Text("rtsp://192.168.1.100:554/...") }, singleLine = true, readOnly = editingField != "url")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, modifier = fieldModifier("username"), label = { Text("Usuário") }, singleLine = true, readOnly = editingField != "username")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = fieldModifier("password"), label = { Text("Senha") }, singleLine = true, readOnly = editingField != "password")
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Protocolo RTSP", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().focusable().clickable { transport = "TCP" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = transport == "TCP", onClick = null)
                    Text("TCP")
                }
                Row(Modifier.fillMaxWidth().focusable().clickable { transport = "UDP" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = transport == "UDP", onClick = null)
                    Text("UDP")
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                if (name.isNotBlank() && url.isNotBlank()) {
                    onAddCamera(name.trim(), url.trim(), username.trim(), password, transport)
                    name = ""; url = ""; username = ""; password = ""; transport = "TCP"; editingField = null
                    keyboardController?.hide()
                }
            },
            modifier = Modifier.fillMaxWidth().focusable(),
            enabled = name.isNotBlank() && url.isNotBlank()
        ) { Text("Adicionar câmera") }

        Spacer(Modifier.height(25.dp))
        Text("Câmeras configuradas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        cameras.forEach { camera ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).focusable().clickable { onOpenCamera(camera) }) {
                        Text(camera.name, style = MaterialTheme.typography.titleSmall)
                        Text(camera.url, style = MaterialTheme.typography.bodySmall)
                        Text("Protocolo RTSP: ${camera.transport}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onDeleteCamera(camera) }, modifier = Modifier.focusable()) { Text("Excluir") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CameraPlayerScreen(camera: Camera, onBack: () -> Unit) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val player = remember(camera) {
        val mediaItem = MediaItem.fromUri(buildRtspUri(camera))
        val mediaSourceFactory = RtspMediaSource.Factory()
            .setForceUseRtpTcp(camera.transport == "TCP")
            .setTimeoutMs(10000)
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMessage = "Erro ao reproduzir a câmera: ${error.message ?: "erro desconhecido"}"
                }
            })
            setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.focusable()) { Text("← Voltar") }
            Text(camera.name, style = MaterialTheme.typography.titleLarge)
        }
        AndroidView(
            factory = { PlayerView(it).apply {
                this.player = player
                useController = true
                keepScreenOn = true
            } },
            Modifier.fillMaxWidth().weight(1f)
        )
        errorMessage?.let { message ->
            Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun buildRtspUri(camera: Camera): Uri {
    val url = camera.url.trim()
    if (camera.username.isBlank() && camera.password.isBlank()) return Uri.parse(url)
    val prefix = "rtsp://"
    if (!url.startsWith(prefix)) return Uri.parse(url)
    val rest = url.removePrefix(prefix)
    return Uri.parse("$prefix${Uri.encode(camera.username)}:${Uri.encode(camera.password)}@$rest")
}

fun saveCameras(context: Context, cameras: List<Camera>) {
    val array = JSONArray()
    cameras.forEach { camera ->
        val obj = JSONObject()
        obj.put("name", camera.name)
        obj.put("url", camera.url)
        obj.put("username", camera.username)
        obj.put("password", camera.password)
        obj.put("transport", camera.transport)
        array.put(obj)
    }
    context.getSharedPreferences("cameras", Context.MODE_PRIVATE).edit().putString("list", array.toString()).apply()
}

fun loadCameras(context: Context): List<Camera> {
    val json = context.getSharedPreferences("cameras", Context.MODE_PRIVATE).getString("list", null) ?: return emptyList()
    return try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(Camera(
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    username = obj.optString("username"),
                    password = obj.optString("password"),
                    transport = obj.optString("transport", "TCP").uppercase()
                ))
            }
        }
    } catch (_: Exception) { emptyList() }
}
