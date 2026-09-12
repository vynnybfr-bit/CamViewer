package com.example.cameraviewer

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val root: LinearLayout by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
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
            includeFontPadding = true
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        return root
    }

    private fun makeButton(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = true
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.rgb(35, 35, 35))
            cornerRadius = dp(6).toFloat()
        }
        setOnFocusChangeListener { view, hasFocus ->
            view.alpha = if (hasFocus) 0.75f else 1f
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply {
            setMargins(0, dp(5), 0, dp(5))
        }
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
                includeFontPadding = true
                setPadding(0, dp(18), 0, dp(6))
            }
            screen.addView(label, LinearLayout.LayoutParams(-1, dp(52)))
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        scroll.addView(content)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val name = editText("Nome")
        val url = editText("URL RTSP")
        val username = editText("Usuário")
        val password = editText("Senha")
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        content.addView(name)
        content.addView(url)
        content.addView(username)
        content.addView(password)

        val transportLabel = TextView(this).apply {
            text = "Transporte"
            textSize = 17f
            setTextColor(Color.WHITE)
            includeFontPadding = true
            setPadding(0, dp(10), 0, dp(2))
        }
        content.addView(transportLabel)

        val transportGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        val tcp = RadioButton(this).apply {
            id = View.generateViewId()
            text = "TCP"
            textSize = 17f
            setTextColor(Color.WHITE)
            isChecked = true
        }
        val udp = RadioButton(this).apply {
            id = View.generateViewId()
            text = "UDP"
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        transportGroup.addView(tcp)
        transportGroup.addView(udp)
        transportGroup.setOnCheckedChangeListener { _, checkedId ->
            tcp.isChecked = checkedId == tcp.id
            udp.isChecked = checkedId == udp.id
        }
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
            includeFontPadding = true
            setPadding(0, dp(20), 0, dp(6))
        }
        content.addView(listTitle)

        cameras.forEach { camera ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val info = TextView(this).apply {
                text = "${camera.name}\n${camera.url}"
                textSize = 16f
                setTextColor(Color.WHITE)
                includeFontPadding = true
            }
            row.addView(info, LinearLayout.LayoutParams(-1, -2))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val view = makeButton("Ver") { showPlayer(camera) }
            view.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(0, dp(4), dp(4), dp(4))
            }
            val delete = makeButton("Excluir") {
                cameras = cameras.filterNot { it == camera }
                saveCameras(this, cameras)
                showSettings()
            }
            delete.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(4), dp(4), 0, dp(4))
            }
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
        includeFontPadding = true
        setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply {
            setMargins(0, dp(5), 0, dp(5))
        }
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = true
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(30), 0, 0)
        }
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
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val back = makeButton("Voltar") { releasePlayer(); showHome() }
        back.layoutParams = LinearLayout.LayoutParams(dp(140), dp(54)).apply {
            setMargins(0, 0, dp(8), 0)
        }
        top.addView(back)
        top.addView(TextView(this).apply {
            text = camera.name
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
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
                        includeFontPadding = true
                        setPadding(dp(12), dp(8), dp(12), dp(8))
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
