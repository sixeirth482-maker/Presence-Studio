package com.presencestudio.discord

import android.util.Log
import com.presencestudio.util.AppLog
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Simple Discord Gateway client for setting Rich Presence.
 * Uses user token (self-bot style). Use at your own risk — against Discord ToS.
 */
class DiscordGateway {

    companion object {
        private const val TAG = "DiscordGateway"
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        private const val INITIAL_RECONNECT_DELAY = 1000L
        private const val MAX_RECONNECT_DELAY = 30000L
    }

    interface Listener {
        fun onStateChanged(state: ConnectionState)
        fun onReady(username: String?)
        fun onPresenceUpdated()
        fun onError(message: String)
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, READY, ERROR
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var sequence: Int? = null
    private var sessionId: String? = null
    private var heartbeatInterval = 41250L
    private var reconnectDelay = INITIAL_RECONNECT_DELAY

    private val isRunning = AtomicBoolean(false)
    private val isReady = AtomicBoolean(false)
    private val tokenRef = AtomicReference<String?>(null)
    private val statusRef = AtomicReference("online")
    private val pendingPresence = AtomicReference<String?>(null)

    private var listener: Listener? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setToken(token: String) {
        tokenRef.set(token.trim())
    }

    fun setStatus(status: String) {
        statusRef.set(status)
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        connect()
    }

    fun stop() {
        isRunning.set(false)
        isReady.set(false)
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Stopped by user")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        sequence = null
        sessionId = null
        listener?.onStateChanged(ConnectionState.DISCONNECTED)
    }

    fun setPresence(activityJson: String) {
        pendingPresence.set(activityJson)
        AppLog.d("setPresence ready=${isReady.get()} running=${isRunning.get()}")
        if (isReady.get()) {
            sendPresence(activityJson)
        } else if (!isRunning.get()) {
            AppLog.d("gateway not running, starting...")
            start()
        } else {
            AppLog.d("gateway connecting, presence queued")
        }
    }

    fun clearPresence() {
        if (!isReady.get()) return
        val status = statusRef.get()
        val payload = JSONObject()
            .put("op", 3)
            .put("d", JSONObject()
                .put("since", JSONObject.NULL)
                .put("activities", JSONArray())
                .put("status", status)
                .put("afk", false)
            )
        webSocket?.send(payload.toString())
        listener?.onPresenceUpdated()
    }

    private fun connect() {
        listener?.onStateChanged(ConnectionState.CONNECTING)

        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(GATEWAY_URL)
            .build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                AppLog.d("Discord WS opened")
                listener?.onStateChanged(ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                isReady.set(false)
                heartbeatJob?.cancel()
                if (isRunning.get()) {
                    scheduleReconnect()
                } else {
                    listener?.onStateChanged(ConnectionState.DISCONNECTED)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure", t)
                AppLog.e("Discord WS failure", t)
                isReady.set(false)
                listener?.onError(t.message ?: "Connection failed")
                listener?.onStateChanged(ConnectionState.ERROR)
                if (isRunning.get()) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(socket: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val op = json.optInt("op", -1)
            val s = if (json.has("s") && !json.isNull("s")) json.getInt("s") else null
            if (s != null) sequence = s

            when (op) {
                10 -> { // Hello
                    val d = json.getJSONObject("d")
                    heartbeatInterval = d.getLong("heartbeat_interval")
                    startHeartbeat(socket)
                    identify(socket)
                }
                0 -> { // Dispatch
                    val t = json.optString("t")
                    when (t) {
                        "READY" -> {
                            isReady.set(true)
                            AppLog.d("Discord READY")
                            reconnectDelay = INITIAL_RECONNECT_DELAY
                            val d = json.getJSONObject("d")
                            sessionId = d.optString("session_id")
                            val user = d.optJSONObject("user")
                            val username = user?.optString("username")
                            listener?.onStateChanged(ConnectionState.READY)
                            listener?.onReady(username)
                            pendingPresence.get()?.let { sendPresence(it) }
                        }
                        "RESUMED" -> {
                            isReady.set(true)
                            listener?.onStateChanged(ConnectionState.READY)
                            pendingPresence.get()?.let { sendPresence(it) }
                        }
                    }
                }
                11 -> { // Heartbeat ACK
                    // ok
                }
                9 -> { // Invalid Session
                    isReady.set(false)
                    val canResume = json.optBoolean("d", false)
                    if (!canResume) {
                        sessionId = null
                        sequence = null
                    }
                    scope.launch {
                        delay(1000)
                        if (isRunning.get()) identify(socket)
                    }
                }
                7 -> { // Reconnect
                    socket.close(1000, "Reconnect requested")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
        }
    }

    private fun identify(socket: WebSocket) {
        val token = tokenRef.get()
        if (token.isNullOrBlank()) {
            listener?.onError("Discord token is empty")
            stop()
            return
        }

        val payload = JSONObject()
            .put("op", 2)
            .put("d", JSONObject()
                .put("token", token)
                .put("intents", 0)
                .put("properties", JSONObject()
                    .put("os", "Windows")
                    .put("browser", "Discord Client")
                    .put("device", "desktop")
                )
            )
        socket.send(payload.toString())
    }

    private fun startHeartbeat(socket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // First heartbeat after jitter
            delay((heartbeatInterval * 0.1).toLong())
            while (isActive && isRunning.get()) {
                val hb = JSONObject()
                    .put("op", 1)
                    .put("d", sequence)
                socket.send(hb.toString())
                delay(heartbeatInterval)
            }
        }
    }

    private fun sendPresence(activityJson: String) {
        try {
            val activity = JSONObject(activityJson)
            val status = statusRef.get()
            val payload = JSONObject()
                .put("op", 3)
                .put("d", JSONObject()
                    .put("since", JSONObject.NULL)
                    .put("activities", JSONArray().put(activity))
                    .put("status", status)
                    .put("afk", false)
                )
            webSocket?.send(payload.toString())
            AppLog.d("sendPresence op3 sent")
            listener?.onPresenceUpdated()
        } catch (e: Exception) {
            AppLog.e("sendPresence failed", e)
            listener?.onError("Failed to set presence: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            listener?.onStateChanged(ConnectionState.CONNECTING)
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 1.5).toLong().coerceAtMost(MAX_RECONNECT_DELAY)
            if (isRunning.get()) {
                connect()
            }
        }
    }

    /**
     * Helper to build a simple Activity JSON for Rich Presence.
     */
    fun buildActivity(
        name: String,
        details: String = "",
        state: String = "",
        largeImage: String = "",
        largeText: String = "",
        smallImage: String = "",
        smallText: String = "",
        applicationId: String = "",
        showElapsed: Boolean = true
    ): String {
        val activity = JSONObject()
            .put("name", name)
            .put("type", 0) // Playing

        if (details.isNotBlank()) activity.put("details", details)
        if (state.isNotBlank()) activity.put("state", state)

        if (showElapsed) {
            activity.put("timestamps", JSONObject().put("start", System.currentTimeMillis()))
        }

        if (largeImage.isNotBlank() || smallImage.isNotBlank()) {
            val assets = JSONObject()
            if (largeImage.isNotBlank()) {
                assets.put("large_image", largeImage)
                if (largeText.isNotBlank()) assets.put("large_text", largeText)
            }
            if (smallImage.isNotBlank()) {
                assets.put("small_image", smallImage)
                if (smallText.isNotBlank()) assets.put("small_text", smallText)
            }
            activity.put("assets", assets)
        }

        if (applicationId.isNotBlank()) {
            activity.put("application_id", applicationId)
        }

        return activity.toString()
    }
}
