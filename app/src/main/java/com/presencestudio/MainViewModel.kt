package com.presencestudio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.presencestudio.data.ActivityProfile
import com.presencestudio.data.ConnectionState
import com.presencestudio.discord.DiscordApi
import com.presencestudio.discord.DiscordGateway
import com.presencestudio.discord.DiscordUser
import com.presencestudio.roblox.RobloxGameInfo
import com.presencestudio.roblox.RobloxPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gateway = DiscordGateway()

    private val prefs = application.getSharedPreferences("presence_studio", Context.MODE_PRIVATE)

    var robloxUsername by mutableStateOf("")
        private set

    var robloxGame by mutableStateOf<RobloxGameInfo?>(null)
        private set

    var robloxStatus by mutableStateOf("")
        private set

    var discordUser by mutableStateOf<DiscordUser?>(null)
        private set

    var autoGameStatus by mutableStateOf(false)
        private set

    var idleText by mutableStateOf("ไม่ได้เข้าแมพ")
        private set

    private var robloxUserId: Long? = null
    private var lastAutoGame: String? = null
    private var presenceJob: Job? = null

    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    var connectedUsername by mutableStateOf<String?>(null)
        private set

    var currentProfile by mutableStateOf<ActivityProfile?>(null)
        private set

    var isPresenceActive by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf("")
        private set

    var profiles by mutableStateOf(
        listOf(
            ActivityProfile(
                id = "roblox",
                name = "Roblox",
                details = "Playing Roblox",
                state = "Brookhaven RP",
                largeImageKey = "",
                largeImageText = "Brookhaven RP",
                smallImageKey = "",
                smallImageText = "Roblox"
            ),
            ActivityProfile(
                id = "minecraft",
                name = "Minecraft",
                details = "Playing Minecraft",
                state = "Survival Mode",
                largeImageKey = "",
                largeImageText = "Minecraft"
            ),
            ActivityProfile(
                id = "netflix",
                name = "Watching Netflix",
                details = "Watching Netflix",
                state = "Something good",
                largeImageKey = "",
                largeImageText = "Netflix"
            )
        )
    )
        private set

    var discordToken by mutableStateOf("")
        private set

    init {
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
                isPresenceActive = true
                statusMessage = "Presence updated"
            }

            override fun onError(message: String) {
                statusMessage = message
            }
        })
    }

    fun connectRoblox(name: String) {
        val clean = name.trim()
        robloxUsername = clean
        if (clean.isNotEmpty()) prefs.edit().putString("roblox", clean).apply()
        robloxUserId = null
        robloxGame = null
        presenceJob?.cancel()

        if (clean.isEmpty()) {
            robloxStatus = "ใส่ชื่อผู้ใช้ Roblox เพื่อดูว่ากำลังเล่นอยู่"
            return
        }

        presenceJob = viewModelScope.launch {
            robloxStatus = "กำลังค้นหาผู้ใช้..."
            try {
                val id = withContext(Dispatchers.IO) { RobloxPresence.resolveUserId(clean) }
                if (id == null) {
                    robloxStatus = "ไม่พบผู้ใช้ \"$clean\""
                    return@launch
                }
                robloxUserId = id
            } catch (e: Exception) {
                robloxStatus = "เชื่อมต่อ Roblox ไม่ได้: ${e.message}"
                return@launch
            }

            while (isActive) {
                refreshRobloxPresence()
                delay(10_000)
            }
        }
    }

    private suspend fun refreshRobloxPresence() {
        val id = robloxUserId ?: return
        try {
            val info = withContext(Dispatchers.IO) { RobloxPresence.fetch(id) }
            robloxGame = info
            applyAutoGame()
            val time = SimpleDateFormat("HH:mm:ss").format(Date())
            robloxStatus = if (info == null) "ไม่มีข้อมูลสถานะ (อาจตั้งค่าความเป็นส่วนตัวไว้) · $time"
                           else "อัปเดตล่าสุด $time"
        } catch (e: Exception) {
            robloxStatus = "ดึงสถานะไม่สำเร็จ: ${e.message}"
        }
    }

    fun toggleAutoGame(enabled: Boolean) {
        autoGameStatus = enabled
        prefs.edit().putBoolean("auto", enabled).apply()
        if (enabled && discordToken.isBlank()) {
            robloxStatus = "ใส่ Discord token ในหน้า Settings ก่อน จึงจะอัปเดตสถานะได้"
            return
        }
        if (!enabled && lastAutoGame != null) {
            lastAutoGame = null
            if (currentProfile != null) startPresence() else clearPresence()
            return
        }
        viewModelScope.launch { applyAutoGame() }
    }

    /** ตั้งข้อความบอกสถานะ Discord ตามแมพที่เล่นอยู่ หรือข้อความตอนไม่ได้เข้าแมพ */
    fun saveIdleText(text: String) {
        val clean = text.trim().ifBlank { "ไม่ได้เข้าแมพ" }
        idleText = clean
        prefs.edit().putString("idle", clean).apply()
        lastAutoGame = null
        if (autoGameStatus) viewModelScope.launch { applyAutoGame() }
    }

    private suspend fun applyAutoGame() {
        if (!autoGameStatus || discordToken.isBlank()) return

        val base = profiles.firstOrNull { it.id == "roblox" } ?: profiles.firstOrNull() ?: return
        val info = robloxGame
        val gameName = if (info?.presenceType == 2) (info.gameName ?: info.lastLocation) else null

        val signature: String
        val details: String
        val cover: String?
        val inGame: Boolean

        if (!gameName.isNullOrBlank()) {
            inGame = true
            details = gameName
            cover = info?.thumbnailUrl
            signature = "game|$gameName|${cover ?: ""}"
        } else {
            inGame = false
            details = idleText
            cover = null
            signature = "idle|$idleText"
        }

        if (signature == lastAutoGame) return
        lastAutoGame = signature

        gateway.setToken(discordToken)
        gateway.start()

        val coverMp = cover?.let { url ->
            withContext(Dispatchers.IO) {
                DiscordApi.fetchMediaProxy(discordToken, DiscordApi.MEDIA_APP_ID, url)
            }
        }

        val activityJson = gateway.buildActivity(
            name = "Roblox",
            details = details,
            state = "Roblox",
            largeImage = coverMp ?: base.largeImageKey,
            largeText = details,
            smallImage = base.smallImageKey,
            smallText = base.smallImageText,
            applicationId = base.applicationId,
            showElapsed = inGame
        )

        gateway.setPresence(activityJson)
        isPresenceActive = true
        statusMessage = if (inGame) "ส่งสถานะ Discord: กำลังเล่น $details" else "ส่งสถานะ Discord: $details"
    }

    fun updateToken(token: String) {
        discordToken = token
        prefs.edit().putString("token", token).apply()
        gateway.setToken(token)
        loadDiscordProfile(token)
    }

    /** เปิดแอปครั้งถัดไป: กู้ token และชื่อ Roblox ที่บันทึกไว้ */
    fun restoreSession() {
        idleText = prefs.getString("idle", "ไม่ได้เข้าแมพ") ?: "ไม่ได้เข้าแมพ"
        val savedToken = prefs.getString("token", "") ?: ""
        if (savedToken.isNotBlank()) {
            updateToken(savedToken)
        } else {
            val savedName = prefs.getString("roblox", "") ?: ""
            if (savedName.isNotBlank()) connectRoblox(savedName)
        }
    }

    /** หลังล็อกอิน: โหลดโปรไฟล์ Discord และเชื่อม Roblox อัตโนมัติ */
    private fun loadDiscordProfile(token: String) {
        viewModelScope.launch {
            try {
                discordUser = withContext(Dispatchers.IO) { DiscordApi.fetchMe(token) }
            } catch (e: Exception) {
                statusMessage = "โหลดโปรไฟล์ Discord ไม่สำเร็จ: ${e.message}"
                return@launch
            }

            val robloxName = try {
                withContext(Dispatchers.IO) { DiscordApi.fetchRobloxUsername(token) }
            } catch (e: Exception) {
                null
            }

            if (!robloxName.isNullOrBlank()) {
                connectRoblox(robloxName)
                toggleAutoGame(prefs.getBoolean("auto", true))
            } else {
                statusMessage = "ยังไม่ได้เชื่อม Roblox กับ Discord"
            }
        }
    }

    fun selectProfile(profile: ActivityProfile) {
        currentProfile = profile
    }

    fun startPresence() {
        val profile = currentProfile ?: return
        if (discordToken.isBlank()) {
            statusMessage = "Please set Discord token in Settings first"
            return
        }

        gateway.setToken(discordToken)

        val activityJson = gateway.buildActivity(
            name = profile.name,
            details = profile.details,
            state = profile.state,
            largeImage = profile.largeImageKey,
            largeText = profile.largeImageText,
            smallImage = profile.smallImageKey,
            smallText = profile.smallImageText,
            applicationId = profile.applicationId,
            showElapsed = profile.showElapsed
        )

        gateway.setPresence(activityJson)
        isPresenceActive = true
        statusMessage = "Starting presence..."
    }

    fun clearPresence() {
        gateway.clearPresence()
        isPresenceActive = false
        statusMessage = "Presence cleared"
    }

    fun stopGateway() {
        gateway.stop()
        isPresenceActive = false
        connectedUsername = null
    }

    fun addOrUpdateProfile(profile: ActivityProfile) {
        val existing = profiles.indexOfFirst { it.id == profile.id }
        profiles = if (existing >= 0) {
            profiles.toMutableList().also { it[existing] = profile }
        } else {
            profiles + profile
        }
        currentProfile = profile
    }

    fun createNewProfile(): ActivityProfile {
        return ActivityProfile(
            id = UUID.randomUUID().toString(),
            name = "New Profile",
            details = "Playing something",
            state = ""
        )
    }

    override fun onCleared() {
        super.onCleared()
        gateway.stop()
    }
}
