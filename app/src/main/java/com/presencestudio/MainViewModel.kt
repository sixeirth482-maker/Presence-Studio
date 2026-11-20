package com.presencestudio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presencestudio.data.ActivityProfile
import com.presencestudio.data.ConnectionState
import com.presencestudio.discord.DiscordGateway
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel : ViewModel() {

    private val gateway = DiscordGateway()

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

    fun updateToken(token: String) {
        discordToken = token
        gateway.setToken(token)
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
