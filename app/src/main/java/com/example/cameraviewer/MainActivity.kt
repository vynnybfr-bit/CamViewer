package com.example.cameraviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
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
                    val newCamera = Camera(name, url, username, password, transport)
                    cameras = cameras + newCamera
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
                CameraPlayerScreen(
                    camera = camera,
                    onBack = {
                        selectedCamera = null
                        screen = "home"
                    }
                )
            }
            else -> HomeScreen(
                cameras = cameras,
                onSettings = { screen = "settings" },
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
    onOpenCamera: (Camera) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        Text("CameraViewer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        if (cameras.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Nenhuma câmera configurada")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettings) { Text("Configurar câmeras") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(cameras) { camera ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                            onOpenCamera(camera)
                        }
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(camera.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(camera.url, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Protocolo RTSP: ${camera.transport}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("Toque para visualizar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("⚙ Configurações")
            }
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

    val keyboardController = LocalSoftwareKeyboardController.current

    val textFieldModifier = Modifier
        .fillMaxWidth()
        .onFocusChanged {
            if (it.isFocused) {
                keyboardController?.hide()
            }
        }
        .onPreviewKeyEvent {
            if (
                it.type == KeyEventType.KeyDown &&
                (it.key == Key.Enter || it.key == Key.NumPadEnter)
            ) {
                keyboardController?.show()
                true
            } else {
                false
            }
        }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Voltar") }
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(20.dp))
        Text("Adicionar câmera", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = textFieldModifier,
            label = { Text("Nome da câmera") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = textFieldModifier,
            label = { Text("URL RTSP") },
            placeholder = { Text("rtsp://192.168.1.100:554/...") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = textFieldModifier,
            label = { Text("Usuário") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = textFieldModifier,
            label = { Text("Senha") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Protocolo RTSP", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().focusable().clickable {
                        transport = "TCP"
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = transport == "TCP",
                        onClick = null
                    )
                    Text("TCP")
                }

                Row(
                    modifier = Modifier.fillMaxWidth().focusable().clickable {
                        transport = "UDP"
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = transport == "UDP",
                        onClick = null
                    )
                    Text("UDP")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (name.isNotBlank() && url.isNotBlank()) {
                    onAddCamera(name.trim(), url.trim(), username.trim(), password, transport)
                    name = ""
                    url = ""
                    username = ""
                    password = ""
                    transport = "TCP"
                    keyboardController?.hide()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && url.isNotBlank()
        ) {
            Text("Adicionar câmera")
        }

        Spacer(Modifier.height(25.dp))
        Text("Câmeras configuradas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.fillMaxWidth()) {
            items(cameras) { camera ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clickable { onOpenCamera(camera) }
                        ) {
                            Text(camera.name, style = MaterialTheme.typography.titleSmall)
                            Text(camera.url, style = MaterialTheme.typography.bodySmall)
                            Text("Protocolo RTSP: ${camera.transport}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Toque para visualizar", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { onDeleteCamera(camera) }) { Text("Excluir") }
                    }
                }
            }
        }
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

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Voltar") }
            Text(camera.name, style = MaterialTheme.typography.titleLarge)
        }

        AndroidView(
            factory = { PlayerView(it).apply {
                this.player = player
                useController = true
                keepScreenOn = true
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
    val encodedUsername = Uri.encode(camera.username)
    val encodedPassword = Uri.encode(camera.password)
    return Uri.parse("$prefix$encodedUsername:$encodedPassword@$rest")
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
    context.getSharedPreferences("cameras", Context.MODE_PRIVATE)
        .edit()
        .putString("list", array.toString())
        .apply()
}

fun loadCameras(context: Context): List<Camera> {
    val json = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)
        .getString("list", null) ?: return emptyList()

    return try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    Camera(
                        name = obj.optString("name"),
                        url = obj.optString("url"),
                        username = obj.optString("username"),
                        password = obj.optString("password"),
                        transport = obj.optString("transport", "TCP").uppercase()
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
