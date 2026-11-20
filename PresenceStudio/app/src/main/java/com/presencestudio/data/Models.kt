package com.presencestudio.data

import kotlinx.serialization.Serializable

@Serializable
data class ActivityProfile(
    val id: String,
    val name: String,
    val details: String = "",
    val state: String = "",
    val largeImageKey: String = "",
    val largeImageText: String = "",
    val smallImageKey: String = "",
    val smallImageText: String = "",
    val showElapsed: Boolean = true,
    val applicationId: String = "" // Discord Application ID for assets
)

@Serializable
data class AppSettings(
    val discordToken: String = "",
    val status: String = "online" // online, idle, dnd, invisible
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY,
    ERROR
}
