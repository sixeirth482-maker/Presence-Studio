package com.presencestudio.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class DiscordUser(
    val id: String,
    val username: String,
    val globalName: String?,
    val avatar: String?,
    val banner: String?,
    val accentColor: Int?
) {
    val displayName: String get() = globalName ?: username

    val avatarUrl: String
        get() = if (avatar != null) {
            val ext = if (avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$id/$avatar.$ext?size=256"
        } else {
            "https://cdn.discordapp.com/embed/avatars/${(id.toLong() shr 22) % 6}.png"
        }

    val bannerUrl: String?
        get() = banner?.let { "https://cdn.discordapp.com/banners/$id/$it.png?size=1024" }
}

object DiscordApi {

    private const val BASE = "https://discord.com/api/v9"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun get(token: String, path: String): String {
        val req = Request.Builder()
            .url(BASE + path)
            .header("Authorization", token)
            .header("User-Agent", UA)
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("Discord API ${res.code}")
            return res.body!!.string()
        }
    }

    fun fetchMe(token: String): DiscordUser {
        val o = json.parseToJsonElement(get(token, "/users/@me")).jsonObject
        return DiscordUser(
            id = o["id"]!!.jsonPrimitive.content,
            username = o["username"]!!.jsonPrimitive.content,
            globalName = o["global_name"]?.jsonPrimitive?.contentOrNull,
            avatar = o["avatar"]?.jsonPrimitive?.contentOrNull,
            banner = o["banner"]?.jsonPrimitive?.contentOrNull,
            accentColor = o["accent_color"]?.jsonPrimitive?.intOrNull
        )
    }

    /** ชื่อ Roblox ที่เชื่อมไว้กับบัญชี Discord คืน null ถ้ายังไม่ได้เชื่อม */
    fun fetchRobloxUsername(token: String): String? {
        val arr = json.parseToJsonElement(get(token, "/users/@me/connections")).jsonArray
        return arr.map { it.jsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "roblox" }
            ?.get("name")?.jsonPrimitive?.contentOrNull
    }
}
