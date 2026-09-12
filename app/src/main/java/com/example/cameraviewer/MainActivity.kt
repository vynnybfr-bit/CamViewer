package com.example.cameraviewer

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
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

class MainActivity : Activity() {
    private var cameras = emptyList<Camera>()
    private var player: ExoPlayer? = null

    private val root: LinearLayout by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        cameras = loadCameras(this)
        showHome()
    }

    override fun onBackPressed() {
        if (player != null) {
            releasePlayer()
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    private fun baseScreen(title: String): LinearLayout {
        root.removeAllViews()
        val titleView = TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 18)
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, 64))
        setContentView(root)
        return root
    }

    private fun makeButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 18f
        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, 60).apply { setMargins(0, 6, 0, 6) }
    }

    private fun showHome() {
        val screen = baseScreen("CamViewer")
        screen.addView(makeButton("MultiView") { showMultiView() })
        screen.addView(makeButton("Configurações") { showSettings() })

        if (cameras.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "Câmeras"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 20, 0, 8)
            }
            screen.addView(label)
            cameras.forEach { camera ->
                screen.addView(makeButton(camera.name) { showPlayer(camera) })
            }
        }

        screen.getChildAt(1).requestFocus()
    }

    private fun showMultiView() {
        val screen = baseScreen("MultiView")
        screen.addView(makeButton("Voltar") { showHome() })

        if (cameras.isEmpty()) {
            screen.addView(message("Nenhuma câmera configurada."))
            return
        }

        cameras.forEach { camera ->
            screen.addView(makeButton(camera.name) { showPlayer(camera) })
        }
        screen.getChildAt(1).requestFocus()
    }

    private fun showSettings() {
        val screen = baseScreen("Configurações")
        screen.addView(makeButton("Voltar") { showHome() })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        scroll.addView(content)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val name = editText("Nome")
        val url = editText("URL RTSP")
        val username = editText("Usuário")
        val password = editText("Senha")
        password.inputType = 0x81

        content.addView(name)
        content.addView(url)
        content.addView(username)
        content.addView(password)

        val transportGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val tcp = RadioButton(this).apply { text = "TCP"; textSize = 17f; isChecked = true }
        val udp = RadioButton(this).apply { text = "UDP"; textSize = 17f }
        transportGroup.addView(tcp)
        transportGroup.addView(udp)
        content.addView(transportGroup)

        content.addView(makeButton("Adicionar câmera") {
            if (name.text.toString().trim().isEmpty() || url.text.toString().trim().isEmpty()) return@makeButton
            val transport = if (tcp.isChecked) "TCP" else "UDP"
            cameras = cameras + Camera(
                name.text.toString().trim(),
                url.text.toString().trim(),
                username.text.toString().trim(),
                password.text.toString(),
                transport
            )
            saveCameras(this, cameras)
            showSettings()
        })

        val listTitle = TextView(this).apply {
            text = "Câmeras configuradas"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 8)
        }
        content.addView(listTitle)

        cameras.forEach { camera ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val info = TextView(this).apply {
                text = "${camera.name}\n${camera.url}"
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            row.addView(info)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val view = makeButton("Ver") { showPlayer(camera) }
            view.layoutParams = LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(0, 4, 4, 4) }
            val delete = makeButton("Excluir") {
                cameras = cameras.filterNot { it == camera }
                saveCameras(this, cameras)
                showSettings()
            }
            delete.layoutParams = LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(4, 4, 0, 4) }
            actions.addView(view)
            actions.addView(delete)
            row.addView(actions)
            content.addView(row)
        }

        screen.getChildAt(1).requestFocus()
    }

    private fun editText(hint: String) = EditText(this).apply {
        this.hint = hint
        textSize = 17f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.LTGRAY)
        setPadding(12, 0, 12, 0)
        layoutParams = LinearLayout.LayoutParams(-1, 58).apply { setMargins(0, 5, 0, 5) }
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 30, 0, 0) }
    }

    @OptIn(UnstableApi::class)
    private fun showPlayer(camera: Camera) {
        releasePlayer()
        val playerView = PlayerView(this).apply {
            useController = false
            keepScreenOn = true
            isFocusable = false
            setBackgroundColor(Color.BLACK)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 4)
        }
        val back = makeButton("Voltar") { releasePlayer(); showHome() }
        back.layoutParams = LinearLayout.LayoutParams(140, 56).apply { setMargins(0, 0, 8, 0) }
        top.addView(back)
        top.addView(TextView(this).apply {
            text = camera.name
            textSize = 20f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, 56, 1f))
        layout.addView(top)
        layout.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        back.requestFocus()

        val mediaItem = MediaItem.fromUri(buildRtspUri(camera))
        val factory = RtspMediaSource.Factory()
            .setForceUseRtpTcp(camera.transport == "TCP")
            .setTimeoutMs(10000)
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val errorText = TextView(this@MainActivity).apply {
                        text = "Erro: ${error.message ?: "falha na reprodução"}"
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        setPadding(12, 8, 12, 8)
                    }
                    layout.addView(errorText)
                }
            })
            setMediaSource(factory.createMediaSource(mediaItem))
            prepare()
            playWhenReady = true
        }
        playerView.player = player
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}

fun buildRtspUri(camera: Camera): Uri {
    val url = camera.url.trim()
    if (camera.username.isBlank() && camera.password.isBlank()) return Uri.parse(url)
    if (!url.startsWith("rtsp://")) return Uri.parse(url)
    val rest = url.removePrefix("rtsp://")
    return Uri.parse("rtsp://${Uri.encode(camera.username)}:${Uri.encode(camera.password)}@$rest")
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
        .edit().putString("list", array.toString()).apply()
}

fun loadCameras(context: Context): List<Camera> {
    val json = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)
        .getString("list", null) ?: return emptyList()
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
    } catch (_: Exception) {
        emptyList()
    }
}
