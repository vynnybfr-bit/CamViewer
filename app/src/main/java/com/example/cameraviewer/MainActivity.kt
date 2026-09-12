package com.example.cameraviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
                    screen = "settings"
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
                onMultiView = { screen = "multiview" }
            )
        }
    }
}

@Composable
fun HomeScreen(
    cameras: List<Camera>,
    onSettings: () -> Unit,
    onMultiView: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CamViewer", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(28.dp))

        TvButton(
            text = "MultiView",
            onClick = onMultiView,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)
        )
        Spacer(Modifier.height(14.dp))
        TvButton(
            text = "Configurações",
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Text(text)
    }
}

@Composable
fun MultiViewScreen(
    cameras: List<Camera>,
    onBack: () -> Unit,
    onOpenCamera: (Camera) -> Unit
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus)
            ) { Text("Voltar") }
            Spacer(Modifier.width(18.dp))
            Text("MultiView", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        if (cameras.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma câmera configurada")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cameras) { camera ->
                    SimpleCameraTile(camera, onOpenCamera)
                }
            }
        }
    }
}

@Composable
fun SimpleCameraTile(camera: Camera, onOpenCamera: (Camera) -> Unit) {
    TvButton(
        text = camera.name,
        onClick = { onOpenCamera(camera) },
        modifier = Modifier.fillMaxWidth()
    )
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
    val firstFocus = remember { FocusRequester() }

    fun confirm(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(firstFocus)
            ) { Text("Voltar") }
            Spacer(Modifier.width(18.dp))
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Adicionar câmera", style = MaterialTheme.typography.titleMedium)
            }
            item {
                TvField("Nome", name, { name = it }, editingField == "name", { editingField = "name" }, confirm, keyboardController)
            }
            item {
                TvField("URL RTSP", url, { url = it }, editingField == "url", { editingField = "url" }, confirm, keyboardController)
            }
            item {
                TvField("Usuário", username, { username = it }, editingField == "username", { editingField = "username" }, confirm, keyboardController)
            }
            item {
                TvField("Senha", password, { password = it }, editingField == "password", { editingField = "password" }, confirm, keyboardController)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("TCP", { transport = "TCP" }, Modifier.weight(1f))
                    TvButton("UDP", { transport = "UDP" }, Modifier.weight(1f))
                }
                Text("Protocolo: $transport", modifier = Modifier.padding(top = 4.dp))
            }
            item {
                TvButton(
                    text = "Adicionar câmera",
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) {
                            onAddCamera(name.trim(), url.trim(), username.trim(), password, transport)
                            name = ""; url = ""; username = ""; password = ""; transport = "TCP"
                            editingField = null
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Câmeras configuradas", style = MaterialTheme.typography.titleMedium)
            }
            items(cameras) { camera ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(camera.name, style = MaterialTheme.typography.titleSmall)
                    Text(camera.url, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onOpenCamera(camera) }) { Text("Ver") }
                        TextButton(onClick = { onDeleteCamera(camera) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
fun TvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    editing: Boolean,
    startEditing: () -> Unit,
    confirm: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = !editing,
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (confirm(event)) {
                    startEditing()
                    keyboardController?.show()
                    true
                } else false
            }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun CameraPlayerScreen(camera: Camera, onBack: () -> Unit) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val backFocus = remember { FocusRequester() }
    val player = remember(camera) {
        val mediaItem = MediaItem.fromUri(buildRtspUri(camera))
        val factory = RtspMediaSource.Factory()
            .setForceUseRtpTcp(camera.transport == "TCP")
            .setTimeoutMs(10000)
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMessage = "Erro ao reproduzir a câmera: ${error.message ?: "erro desconhecido"}"
                }
            })
            setMediaSource(factory.createMediaSource(mediaItem))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Text("Voltar") }
            Spacer(Modifier.width(12.dp))
            Text(camera.name, style = MaterialTheme.typography.titleLarge)
        }
        AndroidView(
            factory = { PlayerView(it).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                focusable = false
                isFocusable = false
            } },
            modifier = Modifier.fillMaxWidth().weight(1f)
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