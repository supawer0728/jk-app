package com.jkapp.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
fun MarkdownEditorWebView(
    initialMarkdown: String,
    onMarkdownChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val onMarkdownChangedState = rememberUpdatedState(onMarkdownChanged)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    EditorBridge(Handler(Looper.getMainLooper())) { onMarkdownChangedState.value(it) },
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
                        if (initialMarkdown.isNotEmpty()) {
                            view.evaluateJavascript(
                                "setContent(${JSONObject.quote(initialMarkdown)})", null
                            )
                        }
                    }
                }
                loadUrl("file:///android_asset/editor.html")
            }
        },
        modifier = modifier
    )
}

private class EditorBridge(
    private val mainHandler: Handler,
    private val onMarkdownChanged: (String) -> Unit
) {
    @JavascriptInterface
    fun onMarkdownChanged(markdown: String) {
        mainHandler.post { onMarkdownChanged(markdown) }
    }
}
