package com.dronewukong.takbridge.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import com.dronewukong.takbridge.ui.bridge.WingmanJsBridge

/**
 * ToolsFragment — Forge RF Tools suite embedded in TAK Bridge.
 *
 * Provides all Forge calculators offline (Channel Planner, Range Estimator,
 * Fresnel Zone, Harmonics, Dipole Length, VTX Config, FC Matcher, ELRS, etc.)
 * RF Terrain Map and Mesh Planner require network for elevation + tiles.
 *
 * GPS bridge: Android.getGpsLocation() pulls device location into tools.
 */
class ToolsFragment : Fragment() {

    private lateinit var webView: WebView
    private var pendingTool: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingReport: String? = null
    private val reportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val report = pendingReport
        pendingReport = null
        if (uri != null && report != null) {
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        requireNotNull(appContext.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) }
                    }.isSuccess
                }
                Toast.makeText(appContext, if (saved) "Report saved" else "Could not save report", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    companion object {
        internal const val TOOLS_URL = "https://appassets.androidplatform.net/assets/tools/tools_offline.html"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()
        webView = WebView(requireContext()).apply {
            setBackgroundColor(Color.rgb(13, 20, 27))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = true // User-selected elevation files only.
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                displayZoomControls = false
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = false
            }

            addJavascriptInterface(WingmanJsBridge(requireContext()) { name, html ->
                post {
                    if (isAdded && url?.substringBefore('#') == TOOLS_URL && pendingReport == null) {
                        pendingReport = html
                        runCatching { reportPicker.launch(name) }.onFailure {
                            pendingReport = null
                            Toast.makeText(context, "No document picker available", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, "Android")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    injectGpsBridge()
                    pendingTool?.let { tool ->
                        view.evaluateJavascript("if(window.showTool)showTool(${JSONObject.quote(tool)});", null)
                        pendingTool = null
                    }
                }
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    if (url.scheme == "https" && url.host == "appassets.androidplatform.net" &&
                        url.path == "/assets/tools/tools_offline.html") return false
                    if (request.isForMainFrame && url.scheme in listOf("https", "http")) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        filePicker.launch(params.createIntent().apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        })
                        true
                    } catch (_: Exception) {
                        callback.onReceiveValue(null)
                        fileCallback = null
                        true
                    }
                }
            }
        }

        webView.loadUrl(TOOLS_URL)
        return webView
    }

    fun navigateTo(toolId: String) {
        if (::webView.isInitialized) {
            webView.post { webView.evaluateJavascript("if(window.showTool)showTool(${JSONObject.quote(toolId)});", null) }
        } else {
            pendingTool = toolId
        }
    }

    private fun injectGpsBridge() {
        val js = """
        (function() {
            if (typeof Android === 'undefined') return;
            function tryInjectGps(latId, lonId) {
                var latEl = document.getElementById(latId);
                var lonEl = document.getElementById(lonId);
                if (!latEl || !lonEl || document.getElementById(latId+'-gps-btn')) return;
                var btn = document.createElement('button');
                btn.id = latId + '-gps-btn';
                btn.textContent = '⊕ Use GPS';
                btn.style.cssText = 'margin-left:8px;padding:4px 10px;font-size:11px;' +
                    'border-radius:4px;border:1px solid #22c55e;background:rgba(34,197,94,0.1);' +
                    'color:#22c55e;cursor:pointer;font-family:inherit;';
                btn.onclick = function() {
                    try {
                        var loc = JSON.parse(Android.getGpsLocation());
                        if (loc.lat) {
                            latEl.value = loc.lat.toFixed(6);
                            lonEl.value = loc.lon.toFixed(6);
                            latEl.dispatchEvent(new Event('input'));
                            lonEl.dispatchEvent(new Event('input'));
                            btn.textContent = '⊕ ' + loc.lat.toFixed(4) + ', ' + loc.lon.toFixed(4);
                        } else { btn.textContent = '⊕ No fix'; }
                    } catch(e) { btn.textContent = '⊕ Error'; }
                };
                latEl.parentNode.insertBefore(btn, latEl.nextSibling);
            }
            tryInjectGps('terrain-tx-lat', 'terrain-tx-lon');
            tryInjectGps('terrain-rx-lat', 'terrain-rx-lon');
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause()  { super.onPause();  webView.onPause() }
    override fun onDestroyView() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.destroy()
        super.onDestroyView()
    }

    fun onBackPressed(onLeaveTools: () -> Unit) {
        webView.evaluateJavascript("""
            (function(){
                var chooser = document.getElementById('tool-library');
                if (chooser && chooser.open) { chooser.close(); return true; }
                return false;
            })();
        """.trimIndent()) { closedChooser ->
            if (isAdded && closedChooser != "true") {
                if (webView.canGoBack()) webView.goBack() else onLeaveTools()
            }
        }
    }
}
