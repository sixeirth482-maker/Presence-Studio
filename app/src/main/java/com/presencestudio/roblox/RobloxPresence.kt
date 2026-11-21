package com.presencestudio.roblox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class RobloxGameInfo(
    val presenceType: Int,
    val placeId: Long?,
    val universeId: Long?,
    val gameName: String?,
    val lastLocation: String?,
    val thumbnailUrl: String? = null
)

/**
 * ดึงสถานะการเล่นของผู้ใช้ Roblox จากเว็บ API สาธารณะ (ไม่ต้องใช้ไฟล์ log)
 * userPresenceType: 0 = ออฟไลน์, 1 = ออนไลน์, 2 = กำลังเล่น, 3 = อยู่ใน Studio
 */
object RobloxPresence {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun presenceLabel(type: Int): String = when (type) {
        0 -> "ออฟไลน์"
        1 -> "ออนไลน์"
        2 -> "กำลังเล่น"
        3 -> "อยู่ใน Studio"
        else -> "ไม่ทราบสถานะ"
    }

    /** แปลงชื่อผู้ใช้ Roblox เป็น userId คืน null ถ้าไม่พบ */
    fun resolveUserId(username: String): Long? {
        val body = buildJsonObject {
            put("usernames", buildJsonArray { add(JsonPrimitive(username)) })
            put("excludeBannedUsers", false)
        }.toString().toRequestBody(jsonType)

        val req = Request.Builder()
            .url("https://users.roblox.com/v1/usernames/users")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("users API ${res.code}")
            val root = json.parseToJsonElement(res.body!!.string()).jsonObject
            return root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("id")?.jsonPrimitive?.longOrNull
        }
    }

    /** ดึงสถานะปัจจุบันของ userId */
    fun fetch(userId: Long): RobloxGameInfo? {
        val body = buildJsonObject {
            put("userIds", buildJsonArray { add(JsonPrimitive(userId)) })
        }.toString().toRequestBody(jsonType)

        val req = Request.Builder()
            .url("https://presence.roblox.com/v1/presence/users")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("presence API ${res.code}")
            val root = json.parseToJsonElement(res.body!!.string()).jsonObject
            val p = root["userPresences"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null

            val type = p["userPresenceType"]?.jsonPrimitive?.intOrNull ?: 0
            val placeId = p["placeId"]?.jsonPrimitive?.longOrNull
            val universeId = p["universeId"]?.jsonPrimitive?.longOrNull
            val lastLocation = p["lastLocation"]?.jsonPrimitive?.contentOrNull

            val info = if (type == 2 && universeId != null) gameInfo(universeId) else null

            return RobloxGameInfo(
                presenceType = type,
                placeId = placeId,
                universeId = universeId,
                gameName = info?.first,
                lastLocation = lastLocation,
                thumbnailUrl = info?.second
            )
        }
    }

    private val gameCache = HashMap<Long, Pair<String?, String?>>()

    /** คืนคู่ (ชื่อแมพ, URL รูปปกแมพ) มี cache กันยิง API ซ้ำทุกรอบ */
    @Synchronized
    private fun gameInfo(universeId: Long): Pair<String?, String?> {
        gameCache[universeId]?.let { return it }
        val name = fetchGameName(universeId)
        val thumb = fetchGameThumbnail(universeId)
        val result = name to thumb
        if (name != null || thumb != null) gameCache[universeId] = result
        return result
    }

    private fun fetchGameName(universeId: Long): String? {
        val req = Request.Builder()
            .url("https://games.roblox.com/v1/games?universeIds=$universeId")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val root = json.parseToJsonElement(res.body!!.string()).jsonObject
            return root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun fetchGameThumbnail(universeId: Long): String? {
        val req = Request.Builder()
            .url(
                "https://thumbnails.roblox.com/v1/games/multiget/thumbnails" +
                    "?universeIds=$universeId&countPerUniverse=1&defaults=true" +
                    "&size=768x432&format=Png&isCircular=false"
            )
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val root = json.parseToJsonElement(res.body!!.string()).jsonObject
            return root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("thumbnails")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("imageUrl")?.jsonPrimitive?.contentOrNull
        }
    }
}
