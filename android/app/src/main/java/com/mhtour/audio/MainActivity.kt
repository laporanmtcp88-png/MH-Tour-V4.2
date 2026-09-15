package com.mhtour.audio

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    companion object { private const val MIC_REQUEST = 1001; private const val CAMERA_REQUEST = 1002 }

    private val tokenBaseUrl get() = BuildConfig.TOKEN_BASE_URL.trimEnd('/')
    private var room: Room? = null
    private var currentCode: String? = null
    private var micEnabled = false

    private val green = Color.rgb(7, 78, 59)
    private val emerald = Color.rgb(11, 122, 91)
    private val deep = Color.rgb(3, 45, 34)
    private val gold = Color.rgb(198, 145, 36)
    private val cream = Color.rgb(248, 246, 238)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val white = Color.WHITE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.init(applicationContext)
        requestMicIfNeeded()
        home()
    }

    private fun isWifiOnly(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun requireWifiOnly(): Boolean {
        if (isWifiOnly()) return true
        Toast.makeText(this, "MH Tour hanya dapat digunakan melalui Wi-Fi hotspot. Matikan data seluler dan sambungkan ke hotspot Guide.", Toast.LENGTH_LONG).show()
        return false
    }

    private fun requestMicIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
        }
    }

    private fun bg(color: Int, radius: Float = 18f, stroke: Int? = null, strokeWidth: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
            stroke?.let { setStroke(dp(strokeWidth), it) }
        }

    private fun gradientBg(top: Int, bottom: Int, radius: Float = 24f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)
    ).apply { cornerRadius = dp(radius.toInt()).toFloat() }

    private fun tv(text: String, size: Float, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun primaryButton(text: String, color: Int = emerald): Button = Button(this).apply {
        this.text = text; setTextColor(white); textSize = 15f; isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD; minHeight = dp(52); background = bg(color, 16f)
        stateListAnimator = null; elevation = dp(2).toFloat()
    }

    private fun outlineButton(text: String): Button = Button(this).apply {
        this.text = text; setTextColor(green); textSize = 15f; isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD; minHeight = dp(50); background = bg(white, 16f, Color.rgb(203,213,225), 1)
        stateListAnimator = null
    }

    private fun edit(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint; setText(value); setSingleLine(true); textSize = 15f
        setTextColor(ink); setHintTextColor(muted); background = bg(white, 14f, Color.rgb(226,232,240), 1)
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun page(title: String, subtitle: String): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cream) }
        root.addView(hero(title, subtitle))
        return root
    }

    private fun hero(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(24))
            background = gradientBg(deep, green, 0f)
        }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "MH"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(green); typeface = Typeface.DEFAULT_BOLD
            background = bg(gold, 14f); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        brand.addView(mark)
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        brandText.addView(tv("MH TOUR", 20f, white, true)); brandText.addView(tv("UMRAH AUDIO COMPANION", 10f, Color.rgb(226,232,240), true))
        brand.addView(brandText)
        box.addView(brand)
        val t = tv(title, 25f, white, true); t.setPadding(0, dp(20), 0, dp(2)); box.addView(t)
        box.addView(tv(subtitle, 13f, Color.rgb(226,232,240)))
        return box
    }

    private fun scroll(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true; addView(content)
    }

    private fun contentColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(26))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17)); background = bg(white, 18f, Color.rgb(231,229,220), 1)
        elevation = dp(1).toFloat()
    }

    private fun addGap(parent: LinearLayout, h: Int = 12) = parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(h)))
    private fun addFull(parent: LinearLayout, view: View, h: Int) = parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h)))

    private fun home() {
        room?.disconnect(); room = null; currentCode = null; micEnabled = false
        val root = page("Perjalanan ibadah, lebih terhubung", "Satu ruang komunikasi profesional untuk Guide dan jemaah selama perjalanan umrah.")
        val body = contentColumn()

        val welcome = card()
        welcome.addView(tv("Assalamu’alaikum", 20f, green, true))
        welcome.addView(tv("Selamat datang di MH Tour. Pilih peran Anda untuk memulai sesi audio rombongan.", 14f, muted))
        body.addView(welcome)
        addGap(body, 14)

        val guide = card(); guide.addView(tv("GUIDE / MUTHAWWIF", 12f, gold, true)); guide.addView(tv("Pimpin komunikasi suara", 19f, ink, true)); guide.addView(tv("Buat kode sesi dan bicara langsung ke seluruh jemaah yang bergabung.", 13f, muted))
        addGap(guide, 14)
        val gb = primaryButton("Mulai sebagai Guide  ›")
        guide.addView(gb); body.addView(guide)
        addGap(body, 14)

        val jamaah = card(); jamaah.addView(tv("JEMAAH", 12f, gold, true)); jamaah.addView(tv("Dengarkan Guide", 19f, ink, true)); jamaah.addView(tv("Masukkan kode rombongan untuk menerima audio secara real-time.", 13f, muted))
        addGap(jamaah, 14)
        val jb = primaryButton("Gabung sebagai Jemaah  ›", green); jamaah.addView(jb); body.addView(jamaah)
        addGap(body, 14)
        val journey = card()
        journey.addView(tv("DASHBOARD PERJALANAN", 12f, gold, true))
        addGap(journey, 5)
        journey.addView(tv("Rombongan Anda dalam satu ruang komunikasi", 17f, ink, true))
        journey.addView(tv("Buat sesi untuk Guide, bagikan kode kepada jemaah, lalu pantau status koneksi audio secara real-time.", 13f, muted))
        addGap(journey, 12)
        val stats = tv("Ruang: siap   •   Jemaah online: tampil saat server mendukung presence", 12f, green, true)
        journey.addView(stats)
        body.addView(journey)
        addGap(body, 18)
        val footer = tv("● LiveKit Audio  •  Token aman  •  Real-time", 12f, green, true); footer.gravity = Gravity.CENTER; body.addView(footer)
        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        gb.setOnClickListener { if (requireWifiOnly()) guideScreen() }; jb.setOnClickListener { if (requireWifiOnly()) jamaahScreen() }
    }

    private fun makeQrBitmap(payload: String, size: Int = 720): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }

    private fun shareText(text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "Bagikan Rombongan MH Tour"))
    }

    private fun guideScreen() {
        val root = page("Ruang Audio Guide", "Buat ruang pribadi untuk rombongan, lalu bagikan kode sesi kepada jemaah.")
        val body = contentColumn()
        val identity = card(); identity.addView(tv("PROFIL GUIDE", 12f, gold, true)); addGap(identity, 5)
        val name = edit("Nama Guide / Muthawwif", "Guide"); identity.addView(name, LinearLayout.LayoutParams(-1, dp(52))); body.addView(identity)
        addGap(body)

        val session = card(); session.addView(tv("SESI ROMBONGAN", 12f, gold, true)); addGap(session, 4)
        val code = tv("—", 34f, green, true); code.gravity = Gravity.CENTER; code.setPadding(0, dp(8), 0, dp(4)); session.addView(code)
        val hint = tv("Kode akan muncul setelah sesi dibuat", 12f, muted); hint.gravity = Gravity.CENTER; session.addView(hint)
        addGap(session, 10)
        val qrTitle = tv("QR CODE ROMBONGAN", 11f, gold, true); qrTitle.gravity = Gravity.CENTER; session.addView(qrTitle)
        addGap(session, 6)
        val qr = ImageView(this).apply {
            setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(12), dp(12), dp(12)); scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        session.addView(qr, LinearLayout.LayoutParams(-1, dp(250)))
        val qrHint = tv("Jemaah dapat memindai QR ini untuk bergabung ke rombongan.", 12f, muted); qrHint.gravity = Gravity.CENTER; qrHint.visibility = View.GONE; session.addView(qrHint)
        addGap(session, 10)
        val create = primaryButton("Buat Sesi Rombongan", gold); session.addView(create)
        val shareQr = outlineButton("Bagikan Kode & QR Rombongan"); shareQr.isEnabled = false; session.addView(shareQr)
        body.addView(session)
        addGap(body)

        val live = card(); live.addView(tv("KONTROL AUDIO", 12f, gold, true)); addGap(live, 5)
        val state = tv("Belum ada sesi aktif", 16f, muted, true); state.gravity = Gravity.CENTER; live.addView(state)
        addGap(live, 10)
        val talk = primaryButton("●  Mulai Bicara", emerald); talk.isEnabled = false; live.addView(talk)
        addGap(live, 8)
        val mute = outlineButton("Mute Mikrofon"); mute.isEnabled = false; live.addView(mute)
        addGap(live, 8)
        val back = outlineButton("Kembali ke Beranda"); live.addView(back); body.addView(live)

        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)

        create.setOnClickListener {
            if (!requireWifiOnly()) return@setOnClickListener
            create.isEnabled = false; state.text = "Membuat sesi..."
            lifecycleScope.launch {
                try {
                    val r = requestSession(name.text.toString().trim().ifBlank { "Guide" })
                    currentCode = r.getString("code"); code.text = currentCode
                    qr.setImageBitmap(makeQrBitmap("MHTOUR|JOIN|$currentCode")); qr.visibility = View.VISIBLE; qrHint.visibility = View.VISIBLE
                    shareQr.isEnabled = true
                    state.text = "Sesi siap — bagikan kode atau QR di atas ke jemaah."; talk.isEnabled = true
                } catch (e: Exception) { state.text = "Gagal membuat sesi: ${e.message}" }
                finally { create.isEnabled = true }
            }
        }

        shareQr.setOnClickListener {
            currentCode?.let { c -> shareText("MH Tour — Rombongan: $c\n\nScan QR untuk bergabung. Kode rombongan: $c") }
        }

        talk.setOnClickListener {
            if (!requireWifiOnly()) return@setOnClickListener
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestMicIfNeeded(); state.text = "Izinkan mikrofon, lalu tekan Mulai Bicara lagi."; return@setOnClickListener }
            talk.isEnabled = false; state.text = "Menghubungkan ke LiveKit lokal..."
            lifecycleScope.launch {
                try {
                    val token = requestToken(currentCode!!, name.text.toString().trim().ifBlank { "Guide" }, "guide")
                    room?.disconnect(); room = connectLiveKit(token)
                    val ok = room!!.localParticipant.setMicrophoneEnabled(true)
                    if (!ok) error("Mikrofon gagal dipublish")
                    micEnabled = true; mute.isEnabled = true; talk.text = "●  Sedang Bicara"; state.text = "TERHUBUNG • Suara Anda LIVE ke jemaah"
                } catch (e: Exception) { state.text = "Gagal terhubung: ${e.message}"; room?.disconnect(); room = null }
                finally { talk.isEnabled = true }
            }
        }
        mute.setOnClickListener {
            lifecycleScope.launch {
                try { micEnabled = !micEnabled; room?.localParticipant?.setMicrophoneEnabled(micEnabled); mute.text = if (micEnabled) "Mute Mikrofon" else "Unmute Mikrofon"; talk.text = if (micEnabled) "●  Sedang Bicara" else "○  Suara Dimute"; state.text = if (micEnabled) "Mikrofon LIVE • Jemaah dapat mendengar" else "Mikrofon dimute • Sesi tetap tersambung" }
                catch (e: Exception) { state.text = "Gagal mengubah mikrofon: ${e.message}" }
            }
        }
        back.setOnClickListener { home() }
    }

    private fun jamaahScreen(prefilledCode: String = "") {
        val root = page("Masuk ke Rombongan", "Masukkan kode yang diberikan Guide untuk mulai menerima audio.")
        val body = contentColumn()
        val form = card(); form.addView(tv("DATA JEMAAH", 12f, gold, true)); addGap(form, 5)
        val name = edit("Nama Jemaah", "Jemaah"); form.addView(name, LinearLayout.LayoutParams(-1, dp(52))); addGap(form, 10)
        val code = edit("Kode sesi  •  contoh UM123456", prefilledCode); code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; form.addView(code, LinearLayout.LayoutParams(-1, dp(52))); addGap(form, 10)
        val scan = outlineButton("▣  Scan QR Rombongan"); form.addView(scan); addGap(form, 12)
        val join = primaryButton("Gabung & Dengarkan", green); form.addView(join); body.addView(form)
        addGap(body)

        val listening = card(); listening.addView(tv("STATUS AUDIO", 12f, gold, true)); addGap(listening, 4)
        val state = tv("Belum terhubung", 17f, muted, true); state.gravity = Gravity.CENTER; listening.addView(state)
        addGap(listening, 10)
        val indicator = tv("◉  MENUNGGU SUARA GUIDE", 13f, green, true); indicator.gravity = Gravity.CENTER; listening.addView(indicator)
        addGap(listening, 12)
        listening.addView(tv("Pastikan volume media HP aktif. Audio Guide akan terdengar otomatis setelah Guide mulai berbicara.", 12f, muted))
        addGap(listening, 12)
        val back = outlineButton("Kembali ke Beranda"); listening.addView(back); body.addView(listening)

        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        scan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            } else {
                startQrScanner()
            }
        }
        join.setOnClickListener {
            if (!requireWifiOnly()) return@setOnClickListener
            val c = code.text.toString().trim().uppercase(); if (c.isBlank()) { code.error = "Masukkan kode sesi"; return@setOnClickListener }
            join.isEnabled = false; state.text = "Menghubungkan..."; indicator.text = "◉  MENGHUBUNGKAN KE LIVEKIT LOKAL"
            lifecycleScope.launch {
                try { val token = requestToken(c, name.text.toString().trim().ifBlank { "Jemaah" }, "jamaah"); room?.disconnect(); room = connectLiveKit(token); currentCode = c; state.text = "TERHUBUNG • Menunggu Guide"; indicator.text = "●  SIAP MENDENGARKAN" }
                catch (e: Exception) { state.text = "Gagal bergabung: ${e.message}"; indicator.text = "◉  KONEKSI GAGAL"; room?.disconnect(); room = null }
                finally { join.isEnabled = true }
            }
        }
        back.setOnClickListener { home() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startQrScanner()
        }
    }

    private fun startQrScanner() {
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code rombongan MH Tour")
            setBeepEnabled(true)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val payload = result.contents.trim()
                val code = payload.removePrefix("MHTOUR|JOIN|").trim().uppercase()
                if (code.matches(Regex("UM[A-Z0-9]{4,20}"))) {
                    jamaahScreenWithCode(code)
                } else {
                    Toast.makeText(this, "QR bukan QR rombongan MH Tour yang valid", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Pemindaian dibatalkan", Toast.LENGTH_SHORT).show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun jamaahScreenWithCode(codeValue: String) {
        jamaahScreen(codeValue)
    }

    private fun httpPost(path: String, body: JSONObject): JSONObject {
        if (!isWifiOnly()) throw IllegalStateException("Wi-Fi hotspot wajib aktif; data seluler tidak diizinkan")
        val c = (URL("$tokenBaseUrl$path").openConnection() as HttpURLConnection)
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 20000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (c.responseCode !in 200..299) throw IllegalStateException(runCatching { JSONObject(response).optString("error") }.getOrDefault("HTTP ${c.responseCode}").ifBlank { "HTTP ${c.responseCode}" })
        return JSONObject(response)
    }

    private suspend fun requestSession(guideName: String) = withContext(Dispatchers.IO) { httpPost("/session", JSONObject().put("guideName", guideName)) }
    private suspend fun requestToken(code: String, name: String, role: String) = withContext(Dispatchers.IO) { httpPost("/token", JSONObject().put("code", code).put("participantName", name).put("role", role)) }

    private suspend fun connectLiveKit(response: JSONObject): Room = LiveKit.connect(applicationContext, response.getString("serverUrl"), response.getString("participantToken"), ConnectOptions(autoSubscribe = true, audio = false, video = false))

    override fun onDestroy() { room?.disconnect(); room = null; super.onDestroy() }
}
