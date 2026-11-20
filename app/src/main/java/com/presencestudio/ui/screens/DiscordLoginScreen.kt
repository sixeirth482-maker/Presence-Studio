package com.presencestudio.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.presencestudio.ui.theme.*

/**
 * Discord login via WebView.
 * Improved settings to reduce white-screen issues on modern Discord pages.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginScreen(
    onTokenReceived: (String) -> Unit,
    onCancel: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Loading Discord login...") }
    var webView by remember { mutableStateOf<WebView?>(null) }
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
                .padding(horizontal = 4.dp, vertical = 8.dp),
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
            IconButton(onClick = {
                statusText = "Refreshing..."
                isLoading = true
                webView?.reload()
            }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Critical settings for Discord
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowContentAccess = true
                            allowFileAccess = false
                            mediaPlaybackRequiresUserGesture = false
                            // Use a normal Chrome mobile UA (Discord often blocks weird UAs)
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                            // Important for modern sites
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportZoom(false)
                            // Enable cookies
                            javaScriptCanOpenWindowsAutomatically = true
                        }

                        // Cookies (needed for Discord login session)
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(this@apply, true)
                        }

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

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                statusText = "Loading..."
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                statusText = "Log in with your Discord account"

                                val extractJs = """
                                    (function() {
                                        function tryExtract() {
                                            try {
                                                var t = window.localStorage.getItem('token');
                                                if (t) { AndroidBridge.onTokenFound(t); return true; }

                                                for (var i = 0; i < localStorage.length; i++) {
                                                    var k = localStorage.key(i);
                                                    var v = localStorage.getItem(k);
                                                    if (!v) continue;
                                                    v = String(v).replace(/^"|"$/g, '');
                                                    if (v.length > 50 && v.indexOf('.') > 0 && v.split('.').length >= 3) {
                                                        AndroidBridge.onTokenFound(v);
                                                        return true;
                                                    }
                                                }

                                                if (typeof webpackChunkdiscord_app !== 'undefined') {
                                                    var chunk = webpackChunkdiscord_app;
                                                    chunk.push([
                                                        [Math.random()],
                                                        {},
                                                        function(e) {
                                                            for (var m in e.c) {
                                                                try {
                                                                    var exp = e.c[m].exports;
                                                                    if (!exp) continue;
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
                                        setTimeout(tryExtract, 2000);
                                        setTimeout(tryExtract, 4000);
                                        setTimeout(tryExtract, 7000);
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(extractJs, null)

                                if (url != null && (url.contains("/channels") || url.contains("/app") || url.contains("/login") == false && url.contains("discord.com"))) {
                                    statusText = "Checking login status..."
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    statusText = "Failed to load. Try Refresh."
                                    isLoading = false
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false
                        }

                        webView = this
                        // Load Discord login (mobile site sometimes works better)
                        loadUrl("https://discord.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { /* keep */ }
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
