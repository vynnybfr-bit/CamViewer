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
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.CompoundButton
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

data class Camera(val name: String, val url: String, val username: String = "", val password: String = "", val transport: String = "TCP")
data class MultiView(val name: String, val cameras: List<String>)

class MainActivity : Activity() {
    private var cameras = emptyList<Camera>()
    private var multiViews = emptyList<MultiView>()
    private var startupTarget = "HOME"
    private var player: ExoPlayer? = null
    private val multiPlayers = mutableListOf<ExoPlayer>()
    private var currentScreen = "HOME"
    private var playerReturnScreen = "SETTINGS"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private val root by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(Color.BLACK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        cameras = loadCameras(this)
        multiViews = loadMultiViews(this)
        startupTarget = loadStartupTarget(this)
        val selected = multiViews.firstOrNull { it.name == startupTarget }
        if (selected != null) showMultiView(selected) else showHome()
    }

    override fun onBackPressed() {
        when {
            player != null -> {
                releasePlayer()
                if (playerReturnScreen == "SETTINGS") showSettings() else showHome()
            }
            currentScreen == "MULTIVIEW" -> {
                releaseMultiPlayers()
                showHome()
            }
            currentScreen == "SETTINGS" -> showHome()
            currentScreen == "MULTIVIEW_EDITOR" -> showSettings()
            else -> super.onBackPressed()
        }
    }

    private fun baseScreen(title: String, screenName: String): LinearLayout {
        root.removeAllViews()
        currentScreen = screenName
        if (title.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = title
                textSize = 26f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(-1, dp(56)))
        }
        setContentView(root)
        return root
    }

    private fun makeButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        maxLines = 1
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setPadding(dp(16), dp(4), dp(16), dp(4))
        background = buttonBackground(false)
        setOnFocusChangeListener { view, hasFocus -> view.background = buttonBackground(hasFocus) }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(5), 0, dp(5)) }
    }

    private fun buttonBackground(focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) Color.rgb(70, 110, 180) else Color.rgb(35, 35, 35))
        cornerRadius = dp(6).toFloat()
        if (focused) setStroke(dp(3), Color.WHITE)
    }

    private fun makeSectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun showHome() {
        releasePlayer(); releaseMultiPlayers()
        val screen = baseScreen("", "HOME")
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "CamViewer"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(56), 1f))
        val settings = TextView(this).apply {
            text = "⚙"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true; contentDescription = "Configurações"
            background = buttonBackground(false)
            setOnFocusChangeListener { view, hasFocus -> view.background = buttonBackground(hasFocus) }
            setOnClickListener { showSettings() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(64), dp(56))); screen.addView(header)
        if (multiViews.isEmpty()) {
            screen.addView(TextView(this).apply {
                text = "Nenhuma MultiView configurada.\n\nVá em ⚙ Configurações para adicionar câmeras e criar uma MultiView."
                textSize = 19f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
                setPadding(dp(30), dp(30), dp(30), dp(30)); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            }); settings.requestFocus()
        } else {
            multiViews.forEach { multiView -> screen.addView(makeButton(multiView.name) { showMultiView(multiView) }) }
            screen.getChildAt(1).requestFocus()
        }
    }

    private fun showMultiView(multiView: MultiView) {
        releasePlayer(); releaseMultiPlayers()
        val screen = baseScreen("", "MULTIVIEW")
        val selected = multiView.cameras.take(2).mapNotNull { name -> cameras.firstOrNull { it.name == name } }
        if (selected.size != 2) {
            screen.addView(message("Esta MultiView precisa de exatamente 2 câmeras."))
            return
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        selected.forEach { camera ->
            val view = PlayerView(this).apply {
                useController = false
                keepScreenOn = true
                isFocusable = false
                setBackgroundColor(Color.BLACK)
            }
            row.addView(view, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
            startMultiPlayer(camera, view)
        }
        screen.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun showSettings() {
        releasePlayer(); releaseMultiPlayers()
        val screen = baseScreen("Configurações", "SETTINGS")
        screen.addView(makeButton("Voltar") { showHome() })
        val scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        scroll.addView(content); screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(makeSectionTitle("Câmeras"))
        val name = editText("Nome"); val url = editText("URL RTSP"); val username = editText("Usuário")
        val password = editText("Senha").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        content.addView(name); content.addView(url); content.addView(username); content.addView(password)
        content.addView(TextView(this).apply { text = "Transporte"; textSize = 17f; setTextColor(Color.WHITE); setPadding(0, dp(10), 0, dp(2)) })
        val transportGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val tcp = RadioButton(this).apply { id = View.generateViewId(); text = "TCP"; textSize = 17f; setTextColor(Color.WHITE); isChecked = true }
        val udp = RadioButton(this).apply { id = View.generateViewId(); text = "UDP"; textSize = 17f; setTextColor(Color.WHITE) }
        transportGroup.addView(tcp); transportGroup.addView(udp); content.addView(transportGroup)
        content.addView(makeButton("Adicionar câmera") {
            val cameraName = name.text.toString().trim(); val cameraUrl = url.text.toString().trim()
            if (cameraName.isEmpty() || cameraUrl.isEmpty()) return@makeButton
            cameras = cameras + Camera(cameraName, cameraUrl, username.text.toString().trim(), password.text.toString(), if (tcp.isChecked) "TCP" else "UDP")
            saveCameras(this, cameras); showSettings()
        })
        content.addView(makeSectionTitle("Câmeras configuradas"))
        cameras.forEach { camera ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            row.addView(TextView(this).apply { text = "${camera.name}\n${camera.url}"; textSize = 16f; setTextColor(Color.WHITE) })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val view = makeButton("Ver") { showPlayer(camera, "SETTINGS") }
            view.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(0, dp(4), dp(4), dp(4)) }
            val delete = makeButton("Excluir") {
                cameras = cameras.filterNot { it == camera }
                multiViews = multiViews.map { mv -> mv.copy(cameras = mv.cameras.filterNot { it == camera.name }) }.filter { it.cameras.size == 2 }
                saveCameras(this, cameras); saveMultiViews(this, multiViews)
                if (startupTarget !in listOf("HOME") && multiViews.none { it.name == startupTarget }) { startupTarget = "HOME"; saveStartupTarget(this, startupTarget) }
                showSettings()
            }
            delete.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(4), dp(4), 0, dp(4)) }
            actions.addView(view); actions.addView(delete); row.addView(actions); content.addView(row)
        }
        content.addView(makeSectionTitle("MultiViews")); content.addView(makeButton("Criar MultiView") { showMultiViewEditor() })
        if (multiViews.isEmpty()) {
            content.addView(TextView(this).apply { text = "Nenhuma MultiView criada."; textSize = 17f; setTextColor(Color.LTGRAY); setPadding(0, dp(12), 0, dp(12)) })
        } else {
            multiViews.forEach { multiView ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this).apply { text = "${multiView.name}\n2 câmeras"; textSize = 16f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, dp(62), 1f))
                val open = makeButton("Abrir") { showMultiView(multiView) }
                open.layoutParams = LinearLayout.LayoutParams(dp(120), dp(54)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }; row.addView(open)
                val delete = makeButton("Excluir") {
                    multiViews = multiViews.filterNot { it == multiView }; if (startupTarget == multiView.name) startupTarget = "HOME"
                    saveMultiViews(this, multiViews); saveStartupTarget(this, startupTarget); showSettings()
                }
                delete.layoutParams = LinearLayout.LayoutParams(dp(130), dp(54)).apply { setMargins(dp(4), dp(4), 0, dp(4)) }; row.addView(delete); content.addView(row)
            }
        }
        content.addView(makeSectionTitle("Tela inicial")); content.addView(TextView(this).apply { text = "Escolha o que será aberto ao iniciar o app:"; textSize = 16f; setTextColor(Color.LTGRAY) })
        val startupGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val homeRadio = RadioButton(this).apply { id = View.generateViewId(); text = "Tela principal"; textSize = 17f; setTextColor(Color.WHITE); isChecked = startupTarget == "HOME"; tag = "HOME" }
        startupGroup.addView(homeRadio)
        multiViews.forEach { multiView -> startupGroup.addView(RadioButton(this).apply { id = View.generateViewId(); text = multiView.name; textSize = 17f; setTextColor(Color.WHITE); tag = multiView.name; isChecked = startupTarget == multiView.name }) }
        startupGroup.setOnCheckedChangeListener { group, checkedId -> startupTarget = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: "HOME"; saveStartupTarget(this, startupTarget) }
        content.addView(startupGroup); screen.getChildAt(1).requestFocus()
    }

    private fun showMultiViewEditor() {
        val screen = baseScreen("Criar MultiView", "MULTIVIEW_EDITOR")
        screen.addView(makeButton("Voltar") { showSettings() })
        val scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        scroll.addView(content); screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(TextView(this).apply { text = "Nome do MultiView"; textSize = 17f; setTextColor(Color.WHITE) })
        val name = editText("Ex.: Duas câmeras"); content.addView(name)
        content.addView(TextView(this).apply { text = "Selecione exatamente 2 câmeras"; textSize = 20f; setTextColor(Color.WHITE); setPadding(0, dp(18), 0, dp(6)) })
        val counter = TextView(this).apply { text = "0/2 câmeras selecionadas"; textSize = 16f; setTextColor(Color.LTGRAY) }; content.addView(counter)
        val checks = mutableMapOf<Camera, CheckBox>()
        cameras.forEach { camera ->
            val check = CheckBox(this).apply { text = camera.name; textSize = 18f; setTextColor(Color.WHITE); isFocusable = true; isFocusableInTouchMode = true; isClickable = true; setPadding(0, dp(5), 0, dp(5)) }
            checks[camera] = check
            check.setOnCheckedChangeListener { button, checked ->
                if (checked && checks.values.count { it.isChecked } > 2) { button.setOnCheckedChangeListener(null); button.isChecked = false; button.setOnCheckedChangeListener { b, c -> if (c && checks.values.count { it.isChecked } > 2) { b.setOnCheckedChangeListener(null); b.isChecked = false; b.setOnCheckedChangeListener(this@MainActivity::noopCheckListener) }; counter.text = "${checks.values.count { it.isChecked }}/2 câmeras selecionadas" }; return@setOnCheckedChangeListener }
                counter.text = "${checks.values.count { it.isChecked }}/2 câmeras selecionadas"
            }
            content.addView(check)
        }
        if (checks.isEmpty()) content.addView(message("Adicione pelo menos duas câmeras nas configurações antes de criar um MultiView."))
        content.addView(makeButton("Salvar MultiView") {
            val selected = checks.filter { it.value.isChecked }.keys.map { it.name }
            if (name.text.toString().trim().isEmpty() || selected.size != 2) return@makeButton
            val multiView = MultiView(name.text.toString().trim(), selected.take(2)); multiViews = multiViews.filterNot { it.name == multiView.name } + multiView
            saveMultiViews(this, multiViews); showSettings()
        })
        if (checks.isNotEmpty()) checks.values.first().requestFocus() else name.requestFocus()
    }

    private fun noopCheckListener(button: CompoundButton, checked: Boolean) {
        if (checked) { button.setOnCheckedChangeListener(null); button.isChecked = false }
    }

    private fun editText(hint: String) = EditText(this).apply {
        this.hint = hint; textSize = 17f; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setPadding(dp(12), 0, dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(5), 0, dp(5)) }
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(30), 0, 0) }
    }

    @OptIn(UnstableApi::class)
    private fun showPlayer(camera: Camera, returnScreen: String) {
        releasePlayer(); releaseMultiPlayers(); playerReturnScreen = returnScreen; currentScreen = "PLAYER"
        val playerView = PlayerView(this).apply { useController = false; keepScreenOn = true; isFocusable = false; setBackgroundColor(Color.BLACK) }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
        val back = makeButton("Voltar") { releasePlayer(); if (playerReturnScreen == "SETTINGS") showSettings() else showHome() }
        back.layoutParams = LinearLayout.LayoutParams(dp(140), dp(54)).apply { setMargins(0, 0, dp(8), 0) }; top.addView(back)
        top.addView(TextView(this).apply { text = camera.name; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(54), 1f))
        layout.addView(top); layout.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(layout); back.requestFocus(); startSinglePlayer(camera, playerView)
    }

    @OptIn(UnstableApi::class)
    private fun startSinglePlayer(camera: Camera, playerView: PlayerView) {
        val factory = RtspMediaSource.Factory().setForceUseRtpTcp(camera.transport == "TCP").setTimeoutMs(10000)
        player = ExoPlayer.Builder(this).build().apply { addListener(playerErrorListener(playerView)); setMediaSource(factory.createMediaSource(MediaItem.fromUri(buildRtspUri(camera)))); prepare(); playWhenReady = true }
        playerView.player = player
    }

    @OptIn(UnstableApi::class)
    private fun startMultiPlayer(camera: Camera, playerView: PlayerView) {
        val factory = RtspMediaSource.Factory().setForceUseRtpTcp(camera.transport == "TCP").setTimeoutMs(10000)
        val p = ExoPlayer.Builder(this).build().apply { addListener(playerErrorListener(playerView)); setMediaSource(factory.createMediaSource(MediaItem.fromUri(buildRtspUri(camera)))); prepare(); playWhenReady = true }
        multiPlayers.add(p); playerView.player = p
    }

    private fun playerErrorListener(playerView: PlayerView) = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val parent = playerView.parent as? LinearLayout
            parent?.addView(TextView(this@MainActivity).apply { text = "Erro: ${error.message ?: "falha na reprodução"}"; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(8), dp(4), dp(8), dp(4)) })
        }
    }

    private fun releasePlayer() { player?.release(); player = null }
    private fun releaseMultiPlayers() { multiPlayers.forEach { it.release() }; multiPlayers.clear() }
}

fun buildRtspUri(camera: Camera): Uri {
    val url = camera.url.trim(); if (camera.username.isBlank() && camera.password.isBlank()) return Uri.parse(url); if (!url.startsWith("rtsp://")) return Uri.parse(url)
    val rest = url.removePrefix("rtsp://"); return Uri.parse("rtsp://${Uri.encode(camera.username)}:${Uri.encode(camera.password)}@$rest")
}

fun saveCameras(context: Context, cameras: List<Camera>) {
    val array = JSONArray(); cameras.forEach { camera -> val obj = JSONObject(); obj.put("name", camera.name); obj.put("url", camera.url); obj.put("username", camera.username); obj.put("password", camera.password); obj.put("transport", camera.transport); array.put(obj) }
    context.getSharedPreferences("cameras", Context.MODE_PRIVATE).edit().putString("list", array.toString()).apply()
}

fun loadCameras(context: Context): List<Camera> {
    val json = context.getSharedPreferences("cameras", Context.MODE_PRIVATE).getString("list", null) ?: return emptyList()
    return try { val array = JSONArray(json); buildList { for (i in 0 until array.length()) { val obj = array.getJSONObject(i); add(Camera(obj.optString("name"), obj.optString("url"), obj.optString("username"), obj.optString("password"), obj.optString("transport", "TCP").uppercase())) } } } catch (_: Exception) { emptyList() }
}

fun saveMultiViews(context: Context, multiViews: List<MultiView>) {
    val array = JSONArray(); multiViews.forEach { multiView -> val obj = JSONObject(); obj.put("name", multiView.name); obj.put("cameras", JSONArray(multiView.cameras.take(2))); array.put(obj) }
    context.getSharedPreferences("multiviews", Context.MODE_PRIVATE).edit().putString("list", array.toString()).apply()
}

fun loadMultiViews(context: Context): List<MultiView> {
    val json = context.getSharedPreferences("multiviews", Context.MODE_PRIVATE).getString("list", null) ?: return emptyList()
    return try { val array = JSONArray(json); buildList { for (i in 0 until array.length()) { val obj = array.getJSONObject(i); val list = obj.optJSONArray("cameras") ?: JSONArray(); val cameras = buildList { for (j in 0 until list.length()) add(list.optString(j)) }.take(2); if (cameras.size == 2) add(MultiView(obj.optString("name"), cameras)) } } } catch (_: Exception) { emptyList() }
}

fun saveStartupTarget(context: Context, target: String) { context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("startup", target).apply() }
fun loadStartupTarget(context: Context): String = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("startup", "HOME") ?: "HOME"
