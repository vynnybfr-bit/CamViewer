package com.example.cameraviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
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

        setContent {
            CameraViewerApp(this)
        }
    }
}

@Composable
fun CameraViewerApp(context: Context) {

    var cameras by remember {
        mutableStateOf(loadCameras(context))
    }

    var screen by remember {
        mutableStateOf("home")
    }

    var selectedCamera by remember {
        mutableStateOf<Camera?>(null)
    }

    MaterialTheme {

        when (screen) {

            "settings" -> {
                SettingsScreen(
                    cameras = cameras,

                    onBack = {
                        screen = "home"
                    },

                    onAddCamera = {
                            name,
                            url,
                            username,
                            password,
                            transport ->

                        val newCamera = Camera(
                            name = name,
                            url = url,
                            username = username,
                            password = password,
                            transport = transport
                        )

                        cameras = cameras + newCamera

                        saveCameras(
                            context,
                            cameras
                        )
                    },

                    onDeleteCamera = { camera ->

                        cameras = cameras.filterNot {
                            it == camera
                        }

                        saveCameras(
                            context,
                            cameras
                        )
                    },

                    onOpenCamera = { camera ->

                        selectedCamera = camera
                        screen = "player"
                    }
                )
            }

            "player" -> {

                selectedCamera?.let { camera ->

                    CameraPlayerScreen(
                        camera = camera,

                        onBack = {
                            selectedCamera = null
                            screen = "home"
                        }
                    )
                }
            }

            else -> {

                HomeScreen(
                    cameras = cameras,

                    onSettings = {
                        screen = "settings"
                    },

                    onOpenCamera = { camera ->

                        selectedCamera = camera
                        screen = "player"
                    }
                )
            }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "CameraViewer",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (cameras.isEmpty()) {

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "Nenhuma câmera configurada"
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Button(
                    onClick = onSettings
                ) {
                    Text("Configurar câmeras")
                }
            }

        } else {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                items(cameras) { camera ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                onOpenCamera(camera)
                            }
                    ) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {

                            Text(
                                text = camera.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = camera.url,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = "Transporte: ${camera.transport}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "Toque para visualizar",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Button(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚙ Configurações")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    cameras: List<Camera>,
    onBack: () -> Unit,
    onAddCamera: (
        String,
        String,
        String,
        String,
        String
    ) -> Unit,
    onDeleteCamera: (Camera) -> Unit,
    onOpenCamera: (Camera) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var url by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var advancedExpanded by remember {
        mutableStateOf(false)
    }

    var transport by remember {
        mutableStateOf("TCP")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBack
            ) {
                Text("← Voltar")
            }

            Text(
                text = "Configurações",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Adicionar câmera",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Nome da câmera")
            },
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("URL RTSP")
            },
            placeholder = {
                Text("rtsp://192.168.1.100:554/...")
            },
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Usuário")
            },
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Senha")
            },
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        TextButton(
            onClick = {
                advancedExpanded = !advancedExpanded
            }
        ) {

            Text(
                if (advancedExpanded) {
                    "⚙ Configuração avançada ▲"
                } else {
                    "⚙ Configuração avançada ▼"
                }
            )
        }

        if (advancedExpanded) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {

                Column(
                    modifier = Modifier.padding(12.dp)
                ) {

                    Text(
                        text = "Transporte RTSP",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected = transport == "TCP",
                            onClick = {
                                transport = "TCP"
                            }
                        )

                        Text("TCP")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected = transport == "UDP",
                            onClick = {
                                transport = "UDP"
                            }
                        )

                        Text("UDP")
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Button(
            onClick = {

                if (
                    name.isNotBlank() &&
                    url.isNotBlank()
                ) {

                    onAddCamera(
                        name.trim(),
                        url.trim(),
                        username.trim(),
                        password,
                        transport
                    )

                    name = ""
                    url = ""
                    username = ""
                    password = ""

                    transport = "TCP"
                    advancedExpanded = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled =
                name.isNotBlank() &&
                url.isNotBlank()
        ) {

            Text("Adicionar câmera")
        }

        Spacer(
            modifier = Modifier.height(25.dp)
        )

        Text(
            text = "Câmeras configuradas",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {

            items(cameras) { camera ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onOpenCamera(camera)
                                }
                        ) {

                            Text(
                                text = camera.name,
                                style = MaterialTheme.typography.titleSmall
                            )

                            Text(
                                text = camera.url,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Transporte: ${camera.transport}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = "Toque para visualizar",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        TextButton(
                            onClick = {
                                onDeleteCamera(camera)
                            }
                        ) {
                            Text("Excluir")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CameraPlayerScreen(
    camera: Camera,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val player = remember(camera) {

        val rtspUri = buildRtspUri(camera)

        val mediaItem = MediaItem.fromUri(
            rtspUri
        )

        val mediaSourceFactory =
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(
                    camera.transport == "TCP"
                )
                .setTimeoutMs(10000)

        ExoPlayer.Builder(context)
            .build()
            .apply {

                addListener(
                    object : Player.Listener {

                        override fun onPlayerError(
                            error: androidx.media3.common.PlaybackException
                        ) {

                            errorMessage =
                                "Erro ao reproduzir a câmera: ${error.message ?: "erro desconhecido"}"
                        }
                    }
                )

                val mediaSource =
                    mediaSourceFactory
                        .createMediaSource(mediaItem)

                setMediaSource(mediaSource)

                prepare()

                playWhenReady = true
            }
    }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBack
            ) {
                Text("← Voltar")
            }

            Text(
                text = camera.name,
                style = MaterialTheme.typography.titleLarge
            )
        }

        AndroidView(
            factory = {
                PlayerView(it).apply {

                    this.player = player

                    useController = true

                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        errorMessage?.let { message ->

            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun buildRtspUri(
    camera: Camera
): Uri {

    val url = camera.url.trim()

    if (
        camera.username.isBlank() &&
        camera.password.isBlank()
    ) {
        return Uri.parse(url)
    }

    val prefix = "rtsp://"

    if (!url.startsWith(prefix)) {
        return Uri.parse(url)
    }

    val rest = url.removePrefix(prefix)

    val encodedUsername =
        Uri.encode(camera.username)

    val encodedPassword =
        Uri.encode(camera.password)

    return Uri.parse(
        "$prefix$encodedUsername:$encodedPassword@$rest"
    )
}

fun saveCameras(
    context: Context,
    cameras: List<Camera>
) {

    val array = JSONArray()

    cameras.forEach { camera ->

        val objectJson = JSONObject()

        objectJson.put(
            "name",
            camera.name
        )

        objectJson.put(
            "url",
            camera.url
        )

        objectJson.put(
            "username",
            camera.username
        )

        objectJson.put(
            "password",
            camera.password
        )

        objectJson.put(
            "transport",
            camera.transport
        )

        array.put(objectJson)
    }

    context
        .getSharedPreferences(
            "camera_viewer",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "cameras",
            array.toString()
        )
        .apply()
}

fun loadCameras(
    context: Context
): List<Camera> {

    val preferences =
        context.getSharedPreferences(
            "camera_viewer",
            Context.MODE_PRIVATE
        )

    val data =
        preferences.getString(
            "cameras",
            null
        ) ?: return emptyList()

    return try {

        val array = JSONArray(data)

        List(array.length()) { index ->

            val objectJson =
                array.getJSONObject(index)

            Camera(
                name = objectJson.getString(
                    "name"
                ),

                url = objectJson.getString(
                    "url"
                ),

                username =
                    objectJson.optString(
                        "username",
                        ""
                    ),

                password =
                    objectJson.optString(
                        "password",
                        ""
                    ),

                transport =
                    objectJson.optString(
                        "transport",
                        "TCP"
                    )
            )
        }

    } catch (e: Exception) {

        emptyList()
    }
}
