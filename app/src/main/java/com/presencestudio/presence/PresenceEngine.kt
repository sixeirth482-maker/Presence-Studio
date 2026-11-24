package com.presencestudio.presence

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.presencestudio.data.ConnectionState
import com.presencestudio.discord.DiscordApi
import com.presencestudio.discord.DiscordGateway
import com.presencestudio.roblox.RobloxGameInfo
import com.presencestudio.roblox.RobloxPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

/**
 * ตัวจัดการสถานะหลัก อยู่ระดับโปรเซส ไม่ผูกกับหน้าจอ
 * ทำงานต่อได้แม้ปัดแอปทิ้ง เพราะ PresenceService ทำให้โปรเซสยังอยู่
 */
object PresenceEngine {

    const val DEFAULT_IDLE = "ไม่ได้เข้าแมพ"
    const val DEFAULT_PLAYING = "Roblox"

    val gateway = DiscordGateway()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var loopJob: Job? = null
    private var robloxUserId: Long? = null
    private var lastSignature: String? = null

    var discordToken by mutableStateOf("")
        private set
    var autoGame by mutableStateOf(false)
        private set
    var robloxUsername by mutableStateOf("")
        private set
    var robloxGame by mutableStateOf<RobloxGameInfo?>(null)
        private set
    var robloxStatus by mutableStateOf("")
        private set
    var idleText by mutableStateOf(DEFAULT_IDLE)
        private set
    var playingText by mutableStateOf(DEFAULT_PLAYING)
        private set
    var presenceActive by mutableStateOf(false)
        private set
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set
    var connectedUsername by mutableStateOf<String?>(null)
        private set
    var statusMessage by mutableStateOf("")
        private set

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences("presence_studio", Context.MODE_PRIVATE)
        prefs = p

        discordToken = p.getString("token", "") ?: ""
        robloxUsername = p.getString("roblox", "") ?: ""
        autoGame = p.getBoolean("auto", false)
        idleText = p.getString("idle", DEFAULT_IDLE) ?: DEFAULT_IDLE
        playingText = p.getString("playing", DEFAULT_PLAYING) ?: DEFAULT_PLAYING

        if (discordToken.isNotBlank()) gateway.setToken(discordToken)

        gateway.setListener(object : DiscordGateway.Listener {
            override fun onStateChanged(state: DiscordGateway.ConnectionState) {
                connectionState = when (state) {
                    DiscordGateway.ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    DiscordGateway.ConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    DiscordGateway.ConnectionState.CONNECTED -> ConnectionState.CONNECTED
                    DiscordGateway.ConnectionState.READY -> ConnectionState.READY
                    DiscordGateway.ConnectionState.ERROR -> ConnectionState.ERROR
                }
            }

            override fun onReady(username: String?) {
                connectedUsername = username
                statusMessage = "Connected as $username"
            }

            override fun onPresenceUpdated() {
                presenceActive = true
            }

            override fun onError(message: String) {
                statusMessage = message
            }
        })
    }

    fun setToken(token: String) {
        discordToken = token
        prefs?.edit()?.putString("token", token)?.apply()
        gateway.setToken(token)
    }

    fun connectRoblox(name: String) {
        val clean = name.trim()
        robloxUsername = clean
        prefs?.edit()?.putString("roblox", clean)?.apply()
        robloxUserId = null
        robloxGame = null
        lastSignature = null
        robloxStatus = if (clean.isEmpty()) "ใส่ชื่อผู้ใช้ Roblox เพื่อดูว่ากำลังเล่นอยู่" else "กำลังค้นหาผู้ใช้..."
        if (autoGame) startLoop()
    }

    fun setAutoGame(enabled: Boolean) {
        autoGame = enabled
        prefs?.edit()?.putBoolean("auto", enabled)?.apply()
        val ctx = appContext

        if (enabled) {
            if (discordToken.isBlank()) {
                statusMessage = "ใส่ Discord token ในหน้า Settings ก่อน จึงจะอัปเดตสถานะได้"
                return
            }
            if (ctx != null) PresenceService.start(ctx)
            startLoop()
        } else {
            stopLoop()
            lastSignature = null
            if (presenceActive) {
                gateway.clearPresence()
                presenceActive = false
            }
            if (ctx != null) PresenceService.stop(ctx)
        }
    }

    /** ตั้งข้อความตอนเล่นและตอนไม่ได้เล่น แล้วส่งสถานะใหม่ทันที */
    fun saveTexts(idle: String, playing: String) {
        idleText = idle.trim().ifBlank { DEFAULT_IDLE }
        playingText = playing.trim().ifBlank { DEFAULT_PLAYING }
        prefs?.edit()?.putString("idle", idleText)?.putString("playing", playingText)?.apply()
        lastSignature = null
        scope.launch {
            try {
                applyAuto()
            } catch (e: Exception) {
                statusMessage = "ส่งสถานะไม่สำเร็จ: ${e.message}"
            }
        }
    }

    fun stopGateway() {
        gateway.stop()
        presenceActive = false
        connectedUsername = null
    }

    /** เรียกจาก PresenceService เมื่อเริ่มทำงาน */
    fun resume() {
        if (autoGame) startLoop()
    }

    @Synchronized
    fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                try {
                    if (robloxUserId == null && robloxUsername.isNotBlank()) {
                        resolveRoblox(robloxUsername)
                    }
                    robloxUserId?.let { refreshRoblox(it) }
                    applyAuto()
                } catch (e: Exception) {
                    statusMessage = "เกิดข้อผิดพลาด: ${e.message}"
                }
                delay(10_000)
            }
        }
    }

    @Synchronized
    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun resolveRoblox(name: String) {
        robloxStatus = "กำลังค้นหาผู้ใช้..."
        try {
            val id = withContext(Dispatchers.IO) { RobloxPresence.resolveUserId(name) }
            if (id == null) {
                robloxStatus = "ไม่พบผู้ใช้ \"$name\""
            } else {
                robloxUserId = id
            }
        } catch (e: Exception) {
            robloxStatus = "เชื่อมต่อ Roblox ไม่ได้: ${e.message}"
        }
    }

    private suspend fun refreshRoblox(id: Long) {
        try {
            val info = withContext(Dispatchers.IO) { RobloxPresence.fetch(id) }
            robloxGame = info
            val time = SimpleDateFormat("HH:mm:ss").format(Date())
            robloxStatus = if (info == null) {
                "ไม่มีข้อมูลสถานะ (อาจตั้งค่าความเป็นส่วนตัวไว้) · $time"
            } else {
                "อัปเดตล่าสุด $time"
            }
        } catch (e: Exception) {
            robloxStatus = "ดึงสถานะไม่สำเร็จ: ${e.message}"
        }
    }

    private suspend fun applyAuto() {
        if (!autoGame || discordToken.isBlank()) return

        val info = robloxGame
        val gameName = if (info?.presenceType == 2) (info.gameName ?: info.lastLocation) else null
        val inGame = !gameName.isNullOrBlank()
        val details = if (inGame) gameName.orEmpty() else idleText
        val cover = if (inGame) info?.thumbnailUrl else null

        val signature = if (inGame) {
            "game|$gameName|${cover ?: ""}|$playingText"
        } else {
            "idle|$idleText"
        }
        if (signature == lastSignature) return
        lastSignature = signature

        gateway.setToken(discordToken)
        gateway.start()

        val coverMp = cover?.let { url ->
            withContext(Dispatchers.IO) {
                DiscordApi.fetchMediaProxy(discordToken, DiscordApi.MEDIA_APP_ID, url)
            }
        }

        val json = gateway.buildActivity(
            name = "Roblox",
            details = details,
            state = if (inGame) playingText else "Roblox",
            largeImage = coverMp.orEmpty(),
            largeText = details,
            smallImage = "",
            smallText = "Roblox",
            applicationId = "",
            showElapsed = inGame
        )

        gateway.setPresence(json)
        presenceActive = true
        statusMessage = if (inGame) "ส่งสถานะ Discord: กำลังเล่น $details" else "ส่งสถานะ Discord: $details"
    }
}
