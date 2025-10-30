package com.prodestino.manaus

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prodestino.manaus.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val APP_URL = "https://manaus.prodestino.com"

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        filePathCallback?.onReceiveValue(uris?.toTypedArray() ?: emptyArray()); filePathCallback = null
    }

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back físico: usa Dispatcher (correto nas versões novas)
        onBackPressedDispatcher.addCallback(this) {
            if (binding.webview.canGoBack()) binding.webview.goBack() else finish()
        }

        goImmersive()
        setupWebView(binding.webview)
        if (savedInstanceState == null) binding.webview.loadUrl(APP_URL)
        else binding.webview.restoreState(savedInstanceState)
    }

    override fun onResume() { super.onResume(); goImmersive() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) goImmersive() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); binding.webview.saveState(outState) }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.allowFileAccess = true; s.allowContentAccess = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val url = r?.url?.toString() ?: return false
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("intent:")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) {}
                    return true
                }
                return false
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, cb: GeolocationPermissions.Callback?) {
                requestRuntimePermissions(); cb?.invoke(origin, true, false)
            }
            override fun onPermissionRequest(req: PermissionRequest?) { req?.grant(req.resources) }
            override fun onShowFileChooser(wv: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                filePathCallback = cb
                pickFiles.launch(params?.acceptTypes?.firstOrNull() ?: "*/*")
                return true
            }
        }

        wv.setBackgroundColor(Color.BLACK)
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) perms += listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        requestPerms.launch(perms.toTypedArray())
    }

    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}
