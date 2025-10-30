package com.prodestino.manaus

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.prodestino.manaus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null

    // Solicita várias permissões de uma vez (location/camera/mic)
    private val askPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado não precisa de tratamento aqui; o WebView lida em runtime */ }

    // File chooser (upload em <input type="file">)
    private val openFiles = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val cb = pendingFilePathCallback
        pendingFilePathCallback = null
        cb?.onReceiveValue(uris?.toTypedArray() ?: emptyArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.WEBVIEW_DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        // Permissões (best effort)
        askPerms.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ))

        setupWebView()
        binding.webView.loadUrl("https://manaus.prodestino.com/")
    }

    private fun setupWebView() = with(binding.webView) {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                view.loadUrl(req.url.toString())
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {

            // Geolocalização do HTML5
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
                val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                callback.invoke(origin, (fine || coarse), false)
            }

            // Permissões de câmera/microfone para WebRTC
            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                val resources = request.resources
                // Concede se a app tiver as permissões do Android
                val allow = resources.all {
                    when (it) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            hasPermission(Manifest.permission.RECORD_AUDIO)
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            hasPermission(Manifest.permission.CAMERA)
                        else -> true
                    }
                }
                if (allow) request.grant(resources) else request.deny()
            }

            // Upload de arquivos
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                pendingFilePathCallback?.onReceiveValue(null)
                pendingFilePathCallback = filePathCallback
                val mime = fileChooserParams?.acceptTypes?.firstOrNull()?.ifBlank { "*/*" } ?: "*/*"
                openFiles.launch(mime)
                return true
            }
        }

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PermissionChecker.PERMISSION_GRANTED

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }
}
