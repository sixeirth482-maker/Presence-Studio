package com.presencestudio.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.presencestudio.ui.theme.*

/**
 * Discord login via WebView (same UX style as Chevstrap / Apexstrap).
 * User logs in with email + password, then the app extracts the token.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginScreen(
    onTokenReceived: (String) -> Unit,
    onCancel: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Loading Discord login...") }
    val handler = remember { Handler(Looper.getMainLooper()) }

    BackHandler { onCancel() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Login with Discord",
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                Text(
                    text = statusText,
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onTokenFound(token: String) {
                                val clean = token.trim()
                                    .removePrefix("\"")
                                    .removeSuffix("\"")
                                    .trim()
                                if (clean.length > 30) {
                                    handler.post {
                                        statusText = "Token found! Saving..."
                                        onTokenReceived(clean)
                                    }
                                }
                            }
                        }, "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                statusText = "Log in with your Discord account"

                                // Inject token extraction script
                                val extractJs = """
                                    (function() {
                                        function tryExtract() {
                                            try {
                                                // 1. Classic localStorage
                                                var t = window.localStorage.getItem('token');
                                                if (t) { AndroidBridge.onTokenFound(t); return true; }

                                                // 2. Search all localStorage values for token-like strings
                                                for (var i = 0; i < localStorage.length; i++) {
                                                    var k = localStorage.key(i);
                                                    var v = localStorage.getItem(k);
                                                    if (!v) continue;
                                                    v = String(v).replace(/^"|"$/g, '');
                                                    // Discord user tokens are long and contain dots (JWT-like)
                                                    if (v.length > 50 && v.indexOf('.') > 0 && v.split('.').length >= 3) {
                                                        AndroidBridge.onTokenFound(v);
                                                        return true;
                                                    }
                                                }

                                                // 3. Webpack module search (common in Discord web clients)
                                                if (typeof webpackChunkdiscord_app !== 'undefined') {
                                                    var chunk = webpackChunkdiscord_app;
                                                    // push a fake module to find the token module
                                                    chunk.push([
                                                        [Math.random()],
                                                        {},
                                                        function(e) {
                                                            for (var m in e.c) {
                                                                try {
                                                                    var exp = e.c[m].exports;
                                                                    if (!exp) continue;
                                                                    // look for token getter
                                                                    if (exp.default && typeof exp.default.getToken === 'function') {
                                                                        var tok = exp.default.getToken();
                                                                        if (tok) { AndroidBridge.onTokenFound(tok); return; }
                                                                    }
                                                                    if (typeof exp.getToken === 'function') {
                                                                        var tok2 = exp.getToken();
                                                                        if (tok2) { AndroidBridge.onTokenFound(tok2); return; }
                                                                    }
                                                                } catch(err) {}
                                                            }
                                                        }
                                                    ]);
                                                }
                                            } catch(e) {}
                                            return false;
                                        }

                                        tryExtract();
                                        // Retry a few times after login redirect
                                        setTimeout(tryExtract, 1500);
                                        setTimeout(tryExtract, 3000);
                                        setTimeout(tryExtract, 5000);
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(extractJs, null)

                                if (url != null && (url.contains("/channels") || url.contains("/app") || url.contains("discord.com/activity"))) {
                                    statusText = "Logged in – extracting token..."
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false
                        }

                        loadUrl("https://discord.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Primary
                )
            }
        }
    }
}
