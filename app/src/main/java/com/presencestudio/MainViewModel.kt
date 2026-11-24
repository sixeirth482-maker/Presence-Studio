package com.presencestudio

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.presencestudio.data.ActivityProfile
import com.presencestudio.data.ConnectionState
import com.presencestudio.data.Template
import com.presencestudio.discord.DiscordApi
import com.presencestudio.discord.DiscordUser
import com.presencestudio.presence.PresenceEngine
import com.presencestudio.presence.PresenceService
import com.presencestudio.roblox.RobloxGameInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ชั้นของหน้าจอ สถานะทั้งหมดอยู่ใน PresenceEngine
 * จึงไม่หายตอนหน้าจอถูกทำลาย
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("presence_studio", Context.MODE_PRIVATE)

    private var manualPresence by mutableStateOf(false)
    private var manualStatus by mutableStateOf("")

    var discordUser by mutableStateOf<DiscordUser?>(null)
        private set

    var currentProfile by mutableStateOf<ActivityProfile?>(null)
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

    var templates by mutableStateOf<List<Template>>(emptyList())
        private set

    // ค่าที่อ่านจากเอนจิน ใช้ getter เพื่อให้หน้าจออัปเดตตามอัตโนมัติ
    val robloxUsername: String get() = PresenceEngine.robloxUsername
    val robloxGame: RobloxGameInfo? get() = PresenceEngine.robloxGame
    val robloxStatus: String get() = PresenceEngine.robloxStatus
    val autoGameStatus: Boolean get() = PresenceEngine.autoGame
    val idleText: String get() = PresenceEngine.idleText
    val playingText: String get() = PresenceEngine.playingText
    val discordToken: String get() = PresenceEngine.discordToken
    val connectionState: ConnectionState get() = PresenceEngine.connectionState
    val connectedUsername: String? get() = PresenceEngine.connectedUsername
    val isPresenceActive: Boolean get() = manualPresence || PresenceEngine.presenceActive
    val statusMessage: String
        get() = if (manualStatus.isNotBlank()) manualStatus else PresenceEngine.statusMessage

    init {
        PresenceEngine.init(application)
        templates = decodeTemplates(prefs.getString("templates", "[]") ?: "[]")
    }

    // ---------- Session ----------

    fun updateToken(token: String) {
        PresenceEngine.setToken(token)
        loadDiscordProfile(token)
    }

    /** เปิดแอปครั้งถัดไป: กู้ token ชื่อ Roblox และสถานะอัตโนมัติ */
    fun restoreSession() {
        PresenceEngine.init(getApplication())
        val savedToken = PresenceEngine.discordToken
        if (savedToken.isNotBlank()) {
            loadDiscordProfile(savedToken)
        } else if (PresenceEngine.robloxUsername.isNotBlank()) {
            PresenceEngine.connectRoblox(PresenceEngine.robloxUsername)
        }
        if (PresenceEngine.autoGame && savedToken.isNotBlank()) {
            PresenceService.start(getApplication())
        }
    }

    private fun loadDiscordProfile(token: String) {
        viewModelScope.launch {
            try {
                discordUser = withContext(Dispatchers.IO) { DiscordApi.fetchMe(token) }
            } catch (e: Exception) {
                manualStatus = "โหลดโปรไฟล์ Discord ไม่สำเร็จ: ${e.message}"
                return@launch
            }

            val robloxName = try {
                withContext(Dispatchers.IO) { DiscordApi.fetchRobloxUsername(token) }
            } catch (e: Exception) {
                null
            }

            if (!robloxName.isNullOrBlank()) {
                PresenceEngine.connectRoblox(robloxName)
                // เปิดอัตโนมัติเฉพาะครั้งแรกที่เชื่อม หลังจากนั้นใช้ค่าที่ผู้ใช้ตั้งไว้
                if (!prefs.contains("auto")) toggleAutoGame(true)
            } else {
                manualStatus = "ยังไม่ได้เชื่อม Roblox กับ Discord"
            }
        }
    }

    // ---------- Roblox / auto status ----------

    fun connectRoblox(name: String) = PresenceEngine.connectRoblox(name)

    fun toggleAutoGame(enabled: Boolean) = PresenceEngine.setAutoGame(enabled)

    fun saveIdleText(text: String) = PresenceEngine.saveTexts(text, PresenceEngine.playingText)

    // ---------- Templates ----------

    fun saveTemplate(name: String, idle: String, playing: String) {
        val t = Template(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            idleText = idle.trim().ifBlank { PresenceEngine.DEFAULT_IDLE },
            playingText = playing.trim().ifBlank { PresenceEngine.DEFAULT_PLAYING }
        )
        templates = templates + t
        persistTemplates()
    }

    fun deleteTemplate(id: String) {
        templates = templates.filterNot { it.id == id }
        persistTemplates()
    }

    fun applyTemplate(t: Template) {
        PresenceEngine.saveTexts(t.idleText, t.playingText)
        manualStatus = "ใช้เทมเพลต \"${t.name}\" แล้ว"
    }

    private fun persistTemplates() {
        val arr = JSONArray()
        templates.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("idle", t.idleText)
                    .put("playing", t.playingText)
            )
        }
        prefs.edit().putString("templates", arr.toString()).apply()
    }

    private fun decodeTemplates(raw: String): List<Template> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Template(
                id = o.getString("id"),
                name = o.getString("name"),
                idleText = o.getString("idle"),
                playingText = o.getString("playing")
            )
        }
    }

    // ---------- Manual presence (Profiles) ----------

    fun selectProfile(profile: ActivityProfile) {
        currentProfile = profile
    }

    fun startPresence() {
        val profile = currentProfile ?: return
        if (discordToken.isBlank()) {
            manualStatus = "Please set Discord token in Settings first"
            return
        }

        val gateway = PresenceEngine.gateway
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
        manualPresence = true
        manualStatus = "Starting presence..."
    }

    fun clearPresence() {
        PresenceEngine.gateway.clearPresence()
        manualPresence = false
        manualStatus = "Presence cleared"
    }

    fun stopGateway() {
        PresenceEngine.stopGateway()
        manualPresence = false
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

    // ไม่ปิดการเชื่อมต่อตอนหน้าจอถูกทำลาย เพราะตอนนี้ทำงานผ่าน PresenceService
}
