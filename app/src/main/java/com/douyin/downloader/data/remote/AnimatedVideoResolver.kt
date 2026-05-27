package com.douyin.downloader.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimatedVideoResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(noteId: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()

        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = DouyinApi.USER_AGENT
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""
                    if (url.contains("douyinvod") && deferred.isActive) {
                        deferred.complete(url)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var videos = document.querySelectorAll('video');
                            for (var v of videos) {
                                if (v.src && v.src.includes('douyinvod')) {
                                    return v.src;
                                }
                            }
                            return '';
                        })();
                        """.trimIndent(),
                    ) { result ->
                        val cleaned = result?.trim('"') ?: ""
                        if (cleaned.isNotEmpty() &&
                            cleaned.contains("douyinvod") &&
                            deferred.isActive
                        ) {
                            deferred.complete(cleaned)
                        }
                    }
                }
            }

            loadUrl("https://www.douyin.com/note/$noteId")
        }

        val result = withTimeoutOrNull(15_000L) { deferred.await() }

        webView.destroy()
        return@withContext result ?: ""
    }
}
