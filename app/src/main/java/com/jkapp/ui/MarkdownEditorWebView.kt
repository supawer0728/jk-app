package com.jkapp.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
fun MarkdownEditorWebView(
    initialMarkdown: String,
    onMarkdownChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    EditorBridge(Handler(Looper.getMainLooper()), onMarkdownChanged),
                    "Android"
                )
                webViewClient = object : WebViewClient() {
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
