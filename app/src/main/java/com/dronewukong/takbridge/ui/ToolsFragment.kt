package com.dronewukong.takbridge.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                displayZoomControls = false
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            addJavascriptInterface(WingmanJsBridge(requireContext()), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    injectGpsBridge()
                    pendingTool?.let { tool ->
                        view.loadUrl("javascript:if(window.showTool)showTool('$tool');")
                        pendingTool = null
                    }
                }
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    return !(url.startsWith("file://") || url.contains("#"))
                }
            }
            webChromeClient = WebChromeClient()
        }

        webView.loadUrl("file:///android_asset/tools/tools_offline.html")
        return webView
    }

    fun navigateTo(toolId: String) {
        if (::webView.isInitialized) {
            webView.post { webView.loadUrl("javascript:if(window.showTool)showTool('$toolId');") }
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
        webView.loadUrl("javascript:$js")
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause()  { super.onPause();  webView.onPause() }
    override fun onDestroyView() { super.onDestroyView(); webView.destroy() }

    fun onBackPressed(): Boolean = if (webView.canGoBack()) { webView.goBack(); true } else false
}
