package com.presencestudio.roblox

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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
 * ดึงสถานะการเล่นของผู้ใช้ Roblox จากเว็บ API สาธารณะ
 * userPresenceType: 0 offline, 1 online, 2 in-game, 3 studio
 *
 * สำคัญ: บางบัญชี (โดยเฉพาะบัญชีรอง) presence API อาจไม่ส่ง universeId มา
 * แต่ยังส่ง placeId / lastLocation → ต้องมี fallback
 */
object RobloxPresence {

    private const val TAG = "RobloxPresence"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var robloxCookie: String? = null

    fun setCookie(cookie: String?) {
        robloxCookie = cookie?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val gameCache = HashMap<Long, Pair<String?, String?>>()
    private val placeToUniverseCache = HashMap<Long, Long?>()

    fun presenceLabel(type: Int): String = when (type) {
        0 -> "ออฟไลน์"
        1 -> "ออนไลน์"
        2 -> "กำลังเล่น"
        3 -> "อยู่ใน Studio"
        else -> "ไม่ทราบสถานะ"
    }

    fun resolveUserId(username: String): Long? {
        val body = buildJsonObject {
            put("usernames", buildJsonArray { add(JsonPrimitive(username.trim())) })
            put("excludeBannedUsers", false)
        }.toString().toRequestBody(jsonType)

        val req = Request.Builder()
            .url("https://users.roblox.com/v1/usernames/users")
            .post(body)
            .header("User-Agent", "PresenceStudio/1.0")
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("users API ${res.code}")
            val root = json.parseToJsonElement(res.body!!.string()).jsonObject
            return root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("id").asLongOrNull()
        }
    }

    fun fetch(userId: Long): RobloxGameInfo? {
        val body = buildJsonObject {
            put("userIds", buildJsonArray { add(JsonPrimitive(userId)) })
        }.toString().toRequestBody(jsonType)

        val reqBuilder = Request.Builder()
            .url("https://presence.roblox.com/v1/presence/users")
            .post(body)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .header("Origin", "https://www.roblox.com")
            .header("Referer", "https://www.roblox.com/")
        robloxCookie?.let { reqBuilder.header("Cookie", ".ROBLOSECURITY=$it") }
        val req = reqBuilder.build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("presence API ${res.code}")
            val raw = res.body!!.string()
            Log.d(TAG, "presence raw userId=$userId body=$raw")

            val root = json.parseToJsonElement(raw).jsonObject
            val p = root["userPresences"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null

            val type = p["userPresenceType"].asIntOrNull() ?: 0
            val placeId = p["placeId"].asLongOrNull()
            val rootPlaceId = p["rootPlaceId"].asLongOrNull()
            var universeId = p["universeId"].asLongOrNull()
            val lastLocation = p["lastLocation"].asStringOrNull()
                ?.takeIf { it.isNotBlank() && !it.equals("Website", ignoreCase = true) }

            // Fallback: บางบัญชีได้ placeId แต่ไม่มี universeId
            if (type == 2 && universeId == null) {
                val placeForLookup = placeId ?: rootPlaceId
                if (placeForLookup != null) {
                    universeId = resolveUniverseId(placeForLookup)
                    Log.d(TAG, "resolved universeId=$universeId from placeId=$placeForLookup")
                }
            }

            var gameName: String? = null
            var thumbnailUrl: String? = null

            if (type == 2 && universeId != null) {
                val info = gameInfo(universeId)
                gameName = info.first
                thumbnailUrl = info.second
            }

            // Fallback ชื่อแมพจาก lastLocation
            if (type == 2 && gameName.isNullOrBlank()) {
                gameName = lastLocation
            }

            Log.d(
                TAG,
                "parsed type=$type placeId=$placeId rootPlaceId=$rootPlaceId universeId=$universeId gameName=$gameName lastLocation=$lastLocation"
            )

            return RobloxGameInfo(
                presenceType = type,
                placeId = placeId ?: rootPlaceId,
                universeId = universeId,
                gameName = gameName,
                lastLocation = lastLocation,
                thumbnailUrl = thumbnailUrl
            )
        }
    }

    @Synchronized
    private fun resolveUniverseId(placeId: Long): Long? {
        if (placeToUniverseCache.containsKey(placeId)) return placeToUniverseCache[placeId]

        val urls = listOf(
            "https://apis.roblox.com/universes/v1/places/$placeId/universe",
            "https://www.roblox.com/places/api-get-details?assetId=$placeId"
        )

        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "PresenceStudio/1.0")
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val body = res.body!!.string()
                    val root = json.parseToJsonElement(body)
                    val uid = when {
                        root is kotlinx.serialization.json.JsonObject && root.containsKey("universeId") ->
                            root["universeId"].asLongOrNull()
                        root is kotlinx.serialization.json.JsonObject && root.containsKey("UniverseId") ->
                            root["UniverseId"].asLongOrNull()
                        else -> null
                    }
                    if (uid != null) {
                        placeToUniverseCache[placeId] = uid
                        return uid
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveUniverseId failed for $placeId via $url: ${e.message}")
            }
        }

        placeToUniverseCache[placeId] = null
        return null
    }

    @Synchronized
    private fun gameInfo(universeId: Long): Pair<String?, String?> {
        gameCache[universeId]?.let { return it }
        val name = fetchGameName(universeId)
        val thumb = fetchGameThumbnail(universeId)
        val result = name to thumb
        if (name != null || thumb != null) {
            gameCache[universeId] = result
        }
        return result
    }

    private fun fetchGameName(universeId: Long): String? {
        val req = Request.Builder()
            .url("https://games.roblox.com/v1/games?universeIds=$universeId")
            .get()
            .header("User-Agent", "PresenceStudio/1.0")
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "games API ${res.code} universeId=$universeId")
                    return null
                }
                val root = json.parseToJsonElement(res.body!!.string()).jsonObject
                root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("name")?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchGameName error: ${e.message}")
            null
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
            .header("User-Agent", "PresenceStudio/1.0")
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val root = json.parseToJsonElement(res.body!!.string()).jsonObject
                root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("thumbnails")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("imageUrl")?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchGameThumbnail error: ${e.message}")
            null
        }
    }

    private fun JsonElement?.asLongOrNull(): Long? {
        if (this == null || this is JsonNull) return null
        return try {
            jsonPrimitive.longOrNull
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        if (this == null || this is JsonNull) return null
        return try {
            jsonPrimitive.intOrNull
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement?.asStringOrNull(): String? {
        if (this == null || this is JsonNull) return null
        return try {
            jsonPrimitive.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}
