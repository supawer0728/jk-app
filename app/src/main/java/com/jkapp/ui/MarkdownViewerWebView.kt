package com.jkapp.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
fun MarkdownViewerWebView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    var contentHeightDp by remember { mutableStateOf(200) }
    val isPageLoaded = remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                isVerticalScrollBarEnabled = false
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    ViewerBridge(Handler(Looper.getMainLooper())) { heightPx ->
                        contentHeightDp = heightPx + 32
                    },
                    "Android"
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "file") return false
                        try { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) {}
                        return true
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        isPageLoaded.value = true
                    }
                }
                loadUrl("file:///android_asset/viewer.html")
            }
        },
        update = { webView ->
            if (isPageLoaded.value && webView.tag != markdown) {
                webView.tag = markdown
                webView.evaluateJavascript(
                    "setContent(${JSONObject.quote(markdown)})", null
                )
            }
        },
        modifier = modifier.then(Modifier.height(contentHeightDp.dp))
    )
}

private class ViewerBridge(
    private val mainHandler: Handler,
    private val onHeightChanged: (Int) -> Unit
) {
    @JavascriptInterface
    fun onHeightChanged(height: Int) {
        mainHandler.post { onHeightChanged(height) }
    }
}
