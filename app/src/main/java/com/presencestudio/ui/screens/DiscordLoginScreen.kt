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
import androidx.compose.material.icons.filled.Check
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
                statusText = "Checking token..."
                webView?.evaluateJavascript("window.__psCheck && window.__psCheck();", null)
            }) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Check token",
                    tint = TextSecondary
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
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this@apply, true)

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
                                        if (window.__psPollInstalled) { if (window.__psCheck) window.__psCheck(); return; }
                                        window.__psPollInstalled = true;
                                        var sent = false;
                                        var timer = null;

                                        function send(t) {
                                            if (sent || !t) return;
                                            t = String(t).trim().replace(/^"+|"+$/g, '').trim();
                                            if (t.length <= 30) return;
                                            sent = true;
                                            if (timer) clearInterval(timer);
                                            AndroidBridge.onTokenFound(t);
                                        }

                                        function fromStorage() {
                                            try {
                                                var t = window.localStorage.getItem('token');
                                                if (t) return t;
                                                for (var i = 0; i < localStorage.length; i++) {
                                                    var k = localStorage.key(i);
                                                    var v = localStorage.getItem(k);
                                                    if (!v) continue;
                                                    v = String(v).replace(/^"|"$/g, '');
                                                    if (v.length > 50 && v.split('.').length >= 3) return v;
                                                }
                                            } catch (e) {}
                                            return null;
                                        }

                                        function fromWebpack() {
                                            try {
                                                if (typeof webpackChunkdiscord_app === 'undefined') return null;
                                                var found = null;
                                                webpackChunkdiscord_app.push([[Math.random()], {}, function(req) {
                                                    for (var m in req.c) {
                                                        try {
                                                            var exp = req.c[m].exports;
                                                            if (!exp) continue;
                                                            var fn = (exp.default && exp.default.getToken) || exp.getToken;
                                                            if (typeof fn === 'function') {
                                                                var tok = fn();
                                                                if (tok) { found = tok; return; }
                                                            }
                                                        } catch (err) {}
                                                    }
                                                }]);
                                                return found;
                                            } catch (e) { return null; }
                                        }

                                        function check() {
                                            if (sent) return;
                                            var t = fromStorage() || fromWebpack();
                                            if (t) send(t);
                                        }

                                        window.__psCheck = check;
                                        timer = setInterval(check, 1500);
                                        check();
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(extractJs, null)
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
